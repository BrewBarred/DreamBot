package main.market;

/**
 * The client's view of what the logged-in account is allowed (Patch B.14; ranks v1.87).
 *
 * <p><b>Read this as advisory UI, not enforcement.</b> The numbers here come from the login
 * response and are cached in the session so the menu can grey out what a free user can't have and
 * size the loop spinner. But the client runs on the user's machine and can be decompiled — so the
 * REAL limits are enforced by the server: it withholds VIP script bundles from non-VIP tokens and
 * clamps loop grants to the tier cap. This class exists to make the UI honest, not to be a wall.
 *
 * <p><b>The ladder (v1.87)</b>, least to most valued: guest → free → vip → <b>subscriber</b> →
 * <b>lifetime</b> → moderator → admin → owner. VIP marks DreamBot VIPs / members; a SUBSCRIBER
 * pays monthly for the menu itself, so they sit above VIP; LIFETIME buyers took the big early
 * risk in exchange for everything forever, so they're the most valued non-staff rank, just
 * below admin. Two ORTHOGONAL marks ride alongside any tier: <b>scripter</b> (earned; its one
 * power is lifting the market upload cap) and <b>donor</b> (cosmetic levels from donations —
 * see {@link DonorRanks}). Neither changes the tier ladder.
 */
public final class Tier {

    private Tier() {}

    public static final String GUEST = "guest";
    public static final String FREE = "free";
    public static final String VIP = "vip";
    /** v1.87: pays monthly for the menu - above VIP by design. */
    public static final String SUBSCRIBER = "subscriber";
    /** v1.87: bought lifetime access - the most valued rank below staff. */
    public static final String LIFETIME = "lifetime";
    public static final String ADMIN = "admin";
    public static final String MODERATOR = "moderator";
    /**
     * v1.32: the OWNER tier, above admin. Owners can open the dev console, search all registered
     * users, and promote them to admin (and, next patch, demote/ban/restrict). Set this directly
     * in the database on your own account - there's deliberately no UI to mint the first owner,
     * so the role can't be escalated into from inside the client. As with every tier, the client
     * only DISPLAYS it; the server must enforce that owner-only endpoints reject non-owner tokens.
     */
    public static final String OWNER = "owner";

    /** The current account's tier ("guest" when not logged in). */
    public static String current() {
        try { return ServerAccount.session().tier; } catch (Throwable t) { return GUEST; }
    }

    /** The ladder position of a tier name (unknown names weigh as free). */
    public static int weight(String tier) {
        if (tier == null) return 1;
        switch (tier.toLowerCase(java.util.Locale.ROOT)) {
            case GUEST:      return 0;
            case VIP:        return 2;
            case SUBSCRIBER: return 3;
            case LIFETIME:   return 4;
            case MODERATOR:  return 5;
            case ADMIN:      return 6;
            case OWNER:      return 7;
            default:         return 1;   // free / unknown
        }
    }

    private static boolean atLeast(String tier) { return weight(current()) >= weight(tier); }

    public static boolean isGuest() { return GUEST.equals(current()); }
    /** VIP or better (DreamBot VIPs / members - and everyone above them). */
    public static boolean isVip() { return atLeast(VIP); }
    /** v1.87: subscriber or better. */
    public static boolean isSubscriber() { return atLeast(SUBSCRIBER); }
    /** v1.87: lifetime or better. */
    public static boolean isLifetime() { return atLeast(LIFETIME); }
    public static boolean isAdmin() { return atLeast(MODERATOR); }
    /** v1.32: owner-only capabilities (the dev console's user ranks). */
    public static boolean isOwner() { return OWNER.equals(current()); }
    public static boolean isLoggedIn() { return !isGuest(); }

    /**
     * v1.87: the SCRIPTER mark - orthogonal to the ladder, earned by posting scripts worth
     * having. Its single power: the market upload cap disappears. The flag rides the session
     * (the server sends {@code scripter: true}); a tier literally named "scripter" also counts,
     * so either server representation works.
     */
    public static boolean isScripter() {
        try {
            if (ServerAccount.session().scripter) return true;
        } catch (Throwable ignored) {}
        return "scripter".equalsIgnoreCase(current()) || isAdmin();
    }

    /** v1.87: total donated, in cents, from the login payload (0 until donations exist). */
    public static long donatedCents() {
        try { return Math.max(0, ServerAccount.session().donatedCents); } catch (Throwable t) { return 0; }
    }

    /** Max queue loops for this account. The server's number wins; these are the tier defaults. */
    public static int maxLoops() {
        try {
            int m = ServerAccount.session().maxLoops;
            if (m > 0) return m;
        } catch (Throwable ignored) {}
        if (isLifetime()) return 9999;
        if (isSubscriber()) return 400;
        if (isVip()) return 150;
        return 50;
    }

    /** Max extra Jagex accounts (beyond the one you're on). Server wins; tier defaults below. */
    public static int maxExtraAccounts() {
        try {
            int m = ServerAccount.session().maxExtraAccounts;
            if (m > 0) return m;
        } catch (Throwable ignored) {}
        if (isLifetime()) return 50;
        if (isSubscriber()) return 20;
        if (isVip()) return 10;
        return 2;
    }

    /**
     * v1.87: how many equipment presets this account may keep. Free users get a taste, VIPs a
     * full wardrobe, subscribers more, and lifetime + staff are uncapped - the app stays free
     * to USE while the deep-storage conveniences reward supporting it. The server may override
     * with a {@code presetLimit} in the login payload; {@link Integer#MAX_VALUE} = unlimited.
     */
    public static int presetLimit() {
        try {
            int m = ServerAccount.session().presetLimit;
            if (m > 0) return m;
        } catch (Throwable ignored) {}
        if (isLifetime() || isAdmin()) return Integer.MAX_VALUE;
        if (isSubscriber()) return 64;
        if (isVip()) return 28;
        return 8;
    }

    /**
     * v1.87: how many market listings this account may have published at once - ADVISORY, the
     * server enforces the real cap. The SCRIPTER mark (and staff) lifts it entirely: post as
     * many good scripts as you like; that's the whole point of the rank.
     */
    public static int marketUploadLimit() {
        if (isScripter()) return Integer.MAX_VALUE;
        if (isLifetime()) return 50;
        if (isSubscriber()) return 25;
        if (isVip()) return 10;
        return 3;
    }

    /** Whether this account may publish VIP-gated tasks (admins only). */
    public static boolean canPublishVip() {
        try { return ServerAccount.session().canPublishVip; } catch (Throwable t) { return false; }
    }

    /** A short label for the account surfaces, e.g. "Free", "VIP", "Subscriber". */
    public static String label() { return labelFor(current()); }

    /** v1.87: the label for ANY tier string (badges, the dev console's user list). */
    public static String labelFor(String tier) {
        if (tier == null) return "Free";
        switch (tier.toLowerCase(java.util.Locale.ROOT)) {
            case OWNER:      return "Owner";
            case ADMIN:      return "Admin";
            case MODERATOR:  return "Moderator";
            case LIFETIME:   return "Lifetime";
            case SUBSCRIBER: return "Subscriber";
            case VIP:        return "VIP";
            case GUEST:      return "Guest";
            default:         return "Free";
        }
    }

    /** True when infinite loops (0) are allowed - VIP and above. Free is always capped. */
    public static boolean allowsInfinite() { return isVip(); }
}
