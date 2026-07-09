package main.actions;

import main.components.JParamTextField;
import org.dreambot.api.wrappers.items.GroundItem;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * Picks up the nearest ground item matching a name, within a radius. If nothing matching is on
 * the ground it completes immediately (looting is opportunistic - it won't stall the queue
 * waiting for a drop; pair it with a Wait if you need to wait for loot to appear).
 */
public class Loot extends Action {

    private JParamTextField paramRadius;

    public Loot() {
        super();
        paramTarget = new JParamTextField("Bones");
        paramRadius = new JParamTextField("8");
    }

    public Loot(Loot o) {
        this();
        paramTarget.setParam(o.paramTarget.getParam());
        paramRadius.setParam(o.paramRadius.getParam());
    }

    @Override
    public boolean execute() {
        int radius = ActionUtil.parseInt(paramRadius.getParam(), 8);
        GroundItem item = ActionUtil.nearestGroundItem(paramTarget.getParam(), radius);

        // nothing to loot -> done (don't block the queue)
        if (item == null)
            return true;

        return item.interact("Take");
    }

    @Override
    public JPanel createParamPanel() {
        JPanel target = createParameterPanel("Item:",
                "The ground item to pick up (nearest match).",
                paramTarget, "  e.g. \"Bones\", \"Coins\", \"Big bones\"");

        JPanel radius = createParameterPanel("Radius:",
                "Only pick up items within this many tiles.",
                paramRadius, "  e.g. \"8\"");

        return ActionUtil.stack(target, radius);
    }

    @Override
    public String getParamTarget() {
        return paramTarget.getParam();
    }

    @Override
    public String toBuildString() {
        return "Loot → " + paramTarget.getParam();
    }

    @Override
    public Action copy() {
        return new Loot(this);
    }

    @Override
    public Map<String, String> serialize() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Target", paramTarget.getParam());
        m.put("Radius", paramRadius.getParam());
        return m;
    }

    @Override
    public void deserialize(Map<String, String> data) {
        if (data == null) return;
        if (data.get("Target") != null) paramTarget.setParam(data.get("Target"));
        if (data.get("Radius") != null) paramRadius.setParam(data.get("Radius"));
    }
}
