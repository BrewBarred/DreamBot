package main.data.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import main.menu.DreamBotMenu;
import org.dreambot.api.utilities.Logger;

import java.io.File;
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

    /**
     * The shipped defaults, fetched from the asset host at runtime and cached on disk.
     *
     * <p>v1.32 (SDN compliance): the guidelines forbid bundling resources as source files, so the
     * default-task set is no longer read from the jar. It's fetched once from
     * {@code <assets>/default-tasks.json}, cached under the sanctioned {@code scripts.path}, and
     * served from that cache thereafter (and whenever the network is unavailable). No defaults
     * simply means an empty starter library - never an error.
     */
    private static List<TaskData> shipped() {
        File cache = new File(LocalStore.getRoot(), "cache/default-tasks.json");
        // 1) try a fresh fetch (best-effort, short timeout); refresh the cache on success
        try {
            byte[] bytes = main.tools.AssetsText.get("/default-tasks.json");
            if (bytes != null && bytes.length > 0) {
                List<TaskData> l = GSON.fromJson(
                        new String(bytes, StandardCharsets.UTF_8), LIST_TYPE);
                if (l != null) {
                    try {
                        cache.getParentFile().mkdirs();
                        Files.write(cache.toPath(), bytes);
                    } catch (Throwable ignored) {}
                    return l;
                }
            }
        } catch (Throwable ignored) { /* offline / host down - fall through to the cache */ }
        // 2) fall back to the on-disk cache from a previous run
        try {
            if (cache.isFile()) {
                List<TaskData> l = GSON.fromJson(new String(Files.readAllBytes(cache.toPath()),
                        StandardCharsets.UTF_8), LIST_TYPE);
                if (l != null) return l;
            }
        } catch (Throwable ignored) {}
        return new ArrayList<>();
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
        all.addAll(server());     // v1.66: the admin-curated server set
        all.addAll(local);
        int added = 0;
        Set<String> seen = new HashSet<>();
        for (TaskData d : all) {
            if (d == null || d.id == null || have.contains(d.id) || !seen.add(d.id)) continue;
            DreamBotMenu.Task t = ProfileCodec.fromData(d);
            if (t == null) continue;
            // v1.66: ADMIN_ORIGIN survives from the server payload so the library can mark these
            // differently from the hardcoded set; anything else is a plain default.
            t.setOrigin(ADMIN_ORIGIN.equals(d.origin) ? ADMIN_ORIGIN : "default");
            menu.libraryAdd(t);
            added++;
        }
        if (added > 0)
            Logger.log(Logger.LogType.INFO, "[DefaultTasks] seeded " + added + " default task(s).");
        return added;
    }

    // ── v1.66: the server-side defaults library ──────────────────────────────────────────────
    //
    // Defaults used to be a JSON file an admin copied into the build by hand, which meant a new
    // default only reached users on the next release. They now live on the server: admins push a
    // task from the management tab, and every client picks it up on its next poll (or restart).

    /** Origin marker for a task an admin pushed, as opposed to one baked into the release. */
    public static final String ADMIN_ORIGIN = "default-admin";

    /** Last defaults version we successfully merged, so a poll that finds nothing new is free. */
    private static volatile int seenVersion = -1;
    private static volatile List<TaskData> serverCache;

    /** How often a running client re-checks for newly published defaults. */
    private static final long POLL_MINUTES = 30;

    /**
     * The admin-curated defaults from {@code GET /defaults}, cached under the sanctioned
     * scripts.path root so an offline start still seeds what the user had last time.
     */
    private static synchronized List<TaskData> server() {
        if (serverCache != null) return serverCache;
        File cache = new File(LocalStore.getRoot(), "cache/server-defaults.json");
        try {
            String json = new main.market.ServerAccount(
                    main.market.ServerAccount.session().baseUrl).fetchDefaultsJson();
            if (json != null && !json.isEmpty()) {
                DefaultsPayload p = GSON.fromJson(json, DefaultsPayload.class);
                if (p != null && p.tasks != null) {
                    seenVersion = p.version;
                    serverCache = p.tasks;
                    try {
                        cache.getParentFile().mkdirs();
                        Files.write(cache.toPath(), json.getBytes(StandardCharsets.UTF_8));
                    } catch (Throwable ignored) {}
                    return serverCache;
                }
            }
        } catch (Throwable ignored) { /* offline - fall through to the cache */ }
        try {
            if (cache.isFile()) {
                DefaultsPayload p = GSON.fromJson(new String(Files.readAllBytes(cache.toPath()),
                        StandardCharsets.UTF_8), DefaultsPayload.class);
                if (p != null && p.tasks != null) {
                    serverCache = p.tasks;
                    return serverCache;
                }
            }
        } catch (Throwable ignored) {}
        serverCache = new ArrayList<>();
        return serverCache;
    }

    /** Wire shape of {@code GET /defaults}. */
    private static final class DefaultsPayload {
        int version;
        List<TaskData> tasks;
    }

    /**
     * Starts the background poll: every {@value #POLL_MINUTES} minutes, ask the server for the
     * defaults version and merge only when it has moved. A daemon thread, so it never holds the
     * client open; every failure is silent by design (a defaults check is not worth a toast).
     */
    public static void startSync(DreamBotMenu menu, java.util.function.Supplier<
            List<DreamBotMenu.Task>> currentLibrary, Runnable onAdded) {
        if (menu == null || currentLibrary == null) return;
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(POLL_MINUTES * 60_000L);
                    int v = new main.market.ServerAccount(
                            main.market.ServerAccount.session().baseUrl).fetchDefaultsVersion();
                    if (v < 0 || v == seenVersion) continue;
                    synchronized (DefaultTasks.class) {
                        serverCache = null;      // force a refetch on the next merge
                        defaultIds = null;
                        local = null;
                    }
                    final int[] added = {0};
                    javax.swing.SwingUtilities.invokeAndWait(() ->
                            added[0] = mergeInto(menu, currentLibrary.get()));
                    if (added[0] > 0) {
                        Logger.log(Logger.LogType.INFO, "[DefaultTasks] " + added[0]
                                + " new default task(s) arrived from the server.");
                        if (onAdded != null) javax.swing.SwingUtilities.invokeLater(onAdded);
                    }
                } catch (InterruptedException ie) {
                    return;
                } catch (Throwable ignored) {
                    // offline, server down, or the menu went away - try again next cycle
                }
            }
        }, "DreamMan-defaults-sync");
        t.setDaemon(true);
        t.start();
    }
}
