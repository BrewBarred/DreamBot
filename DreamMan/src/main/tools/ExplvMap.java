package main.tools;

import main.data.store.LocalStore;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OSRS world-map tiles from Explv's map (v1.86), powering the side-panel minimap and the full
 * world-map view. Explv serves pre-rendered map tiles straight out of a public GitHub repo
 * ({@code Explv/osrs_map_tiles}), which this class fetches on demand and caches.
 *
 * <p><b>The coordinate scheme</b> (taken from Explv's own {@code Position.js} / {@code map.js},
 * and verified against live tiles - Lumbridge lands on Lumbridge):
 * <ul>
 *   <li>At max zoom ({@value #MAX_ZOOM}) one game tile is {@value #PX_PER_GAME_TILE}px.</li>
 *   <li>{@code pxX = (gameX - 960) * 32},  {@code pxY = 364544 - (gameY - 6208) * 32} - pixel Y
 *       grows southward, so north is up.</li>
 *   <li>Each zoom step out halves the scale; map tiles are 256px; the URL's y index is
 *       TMS-flipped: {@code urlY = 2^zoom - 1 - tileY}.</li>
 * </ul>
 *
 * <p><b>Opt-in.</b> Tiles are a network fetch, and DreamMan's rule is that no byte leaves the
 * machine without explicit consent - so fetching rides the SAME online-images switch the Loot
 * Tracker uses ({@link WikiAssets#isEnabled()}). Until it's on, {@link #tile} only ever serves
 * the disk cache and the painters draw an "enable online tiles" notice instead of the world.
 * Everything fetched lands under {@code <root>/cache/map/} (SDN-compliant, KEY THINGS #4), so a
 * revisited area - this session or any later one - costs nothing. Only the handful of 256px
 * tiles covering the visible circle/viewport are ever requested.
 */
public final class ExplvMap {

    private ExplvMap() {}

    public static final int MIN_ZOOM = 4;
    public static final int MAX_ZOOM = 11;

    /** A sensible "somewhere" for map views opened with no player position: Lumbridge. */
    public static final int DEFAULT_X = 3222, DEFAULT_Y = 3218;

    private static final int TILE_PX = 256;
    private static final int RS_OFFSET_X = 960;      // Explv: 1024 - 64
    private static final int RS_OFFSET_Y = 6208;
    private static final int PX_PER_GAME_TILE = 32;  // at MAX_ZOOM
    private static final int MAP_HEIGHT_PX = 364544; // Explv's MAP_HEIGHT_MAX_ZOOM_PX

    private static final String BASE =
            "https://raw.githubusercontent.com/Explv/osrs_map_tiles/master/";
    private static final String UA =
            "DreamMan-Map/1.86 (DreamBot script; local single-user tool)";

    /** The colour the painters use where no tile exists (sea / unmapped / still loading). */
    public static final Color VOID = new Color(0x15, 0x14, 0x11);

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "DreamMan-MapTiles");
        t.setDaemon(true);
        return t;
    });

    /** "plane|zoom|x|y" -> decoded tile, or {@link #MISSING} once a fetch failed this run. */
    private static final Map<String, BufferedImage> MEM =
            java.util.Collections.synchronizedMap(new LinkedHashMap<String, BufferedImage>(96, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> e) {
                    return size() > 64;   // ~64 x 256KB decoded = a bounded ~16MB ceiling
                }
            });
    private static final BufferedImage MISSING = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    private static final Map<String, Boolean> IN_FLIGHT = new ConcurrentHashMap<>();

    private static File cacheDir() {
        File f = new File(LocalStore.getRoot(), "cache/map");
        f.mkdirs();
        return f;
    }

    // ── coordinate math ───────────────────────────────────────────────────────

    /** Pixels one game tile spans at the given zoom (32 at zoom 11, 16 at 10, ...). */
    public static double pxPerGameTile(int zoom) {
        return PX_PER_GAME_TILE / Math.pow(2, MAX_ZOOM - clampZoom(zoom));
    }

    /** Game X -> world pixel X at the given zoom. */
    public static double worldPxX(double gameX, int zoom) {
        return (gameX - RS_OFFSET_X) * pxPerGameTile(zoom);
    }

    /** Game Y -> world pixel Y at the given zoom (grows southward - north is up). */
    public static double worldPxY(double gameY, int zoom) {
        return (MAP_HEIGHT_PX - (gameY - RS_OFFSET_Y) * PX_PER_GAME_TILE)
                / Math.pow(2, MAX_ZOOM - clampZoom(zoom));
    }

    /** World pixel X -> game X (inverse of {@link #worldPxX}). */
    public static double gameXFromPx(double px, int zoom) {
        return px / pxPerGameTile(zoom) + RS_OFFSET_X;
    }

    /** World pixel Y -> game Y (inverse of {@link #worldPxY}). */
    public static double gameYFromPx(double py, int zoom) {
        return RS_OFFSET_Y + (MAP_HEIGHT_PX - py * Math.pow(2, MAX_ZOOM - clampZoom(zoom)))
                / PX_PER_GAME_TILE;
    }

    public static int clampZoom(int zoom) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
    }

    /**
     * Screen position of a game coordinate inside a {@code w x h} viewport centred on
     * ({@code centerGX}, {@code centerGY}). Pass {@code x + 0.5, y + 0.5} to hit tile centres.
     */
    public static double[] gameToScreen(double gx, double gy, double centerGX, double centerGY,
                                        int zoom, int w, int h) {
        return new double[]{
                worldPxX(gx, zoom) - worldPxX(centerGX, zoom) + w / 2.0,
                worldPxY(gy, zoom) - worldPxY(centerGY, zoom) + h / 2.0};
    }

    /** Game coordinate under a screen position - the inverse of {@link #gameToScreen}. */
    public static double[] screenToGame(double sx, double sy, double centerGX, double centerGY,
                                        int zoom, int w, int h) {
        return new double[]{
                gameXFromPx(worldPxX(centerGX, zoom) + sx - w / 2.0, zoom),
                gameYFromPx(worldPxY(centerGY, zoom) + sy - h / 2.0, zoom)};
    }

    // ── tiles ────────────────────────────────────────────────────────────────

    /**
     * The 256px map tile at (tileX, tileY) for a plane+zoom, immediately. Memory first, then the
     * disk cache; a miss starts ONE background fetch (when online images are enabled) and
     * returns null meanwhile - {@code onReady} runs on the EDT when it lands so the caller can
     * repaint. Returns null for tiles known to not exist (sea and unmapped space simply have no
     * file in Explv's repo - that's normal, not an error).
     */
    public static BufferedImage tile(int plane, int zoom, int tileX, int tileY, Runnable onReady) {
        if (tileX < 0 || tileY < 0) return null;
        int z = clampZoom(zoom);
        String key = plane + "|" + z + "|" + tileX + "|" + tileY;

        BufferedImage img = MEM.get(key);
        if (img == MISSING) return null;
        if (img != null) return img;

        // disk cache (served even while online images are off - it's local data)
        File f = new File(cacheDir(), plane + "_" + z + "_" + tileX + "_" + tileY + ".png");
        if (f.isFile()) {
            try {
                BufferedImage read = ImageIO.read(f);
                if (read != null) {
                    MEM.put(key, read);
                    return read;
                }
            } catch (Exception ignored) {}
        }

        if (!WikiAssets.isEnabled()) return null;   // no consent -> never touch the network

        if (IN_FLIGHT.putIfAbsent(key, Boolean.TRUE) == null) {
            POOL.submit(() -> {
                BufferedImage fetched = fetch(plane, z, tileX, tileY, f);
                MEM.put(key, fetched == null ? MISSING : fetched);
                IN_FLIGHT.remove(key);
                if (fetched != null && onReady != null) SwingUtilities.invokeLater(onReady);
            });
        }
        return null;
    }

    /** GETs one tile (TMS y-flip applied HERE, so callers think in plain top-down tile rows). */
    private static BufferedImage fetch(int plane, int zoom, int tileX, int tileY, File saveTo) {
        int tmsY = (1 << zoom) - 1 - tileY;
        if (tmsY < 0) return null;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(
                    BASE + plane + "/" + zoom + "/" + tileX + "/" + tmsY + ".png").openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(6000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", UA);
            if (c.getResponseCode() >= 400) return null;
            try (InputStream in = c.getInputStream();
                 java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(out.toByteArray()));
                if (img != null) {
                    try { ImageIO.write(img, "png", saveTo); } catch (Exception ignored) {}
                }
                return img;
            }
        } catch (Exception e) {
            return null;
        }
    }

    // ── painting ─────────────────────────────────────────────────────────────

    /**
     * Fills a {@code w x h} viewport with the world around ({@code centerGX}, {@code centerGY}).
     * Only the tiles intersecting the viewport are touched (typically 1-9 of them), each drawn
     * where the shared origin puts it - the 256px grid keeps neighbours seam-free regardless of
     * fractional centring. Missing tiles paint {@link #VOID}.
     */
    public static void paintWorld(Graphics2D g, int w, int h, double centerGX, double centerGY,
                                  int plane, int zoom, Runnable onTileReady) {
        int z = clampZoom(zoom);
        double leftPx = worldPxX(centerGX, z) - w / 2.0;
        double topPx  = worldPxY(centerGY, z) - h / 2.0;

        g.setColor(VOID);
        g.fillRect(0, 0, w, h);

        int tx0 = (int) Math.floor(leftPx / TILE_PX);
        int ty0 = (int) Math.floor(topPx / TILE_PX);
        int tx1 = (int) Math.floor((leftPx + w) / TILE_PX);
        int ty1 = (int) Math.floor((topPx + h) / TILE_PX);

        for (int ty = ty0; ty <= ty1; ty++)
            for (int tx = tx0; tx <= tx1; tx++) {
                BufferedImage img = tile(plane, z, tx, ty, onTileReady);
                if (img == null) continue;
                int sx = (int) Math.round(tx * (double) TILE_PX - leftPx);
                int sy = (int) Math.round(ty * (double) TILE_PX - topPx);
                g.drawImage(img, sx, sy, null);
            }
    }
}
