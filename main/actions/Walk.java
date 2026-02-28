package main.actions;

import main.data.Library;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.item.GroundItems;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.walking.impl.Walking;

import javax.swing.*;
import java.util.LinkedHashSet;
import java.util.Set;

import static main.menu.MenuHandler.*;

public class Walk extends Action {
    private final String DEFAULT_TARGET = "3217, 3238, 0"; // Lumbridge magic tutor
    private Runnable variation;

    // Existing empty constructor for initial UI setup
    public Walk() {
        super();
    }

    // New constructor for the actual functional action
    public Walk(String target) {
        super(target);
    }

    public Walk(Walk w) {
        this.paramTarget.setParam(w.getParamTarget());
        this.variation = w.variation;
    }

    private void load(String target) {
        variation = () -> {
            Tile targetTile = Library.resolveToTile(target);
            if (targetTile != null)
                Walking.walk(targetTile);
        };
    }

//    private void load(String target) {
//        switch (TargetFinder.classify(target)) {
//            case COORDINATE:
//                Logger.log(Logger.LogType.DEBUG, "Scanning for input coordinates...");
//                Tile targetTile = parseStringIntoTile(target);
//                if (targetTile != null) {
//                    variation = () -> Walking.walk(targetTile);
//                    Logger.log(Logger.LogType.DEBUG, "Variation: " + variation.getClass().getSimpleName());
//                    break;
//                }
//                Logger.log(Logger.LogType.DEBUG, "No input coordinates found!");
//
//            ///  Fall-back to nearby players, npcs, objects, ground items, in that order
//            case PLAYER:
//                variation = () -> {
//                    var player = Players.closest(p -> p != null && p.getName() != null && p.getName().equalsIgnoreCase(target));
//                    if (player != null)
//                        Walking.walk(player.getTile());
//                };
//                break;
//
//            case NPC:
//                variation = () -> {
//                    var npc = NPCs.closest(n -> n != null && n.getName() != null && n.getName().equalsIgnoreCase(target));
//                    if (npc != null)
//                        Walking.walk(npc.getTile());
//                };
//                break;
//
//            case GAME_OBJECT:
//                variation = () -> {
//                    var obj = GameObjects.closest(o -> o != null && o.getName() != null && o.getName().equalsIgnoreCase(target));
//                    if (obj != null) Walking.walk(obj.getTile());
//                };
//                break;
//
//            case GROUND_ITEM:
//                variation = () -> {
//                    var item = GroundItems.closest(i -> i != null && i.getName() != null && i.getName().equalsIgnoreCase(target));
//                    if (item != null) Walking.walk(item.getTile());
//                };
//                break;
//
//            default:
//                Logger.log(Logger.LogType.ERROR, "[WALK] unable to find target: " + target);
//                variation = () -> {};
//                break;
//        }
//    }

    private boolean isComplete() {
        Tile tile = Library.resolveToTile(paramTarget.getParam());
        if (tile == null) return true; // can't find it, give up
        return Players.getLocal().getTile().distance(tile) <= 3 && !Players.getLocal().isMoving();
    }

    public static Set<String> scanTargets() {
        Set<String> targets = new LinkedHashSet<>();

        NPCs.all().forEach(npc -> {
            if (npc.getName() != null)
                targets.add(npc.getName());
        });

        GameObjects.all().forEach(obj -> {
            if (obj.getName() != null)
                targets.add(obj.getName());
        });

        GroundItems.all().forEach(item -> {
            if (item.getName() != null)
                targets.add(item.getName());
        });

        Players.all().forEach(player -> {
            if (player.getName() != null)
                targets.add(player.getName());
        });

        return targets;
    }

    @Override
    public JPanel createParamPanel() {
        ///  Define target parameter values
        String subtitle = "Target:";
        String description = "Target input is dynamically converted into a tile, custom " +
                "data-type or nearby object, player or ground/inventory item.";
        String example = "  e.g. \"X, Y\" or \"X, Y, Z\" -> Tile\n" +
                "        \"Oak Tree\" -> GameObject\n" +
                "        \"Zezima\" -> Player, etc...";

        return createParameterPanel(subtitle, description, paramTarget, example);
    }

    @Override
    public boolean execute() {
        // Call load every time to refresh the 'variation' lambda logic
        load(paramTarget.getParam());

        if (variation != null)
            variation.run();

        return isComplete();
    }

    @Override
    public String getParamTarget() {
        return paramTarget.getText();
    }

    @Override public Action copy() {
        return new Walk(this);
    }
}