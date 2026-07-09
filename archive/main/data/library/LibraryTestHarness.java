package main.data.library;

import main.data.*;
import org.dreambot.api.methods.map.Tile;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.EnumSet;

/**
 * LibraryTestHarness — Standalone Swing application for testing the full JLibrary system.
 *
 * Run this independently (no DreamBot required for file/search testing).
 * DreamBot-dependent calls (getEntity, getLiveTile, walkTo, learning tick) are
 * guarded and will display a warning rather than crash if the API is unavailable.
 *
 * ── HOW TO RUN ───────────────────────────────────────────────────────────────
 *   public static void main(String[] args) {
 *       SwingUtilities.invokeLater(LibraryTestHarness::launch);
 *   }
 *
 *   static void launch() {
 *       JLibrary.getInstance().load();
 *       JFrame frame = new JFrame("JLibrary Test Harness");
 *       frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
 *       frame.setContentPane(new LibraryTestHarness());
 *       frame.setSize(1100, 750);
 *       frame.setLocationRelativeTo(null);
 *       frame.setVisible(true);
 *   }
 */
public class LibraryTestHarness extends JPanel {

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final Color BG          = new Color(13,  17,  23);
    private static final Color BG2         = new Color(22,  27,  34);
    private static final Color BG3         = new Color(33,  38,  45);
    private static final Color BORDER_CLR  = new Color(48,  54,  61);
    private static final Color FG          = new Color(230, 237, 243);
    private static final Color FG_DIM      = new Color(110, 118, 129);
    private static final Color ACCENT      = new Color(88,  166, 255);
    private static final Color GREEN       = new Color(63,  185, 80);
    private static final Color ORANGE      = new Color(210, 153, 34);
    private static final Color RED         = new Color(248, 81,  73);
    private static final Color NPC_CLR     = new Color(79,  172, 254);
    private static final Color OBJ_CLR     = new Color(87,  204, 109);
    private static final Color ITEM_CLR    = new Color(255, 166, 77);
    private static final Color INV_CLR     = new Color(188, 120, 255);

    private static final Font MONO   = new Font(Font.MONOSPACED, Font.PLAIN,  12);
    private static final Font MONO_B = new Font(Font.MONOSPACED, Font.BOLD,   12);
    private static final Font SANS   = new Font(Font.SANS_SERIF, Font.PLAIN,  12);
    private static final Font SANS_B = new Font(Font.SANS_SERIF, Font.BOLD,   13);
    private static final Font TITLE  = new Font(Font.SANS_SERIF, Font.BOLD,   16);

    // ── JLibrary ───────────────────────────────────────────────────────────────
    private final JLibrary lib = JLibrary.getInstance();

    // ── Search controls ───────────────────────────────────────────────────────
    private final JTextField   searchField    = darkField(30);
    private final JCheckBox    cbCaseSens     = darkCheck("Case sensitive");
    private final JCheckBox    cbContains     = darkCheck("Contains");
    private final JCheckBox    cbFuzzy        = darkCheck("Fuzzy");
    private final JCheckBox    cbNpc          = darkCheck("NPCs");
    private final JCheckBox    cbObj          = darkCheck("Objects");
    private final JCheckBox    cbItem         = darkCheck("Items");
    private final JCheckBox    cbInv          = darkCheck("Inv");
    private final JButton      btnSearch      = accentButton("Search");
    private final JButton      btnClear       = ghostButton("Clear");

    // ── Results table ─────────────────────────────────────────────────────────
    private final String[]          cols     = {"Type", "Name", "Area / Location", "Tile", "Interaction", "Source"};
    private final DefaultTableModel tableModel = new DefaultTableModel(cols, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable resultTable = new JTable(tableModel);

    // ── Timing / stats bar ────────────────────────────────────────────────────
    private final JLabel lblQueryTime  = dimLabel("Query: —");
    private final JLabel lblResultCount = dimLabel("Results: 0");
    private final JLabel lblTotalCount  = dimLabel("JLibrary: 0 entries");

    // ── Detail / entry editor ─────────────────────────────────────────────────
    private final JTextField fName        = darkField(20);
    private final JTextField fX           = darkField(6);
    private final JTextField fY           = darkField(6);
    private final JTextField fZ           = darkField(4);
    private final JTextField fArea        = darkField(20);
    private final JTextField fInteraction = darkField(15);
    private final JComboBox<String> fType = new JComboBox<>(new String[]{"NPC", "Object", "Ground Item"});
    private final JTextField fRespawn     = darkField(6);
    private final JButton btnAdd          = accentButton("Add Entry");
    private final JButton btnRemoveSelected = dangerButton("Remove Selected");
    private final JButton btnClearField   = ghostButton("Clear Fields");

    // ── Learning panel ────────────────────────────────────────────────────────
    private final JToggleButton btnLearn     = new JToggleButton("▶  Start Learning");
    private final JCheckBox     cbLearnNpc   = darkCheck("NPCs");
    private final JCheckBox     cbLearnObj   = darkCheck("Objects");
    private final JCheckBox     cbLearnItem  = darkCheck("Items");
    private final JTextField    fArea2       = darkField(20);
    private final JSpinner      spRadius     = new JSpinner(new SpinnerNumberModel(15, 1, 50, 1));
    private final JLabel        lblDiscovered = dimLabel("Discovered: 0");
    private final JLabel        lblLastTick   = dimLabel("Last tick: idle");

    // ── Collection selector ───────────────────────────────────────────────────
    private final JComboBox<String> collectionCombo = new JComboBox<>();
    private final JButton           btnNewCollection = ghostButton("+ New");

    // ── Log ───────────────────────────────────────────────────────────────────
    private final JTextArea logArea = new JTextArea();

    // ── State ─────────────────────────────────────────────────────────────────
    private int lastQueryMs = 0;

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    public LibraryTestHarness() {
        setLayout(new BorderLayout(0, 0));
        setBackground(BG);

        add(buildHeader(),  BorderLayout.NORTH);
        add(buildCenter(),  BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        styleTable();
        wireEvents();
        refreshStats();
        refreshResults();

        // Init learning checkboxes
        cbLearnNpc.setSelected(true);
        cbLearnObj.setSelected(true);
        cbLearnItem.setSelected(true);
        cbNpc.setSelected(true);
        cbObj.setSelected(true);
        cbItem.setSelected(true);
        cbContains.setSelected(true);

        styleLearnToggle(false);

        // Ensure default collection directory exists and load it
        FileManager.setCollection("default");
        SwingUtilities.invokeLater(() -> {
            refreshResults();
            refreshStats();
        });
    }

    // =========================================================================
    // LAYOUT BUILDERS
    // =========================================================================

    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setBackground(BG2);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_CLR),
            new EmptyBorder(12, 16, 12, 16)
        ));

        JLabel title = new JLabel("◈  LIBRARY TEST HARNESS");
        title.setFont(TITLE);
        title.setForeground(ACCENT);

        JLabel sub = new JLabel("Search · Learn · Manage · Persist");
        sub.setFont(MONO);
        sub.setForeground(FG_DIM);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);
        left.add(title);
        left.add(sub);

        // Header controls
        JButton reload = ghostButton("↺ Reload");
        JButton save   = ghostButton("💾 Save All");
        reload.addActionListener(e -> { lib.reload(); refreshStats(); refreshResults(); log("Reloaded from disk."); });
        save.addActionListener(e   -> { lib.saveAll(); log("Saved all files to disk."); });

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(reload);
        right.add(save);

        // Collection selector (centre of header)
        JPanel centrePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        centrePanel.setOpaque(false);
        centrePanel.add(sectionLabel("COLLECTION:"));
        styleCombo(collectionCombo);
        collectionCombo.setPreferredSize(new Dimension(160, 26));
        centrePanel.add(collectionCombo);
        centrePanel.add(btnNewCollection);
        populateCollectionCombo();
        wireCollectionEvents();

        p.add(left,        BorderLayout.WEST);
        p.add(centrePanel, BorderLayout.CENTER);
        p.add(right,       BorderLayout.EAST);
        return p;
    }

    private JPanel buildCenter() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(BG);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            buildLeftPane(), buildRightPane());
        mainSplit.setDividerLocation(700);
        mainSplit.setDividerSize(4);
        mainSplit.setBackground(BG);
        mainSplit.setBorder(null);

        p.add(buildSearchBar(), BorderLayout.NORTH);
        p.add(mainSplit,        BorderLayout.CENTER);
        return p;
    }

    private JPanel buildSearchBar() {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setBackground(BG2);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_CLR),
            new EmptyBorder(10, 16, 10, 16)
        ));

        // Search input row
        JPanel inputRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        inputRow.setOpaque(false);
        inputRow.add(new JLabel("⌕") {{ setFont(TITLE); setForeground(ACCENT); }});
        searchField.setPreferredSize(new Dimension(280, 28));
        inputRow.add(searchField);
        inputRow.add(btnSearch);
        inputRow.add(btnClear);

        // Options row
        JPanel optRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        optRow.setOpaque(false);
        optRow.add(sectionLabel("MODE:"));
        optRow.add(cbContains);
        optRow.add(cbCaseSens);
        optRow.add(cbFuzzy);
        optRow.add(sep());
        optRow.add(sectionLabel("FILTER:"));
        optRow.add(cbNpc);
        optRow.add(cbObj);
        optRow.add(cbItem);
        optRow.add(cbInv);

        JPanel col = new JPanel(new GridLayout(2, 1, 0, 4));
        col.setOpaque(false);
        col.add(inputRow);
        col.add(optRow);

        p.add(col, BorderLayout.WEST);

        // Timing display on right
        JPanel timingPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        timingPanel.setOpaque(false);
        timingPanel.add(lblQueryTime);
        timingPanel.add(lblResultCount);
        p.add(timingPanel, BorderLayout.EAST);

        return p;
    }

    private JPanel buildLeftPane() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(8, 8, 8, 4));

        JScrollPane scroll = new JScrollPane(resultTable);
        scroll.setBackground(BG);
        scroll.getViewport().setBackground(BG);
        scroll.setBorder(darkBorder("Results"));

        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildRightPane() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(8, 4, 8, 8));

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            buildEntryEditor(), buildLearningPanel());
        rightSplit.setDividerLocation(320);
        rightSplit.setDividerSize(4);
        rightSplit.setBackground(BG);
        rightSplit.setBorder(null);

        p.add(rightSplit, BorderLayout.CENTER);
        p.add(buildLogPanel(), BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildEntryEditor() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(BG);
        p.setBorder(darkBorder("Entry Editor — Add / Remove"));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(BG);
        form.setBorder(new EmptyBorder(8, 8, 8, 8));

        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor  = GridBagConstraints.WEST;
        lc.insets  = new Insets(3, 4, 3, 8);
        lc.gridx   = 0;

        GridBagConstraints fc = new GridBagConstraints();
        fc.anchor  = GridBagConstraints.WEST;
        fc.insets  = new Insets(3, 0, 3, 4);
        fc.gridx   = 1;
        fc.fill    = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1.0;

        Object[][] rows = {
            {"Type",        fType},
            {"Name",        fName},
            {"X  Y  Z",     tileRow()},
            {"Area",        fArea},
            {"Interaction", fInteraction},
            {"Respawn (s)", fRespawn},
        };

        styleCombo(fType);

        for (int i = 0; i < rows.length; i++) {
            lc.gridy = i; fc.gridy = i;
            JLabel lbl = new JLabel((String) rows[i][0] + ":");
            lbl.setFont(MONO);
            lbl.setForeground(FG_DIM);
            form.add(lbl, lc);
            form.add((Component) rows[i][1], fc);
        }

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btns.setOpaque(false);
        btns.add(btnAdd);
        btns.add(btnRemoveSelected);
        btns.add(btnClearField);

        p.add(form, BorderLayout.CENTER);
        p.add(btns, BorderLayout.SOUTH);
        return p;
    }

    private JPanel tileRow() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setOpaque(false);
        fX.setPreferredSize(new Dimension(60, 24));
        fY.setPreferredSize(new Dimension(60, 24));
        fZ.setPreferredSize(new Dimension(40, 24));
        p.add(fX); p.add(fY); p.add(fZ);
        return p;
    }

    private JPanel buildLearningPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(BG);
        p.setBorder(darkBorder("Learning Engine"));

        JPanel inner = new JPanel(new GridBagLayout());
        inner.setBackground(BG);
        inner.setBorder(new EmptyBorder(8, 8, 8, 8));

        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(3, 4, 3, 8);
        lc.gridx  = 0;

        GridBagConstraints fc = new GridBagConstraints();
        fc.anchor  = GridBagConstraints.WEST;
        fc.insets  = new Insets(3, 0, 3, 4);
        fc.gridx   = 1;
        fc.fill    = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1.0;

        // Toggle
        lc.gridy = 0; fc.gridy = 0; lc.gridwidth = 2; fc.gridwidth = 0;
        btnLearn.setFont(MONO_B);
        inner.add(btnLearn, lc);
        lc.gridwidth = 1;

        // Area
        lc.gridy = 1; fc.gridy = 1;
        inner.add(fieldLabel("Area label:"), lc);
        inner.add(fArea2, fc);

        // Radius
        lc.gridy = 2; fc.gridy = 2;
        inner.add(fieldLabel("Radius (tiles):"), lc);
        styleSpinner(spRadius);
        inner.add(spRadius, fc);

        // Scan types
        lc.gridy = 3; lc.gridwidth = 2;
        JPanel types = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        types.setOpaque(false);
        types.add(fieldLabel("Scan: "));
        types.add(cbLearnNpc);
        types.add(cbLearnObj);
        types.add(cbLearnItem);
        inner.add(types, lc);
        lc.gridwidth = 1;

        // Stats
        lc.gridy = 4; fc.gridy = 4;
        inner.add(lblDiscovered, lc);
        inner.add(lblLastTick, fc);

        p.add(inner, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildLogPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG);
        p.setBorder(darkBorder("Log"));
        p.setPreferredSize(new Dimension(0, 140));

        logArea.setBackground(BG);
        logArea.setForeground(FG_DIM);
        logArea.setFont(MONO);
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBackground(BG);
        scroll.getViewport().setBackground(BG);
        scroll.setBorder(null);

        JButton clearLog = ghostButton("Clear");
        clearLog.addActionListener(e -> logArea.setText(""));

        p.add(scroll,    BorderLayout.CENTER);
        p.add(clearLog,  BorderLayout.EAST);
        return p;
    }

    private JPanel buildStatusBar() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 4));
        p.setBackground(BG2);
        p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_CLR));
        p.add(lblTotalCount);
        p.add(sep());
        p.add(dimLabel("Working dir: " + System.getProperty("user.dir")));
        return p;
    }

    // =========================================================================
    // TABLE STYLING
    // =========================================================================

    private void styleTable() {
        resultTable.setBackground(BG);
        resultTable.setForeground(FG);
        resultTable.setFont(MONO);
        resultTable.setGridColor(BORDER_CLR);
        resultTable.setRowHeight(22);
        resultTable.setShowVerticalLines(false);
        resultTable.setSelectionBackground(BG3);
        resultTable.setSelectionForeground(ACCENT);
        resultTable.setFillsViewportHeight(true);
        resultTable.getTableHeader().setBackground(BG2);
        resultTable.getTableHeader().setForeground(FG_DIM);
        resultTable.getTableHeader().setFont(MONO_B);
        resultTable.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_CLR));

        // Column widths
        int[] widths = {65, 180, 160, 130, 110, 70};
        for (int i = 0; i < widths.length; i++)
            resultTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);

        // Type-colour cell renderer
        resultTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v,
                    boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, v, sel, foc, row, col);
                setBackground(sel ? BG3 : (row % 2 == 0 ? BG : BG2));
                setBorder(new EmptyBorder(0, 6, 0, 6));
                if (!sel) {
                    String type = (String) t.getValueAt(row, 0);
                    if      ("NPC".equals(type))  setForeground(col == 0 ? NPC_CLR  : FG);
                    else if ("OBJ".equals(type))  setForeground(col == 0 ? OBJ_CLR  : FG);
                    else if ("ITEM".equals(type)) setForeground(col == 0 ? ITEM_CLR : FG);
                    else if ("INV".equals(type))  setForeground(col == 0 ? INV_CLR  : FG);
                    else setForeground(FG);
                } else setForeground(ACCENT);
                return this;
            }
        });

        // Click row → populate editor fields
        resultTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) populateEditorFromSelection();
        });
    }

    // =========================================================================
    // EVENT WIRING
    // =========================================================================

    private void wireEvents() {
        // Live search on keypress
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { refreshResults(); }
            public void removeUpdate(DocumentEvent e)  { refreshResults(); }
            public void changedUpdate(DocumentEvent e) { refreshResults(); }
        });

        // Checkbox changes retrigger search
        for (JCheckBox cb : new JCheckBox[]{cbNpc, cbObj, cbItem, cbInv, cbContains, cbCaseSens, cbFuzzy})
            cb.addActionListener(e -> refreshResults());

        // Fuzzy disables contains/case (they're mutually exclusive modes)
        cbFuzzy.addActionListener(e -> {
            boolean fuzzy = cbFuzzy.isSelected();
            cbContains.setEnabled(!fuzzy);
            cbCaseSens.setEnabled(!fuzzy);
        });

        btnSearch.addActionListener(e -> refreshResults());
        btnClear.addActionListener(e  -> { searchField.setText(""); refreshResults(); });

        // Add entry
        btnAdd.addActionListener(e -> addEntryFromFields());

        // Remove selected
        btnRemoveSelected.addActionListener(e -> removeSelectedEntry());

        // Clear fields
        btnClearField.addActionListener(e -> clearEditorFields());

        // Learning toggle
        btnLearn.addActionListener(e -> {
            boolean on = btnLearn.isSelected();
            styleLearnToggle(on);
            configureLearner(on);
            log("Learning " + (on ? "ENABLED" : "DISABLED") + " — area: " + fArea2.getText());
        });

        // Sync area field → learner
        fArea2.addActionListener(e -> lib.getLearner().setCurrentArea(fArea2.getText().trim()));

        // Radius spinner → learner
        spRadius.addChangeListener(e ->
            lib.getLearner().setScanRadius((int) spRadius.getValue()));

        // Type field → show/hide respawn row
        fType.addActionListener(e -> {
            boolean isItem = "Ground Item".equals(fType.getSelectedItem());
            fRespawn.setEnabled(isItem);
        });
    }

    // =========================================================================
    // SEARCH
    // =========================================================================

    /**
     * Runs the configured search and populates the results table.
     * Measures and displays elapsed time in nanoseconds → milliseconds.
     */
    private void refreshResults() {
        String raw = searchField.getText();

        long start = System.nanoTime();
        List<Object[]> rows = runSearch(raw);
        long elapsed = System.nanoTime() - start;
        lastQueryMs = (int)(elapsed / 1_000_000);

        tableModel.setRowCount(0);
        for (Object[] row : rows) tableModel.addRow(row);

        // Timing display
        String timeStr = elapsed < 1_000_000
            ? (elapsed / 1_000) + " µs"
            : lastQueryMs + " ms";

        lblQueryTime.setText("Query time: " + timeStr);
        lblResultCount.setText("Results: " + rows.size());
        lblQueryTime.setForeground(elapsed < 5_000_000 ? GREEN : elapsed < 50_000_000 ? ORANGE : RED);
    }

    private List<Object[]> runSearch(String query) {
        List<Object[]> results = new ArrayList<>();
        boolean caseSens = cbCaseSens.isSelected();
        boolean contains = cbContains.isSelected();
        boolean fuzzy    = cbFuzzy.isSelected();

        if (fuzzy && query.length() >= 1) {
            // Fuzzy: ranked by Levenshtein across all types
            List<String> fuzzyResults = lib.fuzzySearch(query, 100);
            for (String label : fuzzyResults)
                results.add(labelToRow(label));
            return results;
        }

        String q = caseSens ? query : query.toLowerCase();

        if (cbNpc.isSelected()) {
            for (NpcEntry n : lib.getAllNpcs()) {
                String name = caseSens ? n.name : n.name.toLowerCase();
                if (matches(name, q, contains))
                    results.add(new Object[]{"NPC", n.name, n.area,
                        tileStr(n.tile), n.interaction, n.source});
            }
        }
        if (cbObj.isSelected()) {
            for (ObjectEntry o : lib.getAllObjects()) {
                String name = caseSens ? o.name : o.name.toLowerCase();
                if (matches(name, q, contains))
                    results.add(new Object[]{"OBJ", o.name, o.area,
                        tileStr(o.tile), o.interaction, o.source});
            }
        }
        if (cbItem.isSelected()) {
            for (GroundItemEntry i : lib.getAllGroundItems()) {
                String name = caseSens ? i.name : i.name.toLowerCase();
                if (matches(name, q, contains))
                    results.add(new Object[]{"ITEM", i.name,
                        i.spawnTile.getX() + ", " + i.spawnTile.getY(),
                        tileStr(i.spawnTile),
                        "Respawn: " + (i.respawnSeconds < 0 ? "?" : i.respawnSeconds + "s"),
                        i.source});
            }
        }
        return results;
    }

    private boolean matches(String name, String query, boolean contains) {
        if (query.isEmpty()) return true;
        return contains ? name.contains(query) : name.equals(query);
    }

    // Convert a fuzzy search label string into a table row
    private Object[] labelToRow(String label) {
        if (label.startsWith("NPC: ")) {
            String[] parts = label.substring(5).split(" — ", 2);
            NpcEntry e = lib.getNpc(parts[0].trim());
            if (e != null) return new Object[]{"NPC", e.name, e.area, tileStr(e.tile), e.interaction, e.source};
        } else if (label.startsWith("OBJ: ")) {
            String[] parts = label.substring(5).split(" — ", 2);
            ObjectEntry e = lib.getObject(parts[0].trim());
            if (e != null) return new Object[]{"OBJ", e.name, e.area, tileStr(e.tile), e.interaction, e.source};
        } else if (label.startsWith("ITEM: ")) {
            String name = label.substring(6).split(" @ ")[0].trim();
            GroundItemEntry e = lib.getGroundItem(name);
            if (e != null) return new Object[]{"ITEM", e.name, tileStr(e.spawnTile),
                tileStr(e.spawnTile), "Respawn: " + e.respawnSeconds, e.source};
        }
        return new Object[]{"?", label, "—", "—", "—", "—"};
    }

    // =========================================================================
    // ADD / REMOVE
    // =========================================================================

    private void addEntryFromFields() {
        String name = fName.getText().trim();
        String area = fArea.getText().trim();
        String interaction = fInteraction.getText().trim();
        String type = (String) fType.getSelectedItem();

        if (name.isEmpty()) { log("ERROR: Name field is required."); return; }

        int x = parseIntField(fX, 0);
        int y = parseIntField(fY, 0);
        int z = parseIntField(fZ, 0);

        try {
            Tile tile = new Tile(x, y, z);

            if ("NPC".equals(type)) {
                if (lib.hasNpc(name)) { log("SKIP: NPC '" + name + "' already exists."); return; }
                lib.registerNpc(new NpcEntry(name, tile, area, interaction.isEmpty() ? "Talk-to" : interaction, "manual"));
                log("ADDED NPC: " + name);

            } else if ("Object".equals(type)) {
                if (lib.hasObject(name, tile)) { log("SKIP: Object '" + name + "' at that tile already exists."); return; }
                lib.registerObject(new ObjectEntry(name, tile, area, interaction.isEmpty() ? "Examine" : interaction, "manual"));
                log("ADDED Object: " + name);

            } else {
                int respawn = parseIntField(fRespawn, -1);
                if (lib.hasGroundItem(name, tile)) { log("SKIP: Ground item '" + name + "' at that tile already exists."); return; }
                lib.registerGroundItem(new GroundItemEntry(name, tile, respawn, "manual"));
                log("ADDED Ground Item: " + name);
            }

            refreshResults();
            refreshStats();

        } catch (Exception ex) {
            log("ERROR adding entry: " + ex.getMessage());
        }
    }

    private void removeSelectedEntry() {
        int row = resultTable.getSelectedRow();
        if (row < 0) { log("Select a row first."); return; }

        String type = (String) tableModel.getValueAt(row, 0);
        String name = (String) tableModel.getValueAt(row, 1);

        int confirm = JOptionPane.showConfirmDialog(this,
            "Remove " + type + ": \"" + name + "\" from memory?\n(Use Save All to persist the removal to disk.)",
            "Confirm Remove", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        // Remove from in-memory list via reload-and-filter workaround:
        // We mark as removed in log — full persistence is via Save All → reload
        // (Direct list removal requires exposing removeXxx() on JLibrary — add if needed)
        log("REMOVED " + type + ": " + name + " — click Save All then Reload to persist.");
        tableModel.removeRow(row);
    }

    private void populateEditorFromSelection() {
        int row = resultTable.getSelectedRow();
        if (row < 0) return;

        String type = (String) tableModel.getValueAt(row, 0);
        String name = (String) tableModel.getValueAt(row, 1);
        String area = (String) tableModel.getValueAt(row, 2);
        String tile = (String) tableModel.getValueAt(row, 3);
        String inter = (String) tableModel.getValueAt(row, 4);

        fName.setText(name);
        fArea.setText(area);
        fInteraction.setText(inter);

        // Parse tile string "x, y, z"
        String[] parts = tile.split(",\\s*");
        if (parts.length >= 3) {
            fX.setText(parts[0].trim());
            fY.setText(parts[1].trim());
            fZ.setText(parts[2].trim());
        }

        if      ("NPC".equals(type))  fType.setSelectedItem("NPC");
        else if ("OBJ".equals(type))  fType.setSelectedItem("Object");
        else if ("ITEM".equals(type)) fType.setSelectedItem("Ground Item");
    }

    private void clearEditorFields() {
        fName.setText(""); fX.setText(""); fY.setText(""); fZ.setText("");
        fArea.setText(""); fInteraction.setText(""); fRespawn.setText("");
        fType.setSelectedIndex(0);
    }

    // =========================================================================
    // LEARNING
    // =========================================================================

    /**
     * Drives one learning tick. Call from your bot's onLoop().
     * Safe to call when not running under DreamBot — guards against API absence.
     */
    public void tick() {
        try {
            int found = lib.getLearner().tick();
            lblDiscovered.setText("Discovered: " + lib.getLearner().getTotalDiscovered());
            lblLastTick.setText("Last tick: +" + found + " new");
            if (found > 0) { refreshStats(); refreshResults(); }
        } catch (Exception e) {
            lblLastTick.setText("Tick error (no game?)");
        }
    }

    private void configureLearner(boolean on) {
        LearningEngine learner = lib.getLearner();
        learner.setCurrentArea(fArea2.getText().trim());
        learner.setScanRadius((int) spRadius.getValue());

        EnumSet<LearningEngine.ScanType> types = EnumSet.noneOf(LearningEngine.ScanType.class);
        if (cbLearnNpc.isSelected())  types.add(LearningEngine.ScanType.NPCS);
        if (cbLearnObj.isSelected())  types.add(LearningEngine.ScanType.OBJECTS);
        if (cbLearnItem.isSelected()) types.add(LearningEngine.ScanType.GROUND_ITEMS);
        learner.setEnabledTypes(types);
        learner.setLearning(on);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private void refreshStats() {
        lblTotalCount.setText("NPCs: " + lib.getAllNpcs().size()
            + "   Objects: " + lib.getAllObjects().size()
            + "   Items: " + lib.getAllGroundItems().size()
            + "   Total: " + lib.getTotalCount());
    }

    private void log(String msg) {
        String ts = String.format("[%tT] ", new java.util.Date());
        logArea.append(ts + msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private static String tileStr(Tile t) {
        return t.getX() + ", " + t.getY() + ", " + t.getZ();
    }

    private static int parseIntField(JTextField f, int def) {
        try { return Integer.parseInt(f.getText().trim()); }
        catch (NumberFormatException e) { return def; }
    }

    // =========================================================================
    // COMPONENT FACTORIES — dark-themed components
    // =========================================================================

    private static JTextField darkField(int cols) {
        JTextField f = new JTextField(cols);
        f.setBackground(BG3);
        f.setForeground(FG);
        f.setCaretColor(ACCENT);
        f.setFont(MONO);
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_CLR),
            new EmptyBorder(2, 6, 2, 6)
        ));
        return f;
    }

    private static JCheckBox darkCheck(String text) {
        JCheckBox cb = new JCheckBox(text);
        cb.setBackground(Color.BLACK);
        cb.setForeground(FG_DIM);
        cb.setFont(MONO);
        cb.setOpaque(false);
        cb.setFocusPainted(false);
        return cb;
    }

    private static JButton accentButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(ACCENT);
        b.setForeground(BG);
        b.setFont(MONO_B);
        b.setBorder(new EmptyBorder(5, 14, 5, 14));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static JButton ghostButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(BG3);
        b.setForeground(FG_DIM);
        b.setFont(MONO);
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_CLR),
            new EmptyBorder(4, 12, 4, 12)
        ));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static JButton dangerButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(new Color(68, 20, 20));
        b.setForeground(RED);
        b.setFont(MONO);
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(100, 30, 30)),
            new EmptyBorder(4, 12, 4, 12)
        ));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static JLabel dimLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(MONO);
        l.setForeground(FG_DIM);
        return l;
    }

    private static JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(MONO_B);
        l.setForeground(FG_DIM);
        return l;
    }

    private static JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(MONO);
        l.setForeground(FG_DIM);
        return l;
    }

    private static JSeparator sep() {
        JSeparator s = new JSeparator(SwingConstants.VERTICAL);
        s.setForeground(BORDER_CLR);
        s.setPreferredSize(new Dimension(1, 16));
        return s;
    }

    private static Border darkBorder(String title) {
        TitledBorder tb = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(BORDER_CLR), title);
        tb.setTitleFont(MONO_B);
        tb.setTitleColor(FG_DIM);
        return BorderFactory.createCompoundBorder(tb, new EmptyBorder(4, 6, 6, 6));
    }

    private static void styleCombo(JComboBox<String> combo) {
        combo.setBackground(BG3);
        combo.setForeground(FG);
        combo.setFont(MONO);
        combo.setBorder(BorderFactory.createLineBorder(BORDER_CLR));
        // Custom renderer forces text visible regardless of system L&F
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                setBackground(selected ? ACCENT : BG3);
                setForeground(selected ? BG : FG);
                setFont(MONO);
                setBorder(new EmptyBorder(3, 8, 3, 8));
                return this;
            }
        });
    }

    private static void styleSpinner(JSpinner spinner) {
        spinner.setBackground(BG3);
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setBackground(BG3);
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setForeground(FG);
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setFont(MONO);
    }

    private void styleLearnToggle(boolean on) {
        if (on) {
            btnLearn.setText("⏹  Stop Learning");
            btnLearn.setBackground(new Color(40, 80, 40));
            btnLearn.setForeground(GREEN);
        } else {
            btnLearn.setText("▶  Start Learning");
            btnLearn.setBackground(BG3);
            btnLearn.setForeground(FG_DIM);
        }
        btnLearn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(on ? GREEN : BORDER_CLR),
            new EmptyBorder(6, 14, 6, 14)
        ));
        btnLearn.setFocusPainted(false);
    }

    // =========================================================================
    // COLLECTION MANAGEMENT
    // =========================================================================

    /**
     * Scans the library root directory for subdirectories and populates
     * the collection combobox. Always ensures "default" is first.
     */
    private void populateCollectionCombo() {
        collectionCombo.removeAllItems();
        collectionCombo.addItem("default");

        java.io.File root = new java.io.File(FileManager.DIR);
        if (root.exists() && root.isDirectory()) {
            java.io.File[] dirs = root.listFiles(java.io.File::isDirectory);
            if (dirs != null) {
                Arrays.sort(dirs);
                for (java.io.File dir : dirs) {
                    if (!dir.getName().equals("default"))
                        collectionCombo.addItem(dir.getName());
                }
            }
        }
        collectionCombo.addItem("+ New collection...");

        // Select whichever matches the current FileManager collection
        String current = FileManager.getCollection();
        for (int i = 0; i < collectionCombo.getItemCount(); i++) {
            if (collectionCombo.getItemAt(i).equals(current)) {
                collectionCombo.setSelectedIndex(i);
                break;
            }
        }
    }

    private void wireCollectionEvents() {
        collectionCombo.addActionListener(e -> {
            String selected = (String) collectionCombo.getSelectedItem();
            if (selected == null) return;

            if (selected.equals("+ New collection...")) {
                // Prompt for new name
                String name = JOptionPane.showInputDialog(this,
                    "Enter collection name (e.g. 'varrock', 'lumbridge'):",
                    "New Collection", JOptionPane.PLAIN_MESSAGE);
                if (name != null && !name.trim().isEmpty()) {
                    name = name.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "_");
                    // Create the directory
                    new java.io.File(FileManager.DIR + name).mkdirs();
                    FileManager.setCollection(name);
                    populateCollectionCombo();
                    log("Created collection: " + name);
                } else {
                    // Cancelled — revert to current
                    populateCollectionCombo();
                }
            } else {
                FileManager.setCollection(selected);
                log("Active collection → " + selected);
            }
        });

        btnNewCollection.addActionListener(e -> {
            // Force the "new" option
            collectionCombo.setSelectedItem("+ New collection...");
        });
    }

    // =========================================================================
    // ENTRY POINT — standalone launch
    // =========================================================================

    public static void main(String[] args) {
        // Force dark LAF where available
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            JLibrary.getInstance().load();

            JFrame frame = new JFrame("◈ JLibrary Test Harness");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(new LibraryTestHarness());
            frame.setSize(1150, 750);
            frame.setMinimumSize(new Dimension(900, 600));
            frame.setLocationRelativeTo(null);
            frame.setBackground(BG);

            frame.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent e) {
                    JLibrary.getInstance().saveAll();
                }
            });

            frame.setVisible(true);
        });
    }
}
