package main.menu.components;

import main.menu.Theme;
import main.tools.EquipmentPresets;
import main.tools.WikiAssets;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Equipment tab (v1.31): a replica of the in-game worn-equipment interface - the same
 * slot layout you see in the equipment tab - live-updating with what you're wearing, plus
 * equipment PRESETS: save your current setup under a name, pick a saved one from the dropdown,
 * and "Equip preset" builds a task out of the existing actions (FindBank + withdraw + Wield)
 * to change into it - after a clear warning that it interrupts whatever's running.
 *
 * <p>Item images ride the Loot Tracker's opt-in Wiki toggle; offline you get the letter tiles.
 */
public class EquipmentPanel extends JPanel {

    /** The in-game slot grid: 5 rows x 3 columns (null = empty cell). */
    private static final String[][] LAYOUT = {
            {null,     "HEAD",  null},
            {"CAPE",   "AMULET","ARROWS"},
            {"WEAPON", "CHEST", "SHIELD"},
            {null,     "LEGS",  null},
            {"HANDS",  "FEET",  "RING"},
    };

    /** Slot name -> the cell showing it. */
    private final Map<String, SlotCell> cells = new LinkedHashMap<>();
    private final JComboBox<String> presetCombo = new JComboBox<>();
    private final JLabel status = new JLabel(" ");
    private final javax.swing.Timer timer = new javax.swing.Timer(2000, e -> refreshLive());

    /** The menu wires this to: pause script -> queue the equip task -> start it. */
    private java.util.function.Consumer<main.menu.DreamBotMenu.Task> equipRunner;
    private JActionSelector actionFactory;

    public EquipmentPanel() {
        setLayout(new BorderLayout(14, 0));
        setOpaque(false);

        // ── the paper doll ──
        JPanel doll = new JPanel(new GridLayout(LAYOUT.length, 3, 6, 6));
        doll.setOpaque(true);
        doll.setBackground(new Color(0x21, 0x1E, 0x18));   // the in-game brown-dark panel
        doll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 2),
                new EmptyBorder(10, 10, 10, 10)));
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
        JPanel dollWrap = new JPanel(new GridBagLayout());
        dollWrap.setOpaque(false);
        doll.setPreferredSize(new Dimension(230, 340));
        dollWrap.add(doll);

        // ── presets side ──
        JLabel title = new JLabel("Equipment presets");
        title.setForeground(Theme.ACCENT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));

        JLabel blurb = new JLabel("<html>The doll mirrors your worn equipment live, exactly like "
                + "the in-game tab. Save the current setup under a name, then <b>Equip preset</b> "
                + "changes into a saved one - banking automatically for anything you're not "
                + "carrying, using the same Walk/Bank/Wield actions your tasks do.</html>");
        blurb.setForeground(Theme.TEXT_DIM);

        presetCombo.setToolTipText("Your saved equipment setups");
        reloadPresetCombo(null);

        JButton btnSave = new Theme.ThemedButton("Save current as...");
        btnSave.addActionListener(e -> {
            Map<String, String> live = EquipmentPresets.readLive();
            if (live.isEmpty()) {
                status("Nothing worn to save - log in first.", false);
                return;
            }
            String name = JOptionPane.showInputDialog(this, "Preset name:", "Save equipment preset",
                    JOptionPane.PLAIN_MESSAGE);
            if (name == null || name.isBlank()) return;
            EquipmentPresets.put(name.trim(), live);
            reloadPresetCombo(name.trim());
            status("Saved \"" + name.trim() + "\" (" + live.size() + " slots).", true);
        });

        JButton btnEquip = new Theme.ThemedButton("Equip preset");
        btnEquip.putClientProperty("fillColor", new Color(30, 70, 40));
        btnEquip.addActionListener(e -> equipSelected());

        JButton btnDelete = new Theme.ThemedButton("Delete");
        btnDelete.addActionListener(e -> {
            Object sel = presetCombo.getSelectedItem();
            if (sel == null) return;
            if (JOptionPane.showConfirmDialog(this, "Delete preset \"" + sel + "\"?",
                    "Delete preset", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                EquipmentPresets.delete(String.valueOf(sel));
                reloadPresetCombo(null);
            }
        });

        JPanel presetRow = new JPanel(new BorderLayout(6, 0));
        presetRow.setOpaque(false);
        presetRow.add(presetCombo, BorderLayout.CENTER);
        JPanel presetBtns = new JPanel(new GridLayout(1, 2, 4, 0));
        presetBtns.setOpaque(false);
        presetBtns.add(btnEquip);
        presetBtns.add(btnDelete);
        presetRow.add(presetBtns, BorderLayout.EAST);

        status.setForeground(Theme.TEXT_DIM);
        status.setFont(new Font("Segoe UI", Font.ITALIC, 11));

        JPanel side = new JPanel();
        side.setOpaque(false);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        for (JComponent c : new JComponent[]{title, blurb, btnSave, presetRow, status})
            c.setAlignmentX(LEFT_ALIGNMENT);
        side.add(title);
        side.add(Box.createVerticalStrut(6));
        side.add(blurb);
        side.add(Box.createVerticalStrut(14));
        side.add(btnSave);
        side.add(Box.createVerticalStrut(8));
        side.add(presetRow);
        side.add(Box.createVerticalStrut(10));
        side.add(status);
        side.add(Box.createVerticalGlue());

        add(dollWrap, BorderLayout.WEST);
        add(side, BorderLayout.CENTER);

        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) { refreshLive(); timer.start(); } else timer.stop();
            }
        });
    }

    /** The menu injects how an equip task gets run (pause + queue-front + play). */
    public void wire(JActionSelector factory,
                     java.util.function.Consumer<main.menu.DreamBotMenu.Task> runner) {
        this.actionFactory = factory;
        this.equipRunner = runner;
    }

    private void equipSelected() {
        Object sel = presetCombo.getSelectedItem();
        if (sel == null) { status("No preset selected.", false); return; }
        if (actionFactory == null || equipRunner == null) { status("Not wired yet.", false); return; }

        List<String> missing = EquipmentPresets.missingItems(EquipmentPresets.get(String.valueOf(sel)));
        String detail = missing.isEmpty()
                ? "Everything needed is already worn or carried."
                : "Missing from inventory (will bank for these):\n  " + String.join("\n  ", missing);
        int ok = JOptionPane.showConfirmDialog(this,
                "Equip \"" + sel + "\" now?\n\n"
                        + "This PAUSES and interrupts any running script, runs the equip task\n"
                        + "(walking to a bank if needed), then you press Play to resume your queue.\n\n"
                        + detail,
                "Equip preset - interrupts the current script",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;

        main.menu.DreamBotMenu.Task task =
                EquipmentPresets.buildEquipTask(String.valueOf(sel), actionFactory);
        if (task == null) { status("Preset is empty.", false); return; }
        equipRunner.accept(task);
        status("Equip task queued - it runs now.", true);
    }

    private void reloadPresetCombo(String select) {
        presetCombo.removeAllItems();
        for (String n : EquipmentPresets.names()) presetCombo.addItem(n);
        if (select != null) presetCombo.setSelectedItem(select);
    }

    private void status(String text, boolean good) {
        status.setText(text);
        status.setForeground(good ? Theme.TEXT_DIM : new Color(200, 90, 70));
    }

    /** Repaints every slot from the live equipment (off the EDT for the API read). */
    private void refreshLive() {
        Thread t = new Thread(() -> {
            Map<String, String> live = EquipmentPresets.readLive();
            SwingUtilities.invokeLater(() -> {
                for (Map.Entry<String, SlotCell> e : cells.entrySet())
                    e.getValue().setItem(live.get(e.getKey()));
            });
        }, "DreamMan-EquipRead");
        t.setDaemon(true);
        t.start();
    }

    /** One equipment slot: item icon + tooltip, styled like the in-game boxes. */
    private final class SlotCell extends JLabel {
        private final String slot;
        private String item;

        SlotCell(String slot) {
            this.slot = slot;
            setHorizontalAlignment(CENTER);
            setOpaque(true);
            setBackground(new Color(0x3A, 0x35, 0x2B));   // the in-game slot brown
            setBorder(BorderFactory.createLineBorder(new Color(0x18, 0x16, 0x12), 2));
            setPreferredSize(new Dimension(52, 52));
            setToolTipText(pretty(slot) + ": empty");
        }

        void setItem(String name) {
            if (java.util.Objects.equals(name, item)) return;
            item = name;
            if (name == null || name.isBlank()) {
                setIcon(null);
                setText(shortSlot(slot));
                setFont(new Font("Segoe UI", Font.BOLD, 9));
                setForeground(new Color(0x6A, 0x62, 0x50));
                setToolTipText(pretty(slot) + ": empty");
            } else {
                setText("");
                setIcon(WikiAssets.icon(WikiAssets.Kind.ITEM, name, 36, this::repaint));
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
}
