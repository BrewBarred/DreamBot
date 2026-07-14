package main.tools;

import com.google.gson.Gson;
import main.data.store.LocalStore;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Item / NPC images and Grand Exchange prices from the OSRS Wiki (v1.30), powering the
 * RuneLite-style Loot Tracker.
 *
 * <p><b>Off by default.</b> Everything here is a network call, and DreamMan's rule is that no
 * byte leaves the machine without an explicit opt-in - so the tracker has an "Icons &amp; prices"
 * toggle, and until it's ticked this class only ever draws local placeholders. The only data
 * sent when enabled is the looted item/NPC names being looked up (in the request URL) plus a
 * descriptive User-Agent, which the prices API requires.
 *
 * <p>Everything fetched is cached on disk under {@code <root>/cache/wiki/} (icons forever,
 * the id-mapping for 7 days, prices for 30 minutes), so a session's second look-up - and every
 * later session - costs nothing. Missing images are remembered per run and never re-requested.
 */
public final class WikiAssets {

    private WikiAssets() {}

    public enum Kind { ITEM, NPC }

    private static final String UA =
            "DreamMan-LootTracker/1.30 (DreamBot script; local single-user tool)";
    private static final String IMG_BASE = "https://oldschool.runescape.wiki/images/";
    private static final String PRICES_BASE = "https://prices.runescape.wiki/api/v1/osrs";

    private static final Gson GSON = new Gson();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "DreamMan-WikiAssets");
        t.setDaemon(true);
        return t;
    });

    /** name-key -> loaded image, or {@link #MISSING} once a fetch has failed this run. */
    private static final Map<String, Image> IMAGES = new ConcurrentHashMap<>();
    private static final Image MISSING = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    /** keys currently being fetched, so bursts of repaints queue one download, not fifty. */
    private static final Map<String, Boolean> IN_FLIGHT = new ConcurrentHashMap<>();

    // ── enable flag (persisted as a marker file, no profile-schema change) ────
    private static volatile Boolean enabled;

    private static File cacheDir() {
        File f = new File(LocalStore.getRoot(), "cache/wiki");
        f.mkdirs();
        return f;
    }

    private static File flagFile() { return new File(cacheDir(), "online.flag"); }

    /** Whether online icon/price fetching is allowed (the Loot Tracker's toggle). */
    public static boolean isEnabled() {
        Boolean e = enabled;
        if (e == null) enabled = e = flagFile().isFile();
        return e;
    }

    public static void setEnabled(boolean on) {
        enabled = on;
        try {
            if (on) Files.write(flagFile().toPath(), "on".getBytes(StandardCharsets.UTF_8));
            else Files.deleteIfExists(flagFile().toPath());
        } catch (Exception ignored) {}
    }

    // ── images ────────────────────────────────────────────────────────────────

    /**
     * An icon for the given item/NPC, immediately. If the image is cached it's returned scaled;
     * otherwise a drawn placeholder comes back and (when enabled) a background fetch starts -
     * {@code onReady} runs on the EDT once it lands so the caller can repaint.
     */
    public static Icon icon(Kind kind, String name, int size, Runnable onReady) {
        if (name == null || name.isBlank()) return placeholder("?", size);
        String key = kind + "|" + name.toLowerCase(Locale.ROOT);

        Image img = IMAGES.get(key);
        if (img != null && img != MISSING) return scaled(img, size);
        if (img == MISSING || !isEnabled()) return placeholder(name, size);

        // disk cache?
        File f = new File(cacheDir(), fileName(kind, name));
        if (f.isFile()) {
            try {
                BufferedImage read = ImageIO.read(f);
                if (read != null) {
                    IMAGES.put(key, read);
                    return scaled(read, size);
                }
            } catch (Exception ignored) {}
        }

        if (IN_FLIGHT.putIfAbsent(key, Boolean.TRUE) == null) {
            POOL.submit(() -> {
                Image fetched = fetchImage(kind, name, f);
                IMAGES.put(key, fetched == null ? MISSING : fetched);
                IN_FLIGHT.remove(key);
                if (fetched != null && onReady != null) SwingUtilities.invokeLater(onReady);
            });
        }
        return placeholder(name, size);
    }

    /** NPCs try the wiki chathead first (reads best at 40px), then the full render. */
    private static Image fetchImage(Kind kind, String name, File saveTo) {
        String base = wikiFileName(name);
        String[] candidates = kind == Kind.NPC
                ? new String[]{base + "_chathead.png", base + ".png"}
                : new String[]{base + ".png"};
        for (String c : candidates) {
            try {
                byte[] bytes = get(IMG_BASE + c, 6000);
                if (bytes == null || bytes.length == 0) continue;
                BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                if (img == null) continue;
                try { ImageIO.write(img, "png", saveTo); } catch (Exception ignored) {}
                return img;
            } catch (Exception ignored) { /* try the next candidate */ }
        }
        return null;
    }

    /** Wiki file names: spaces to underscores, first letter capitalised, rest URL-encoded. */
    private static String wikiFileName(String name) {
        String n = name.trim().replace(' ', '_');
        if (!n.isEmpty()) n = Character.toUpperCase(n.charAt(0)) + n.substring(1);
        try {
            return URLEncoder.encode(n, StandardCharsets.UTF_8.name())
                    .replace("%2F", "/").replace("+", "%20");
        } catch (Exception e) { return n; }
    }

    private static String fileName(Kind kind, String name) {
        return kind.name().toLowerCase(Locale.ROOT) + "_"
                + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_") + ".png";
    }

    private static Icon scaled(Image img, int size) {
        int w = img.getWidth(null), h = img.getHeight(null);
        if (w <= 0 || h <= 0) return placeholder("?", size);
        double s = Math.min((double) size / w, (double) size / h);
        int nw = Math.max(1, (int) Math.round(w * s)), nh = Math.max(1, (int) Math.round(h * s));
        Image scaled = img.getScaledInstance(nw, nh, Image.SCALE_SMOOTH);
        // centre inside a size x size box so grids line up
        BufferedImage box = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = box.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(scaled, (size - nw) / 2, (size - nh) / 2, null);
        g.dispose();
        return new ImageIcon(box);
    }

    /** A neat local stand-in: rounded tile with the first letter. Never touches the network. */
    public static Icon placeholder(String name, int size) {
        String letter = name == null || name.isBlank() ? "?"
                : name.trim().substring(0, 1).toUpperCase(Locale.ROOT);
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x32, 0x32, 0x32));
        g.fillRoundRect(1, 1, size - 2, size - 2, size / 4, size / 4);
        g.setColor(new Color(0x50, 0x3C, 0x14));
        g.drawRoundRect(1, 1, size - 2, size - 2, size / 4, size / 4);
        g.setColor(new Color(0xDC, 0xB4, 0x3C));
        g.setFont(new Font("Segoe UI", Font.BOLD, Math.max(10, size / 2)));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(letter, (size - fm.stringWidth(letter)) / 2,
                (size + fm.getAscent() - fm.getDescent()) / 2);
        g.dispose();
        return new ImageIcon(img);
    }

    // ── prices (GE) ───────────────────────────────────────────────────────────

    private static volatile Map<String, Integer> nameToId;      // lower-case name -> item id
    private static volatile Map<Integer, Long> idToPrice;       // item id -> mid price
    private static volatile long pricesLoadedAt = 0;
    private static volatile boolean priceLoadInFlight = false;

    /** Mid GE price for an item name, or 0 when unknown / prices disabled / still loading. */
    public static long price(String itemName) {
        if (itemName == null || !isEnabled()) return 0;
        ensurePrices(null);
        Map<String, Integer> ids = nameToId;
        Map<Integer, Long> prices = idToPrice;
        if (ids == null || prices == null) return 0;
        Integer id = ids.get(itemName.toLowerCase(Locale.ROOT));
        if (id == null) return 0;
        Long p = prices.get(id);
        return p == null ? 0 : p;
    }

    /** Kicks a background load/refresh of the mapping + latest prices when due. */
    public static void ensurePrices(Runnable onReady) {
        if (!isEnabled()) return;
        long now = System.currentTimeMillis();
        boolean fresh = nameToId != null && idToPrice != null
                && now - pricesLoadedAt < 30 * 60_000L;      // prices refresh every 30 min
        if (fresh || priceLoadInFlight) return;
        priceLoadInFlight = true;
        POOL.submit(() -> {
            try {
                loadMapping();
                loadLatest();
                pricesLoadedAt = System.currentTimeMillis();
                if (onReady != null) SwingUtilities.invokeLater(onReady);
            } catch (Throwable ignored) {
            } finally {
                priceLoadInFlight = false;
            }
        });
    }

    /** The ~4MB id-name mapping, cached on disk for 7 days. */
    @SuppressWarnings("unchecked")
    private static void loadMapping() throws Exception {
        File f = new File(cacheDir(), "mapping.json");
        String json = null;
        if (f.isFile() && System.currentTimeMillis() - f.lastModified() < 7 * 24 * 3600_000L)
            json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        if (json == null || json.isBlank()) {
            byte[] b = get(PRICES_BASE + "/mapping", 15000);
            if (b == null) return;
            json = new String(b, StandardCharsets.UTF_8);
            try { Files.write(f.toPath(), b); } catch (Exception ignored) {}
        }
        java.util.List<Map<String, Object>> list = GSON.fromJson(json, java.util.List.class);
        if (list == null) return;
        Map<String, Integer> ids = new ConcurrentHashMap<>();
        for (Map<String, Object> m : list) {
            Object name = m.get("name"), id = m.get("id");
            if (name != null && id instanceof Number)
                ids.put(String.valueOf(name).toLowerCase(Locale.ROOT), ((Number) id).intValue());
        }
        nameToId = ids;
    }

    /** The latest high/low prices; mid = (high+low)/2, falling back to whichever exists. */
    @SuppressWarnings("unchecked")
    private static void loadLatest() throws Exception {
        byte[] b = get(PRICES_BASE + "/latest", 15000);
        if (b == null) return;
        Map<String, Object> root = GSON.fromJson(new String(b, StandardCharsets.UTF_8), Map.class);
        Object dataObj = root == null ? null : root.get("data");
        if (!(dataObj instanceof Map)) return;
        Map<Integer, Long> prices = new ConcurrentHashMap<>();
        for (Map.Entry<String, Object> e : ((Map<String, Object>) dataObj).entrySet()) {
            try {
                int id = Integer.parseInt(e.getKey());
                Map<String, Object> v = (Map<String, Object>) e.getValue();
                Number high = (Number) v.get("high"), low = (Number) v.get("low");
                long p = high != null && low != null
                        ? (high.longValue() + low.longValue()) / 2
                        : high != null ? high.longValue()
                        : low != null ? low.longValue() : 0;
                if (p > 0) prices.put(id, p);
            } catch (Throwable ignored) {}
        }
        idToPrice = prices;
    }

    // ── plumbing ──────────────────────────────────────────────────────────────

    private static byte[] get(String url, int timeoutMs) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", UA);
            if (c.getResponseCode() >= 400) return null;
            try (InputStream in = c.getInputStream();
                 java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toByteArray();
            }
        } catch (Exception e) {
            return null;
        }
    }
}
