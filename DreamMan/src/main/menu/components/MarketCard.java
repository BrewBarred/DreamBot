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
        /** v1.63: expand/collapse the card's tasks -> actions -> triggers outline. */
        void onToggleStructure(ScriptListing l, MarketCard card);
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
    /** v1.63: cap for the expanded structure outline; taller content scrolls. */
    private static final int STRUCT_MAX_H = 260;
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
    private JPanel commentsPanel;      // collapsible south
    private JTextArea commentsArea;
    private JPanel structurePanel;     // v1.63: collapsible tasks->actions->triggers outline
    private JButton structureBtn;      // v1.63
    private boolean structureExpanded; // v1.63
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
        commentBtn = smallButton(UIIcons.comment(15, Theme.TEXT_DIM), "Comments");
        commentBtn.addActionListener(e -> cb.onToggleComments(listing, this));
        actions.add(commentBtn);
        // v1.63: (i) opens the "what's inside" outline (tasks -> actions -> checks). Right-clicking
        // the card does the same. Disabled when the bundle is withheld (VIP hidden from non-VIP).
        boolean haveBundle = listing.bundle != null;
        structureBtn = smallButton(UIIcons.info(15, Theme.TEXT_DIM),
                haveBundle ? "What's inside (tasks, actions, checks) \u2014 or right-click the card"
                           : "Details unavailable \u2014 download to view");
        structureBtn.setEnabled(haveBundle);
        structureBtn.addActionListener(e -> cb.onToggleStructure(listing, this));
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

        // south: collapsible sections (v1.63 structure outline, then the comments box), stacked
        JPanel south = new JPanel();
        south.setOpaque(false);
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        structurePanel = buildStructurePanel();
        structurePanel.setVisible(false);
        structurePanel.setAlignmentX(LEFT_ALIGNMENT);
        commentsPanel = buildCommentsPanel();
        commentsPanel.setVisible(false);
        commentsPanel.setAlignmentX(LEFT_ALIGNMENT);
        south.add(structurePanel);
        south.add(commentsPanel);
        add(south, BorderLayout.SOUTH);

        refreshFromListing();
    }

    /** v1.63: the collapsible outline of what a listing contains - tasks, their actions, checks. */
    private JPanel buildStructurePanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(8, 0, 0, 0));

        JPanel outline = new JPanel();
        outline.setOpaque(false);
        outline.setLayout(new BoxLayout(outline, BoxLayout.Y_AXIS));

        JScrollPane sp = new JScrollPane(outline,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sp.getViewport().setOpaque(false);
        sp.setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(14);

        main.data.store.ScriptBundle b = listing.bundle;
        if (b == null) {
            outline.add(outlineLabel("Structure isn't available for this listing.", 0, false,
                    Theme.TEXT_DIM));
        } else {
            java.util.List<main.data.store.TaskData> tasks = b.tasks;
            int taskCount = (tasks == null) ? 0 : tasks.size();
            String loops = (b.loops <= 0) ? "loops forever"
                    : (b.loops == 1 ? "runs once" : "runs \u00d7" + b.loops);
            outline.add(outlineLabel("Tasks (" + taskCount + ")  \u00b7  queue " + loops, 0,
                    true, Theme.ACCENT));
            if (tasks != null) {
                for (int i = 0; i < tasks.size(); i++) {
                    main.data.store.TaskData t = tasks.get(i);
                    if (t == null) continue;
                    outline.add(buildTaskNode(i + 1, t, sp, outline));   // v1.63: collapsible
                }
            }
            java.util.List<String> checks = describeTriggers(b.globalTriggers);
            outline.add(outlineLabel("Always-on checks (" + checks.size() + ")", 0, true,
                    Theme.ACCENT));
            if (checks.isEmpty()) {
                outline.add(outlineLabel("(none)", 1, false, Theme.TEXT_MUTED));
            } else {
                for (String c : checks)
                    outline.add(outlineLabel("\u2022 " + c, 1, false, Theme.TEXT_DIM));
            }
        }

        resizeStructureScroll(sp, outline);
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    /**
     * v1.63: one collapsible task - a clickable header (arrow + "N. name xR") over its actions.
     * Actions are shown by default (everything expanded for transparency); clicking the header
     * collapses/expands just that task, and the card re-grows to fit (or the outline scrolls).
     */
    private JPanel buildTaskNode(int index, main.data.store.TaskData t,
                                 JScrollPane sp, JPanel outline) {
        JPanel node = new JPanel();
        node.setOpaque(false);
        node.setLayout(new BoxLayout(node, BoxLayout.Y_AXIS));
        node.setAlignmentX(LEFT_ALIGNMENT);

        String rep = (t.repeat > 1) ? "  \u00d7" + t.repeat : "";
        String nm = (t.name == null || t.name.isEmpty()) ? "(unnamed task)" : t.name;

        JPanel acts = new JPanel();
        acts.setOpaque(false);
        acts.setLayout(new BoxLayout(acts, BoxLayout.Y_AXIS));
        acts.setAlignmentX(LEFT_ALIGNMENT);
        java.util.List<main.data.ActionData> list = t.actions;
        if (list == null || list.isEmpty()) {
            acts.add(outlineLabel("(no actions)", 2, false, Theme.TEXT_MUTED));
        } else {
            for (main.data.ActionData a : list)
                acts.add(outlineLabel("\u2022 " + describeAction(a), 2, false, Theme.TEXT_DIM));
        }
        acts.setVisible(true);   // expanded by default

        String label = index + ". " + nm + rep;
        JLabel header = outlineLabel("\u25be  " + label, 1, true, Theme.TEXT);
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        header.setToolTipText("Click to collapse / expand this task's actions");
        header.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                boolean show = !acts.isVisible();
                acts.setVisible(show);
                header.setText((show ? "\u25be" : "\u25b8") + "  " + label);
                resizeStructureScroll(sp, outline);
                regrowCard();
            }
        });

        node.add(header);
        node.add(acts);
        return node;
    }

    /** Sizes the outline scroller to its content up to {@link #STRUCT_MAX_H}, then it scrolls. */
    private void resizeStructureScroll(JScrollPane sp, JPanel outline) {
        // A task collapsing changes nested BoxLayout sizes; clear their caches so the height we
        // read below is fresh (invalidate() alone doesn't recurse into the child nodes).
        invalidateTree(outline);
        int wanted = outline.getPreferredSize().height + 6;
        sp.setPreferredSize(new Dimension(GRID_W - 20,
                Math.min(STRUCT_MAX_H, Math.max(40, wanted))));
        sp.invalidate();
    }

    private static void invalidateTree(java.awt.Component c) {
        c.invalidate();
        if (c instanceof java.awt.Container)
            for (java.awt.Component ch : ((java.awt.Container) c).getComponents())
                invalidateTree(ch);
    }

    /** Re-lay the card and everything above it so a task collapsing changes the card's height. */
    private void regrowCard() {
        // Clear every layout cache inside the card (the SOUTH BoxLayout sits ABOVE the outline and
        // caches child sizes, so invalidating just the outline isn't enough) before we re-measure.
        invalidateTree(this);
        revalidate();
        repaint();
        java.awt.Container c = getParent();
        while (c != null) { c.revalidate(); c.repaint(); c = c.getParent(); }
    }

    /** One indented outline row. level 0/1/2 = section / task / action indentation. */
    private JLabel outlineLabel(String text, int level, boolean bold, Color fg) {
        JLabel l = new JLabel(text);
        l.setFont(bold ? Theme.fontBold(11) : Theme.font(11));
        l.setForeground(fg);
        l.setBorder(new EmptyBorder(1, 6 + level * 16, 1, 4));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    /** A compact one-line description of an action: its type plus a hint of its main parameter. */
    private static String describeAction(main.data.ActionData a) {
        if (a == null) return "(action)";
        String type = a.getType() == null ? "Action" : a.getType();
        java.util.Map<String, String> p = a.getParams();
        String hint = "";
        if (p != null && !p.isEmpty()) {
            // prefer a recognisable target-ish key, else just the first value
            for (String k : new String[]{"Target", "Name", "Item", "Item(s)", "Object", "NPC",
                    "TaskName", "Mode", "Spell", "Bone type (exact)"}) {
                if (p.containsKey(k) && p.get(k) != null && !p.get(k).isEmpty()) {
                    hint = p.get(k);
                    break;
                }
            }
            if (hint.isEmpty()) {
                for (String v : p.values())
                    if (v != null && !v.isEmpty()) { hint = v; break; }
            }
        }
        return hint.isEmpty() ? type : type + " \u2192 " + hint;
    }

    /** Parses the bundle's trigger JSON into human-readable descriptions (best-effort). */
    private static java.util.List<String> describeTriggers(String json) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (json == null || json.trim().isEmpty()) return out;
        try {
            for (main.watchers.Trigger t : main.watchers.TriggerCodec.fromJson(json))
                if (t != null) out.add(t.describe());
        } catch (Exception ignored) {
            // malformed / unknown trigger payload - just show nothing rather than break the card
        }
        return out;
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

    // ── v1.63 structure API (driven by the menu, accordion-style like comments) ──────────────

    public void setStructureExpanded(boolean expanded) {
        this.structureExpanded = expanded;
        if (structurePanel != null) structurePanel.setVisible(expanded);
        if (structureBtn != null)
            structureBtn.setIcon(UIIcons.info(15, expanded ? Theme.ACCENT : Theme.TEXT_DIM));
        revalidate();
        repaint();
    }

    public boolean isStructureExpanded() { return structureExpanded; }

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
            private void handlePopup(MouseEvent e) {
                // v1.63: in the GRID, right-click opens the "what's inside" outline (the management
                // menu moved to the card's "..." button). STRIP cards keep their right-click menu.
                if (mode == Mode.GRID) {
                    if (structureBtn != null && structureBtn.isEnabled())
                        cb.onToggleStructure(listing, MarketCard.this);
                } else {
                    cb.onContextMenu(listing, e, MarketCard.this);
                }
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
