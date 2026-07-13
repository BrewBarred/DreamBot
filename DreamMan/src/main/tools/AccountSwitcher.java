package main.tools;

import org.dreambot.api.utilities.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads DreamBot's Account Manager and switches the logged-in account (Patch B.9).
 *
 * <p><b>Why reflection:</b> as with the bank PIN, this round has no verified access to the live
 * DreamBot javadocs. The one thing we know for certain (it's already used in the Status tab) is
 * that {@code AccountManager} exists with {@code getAccountUsername()}/{@code getAccountNickname()}.
 * The account LIST and the SWITCH call are resolved by name at runtime against a set of plausible
 * candidates, so:
 * <ul>
 *   <li>on a client version that exposes them, the Status tab gets a working account picker;</li>
 *   <li>on one that doesn't, the picker simply reports that it isn't available - the build still
 *       compiles and everything else keeps working.</li>
 * </ul>
 * A guessed API name can never break your build this way.
 */
public final class AccountSwitcher {

    private AccountSwitcher() {}

    private static final String ACCOUNT_MANAGER = "org.dreambot.api.utilities.AccountManager";

    /** The account currently logged in (or empty). Uses the API we already rely on. */
    public static String currentAccount() {
        try {
            String nick = org.dreambot.api.utilities.AccountManager.getAccountNickname();
            if (nick != null && !nick.isEmpty()) return nick;
            String user = org.dreambot.api.utilities.AccountManager.getAccountUsername();
            return user == null ? "" : user;
        } catch (Throwable t) { return ""; }
    }

    /**
     * The accounts DreamBot knows about, if this client exposes them.
     * @return the account names, or an empty list when unavailable.
     */
    @SuppressWarnings("unchecked")
    public static List<String> listAccounts() {
        Class<?> am = cls(ACCOUNT_MANAGER);
        if (am == null) return Collections.emptyList();

        String[] getters = {"getAccounts", "getAccountList", "getAllAccounts", "accounts"};
        for (String g : getters) {
            try {
                Method m = am.getMethod(g);
                Object r = m.invoke(null);
                List<String> names = coerceNames(r);
                if (!names.isEmpty()) return names;
            } catch (Throwable ignored) {}
        }
        return Collections.emptyList();
    }

    /** True when this client build lets us enumerate and switch accounts. */
    public static boolean isSupported() {
        return !listAccounts().isEmpty() && findSwitchMethod() != null;
    }

    /**
     * Switches the active account. The caller should log out first (the menu does), so the client
     * picks the new account up on the next login.
     *
     * @return true if the switch call was accepted.
     */
    public static boolean switchTo(String accountName) {
        if (accountName == null || accountName.isEmpty()) return false;
        Method m = findSwitchMethod();
        if (m == null) {
            Logger.log(Logger.LogType.WARN, "[Accounts] This client build doesn't expose an "
                    + "account-switch API that DreamMan recognises.");
            return false;
        }
        try {
            m.invoke(null, accountName);
            Logger.log("[Accounts] Switched to " + accountName);
            return true;
        } catch (Throwable t) {
            Logger.log(Logger.LogType.WARN, "[Accounts] Switch failed: " + t);
            return false;
        }
    }

    /** Finds a static switch/select method taking a single String. */
    private static Method findSwitchMethod() {
        Class<?> am = cls(ACCOUNT_MANAGER);
        if (am == null) return null;
        String[] names = {"setAccount", "selectAccount", "switchAccount", "login", "loadAccount"};
        for (String n : names) {
            try { return am.getMethod(n, String.class); }
            catch (NoSuchMethodException ignored) {}
        }
        return null;
    }

    /** Turns whatever the client returned (List, array, Collection of objects) into names. */
    private static List<String> coerceNames(Object r) {
        List<String> out = new ArrayList<>();
        if (r == null) return out;
        try {
            if (r instanceof Object[]) {
                for (Object o : (Object[]) r) addName(out, o);
            } else if (r instanceof Iterable) {
                for (Object o : (Iterable<?>) r) addName(out, o);
            }
        } catch (Throwable ignored) {}
        return out;
    }

    /** Accounts may come back as Strings or as account objects with a name/username getter. */
    private static void addName(List<String> out, Object o) {
        if (o == null) return;
        if (o instanceof String) { out.add((String) o); return; }
        for (String g : new String[]{"getNickname", "getUsername", "getName", "toString"}) {
            try {
                Method m = o.getClass().getMethod(g);
                Object v = m.invoke(o);
                if (v instanceof String && !((String) v).isEmpty()) { out.add((String) v); return; }
            } catch (Throwable ignored) {}
        }
    }

    private static Class<?> cls(String n) {
        try { return Class.forName(n); } catch (Throwable t) { return null; }
    }
}
