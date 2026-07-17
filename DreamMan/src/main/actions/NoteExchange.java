package main.actions;

import main.components.JParamTextField;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * Banker note exchange (v1.31), the UIM quality-of-life helper: uses an item stack on the
 * nearest banker and picks the "Exchange" dialogue option - the standard way to swap between
 * noted and un-noted forms at a banker without touching a bank. Available to everyone, but
 * pitched at Ultimate Ironmen (who don't get the Bank action at all).
 */
public class NoteExchange extends Action {

    private transient boolean used;
    private transient long usedAt;

    public NoteExchange() {
        super();
        paramTarget = new JParamTextField("Yew logs");
        maxAttempts = 10;
    }

    public NoteExchange(NoteExchange o) {
        this();
        paramTarget.setParam(o.paramTarget.getParam());
    }

    @Override
    public boolean execute() {
        String item = paramTarget.getParam() == null ? "" : paramTarget.getParam().trim();
        if (item.isEmpty()) return true;
        try {
            // dialogue open? pick the Exchange option, then we're done when it closes
            if (org.dreambot.api.methods.dialogues.Dialogues.inDialogue()) {
                String[] opts = org.dreambot.api.methods.dialogues.Dialogues.getOptions();
                if (opts != null)
                    for (String o : opts)
                        if (o != null && o.toLowerCase().contains("exchange")) {
                            org.dreambot.api.methods.dialogues.Dialogues.chooseOption(o);
                            used = true;
                            usedAt = System.currentTimeMillis();
                            return false;
                        }
                if (org.dreambot.api.methods.dialogues.Dialogues.canContinue())
                    org.dreambot.api.methods.dialogues.Dialogues.continueDialogue();
                return false;
            }
            if (used) {
                if (System.currentTimeMillis() - usedAt > 1200) { used = false; return true; }
                return false;
            }

            org.dreambot.api.wrappers.items.Item inv =
                    org.dreambot.api.methods.container.impl.Inventory.get(item);
            if (inv == null) { noteAttempt(); return false; }
            org.dreambot.api.wrappers.interactive.NPC banker = ActionUtil.nearestNpc("Banker", 12);
            if (banker == null) { noteAttempt(); return false; }
            noteAttempt();
            inv.useOn(banker);
        } catch (Throwable ignored) { noteAttempt(); }
        return false;
    }

    @Override public String getParamTarget() { return paramTarget.getParam(); }
    @Override public Action copy() { return new NoteExchange(this); }
    @Override public String conflictGroup() { return "bank"; }

    @Override
    public JPanel createParamPanel() {
        return ActionUtil.stack(createParameterPanel("Item:",
                "The inventory item (or its noted form) to use on the nearest banker; the "
                        + "Exchange option is picked automatically. Stand near a banker first.",
                paramTarget, "  e.g. \"Yew logs\""));
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
