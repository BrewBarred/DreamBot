package main.watchers;

import main.actions.Action;
import main.actions.ActionUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs watchers between the bot's actions (Patch B.4). The engine calls {@link #service} once
 * per loop, BEFORE the current task-action runs, with the set of triggers that apply right now
 * (the always-on globals plus the current action's own). Only when the player is idle/safe does
 * it evaluate conditions - so a trip never cuts off a click already in flight.
 *
 * <p>When a trigger fires, its response chain is stepped one action per loop, exactly like the
 * main queue, until it finishes; the engine holds the queue in place meanwhile. A firing trigger
 * marked {@code replacesAction} tells the caller (via {@link Outcome#REPLACED}) to skip the
 * current queue action for this pass - the "bank/drop instead of loot when the bag is full" case.
 */
public final class WatcherEngine {

    public enum Outcome {
        /** No watcher is active; run the normal queue action this loop. */
        IDLE,
        /** A watcher response is mid-run; the queue action was NOT run this loop. */
        RUNNING,
        /** A replacesAction watcher fired; skip the current queue action for this pass. */
        REPLACED
    }

    /** The watcher currently running its response chain (null when none). */
    private Trigger active;
    /** True when the active trigger was a replacesAction one. */
    private boolean activeReplaces;

    /**
     * @param globals   always-on triggers (from the menu)
     * @param perAction the current action's attached triggers (may be null)
     * @return what the caller should do with this loop
     */
    public Outcome service(List<Trigger> globals, List<Trigger> perAction) {
        // continue a response already in progress
        if (active != null) {
            boolean done = active.stepResponse();
            if (!done) return Outcome.RUNNING;
            boolean wasReplace = activeReplaces;
            active = null;
            activeReplaces = false;
            // a completed replace still consumes this loop; next loop re-evaluates fresh
            return wasReplace ? Outcome.REPLACED : Outcome.RUNNING;
        }

        // only start firing when safe (idle) - never interrupt an in-flight action
        if (!ActionUtil.isIdle())
            return Outcome.IDLE;

        // per-action triggers first (more specific), then globals
        List<Trigger> ordered = new ArrayList<>();
        if (perAction != null) ordered.addAll(perAction);
        if (globals != null) ordered.addAll(globals);

        for (Trigger t : ordered) {
            if (t == null || !t.shouldFire()) continue;
            active = t;
            activeReplaces = t.replacesAction();
            boolean done = t.stepResponse();     // kick the first step immediately
            if (done) {
                active = null;
                activeReplaces = false;
                return t.replacesAction() ? Outcome.REPLACED : Outcome.RUNNING;
            }
            return Outcome.RUNNING;
        }
        return Outcome.IDLE;
    }

    /** True while a response chain is mid-run. */
    public boolean isBusy() { return active != null; }

    /** Abandons any in-progress response (e.g. on Skip / queue edit). */
    public void reset() {
        if (active != null) active.resetRun();
        active = null;
        activeReplaces = false;
    }
}
