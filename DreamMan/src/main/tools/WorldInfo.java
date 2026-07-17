package main.tools;

import java.lang.reflect.Method;

/**
 * Current-world lookup via reflection (v1.31 hotfix). {@code WorldHopper.getCurrentWorld()}
 * doesn't exist on the user's DreamBot build; the number lives somewhere else depending on
 * version (Client, or the Worlds wrapper). This probes the options once and caches the winner.
 * {@link #currentWorld()} returns 0 when no accessor exists - callers must treat 0 as
 * "unknown", not as a world.
 */
public final class WorldInfo {

    private WorldInfo() {}

    private static volatile boolean resolved;
    private static Method mClientCurrent;      // static int Client.getCurrentWorld()
    private static Method mWorldsGetCurrent;   // static ? Worlds.getCurrent()
    private static Method mWorldNumber;        // instance int on the world wrapper

    private static synchronized void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            Class<?> client = Class.forName("org.dreambot.api.Client");
            for (String n : new String[]{"getCurrentWorld", "getWorld"}) {
                try {
                    Method m = client.getMethod(n);
                    if (m.getReturnType() == int.class) { mClientCurrent = m; return; }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        try {
            Class<?> worlds = Class.forName("org.dreambot.api.methods.world.Worlds");
            mWorldsGetCurrent = worlds.getMethod("getCurrent");
        } catch (Throwable ignored) {}
    }

    /** The current world number, or 0 when this build offers no way to read it. */
    public static int currentWorld() {
        resolve();
        try {
            if (mClientCurrent != null) return (int) mClientCurrent.invoke(null);
            if (mWorldsGetCurrent != null) {
                Object w = mWorldsGetCurrent.invoke(null);
                if (w == null) return 0;
                if (mWorldNumber == null || !mWorldNumber.getDeclaringClass().isInstance(w)) {
                    for (String n : new String[]{"getWorld", "getId", "getWorldNumber", "getNumber"}) {
                        try {
                            Method m = w.getClass().getMethod(n);
                            if (m.getReturnType() == int.class) { mWorldNumber = m; break; }
                        } catch (Throwable ignored) {}
                    }
                }
                if (mWorldNumber != null) return (int) mWorldNumber.invoke(w);
            }
        } catch (Throwable ignored) {}
        return 0;
    }
}
