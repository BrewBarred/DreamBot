package main.actions;

import main.components.JParamTextField;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.wrappers.items.Item;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * Rule-based inventory processing (Patch B.4) - the "check inventory and act" action. It reads a
 * small rule script and applies one matching operation per idle poll, so it looks human and
 * composes cleanly as a watcher response (e.g. attach "if inventory full → InventoryManager" to
 * a Loot action to make room instead of banking).
 *
 * <p><b>Rule format</b> - semicolon-separated {@code verb:items}, items comma-separated, exact
 * (case-insensitive) names:
 * <pre>
 *   drop:Bones,Ashes ; bury:Big bones ; eat:Shrimps,Trout ; drink:Prayer potion(4) ; cook:Raw shrimps
 * </pre>
 * Verbs: <b>drop</b>, <b>bury</b>, <b>eat</b>, <b>drink</b>, <b>cook</b> (cook uses the item's
 * "Cook"/"Use" verb - pair with a range/fire nearby). Unknown verbs are skipped safely. The step
 * completes when no rule has anything left to act on, so it drains the inventory in passes.
 */
public class InventoryManager extends Action {

    private transient long lastActAt;

    public InventoryManager() {
        super();
        paramTarget = new JParamTextField("drop:Bones; eat:Shrimps");
    }

    public InventoryManager(InventoryManager o) {
        this();
        paramTarget.setParam(o.paramTarget.getParam());
        copyTriggersFrom(o);
    }

    /** One rule: a verb and its exact item names. */
    private static final class Rule {
        final String verb; final String[] items;
        Rule(String verb, String[] items) { this.verb = verb; this.items = items; }
    }

    private java.util.List<Rule> parseRules() {
        java.util.List<Rule> rules = new java.util.ArrayList<>();
        String raw = paramTarget.getParam();
        if (raw == null) return rules;
        for (String clause : raw.split(";")) {
            String[] vp = clause.split(":", 2);
            if (vp.length != 2) continue;
            String verb = vp[0].trim().toLowerCase();
            String[] items = ActionUtil.names(vp[1]);
            if (verb.isEmpty() || items.length == 0) continue;
            rules.add(new Rule(verb, items));
        }
        return rules;
    }

    /** Exact (case-insensitive) inventory lookup - never fuzzy, so pricey items stay safe. */
    private Item exact(String name) {
        try {
            for (Item i : Inventory.all())
                if (i != null && i.getName() != null && i.getName().equalsIgnoreCase(name))
                    return i;
        } catch (Throwable ignored) {}
        return null;
    }

    /** Runs one item op for a verb; returns true if it acted. */
    private boolean applyOnce(String verb, Item item) {
        try {
            switch (verb) {
                case "drop":  return item.interact("Drop");
                case "bury":  return item.interact("Bury");
                case "eat":   return item.interact("Eat");
                case "drink": return item.interact("Drink");
                case "cook":  return item.interact("Cook") || item.interact("Use");
                default:      return false;   // unknown verb - skip safely
            }
        } catch (Throwable t) { return false; }
    }

    @Override
    public boolean execute() {
        java.util.List<Rule> rules = parseRules();
        if (rules.isEmpty()) return true;

        // find the first rule with a present item
        for (Rule r : rules) {
            for (String name : r.items) {
                Item it = exact(name);
                if (it == null) continue;

                long now = System.currentTimeMillis();
                // Patch B.5: as a check response (emergency) the idle gate is skipped - you CAN
                // eat while running; that's exactly what an "HP low" check needs.
                if ((isEmergency() || ActionUtil.isIdle()) && now - lastActAt > 700) {
                    lastActAt = now;
                    if (!applyOnce(r.verb, it)) noteAttempt();
                }
                return false;   // acted (or waiting to) - more to do, not complete yet
            }
        }
        resetAttempts();
        return true;            // nothing matched any rule -> inventory is in the desired state
    }

    @Override
    public JPanel createParamPanel() {
        return createParameterPanel("Rules:",
                "Semicolon-separated verb:items. Verbs: drop, bury, eat, drink, cook. Names are"
                        + " exact, so \"Bones\" never touches \"Dragon bones\". Great as a watcher"
                        + " response for \"inventory full\".",
                paramTarget, "  e.g. \"drop:Bones,Ashes; bury:Big bones; eat:Trout\"");
    }

    @Override
    public String getParamTarget() { return paramTarget.getParam(); }

    @Override
    public String toBuildString() { return "Manage inv \u2192 " + paramTarget.getParam(); }

    @Override public String conflictGroup() { return "inventory"; }

    public Action copy() { return new InventoryManager(this); }

    @Override
    public Map<String, String> serialize() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Rules", paramTarget.getParam());
        return m;
    }

    @Override
    public void deserialize(Map<String, String> data) {
        if (data == null) return;
        if (data.get("Rules") != null) paramTarget.setParam(data.get("Rules"));
    }
}
