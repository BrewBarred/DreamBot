package main.tools;

import org.dreambot.api.utilities.Logger;

import java.lang.reflect.Method;

/**
 * Group-storage access via reflection (v1.31 hotfix). The user's DreamBot build has no
 * {@code SharedStorage} class where I expected one, and the group-storage API location varies
 * across versions - so this probes the likely homes at runtime and calls whichever exists.
 * When NONE exists, {@link #available()} is false, the GroupStorage action fails its attempts
 * cleanly, and one WARN line explains why instead of the script clicking blind.
 */
public final class GroupStorageApi {

    private GroupStorageApi() {}

    private static final String[] CANDIDATES = {
            "org.dreambot.api.methods.container.impl.SharedStorage",
            "org.dreambot.api.methods.container.impl.sharedstorage.SharedStorage",
            "org.dreambot.api.methods.container.impl.groupstorage.GroupStorage",
            "org.dreambot.api.methods.grouping.GroupStorage",
            "org.dreambot.api.methods.groupironman.GroupStorage",
    };

    private static volatile boolean resolved;
    private static Class<?> api;
    private static Method mIsOpen, mOpen, mClose, mDeposit, mDepositAll, mWithdraw;
    private static volatile boolean warned;

    private static synchronized void resolve() {
        if (resolved) return;
        resolved = true;
        for (String name : CANDIDATES) {
            try {
                Class<?> c = Class.forName(name);
                Method isOpen = find(c, "isOpen");
                Method open = find(c, "open");
                if (isOpen == null || open == null) continue;
                api = c;
                mIsOpen = isOpen;
                mOpen = open;
                mClose = find(c, "close");
                mDeposit = find(c, "deposit", String.class, int.class);
                mDepositAll = find(c, "depositAll", String.class);
                mWithdraw = find(c, "withdraw", String.class, int.class);
                return;
            } catch (Throwable ignored) {}
        }
    }

    private static Method find(Class<?> c, String name, Class<?>... params) {
        try { return c.getMethod(name, params); } catch (Throwable t) { return null; }
    }

    /** True when this DreamBot build exposes a group-storage API at all. */
    public static boolean available() {
        resolve();
        if (api == null && !warned) {
            warned = true;
            Logger.log(Logger.LogType.WARN, "[GroupStorage] This DreamBot build doesn't expose "
                    + "a group-storage API - the Group Storage action can't run on it.");
        }
        return api != null;
    }

    private static boolean call(Method m, Object... args) {
        if (m == null) return false;
        try {
            Object r = m.invoke(null, args);
            return r instanceof Boolean ? (Boolean) r : true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isOpen()                       { resolve(); return call(mIsOpen); }
    public static boolean open()                         { resolve(); return call(mOpen); }
    public static boolean close()                        { resolve(); return call(mClose); }
    public static boolean deposit(String item, int amt)  { resolve(); return call(mDeposit, item, amt); }
    public static boolean depositAll(String item)        {
        resolve();
        if (mDepositAll != null) return call(mDepositAll, item);
        return call(mDeposit, item, Integer.MAX_VALUE);
    }
    public static boolean withdraw(String item, int amt) { resolve(); return call(mWithdraw, item, amt); }
}
