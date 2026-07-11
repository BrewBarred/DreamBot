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

    /** Copies attached triggers from another action - call this in every subclass copy ctor. */
    protected final void copyTriggersFrom(Action other) {
        if (other == null) return;
        triggers.clear();
        for (main.watchers.Trigger t : other.getTriggers())
            if (t != null) triggers.add(new main.watchers.Trigger(t));
    }

    /** Reserved key under which attached triggers ride inside serialize()/deserialize(). */
    public static final String TRIGGERS_KEY = "__triggers";

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