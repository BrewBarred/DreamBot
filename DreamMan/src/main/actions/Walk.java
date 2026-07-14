package main.actions;

import main.components.JParamTextField;
import main.data.Library;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.item.GroundItems;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.walking.impl.Walking;

import javax.swing.*;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static main.menu.MenuHandler.*;

public class Walk extends Action {

    /** Patch B.17: Walk targets a PLACE - the entity list auto-fills "X, Y, Z" for it. */
    @Override public boolean prefersTileTarget() { return true; }

    private final String DEFAULT_TARGET = "3217, 3238, 0"; // Default target: Lumbridge magic tutor
    private Runnable variation;

    private JParamTextField paramArrive;
    private transient long lastWalkIssueAt;

    // Existing empty constructor for initial UI setup
    public Walk() {
        super();
        paramTarget = new JParamTextField(DEFAULT_TARGET);
        paramArrive = new JParamTextField("3");
    }

    // New constructor for the actual functional action
    public Walk(String target) {
        this();
        paramTarget.setParam(target);
    }

    public Walk(Walk w) {
        this(w.getParamTarget());
        this.variation = w.variation;
        this.paramArrive.setParam(w.paramArrive.getParam());
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
        // Patch B.2: adjustable arrive radius. Already within N tiles of the destination (or
        // of the resolved target's tile for named targets)? The step is skipped outright -
        // no pointless walk click, moving or not.
        int arrive = ActionUtil.parseInt(paramArrive.getParam(), 3);
        return Players.getLocal().getTile().distance(tile) <= Math.max(0, arrive);
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

        JPanel target = createParameterPanel(subtitle, description, paramTarget, example);

        JPanel arrive = createParameterPanel("Arrive within (tiles):",
                "Skip this step when the player is already this close to the destination"
                        + " (or the target's approximate tile for named targets).",
                paramArrive, "  e.g. \"3\"");

        return main.actions.ActionUtil.stack(target, arrive);
    }

    @Override
    public boolean execute() {
        // already there? don't click again
        if (isComplete())
            return true;

        // Only (re)issue a walk when the player is idle - previously this fired a fresh
        // Walking.walk() every single loop, spamming clicks while mid-path. Each idle re-issue
        // that still hasn't arrived counts as one attempt (rate-limited to one per 2s), so a
        // destination we genuinely can't reach fails the task instead of walking forever.
        if (!Players.getLocal().isMoving()) {
            long now = System.currentTimeMillis();
            if (now - lastWalkIssueAt > 2000) {
                lastWalkIssueAt = now;
                noteAttempt();
            }
            load(paramTarget.getParam());

            if (variation != null)
                variation.run();
        }

        if (isComplete()) {
            resetAttempts();
            return true;
        }
        return false;
    }

    @Override
    public String getParamTarget() {
        return paramTarget.getParam();
    }

    //TODO there was a bug with this function that caused the menu to stop loading
    @Override public String conflictGroup() { return "movement"; }

    @Override public Action copy() {
        return new Walk(this);
    }

    @Override
    public Map<String, String> serialize() {
        return Map.of(
                "Target", paramTarget.getParam(),
                "Arrive", paramArrive.getParam()
        );
    }

    @Override
    public void deserialize(Map<String, String> data) {
        String target = data.get("Target");

        if (target != null) {
            paramTarget.setParam(target);   // restore UI field
        }
        if (data.get("Arrive") != null)
            paramArrive.setParam(data.get("Arrive"));
    }
}