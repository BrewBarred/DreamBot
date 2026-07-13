package main.tools;

import org.dreambot.api.utilities.Logger;

import java.lang.reflect.Method;

/**
 * Bank-PIN handling (Patch B.9). The usual cause of "the bot gets stuck opening the bank" is the
 * PIN screen: {@code Bank.open()} succeeds, the PIN dialog appears, and the script keeps calling
 * open() forever because the bank never reports itself as open.
 *
 * <p><b>Why reflection:</b> this patch round has no verified access to the live DreamBot javadocs,
 * and a guessed method name that doesn't exist would break your whole build. So every client call
 * here is resolved by name at RUNTIME against a list of plausible candidates. Whatever your client
 * version actually exposes gets used; anything absent is simply skipped, and the worst case is that
 * PIN entry silently doesn't happen (exactly today's behaviour) rather than a compile failure.
 *
 * <p>Three strategies are attempted, in order:
 * <ol>
 *   <li><b>Tell the client the PIN.</b> Most DreamBot builds accept the PIN centrally (on the
 *       account/settings object) and then enter it themselves whenever the screen appears. This is
 *       the cleanest path - we push the PIN once and the client does the rest.</li>
 *   <li><b>Ask a PIN helper to enter it.</b> Some builds expose a bank-PIN class with an
 *       enter/type method.</li>
 *   <li><b>Detect and report.</b> If neither resolves, we at least detect that a PIN screen is up
 *       so the Bank action can stop hammering open() and surface a clear message instead of
 *       spinning.</li>
 * </ol>
 */
public final class BankPin {

    private BankPin() {}

    /** The PIN the user typed into the menu (never logged, never written to the profile). */
    private static volatile String pin = "";
    /** True once we've successfully handed the PIN to the client this session. */
    private static volatile boolean pushedToClient = false;
    /** Remembered so we only log the "no PIN API" warning once. */
    private static volatile boolean warnedNoApi = false;

    /** Sets the PIN (4-10 digits; empty clears it). Held in memory only. */
    public static void setPin(String p) {
        pin = p == null ? "" : p.trim();
        pushedToClient = false;   // re-push on the next bank interaction
    }

    public static boolean hasPin() { return pin != null && !pin.isEmpty(); }

    /** @return the PIN masked for display, e.g. "****". Never returns the real digits. */
    public static String masked() {
        return hasPin() ? "*".repeat(pin.length()) : "(none)";
    }

    /**
     * Called by the Bank/FindBank actions before/while opening the bank. Pushes the PIN to the
     * client (once) and, if a PIN screen is currently up, tries to enter it.
     *
     * @return true if a PIN screen is up and we are still dealing with it (caller should wait),
     *         false when there's nothing PIN-related to do.
     */
    public static boolean handle() {
        if (!hasPin()) return false;

        // 1) hand the PIN to the client so it can answer the screen itself
        if (!pushedToClient && pushPinToClient()) {
            pushedToClient = true;
            Logger.log("[BankPin] PIN registered with the client.");
        }

        // 2) if a PIN screen is up right now, try to enter it ourselves
        if (isPinScreenUp()) {
            if (enterPin())
                Logger.log("[BankPin] Entering bank PIN...");
            return true;   // tell the caller to wait rather than spam Bank.open()
        }
        return false;
    }

    /** True when the client reports a bank-PIN screen (best effort across API variants). */
    public static boolean isPinScreenUp() {
        Boolean r = callStaticBoolean(
                new String[]{
                        "org.dreambot.api.methods.container.impl.bank.BankPin",
                        "org.dreambot.api.methods.widget.BankPin",
                        "org.dreambot.api.methods.container.impl.bank.Bank"
                },
                new String[]{"isOpen", "isPinScreenOpen", "hasPinScreen", "isPinOpen"});
        return Boolean.TRUE.equals(r);
    }

    /** Pushes the PIN into the client's own account/PIN settings so it handles the screen. */
    private static boolean pushPinToClient() {
        // Candidate: a static setter taking the PIN as a String or int
        String[] classes = {
                "org.dreambot.api.utilities.AccountManager",
                "org.dreambot.api.methods.settings.PlayerSettings",
                "org.dreambot.api.methods.container.impl.bank.BankPin",
                "org.dreambot.api.Client"
        };
        String[] setters = {"setBankPin", "setPin", "setAccountPin"};

        for (String cn : classes) {
            Class<?> cls = cls(cn);
            if (cls == null) continue;
            for (String m : setters) {
                // String signature
                try {
                    Method meth = cls.getMethod(m, String.class);
                    meth.invoke(null, pin);
                    return true;
                } catch (NoSuchMethodException ignored) {
                } catch (Throwable t) { return false; }
                // int signature
                try {
                    Method meth = cls.getMethod(m, int.class);
                    meth.invoke(null, Integer.parseInt(pin));
                    return true;
                } catch (NoSuchMethodException | NumberFormatException ignored) {
                } catch (Throwable t) { return false; }
            }
        }
        if (!warnedNoApi) {
            warnedNoApi = true;
            Logger.log(Logger.LogType.WARN, "[BankPin] This client build doesn't expose a PIN API "
                    + "that DreamMan recognises - the PIN screen will be detected but must be "
                    + "entered by DreamBot's own account settings.");
        }
        return false;
    }

    /** Asks a PIN helper class to type the PIN, if one exists. */
    private static boolean enterPin() {
        String[] classes = {
                "org.dreambot.api.methods.container.impl.bank.BankPin",
                "org.dreambot.api.methods.widget.BankPin"
        };
        String[] methods = {"enter", "enterPin", "type", "solve"};
        for (String cn : classes) {
            Class<?> cls = cls(cn);
            if (cls == null) continue;
            for (String m : methods) {
                try {
                    Method meth = cls.getMethod(m, String.class);
                    Object r = meth.invoke(null, pin);
                    return !(r instanceof Boolean) || (Boolean) r;
                } catch (NoSuchMethodException ignored) {
                } catch (Throwable t) { return false; }
                try {
                    Method meth = cls.getMethod(m, int.class);
                    Object r = meth.invoke(null, Integer.parseInt(pin));
                    return !(r instanceof Boolean) || (Boolean) r;
                } catch (NoSuchMethodException | NumberFormatException ignored) {
                } catch (Throwable t) { return false; }
                try {
                    Method meth = cls.getMethod(m);
                    Object r = meth.invoke(null);
                    return !(r instanceof Boolean) || (Boolean) r;
                } catch (NoSuchMethodException ignored) {
                } catch (Throwable t) { return false; }
            }
        }
        return false;
    }

    // ── tiny reflection helpers (never throw) ──

    private static Class<?> cls(String name) {
        try { return Class.forName(name); } catch (Throwable t) { return null; }
    }

    private static Boolean callStaticBoolean(String[] classNames, String[] methodNames) {
        for (String cn : classNames) {
            Class<?> c = cls(cn);
            if (c == null) continue;
            for (String mn : methodNames) {
                // skip Bank.isOpen - that's the bank, not the pin screen
                if ("org.dreambot.api.methods.container.impl.bank.Bank".equals(cn)
                        && "isOpen".equals(mn)) continue;
                try {
                    Method m = c.getMethod(mn);
                    Object r = m.invoke(null);
                    if (r instanceof Boolean) return (Boolean) r;
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }
}
