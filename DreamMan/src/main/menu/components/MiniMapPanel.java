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
 * The side panel's minimap (v1.86): a circular, zoomed-in cut of Explv's OSRS map centred on
 * the player, mimicking the in-game minimap (minus rotation - the tiles are fixed north-up, so
 * north is simply always up, marked with a small N). Below the circle sit the world-map GLOBE
 * button - opening the full pan/zoom/flag map view - and the live coordinates, click-to-copy.
 *
 * <p>Only the 1-4 tiles covering the circle are ever loaded (see {@link ExplvMap}), fetching is
 * gated behind the same online-images opt-in as the Loot Tracker's wiki icons, and while that's
 * off the circle itself explains and offers the switch. Scroll on the circle to nudge the zoom
 * (8-11); it starts right in at 10 (~16px per game tile).
 */
public class MiniMapPanel extends JPanel {

    /** Supplies {x, y, plane} of the local player, or null when unknown. Reads must be cheap. */
    private final Supplier<int[]> position;

    private final Circle circle = new Circle();
    private final JLabel coords = new JLabel("-", SwingConstants.LEFT);
    private final javax.swing.Timer timer = new javax.swing.Timer(600, e -> tick());
    private final javax.swing.Timer copyFlash = new javax.swing.Timer(900, e -> {
        coords.setForeground(Theme.TEXT_DIM);
        ((javax.swing.Timer) e.getSource()).stop();
    });

    private WorldMapDialog worldMap;   // lazily created, then reused (flags survive re-opens)
    private int zoom = 10;
    private int[] lastPos;             // last known {x, y, plane}, kept while the read blips

    public MiniMapPanel(Supplier<int[]> position) {
        this.position = position;
        try { lastPos = position.get(); } catch (Throwable ignored) {}   // paint frame 1, not tick 1
        setOpaque(false);
        setLayout(new BorderLayout(0, 4));

        circle.setPreferredSize(new Dimension(170, 170));
        add(circle, BorderLayout.CENTER);

        JButton globe = new Theme.ThemedButton();
        globe.setIcon(UIIcons.globe(16, Theme.ACCENT));
        globe.setToolTipText("Open the world map (Explv) - pan, zoom, flag and copy coordinates");
        globe.setPreferredSize(new Dimension(30, 26));
        globe.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        globe.addActionListener(e -> openWorldMap());

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

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.add(globe, BorderLayout.WEST);
        row.add(coords, BorderLayout.CENTER);
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
        int[] show = lastPos;
        coords.setText(show == null ? "-" : show[0] + ", " + show[1] + ", " + show[2]);
        circle.repaint();
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
            g2.setFont(Theme.fontBold(10));
            g2.setColor(Theme.ACCENT);
            g2.drawString("N", cx - 3, oy + 12);

            g2.dispose();
        }
    }
}
