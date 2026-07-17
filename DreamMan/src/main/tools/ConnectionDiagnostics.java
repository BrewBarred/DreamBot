package main.tools;

import main.market.MarketIdentity;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Connection diagnostics (v1.32) - turns "it doesn't work" into an exact, actionable reason.
 *
 * <p>Logging in or registering fails for a dozen different reasons that all look identical to a
 * user (a 502, a Cloudflare challenge, a dead DNS record, a TLS mismatch). This probes the
 * configured server the same way the client does - a real {@code POST /vault/salts} - and reads
 * the response critically enough to say <i>which</i> layer is broken and <i>what to change</i>.
 *
 * <p>It's built for the exact problems this deployment hit: an nginx reverse proxy in front of a
 * Node backend, behind a reverse proxy. So it specifically recognises:
 * <ul>
 *   <li><b>502/504</b> - nginx reached but the Node upstream is down/misconfigured (the current
 *       login failure);</li>
 *   <li><b>Cloudflare challenge HTML</b> - the WAF is interrupting the client, which has no
 *       browser to solve it;</li>
 *   <li>DNS / connection-refused / TLS / timeout - the transport never completed;</li>
 *   <li>a clean JSON error - the backend is alive and talking (login logic problem, not infra).</li>
 * </ul>
 */
public final class ConnectionDiagnostics {

    private ConnectionDiagnostics() {}

    public enum Status { OK, REACHABLE_ERROR, CLOUDFLARE_CHALLENGE, BAD_GATEWAY,
        DNS, REFUSED, TIMEOUT, TLS, PROTOCOL, UNKNOWN }

    public static final class Result {
        public final Status status;
        public final int httpCode;        // 0 when no HTTP response was received
        public final long millis;         // round-trip time
        public final String headline;     // one-line summary
        public final String detail;       // what's happening + what to do (multi-line)
        public final String server;       // the "Server:" response header, when present

        Result(Status status, int httpCode, long millis, String headline, String detail, String server) {
            this.status = status;
            this.httpCode = httpCode;
            this.millis = millis;
            this.headline = headline;
            this.detail = detail;
            this.server = server == null ? "" : server;
        }

        public boolean ok() { return status == Status.OK; }
    }

    /**
     * Probes {@code baseUrl} with the same call the login flow makes first: {@code POST
     * /vault/salts} for a throwaway username. A 4xx here is actually GOOD news - it means the
     * backend received the request and answered, so the transport + proxy + backend are all alive.
     */
    public static Result probe(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.isEmpty())
            return new Result(Status.UNKNOWN, 0, 0, "No server URL set",
                    "There's no server address configured to test.", null);

        String url = base + "/vault/salts";
        long start = System.currentTimeMillis();
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "DreamMan/1.32 (DreamBot script)");
            c.setRequestProperty("X-Install-Id", safeInstallId());
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (java.io.OutputStream os = c.getOutputStream()) {
                os.write("{\"username\":\"__connectiontest__\"}".getBytes(StandardCharsets.UTF_8));
            }

            int code = c.getResponseCode();
            long ms = System.currentTimeMillis() - start;
            String server = c.getHeaderField("Server");
            String cfRay = c.getHeaderField("CF-RAY");
            String body = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
            String lowerBody = body == null ? "" : body.toLowerCase();

            // ── Cloudflare challenge? (the client can't solve it) ──
            boolean looksLikeChallenge =
                    (code == 403 || code == 503 || code == 429)
                            && (lowerBody.contains("just a moment")
                            || lowerBody.contains("cf-challenge")
                            || lowerBody.contains("challenge-platform")
                            || lowerBody.contains("checking if the site connection is secure")
                            || lowerBody.contains("cf-browser-verification"));
            if (looksLikeChallenge)
                return new Result(Status.CLOUDFLARE_CHALLENGE, code, ms,
                        "Cloudflare is challenging the client",
                        "Cloudflare returned a browser challenge page (HTTP " + code
                                + (cfRay != null ? ", CF-RAY " + cfRay : "") + "). The DreamBot "
                                + "client isn't a browser and can't solve it, so every request "
                                + "dies here.\n\nFix: in Cloudflare, add a WAF rule that SKIPS "
                                + "managed challenges / Bot Fight Mode for this path - e.g. "
                                + "(http.request.uri.path starts_with \"/ghost-bot/\") \u2192 Skip. "
                                + "Free-plan Bot Fight Mode can't be path-exempted, so turn it off "
                                + "globally if that's what's firing.", server);

            // ── 502/504: nginx up, Node upstream down (the login 502) ──
            if (code == 502 || code == 504)
                return new Result(Status.BAD_GATEWAY, code, ms,
                        "Reverse proxy can't reach the backend (HTTP " + code + ")",
                        "The proxy answered but couldn't get a reply from the Node backend "
                                + (server != null ? "(Server: " + server + "). " : ". ")
                                + "This is exactly the login/register 502.\n\nUsual causes: the "
                                + "backend (ghost-bot) container isn't running, the nginx "
                                + "'proxy_pass' points at the wrong host:port, or the backend "
                                + "crashed on this route. Check the backend container is up and "
                                + "that /ghost-bot/ proxies to it; then hit "
                                + "/ghost-bot/vault/salts directly on the server to confirm the backend "
                                + "responds.", server);

            // ── any other 4xx/5xx WITH a JSON error = backend is alive and talking ──
            boolean jsonError = lowerBody.contains("\"error\"") || lowerBody.trim().startsWith("{");
            if (code >= 200 && code < 500 && (jsonError || code == 400 || code == 404 || code == 200))
                return new Result(code < 400 ? Status.OK : Status.REACHABLE_ERROR, code, ms,
                        code < 400 ? "Server reachable and responding"
                                : "Server reachable (HTTP " + code + ")",
                        "The backend received the request and answered in " + ms + " ms"
                                + (server != null ? " (Server: " + server + ")" : "") + ". "
                                + (code < 400
                                ? "Transport, proxy and backend are all working."
                                : "That means the infrastructure is fine - a " + code + " here is "
                                + "the backend's own response to the test username, so any "
                                + "login problem is application logic, not the connection.")
                                + "\n\nFirst 200 chars of the reply:\n"
                                + snippet(body), server);

            // ── other 5xx ──
            return new Result(Status.REACHABLE_ERROR, code, ms,
                    "Server error (HTTP " + code + ")",
                    "The server answered with HTTP " + code
                            + (server != null ? " (Server: " + server + ")" : "")
                            + " in " + ms + " ms. The request reached something, but the backend "
                            + "returned an error rather than JSON.\n\nFirst 200 chars:\n"
                            + snippet(body), server);

        } catch (java.net.UnknownHostException e) {
            return new Result(Status.DNS, 0, System.currentTimeMillis() - start,
                    "DNS lookup failed",
                    "The hostname in the URL couldn't be resolved. Check the domain is spelled "
                            + "correctly and has a DNS record (and, on Cloudflare, that the record "
                            + "is present - proxied or not).", null);
        } catch (java.net.ConnectException e) {
            return new Result(Status.REFUSED, 0, System.currentTimeMillis() - start,
                    "Connection refused",
                    "The host resolved but nothing accepted the connection on that port. The web "
                            + "server (nginx) may be down, or the URL's port is wrong.", null);
        } catch (java.net.SocketTimeoutException e) {
            return new Result(Status.TIMEOUT, 0, System.currentTimeMillis() - start,
                    "Connection timed out",
                    "The server didn't respond within 8 seconds. It may be overloaded, firewalled, "
                            + "or a proxy is stalling. Cloudflare 522/524s also show up as timeouts.", null);
        } catch (javax.net.ssl.SSLException e) {
            return new Result(Status.TLS, 0, System.currentTimeMillis() - start,
                    "TLS/SSL handshake failed",
                    "Couldn't establish a secure connection: " + e.getMessage() + ".\n\nUsually a "
                            + "certificate problem - an expired/mismatched cert, or Cloudflare SSL "
                            + "mode set to 'Flexible' while the origin expects HTTPS. Use 'Full "
                            + "(strict)' with a valid origin certificate.", null);
        } catch (java.net.ProtocolException e) {
            return new Result(Status.PROTOCOL, 0, System.currentTimeMillis() - start,
                    "Protocol error", e.getMessage(), null);
        } catch (Throwable t) {
            return new Result(Status.UNKNOWN, 0, System.currentTimeMillis() - start,
                    "Couldn't reach the server",
                    "Unexpected failure: " + t.getClass().getSimpleName()
                            + (t.getMessage() != null ? " - " + t.getMessage() : ""), null);
        } finally {
            if (c != null) try { c.disconnect(); } catch (Throwable ignored) {}
        }
    }

    private static String safeInstallId() {
        try { return MarketIdentity.installId(); } catch (Throwable t) { return "test"; }
    }

    private static String read(InputStream in) {
        if (in == null) return "";
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n, total = 0;
            while ((n = in.read(buf)) > 0 && total < 65536) { bos.write(buf, 0, n); total += n; }
            return bos.toString(StandardCharsets.UTF_8.name());
        } catch (Throwable t) {
            return "";
        }
    }

    private static String snippet(String body) {
        if (body == null) return "(empty)";
        String s = body.strip().replaceAll("\\s+", " ");
        if (s.isEmpty()) return "(empty)";
        return s.length() <= 200 ? s : s.substring(0, 200) + "\u2026";
    }
}
