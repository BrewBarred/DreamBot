package main.actions;

import main.components.JParamTextField;
import org.dreambot.api.methods.container.impl.Inventory;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * Drops all matching items from the inventory. Accepts a comma-separated list of names, and
 * repeats until none remain (so it completes cleanly even if the client drops in passes).
 */
public class Drop extends Action {

    public Drop() {
        super();
        paramTarget = new JParamTextField("Logs");
    }

    public Drop(Drop o) {
        this();
        paramTarget.setParam(o.paramTarget.getParam());
    }

    @Override
    public boolean execute() {
        String[] names = ActionUtil.names(paramTarget.getParam());
        if (names.length == 0)
            return true;

        Inventory.dropAll(names);
        // done once none of the named items remain in the inventory
        return !Inventory.contains(names);
    }

    @Override
    public JPanel createParamPanel() {
        JPanel target = createParameterPanel("Items:",
                "Inventory items to drop. Separate multiple with commas.",
                paramTarget, "  e.g. \"Logs\"  or  \"Oak logs, Willow logs\"");

        return ActionUtil.stack(target);
    }

    @Override
    public String getParamTarget() {
        return paramTarget.getParam();
    }

    @Override
    public String toBuildString() {
        return "Drop → " + paramTarget.getParam();
    }

    @Override public String conflictGroup() { return "inventory"; }

    public Action copy() {
        return new Drop(this);
    }

    @Override
    public Map<String, String> serialize() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Target", paramTarget.getParam());
        return m;
    }

    @Override
    public void deserialize(Map<String, String> data) {
        if (data == null) return;
        if (data.get("Target") != null) paramTarget.setParam(data.get("Target"));
    }
}
