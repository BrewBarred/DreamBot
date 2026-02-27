package main.tools;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class Rand {
    private static SecureRandom secureRandom = null;

    public Rand() {
        SecureRandom temp;
        try {
            temp = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            // Fallback to default if strong algorithm isn't available
            temp = new SecureRandom();
        }
        secureRandom = temp;
    }

    /**
     * Returns a secure random int (full int range).
     *
     * @return A {@link SecureRandom} integer value between 0 and {@link Integer#MAX_VALUE}.
     */
    public static int nextInt() {
        return secureRandom.nextInt();
    }

    /**
     * Returns a secure random int from 0 (inclusive) to max (inclusive).
     *
     * @param max The highest possible value.
     * @return A {@link SecureRandom} integer value between 0 and max (inclusive).
     */
    public static int nextInt(int max) {
        if (max < 0) {
            throw new IllegalArgumentException("max must be >= 0");
        }

        return secureRandom.nextInt(max + 1);
    }

    /**
     * Returns a secure random int from the min (inclusive) value to the max (inclusive) value.
     *
     * @param min The lowest possible value.
     * @param max The highest possible value.
     * @return A {@link SecureRandom} integer value between min and max (inclusive).
     */
    public static int nextInt(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min must be <= max");
        }
        return min + secureRandom.nextInt((max - min) + 1);
    }

    public static int actionDelay() {
        return nextInt(
                // lowest min -> highest min
                nextInt(324, 564),
                // lowest max -> highest max
                nextInt(1478, 3294)
        );
    }

//    /**
//     * Returns a secure random byte array of the given length.
//     */
//    public byte[] nextBytes(int length) {
//        byte[] bytes = new byte[length];
//        secureRandom.nextBytes(bytes);
//        return bytes;
//    }
}
