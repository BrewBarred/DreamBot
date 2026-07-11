package main.watchers;

import main.actions.Action;

import java.util.ArrayList;
import java.util.List;

/**
 * A watcher (Patch B.4): "when {@link Condition} holds, run this response chain instead of
 * carrying on." Triggers live in two places:
 *
 * <ul>
 *   <li><b>Global</b> - checked between every action while the player is idle/safe. This is the
 *       "default set of background checks" (e.g. HP &lt; 15 → eat; prayer &lt; 10 → drink).</li>
 *   <li><b>Per-action</b> - attached to one action and checked right before/after it runs, so a
 *       Loot action can carry "if inventory full → bank" without touching any other step.</li>
 * </ul>
 *
 * A trigger owns a small list of {@link Action}s (its "emergency list"). When it fires, the
 * engine runs that chain to completion, then returns to whatever it was doing. A per-action
 * trigger can also be marked {@code replacesAction}: if it fires when its owner is about to run,
 * the response runs <i>instead</i> of the owner for that pass (your "bank/drop instead of loot
 * when the bag is full" case).
 */
public class Trigger {

    private Condition condition;
    private String arg = "";
    private final List<Action> response = new ArrayList<>();
    private boolean enabled = true;
    private boolean replacesAction = false;

    /** Minimum gap between fires (ms) so a still-true condition doesn't spam its chain. */
    private long cooldownMs = 3000;
    private transient long lastFiredAt = 0;

    // live run state for the response chain (transient)
    private transient boolean running = false;
    private transient int cursor = 0;

    public Trigger() {}

    public Trigger(Condition condition, String arg) {
        this.condition = condition;
        this.arg = arg == null ? "" : arg;
    }

    public Trigger(Trigger o) {
        this.condition = o.condition;
        this.arg = o.arg;
        this.enabled = o.enabled;
        this.replacesAction = o.replacesAction;
        this.cooldownMs = o.cooldownMs;
        for (Action a : o.response)
            if (a != null) response.add(a.copy());
    }

    // ── config ──
    public Condition getCondition() { return condition; }
    public void setCondition(Condition c) { this.condition = c; }
    public String getArg() { return arg; }
    public void setArg(String a) { this.arg = a == null ? "" : a; }
    public List<Action> getResponse() { return response; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean e) { this.enabled = e; }
    public boolean replacesAction() { return replacesAction; }
    public void setReplacesAction(boolean r) { this.replacesAction = r; }
    public long getCooldownMs() { return cooldownMs; }
    public void setCooldownMs(long ms) { this.cooldownMs = Math.max(0, ms); }

    /** True when the condition currently holds AND the cooldown has elapsed. Never throws. */
    public boolean shouldFire() {
        if (!enabled || condition == null) return false;
        if (System.currentTimeMillis() - lastFiredAt < cooldownMs && !running) return false;
        try { return condition.test(arg); } catch (Throwable t) { return false; }
    }

    /**
     * Advances the response chain one action per call (mirrors the engine's own stepping, so
     * pacing and pause-safety are identical). @return true when the whole chain has completed.
     */
    public boolean stepResponse() {
        if (response.isEmpty()) { running = false; return true; }
        if (!running) { running = true; cursor = 0; lastFiredAt = System.currentTimeMillis(); }

        if (cursor >= response.size()) { running = false; cursor = 0; return true; }

        Action a = response.get(cursor);
        if (a == null) { cursor++; return false; }
        if (a != lastStepped) { a.resetAttempts(); lastStepped = a; }

        if (a.execute()) {
            a.resetAttempts();
            cursor++;
        } else if (a.attemptsExhausted()) {
            // a response action gave up - don't wedge the watcher; abandon the chain for now
            a.resetAttempts();
            running = false;
            cursor = 0;
            lastStepped = null;
            return true;
        }

        if (cursor >= response.size()) {
            running = false;
            cursor = 0;
            lastStepped = null;
            return true;
        }
        return false;
    }

    private transient Action lastStepped;

    public boolean isRunning() { return running; }
    public void resetRun() { running = false; cursor = 0; lastStepped = null; }

    /** Short label for UI/overlay. */
    public String describe() {
        String when = condition == null ? "(no condition)" : condition.describe(arg);
        String then = response.isEmpty() ? "(no response)"
                : response.size() + (response.size() == 1 ? " action" : " actions");
        return (replacesAction ? "instead: " : "") + "if " + when + " → " + then;
    }
}
