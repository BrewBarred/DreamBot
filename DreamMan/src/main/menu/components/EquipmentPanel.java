package main.menu.components;

import main.menu.Theme;
import main.tools.EquipmentPresets;
import main.tools.WikiAssets;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.wrappers.items.Item;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The worn-equipment doll + live inventory (v1.86) - reshaped from the old Equipment TAB into
 * the compact stack the live side panel hosts: the in-game 5x3 equipment layout, a preset
 * control bar, the 4x7 inventory mirroring your backpack slot-for-slot (stack sizes drawn
 * OSRS-style), and a second identical control bar under it, per the sketch. Presets work like
 * the game's gear setups: save the current loadout under a name, pick one from the dropdown,
 * and Equip builds a task from EXISTING actions (FindBank + withdraw + Wield) - after a clear
 * warning that it interrupts whatever's running.
 *
 * <p>Two long-standing display bugs die here (v1.86): the HEAD (and necklace) cell never
 * populating - the doll asked for {@code HEAD} while DreamBot answers {@code HAT}, fixed by
 * {@link EquipmentPresets#canonicalSlot(String)} on every read - and item images sticking on
 * their letter placeholders until a reload: the wiki fetch completed, but the ready-callback
 * only REPAINTED the old placeholder instead of re-asking for the now-cached image; cells now
 * re-apply the icon when it lands.
 *
 * <p>Item images ride the Loot Tracker's opt-in Wiki toggle; offline you get the letter tiles.
 */
public class EquipmentPanel extends JPanel {

    /** The in-game slot grid: 5 rows x 3 columns (null = empty cell). Canonical slot names. */
    private static final String[][] LAYOUT = {
            {null,     "HEAD",  null},
            {"CAPE",   "AMULET","ARROWS"},
            {"WEAPON", "CHEST", "SHIELD"},
            {null,     "LEGS",  null},
            {"HANDS",  "FEET",  "RING"},
    };

    private static final Color PANEL_BROWN = new Color(0x21, 0x1E, 0x18);  // in-game dark panel
    private static final Color SLOT_BROWN  = new Color(0x3A, 0x35, 0x2B);  // in-game slot fill
    private static final Color SLOT_EDGE   = new Color(0x18, 0x16, 0x12);

    /** Canonical slot name -> the doll cell showing it. */
    private final Map<String, SlotCell> cells = new LinkedHashMap<>();
    /** The 28 inventory cells, index = in-game slot (top-left across, then down). */
    private final InvCell[] invCells = new InvCell[28];

    private final PresetBar barTop = new PresetBar();
    private final PresetBar barBottom = new PresetBar();
    private final JLabel status = new JLabel(" ");
    private final javax.swing.Timer timer = new javax.swing.Timer(2000, e -> refreshLive());

    private String selectedPreset;   // shared by both bars

    /** The menu wires this to: pause script -> queue the equip task -> start it. */
    private java.util.function.Consumer<main.menu.DreamBotMenu.Task> equipRunner;
    private JActionSelector actionFactory;

    public EquipmentPanel() {
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(4, 8, 6, 8));

        // ── the paper doll ──
        JPanel doll = new JPanel(new GridLayout(LAYOUT.length, 3, 5, 5));
        doll.setOpaque(false);
        for (String[] row : LAYOUT)
            for (String slot : row) {
                if (slot == null) {
                    JPanel filler = new JPanel();
                    filler.setOpaque(false);
                    doll.add(filler);
                } else {
                    SlotCell cell = new SlotCell(slot);
                    cells.put(slot, cell);
                    doll.add(cell);
                }
            }
        add(section(" Equipment ", doll));
        add(Box.createVerticalStrut(6));
        add(align(barTop));
        add(Box.createVerticalStrut(10));

        // ── the inventory ──
        JPanel grid = new JPanel(new GridLayout(7, 4, 4, 4));
        grid.setOpaque(false);
        for (int i = 0; i < invCells.length; i++) {
            invCells[i] = new InvCell(i);
            grid.add(invCells[i]);
        }
        add(section(" Inventory ", grid));
        add(Box.createVerticalStrut(6));
        add(align(barBottom));
        add(Box.createVerticalStrut(6));

        status.setForeground(Theme.TEXT_DIM);
        status.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        status.setHorizontalAlignment(SwingConstants.CENTER);
        add(align(status));

        syncBars();

        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) { refreshLive(); timer.start(); } else timer.stop();
            }
        });
    }

    /** Wraps content in the in-game brown panel with a gold titled border, centred. */
    private JComponent section(String title, JComponent content) {
        JPanel box = new JPanel(new GridBagLayout());
        box.setOpaque(true);
        box.setBackground(PANEL_BROWN);
        TitledBorder tb = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 2), title);
        tb.setTitleColor(Theme.ACCENT);
        tb.setTitleFont(Theme.monoBold(12));
        box.setBorder(BorderFactory.createCompoundBorder(tb, new EmptyBorder(4, 8, 8, 8)));
        box.add(content);
        return align(box);
    }

    private static JComponent align(JComponent c) {
        c.setAlignmentX(CENTER_ALIGNMENT);
        return c;
    }

    /** The menu injects how an equip task gets run (pause + queue-front + play). */
    public void wire(JActionSelector factory,
                     java.util.function.Consumer<main.menu.DreamBotMenu.Task> runner) {
        this.actionFactory = factory;
        this.equipRunner = runner;
    }

    // ── presets ───────────────────────────────────────────────────────────────

    /**
     * One [Equip] [<preset> v] control bar - the sketch puts an identical pair under the doll
     * and under the inventory, so this exists twice with shared selection state.
     */
    private final class PresetBar extends JPanel {
        final JButton btnEquip = small(new Theme.ThemedButton("Equip"));
        final JButton btnSelect = small(new Theme.ThemedButton());

        PresetBar() {
            setOpaque(false);
            setLayout(new BorderLayout(6, 0));
            btnEquip.setToolTipText("Change into the selected preset (interrupts the running script)");
            btnEquip.addActionListener(e -> equipSelected());
            btnSelect.setIcon(UIIcons.caretDown(12, Theme.TEXT_DIM));
            btnSelect.setHorizontalTextPosition(SwingConstants.LEFT);
            btnSelect.setIconTextGap(6);
            btnSelect.setToolTipText("Pick, save, rename or delete equipment presets");
            btnSelect.addActionListener(e -> showPresetMenu(btnSelect));
            add(btnEquip, BorderLayout.WEST);
            add(btnSelect, BorderLayout.CENTER);
            setMaximumSize(new Dimension(240, 28));
            setPreferredSize(new Dimension(230, 26));
        }

        private JButton small(JButton b) {
            b.setFont(Theme.font(11));
            b.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
            return b;
        }
    }

    private void syncBars() {
        String label = selectedPreset == null ? "Select preset" : selectedPreset;
        for (PresetBar bar : new PresetBar[]{barTop, barBottom}) {
            bar.btnSelect.setText(label);
            bar.btnEquip.setEnabled(selectedPreset != null);
        }
    }

    private void showPresetMenu(Component anchor) {
        JPopupMenu menu = new JPopupMenu();
        List<String> names = EquipmentPresets.names();
        if (names.isEmpty()) {
            JMenuItem none = new JMenuItem("No presets saved yet");
            none.setEnabled(false);
            menu.add(none);
        } else {
            for (String n : names) {
                JRadioButtonMenuItem item = new JRadioButtonMenuItem(n, n.equals(selectedPreset));
                item.addActionListener(e -> {
                    selectedPreset = n;
                    syncBars();
                    status("Selected \"" + n + "\" (" + EquipmentPresets.get(n).size() + " slots).", true);
                });
                menu.add(item);
            }
        }
        menu.addSeparator();

        JMenuItem save = new JMenuItem("Save current as...");
        save.addActionListener(e -> {
            Map<String, String> live = EquipmentPresets.readLive();
            if (live.isEmpty()) {
                status("Nothing worn to save - log in first.", false);
                return;
            }
            String name = JOptionPane.showInputDialog(this, "Preset name:", "Save equipment preset",
                    JOptionPane.PLAIN_MESSAGE);
            if (name == null || name.isBlank()) return;
            // v1.87: the tiered preset cap - overwriting an existing name is always allowed
            // (no new slot), only NEW presets count against the limit. Free 8 · VIP 28 ·
            // Subscriber 64 · Lifetime and staff unlimited (the server can override).
            int cap = main.market.Tier.presetLimit();
            boolean isNew = !EquipmentPresets.names().contains(name.trim());
            if (isNew && EquipmentPresets.names().size() >= cap) {
                status("Preset limit reached (" + cap + " on "
                        + main.market.Tier.label() + ").", false);
                JOptionPane.showMessageDialog(this,
                        "You've used all " + cap + " preset slots your "
                                + main.market.Tier.label() + " account gets.\n\n"
                                + "Free accounts keep 8, VIPs 28, subscribers 64, and lifetime\n"
                                + "supporters have no limit - or delete a preset you've outgrown.",
                        "Preset limit", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            EquipmentPresets.put(name.trim(), live);
            selectedPreset = name.trim();
            syncBars();
            status("Saved \"" + name.trim() + "\" (" + live.size() + " slots).", true);
        });
        menu.add(save);

        JMenuItem rename = new JMenuItem("Rename selected...");
        rename.setEnabled(selectedPreset != null);
        rename.addActionListener(e -> {
            String name = (String) JOptionPane.showInputDialog(this, "New name:", "Rename preset",
                    JOptionPane.PLAIN_MESSAGE, null, null, selectedPreset);
            if (name == null || name.isBlank() || name.trim().equals(selectedPreset)) return;
            if (EquipmentPresets.rename(selectedPreset, name.trim())) {
                selectedPreset = name.trim();
                syncBars();
                status("Renamed to \"" + name.trim() + "\".", true);
            } else {
                status("A preset named \"" + name.trim() + "\" already exists.", false);
            }
        });
        menu.add(rename);

        JMenuItem delete = new JMenuItem("Delete selected...");
        delete.setEnabled(selectedPreset != null);
        delete.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Delete preset \"" + selectedPreset + "\"?",
                    "Delete preset", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                EquipmentPresets.delete(selectedPreset);
                selectedPreset = null;
                syncBars();
                status("Preset deleted.", true);
            }
        });
        menu.add(delete);

        menu.show(anchor, 0, anchor.getHeight());
    }

    private void equipSelected() {
        if (selectedPreset == null) { status("No preset selected.", false); return; }
        if (actionFactory == null || equipRunner == null) { status("Not wired yet.", false); return; }

        List<String> missing = EquipmentPresets.missingItems(EquipmentPresets.get(selectedPreset));
        String detail = missing.isEmpty()
                ? "Everything needed is already worn or carried."
                : "Missing from inventory (will bank for these):\n  " + String.join("\n  ", missing);
        int ok = JOptionPane.showConfirmDialog(this,
                "Equip \"" + selectedPreset + "\" now?\n\n"
                        + "This PAUSES and interrupts any running script, runs the equip task\n"
                        + "(walking to a bank if needed), then you press Play to resume your queue.\n\n"
                        + detail,
                "Equip preset - interrupts the current script",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;

        main.menu.DreamBotMenu.Task task =
                EquipmentPresets.buildEquipTask(selectedPreset, actionFactory);
        if (task == null) { status("Preset is empty.", false); return; }
        equipRunner.accept(task);
        status("Equip task queued - it runs now.", true);
    }

    private void status(String text, boolean good) {
        status.setText(text);
        status.setForeground(good ? Theme.TEXT_DIM : new Color(200, 90, 70));
    }

    // ── live refresh ──────────────────────────────────────────────────────────

    /** Re-reads worn equipment AND the inventory off the EDT, then repaints every cell. */
    private void refreshLive() {
        Thread t = new Thread(() -> {
            Map<String, String> worn = EquipmentPresets.readLive();
            String[] invNames = new String[28];
            int[] invCounts = new int[28];
            try {
                List<Item> items = Inventory.all();   // slot-indexed; empty slots are null
                if (items != null)
                    for (int i = 0; i < items.size() && i < 28; i++) {
                        Item it = items.get(i);
                        if (it == null || it.getName() == null || it.getName().isEmpty()
                                || "null".equalsIgnoreCase(it.getName())) continue;
                        invNames[i] = it.getName();
                        invCounts[i] = Math.max(1, it.getAmount());
                    }
            } catch (Throwable ignored) {}
            SwingUtilities.invokeLater(() -> {
                for (Map.Entry<String, SlotCell> e : cells.entrySet())
                    e.getValue().setItem(worn.get(e.getKey()));
                for (int i = 0; i < 28; i++)
                    invCells[i].setItem(invNames[i], invCounts[i]);
            });
        }, "DreamMan-EquipRead");
        t.setDaemon(true);
        t.start();
    }

    // ── cells ─────────────────────────────────────────────────────────────────

    /**
     * The shared item tile: in-game brown box showing the item's wiki image. The icon is
     * (re-)applied through {@link #applyIcon()} which passes ITSELF as the ready-callback -
     * the v1.86 fix for images stuck on placeholders: when the background fetch lands, the
     * cell asks {@link WikiAssets#icon} again, hits the now-warm cache, and swaps the real
     * image in (a cache hit registers no callback, so this terminates immediately).
     */
    private abstract static class ItemBox extends JLabel {
        private final int iconPx;
        String item;

        ItemBox(int cellPx, int iconPx) {
            this.iconPx = iconPx;
            setHorizontalAlignment(CENTER);
            setOpaque(true);
            setBackground(SLOT_BROWN);
            setBorder(BorderFactory.createLineBorder(SLOT_EDGE, 2));
            setPreferredSize(new Dimension(cellPx, cellPx));
            setMinimumSize(new Dimension(cellPx, cellPx));
        }

        final void applyIcon() {
            if (item == null) return;
            setIcon(WikiAssets.icon(WikiAssets.Kind.ITEM, item, iconPx, this::applyIcon));
        }

        void empty() {
            setIcon(null);
            setText("");
        }
    }

    /** One equipment slot, keyed by its canonical name. */
    private final class SlotCell extends ItemBox {
        private final String slot;

        SlotCell(String slot) {
            super(46, 34);
            this.slot = slot;
            showEmpty();   // labelled from the first paint - setItem(null) would no-op (item
                           // already null), which used to leave brand-new cells blank
        }

        private void showEmpty() {
            empty();
            setText(shortSlot(slot));
            setFont(new Font("Segoe UI", Font.BOLD, 9));
            setForeground(new Color(0x6A, 0x62, 0x50));
            setToolTipText(pretty(slot) + ": empty");
        }

        void setItem(String name) {
            if (java.util.Objects.equals(name, item)) return;
            item = name;
            if (name == null || name.isBlank()) {
                showEmpty();
            } else {
                setText("");
                applyIcon();
                setToolTipText(pretty(slot) + ": " + name);
            }
        }

        private String pretty(String s) {
            return s.charAt(0) + s.substring(1).toLowerCase();
        }

        private String shortSlot(String s) {
            switch (s) {
                case "AMULET": return "NECK";
                case "ARROWS": return "AMMO";
                case "WEAPON": return "WEAP";
                case "CHEST":  return "BODY";
                case "HANDS":  return "GLOVE";
                case "FEET":   return "BOOTS";
                default:       return s;
            }
        }
    }

    /** One inventory slot: the item image plus its stack size, drawn like the game draws it. */
    private final class InvCell extends ItemBox {
        private final int slot;
        private int count;

        InvCell(int slot) {
            super(38, 30);
            this.slot = slot;
            setToolTipText("Slot " + (slot + 1) + ": empty");
        }

        void setItem(String name, int amount) {
            if (java.util.Objects.equals(name, item) && amount == count) return;
            boolean sameItem = java.util.Objects.equals(name, item);
            item = name;
            count = amount;
            if (name == null) {
                empty();
                setToolTipText("Slot " + (slot + 1) + ": empty");
            } else {
                setText("");
                if (!sameItem) applyIcon();   // count-only changes just need the repaint below
                setToolTipText("Slot " + (slot + 1) + ": " + name
                        + (count > 1 ? "  \u00d7" + String.format("%,d", count) : ""));
            }
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (item == null || count <= 1) return;
            // OSRS stack text: yellow, then "K" white past 100K, "M" green past 10M
            String txt;
            Color col;
            if (count >= 10_000_000)     { txt = (count / 1_000_000) + "M"; col = new Color(0x00, 0xFF, 0x80); }
            else if (count >= 100_000)   { txt = (count / 1_000) + "K";     col = Color.WHITE; }
            else                         { txt = String.valueOf(count);     col = new Color(0xFF, 0xFF, 0x00); }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g2.setColor(Color.BLACK);
            g2.drawString(txt, 4, 12);
            g2.setColor(col);
            g2.drawString(txt, 3, 11);
            g2.dispose();
        }
    }
}
