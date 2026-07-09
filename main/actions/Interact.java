package main.actions;

import main.components.JParamTextField;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.NPC;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * Interacts with the nearest NPC or game object matching a name, using a chosen action verb
 * (Attack, Chop down, Mine, Talk-to, Open, …), within a radius.
 *
 * <p><b>Stopping condition (the important part).</b> Each action decides for itself when it's
 * "done" — the queue only advances when {@link #execute()} returns true. The "Wait until" field
 * controls that:
 * <ul>
 *   <li><b>auto</b> (default): combat verbs (attack/fight/kill) wait until the target is gone;
 *       everything else interacts once.</li>
 *   <li><b>once</b>: send the interaction and finish immediately.</li>
 *   <li><b>gone</b>: keep going until the target is dead / despawned / out of range.</li>
 *   <li><b>inv-full</b>: keep interacting (e.g. chopping, mining) until the inventory is full.</li>
 * </ul>
 * In the waiting modes it only re-issues the interaction while the player is idle, so it won't
 * spam-click mid-action.
 */
public class Interact extends Action {

    private JParamTextField paramAction;
    private JParamTextField paramRadius;
    private JParamTextField paramUntil;

    public Interact() {
        super();
        paramTarget = new JParamTextField("Cow");
        paramAction = new JParamTextField("Attack");
        paramRadius = new JParamTextField("12");
        paramUntil  = new JParamTextField("auto");
    }

    public Interact(Interact o) {
        this();
        paramTarget.setParam(o.paramTarget.getParam());
        paramAction.setParam(o.paramAction.getParam());
        paramRadius.setParam(o.paramRadius.getParam());
        paramUntil.setParam(o.paramUntil.getParam());
    }

    private String resolveMode() {
        String m = paramUntil.getParam() == null ? "auto" : paramUntil.getParam().trim().toLowerCase();
        if (m.equals("auto")) {
            String v = paramAction.getParam() == null ? "" : paramAction.getParam().toLowerCase();
            return (v.contains("attack") || v.contains("fight") || v.contains("kill")) ? "gone" : "once";
        }
        return m;
    }

    @Override
    public boolean execute() {
        String name = paramTarget.getParam();
        String verb = paramAction.getParam();
        int radius = ActionUtil.parseInt(paramRadius.getParam(), 12);
        String mode = resolveMode();

        if (mode.equals("inv-full") && Inventory.isFull())
            return true;

        NPC npc = ActionUtil.nearestNpc(name, radius);
        GameObject obj = (npc == null) ? ActionUtil.nearestObject(name, radius) : null;
        boolean present = (npc != null || obj != null);

        switch (mode) {
            case "once":
                if (npc != null) return npc.interact(verb);
                if (obj != null) return obj.interact(verb);
                return false; // not in range yet - keep trying

            case "gone":
                // done once the target is no longer around (dead / despawned / out of range)
                if (!present) return true;
                if (ActionUtil.isIdle()) {
                    if (npc != null) npc.interact(verb);
                    else obj.interact(verb);
                }
                return false;

            case "inv-full":
                if (!present) return true; // nothing left to gather
                if (ActionUtil.isIdle()) {
                    if (npc != null) npc.interact(verb);
                    else obj.interact(verb);
                }
                return false;

            default: // treat unknown as "once"
                if (npc != null) return npc.interact(verb);
                if (obj != null) return obj.interact(verb);
                return false;
        }
    }

    @Override
    public JPanel createParamPanel() {
        JPanel target = createParameterPanel("Target:",
                "The NPC or object to interact with (nearest match wins).",
                paramTarget, "  e.g. \"Cow\", \"Oak tree\", \"Bank booth\"");

        JPanel action = createParameterPanel("Action:",
                "The right-click option / verb to use on it.",
                paramAction, "  e.g. \"Attack\", \"Chop down\", \"Mine\", \"Talk-to\", \"Open\"");

        JPanel radius = createParameterPanel("Radius:",
                "Only match targets within this many tiles.",
                paramRadius, "  e.g. \"12\"");

        JPanel until = createParameterPanel("Wait until:",
                "When this step is finished: auto / once / gone / inv-full.",
                paramUntil, "  auto = smart · gone = target dead/out of range · inv-full = bag full");

        return ActionUtil.stack(target, action, radius, until);
    }

    @Override
    public String getParamTarget() {
        return paramTarget.getParam();
    }

    @Override
    public String toBuildString() {
        return "Interact → " + paramAction.getParam() + " " + paramTarget.getParam()
                + " (until " + resolveMode() + ")";
    }

    @Override
    public Action copy() {
        return new Interact(this);
    }

    @Override
    public Map<String, String> serialize() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Target", paramTarget.getParam());
        m.put("Action", paramAction.getParam());
        m.put("Radius", paramRadius.getParam());
        m.put("Until", paramUntil.getParam());
        return m;
    }

    @Override
    public void deserialize(Map<String, String> data) {
        if (data == null) return;
        if (data.get("Target") != null) paramTarget.setParam(data.get("Target"));
        if (data.get("Action") != null) paramAction.setParam(data.get("Action"));
        if (data.get("Radius") != null) paramRadius.setParam(data.get("Radius"));
        if (data.get("Until")  != null) paramUntil.setParam(data.get("Until"));
    }
}
