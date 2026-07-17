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
        if (t == null) return;
        current = t;
        try {
            Files.write(file().toPath(), t.name().getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {}
    }

    @Override public String toString() { return label; }
}
