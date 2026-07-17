package main.actions;

import main.components.JParamTextField;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * Turns run on or off (v1.31). Completes as soon as the run state matches; if energy is 0 the
 * client can refuse to enable run - that counts against retries so a check can react.
 */
public class ToggleRun extends Action {

    @Override public boolean acceptsEntityTarget() { return false; }

    public ToggleRun() {
        super();
        paramTarget = new JParamTextField("on");
        maxAttempts = 6;
    }

    public ToggleRun(ToggleRun o) {
        this();
        paramTarget.setParam(o.paramTarget.getParam());
    }

    private boolean wantOn() {
        String v = paramTarget.getParam() == null ? "on" : paramTarget.getParam().trim().toLowerCase();
        return !v.startsWith("off") && !v.startsWith("dis") && !v.equals("false") && !v.equals("0");
    }

    @Override
    public boolean execute() {
        try {
            boolean on = org.dreambot.api.methods.walking.impl.Walking.isRunEnabled();
            if (on == wantOn()) return true;
            noteAttempt();
            org.dreambot.api.methods.walking.impl.Walking.toggleRun();
        } catch (Throwable ignored) { noteAttempt(); }
        return false;
    }

    @Override public String getParamTarget() { return paramTarget.getParam(); }
    @Override public Action copy() { return new ToggleRun(this); }

    @Override
    public JPanel createParamPanel() {
        return ActionUtil.stack(createParameterPanel("Run:",
                "Enable or disable running.", paramTarget, "  \"on\" or \"off\""));
    }

    @Override
    public Map<String, String> serialize() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Run", paramTarget.getParam());
        return m;
    }

    @Override
    public void deserialize(Map<String, String> data) {
        if (data == null) return;
        if (data.get("Run") != null) paramTarget.setParam(data.get("Run"));
    }
}
