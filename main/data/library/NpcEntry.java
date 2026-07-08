package main.data.library;

import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.wrappers.interactive.NPC;

/**
 * Represents a known NPC in the library.
 *
 * Stores static data loaded from npcs.txt, but can also reach into the
 * live game world to fetch the actual NPC entity when needed.
 *
 * File format (one entry per line):
 *   name, x, y, z, area, interaction
 *   e.g. "Banker, 3208, 3221, 2, Lumbridge, Bank"
 */
public class NpcEntry {

    public String name;
    public Tile   tile;          // Approximate/known spawn tile
    public String area;          // Human-readable area name
    public String interaction;   // Primary right-click action e.g. "Talk-to"
    public String source;        // "file" | "learned" — where this entry came from

    public NpcEntry(String name, Tile tile, String area, String interaction, String source) {
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
     * Finds the closest matching live NPC in the current scene.
     * Returns null if none is loaded/visible.
     */
    public NPC getEntity() {
        return NPCs.closest(n -> n != null
                && n.getName() != null
                && n.getName().equalsIgnoreCase(name));
    }

    /**
     * Returns the live tile if the NPC is visible, otherwise falls back
     * to the stored approximate tile.
     */
    public Tile getLiveTile() {
        NPC live = getEntity();
        return live != null ? live.getTile() : tile;
    }

    /**
     * Walks to this NPC. Uses live position if visible, stored tile otherwise.
     */
    public boolean walkTo() {
        Tile dest = getLiveTile();
        return dest != null && Walking.walk(dest);
    }

    // -------------------------------------------------------------------------
    // Serialisation
    // -------------------------------------------------------------------------

    /**
     * Converts this entry back to the single-line text file format.
     * Mirrors exactly what FileManager expects when reading.
     */
    public String toFileLine() {
        return String.format("%s, %d, %d, %d, %s, %s",
                name,
                tile.getX(), tile.getY(), tile.getZ(),
                area,
                interaction);
    }

    @Override
    public String toString() {
        return "NPC: " + name + " (" + area + ")";
    }
}
