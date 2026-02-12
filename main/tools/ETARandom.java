package main.tools;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Random class re-skinned to fetch common values within fixed ranges for easy script development. Here, a new delay can
 * be defined every time one of these delays become inappropriate for a task. This centralizes all delays and allows for
 * fast script-wide adjustments. (So be careful changing these values!! You might break a script that relays on the timing!!).
 */
public final class ETARandom {
    private static final int MIN_LOW = 4738;
    private static final int MIN_HIGH = 12288;
    private static final int MAX_LOW = 62234;
    private static final int MAX_HIGH = 122349;
    /**
     * Return a random integer value between the two passed integer values.
     * Example usage includes the generation of a random delay between script actions.
     *
     * @param min The minimum value that can be returned
     * @param max The maximum value that can be returned
     * @return A random integer value between the passed minimum and maximum values.
     */
    public static int getRand(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /**
     * Return a random integer between 0 and the passed inclusive integer value.
     *
     * @param max The maximum inclusive value that can be returned
     * @return A random integer value between 0 and the passed maximum value.
     */
    public static int getRand(int max) {
        return ThreadLocalRandom.current().nextInt(max + 1);
    }

    /**
     * Return a random integer value between two preset 'low' values, intended for minimum delay times.
     *
     * @return A random integer value between two preset 'low' values as defined in ETARandom.java.
     */
    public static int getRandReallyShortDelayInt() {
        return ETARandom.getRand(MIN_LOW - 2000, MIN_HIGH - 8000);
    }

    /**
     * Return a random integer value between two random 'really low' values, ideal for miniscule pauses between quick
     * actions.
     * <p>
     * This number also happens to be super random which helps with realism ;)
     *
     * @return A really really short random integer value designed for delay purposes.
     */
    public static int getRandReallyReallyShortDelayInt() {
        return ETARandom.getRand(MIN_LOW - 4000, MIN_HIGH - 10000);
    }

    /**
     * Return a random integer value between two preset 'low' values, intended for minimum delay times.
     *
     * @return A random integer value between two preset 'low' values as defined in ETARandom.java.
     */
    public static int getRandShortDelayInt() {
        return ETARandom.getRand(MIN_LOW, MIN_HIGH);
    }

    /**
     * Return a random integer value between two preset 'high' values, intended for maximum delay times.
     *
     * @return A random integer value between two preset 'high' values as defined in ETARandom.java.
     */
    public static int getRandLongDelayInt() {
        return ETARandom.getRand(MAX_LOW, MAX_HIGH);
    }

    /**
     * Return a random double value between the two passed integer values.
     * Example usage includes the generation of a random delay between script actions.
     *
     * @param min The minimum value that can be returned
     * @param max The maximum value that can be returned
     * @return A random double value between the passed minimum and maximum values.
     */
    public static double getRand(double min, double max) {
        return ThreadLocalRandom.current().nextDouble(min, max + 1);
    }

    /**
     * Return a random double value between 0 and the passed integer value.
     *
     * @param max The maximum value that can be returned
     * @return A random value double between the passed minimum and maximum values.
     */
    public static double getRand(double max) {
        return ThreadLocalRandom.current().nextDouble(max + 1);
    }
}
