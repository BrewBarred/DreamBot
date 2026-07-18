// DreamMan server (v1.3) — Node/Express + MySQL.
// Implements the full client wire contract: zero-knowledge vault auth, cloud profiles, the
// marketplace, the v1.32 owner/admin endpoints, and static asset serving. The client stores
// this at ~/docker/ghost-bot/server.js and runs it in the ghost-bot container on port 3000.
//
// Design notes:
//  - Zero-knowledge: all crypto happens in the CLIENT. Every vault field here is an opaque
//    base64 string the server can't read; it only stores and returns them.
//  - role -> tier: the DB column is `role` (owner|admin|moderator|vip|free). The client reads a
//    field called `tier`, so auth responses map role -> tier and attach the matching `limits`.
//  - The pool is injectable via global.__DREAMMAN_POOL__ so the server can be tested without a
//    live database; in production it builds a real mysql2 pool from env.

const express = require('express');
const crypto = require('crypto');
const path = require('path');

// ── database ────────────────────────────────────────────────────────────────
function buildPool() {
  if (global.__DREAMMAN_POOL__) return global.__DREAMMAN_POOL__;   // test seam
  const mysql = require('mysql2/promise');
  return mysql.createPool({
    host: process.env.DB_HOST || 'ghost-mysql',
    port: Number(process.env.DB_PORT) || 3306,
    user: process.env.DB_USER || 'root',
    // Accept the common env var names - the deployment's .env uses DB_PASSWORD, not DB_PASS.
    password: process.env.DB_PASSWORD || process.env.DB_PASS
      || process.env.DB_ROOT_PASSWORD || process.env.MYSQL_ROOT_PASSWORD || '',
    database: process.env.DB_NAME || 'dreamman',
    waitForConnections: true,
    connectionLimit: 10,
    charset: 'utf8mb4',
  });
}
const pool = buildPool();
const q = async (sql, args = []) => {
  try {
    const [rows] = await pool.query(sql, args);
    return rows;
  } catch (e) {
    // Surface the REAL cause in `docker logs ghost-bot` instead of a generic 500. Common codes:
    //   ER_ACCESS_DENIED_ERROR -> wrong DB_USER/DB_PASS (the usual culprit)
    //   ER_BAD_DB_ERROR        -> DB_NAME wrong / database missing
    //   ER_NO_SUCH_TABLE       -> schema.sql not applied
    //   ER_BAD_FIELD_ERROR     -> schema doesn't match this server.js (re-apply schema.sql)
    console.error('[db] query failed:', (e.code || e.message), '::', String(sql).slice(0, 70));
    throw e;
  }
};

// Startup self-check: log clearly whether the database is reachable, so a bad password or an
// unapplied schema shows up in the container logs immediately on boot.
(async () => {
  try {
    const c = await pool.getConnection();
    await c.ping();
    c.release();
    console.log('[dreamman] database connection OK');
  } catch (e) {
    console.error('[dreamman] DATABASE CONNECTION FAILED:', (e.code || e.message));
    console.error('[dreamman] check the ghost-bot service env: DB_HOST, DB_USER, DB_PASS, DB_NAME');
  }
})();

// ── tiers ───────────────────────────────────────────────────────────────────
function limitsFor(tier) {
  switch (tier) {
    case 'owner':
    case 'admin':
    case 'moderator':
      return { maxLoops: 999, maxExtraAccounts: 999, canPublishVip: true, maxScripts: 1000 };
    case 'vip':
      return { maxLoops: 150, maxExtraAccounts: 5, canPublishVip: true, maxScripts: 30 };
    default: // free
      return { maxLoops: 50, maxExtraAccounts: 2, canPublishVip: false, maxScripts: 5 };
  }
}
// The user object the client expects inside auth responses (role -> tier + limits).
function userObject(row) {
  const tier = (row && row.role) ? row.role : 'free';
  return { username: row.username, tier, limits: limitsFor(tier) };
}

// ── helpers ─────────────────────────────────────────────────────────────────
const now = () => Date.now();
const newToken = () => crypto.randomBytes(32).toString('base64url');
const bad = (res, code, msg) => res.status(code).json({ error: msg });

async function userByName(username) {
  const rows = await q('SELECT * FROM users WHERE username = ? LIMIT 1', [username]);
  return rows[0] || null;
}
async function userByToken(token) {
  if (!token) return null;
  const rows = await q(
    'SELECT u.* FROM tokens t JOIN users u ON u.id = t.user_id WHERE t.token = ? LIMIT 1',
    [token]);
  return rows[0] || null;
}
function bearer(req) {
  const h = req.headers['authorization'] || '';
  return h.startsWith('Bearer ') ? h.slice(7).trim() : null;
}
// Auth middleware: attaches req.dbUser or 401s.
async function requireAuth(req, res, next) {
  try {
    const u = await userByToken(bearer(req));
    if (!u) return bad(res, 401, 'Not signed in.');
    if (u.banned) return bad(res, 403, 'This account is banned.');
    req.dbUser = u;
    next();
  } catch (e) { bad(res, 500, 'Auth check failed.'); }
}
// Owner gate for the admin endpoints.
function requireOwner(req, res, next) {
  if (!req.dbUser || req.dbUser.role !== 'owner')
    return bad(res, 403, 'Owner only.');
  next();
}
async function issueToken(userId, installId) {
  const token = newToken();
  await q('INSERT INTO tokens (token, user_id, install_id, created_at) VALUES (?,?,?,?)',
    [token, userId, installId || null, now()]);
  return token;
}

// ── app ─────────────────────────────────────────────────────────────────────
const app = express();
// Honor the deployment's MAX_BUNDLE_BYTES (.env) for the request body cap; default 20MB.
app.use(express.json({ limit: Number(process.env.MAX_BUNDLE_BYTES) || 20 * 1024 * 1024 }));

// Static assets (icons + default-tasks.json). Served at /assets so that, behind NPM's
// `location /ghost-bot/ { proxy_pass http://ghost-bot:3000/; }`, the client's requests to
// https://<host>/ghost-bot/assets/... land here. Mount your assets folder at /app/assets
// (compose: `- ./assets:/app/assets:ro`) or COPY it in the Dockerfile.
app.use('/assets', express.static(path.join(__dirname, 'assets'), {
  maxAge: '7d', fallthrough: true,
}));

app.get('/health', (req, res) =>
  res.json({ ok: true, service: 'dreamman', time: now() }));

// ══ VAULT (zero-knowledge auth) ══════════════════════════════════════════════

// Signup: store all opaque vault fields, create the user + first token.
app.post('/vault/register', async (req, res) => {
  try {
    if (process.env.ALLOW_REGISTRATION === 'false')
      return bad(res, 403, 'Registration is currently disabled.');
    const b = req.body || {};
    const required = ['username', 'authSalt', 'kekSalt', 'recoverySalt',
      'authHash', 'wrappedByKek', 'wrappedByRecovery'];
    for (const k of required) if (!b[k]) return bad(res, 400, 'Missing ' + k + '.');
    if (await userByName(b.username)) return bad(res, 409, 'That username is taken.');

    const ins = await q(
      'INSERT INTO users (username, email, role, created_at) VALUES (?,?,?,?)',
      [b.username, b.email || null, 'free', now()]);
    const userId = ins.insertId;
    await q(`INSERT INTO vaults
      (user_id, auth_salt, kek_salt, recovery_salt, auth_hash, wrapped_by_kek, wrapped_by_recovery, updated_at)
      VALUES (?,?,?,?,?,?,?,?)`,
      [userId, b.authSalt, b.kekSalt, b.recoverySalt, b.authHash,
        b.wrappedByKek, b.wrappedByRecovery, now()]);

    const token = await issueToken(userId, req.headers['x-install-id']);
    const u = await userByName(b.username);
    res.json({ token, user: userObject(u) });
  } catch (e) { bad(res, 500, 'Register failed.'); }
});

// Login step 1: hand back the salts so the client can derive its auth hash + KEK.
app.post('/vault/salts', async (req, res) => {
  try {
    const username = (req.body || {}).username;
    if (!username) return bad(res, 400, 'Missing username.');
    const u = await userByName(username);
    if (!u) {
      // reveal non-existence like the existing server did (exists:false) but still return
      // random-looking salts so the shape is always consistent
      return res.json({
        authSalt: crypto.randomBytes(18).toString('base64'),
        kekSalt: crypto.randomBytes(18).toString('base64'),
        recoverySalt: crypto.randomBytes(18).toString('base64'),
        exists: false,
      });
    }
    const v = (await q('SELECT * FROM vaults WHERE user_id = ?', [u.id]))[0];
    if (!v) return bad(res, 404, 'No vault for that user.');
    res.json({ authSalt: v.auth_salt, kekSalt: v.kek_salt,
      recoverySalt: v.recovery_salt, exists: true });
  } catch (e) { bad(res, 500, 'Salts lookup failed.'); }
});

// Login step 2: verify the auth hash, return the token + wrapped key + ciphertext.
app.post('/vault/login', async (req, res) => {
  try {
    const b = req.body || {};
    if (!b.username || !b.authHash) return bad(res, 400, 'Missing credentials.');
    const u = await userByName(b.username);
    if (!u) return bad(res, 404, 'Unknown user.');
    if (u.banned) return bad(res, 403, 'This account is banned.');
    const v = (await q('SELECT * FROM vaults WHERE user_id = ?', [u.id]))[0];
    if (!v) return bad(res, 404, 'No vault for that user.');

    // constant-time compare of the client-supplied auth hash against the stored one
    const a = Buffer.from(String(b.authHash));
    const stored = Buffer.from(String(v.auth_hash));
    if (a.length !== stored.length || !crypto.timingSafeEqual(a, stored))
      return bad(res, 401, 'Wrong username or password.');

    const token = await issueToken(u.id, req.headers['x-install-id']);
    res.json({
      token, user: userObject(u),
      wrappedByKek: v.wrapped_by_kek,
      ciphertext: v.ciphertext || null,
    });
  } catch (e) { bad(res, 500, 'Login failed.'); }
});

// Recovery: hand back the recovery-wrapped key so the client can rebuild the vault key.
app.post('/vault/recovery', async (req, res) => {
  try {
    const username = (req.body || {}).username;
    if (!username) return bad(res, 400, 'Missing username.');
    const u = await userByName(username);
    if (!u) return bad(res, 404, 'Unknown user.');
    const v = (await q('SELECT * FROM vaults WHERE user_id = ?', [u.id]))[0];
    if (!v) return bad(res, 404, 'No vault.');
    res.json({ recoverySalt: v.recovery_salt,
      wrappedByRecovery: v.wrapped_by_recovery, ciphertext: v.ciphertext || null });
  } catch (e) { bad(res, 500, 'Recovery lookup failed.'); }
});

// Store the encrypted account blob.
app.put('/vault/data', requireAuth, async (req, res) => {
  try {
    const ct = (req.body || {}).ciphertext;
    if (typeof ct !== 'string') return bad(res, 400, 'Missing ciphertext.');
    await q('UPDATE vaults SET ciphertext = ?, updated_at = ? WHERE user_id = ?',
      [ct, now(), req.dbUser.id]);
    res.json({ ok: true });
  } catch (e) { bad(res, 500, 'Save failed.'); }
});

// Password change: new salts/hash/wrap, same underlying vault key.
app.put('/vault/rewrap', requireAuth, async (req, res) => {
  try {
    const b = req.body || {};
    for (const k of ['authSalt', 'kekSalt', 'authHash', 'wrappedByKek'])
      if (!b[k]) return bad(res, 400, 'Missing ' + k + '.');
    await q(`UPDATE vaults SET auth_salt=?, kek_salt=?, auth_hash=?, wrapped_by_kek=?, updated_at=?
             WHERE user_id=?`,
      [b.authSalt, b.kekSalt, b.authHash, b.wrappedByKek, now(), req.dbUser.id]);
    res.json({ ok: true });
  } catch (e) { bad(res, 500, 'Rewrap failed.'); }
});

app.post('/auth/logout', requireAuth, async (req, res) => {
  try {
    await q('DELETE FROM tokens WHERE token = ?', [bearer(req)]);
    res.json({ ok: true });
  } catch (e) { bad(res, 500, 'Logout failed.'); }
});

// ══ PROFILES (cloud task-profiles, keyed by local label) ═════════════════════

app.get('/profiles', requireAuth, async (req, res) => {
  try {
    const rows = await q('SELECT label FROM profiles WHERE user_id = ? ORDER BY label',
      [req.dbUser.id]);
    res.json(rows.map(r => ({ key: r.label })));
  } catch (e) { bad(res, 500, 'Could not list profiles.'); }
});
app.put('/profiles/:label', requireAuth, async (req, res) => {
  try {
    const data = JSON.stringify(req.body || {});
    await q(`INSERT INTO profiles (user_id, label, data, updated_at) VALUES (?,?,?,?)
             ON DUPLICATE KEY UPDATE data = VALUES(data), updated_at = VALUES(updated_at)`,
      [req.dbUser.id, req.params.label, data, now()]);
    res.json({ ok: true });
  } catch (e) { bad(res, 500, 'Could not save profile.'); }
});
app.get('/profiles/:label', requireAuth, async (req, res) => {
  try {
    const rows = await q('SELECT data FROM profiles WHERE user_id = ? AND label = ?',
      [req.dbUser.id, req.params.label]);
    if (!rows[0]) return bad(res, 404, 'No such profile.');
    res.type('application/json').send(rows[0].data);
  } catch (e) { bad(res, 500, 'Could not load profile.'); }
});
app.delete('/profiles/:label', requireAuth, async (req, res) => {
  try {
    await q('DELETE FROM profiles WHERE user_id = ? AND label = ?',
      [req.dbUser.id, req.params.label]);
    res.json({ ok: true });
  } catch (e) { bad(res, 500, 'Could not delete profile.'); }
});

// ══ ACCOUNT DATA (export / delete) ═══════════════════════════════════════════

app.get('/me/data', requireAuth, async (req, res) => {
  try {
    const id = req.dbUser.id;
    const [profiles, characters, consents, ratings] = await Promise.all([
      q('SELECT label, data, updated_at FROM profiles WHERE user_id = ?', [id]),
      q('SELECT label, meta FROM characters WHERE user_id = ?', [id]),
      q('SELECT consent_key, granted FROM consents WHERE user_id = ?', [id]),
      q('SELECT script_id, stars FROM ratings WHERE user_id = ?', [id]),
    ]);
    res.json({ user: userObject(req.dbUser), profiles, characters, consents, ratings });
  } catch (e) { bad(res, 500, 'Export failed.'); }
});
app.delete('/me', requireAuth, async (req, res) => {
  try {
    await q('DELETE FROM users WHERE id = ?', [req.dbUser.id]); // FKs cascade the rest
    res.json({ ok: true });
  } catch (e) { bad(res, 500, 'Delete failed.'); }
});

// ══ MARKETPLACE ══════════════════════════════════════════════════════════════

function listingRow(s, stats) {
  let tags = [];
  try { tags = s.tags ? JSON.parse(s.tags) : []; } catch (_) { tags = []; }
  // Field names must match the client's ScriptListing exactly (avgRating, publishedAt, locked)
  // or Gson silently leaves them at defaults and the market looks broken.
  return {
    id: s.id, name: s.name, author: s.author, description: s.description || '',
    tags, vipOnly: !!s.vip_only, locked: (s.format === 'jar'),
    version: s.version, format: s.format || 'json',
    publishedAt: s.created_at,
    downloads: stats ? Number(stats.downloads) : 0,
    avgRating: stats ? Number(stats.rating) : 0,
    ratingCount: stats ? Number(stats.rating_count) : 0,
  };
}

// Browse. Optional ?format=json|jar and ?q= search. Omits the heavy `data` bundle.
app.get('/scripts', async (req, res) => {
  try {
    const where = [];
    const args = [];
    if (req.query.format) { where.push('s.format = ?'); args.push(String(req.query.format)); }
    if (req.query.q) {
      where.push('(s.name LIKE ? OR s.author LIKE ? OR s.description LIKE ? OR s.tags LIKE ?)');
      const like = '%' + req.query.q + '%';
      args.push(like, like, like, like);
    }
    const sql = 'SELECT * FROM scripts s' +
      (where.length ? ' WHERE ' + where.join(' AND ') : '') +
      ' ORDER BY s.created_at DESC LIMIT 500';
    const scripts = await q(sql, args);
    const stats = await q('SELECT * FROM script_stats');
    const byId = Object.fromEntries(stats.map(s => [s.script_id, s]));
    res.json(scripts.map(s => {
      const row = listingRow(s, byId[s.id]);
      // Include the bundle so the client can import directly from the list (its ScriptListing
      // reads l.bundle; there's no separate detail-fetch step). Parsed to an object, not a
      // string, to match the client's ScriptBundle type.
      try { row.bundle = s.data ? JSON.parse(s.data) : null; } catch (_) { row.bundle = null; }
      return row;
    }));
  } catch (e) { bad(res, 500, 'Could not list scripts.'); }
});

// One listing WITH its bundle (what import pulls in).
app.get('/scripts/:id', async (req, res) => {
  try {
    const s = (await q('SELECT * FROM scripts WHERE id = ?', [req.params.id]))[0];
    if (!s) return bad(res, 404, 'No such script.');
    const stats = (await q('SELECT * FROM script_stats WHERE script_id = ?', [s.id]))[0];
    const out = listingRow(s, stats);
    // The client's ScriptListing.bundle is a ScriptBundle OBJECT, so parse the stored string
    // back into JSON (not a raw string, which Gson can't map onto the object).
    let bundleObj = null;
    try { bundleObj = s.data ? JSON.parse(s.data) : null; } catch (_) { bundleObj = null; }
    out.bundle = bundleObj;
    out.data = s.data;   // also expose the raw form for any string consumer
    res.json(out);
  } catch (e) { bad(res, 500, 'Could not load script.'); }
});

// Publish. Author is taken from the TOKEN, never the client — names can't be spoofed.
app.post('/scripts', requireAuth, async (req, res) => {
  try {
    if (req.dbUser.can_publish === 0) return bad(res, 403, 'Publishing is disabled for your account.');
    // v1.32b: per-tier upload quota (free 5 / vip 30 / admin+owner 1000).
    const cap = limitsFor(req.dbUser.role).maxScripts;
    const mine = (await q('SELECT COUNT(*) AS n FROM scripts WHERE author_id = ?', [req.dbUser.id]))[0];
    if (Number(mine.n) >= cap)
      return bad(res, 403, 'You\'ve reached your upload limit (' + cap + '). '
        + 'Delete one, or upgrade your tier for more space.');
    const b = req.body || {};
    const data = (b.data != null ? b.data : b.bundle);
    if (!b.name || data == null) return bad(res, 400, 'Missing name or data.');
    const vipOnly = !!b.vipOnly;
    if (vipOnly && !limitsFor(req.dbUser.role).canPublishVip)
      return bad(res, 403, 'Your tier cannot publish VIP-only scripts.');
    const id = crypto.randomBytes(10).toString('hex');
    const tags = Array.isArray(b.tags) ? JSON.stringify(b.tags) : (b.tags || '[]');
    const dataStr = typeof data === 'string' ? data : JSON.stringify(data);
    // The client marks locked (.jar) listings with `locked`; older callers may send `format`.
    const format = (b.format === 'jar' || b.locked === true) ? 'jar' : 'json';
    await q(`INSERT INTO scripts
      (id, author_id, author, name, description, tags, vip_only, version, format, data, created_at)
      VALUES (?,?,?,?,?,?,?,?,?,?,?)`,
      [id, req.dbUser.id, req.dbUser.username, b.name, b.description || '', tags,
        vipOnly ? 1 : 0, Number(b.version) || 1.0, format, dataStr, now()]);
    const s = (await q('SELECT * FROM scripts WHERE id = ?', [id]))[0];
    res.json(listingRow(s, null));
  } catch (e) { bad(res, 500, 'Publish failed.'); }
});

// Delete — author, or admin/owner.
app.delete('/scripts/:id', requireAuth, async (req, res) => {
  try {
    const s = (await q('SELECT * FROM scripts WHERE id = ?', [req.params.id]))[0];
    if (!s) return bad(res, 404, 'No such script.');
    const privileged = ['admin', 'owner', 'moderator'].includes(req.dbUser.role);
    if (s.author_id !== req.dbUser.id && !privileged)
      return bad(res, 403, 'Only the author can remove this.');
    await q('DELETE FROM scripts WHERE id = ?', [req.params.id]);
    res.json({ ok: true });
  } catch (e) { bad(res, 500, 'Delete failed.'); }
});

// ── My uploads, rename, comments (v1.32b market/forum) ───────────────────────

// The signed-in user's own uploads, with stats + how much quota is left.
app.get('/me/scripts', requireAuth, async (req, res) => {
  try {
    const scripts = await q('SELECT * FROM scripts WHERE author_id = ? ORDER BY created_at DESC',
      [req.dbUser.id]);
    const stats = await q('SELECT * FROM script_stats');
    const byId = Object.fromEntries(stats.map(s => [s.script_id, s]));
    const cap = limitsFor(req.dbUser.role).maxScripts;
    res.json({
      used: scripts.length, cap, tier: req.dbUser.role,
      scripts: scripts.map(s => listingRow(s, byId[s.id])),
    });
  } catch (e) { bad(res, 500, 'Could not load your uploads.'); }
});

// Rename / update a listing's metadata (author, or admin/owner). Body: {name?, description?, tags?}.
app.put('/scripts/:id', requireAuth, async (req, res) => {
  try {
    const s = (await q('SELECT * FROM scripts WHERE id = ?', [req.params.id]))[0];
    if (!s) return bad(res, 404, 'No such script.');
    const privileged = ['admin', 'owner', 'moderator'].includes(req.dbUser.role);
    if (s.author_id !== req.dbUser.id && !privileged)
      return bad(res, 403, 'Only the author can edit this.');
    const b = req.body || {};
    const name = (typeof b.name === 'string' && b.name.trim()) ? b.name.trim() : s.name;
    const description = (typeof b.description === 'string') ? b.description : s.description;
    const tags = Array.isArray(b.tags) ? JSON.stringify(b.tags) : s.tags;
    await q('UPDATE scripts SET name = ?, description = ?, tags = ? WHERE id = ?',
      [name, description, tags, s.id]);
    const updated = (await q('SELECT * FROM scripts WHERE id = ?', [s.id]))[0];
    res.json(listingRow(updated, null));
  } catch (e) { bad(res, 500, 'Rename failed.'); }
});

// Comments on a listing (forum-style). v1.32b: PAGINATED - one page at a time keeps the
// client lightweight. ?page=0&size=20 (size capped at 50), newest first. Response:
// { comments:[{author,body,at}], page, size, total, hasMore }
app.get('/scripts/:id/comments', async (req, res) => {
  try {
    const size = Math.max(1, Math.min(50, parseInt(req.query.size, 10) || 20));
    const page = Math.max(0, parseInt(req.query.page, 10) || 0);
    const totalRow = (await q('SELECT COUNT(*) AS n FROM comments WHERE script_id = ?',
      [req.params.id]))[0];
    const total = Number(totalRow.n) || 0;
    const rows = await q(
      'SELECT author, body, at FROM comments WHERE script_id = ? ORDER BY at DESC LIMIT ? OFFSET ?',
      [req.params.id, size, page * size]);
    res.json({
      comments: rows.map(c => ({ author: c.author, body: c.body, at: c.at })),
      page, size, total,
      hasMore: (page + 1) * size < total,
    });
  } catch (e) { bad(res, 500, 'Could not load comments.'); }
});
app.post('/scripts/:id/comments', requireAuth, async (req, res) => {
  try {
    const body = ((req.body || {}).body || '').toString().trim();
    if (!body) return bad(res, 400, 'Empty comment.');
    if (body.length > 2000) return bad(res, 400, 'Comment too long (2000 max).');
    const s = (await q('SELECT id FROM scripts WHERE id = ?', [req.params.id]))[0];
    if (!s) return bad(res, 404, 'No such script.');
    await q('INSERT INTO comments (script_id, author_id, author, body, at) VALUES (?,?,?,?,?)',
      [req.params.id, req.dbUser.id, req.dbUser.username, body, now()]);
    res.json({ ok: true });
  } catch (e) { bad(res, 500, 'Comment failed.'); }
});

// Rate (1..5), one per user per script. Accept both /scripts/:id/ratings and /ratings.
async function doRate(userId, scriptId, stars, res) {
  const n = Math.max(1, Math.min(5, parseInt(stars, 10) || 0));
  await q(`INSERT INTO ratings (user_id, script_id, stars) VALUES (?,?,?)
           ON DUPLICATE KEY UPDATE stars = VALUES(stars)`, [userId, scriptId, n]);
  res.json({ ok: true });
}
app.post('/scripts/:id/ratings', requireAuth, (req, res) =>
  doRate(req.dbUser.id, req.params.id, (req.body || {}).stars, res)
    .catch(() => bad(res, 500, 'Rating failed.')));
app.post('/ratings', requireAuth, (req, res) =>
  doRate(req.dbUser.id, (req.body || {}).scriptId, (req.body || {}).stars, res)
    .catch(() => bad(res, 500, 'Rating failed.')));

// Download counter (fire-and-forget). Both path styles.
async function doDownload(scriptId, installId, res) {
  await q('INSERT INTO downloads (script_id, install_id, at) VALUES (?,?,?)',
    [scriptId, installId || null, now()]);
  res.json({ ok: true });
}
app.post('/scripts/:id/downloads', (req, res) =>
  doDownload(req.params.id, req.headers['x-install-id'], res)
    .catch(() => bad(res, 500, 'Download log failed.')));
app.post('/downloads', (req, res) =>
  doDownload((req.body || {}).scriptId, req.headers['x-install-id'], res)
    .catch(() => bad(res, 500, 'Download log failed.')));

// ══ ADMIN (owner-only) — v1.32 dev console ═══════════════════════════════════

// Search users by username/email fragment.
app.get('/admin/users', requireAuth, requireOwner, async (req, res) => {
  try {
    const like = '%' + (req.query.q || '') + '%';
    const rows = await q(
      `SELECT username, role, email, can_publish, banned FROM users
       WHERE username LIKE ? OR email LIKE ? ORDER BY username LIMIT 200`, [like, like]);
    res.json(rows.map(u => ({ username: u.username, tier: u.role,
      canPublish: u.can_publish !== 0, banned: !!u.banned })));
  } catch (e) { bad(res, 500, 'Search failed.'); }
});

// Set a user's role. Refuses to mint another owner from inside the client.
app.post('/admin/users/:username/role', requireAuth, requireOwner, async (req, res) => {
  try {
    const tier = (req.body || {}).tier;
    const allowed = ['free', 'vip', 'moderator', 'admin'];  // NOT 'owner'
    if (!allowed.includes(tier))
      return bad(res, 400, "Role must be one of: " + allowed.join(', ') + ".");
    const target = await userByName(req.params.username);
    if (!target) return bad(res, 404, 'No such user.');
    if (target.role === 'owner')
      return bad(res, 403, "Can't change another owner's role.");
    await q('UPDATE users SET role = ? WHERE id = ?', [tier, target.id]);
    res.json({ username: target.username, tier });
  } catch (e) { bad(res, 500, 'Role change failed.'); }
});

// ── start ───────────────────────────────────────────────────────────────────
const PORT = process.env.PORT || 3000;
if (require.main === module) {
  app.listen(PORT, () => console.log('[dreamman] listening on ' + PORT));
}
module.exports = app;   // for tests
