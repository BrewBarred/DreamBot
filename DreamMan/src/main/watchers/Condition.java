package main.watchers;

import org.dreambot.api.Client;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.methods.skills.Skills;

/**
 * A safe-to-evaluate world condition (Patch B.4) - the "when" half of a {@link Trigger}. These
 * are checked by the watcher engine between actions, only while the player is idle/safe, so a
 * trip never interrupts a click mid-flight. Each condition parses its own argument string
 * (e.g. an HP threshold, an area box, a coordinate) so the whole trigger round-trips as plain
 * text through the existing action serialization.
 *
 * <p>Everything is wrapped defensively: a condition that can't read the world (not logged in,
 * API hiccup) simply reports false rather than throwing into the loop.
 */
public enum Condition {

    INVENTORY_FULL("Inventory is full") {
        @Override public boolean test(String arg) {
            try { return Inventory.isFull(); } catch (Throwable t) { return false; }
        }
        @Override public String describe(String arg) { return "inventory full"; }
        @Override public boolean needsArg() { return false; }   // it's a boolean - no "15"
    },

    INVENTORY_SLOTS_BELOW("Empty slots below N") {
        @Override public boolean test(String arg) {
            try {
                int occupied = 0;
                for (Object i : Inventory.all()) if (i != null) occupied++;
                int empty = 28 - occupied;
                return empty < parseInt(arg, 0);
            } catch (Throwable t) { return false; }
        }
        @Override public String describe(String arg) { return "empty slots < " + arg; }
        @Override public String argHint() { return "number of empty slots, e.g. 3"; }
    },

    INVENTORY_CONTAINS("Inventory contains item") {
        @Override public boolean test(String arg) {
            if (arg == null || arg.isBlank()) return false;
            try { return Inventory.contains(arg.trim()); } catch (Throwable t) { return false; }
        }
        @Override public String describe(String arg) { return "have \"" + arg + "\""; }
        @Override public String argHint() { return "exact item name, e.g. Lobster"; }
    },

    INVENTORY_LACKS("Inventory is missing item") {
        @Override public boolean test(String arg) {
            if (arg == null || arg.isBlank()) return false;
            try { return !Inventory.contains(arg.trim()); } catch (Throwable t) { return false; }
        }
        @Override public String describe(String arg) { return "missing \"" + arg + "\""; }
        @Override public String argHint() { return "exact item name, e.g. Prayer potion(4)"; }
    },

    HP_BELOW("HP below (value or %)") {
        @Override public boolean test(String arg) {
            Integer v = current(Skill.HITPOINTS);
            if (v == null) return false;
            return belowThreshold(v, maxLevel(Skill.HITPOINTS), arg);
        }
        @Override public String describe(String arg) { return "HP < " + arg; }
        @Override public String argHint() { return "absolute (15) or percent of max (40%)"; }
    },

    PRAYER_BELOW("Prayer below (value or %)") {
        @Override public boolean test(String arg) {
            Integer v = current(Skill.PRAYER);
            if (v == null) return false;
            return belowThreshold(v, maxLevel(Skill.PRAYER), arg);
        }
        @Override public String describe(String arg) { return "Prayer < " + arg; }
        @Override public String argHint() { return "absolute (10) or percent of max (25%)"; }
    },

    RUN_ENERGY_BELOW("Run energy below %") {
        @Override public boolean test(String arg) {
            try {
                int e = org.dreambot.api.methods.walking.impl.Walking.getRunEnergy();
                return e < parseInt(arg, 0);
            } catch (Throwable t) { return false; }
        }
        @Override public String describe(String arg) { return "run < " + arg + "%"; }
        @Override public String argHint() { return "percent, e.g. 20"; }
    },

    IN_AREA("Inside area (x1,y1,x2,y2[,z])") {
        @Override public boolean test(String arg) {
            Area a = parseArea(arg);
            Tile t = playerTile();
            return a != null && t != null && a.contains(t);
        }
        @Override public String describe(String arg) { return "in area"; }
        @Override public String argHint() { return "x1,y1,x2,y2 or x1,y1,x2,y2,z"; }
    },

    NOT_IN_AREA("Outside area (x1,y1,x2,y2[,z])") {
        @Override public boolean test(String arg) {
            Area a = parseArea(arg);
            Tile t = playerTile();
            if (a == null || t == null) return false;
            return !a.contains(t);
        }
        @Override public String describe(String arg) { return "outside area"; }
        @Override public String argHint() { return "x1,y1,x2,y2 or x1,y1,x2,y2,z"; }
    },

    AT_TILE("On coordinate (x,y[,z])") {
        @Override public boolean test(String arg) {
            Tile target = parseTile(arg);
            Tile t = playerTile();
            return target != null && t != null
                    && t.getX() == target.getX() && t.getY() == target.getY()
                    && t.getZ() == target.getZ();
        }
        @Override public String describe(String arg) { return "at " + arg; }
        @Override public String argHint() { return "x,y or x,y,z"; }
    };

    private final String label;
    Condition(String label) { this.label = label; }

    /** @return true when the condition currently holds. Never throws. */
    public abstract boolean test(String arg);
    /** Short human phrase for overlays/UI (e.g. "HP < 15"). */
    public abstract String describe(String arg);
    /** Menu-facing label. */
    public String label() { return label; }

    /** Whether this condition takes an argument at all (Patch B.5). Booleans return false. */
    public boolean needsArg() { return true; }

    /** A one-line hint describing what the argument means, shown under the field (B.5). */
    public String argHint() { return "value"; }

    // ── shared helpers ──

    static Integer current(Skill s) {
        try { return Skills.getBoostedLevel(s); } catch (Throwable t) {
            try { return Skills.getRealLevel(s); } catch (Throwable t2) { return null; }
        }
    }

    static int maxLevel(Skill s) {
        try { return Skills.getRealLevel(s); } catch (Throwable t) { return 99; }
    }

    /** Accepts "15" (absolute) or "40%" (of max). */
    static boolean belowThreshold(int value, int max, String arg) {
        if (arg == null) return false;
        String a = arg.trim();
        try {
            if (a.endsWith("%")) {
                int pct = Integer.parseInt(a.substring(0, a.length() - 1).trim());
                return value < (max * pct) / 100.0;
            }
            return value < Integer.parseInt(a);
        } catch (NumberFormatException e) { return false; }
    }

    static int parseInt(String s, int fallback) {
        if (s == null) return fallback;
        try { return Integer.parseInt(s.trim().replace("%", "")); }
        catch (NumberFormatException e) { return fallback; }
    }

    static Tile playerTile() {
        try {
            if (!Client.isLoggedIn()) return null;
            return Players.getLocal().getTile();
        } catch (Throwable t) { return null; }
    }

    static Tile parseTile(String arg) {
        if (arg == null) return null;
        String[] p = arg.replaceAll("[^0-9, ]", "").trim().split("[, ]+");
        try {
            if (p.length == 2) return new Tile(Integer.parseInt(p[0]), Integer.parseInt(p[1]), 0);
            if (p.length >= 3) return new Tile(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        } catch (NumberFormatException e) { return null; }
        return null;
    }

    static Area parseArea(String arg) {
        if (arg == null) return null;
        String[] p = arg.replaceAll("[^0-9, ]", "").trim().split("[, ]+");
        try {
            if (p.length >= 4) {
                int x1 = Integer.parseInt(p[0]), y1 = Integer.parseInt(p[1]);
                int x2 = Integer.parseInt(p[2]), y2 = Integer.parseInt(p[3]);
                int z = p.length >= 5 ? Integer.parseInt(p[4]) : 0;
                return new Area(x1, y1, x2, y2, z);
            }
        } catch (NumberFormatException e) { return null; }
        return null;
    }

    /** Safe valueOf for persistence - unknown names map to null. */
    public static Condition fromName(String name) {
        if (name == null) return null;
        try { return Condition.valueOf(name); } catch (IllegalArgumentException e) { return null; }
    }
}
