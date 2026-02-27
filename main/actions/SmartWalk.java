//package main.actions;
//
//import org.dreambot.api.methods.interactive.GameObjects;
//import org.dreambot.api.methods.interactive.NPCs;
//import org.dreambot.api.methods.item.GroundItems;
//import org.dreambot.api.methods.map.Tile;
//import org.dreambot.api.methods.walking.impl.Walking;
//import org.dreambot.api.utilities.Logger;
//import org.dreambot.api.wrappers.interactive.GameObject;
//import org.dreambot.api.wrappers.interactive.NPC;
//import org.dreambot.api.wrappers.items.GroundItem;
//
//import javax.swing.*;
//import java.lang.annotation.Target;
//
//import static main.actions.Walk.parseCoords;
//
//public class SmartWalk extends Action {
//    private String rawTarget;
//
//    public SmartWalk(String target) { this.rawTarget = target; }
//
//    @Override
//    public boolean execute() {
//        switch (TargetFinder.classify(rawTarget)) {
//            case COORDINATE:
//                int[] coords = parseCoords(rawTarget);
//                return Walking.walk(new Tile(coords[0], coords[1], coords[2]));
//            case NPC:
//                NPC npc = NPCs.closest(rawTarget);
//                return npc != null && Walking.walk(npc.getTile());
//            case GAME_OBJECT:
//                GameObject obj = GameObjects.closest(rawTarget);
//                return obj != null && Walking.walk(obj.getTile());
//            case INVENTORY_ITEM:
//                GroundItem item = GroundItems.closest(rawTarget);
//                return item != null && Walking.walk(item.getTile());
//            default:
//                Logger.log("SmartWalk: couldn't resolve target '" + rawTarget + "'");
//                return false;
//        }
//    }
//
//    @Override
//    public String getType() {
//        return "";
//    }
//
//    @Override
//    public String getTarget() {
//        return "";
//    }
//
//    @Override
//    public Action copy() {
//        return null;
//    }
//
//    @Override
//    public JPanel getParameterControls() {
//        return null;
//    }
//
//    @Override
//    public Action buildFromControls() {
//        return null;
//    }
//}