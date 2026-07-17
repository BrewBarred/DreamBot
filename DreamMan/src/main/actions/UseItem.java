package main.actions;

import main.components.JParamTextField;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * Inventory item plays (v1.31), one action instead of two:
 * <ul>
 *   <li><b>interact</b> - click the item (optionally a specific verb: Eat, Rub, Open...)</li>
 *   <li><b>on-item</b> - use the item on ANOTHER inventory item (tinderbox on logs)</li>
 *   <li><b>on-target</b> - use the item on a nearby named NPC or object (bones on altar,
 *       item on banker) without needing a separate Interact step</li>
 * </ul>
 */
public class UseItem extends Action {

    private JParamTextField paramMode;    // interact | on-item | on-target
    private JParamTextField paramOther;   // second item / nearby target name
    private JParamTextField paramVerb;    // interact-mode verb ("" = first action)

    private transient boolean acted;

    public UseItem() {
        super();
        paramTarget = new JParamTextField("Logs");
        paramMode = new JParamTextField("interact");
        paramOther = new JParamTextField("");
        paramVerb = new JParamTextField("");
        maxAttempts = 8;
    }

    public UseItem(UseItem o) {
        this();
        paramTarget.setParam(o.paramTarget.getParam());
        paramMode.setParam(o.paramMode.getParam());
        paramOther.setParam(o.paramOther.getParam());
        paramVerb.setParam(o.paramVerb.getParam());
    }

    private String mode() {
        String m = paramMode.getParam() == null ? "interact" : paramMode.getParam().trim().toLowerCase();
        if (m.contains("target") || m.contains("nearby")) return "on-target";
        if (m.contains("item") && m.startsWith("on")) return "on-item";
        return "interact";
    }

    @Override
    public boolean execute() {
        if (acted) { acted = false; return true; }   // settle one poll after acting
        String item = paramTarget.getParam() == null ? "" : paramTarget.getParam().trim();
        if (item.isEmpty()) return true;
        try {
            org.dreambot.api.wrappers.items.Item inv =
                    org.dreambot.api.methods.container.impl.Inventory.get(item);
            if (inv == null) { noteAttempt(); return false; }

            switch (mode()) {
                case "on-item": {
                    String other = paramOther.getParam() == null ? "" : paramOther.getParam().trim();
                    org.dreambot.api.wrappers.items.Item second =
                            org.dreambot.api.methods.container.impl.Inventory.get(other);
                    if (second == null) { noteAttempt(); return false; }
                    noteAttempt();
                    if (inv.useOn(second)) acted = true;
                    return false;
                }
                case "on-target": {
                    String name = paramOther.getParam() == null ? "" : paramOther.getParam().trim();
                    org.dreambot.api.wrappers.interactive.NPC npc = ActionUtil.nearestNpc(name, 15);
                    if (npc != null) {
                        noteAttempt();
                        if (inv.useOn(npc)) acted = true;
                        return false;
                    }
                    org.dreambot.api.wrappers.interactive.GameObject obj =
                            ActionUtil.nearestObject(name, 15);
                    if (obj != null) {
                        noteAttempt();
                        if (inv.useOn(obj)) acted = true;
                        return false;
                    }
                    noteAttempt();
                    return false;
                }
                default: {
                    String verb = paramVerb.getParam() == null ? "" : paramVerb.getParam().trim();
                    noteAttempt();
                    boolean ok = verb.isEmpty() ? inv.interact() : inv.interact(verb);
                    if (ok) acted = true;
                    return false;
                }
            }
        } catch (Throwable ignored) { noteAttempt(); }
        return false;
    }

    @Override public String getParamTarget() { return paramTarget.getParam(); }
    @Override public Action copy() { return new UseItem(this); }

    @Override
    public JPanel createParamPanel() {
        JPanel it = createParameterPanel("Item:",
                "The inventory item to use.", paramTarget, "  e.g. \"Logs\", \"Bones\"");
        JPanel md = createParameterPanel("Mode:",
                "interact = click the item (with the verb below); on-item = use it on another "
                        + "inventory item; on-target = use it on a nearby named NPC/object.",
                paramMode, "  \"interact\", \"on-item\", or \"on-target\"");
        JPanel ot = createParameterPanel("Other / target:",
                "on-item: the second inventory item. on-target: the nearby NPC or object name.",
                paramOther, "  e.g. \"Tinderbox\"  or  \"Banker\"");
        JPanel vb = createParameterPanel("Verb:",
                "interact mode only - a specific right-click action. Blank = the default one.",
                paramVerb, "  e.g. \"Eat\", \"Rub\", \"Open\" (usually blank)");
        return ActionUtil.stack(it, md, ot, vb);
    }

    @Override
    public Map<String, String> serialize() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Item", paramTarget.getParam());
        m.put("Mode", paramMode.getParam());
        m.put("Other", paramOther.getParam());
        m.put("Verb", paramVerb.getParam());
        return m;
    }

    @Override
    public void deserialize(Map<String, String> data) {
        if (data == null) return;
        if (data.get("Item") != null) paramTarget.setParam(data.get("Item"));
        if (data.get("Mode") != null) paramMode.setParam(data.get("Mode"));
        if (data.get("Other") != null) paramOther.setParam(data.get("Other"));
        if (data.get("Verb") != null) paramVerb.setParam(data.get("Verb"));
    }
}
