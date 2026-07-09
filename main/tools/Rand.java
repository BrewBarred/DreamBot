package main.tools;

import java.security.SecureRandom;

/**
 * Static secure-random helpers for delays and jitter.
 *
 * <p>FIX: the RNG was previously created only inside the (never-called) constructor, so every
 * static {@code nextInt(...)} call hit a null field and threw, which made anything relying on it
 * (e.g. the Wait action's delay) fail silently and appear to "hang forever". It's now initialised
 * in a static block so the static methods work without anyone constructing the class.
 *
 * <p>Also switched from {@link SecureRandom#getInstanceStrong()} (which can BLOCK waiting for OS
 * entropy - a real hazard for a bot generating constant delays) to a plain {@link SecureRandom},
 * which is non-blocking and more than random enough for humanising timings.
 */
public class Rand {

    private static final SecureRandom secureRandom = new SecureRandom();

    /** Kept for backward compatibility; no longer required to initialise the RNG. */
    public Rand() {
        super();
    }

    /** Secure random int across the full int range. */
    public static int nextInt() {
        return secureRandom.nextInt();
    }

    /** Secure random int from 0 (inclusive) to max (inclusive). */
    public static int nextInt(int max) {
        if (max < 0)
            throw new IllegalArgumentException("max must be >= 0");
        return secureRandom.nextInt(max + 1);
    }

    /** Secure random int from min (inclusive) to max (inclusive). */
    public static int nextInt(int min, int max) {
        if (min > max)
            throw new IllegalArgumentException("min must be <= max");
        return min + secureRandom.nextInt((max - min) + 1);
    }

    /** Quick jitter - tiny random delay for tight action sequences (~60-220ms). */
    public static int quickDelay() {
        return nextInt(60, 220);
    }

    /** A short, human-looking pause between actions (~300-900ms). */
    public static int pauseDelay() {
        return nextInt(300, 900);
    }

    /**
     * A longer semi-AFK delay you can scale to mimic looking away / multitasking. The base is
     * ~1.8-4.2s; multiply for longer AFKs (e.g. afkDelay(3) ≈ 5.4-12.6s).
     */
    public static int afkDelay(int multiplier) {
        return nextInt(1800, 4200) * Math.max(1, multiplier);
    }

    /** A realistic delay between actions (randomised min AND max for extra spread). */
    public static int actionDelay() {
        return nextInt(
                // lowest min -> highest min
                nextInt(324, 564),
                // lowest max -> highest max
                nextInt(1478, 3294)
        );
    }
}
