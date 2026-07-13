package main.market;

import main.data.store.LocalStore;
import org.dreambot.api.Client;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

/**
 * Who you are to the market (Patch B.11).
 *
 * <p>Ratings and downloads are keyed to a random <b>install id</b> - generated once and kept in
 * {@code <home>/DreamMan/install.id} - rather than to your character name. That means one rating
 * per install (re-rating just overwrites your own file), your OSRS account name never has to leave
 * your machine to make the counts work, and the market keeps working while you're logged out.
 *
 * <p>Your character name is used only as a display name when you choose to publish something.
 */
public final class MarketIdentity {

    private MarketIdentity() {}

    private static volatile String cached;

    /** A stable, anonymous id for this DreamMan install. */
    public static String installId() {
        if (cached != null) return cached;
        synchronized (MarketIdentity.class) {
            if (cached != null) return cached;
            File f = new File(LocalStore.getRoot(), "install.id");
            try {
                if (f.isFile()) {
                    String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
                    if (!s.isEmpty()) return cached = sanitise(s);
                }
                String id = UUID.randomUUID().toString().substring(0, 12);
                f.getParentFile().mkdirs();
                Files.write(f.toPath(), id.getBytes(StandardCharsets.UTF_8));
                return cached = id;
            } catch (Throwable t) {
                // even if we can't persist it, stay consistent for this session
                return cached = "anon-" + Integer.toHexString(System.identityHashCode(MarketIdentity.class));
            }
        }
    }

    /** The name shown next to things you publish (your character, or "Anonymous"). */
    public static String displayName() {
        try {
            if (Client.isLoggedIn()) {
                String n = org.dreambot.api.methods.interactive.Players.getLocal().getName();
                if (n != null && !n.isEmpty()) return n;
            }
        } catch (Throwable ignored) {}
        return "Anonymous";
    }

    /** Ids end up in file names, so keep them boring. */
    private static String sanitise(String s) {
        String out = s.replaceAll("[^A-Za-z0-9-]", "");
        return out.isEmpty() ? UUID.randomUUID().toString().substring(0, 12) : out;
    }
}
