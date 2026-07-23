package main.actions;

import main.components.JParamTextField;
import main.tools.ChatLog;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * NOTE (v1.87): write a line to the Player Log and move on - the script's own diary. Drop
 * "Completed mining!" after the mining chain and the log shows exactly when each stage of a
 * run happened, timestamped like every other entry, which is most of what testing a long task
 * ever needs. Nothing in the game is touched; the note is written and the action is done.
 */
public class Note extends Action {

    @Override public boolean acceptsEntityTarget() { return false; }

    public Note() {
        super();
        paramTarget = new JParamTextField("Checkpoint reached");
        maxAttempts = 1;
    }

    public Note(Note o) {
        this();
        paramTarget.setParam(o.paramTarget.getParam());
    }

    @Override
    public boolean execute() {
        String text = paramTarget.getParam();
        if (text != null && !text.isBlank())
            ChatLog.note("script", text.trim());
        return true;   // instant - a note is never something to retry
    }

    @Override public String getParamTarget() { return paramTarget.getParam(); }
    @Override public Action copy() { return new Note(this); }

    @Override
    public JPanel createParamPanel() {
        return ActionUtil.stack(createParameterPanel("Note:",
                "Written to the Player Log with a timestamp when this step runs - milestones "
                        + "(\"Completed mining!\"), loop markers, anything that helps you read "
                        + "back how a run went.",
                paramTarget, "  e.g. \"Completed mining!\""));
    }

    @Override
    public Map<String, String> serialize() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Text", paramTarget.getParam());
        return m;
    }

    @Override
    public void deserialize(Map<String, String> data) {
        if (data == null) return;
        if (data.get("Text") != null) paramTarget.setParam(data.get("Text"));
    }
}
