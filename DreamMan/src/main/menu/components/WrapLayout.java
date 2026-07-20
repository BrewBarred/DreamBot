package main.menu.components;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

/**
 * A {@link FlowLayout} that actually wraps (v1.60 - the card grid). Stock FlowLayout lays every
 * child on one endless row and reports a single-row preferred size, so inside a vertical
 * {@code JScrollPane} it never grows tall and the cards run off the right edge. This subclass
 * computes the wrapped height for the width it's really given, so the grid reflows into as many
 * rows as it needs and the scroll pane sizes to match.
 *
 * <p>The one subtlety is the chicken-and-egg between width and height: to know the height you need
 * the width, but a scroll pane asks for the preferred size before it has assigned one. We resolve
 * it by walking up to the nearest ancestor with a real width (the viewport), subtracting insets and
 * the vertical scrollbar, and wrapping to that. Classic approach, adapted here to match the app's
 * spacing and to be null-safe on the target row.
 */
public class WrapLayout extends FlowLayout {

    public WrapLayout() { super(); }

    public WrapLayout(int align) { super(align); }

    public WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension d = layoutSize(target, false);
        // let the pane shrink us horizontally without fighting the wrap
        d.width -= (getHgap() + 1);
        return d;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = resolveWidth(target);
            if (targetWidth == 0) targetWidth = Integer.MAX_VALUE;

            Insets insets = target.getInsets();
            int hgap = getHgap();
            int vgap = getVgap();
            int maxWidth = targetWidth - (insets.left + insets.right + hgap * 2);

            Dimension dim = new Dimension(0, 0);
            int rowWidth = 0, rowHeight = 0;
            boolean firstInRow = true;

            int n = target.getComponentCount();
            for (int i = 0; i < n; i++) {
                Component m = target.getComponent(i);
                if (!m.isVisible()) continue;
                Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();

                if (!firstInRow && rowWidth + hgap + d.width > maxWidth) {
                    // this component starts a new row
                    dim.width = Math.max(dim.width, rowWidth);
                    dim.height += rowHeight + vgap;
                    rowWidth = 0;
                    rowHeight = 0;
                    firstInRow = true;
                }
                if (!firstInRow) rowWidth += hgap;
                rowWidth += d.width;
                rowHeight = Math.max(rowHeight, d.height);
                firstInRow = false;
            }
            dim.width = Math.max(dim.width, rowWidth);
            dim.height += rowHeight;

            dim.width += insets.left + insets.right + hgap * 2;
            dim.height += insets.top + insets.bottom + vgap * 2;

            // a scroll pane's viewport can round our height down and re-show the scrollbar in a
            // loop; nudging the height up by one avoids the flicker.
            JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
            if (sp != null) dim.height += vgap;

            return dim;
        }
    }

    /** The width to wrap to: the nearest real ancestor width, minus a vertical scrollbar. */
    private int resolveWidth(Container target) {
        Container c = target;
        while (c.getSize().width == 0 && c.getParent() != null) c = c.getParent();

        int width = c.getSize().width;
        if (width == 0) return 0;

        // if we're inside a scroll pane, wrap to the viewport minus the vertical scrollbar so the
        // last column never hides behind it.
        JViewport vp = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, target);
        if (vp != null) {
            width = vp.getWidth();
            JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
            if (sp != null && sp.getVerticalScrollBar() != null && sp.getVerticalScrollBar().isVisible())
                width -= sp.getVerticalScrollBar().getWidth();
        }
        return Math.max(0, width);
    }
}
