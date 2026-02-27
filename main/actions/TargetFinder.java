//package main.actions;
//
//import org.dreambot.api.methods.container.impl.Inventory;
//import org.dreambot.api.methods.interactive.GameObjects;
//import org.dreambot.api.methods.interactive.NPCs;
//import org.dreambot.api.methods.interactive.Players;
//import org.dreambot.api.methods.item.GroundItems;
//
//import java.util.Arrays;
//
//public class TargetFinder {
//
//    public enum TargetType { COORDINATE, PLAYER, GAME_OBJECT, NPC, INVENTORY_ITEM, GROUND_ITEM, UNKNOWN }
//
//    public static TargetType classify(String input) {
//        if (input == null || input.isBlank())
//            return TargetType.UNKNOWN;
//
//        if (Action.parseStringIntoTile(input) != null)
//            return TargetType.COORDINATE;
//
//        if (Players.closest(input) != null)
//            return TargetType.PLAYER;
//
//        // Check for a live NPC match
//        if (NPCs.closest(input) != null)
//            return TargetType.NPC;
//
//        // Check for a live GameObject match
//        if (GameObjects.closest(input) != null)
//            return TargetType.GAME_OBJECT;
//
//        // Check inventory items
//        if (Inventory.contains(input))
//            return TargetType.INVENTORY_ITEM;
//
//        // Check ground items
//        if (GroundItems.all().stream().anyMatch(e -> e.getName().equals(input)))
//            return TargetType.GROUND_ITEM;
//
//        return TargetType.UNKNOWN;
//    }
//}
