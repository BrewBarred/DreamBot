package main.actions;

import main.components.JParamTextField;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * Blocks until the inventory holds the listed items (v1.31) - the "do things until you have
 * 1 copper ore AND 1 tin ore" gate. Counts are ABSOLUTE inventory totals ("Coal x8, Mithril
 * ore x1"), so it reads exactly like your smithing plan. Times out into a failed attempt so a
 * task can retry the gathering step or fail loudly instead of standing there forever.
 */
public class WaitForItems extends Action {

    @Override public boolean acceptsEntityTarget() { return false; }

    private JParamTextField paramTimeout;

    private transient long startedAt;

    public WaitForItems() {
        super();
        paramTarget = new JParamTextField("Copper ore x1, Tin ore x1");
        paramTimeout = new JParamTextField("60");
        maxAttempts = 3;
    }

    public WaitForItems(WaitForItems o) {
        this();
        paramTarget.setParam(o.paramTarget.getParam());
        paramTimeout.setParam(o.paramTimeout.getParam());
    }

    @Override
    public boolean execute() {
        java.util.Map<String, Integer> wanted = ActionUtil.parseItemList(paramTarget.getParam());
        if (wanted.isEmpty()) return true;
        if (ActionUtil.inventoryHasAll(wanted)) { startedAt = 0; return true; }

        long now = System.currentTimeMillis();
        if (startedAt == 0) startedAt = now;
        int timeoutS = Math.max(5, ActionUtil.parseInt(paramTimeout.getParam(), 60));
        if (now - startedAt > timeoutS * 1000L) {
            startedAt = 0;
            noteAttempt();   // timed out - burns a retry so the task can react/fail
        }
        return false;
    }

    @Override public String getParamTarget() { return paramTarget.getParam(); }
    @Override public Action copy() { return new WaitForItems(this); }

    @Override
    public JPanel createParamPanel() {
        JPanel it = createParameterPanel("Until inventory has:",
                "Absolute inventory counts, comma-separated. The step only completes when EVERY "
                        + "entry is met.",
                paramTarget, "  e.g. \"Copper ore x1, Tin ore x1\"  or  \"Coal x8, Mithril ore x1\"");
        JPanel to = createParameterPanel("Timeout (seconds):",
                "How long to wait before burning a retry (min 5s).",
                paramTimeout, "  e.g. \"60\"");
        return ActionUtil.stack(it, to);
    }

    @Override
    public Map<String, String> serialize() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Items", paramTarget.getParam());
        m.put("Timeout", paramTimeout.getParam());
        return m;
    }

    @Override
    public void deserialize(Map<String, String> data) {
        if (data == null) return;
        if (data.get("Items") != null) paramTarget.setParam(data.get("Items"));
        if (data.get("Timeout") != null) paramTimeout.setParam(data.get("Timeout"));
    }
}
