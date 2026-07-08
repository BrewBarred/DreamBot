package main.data.library;

import org.dreambot.api.methods.item.GroundItems;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.wrappers.items.GroundItem;

/**
 * Represents a known ground item spawn in the library.
 *
 * Ground items are identified by name + spawn tile. Multiple entries with the
 * same name but different tiles are distinct spawns (e.g. Cabbage in two locations).
 *
 * File format (one entry per line):
 *   name, x, y, z, respawnSeconds
 *   e.g. "Air rune, 3244, 3162, 0, 30"
 *
 * respawnSeconds = 0 means the item is always present (no respawn needed).
 * respawnSeconds = -1 means unknown.
 */
public class GroundItemEntry {

    public String name;
    public Tile   spawnTile;
    public int    respawnSeconds;   // 0 = always present, -1 = unknown
    public String source;           // "file" | "learned"

    public GroundItemEntry(String name, Tile spawnTile, int respawnSeconds, String source) {
        this.name           = name;
        this.spawnTile      = spawnTile;
        this.respawnSeconds = respawnSeconds;
        this.source         = source;
    }

    // -------------------------------------------------------------------------
    // Live game access
    // -------------------------------------------------------------------------

    /**
     * Searches the current scene for the closest ground item matching this name.
     * Returns null if not currently visible.
     */
    public GroundItem getEntity() {
        return GroundItems.closest(i -> i != null
                && i.getName() != null
                && i.getName().equalsIgnoreCase(name));
    }

    /**
     * Walks toward the live item if visible, otherwise walks to the stored spawn tile.
     */
    public boolean walkTo() {
        GroundItem live = getEntity();
        if (live != null) {
            Walking.walk(live.getTile());
            return true;
        }
        return Walking.walk(spawnTile);
    }

    // -------------------------------------------------------------------------
    // Serialisation
    // -------------------------------------------------------------------------

    /** Converts this entry to the text file line format. */
    public String toFileLine() {
        return String.format("%s, %d, %d, %d, %d",
                name,
                spawnTile.getX(), spawnTile.getY(), spawnTile.getZ(),
                respawnSeconds);
    }

    @Override
    public String toString() {
        return "ITEM: " + name + " @ (" + spawnTile.getX() + ", " + spawnTile.getY() + ")";
    }
}
