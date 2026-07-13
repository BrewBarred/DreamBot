package main.data.library;

import main.data.library.JLibrary;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.item.GroundItems;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.items.GroundItem;

import java.util.EnumSet;
import java.util.Set;

/**
 * LearningEngine scans the area around the player and registers any new
 * entities into the Library that aren't already known.
 *
 * ── HOW IT WORKS ────────────────────────────────────────────────────────────
 *
 *   Call {@link #tick()} from your bot's main loop (or on a timer). When
 *   learning is enabled, tick() will:
 *
 *     1. Fetch all entities of the enabled types within scan radius
 *     2. For each entity, check if it already exists in the Library
 *     3. If not → create a new entry, add it to the Library, append to file
 *
 *   Deduplication rules per type:
 *     - NPC:         unique by name (same NPC name = same entry, regardless of tile)
 *     - GameObject:  unique by name + tile (same object in two locations = two entries)
 *     - GroundItem:  unique by name + tile (same item, different spawn = two entries)
 *
 * ── SCAN TYPES ──────────────────────────────────────────────────────────────
 *
 *   Control what gets learned by passing a set of {@link ScanType} values to
 *   {@link #setEnabledTypes(Set)}. You can mix and match freely.
 *
 * ── AREA TAGGING ────────────────────────────────────────────────────────────
 *
 *   Learned entries are tagged with the current area label, which you can set
 *   via {@link #setCurrentArea(String)}. If not set, defaults to the player's
 *   coordinates as a fallback label.
 */
public class LearningEngine {

    // ── Scan types — controls which entity types get scanned each tick ────────

    public enum ScanType {
        NPCS,
        OBJECTS,
        GROUND_ITEMS
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private boolean       learning    = false;
    private int           scanRadius  = 15;    // tiles around the player to scan
    private Set<ScanType> enabledTypes = EnumSet.allOf(ScanType.class);
    private String        currentArea  = "Unknown";

    private int           discoveredThisTick = 0; // for UI feedback
    private int           totalDiscovered    = 0;

    private final JLibrary library;

    public LearningEngine(JLibrary library) {
        this.library = library;
    }

    // =========================================================================
    // MAIN TICK — call from your bot loop
    // =========================================================================

    /**
     * Processes one learning tick. Safe to call every game tick — does nothing
     * if learning is disabled.
     *
     * @return number of new entries discovered this tick (0 if learning is off)
     */
    public int tick() {
        Logger.log("[Learn] tick() called, learning=" + learning);
        if (!learning) return 0;

        discoveredThisTick = 0;
        Tile playerTile = Players.getLocal().getTile();

        if (enabledTypes.contains(ScanType.NPCS))
            scanNpcs(playerTile);

        if (enabledTypes.contains(ScanType.OBJECTS))
            scanObjects(playerTile);

        if (enabledTypes.contains(ScanType.GROUND_ITEMS))
            scanGroundItems(playerTile);

        totalDiscovered += discoveredThisTick;
        return discoveredThisTick;
    }

    // =========================================================================
    // SCANNING
    // =========================================================================

    /** Scans nearby NPCs and registers any not already in the library. */
    private void scanNpcs(Tile origin) {
        Logger.log("[Learn] scanNpcs called, found " + NPCs.all().size() + " npcs");
        for (NPC npc : NPCs.all()) {
            if (npc == null || npc.getName() == null) continue;
            if (npc.getTile() == null) continue;
            if (origin.distance(npc.getTile()) > scanRadius) continue;

            String name = npc.getName().trim();
            if (name.isEmpty()) continue;

            // Dedup: one entry per unique name
            if (library.hasNpc(name)) continue;

            String[] actions = npc.getActions();
            String interaction = (actions != null && actions.length > 0 && actions[0] != null)
                    ? actions[0] : "Talk-to";

            NpcEntry entry = new NpcEntry(name, npc.getTile(), currentArea, interaction, "learned");
            library.registerNpc(entry);
            Logger.log("[Learn] New NPC: " + name + " @ " + currentArea);
            discoveredThisTick++;
        }
    }

    /** Scans nearby GameObjects and registers any not already in the library. */
    private void scanObjects(Tile origin) {
        for (GameObject obj : GameObjects.all()) {
            if (obj == null || obj.getName() == null) continue;
            if (obj.getTile() == null) continue;
            if (origin.distance(obj.getTile()) > scanRadius) continue;

            String name = obj.getName().trim();
            if (name.isEmpty() || name.equals("null")) continue;

            // Dedup: one entry per name + tile combination
            if (library.hasObject(name, obj.getTile())) continue;

            String[] actions = obj.getActions();
            String interaction = (actions != null && actions.length > 0 && actions[0] != null)
                    ? actions[0] : "Examine";

            ObjectEntry entry = new ObjectEntry(name, obj.getTile(), currentArea, interaction, "learned");
            library.registerObject(entry);
            Logger.log("[Learn] New Object: " + name + " @ " + obj.getTile());
            discoveredThisTick++;
        }
    }

    /** Scans nearby GroundItems and registers any not already in the library. */
    private void scanGroundItems(Tile origin) {
        for (GroundItem item : GroundItems.all()) {
            if (item == null || item.getName() == null) continue;
            if (item.getTile() == null) continue;
            if (origin.distance(item.getTile()) > scanRadius) continue;

            String name = item.getName().trim();
            if (name.isEmpty()) continue;

            // Dedup: one entry per name + tile combination
            if (library.hasGroundItem(name, item.getTile())) continue;

            GroundItemEntry entry = new GroundItemEntry(name, item.getTile(), -1, "learned");
            library.registerGroundItem(entry);
            Logger.log("[Learn] New GroundItem: " + name + " @ " + item.getTile());
            discoveredThisTick++;
        }
    }

    // =========================================================================
    // CONFIGURATION
    // =========================================================================

    /** Enables or disables the learning engine. */
    public void setLearning(boolean enabled) {
        this.learning = enabled;
        Logger.log("[Learn] Learning " + (enabled ? "ENABLED" : "DISABLED"));
    }

    public boolean isLearning() { return learning; }

    /**
     * Sets which entity types to scan.
     * Example: setEnabledTypes(EnumSet.of(ScanType.NPCS, ScanType.OBJECTS))
     */
    public void setEnabledTypes(Set<ScanType> types) {
        this.enabledTypes = types;
    }

    public Set<ScanType> getEnabledTypes() { return enabledTypes; }

    /** Sets the radius (in tiles) around the player to scan. Default: 15. */
    public void setScanRadius(int radius) {
        this.scanRadius = Math.max(1, Math.min(radius, 50));
    }

    public int getScanRadius() { return scanRadius; }

    /**
     * Sets the area label to tag newly learned entries with.
     * Call this when the player enters a new named area.
     * e.g. "Varrock East Bank", "Lumbridge Castle", etc.
     */
    public void setCurrentArea(String area) {
        this.currentArea = (area != null && !area.isEmpty()) ? area : "Unknown";
    }

    public String getCurrentArea() { return currentArea; }

    /** Returns how many entries were discovered on the last tick. */
    public int getDiscoveredThisTick() { return discoveredThisTick; }

    /** Returns the total number of entries discovered since learning was started. */
    public int getTotalDiscovered() { return totalDiscovered; }

    /** Resets the total discovered counter. */
    public void resetCounter() { totalDiscovered = 0; }
}
