package main.tools;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Tiny text/JSON fetcher from the asset host (v1.32), companion to {@link Assets}.
 *
 * <p>Part of the SDN-compliance move: config-ish resources that used to be bundled (the default
 * task set, and anything similar later) are pulled at runtime instead. This only retrieves
 * DreamMan's own static, non-sensitive files - no game or account data passes through it - so it
 * needs no consent gate. It shares the same configurable base URL as {@link Assets}, minus the
 * {@code /icons} segment, so a path like {@code /default-tasks.json} resolves to
 * {@code <assets>/default-tasks.json}.
 */
public final class AssetsText {

    private AssetsText() {}

    private static volatile String baseUrl = "https://ghost-server.nz/ghost-bot/assets";

    private static final String UA = "DreamMan/1.32 (DreamBot script; asset loader)";

    public static void setBaseUrl(String url) {
        if (url != null && !url.trim().isEmpty()) {
            String u = url.trim();
            while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
            baseUrl = u;
        }
    }

    /**
     * Fetches {@code <base>+path} as raw bytes, or null on any failure (offline, 404, timeout).
     * Short timeouts - callers are expected to fall back to a disk cache.
     */
    public static byte[] get(String path) {
        try {
            String url = baseUrl + (path.startsWith("/") ? path : "/" + path);
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(6000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "application/json, text/plain, */*");
            if (c.getResponseCode() >= 400) return null;
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toByteArray();
            }
        } catch (Throwable t) {
            return null;
        }
    }
}
