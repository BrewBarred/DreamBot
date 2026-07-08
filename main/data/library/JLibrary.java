package main.data.library;

import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.interactive.GameObject;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static main.actions.Action.parseStringIntoTile;

/**
 * JLibrary is the central data store for all known game entities.
 *
 * It replaces the previous enum-based system with a runtime-growable structure
 * backed by plain text files, allowing:
 *
 *   ✓ Human-editable data files (no recompile to add entries)
 *   ✓ Automatic discovery via LearningEngine
 *   ✓ Fast indexed search across all types
 *   ✓ Typed fetch methods that return live-ready objects
 *   ✓ Fuzzy / smart string resolution (coordinate → NPC → object → fuzzy)
 *   ✓ Safe atomic file persistence
 *
 * ── USAGE ───────────────────────────────────────────────────────────────────
 *
 *   JLibrary library = JLibrary.getInstance();
 *   library.load();                          // call once at startup
 *
 *   library.getLearner().setLearning(true);  // start learning mode
 *   library.getLearner().tick();             // call in your bot loop
 *
 *   library.search("banker")                 // List<String> for JList display
 *   library.getNpc("Banker")                 // NpcEntry, ready to use
 *   library.resolveToTile("3208, 3221, 2")   // Tile from any string
 *   library.saveAll()                        // safe full rewrite to disk
 *
 * ── SINGLETON ───────────────────────────────────────────────────────────────
 *
 *   JLibrary is a singleton. Call {@link #getInstance()} everywhere.
 *   This ensures the learning engine, search indexes, and file state are
 *   always in sync across the whole application.
 *
 * ── SEARCH vs FETCH ─────────────────────────────────────────────────────────
 *
 *   search*(query)  → returns List<String>, formatted for JList display
 *   get*(name)      → returns the typed entry object, ready to call .walkTo() etc.
 *   resolveToTile() → smart resolution of any string to a Tile
 */
public final class JLibrary {

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static JLibrary instance;

    public static JLibrary getInstance() {
        if (instance == null)
            instance = new JLibrary();
        return instance;
    }

    private JLibrary() {
        learner = new LearningEngine(this);
    }

    // ── Entity type flags (for filtered search) ───────────────────────────────

    public enum TargetType {
        COORDINATE, NPC, GAME_OBJECT, GROUND_ITEM, INVENTORY_ITEM, UNKNOWN
    }

    // ── Data stores ───────────────────────────────────────────────────────────

    private final List<NpcEntry>        npcs        = new ArrayList<>();
    private final List<ObjectEntry>     objects     = new ArrayList<>();
    private final List<GroundItemEntry> groundItems = new ArrayList<>();

    // ── Learning engine ───────────────────────────────────────────────────────

    private final LearningEngine learner;

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    /**
     * Loads all data from the default file paths.
     * Call once at bot startup, before any other JLibrary methods.
     */
    public void load() {
        npcs.clear();
        objects.clear();
        groundItems.clear();

        npcs.addAll(FileManager.loadNpcs(FileManager.getNpcFile()));
        objects.addAll(FileManager.loadObjects(FileManager.getObjectFile()));
        groundItems.addAll(FileManager.loadGroundItems(FileManager.getItemFile()));

        Logger.log("[JLibrary] Loaded — NPCs: " + npcs.size()
                + ", Objects: " + objects.size()
                + ", GroundItems: " + groundItems.size());
    }

    /**
     * Reloads all data from disk, discarding any runtime-only state.
     * Useful after the user has manually edited a text file.
     */
    public void reload() {
        Logger.log("[JLibrary] Reloading from disk...");
        load();
    }

    /**
     * Performs a full safe rewrite of all three data files.
     * Call on bot shutdown, or when the user clicks "Save" in the UI.
     * Uses atomic .tmp → rename strategy (see FileManager).
     */
    public void saveAll() {
        FileManager.persistNpcs(npcs);
        FileManager.persistObjects(objects);
        FileManager.persistGroundItems(groundItems);
        Logger.log("[JLibrary] All files saved.");
    }

    // =========================================================================
    // REGISTRATION (called by LearningEngine and externally)
    // =========================================================================

    /**
     * Adds an NPC entry to the in-memory store and immediately appends it to
     * the file. Does NOT check for duplicates — call {@link #hasNpc} first.
     */
    public void registerNpc(NpcEntry entry) {
        npcs.add(entry);
        FileManager.appendNpc(entry);
    }

    /**
     * Adds an Object entry to the in-memory store and appends to file.
     * Does NOT check for duplicates — call {@link #hasObject} first.
     */
    public void registerObject(ObjectEntry entry) {
        objects.add(entry);
        FileManager.appendObject(entry);
    }

    /**
     * Adds a GroundItem entry to the in-memory store and appends to file.
     * Does NOT check for duplicates — call {@link #hasGroundItem} first.
     */
    public void registerGroundItem(GroundItemEntry entry) {
        groundItems.add(entry);
        FileManager.appendGroundItem(entry);
    }

    // =========================================================================
    // DEDUPLICATION CHECKS (used by LearningEngine)
    // =========================================================================

    /** Returns true if an NPC with this name already exists in the library. */
    public boolean hasNpc(String name) {
        return npcs.stream().anyMatch(n -> n.name.equalsIgnoreCase(name));
    }

    /**
     * Returns true if an object with this name at this exact tile already exists.
     * Same name at a different tile = different entry (e.g. two oak trees).
     */
    public boolean hasObject(String name, Tile tile) {
        return objects.stream().anyMatch(o ->
                o.name.equalsIgnoreCase(name)
                        && o.tile.getX() == tile.getX()
                        && o.tile.getY() == tile.getY()
                        && o.tile.getZ() == tile.getZ());
    }

    /**
     * Returns true if a ground item with this name at this exact tile already exists.
     */
    public boolean hasGroundItem(String name, Tile tile) {
        return groundItems.stream().anyMatch(i ->
                i.name.equalsIgnoreCase(name)
                        && i.spawnTile.getX() == tile.getX()
                        && i.spawnTile.getY() == tile.getY()
                        && i.spawnTile.getZ() == tile.getZ());
    }

    // =========================================================================
    // FETCH METHODS — return typed entries, ready to use
    // =========================================================================

    /**
     * Returns the first NPC entry matching the given name (case-insensitive).
     * Returns null if not found.
     */
    public NpcEntry getNpc(String name) {
        return npcs.stream()
                .filter(n -> n.name.equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    /**
     * Returns the first object entry matching the given name.
     * Returns null if not found.
     */
    public ObjectEntry getObject(String name) {
        return objects.stream()
                .filter(o -> o.name.equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    /**
     * Returns the first ground item entry matching the given name.
     * Returns null if not found.
     */
    public GroundItemEntry getGroundItem(String name) {
        return groundItems.stream()
                .filter(i -> i.name.equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    /** Returns all NPC entries with names containing the query (case-insensitive). */
    public List<NpcEntry> getNpcsMatching(String query) {
        String q = query.toLowerCase();
        return npcs.stream()
                .filter(n -> n.name.toLowerCase().contains(q))
                .collect(Collectors.toList());
    }

    /** Returns all object entries with names containing the query. */
    public List<ObjectEntry> getObjectsMatching(String query) {
        String q = query.toLowerCase();
        return objects.stream()
                .filter(o -> o.name.toLowerCase().contains(q))
                .collect(Collectors.toList());
    }

    /** Returns all ground item entries with names containing the query. */
    public List<GroundItemEntry> getGroundItemsMatching(String query) {
        String q = query.toLowerCase();
        return groundItems.stream()
                .filter(i -> i.name.toLowerCase().contains(q))
                .collect(Collectors.toList());
    }

    // =========================================================================
    // SEARCH METHODS — return List<String> for JList display
    // =========================================================================

    /**
     * Searches across all types. Returns formatted strings for display.
     * Each result is prefixed with its type: "NPC: Banker", "OBJ: Oak", etc.
     */
    public List<String> search(String query) {
        return search(query, TargetType.NPC, TargetType.GAME_OBJECT, TargetType.GROUND_ITEM);
    }

    /**
     * Searches across the specified types only.
     * Pass no types to search everything.
     */
    public List<String> search(String query, TargetType... types) {
        String q = query.toLowerCase();
        List<String> results = new ArrayList<>();
        Set<TargetType> filter = new HashSet<>(Arrays.asList(types));
        boolean all = filter.isEmpty();

        if (all || filter.contains(TargetType.NPC)) {
            npcs.stream()
                    .filter(n -> n.name.toLowerCase().contains(q))
                    .forEach(n -> results.add("NPC: " + n.name + " — " + n.area));
        }

        if (all || filter.contains(TargetType.GAME_OBJECT)) {
            objects.stream()
                    .filter(o -> o.name.toLowerCase().contains(q))
                    .forEach(o -> results.add("OBJ: " + o.name + " — " + o.area));
        }

        if (all || filter.contains(TargetType.GROUND_ITEM)) {
            groundItems.stream()
                    .filter(i -> i.name.toLowerCase().contains(q))
                    .forEach(i -> results.add("ITEM: " + i.name
                            + " @ (" + i.spawnTile.getX() + ", " + i.spawnTile.getY() + ")"));
        }

        if (all || filter.contains(TargetType.INVENTORY_ITEM)) {
            org.dreambot.api.methods.container.impl.Inventory.all().stream()
                    .filter(i -> i != null && i.getName() != null && i.getName().toLowerCase().contains(q))
                    .map(i -> "INV: " + i.getName())
                    .distinct()
                    .forEach(results::add);
        }

        return results;
    }

    /**
     * Fuzzy search — returns the best matching entry name across all types,
     * ranked by Levenshtein edit distance. Used when exact/contains search fails.
     *
     * @param query  the string to match against
     * @param limit  maximum number of results to return
     */
    public List<String> fuzzySearch(String query, int limit) {
        String q = query.toLowerCase();
        List<ScoredResult> scored = new ArrayList<>();

        npcs.forEach(n -> scored.add(new ScoredResult(
                "NPC: " + n.name + " — " + n.area, levenshtein(q, n.name.toLowerCase()))));
        objects.forEach(o -> scored.add(new ScoredResult(
                "OBJ: " + o.name + " — " + o.area, levenshtein(q, o.name.toLowerCase()))));
        groundItems.forEach(i -> scored.add(new ScoredResult(
                "ITEM: " + i.name, levenshtein(q, i.name.toLowerCase()))));

        return scored.stream()
                .sorted(Comparator.comparingInt(r -> r.score))
                .limit(limit)
                .map(r -> r.label)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // SMART RESOLUTION — the single entry point for "give me a tile for X"
    // =========================================================================

    /**
     * Resolves any string to a Tile using a priority pipeline:
     *
     *   1. Coordinate format (two commas → guaranteed tile)   e.g. "3208, 3221, 2"
     *   2. Live NPC in scene matching name exactly
     *   3. Live GameObject in scene matching name exactly
     *   4. JLibrary NPC entry (uses live tile if visible, stored tile as fallback)
     *   5. JLibrary Object entry
     *   6. JLibrary GroundItem entry (spawn tile)
     *   7. Fuzzy best match across all library entries
     *
     * Returns null only if absolutely nothing can be resolved.
     */
    public Tile resolveToTile(String target) {
        if (target == null || target.isEmpty()) return null;

        // 1. Coordinate format — two commas = guaranteed tile, no ambiguity
        Tile coord = parseStringIntoTile(target);
        if (coord != null) return coord;

        String trimmed = target.trim();

        // 2. Live NPC in current scene
        NPC liveNpc = NPCs.closest(n -> n != null
                && n.getName() != null
                && n.getName().equalsIgnoreCase(trimmed));
        if (liveNpc != null) return liveNpc.getTile();

        // 3. Live GameObject in current scene
        GameObject liveObj = org.dreambot.api.methods.interactive.GameObjects.closest(o -> o != null
                && o.getName() != null
                && o.getName().equalsIgnoreCase(trimmed));
        if (liveObj != null) return liveObj.getTile();

        // 4. JLibrary NPC (live tile preferred, stored fallback)
        NpcEntry npcEntry = getNpc(trimmed);
        if (npcEntry != null) return npcEntry.getLiveTile();

        // 5. JLibrary Object
        ObjectEntry objEntry = getObject(trimmed);
        if (objEntry != null) return objEntry.getLiveTile();

        // 6. JLibrary GroundItem spawn tile
        GroundItemEntry itemEntry = getGroundItem(trimmed);
        if (itemEntry != null) return itemEntry.spawnTile;

        // 7. Fuzzy fallback — find the closest name match and resolve that
        List<String> fuzzy = fuzzySearch(trimmed, 1);
        if (!fuzzy.isEmpty()) {
            String bestLabel = fuzzy.get(0);
            String bestName  = bestLabel.replaceFirst("^(NPC|OBJ|ITEM): ", "").split(" — ")[0].trim();
            Logger.log("[JLibrary] Fuzzy resolved '" + target + "' → '" + bestName + "'");
            // Recurse once with the resolved name (won't infinite loop — fuzzy not called again)
            return resolveToTile(bestName);
        }

        Logger.log("[JLibrary] Unable to resolve target: " + target);
        return null;
    }

    /**
     * Auto-classifies a raw input string and returns its most likely TargetType.
     * Useful for action routing (e.g. Walk needs a tile; Interact needs an entity).
     */
    public TargetType classify(String input) {
        if (input == null || input.isEmpty()) return TargetType.UNKNOWN;
        if (parseStringIntoTile(input) != null)    return TargetType.COORDINATE;
        if (getNpc(input) != null)                 return TargetType.NPC;
        if (getObject(input) != null)              return TargetType.GAME_OBJECT;
        if (getGroundItem(input) != null)          return TargetType.GROUND_ITEM;

        // Live scene fallback
        if (NPCs.closest(n -> n != null && n.getName() != null
                && n.getName().equalsIgnoreCase(input)) != null) return TargetType.NPC;
        if (org.dreambot.api.methods.interactive.GameObjects.closest(o -> o != null
                && o.getName() != null
                && o.getName().equalsIgnoreCase(input)) != null) return TargetType.GAME_OBJECT;

        return TargetType.UNKNOWN;
    }

    // =========================================================================
    // UTILITY
    // =========================================================================

    /**
     * Calculates the walking distance from the player to a resolved target.
     * Returns Integer.MAX_VALUE if the target cannot be resolved.
     */
    public int getDistanceTo(String target) {
        Tile dest    = resolveToTile(target);
        Tile myTile  = Players.getLocal().getTile();
        if (dest == null || myTile == null) return Integer.MAX_VALUE;
        return (int) myTile.walkingDistance(dest);
    }

    /** Returns a copy of all NPC entries (safe to iterate/display). */
    public List<NpcEntry> getAllNpcs() { return Collections.unmodifiableList(npcs); }

    /** Returns a copy of all Object entries. */
    public List<ObjectEntry> getAllObjects() { return Collections.unmodifiableList(objects); }

    /** Returns a copy of all GroundItem entries. */
    public List<GroundItemEntry> getAllGroundItems() { return Collections.unmodifiableList(groundItems); }

    /** Returns the total number of entries across all types. */
    public int getTotalCount() { return npcs.size() + objects.size() + groundItems.size(); }

    /** Access the LearningEngine to configure or tick it. */
    public LearningEngine getLearner() { return learner; }

    // ── Levenshtein edit distance ─────────────────────────────────────────────

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            int[] curr = new int[b.length() + 1];
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            prev = curr;
        }
        return prev[b.length()];
    }

    // ── Internal scoring helper for fuzzy search ──────────────────────────────

    private static class ScoredResult {
        final String label;
        final int    score;
        ScoredResult(String label, int score) { this.label = label; this.score = score; }
    }
}
