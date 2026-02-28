package main.menu.components;

import main.data.Library;
import main.data.Library.TargetType;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.item.GroundItems;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.items.GroundItem;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * EntityBrowserPanel
 * ─────────────────────────────────────────────────────────────────────────────
 * A self-contained JPanel that exposes two swappable lists:
 *
 *   • NEARBY  — live entities scanned from the current player tile
 *   • LIBRARY — all static entries registered in Library.Npcs / GameObjects / GroundItems
 *
 * Features
 *   ✔ Pill-style tab toggle between Nearby / Library
 *   ✔ Per-type coloured badges on every list row  (NPC · OBJ · GND · PLY · INV)
 *   ✔ Live search bar — filters results as you type
 *   ✔ Type-filter checkboxes  (NPC, Object, Ground Item, Player, Inventory)
 *   ✔ "Scan Nearby" button refreshes the live scan
 *   ✔ Status label shows result counts
 *
 * Public API
 *   getSelectedName()   → String        bare entity name, or null
 *   getSelectedEntry()  → EntityEntry   typed wrapper (name + TargetType + live refs)
 *   addSelectionListener(...)           wire to selection-change events
 *   addDoubleClickListener(...)         wire to double-click events
 *   rescanNearby()                      force a fresh nearby scan
 *   reloadLibrary()                     force a fresh library load
 *
 * Wiring example:
 *   EntityBrowserPanel browser = new EntityBrowserPanel();
 *   parentPanel.add(browser);
 *   browser.addDoubleClickListener(e -> myField.setText(browser.getSelectedName()));
 */
public class JLibraryList extends JPanel {

    // =========================================================================
    // Colour palette — dark OSRS-style theme
    // =========================================================================
    private static final Color BG_DARK      = new Color(28,  30,  35);
    private static final Color BG_PANEL     = new Color(36,  38,  45);
    private static final Color BG_LIST      = new Color(22,  24,  29);
    private static final Color BG_ROW_ALT   = new Color(25,  27,  33);
    private static final Color BG_SELECTED  = new Color(55,  95, 145);
    private static final Color BORDER_COLOR = new Color(55,  58,  68);
    private static final Color TEXT_PRIMARY = new Color(220, 220, 225);
    private static final Color TEXT_DIM     = new Color(130, 135, 150);
    private static final Color TEXT_SEARCH  = new Color(180, 185, 200);
    private static final Color ACCENT_BLUE  = new Color(70,  130, 200);
    private static final Color ACCENT_HOVER = new Color(90,  160, 230);

    // Per-type badge colours
    private static final Color COLOR_NPC    = new Color(200, 100,  80);  // coral-red
    private static final Color COLOR_OBJECT = new Color( 80, 170, 120);  // sage-green
    private static final Color COLOR_GROUND = new Color(180, 140,  60);  // gold
    private static final Color COLOR_PLAYER = new Color(100, 150, 230);  // sky-blue
    private static final Color COLOR_INV    = new Color(160, 100, 210);  // violet

    // =========================================================================
    // Data model
    // =========================================================================

    /**
     * A single displayable entity with its resolved TargetType and optional
     * live references (populated only when scanned as NEARBY).
     */
    public static class EntityEntry {
        public final String     name;
        public final TargetType type;
        public final String     source;   // "NEARBY" or "LIBRARY"

        // Live DreamBot refs — non-null only for NEARBY entries
        public NPC        npcRef;
        public GameObject objectRef;
        public GroundItem groundRef;

        public EntityEntry(String name, TargetType type, String source) {
            this.name   = name;
            this.type   = type;
            this.source = source;
        }

        @Override public String toString() { return name; }

        @Override public boolean equals(Object o) {
            if (!(o instanceof EntityEntry)) return false;
            EntityEntry other = (EntityEntry) o;
            return name.equalsIgnoreCase(other.name) && type == other.type;
        }

        @Override public int hashCode() {
            return Objects.hash(name.toLowerCase(), type);
        }
    }

    // =========================================================================
    // State
    // =========================================================================
    private enum ViewMode { NEARBY, LIBRARY }
    private ViewMode currentMode = ViewMode.NEARBY;

    private final List<EntityEntry> nearbyFull  = new ArrayList<>();
    private final List<EntityEntry> libraryFull = new ArrayList<>();

    /** The model the JList always renders from — rebuilt on every filter pass. */
    private final DefaultListModel<EntityEntry> displayModel = new DefaultListModel<>();

    /** Scan radius in tiles. Adjust to taste. */
    private static final int NEARBY_RADIUS = 15;

    // =========================================================================
    // UI components (kept as fields so applyFilter / switchTo can access them)
    // =========================================================================
    private JButton   btnNearby;
    private JButton   btnLibrary;
    private JTextField searchField;
    private JList<EntityEntry> entityList;
    private JLabel    statusLabel;
    private JButton   refreshBtn;

    private JCheckBox cbNPC;
    private JCheckBox cbObject;
    private JCheckBox cbGround;
    private JCheckBox cbPlayer;
    private JCheckBox cbInventory;

    // =========================================================================
    // Constructor
    // =========================================================================
    public JLibraryList() {
        setLayout(new BorderLayout(0, 0));
        setBackground(BG_DARK);
        setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        setPreferredSize(new Dimension(240, 320));

        buildUI();
        populateLibrary();
        scanNearby();
        switchTo(ViewMode.NEARBY);
    }

    // =========================================================================
    // UI Construction
    // =========================================================================

    private void buildUI() {
        add(buildTopBar(),   BorderLayout.NORTH);
        add(buildListArea(), BorderLayout.CENTER);
        add(buildBottomBar(),BorderLayout.SOUTH);
    }

    /** Tab pill row + search field */
    private JPanel buildTopBar() {
        JPanel top = new JPanel(new BorderLayout(0, 5));
        top.setBackground(BG_PANEL);
        top.setBorder(new EmptyBorder(7, 7, 6, 7));

        // ── Tab switcher ────────────────────────────────────────────────────
        JPanel tabRow = new JPanel(new GridLayout(1, 2, 1, 0));
        tabRow.setBackground(BG_DARK);
        tabRow.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        tabRow.setPreferredSize(new Dimension(0, 26));

        btnNearby  = buildTabButton("📍 Nearby",  true);
        btnLibrary = buildTabButton("📚 Library", false);

        btnNearby .addActionListener(e -> switchTo(ViewMode.NEARBY));
        btnLibrary.addActionListener(e -> switchTo(ViewMode.LIBRARY));

        tabRow.add(btnNearby);
        tabRow.add(btnLibrary);

        // ── Search row ───────────────────────────────────────────────────────
        JPanel searchRow = new JPanel(new BorderLayout(3, 0));
        searchRow.setBackground(BG_PANEL);

        searchField = new JTextField();
        searchField.setBackground(BG_LIST);
        searchField.setForeground(TEXT_DIM);
        searchField.setCaretColor(TEXT_PRIMARY);
        searchField.setFont(new Font("Consolas", Font.PLAIN, 11));
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(3, 6, 3, 6)));
        installPlaceholder(searchField, "Search…");

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate (DocumentEvent e) { applyFilter(); }
            public void removeUpdate (DocumentEvent e) { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        // tiny ✕ clear button on the right of the search bar
        JButton clearBtn = new JButton("✕");
        clearBtn.setFont(new Font("Consolas", Font.PLAIN, 9));
        clearBtn.setForeground(TEXT_DIM);
        clearBtn.setBackground(BG_LIST);
        clearBtn.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        clearBtn.setFocusPainted(false);
        clearBtn.setPreferredSize(new Dimension(22, 22));
        clearBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearBtn.addActionListener(e -> searchField.setText(""));

        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(clearBtn,    BorderLayout.EAST);

        top.add(tabRow,    BorderLayout.NORTH);
        top.add(searchRow, BorderLayout.SOUTH);
        return top;
    }

    /** Scrollable entity list */
    private JScrollPane buildListArea() {
        entityList = new JList<>(displayModel);
        entityList.setBackground(BG_LIST);
        entityList.setForeground(TEXT_PRIMARY);
        entityList.setSelectionBackground(BG_SELECTED);
        entityList.setSelectionForeground(Color.WHITE);
        entityList.setFixedCellHeight(24);
        entityList.setBorder(new EmptyBorder(2, 0, 2, 0));
        entityList.setCellRenderer(new EntityCellRenderer());
        entityList.setFont(new Font("Consolas", Font.PLAIN, 11));

        JScrollPane scroll = new JScrollPane(entityList);
        scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, BORDER_COLOR));
        scroll.setBackground(BG_LIST);
        scroll.getViewport().setBackground(BG_LIST);
        scroll.getVerticalScrollBar().setUI(new DarkScrollBarUI());
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(7, 0));
        return scroll;
    }

    /** Type-filter checkboxes + refresh button + status label */
    private JPanel buildBottomBar() {
        JPanel bottom = new JPanel(new BorderLayout(0, 5));
        bottom.setBackground(BG_PANEL);
        bottom.setBorder(new EmptyBorder(5, 7, 7, 7));

        // ── Filter label + checkboxes ────────────────────────────────────────
        JPanel cbWrap = new JPanel();
        cbWrap.setLayout(new BoxLayout(cbWrap, BoxLayout.Y_AXIS));
        cbWrap.setBackground(BG_PANEL);

        JLabel filterLbl = new JLabel("TYPE FILTER");
        filterLbl.setFont(new Font("Consolas", Font.BOLD, 9));
        filterLbl.setForeground(TEXT_DIM);

        cbNPC       = buildCheckbox("NPC",         COLOR_NPC,    true);
        cbObject    = buildCheckbox("Game Object", COLOR_OBJECT, true);
        cbGround    = buildCheckbox("Ground Item", COLOR_GROUND, true);
        cbPlayer    = buildCheckbox("Player",      COLOR_PLAYER, false);
        cbInventory = buildCheckbox("Inventory",   COLOR_INV,    false);

        ActionListener filterListener = e -> applyFilter();
        cbNPC.addActionListener(filterListener);
        cbObject.addActionListener(filterListener);
        cbGround.addActionListener(filterListener);
        cbPlayer.addActionListener(filterListener);
        cbInventory.addActionListener(filterListener);

        // Two-column grid for checkboxes
        JPanel cbGrid = new JPanel(new GridLayout(3, 2, 4, 0));
        cbGrid.setBackground(BG_PANEL);
        cbGrid.add(cbNPC);
        cbGrid.add(cbObject);
        cbGrid.add(cbGround);
        cbGrid.add(cbPlayer);
        cbGrid.add(cbInventory);
        cbGrid.add(new JLabel()); // spacer

        cbWrap.add(filterLbl);
        cbWrap.add(Box.createVerticalStrut(3));
        cbWrap.add(cbGrid);

        // ── Action row: status + refresh ─────────────────────────────────────
        JPanel actionRow = new JPanel(new BorderLayout(4, 0));
        actionRow.setBackground(BG_PANEL);
        actionRow.setBorder(new EmptyBorder(4, 0, 0, 0));

        statusLabel = new JLabel("Ready");
        statusLabel.setFont(new Font("Consolas", Font.PLAIN, 9));
        statusLabel.setForeground(TEXT_DIM);

        refreshBtn = new JButton("⟳ Scan Nearby");
        styleActionButton(refreshBtn, ACCENT_BLUE, ACCENT_HOVER);
        refreshBtn.addActionListener(e -> {
            if (currentMode == ViewMode.NEARBY) {
                scanNearby();
            } else {
                populateLibrary();
            }
            applyFilter();
        });

        actionRow.add(statusLabel, BorderLayout.CENTER);
        actionRow.add(refreshBtn,  BorderLayout.EAST);

        bottom.add(cbWrap,    BorderLayout.CENTER);
        bottom.add(actionRow, BorderLayout.SOUTH);
        return bottom;
    }

    // =========================================================================
    // Data population
    // =========================================================================

    /**
     * Queries the live DreamBot environment for entities within NEARBY_RADIUS
     * tiles of the local player. Safe to call outside a bot context (returns
     * an empty list if the client is not running).
     */
    public void scanNearby() {
        nearbyFull.clear();

        try {
            // ── NPCs ──────────────────────────────────────────────────────────
            List<NPC> liveNpcs = NPCs.all(n -> n != null
                    && n.getName() != null
                    && !n.getName().isEmpty()
                    && !n.getName().equalsIgnoreCase("null"));
            if (liveNpcs != null) {
                for (NPC npc : liveNpcs) {
                    if (withinRadius(npc.getTile())) {
                        EntityEntry e = new EntityEntry(npc.getName(), TargetType.NPC, "NEARBY");
                        e.npcRef = npc;
                        addUnique(nearbyFull, e);
                    }
                }
            }

            // ── Game Objects ──────────────────────────────────────────────────
            List<GameObject> liveObjs = GameObjects.all(o -> o != null
                    && o.getName() != null
                    && !o.getName().isEmpty()
                    && !o.getName().equalsIgnoreCase("null"));
            if (liveObjs != null) {
                for (GameObject obj : liveObjs) {
                    if (withinRadius(obj.getTile())) {
                        EntityEntry e = new EntityEntry(obj.getName(), TargetType.GAME_OBJECT, "NEARBY");
                        e.objectRef = obj;
                        addUnique(nearbyFull, e);
                    }
                }
            }

            // ── Ground Items ──────────────────────────────────────────────────
            List<GroundItem> liveItems = GroundItems.all(i -> i != null
                    && i.getName() != null
                    && !i.getName().isEmpty()
                    && !i.getName().equalsIgnoreCase("null"));
            if (liveItems != null) {
                for (GroundItem item : liveItems) {
                    if (withinRadius(item.getTile())) {
                        EntityEntry e = new EntityEntry(item.getName(), TargetType.GROUND_ITEM, "NEARBY");
                        e.groundRef = item;
                        addUnique(nearbyFull, e);
                    }
                }
            }

            // ── Players ───────────────────────────────────────────────────────
            var local = Players.getLocal();
            if (local != null) {
                var livePlayers = Players.all(p -> p != null
                        && p.getName() != null
                        && !p.getName().equals(local.getName()));
                if (livePlayers != null) {
                    for (var p : livePlayers) {
                        if (withinRadius(p.getTile())) {
                            addUnique(nearbyFull, new EntityEntry(p.getName(), TargetType.PLAYER, "NEARBY"));
                        }
                    }
                }
            }

        } catch (Exception ignored) {
            // Client not running — nearbyFull stays empty, which is fine
        }

        setStatus(nearbyFull.size() + " entities nearby");
    }

    /** Loads all static Library enum entries into libraryFull. */
    private void populateLibrary() {
        libraryFull.clear();

        for (Library.Npcs n : Library.Npcs.values()) {
            libraryFull.add(new EntityEntry(n.npcName, TargetType.NPC, "LIBRARY"));
        }
        for (Library.GameObjects o : Library.GameObjects.values()) {
            libraryFull.add(new EntityEntry(o.objectName, TargetType.GAME_OBJECT, "LIBRARY"));
        }
        for (Library.GroundItems i : Library.GroundItems.values()) {
            libraryFull.add(new EntityEntry(i.itemName, TargetType.GROUND_ITEM, "LIBRARY"));
        }

        setStatus("Library: " + libraryFull.size() + " entries");
    }

    // =========================================================================
    // View switching and filtering
    // =========================================================================

    private void switchTo(ViewMode mode) {
        currentMode = mode;
        setTabActive(btnNearby,  mode == ViewMode.NEARBY);
        setTabActive(btnLibrary, mode == ViewMode.LIBRARY);
        refreshBtn.setText(mode == ViewMode.NEARBY ? "⟳ Scan Nearby" : "⟳ Refresh");
        applyFilter();
    }

    /**
     * Rebuilds the displayModel from the active source list,
     * respecting the current search query and checkbox filters.
     */
    private void applyFilter() {
        // Resolve search text (ignore placeholder)
        String raw = searchField.getText().trim();
        String query = (raw.equalsIgnoreCase("search…") || raw.equalsIgnoreCase("search..."))
                ? "" : raw.toLowerCase();

        // Build allowed-type set from checkboxes
        Set<TargetType> allowed = EnumSet.noneOf(TargetType.class);
        if (cbNPC.isSelected())       allowed.add(TargetType.NPC);
        if (cbObject.isSelected())    allowed.add(TargetType.GAME_OBJECT);
        if (cbGround.isSelected())    allowed.add(TargetType.GROUND_ITEM);
        if (cbPlayer.isSelected())    allowed.add(TargetType.PLAYER);
        if (cbInventory.isSelected()) allowed.add(TargetType.INVENTORY_ITEM);

        List<EntityEntry> source = currentMode == ViewMode.NEARBY ? nearbyFull : libraryFull;

        List<EntityEntry> filtered = source.stream()
                .filter(e -> allowed.contains(e.type))
                .filter(e -> query.isEmpty() || e.name.toLowerCase().contains(query))
                .sorted(Comparator.comparing(e -> e.name))
                .distinct()
                .collect(Collectors.toList());

        // Preserve selection if possible
        EntityEntry selected = entityList.getSelectedValue();

        displayModel.clear();
        filtered.forEach(displayModel::addElement);

        // Restore selection
        if (selected != null) {
            for (int i = 0; i < displayModel.size(); i++) {
                if (displayModel.get(i).equals(selected)) {
                    entityList.setSelectedIndex(i);
                    break;
                }
            }
        }

        setStatus(filtered.size() + " result" + (filtered.size() == 1 ? "" : "s")
                + (currentMode == ViewMode.NEARBY ? " nearby" : " in library"));
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Returns the plain name of the currently selected entry, or {@code null}
     * if nothing is selected. Suitable for pasting into a text field directly.
     */
    public String getSelectedName() {
        EntityEntry e = entityList.getSelectedValue();
        return e != null ? e.name : null;
    }

    /**
     * Returns the full {@link EntityEntry} for the currently selected row.
     * The entry exposes:
     * <ul>
     *   <li>{@code .name}      — entity name string</li>
     *   <li>{@code .type}      — {@link TargetType} (NPC / GAME_OBJECT / etc.)</li>
     *   <li>{@code .source}    — "NEARBY" or "LIBRARY"</li>
     *   <li>{@code .npcRef}    — live {@link NPC} reference (NEARBY only, may be null)</li>
     *   <li>{@code .objectRef} — live {@link GameObject} reference (NEARBY only, may be null)</li>
     *   <li>{@code .groundRef} — live {@link GroundItem} reference (NEARBY only, may be null)</li>
     * </ul>
     */
    public EntityEntry getSelectedEntry() {
        return entityList.getSelectedValue();
    }

    /**
     * Attaches a {@link javax.swing.event.ListSelectionListener} to the underlying JList.
     * Fires whenever the selected row changes.
     */
    public void addSelectionListener(javax.swing.event.ListSelectionListener l) {
        entityList.addListSelectionListener(l);
    }

    /**
     * Attaches a double-click {@link MouseAdapter} to the underlying JList.
     * Only fires on double-clicks (clickCount == 2).
     *
     * Example:
     * <pre>
     *   browser.addDoubleClickListener(e -> {
     *       targetField.setText(browser.getSelectedName());
     *   });
     * </pre>
     */
    public void addDoubleClickListener(MouseAdapter listener) {
        entityList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) listener.mouseClicked(e);
            }
        });
    }

    /** Forces a fresh nearby scan and refreshes the list if currently in Nearby mode. */
    public void rescanNearby() {
        scanNearby();
        if (currentMode == ViewMode.NEARBY) applyFilter();
    }

    /** Forces a fresh library load and refreshes the list if currently in Library mode. */
    public void reloadLibrary() {
        populateLibrary();
        if (currentMode == ViewMode.LIBRARY) applyFilter();
    }

    // =========================================================================
    // Utility helpers
    // =========================================================================

    private boolean withinRadius(org.dreambot.api.methods.map.Tile tile) {
        if (tile == null) return false;
        try {
            var local = Players.getLocal();
            if (local == null) return false;
            var t = local.getTile();
            return t != null && t.distance(tile) <= NEARBY_RADIUS;
        } catch (Exception e) {
            return false;
        }
    }

    private void addUnique(List<EntityEntry> list, EntityEntry entry) {
        if (!list.contains(entry)) list.add(entry);
    }

    private void setStatus(String msg) {
        if (statusLabel != null) statusLabel.setText(msg);
    }

    // =========================================================================
    // Style helpers
    // =========================================================================

    private JButton buildTabButton(String text, boolean active) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Consolas", Font.BOLD, 11));
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setTabActive(btn, active);
        return btn;
    }

    private void setTabActive(JButton btn, boolean active) {
        btn.setBackground(active ? ACCENT_BLUE : BG_DARK);
        btn.setForeground(active ? Color.WHITE  : TEXT_DIM);
    }

    private void styleActionButton(JButton btn, Color bg, Color hover) {
        btn.setFont(new Font("Consolas", Font.BOLD, 10));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(4, 10, 4, 10));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(hover); }
            public void mouseExited (MouseEvent e) { btn.setBackground(bg);    }
        });
    }

    private JCheckBox buildCheckbox(String label, Color color, boolean selected) {
        JCheckBox cb = new JCheckBox(label, selected);
        cb.setFont(new Font("Consolas", Font.BOLD, 9));
        cb.setBackground(BG_PANEL);
        cb.setForeground(color);
        cb.setFocusPainted(false);
        cb.setOpaque(true);
        return cb;
    }

    /** Installs grey placeholder text that disappears on focus. */
    private void installPlaceholder(JTextField field, String placeholder) {
        field.setText(placeholder);
        field.setForeground(TEXT_DIM);

        field.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                if (field.getText().equals(placeholder)) {
                    field.setText("");
                    field.setForeground(TEXT_SEARCH);
                }
            }
            public void focusLost(FocusEvent e) {
                if (field.getText().isEmpty()) {
                    field.setText(placeholder);
                    field.setForeground(TEXT_DIM);
                }
            }
        });
    }

    // =========================================================================
    // Custom list cell renderer
    // =========================================================================

    private static class EntityCellRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {

            // Outer row panel
            JPanel row = new JPanel(new BorderLayout(6, 0));
            row.setOpaque(true);
            row.setBorder(new EmptyBorder(2, 6, 2, 6));
            row.setBackground(isSelected ? BG_SELECTED
                    : (index % 2 == 0 ? BG_LIST : BG_ROW_ALT));

            if (!(value instanceof EntityEntry)) {
                row.add(new JLabel(value != null ? value.toString() : "—"), BorderLayout.CENTER);
                return row;
            }

            EntityEntry entry = (EntityEntry) value;

            // Type badge (painted pill)
            row.add(buildBadge(entry.type), BorderLayout.WEST);

            // Entity name
            JLabel name = new JLabel(entry.name);
            name.setFont(new Font("Consolas", Font.PLAIN, 11));
            name.setForeground(isSelected ? Color.WHITE : TEXT_PRIMARY);
            row.add(name, BorderLayout.CENTER);

            // Source tag (tiny, right-aligned)
            JLabel src = new JLabel(entry.source.equals("NEARBY") ? "live" : "lib");
            src.setFont(new Font("Consolas", Font.PLAIN, 9));
            src.setForeground(isSelected ? new Color(180, 200, 230) : TEXT_DIM);
            src.setBorder(new EmptyBorder(0, 0, 0, 2));
            row.add(src, BorderLayout.EAST);

            return row;
        }

        private static JLabel buildBadge(TargetType type) {
            final String text;
            final Color  color;

            switch (type) {
                case NPC:            text = "NPC"; color = COLOR_NPC;    break;
                case GAME_OBJECT:    text = "OBJ"; color = COLOR_OBJECT; break;
                case GROUND_ITEM:    text = "GND"; color = COLOR_GROUND; break;
                case PLAYER:         text = "PLY"; color = COLOR_PLAYER; break;
                case INVENTORY_ITEM: text = "INV"; color = COLOR_INV;    break;
                default:             text = "???"; color = TEXT_DIM;     break;
            }

            // Custom-painted rounded pill label
            JLabel badge = new JLabel(text) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    // fill
                    g2.setColor(new Color(color.getRed(), color.getGreen(),
                            color.getBlue(), 50));
                    g2.fill(new RoundRectangle2D.Float(0, 1, getWidth(), getHeight() - 2, 7, 7));
                    // border
                    g2.setColor(new Color(color.getRed(), color.getGreen(),
                            color.getBlue(), 160));
                    g2.draw(new RoundRectangle2D.Float(0, 1, getWidth() - 1,
                            getHeight() - 3, 7, 7));
                    g2.dispose();
                    super.paintComponent(g);
                }
            };

            badge.setFont(new Font("Consolas", Font.BOLD, 8));
            badge.setForeground(color);
            badge.setHorizontalAlignment(SwingConstants.CENTER);
            badge.setPreferredSize(new Dimension(30, 14));
            badge.setOpaque(false);
            return badge;
        }
    }

    // =========================================================================
    // Minimal dark scrollbar
    // =========================================================================

    private static class DarkScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {

        @Override
        protected void configureScrollBarColors() {
            thumbColor          = new Color(70, 75, 90);
            trackColor          = BG_LIST;
            thumbHighlightColor = new Color(90, 100, 120);
        }

        @Override protected JButton createDecreaseButton(int o) { return zeroButton(); }
        @Override protected JButton createIncreaseButton(int o) { return zeroButton(); }

        private static JButton zeroButton() {
            JButton b = new JButton();
            Dimension d = new Dimension(0, 0);
            b.setPreferredSize(d);
            b.setMinimumSize(d);
            b.setMaximumSize(d);
            return b;
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(thumbColor);
            g2.fill(new RoundRectangle2D.Float(
                    r.x + 1, r.y + 2, r.width - 2, r.height - 4, 6, 6));
            g2.dispose();
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(trackColor);
            g.fillRect(r.x, r.y, r.width, r.height);
        }
    }
}