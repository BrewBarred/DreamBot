package main.actions;

import main.components.JParamTextField;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * Equips an item from the inventory (v1.31) - tries Wield, then Wear, then Equip (the verb
 * varies by item). Completes when the item shows up in your worn equipment.
 */
public class Wield extends Action {

    public Wield() {
        super();
        paramTarget = new JParamTextField("Bronze pickaxe");
        maxAttempts = 8;
    }

    public Wield(Wield o) {
        this();
        paramTarget.setParam(o.paramTarget.getParam());
    }

    @Override
    public boolean execute() {
        String item = paramTarget.getParam() == null ? "" : paramTarget.getParam().trim();
        if (item.isEmpty()) return true;
        try {
            if (org.dreambot.api.methods.container.impl.equipment.Equipment.contains(item))
                return true;
            if (!org.dreambot.api.methods.container.impl.Inventory.contains(item)) {
                noteAttempt();   // not carrying it - fail attempts so the task can react/bank
                return false;
            }
            noteAttempt();
            for (String verb : new String[]{"Wield", "Wear", "Equip"})
                if (org.dreambot.api.methods.container.impl.Inventory.interact(item, verb))
                    break;
        } catch (Throwable ignored) { noteAttempt(); }
        return false;
    }

    @Override public String getParamTarget() { return paramTarget.getParam(); }
    @Override public Action copy() { return new Wield(this); }

    @Override
    public JPanel createParamPanel() {
        return ActionUtil.stack(createParameterPanel("Item:",
                "The inventory item to equip. Tries Wield / Wear / Equip; completes once it's "
                        + "worn.",
                paramTarget, "  e.g. \"Bronze pickaxe\", \"Amulet of strength\""));
    }

    @Override
    public Map<String, String> serialize() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Item", paramTarget.getParam());
        return m;
    }

    @Override
    public void deserialize(Map<String, String> data) {
        if (data == null) return;
        if (data.get("Item") != null) paramTarget.setParam(data.get("Item"));
    }
}
