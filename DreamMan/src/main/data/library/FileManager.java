package main.data.library;

import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.utilities.Logger;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * FileManager handles all reading and writing for the JLibrary's text files.
 *
 * ── FILE FORMAT ─────────────────────────────────────────────────────────────
 *
 *   npcs.txt         →  name, x, y, z, area, interaction
 *   objects.txt      →  name, x, y, z, area, interaction
 *   ground_items.txt  →  name, x, y, z, respawnSeconds
 *
 *   Lines beginning with '#' are comments and are ignored.
 *   Blank lines are ignored.
 *
 * ── SAFE WRITE STRATEGY ─────────────────────────────────────────────────────
 *
 *   To avoid file corruption during writes (e.g. bot crash mid-write), we use
 *   a dual-buffer approach:
 *
 *     1. Write the full updated contents to  <file>.tmp
 *     2. Atomically move (rename) .tmp  →  <file>
 *
 *   This means the live file is never left in a half-written state. On most
 *   systems the rename is atomic at the OS level. If the bot dies during step 1,
 *   the original file is untouched. If it dies during step 2, the .tmp survives
 *   and is cleaned up on next load.
 *
 * ── APPEND STRATEGY ─────────────────────────────────────────────────────────
 *
 *   For single new entries discovered during learning, we append directly to
 *   the live file. This is fast and safe for single-line appends. A full
 *   rewrite (using the safe strategy above) is only triggered when the JLibrary
 *   calls persistAll(), e.g. on shutdown or manual save.
 */
public final class FileManager {

    private FileManager() {}

    /**
     * Root library directory. Now an ABSOLUTE path under the user's home directory:
     * {@code <user.home>/DreamMan/library/}.
     * <p>
     * Previously this was the relative path {@code "main/data/library/"}, which resolves against
     * whatever working directory the DreamBot client launches from - so once the script ran as a
     * packaged .jar, learned NPCs/objects were written somewhere unexpected (or lost). An absolute
     * path fixes that regardless of how the script is launched, and keeps all DreamMan data
     * (profiles + library) together in one place.
     */
    public static final String DIR = resolveRootDir();

    /** Legacy relative location, kept only so first-run migration can pull old data forward. */
    private static final String LEGACY_DIR = "main/data/library/";

    /** The currently active collection name. Defaults to "default". */
    private static String activeCollection = "default";

    private static String resolveRootDir() {
        String home = System.getProperty("user.home");
        if (home == null || home.isEmpty())
            home = ".";
        String path = new java.io.File(home, "DreamMan/library").getPath();
        return path.endsWith(java.io.File.separator) ? path : path + java.io.File.separator;
    }

    /**
     * Sets the active collection. All subsequent reads/writes will use this
     * collection's subdirectory. Creates the directory if needed and, on first run,
     * seeds it from the old relative location if that still has data.
     */
    public static void setCollection(String name) {
        activeCollection = (name != null && !name.isEmpty()) ? name : "default";
        new java.io.File(getCollectionDir()).mkdirs();
        migrateLegacyIfNeeded();
    }

    /** Returns the name of the currently active collection. */
    public static String getCollection() { return activeCollection; }

    /** Returns the full path to the active collection directory. */
    public static String getCollectionDir() { return DIR + activeCollection + "/"; }

    /**
     * One-time best-effort migration: if the new collection directory has no data files yet but
     * the old relative {@code main/data/library/<collection>/} does, copy them across. This means
     * anyone upgrading keeps the library they already built, without losing anything (the old
     * files are copied, not moved).
     */
    private static void migrateLegacyIfNeeded() {
        try {
            java.io.File newNpc = new java.io.File(getNpcFile());
            java.io.File newObj = new java.io.File(getObjectFile());
            java.io.File newItm = new java.io.File(getItemFile());
            boolean newHasData = newNpc.exists() || newObj.exists() || newItm.exists();
            if (newHasData)
                return;

            java.io.File legacyDir = new java.io.File(LEGACY_DIR + activeCollection + "/");
            if (!legacyDir.isDirectory())
                return;

            copyIfPresent(new java.io.File(legacyDir, "npcs.txt"), newNpc);
            copyIfPresent(new java.io.File(legacyDir, "objects.txt"), newObj);
            copyIfPresent(new java.io.File(legacyDir, "ground_items.txt"), newItm);
            Logger.log("[JLibrary] Migrated existing '" + activeCollection + "' library to " + getCollectionDir());
        } catch (Exception e) {
            Logger.log("[JLibrary] Legacy library migration skipped: " + e.getMessage());
        }
    }

    private static void copyIfPresent(java.io.File from, java.io.File to) {
        if (!from.exists())
            return;
        try {
            ensureDirectoryExists(to);
            Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Logger.log("[JLibrary] Could not migrate " + from + ": " + e.getMessage());
        }
    }

    // ── File paths — always relative to the active collection ─────────────────
    public static String getNpcFile()    { return getCollectionDir() + "npcs.txt"; }
    public static String getObjectFile() { return getCollectionDir() + "objects.txt"; }
    public static String getItemFile()   { return getCollectionDir() + "ground_items.txt"; }

    // =========================================================================
    // LOADING
    // =========================================================================

    /**
     * Loads all NPC entries from npcs.txt.
     * Skips malformed lines and logs a warning for each.
     */
    public static List<NpcEntry> loadNpcs(String filePath) {
        List<NpcEntry> entries = new ArrayList<>();
        for (String line : readLines(filePath)) {
            try {
                String[] p = splitLine(line, 6);
                entries.add(new NpcEntry(
                        p[0].trim(),
                        new Tile(parseInt(p[1]), parseInt(p[2]), parseInt(p[3])),
                        p[4].trim(),
                        p[5].trim(),
                        "file"
                ));
            } catch (Exception e) {
                Logger.log("[JLibrary] Skipping malformed NPC line: " + line);
            }
        }
        Logger.log("[JLibrary] Loaded " + entries.size() + " NPCs from " + filePath);
        return entries;
    }

    /**
     * Loads all object entries from objects.txt.
     */
    public static List<ObjectEntry> loadObjects(String filePath) {
        List<ObjectEntry> entries = new ArrayList<>();
        for (String line : readLines(filePath)) {
            try {
                String[] p = splitLine(line, 6);
                entries.add(new ObjectEntry(
                        p[0].trim(),
                        new Tile(parseInt(p[1]), parseInt(p[2]), parseInt(p[3])),
                        p[4].trim(),
                        p[5].trim(),
                        "file"
                ));
            } catch (Exception e) {
                Logger.log("[JLibrary] Skipping malformed Object line: " + line);
            }
        }
        Logger.log("[JLibrary] Loaded " + entries.size() + " Objects from " + filePath);
        return entries;
    }

    /**
     * Loads all ground item entries from ground_items.txt.
     */
    public static List<GroundItemEntry> loadGroundItems(String filePath) {
        List<GroundItemEntry> entries = new ArrayList<>();
        for (String line : readLines(filePath)) {
            try {
                String[] p = splitLine(line, 5);
                entries.add(new GroundItemEntry(
                        p[0].trim(),
                        new Tile(parseInt(p[1]), parseInt(p[2]), parseInt(p[3])),
                        parseInt(p[4]),
                        "file"
                ));
            } catch (Exception e) {
                Logger.log("[JLibrary] Skipping malformed GroundItem line: " + line);
            }
        }
        Logger.log("[JLibrary] Loaded " + entries.size() + " GroundItems from " + filePath);
        return entries;
    }

    // =========================================================================
    // APPENDING (fast single-entry write during learning)
    // =========================================================================

    /** Appends a single NPC entry to the live file immediately. */
    public static void appendNpc(NpcEntry entry) {
        appendLine(getNpcFile(), entry.toFileLine());
    }

    /** Appends a single Object entry to the live file immediately. */
    public static void appendObject(ObjectEntry entry) {
        appendLine(getObjectFile(), entry.toFileLine());
    }

    /** Appends a single GroundItem entry to the live file immediately. */
    public static void appendGroundItem(GroundItemEntry entry) {
        appendLine(getItemFile(), entry.toFileLine());
    }

    // =========================================================================
    // FULL REWRITE (safe atomic write, used on shutdown / manual save)
    // =========================================================================

    /**
     * Rewrites npcs.txt with the full current list.
     * Uses the .tmp → rename strategy to prevent corruption.
     */
    public static void persistNpcs(List<NpcEntry> entries) {
        List<String> lines = new ArrayList<>();
        lines.add("# NPC JLibrary — auto-managed, manual edits are safe");
        lines.add("# Format: name, x, y, z, area, interaction");
        lines.add("");
        entries.forEach(e -> lines.add(e.toFileLine()));
        safeWrite(getNpcFile(), lines);
    }

    /**
     * Rewrites objects.txt with the full current list.
     */
    public static void persistObjects(List<ObjectEntry> entries) {
        List<String> lines = new ArrayList<>();
        lines.add("# Object JLibrary — auto-managed, manual edits are safe");
        lines.add("# Format: name, x, y, z, area, interaction");
        lines.add("");
        entries.forEach(e -> lines.add(e.toFileLine()));
        safeWrite(getObjectFile(), lines);
    }

    /**
     * Rewrites ground_items.txt with the full current list.
     */
    public static void persistGroundItems(List<GroundItemEntry> entries) {
        List<String> lines = new ArrayList<>();
        lines.add("# Ground Item JLibrary — auto-managed, manual edits are safe");
        lines.add("# Format: name, x, y, z, respawnSeconds");
        lines.add("");
        entries.forEach(e -> lines.add(e.toFileLine()));
        safeWrite(getItemFile(), lines);
    }

    // =========================================================================
    // INTERNAL HELPERS
    // =========================================================================

    /**
     * Reads all non-blank, non-comment lines from a file.
     * Creates the file (with a header comment) if it doesn't exist yet.
     */
    private static List<String> readLines(String filePath) {
        List<String> lines = new ArrayList<>();
        File file = new File(filePath);

        if (!file.exists()) {
            ensureDirectoryExists(file);
            try {
                file.createNewFile();
                Logger.log("[JLibrary] Created new file: " + filePath);
            } catch (IOException e) {
                Logger.log("[JLibrary] Could not create file: " + filePath);
                return lines;
            }
        }

        // Clean up any leftover .tmp from a previous crashed write
        File tmp = new File(filePath + ".tmp");
        if (tmp.exists()) tmp.delete();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#"))
                    lines.add(trimmed);
            }
        } catch (IOException e) {
            Logger.log("[JLibrary] Error reading file: " + filePath + " — " + e.getMessage());
        }
        return lines;
    }

    /**
     * Appends a single line to the end of a file.
     * Fast path for learning-mode discoveries.
     */
    private static void appendLine(String filePath, String line) {
        File file = new File(filePath);
        ensureDirectoryExists(file);
        try (FileWriter fw = new FileWriter(file, true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write(line);
            bw.newLine();
        } catch (IOException e) {
            Logger.log("[JLibrary] Error appending to " + filePath + ": " + e.getMessage());
        }
    }

    /**
     * Writes lines to <filePath>.tmp, then atomically renames it to <filePath>.
     * If the rename fails (cross-device, permissions), falls back to direct write.
     */
    private static void safeWrite(String filePath, List<String> lines) {
        File target = new File(filePath);
        File tmp    = new File(filePath + ".tmp");

        ensureDirectoryExists(target);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tmp))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            Logger.log("[JLibrary] Error writing temp file: " + e.getMessage());
            return;
        }

        // Atomic rename: .tmp → target
        try {
            Files.move(tmp.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            Logger.log("[JLibrary] Saved: " + filePath);
        } catch (IOException e) {
            // Atomic move failed (e.g. cross-filesystem) — try regular rename
            if (!tmp.renameTo(target)) {
                Logger.log("[JLibrary] Rename failed, using copy fallback for: " + filePath);
                try {
                    Files.copy(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    tmp.delete();
                } catch (IOException ex) {
                    Logger.log("[JLibrary] Critical: Could not save " + filePath + ": " + ex.getMessage());
                }
            }
        }
    }

    /** Ensures the parent directory of a file exists, creating it if needed. */
    private static void ensureDirectoryExists(File file) {
        File dir = file.getParentFile();
        if (dir != null && !dir.exists())
            dir.mkdirs();
    }

    /**
     * Splits a CSV line into exactly {@code expectedParts} parts.
     * Throws IllegalArgumentException if part count doesn't match.
     */
    private static String[] splitLine(String line, int expectedParts) {
        String[] parts = line.split(",", expectedParts);
        if (parts.length != expectedParts)
            throw new IllegalArgumentException(
                    "Expected " + expectedParts + " fields, got " + parts.length + " in: " + line);
        return parts;
    }

    private static int parseInt(String s) {
        return Integer.parseInt(s.trim());
    }
}
