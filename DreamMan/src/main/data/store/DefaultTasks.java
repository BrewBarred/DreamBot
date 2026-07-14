package main.data.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import main.menu.DreamBotMenu;
import org.dreambot.api.utilities.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Default tasks (v1.30): the tasks every user starts with.
 *
 * <p><b>How defaults work:</b> two sources are merged into the library at startup, matched by
 * task id so nothing ever duplicates:
 * <ol>
 *   <li><b>Shipped</b> - {@code /resources/default-tasks.json} baked into the build. This is
 *       what your users receive.</li>
 *   <li><b>Local</b> - {@code <root>/default-tasks.json} on this machine. This is the admin's
 *       working set: the gold star on a library row toggles the task in and out of it.</li>
 * </ol>
 *
 * <p><b>Admin workflow:</b> star the tasks you want as defaults, then copy your local
 * {@code default-tasks.json} into {@code src/resources/} before building a release - the file
 * is a full snapshot (whole tasks, not references), so users receive them even with an empty
 * library. The star writes the CURRENT state of the task; re-star after editing to update the
 * snapshot.
 */
public final class DefaultTasks {

    private DefaultTasks() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<TaskData>>() {}.getType();

    /** The admin's local working set (full task snapshots). Loaded lazily. */
    private static List<TaskData> local;
    /** Ids of every known default (shipped + local), for fast isDefault checks. */
    private static Set<String> defaultIds;

    private static java.io.File localFile() {
        return new java.io.File(LocalStore.getRoot(), "default-tasks.json");
    }

    private static synchronized void load() {
        if (local != null) return;
        local = new ArrayList<>();
        defaultIds = new HashSet<>();
        // local working set
        try {
            java.io.File f = localFile();
            if (f.isFile()) {
                List<TaskData> l = GSON.fromJson(new String(Files.readAllBytes(f.toPath()),
                        StandardCharsets.UTF_8), LIST_TYPE);
                if (l != null) local.addAll(l);
            }
        } catch (Throwable e) {
            Logger.log(Logger.LogType.WARN, "[DefaultTasks] couldn't read local file: " + e);
        }
        for (TaskData d : local) if (d != null && d.id != null) defaultIds.add(d.id);
        // shipped set (ids only here; merge() materialises them)
        for (TaskData d : shipped()) if (d != null && d.id != null) defaultIds.add(d.id);
    }

    /** The defaults baked into this build, or an empty list when none are shipped. */
    private static List<TaskData> shipped() {
        try (InputStream in = DefaultTasks.class.getResourceAsStream("/resources/default-tasks.json")) {
            if (in == null) return new ArrayList<>();
            List<TaskData> l = GSON.fromJson(
                    new InputStreamReader(in, StandardCharsets.UTF_8), LIST_TYPE);
            return l == null ? new ArrayList<>() : l;
        } catch (Throwable e) {
            return new ArrayList<>();
        }
    }

    /** @return true when this task id is a default (shipped or locally starred). */
    public static boolean isDefault(String taskId) {
        if (taskId == null) return false;
        load();
        return defaultIds.contains(taskId);
    }

    /**
     * Toggles a task in the admin's local defaults set (the star button). Stores a full
     * snapshot of the task as it is right now. @return true when the task IS a default after
     * the call.
     */
    public static synchronized boolean toggle(DreamBotMenu.Task task) {
        if (task == null || task.getId() == null) return false;
        load();
        boolean nowDefault;
        if (defaultIds.contains(task.getId())) {
            local.removeIf(d -> d != null && task.getId().equals(d.id));
            defaultIds.remove(task.getId());
            nowDefault = false;
        } else {
            local.add(ProfileCodec.toData(task));
            defaultIds.add(task.getId());
            nowDefault = true;
        }
        save();
        return nowDefault;
    }

    private static void save() {
        try {
            Files.write(localFile().toPath(),
                    GSON.toJson(local, LIST_TYPE).getBytes(StandardCharsets.UTF_8));
        } catch (Throwable e) {
            Logger.log(Logger.LogType.WARN, "[DefaultTasks] couldn't save: " + e);
        }
    }

    /**
     * Seeds every default the user doesn't already have (matched by id) into the library.
     * Called once after the profile loads. @return how many tasks were added.
     */
    public static int mergeInto(DreamBotMenu menu, List<DreamBotMenu.Task> existing) {
        load();
        Set<String> have = new HashSet<>();
        if (existing != null)
            for (DreamBotMenu.Task t : existing)
                if (t != null && t.getId() != null) have.add(t.getId());

        List<TaskData> all = new ArrayList<>(shipped());
        all.addAll(local);
        int added = 0;
        Set<String> seen = new HashSet<>();
        for (TaskData d : all) {
            if (d == null || d.id == null || have.contains(d.id) || !seen.add(d.id)) continue;
            DreamBotMenu.Task t = ProfileCodec.fromData(d);
            if (t == null) continue;
            t.setOrigin("default");
            menu.libraryAdd(t);
            added++;
        }
        if (added > 0)
            Logger.log(Logger.LogType.INFO, "[DefaultTasks] seeded " + added + " default task(s).");
        return added;
    }
}
