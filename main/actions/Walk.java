package main.actions;

import main.menu.MenuBuilder;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.item.GroundItems;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.wrappers.interactive.Player;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashSet;
import java.util.Set;

import static main.menu.MenuBuilder.*;

public class Walk extends Action {
    private static final String DEFAULT_TARGET = "3217, 3238, 0"; // Lumbridge magic tutor
    private final JTextField targetField = new JTextField();
    private Runnable variation;

    // Existing empty constructor for initial UI setup
    public Walk() {
        super();
    }

    // New constructor for the actual functional action
    public Walk(String target) {
        super(target);
    }

    private void load(String target) {
        switch (TargetFinder.classify(target)) {
            case COORDINATE:
                Logger.log(Logger.LogType.DEBUG, "Scanning for input coordinates...");
                Tile targetTile = parseStringIntoTile(target);
                if (targetTile != null) {
                    variation = () -> Walking.walk(targetTile);
                    Logger.log(Logger.LogType.DEBUG, "Variation: " + variation.getClass().getSimpleName());
                    break;
                }
                Logger.log(Logger.LogType.DEBUG, "No input coordinates found!");

            case PLAYER:
                variation = () -> {
                    var player = Players.closest(target);
                    if (player != null)
                        Walking.walk(player.getTile());
                };
                break;

            case NPC:
                variation = () -> {
                    var npc = NPCs.closest(target);
                    if (npc != null)
                        Walking.walk(npc.getTile());
                };
                break;

            case GAME_OBJECT:
                variation = () -> {
                    var obj = GameObjects.closest(target);
                    if (obj != null) Walking.walk(obj.getTile());
                };

                break;

            case GROUND_ITEM:
                variation = () -> {
                    var item = GroundItems.closest(target);
                    if (item != null) Walking.walk(item.getTile());
                };

                break;

            default:
                Logger.log(Logger.LogType.ERROR, "[WALK] unable to find target: " + target);
                variation = () -> {};
                break;
        }

    }

    @Override
    public JPanel getParameterControls() {
        ///  Define target parameter values
        String subtitle = "Target:";
        String description = "Target input is dynamically converted into a tile, custom " +
                "data-type or nearby object, player or ground/inventory item.";
        String example = "  e.g. \"X, Y\" or \"X, Y, Z\" -> Tile\n" +
                        "        \"Oak Tree\" -> GameObject\n" +
                        "        \"Zezima\" -> Player, etc...";

        return createParameterPanel(subtitle, description, targetField, example);
        ///  Update user inputs to match current parameters
        //targetField.setText(target);
    }

    @Override
    public Action buildFromControls() {
        target = targetField.getText();
        return new Walk(target);
    }

    @Override
    public boolean execute() {
        // Call load every time to refresh the 'variation' lambda logic
        load(target);

        if (variation != null)
            variation.run();

        return true;
    }

    @Override public String getType() { return "Walk"; }
    @Override public String getTarget() { return targetField.getText(); }
    @Override public Action copy() { return new Walk(targetField.getText()); }

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

        return targets;
    }
}