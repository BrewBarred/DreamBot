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

    /**
     * v1.33: what the QUEUE should do after this trigger's response finishes (on top of running
     * the response actions). Lets a trigger reshape execution flow, not just do a side-task.
     */
    public enum Control {
        NONE,           // just run the response, carry on
        SKIP_NEXT,      // skip the next queued action
        RESTART_LAST,   // re-run the action the queue was on
        RESTART_TASK,   // restart the current task from its first action
        RESTART_QUEUE   // jump the whole queue back to the first task
    }
    private Control control = Control.NONE;

    /**
     * v1.33: additional ANDed conditions. The trigger fires only when the primary condition AND
     * every extra clause hold. Each clause can be negated (the "NOT" you wanted), so you can
     * express "low HP AND NOT holding food -> bank" as primary=HP_BELOW plus a negated
     * INVENTORY_CONTAINS clause. Empty by default, so existing single-condition triggers are
     * unchanged.
     */
    public static final class Clause {
        public Condition condition;
        public String arg = "";
        public boolean negate = false;   // true = the condition must be FALSE
        /** v1.50: connector to the PREVIOUS term - false = AND, true = OR. Evaluated with
         *  code-style precedence: AND binds tighter than OR (x AND y OR z = (x AND y) OR z). */
        public boolean or = false;
        public Clause() {}
        public Clause(Condition c, String a, boolean n) {
            condition = c; arg = a == null ? "" : a; negate = n;
        }
        public Clause(Condition c, String a, boolean n, boolean orConn) {
            this(c, a, n); this.or = orConn;
        }
        /** Whether this clause is currently satisfied (negation applied). Never throws. */
        public boolean holds() {
            if (condition == null) return true;   // a blank clause doesn't block
            boolean r;
            try { r = condition.test(arg); } catch (Throwable t) { r = false; }
            return negate != r;   // XOR: negate flips the result
        }
    }
    private final List<Clause> extraClauses = new ArrayList<>();
    public List<Clause> getExtraClauses() { return extraClauses; }

    /** Minimum gap between fires (ms) so a still-true condition doesn't spam its chain. */
    private long cooldownMs = 3000;
    private transient long lastFiredAt = 0;

    // ── v1.30: whole-check randomness ────────────────────────────────────────
    /**
     * Chance (1-100) that this check fires when it otherwise would. 100 = always (default).
     * At, say, 10%, each eligible moment rolls once; a miss CONSUMES that moment (the
     * cooldown/timer window restarts), so "bury bones at 10%" really does skip ~9 in 10
     * full-inventory moments instead of just delaying a few seconds.
     */
    private int chancePercent = 100;

    // ── v1.30: run-every timer ───────────────────────────────────────────────
    /** Minimum interval for the timer to engage at all: 5 seconds. */
    public static final long MIN_TIMER_MS = 5_000L;
    /** When true (and the interval is >= {@link #MIN_TIMER_MS}), the check fires at most once per interval. */
    private boolean timerEnabled = false;
    /** The "only every h/m/s" interval in ms. Below MIN_TIMER_MS the timer is inert. */
    private long timerIntervalMs = 0;

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
        this.chancePercent = o.chancePercent;       // v1.30
        this.timerEnabled = o.timerEnabled;         // v1.30
        this.timerIntervalMs = o.timerIntervalMs;   // v1.30
        for (Action a : o.response)
            if (a != null) response.add(a.copyDeep());
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
    public Control getControl() { return control == null ? Control.NONE : control; }
    public void setControl(Control c) { this.control = c == null ? Control.NONE : c; }
    /** v1.50: this trigger is an ELSE-IF of the trigger above it in the list - it is only
     *  considered when no earlier trigger in its chain fired this evaluation cycle. */
    private boolean chainedElse = false;
    public boolean isChainedElse() { return chainedElse; }
    public void setChainedElse(boolean b) { this.chainedElse = b; }
    public long getCooldownMs() { return cooldownMs; }
    public void setCooldownMs(long ms) { this.cooldownMs = Math.max(0, ms); }

    // ── v1.30 config ──
    public int getChancePercent() { return chancePercent; }
    public void setChancePercent(int pct) { this.chancePercent = Math.max(1, Math.min(100, pct)); }
    public boolean isTimerEnabled() { return timerEnabled; }
    public long getTimerIntervalMs() { return timerIntervalMs; }
    public void setTimer(boolean enabled, long intervalMs) {
        this.timerEnabled = enabled;
        this.timerIntervalMs = Math.max(0, intervalMs);
    }
    /** True only when the timer is on AND long enough (>= 5s) to actually function. */
    public boolean timerActive() { return timerEnabled && timerIntervalMs >= MIN_TIMER_MS; }

    /**
     * True when the condition currently holds, the cooldown AND run-every timer (v1.30) have
     * both elapsed, and the chance roll (v1.30) passes. Never throws.
     *
     * <p>Order matters: gates first, the roll LAST - and a failed roll stamps
     * {@code lastFiredAt}, consuming the whole window. Rolling on every poll instead would let
     * a "10%" check fire within a few seconds anyway (10% per poll compounds); consuming the
     * window is what makes 10% mean "about 1 in 10 opportunities".
     */
    public boolean shouldFire() {
        if (!enabled || condition == null) return false;
        long now = System.currentTimeMillis();
        if (!running) {
            if (now - lastFiredAt < cooldownMs) return false;
            if (timerActive() && now - lastFiredAt < timerIntervalMs) return false;
        }
        // v1.50: code-style evaluation with precedence - AND binds tighter than OR. The primary
        // condition is the first term; each extra clause carries its connector (AND / OR) to the
        // previous term. "x AND y OR z" evaluates as "(x AND y) OR z", exactly as it would in code.
        boolean primary;
        try { primary = condition.test(arg); } catch (Throwable t) { primary = false; }
        boolean orAccum = false;      // OR of the completed AND-groups so far
        boolean andAccum = primary;   // the AND-group currently being built
        for (Clause c : extraClauses) {
            if (c == null || c.condition == null) continue;
            if (c.or) {               // OR closes the current AND-group and starts a new one
                orAccum = orAccum || andAccum;
                andAccum = c.holds();
            } else {                  // AND extends the current group
                andAccum = andAccum && c.holds();
            }
        }
        if (!(orAccum || andAccum)) return false;
        if (!running && chancePercent < 100
                && java.util.concurrent.ThreadLocalRandom.current().nextInt(100) >= chancePercent) {
            lastFiredAt = now;   // roll missed: this opportunity is spent, wait out the window
            return false;
        }
        return true;
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

        a.setEmergency(true);   // Patch B.5: responses may act mid-run (eat while fleeing)
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

    /**
     * The response action this trigger would step next (Patch B.8), without advancing it - lets
     * the engine check its conflict group before deciding whether to run it this cycle. Null when
     * the chain is finished or empty.
     */
    public main.actions.Action currentResponseAction() {
        if (response.isEmpty()) return null;
        int i = running ? cursor : 0;
        return (i >= 0 && i < response.size()) ? response.get(i) : null;
    }

    /** Short label for UI/overlay. */
    public String describe() {
        String when = condition == null ? "(no condition)" : condition.describe(arg);
        String then = response.isEmpty() ? "(no response)"
                : response.size() + (response.size() == 1 ? " action" : " actions");
        String prefix = "";
        if (chancePercent < 100) prefix += "~" + chancePercent + "% ";           // v1.30
        if (timerActive()) prefix += "every " + formatInterval(timerIntervalMs) + " ";  // v1.30
        return prefix + (replacesAction ? "instead: " : "") + "if " + when + " -> " + then;
    }

    /** "1h 05m 30s"-style compact interval, dropping zero parts (v1.30). */
    public static String formatInterval(long ms) {
        long total = Math.max(0, ms / 1000);
        long h = total / 3600, m = (total % 3600) / 60, s = total % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.length() == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }
}
