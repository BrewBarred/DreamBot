package main.watchers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Runs checks between the bot's actions (Patch B.5 rewrite: CONCURRENT). The engine calls
 * {@link #service} once per loop with the always-on checks plus the current action's own.
 *
 * <p><b>Why concurrent matters:</b> if you're dying you need to run away AND eat - not run away,
 * arrive, and then eat over your own gravestone. This engine therefore:
 * <ul>
 *   <li>evaluates EVERY check every cycle (no idle gate on evaluation - a check must be able to
 *       fire mid-run), each still honouring its own cooldown;</li>
 *   <li>keeps ALL fired responses active simultaneously and steps EACH of them every service
 *       call, interleaving their actions fairly so no chain thread-locks the others;</li>
 *   <li>marks response actions as emergency (see Action.isEmergency()), letting inventory-style
 *       actions act while moving - eating mid-flee works.</li>
 * </ul>
 *
 * <p><b>Honest note on threads:</b> true background threads can't safely click the game - all
 * DreamBot input belongs to the script thread. Interleaving inside the loop achieves the same
 * outcome (several checks making progress in the same game tick window) without unsafe input or
 * thread-lock, which is what the "run away AND eat" case actually needs.
 */
public final class WatcherEngine {

    public enum Outcome {
        /** No check is active; run the normal queue action this loop. */
        IDLE,
        /** One or more responses are mid-run; the queue action was NOT run this loop. */
        RUNNING,
        /** A replacesAction check finished; skip the current queue action for this pass. */
        REPLACED
    }

    /** All responses currently mid-run (each Trigger holds its own chain cursor). */
    private final List<Trigger> active = new ArrayList<>();

    /**
     * @param globals   always-on checks (from the menu)
     * @param perAction the current action's attached checks (may be null)
     * @return what the caller should do with this loop
     */
    public Outcome service(List<Trigger> globals, List<Trigger> perAction) {
        // 1) evaluate everything that isn't already running - fired checks join the active set.
        //    Per-action first (more specific), then globals. Cooldowns are per-trigger.
        List<Trigger> candidates = new ArrayList<>();
        if (perAction != null) candidates.addAll(perAction);
        if (globals != null) candidates.addAll(globals);
        for (Trigger t : candidates) {
            if (t == null || active.contains(t)) continue;
            try {
                if (t.shouldFire()) active.add(t);
            } catch (Throwable ignored) {}
        }

        if (active.isEmpty())
            return Outcome.IDLE;

        // 2) step EVERY active response once - fair interleaving, nobody blocks anybody.
        boolean replacedFinished = false;
        Iterator<Trigger> it = active.iterator();
        while (it.hasNext()) {
            Trigger t = it.next();
            boolean done;
            try { done = t.stepResponse(); } catch (Throwable ex) { done = true; }
            if (done) {
                it.remove();
                if (t.replacesAction()) replacedFinished = true;
            }
        }

        if (replacedFinished)
            return Outcome.REPLACED;   // consume the queue action for this pass
        return active.isEmpty() ? Outcome.IDLE : Outcome.RUNNING;
    }

    /** True while any response chain is mid-run. */
    public boolean isBusy() { return !active.isEmpty(); }

    /** Abandons all in-progress responses (e.g. on Skip / queue edit). */
    public void reset() {
        for (Trigger t : active)
            try { t.resetRun(); } catch (Throwable ignored) {}
        active.clear();
    }
}
