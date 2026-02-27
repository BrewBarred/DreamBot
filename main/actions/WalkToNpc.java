//package main.actions;
//
//import org.dreambot.api.methods.interactive.NPCs;
//import org.dreambot.api.methods.interactive.Players;
//import org.dreambot.api.methods.walking.impl.Walking;
//import org.dreambot.api.wrappers.interactive.NPC;
//
//import javax.swing.*;
//import java.util.*;
//import java.util.stream.Collectors;
//
//public class WalkToNpc extends Action {
//
//    private final String target;
//
//    public WalkToNpc(String target) {
//        this.target = target;
//    }
//
//    public static Set<String> scanTargets() {
//        // shows all nearby NPC names in the nearby list
//        return NPCs.all().stream()
//                .map(NPC::getName)
//                .filter(Objects::nonNull)
//                .collect(Collectors.toSet());
//    }
//
//    public static Map<String, JComponent> loadPanel() {
//        Map<String, JComponent> controls = new LinkedHashMap<>();
//        controls.put("NPC Name:", new JTextField(""));
//        return controls;
//    }
//
//    @Override
//    public JPanel getParameterControls() {
//        return null;
//    }
//
//    @Override
//    boolean performAction() {
//        NPC npc = NPCs.closest(target);
//
//        if (npc == null)
//            return false;
//
//        Walking.walk(npc.getTile());
//        return true;
//    }
//
//    @Override
//    public boolean isComplete() {
//        NPC npc = NPCs.closest(target);
//        return npc != null && Players.getLocal().getTile().distance(npc.getTile()) < 3;
//    }
//
//    @Override public Action copy() { return new WalkToNpc(target); }
//    @Override public String getType() { return "Walk to NPC"; }
//    @Override public String getTarget() { return target; }
//    @Override public String toString() { return "Walk to NPC → " + target; }
//}