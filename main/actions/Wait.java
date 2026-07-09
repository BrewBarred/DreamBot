package main.actions;

import main.components.JParamTextField;
import main.tools.Rand;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * Waits a random amount of time between Min and Max milliseconds - the humanising glue between
 * actions. Non-blocking: it reports "not done" until the delay elapses (so pausing still works),
 * then resets so a repeated task re-randomises the wait each pass.
 */
public class Wait extends Action {

    private JParamTextField paramMin;
    private JParamTextField paramMax;

    /** Transient countdown target (not serialised). 0 = not currently waiting. */
    private long endTime = 0L;

    public Wait() {
        super();
        paramMin = new JParamTextField("400");
        paramMax = new JParamTextField("1200");
    }

    public Wait(Wait o) {
        this();
        paramMin.setParam(o.paramMin.getParam());
        paramMax.setParam(o.paramMax.getParam());
    }

    @Override
    public boolean execute() {
        int min = ActionUtil.parseInt(paramMin.getParam(), 400);
        int max = ActionUtil.parseInt(paramMax.getParam(), 1200);
        if (max < min) max = min;

        long now = System.currentTimeMillis();
        if (endTime == 0L)
            endTime = now + Rand.nextInt(min, max + 1);

        if (now >= endTime) {
            endTime = 0L;          // reset so the next run re-randomises
            return true;
        }
        return false;
    }

    @Override
    public JPanel createParamPanel() {
        JPanel min = createParameterPanel("Min (ms):",
                "Shortest wait, in milliseconds.",
                paramMin, "  e.g. \"400\"");

        JPanel max = createParameterPanel("Max (ms):",
                "Longest wait, in milliseconds.",
                paramMax, "  e.g. \"1200\"");

        return ActionUtil.stack(min, max);
    }

    @Override
    public String getParamTarget() {
        return paramMin.getParam() + "-" + paramMax.getParam() + "ms";
    }

    @Override
    public String toBuildString() {
        return "Wait → " + getParamTarget();
    }

    @Override
    public Action copy() {
        return new Wait(this);
    }

    @Override
    public Map<String, String> serialize() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Min", paramMin.getParam());
        m.put("Max", paramMax.getParam());
        return m;
    }

    @Override
    public void deserialize(Map<String, String> data) {
        if (data == null) return;
        if (data.get("Min") != null) paramMin.setParam(data.get("Min"));
        if (data.get("Max") != null) paramMax.setParam(data.get("Max"));
    }
}
