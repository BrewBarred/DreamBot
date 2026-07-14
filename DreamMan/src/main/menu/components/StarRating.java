package main.menu.components;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;

/**
 * An interactive 5-star rating widget (Patch B.16, fixed in B.17). Stars are drawn as vector
 * shapes ({@link UIIcons#star}) - never font glyphs, so they can't degrade into "missing glyph"
 * rectangles on systems whose UI font lacks \u2605/\u2606.
 *
 * <p>Used two ways: standalone (it handles its own mouse), or embedded in a list-cell renderer,
 * where the OWNER drives it - {@link #starIndexAt(int)} for hit-testing and
 * {@link #setHoverPreview(int)} for the hover fill - because a renderer is a rubber stamp and
 * receives no events of its own.
 */
public class StarRating extends JComponent {

    /** Gap between stars, px. Kept in one place so painting and hit-testing can never drift. */
    public static final int GAP = 2;

    private final int starSize;
    private double displayValue;      // the average shown (0..5)
    private int myRating;             // this user's own rating (0 = none)
    private int hover = -1;           // star under the cursor (own mouse OR external preview)
    private final boolean interactive;
    private final Color gold = new Color(220, 180, 60);   // Theme.ACCENT
    private final Color dim = new Color(110, 102, 88);
    private IntConsumer onRate;       // called with 1..5 when a star is clicked

    public StarRating(int starSize, boolean interactive) {
        this.starSize = starSize;
        this.interactive = interactive;
        setOpaque(false);
        int w = starSize * 5 + GAP * 4;
        setPreferredSize(new Dimension(w, starSize + 2));
        setMinimumSize(getPreferredSize());
        setMaximumSize(getPreferredSize());

        if (interactive) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseMotionListener(new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int s = starIndexAt(e.getX());
                    if (s != hover) { hover = s; repaint(); }
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
                @Override public void mouseClicked(MouseEvent e) {
                    int s = starIndexAt(e.getX());
                    if (s >= 1 && s <= 5 && onRate != null) onRate.accept(s);
                }
            });
        }
    }

    public void setOnRate(IntConsumer c) { this.onRate = c; }

    /** Sets the average shown and the user's own rating. */
    public void setValues(double average, int myRating) {
        this.displayValue = Math.max(0, Math.min(5, average));
        this.myRating = myRating;
        repaint();
    }

    /**
     * Externally drives the hover preview (1..5, or <=0 to clear) - used when this widget lives
     * inside a list-cell renderer and the JList forwards its own mouse position.
     */
    public void setHoverPreview(int star) {
        this.hover = (star >= 1 && star <= 5) ? star : -1;
    }

    /**
     * @return the 1-based star at local x, or -1. The maths mirrors {@link #paintComponent}
     * exactly (same size, same gap), so a click can never land "between" the shape and the zone.
     */
    public int starIndexAt(int x) {
        if (x < 0) return -1;
        int cell = starSize + GAP;
        int i = x / cell;                       // which cell the point falls into
        if (i < 0 || i > 4) return -1;
        return i + 1;                           // the whole cell counts as that star
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // when hovering, preview that many gold stars; else show the average (rounded for fill)
        int shown = hover > 0 ? hover : (int) Math.round(displayValue);
        boolean previewing = hover > 0;
        for (int i = 0; i < 5; i++) {
            int sx = i * (starSize + GAP);
            boolean filled = i < shown;
            Color c = filled ? (previewing ? new Color(240, 200, 80) : gold) : dim;
            Icon star = UIIcons.star(starSize, c, filled);
            star.paintIcon(this, g2, sx, 1);
        }
        g2.dispose();
    }
}
