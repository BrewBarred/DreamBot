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
        /**
         * v1.64: open the full DETAIL view for a listing (replaces the old in-card comment and
         * structure accordions). focusComments scrolls the view to the comment thread.
         */
        void onOpenDetails(ScriptListing l, boolean focusComments);
        /** v1.63: open the author's public profile (author-link click on a server listing). */
        void onOpenProfile(ScriptListing l);
        void onPostComment(ScriptListing l, String body, MarketCard card);
        void onSetIcon(ScriptListing l);
        /** v1.61: open the Card Builder for a staged listing (strip card button / context menu). */
        void onBuildCard(ScriptListing l);
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
    private JLabel authorLink;         // v1.63: clickable "by <scripter>" -> public profile
    private JPanel badgeRow;
    private JLabel tagsLabel;
    private StarRating stars;
    private JLabel ratingText;
    private JLabel downloadsLabel;
    private FavoriteButton favButton;
    private JButton commentBtn;
    private JButton structureBtn;      // v1.63/64: the (i) details button
    /** v1.63: minimum (collapsed) card height; sections add to this when they open. */
    private int collapsedFloor = 150;

    /**
     * v1.63: width stays fixed (for the WrapLayout grid) but height follows the content, so the
     * card grows when its collapsible sections (structure outline, comments) open and shrinks
     * back when they close. A floor keeps the collapsed card looking exactly as it did before.
     */
    @Override
    public Dimension getPreferredSize() {
        int w = (mode == Mode.GRID) ? GRID_W : STRIP_W;
        int h;
        java.awt.LayoutManager lm = getLayout();
        if (lm != null) {
            java.awt.Insets in = getInsets();
            h = lm.preferredLayoutSize(this).height;   // BorderLayout height incl. visible SOUTH
            // preferredLayoutSize already accounts for insets via the container; keep as-is
            h = Math.max(h, in.top + in.bottom);
        } else {
            h = collapsedFloor;
        }
        return new Dimension(w, Math.max(collapsedFloor, h));
    }

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
        // v1.63: height is dynamic now (see getPreferredSize) so the card can grow when its
        // collapsible sections open. Width stays fixed for a tidy WrapLayout grid.
        this.collapsedFloor = (mode == Mode.GRID) ? 150 : 112;
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
        // v1.63: the author is its own clickable link (opens their public profile); the rest of
        // the meta line (version + date) stays a plain label beside it.
        authorLink = new JLabel();
        authorLink.setFont(Theme.font(11));
        authorLink.setForeground(Theme.TEXT_DIM);
        authorLink.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && "server".equals(listing.origin))
                    cb.onOpenProfile(listing);
            }
        });
        metaLabel = new JLabel();
        metaLabel.setFont(Theme.font(11));
        metaLabel.setForeground(Theme.TEXT_DIM);
        JPanel metaRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        metaRow.setOpaque(false);
        metaRow.setAlignmentX(LEFT_ALIGNMENT);
        metaRow.add(authorLink);
        metaRow.add(metaLabel);
        titleCol.add(titleLabel);
        titleCol.add(Box.createVerticalStrut(2));
        titleCol.add(metaRow);
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
        // v1.64: both of these open the full DETAIL view (which took over from the old in-card
        // accordions): the comment bubble lands on the thread, the (i) lands at the top.
        commentBtn = smallButton(UIIcons.comment(15, Theme.TEXT_DIM), "Comments");
        commentBtn.addActionListener(e -> cb.onOpenDetails(listing, true));
        actions.add(commentBtn);
        structureBtn = smallButton(UIIcons.info(15, Theme.TEXT_DIM),
                "Details \u2014 what's inside, full description, comments (or right-click)");
        structureBtn.addActionListener(e -> cb.onOpenDetails(listing, false));
        actions.add(structureBtn);
        // Patch B.16 carried forward: the server sends VIP bundles as null to non-VIP callers
        boolean lockedVip = listing.vipOnly && listing.bundle == null;
        JButton dl = smallButton(UIIcons.importIcon(15, lockedVip ? Theme.TEXT_MUTED : Theme.GREEN),
                lockedVip ? "VIP only \u2014 upgrade to download"
                          : "Download (or double-click the card)");
        dl.setEnabled(!lockedVip);
        dl.addActionListener(e -> cb.onDownload(listing));
        actions.add(dl);
        // v1.63: right-click used to open the management menu; it now opens the outline, so the
        // menu moved to this explicit "more" button (rename / publish / unpublish / remove / ...).
        JButton moreBtn = smallButton(UIIcons.more(15, Theme.TEXT_DIM), "More actions\u2026");
        moreBtn.addActionListener(e -> cb.onContextMenu(listing, null, moreBtn));
        actions.add(moreBtn);
        if (cb.isOwn(listing) && "server".equals(listing.origin)) {
            JButton unpub = smallButton(UIIcons.publish(15, Theme.AMBER), "Unpublish");
            unpub.addActionListener(e -> cb.onUnpublish(listing, unpub));
            actions.add(unpub);
        }
        body.add(actions);

        add(body, BorderLayout.CENTER);
        // v1.64: the in-card comment/structure accordions are gone - the DETAIL view owns them

        refreshFromListing();
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
            private void handlePopup(MouseEvent e) {
                // v1.64: in the GRID, right-click opens the DETAIL view (the management menu
                // moved to the card's "..." button). STRIP cards keep their right-click menu.
                if (mode == Mode.GRID) cb.onOpenDetails(listing, false);
                else cb.onContextMenu(listing, e, MarketCard.this);
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                    handlePopup(e);
                } else if (mode == Mode.GRID && e.getClickCount() == 2
                        && SwingUtilities.isLeftMouseButton(e)) {
                    cb.onDownload(listing);   // double-left-click still downloads
                }
            }
            @Override public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) handlePopup(e);
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) handlePopup(e);
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

    static String trimVersion(double v) {
        if (v == Math.floor(v)) return String.valueOf((int) v);
        return String.valueOf(v);
    }

    static String shortDate(long millis) {
        if (millis <= 0) return "\u2014";
        return new java.text.SimpleDateFormat("d MMM yyyy").format(new java.util.Date(millis));
    }

    // ── favourite heart button ──────────────────────────────────────────────────────────────

    /** A tiny heart toggle that shows the favourite count beside it. */
    private void buildStrip() {
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setOpaque(false);

        iconLabel = new JLabel(decodeIcon(listing.icon, 40));
        iconLabel.setPreferredSize(new Dimension(40, 40));
        iconLabel.setToolTipText("Click to build this item's card (icon, details, ready state)");
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
        // v1.61: the card state, worn on the sleeve - the publish gate should never surprise
        col.add(Box.createVerticalStrut(3));
        JPanel stateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        stateRow.setOpaque(false);
        stateRow.setAlignmentX(LEFT_ALIGNMENT);
        stateRow.add(listing.cardReady
                ? chip("CARD \u2713", Theme.GREEN, Theme.BG_APP)
                : chip("NO CARD", Theme.AMBER, Theme.AMBER_TINT));
        col.add(stateRow);
        top.add(col, BorderLayout.CENTER);

        add(top, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        // v1.61: build/edit the card - amber until a card exists, to pull the eye to step one
        JButton card = smallButton(UIIcons.card(15, listing.cardReady ? Theme.TEXT_DIM : Theme.AMBER),
                listing.cardReady ? "Edit this item's card" : "Build this item's card");
        card.addActionListener(e -> cb.onBuildCard(listing));
        // v1.61: publishing is gated on a finished card - the button says so before you click
        JButton pub = smallButton(
                UIIcons.publish(16, listing.cardReady ? Theme.GREEN : Theme.TEXT_MUTED),
                listing.cardReady ? "Publish"
                        : "A finished card is required first \u2014 click to open the card builder");
        pub.addActionListener(e -> cb.onPublish(listing));
        JButton del = smallButton(UIIcons.cross(14, Theme.DANGER), "Remove from market-ready");
        del.addActionListener(e -> cb.onDeleteLocal(listing));
        actions.add(card);
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
        // v1.63: server listings get a clickable author (their public profile lives there);
        // local/folder listings show a plain author, since there's no profile to open.
        boolean profileable = "server".equals(listing.origin)
                && listing.author != null && !listing.author.isEmpty();
        authorLink.setText("by " + clip(listing.author, 16));
        authorLink.setForeground(profileable ? Theme.ACCENT : Theme.TEXT_DIM);
        authorLink.setCursor(Cursor.getPredefinedCursor(
                profileable ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        authorLink.setToolTipText(profileable
                ? "View " + listing.author + "'s public profile" : null);
        metaLabel.setText("  \u00b7  v" + trimVersion(listing.version)
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


    static class FavoriteButton extends JButton {
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
