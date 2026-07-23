package main.data.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.dreambot.api.utilities.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Local, offline-first persistence for DreamMan.
 * <p>
 * Everything a character builds - queue, task library, presets, builder draft and settings -
 * is written to a single JSON file per character under the user's home directory:
 *
 * <pre>
 *   &lt;scripts.path&gt;/DreamMan/profiles/&lt;character&gt;/profile.json
 * </pre>
 *
 * This replaces the (now dead) Supabase round-trip as the source of truth. The optional server
 * sync added in a later patch layers on top of this - local always wins if the network is down.
 *
 * <h3>Why a home-directory path?</h3>
 * The old library code used a <em>relative</em> path ("main/data/library/"), which resolves
 * against whatever working directory the DreamBot client happens to launch from once the script
 * is packaged as a .jar - so saved files effectively vanished. An absolute per-user path is
 * stable no matter how the script is run.
 *
 * <h3>Crash-safe writes</h3>
 * We write to {@code profile.json.tmp} then atomically rename onto {@code profile.json}, so a
 * crash mid-write never corrupts the live file (same strategy the JLibrary's FileManager uses).
 */
public final class LocalStore {

    private LocalStore() {}

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final String PROFILE_FILE = "profile.json";

    /**
     * Root DreamMan data directory: {@code <scripts.path>/DreamMan}.
     *
     * <p>v1.32 (SDN compliance): the DreamBot Scripter Guidelines require that scripts only write
     * inside a subdirectory of {@code System.getProperty("scripts.path")} - not {@code user.home}
     * or any other location. This roots everything DreamMan persists (profiles, session, presets,
     * the asset cache) under the sanctioned path. Fallbacks keep unit tests / non-client runs
     * working: {@code scripts.path}, or the working directory when there's no client (tests).
     */
    public static File getRoot() {
        String scripts = System.getProperty("scripts.path");
        File base;
        if (scripts != null && !scripts.trim().isEmpty()) {
            base = new File(scripts.trim());
        } else {
            // v1.89 (SDN compliance): NO user.home fallback. The guidelines name it explicitly
            // as a path that must not be used, and a reviewer greps for it. Outside the client
            // (tests, tooling) we fall back to the working directory, which is scoped to
            // whatever launched us and never writes into someone's home folder.
            base = new File(".");
        }
        return new File(base, "DreamMan");
    }

    // v1.89 (SDN compliance): the pre-v1.32 legacy migration is GONE. It read from
    // <user.home>/DreamMan to recover data saved before the storage move, which meant this file
    // still touched user.home even though it never wrote there - and the guidelines call that
    // path out by name. The migration has had many releases to run for anyone who needed it,
    // and carrying a home-directory read into the SDN submission isn't worth the review risk.


    /** Recursively copies {@code src} into {@code dst}, OVERWRITING existing files. */
    private static void copyTree(File src, File dst) throws java.io.IOException {
        if (src.isDirectory()) {
            dst.mkdirs();
            String[] kids = src.list();
            if (kids == null) return;
            for (String k : kids) copyTree(new File(src, k), new File(dst, k));
        } else if (src.isFile()) {
            java.nio.file.Files.copy(src.toPath(), dst.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Directory holding all per-character profiles. */
    public static File getProfilesDir() {
        return new File(getRoot(), "profiles");
    }

    /**
     * v1.32b: logical, predictable folders for the file dialogs - both under the script data
     * root so import/export always open somewhere sensible instead of a random last-used path.
     * Pointing the choosers at a small local folder also sidesteps the multi-second freeze the
     * default chooser hits while enumerating Windows shell/network locations.
     */
    public static File getExportsDir() { File d = new File(getRoot(), "exports"); d.mkdirs(); return d; }
    public static File getImportsDir() { File d = new File(getRoot(), "imports"); d.mkdirs(); return d; }

    /** Directory for a single character's data (created on demand). */
    public static File getProfileDir(String character) {
        return new File(getProfilesDir(), sanitize(character));
    }

    // =========================================================================
    // SAVE / LOAD
    // =========================================================================

    /**
     * Writes a character's full profile to disk atomically.
     *
     * @return true on success, false if anything went wrong (caller can surface this).
     */
    public static boolean save(String character, ProfileData data) {
        if (character == null || character.isEmpty() || data == null) {
            Logger.log(Logger.LogType.ERROR, "[LocalStore] save() called with no character/data.");
            return false;
        }

        data.character = character;
        data.savedAt = System.currentTimeMillis();
        data.version = ProfileData.CURRENT_VERSION;

        File dir = getProfileDir(character);
        if (!dir.exists() && !dir.mkdirs()) {
            Logger.log(Logger.LogType.ERROR, "[LocalStore] Could not create profile dir: " + dir);
            return false;
        }

        File target = new File(dir, PROFILE_FILE);
        File tmp = new File(dir, PROFILE_FILE + ".tmp");

        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            GSON.toJson(data, w);
        } catch (Exception e) {
            Logger.log(Logger.LogType.ERROR, "[LocalStore] Failed writing temp profile: " + e.getMessage());
            return false;
        }

        // Patch B.1: keep a one-deep backup of the previous profile before every overwrite,
        // so any save - including a deliberate save of an empty workspace - is recoverable
        // from profile.json.bak in the same folder.
        if (target.exists()) {
            try {
                Files.copy(target.toPath(), new File(dir, PROFILE_FILE + ".bak").toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException backupFailed) {
                Logger.log(Logger.LogType.WARN,
                        "[LocalStore] Could not write backup: " + backupFailed.getMessage());
            }
        }

        try {
            Files.move(tmp.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            // e.g. cross-filesystem - fall back to a plain replace
            try {
                Files.copy(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                tmp.delete();
            } catch (IOException e) {
                Logger.log(Logger.LogType.ERROR, "[LocalStore] Could not save profile: " + e.getMessage());
                return false;
            }
        }

        Logger.log(Logger.LogType.INFO, "[LocalStore] Saved profile for '" + character + "' -> " + target);
        return true;
    }

    /**
     * Loads a character's profile, or returns {@code null} if none exists yet (a brand-new
     * character) or if the file is unreadable/corrupt. Callers treat null as "start empty".
     */
    public static ProfileData load(String character) {
        if (character == null || character.isEmpty())
            return null;

        File file = new File(getProfileDir(character), PROFILE_FILE);
        if (!file.exists()) {
            Logger.log(Logger.LogType.INFO, "[LocalStore] No saved profile for '" + character + "' yet.");
            return null;
        }

        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            ProfileData data = GSON.fromJson(r, ProfileData.class);
            if (data == null)
                return null;
            data = migrate(data);
            Logger.log(Logger.LogType.INFO, "[LocalStore] Loaded profile for '" + character + "'.");
            return data;
        } catch (Exception e) {
            Logger.log(Logger.LogType.ERROR, "[LocalStore] Failed reading profile (starting empty): " + e.getMessage());
            return null;
        }
    }

    /** Migrates older profile shapes forward. No-op today (only v1 exists). */
    private static ProfileData migrate(ProfileData data) {
        // Future: if (data.version < 2) { ...transform...; data.version = 2; }
        return data;
    }

    // =========================================================================
    // IMPORT / EXPORT  (single task or preset as a shareable .json)
    // =========================================================================

    /** Serialises any DTO (e.g. a {@link TaskData} or {@link PresetData}) to a chosen file. */
    public static boolean exportToFile(Object dto, File file) {
        if (dto == null || file == null)
            return false;
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(dto, w);
            Logger.log(Logger.LogType.INFO, "[LocalStore] Exported -> " + file);
            return true;
        } catch (Exception e) {
            Logger.log(Logger.LogType.ERROR, "[LocalStore] Export failed: " + e.getMessage());
            return false;
        }
    }

    /** Reads a DTO of the given type back from a file, or null on failure. */
    public static <T> T importFromFile(File file, Class<T> type) {
        if (file == null || !file.exists())
            return null;
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, type);
        } catch (Exception e) {
            Logger.log(Logger.LogType.ERROR, "[LocalStore] Import failed: " + e.getMessage());
            return null;
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /** Makes a name safe to use as a folder or file name across OSes. */
    public static String sanitize(String name) {
        if (name == null || name.trim().isEmpty())
            return "default";
        String cleaned = name.trim().replaceAll("[^A-Za-z0-9._ -]", "_");
        return cleaned.isEmpty() ? "default" : cleaned;
    }
}
