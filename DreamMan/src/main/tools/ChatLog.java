package main.tools;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * The in-game chat log + the script's Player Log (v1.31), backing the Logs tab and the
 * CHAT_CONTAINS check.
 *
 * <p><b>Memory only, on purpose.</b> Nothing here is written to disk or any server - the whole
 * log evaporates when the client closes. The Logs tab offers Copy and Save buttons for the
 * moments you *want* to keep something; that's the only way any of it leaves this process.
 *
 * <p>Entries come from DreamBot's ChatListener hooks (wired in the script class) and from the
 * Widget action's "read" mode, which appends whatever text it captured to the Player Log -
 * clue steps, quest text, anything on screen you want to track and search later.
 */
public final class ChatLog {

    private ChatLog() {}

    /** One line in a log. */
    public static final class Entry {
        public final long at = System.currentTimeMillis();
        public final String type;     // GAME, PUBLIC, PRIVATE, TRADE, CLAN, NOTE (player log)
        public final String who;      // sender ("" for game messages / notes)
        public final String text;

        Entry(String type, String who, String text) {
            this.type = type == null ? "GAME" : type;
            this.who = who == null ? "" : who;
            this.text = text == null ? "" : text;
        }

        @Override public String toString() {
            String t = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(at));
            return "[" + t + "] [" + type + "] " + (who.isEmpty() ? "" : who + ": ") + text;
        }
    }

    private static final int MAX = 2000;
    private static final Deque<Entry> CHAT = new ArrayDeque<>();
    private static final Deque<Entry> PLAYER_LOG = new ArrayDeque<>();
    /** Bumped on every append so panels can refresh only when something changed. */
    private static volatile long revision = 0;

    // ── feeding ──────────────────────────────────────────────────────────────

    public static void chat(String type, String who, String text) {
        if (text == null || text.isBlank()) return;
        synchronized (CHAT) {
            CHAT.addLast(new Entry(type, who, text));
            while (CHAT.size() > MAX) CHAT.removeFirst();
        }
        revision++;
    }

    /** Appends a line to the Player Log (widget reads, script notes). */
    public static void note(String label, String text) {
        if (text == null || text.isBlank()) return;
        synchronized (PLAYER_LOG) {
            PLAYER_LOG.addLast(new Entry("NOTE", label == null ? "" : label, text));
            while (PLAYER_LOG.size() > MAX) PLAYER_LOG.removeFirst();
        }
        revision++;
    }

    // ── reading ──────────────────────────────────────────────────────────────

    public static long revision() { return revision; }

    public static List<Entry> chatEntries() {
        synchronized (CHAT) { return new ArrayList<>(CHAT); }
    }

    public static List<Entry> playerLogEntries() {
        synchronized (PLAYER_LOG) { return new ArrayList<>(PLAYER_LOG); }
    }

    public static void clearChat()      { synchronized (CHAT) { CHAT.clear(); } revision++; }
    public static void clearPlayerLog() { synchronized (PLAYER_LOG) { PLAYER_LOG.clear(); } revision++; }

    // ── the CHAT_CONTAINS check ──────────────────────────────────────────────

    /** Timestamp up to which matches have already been consumed (so one line fires once). */
    private static volatile long consumedUpTo = 0;

    /**
     * True when any RECENT chat line (last {@code windowMs}) from someone else contains any of
     * the "|"-separated phrases. A match CONSUMES everything up to that line, so the same
     * message can't re-fire the check after its cooldown - only a new matching line can.
     */
    public static boolean matchAndConsume(String phrases, long windowMs) {
        if (phrases == null || phrases.isBlank()) return false;
        String[] parts = phrases.toLowerCase(Locale.ROOT).split("\\|");
        long cutoff = System.currentTimeMillis() - Math.max(1000, windowMs);
        List<Entry> snap = chatEntries();
        for (Entry e : snap) {
            if (e.at <= consumedUpTo || e.at < cutoff) continue;
            if (!"PUBLIC".equals(e.type) && !"PRIVATE".equals(e.type)
                    && !"CLAN".equals(e.type) && !"TRADE".equals(e.type)) continue;
            // v1.32: never match the bot's OWN chat. The Say action logs what it says as
            // "(me)", and without this a CHAT_CONTAINS trigger would hear its own reply and
            // fire again forever. Own lines are skipped but still ADVANCE the consumed marker,
            // so they can't be re-examined next tick either.
            if (isSelf(e.who)) { consumedUpTo = Math.max(consumedUpTo, e.at); continue; }
            String line = e.text.toLowerCase(Locale.ROOT);
            for (String p : parts) {
                String needle = p.trim();
                if (!needle.isEmpty() && line.contains(needle)) {
                    consumedUpTo = e.at;
                    return true;
                }
            }
        }
        return false;
    }

    /** True when a chat sender is the local player (the Say action tags its lines "(me)"). */
    private static boolean isSelf(String who) {
        if (who == null) return false;
        String w = who.trim();
        if (w.equals("(me)")) return true;
        try {
            var me = org.dreambot.api.methods.interactive.Players.getLocal();
            String name = me == null ? null : me.getName();
            return name != null && !name.isEmpty() && name.equalsIgnoreCase(w);
        } catch (Throwable t) {
            return false;
        }
    }
}
