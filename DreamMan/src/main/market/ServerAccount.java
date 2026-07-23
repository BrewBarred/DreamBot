package main.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import main.data.store.LocalStore;
import main.privacy.Consent;
import org.dreambot.api.utilities.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Your DreamMan server account (Patch B.12): one login that manages all your game accounts.
 *
 * <p>The design that matters: <b>the server never learns your OSRS character names.</b> DreamMan
 * maps each character to a local label ({@link main.privacy.CharacterMap}) and syncs profiles under
 * the LABEL. So one account holds "Main", "Alt 1", "Ironman" - and even a fully breached server
 * gives an attacker no game accounts to report or ban. Given that the whole point of these accounts
 * is doing something Jagex forbids, that seemed worth engineering rather than hand-waving.
 *
 * <p>Every call here is gated on {@link Consent}. Log in, and DreamMan still sends nothing until
 * you've agreed to the specific thing it's about to do.
 *
 * <p>The token is stored in {@code <home>/DreamMan/session.json}, readable only by you (the same
 * place your profiles already live). Your password is never stored - only the token the server
 * gives back, which you can revoke by logging out.
 */
public class ServerAccount {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final String base;

    /** What we keep between sessions. Never the password. */
    public static class Session {
        public String baseUrl = "";
        public String token = "";
        public String username = "";
        /** The label of the character we're currently syncing. */
        public String activeCharacter = "";
        public boolean autoSync = false;
        // Patch B.14: the tier the SERVER assigned, cached from the login response. The client
        // reflects these in its UI, but the server is what actually enforces them - a tampered
        // client that sets tier="vip" here still can't get VIP content or extra loops, because
        // those are gated server-side.
        public String tier = "guest";       // guest | free | vip | subscriber | lifetime | admin | moderator
        public int maxLoops = 50;
        public int maxExtraAccounts = 2;
        public boolean canPublishVip = false;
        // ── v1.87 ranks ──
        /** The orthogonal SCRIPTER mark (unlimited market uploads). Server-sent. */
        public boolean scripter = false;
        /** Lifetime donations in cents (0 until the donation system exists). Server-sent. */
        public long donatedCents = 0;
        /** Server override for the equipment-preset cap (0 = use the tier default). */
        public int presetLimit = 0;
    }

    private static volatile Session session;

    public ServerAccount(String baseUrl) {
        String b = baseUrl == null ? "" : baseUrl.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        this.base = b;
    }

    // ── the stored session ──

    private static File file() { return new File(LocalStore.getRoot(), "session.json"); }

    public static synchronized Session session() {
        if (session != null) return session;
        try {
            File f = file();
            if (f.isFile()) {
                Session s = GSON.fromJson(
                        new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8), Session.class);
                if (s != null) return session = s;
            }
        } catch (Throwable ignored) {}
        return session = new Session();
    }

    private static synchronized void saveSession() {
        try {
            File f = file();
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), GSON.toJson(session()).getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            Logger.log(Logger.LogType.WARN, "[Account] Could not save the session: " + t);
        }
    }

    public static boolean isLoggedIn() {
        Session s = session();
        return s.token != null && !s.token.isEmpty();
    }

    public static String username() { return session().username; }

    /** Forgets the token locally AND revokes it on the server, so it can't be reused. */
    public void logout() {
        Session s = session();
        try {
            if (isLoggedIn()) request("POST", "/auth/logout", "", s.token);
        } catch (Throwable ignored) {}
        s.token = "";
        s.username = "";
        // v1.32b: the tier must reset too - it's a property of the signed-in account, and
        // leaving it as "owner" after logout kept owner UI (dev console, admin buttons) alive
        // for whoever used the client next.
        s.tier = "guest";
        s.maxLoops = 50;           // back to the guest/free default, not zero
        s.canPublishVip = false;
        s.scripter = false;        // v1.87: the marks are account properties too
        s.donatedCents = 0;
        s.presetLimit = 0;
        saveSession();
        Logger.log("[Account] Logged out (the token was revoked on the server too).");
    }

    // ── auth ──

    /** @return the username on success. Throws with a readable reason on failure. */
    public String login(String username, String password) throws Exception {
        // Logging in is itself a network call, so it needs consent for SOMETHING network-ish.
        if (!Consent.anyNetworkConsent())
            throw new IOException("You haven't agreed to DreamMan talking to a server yet. "
                    + "Nothing was sent. Open Settings \u2192 Privacy first.");

        String body = GSON.toJson(Map.of("username", username, "password", password));
        String json = request("POST", "/auth/login", body, null);
        return adoptToken(json, username);
    }

    public String register(String username, String password, String email) throws Exception {
        if (!Consent.anyNetworkConsent())
            throw new IOException("You haven't agreed to DreamMan talking to a server yet. "
                    + "Nothing was sent. Open Settings \u2192 Privacy first.");

        Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put("username", username);
        m.put("password", password);
        if (email != null && !email.trim().isEmpty()) m.put("email", email.trim());
        String json = request("POST", "/auth/register", GSON.toJson(m), null);
        return adoptToken(json, username);
    }

    @SuppressWarnings("unchecked")
    private String adoptToken(String json, String username) throws IOException {
        Map<String, Object> r = GSON.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
        Object token = r == null ? null : r.get("token");
        if (!(token instanceof String) || ((String) token).isEmpty())
            throw new IOException("The server didn't return a token.");
        Session s = session();
        s.baseUrl = base;
        s.token = (String) token;
        s.username = username;
        adoptTier(r);   // Patch B.14: cache the tier + limits the server assigned
        saveSession();
        Logger.log("[Account] Signed in as " + username);
        return username;
    }

    /** Reads tier + limits out of an auth response's "user" object into the session (B.14). */
    @SuppressWarnings("unchecked")
    private void adoptTier(Map<String, Object> resp) {
        try {
            Object uo = resp.get("user");
            if (!(uo instanceof Map)) return;
            Map<String, Object> u = (Map<String, Object>) uo;
            Session s = session();
            Object tier = u.get("tier");
            if (tier instanceof String) s.tier = (String) tier;
            Object lim = u.get("limits");
            if (lim instanceof Map) {
                Map<String, Object> l = (Map<String, Object>) lim;
                if (l.get("maxLoops") instanceof Number) s.maxLoops = ((Number) l.get("maxLoops")).intValue();
                if (l.get("maxExtraAccounts") instanceof Number) s.maxExtraAccounts = ((Number) l.get("maxExtraAccounts")).intValue();
                if (l.get("canPublishVip") instanceof Boolean) s.canPublishVip = (Boolean) l.get("canPublishVip");
            }
            if (u.get("canPublishVip") instanceof Boolean) s.canPublishVip = (Boolean) u.get("canPublishVip");
            // ── v1.87: the orthogonal marks + preset override, wherever the server puts them ──
            if (u.get("scripter") instanceof Boolean) s.scripter = (Boolean) u.get("scripter");
            if (u.get("donatedCents") instanceof Number)
                s.donatedCents = ((Number) u.get("donatedCents")).longValue();
            else if (u.get("donated") instanceof Number)   // dollars, if the server prefers those
                s.donatedCents = Math.round(((Number) u.get("donated")).doubleValue() * 100);
            if (lim instanceof Map) {
                Map<String, Object> l2 = (Map<String, Object>) lim;
                if (l2.get("presetLimit") instanceof Number)
                    s.presetLimit = ((Number) l2.get("presetLimit")).intValue();
            }
        } catch (Throwable ignored) {}
    }

    // ═══ Patch B.15: zero-knowledge vault flow ═══

    /** Registers a vault account. All crypto is done by the caller; we send opaque strings. */
    @SuppressWarnings("unchecked")
    public String vaultRegister(String username, Map<String, String> vaultFields) throws Exception {
        if (!Consent.anyNetworkConsent())
            throw new IOException("Agree to DreamMan talking to a server first (Settings \u2192 Privacy).");
        Map<String, Object> body = new java.util.LinkedHashMap<>(vaultFields);
        body.put("username", username);
        String json = request("POST", "/vault/register", GSON.toJson(body), null);
        return adoptToken(json, username);
    }

    /** Login step 1: fetch the salts so the caller can derive keys locally. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> vaultSalts(String username) throws Exception {
        String json = request("POST", "/vault/salts",
                GSON.toJson(Map.of("username", username)), null);
        return GSON.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
    }

    /** Login step 2: send the derived auth hash; get back token + wrapped key + ciphertext. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> vaultLogin(String username, String authHash) throws Exception {
        if (!Consent.anyNetworkConsent())
            throw new IOException("Agree to DreamMan talking to a server first (Settings \u2192 Privacy).");
        Map<String, Object> body = Map.of("username", username, "authHash", authHash);
        String json = request("POST", "/vault/login", GSON.toJson(body), null);
        Map<String, Object> r = GSON.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
        // adopt token + tier so the session is live
        Object token = r.get("token");
        if (token instanceof String && !((String) token).isEmpty()) {
            Session s = session();
            s.baseUrl = base; s.token = (String) token; s.username = username;
            adoptTier(r);
            saveSession();
        }
        return r;
    }

    /** Recovery: fetch the recovery-wrapped key + ciphertext (caller unwraps with the code). */
    @SuppressWarnings("unchecked")
    public Map<String, Object> vaultRecovery(String username) throws Exception {
        String json = request("POST", "/vault/recovery",
                GSON.toJson(Map.of("username", username)), null);
        return GSON.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
    }

    /** Pushes the encrypted account blob to the server (called by AccountVault.sync). */
    public void putVaultData(String ciphertext) throws Exception {
        if (!Consent.has(Consent.CLOUD_SYNC))
            throw new IOException("Cloud sync isn't enabled.");
        request("PUT", "/vault/data", GSON.toJson(Map.of("ciphertext", ciphertext)), session().token);
    }

    /** Re-wraps the vault key under a new password (caller derived the new salts/hash/wrap). */
    public void rewrap(Map<String, String> fields) throws Exception {
        request("PUT", "/vault/rewrap", GSON.toJson(new java.util.LinkedHashMap<>(fields)), session().token);
    }

    // ── v1.63: PUBLIC scripter profiles (a different thing from the character-settings sync
    // below: these are the market-facing "who is this scripter" pages) ──

    /**
     * Fetches a scripter's public profile as raw JSON. No auth - profiles are public, so this
     * works logged out too. The caller parses defensively (the exact field set is the server's).
     */
    public String fetchUserProfile(String name) throws Exception {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("No name");
        return request("GET", "/users/" + enc(name.trim()) + "/profile", null, null);
    }

    /** Updates MY public profile's bio (auth'd - PUT /me/profile). */
    public void updateMyProfile(String bio) throws Exception {
        if (!isLoggedIn()) throw new IllegalStateException("Log in first");
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("bio", bio == null ? "" : bio);
        request("PUT", "/me/profile", GSON.toJson(body), session().token);
    }

    // ── cloud profiles, keyed by LOCAL LABEL (never the OSRS name) ──

    /** The character labels this account has profiles for. */
    @SuppressWarnings("unchecked")
    public List<String> remoteCharacters() throws Exception {
        requireCloud();
        String json = request("GET", "/profiles", null, session().token);
        List<Map<String, Object>> rows = GSON.fromJson(json,
                new TypeToken<List<Map<String, Object>>>() {}.getType());
        List<String> out = new ArrayList<>();
        if (rows != null)
            for (Map<String, Object> r : rows) {
                Object k = r.get("key");
                if (k instanceof String) out.add((String) k);
            }
        return out;
    }

    /** Uploads a profile under a character LABEL. */
    public void saveProfile(String label, String profileJson) throws Exception {
        requireCloud();
        if (label == null || label.trim().isEmpty()) throw new IOException("No character selected.");
        assertNoSecrets(profileJson);
        request("PUT", "/profiles/" + enc(label), profileJson, session().token);
    }

    /** Downloads a profile by character LABEL. */
    public String loadProfile(String label) throws Exception {
        requireCloud();
        if (label == null || label.trim().isEmpty()) throw new IOException("No character selected.");
        return request("GET", "/profiles/" + enc(label), null, session().token);
    }

    public void deleteProfile(String label) throws Exception {
        requireCloud();
        request("DELETE", "/profiles/" + enc(label), null, session().token);
    }

    /** Everything the server holds about you, as JSON (data portability). */
    public String exportMyData() throws Exception {
        requireLoggedIn();
        return request("GET", "/me/data", null, session().token);
    }

    /** Erases your account and everything attached to it, permanently. */
    public void deleteEverything() throws Exception {
        requireLoggedIn();
        request("DELETE", "/me", null, session().token);
        Session s = session();
        s.token = "";
        s.username = "";
        saveSession();
        Logger.log("[Account] Account and all server data deleted.");
    }

    // ═══ v1.32: owner-only admin API ═══
    // These call owner-gated endpoints. The client shows them only to owners, but the SERVER is
    // the real gate: every /admin/* route must verify the caller's token belongs to an owner and
    // reject everyone else with 403, exactly like the tier limits. The client's checks are UI.

    /**
     * Searches registered users by username/email fragment. @return a list of rows, each a map
     * with at least {@code username} and {@code tier} (the server decides the full shape).
     *
     * <p>Endpoint: {@code GET /admin/users?q=<fragment>} \u2192 {@code [{username, tier, ...}]}.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> adminSearchUsers(String query) throws Exception {
        requireLoggedIn();
        String json = request("GET", "/admin/users?q=" + enc(query == null ? "" : query),
                null, session().token);
        List<Map<String, Object>> rows = GSON.fromJson(json,
                new TypeToken<List<Map<String, Object>>>() {}.getType());
        return rows == null ? new ArrayList<>() : rows;
    }

    /**
     * Sets a user's tier (the promote/demote action). @return the server's response.
     *
     * <p>Endpoint: {@code POST /admin/users/<username>/role} with body {@code {"tier":"admin"}}.
     * The server must refuse to let anyone create another owner through this route.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> adminSetRole(String username, String tier) throws Exception {
        requireLoggedIn();
        if (username == null || username.trim().isEmpty()) throw new IOException("No user given.");
        String body = GSON.toJson(Map.of("tier", tier));
        String json = request("POST", "/admin/users/" + enc(username) + "/role", body, session().token);
        return GSON.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
    }

    /**
     * v1.87: grants or removes the SCRIPTER mark (unlimited market uploads). Owner-gated on the
     * server. A server without the endpoint answers 404 and the console says so honestly.
     *
     * <p>Endpoint: {@code POST /admin/users/<username>/scripter} with body {@code {"on":true}}.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> adminSetScripter(String username, boolean on) throws Exception {
        requireLoggedIn();
        if (username == null || username.trim().isEmpty()) throw new IOException("No user given.");
        String json = request("POST", "/admin/users/" + enc(username) + "/scripter",
                GSON.toJson(Map.of("on", on)), session().token);
        return GSON.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
    }

    /**
     * v1.87: records a user's lifetime donation total, in cents - the manual ledger until a
     * real payment system exists. 404 on older servers; the console explains.
     *
     * <p>Endpoint: {@code POST /admin/users/<username>/donated} with body {@code {"cents":N}}.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> adminSetDonated(String username, long cents) throws Exception {
        requireLoggedIn();
        if (username == null || username.trim().isEmpty()) throw new IOException("No user given.");
        String json = request("POST", "/admin/users/" + enc(username) + "/donated",
                GSON.toJson(Map.of("cents", cents)), session().token);
        return GSON.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
    }

    /**
     * v1.87: publishes the donor-rank ladder ({@code [{minCents,name},...]}) so every client
     * shows the same names. 404 on older servers; DonorRanks keeps the local copy either way.
     *
     * <p>Endpoint: {@code PUT /admin/donor-ranks}.
     */
    public void putDonorRanks(String json) throws Exception {
        requireLoggedIn();
        request("PUT", "/admin/donor-ranks", json, session().token);
    }

    /** v1.87: fetches the shared donor-rank ladder (any signed-in user). 404 = not published. */
    public String getDonorRanks() throws Exception {
        return request("GET", "/donor-ranks", null, session().token);
    }

    // ── v1.66: moderation, submissions queue, and the server-side defaults library ──
    // All of these are stored on the SERVER, so every admin sees the same state from any
    // machine (the old defaults flow lived in a file on one admin's disk).

    /** {@code GET /settings} - the moderation valve + current defaults version. Public. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchSettings() throws Exception {
        String json = request("GET", "/settings", null, session().token);
        Map<String, Object> m = GSON.fromJson(json,
                new TypeToken<Map<String, Object>>() {}.getType());
        return m == null ? new HashMap<>() : m;
    }

    /** {@code PUT /admin/settings} - turn publish moderation on or off. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> adminSetModeration(boolean on) throws Exception {
        requireLoggedIn();
        Map<String, Object> body = new HashMap<>();
        body.put("submissionsModerated", on);
        String json = request("PUT", "/admin/settings", GSON.toJson(body), session().token);
        return GSON.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
    }

    /** {@code GET /admin/submissions?status=pending|archived}. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> adminListSubmissions(String status) throws Exception {
        requireLoggedIn();
        String json = request("GET", "/admin/submissions?status="
                + enc(status == null ? "pending" : status), null, session().token);
        List<Map<String, Object>> rows = GSON.fromJson(json,
                new TypeToken<List<Map<String, Object>>>() {}.getType());
        return rows == null ? new ArrayList<>() : rows;
    }

    /** Approve a queued submission - it becomes a normal market listing. */
    public void adminApproveSubmission(String id) throws Exception {
        requireLoggedIn();
        request("POST", "/admin/submissions/" + enc(id) + "/approve", "{}", session().token);
    }

    /** Deny a queued submission - archived with the reason, never published. */
    public void adminDenySubmission(String id, String reason) throws Exception {
        requireLoggedIn();
        Map<String, Object> body = new HashMap<>();
        body.put("reason", reason == null ? "" : reason);
        request("POST", "/admin/submissions/" + enc(id) + "/deny", GSON.toJson(body),
                session().token);
    }

    /** Put an archived submission back in the queue. */
    public void adminRestoreSubmission(String id) throws Exception {
        requireLoggedIn();
        request("POST", "/admin/submissions/" + enc(id) + "/restore", "{}", session().token);
    }

    /** {@code GET /admin/defaults} - the defaults library with provenance. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> adminListDefaults() throws Exception {
        requireLoggedIn();
        String json = request("GET", "/admin/defaults", null, session().token);
        List<Map<String, Object>> rows = GSON.fromJson(json,
                new TypeToken<List<Map<String, Object>>>() {}.getType());
        return rows == null ? new ArrayList<>() : rows;
    }

    /**
     * Pushes a task into the defaults library, so it lands in every user's task library.
     * {@code taskData} is a {@code main.data.store.TaskData} (serialised here, so this class
     * stays free of a compile-time dependency on the store package).
     */
    public void adminAddDefault(Object taskData, String source) throws Exception {
        requireLoggedIn();
        Map<String, Object> body = new HashMap<>();
        body.put("task", taskData);
        body.put("source", "market".equals(source) ? "market" : "library");
        request("POST", "/admin/defaults", GSON.toJson(body), session().token);
    }

    /** Removes a task from the defaults library (existing copies in user libraries stay). */
    public void adminRemoveDefault(String taskId) throws Exception {
        requireLoggedIn();
        request("DELETE", "/admin/defaults/" + enc(taskId), null, session().token);
    }

    /** {@code GET /defaults} - raw JSON body, so callers can map it to their own types. */
    public String fetchDefaultsJson() throws Exception {
        return request("GET", "/defaults", null, session().token);
    }

    /** {@code GET /defaults/version} - the cheap poll target. -1 when unreachable. */
    public int fetchDefaultsVersion() {
        try {
            String json = request("GET", "/defaults/version", null, session().token);
            Map<?, ?> m = GSON.fromJson(json, Map.class);
            Object v = m == null ? null : m.get("version");
            return v == null ? -1 : (int) Math.round(Double.parseDouble(String.valueOf(v)));
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * v1.67: the signed-in user's own submissions. Without this the moderation valve is a black
     * hole from the author's side - their listing just never appears and nothing says why.
     * Rows carry {@code status} (pending|approved|archived), {@code reason} and {@code seen}.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> mySubmissions() throws Exception {
        requireLoggedIn();
        String json = request("GET", "/me/submissions", null, session().token);
        List<Map<String, Object>> rows = GSON.fromJson(json,
                new TypeToken<List<Map<String, Object>>>() {}.getType());
        return rows == null ? new ArrayList<>() : rows;
    }

    /** Marks one outcome as shown, so it isn't reported again on the next start. */
    public void ackSubmission(String id) throws Exception {
        requireLoggedIn();
        request("POST", "/me/submissions/" + enc(id) + "/ack", "{}", session().token);
    }

    /**
     * v1.82: a raw, authenticated probe for the Dev Console's Connection tab. Deliberately thin -
     * it returns the body and lets exceptions through, because the diagnostic value is in WHICH
     * status came back, not in a tidied result.
     */
    public String probe(String method, String path) throws Exception {
        return request(method == null ? "GET" : method, path, null, session().token);
    }

    /** v1.84: an admin's view of one user - moderation state, market counts, complexity. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> adminUserProfile(String username) throws Exception {
        requireLoggedIn();
        String json = request("GET", "/admin/users/" + enc(username) + "/profile", null,
                session().token);
        Map<String, Object> m = GSON.fromJson(json,
                new TypeToken<Map<String, Object>>() {}.getType());
        return m == null ? new HashMap<>() : m;
    }

    /**
     * v1.85: stores a PRIVATE copy of a listing on the user's profile. Sends the whole listing,
     * because the server has no other way to know what it's keeping - an id alone would refer to
     * a local file it has never seen.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> vaultScript(main.market.ScriptListing l) throws Exception {
        requireLoggedIn();
        if (l == null || l.bundle == null) throw new IOException("Nothing to vault.");
        Map<String, Object> body = new HashMap<>();
        body.put("name", l.name);
        body.put("kind", l.kind);
        body.put("version", l.version);
        body.put("data", l.bundle);
        String json = request("POST", "/vault/scripts", GSON.toJson(body), session().token);
        Map<String, Object> m = GSON.fromJson(json,
                new TypeToken<Map<String, Object>>() {}.getType());
        return m == null ? new HashMap<>() : m;
    }

    // ── guards ──

    private void requireCloud() throws IOException {
        if (!Consent.has(Consent.CLOUD_SYNC))
            throw new IOException("You haven't agreed to cloud sync. Nothing was sent.");
        requireLoggedIn();
    }

    private void requireLoggedIn() throws IOException {
        if (!isLoggedIn()) throw new IOException("Log in to your DreamMan account first.");
    }

    /**
     * A last-line check that we never upload something we promised not to. The bank PIN is held in
     * memory and isn't part of a profile at all - but promises in comments don't hold, so this
     * actually looks before sending, and refuses rather than trusting.
     */
    private void assertNoSecrets(String profileJson) throws IOException {
        if (profileJson == null) return;
        String lower = profileJson.toLowerCase();
        if (lower.contains("\"bankpin\"") || lower.contains("\"pin\"") || lower.contains("password"))
            throw new IOException("Refusing to upload: that profile looks like it contains a "
                    + "secret (a PIN or password). This is a bug - please report it.");
    }

    // ── plumbing ──

    private String request(String method, String path, String body, String token) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Accept", "application/json");
        // v1.31: a descriptive UA - Java's default ("Java/21") is exactly the fingerprint
        // Cloudflare's bot rules love to challenge, and a challenge page breaks the client.
        c.setRequestProperty("User-Agent", "DreamMan/1.59 (DreamBot script)");
        c.setRequestProperty("X-Install-Id", MarketIdentity.installId());
        if (token != null && !token.isEmpty())
            c.setRequestProperty("Authorization", "Bearer " + token);

        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }

        int code = c.getResponseCode();
        String text = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code >= 400) {
            String msg = text;
            try {
                Map<?, ?> err = GSON.fromJson(text, Map.class);
                if (err != null && err.get("error") != null) msg = String.valueOf(err.get("error"));
            } catch (Throwable ignored) {}
            // v1.30: a TYPED error carrying the HTTP status, so the login UI can tell
            // "wrong password" (401/403/404) apart from "server broke" and say the right thing.
            throw new HttpError(code, msg.isEmpty() ? ("Server said " + code) : msg);
        }
        return text;
    }

    /** An HTTP-level failure with its status code (v1.30). */
    public static final class HttpError extends IOException {
        public final int status;
        public HttpError(int status, String message) {
            super(message);
            this.status = status;
        }
        /** 401/403/404 on the auth endpoints all mean "we don't know you / wrong secret". */
        public boolean looksLikeBadCredentials() {
            return status == 401 || status == 403 || status == 404;
        }
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return bos.toString(StandardCharsets.UTF_8.name());
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8.name()); }
        catch (Exception e) { return ""; }
    }
}
