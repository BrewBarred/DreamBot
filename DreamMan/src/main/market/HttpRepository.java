package main.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.dreambot.api.utilities.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A market backed by a REST server (Patch B.11) - for when there's a real one to point at.
 *
 * <p>The {@link FolderRepository} already gives a group of friends a working market with no server
 * at all. This class is the path to a public one: stand up something that speaks the contract
 * below, put its URL in the Market tab, and the same UI becomes a real community market with
 * proper moderation and auth.
 *
 * <h3>The contract</h3>
 * <pre>
 *   GET    {base}/scripts                 -> 200, JSON array of ScriptListing
 *                                            (each with avgRating, ratingCount, downloads filled in;
 *                                             "myRating" may be filled from the X-Install-Id header)
 *   POST   {base}/scripts                 body: one ScriptListing as JSON
 *                                         -> 200/201. Server assigns "id" if absent and should
 *                                            reject a listing whose author doesn't match the caller.
 *   POST   {base}/scripts/{id}/ratings    body: {"stars":1..5}
 *                                         -> 200. One rating per install id (upsert, not append).
 *   POST   {base}/scripts/{id}/downloads  -> 200. Idempotent per install id.
 *   DELETE {base}/scripts/{id}            -> 200/204. Only the owner may delete.
 * </pre>
 *
 * Every request carries {@code X-Install-Id} (the anonymous per-install id) and, when set, an
 * {@code Authorization: Bearer &lt;token&gt;} header, so a real server can authenticate publishers
 * and rate-limit voting rather than trusting the client.
 *
 * <p><b>Honest status:</b> this client is written and tested against a mock server that implements
 * the contract above. There is no hosted DreamMan market yet - so until someone runs one, the
 * folder repository is the one that actually does something.
 */
public class HttpRepository implements ScriptRepository {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Type LIST_TYPE = new TypeToken<List<ScriptListing>>() {}.getType();

    private final String base;
    private final String token;   // optional
    private final int timeoutMs;

    public HttpRepository(String baseUrl, String token) {
        this(baseUrl, token, 8000);
    }

    public HttpRepository(String baseUrl, String token, int timeoutMs) {
        String b = baseUrl == null ? "" : baseUrl.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        this.base = b;
        this.token = token == null ? "" : token.trim();
        this.timeoutMs = timeoutMs;
    }

    @Override
    public String describe() { return "Server: " + base; }

    /** The server base URL (Patch B.15) - used by the login row + account sync. */
    public String baseUrl() { return base; }

    @Override
    public List<ScriptListing> list() {
        try {
            requireConsent(main.privacy.Consent.MARKET_BROWSE);
            String json = request("GET", "/scripts", null);
            List<ScriptListing> out = GSON.fromJson(json, LIST_TYPE);
            return out == null ? new ArrayList<>() : out;
        } catch (Throwable t) {
            Logger.log(Logger.LogType.WARN, "[Market] Could not reach " + base + ": " + t.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void publish(ScriptListing listing) throws Exception {
        requireConsent(main.privacy.Consent.MARKET_PUBLISH);
        if (listing == null || listing.bundle == null) throw new IOException("Nothing to publish.");
        request("POST", "/scripts", GSON.toJson(listing));
    }

    @Override
    public void rate(String scriptId, int stars) throws Exception {
        requireConsent(main.privacy.Consent.MARKET_BROWSE);
        if (stars < 1 || stars > 5) throw new IOException("Rating must be 1-5 stars.");
        request("POST", "/scripts/" + enc(scriptId) + "/ratings", "{\"stars\":" + stars + "}");
    }

    /** v1.59: toggles a favorite; returns the raw JSON ({myFavorite, favorites}). */
    public String favorite(String scriptId) throws Exception {
        requireConsent(main.privacy.Consent.MARKET_BROWSE);
        return request("POST", "/scripts/" + enc(scriptId) + "/favorites", "{}");
    }

    @Override
    public void noteDownload(String scriptId) {
        try {
            requireConsent(main.privacy.Consent.MARKET_BROWSE);
            request("POST", "/scripts/" + enc(scriptId) + "/downloads", "");
        } catch (Throwable ignored) { /* a missed count never fails a download */ }
    }

    @Override
    public void remove(String scriptId) throws Exception {
        requireConsent(main.privacy.Consent.MARKET_PUBLISH);
        request("DELETE", "/scripts/" + enc(scriptId), null);
    }

    // ── v1.32b: forum/my-uploads extras (server only) ──

    /** Your own uploads plus how much of your quota is used. */
    public static final class MyUploads {
        public int used;
        public int cap;
        public String tier = "free";
        public List<ScriptListing> scripts = new ArrayList<>();
        /** v1.33: per-kind usage - {task:{used,cap}, script:{used,cap}}. */
        public Usage usage;
    }

    /** v1.33: per-kind quota usage. */
    public static final class Usage {
        public KindUsage task;
        public KindUsage script;
    }
    public static final class KindUsage {
        public int used;
        public int cap;
    }

    /** One forum comment on a listing. */
    public static final class Comment {
        public String author = "";
        public String body = "";
        public long at;
    }

    /** GET /me/scripts — the signed-in user's uploads + quota. */
    public MyUploads myScripts() throws Exception {
        requireConsent(main.privacy.Consent.MARKET_BROWSE);
        String json = request("GET", "/me/scripts", null);
        MyUploads m = GSON.fromJson(json, MyUploads.class);
        return m == null ? new MyUploads() : m;
    }

    /** PUT /scripts/{id} — rename (author/admin only, enforced server-side). */
    public void rename(String scriptId, String newName) throws Exception {
        requireConsent(main.privacy.Consent.MARKET_PUBLISH);
        request("PUT", "/scripts/" + enc(scriptId),
                "{\"name\":" + GSON.toJson(newName) + "}");
    }

    /** One PAGE of comments (v1.32b - kept light: the client never loads a whole thread). */
    public static final class CommentPage {
        public List<Comment> comments = new ArrayList<>();
        public int page;
        public int size = 20;
        public int total;
        public boolean hasMore;
    }

    /**
     * GET /scripts/{id}/comments?page=&size= - one page, newest first. Tolerates an older
     * server that still returns a bare array (treated as a single page).
     */
    public CommentPage comments(String scriptId, int page, int size) throws Exception {
        requireConsent(main.privacy.Consent.MARKET_BROWSE);
        String json = request("GET", "/scripts/" + enc(scriptId)
                + "/comments?page=" + Math.max(0, page) + "&size=" + Math.max(1, size), null);
        String trimmed = json == null ? "" : json.trim();
        if (trimmed.startsWith("[")) {                       // pre-pagination server
            CommentPage p = new CommentPage();
            List<Comment> all = GSON.fromJson(trimmed, new TypeToken<List<Comment>>() {}.getType());
            p.comments = all == null ? new ArrayList<>() : all;
            p.total = p.comments.size();
            return p;
        }
        CommentPage p = GSON.fromJson(trimmed, CommentPage.class);
        if (p == null) p = new CommentPage();
        if (p.comments == null) p.comments = new ArrayList<>();
        return p;
    }

    /** POST /scripts/{id}/comments. */
    public void addComment(String scriptId, String body) throws Exception {
        requireConsent(main.privacy.Consent.MARKET_BROWSE);
        request("POST", "/scripts/" + enc(scriptId) + "/comments",
                "{\"body\":" + GSON.toJson(body) + "}");
    }

    // ── plumbing ──

    /**
     * Patch B.12: the consent gate. Every single outbound call passes through here, and if the
     * person hasn't explicitly agreed to this purpose the request is <b>never made</b> - we don't
     * send it and ignore the reply, we don't queue it, we don't "just check". No consent, no
     * packet. This is the one place that has to be right, so it's the one place it's enforced.
     */
    private void requireConsent(String purpose) throws IOException {
        if (!main.privacy.Consent.has(purpose))
            throw new IOException("You haven't agreed to this yet ("
                    + purpose + "). Nothing was sent. Open Settings \u2192 Privacy to decide.");
    }

    private String request(String method, String path, String body) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("X-Install-Id", MarketIdentity.installId());
        if (!token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);

        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = c.getOutputStream()) { os.write(bytes); }
        }

        int code = c.getResponseCode();
        String text = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code >= 400)
            throw new IOException("Server said " + code
                    + (text.isEmpty() ? "" : ": " + text.substring(0, Math.min(200, text.length()))));
        return text;
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
