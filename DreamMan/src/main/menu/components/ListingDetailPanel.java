package main.menu.components;

import main.market.ScriptListing;
import main.menu.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * v1.64: the market DETAIL view - what opens when you hit a card's ⓘ, its comment button, or
 * right-click it. It replaces the old in-card accordions (which squeezed expanded content into a
 * 236px card and reflowed the whole grid) by taking over the entire grid area: one listing,
 * full width, with everything readable at once - the FULL description (grid cards never had room
 * for it), the complete tag list, the tasks → actions → checks outline (expanded by default,
 * per-task collapsible), and the comment thread with its post box.
 *
 * <p>Closing is explicit and everywhere: the ✕ button top-right, or Esc. The menu swaps this in
 * and out of the grid's spot via a CardLayout, so the market header and staging strip stay put.
 *
 * <p>Pure view: every action routes through the same {@link MarketCard.Callbacks} the cards use,
 * so download / rate / favourite / profile / comment behave identically here and in the grid.
 */
public class ListingDetailPanel extends JPanel {

    private final ScriptListing listing;
    private final MarketCard.Callbacks cb;

    private final StarRating stars;
    private final JLabel ratingText;
    private final JLabel downloadsLabel;
    private final MarketCard.FavoriteButton favButton;
    private final JTextArea commentsArea;
    private final JPanel commentsSection;
    private final JScrollPane column;

    public ListingDetailPanel(ScriptListing l, MarketCard.Callbacks cb,
                              Runnable onClose, boolean focusComments) {
        this.listing = l;
        this.cb = cb;
        setLayout(new BorderLayout(0, 8));
        setOpaque(true);
        setBackground(Theme.BG_APP);
        setBorder(new EmptyBorder(10, 12, 10, 12));

        // ── header: icon · identity · stats · [✕ / download] ────────────────────────────────
        JPanel headRow = new JPanel(new BorderLayout(12, 0));
        headRow.setOpaque(false);

        JLabel icon = new JLabel(MarketCard.decodeIcon(l.icon, 64));
        icon.setPreferredSize(new Dimension(64, 64));
        icon.setVerticalAlignment(SwingConstants.TOP);
        headRow.add(icon, BorderLayout.WEST);

        JPanel idCol = new JPanel();
        idCol.setOpaque(false);
        idCol.setLayout(new BoxLayout(idCol, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(l.name);
        name.setFont(Theme.fontBold(16));
        name.setForeground(Theme.TEXT);
        name.setAlignmentX(LEFT_ALIGNMENT);
        idCol.add(name);
        idCol.add(Box.createVerticalStrut(3));

        boolean profileable = "server".equals(l.origin)
                && l.author != null && !l.author.isEmpty();
        JLabel author = new JLabel("by " + l.author);
        author.setFont(Theme.font(12));
        author.setForeground(profileable ? Theme.ACCENT : Theme.TEXT_DIM);
        author.setCursor(Cursor.getPredefinedCursor(
                profileable ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        if (profileable) {
            author.setToolTipText("View " + l.author + "'s public profile");
            author.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) cb.onOpenProfile(listing);
                }
            });
        }
        JLabel meta = new JLabel("  \u00b7  v" + MarketCard.trimVersion(l.version)
                + "  \u00b7  " + MarketCard.shortDate(l.publishedAt));
        meta.setFont(Theme.font(12));
        meta.setForeground(Theme.TEXT_DIM);
        JPanel authorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        // v1.87: rank badges beside the author, once the server sends listing rank fields
        if (l.authorTier != null || Boolean.TRUE.equals(l.authorScripter)
                || (l.authorDonatedCents != null && l.authorDonatedCents > 0)) {
            authorRow.add(RankBadge.of(l.authorTier, Boolean.TRUE.equals(l.authorScripter),
                    l.authorDonatedCents == null ? 0 : l.authorDonatedCents, 12));
            authorRow.add(javax.swing.Box.createHorizontalStrut(8));
        }
        authorRow.setOpaque(false);
        authorRow.setAlignmentX(LEFT_ALIGNMENT);
        authorRow.add(author);
        authorRow.add(meta);
        idCol.add(authorRow);
        idCol.add(Box.createVerticalStrut(5));

        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        chips.setOpaque(false);
        chips.setAlignmentX(LEFT_ALIGNMENT);
        // v1.90: the exact rank, not a VIP/FREE binary
        String needTier = l.minTier == null ? (l.vipOnly ? "vip" : "free") : l.minTier;
        boolean ranked = !"free".equalsIgnoreCase(needTier);
        chips.add(chip(ranked ? main.market.Tier.labelFor(needTier).toUpperCase() : "FREE",
                ranked ? main.menu.components.RankBadge.tierColor(needTier) : Theme.GREEN,
                ranked ? Theme.ACCENT_TINT : Theme.BG_APP));
        chips.add(chip(l.kind == null ? "task" : l.kind, Theme.BLUE, Theme.BG_APP));
        idCol.add(chips);
        idCol.add(Box.createVerticalStrut(5));

        stars = new StarRating(14, cb.canRate(l));
        stars.setOnRate(v -> cb.onRate(listing, v));
        ratingText = new JLabel();
        ratingText.setFont(Theme.font(12));
        ratingText.setForeground(Theme.TEXT_DIM);
        downloadsLabel = new JLabel();
        downloadsLabel.setFont(Theme.font(12));
        downloadsLabel.setForeground(Theme.TEXT_DIM);
        favButton = new MarketCard.FavoriteButton();
        favButton.addActionListener(e -> cb.onToggleFavorite(listing));
        JPanel statsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        statsRow.setOpaque(false);
        statsRow.setAlignmentX(LEFT_ALIGNMENT);
        statsRow.add(stars);
        statsRow.add(ratingText);
        statsRow.add(downloadsLabel);
        statsRow.add(favButton);
        idCol.add(statsRow);
        headRow.add(idCol, BorderLayout.CENTER);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        // v1.69: icon buttons - a drawn X and up-arrow instead of a glyph-plus-word pair,
        // which rendered inconsistently across platform fonts.
        JButton close = new JButton("Close", UIIcons.close(13, Theme.TEXT));
        close.setFont(Theme.fontBold(12));
        close.setToolTipText("Back to the market grid (Esc works too)");
        close.addActionListener(e -> onClose.run());
        close.setAlignmentX(RIGHT_ALIGNMENT);
        boolean lockedVip = (l.vipOnly || Boolean.TRUE.equals(l.lockedByTier))
                && l.bundle == null;   // v1.90
        JButton download = new JButton(lockedVip ? "VIP only" : "Download",
                UIIcons.arrowUp(13, lockedVip ? Theme.TEXT_MUTED : Theme.GREEN));
        download.setFont(Theme.fontBold(12));
        download.setEnabled(!lockedVip);
        download.setForeground(lockedVip ? Theme.TEXT_MUTED : Theme.GREEN);
        download.setToolTipText(lockedVip ? "Upgrade to download this one"
                : "trigger".equalsIgnoreCase(l.kind)
                    ? "Install this check into your always-on checks"
                    : "Import into your Task Library");
        download.addActionListener(e -> cb.onDownload(listing));
        download.setAlignmentX(RIGHT_ALIGNMENT);
        right.add(close);
        right.add(Box.createVerticalStrut(8));
        right.add(download);
        headRow.add(right, BorderLayout.EAST);
        add(headRow, BorderLayout.NORTH);

        // v1.70 (item 2): right-click anywhere in the open card closes it. Right-click used to
        // be how you OPENED a card from the grid; that is now a left-click, so the gesture is
        // free and closing is the thing you actually want while a card is open.
        java.awt.event.MouseAdapter closeOnRight = new java.awt.event.MouseAdapter() {
            private void maybeClose(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) onClose.run();
            }
            @Override public void mousePressed(java.awt.event.MouseEvent e)  { maybeClose(e); }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) { maybeClose(e); }
        };
        addMouseListener(closeOnRight);
        headRow.addMouseListener(closeOnRight);

        // ── the scrolling column: description · tags · structure · comments ─────────────────
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBorder(new EmptyBorder(4, 2, 8, 10));

        col.add(sectionTitle("Description"));
        String desc = l.description == null ? "" : l.description.trim();
        JTextArea descArea = new JTextArea(desc.isEmpty()
                ? "(the author didn't write a description)" : desc);
        descArea.setEditable(false);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        descArea.setFont(Theme.font(12));
        descArea.setForeground(desc.isEmpty() ? Theme.TEXT_MUTED : Theme.TEXT);
        descArea.setBackground(Theme.SURFACE_2);
        descArea.setBorder(new EmptyBorder(8, 10, 8, 10));
        descArea.setAlignmentX(LEFT_ALIGNMENT);
        col.add(descArea);

        if (l.tags != null && !l.tags.isEmpty()) {
            JLabel tags = new JLabel(String.join("   ",
                    l.tags.stream().map(t -> "#" + t).toArray(String[]::new)));
            tags.setFont(Theme.font(11));
            tags.setForeground(Theme.TEXT_MUTED);
            tags.setBorder(new EmptyBorder(6, 2, 0, 0));
            tags.setAlignmentX(LEFT_ALIGNMENT);
            col.add(tags);
        }

        col.add(Box.createVerticalStrut(12));
        col.add(sectionTitle("What's inside"));
        col.add(new ListingStructureView(l));

        // comments only exist for server listings; local/folder staging has no thread
        commentsSection = new JPanel();
        commentsSection.setOpaque(false);
        commentsSection.setLayout(new BoxLayout(commentsSection, BoxLayout.Y_AXIS));
        commentsSection.setAlignmentX(LEFT_ALIGNMENT);
        commentsArea = new JTextArea(4, 20);
        if ("server".equals(l.origin)) {
            commentsSection.add(Box.createVerticalStrut(12));
            commentsSection.add(sectionTitle("Comments"));
            commentsArea.setEditable(false);
            commentsArea.setLineWrap(true);
            commentsArea.setWrapStyleWord(true);
            commentsArea.setFont(Theme.font(12));
            commentsArea.setForeground(Theme.TEXT);
            commentsArea.setBackground(Theme.SURFACE_2);
            commentsArea.setBorder(new EmptyBorder(8, 10, 8, 10));
            commentsArea.setText("Loading comments\u2026");
            commentsArea.setAlignmentX(LEFT_ALIGNMENT);
            commentsSection.add(commentsArea);
            if (cb.canComment(l)) {
                JTextField input = new JTextField();
                JButton post = new JButton(UIIcons.publish(14, Theme.GREEN));
                post.setToolTipText("Post comment");
                Runnable submit = () -> {
                    String t = input.getText().trim();
                    if (t.isEmpty()) return;
                    cb.onPostComment(listing, t, null);
                    input.setText("");
                };
                post.addActionListener(e -> submit.run());
                input.addActionListener(e -> submit.run());
                JPanel postRow = new JPanel(new BorderLayout(6, 0));
                postRow.setOpaque(false);
                postRow.setBorder(new EmptyBorder(6, 0, 0, 0));
                postRow.setAlignmentX(LEFT_ALIGNMENT);
                postRow.add(input, BorderLayout.CENTER);
                postRow.add(post, BorderLayout.EAST);
                postRow.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                        input.getPreferredSize().height + 8));
                commentsSection.add(postRow);
            }
        }
        col.add(commentsSection);

        column = Theme.thinScrollbars(new JScrollPane(col,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER));
        column.setBorder(BorderFactory.createEmptyBorder());
        column.getViewport().setOpaque(false);
        column.setOpaque(false);
        column.getVerticalScrollBar().setUnitIncrement(18);
        add(column, BorderLayout.CENTER);

        // Esc = close, from anywhere in the window while the detail is showing
        registerKeyboardAction(e -> onClose.run(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                WHEN_IN_FOCUSED_WINDOW);

        if (focusComments && "server".equals(l.origin)) {
            SwingUtilities.invokeLater(() ->
                    commentsSection.scrollRectToVisible(
                            new Rectangle(0, 0, 10, commentsSection.getHeight())));
        }
        refreshFrom(l);
    }

    /** Which listing this detail is showing (the menu routes comment fetches by this). */
    public String listingId() { return listing.id; }

    /** The comment thread text (pushed by the menu's fetch, same as the old accordion). */
    public void setCommentsText(String text) {
        commentsArea.setText(text == null ? "" : text);
        commentsArea.setCaretPosition(0);
    }

    /** Live stats refresh after a rate/favourite/download round-trips through the server. */
    public void refreshFrom(ScriptListing fresh) {
        ScriptListing s = fresh == null ? listing : fresh;
        stars.setValues(s.avgRating, s.myRating);
        ratingText.setText(s.ratingCount > 0
                ? String.format("%.1f (%d)", s.avgRating, s.ratingCount) : "no ratings");
        downloadsLabel.setText(s.downloads + (s.downloads == 1 ? " dl" : " dls"));
        favButton.setFavorited(s.myFavorite, s.favorites);
        revalidate();
        repaint();
    }

    private JLabel sectionTitle(String t) {
        JLabel l = new JLabel(t);
        l.setFont(Theme.fontBold(13));
        l.setForeground(Theme.ACCENT);
        l.setBorder(new EmptyBorder(0, 0, 4, 0));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JLabel chip(String text, Color fg, Color bg) {
        JLabel c = new JLabel(text);
        c.setOpaque(true);
        c.setFont(Theme.fontBold(10));
        c.setForeground(fg);
        c.setBackground(bg);
        c.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(fg.darker()),
                new EmptyBorder(1, 6, 1, 6)));
        return c;
    }
}
