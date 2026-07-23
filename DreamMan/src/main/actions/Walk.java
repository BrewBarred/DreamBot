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
    /** v1.31: explicit "skip when already close" toggle - unticked disables the field. */
    private JCheckBox chkArrive;
    /** v1.89: how to move - leave it alone, force walking, auto-run, or run flat out. */
    private JComboBox<main.tools.RunControl.Mode> cmbMove;
    private transient long lastWalkIssueAt;

    // Existing empty constructor for initial UI setup
    public Walk() {
        super();
        paramTarget = new JParamTextField(DEFAULT_TARGET);
        paramArrive = new JParamTextField("3");
        chkArrive = new JCheckBox("Skip if within (tiles):", true);   // v1.87: fits the 300px column
        chkArrive.setOpaque(false);
        chkArrive.setToolTipText("v1.31: when ticked, the walk is skipped entirely if you're"
                + " already this close to the destination. Untick to always walk.");
        chkArrive.addActionListener(e -> paramArrive.setEnabled(chkArrive.isSelected()));

        // v1.89: the movement mode. AUTO is first and default, so every task saved before this
        // patch keeps behaving exactly as it did.
        cmbMove = new JComboBox<>(main.tools.RunControl.Mode.values());
        cmbMove.setSelectedItem(main.tools.RunControl.Mode.AUTO);
        cmbMove.setToolTipText("<html><b>Auto</b> \u2014 don't touch run; the client decides.<br>"
                + "<b>Force walk</b> \u2014 run is switched off and kept off.<br>"
                + "<b>Auto-run</b> \u2014 run switches on once you have "
                + main.tools.RunControl.AUTO_RUN_ON_AT + "% energy, then it's left alone as it "
                + "drains.<br><b>Force run</b> \u2014 run every step that's possible: re-enabled "
                + "the moment there's any energy at all,<br>and stamina / energy potions in your "
                + "inventory are drunk to keep it going (it will spend them).</html>");
        cmbMove.setRenderer(new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(
                    JList<?> l, Object v, int i, boolean sel, boolean foc) {
                java.awt.Component c = super.getListCellRendererComponent(l, v, i, sel, foc);
                if (v instanceof main.tools.RunControl.Mode)
                    setText(((main.tools.RunControl.Mode) v).label);
                return c;
            }
        });
    }

    /** The chosen movement mode (AUTO when the control hasn't been built yet). */
    private main.tools.RunControl.Mode moveMode() {
        Object v = cmbMove == null ? null : cmbMove.getSelectedItem();
        return v instanceof main.tools.RunControl.Mode
                ? (main.tools.RunControl.Mode) v : main.tools.RunControl.Mode.AUTO;
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
        this.chkArrive.setSelected(w.chkArrive.isSelected());
        this.paramArrive.setEnabled(this.chkArrive.isSelected());
        this.cmbMove.setSelectedItem(w.moveMode());   // v1.89
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
        // v1.31: checkbox off = never skip; the walk always issues (arrive gate 0)
        int arrive = chkArrive != null && !chkArrive.isSelected()
                ? 0 : ActionUtil.parseInt(paramArrive.getParam(), 3);
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

        // v1.31: the arrive gate is now an explicit checkbox (clarity ask) - the field greys
        // out when it's off, and off means "always walk, never skip".
        // v1.87: checkbox and field share ONE row. The old vertical stack gave the checkbox
        // the column's full width and let a narrow client CLIP its label to "Skip if already"
        // (the Walk screenshot bug); a BorderLayout row keeps the shortened label whole and
        // hands the field whatever width is left.
        JPanel arrive = new JPanel(new java.awt.BorderLayout(6, 0));
        arrive.setOpaque(false);
        paramArrive.setEnabled(chkArrive.isSelected());
        arrive.add(chkArrive, java.awt.BorderLayout.WEST);
        arrive.add(paramArrive, java.awt.BorderLayout.CENTER);
        arrive.setMaximumSize(new java.awt.Dimension(280, 30));
        JPanel arriveWrap = new JPanel();
        arriveWrap.setLayout(new BoxLayout(arriveWrap, BoxLayout.Y_AXIS));
        arriveWrap.setOpaque(false);
        arrive.setAlignmentX(0f);
        arriveWrap.add(arrive);
        arriveWrap.add(Box.createVerticalStrut(10));

        // v1.89: the movement row, under the arrive gate - both are "how this walk behaves"
        JPanel moveRow = new JPanel(new java.awt.BorderLayout(6, 0));
        moveRow.setOpaque(false);
        JLabel moveLbl = new JLabel("Movement:");
        moveLbl.setForeground(main.menu.Theme.TEXT_DIM);
        moveLbl.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        moveRow.add(moveLbl, java.awt.BorderLayout.WEST);
        moveRow.add(cmbMove, java.awt.BorderLayout.CENTER);
        moveRow.setMaximumSize(new java.awt.Dimension(280, 30));
        moveRow.setAlignmentX(0f);
        JPanel moveWrap = new JPanel();
        moveWrap.setLayout(new BoxLayout(moveWrap, BoxLayout.Y_AXIS));
        moveWrap.setOpaque(false);
        moveWrap.add(moveRow);
        moveWrap.add(Box.createVerticalStrut(10));

        return main.actions.ActionUtil.stack(target, arriveWrap, moveWrap);
    }

    @Override
    public boolean execute() {
        // already there? don't click again
        if (isComplete())
            return true;

        // v1.89: put movement in the state this step asked for BEFORE walking, so the very
        // first step of the trip already runs (or already doesn't). RunControl rate-limits
        // itself, so calling it every loop costs nothing and never spams toggles.
        main.tools.RunControl.apply(moveMode());

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
                // v1.31: "off" = the checkbox is unticked (always walk)
                "Arrive", chkArrive.isSelected() ? paramArrive.getParam() : "off",
                "Move", moveMode().name()   // v1.89
        );
    }

    @Override
    public void deserialize(Map<String, String> data) {
        String target = data.get("Target");

        if (target != null) {
            paramTarget.setParam(target);   // restore UI field
        }
        if (data.get("Arrive") != null) {
            String a = data.get("Arrive").trim();
            if (a.equalsIgnoreCase("off") || a.isEmpty()) {          // v1.31: checkbox off
                chkArrive.setSelected(false);
            } else {
                chkArrive.setSelected(true);
                paramArrive.setParam(a);
            }
            paramArrive.setEnabled(chkArrive.isSelected());
        }
        // v1.89: absent on tasks saved before this patch, which Mode.from() reads as AUTO -
        // so an old profile keeps its exact old behaviour.
        cmbMove.setSelectedItem(main.tools.RunControl.Mode.from(data.get("Move")));
    }
}