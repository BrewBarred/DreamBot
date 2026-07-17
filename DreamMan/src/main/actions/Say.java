package main.actions;

import main.components.JParamTextField;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * Speaks in public chat (v1.31). Give it one phrase or several separated by "|"
 * ("hi|hello|yo") and it types ONE of them, picked at random - pair it with a CHAT_CONTAINS
 * check to react to nearby players with a natural, varied reply.
 */
public class Say extends Action {

    @Override public boolean acceptsEntityTarget() { return false; }

    private transient boolean sent;
    private transient long sentAt;

    public Say() {
        super();
        paramTarget = new JParamTextField("hi");
        maxAttempts = 5;
    }

    public Say(Say o) {
        this();
        paramTarget.setParam(o.paramTarget.getParam());
    }

    @Override
    public boolean execute() {
        if (sent) {
            // tiny settle so back-to-back Says don't merge into one line
            if (System.currentTimeMillis() - sentAt > 600) { sent = false; return true; }
            return false;
        }
        String phrase = ActionUtil.pickPhrase(paramTarget.getParam());
        if (phrase.isEmpty()) return true;   // nothing to say = done
        noteAttempt();
        try {
            if (org.dreambot.api.input.Keyboard.type(phrase, true)) {
                main.tools.ChatLog.chat("PUBLIC", "(me)", phrase);
                sent = true;
                sentAt = System.currentTimeMillis();
            }
        } catch (Throwable ignored) {}
        return false;
    }

    @Override public String getParamTarget() { return paramTarget.getParam(); }
    @Override public Action copy() { return new Say(this); }

    @Override
    public JPanel createParamPanel() {
        return ActionUtil.stack(createParameterPanel("Say:",
                "What to type in public chat. Several phrases separated by | picks one at "
                        + "random each run - keeps replies from looking scripted.",
                paramTarget, "  e.g. \"hi\"   or   \"hi|hey|yo\""));
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
