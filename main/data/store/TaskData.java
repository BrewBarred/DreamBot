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
    public String name;
    public String description;
    public String status;
    /** How many times this task runs before the queue advances (>=1). */
    public int repeat = 1;
    public List<main.data.ActionData> actions = new ArrayList<>();
}
