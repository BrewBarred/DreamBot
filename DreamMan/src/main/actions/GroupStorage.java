package main.actions;

import main.components.JParamTextField;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * Group Ironman shared storage (v1.31) - deposit or withdraw from the group chest. Only
 * offered in the action list when the account type (Status tab) is set to Group Ironman.
 * Stand near the group storage / a bank chest first (a Walk step gets you there).
 *
 * <p>Honest note: this rides DreamBot's SharedStorage API; on client builds where that API
 * isn't available the attempts fail cleanly rather than clicking blind.
 */
public class GroupStorage extends Action {

    @Override public boolean acceptsEntityTarget() { return false; }

    private JParamTextField paramMode;     // deposit | withdraw
    private JParamTextField paramAmount;

    public GroupStorage() {
        super();
        paramTarget = new JParamTextField("");
        paramMode = new JParamTextField("deposit");
        paramAmount = new JParamTextField("0");
        maxAttempts = 10;
    }

    public GroupStorage(GroupStorage o) {
        this();
        paramTarget.setParam(o.paramTarget.getParam());
        paramMode.setParam(o.paramMode.getParam());
        paramAmount.setParam(o.paramAmount.getParam());
    }

    @Override
    public boolean execute() {
        try {
            // v1.31 hotfix: rides the reflection adapter - builds without a group-storage API
            // fail attempts cleanly (one WARN in the console) instead of not compiling.
            if (!main.tools.GroupStorageApi.available()) { noteAttempt(); return false; }
            if (!main.tools.GroupStorageApi.isOpen()) {
                noteAttempt();
                main.tools.GroupStorageApi.open();
                return false;
            }
            String mode = paramMode.getParam() == null ? "deposit"
                    : paramMode.getParam().trim().toLowerCase();
            int amount = ActionUtil.parseInt(paramAmount.getParam(), 0);
            String[] items = ActionUtil.names(paramTarget.getParam());
            boolean allDone = true;
            for (String item : items) {
                if (item == null || item.isBlank()) continue;
                if (mode.startsWith("w")) {
                    if (!org.dreambot.api.methods.container.impl.Inventory.contains(item)) {
                        noteAttempt();
                        main.tools.GroupStorageApi
                                .withdraw(item, amount <= 0 ? Integer.MAX_VALUE : amount);
                        allDone = false;
                    }
                } else {
                    if (org.dreambot.api.methods.container.impl.Inventory.contains(item)) {
                        noteAttempt();
                        if (amount <= 0)
                            main.tools.GroupStorageApi.depositAll(item);
                        else
                            main.tools.GroupStorageApi.deposit(item, amount);
                        allDone = false;
                    }
                }
            }
            if (allDone) {
                main.tools.GroupStorageApi.close();
                return true;
            }
        } catch (Throwable ignored) { noteAttempt(); }
        return false;
    }

    @Override public String getParamTarget() { return paramTarget.getParam(); }
    @Override public Action copy() { return new GroupStorage(this); }
    @Override public String conflictGroup() { return "bank"; }

    @Override
    public JPanel createParamPanel() {
        JPanel md = createParameterPanel("Mode:",
                "deposit puts the items into group storage; withdraw takes them out.",
                paramMode, "  \"deposit\" or \"withdraw\"");
        JPanel it = createParameterPanel("Items:",
                "Item name(s), comma-separated.", paramTarget, "  e.g. \"Lobster, Coal\"");
        JPanel am = createParameterPanel("Amount:",
                "Per item. 0 = all.", paramAmount, "  e.g. \"0\"");
        return ActionUtil.stack(md, it, am);
    }

    @Override
    public Map<String, String> serialize() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Mode", paramMode.getParam());
        m.put("Items", paramTarget.getParam());
        m.put("Amount", paramAmount.getParam());
        return m;
    }

    @Override
    public void deserialize(Map<String, String> data) {
        if (data == null) return;
        if (data.get("Mode") != null) paramMode.setParam(data.get("Mode"));
        if (data.get("Items") != null) paramTarget.setParam(data.get("Items"));
        if (data.get("Amount") != null) paramAmount.setParam(data.get("Amount"));
    }
}
