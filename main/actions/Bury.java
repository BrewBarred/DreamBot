package main.actions;

import main.components.JParamTextField;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.wrappers.items.Item;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * Buries bones from the inventory by EXACT bone type (Patch B.2). "Bones" buries only items
 * named exactly Bones - never "Big bones", "Dragon bones" or anything else that merely contains
 * the word - so expensive bones can't be buried by accident. Amount is "all" or a number; one
 * bury per idle poll keeps the pacing human. Completes when the count is reached or none of
 * that bone type remain.
 */
public class Bury extends Action {

    private JParamTextField paramAmount;
    private transient int buried;
    private transient long lastBuryAt;

    public Bury() {
        super();
        paramTarget = new JParamTextField("Bones");
        paramAmount = new JParamTextField("all");
    }

    public Bury(Bury o) {
        this();
        paramTarget.setParam(o.paramTarget.getParam());
        paramAmount.setParam(o.paramAmount.getParam());
    }

    private int targetCount() {
        String a = paramAmount.getParam() == null ? "all" : paramAmount.getParam().trim().toLowerCase();
        if (a.isEmpty() || a.equals("all") || a.equals("*")) return Integer.MAX_VALUE;
        return Math.max(0, ActionUtil.parseInt(a, Integer.MAX_VALUE));
    }

    /** Exact-name (case-insensitive) inventory lookup - the whole safety point of this action. */
    private Item exactBone(String name) {
        try {
            Item item = Inventory.get(name);
            if (item != null && item.getName() != null && item.getName().equalsIgnoreCase(name))
                return item;
            // Inventory.get can fuzzy-match on some client versions; verify against all().
            for (Item i : Inventory.all())
                if (i != null && i.getName() != null && i.getName().equalsIgnoreCase(name))
                    return i;
        } catch (Throwable ignored) {}
        return null;
    }

    @Override
    public boolean execute() {
        String bone = paramTarget.getParam();
        int want = targetCount();

        if (buried >= want) {
            buried = 0;
            resetAttempts();
            return true;
        }

        Item item = exactBone(bone);
        if (item == null) {
            // none of that exact bone left: complete (this is the normal end for "all")
            buried = 0;
            resetAttempts();
            return true;
        }

        long now = System.currentTimeMillis();
        if (ActionUtil.isIdle() && now - lastBuryAt > 900) {
            lastBuryAt = now;
            boolean ok;
            try { ok = item.interact("Bury"); } catch (Throwable t) { ok = false; }
            if (ok) buried++;
            else noteAttempt();
        }
        return false;
    }

    @Override
    public JPanel createParamPanel() {
        JPanel bone = createParameterPanel("Bone type (exact):",
                "Only items with EXACTLY this name are buried - \"Bones\" never touches"
                        + " \"Big bones\" or \"Dragon bones\".",
                paramTarget, "  e.g. \"Bones\", \"Big bones\", \"Babydragon bones\"");

        JPanel amount = createParameterPanel("Amount:",
                "How many to bury this step: a number, or \"all\".",
                paramAmount, "  e.g. \"all\" or \"5\"");

        return ActionUtil.stack(bone, amount);
    }

    @Override
    public String getParamTarget() { return paramTarget.getParam(); }

    @Override
    public String toBuildString() {
        return "Bury → " + paramTarget.getParam() + " (" + paramAmount.getParam() + ")";
    }

    @Override
    public Action copy() { return new Bury(this); }

    @Override
    public Map<String, String> serialize() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Target", paramTarget.getParam());
        m.put("Amount", paramAmount.getParam());
        return m;
    }

    @Override
    public void deserialize(Map<String, String> data) {
        if (data == null) return;
        if (data.get("Target") != null) paramTarget.setParam(data.get("Target"));
        if (data.get("Amount") != null) paramAmount.setParam(data.get("Amount"));
    }
}
