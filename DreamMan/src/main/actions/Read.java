package main.actions;

import main.components.JParamTextField;
import main.tools.ChatLog;
import main.tools.WidgetFinder;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.wrappers.widgets.WidgetChild;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * READ (v1.87): capture what's on screen into the Player Log, so scripts can leave a paper
 * trail and players can hand you the exact widget text a task needs - read a clue scroll once
 * and its text (plus the widget ids it lives at) is on record for building the solver.
 *
 * <p>The target decides what gets read, checked in this order:
 * <ol>
 *   <li><b>empty / "dialogue"</b> - the open dialogue: speaker, line, and any options.</li>
 *   <li><b>"group,child" or "group,child,index"</b> - that exact widget's text by id.</li>
 *   <li><b>an item name</b> - the item is right-clicked "Read" (clue scrolls, books, notes),
 *       a beat passes for the interface, then every NEW readable widget on screen is captured.
 *       The before/after diff keeps the log to what the read actually opened.</li>
 * </ol>
 * Whatever it reads, the entry's double-click payload is the raw text - paste-ready.
 */
public class Read extends Action {

    @Override public boolean acceptsEntityTarget() { return false; }

    private transient boolean interacted;
    private transient long interactedAt;
    private transient java.util.Set<String> before;   // widget texts visible pre-click

    public Read() {
        super();
        paramTarget = new JParamTextField("");
        maxAttempts = 6;
    }

    public Read(Read o) {
        this();
        paramTarget.setParam(o.paramTarget.getParam());
    }

    @Override
    public boolean execute() {
        String target = paramTarget.getParam() == null ? "" : paramTarget.getParam().trim();

        // 1) the open dialogue (also the default when no target is given)
        boolean wantDialogue = target.isEmpty() || target.equalsIgnoreCase("dialogue");
        try {
            if (Dialogues.inDialogue()) {
                String text = Dialogues.getNPCDialogue();
                String[] opts = null;
                try { if (Dialogues.areOptionsAvailable()) opts = Dialogues.getOptions(); } catch (Throwable ignored) {}
                ChatLog.dialogue("", text, opts);
                return true;
            }
        } catch (Throwable ignored) {}
        if (wantDialogue) {
            // no dialogue open: capture whatever readable widgets ARE up (an open clue, a sign
            // interface) so a bare Read is still useful, then finish either way
            int got = snapshotAll("read");
            if (got == 0) ChatLog.note("read", "Nothing readable on screen.");
            return true;
        }

        // 2) explicit widget ids: "group,child[,index]"
        int[] ids = parseIds(target);
        if (ids != null) {
            try {
                WidgetChild w = WidgetFinder.byIds(ids[0], ids[1], ids.length > 2 ? ids[2] : -1);
                String text = w == null ? null : w.getText();
                if (text != null && !text.replaceAll("<[^>]*>", "").isBlank()) {
                    ChatLog.widget(target, text.replaceAll("<br>", " / ").replaceAll("<[^>]*>", "").trim());
                    return true;
                }
            } catch (Throwable ignored) {}
            noteAttempt();
            return false;   // widget not up yet - retry until attempts run out
        }

        // 3) an inventory item: interact "Read", wait a beat, capture what appeared
        if (!interacted) {
            before = textsNow();
            try {
                if (Inventory.contains(target) && Inventory.interact(target, "Read")) {
                    interacted = true;
                    interactedAt = System.currentTimeMillis();
                    return false;
                }
            } catch (Throwable ignored) {}
            noteAttempt();
            return false;
        }
        if (System.currentTimeMillis() - interactedAt < 1200) return false;   // let the UI open
        int captured = 0;
        try {
            for (String[] pair : WidgetFinder.visibleTexts()) {
                if (before != null && before.contains(pair[1])) continue;   // was already there
                ChatLog.widget(pair[0], pair[1]);
                captured++;
            }
        } catch (Throwable ignored) {}
        if (captured == 0)
            ChatLog.note("read", "\"" + target + "\" opened nothing readable.");
        interacted = false;
        before = null;
        return true;
    }

    private static java.util.Set<String> textsNow() {
        java.util.Set<String> s = new java.util.HashSet<>();
        try {
            for (String[] pair : WidgetFinder.visibleTexts()) s.add(pair[1]);
        } catch (Throwable ignored) {}
        return s;
    }

    /** Logs every currently readable widget under the given label. @return how many. */
    private static int snapshotAll(String label) {
        int n = 0;
        try {
            List<String[]> all = WidgetFinder.visibleTexts();
            for (String[] pair : all) {
                ChatLog.widget(pair[0], pair[1]);
                n++;
            }
        } catch (Throwable ignored) {}
        return n;
    }

    /** "548,12" / "548, 12, 3" → ids, or null when the target isn't an id path. */
    private static int[] parseIds(String s) {
        String[] parts = s.split(",");
        if (parts.length < 2 || parts.length > 3) return null;
        int[] out = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) out[i] = Integer.parseInt(parts[i].trim());
            return out;
        } catch (NumberFormatException nope) {
            return null;
        }
    }

    @Override public String getParamTarget() { return paramTarget.getParam(); }
    @Override public Action copy() { return new Read(this); }

    @Override
    public JPanel createParamPanel() {
        return ActionUtil.stack(createParameterPanel("Read:",
                "What to read into the Player Log. Leave empty for the open dialogue "
                        + "(speaker, line and options); give widget ids to read that exact "
                        + "widget; give an item name to right-click Read it (clue scrolls, "
                        + "books) and capture what opens.",
                paramTarget, "  e.g. \"\" (dialogue)   \"229,1\" (widget)   \"Clue scroll (easy)\""));
    }

    @Override
    public Map<String, String> serialize() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Target", paramTarget.getParam());
        return m;
    }

    @Override
    public void deserialize(Map<String, String> data) {
        if (data == null) return;
        if (data.get("Target") != null) paramTarget.setParam(data.get("Target"));
    }
}
