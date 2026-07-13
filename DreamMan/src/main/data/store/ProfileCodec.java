package main.data.store;

import main.actions.Action;
import main.data.ActionData;
import main.menu.DreamBotMenu;
import main.menu.components.JActionSelector;
import org.dreambot.api.utilities.Logger;

import javax.swing.DefaultListModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts between the menu's live runtime types ({@code Task}, {@code Preset}, {@code Action})
 * and the flat, Gson-friendly DTOs in this package.
 * <p>
 * Actions are the tricky part: on the way out we call {@link Action#getName()} (the concrete
 * class's simple name, which is also its registry key) plus {@link Action#serialize()}; on the
 * way back in we ask {@link JActionSelector#createByType(String)} for a fresh instance and then
 * {@link Action#deserialize(java.util.Map)} it. Unknown action types are skipped with a log line
 * rather than throwing, so one bad entry can't nuke an entire profile load.
 */
public final class ProfileCodec {

    private ProfileCodec() {}

    // ---- Action <-> ActionData -------------------------------------------------------------

    public static ActionData toData(Action action) {
        ActionData d = new ActionData();
        d.setType(action.getName());        // simple class name == JActionSelector registry key
        java.util.Map<String, String> params = action.serialize();
        // Patch B.4: fold the action's attached watchers into its params centrally, so every
        // action persists its triggers without each serialize() needing to know about them.
        if (params != null && !action.getTriggers().isEmpty()) {
            params = new java.util.LinkedHashMap<>(params);
            params.put(Action.TRIGGERS_KEY, main.watchers.TriggerCodec.toJson(action.getTriggers()));
        }
        // Patch B.7: fold the chance-to-run in centrally too (only when < 100, to keep old
        // saves clean and unchanged for guaranteed actions).
        if (params != null && action.getChancePercent() < 100) {
            params = new java.util.LinkedHashMap<>(params);
            params.put(Action.CHANCE_KEY, String.valueOf(action.getChancePercent()));
        }
        d.setParams(params);
        return d;
    }

    public static Action fromData(ActionData data) {
        if (data == null || data.getType() == null)
            return null;

        Action action = JActionSelector.createByType(data.getType());
        if (action == null) {
            Logger.log(Logger.LogType.ERROR, "[LocalStore] Unknown action type '" + data.getType()
                    + "' - skipping (was it renamed or removed?).");
            return null;
        }

        if (data.getParams() != null) {
            action.deserialize(data.getParams());
            // Patch B.4: restore attached watchers centrally (deserialize() ignores the
            // reserved __triggers key, so pull it back here).
            String tj = data.getParams().get(Action.TRIGGERS_KEY);
            if (tj != null && !tj.isBlank()) {
                action.getTriggers().clear();
                action.getTriggers().addAll(main.watchers.TriggerCodec.fromJson(tj));
            }
            // Patch B.7: restore the chance-to-run (absent -> stays 100%)
            String cj = data.getParams().get(Action.CHANCE_KEY);
            if (cj != null && !cj.isBlank()) {
                try { action.setChancePercent(Integer.parseInt(cj.trim())); }
                catch (NumberFormatException ignored) {}
            }
        }
        return action;
    }

    private static List<ActionData> actionsToData(List<Action> actions) {
        List<ActionData> out = new ArrayList<>();
        if (actions != null)
            for (Action a : actions)
                if (a != null)
                    out.add(toData(a));
        return out;
    }

    private static List<Action> actionsFromData(List<ActionData> data) {
        List<Action> out = new ArrayList<>();
        if (data != null)
            for (ActionData d : data) {
                Action a = fromData(d);
                if (a != null)
                    out.add(a);
            }
        return out;
    }

    // ---- Task <-> TaskData -----------------------------------------------------------------

    public static TaskData toData(DreamBotMenu.Task task) {
        TaskData d = new TaskData();
        d.id = task.getId();
        d.createdAt = task.getCreatedAt();
        d.origin = task.getOrigin();
        d.vipOnly = task.isVipOnly();
        d.timed = task.isTimed();
        d.timerMinutes = task.getTimerMinutes();
        d.timerJitterPct = task.getTimerJitterPct();
        d.timerWhen = task.getTimerWhen();
        d.name = task.getName();
        d.description = task.getDescription();
        d.status = task.getStatus();
        d.repeat = task.getRepeat();
        d.autoDelay = task.isAutoDelay();
        d.autoDelayMinMs = task.getAutoDelayMinMs();
        d.autoDelayMaxMs = task.getAutoDelayMaxMs();
        d.actions = actionsToData(task.getActions());
        return d;
    }

    public static DreamBotMenu.Task fromData(TaskData d) {
        if (d == null)
            return null;
        DreamBotMenu.Task t = new DreamBotMenu.Task(d.name, d.description, actionsFromData(d.actions), d.status);
        t.restoreId(d.id);   // Patch B.2: keep the saved identity (no-op for pre-B.2 files)
        t.restoreCreatedAt(d.createdAt);
        t.setOrigin(d.origin);
        t.setVipOnly(d.vipOnly);
        t.setTimed(d.timed);
        if (d.timerMinutes > 0) t.setTimerMinutes(d.timerMinutes);
        t.setTimerJitterPct(d.timerJitterPct);
        t.setTimerWhen(d.timerWhen);
        t.setRepeat(d.repeat);
        t.setAutoDelay(d.autoDelay, d.autoDelayMinMs, d.autoDelayMaxMs);
        return t;
    }

    // ---- Preset <-> PresetData -------------------------------------------------------------

    public static PresetData toData(DreamBotMenu.Preset preset) {
        PresetData d = new PresetData();
        d.name = preset.getName();
        d.loops = preset.getLoops();
        d.tasks = new ArrayList<>();
        if (preset.getTasks() != null)
            for (DreamBotMenu.Task t : preset.getTasks())
                if (t != null)
                    d.tasks.add(toData(t));
        return d;
    }

    public static DreamBotMenu.Preset fromData(PresetData d) {
        if (d == null)
            return null;
        List<DreamBotMenu.Task> tasks = new ArrayList<>();
        if (d.tasks != null)
            for (TaskData td : d.tasks) {
                DreamBotMenu.Task t = fromData(td);
                if (t != null)
                    tasks.add(t);
            }
        DreamBotMenu.Preset p = new DreamBotMenu.Preset(d.name, tasks);
        p.setLoops(d.loops);
        return p;
    }

    // ---- List-model helpers (convenience for the menu) -------------------------------------

    /** Master-list variant (Patch B.5): captures ALL tasks, filtered-out ones included. */
    public static List<TaskData> tasksToData(java.util.List<DreamBotMenu.Task> tasks) {
        List<TaskData> out = new ArrayList<>();
        if (tasks != null)
            for (DreamBotMenu.Task t : tasks)
                if (t != null) out.add(toData(t));
        return out;
    }

    public static List<TaskData> tasksToData(DefaultListModel<DreamBotMenu.Task> model) {
        List<TaskData> out = new ArrayList<>();
        for (int i = 0; i < model.size(); i++)
            out.add(toData(model.get(i)));
        return out;
    }

    public static List<PresetData> presetsToData(DefaultListModel<DreamBotMenu.Preset> model) {
        List<PresetData> out = new ArrayList<>();
        for (int i = 0; i < model.size(); i++)
            out.add(toData(model.get(i)));
        return out;
    }
}
