package main.actions;

import main.components.JParamTextField;
import main.menu.components.JParamComboField;
import main.menu.DreamBotMenu;
import org.dreambot.api.utilities.Logger;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * Runs a task from your Task Library as a single action (Patch B.2) - the composition piece:
 * chain simple tasks together inside bigger ones to build complex scripts out of parts you've
 * already made and tested.
 *
 * <p><b>Live by identity:</b> the referenced task is resolved from the library BY ID at run
 * time, every pass. Edit the library task once ("Save changes") and every script that embeds it
 * runs the new version automatically - true modularity, no stale copies.
 *
 * <p>The referenced task's own repeat (&times;N) is honoured: one TaskRef execution = that task,
 * run its configured number of times. If a step inside it exhausts its retries, this action
 * fails too and the engine moves the outer task on. Circular references (a task containing
 * itself, directly or through another task) are detected and fail cleanly instead of recursing.
 */
public class TaskRef extends Action {

    /** Patch B.17: no entity target - the entity list never writes into this action. */
    @Override public boolean acceptsEntityTarget() { return false; }


    /** Set by the menu at startup: (id, name) -> live library task, or null. */
    private static volatile BiFunction<String, String, DreamBotMenu.Task> RESOLVER;
    /** Tasks currently executing on this chain - the circular-reference guard. */
    private static final Deque<String> ACTIVE = new ArrayDeque<>();
    private static final int MAX_DEPTH = 6;

    public static void setResolver(BiFunction<String, String, DreamBotMenu.Task> r) {
        RESOLVER = r;
    }

    /** v1.62: set by the menu - supplies current library task names for the param dropdown. */
    private static volatile Supplier<List<String>> NAMES_SUPPLIER;

    public static void setNamesSupplier(Supplier<List<String>> s) {
        NAMES_SUPPLIER = s;
    }

    /** The library task's id (name is the user-facing parameter; id locks on first resolve). */
    private String taskId;

    /** True for selector entries generated from the library (Patch B.3): shows "Task: name". */
    private transient boolean boundEntry;

    /** Binds this instance to a specific library task (selector entries). */
    public void bind(String name, String id) {
        paramTarget.setParam(name);
        this.taskId = id;
        this.boundEntry = true;
    }

    /** @return true when this instance is a selector entry generated from the library. */
    public boolean isBoundEntry() { return boundEntry; }

    // ── live pass state (transient) ──
    private transient List<Action> steps;
    private transient int cursor;
    private transient int passesDone;
    private transient int passesWanted;
    private transient boolean passActive;
    private transient long lastPollAt;
    private transient String activeGuardId;

    public TaskRef() {
        super();
        paramTarget = new JParamTextField("My task name");
    }

    public TaskRef(TaskRef o) {
        this();
        paramTarget.setParam(o.paramTarget.getParam());
        this.taskId = o.taskId;
        this.boundEntry = o.boundEntry;
    }

    private void teardown() {
        steps = null;
        cursor = 0;
        passesDone = 0;
        passActive = false;
        if (activeGuardId != null) {
            synchronized (ACTIVE) { ACTIVE.remove(activeGuardId); }
            activeGuardId = null;
        }
    }

    /** Resolves the library task and copies its actions for one fresh run. */
    private boolean setup() {
        BiFunction<String, String, DreamBotMenu.Task> r = RESOLVER;
        if (r == null) {
            Logger.log(Logger.LogType.ERROR, "[TaskRef] No library resolver - menu not initialised?");
            noteAttempt();
            return false;
        }
        DreamBotMenu.Task task = r.apply(taskId, paramTarget.getParam());
        if (task == null) {
            Logger.log(Logger.LogType.WARN,
                    "[TaskRef] Library task not found: '" + paramTarget.getParam() + "'");
            noteAttempt();
            return false;
        }
        taskId = task.getId();   // lock onto the identity - survives renames from here on

        synchronized (ACTIVE) {
            if (ACTIVE.contains(taskId) || ACTIVE.size() >= MAX_DEPTH) {
                Logger.log(Logger.LogType.ERROR, "[TaskRef] Circular/too-deep task reference: '"
                        + task.getName() + "' - failing this step instead of recursing.");
                maxAttempts = 1;
                noteAttempt();
                return false;
            }
            ACTIVE.push(taskId);
            activeGuardId = taskId;
        }

        steps = new ArrayList<>();
        copyStepsOnEdt(task);   // v1.33: action.copy() builds Swing fields - must be on the EDT
        cursor = 0;
        passesDone = 0;
        passesWanted = Math.max(1, task.getRepeat());
        passActive = true;

        if (steps.isEmpty()) {
            Logger.log(Logger.LogType.WARN, "[TaskRef] '" + task.getName() + "' has no actions.");
            teardown();
            return true;   // an empty task is trivially complete
        }
        return true;
    }

    /** v1.33: build the step copies on the EDT - Action constructors create Swing fields, which
     *  Substance forbids off the EDT (that's why every TaskRef used to throw and fail). */
    private void copyStepsOnEdt(DreamBotMenu.Task task) {
        Runnable copy = () -> {
            if (task.getActions() != null)
                for (Action a : task.getActions())
                    // v1.86: copyDeep - copy() is a subclass hook and several subclasses
                    // (Walk among them) never call copyTriggersFrom, so it drops chance,
                    // triggers and the on-start flag.
                    if (a != null) steps.add(a.copyDeep());
        };
        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            copy.run();
        } else {
            try {
                javax.swing.SwingUtilities.invokeAndWait(copy);
            } catch (Throwable t) {
                steps.clear();
                copy.run();   // last-resort direct copy; a warning beats a dead TaskRef
            }
        }
    }

    @Override
    public boolean execute() {
        long now = System.currentTimeMillis();
        // stale state from an interrupted run (outer task skipped mid-pass) -> start fresh
        if (passActive && now - lastPollAt > 10_000)
            teardown();
        lastPollAt = now;

        if (!passActive) {
            boolean ok = setup();
            if (!ok) return false;          // resolver/guard problem - attempts already noted
            if (!passActive) return true;   // empty task completed immediately
        }

        Action step = steps.get(cursor);
        if (step == null) {
            cursor++;
        } else if (step.execute()) {
            step.resetAttempts();
            cursor++;
        } else if (step.attemptsExhausted()) {
            Logger.log(Logger.LogType.WARN, "[TaskRef] Step failed inside '"
                    + paramTarget.getParam() + "': " + step.getName()
                    + " gave up after " + step.getAttempts() + " tries.");
            teardown();
            maxAttempts = 1;   // bubble the failure: this composite can't complete
            noteAttempt();
            return false;
        }

        if (cursor >= steps.size()) {
            passesDone++;
            if (passesDone >= passesWanted) {
                teardown();
                resetAttempts();
                return true;   // all passes of the referenced task complete
            }
            cursor = 0;        // honour the referenced task's xN repeat
        }
        return false;
    }

    @Override
    public JPanel createParamPanel() {
        // v1.62: the library reference is now a pick-from-list dropdown instead of a free-text
        // field. The combo is populated from the current Task Library, but stays editable so a
        // name that isn't in the library yet still works (backward-compatible with old refs).
        // It syncs into the base paramTarget on every change, so serialize/apply are unchanged.
        JParamComboField combo = new JParamComboField(paramTarget.getParam());
        Supplier<List<String>> names = NAMES_SUPPLIER;
        if (names != null) {
            try { combo.setItemsPreservingValue(names.get()); } catch (Exception ignored) {}
        }
        java.awt.Component ed = combo.getEditor() != null ? combo.getEditor().getEditorComponent() : null;
        if (ed instanceof javax.swing.text.JTextComponent) {
            ((javax.swing.text.JTextComponent) ed).getDocument().addDocumentListener(
                    new DocumentListener() {
                        private void sync() { paramTarget.setParam(combo.getParam()); }
                        public void insertUpdate(DocumentEvent e)  { sync(); }
                        public void removeUpdate(DocumentEvent e)  { sync(); }
                        public void changedUpdate(DocumentEvent e) { sync(); }
                    });
        }
        return createParameterPanel("Library task:",
                "Runs a task from your Task Library as one step - chain tested tasks into bigger"
                        + " scripts. Resolved live: edit the library task and every script using"
                        + " it updates automatically. Its ×N repeat is honoured.",
                combo, "  pick from your library, or type a task name");
    }

    @Override
    public String getParamTarget() { return paramTarget.getParam(); }

    @Override
    public String toBuildString() { return "Task → " + paramTarget.getParam(); }

    @Override
    public Action copy() { return new TaskRef(this); }

    @Override
    public Map<String, String> serialize() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("TaskName", paramTarget.getParam());
        if (taskId != null) m.put("TaskId", taskId);
        return m;
    }

    @Override
    public void deserialize(Map<String, String> data) {
        if (data == null) return;
        if (data.get("TaskName") != null) paramTarget.setParam(data.get("TaskName"));
        if (data.get("TaskId") != null) taskId = data.get("TaskId");
    }
}
