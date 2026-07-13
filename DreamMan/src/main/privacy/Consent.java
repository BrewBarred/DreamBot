package main.privacy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import main.data.store.LocalStore;
import org.dreambot.api.utilities.Logger;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Consent (Patch B.12). <b>Nothing about you leaves your machine unless you have explicitly said
 * yes to that specific thing.</b>
 *
 * <p>This class is the gate. Every outbound network call in DreamMan asks it first, and if the
 * answer is no, <i>the request is never made</i> - not sent-and-ignored, not queued, not made.
 * Consent is per-purpose and opt-in: the defaults below are all {@code false}, so a fresh install
 * is entirely offline until someone chooses otherwise.
 *
 * <p>The record of what you agreed to is stored <b>locally</b>, in
 * {@code <home>/DreamMan/consent.json}, and is never transmitted anywhere. Withdrawing consent
 * takes effect immediately.
 *
 * <h3>The purposes, and exactly what each one sends</h3>
 * <ul>
 *   <li>{@link #MARKET_BROWSE} - browse and download community scripts.<br>
 *       Sends: an anonymous random install id (so a rating can be counted once). Nothing else.
 *       No character name, no account, no game data.</li>
 *   <li>{@link #MARKET_PUBLISH} - publish your own scripts.<br>
 *       Sends: the script you chose to publish, and the display name you typed on it.</li>
 *   <li>{@link #CLOUD_SYNC} - keep your DreamMan setup on the server so another PC can load it.<br>
 *       Sends: your task queue, library and preferences. <b>Never your bank PIN</b> - see
 *       {@link main.tools.BankPin}, which is memory-only and is not part of a profile at all.</li>
 *   <li>{@link #LINK_CHARACTER_NAME} - <b>off by default, and we recommend leaving it off.</b>
 *       If off (the default), your OSRS character names never leave your PC: DreamMan maps them to
 *       local labels ("Main", "Alt 1") and only the label is sent. If on, the real name is sent.
 *       Turning this on puts a permanent record on a server linking a real game account to botting.
 *       There is no good reason to do it; the option exists only so nobody is surprised by a
 *       hidden default.</li>
 * </ul>
 */
public final class Consent {

    private Consent() {}

    /** Browse/download community scripts. Sends an anonymous install id only. */
    public static final String MARKET_BROWSE = "market_browse";
    /** Publish your scripts. Sends the script and your chosen display name. */
    public static final String MARKET_PUBLISH = "market_publish";
    /** Sync your DreamMan setup to your account. Never includes the bank PIN. */
    public static final String CLOUD_SYNC = "cloud_sync";
    /** Send REAL OSRS character names instead of local labels. Off by default. Not recommended. */
    public static final String LINK_CHARACTER_NAME = "link_character_name";

    /** Bumped when the wording of what we ask changes, so consent is re-confirmed rather than assumed. */
    public static final int CURRENT_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Record record;

    /** What's on disk. Local only - never sent to any server. */
    public static class Record {
        public int version = 0;
        public Map<String, Boolean> granted = new LinkedHashMap<>();
        public Map<String, Long> grantedAt = new LinkedHashMap<>();
        /** True once the person has actually seen the consent dialog (vs. never having been asked). */
        public boolean asked = false;
    }

    private static File file() {
        return new File(LocalStore.getRoot(), "consent.json");
    }

    private static synchronized Record load() {
        if (record != null) return record;
        try {
            File f = file();
            if (f.isFile()) {
                String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                Record r = GSON.fromJson(json, Record.class);
                if (r != null) {
                    // a change in what we ask means the old answer no longer applies
                    if (r.version != CURRENT_VERSION) {
                        r.granted.clear();
                        r.grantedAt.clear();
                        r.asked = false;
                        r.version = CURRENT_VERSION;
                    }
                    return record = r;
                }
            }
        } catch (Throwable t) {
            Logger.log(Logger.LogType.WARN, "[Consent] Could not read consent.json: " + t);
        }
        Record r = new Record();
        r.version = CURRENT_VERSION;
        return record = r;   // default: nothing granted
    }

    private static synchronized void save() {
        try {
            File f = file();
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), GSON.toJson(load()).getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            Logger.log(Logger.LogType.WARN, "[Consent] Could not save consent.json: " + t);
        }
    }

    /** The gate. False unless the person has explicitly said yes to THIS purpose. */
    public static boolean has(String purpose) {
        return Boolean.TRUE.equals(load().granted.get(purpose));
    }

    /** True once we've actually asked (so we don't nag on every click). */
    public static boolean hasBeenAsked() { return load().asked; }

    /** Records an answer. Grant or withdraw - both take effect immediately. */
    public static synchronized void set(String purpose, boolean grant) {
        Record r = load();
        r.granted.put(purpose, grant);
        if (grant) r.grantedAt.put(purpose, System.currentTimeMillis());
        else r.grantedAt.remove(purpose);
        r.asked = true;
        save();
        Logger.log("[Consent] " + purpose + " -> " + (grant ? "granted" : "withdrawn"));
    }

    /** Marks that the dialog was shown, even if everything was declined. */
    public static synchronized void markAsked() {
        load().asked = true;
        save();
    }

    /** Withdraws everything. The next network call will simply not happen. */
    public static synchronized void withdrawAll() {
        Record r = load();
        r.granted.clear();
        r.grantedAt.clear();
        save();
        Logger.log("[Consent] All consent withdrawn - DreamMan is offline again.");
    }

    /** When a purpose was agreed to (0 if never). For the "what have I agreed to" screen. */
    public static long grantedAt(String purpose) {
        Long t = load().grantedAt.get(purpose);
        return t == null ? 0 : t;
    }

    /** True if ANY network purpose is on - i.e. whether DreamMan talks to a server at all. */
    public static boolean anyNetworkConsent() {
        return has(MARKET_BROWSE) || has(MARKET_PUBLISH) || has(CLOUD_SYNC);
    }

    /** Human-readable list of what each purpose actually sends - used by the dialog and the docs. */
    public static String describe(String purpose) {
        switch (purpose) {
            case MARKET_BROWSE:
                return "Browse and download community scripts.\n"
                        + "Sends: a random anonymous ID for this install (so your rating counts once).\n"
                        + "Does NOT send: your character name, your account, or anything about your game.";
            case MARKET_PUBLISH:
                return "Publish your own scripts to the market.\n"
                        + "Sends: the script you choose to publish, plus the display name you type on it.\n"
                        + "Only when you press Publish - never automatically.";
            case CLOUD_SYNC:
                return "Save your DreamMan setup to your account so another PC can load it.\n"
                        + "Sends: your task queue, task library and preferences.\n"
                        + "NEVER sends: your bank PIN. It is held in memory only and is not part of a profile.";
            case LINK_CHARACTER_NAME:
                return "Send your REAL OSRS character names to the server instead of local labels.\n"
                        + "OFF by default, and we suggest leaving it off.\n"
                        + "With it off, your characters are labelled locally (\"Main\", \"Alt 1\") and the\n"
                        + "server only ever sees the label - your OSRS names never leave this PC.\n"
                        + "Turning it on puts a permanent record on a server tying a real game account\n"
                        + "to botting. There is no feature that needs this.";
            default:
                return purpose;
        }
    }
}
