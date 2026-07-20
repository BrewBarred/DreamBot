package main.menu.components;

import main.market.ScriptListing;
import main.menu.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single market listing rendered as a real, interactive Swing panel (v1.60). This replaces the
 * old {@code JList} + rubber-stamp cell renderer: a renderer can't hold live sub-components, so
 * stars, the favourite heart, the comments box and the action buttons all had to be faked with
 * hit-testing maths. Here each of those is a genuine component that receives its own events, which
 * makes the grid far easier to reason about and lets comments expand <i>inside</i> the card.
 *
 * <p>Two shapes, chosen at construction:
 * <ul>
 *   <li>{@link Mode#GRID} - the big card in the scrollable top grid: icon, title, author, badges,
 *       tags, rating, downloads, and an action row (comment / download-or-unpublish / publish).
 *   <li>{@link Mode#STRIP} - the compact card in the bottom "my market-ready" row: a clickable
 *       icon (set/replace), name + version, and publish / delete buttons.
 * </ul>
 *
 * <p>All behaviour is delegated to {@link Callbacks} so this class stays a pure view - it never
 * touches the repository or the menu's state directly.
 */
public class MarketCard extends JPanel {

    public enum Mode { GRID, STRIP }

    /** Everything the owning menu wires up; the card only calls back, never reaches in. */
    public interface Callbacks {
        void onDownload(ScriptListing l);
        void onPublish(ScriptListing l);
        void onUnpublish(ScriptListing l, JComponent src);
        void onRate(ScriptListing l, int stars);
        void onToggleFavorite(ScriptListing l);
        void onToggleComments(ScriptListing l, MarketCard card);
        void onPostComment(ScriptListing l, String body, MarketCard card);
        void onSetIcon(ScriptListing l);
        void onDeleteLocal(ScriptListing l);
        void onContextMenu(ScriptListing l, MouseEvent e, JComponent src);
        boolean isOwn(ScriptListing l);
        boolean canRate(ScriptListing l);
        boolean canComment(ScriptListing l);
    }

    private static final int GRID_W = 236;
    private static final int STRIP_W = 208;
    private static final Color CARD_BG = Theme.SURFACE_2_ALT;
    private static final Color CARD_BORDER = Theme.BORDER;

    private final Mode mode;
    private final Callbacks cb;
    private ScriptListing listing;

    // GRID sub-components we refresh in place
    private JLabel iconLabel;
    private JLabel titleLabel;
    private JLabel metaLabel;
    private JPanel badgeRow;
    private JLabel tagsLabel;
    private StarRating stars;
    private JLabel ratingText;
    private JLabel downloadsLabel;
    private FavoriteButton favButton;
    private JButton commentBtn;
    private JPanel commentsPanel;      // collapsible south
    private JTextArea commentsArea;
    private JTextField commentInput;
    private boolean commentsExpanded;

    public MarketCard(ScriptListing l, Mode mode, Callbacks cb) {
        this.listing = l;
        this.mode = mode;
        this.cb = cb;
        setLayout(new BorderLayout());
        setBackground(CARD_BG);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER),
                new EmptyBorder(8, 9, 8, 9)));
        int w = (mode == Mode.GRID) ? GRID_W : STRIP_W;
        setPreferredSize(new Dimension(w, mode == Mode.GRID ? 150 : 96));
        if (mode == Mode.GRID) buildGrid(); else buildStrip();
        wireCommonMouse();
    }

    // ── GRID ────────────────────────────────────────────────────────────────────────────────

    private void buildGrid() {
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        // top: icon + (title, meta) + favourite
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setOpaque(false);
        top.setAlignmentX(LEFT_ALIGNMENT);

        iconLabel = new JLabel(decodeIcon(listing.icon, 48));
        iconLabel.setPreferredSize(new Dimension(48, 48));
        iconLabel.setVerticalAlignment(SwingConstants.TOP);
        top.add(iconLabel, BorderLayout.WEST);

        JPanel titleCol = new JPanel();
        titleCol.setOpaque(false);
        titleCol.setLayout(new BoxLayout(titleCol, BoxLayout.Y_AXIS));
        titleLabel = new JLabel();
        titleLabel.setFont(Theme.fontBold(13));
        titleLabel.setForeground(Theme.TEXT);
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);
        metaLabel = new JLabel();
        metaLabel.setFont(Theme.font(11));
        metaLabel.setForeground(Theme.TEXT_DIM);
        metaLabel.setAlignmentX(LEFT_ALIGNMENT);
        titleCol.add(titleLabel);
        titleCol.add(Box.createVerticalStrut(2));
        titleCol.add(metaLabel);
        top.add(titleCol, BorderLayout.CENTER);

        favButton = new FavoriteButton();
        favButton.addActionListener(e -> cb.onToggleFavorite(listing));
        JPanel favWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        favWrap.setOpaque(false);
        favWrap.add(favButton);
        top.add(favWrap, BorderLayout.EAST);

        body.add(top);
        body.add(Box.createVerticalStrut(6));

        badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        badgeRow.setOpaque(false);
        badgeRow.setAlignmentX(LEFT_ALIGNMENT);
        body.add(badgeRow);

        tagsLabel = new JLabel();
        tagsLabel.setFont(Theme.font(11));
        tagsLabel.setForeground(Theme.TEXT_MUTED);
        tagsLabel.setAlignmentX(LEFT_ALIGNMENT);
        body.add(tagsLabel);
        body.add(Box.createVerticalStrut(6));

        // rating row: stars + "4.6 (12)"    ·    downloads
        JPanel rating = new JPanel(new BorderLayout(6, 0));
        rating.setOpaque(false);
        rating.setAlignmentX(LEFT_ALIGNMENT);
        JPanel starWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        starWrap.setOpaque(false);
        stars = new StarRating(13, cb.canRate(listing));
        stars.setOnRate(s -> cb.onRate(listing, s));
        ratingText = new JLabel();
        ratingText.setFont(Theme.font(11));
        ratingText.setForeground(Theme.TEXT_DIM);
        starWrap.add(stars);
        starWrap.add(ratingText);
        rating.add(starWrap, BorderLayout.WEST);
        downloadsLabel = new JLabel();
        downloadsLabel.setFont(Theme.font(11));
        downloadsLabel.setForeground(Theme.TEXT_DIM);
        downloadsLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        rating.add(downloadsLabel, BorderLayout.EAST);
        body.add(rating);
        body.add(Box.createVerticalStrut(6));

        // action row
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        commentBtn = smallButton(UIIcons.comment(15, Theme.TEXT_DIM), "Comments");
        commentBtn.addActionListener(e -> cb.onToggleComments(listing, this));
        actions.add(commentBtn);
        // Patch B.16 carried forward: the server sends VIP bundles as null to non-VIP callers
        boolean lockedVip = listing.vipOnly && listing.bundle == null;
        JButton dl = smallButton(UIIcons.importIcon(15, lockedVip ? Theme.TEXT_MUTED : Theme.GREEN),
                lockedVip ? "VIP only \u2014 upgrade to download" : "Download");
        dl.setEnabled(!lockedVip);
        dl.addActionListener(e -> cb.onDownload(listing));
        actions.add(dl);
        if (cb.isOwn(listing) && "server".equals(listing.origin)) {
            JButton unpub = smallButton(UIIcons.publish(15, Theme.AMBER), "Unpublish");
            unpub.addActionListener(e -> cb.onUnpublish(listing, unpub));
            actions.add(unpub);
        }
        body.add(actions);

        add(body, BorderLayout.CENTER);

        // south: collapsible comments
        commentsPanel = buildCommentsPanel();
        commentsPanel.setVisible(false);
        add(commentsPanel, BorderLayout.SOUTH);

        refreshFromListing();
    }

    private JPanel buildCommentsPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(8, 0, 0, 0));

        commentsArea = new JTextArea(4, 18);
        commentsArea.setEditable(false);
        commentsArea.setLineWrap(true);
        commentsArea.setWrapStyleWord(true);
        commentsArea.setFont(Theme.font(11));
        commentsArea.setForeground(Theme.TEXT);
        commentsArea.setBackground(Theme.BG_APP);
        commentsArea.setText("Loading comments\u2026");
        JScrollPane sp = new JScrollPane(commentsArea);
        sp.setPreferredSize(new Dimension(GRID_W - 20, 84));
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        p.add(sp, BorderLayout.CENTER);

        JPanel postRow = new JPanel(new BorderLayout(4, 0));
        postRow.setOpaque(false);
        commentInput = new JTextField();
        commentInput.setFont(Theme.font(11));
        boolean can = cb.canComment(listing);
        commentInput.setEnabled(can);
        if (!can) commentInput.setText("Log in to comment");
        JButton post = smallButton(UIIcons.publish(14, Theme.ACCENT), "Post comment");
        post.setEnabled(can);
        Runnable doPost = () -> {
            String body = commentInput.getText().trim();
            if (body.isEmpty()) return;
            commentInput.setText("");
            cb.onPostComment(listing, body, this);
        };
        post.addActionListener(e -> doPost.run());
        commentInput.addActionListener(e -> doPost.run());
        postRow.add(commentInput, BorderLayout.CENTER);
        postRow.add(post, BorderLayout.EAST);
        p.add(postRow, BorderLayout.SOUTH);
        return p;
    }

    // ── STRIP ───────────────────────────────────────────────────────────────────────────────

    private void buildStrip() {
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setOpaque(false);

        iconLabel = new JLabel(decodeIcon(listing.icon, 40));
        iconLabel.setPreferredSize(new Dimension(40, 40));
        iconLabel.setToolTipText("Click to set an icon");
        iconLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        iconLabel.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) cb.onSetIcon(listing);
            }
        });
        top.add(iconLabel, BorderLayout.WEST);

        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        titleLabel = new JLabel(listing.name);
        titleLabel.setFont(Theme.fontBold(12));
        titleLabel.setForeground(Theme.TEXT);
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);
        String kind = (listing.kind == null ? "task" : listing.kind);
        String tags = (listing.tags == null || listing.tags.isEmpty())
                ? "" : "  \u00b7  " + String.join(", ", listing.tags);
        metaLabel = new JLabel("v" + trimVersion(listing.version) + "  \u00b7  " + kind + tags);
        metaLabel.setFont(Theme.font(11));
        metaLabel.setForeground(Theme.TEXT_DIM);
        metaLabel.setAlignmentX(LEFT_ALIGNMENT);
        col.add(titleLabel);
        col.add(Box.createVerticalStrut(2));
        col.add(metaLabel);
        top.add(col, BorderLayout.CENTER);

        add(top, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        JButton pub = smallButton(UIIcons.publish(16, Theme.GREEN), "Publish");
        pub.addActionListener(e -> cb.onPublish(listing));
        JButton del = smallButton(UIIcons.cross(14, Theme.DANGER), "Remove from market-ready");
        del.addActionListener(e -> cb.onDeleteLocal(listing));
        actions.add(pub);
        actions.add(del);
        add(actions, BorderLayout.SOUTH);
    }

    // ── refresh ─────────────────────────────────────────────────────────────────────────────

    /** Re-reads the listing into the live sub-components (GRID only has dynamic bits). */
    public void refreshFromListing() {
        if (mode != Mode.GRID) return;
        iconLabel.setIcon(decodeIcon(listing.icon, 48));
        titleLabel.setText(clip(listing.name, 26));
        titleLabel.setToolTipText(listing.name);
        metaLabel.setText("by " + clip(listing.author, 16) + "  \u00b7  v" + trimVersion(listing.version)
                + "  \u00b7  " + shortDate(listing.publishedAt));

        badgeRow.removeAll();
        if (listing.vipOnly) badgeRow.add(chip("VIP", Theme.ACCENT, Theme.ACCENT_TINT));
        else badgeRow.add(chip("FREE", Theme.GREEN, Theme.BG_APP));
        String kind = (listing.kind == null ? "task" : listing.kind);
        badgeRow.add(chip(kind, Theme.BLUE, Theme.BG_APP));

        if (listing.tags == null || listing.tags.isEmpty()) tagsLabel.setText(" ");
        else tagsLabel.setText(clip(String.join("  ", prefixHash(listing.tags)), 34));

        stars.setValues(listing.avgRating, listing.myRating);
        ratingText.setText(listing.ratingCount > 0
                ? String.format("%.1f (%d)", listing.avgRating, listing.ratingCount) : "no ratings");
        downloadsLabel.setText(listing.downloads + (listing.downloads == 1 ? " dl" : " dls"));
        favButton.setFavorited(listing.myFavorite, listing.favorites);

        revalidate();
        repaint();
    }

    // ── comments API (driven by the menu) ───────────────────────────────────────────────────

    public void setCommentsExpanded(boolean expanded) {
        this.commentsExpanded = expanded;
        if (commentsPanel != null) {
            commentsPanel.setVisible(expanded);
            commentBtn.setIcon(UIIcons.comment(15, expanded ? Theme.ACCENT : Theme.TEXT_DIM));
        }
        revalidate();
        repaint();
    }

    public boolean isCommentsExpanded() { return commentsExpanded; }

    public void setCommentsText(String text) {
        if (commentsArea != null) {
            commentsArea.setText(text == null ? "" : text);
            commentsArea.setCaretPosition(0);
        }
    }

    public ScriptListing getListing() { return listing; }

    public void setListing(ScriptListing l) { this.listing = l; }

    // ── shared bits ─────────────────────────────────────────────────────────────────────────

    private void wireCommonMouse() {
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Theme.BORDER_STRONG),
                        new EmptyBorder(8, 9, 8, 9)));
            }
            @Override public void mouseExited(MouseEvent e) {
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(CARD_BORDER),
                        new EmptyBorder(8, 9, 8, 9)));
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                    cb.onContextMenu(listing, e, MarketCard.this);
                } else if (mode == Mode.GRID && e.getClickCount() == 2
                        && SwingUtilities.isLeftMouseButton(e)) {
                    cb.onDownload(listing);
                }
            }
            @Override public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) cb.onContextMenu(listing, e, MarketCard.this);
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) cb.onContextMenu(listing, e, MarketCard.this);
            }
        };
        addMouseListener(ma);
    }

    private JButton smallButton(Icon icon, String tip) {
        JButton b = new JButton(icon);
        b.setToolTipText(tip);
        b.setPreferredSize(new Dimension(30, 26));
        b.setFocusPainted(false);
        b.setMargin(new Insets(1, 1, 1, 1));
        b.setBackground(Theme.SURFACE_2);
        return b;
    }

    private JLabel chip(String text, Color fg, Color bg) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.fontBold(10));
        l.setForeground(fg);
        l.setOpaque(true);
        l.setBackground(bg);
        l.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(fg.darker()),
                new EmptyBorder(1, 5, 1, 5)));
        return l;
    }

    private static String[] prefixHash(java.util.List<String> tags) {
        int n = Math.min(tags.size(), 4);
        String[] out = new String[n];
        for (int i = 0; i < n; i++) out[i] = "#" + tags.get(i);
        return out;
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "\u2026";
    }

    private static String trimVersion(double v) {
        if (v == Math.floor(v)) return String.valueOf((int) v);
        return String.valueOf(v);
    }

    private static String shortDate(long millis) {
        if (millis <= 0) return "\u2014";
        return new java.text.SimpleDateFormat("d MMM yyyy").format(new java.util.Date(millis));
    }

    // ── favourite heart button ──────────────────────────────────────────────────────────────

    /** A tiny heart toggle that shows the favourite count beside it. */
    private static class FavoriteButton extends JButton {
        private boolean favorited;
        private int count;
        FavoriteButton() {
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setMargin(new Insets(0, 0, 0, 0));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setHorizontalTextPosition(SwingConstants.RIGHT);
            setFont(Theme.font(11));
            setForeground(Theme.TEXT_DIM);
            setFavorited(false, 0);
        }
        void setFavorited(boolean fav, int count) {
            this.favorited = fav;
            this.count = Math.max(0, count);
            setIcon(UIIcons.heart(16, fav ? Theme.DANGER : Theme.TEXT_DIM, fav));
            setText(this.count > 0 ? " " + this.count : "");
            setToolTipText(fav ? "Loved - click to unlove" : "Add to your loved list");
        }
    }

    // ── icon decode + cache ─────────────────────────────────────────────────────────────────

    /** Small LRU of decoded, size-scaled icons so scrolling doesn't re-decode every paint. */
    private static final int ICON_CACHE_MAX = 96;
    private static final Map<String, ImageIcon> ICON_CACHE =
            new LinkedHashMap<String, ImageIcon>(128, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, ImageIcon> e) {
                    return size() > ICON_CACHE_MAX;
                }
            };

    /**
     * Decodes a listing's base64 icon to a square {@code size}px {@link Icon}. Tolerant of a
     * {@code data:} URI prefix and of raw base64; on any failure (or no icon) returns a drawn
     * placeholder so a bad blob can never break the grid. Results are cached by (hash,size).
     */
    public static Icon decodeIcon(String base64, int size) {
        if (base64 == null || base64.isEmpty())
            return UIIcons.image(size, Theme.TEXT_MUTED);
        String key = size + ":" + Integer.toHexString(base64.hashCode()) + ":" + base64.length();
        ImageIcon cached = ICON_CACHE.get(key);
        if (cached != null) return cached;
        try {
            String data = base64;
            int comma = data.indexOf(',');
            if (data.startsWith("data:") && comma > 0) data = data.substring(comma + 1);
            byte[] bytes = java.util.Base64.getMimeDecoder().decode(data.trim());
            java.awt.image.BufferedImage img =
                    javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if (img == null) return UIIcons.image(size, Theme.TEXT_MUTED);
            Image scaled = img.getScaledInstance(size, size, Image.SCALE_SMOOTH);
            ImageIcon icon = new ImageIcon(scaled);
            ICON_CACHE.put(key, icon);
            return icon;
        } catch (Throwable t) {
            return UIIcons.image(size, Theme.TEXT_MUTED);
        }
    }
}
