package main.data.store;

import java.util.ArrayList;
import java.util.List;

/**
 * Serialisable, storage-safe mirror of {@code DreamBotMenu.Task}.
 * <p>
 * A {@code Task} holds a {@code List<Action>}, and {@code Action} is abstract, so Gson cannot
 * (de)serialise a Task directly - it has no way to know which concrete subclass to instantiate.
 * (This was a latent crash in the old Supabase load path.) Instead we store each action as an
 * {@link main.data.ActionData} (type-name + flat param map), which round-trips cleanly, and
 * rebuild the concrete actions on load via {@link main.menu.components.JActionSelector}.
 */
public class TaskData {

    /** Patch B.2: the task's stable identity (see Task.getId()); null in pre-B.2 saves. */
    public String id;

    /** Patch B.3: creation time (epoch ms); 0 in older saves. */
    public long createdAt;

    /** Patch B.5: "user" | "imported" | "default"; null in older saves (treated as user). */
    public String origin;

    /** Patch B.14: VIP-only task flag. */
    public boolean vipOnly = false;
    /** v1.49: version + lifecycle flags. Absent in old saves (default 1.0 / false). */
    public double version = 1.0;
    public boolean exported = false;
    public boolean marketReady = false;
    public boolean published = false;
    public boolean downloaded = false;
    /** v1.51: comma-entered tags (search filters). Absent in old saves. */
    public java.util.List<String> tags;
    public String name;
    public String description;
    public String status;
    /** How many times this task runs before the queue advances (>=1). */
    public int repeat = 1;

    /**
     * v1.68: this queue entry runs on the first queue loop only, then is skipped.
     * Lives on the TASK SNAPSHOT rather than in any global side-table, so it travels with
     * exports and market bundles the way action/task triggers do, and so two instances of the
     * same logical task can differ (a side-table keyed by task id could not tell them apart).
     */
    public boolean onStartOnly = false;

    /**
     * v1.80: TASK triggers, stored as TriggerCodec JSON so the shape can evolve without breaking
     * old saves. Travels in the task snapshot, so exports and market bundles carry it.
     */
    public String taskTriggers;
    /** Patch B: automatic humanised pause between actions. */
    /** Patch B.8: timed-task config. */
    public boolean timed = false;
    public int timerMinutes = 30;
    public int timerJitterPct = 20;
    public String timerWhen;

    public boolean autoDelay = false;
    public int autoDelayMinMs = 600;
    public int autoDelayMaxMs = 1400;
    public List<main.data.ActionData> actions = new ArrayList<>();
}
