//package main.managers;
//
//import org.dreambot.api.methods.world.World;
//import org.dreambot.api.methods.world.Worlds;
//import main.BotMan;
//import org.dreambot.api.methods.worldhopper.WorldHopper;
//
//import javax.swing.*;
//import javax.swing.border.TitledBorder;
//import java.awt.*;
//import java.awt.event.MouseAdapter;
//import java.awt.event.MouseEvent;
//import java.util.List;
//import java.util.*;
//
//public class WorldMan {
//    private final BotMan bot;
//    private final Set<Integer> whitelist = new HashSet<>();
//    private boolean filterP2P = false;
//    private boolean filterF2P = false;
//    private boolean filterHighRisk = true;
//
//    public WorldMan(BotMan bot) {
//        this.bot = bot;
//    }
//
//    /**
//     * Hops to a random world that matches the current filters and whitelist.
//     * @param membersOnly Set to true for P2P, false for F2P.
//     */
//    public void hopRandom(boolean membersOnly) {
//        // Use Worlds.all() static method. Added whitelist check and policy check.
//        List<World> available = Worlds.all(w ->
//                w.isMembers() == membersOnly &&
//                        allowedByPolicy(w) &&
//                        (whitelist.isEmpty() || whitelist.contains(w.getWorld()))
//        );
//
//        if (available.isEmpty()) {
//            bot.setBotStatus("No worlds found matching criteria!");
//            return;
//        }
//
//        // Pick a random world from the filtered list
//        World target = available.get(new Random().nextInt(available.size()));
//        bot.setBotStatus("Attempting to hop world: " + target.getWorld());
//
//        // Update status and initiate the hop using WorldHopper.hop()
//        bot.setPlayerStatus("Hopping to world: " + target.getWorld());
//        WorldHopper.hopWorld(target);
//    }
//
//    /**
//     * Policy filter to ensure we don't hop into dangerous worlds.
//     */
//    private boolean allowedByPolicy(World w) {
//        if (filterHighRisk && w.isHighRisk()) return false;
//        return !w.isPVP() && !w.isTournamentWorld();
//    }
//
//    /**
//     * Build the world selector GUI component.
//     */
//    public JComponent buildWorldSelector() {
//        JPanel container = new JPanel(new BorderLayout(5, 5));
//        container.setBackground(Color.WHITE);
//
//        DefaultListModel<World> model = new DefaultListModel<>();
//
//        // Corrected: Use Worlds.all() static call
//        Worlds.all().forEach(model::addElement);
//
//        JList<World> list = new JList<>(model);
//        list.setCellRenderer(new WorldCellRenderer());
//
//        list.addMouseListener(new MouseAdapter() {
//            @Override
//            public void mouseClicked(MouseEvent e) {
//                World w = list.getSelectedValue();
//                if (w != null) {
//                    if (whitelist.contains(w.getWorld())) {
//                        whitelist.remove(w.getWorld());
//                    } else {
//                        whitelist.add(w.getWorld());
//                    }
//                    list.repaint();
//                }
//            }
//        });
//
//        container.add(new JScrollPane(list), BorderLayout.CENTER);
//        return container;
//    }
//
//    private class WorldCellRenderer extends DefaultListCellRenderer {
//        @Override
//        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
//            JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
//            World w = (World) value;
//
//            String tags = (w.isMembers() ? "P2P" : "F2P")
//                    + (w.isPVP() ? " | PvP" : "")
//                    + (w.isHighRisk() ? " | High-risk" : "")
//                    + (w.getPopulation() > 1999 ? " | FULL" : "");
//
//            boolean whitelisted = whitelist.contains(w.getWorld());
//            boolean allowed = allowedByPolicy(w);
//
//            lbl.setText((whitelisted ? "✓ " : "  ")
//                    + w.getWorld()
//                    + "  (" + w.getPopulation() + ")  "
//                    + tags);
//
//            if (!isSelected) {
//                if (!allowed) lbl.setForeground(new Color(160, 60, 60));
//                else if (whitelisted) lbl.setForeground(new Color(40, 130, 60));
//                else lbl.setForeground(new Color(120, 120, 120));
//            }
//
//            return lbl;
//        }
//    }
//
//    private static JPanel section(String title) {
//        JPanel p = new JPanel(new BorderLayout());
//        p.setBorder(BorderFactory.createTitledBorder(
//                BorderFactory.createLineBorder(new Color(210, 210, 210)),
//                title,
//                TitledBorder.LEFT,
//                TitledBorder.TOP,
//                new Font("Segoe UI", Font.PLAIN, 12)
//        ));
//        p.setBackground(Color.WHITE);
//        return p;
//    }
//}