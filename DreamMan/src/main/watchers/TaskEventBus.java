package main.watchers;

/**
 * v1.33: a tiny shared signal for task-position triggers (before/after a named task). The runner
 * posts a "task starting" event the moment a task becomes current (before its first action) and a
 * "task finished" event when a task completes; the BEFORE_TASK / AFTER_TASK conditions read these.
 *
 * <p>Each event stays "hot" for a short window so the between-actions condition check has a chance
 * to see it and fire the trigger's response. Conditions are stateless enums, so this static bus is
 * how they reach runtime task-flow state without a reference to the runner.
 */
public final class TaskEventBus {

    /** How long (ms) an event remains visible to the condition checks after it's posted. */
    private static final long WINDOW_MS = 2000;

    private static volatile String startingTask = null;
    private static volatile long   startingAt = 0;
    private static volatile String finishedTask = null;
    private static volatile long   finishedAt = 0;

    private TaskEventBus() {}

    public static void taskStarting(String name) {
        startingTask = name;
        startingAt = System.currentTimeMillis();
    }

    public static void taskFinished(String name) {
        finishedTask = name;
        finishedAt = System.currentTimeMillis();
    }

    /** True while the named task is in its just-starting window (case-insensitive). */
    public static boolean isStarting(String name) {
        return name != null && !name.isBlank()
                && name.trim().equalsIgnoreCase(startingTask)
                && System.currentTimeMillis() - startingAt < WINDOW_MS;
    }

    /** True while the named task is in its just-finished window (case-insensitive). */
    public static boolean isFinished(String name) {
        return name != null && !name.isBlank()
                && name.trim().equalsIgnoreCase(finishedTask)
                && System.currentTimeMillis() - finishedAt < WINDOW_MS;
    }

    /** Clears both events (called on stop / queue reset so stale events don't re-fire). */
    public static void clear() {
        startingTask = null; finishedTask = null;
        startingAt = 0; finishedAt = 0;
    }
}
