package main.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import main.crypto.Vault;
import main.privacy.Consent;
import org.dreambot.api.utilities.Logger;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.List;

/**
 * The logged-in user's decrypted account list (Patch B.15). Holds the Jagex accounts + per-account
 * PINs in memory only, encrypts them with the vault key, and syncs the ciphertext to the server.
 *
 * <p>This is where the zero-knowledge vault (B.13) meets the UI. The plaintext account list exists
 * only while you're logged in and only in this process's memory; what goes to the server is the
 * AES-256-GCM blob. The vault key lives here (unwrapped at login from your password); it never
 * touches disk or the network.
 */
public final class AccountVault {

    private AccountVault() {}

    private static final Gson GSON = new GsonBuilder().create();

    /** One saved Jagex account. The NAME never leaves this machine unless the server holds the
     *  encrypted blob - and even then it's ciphertext. PIN is optional per account. */
    public static final class Account {
        public String name = "";       // OSRS login name
        public String label = "";      // friendly label ("Main", "Ironman")
        public String pin = "";        // bank PIN for this account (optional)
        public String world = "";      // preferred world (optional)
        public Account() {}
        public Account(String name, String label) { this.name = name; this.label = label; }
    }

    // in-memory state, valid only while logged in
    private static volatile List<Account> accounts = new ArrayList<>();
    private static volatile SecretKey vaultKey;     // unwrapped at login
    private static volatile boolean unlocked = false;

    /** True when a vault has been unlocked this session (logged in with the right password). */
    public static boolean isUnlocked() { return unlocked && vaultKey != null; }

    /** The decrypted accounts (empty when locked). Never persisted in plaintext. */
    public static List<Account> accounts() {
        return isUnlocked() ? new ArrayList<>(accounts) : new ArrayList<>();
    }

    /** Called at login once the vault key is unwrapped and the ciphertext decrypted. */
    public static synchronized void unlock(SecretKey key, String decryptedJson) {
        vaultKey = key;
        accounts = parse(decryptedJson);
        unlocked = true;
        Logger.log("[Vault] Unlocked - " + accounts.size() + " account(s) available.");
    }

    /** Called at logout: wipe the plaintext + key from memory. */
    public static synchronized void lock() {
        accounts = new ArrayList<>();
        vaultKey = null;
        unlocked = false;
    }

    /** Adds an account (respecting the tier's extra-account cap) and syncs. @return error or null. */
    public static synchronized String addAccount(Account a) {
        if (!isUnlocked()) return "Log in first.";
        if (a == null || a.name.trim().isEmpty()) return "Enter an account name.";
        // the first account is "free"; extras count against the tier cap
        int extras = Math.max(0, accounts.size());   // each beyond the first is an extra
        if (extras >= Tier.maxExtraAccounts() + 1)    // +1 because the first isn't an "extra"
            return "Your tier allows " + Tier.maxExtraAccounts() + " extra account(s). "
                    + (Tier.isVip() ? "" : "Upgrade to VIP for up to 10.");
        for (Account ex : accounts)
            if (ex.name.equalsIgnoreCase(a.name)) return "That account is already saved.";
        accounts.add(a);
        return null;
    }

    public static synchronized void removeAccount(String name) {
        accounts.removeIf(x -> x.name.equalsIgnoreCase(name));
    }

    public static synchronized Account find(String name) {
        for (Account a : accounts) if (a.name.equalsIgnoreCase(name)) return a;
        return null;
    }

    /** Re-encrypts the current accounts and returns the ciphertext to push to the server. */
    public static synchronized String encryptForSync() {
        if (!isUnlocked()) return null;
        String json = GSON.toJson(accounts);
        return Vault.encryptData(json, vaultKey);
    }

    /** Pushes the encrypted account blob to the server (gated on cloud-sync consent). */
    public static void sync(ServerAccount server) {
        if (!isUnlocked() || server == null) return;
        if (!Consent.has(Consent.CLOUD_SYNC)) return;   // no consent -> nothing sent
        try {
            String blob = encryptForSync();
            if (blob != null) server.putVaultData(blob);
        } catch (Throwable t) {
            Logger.log(Logger.LogType.WARN, "[Vault] sync failed: " + t.getMessage());
        }
    }

    private static List<Account> parse(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            List<Account> a = GSON.fromJson(json, new TypeToken<List<Account>>() {}.getType());
            return a == null ? new ArrayList<>() : a;
        } catch (Throwable t) { return new ArrayList<>(); }
    }
}
