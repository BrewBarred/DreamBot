package main.menu.components;

import main.menu.Theme;
import main.tools.ExplvMap;
import main.tools.WikiAssets;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.util.function.Supplier;

/**
 * The side panel's minimap (v1.86; orbs + earth v1.87): a circular, zoomed-in cut of Explv's
 * map centred on the player, now dressed like the REAL in-game minimap - the four status orbs
 * (hitpoints, prayer, run energy, special attack) arc down its left side with live numbers,
 * and the world-map button is a little planet Earth tucked into the bottom-right of the rim,
 * exactly where the game puts its globe. Tiles stay fixed north-up (marked with a small N),
 * the white player dot sits dead centre, and the live coordinates below are click-to-copy.
 *
 * <p>Only the 1-4 tiles covering the circle are ever loaded (see {@link ExplvMap}), fetching is
 * gated behind the same online-images opt-in as the Loot Tracker's wiki icons, and while that's
 * off the circle itself explains and offers the switch. Scroll on the circle to nudge the zoom
 * (8-11); it starts right in at 10 (~16px per game tile).
 */
public class MiniMapPanel extends JPanel {

    /** Supplies {x, y, plane} of the local player, or null when unknown. Reads must be cheap. */
    private final Supplier<int[]> position;
    /**
     * v1.87: supplies {hpCur, hpMax, prayerCur, prayerMax, runPct, specPct}, or null when the
     * client can't be read. Injected so this component never touches the DreamBot API itself.
     */
    private final Supplier<int[]> stats;

    private final Circle circle = new Circle();
    private final Orb orbHp     = new Orb("HP",   new Color(0x58, 0xC8, 0x50));
    private final Orb orbPray   = new Orb("Pray", new Color(0x38, 0xB8, 0xC8));
    private final Orb orbRun    = new Orb("Run",  new Color(0xE8, 0xC2, 0x4A));
    private final Orb orbSpec   = new Orb("Spec", new Color(0x48, 0xC8, 0x88));
    private final JButton earth = new JButton(UIIcons.earth(26));
    private final JLabel coords = new JLabel("-", SwingConstants.LEFT);
    private final javax.swing.Timer timer = new javax.swing.Timer(600, e -> tick());
    private final javax.swing.Timer copyFlash = new javax.swing.Timer(900, e -> {
        coords.setForeground(Theme.TEXT_DIM);
        ((javax.swing.Timer) e.getSource()).stop();
    });

    /**
     * v1.89d: the compass. The real minimap rotates with the camera, so this one does too -
     * read reflectively because the camera class has moved between client builds and a
     * hard reference would refuse to compile on the ones that name it differently.
     *
     * <p>When nothing can be read the map simply stays north-up, exactly as it was in v1.86,
     * and the N marker keeps telling the truth. A rotating map that guessed its angle wrong
     * would be worse than one that never rotates.
     */
    private static java.lang.reflect.Method camYawMethod;
    private static boolean camLookupFailed;

    /** Camera yaw in degrees (0 = north, clockwise), or -1 when this build won't say. */
    static double cameraYawDegrees() {
        if (camLookupFailed) return -1;
        try {
            if (camYawMethod == null) {
                Class<?> cam = Class.forName("org.dreambot.api.methods.camera.Camera");
                for (String n : new String[]{"getYaw", "getRotation", "getAngle", "getYawAngle"}) {
                    try { camYawMethod = cam.getMethod(n); break; } catch (Throwable ignored) {}
                }
                if (camYawMethod == null) { camLookupFailed = true; return -1; }
            }
            Object v = camYawMethod.invoke(null);
            if (!(v instanceof Number)) { camLookupFailed = true; return -1; }
            double raw = ((Number) v).doubleValue();
            // OSRS reports yaw in 0..2047 units; anything already in degrees passes through.
            double deg = raw > 360 ? raw * 360.0 / 2048.0 : raw;
            return ((deg % 360) + 360) % 360;
        } catch (Throwable t) {
            camLookupFailed = true;
            return -1;
        }
    }

    private WorldMapDialog worldMap;   // lazily created, then reused (flags survive re-opens)
    private int zoom = 10;
    private int[] lastPos;             // last known {x, y, plane}, kept while the read blips
    private int[] lastStats;           // last known orb values, same idea

    /** v1.86 signature, kept for callers that only have a position. */
    public MiniMapPanel(Supplier<int[]> position) { this(position, null); }

    public MiniMapPanel(Supplier<int[]> position, Supplier<int[]> stats) {
        this.position = position;
        this.stats = stats;
        setOpaque(false);
        setLayout(new BorderLayout(0, 4));

        // ── the face: circle + orbs + earth, laid out by hand so the orbs can hug the rim ──
        earth.setToolTipText("Open the world map (Explv) - pan, zoom, flag and copy coordinates");
        earth.setContentAreaFilled(false);
        earth.setBorder(BorderFactory.createEmptyBorder());
        earth.setFocusPainted(false);
        earth.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        earth.addActionListener(e -> openWorldMap());

        JPanel face = new JPanel(null) {
            @Override public void doLayout() {
                int w = getWidth(), h = getHeight();
                int orbW = 62, orbH = 22;
                int d = Math.min(h - 4, w - orbW - 10);
                int cx = orbW + 4 + (w - orbW - 8 - d) / 2;
                int cy = (h - d) / 2;
                circle.setBounds(cx, cy, d, d);
                // the four orbs, spread down the circle's left flank like the game's
                Orb[] orbs = {orbHp, orbPray, orbRun, orbSpec};
                int span = (int) (d * 0.86);
                int top = cy + (d - span) / 2;
                int step = (span - orbH) / (orbs.length - 1);
                for (int i = 0; i < orbs.length; i++)
                    orbs[i].setBounds(2, top + i * step, orbW, orbH);
                // planet earth on the bottom-right rim, where the game keeps its world map
                int es = 32;
                earth.setBounds(cx + d - es + 4, cy + d - es + 4, es, es);
            }
        };
        face.setOpaque(false);
        face.add(earth);          // added first = painted last-to-first? (Swing paints in
        face.add(circle);         // reverse add order, so earth sits ABOVE the circle's rim)
        face.add(orbHp);
        face.add(orbPray);
        face.add(orbRun);
        face.add(orbSpec);
        add(face, BorderLayout.CENTER);

        coords.setFont(Theme.mono(12));
        coords.setForeground(Theme.TEXT_DIM);
        coords.setToolTipText("Your current tile - click to copy \"x, y, z\"");
        coords.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        coords.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                int[] p = lastPos;
                if (p == null) return;
                copyToClipboard(p[0] + ", " + p[1] + ", " + p[2]);
                coords.setForeground(Theme.ACCENT);
                copyFlash.restart();
            }
        });
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        row.setOpaque(false);
        row.add(coords);
        add(row, BorderLayout.SOUTH);

        // repaint on our own beat only while actually on screen (same pattern as the old
        // Equipment tab) - the tile cache makes each frame nearly free
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) { tick(); timer.start(); } else timer.stop();
            }
        });
    }

    private void tick() {
        int[] p = null;
        try { p = position.get(); } catch (Throwable ignored) {}
        if (p != null) lastPos = p;
        if (stats != null) {
            int[] s = null;
            try { s = stats.get(); } catch (Throwable ignored) {}
            if (s != null && s.length >= 6) lastStats = s;
        }
        int[] show = lastPos;
        coords.setText(show == null ? "-" : show[0] + ", " + show[1] + ", " + show[2]);
        int[] st = lastStats;
        orbHp.set(st == null ? -1 : st[0], st == null ? 0 : st[1]);
        orbPray.set(st == null ? -1 : st[2], st == null ? 0 : st[3]);
        orbRun.set(st == null ? -1 : st[4], 100);
        orbSpec.set(st == null ? -1 : st[5], 100);
        repaint();
    }

    private void openWorldMap() {
        if (worldMap == null) {
            Window owner = SwingUtilities.getWindowAncestor(this);
            worldMap = new WorldMapDialog(owner, position);
        }
        worldMap.open();
    }

    static void copyToClipboard(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(text), null);
        } catch (Throwable ignored) {}
    }

    /**
     * The shared online-tiles gate: true when fetching is already allowed, otherwise asks once
     * (naming exactly what would be fetched, from where) and flips the SAME switch the Loot
     * Tracker's "Icons &amp; prices" toggle drives - one consent surface for all online images.
     */
    static boolean ensureOnlineEnabled(Component parent) {
        if (WikiAssets.isEnabled()) return true;
        int ok = JOptionPane.showConfirmDialog(parent,
                "Map tiles load from Explv's OSRS map (a public GitHub repo).\n\n"
                        + "Enabling turns on ONLINE IMAGES for DreamMan - the same switch as the\n"
                        + "Loot Tracker's \"Icons & prices\" toggle - so map tiles and item icons\n"
                        + "can be fetched and cached locally. Only the map area you look at is\n"
                        + "requested, and every tile is cached on disk so it's fetched once.\n\n"
                        + "Enable online images?",
                "Enable online map tiles", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ok == JOptionPane.YES_OPTION) {
            WikiAssets.setEnabled(true);
            return true;
        }
        return false;
    }

    /**
     * One in-game-style status orb (v1.87): the number on the left, the orb on the right,
     * filling bottom-up with its colour as the fraction rises. HP shifts green → red as it
     * drops, the way the real orb does; a value of -1 draws the "can't read it" empty state.
     */
    private static final class Orb extends JComponent {
        private final String tip;
        private final Color color;
        private int value = -1, max = 0;

        Orb(String tip, Color color) {
            this.tip = tip;
            this.color = color;
            setToolTipText(tip);
        }

        void set(int value, int max) {
            this.value = value;
            this.max = Math.max(0, max);
            if (value < 0) setToolTipText(tip);
            else if (max == 100) setToolTipText(tip + ": " + value + "%");
            else setToolTipText(tip + ": " + value + " / " + max);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int h = getHeight(), w = getWidth();
            int d = h;                                 // orb diameter = row height
            int ox = w - d;

            double frac = value < 0 || max <= 0 ? 0 : Math.max(0, Math.min(1, value / (double) max));
            Color fill = color;
            if ("HP".equals(tip) && value >= 0)        // green fades to red as HP drops
                fill = new Color(
                        (int) (0x58 + (0xD8 - 0x58) * (1 - frac)),
                        (int) (0xC8 * frac + 0x40 * (1 - frac)),
                        0x46);

            Ellipse2D disc = new Ellipse2D.Float(ox, 0, d - 1, h - 1);
            g2.setColor(new Color(0x14, 0x12, 0x0E));
            g2.fill(disc);
            if (frac > 0) {
                Shape oldClip = g2.getClip();
                g2.clip(disc);
                int fillH = (int) Math.round((h - 2) * frac);
                g2.setColor(fill);
                g2.fillRect(ox, h - 1 - fillH, d, fillH);
                g2.setClip(oldClip);
            }
            g2.setStroke(new BasicStroke(1.4f));
            g2.setColor(new Color(0x18, 0x16, 0x12));
            g2.draw(disc);

            String txt = value < 0 ? "-" : String.valueOf(value);
            g2.setFont(Theme.monoBold(11));
            FontMetrics fm = g2.getFontMetrics();
            int tx = ox - 5 - fm.stringWidth(txt);
            int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
            g2.setColor(Color.BLACK);
            g2.drawString(txt, tx + 1, ty + 1);
            g2.setColor(value < 0 ? Theme.TEXT_MUTED : fill);
            g2.drawString(txt, tx, ty);
            g2.dispose();
        }
    }

    /** The circular map itself. */
    private final class Circle extends JComponent {

        Circle() {
            setToolTipText("Your surroundings (Explv's map, north up) - scroll to zoom");
            addMouseWheelListener(e -> {
                zoom = Math.max(8, Math.min(ExplvMap.MAX_ZOOM, zoom + (e.getWheelRotation() < 0 ? 1 : -1)));
                repaint();
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (!WikiAssets.isEnabled()) {
                        if (ensureOnlineEnabled(Circle.this)) repaint();
                    }
                }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int d = Math.min(getWidth(), getHeight()) - 4;
            int cx = getWidth() / 2, cy = getHeight() / 2;
            int ox = cx - d / 2, oy = cy - d / 2;
            Ellipse2D disc = new Ellipse2D.Float(ox, oy, d, d);

            int[] p = lastPos;
            Shape oldClip = g2.getClip();
            g2.clip(disc);

            if (p == null || !WikiAssets.isEnabled()) {
                g2.setColor(ExplvMap.VOID);
                g2.fill(disc);
                g2.setColor(Theme.TEXT_DIM);
                g2.setFont(Theme.font(11));
                String[] lines = p == null
                        ? new String[]{"waiting for", "position..."}
                        : new String[]{"Online map tiles", "are off.", "Click to enable", "(Explv's map)"};
                FontMetrics fm = g2.getFontMetrics();
                int ty = cy - (lines.length - 1) * fm.getHeight() / 2;
                for (String line : lines) {
                    g2.drawString(line, cx - fm.stringWidth(line) / 2, ty);
                    ty += fm.getHeight();
                }
            } else {
                // the map, centred on the middle of the player's tile
                Graphics2D world = (Graphics2D) g2.create(ox, oy, d, d);
                // v1.89d: rotate the WORLD under the player, the way the game does. -yaw
                // because turning the camera right sweeps the world left past you.
                double yaw = cameraYawDegrees();
                if (yaw >= 0)
                    world.rotate(Math.toRadians(-yaw), d / 2.0, d / 2.0);
                ExplvMap.paintWorld(world, d, d, p[0] + 0.5, p[1] + 0.5, p[2], zoom, this::repaint);
                world.dispose();

                // the player: white marker, dead centre (the map moves under it, like in game)
                g2.setColor(Color.BLACK);
                g2.fillOval(cx - 4, cy - 4, 8, 8);
                g2.setColor(Color.WHITE);
                g2.fillOval(cx - 3, cy - 3, 6, 6);
            }

            g2.setClip(oldClip);

            // the ring: dark rim + gold hairline, plus a small N (north never rotates)
            g2.setStroke(new BasicStroke(3f));
            g2.setColor(new Color(0x18, 0x16, 0x12));
            g2.draw(disc);
            g2.setStroke(new BasicStroke(1.2f));
            g2.setColor(Theme.BORDER);
            g2.draw(disc);
            // v1.89d: N follows the compass. When the camera can't be read it stays at the top
            // and the map stays unrotated, so the marker is never lying about which way is up.
            double yawN = cameraYawDegrees();
            g2.setFont(Theme.fontBold(10));
            g2.setColor(Theme.ACCENT);
            if (yawN < 0) {
                g2.drawString("N", cx - 3, oy + 12);
            } else {
                double a = Math.toRadians(-yawN - 90);      // -90: 0 deg should sit at the top
                int r = d / 2 - 9;
                int nx = cx + (int) Math.round(Math.cos(a) * r);
                int ny = cy + (int) Math.round(Math.sin(a) * r);
                g2.drawString("N", nx - 3, ny + 4);
            }

            g2.dispose();
        }
    }
}
