package main.tools;

import main.data.store.LocalStore;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The player's account type (v1.31): Regular / Ironman / Ultimate / Group. Set from the Status
 * tab's Player card; the client can't reliably detect this, so it's an honest manual setting.
 *
 * <p>It gates which actions appear in the Task Builder: UIMs don't see Bank (they can't use
 * one), only Group Ironmen see Group Storage, and the banker Note-Exchange helper is pitched
 * at UIMs but available to all.
 */
public enum AccountTypes {

    REGULAR("Regular"), IRONMAN("Ironman"), UIM("Ultimate Ironman"), GIM("Group Ironman");

    public final String label;

    AccountTypes(String label) { this.label = label; }

    private static volatile AccountTypes current;
    /** v1.87: true once the person picked a type BY HAND this session - auto-detect then defers. */
    private static volatile boolean manualThisSession = false;

    private static File file() { return new File(LocalStore.getRoot(), "account-type.txt"); }

    public static AccountTypes current() {
        AccountTypes c = current;
        if (c != null) return c;
        try {
            File f = file();
            if (f.isFile()) {
                String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
                current = c = valueOf(s);
                return c;
            }
        } catch (Throwable ignored) {}
        current = REGULAR;
        return REGULAR;
    }

    public static void set(AccountTypes t) {
        manualThisSession = true;   // v1.87: a hand-picked type outranks auto-detection
        apply(t);
    }

    private static void apply(AccountTypes t) {
        if (t == null) return;
        current = t;
        try {
            Files.write(file().toPath(), t.name().getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {}
    }

    // ── v1.87: best-effort auto-detection on login ────────────────────────────
    // OSRS stores the ironman mode in varbit 1777 (0 regular · 1 ironman · 2 ultimate ·
    // 3 hardcore · 4-6 the group variants). Whether THIS client build can read varbits at all
    // varies, so the lookup is reflective and every failure is silent: detection either works
    // and quietly sets the right type, or nothing changes and the combo stays the honest
    // manual control it always was. A manual pick this session always wins.

    private static java.lang.reflect.Method varbitMethod;
    private static boolean varbitLookupFailed;

    /**
     * Reads the account type from the client, or null when it can't. Never throws.
     * HC variants map onto the nearest type this enum models (HCIM → IRONMAN, HCGIM → GIM):
     * what matters downstream is bank/group-storage gating, which those share.
     */
    public static AccountTypes detect() {
        try {
            if (varbitMethod == null && !varbitLookupFailed) {
                Class<?> ps = Class.forName("org.dreambot.api.methods.settings.PlayerSettings");
                for (String name : new String[]{"getBitValue", "getVarbitValue", "getVarpbitValue"}) {
                    try {
                        varbitMethod = ps.getMethod(name, int.class);
                        break;
                    } catch (Throwable ignored) {}
                }
                if (varbitMethod == null) varbitLookupFailed = true;
            }
            if (varbitMethod == null) return null;
            Object v = varbitMethod.invoke(null, 1777);
            if (!(v instanceof Number)) return null;
            switch (((Number) v).intValue()) {
                case 0: return REGULAR;
                case 1:
                case 3: return IRONMAN;   // 3 = hardcore
                case 2: return UIM;
                case 4:
                case 5:
                case 6: return GIM;       // group / hardcore group / unranked group
                default: return null;
            }
        } catch (Throwable t) {
            varbitLookupFailed = true;
            return null;
        }
    }

    /**
     * Called by the menu right after a login: applies the detected type UNLESS the person set
     * one by hand this session. @return the detected type when it was applied, else null.
     */
    public static AccountTypes applyDetected() {
        if (manualThisSession) return null;
        AccountTypes found = detect();
        if (found == null || found == current()) return null;
        apply(found);
        return found;
    }

    @Override public String toString() { return label; }
}
