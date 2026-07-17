package main.menu.components;

import main.data.Library;
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
    private static final Color COLOR_LOCATION = new Color(160, 110, 210); // violet (v1.31)
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
        public final Library.TargetType type;
        public final String     source;   // "NEARBY" or "LIBRARY"

        // Live DreamBot refs — non-null only for NEARBY entries
        public NPC        npcRef;
        public GameObject objectRef;
        public GroundItem groundRef;

        /**
         * Patch B.17: the entity's tile, captured at scan time (nearby) or from the static
         * Library data. Null when unknown (e.g. inventory items, or library objects that are
         * resolved live). Coordinates are the single most-used value in the Task Builder, so
         * they're carried on the row instead of hiding behind a live ref.
         */
        public Integer x, y, z;
        /** Ground-item stack size (1 for everything else). */
        public int quantity = 1;

        /** v1.31: a human area label ("Lumbridge", "Varrock") when the library knows one. */
        public String area;
        /** v1.31: set for LOCATION entries - carries the bounds for the nearby check. */
        public Library.Locations locationRef;

        public EntityEntry(String name, Library.TargetType type, String source) {
            this.name   = name;
            this.type   = type;
            this.source = source;
        }

        /** Remembers a tile on this entry (no-op for a null tile). @return this, for chaining. */
        public EntityEntry withTile(org.dreambot.api.methods.map.Tile t) {
            if (t != null) { this.x = t.getX(); this.y = t.getY(); this.z = t.getZ(); }
            return this;
        }

        /** @return true when this entry knows where it is. */
        public boolean hasTile() { return x != null && y != null; }

        /** The tile as the "X, Y, Z" string every location parameter in DreamMan accepts. */
        public String tileString() {
            return hasTile() ? x + ", " + y + ", " + (z == null ? 0 : z) : null;
        }

        /** v1.31: remembers the area label. @return this, for chaining. */
        public EntityEntry withArea(String a) {
            if (a != null && !a.isBlank()) this.area = a.trim();
            return this;
        }

        /** Compact row text for the coords, e.g. "3258,3271" (z shown only when non-zero). */
        public String coordsLabel() {
            if (!hasTile()) return null;
            return x + "," + y + (z != null && z != 0 ? "," + z : "");
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

    /** Single background worker for nearby scans (Patch B.1) - keeps DreamBot API calls off the EDT. */
    private final java.util.concurrent.ExecutorService scanPool =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "DreamMan-Scan");
                t.setDaemon(true);
                return t;
            });

    /** Coalesces overlapping scan requests - only one scan runs at a time. */
    private final java.util.concurrent.atomic.AtomicBoolean scanInFlight =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    // =========================================================================
    // UI components (kept as fields so applyFilter / switchTo can access them)
    // =========================================================================
    private JButton   btnNearby;
    private JButton   btnLibrary;
    private JTextField searchField;
    private JList<EntityEntry> entityList;
    private JLabel    statusLabel;
    /** v1.31: captured on every background scan - drives the nearby-library merge. */
    private volatile org.dreambot.api.methods.map.Tile lastKnownPlayerTile;
    /** v1.31: where clicked chips (name/position/area/my-position) write their value. */
    private java.util.function.Consumer<String> targetSink;
    // the selected-entity detail strip
    private JPanel stripPanel;
    private JButton chipName, chipPos, chipArea, chipMyPos;
    private JButton   refreshBtn;

    private JCheckBox cbNPC;
    private JCheckBox cbObject;
    private JCheckBox cbGround;
    private JCheckBox cbPlayer;
    private JCheckBox cbLocation;   // v1.31
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
        switchTo(ViewMode.NEARBY);
        // Patch B.1: the first nearby scan runs on the background worker, not the EDT, so
        // building the menu never stalls on DreamBot API calls.
        rescanNearby();
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

        btnNearby  = buildTabButton("Nearby",  true);
        btnLibrary = buildTabButton("Library", false);

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

        // tiny clear button (plain "x" - glyph-safe, B.17) on the right of the search bar
        JButton clearBtn = new JButton("x");
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
        // Patch B.1: bind the dark scrollbar per-instance via updateUI() - immune to the client
        // LAF's updateUI sweeps and to classloader lookups (no UIManager involvement).
        JScrollBar darkBar = new JScrollBar(JScrollBar.VERTICAL) {
            @Override public void updateUI() { setUI(new DarkScrollBarUI()); }
        };
        darkBar.setPreferredSize(new Dimension(7, 0));
        darkBar.setUnitIncrement(16);
        scroll.setVerticalScrollBar(darkBar);
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
        cbLocation  = buildCheckbox("Location",    COLOR_LOCATION, true);   // v1.31
        cbInventory = buildCheckbox("Inventory",   COLOR_INV,    false);

        ActionListener filterListener = e -> applyFilter();
        cbNPC.addActionListener(filterListener);
        cbObject.addActionListener(filterListener);
        cbGround.addActionListener(filterListener);
        cbPlayer.addActionListener(filterListener);
        cbInventory.addActionListener(filterListener);
        cbLocation.addActionListener(filterListener);   // v1.31

        // Two-column grid for checkboxes
        JPanel cbGrid = new JPanel(new GridLayout(3, 2, 4, 0));
        cbGrid.setBackground(BG_PANEL);
        cbGrid.add(cbNPC);
        cbGrid.add(cbObject);
        cbGrid.add(cbGround);
        cbGrid.add(cbPlayer);
        cbGrid.add(cbInventory);
        cbGrid.add(cbLocation);   // v1.31: filled the old spacer slot

        cbWrap.add(filterLbl);
        cbWrap.add(Box.createVerticalStrut(3));
        cbWrap.add(cbGrid);

        // ── v1.31: selected-entity strip ─────────────────────────────────────
        // Clicking a row used to make the builder GUESS what you wanted (name? tile?). Now the
        // strip shows the selected entry's Name / Position / Area as buttons - click the one
        // you want in the target - plus "My position", which drops YOUR current tile in
        // (the "current position button near the target input" ask).
        stripPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 2));
        stripPanel.setBackground(BG_PANEL);
        chipName = buildChip("name");
        chipPos = buildChip("position");
        chipArea = buildChip("area");
        chipMyPos = buildChip("My position");
        chipMyPos.setToolTipText("Insert YOUR current tile into the target field");
        chipMyPos.addActionListener(e -> {
            try {
                var me = Players.getLocal();
                var t = me == null ? null : me.getTile();
                if (t != null && targetSink != null)
                    targetSink.accept(t.getX() + ", " + t.getY() + ", " + t.getZ());
            } catch (Throwable ignored) {}
        });
        stripPanel.add(chipMyPos);
        stripPanel.add(chipName);
        stripPanel.add(chipPos);
        stripPanel.add(chipArea);
        chipName.setVisible(false);
        chipPos.setVisible(false);
        chipArea.setVisible(false);
        entityList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) refreshStrip(entityList.getSelectedValue());
        });

        // ── Action row: status + refresh ─────────────────────────────────────
        JPanel actionRow = new JPanel(new BorderLayout(4, 0));
        actionRow.setBackground(BG_PANEL);
        actionRow.setBorder(new EmptyBorder(4, 0, 0, 0));

        statusLabel = new JLabel("Ready");
        statusLabel.setFont(new Font("Consolas", Font.PLAIN, 9));
        statusLabel.setForeground(TEXT_DIM);

        refreshBtn = new JButton("Scan Nearby");
        styleActionButton(refreshBtn, ACCENT_BLUE, ACCENT_HOVER);
        refreshBtn.addActionListener(e -> {
            if (currentMode == ViewMode.NEARBY) {
                rescanNearby();   // async (Patch B.1) - the click never blocks the EDT
            } else {
                populateLibrary();
                applyFilter();
            }
        });

        actionRow.add(statusLabel, BorderLayout.CENTER);
        actionRow.add(refreshBtn,  BorderLayout.EAST);

        JPanel southStack = new JPanel(new BorderLayout());
        southStack.setBackground(BG_PANEL);
        southStack.add(stripPanel, BorderLayout.NORTH);   // v1.31: clickable target chips
        southStack.add(actionRow, BorderLayout.SOUTH);

        bottom.add(cbWrap,     BorderLayout.CENTER);
        bottom.add(southStack, BorderLayout.SOUTH);
        return bottom;
    }

    // ── v1.31: chip strip plumbing ───────────────────────────────────────────

    /** A small pill button for the strip. */
    private JButton buildChip(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("Consolas", Font.PLAIN, 10));
        b.setForeground(TEXT_PRIMARY);
        b.setBackground(new Color(45, 45, 45));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                new EmptyBorder(2, 7, 2, 7)));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** The Task Builder registers where chip clicks should write their value. */
    public void setTargetSink(java.util.function.Consumer<String> sink) {
        this.targetSink = sink;
    }

    /** Rebinds the chips to the selected entry (hidden when there's nothing to offer). */
    private void refreshStrip(EntityEntry e) {
        if (e == null) {
            chipName.setVisible(false);
            chipPos.setVisible(false);
            chipArea.setVisible(false);
        } else {
            chipName.setText(trimChip(e.name));
            chipName.setToolTipText("Use the NAME as the target: " + e.name);
            for (java.awt.event.ActionListener l : chipName.getActionListeners())
                chipName.removeActionListener(l);
            chipName.addActionListener(a -> { if (targetSink != null) targetSink.accept(e.name); });
            chipName.setVisible(true);

            String tile = e.tileString();
            chipPos.setVisible(tile != null);
            if (tile != null) {
                chipPos.setText(e.coordsLabel());
                chipPos.setToolTipText("Use the POSITION as the target: " + tile);
                for (java.awt.event.ActionListener l : chipPos.getActionListeners())
                    chipPos.removeActionListener(l);
                chipPos.addActionListener(a -> { if (targetSink != null) targetSink.accept(tile); });
            }

            chipArea.setVisible(e.area != null);
            if (e.area != null) {
                chipArea.setText(trimChip(e.area));
                chipArea.setToolTipText("Use the AREA name as the target: " + e.area);
                for (java.awt.event.ActionListener l : chipArea.getActionListeners())
                    chipArea.removeActionListener(l);
                chipArea.addActionListener(a -> { if (targetSink != null) targetSink.accept(e.area); });
            }
        }
        stripPanel.revalidate();
        stripPanel.repaint();
    }

    private static String trimChip(String s) {
        return s == null ? "" : (s.length() <= 16 ? s : s.substring(0, 15) + "\u2026");
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
        List<EntityEntry> fresh = new ArrayList<>();
        scanInto(fresh);
        nearbyFull.clear();
        nearbyFull.addAll(fresh);
        setStatus(nearbyFull.size() + " entities nearby");
    }

    /**
     * The actual environment scan, writing into {@code out}. Pure of UI state so it can run on
     * the background worker (Patch B.1); callers publish the result to the EDT themselves.
     */
    private void scanInto(List<EntityEntry> out) {
        try {
            var me0 = Players.getLocal();
            if (me0 != null && me0.getTile() != null) lastKnownPlayerTile = me0.getTile();
        } catch (Throwable ignored) {}
        try {
            // ── NPCs ──────────────────────────────────────────────────────────
            List<NPC> liveNpcs = NPCs.all(n -> n != null
                    && n.getName() != null
                    && !n.getName().isEmpty()
                    && !n.getName().equalsIgnoreCase("null"));
            if (liveNpcs != null) {
                for (NPC npc : liveNpcs) {
                    if (withinRadius(npc.getTile())) {
                        EntityEntry e = new EntityEntry(npc.getName(), Library.TargetType.NPC, "NEARBY")
                                .withTile(npc.getTile());   // B.17: coords ride on the row
                        e.npcRef = npc;
                        addUnique(out, e);
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
                        EntityEntry e = new EntityEntry(obj.getName(), Library.TargetType.GAME_OBJECT, "NEARBY")
                                .withTile(obj.getTile());
                        e.objectRef = obj;
                        addUnique(out, e);
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
                        EntityEntry e = new EntityEntry(item.getName(), Library.TargetType.GROUND_ITEM, "NEARBY")
                                .withTile(item.getTile());
                        // B.17: stack size on the row (same defensive read LootTracker uses)
                        try { e.quantity = Math.max(1, item.getAmount()); } catch (Throwable ignored) {}
                        e.groundRef = item;
                        addUnique(out, e);
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
                            addUnique(out, new EntityEntry(p.getName(), Library.TargetType.PLAYER, "NEARBY")
                                    .withTile(p.getTile()));
                        }
                    }
                }
            }

        } catch (Exception ignored) {
            // Client not running — the list stays empty, which is fine
        }
    }

    /** Loads all static Library enum entries into libraryFull. */
    private void populateLibrary() {
        libraryFull.clear();

        for (Library.Npcs n : Library.Npcs.values()) {
            libraryFull.add(new EntityEntry(n.npcName, Library.TargetType.NPC, "LIBRARY")
                    .withTile(n.approxTile)     // B.17: known spawn coords on the row
                    .withArea(n.approxArea));   // v1.31: area label for the detail strip
        }
        for (Library.GameObjects o : Library.GameObjects.values()) {
            // objects are resolved live (no stored tile) - the row just shows the name
            libraryFull.add(new EntityEntry(o.objectName, Library.TargetType.GAME_OBJECT, "LIBRARY"));
        }
        for (Library.GroundItems i : Library.GroundItems.values()) {
            libraryFull.add(new EntityEntry(i.itemName, Library.TargetType.GROUND_ITEM, "LIBRARY")
                    .withTile(i.spawnTile));
        }
        // v1.31: named Locations - Lumbridge Castle and friends
        for (Library.Locations loc : Library.Locations.values()) {
            EntityEntry e = new EntityEntry(loc.locationName, Library.TargetType.LOCATION, "LIBRARY")
                    .withTile(loc.center)
                    .withArea(loc.region);
            e.locationRef = loc;
            libraryFull.add(e);
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
        refreshBtn.setText(mode == ViewMode.NEARBY ? "Scan Nearby" : "Refresh");
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
        Set<Library.TargetType> allowed = EnumSet.noneOf(Library.TargetType.class);
        if (cbNPC.isSelected())       allowed.add(Library.TargetType.NPC);
        if (cbObject.isSelected())    allowed.add(Library.TargetType.GAME_OBJECT);
        if (cbGround.isSelected())    allowed.add(Library.TargetType.GROUND_ITEM);
        if (cbPlayer.isSelected())    allowed.add(Library.TargetType.PLAYER);
        if (cbInventory.isSelected()) allowed.add(Library.TargetType.INVENTORY_ITEM);
        if (cbLocation.isSelected())  allowed.add(Library.TargetType.LOCATION);   // v1.31

        // v1.31: Nearby also surfaces LIBRARY entries you're standing near - known spawns,
        // objects with tiles, and Locations whose area you're inside ("you're at Lumbridge
        // Castle"). The live scan captures where YOU are; the library adds what we KNOW is
        // there. Tagged "lib" so the source stays obvious.
        List<EntityEntry> source;
        if (currentMode == ViewMode.NEARBY) {
            source = new ArrayList<>(nearbyFull);
            org.dreambot.api.methods.map.Tile me = lastKnownPlayerTile;
            if (me != null) {
                for (EntityEntry lib : libraryFull) {
                    if (lib == null) continue;
                    boolean near = false;
                    if (lib.type == Library.TargetType.LOCATION && lib.locationRef != null)
                        near = lib.locationRef.isNear(me, NEARBY_RADIUS);
                    else if (lib.hasTile())
                        near = new org.dreambot.api.methods.map.Tile(lib.x, lib.y,
                                lib.z == null ? 0 : lib.z).distance(me) <= NEARBY_RADIUS;
                    if (near) source.add(lib);
                }
            }
        } else {
            source = libraryFull;
        }

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

    /**
     * Patch B.17: fires on ANY left-click of a row - the hook the Task Builder uses to auto-fill
     * the current action's target. Deliberately a mouse gesture rather than a selection
     * listener: background rescans re-apply the selection programmatically, and that must never
     * overwrite a target the user has hand-edited since.
     */
    public void addClickListener(java.util.function.Consumer<EntityEntry> listener) {
        entityList.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                int idx = entityList.locationToIndex(e.getPoint());
                if (idx < 0) return;
                Rectangle cell = entityList.getCellBounds(idx, idx);
                if (cell == null || !cell.contains(e.getPoint())) return;
                entityList.setSelectedIndex(idx);
                EntityEntry entry = entityList.getSelectedValue();
                if (entry != null) listener.accept(entry);
            }
        });
    }

    /**
     * Refreshes the Nearby view (Patch B.1): re-filters the CACHED scan immediately - so
     * switching actions updates the visible list instantly - then kicks a fresh scan on the
     * background worker; results land on the EDT when ready. Overlapping calls coalesce.
     */
    public void rescanNearby() {
        if (currentMode == ViewMode.NEARBY) applyFilter();   // instant, from cache

        if (!scanInFlight.compareAndSet(false, true))
            return;                                           // a scan is already running

        scanPool.submit(() -> {
            final List<EntityEntry> fresh = new ArrayList<>();
            try {
                scanInto(fresh);
            } finally {
                SwingUtilities.invokeLater(() -> {
                    nearbyFull.clear();
                    nearbyFull.addAll(fresh);
                    setStatus(fresh.size() + " entities nearby");
                    if (currentMode == ViewMode.NEARBY) applyFilter();
                    scanInFlight.set(false);
                });
            }
        });
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

            // Entity name (+ stack size for items, Patch B.17)
            JLabel name = new JLabel(entry.name + (entry.quantity > 1 ? "  x" + entry.quantity : ""));
            name.setFont(new Font("Consolas", Font.PLAIN, 11));
            name.setForeground(isSelected ? Color.WHITE : TEXT_PRIMARY);
            row.add(name, BorderLayout.CENTER);

            // Right side: coordinates (the value you'll actually paste into Walk/dig targets)
            // with the tiny live/lib tag after them (Patch B.17)
            String coords = entry.coordsLabel();
            JLabel src = new JLabel((coords != null ? coords + " \u00b7 " : "")
                    + (entry.source.equals("NEARBY") ? "live" : "lib"));
            src.setFont(new Font("Consolas", Font.PLAIN, 9));
            src.setForeground(isSelected ? new Color(180, 200, 230) : TEXT_DIM);
            src.setBorder(new EmptyBorder(0, 0, 0, 2));
            row.add(src, BorderLayout.EAST);

            String tip = "<html><b>" + entry.name + "</b>"
                    + (entry.quantity > 1 ? " x" + entry.quantity : "")
                    + (entry.tileString() != null ? "<br>Tile: " + entry.tileString() : "")
                    + "<br><i>Click to use as the current action's target</i></html>";
            row.setToolTipText(tip);

            return row;
        }

        private static JLabel buildBadge(Library.TargetType type) {
            final String text;
            final Color  color;

            switch (type) {
                case NPC:            text = "NPC"; color = COLOR_NPC;    break;
                case GAME_OBJECT:    text = "OBJ"; color = COLOR_OBJECT; break;
                case LOCATION:       text = "LOC"; color = COLOR_LOCATION; break;
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