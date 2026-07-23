package main.tools;

import org.dreambot.api.methods.dialogues.Dialogues;

/**
 * The automatic dialogue recorder (v1.87). Every UI tick the menu asks {@link #poll()} to look
 * at the screen; when a dialogue is open and it's CHANGED since last look - new speaker, new
 * line, or new options - the moment lands in the Player Log as a DIALOGUE entry, options and
 * all. Nothing is clicked, nothing is answered: this only watches.
 *
 * <p>Why it exists: building a Chat action means knowing the exact option strings a
 * conversation offers, and the old way was typing them from memory with the game open. Now the
 * player just HAS the conversation once - every line and option list is in the Player Log,
 * double-click copies the piece you need ("Yes.|No thanks." goes straight into a Chat action's
 * options field), and clue text captured the same way references straight into task building.
 *
 * <p>Self-rate-limited (~700ms) and change-detected, so the log gets each moment once, not
 * once per tick. Every client read is guarded - a mid-hop poll can never hurt the UI timer.
 */
public final class DialogueWatcher {

    private DialogueWatcher() {}

    private static long lastLookAt;
    private static String lastSeen = "";   // npc + text + options, hashed by concatenation

    /** Look at the screen; record the dialogue if it's new. Safe to call from the UI timer. */
    public static void poll() {
        long now = System.currentTimeMillis();
        if (now - lastLookAt < 700) return;
        lastLookAt = now;
        try {
            if (!Dialogues.inDialogue()) {
                lastSeen = "";
                return;
            }
            String text = safe(Dialogues.getNPCDialogue());
            String[] options = null;
            try {
                if (Dialogues.areOptionsAvailable()) options = Dialogues.getOptions();
            } catch (Throwable ignored) {}
            if (options != null && options.length == 0) options = null;

            String npc = speakerOf(text);
            String key = npc + "\u0001" + text + "\u0001"
                    + (options == null ? "" : String.join("|", options));
            if (key.equals(lastSeen) || (text.isBlank() && options == null)) return;
            lastSeen = key;
            ChatLog.dialogue(npc, text, options);
        } catch (Throwable ignored) {
            // no client behind us (tests) or a mid-hop read - just try again next tick
        }
    }

    /**
     * DreamBot's dialogue text often arrives as "Name: line". When it does, the name becomes
     * the entry's speaker so the log reads like a transcript; when it doesn't, the speaker
     * column stays blank rather than guessing.
     */
    private static String speakerOf(String text) {
        if (text == null) return "";
        int i = text.indexOf(':');
        if (i > 0 && i <= 24 && !text.substring(0, i).contains(" er")) {
            String name = text.substring(0, i).trim();
            if (name.matches("[A-Za-z][A-Za-z' .-]{0,22}")) return name;
        }
        return "";
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }
}
