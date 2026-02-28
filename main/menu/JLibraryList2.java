package main.menu;

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
 * Layout (top → bottom):
 *   1. Search bar  (with refresh icon button on the right)
 *   2. Sort bar    (A→Z  Z→A  near→far  far→near arrows)
 *   3. JList       (scrollable entity rows with ASCII-icon type badges)
 *   4. Tab switcher  [ (~) Nearby ] [ (=) Library ]
 *   5. Bottom bar  — type-filter checkboxes
 *                  — [  (>) Scan Nearby  ] full-width orange button
 *                  — status label (right-aligned, above button)
 *
 * ASCII type icons used in badges:
 *   @  — NPC          (person / character)
 *   #  — Game Object  (structure / block)
 *   *  — Ground Item  (loot / drop)
 *   &  — Player       (other player)
 *   ~  — Inventory    (bag contents)
 *
 * Search bar refresh icon:
 *   Default: "(^)" text button.
 *   To swap in your own ImageIcon, change SEARCH_REFRESH_ICON_PATH below
 *   and the loader will use it automatically if the file exists; otherwise
 *   the ASCII fallback is used.
 *
 * Public API
 *   getSelectedName()              → String        bare entity name or null
 *   getSelectedEntry()             → EntityEntry   typed wrapper with live refs
 *   addSelectionListener(l)        wire ListSelectionListener
 *   addDoubleClickListener(a)      wire double-click MouseAdapter
 *   rescanNearby()                 force a fresh nearby scan
 *   reloadLibrary()                force a fresh library load
 */
public class JLibraryList2 extends JPanel {

    // =========================================================================
    // Icon configuration
    // =========================================================================

    /**
     * Path to a custom icon for the search-bar refresh button.
     * Set to null (or leave pointing at a missing file) to use the ASCII fallback.
     * Example: "res/icons/refresh.png"
     */
    private static final String SEARCH_REFRESH_ICON_PATH = null;

    // ASCII type icons — one char per type, easy to swap
    private static final String ICON_NPC     = "@";   // person / character
    private static final String ICON_OBJECT  = "#";   // structure / block
    private static final String ICON_GROUND  = "*";   // loot / drop
    private static final String ICON_PLAYER  = "&";   // other player
    private static final String ICON_INV     = "~";   // inventory

    // Tab icons
    private static final String ICON_NEARBY  = "(~)"; // radar sweep feel
    private static final String ICON_LIBRARY = "(=)"; // stacked lines / book

    // Sort arrows
    private static final String SORT_AZ      = "A" + "\u2193";  // A↓
    private static final String SORT_ZA      = "Z" + "\u2191";  // Z↑
    private static final String SORT_NEAR    = "\u2022" + "\u2192"; // •→  nearest first
    private static final String SORT_FAR     = "\u2022" + "\u2190"; // •←  farthest first

    // =========================================================================
    // Colour palette
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
    private static final Color ACCENT_BLUE_H= new Color(90,  160, 230);

    // Orange scan button
    private static final Color ORANGE_BASE  = new Color(200, 110,  30);
    private static final Color ORANGE_HOVER = new Color(230, 135,  45);
    private static final Color ORANGE_PRESS = new Color(170,  85,  15);

    // Per-type badge colours
    private static final Color COLOR_NPC    = new Color(200, 100,  80);
    private static final Color COLOR_OBJECT = new Color( 80, 170, 120);
    private static final Color COLOR_GROUND = new Color(180, 140,  60);
    private static final Color COLOR_PLAYER = new Color(100, 150, 230);
    private static final Color COLOR_INV    = new Color(160, 100, 210);

    // =========================================================================
    // Data model
    // =========================================================================

    public static class EntityEntry {
        public final String     name;
        public final TargetType type;
        public final String     source;   // "NEARBY" or "LIBRARY"
        public       int        distanceTiles = -1; // -1 = unknown

        // Live DreamBot refs — non-null only for NEARBY entries
        public NPC        npcRef;
        public GameObject objectRef;
        public GroundItem groundRef;

        public EntityEntry(String name, TargetType type, String source) {
            this.name   = name;
            this.type   = type;
            this.source = source;
        }

        @Override public String  toString()  { return name; }

        @Override public boolean equals(Object o) {
            if (!(o instanceof EntityEntry)) return false;
            EntityEntry x = (EntityEntry) o;
            return name.equalsIgnoreCase(x.name) && type == x.type;
        }

        @Override public int hashCode() {
            return Objects.hash(name.toLowerCase(), type);
        }
    }

    // =========================================================================
    // Sort mode
    // =========================================================================
    private enum SortMode { AZ, ZA, NEAR, FAR }

    // =========================================================================
    // State
    // =========================================================================
    private enum ViewMode { NEARBY, LIBRARY }
    private ViewMode currentMode = ViewMode.NEARBY;
    private SortMode currentSort = SortMode.AZ;

    private final List<EntityEntry> nearbyFull  = new ArrayList<>();
    private final List<EntityEntry> libraryFull = new ArrayList<>();
    private final DefaultListModel<EntityEntry> displayModel = new DefaultListModel<>();

    private static final int NEARBY_RADIUS = 15;

    // =========================================================================
    // UI fields
    // =========================================================================
    private JButton    btnNearby;
    private JButton    btnLibrary;
    private JTextField searchField;
    private JList<EntityEntry> entityList;
    private JLabel     statusLabel;
    private JButton    refreshBtn;

    private JButton    btnSortAZ;
    private JButton    btnSortZA;
    private JButton    btnSortNear;
    private JButton    btnSortFar;

    private JCheckBox  cbNPC;
    private JCheckBox  cbObject;
    private JCheckBox  cbGround;
    private JCheckBox  cbPlayer;
    private JCheckBox  cbInventory;

    // =========================================================================
    // Constructor
    // =========================================================================
    public JLibraryList2() {
        setLayout(new BorderLayout(0, 0));
        setBackground(BG_DARK);
        setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        setPreferredSize(new Dimension(250, 360));

        buildUI();
        populateLibrary();
        scanNearby();
        switchTo(ViewMode.NEARBY);
    }

    // =========================================================================
    // UI construction
    // =========================================================================

    private void buildUI() {
        add(buildTopBar(),    BorderLayout.NORTH);
        add(buildListArea(),  BorderLayout.CENTER);
        add(buildBottomBar(), BorderLayout.SOUTH);
    }

    /**
     * Top bar: search field (with refresh icon) + sort arrow row.
     * This is the very first thing the user sees.
     */
    private JPanel buildTopBar() {
        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.setBackground(BG_PANEL);
        top.setBorder(new EmptyBorder(7, 7, 5, 7));

        // ── Search row ────────────────────────────────────────────────────────
        JPanel searchRow = new JPanel(new BorderLayout(3, 0));
        searchRow.setBackground(BG_PANEL);

        searchField = new JTextField();
        searchField.setBackground(BG_LIST);
        searchField.setForeground(TEXT_DIM);
        searchField.setCaretColor(TEXT_PRIMARY);
        searchField.setFont(new Font("Consolas", Font.PLAIN, 11));
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(3, 6, 3, 4)));
        installPlaceholder(searchField, "Search...");

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate (DocumentEvent e) { applyFilter(); }
            public void removeUpdate (DocumentEvent e) { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        // Refresh / clear icon button
        JButton searchRefreshBtn = buildSearchRefreshButton();
        searchRefreshBtn.addActionListener(e -> searchField.setText(""));

        searchRow.add(searchField,       BorderLayout.CENTER);
        searchRow.add(searchRefreshBtn,  BorderLayout.EAST);

        // ── Sort bar ──────────────────────────────────────────────────────────
        JPanel sortRow = new JPanel(new GridLayout(1, 4, 1, 0));
        sortRow.setBackground(BG_DARK);
        sortRow.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        sortRow.setPreferredSize(new Dimension(0, 22));

        btnSortAZ   = buildSortButton(SORT_AZ,   SortMode.AZ,   true);
        btnSortZA   = buildSortButton(SORT_ZA,   SortMode.ZA,   false);
        btnSortNear = buildSortButton(SORT_NEAR, SortMode.NEAR, false);
        btnSortFar  = buildSortButton(SORT_FAR,  SortMode.FAR,  false);

        sortRow.add(btnSortAZ);
        sortRow.add(btnSortZA);
        sortRow.add(btnSortNear);
        sortRow.add(btnSortFar);

        top.add(searchRow, BorderLayout.NORTH);
        top.add(sortRow,   BorderLayout.SOUTH);
        return top;
    }

    /** Scrollable JList in the centre. */
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
        scroll.getViewport().setBackground(BG_LIST);
        scroll.getVerticalScrollBar().setUI(new DarkScrollBarUI());
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(7, 0));
        return scroll;
    }

    /**
     * Bottom bar (south, below list):
     *   Row 1 — tab switcher [ (~) Nearby | (=) Library ]
     *   Row 2 — type-filter checkboxes
     *   Row 3 — status label (right-aligned)
     *   Row 4 — full-width orange Scan Nearby button
     */
    private JPanel buildBottomBar() {
        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBackground(BG_PANEL);
        bottom.setBorder(new EmptyBorder(5, 7, 7, 7));

        // ── Tab switcher ──────────────────────────────────────────────────────
        JPanel tabRow = new JPanel(new GridLayout(1, 2, 1, 0));
        tabRow.setBackground(BG_DARK);
        tabRow.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        tabRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        btnNearby  = buildTabButton(ICON_NEARBY  + " Nearby",  true);
        btnLibrary = buildTabButton(ICON_LIBRARY + " Library", false);
        btnNearby .addActionListener(e -> switchTo(ViewMode.NEARBY));
        btnLibrary.addActionListener(e -> switchTo(ViewMode.LIBRARY));

        tabRow.add(btnNearby);
        tabRow.add(btnLibrary);

        // ── Filter label + checkboxes ─────────────────────────────────────────
        JLabel filterLbl = new JLabel("TYPE FILTER");
        filterLbl.setFont(new Font("Consolas", Font.BOLD, 9));
        filterLbl.setForeground(TEXT_DIM);
        filterLbl.setBorder(new EmptyBorder(6, 0, 2, 0));
        filterLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        cbNPC       = buildCheckbox(ICON_NPC    + " NPC",         COLOR_NPC,    true);
        cbObject    = buildCheckbox(ICON_OBJECT + " Game Object", COLOR_OBJECT, true);
        cbGround    = buildCheckbox(ICON_GROUND + " Ground Item", COLOR_GROUND, true);
        cbPlayer    = buildCheckbox(ICON_PLAYER + " Player",      COLOR_PLAYER, false);
        cbInventory = buildCheckbox(ICON_INV    + " Inventory",   COLOR_INV,    false);

        ActionListener filterListener = e -> applyFilter();
        cbNPC.addActionListener(filterListener);
        cbObject.addActionListener(filterListener);
        cbGround.addActionListener(filterListener);
        cbPlayer.addActionListener(filterListener);
        cbInventory.addActionListener(filterListener);

        JPanel cbGrid = new JPanel(new GridLayout(3, 2, 4, 0));
        cbGrid.setBackground(BG_PANEL);
        cbGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        cbGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        cbGrid.add(cbNPC);
        cbGrid.add(cbObject);
        cbGrid.add(cbGround);
        cbGrid.add(cbPlayer);
        cbGrid.add(cbInventory);
        cbGrid.add(new JLabel()); // spacer

        // ── Status label (right-aligned) ──────────────────────────────────────
        JPanel statusRow = new JPanel(new BorderLayout());
        statusRow.setBackground(BG_PANEL);
        statusRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        statusRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusRow.setBorder(new EmptyBorder(5, 0, 2, 0));

        statusLabel = new JLabel("Ready");
        statusLabel.setFont(new Font("Consolas", Font.PLAIN, 9));
        statusLabel.setForeground(TEXT_DIM);
        statusLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        statusRow.add(statusLabel, BorderLayout.EAST);

        // ── Full-width orange Scan button ─────────────────────────────────────
        refreshBtn = new JButton("(>) Scan Nearby");
        styleOrangeButton(refreshBtn);
        refreshBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        refreshBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        refreshBtn.addActionListener(e -> {
            if (currentMode == ViewMode.NEARBY) {
                scanNearby();
            } else {
                populateLibrary();
                refreshBtn.setText("(>) Refresh Library");
            }
            applyFilter();
        });

        bottom.add(tabRow);
        bottom.add(filterLbl);
        bottom.add(cbGrid);
        bottom.add(statusRow);
        bottom.add(refreshBtn);

        return bottom;
    }

    // =========================================================================
    // Data population
    // =========================================================================

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
                    int dist = distanceTo(npc.getTile());
                    if (dist >= 0 && dist <= NEARBY_RADIUS) {
                        EntityEntry e = new EntityEntry(npc.getName(), TargetType.NPC, "NEARBY");
                        e.npcRef = npc;
                        e.distanceTiles = dist;
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
                    int dist = distanceTo(obj.getTile());
                    if (dist >= 0 && dist <= NEARBY_RADIUS) {
                        EntityEntry e = new EntityEntry(obj.getName(), TargetType.GAME_OBJECT, "NEARBY");
                        e.objectRef = obj;
                        e.distanceTiles = dist;
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
                    int dist = distanceTo(item.getTile());
                    if (dist >= 0 && dist <= NEARBY_RADIUS) {
                        EntityEntry e = new EntityEntry(item.getName(), TargetType.GROUND_ITEM, "NEARBY");
                        e.groundRef = item;
                        e.distanceTiles = dist;
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
                        int dist = distanceTo(p.getTile());
                        if (dist >= 0 && dist <= NEARBY_RADIUS) {
                            EntityEntry e = new EntityEntry(p.getName(), TargetType.PLAYER, "NEARBY");
                            e.distanceTiles = dist;
                            addUnique(nearbyFull, e);
                        }
                    }
                }
            }

        } catch (Exception ignored) { /* client not running — safe */ }

        setStatus(nearbyFull.size() + " entities nearby");
    }

    private void populateLibrary() {
        libraryFull.clear();
        for (Library.Npcs    n : Library.Npcs.values())
            libraryFull.add(new EntityEntry(n.npcName,    TargetType.NPC,         "LIBRARY"));
        for (Library.GameObjects o : Library.GameObjects.values())
            libraryFull.add(new EntityEntry(o.objectName, TargetType.GAME_OBJECT, "LIBRARY"));
        for (Library.GroundItems i : Library.GroundItems.values())
            libraryFull.add(new EntityEntry(i.itemName,   TargetType.GROUND_ITEM, "LIBRARY"));
        setStatus("Library: " + libraryFull.size() + " entries");
    }

    // =========================================================================
    // View switching and filtering
    // =========================================================================

    private void switchTo(ViewMode mode) {
        currentMode = mode;
        setTabActive(btnNearby,  mode == ViewMode.NEARBY);
        setTabActive(btnLibrary, mode == ViewMode.LIBRARY);
        refreshBtn.setText(mode == ViewMode.NEARBY
                ? "(>) Scan Nearby"
                : "(>) Refresh Library");
        applyFilter();
    }

    private void setSortMode(SortMode mode) {
        currentSort = mode;
        setSortActive(btnSortAZ,   mode == SortMode.AZ);
        setSortActive(btnSortZA,   mode == SortMode.ZA);
        setSortActive(btnSortNear, mode == SortMode.NEAR);
        setSortActive(btnSortFar,  mode == SortMode.FAR);
        applyFilter();
    }

    private void applyFilter() {
        // Resolve search text, ignoring placeholder
        String raw = searchField.getText().trim();
        String query = raw.equalsIgnoreCase("search...") ? "" : raw.toLowerCase();

        // Active type set
        Set<TargetType> allowed = EnumSet.noneOf(TargetType.class);
        if (cbNPC.isSelected())
            allowed.add(TargetType.NPC);
        if (cbObject.isSelected())
            allowed.add(TargetType.GAME_OBJECT);
        if (cbGround.isSelected())
            allowed.add(TargetType.GROUND_ITEM);
        if (cbPlayer.isSelected())
            allowed.add(TargetType.PLAYER);
        if (cbInventory.isSelected())
            allowed.add(TargetType.INVENTORY_ITEM);

        List<EntityEntry> source = currentMode == ViewMode.NEARBY ? nearbyFull : libraryFull;

        List<EntityEntry> filtered = source.stream()
                .filter(e -> allowed.contains(e.type))
                .filter(e -> query.isEmpty() || e.name.toLowerCase().contains(query))
                .distinct()
                .collect(Collectors.toList());

        // Apply sort
        switch (currentSort) {
            case AZ:
                filtered.sort(Comparator.comparing(e -> e.name.toLowerCase()));
                break;
            case ZA:
                filtered.sort((a, b) -> b.name.compareToIgnoreCase(a.name));
                break;
            case NEAR:
                filtered.sort(Comparator.comparingInt(e -> (e.distanceTiles < 0 ? Integer.MAX_VALUE : e.distanceTiles)));
                break;
            case FAR:
                filtered.sort((a, b) -> {
                    int da = a.distanceTiles < 0 ? Integer.MAX_VALUE : a.distanceTiles;
                    int db = b.distanceTiles < 0 ? Integer.MAX_VALUE : b.distanceTiles;
                    return Integer.compare(db, da);
                });
                break;
        }

        // Preserve selection
        EntityEntry selected = entityList.getSelectedValue();
        displayModel.clear();
        filtered.forEach(displayModel::addElement);
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
     * Returns the plain name of the selected entry, or {@code null}.
     * Suitable for pasting directly into a JTextField.
     */
    public String getSelectedName() {
        EntityEntry e = entityList.getSelectedValue();
        return e != null ? e.name : null;
    }

    /**
     * Returns the full {@link EntityEntry} for the selected row.
     *   .name          — entity name string
     *   .type          — TargetType (NPC / GAME_OBJECT / GROUND_ITEM / PLAYER / …)
     *   .source        — "NEARBY" or "LIBRARY"
     *   .distanceTiles — tile distance (-1 if unknown / library entry)
     *   .npcRef        — live NPC   (NEARBY only, may be null)
     *   .objectRef     — live GameObject (NEARBY only, may be null)
     *   .groundRef     — live GroundItem (NEARBY only, may be null)
     */
    public EntityEntry getSelectedEntry() {
        return entityList.getSelectedValue();
    }

    /** Attach a ListSelectionListener to react to selection changes. */
    public void addSelectionListener(javax.swing.event.ListSelectionListener l) {
        entityList.addListSelectionListener(l);
    }

    /**
     * Attach a double-click handler.
     *   browser.addDoubleClickListener(e -> myField.setText(browser.getSelectedName()));
     */
    public void addDoubleClickListener(MouseAdapter listener) {
        entityList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) listener.mouseClicked(e);
            }
        });
    }

    /** Force a fresh nearby scan and re-apply the filter if in Nearby mode. */
    public void rescanNearby() {
        scanNearby();
        if (currentMode == ViewMode.NEARBY) applyFilter();
    }

    /** Force a fresh library load and re-apply the filter if in Library mode. */
    public void reloadLibrary() {
        populateLibrary();
        if (currentMode == ViewMode.LIBRARY) applyFilter();
    }

    // =========================================================================
    // Utility helpers
    // =========================================================================

    private int distanceTo(org.dreambot.api.methods.map.Tile tile) {
        if (tile == null) return -1;
        try {
            var local = Players.getLocal();
            if (local == null || local.getTile() == null) return -1;
            return (int) local.getTile().distance(tile);
        } catch (Exception e) { return -1; }
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

    /** Builds the search-bar refresh/clear button. Uses icon if path resolves, else ASCII. */
    private JButton buildSearchRefreshButton() {
        JButton btn = new JButton();
        btn.setPreferredSize(new Dimension(24, 24));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBackground(BG_LIST);
        btn.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));

        boolean iconLoaded = false;
        if (SEARCH_REFRESH_ICON_PATH != null) {
            java.io.File f = new java.io.File(SEARCH_REFRESH_ICON_PATH);
            if (f.exists()) {
                try {
                    ImageIcon raw = new ImageIcon(SEARCH_REFRESH_ICON_PATH);
                    Image scaled = raw.getImage().getScaledInstance(14, 14, Image.SCALE_SMOOTH);
                    btn.setIcon(new ImageIcon(scaled));
                    iconLoaded = true;
                } catch (Exception ignored) {}
            }
        }
        if (!iconLoaded) {
            // ASCII fallback — looks like a reset/clear symbol
            btn.setText("(x)");
            btn.setFont(new Font("Consolas", Font.BOLD, 9));
            btn.setForeground(TEXT_DIM);
        }

        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(BG_HOVER_TINT()); }
            public void mouseExited (MouseEvent e) { btn.setBackground(BG_LIST); }
        });
        return btn;
    }

    private Color BG_HOVER_TINT() { return new Color(40, 43, 52); }

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

    private JButton buildSortButton(String text, SortMode mode, boolean active) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Consolas", Font.BOLD, 10));
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(sortTooltip(mode));
        setSortActive(btn, active);
        btn.addActionListener(e -> setSortMode(mode));
        return btn;
    }

    private String sortTooltip(SortMode m) {
        switch (m) {
            case AZ:   return "Sort A \u2192 Z";
            case ZA:   return "Sort Z \u2192 A";
            case NEAR: return "Nearest first";
            case FAR:  return "Farthest first";
            default:   return "";
        }
    }

    private void setSortActive(JButton btn, boolean active) {
        btn.setBackground(active ? new Color(50, 53, 65) : BG_DARK);
        btn.setForeground(active ? TEXT_PRIMARY : TEXT_DIM);
    }

    /**
     * Full-width orange action button.
     * Used for Scan Nearby / Refresh Library.
     */
    private void styleOrangeButton(JButton btn) {
        btn.setFont(new Font("Consolas", Font.BOLD, 11));
        btn.setBackground(ORANGE_BASE);
        btn.setForeground(Color.WHITE);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(5, 12, 5, 12));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(ORANGE_HOVER); }
            public void mouseExited (MouseEvent e) { btn.setBackground(ORANGE_BASE);  }
            public void mousePressed(MouseEvent e) { btn.setBackground(ORANGE_PRESS); }
            public void mouseReleased(MouseEvent e){ btn.setBackground(ORANGE_HOVER); }
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
    // Cell renderer
    // =========================================================================

    private static class EntityCellRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {

            JPanel row = new JPanel(new BorderLayout(5, 0));
            row.setOpaque(true);
            row.setBorder(new EmptyBorder(2, 6, 2, 6));
            row.setBackground(isSelected ? BG_SELECTED
                    : (index % 2 == 0 ? BG_LIST : BG_ROW_ALT));

            if (!(value instanceof EntityEntry)) {
                row.add(new JLabel(value != null ? value.toString() : "—"), BorderLayout.CENTER);
                return row;
            }

            EntityEntry entry = (EntityEntry) value;

            // Left: type badge
            row.add(buildBadge(entry.type), BorderLayout.WEST);

            // Centre: entity name
            JLabel nameLabel = new JLabel(entry.name);
            nameLabel.setFont(new Font("Consolas", Font.PLAIN, 11));
            nameLabel.setForeground(isSelected ? Color.WHITE : TEXT_PRIMARY);
            row.add(nameLabel, BorderLayout.CENTER);

            // Right: distance or "lib" tag
            String rightText;
            if (entry.distanceTiles >= 0) {
                rightText = entry.distanceTiles + "t";   // e.g. "7t"
            } else {
                rightText = "lib";
            }
            JLabel rightLabel = new JLabel(rightText);
            rightLabel.setFont(new Font("Consolas", Font.PLAIN, 9));
            rightLabel.setForeground(isSelected ? new Color(180, 200, 230) : TEXT_DIM);
            rightLabel.setBorder(new EmptyBorder(0, 0, 0, 2));
            row.add(rightLabel, BorderLayout.EAST);

            return row;
        }

        private static JLabel buildBadge(TargetType type) {
            final String icon;
            final Color  color;

            switch (type) {
                case NPC:            icon = ICON_NPC;    color = COLOR_NPC;    break;
                case GAME_OBJECT:    icon = ICON_OBJECT; color = COLOR_OBJECT; break;
                case GROUND_ITEM:    icon = ICON_GROUND; color = COLOR_GROUND; break;
                case PLAYER:         icon = ICON_PLAYER; color = COLOR_PLAYER; break;
                case INVENTORY_ITEM: icon = ICON_INV;    color = COLOR_INV;    break;
                default:             icon = "?";         color = TEXT_DIM;     break;
            }

            JLabel badge = new JLabel(icon) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 45));
                    g2.fill(new RoundRectangle2D.Float(0, 1, getWidth(), getHeight() - 2, 6, 6));
                    g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 150));
                    g2.draw(new RoundRectangle2D.Float(0, 1, getWidth() - 1, getHeight() - 3, 6, 6));
                    g2.dispose();
                    super.paintComponent(g);
                }
            };

            badge.setFont(new Font("Consolas", Font.BOLD, 11));
            badge.setForeground(color);
            badge.setHorizontalAlignment(SwingConstants.CENTER);
            badge.setPreferredSize(new Dimension(18, 16));
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

        @Override protected JButton createDecreaseButton(int o) { return zero(); }
        @Override protected JButton createIncreaseButton(int o) { return zero(); }

        private static JButton zero() {
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
            g2.fill(new RoundRectangle2D.Float(r.x + 1, r.y + 2,
                    r.width - 2, r.height - 4, 6, 6));
            g2.dispose();
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(trackColor);
            g.fillRect(r.x, r.y, r.width, r.height);
        }
    }
}