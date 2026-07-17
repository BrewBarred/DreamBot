package main.actions;

import main.components.JParamTextField;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * Works the game's interfaces directly (v1.31). Two modes:
 *
 * <ul>
 *   <li><b>interact</b> - clicks a widget, found either by ids ("parent,child" or
 *       "parent,child,sub") or by the text it shows ("Bronze", "Claim") - which is how you
 *       drive production menus (furnace bar choice) and anything Dialogues doesn't cover.</li>
 *   <li><b>read</b> - captures the widget's text (by ids, or every visible widget containing
 *       the given text) and appends it to the <b>Player Log</b> in the Logs tab, where it's
 *       searchable - clue steps, quest text, whatever's on screen.</li>
 * </ul>
 */
public class WidgetAction extends Action {

    @Override public boolean acceptsEntityTarget() { return false; }

    private JParamTextField paramMode;     // interact | read
    private JParamTextField paramIds;      // "parent,child[,sub]" - blank = find by text
    private JParamTextField paramText;     // contains-text locator / read label
    private JParamTextField paramVerb;     // interact action ("" = default click)

    private transient boolean done;

    public WidgetAction() {
        super();
        paramMode = new JParamTextField("interact");
        paramIds = new JParamTextField("");
        paramText = new JParamTextField("");
        paramVerb = new JParamTextField("");
        paramTarget = paramIds;
        maxAttempts = 8;
    }

    public WidgetAction(WidgetAction o) {
        this();
        paramMode.setParam(o.paramMode.getParam());
        paramIds.setParam(o.paramIds.getParam());
        paramText.setParam(o.paramText.getParam());
        paramVerb.setParam(o.paramVerb.getParam());
    }

    private org.dreambot.api.wrappers.widgets.WidgetChild locate() {
        String ids = paramIds.getParam() == null ? "" : paramIds.getParam().trim();
        try {
            if (!ids.isEmpty()) {
                // v1.31 hotfix: the static Widgets accessors differ per DreamBot build, so
                // lookups go through the reflection-backed WidgetFinder.
                String[] p = ids.split("[,:\\s]+");
                int parent = p.length > 0 ? ActionUtil.parseInt(p[0], -1) : -1;
                int child  = p.length > 1 ? ActionUtil.parseInt(p[1], -1) : -1;
                int sub    = p.length > 2 ? ActionUtil.parseInt(p[2], -1) : -1;
                if (parent < 0 || child < 0) return null;
                return main.tools.WidgetFinder.byIds(parent, child, sub);
            }
            String text = paramText.getParam() == null ? "" : paramText.getParam().trim();
            if (text.isEmpty()) return null;
            for (org.dreambot.api.wrappers.widgets.WidgetChild c
                    : main.tools.WidgetFinder.containingText(text))
                if (c != null && c.isVisible()) return c;
        } catch (Throwable ignored) {}
        return null;
    }

    @Override
    public boolean execute() {
        if (done) { done = false; return true; }
        boolean read = paramMode.getParam() != null
                && paramMode.getParam().trim().toLowerCase().startsWith("r");
        org.dreambot.api.wrappers.widgets.WidgetChild wc = locate();
        if (wc == null) { noteAttempt(); return false; }

        if (read) {
            String label = paramText.getParam() == null || paramText.getParam().isBlank()
                    ? "Widget " + paramIds.getParam() : paramText.getParam();
            StringBuilder sb = new StringBuilder();
            try {
                String t = wc.getText();
                if (t != null && !t.isBlank()) sb.append(t.replace("<br>", " ").trim());
                // v1.31 hotfix: getChildren() returns an ARRAY on the real client
                org.dreambot.api.wrappers.widgets.WidgetChild[] kids = wc.getChildren();
                if (kids != null)
                    for (org.dreambot.api.wrappers.widgets.WidgetChild k : kids) {
                        if (k == null || !k.isVisible()) continue;
                        String kt = k.getText();
                        if (kt != null && !kt.isBlank())
                            sb.append(sb.length() > 0 ? " | " : "").append(kt.replace("<br>", " ").trim());
                    }
            } catch (Throwable ignored) {}
            if (sb.length() == 0) { noteAttempt(); return false; }
            main.tools.ChatLog.note(label, sb.toString());
            return true;
        }

        // interact
        noteAttempt();
        try {
            String verb = paramVerb.getParam() == null ? "" : paramVerb.getParam().trim();
            boolean ok = verb.isEmpty() ? wc.interact() : wc.interact(verb);
            if (ok) { done = true; }   // settle one poll, then complete
        } catch (Throwable ignored) {}
        return false;
    }

    @Override public String getParamTarget() {
        String ids = paramIds.getParam();
        return ids != null && !ids.isBlank() ? ids : paramText.getParam();
    }
    @Override public Action copy() { return new WidgetAction(this); }

    @Override
    public JPanel createParamPanel() {
        JPanel mode = createParameterPanel("Mode:",
                "\"interact\" clicks the widget; \"read\" captures its text into the Player Log "
                        + "(Logs tab) so you can search it later.",
                paramMode, "  \"interact\" or \"read\"");
        JPanel ids = createParameterPanel("Widget ids:",
                "The widget as \"parent,child\" or \"parent,child,sub\". Leave blank to find "
                        + "the widget by its text instead.",
                paramIds, "  e.g. \"270,14\"  (blank = use text below)");
        JPanel text = createParameterPanel("Text:",
                "Find the first visible widget containing this text (e.g. the \"Bronze\" choice "
                        + "on the smelting menu). In read mode this is also the log label.",
                paramText, "  e.g. \"Bronze\"");
        JPanel verb = createParameterPanel("Action:",
                "Optional right-click action for interact mode. Blank = default click.",
                paramVerb, "  e.g. \"Smelt\" (usually blank)");
        return ActionUtil.stack(mode, ids, text, verb);
    }

    @Override
    public Map<String, String> serialize() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Mode", paramMode.getParam());
        m.put("Ids", paramIds.getParam());
        m.put("Text", paramText.getParam());
        m.put("Verb", paramVerb.getParam());
        return m;
    }

    @Override
    public void deserialize(Map<String, String> data) {
        if (data == null) return;
        if (data.get("Mode") != null) paramMode.setParam(data.get("Mode"));
        if (data.get("Ids") != null) paramIds.setParam(data.get("Ids"));
        if (data.get("Text") != null) paramText.setParam(data.get("Text"));
        if (data.get("Verb") != null) paramVerb.setParam(data.get("Verb"));
    }
}
