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
        d.setParams(action.serialize());
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

        if (data.getParams() != null)
            action.deserialize(data.getParams());
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
        d.name = task.getName();
        d.description = task.getDescription();
        d.status = task.getStatus();
        d.repeat = task.getRepeat();
        d.actions = actionsToData(task.getActions());
        return d;
    }

    public static DreamBotMenu.Task fromData(TaskData d) {
        if (d == null)
            return null;
        DreamBotMenu.Task t = new DreamBotMenu.Task(d.name, d.description, actionsFromData(d.actions), d.status);
        t.setRepeat(d.repeat);
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
