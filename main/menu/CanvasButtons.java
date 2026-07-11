package main.menu;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * A small row of clickable buttons drawn on the game canvas (Patch A2), like the "Open settings /
 * Skip / +30m / -30m" controls. Buttons are drawn from the script's {@code onPaint(Graphics)} and
 * clicks are routed in via {@link #handleClick(MouseEvent)} from the script's
 * {@code HumanMouseListener.onMouseClicked}.
 *
 * <p>Universal across scripts: the framework wires the same buttons every time, so any script the
 * user builds gets these controls for free. Uses only {@code java.awt}, so it's client-safe.
 */
public final class CanvasButtons {

    /** One drawn button: label (recomputed every frame), hit rect, and its action. */
    private static final class Btn {
        final java.util.function.Supplier<String> label;
        final Runnable action;
        final Rectangle bounds = new Rectangle();
        Btn(java.util.function.Supplier<String> label, Runnable action) {
            this.label = label; this.action = action;
        }
    }

    private final List<Btn> buttons = new ArrayList<>();
    private volatile boolean visible = true;
    private int x = 6, y = 338; // along the top of the chatbox (bottom-left)

    public void setVisible(boolean v) { visible = v; }
    public boolean isVisible() { return visible; }
    public void setPosition(int px, int py) { x = px; y = py; }

    public void add(String label, Runnable action) { buttons.add(new Btn(() -> label, action)); }

    /** A button whose label is recomputed every frame - e.g. Pause/Resume (Patch B.2). */
    public void add(java.util.function.Supplier<String> label, Runnable action) {
        buttons.add(new Btn(label, action));
    }

    private static final Font FONT = new Font("Segoe UI", Font.BOLD, 12);
    private static final int H = 24, PADX = 11, GAP = 5;

    /**
     * Draws the buttons as a single row spanning [rowX .. rowX+rowW] at rowY, recording each
     * button's bounds for hit-testing. Used to attach the row directly under the overlay panel.
     */
    public void renderRow(Graphics g, int rowX, int rowY, int rowW) {
        if (!visible || !(g instanceof Graphics2D) || buttons.isEmpty()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(FONT);
        FontMetrics fm = g2.getFontMetrics();
        int n = buttons.size();
        int gap = GAP;
        int bw = (rowW - gap * (n - 1)) / n;
        int cx = rowX;
        for (Btn b : buttons) {
            String label = b.label.get();
            b.bounds.setBounds(cx, rowY, bw, H);
            g2.setColor(new Color(28, 28, 28, 230));
            g2.fillRoundRect(cx, rowY, bw, H, 8, 8);
            g2.setColor(Theme.ACCENT);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(cx, rowY, bw, H, 8, 8);
            g2.setColor(Theme.ACCENT);
            int tx = cx + (bw - fm.stringWidth(label)) / 2;
            int ty = rowY + (H + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(label, tx, ty);
            cx += bw + gap;
        }
        g2.dispose();
    }

    /** Draws the button row and records each button's on-screen bounds for hit-testing. */
    public void render(Graphics g) {
        if (!visible || !(g instanceof Graphics2D) || buttons.isEmpty()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(FONT);
        FontMetrics fm = g2.getFontMetrics();

        int cx = x;
        for (Btn b : buttons) {
            String label = b.label.get();
            int w = fm.stringWidth(label) + PADX * 2;
            b.bounds.setBounds(cx, y, w, H);

            g2.setColor(new Color(28, 28, 28, 230));
            g2.fillRoundRect(cx, y, w, H, 8, 8);
            g2.setColor(Theme.ACCENT);           // gold border
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(cx, y, w, H, 8, 8);

            g2.setColor(Theme.ACCENT);            // gold label text
            int tx = cx + (w - fm.stringWidth(label)) / 2;
            int ty = y + (H + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(label, tx, ty);

            cx += w + GAP;                        // lay out left-to-right
        }
        g2.dispose();
    }

    /**
     * If the click lands on a button, run its action and return true (so the script can consume it).
     */
    public boolean handleClick(MouseEvent e) {
        if (!visible || e == null) return false;
        for (Btn b : buttons) {
            if (b.bounds.contains(e.getPoint())) {
                try { b.action.run(); } catch (Throwable ignored) {}
                return true;
            }
        }
        return false;
    }
}
