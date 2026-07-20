package main.menu.components;

import main.market.ScriptListing;
import main.menu.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

/**
 * The Card Builder (v1.61): one dedicated place to build and edit a staged item's market card,
 * and the only way to flip it <b>ready to publish</b>. The roadmap's rule is that an item without
 * a finished card cannot be published - this dialog is where a card gets finished, and the
 * {@code cardReady} flag it sets is what every publish path checks.
 *
 * <p>The right-hand side is a <i>live preview</i>: a real {@link MarketCard} in GRID mode, rebuilt
 * as you type, so what you see is byte-for-byte what the market grid will render (stats start at
 * zero until it's live). The icon row makes the mandatory icon a one-click job: pick from the
 * drawn defaults, hit the die for a random identicon, or choose your own file (which goes through
 * the menu's existing 300 KB-capped picker with the resize link).
 *
 * <p>The dialog never touches repositories or the menu's state itself - everything goes through
 * {@link Host}, wired up by {@code DreamBotMenu}, keeping this a pure view like {@code MarketCard}.
 */
public class CardBuilderDialog extends JDialog {

    /** Everything the owning menu wires up; the dialog only calls back, never reaches in. */
    public interface Host {
        /** Persist the (already-updated) listing to local staging; throw to keep the dialog open. */
        void saveListing(ScriptListing l) throws Exception;
        /** Publish the (saved, card-ready) listing to the current market target. */
        void publishListing(ScriptListing l);
        /** Whether "Save & publish" has somewhere to go right now (server logged in / folder set). */
        boolean canPublishNow();
        /** Human name of the publish target, for the button tooltip. */
        String publishTargetName();
        /** The menu's file picker (300 KB cap + resize link). Null = cancelled / rejected. */
        String pickIconFile(JComponent anchor);
    }

    private final ScriptListing listing;      // the real staged listing - written on Save only
    private final ScriptListing preview;      // what the live card renders from
    private final Host host;

    private final JTextField txtName;
    private final JSpinner spVersion;
    private final JComboBox<String> cmbKind;
    private final JCheckBox chkVip;
    private final JTextField txtTags;
    private final JTextArea txtDesc;
    private final JLabel iconPreview;
    private final JButton btnRemoveIcon;
    private final JCheckBox chkReady;
    private final JLabel readyHint;
    private final JLabel banner;
    private final JButton btnPublish;
    private final JPanel previewHolder;

    private String iconB64;                   // working icon; null = none yet

    public CardBuilderDialog(Window owner, ScriptListing l, Host host) {
        super(owner, "Card builder \u2014 " + (l.name == null ? "" : l.name),
                ModalityType.APPLICATION_MODAL);
        this.listing = l;
        this.host = host;
        this.iconB64 = l.icon;
        this.preview = previewCopy(l);
        setAlwaysOnTop(true);   // the DreamBot canvas is always-on-top; match it or we hide behind

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(new EmptyBorder(12, 14, 12, 14));
        root.setBackground(Theme.BG_APP);

        // ── status banner: the gate, stated up front ────────────────────────────────────────
        banner = new JLabel();
        banner.setOpaque(true);
        banner.setFont(Theme.font(12));
        banner.setBorder(new EmptyBorder(7, 10, 7, 10));
        root.add(banner, BorderLayout.NORTH);

        // ── left: the form ──────────────────────────────────────────────────────────────────
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        txtName = new JTextField(l.name == null ? "" : l.name, 18);
        double v0 = Math.max(0.1, Math.min(999.0, l.version <= 0 ? 1.0 : l.version));
        spVersion = new JSpinner(new SpinnerNumberModel(v0, 0.1, 999.0, 0.1));
        cmbKind = new JComboBox<>(new String[]{"Task (building block)", "Script (full routine)"});
        cmbKind.setSelectedIndex("script".equalsIgnoreCase(l.kind) ? 1 : 0);
        chkVip = new JCheckBox("VIP-only listing", l.vipOnly);
        chkVip.setOpaque(false);
        chkVip.setForeground(Theme.TEXT);
        chkVip.setToolTipText("VIP-gated: non-VIP players see the card but can't download it");
        txtTags = new JTextField(l.tags == null ? "" : String.join(", ", l.tags), 18);
        txtTags.setToolTipText("Comma-separated, e.g. combat, ironman, f2p");
        txtDesc = new JTextArea(l.description == null ? "" : l.description, 3, 18);
        txtDesc.setLineWrap(true);
        txtDesc.setWrapStyleWord(true);

        int y = 0;
        c.gridx = 0; c.gridy = y; c.weightx = 0;
        form.add(label("Name:"), c);
        c.gridx = 1; c.weightx = 1;
        form.add(txtName, c);
        c.gridx = 0; c.gridy = ++y; c.weightx = 0;
        form.add(label("Version:"), c);
        c.gridx = 1; c.weightx = 1;
        form.add(spVersion, c);
        c.gridx = 0; c.gridy = ++y; c.weightx = 0;
        form.add(label("Kind:"), c);
        c.gridx = 1; c.weightx = 1;
        form.add(cmbKind, c);
        c.gridx = 1; c.gridy = ++y;
        form.add(chkVip, c);
        c.gridx = 0; c.gridy = ++y; c.weightx = 0;
        form.add(label("Tags:"), c);
        c.gridx = 1; c.weightx = 1;
        form.add(txtTags, c);
        c.gridx = 0; c.gridy = ++y; c.weightx = 0;
        c.anchor = GridBagConstraints.NORTHWEST;
        form.add(label("Description:"), c);
        c.gridx = 1; c.weightx = 1;
        JScrollPane descScroll = new JScrollPane(txtDesc);
        descScroll.setPreferredSize(new Dimension(220, 62));
        form.add(descScroll, c);
        c.anchor = GridBagConstraints.WEST;

        // ── icon row: preview + defaults + random + file + remove ───────────────────────────
        iconPreview = new JLabel();
        iconPreview.setPreferredSize(new Dimension(40, 40));

        JButton btnDefaults = new JButton("Defaults \u25be");
        btnDefaults.setFont(Theme.font(11));
        btnDefaults.setToolTipText("Pick one of the built-in icons");
        btnDefaults.addActionListener(e -> showDefaultsPopup(btnDefaults));

        JButton btnRandom = new JButton(UIIcons.dice(15, Theme.ACCENT));
        btnRandom.setToolTipText("Generate a random icon \u2014 click again for another");
        btnRandom.setMargin(new Insets(2, 6, 2, 6));
        btnRandom.addActionListener(e ->
                setIcon(CardIcons.random(txtName.getText())));

        JButton btnFile = new JButton("File\u2026");
        btnFile.setFont(Theme.font(11));
        btnFile.setToolTipText("Use your own image (128\u00d7128 PNG recommended, 300 KB cap)");
        btnFile.addActionListener(e -> {
            String b = host.pickIconFile(btnFile);
            if (b != null) setIcon(b);
        });

        btnRemoveIcon = new JButton(UIIcons.cross(12, Theme.TEXT_DIM));
        btnRemoveIcon.setToolTipText("Remove the icon");
        btnRemoveIcon.setMargin(new Insets(2, 6, 2, 6));
        btnRemoveIcon.addActionListener(e -> setIcon(null));

        JPanel iconRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        iconRow.setOpaque(false);
        iconRow.add(iconPreview);
        iconRow.add(btnDefaults);
        iconRow.add(btnRandom);
        iconRow.add(btnFile);
        iconRow.add(btnRemoveIcon);

        c.gridx = 0; c.gridy = ++y; c.weightx = 0;
        form.add(label("Icon:"), c);
        c.gridx = 1; c.weightx = 1;
        form.add(iconRow, c);

        // ── the ready switch - what the publish gate reads ──────────────────────────────────
        chkReady = new JCheckBox("Card finished \u2014 ready to publish", l.cardReady);
        chkReady.setOpaque(false);
        chkReady.setForeground(Theme.TEXT);
        chkReady.setFont(Theme.fontBold(12));
        readyHint = new JLabel();
        readyHint.setFont(Theme.font(11));
        readyHint.setForeground(Theme.TEXT_DIM);
        c.gridx = 1; c.gridy = ++y;
        form.add(chkReady, c);
        c.gridy = ++y;
        form.add(readyHint, c);

        // ── right: the live preview ─────────────────────────────────────────────────────────
        previewHolder = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        previewHolder.setOpaque(false);
        JLabel previewCaption = new JLabel("Live preview \u2014 stats fill in once it's live");
        previewCaption.setFont(Theme.font(10));
        previewCaption.setForeground(Theme.TEXT_MUTED);
        previewCaption.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel right = new JPanel(new BorderLayout(0, 6));
        right.setOpaque(false);
        right.setBorder(new EmptyBorder(0, 12, 0, 0));
        right.add(previewHolder, BorderLayout.CENTER);
        right.add(previewCaption, BorderLayout.SOUTH);

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(form, BorderLayout.CENTER);
        center.add(right, BorderLayout.EAST);
        root.add(center, BorderLayout.CENTER);

        // ── buttons ─────────────────────────────────────────────────────────────────────────
        JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(e -> dispose());
        JButton btnSave = new JButton("Save card");
        btnSave.setToolTipText("Save the card to your market-ready staging");
        btnSave.addActionListener(e -> { if (applyAndSave()) dispose(); });
        btnPublish = new JButton("Save & publish");
        btnPublish.addActionListener(e -> {
            if (!applyAndSave()) return;
            dispose();
            host.publishListing(listing);
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(btnCancel);
        buttons.add(btnSave);
        buttons.add(btnPublish);
        root.add(buttons, BorderLayout.SOUTH);

        // ── live wiring ─────────────────────────────────────────────────────────────────────
        DocumentListener onType = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refresh(); }
            public void removeUpdate(DocumentEvent e) { refresh(); }
            public void changedUpdate(DocumentEvent e) { refresh(); }
        };
        txtName.getDocument().addDocumentListener(onType);
        txtTags.getDocument().addDocumentListener(onType);
        txtDesc.getDocument().addDocumentListener(onType);
        spVersion.addChangeListener(e -> refresh());
        cmbKind.addActionListener(e -> refresh());
        chkVip.addActionListener(e -> refresh());
        chkReady.addActionListener(e -> refresh());

        setContentPane(root);
        refresh();
        pack();
        setMinimumSize(new Dimension(660, getHeight()));
        setLocationRelativeTo(owner);
    }

    // ── behaviour ───────────────────────────────────────────────────────────────────────────

    private void setIcon(String b64) {
        iconB64 = (b64 == null || b64.isEmpty()) ? null : b64;
        refresh();
    }

    /** Re-reads every control into the preview copy, rebuilds the card, re-runs the gate rules. */
    private void refresh() {
        boolean hasIcon = iconB64 != null;
        boolean hasName = !txtName.getText().trim().isEmpty();

        // a finished card needs an icon and a name; drop the flag the moment either goes away
        chkReady.setEnabled(hasIcon && hasName);
        if (!chkReady.isEnabled() && chkReady.isSelected()) chkReady.setSelected(false);
        readyHint.setText(hasIcon && hasName
                ? "Ticking this is what lets the item publish."
                : (hasName ? "An icon is required \u2014 a default or the Random die is one click."
                           : "Give it a name first."));

        boolean ready = chkReady.isSelected();
        banner.setText(ready
                ? "This card is finished \u2014 the item can be published."
                : "Not publishable yet \u2014 finish the card below, then tick ready.");
        banner.setForeground(ready ? Theme.GREEN : Theme.AMBER);
        banner.setBackground(ready ? new Color(0x20, 0x2A, 0x1C) : Theme.AMBER_TINT);

        boolean target = host.canPublishNow();
        btnPublish.setEnabled(ready && target);
        btnPublish.setToolTipText(!ready
                ? "Tick \u201cCard finished\u201d first"
                : (target ? "Publishes to: " + host.publishTargetName()
                          : "No publish target right now \u2014 log in to the server "
                            + "(or pick a folder market) first, or just Save"));

        iconPreview.setIcon(MarketCard.decodeIcon(iconB64, 40));
        iconPreview.setToolTipText(hasIcon ? "Current icon" : "No icon yet");
        btnRemoveIcon.setEnabled(hasIcon);

        // mirror the controls into the preview listing and rebuild the live card
        preview.name = txtName.getText().trim();
        preview.version = ((Number) spVersion.getValue()).doubleValue();
        preview.kind = cmbKind.getSelectedIndex() == 1 ? "script" : "task";
        preview.vipOnly = chkVip.isSelected();
        preview.tags = parseTags();
        preview.description = txtDesc.getText().trim();
        preview.icon = iconB64;
        previewHolder.removeAll();
        previewHolder.add(new MarketCard(preview, MarketCard.Mode.GRID, INERT));
        previewHolder.revalidate();
        previewHolder.repaint();
    }

    /** Validates, writes the controls into the REAL listing, and saves through the host. */
    private boolean applyAndSave() {
        String name = txtName.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Give the card a name first.",
                    "Card builder", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if (chkReady.isSelected() && iconB64 == null) {   // belt & braces; the checkbox enforces it
            JOptionPane.showMessageDialog(this,
                    "A finished card needs an icon \u2014 pick a default or hit the die.",
                    "Card builder", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        listing.name = name;
        listing.version = ((Number) spVersion.getValue()).doubleValue();
        listing.kind = cmbKind.getSelectedIndex() == 1 ? "script" : "task";
        listing.vipOnly = chkVip.isSelected();
        listing.tags = parseTags();
        listing.description = txtDesc.getText().trim();
        listing.icon = iconB64;
        listing.cardReady = chkReady.isSelected();
        if (listing.bundle != null) {   // keep the bundle's own header in step with the card
            listing.bundle.name = listing.name;
            listing.bundle.version = listing.version;
            listing.bundle.description = listing.description;
        }
        try {
            host.saveListing(listing);
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Couldn't save the card: " + ex.getMessage(),
                    "Card builder", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private java.util.List<String> parseTags() {
        java.util.List<String> out = new ArrayList<>();
        for (String t : txtTags.getText().split(","))
            if (!t.trim().isEmpty()) out.add(t.trim());
        return out;
    }

    /** The drawn defaults as a 5-per-row popup of thumbnails. */
    private void showDefaultsPopup(JComponent anchor) {
        JPopupMenu pop = new JPopupMenu();
        JPanel grid = new JPanel(new GridLayout(0, 5, 4, 4));
        grid.setOpaque(false);
        grid.setBorder(new EmptyBorder(6, 6, 6, 6));
        for (CardIcons.DefaultIcon di : CardIcons.defaults()) {
            JButton b = new JButton(MarketCard.decodeIcon(di.base64, 36));
            b.setToolTipText(di.name);
            b.setMargin(new Insets(2, 2, 2, 2));
            b.setFocusPainted(false);
            b.addActionListener(e -> { setIcon(di.base64); pop.setVisible(false); });
            grid.add(b);
        }
        pop.add(grid);
        pop.show(anchor, 0, anchor.getHeight());
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Theme.TEXT);
        l.setFont(Theme.font(12));
        return l;
    }

    /** A field-for-field copy for the preview, so nothing real changes until Save. */
    private static ScriptListing previewCopy(ScriptListing l) {
        ScriptListing p = new ScriptListing();
        p.id = l.id;
        p.name = l.name;
        p.author = l.author;
        p.description = l.description;
        p.version = l.version;
        p.publishedAt = l.publishedAt;
        p.tags = l.tags == null ? new ArrayList<>() : new ArrayList<>(l.tags);
        p.icon = l.icon;
        p.kind = l.kind;
        p.vipOnly = l.vipOnly;
        p.bundle = l.bundle;     // shared ref is fine - the preview never writes through it
        p.origin = "local";
        return p;
    }

    /** Preview cards look real but do nothing - every callback is a no-op. */
    private static final MarketCard.Callbacks INERT = new MarketCard.Callbacks() {
        @Override public void onDownload(ScriptListing l) {}
        @Override public void onPublish(ScriptListing l) {}
        @Override public void onUnpublish(ScriptListing l, JComponent src) {}
        @Override public void onRate(ScriptListing l, int stars) {}
        @Override public void onToggleFavorite(ScriptListing l) {}
        @Override public void onToggleComments(ScriptListing l, MarketCard card) {}
        @Override public void onPostComment(ScriptListing l, String body, MarketCard card) {}
        @Override public void onSetIcon(ScriptListing l) {}
        @Override public void onBuildCard(ScriptListing l) {}
        @Override public void onDeleteLocal(ScriptListing l) {}
        @Override public void onContextMenu(ScriptListing l, MouseEvent e, JComponent src) {}
        @Override public boolean isOwn(ScriptListing l) { return false; }
        @Override public boolean canRate(ScriptListing l) { return false; }
        @Override public boolean canComment(ScriptListing l) { return false; }
    };
}
