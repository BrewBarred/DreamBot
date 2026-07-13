package main.market;

/**
 * The client's view of what the logged-in account is allowed (Patch B.14).
 *
 * <p><b>Read this as advisory UI, not enforcement.</b> The numbers here come from the login
 * response and are cached in the session so the menu can grey out what a free user can't have and
 * size the loop spinner. But the client runs on the user's machine and can be decompiled — so the
 * REAL limits are enforced by the server: it withholds VIP script bundles from non-VIP tokens and
 * clamps loop grants to the tier cap. This class exists to make the UI honest, not to be a wall.
 */
public final class Tier {

    private Tier() {}

    public static final String GUEST = "guest";
    public static final String FREE = "free";
    public static final String VIP = "vip";
    public static final String ADMIN = "admin";
    public static final String MODERATOR = "moderator";

    /** The current account's tier ("guest" when not logged in). */
    public static String current() {
        try { return ServerAccount.session().tier; } catch (Throwable t) { return GUEST; }
    }

    public static boolean isGuest() { return GUEST.equals(current()); }
    public static boolean isVip() {
        String t = current();
        return VIP.equals(t) || ADMIN.equals(t) || MODERATOR.equals(t);
    }
    public static boolean isAdmin() {
        String t = current();
        return ADMIN.equals(t) || MODERATOR.equals(t);
    }
    public static boolean isLoggedIn() { return !isGuest(); }

    /** Max queue loops for this account. Guests/free = 50, VIP = 150 (server is the truth). */
    public static int maxLoops() {
        try {
            int m = ServerAccount.session().maxLoops;
            return m > 0 ? m : 50;
        } catch (Throwable t) { return 50; }
    }

    /** Max extra Jagex accounts (beyond the one you're on). Free = 2, VIP = 10. */
    public static int maxExtraAccounts() {
        try {
            int m = ServerAccount.session().maxExtraAccounts;
            return m > 0 ? m : 2;
        } catch (Throwable t) { return 2; }
    }

    /** Whether this account may publish VIP-gated tasks (admins only). */
    public static boolean canPublishVip() {
        try { return ServerAccount.session().canPublishVip; } catch (Throwable t) { return false; }
    }

    /** A short label for the Status card, e.g. "Free", "VIP", "Admin". */
    public static String label() {
        switch (current()) {
            case VIP: return "VIP";
            case ADMIN: return "Admin";
            case MODERATOR: return "Moderator";
            case FREE: return "Free";
            default: return "Guest";
        }
    }

    /** True when infinite loops (0) are allowed - only VIP+ get uncapped. Free is always capped. */
    public static boolean allowsInfinite() { return isVip(); }
}
