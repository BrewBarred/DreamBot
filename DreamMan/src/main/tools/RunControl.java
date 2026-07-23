package main.tools;

import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.walking.impl.Walking;

/**
 * How the bot moves (v1.89) — the engine behind the Walk action's movement dropdown.
 *
 * <p>Four modes, in plain terms:
 * <ul>
 *   <li><b>Auto</b> — don't touch anything. Whatever the client is doing, it keeps doing. This
 *       is the default so every task saved before v1.89 behaves exactly as it did.</li>
 *   <li><b>Force walk</b> — run is turned OFF and kept off. For anything where speed isn't the
 *       point and burning energy is wasteful.</li>
 *   <li><b>Auto-run</b> — run is turned ON once there's a sensible amount of energy
 *       ({@value #AUTO_RUN_ON_AT}%) and simply left alone as it drains. The natural default for
 *       long trips: you run while you can and walk when you can't, with no toggle spam.</li>
 *   <li><b>Force run</b> — run every step that's physically possible. Run is re-enabled the
 *       instant there's any energy at all, and stamina / energy potions in the inventory are
 *       drunk to keep it going. This burns supplies on purpose; it's the "get there now" mode.</li>
 * </ul>
 *
 * <p><b>Everything here is rate-limited and guarded.</b> Toggling run is a real click in the
 * client, so a mode that fought the game every tick would look far more robotic than one that
 * never ran at all — which is why each action has a cooldown and every API read is wrapped.
 */
public final class RunControl {

    private RunControl() {}

    /** The movement modes, in the order the dropdown shows them. */
    public enum Mode {
        AUTO("Auto (leave it alone)"),
        FORCE_WALK("Force walk (run off)"),
        AUTO_RUN("Auto-run (run when energy allows)"),
        FORCE_RUN("Force run (drain to 0, drink potions)");

        public final String label;
        Mode(String label) { this.label = label; }

        /** Parses a saved value or a dropdown label back to a mode; unknown text = AUTO. */
        public static Mode from(String s) {
            if (s == null) return AUTO;
            String v = s.trim().toLowerCase(java.util.Locale.ROOT);
            if (v.isEmpty()) return AUTO;
            for (Mode m : values())
                if (v.equals(m.name().toLowerCase(java.util.Locale.ROOT))
                        || v.equals(m.label.toLowerCase(java.util.Locale.ROOT))) return m;
            // forgiving matches, so a hand-edited profile still reads sensibly
            if (v.startsWith("force w") || v.equals("walk")) return FORCE_WALK;
            if (v.startsWith("force r")) return FORCE_RUN;
            if (v.startsWith("auto-r") || v.startsWith("auto r") || v.equals("run")) return AUTO_RUN;
            return AUTO;
        }
    }

    /** Auto-run waits for this much energy before switching run on, to avoid toggle spam. */
    public static final int AUTO_RUN_ON_AT = 20;
    /** Below this, Force run starts looking for something to drink. */
    private static final int POTION_AT = 40;
    /** Minimum gap between run toggles (ms) - a click, not a setting. */
    private static final long TOGGLE_COOLDOWN_MS = 1200;
    /** Minimum gap between potion sips (ms) - one sip needs time to register. */
    private static final long SIP_COOLDOWN_MS = 2500;

    private static volatile long lastToggleAt;
    private static volatile long lastSipAt;

    /**
     * Names checked, in order, when Force run wants a drink. Stamina first (it's the one people
     * bring for travel and it restores the most useful amount), then the energy potions from
     * strongest to weakest. Doses are matched by prefix so "(4)" through "(1)" all work.
     */
    private static final String[] DRINKABLE = {
            "Stamina potion", "Super energy", "Energy potion",
    };

    /**
     * Applies a movement mode. Safe to call every loop: it's rate-limited internally and does
     * nothing at all in {@link Mode#AUTO}.
     *
     * @return true when this call actually changed something (toggled run or drank)
     */
    public static boolean apply(Mode mode) {
        if (mode == null || mode == Mode.AUTO) return false;
        try {
            boolean running = Walking.isRunEnabled();
            int energy = Walking.getRunEnergy();

            switch (mode) {
                case FORCE_WALK:
                    return running && toggle();

                case AUTO_RUN:
                    // Turn it on once, when there's enough to be worth it. Deliberately does
                    // NOT turn it back off as energy drains - that's the client's job and
                    // fighting it produces a toggle every few seconds.
                    return !running && energy >= AUTO_RUN_ON_AT && toggle();

                case FORCE_RUN:
                    // Drink first: getting energy back is what makes running possible again.
                    if (energy < POTION_AT && sip()) return true;
                    // Any energy at all is enough - this mode runs the tank to zero on purpose
                    // and re-enables the moment regeneration gives it something to work with.
                    return !running && energy > 0 && toggle();

                default:
                    return false;
            }
        } catch (Throwable ignored) {
            return false;   // not logged in, or a mid-hop read - try again next loop
        }
    }

    /** Flips run, respecting the cooldown. @return true if a toggle was actually issued. */
    private static boolean toggle() {
        long now = System.currentTimeMillis();
        if (now - lastToggleAt < TOGGLE_COOLDOWN_MS) return false;
        lastToggleAt = now;
        try { return Walking.toggleRun(); } catch (Throwable t) { return false; }
    }

    /**
     * Drinks the best available energy restore. @return true if a sip was issued.
     *
     * <p>Nothing to drink is a perfectly normal outcome - Force run without potions simply
     * means running until empty and resuming as energy trickles back, which is exactly what it
     * promises.
     */
    private static boolean sip() {
        long now = System.currentTimeMillis();
        if (now - lastSipAt < SIP_COOLDOWN_MS) return false;
        for (String prefix : DRINKABLE) {
            String found = findDose(prefix);
            if (found == null) continue;
            try {
                if (Inventory.interact(found, "Drink")) {
                    lastSipAt = now;
                    main.tools.ChatLog.note("run", "Drank " + found + " to keep running.");
                    return true;
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    /** The exact inventory name of a potion whose name starts with {@code prefix}, or null. */
    private static String findDose(String prefix) {
        try {
            for (Object o : Inventory.all()) {
                if (o == null) continue;
                String name = nameOf(o);
                if (name != null && name.toLowerCase(java.util.Locale.ROOT)
                        .startsWith(prefix.toLowerCase(java.util.Locale.ROOT)))
                    return name;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Item.getName() via reflection - the wrapper type has moved between client builds. */
    private static String nameOf(Object item) {
        try {
            java.lang.reflect.Method m = item.getClass().getMethod("getName");
            Object v = m.invoke(item);
            return v == null ? null : String.valueOf(v);
        } catch (Throwable t) {
            return null;
        }
    }
}
