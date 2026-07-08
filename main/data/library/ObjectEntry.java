package main.data.library;

import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.wrappers.interactive.GameObject;

/**
 * Represents a known game object in the library (trees, rocks, doors, chests, etc.).
 *
 * Unlike NPCs, objects don't roam — their tile IS their identity. Two objects
 * with the same name at different tiles are stored as separate entries.
 *
 * File format (one entry per line):
 *   name, x, y, z, area, interaction
 *   e.g. "Oak, 3203, 3425, 0, Varrock, Chop"
 *
 * If x/y/z are unknown (hand-added entries with no tile), they are stored as 0,0,0
 * and getLiveTile() will search the scene by name only.
 */
public class ObjectEntry {

    public String name;
    public Tile   tile;          // Known tile, or 0,0,0 if unknown
    public String area;
    public String interaction;   // Primary action e.g. "Chop", "Mine", "Open"
    public String source;        // "file" | "learned"

    public ObjectEntry(String name, Tile tile, String area, String interaction, String source) {
        this.name        = name;
        this.tile        = tile;
        this.area        = area;
        this.interaction = interaction;
        this.source      = source;
    }

    // -------------------------------------------------------------------------
    // Live game access
    // -------------------------------------------------------------------------

    /**
     * Finds the closest matching game object in the current scene by name.
     * Returns null if none is visible.
     */
    public GameObject getEntity() {
        return GameObjects.closest(o -> o != null
                && o.getName() != null
                && o.getName().equalsIgnoreCase(name));
    }

    /**
     * Returns the live tile if the object is visible, otherwise the stored tile.
     * Returns null if stored tile is the 0,0,0 placeholder and object is not visible.
     */
    public Tile getLiveTile() {
        GameObject live = getEntity();
        if (live != null) return live.getTile();
        // Don't return a meaningless 0,0,0 tile
        return (tile.getX() == 0 && tile.getY() == 0) ? null : tile;
    }

    /**
     * Walks toward this object. Uses live position if visible, stored tile otherwise.
     * Returns false if no tile can be resolved.
     */
    public boolean walkTo() {
        Tile dest = getLiveTile();
        return dest != null && Walking.walk(dest);
    }

    // -------------------------------------------------------------------------
    // Serialisation
    // -------------------------------------------------------------------------

    /** Converts this entry to the text file line format. */
    public String toFileLine() {
        return String.format("%s, %d, %d, %d, %s, %s",
                name,
                tile.getX(), tile.getY(), tile.getZ(),
                area,
                interaction);
    }

    @Override
    public String toString() {
        return "OBJ: " + name + " (" + area + ")";
    }
}
