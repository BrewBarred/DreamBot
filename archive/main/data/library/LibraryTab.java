package main.data.library;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * LibraryTab — compact Library panel designed to live inside your bot's tabbed menu.
 *
 * ── PURPOSE ──────────────────────────────────────────────────────────────────
 *
 *   Provides search, learning toggle, and quick-access to library entries
 *   from within the bot's existing UI, without taking over the whole screen.
 *
 *   The panel exposes a callback system so other parts of your script can
 *   react to library selections — e.g. when the user picks "Banker" from the
 *   list, you can auto-fill a Walk action's target field.
 *
 * ── PLUGGING IN ──────────────────────────────────────────────────────────────
 *
 *   // In your menu setup:
 *   LibraryTab libTab = new LibraryTab();
 *
 *   // React to user selecting an entry:
 *   libTab.onEntrySelected(entry -> {
 *       myActionTargetField.setText(entry.name);
 *   });
 *
 *   // React to user picking a tile:
 *   libTab.onTileSelected(tile -> {
 *       myWalkTargetField.setText(tile.getX() + ", " + tile.getY() + ", " + tile.getZ());
 *   });
 *
 *   tabbedPane.addTab("Library", libTab);
 *
 * ── BOT LOOP ─────────────────────────────────────────────────────────────────
 *
 *   @Override
 *   public int onLoop() {
 *       libTab.tick();   // drives learning engine
 *       return 300;
 *   }
 *
 * ── INTERFACE ────────────────────────────────────────────────────────────────
 *
 *   libTab.getSelectedName()    → String name of selected entry (null if none)
 *   libTab.getSelectedTile()    → resolves selected entry to a Tile (null if unresolvable)
 *   libTab.isLearning()         → true if learning engine is active
 *   libTab.setArea(String)      → programmatically set the current area label
 */
public class LibraryTab extends JPanel {

    // ── Palette — matches your existing menu style (neutral, adapts to system L&F)
    private static final Color ACCENT     = new Color(88,  166, 255);
    private static final Color GREEN      = new Color(63,  185, 80);
    private static final Color RED_CLR    = new Color(248, 81,  73);
    private static final Color NPC_CLR    = new Color(60,  130, 210);
    private static final Color OBJ_CLR    = new Color(50,  160, 70);
    private static final Color ITEM_CLR   = new Color(200, 130, 40);
    private static final Color DIM        = new Color(110, 118, 129);

    private static final Font MONO   = new Font(Font.MONOSPACED, Font.PLAIN, 11);
    private static final Font MONO_B = new Font(Font.MONOSPACED, Font.BOLD,  11);

    // ── Library ───────────────────────────────────────────────────────────────
    private final JLibrary lib = JLibrary.getInstance();

    // ── Search controls ───────────────────────────────────────────────────────
    private final JTextField   searchField  = new JTextField(18);
    private final JCheckBox    cbNpc        = check("NPC");
    private final JCheckBox    cbObj        = check("Obj");
    private final JCheckBox    cbItem       = check("Item");
    private final JCheckBox    cbCaseSens   = check("Aa");
    private final JCheckBox    cbFuzzy      = check("~");

    // ── Results list ──────────────────────────────────────────────────────────
    private final DefaultListModel<String> listModel  = new DefaultListModel<>();
    private final JList<String>            resultList = new JList<>(listModel);
    private final JLabel                   countLabel = new JLabel("0");

    // ── Detail row (below list) ───────────────────────────────────────────────
    private final JLabel detailLabel = new JLabel(" ");

    // ── Quick-use buttons ─────────────────────────────────────────────────────
    private final JButton btnCopyName = smallButton("Copy Name");
    private final JButton btnCopyTile = smallButton("Copy Tile");
    private final JButton btnUseAsTarget = accentButton("→ Use as Target");

    // ── Learning controls ─────────────────────────────────────────────────────
    private final JToggleButton btnLearn  = new JToggleButton("▶ Learn");
    private final JCheckBox     cbLNpc    = check("NPC");
    private final JCheckBox     cbLObj    = check("Obj");
    private final JCheckBox     cbLItem   = check("Item");
    private final JTextField    areaField = new JTextField("Unknown", 14);
    private final JLabel        learnStat = new JLabel("0 discovered");

    // ── Stats ─────────────────────────────────────────────────────────────────
    private final JLabel statsLabel = new JLabel("Loading…");

    // ── Callbacks ─────────────────────────────────────────────────────────────
    private Consumer<String>                               onNameSelected = null;
    private Consumer<org.dreambot.api.methods.map.Tile>   onTileSelected = null;

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    public LibraryTab() {
        setLayout(new BorderLayout(4, 4));
        setBorder(new EmptyBorder(8, 8, 8, 8));

        add(buildSearchPanel(),   BorderLayout.NORTH);
        add(buildResultsPanel(),  BorderLayout.CENTER);
        add(buildBottomPanel(),   BorderLayout.SOUTH);

        // Defaults
        cbNpc.setSelected(true);
        cbObj.setSelected(true);
        cbItem.setSelected(true);
        cbLNpc.setSelected(true);
        cbLObj.setSelected(true);
        cbLItem.setSelected(true);

        wireEvents();
        refreshResults();
        refreshStats();
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Drives the learning engine. Call from your bot's onLoop().
     */
    public void tick() {
        try {
            int found = lib.getLearner().tick();
            if (found > 0) {
                learnStat.setText(lib.getLearner().getTotalDiscovered() + " discovered");
                refreshStats();
                if (!searchField.getText().isEmpty()) refreshResults();
            }
        } catch (Exception ignored) {}
    }

    /**
     * Registers a callback invoked when the user selects an entry.
     * Receives the raw display string e.g. "NPC: Banker — Lumbridge".
     * Extract just the name with: label.replaceFirst("^(NPC|OBJ|ITEM): ", "").split(" — ")[0]
     */
    public void onEntrySelected(Consumer<String> callback) {
        this.onNameSelected = callback;
    }

    /**
     * Registers a callback invoked when the user clicks "→ Use as Target".
     * Receives the resolved Tile for the selected entry, or null if unresolvable.
     */
    public void onTileSelected(Consumer<org.dreambot.api.methods.map.Tile> callback) {
        this.onTileSelected = callback;
    }

    /** Returns the display string of the currently selected entry, or null. */
    public String getSelectedLabel() {
        return resultList.getSelectedValue();
    }

    /** Returns just the name portion of the selected entry, or null. */
    public String getSelectedName() {
        String label = getSelectedLabel();
        if (label == null) return null;
        return label.replaceFirst("^(NPC|OBJ|ITEM): ", "").split(" — ")[0].trim();
    }

    /**
     * Resolves the selected entry to a Tile.
     * Returns null if nothing is selected or the tile cannot be resolved.
     */
    public org.dreambot.api.methods.map.Tile getSelectedTile() {
        String name = getSelectedName();
        return name != null ? lib.resolveToTile(name) : null;
    }

    /** Returns true if the learning engine is currently active. */
    public boolean isLearning() { return lib.getLearner().isLearning(); }

    /** Programmatically set the current area label (e.g. from your bot's region detector). */
    public void setArea(String area) {
        areaField.setText(area);
        lib.getLearner().setCurrentArea(area);
    }

    // =========================================================================
    // LAYOUT BUILDERS
    // =========================================================================

    private JPanel buildSearchPanel() {
        JPanel p = new JPanel(new GridLayout(2, 1, 0, 3));
        p.setOpaque(false);

        // Row 1: search input
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row1.setOpaque(false);
        searchField.setFont(MONO);
        row1.add(searchField);

        JButton clearBtn = smallButton("✕");
        clearBtn.setPreferredSize(new Dimension(24, 22));
        clearBtn.addActionListener(e -> searchField.setText(""));
        row1.add(clearBtn);

        row1.add(pad(8));
        row1.add(countLabel);

        // Row 2: filter checkboxes
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row2.setOpaque(false);
        row2.add(miniLabel("SHOW:"));
        row2.add(cbNpc);
        row2.add(cbObj);
        row2.add(cbItem);
        row2.add(new JSeparator(SwingConstants.VERTICAL) {{
            setPreferredSize(new Dimension(1, 12));
        }});
        row2.add(miniLabel("MODE:"));
        row2.add(cbCaseSens);
        row2.add(cbFuzzy);

        p.add(row1);
        p.add(row2);
        return p;
    }

    private JPanel buildResultsPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);

        resultList.setFont(MONO);
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setCellRenderer(new TypeRenderer());

        JScrollPane scroll = new JScrollPane(resultList);
        scroll.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground")));

        detailLabel.setFont(MONO);
        detailLabel.setForeground(DIM);
        detailLabel.setBorder(new EmptyBorder(2, 4, 0, 0));

        p.add(scroll,       BorderLayout.CENTER);
        p.add(detailLabel,  BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildBottomPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);

        // Quick-action buttons
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        actionRow.setOpaque(false);
        actionRow.add(btnUseAsTarget);
        actionRow.add(btnCopyName);
        actionRow.add(btnCopyTile);

        // Learning panel
        JPanel learnPanel = buildLearnPanel();

        // Stats
        statsLabel.setFont(MONO);
        statsLabel.setForeground(DIM);

        p.add(actionRow,   BorderLayout.NORTH);
        p.add(learnPanel,  BorderLayout.CENTER);
        p.add(statsLabel,  BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildLearnPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Learning Engine",
                TitledBorder.LEFT, TitledBorder.TOP, MONO_B),
            new EmptyBorder(2, 4, 4, 4)
        ));
        p.setOpaque(false);

        GridBagConstraints g = new GridBagConstraints();
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(2, 2, 2, 4);

        // Toggle + area on same row
        g.gridx = 0; g.gridy = 0; g.gridwidth = 1;
        styleLearnToggle(false);
        p.add(btnLearn, g);

        g.gridx = 1;
        p.add(miniLabel("Area:"), g);
        g.gridx = 2; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1.0;
        areaField.setFont(MONO);
        p.add(areaField, g);

        // Types + stat
        g.gridx = 0; g.gridy = 1; g.fill = GridBagConstraints.NONE; g.weightx = 0; g.gridwidth = 3;
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row2.setOpaque(false);
        row2.add(miniLabel("Scan:"));
        row2.add(cbLNpc);
        row2.add(cbLObj);
        row2.add(cbLItem);
        row2.add(new JSeparator(SwingConstants.VERTICAL) {{ setPreferredSize(new Dimension(1,12)); }});
        learnStat.setFont(MONO);
        learnStat.setForeground(DIM);
        row2.add(learnStat);
        p.add(row2, g);

        return p;
    }

    // =========================================================================
    // EVENT WIRING
    // =========================================================================

    private void wireEvents() {
        // Live filter
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { refreshResults(); }
            public void removeUpdate(DocumentEvent e)  { refreshResults(); }
            public void changedUpdate(DocumentEvent e) { refreshResults(); }
        });

        for (JCheckBox cb : new JCheckBox[]{cbNpc, cbObj, cbItem, cbCaseSens, cbFuzzy})
            cb.addActionListener(e -> refreshResults());

        // Selection → detail label + callback
        resultList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            String selected = resultList.getSelectedValue();
            updateDetailLabel(selected);
            if (onNameSelected != null && selected != null)
                onNameSelected.accept(selected);
        });

        // Quick-use buttons
        btnUseAsTarget.addActionListener(e -> {
            if (onTileSelected != null) {
                org.dreambot.api.methods.map.Tile tile = getSelectedTile();
                onTileSelected.accept(tile);
            }
        });

        btnCopyName.addActionListener(e -> {
            String name = getSelectedName();
            if (name != null) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(name), null);
            }
        });

        btnCopyTile.addActionListener(e -> {
            try {
                org.dreambot.api.methods.map.Tile tile = getSelectedTile();
                if (tile != null) {
                    String tileStr = tile.getX() + ", " + tile.getY() + ", " + tile.getZ();
                    Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new java.awt.datatransfer.StringSelection(tileStr), null);
                }
            } catch (Exception ignored) {}
        });

        // Learning toggle
        btnLearn.addActionListener(e -> {
            boolean on = btnLearn.isSelected();
            styleLearnToggle(on);
            applyLearnerConfig(on);
        });

        areaField.addActionListener(e ->
            lib.getLearner().setCurrentArea(areaField.getText().trim()));
    }

    // =========================================================================
    // SEARCH / REFRESH
    // =========================================================================

    private void refreshResults() {
        String q = searchField.getText().trim();
        boolean caseSens = cbCaseSens.isSelected();
        boolean fuzzy    = cbFuzzy.isSelected();

        List<String> results;

        if (fuzzy && q.length() >= 1) {
            results = lib.fuzzySearch(q, 80);
        } else {
            // Build target type filter
            List<JLibrary.TargetType> types = new ArrayList<>();
            if (cbNpc.isSelected())  types.add(JLibrary.TargetType.NPC);
            if (cbObj.isSelected())  types.add(JLibrary.TargetType.GAME_OBJECT);
            if (cbItem.isSelected()) types.add(JLibrary.TargetType.GROUND_ITEM);

            if (caseSens) {
                // Manual case-sensitive filter (Library.search is case-insensitive by default)
                results = new ArrayList<>();
                if (cbNpc.isSelected())
                    lib.getAllNpcs().stream()
                        .filter(n -> n.name.contains(q))
                        .forEach(n -> results.add("NPC: " + n.name + " — " + n.area));
                if (cbObj.isSelected())
                    lib.getAllObjects().stream()
                        .filter(o -> o.name.contains(q))
                        .forEach(o -> results.add("OBJ: " + o.name + " — " + o.area));
                if (cbItem.isSelected())
                    lib.getAllGroundItems().stream()
                        .filter(i -> i.name.contains(q))
                        .forEach(i -> results.add("ITEM: " + i.name));
            } else {
                results = lib.search(q, types.toArray(new JLibrary.TargetType[0]));
            }
        }

        listModel.clear();
        results.forEach(listModel::addElement);
        countLabel.setFont(MONO);
        countLabel.setForeground(DIM);
        countLabel.setText(results.size() + " results");
    }

    private void refreshStats() {
        statsLabel.setText("NPCs: " + lib.getAllNpcs().size()
            + "  Obj: " + lib.getAllObjects().size()
            + "  Items: " + lib.getAllGroundItems().size());
    }

    private void updateDetailLabel(String selected) {
        if (selected == null) { detailLabel.setText(" "); return; }

        if (selected.startsWith("NPC: ")) {
            String name = selected.substring(5).split(" — ")[0].trim();
            NpcEntry e = lib.getNpc(name);
            if (e != null) {
                detailLabel.setText("Tile: " + e.tile.getX() + ", " + e.tile.getY()
                    + ", " + e.tile.getZ() + "  ·  " + e.interaction);
                return;
            }
        } else if (selected.startsWith("OBJ: ")) {
            String name = selected.substring(5).split(" — ")[0].trim();
            ObjectEntry e = lib.getObject(name);
            if (e != null) {
                detailLabel.setText("Tile: " + e.tile.getX() + ", " + e.tile.getY()
                    + ", " + e.tile.getZ() + "  ·  " + e.interaction);
                return;
            }
        } else if (selected.startsWith("ITEM: ")) {
            String name = selected.substring(6).split(" @ ")[0].trim();
            GroundItemEntry e = lib.getGroundItem(name);
            if (e != null) {
                detailLabel.setText("Spawn: " + e.spawnTile.getX() + ", " + e.spawnTile.getY()
                    + "  ·  Respawn: " + (e.respawnSeconds < 0 ? "unknown" : e.respawnSeconds + "s"));
                return;
            }
        }
        detailLabel.setText(" ");
    }

    // =========================================================================
    // LEARNING
    // =========================================================================

    private void applyLearnerConfig(boolean on) {
        LearningEngine learner = lib.getLearner();
        learner.setCurrentArea(areaField.getText().trim());

        EnumSet<LearningEngine.ScanType> types = EnumSet.noneOf(LearningEngine.ScanType.class);
        if (cbLNpc.isSelected())  types.add(LearningEngine.ScanType.NPCS);
        if (cbLObj.isSelected())  types.add(LearningEngine.ScanType.OBJECTS);
        if (cbLItem.isSelected()) types.add(LearningEngine.ScanType.GROUND_ITEMS);
        learner.setEnabledTypes(types);
        learner.setLearning(on);
    }

    // =========================================================================
    // COMPONENT FACTORIES
    // =========================================================================

    private static JCheckBox check(String text) {
        JCheckBox cb = new JCheckBox(text);
        cb.setFont(MONO);
        cb.setFocusPainted(false);
        cb.setOpaque(false);
        return cb;
    }

    private static JButton smallButton(String text) {
        JButton b = new JButton(text);
        b.setFont(MONO);
        b.setFocusPainted(false);
        b.setMargin(new Insets(2, 8, 2, 8));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static JButton accentButton(String text) {
        JButton b = new JButton(text);
        b.setFont(MONO_B);
        b.setForeground(new Color(0, 80, 160));
        b.setFocusPainted(false);
        b.setMargin(new Insets(3, 10, 3, 10));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static JLabel miniLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(MONO_B);
        l.setForeground(DIM);
        return l;
    }

    private static Component pad(int width) {
        return Box.createHorizontalStrut(width);
    }

    private void styleLearnToggle(boolean on) {
        if (on) {
            btnLearn.setText("⏹ Stop");
            btnLearn.setForeground(GREEN);
        } else {
            btnLearn.setText("▶ Learn");
            btnLearn.setForeground(DIM);
        }
        btnLearn.setFont(MONO_B);
        btnLearn.setFocusPainted(false);
        btnLearn.setMargin(new Insets(3, 8, 3, 8));
    }

    // =========================================================================
    // LIST CELL RENDERER
    // =========================================================================

    private static class TypeRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            setFont(MONO);
            if (!selected && value instanceof String) {
                String s = (String) value;
                if      (s.startsWith("NPC: "))  setForeground(NPC_CLR);
                else if (s.startsWith("OBJ: "))  setForeground(OBJ_CLR);
                else if (s.startsWith("ITEM: ")) setForeground(ITEM_CLR);
                else setForeground(list.getForeground());
            }
            return this;
        }
    }
}
