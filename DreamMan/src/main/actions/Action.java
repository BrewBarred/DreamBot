package main.actions;

import main.components.JParamTextField;
import org.dreambot.api.methods.map.Tile;

import javax.swing.*;

import java.awt.*;
import java.util.Map;

import static main.menu.MenuHandler.*;

public abstract class Action {
    public JParamTextField paramTarget;

    // ── Attempt tracking (Patch B.2) ─────────────────────────────────────────
    // Actions call noteAttempt() when a try genuinely fails (nothing in range, click failed,
    // couldn't take an item...). The engine skips the task once attemptsExhausted() - so a task
    // only fails when it really can't be completed. Soft retries (a cow someone else grabbed,
    // with other cows available) should NOT call noteAttempt(): pick another target instead.
    // Attempts reset automatically when the engine sees the action complete or re-enter.
    private transient int attempts = 0;
    /** Max failed tries before the task is considered impossible; <=0 means retry forever. */
    protected int maxAttempts = 12;

    /** Records one genuinely failed try. */
    protected final void noteAttempt() { attempts++; }
    /** @return failed tries since the last reset. */
    public final int getAttempts() { return attempts; }
    /** @return true when this action has used up its tries (and doesn't retry forever). */
    public final boolean attemptsExhausted() { return maxAttempts > 0 && attempts >= maxAttempts; }
    /** Called by the engine when the action completes or is (re-)entered. */
    public final void resetAttempts() { attempts = 0; }
    /** @return the configured try budget (<=0 = infinite). */
    public final int getMaxAttempts() { return maxAttempts; }

    // ── Attached watchers (Patch B.4) ────────────────────────────────────────
    // Triggers bound to THIS action: checked by the engine when this action is the current one.
    // A trigger marked replacesAction() runs its response INSTEAD of this action for that pass
    // (e.g. Loot carries "if inventory full -> bank"); others run alongside as safety chains.
    // The list travels inside serialize()/deserialize() under the reserved "__triggers" key, so
    // every existing action persists its watchers for free without changing the JSON schema.
    private final java.util.List<main.watchers.Trigger> triggers = new java.util.ArrayList<>();

    /** Watchers attached to this action (mutable). */
    public java.util.List<main.watchers.Trigger> getTriggers() { return triggers; }

    /**
     * Copies attached triggers AND the chance-to-run from another action - call this in every
     * subclass copy ctor (Patch B.7: chance folded in here so no copy ctor needs updating).
     */
    protected final void copyTriggersFrom(Action other) {
        if (other == null) return;
        triggers.clear();
        for (main.watchers.Trigger t : other.getTriggers())
            if (t != null) triggers.add(new main.watchers.Trigger(t));
        this.chancePercent = other.chancePercent;
    }

    /** Reserved key under which attached triggers ride inside serialize()/deserialize(). */
    public static final String TRIGGERS_KEY = "__triggers";

    // ── Chance to run (Patch B.7) ────────────────────────────────────────────
    // A per-action probability, 1..100. 100 = guaranteed (the default, so nothing changes unless
    // you dial it down). When the engine reaches this action it rolls once; on a miss the action
    // is skipped for this pass and the task moves on - a cheap way to sprinkle in niche/human
    // detours that only happen, say, 30% or 5% of the time. Rides in the params under the
    // reserved key below, so every action persists it without touching each serialize().
    private int chancePercent = 100;

    public final int getChancePercent() { return chancePercent; }
    public final void setChancePercent(int pct) {
        this.chancePercent = Math.max(1, Math.min(100, pct));
    }
    /** @return true if this action should run this pass (always true at 100%). */
    public final boolean rollChance() {
        if (chancePercent >= 100) return true;
        return java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < chancePercent;
    }

    /**
     * Conflict group (Patch B.8). Two concurrently-firing checks whose CURRENT response actions
     * share the same non-null group physically clash - you can't walk to two places at once, or
     * drive the bank interface from two chains at the same time. The check engine lets same-group
     * actions take turns (higher-priority check first, by list order) while letting independent
     * actions (null group - eat, chat, wait...) run alongside freely.
     *
     * <p>Return null (the default) for actions that don't contend for a shared resource. Override
     * with a short stable tag for those that do.
     */
    public String conflictGroup() { return null; }

    /**
     * Patch B.17: whether this action's target is a PLACE rather than a THING. Drives what the
     * Task Builder's entity list auto-fills on selection: tile-targeted actions (Walk today;
     * Dig and friends tomorrow) receive the entity's "X, Y, Z", everything else gets the name.
     * Default false - override in location-based actions.
     */
    public boolean prefersTileTarget() { return false; }

    /**
     * Patch B.17: whether this action has a meaningful entity target at all. Actions like Wait
     * or Chat return false so clicking the entity list never scribbles a cow's name into a
     * milliseconds field. Default true.
     */
    public boolean acceptsEntityTarget() { return true; }

    /** Reserved key under which the chance-to-run rides inside serialize()/deserialize(). */
    public static final String CHANCE_KEY = "__chance";

    /**
     * Patch B.5: set while this action runs as a CHECK response. Emergency actions may act even
     * when the player isn't idle (e.g. eat while running away) - the whole point of a check like
     * "HP low" is that it can't wait for the current movement to finish.
     */
    private transient boolean emergency = false;
    public final void setEmergency(boolean e) { this.emergency = e; }
    public final boolean isEmergency() { return emergency; }

    /**
     * Folds attached triggers into a params map (call at the end of serialize()). Kept separate
     * so subclasses opt in explicitly; the engine and codec also handle it centrally.
     */
    protected final void writeTriggers(java.util.Map<String, String> m) {
        if (m == null || triggers.isEmpty()) return;
        m.put(TRIGGERS_KEY, main.watchers.TriggerCodec.toJson(triggers));
    }

    /** Restores attached triggers from a params map (call at the start of deserialize()). */
    protected final void readTriggers(java.util.Map<String, String> m) {
        triggers.clear();
        if (m == null) return;
        String json = m.get(TRIGGERS_KEY);
        if (json != null && !json.isBlank())
            triggers.addAll(main.watchers.TriggerCodec.fromJson(json));
    }

    ///
    ///     Every subclass must implement these:
    ///
    public abstract boolean execute();
    public abstract String getParamTarget();
    public abstract Action copy();

    /**
     * Deep copy that ALSO carries the cross-cutting state every action has - the chance-to-run
     * and any attached checks (Patch B.7). Subclass copy() ctors vary in whether they remember
     * to copy these; routing copies through here makes it uniform, so a task copy never silently
     * drops an action's randomness or its checks. Prefer this over copy() when duplicating an
     * action for the queue/library.
     */
    public final Action copyDeep() {
        Action c = copy();
        if (c != null) {
            c.setChancePercent(this.chancePercent);
            c.getTriggers().clear();
            for (main.watchers.Trigger t : this.triggers)
                if (t != null) c.getTriggers().add(new main.watchers.Trigger(t));
        }
        return c;
    }
    public abstract Map<String, String> serialize();
    public abstract void deserialize(Map<String, String> data);

    ///
    ///     Constructor
    ///
    public Action() {
        super();
    }

    ///
    ///     Getters/setters
    ///
    public String getName() {
        return this.getClass().getSimpleName();
    }

    ///  Getters/setters
    public Class<? extends Action> getType() {
        return this.getClass();
    }

    /**
     * Returns a JPanel containing both the compulsory AND dynamic controls for the selected {@link Action} in the
     * {@link main.menu.components.JActionSelector}.
     */
    public JPanel getParamPanel() {
        JPanel paramPanel = new JPanel(new BorderLayout());
        JPanel dynamicPanel = createParamPanel();

        paramPanel.add(dynamicPanel, BorderLayout.CENTER);

        styleComp(paramPanel);

        return paramPanel;
    }

    public abstract JPanel createParamPanel();

    ///  Helper functions
    public static Tile parseStringIntoTile(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }

        // Remove any characters that aren't digits, commas, or spaces
        // This allows for formats like "(3222, 3218)" or "3222 3218 0"
        String cleaned = input.replaceAll("[^0-9, ]", "").trim();

        // Split by comma or space
        String[] parts = cleaned.split("[, ]+");

        try {
            if (parts.length == 2) {
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);

                if (isValid(x, y, 0)) {
                    return new Tile(x, y, 0);
                }

            } else if (parts.length == 3) {
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);

                if (isValid(x, y, z)) {
                    return new Tile(x, y, z);
                }
            }
        } catch (NumberFormatException e) {
            return null;
        }

        return null;
    }

    private static boolean isValid(int x, int y, int z) {
        return x >= 0 && x <= 16383 &&
                y >= 0 && y <= 16383 &&
                z >= 0 && z <= 3;
    }

    public String toBuildString() {
        return this + " → " + getParamTarget();
    }

    @Override
    public final String toString() {
        return getClass().getSimpleName();
    }
}