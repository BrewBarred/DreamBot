package main.tools;

import main.data.store.LocalStore;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runtime asset loader (v1.32) - the SDN-compliance replacement for bundled icon resources.
 *
 * <p><b>Why this exists.</b> The DreamBot Scripter Guidelines forbid shipping resources as source
 * files inside an SDN script: "Resources can't be included in SDN scripts as source files. You
 * will need to load those from the internet at runtime instead." So DreamMan no longer bakes its
 * PNGs into the jar. Icons are fetched from the asset host on first use, cached on disk under the
 * sanctioned {@code scripts.path}, and - crucially - every icon has a <b>drawn vector fallback</b>
 * so the UI is fully functional the instant it opens and stays functional with no network at all.
 *
 * <p><b>How it behaves.</b> {@link #icon} returns immediately, every time:
 * <ul>
 *   <li>cached in memory → the real icon;</li>
 *   <li>on disk (a previous session fetched it) → loaded and returned;</li>
 *   <li>otherwise → a tidy drawn placeholder <i>now</i>, while a background fetch runs; when it
 *       lands, {@code onReady} fires so the caller can repaint with the real thing.</li>
 * </ul>
 * A failed fetch is remembered so it isn't retried every repaint, and the placeholder simply
 * stays - a missing icon is never a broken window.
 *
 * <p>No game or account data is involved here - only static UI icon names - so this needs no
 * consent gate. The base URL is configurable so the asset host can move without a code change.
 */
public final class Assets {

    private Assets() {}

    /**
     * Where icons are fetched from. Layout mirrors the old bundled tree, so a category+name maps
     * to {@code <base>/icons/<category>/<name>.png}. Point this at wherever you host the assets
     * (e.g. your ghost-server static path). Overridable at runtime via {@link #setBaseUrl}.
     */
    private static volatile String baseUrl = "https://ghost-server.nz/ghost-bot/assets";

    private static final String UA = "DreamMan/1.32 (DreamBot script; asset loader)";

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "DreamMan-Assets");
        t.setDaemon(true);
        return t;
    });

    /** cacheKey -> scaled icon, or {@link #MISSING} once a fetch has failed this run. */
    private static final Map<String, ImageIcon> MEM = new ConcurrentHashMap<>();
    private static final ImageIcon MISSING = new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
    private static final Map<String, Boolean> IN_FLIGHT = new ConcurrentHashMap<>();

    public static void setBaseUrl(String url) {
        if (url != null && !url.trim().isEmpty()) {
            String u = url.trim();
            while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
            baseUrl = u;
        }
    }

    private static File cacheDir() {
        File f = new File(LocalStore.getRoot(), "cache/assets");
        f.mkdirs();
        return f;
    }

    /**
     * An icon for {@code category/name} at {@code size}px, immediately. Fetches + caches in the
     * background when needed; {@code onReady} runs on completion so the caller can repaint.
     * {@code fallbackLabel} seeds the drawn placeholder (a letter/glyph) shown until then.
     */
    public static ImageIcon icon(String category, String name, int size,
                                 String fallbackLabel, Runnable onReady) {
        String key = category + "/" + name + "@" + size;
        ImageIcon cached = MEM.get(key);
        if (cached != null && cached != MISSING) return cached;
        if (cached == MISSING) return placeholder(fallbackLabel, size);

        // disk cache from a previous session?
        File f = new File(cacheDir(), safe(category) + "_" + safe(name) + ".png");
        if (f.isFile()) {
            try {
                BufferedImage img = ImageIO.read(f);
                if (img != null) {
                    ImageIcon scaled = scale(img, size);
                    MEM.put(key, scaled);
                    return scaled;
                }
            } catch (Throwable ignored) {}
        }

        if (IN_FLIGHT.putIfAbsent(key, Boolean.TRUE) == null) {
            POOL.submit(() -> {
                BufferedImage img = fetch(category, name, f);
                if (img == null) {
                    MEM.put(key, MISSING);
                } else {
                    MEM.put(key, scale(img, size));
                    if (onReady != null) javax.swing.SwingUtilities.invokeLater(onReady);
                }
                IN_FLIGHT.remove(key);
            });
        }
        return placeholder(fallbackLabel, size);
    }

    /** Convenience: no fallback label (uses a neutral dot placeholder). */
    public static ImageIcon icon(String category, String name, int size, Runnable onReady) {
        return icon(category, name, size, name, onReady);
    }

    private static BufferedImage fetch(String category, String name, File saveTo) {
        try {
            String url = baseUrl + "/icons/" + category + "/" + name + ".png";
            byte[] bytes = get(url);
            if (bytes == null || bytes.length == 0) return null;
            BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if (img == null) return null;
            try { ImageIO.write(img, "png", saveTo); } catch (Throwable ignored) {}
            return img;
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] get(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(6000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", UA);
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

    private static ImageIcon scale(BufferedImage img, int size) {
        return new ImageIcon(img.getScaledInstance(size, size, Image.SCALE_SMOOTH));
    }

    /**
     * A neat drawn stand-in - rounded tile with a letter - so the UI is complete before (and
     * without) any download. Never touches the network.
     */
    public static ImageIcon placeholder(String label, int size) {
        String letter = label == null || label.isBlank()
                ? "\u2022" : label.trim().substring(0, 1).toUpperCase(Locale.ROOT);
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x35, 0x32, 0x2A));
        g.fillRoundRect(0, 0, size - 1, size - 1, size / 4, size / 4);
        g.setColor(new Color(0x50, 0x48, 0x30));
        g.drawRoundRect(0, 0, size - 1, size - 1, size / 4, size / 4);
        g.setColor(new Color(0xC9, 0xA7, 0x4B));
        g.setFont(new Font("Segoe UI", Font.BOLD, Math.max(9, size * 5 / 9)));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(letter, (size - fm.stringWidth(letter)) / 2,
                (size + fm.getAscent() - fm.getDescent()) / 2 - 1);
        g.dispose();
        return new ImageIcon(img);
    }

    private static String safe(String s) {
        return s == null ? "x" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
    }
}
