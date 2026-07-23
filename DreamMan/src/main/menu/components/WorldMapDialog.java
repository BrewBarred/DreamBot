package main.menu.components;

import main.menu.Theme;
import main.tools.ExplvMap;
import main.tools.WikiAssets;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The full world map (v1.86), opened from the minimap's globe button - DreamMan's take on
 * Explv's map, fed by the same tiles. Drag to pan, scroll to zoom (around the cursor, zooms
 * 4-11), switch planes, and FLAG tiles by clicking, exactly for the "copy these coordinates"
 * workflow: copy your own position, copy the flags as a {@code new Tile(...)} path, or copy
 * their bounding box as a {@code new Area(...)} - the formats a task/script author pastes
 * straight into use. Right-click a flag to remove it; flags survive closing the window.
 *
 * <p>The dialog is a thin window shell around {@link WorldMapView} so the whole view renders
 * headless for verification (a JDialog itself can't).
 */
public class WorldMapDialog extends JDialog {

    private final WorldMapView view;

    public WorldMapDialog(Window owner, Supplier<int[]> playerPos) {
        super(owner, "World map - Explv tiles", ModalityType.MODELESS);
        this.view = new WorldMapView(playerPos);
        setDefaultCloseOperation(HIDE_ON_CLOSE);   // keep flags between opens
        setContentPane(view);
        setSize(920, 640);
        setLocationRelativeTo(owner);
    }

    /** Shows the map, re-centred on the player (or Lumbridge until a position exists). */
    public void open() {
        view.centerOnPlayer();
        setVisible(true);
        toFront();
    }
}

/**
 * The world-map view itself: toolbar, the pannable/zoomable canvas, and the status line.
 * Package-private and JDialog-free so it can be built and painted headless.
 */
class WorldMapView extends JPanel {

    private final Supplier<int[]> playerPos;

    private double centerGX = ExplvMap.DEFAULT_X + 0.5, centerGY = ExplvMap.DEFAULT_Y + 0.5;
    private int zoom = 8;
    private int plane = 0;

    /** Flagged tiles, in click order: each {x, y, plane}. */
    private final List<int[]> flags = new ArrayList<>();

    private final MapCanvas canvas = new MapCanvas();
    private final JComboBox<Integer> planeBox = new JComboBox<>(new Integer[]{0, 1, 2, 3});
    private final JLabel status = new JLabel(" ");
    private final javax.swing.Timer timer = new javax.swing.Timer(1000, e -> canvas.repaint());

    private int[] cursorTile;          // tile under the mouse, or null

    WorldMapView(Supplier<int[]> playerPos) {
        this.playerPos = playerPos;
        setLayout(new BorderLayout(0, 6));
        setBackground(Theme.BG_APP);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 8, 10));

        // ── toolbar ──
        JButton btnCenter = smallButton("Center on player", "Jump back to where you are");
        btnCenter.addActionListener(e -> { centerOnPlayer(); canvas.repaint(); });

        planeBox.setSelectedItem(0);
        planeBox.setToolTipText("Map plane (floor) - 0 is the ground level");
        planeBox.setPreferredSize(new Dimension(52, 24));
        planeBox.addActionListener(e -> {
            Integer v = (Integer) planeBox.getSelectedItem();
            plane = v == null ? 0 : v;
            canvas.repaint();
        });

        JButton btnCopyPos = smallButton("Copy position", "Copy your tile as \"x, y, z\"");
        btnCopyPos.addActionListener(e -> {
            int[] p = safePos();
            if (p == null) { flash("No position yet - log in first."); return; }
            MiniMapPanel.copyToClipboard(p[0] + ", " + p[1] + ", " + p[2]);
            flash("Copied: " + p[0] + ", " + p[1] + ", " + p[2]);
        });

        JButton btnCopyPath = smallButton("Copy path", "Copy every flag, in order, as new Tile(...) lines");
        btnCopyPath.addActionListener(e -> {
            if (flags.isEmpty()) { flash("No flags - left-click the map to place some."); return; }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < flags.size(); i++) {
                int[] f = flags.get(i);
                sb.append("new Tile(").append(f[0]).append(", ").append(f[1]).append(", ")
                        .append(f[2]).append(")");
                if (i < flags.size() - 1) sb.append(",\n");
            }
            MiniMapPanel.copyToClipboard(sb.toString());
            flash("Copied " + flags.size() + " tile(s) as a path.");
        });

        JButton btnCopyArea = smallButton("Copy area",
                "Copy the flags' bounding box as new Area(NW x, NW y, SE x, SE y, z)");
        btnCopyArea.addActionListener(e -> {
            if (flags.size() < 2) { flash("Flag at least 2 tiles to define an area."); return; }
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            for (int[] f : flags) {
                minX = Math.min(minX, f[0]); maxX = Math.max(maxX, f[0]);
                minY = Math.min(minY, f[1]); maxY = Math.max(maxY, f[1]);
            }
            // Explv's convention: north-west corner first, then south-east
            String area = "new Area(" + minX + ", " + maxY + ", " + maxX + ", " + minY + ", "
                    + flags.get(0)[2] + ")";
            MiniMapPanel.copyToClipboard(area);
            flash("Copied: " + area);
        });

        JButton btnClear = smallButton("Clear flags", "Remove every flag");
        btnClear.addActionListener(e -> { flags.clear(); canvas.repaint(); flash("Flags cleared."); });

        JLabel planeLbl = new JLabel("Plane");
        planeLbl.setForeground(Theme.TEXT_DIM);
        planeLbl.setFont(Theme.font(12));

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        bar.setOpaque(false);
        bar.add(btnCenter);
        bar.add(planeLbl);
        bar.add(planeBox);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(btnCopyPos);
        bar.add(btnCopyPath);
        bar.add(btnCopyArea);
        bar.add(btnClear);
        add(bar, BorderLayout.NORTH);

        // ── canvas ──
        canvas.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        add(canvas, BorderLayout.CENTER);

        // ── status ──
        status.setForeground(Theme.TEXT_DIM);
        status.setFont(Theme.mono(11));
        add(status, BorderLayout.SOUTH);
        updateStatus();

        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) timer.start(); else timer.stop();
            }
        });
    }

    private static JButton smallButton(String text, String tip) {
        JButton b = new Theme.ThemedButton(text);
        b.setFont(Theme.font(11));
        b.setToolTipText(tip);
        b.setBorder(BorderFactory.createEmptyBorder(3, 9, 3, 9));
        return b;
    }

    private int[] safePos() {
        try { return playerPos.get(); } catch (Throwable t) { return null; }
    }

    void centerOnPlayer() {
        int[] p = safePos();
        if (p != null) {
            centerGX = p[0] + 0.5;
            centerGY = p[1] + 0.5;
            plane = p[2];
            planeBox.setSelectedItem(Math.max(0, Math.min(3, p[2])));
        }
    }

    private void flash(String msg) {
        status.setText(msg);
        status.setForeground(Theme.ACCENT);
        javax.swing.Timer t = new javax.swing.Timer(2600, e -> updateStatus());
        t.setRepeats(false);
        t.start();
    }

    private void updateStatus() {
        int[] p = safePos();
        String cur = cursorTile == null ? "-" : cursorTile[0] + ", " + cursorTile[1];
        String me = p == null ? "-" : p[0] + ", " + p[1] + ", " + p[2];
        status.setForeground(Theme.TEXT_DIM);
        status.setText("Cursor " + cur + "   Player " + me + "   " + flags.size()
                + " flag(s)   |   left-click: flag   right-click: remove   drag: pan   scroll: zoom");
    }

    /** The map surface: paint + pan + zoom + flag interactions. */
    private final class MapCanvas extends JComponent {

        private Point pressAt;                 // where the current drag started (screen)
        private double pressCX, pressCY;       // centre at drag start (game coords)
        private boolean dragged;

        MapCanvas() {
            MouseAdapter m = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (!WikiAssets.isEnabled()) {
                        if (MiniMapPanel.ensureOnlineEnabled(MapCanvas.this)) repaint();
                        return;
                    }
                    pressAt = e.getPoint();
                    pressCX = centerGX;
                    pressCY = centerGY;
                    dragged = false;
                }

                @Override public void mouseDragged(MouseEvent e) {
                    if (pressAt == null) return;
                    double per = ExplvMap.pxPerGameTile(zoom);
                    double dx = e.getX() - pressAt.x, dy = e.getY() - pressAt.y;
                    if (Math.abs(dx) + Math.abs(dy) > 4) dragged = true;
                    centerGX = clampX(pressCX - dx / per);
                    centerGY = clampY(pressCY + dy / per);   // screen y down = game y south
                    repaint();
                }

                @Override public void mouseReleased(MouseEvent e) {
                    if (pressAt == null || dragged) { pressAt = null; return; }
                    pressAt = null;
                    double[] gTile = ExplvMap.screenToGame(e.getX(), e.getY(), centerGX, centerGY,
                            zoom, getWidth(), getHeight());
                    int tx = (int) Math.floor(gTile[0]), ty = (int) Math.floor(gTile[1]);
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        flags.add(new int[]{tx, ty, plane});
                    } else if (SwingUtilities.isRightMouseButton(e)) {
                        removeNearestFlag(e.getX(), e.getY());
                    }
                    updateStatus();
                    repaint();
                }

                @Override public void mouseMoved(MouseEvent e) {
                    double[] g = ExplvMap.screenToGame(e.getX(), e.getY(), centerGX, centerGY,
                            zoom, getWidth(), getHeight());
                    cursorTile = new int[]{(int) Math.floor(g[0]), (int) Math.floor(g[1])};
                    updateStatus();
                    repaint();
                }

                @Override public void mouseExited(MouseEvent e) {
                    cursorTile = null;
                    updateStatus();
                    repaint();
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
            addMouseWheelListener(e -> {
                // zoom around the cursor: the game coord under the mouse stays under the mouse
                double[] under = ExplvMap.screenToGame(e.getX(), e.getY(), centerGX, centerGY,
                        zoom, getWidth(), getHeight());
                int nz = ExplvMap.clampZoom(zoom + (e.getWheelRotation() < 0 ? 1 : -1));
                if (nz == zoom) return;
                zoom = nz;
                double per = ExplvMap.pxPerGameTile(zoom);
                centerGX = clampX(under[0] - (e.getX() - getWidth() / 2.0) / per);
                centerGY = clampY(under[1] + (e.getY() - getHeight() / 2.0) / per);
                repaint();
            });
        }

        private void removeNearestFlag(int sx, int sy) {
            int best = -1;
            double bestD = 16;   // screen px
            for (int i = 0; i < flags.size(); i++) {
                int[] f = flags.get(i);
                double[] s = ExplvMap.gameToScreen(f[0] + 0.5, f[1] + 0.5, centerGX, centerGY,
                        zoom, getWidth(), getHeight());
                double d = Point.distance(sx, sy, s[0], s[1]);
                if (d < bestD) { bestD = d; best = i; }
            }
            if (best >= 0) flags.remove(best);
        }

        private double clampX(double x) { return Math.max(832, Math.min(4352, x)); }
        private double clampY(double y) { return Math.max(1152, Math.min(13000, y)); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth(), h = getHeight();

            if (!WikiAssets.isEnabled()) {
                g2.setColor(ExplvMap.VOID);
                g2.fillRect(0, 0, w, h);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.TEXT_DIM);
                g2.setFont(Theme.font(13));
                String s1 = "Online map tiles are off.";
                String s2 = "Click anywhere to enable them (Explv's OSRS map).";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(s1, (w - fm.stringWidth(s1)) / 2, h / 2 - 4);
                g2.drawString(s2, (w - fm.stringWidth(s2)) / 2, h / 2 + fm.getHeight());
                g2.dispose();
                return;
            }

            ExplvMap.paintWorld(g2, w, h, centerGX, centerGY, plane, zoom, this::repaint);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double per = ExplvMap.pxPerGameTile(zoom);

            // the tile under the cursor (Explv-style highlight)
            if (cursorTile != null && per >= 4) {
                double[] tl = ExplvMap.gameToScreen(cursorTile[0], cursorTile[1] + 1,
                        centerGX, centerGY, zoom, w, h);
                g2.setColor(new Color(Theme.ACCENT.getRed(), Theme.ACCENT.getGreen(),
                        Theme.ACCENT.getBlue(), 90));
                g2.fillRect((int) tl[0], (int) tl[1], (int) Math.ceil(per), (int) Math.ceil(per));
            }

            // the flags: connecting path first, then markers with their order
            if (flags.size() > 1) {
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(Theme.ACCENT.getRed(), Theme.ACCENT.getGreen(),
                        Theme.ACCENT.getBlue(), 170));
                for (int i = 1; i < flags.size(); i++) {
                    double[] a = flagScreen(flags.get(i - 1), w, h);
                    double[] b = flagScreen(flags.get(i), w, h);
                    g2.drawLine((int) a[0], (int) a[1], (int) b[0], (int) b[1]);
                }
            }
            for (int i = 0; i < flags.size(); i++) {
                int[] f = flags.get(i);
                double[] s = flagScreen(f, w, h);
                UIIcons.flag(16, f[2] == plane ? Theme.ACCENT : Theme.TEXT_MUTED)
                        .paintIcon(this, g2, (int) s[0] - 5, (int) s[1] - 14);
                g2.setFont(Theme.fontBold(10));
                g2.setColor(Theme.TEXT);
                g2.drawString(String.valueOf(i + 1), (int) s[0] + 7, (int) s[1] - 6);
            }

            // the player
            int[] p = safePos();
            if (p != null && p[2] == plane) {
                double[] s = ExplvMap.gameToScreen(p[0] + 0.5, p[1] + 0.5, centerGX, centerGY,
                        zoom, w, h);
                g2.setColor(Color.BLACK);
                g2.fillOval((int) s[0] - 5, (int) s[1] - 5, 10, 10);
                g2.setColor(Color.WHITE);
                g2.fillOval((int) s[0] - 3, (int) s[1] - 3, 6, 6);
            }
            g2.dispose();
        }

        private double[] flagScreen(int[] f, int w, int h) {
            return ExplvMap.gameToScreen(f[0] + 0.5, f[1] + 0.5, centerGX, centerGY, zoom, w, h);
        }
    }
}
