package main.menu.components;

import main.menu.Theme;
import main.tools.LootTracker;
import main.tools.WikiAssets;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;

/**
 * The RuneLite-style Loot Tracker (v1.30). One box per NPC - its image, "x N" session kills with
 * the character's lifetime total beside it, and a grid of item icons with stack quantities and
 * GE values - plus a session header (total kills, total value, gp/hr) and the options RuneLite
 * users expect: sorting, per-NPC reset (right-click), session reset, lifetime reset.
 *
 * <p>Images and prices come from {@link WikiAssets} and are strictly opt-in via the
 * "Icons &amp; prices" toggle; until then the boxes use drawn placeholders and skip gp values,
 * and everything else works fully offline.
 */
public class LootTrackerPanel extends JPanel {

    private static final int ICON_NPC = 40, ICON_ITEM = 34;

    private final JLabel summary = new JLabel(" ");
    private final JComboBox<String> sort =
            new JComboBox<>(new String[]{"Most valuable", "Most kills", "A-Z", "Recent"});
    private final JCheckBox chkOnline = new JCheckBox("Icons & prices (OSRS Wiki)");
    private final JPanel boxes = new JPanel();
    private final JLabel empty = new JLabel();
    private final javax.swing.Timer refreshTimer = new javax.swing.Timer(2500, e -> refreshNow());

    public LootTrackerPanel() {
        setLayout(new BorderLayout(0, 8));
        setOpaque(false);

        // ── header: summary + options ──
        summary.setForeground(Theme.TEXT_DIM);
        summary.setFont(new Font("Consolas", Font.PLAIN, 12));

        sort.setToolTipText("How the NPC boxes are ordered");
        sort.addActionListener(e -> refreshNow());

        chkOnline.setOpaque(false);
        chkOnline.setForeground(Theme.TEXT);
        chkOnline.setSelected(WikiAssets.isEnabled());
        chkOnline.setToolTipText("<html>Fetch item/NPC images and Grand Exchange prices from the "
                + "OSRS Wiki.<br>Only the looted names being looked up are sent; everything is "
                + "cached on disk.<br>Off = drawn placeholders, no gp values, fully offline.</html>");
        chkOnline.addActionListener(e -> {
            WikiAssets.setEnabled(chkOnline.isSelected());
            if (chkOnline.isSelected()) WikiAssets.ensurePrices(this::refreshNow);
            refreshNow();
        });

        JButton btnRefresh = new Theme.ThemedButton("Refresh");
        btnRefresh.addActionListener(e -> refreshNow());
        JButton btnResetSession = new Theme.ThemedButton("Reset session");
        btnResetSession.putClientProperty("fillColor", new Color(90, 30, 30));
        btnResetSession.addActionListener(e -> {
            LootTracker.resetSession();
            refreshNow();
        });
        JButton btnResetLife = new Theme.ThemedButton("Reset lifetime");
        btnResetLife.setToolTipText("Wipes this character's saved lifetime kill counts and drops");
        btnResetLife.addActionListener(e -> {
            int ok = JOptionPane.showConfirmDialog(this,
                    "Wipe the LIFETIME kill counts and drops saved for \""
                            + LootTracker.getCharacter() + "\"?\nThe current session keeps counting.",
                    "Reset lifetime", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ok == JOptionPane.YES_OPTION) {
                LootTracker.resetLifetime();
                refreshNow();
            }
        });

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        controls.setOpaque(false);
        controls.add(chkOnline);
        controls.add(sort);
        controls.add(btnRefresh);
        controls.add(btnResetSession);
        controls.add(btnResetLife);

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.add(summary, BorderLayout.WEST);
        header.add(controls, BorderLayout.EAST);

        // ── body: the NPC boxes ──
        boxes.setLayout(new BoxLayout(boxes, BoxLayout.Y_AXIS));
        boxes.setOpaque(false);

        empty.setForeground(Theme.TEXT_DIM);
        empty.setBorder(new EmptyBorder(18, 8, 8, 8));
        empty.setText("<html>No kills recorded yet.<br><br>Kill NPCs with an Interact (Attack) "
                + "action and loot near them - drops seen on your kill tiles are tracked here "
                + "per NPC, RuneLite-style.</html>");

        JPanel bodyWrap = new JPanel(new BorderLayout());
        bodyWrap.setOpaque(false);
        bodyWrap.add(boxes, BorderLayout.NORTH);   // boxes hug the top; the scroll fills below

        JScrollPane scroll = Theme.thinScrollbars(new JScrollPane(bodyWrap));
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        // auto-refresh only while this tab is actually on screen
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) {
                    refreshNow();
                    if (WikiAssets.isEnabled()) WikiAssets.ensurePrices(this::refreshNow);
                    refreshTimer.start();
                } else refreshTimer.stop();
            }
        });
        refreshNow();
    }

    /** Rebuilds every box from the tracker's current tallies. */
    private void refreshNow() {
        Map<String, Integer> kills = LootTracker.allKillCounts();
        Map<String, Map<String, Long>> drops = LootTracker.allDropCounts();

        Set<String> npcSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        npcSet.addAll(kills.keySet());
        npcSet.addAll(drops.keySet());
        List<String> npcs = new ArrayList<>(npcSet);

        // header summary: kills, value, gp/hr (values only when prices are on + loaded)
        long totalValue = 0;
        int totalKills = 0;
        boolean anyPrice = false;
        for (String npc : npcs) {
            totalKills += kills.getOrDefault(npc, 0);
            for (Map.Entry<String, Long> it : drops.getOrDefault(npc, Collections.emptyMap()).entrySet()) {
                long p = WikiAssets.price(it.getKey());
                if (p > 0) { anyPrice = true; totalValue += p * it.getValue(); }
            }
        }
        double hours = Math.max(1 / 3600.0,
                (System.currentTimeMillis() - LootTracker.sessionStartMs()) / 3600_000.0);
        String who = LootTracker.getCharacter();
        summary.setText("session: " + totalKills + (totalKills == 1 ? " kill" : " kills")
                + (anyPrice ? "  \u00b7  " + gp(totalValue) + " gp  \u00b7  "
                        + gp(Math.round(totalValue / hours)) + " gp/hr" : "")
                + (who.isEmpty() ? "" : "  \u00b7  " + who));

        // sort
        String mode = String.valueOf(sort.getSelectedItem());
        Comparator<String> cmp;
        switch (mode) {
            case "Most kills":
                cmp = Comparator.comparingInt((String n) -> kills.getOrDefault(n, 0)).reversed();
                break;
            case "Recent":
                cmp = Comparator.comparingLong(LootTracker::lastSeen);
                break;
            case "A-Z":
                cmp = String.CASE_INSENSITIVE_ORDER;
                break;
            default:   // Most valuable
                cmp = Comparator.comparingLong((String n) -> {
                    long v = 0;
                    for (Map.Entry<String, Long> it
                            : drops.getOrDefault(n, Collections.emptyMap()).entrySet())
                        v += WikiAssets.price(it.getKey()) * it.getValue();
                    return v;
                }).reversed();
        }
        npcs.sort(cmp);

        boxes.removeAll();
        if (npcs.isEmpty()) {
            boxes.add(empty);
        } else {
            for (String npc : npcs) {
                boxes.add(buildNpcBox(npc, kills.getOrDefault(npc, 0),
                        drops.getOrDefault(npc, Collections.emptyMap())));
                boxes.add(Box.createVerticalStrut(10));
            }
        }
        boxes.revalidate();
        boxes.repaint();
    }

    /** One RuneLite-style box: NPC header + wrapped grid of item cells. */
    private JPanel buildNpcBox(String npc, int killsNow, Map<String, Long> items) {
        JPanel box = new JPanel(new BorderLayout(0, 6));
        box.setBackground(Theme.SURFACE_1);
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                new EmptyBorder(8, 10, 8, 10)));
        box.setAlignmentX(LEFT_ALIGNMENT);

        // header: icon | name + kill counts | value
        JLabel icon = new JLabel(WikiAssets.icon(WikiAssets.Kind.NPC, npc, ICON_NPC, this::repaintBoxes));
        JLabel name = new JLabel(npc);
        name.setFont(new Font("Segoe UI", Font.BOLD, 14));
        name.setForeground(Theme.ACCENT);
        int lifetime = LootTracker.lifetimeKillCount(npc);
        JLabel countLbl = new JLabel("x " + killsNow
                + (lifetime > killsNow ? "   (lifetime " + lifetime + ")" : ""));
        countLbl.setFont(new Font("Consolas", Font.PLAIN, 12));
        countLbl.setForeground(Theme.TEXT_DIM);
        countLbl.setToolTipText("Kills this session"
                + (lifetime > 0 ? " / all-time on this character" : ""));

        JPanel mid = new JPanel(new GridLayout(2, 1));
        mid.setOpaque(false);
        mid.add(name);
        mid.add(countLbl);

        long value = 0;
        for (Map.Entry<String, Long> it : items.entrySet())
            value += WikiAssets.price(it.getKey()) * it.getValue();
        JLabel valueLbl = new JLabel(value > 0 ? gp(value) + " gp" : " ");
        valueLbl.setFont(new Font("Consolas", Font.BOLD, 13));
        valueLbl.setForeground(valueColor(value));

        JPanel head = new JPanel(new BorderLayout(10, 0));
        head.setOpaque(false);
        head.add(icon, BorderLayout.WEST);
        head.add(mid, BorderLayout.CENTER);
        head.add(valueLbl, BorderLayout.EAST);

        // item grid: wrap layout of fixed cells, most valuable first
        JPanel grid = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        grid.setOpaque(false);
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(items.entrySet());
        sorted.sort((a, b) -> Long.compare(
                WikiAssets.price(b.getKey()) * b.getValue(),
                WikiAssets.price(a.getKey()) * a.getValue()));
        for (Map.Entry<String, Long> it : sorted)
            grid.add(buildItemCell(it.getKey(), it.getValue(), killsNow));

        box.add(head, BorderLayout.NORTH);
        if (!items.isEmpty()) box.add(grid, BorderLayout.CENTER);

        // right-click: per-NPC session reset
        JPopupMenu menu = new JPopupMenu();
        JMenuItem miReset = new JMenuItem("Reset \"" + npc + "\" (session)");
        miReset.addActionListener(e -> {
            LootTracker.resetSessionNpc(npc);
            refreshNow();
        });
        menu.add(miReset);
        box.setComponentPopupMenu(menu);

        // keep the box from stretching to fill the whole viewport height
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, box.getPreferredSize().height));
        return box;
    }

    /** A 34px item icon with the RuneLite-style quantity overlay and a detail tooltip. */
    private JComponent buildItemCell(String item, long qty, int kills) {
        Icon base = WikiAssets.icon(WikiAssets.Kind.ITEM, item, ICON_ITEM, this::repaintBoxes);
        long price = WikiAssets.price(item);
        long stackValue = price * qty;

        JLabel cell = new JLabel(base) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (qty <= 1) return;
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                String q = shortQty(qty);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
                FontMetrics fm = g2.getFontMetrics();
                int tx = getWidth() - fm.stringWidth(q) - 2, ty = getHeight() - 3;
                g2.setColor(Color.BLACK);                    // outline for readability
                g2.drawString(q, tx + 1, ty + 1);
                g2.setColor(qtyColor(qty));
                g2.drawString(q, tx, ty);
                g2.dispose();
            }
        };
        cell.setPreferredSize(new Dimension(ICON_ITEM + 4, ICON_ITEM + 4));
        cell.setBorder(BorderFactory.createLineBorder(new Color(0x32, 0x32, 0x32)));
        cell.setOpaque(true);
        cell.setBackground(new Color(0x20, 0x20, 0x20));
        double perKill = kills > 0 ? (double) qty / kills : 0;
        cell.setToolTipText("<html><b>" + item + "</b>  x" + qty
                + (perKill > 0 ? String.format("<br>%.2f per kill", perKill) : "")
                + (price > 0 ? "<br>" + gp(price) + " gp each  \u00b7  " + gp(stackValue) + " gp total" : "")
                + "</html>");
        return cell;
    }

    private void repaintBoxes() { boxes.repaint(); }

    // ── formatting helpers (RuneLite conventions) ─────────────────────────────

    /** 12345 -> "12,345"; 1.2m style shortening for the summary/value labels. */
    private static String gp(long v) {
        if (v >= 10_000_000) return String.format("%.1fm", v / 1_000_000.0);
        if (v >= 100_000) return String.format("%.0fk", v / 1_000.0);
        return String.format("%,d", v);
    }

    /** Stack label like the game: 100000 -> "100K", 10000000 -> "10M". */
    private static String shortQty(long q) {
        if (q >= 10_000_000) return (q / 1_000_000) + "M";
        if (q >= 100_000) return (q / 1_000) + "K";
        return String.valueOf(q);
    }

    /** Classic stack colours: yellow, then white at 100K, green at 10M. */
    private static Color qtyColor(long q) {
        if (q >= 10_000_000) return new Color(0x00, 0xFF, 0x80);
        if (q >= 100_000) return Color.WHITE;
        return new Color(0xFF, 0xFF, 0x00);
    }

    private static Color valueColor(long v) {
        if (v >= 10_000_000) return new Color(0x00, 0xE6, 0x76);
        if (v >= 100_000) return Color.WHITE;
        return Theme.ACCENT;
    }
}
