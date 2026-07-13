package main.menu.components;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;

/**
 * An interactive 5-star rating widget (Patch B.16). Shows the average as filled/outline stars, and
 * — when interactive — lets the user click a star to rate. Hovering previews the rating.
 *
 * <p>Used two ways: read-only on each market row to show the score, and clickable to submit a
 * rating right there instead of via a separate dialog.
 */
public class StarRating extends JComponent {

    private final int starSize;
    private double displayValue;      // the average shown (0..5)
    private int myRating;             // this user's own rating (0 = none)
    private int hover = -1;           // star under the cursor when interactive
    private boolean interactive;
    private final Color gold = new Color(212, 175, 55);
    private final Color dim = new Color(90, 90, 90);
    private IntConsumer onRate;       // called with 1..5 when a star is clicked

    public StarRating(int starSize, boolean interactive) {
        this.starSize = starSize;
        this.interactive = interactive;
        setOpaque(false);
        int w = starSize * 5 + 4 * 2;
        setPreferredSize(new Dimension(w, starSize + 2));
        setMinimumSize(getPreferredSize());

        if (interactive) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseMotionListener(new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int s = starAt(e.getX());
                    if (s != hover) { hover = s; repaint(); }
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
                @Override public void mouseClicked(MouseEvent e) {
                    int s = starAt(e.getX());
                    if (s >= 1 && s <= 5 && onRate != null) onRate.accept(s);
                }
            });
        }
    }

    public void setOnRate(IntConsumer c) { this.onRate = c; }

    /** Sets the average, count-independent display value and the user's own rating. */
    public void setValues(double average, int myRating) {
        this.displayValue = Math.max(0, Math.min(5, average));
        this.myRating = myRating;
        repaint();
    }

    private int starAt(int x) {
        int gap = 2;
        for (int i = 0; i < 5; i++) {
            int sx = i * (starSize + gap);
            if (x >= sx && x < sx + starSize + gap) return i + 1;
        }
        return -1;
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int gap = 2;
        // when hovering, preview that many gold stars; else show the average (rounded for fill)
        int shown = interactive && hover > 0 ? hover : (int) Math.round(displayValue);
        for (int i = 0; i < 5; i++) {
            int sx = i * (starSize + gap);
            boolean filled = i < shown;
            Color c = filled ? gold : dim;
            // if the user has rated, tint their own stars slightly brighter when not hovering
            javax.swing.Icon star = UIIcons.star(starSize, c, filled);
            star.paintIcon(this, g2, sx, 1);
        }
        g2.dispose();
    }
}
