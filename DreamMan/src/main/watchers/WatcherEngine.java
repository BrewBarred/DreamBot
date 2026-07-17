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
        REPLACED,
        /** v1.33 control signals emitted when a finished trigger carries a Control. */
        SKIP_NEXT,
        RESTART_LAST,
        RESTART_TASK,
        RESTART_QUEUE
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

        // 2) step active responses, honouring CONFLICT GROUPS (Patch B.8). Independent actions
        //    (null group - eat, chat, wait...) all step this cycle. But two responses whose
        //    current actions share a non-null group physically clash (can't walk to two places,
        //    can't drive the bank from two chains, can't both free the same inventory) - so only
        //    the HIGHER-PRIORITY one (earlier in the active list = earlier in your check list)
        //    steps this cycle; the others wait their turn. By the time the winner finishes, the
        //    condition may no longer hold, so the loser simply won't fire - which is exactly the
        //    "bank OR bury on full, not both" behaviour, decided by the order you arrange checks.
        boolean replacedFinished = false;
        Trigger.Control pendingControl = Trigger.Control.NONE;   // v1.33: strongest wins
        java.util.Set<String> claimedGroups = new java.util.HashSet<>();
        Iterator<Trigger> it = active.iterator();
        while (it.hasNext()) {
            Trigger t = it.next();

            // does this trigger's NEXT response action clash with one already stepped this cycle?
            String group = null;
            try {
                main.actions.Action next = t.currentResponseAction();
                if (next != null) group = next.conflictGroup();
            } catch (Throwable ignored) {}
            if (group != null && !claimedGroups.add(group))
                continue;   // a higher-priority check already owns this group this cycle - wait

            boolean done;
            try { done = t.stepResponse(); } catch (Throwable ex) { done = true; }
            if (done) {
                it.remove();
                if (t.replacesAction()) replacedFinished = true;
                pendingControl = stronger(pendingControl, t.getControl());   // v1.33
            }
        }

        // v1.33: a finished trigger's Control reshapes the queue - takes precedence over REPLACED.
        switch (pendingControl) {
            case RESTART_QUEUE: return Outcome.RESTART_QUEUE;
            case RESTART_TASK:  return Outcome.RESTART_TASK;
            case SKIP_NEXT:     return Outcome.SKIP_NEXT;
            case RESTART_LAST:  return Outcome.RESTART_LAST;
            default: break;
        }
        if (replacedFinished)
            return Outcome.REPLACED;   // consume the queue action for this pass
        return active.isEmpty() ? Outcome.IDLE : Outcome.RUNNING;
    }

    /** Precedence when several finish the same cycle: restarting the queue is the most drastic. */
    private static Trigger.Control stronger(Trigger.Control a, Trigger.Control b) {
        return rank(b) > rank(a) ? b : a;
    }
    private static int rank(Trigger.Control c) {
        switch (c) {
            case RESTART_QUEUE: return 4;
            case RESTART_TASK:  return 3;
            case SKIP_NEXT:     return 2;
            case RESTART_LAST:  return 1;
            default:            return 0;
        }
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
