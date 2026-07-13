package main.privacy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import main.data.store.LocalStore;
import org.dreambot.api.utilities.Logger;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps your OSRS characters to local labels (Patch B.12) - the thing that lets one server account
 * manage several game accounts <b>without the server ever learning their names</b>.
 *
 * <p>The mapping lives in {@code <home>/DreamMan/characters.json} and <b>never leaves this PC</b>.
 * When DreamMan syncs a profile it sends the LABEL ("Main", "Alt 1"), never the OSRS name. So a
 * server - even a breached one - holds no record tying a real game account to botting. That's a
 * real risk worth engineering away rather than accepting, since the whole point of the account is
 * that it's doing something Jagex forbids.
 *
 * <p>If someone explicitly turns on {@link Consent#LINK_CHARACTER_NAME} (off by default, and we
 * advise against it), {@link #labelFor} returns the real name instead - because a hidden default
 * would be worse than an informed choice.
 */
public final class CharacterMap {

    private CharacterMap() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Map<String, String> map;   // osrs name (lowercase) -> label

    private static File file() { return new File(LocalStore.getRoot(), "characters.json"); }

    @SuppressWarnings("unchecked")
    private static synchronized Map<String, String> load() {
        if (map != null) return map;
        try {
            File f = file();
            if (f.isFile()) {
                String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                Map<String, String> m = GSON.fromJson(json,
                        new TypeToken<LinkedHashMap<String, String>>() {}.getType());
                if (m != null) return map = m;
            }
        } catch (Throwable t) {
            Logger.log(Logger.LogType.WARN, "[Characters] Could not read characters.json: " + t);
        }
        return map = new LinkedHashMap<>();
    }

    private static synchronized void save() {
        try {
            File f = file();
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), GSON.toJson(load()).getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            Logger.log(Logger.LogType.WARN, "[Characters] Could not save characters.json: " + t);
        }
    }

    /**
     * The name to use ON THE SERVER for this character.
     *
     * @return the local label (default), or the real OSRS name only if the person explicitly
     *         consented to {@link Consent#LINK_CHARACTER_NAME}.
     */
    public static String labelFor(String osrsName) {
        if (osrsName == null || osrsName.trim().isEmpty()) return "default";

        if (Consent.has(Consent.LINK_CHARACTER_NAME))
            return osrsName.trim();          // they asked for this, explicitly, having been warned

        String key = osrsName.trim().toLowerCase();
        Map<String, String> m = load();
        String label = m.get(key);
        if (label != null) return label;

        // first time we've seen this character: give it a neutral label, locally
        label = m.isEmpty() ? "Main" : "Alt " + m.size();
        m.put(key, label);
        save();
        Logger.log("[Characters] New character labelled \"" + label + "\" (its real name stays on this PC)");
        return label;
    }

    /** Renames the label for a character (e.g. "Alt 1" -> "Ironman"). Local only. */
    public static synchronized void setLabel(String osrsName, String label) {
        if (osrsName == null || label == null || label.trim().isEmpty()) return;
        load().put(osrsName.trim().toLowerCase(), label.trim());
        save();
    }

    /** Every label we know about, for the "which character?" picker. */
    public static List<String> labels() {
        return new ArrayList<>(load().values());
    }

    /** The whole local mapping, for the privacy screen ("here's exactly what's on your disk"). */
    public static Map<String, String> all() {
        return new LinkedHashMap<>(load());
    }

    /** Forgets a character entirely. */
    public static synchronized void forget(String osrsName) {
        if (osrsName == null) return;
        load().remove(osrsName.trim().toLowerCase());
        save();
    }

    /** Forgets everything. */
    public static synchronized void forgetAll() {
        load().clear();
        save();
    }
}
