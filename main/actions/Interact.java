package main.actions;

import main.components.JParamTextField;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.NPC;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * Interacts with the nearest NPC or game object matching a name, using a chosen action verb
 * (e.g. "Attack", "Chop down", "Mine", "Talk-to", "Open"). NPCs are tried first, then objects,
 * so one action covers both. A radius filter keeps it from running off across the map.
 */
public class Interact extends Action {

    private JParamTextField paramAction;
    private JParamTextField paramRadius;

    public Interact() {
        super();
        paramTarget = new JParamTextField("Oak tree");
        paramAction = new JParamTextField("Chop down");
        paramRadius = new JParamTextField("12");
    }

    public Interact(Interact o) {
        this();
        paramTarget.setParam(o.paramTarget.getParam());
        paramAction.setParam(o.paramAction.getParam());
        paramRadius.setParam(o.paramRadius.getParam());
    }

    @Override
    public boolean execute() {
        String name = paramTarget.getParam();
        String verb = paramAction.getParam();
        int radius = ActionUtil.parseInt(paramRadius.getParam(), 12);

        // NPCs first, then objects - both share interact(String)
        NPC npc = ActionUtil.nearestNpc(name, radius);
        if (npc != null)
            return npc.interact(verb);

        GameObject obj = ActionUtil.nearestObject(name, radius);
        if (obj != null)
            return obj.interact(verb);

        // Nothing in range yet - let the loop retry (e.g. waiting for a respawn)
        return false;
    }

    @Override
    public JPanel createParamPanel() {
        JPanel target = createParameterPanel("Target:",
                "The NPC or object to interact with (nearest match wins).",
                paramTarget, "  e.g. \"Oak tree\", \"Goblin\", \"Bank booth\"");

        JPanel action = createParameterPanel("Action:",
                "The right-click option / verb to use on it.",
                paramAction, "  e.g. \"Chop down\", \"Attack\", \"Mine\", \"Talk-to\", \"Open\"");

        JPanel radius = createParameterPanel("Radius:",
                "Only match targets within this many tiles.",
                paramRadius, "  e.g. \"12\"");

        return ActionUtil.stack(target, action, radius);
    }

    @Override
    public String getParamTarget() {
        return paramTarget.getParam();
    }

    @Override
    public String toBuildString() {
        return "Interact → " + paramAction.getParam() + " " + paramTarget.getParam();
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
        return m;
    }

    @Override
    public void deserialize(Map<String, String> data) {
        if (data == null) return;
        if (data.get("Target") != null) paramTarget.setParam(data.get("Target"));
        if (data.get("Action") != null) paramAction.setParam(data.get("Action"));
        if (data.get("Radius") != null) paramRadius.setParam(data.get("Radius"));
    }
}
