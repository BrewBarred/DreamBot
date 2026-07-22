package main.menu.components;

import main.menu.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * v1.75 (item 14): the market's tag filter, as clickable pills under the search field.
 *
 * <p>Tags used to live as checkboxes inside the filter popup, which meant you had to know the
 * feature existed, open a menu, and tick things you couldn't see the effect of. Here the whole
 * vocabulary is visible and one click filters. The list grows on its own: it's rebuilt from
 * whatever tags the loaded listings actually carry, so a newly published tag simply appears.
 *
 * <p>It collapses, because a market with fifty tags is a wall of noise for someone who doesn't
 * use them. The header stays visible so the feature is still discoverable when collapsed, and
 * the count tells you whether anything is filtered even while it's hidden.
 */
public final class TagFilterBar extends JPanel {

    /** Wraps children onto as many rows as needed - {@link FlowLayout} reports one row and clips. */
    private static final class WrapLayout extends FlowLayout {
        WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

        @Override public Dimension preferredLayoutSize(Container target) { return layoutSize(target); }
        @Override public Dimension minimumLayoutSize(Container target) {
            Dimension d = layoutSize(target);
            d.width -= (getHgap() + 1);
            return d;
        }

        private Dimension layoutSize(Container target) {
            synchronized (target.getTreeLock()) {
                int maxW = target.getWidth();
                if (maxW == 0) maxW = Integer.MAX_VALUE;
                Insets in = target.getInsets();
                int available = maxW - (in.left + in.right + getHgap() * 2);
                int rowW = 0, rowH = 0, totalW = 0, totalH = in.top + in.bottom + getVgap() * 2;
                for (Component c : target.getComponents()) {
                    if (!c.isVisible()) continue;
                    Dimension d = c.getPreferredSize();
                    if (rowW + d.width > available && rowW > 0) {
                        totalW = Math.max(totalW, rowW);
                        totalH += rowH + getVgap();
                        rowW = 0; rowH = 0;
                    }
                    rowW += d.width + getHgap();
                    rowH = Math.max(rowH, d.height);
                }
                totalW = Math.max(totalW, rowW);
                totalH += rowH;
                return new Dimension(totalW + in.left + in.right + getHgap() * 2, totalH);
            }
        }
    }

    private final JPanel pills = new JPanel(new WrapLayout(FlowLayout.LEFT, 5, 5));
    private final JLabel header = new JLabel();
    private final JLabel caret = new JLabel();
    private final Set<String> selected;
    private final Consumer<String> onToggle;
    private final Runnable onClear;
    private boolean collapsed = false;
    private int tagCount = 0;

    /**
     * @param selected the live filter set (read for pill state, never written here)
     * @param onToggle called with the tag that was clicked; the owner mutates and re-filters
     * @param onClear  called when the "clear" pill is clicked
     */
    public TagFilterBar(Set<String> selected, Consumer<String> onToggle, Runnable onClear) {
        this.selected = selected;
        this.onToggle = onToggle;
        this.onClear = onClear;

        setOpaque(false);
        setLayout(new BorderLayout(0, 4));
        // v1.78: more air above - the pills sat hard against the search field and the
        // whole header read as squashed.
        setBorder(new EmptyBorder(10, 2, 2, 2));

        header.setIcon(UIIcons.tag(12, Theme.TEXT_DIM));   // v1.78: a real tag glyph
        header.setIconTextGap(5);
        header.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        header.setForeground(Theme.TEXT_DIM);
        caret.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        caret.setForeground(Theme.TEXT_DIM);

        JPanel headRow = new JPanel(new BorderLayout(6, 0));
        headRow.setOpaque(false);
        headRow.add(header, BorderLayout.WEST);
        headRow.add(caret, BorderLayout.EAST);
        headRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        headRow.setToolTipText("<html>Show or hide the tag filter.<br>While hidden, the search "
                + "box <b>ignores tags</b> \u2014 useful when a common tag would otherwise "
                + "bury<br>the few listings that really mention the word.</html>");
        headRow.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { setCollapsed(!collapsed); }
        });

        pills.setOpaque(false);
        add(headRow, BorderLayout.NORTH);
        add(pills, BorderLayout.CENTER);
        syncHeader();
    }

    /** v1.76: collapsed also means "don't search tags" - see the market's search predicate. */
    public boolean isCollapsed() { return collapsed; }

    /** Run when the bar is shown/hidden, so the owner can re-apply the search. */
    public Runnable onVisibilityChanged;

    public void setCollapsed(boolean c) {
        collapsed = c;
        pills.setVisible(!c);
        syncHeader();
        revalidate();
        repaint();
        if (onVisibilityChanged != null) onVisibilityChanged.run();
    }

    private void syncHeader() {
        int n = selected.size();
        header.setText("FILTER BY TAG (all selected must match)" + (tagCount == 0 ? "" : "  \u00b7  " + tagCount + " available")
                + (n == 0 ? "" : "  \u00b7  " + n + " active"));
        header.setForeground(n > 0 ? Theme.ACCENT : Theme.TEXT_DIM);
        header.setIcon(UIIcons.tag(12, n > 0 ? Theme.ACCENT : Theme.TEXT_DIM));
        caret.setText(collapsed ? "show tags \u25b8" : "hide tags \u25be");
    }

    /** Rebuilds from the tags actually present on the loaded listings. */
    public void setTags(Collection<String> tags) {
        pills.removeAll();
        TreeSet<String> sorted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (tags != null) sorted.addAll(tags);
        // A selected tag stays on screen even if nothing currently loaded carries it - otherwise
        // filtering to a tag could remove the only control that undoes the filter.
        sorted.addAll(selected);
        tagCount = sorted.size();

        for (String tag : sorted) pills.add(pill(tag));
        if (!selected.isEmpty()) {
            JComponent clear = plainPill("clear \u2715", Theme.DANGER);
            clear.setToolTipText("Clear every tag filter");
            clear.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { onClear.run(); }
            });
            pills.add(clear);
        }
        if (sorted.isEmpty()) {
            JLabel none = new JLabel("no tags on the market yet");
            none.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            none.setForeground(Theme.TEXT_MUTED);
            pills.add(none);
        }
        syncHeader();
        revalidate();
        repaint();
    }

    private JComponent pill(String tag) {
        boolean on = selected.contains(tag);
        JComponent p = plainPill("#" + tag, on ? Theme.ACCENT : Theme.TEXT_DIM);
        p.setToolTipText(on ? "Click to stop filtering by #" + tag
                            : "Click to filter by #" + tag);
        p.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { onToggle.accept(tag); }
        });
        return p;
    }

    /** A rounded chip that paints itself - Swing has no pill, and a bordered JLabel looks wrong. */
    private JComponent plainPill(String text, Color accent) {
        JLabel l = new JLabel(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                boolean active = accent.equals(Theme.ACCENT);
                g2.setColor(active ? PILL_ON : PILL_OFF);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.setColor(active ? Theme.ACCENT : Theme.BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        l.setForeground(accent);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        l.setBorder(new EmptyBorder(3, 10, 4, 10));
        l.setOpaque(false);
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return l;
    }

    private static final Color PILL_OFF = new Color(0x26, 0x26, 0x26);
    private static final Color PILL_ON  = new Color(0x3A, 0x33, 0x18);
}
