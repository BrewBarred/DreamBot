package main.actions;

import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.item.GroundItems;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.items.GroundItem;

import javax.swing.*;

import static main.menu.MenuHandler.styleComp;

/**
 * Shared helpers for the action pack (Patch 4).
 * <p>
 * All the DreamBot lookups the actions need live here in one place, so if a method signature ever
 * shifts between client versions there's a single file to adjust rather than five. Every finder
 * returns {@code null} when nothing matching is in range, and callers decide what that means
 * (wait vs. skip). Name matching is case-insensitive; radius is a straight-line tile distance
 * from the local player.
 */
public final class ActionUtil {

    private ActionUtil() {}

    /** Lenient int parse with a fallback (used for radius / amount / delay fields). */
    public static int parseInt(String s, int fallback) {
        if (s == null) return fallback;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Splits a comma-separated field into trimmed, non-empty names. */
    /**
     * v1.31: parses an item-requirement list like "Copper ore x1, Tin ore x14" (also accepts
     * "Copper ore*1" or a bare name meaning x1) into name -> required count.
     */
    public static java.util.LinkedHashMap<String, Integer> parseItemList(String raw) {
        java.util.LinkedHashMap<String, Integer> out = new java.util.LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return out;
        for (String part : raw.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            int qty = 1;
            String name = p;
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(.+?)\\s*[x*]\\s*(\\d+)$", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(p);
            if (m.matches()) {
                name = m.group(1).trim();
                qty = Math.max(1, parseInt(m.group(2), 1));
            }
            if (!name.isEmpty()) out.merge(name, qty, Integer::sum);
        }
        return out;
    }

    /** v1.31: true when the inventory holds at least the given count of EVERY listed item. */
    public static boolean inventoryHasAll(java.util.Map<String, Integer> wanted) {
        if (wanted == null || wanted.isEmpty()) return true;
        for (java.util.Map.Entry<String, Integer> e : wanted.entrySet()) {
            int have = 0;
            try { have = org.dreambot.api.methods.container.impl.Inventory.count(e.getKey()); }
            catch (Throwable ignored) {}
            if (have < e.getValue()) return false;
        }
        return true;
    }

    /** v1.31: picks one phrase at random from a "|"-separated set ("hi|hello|yo"). */
    public static String pickPhrase(String raw) {
        if (raw == null) return "";
        String[] parts = raw.split("\\|");
        java.util.List<String> ok = new java.util.ArrayList<>();
        for (String p : parts) if (p != null && !p.trim().isEmpty()) ok.add(p.trim());
        if (ok.isEmpty()) return "";
        return ok.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(ok.size()));
    }

    public static String[] names(String raw) {
        if (raw == null || raw.trim().isEmpty())
            return new String[0];
        String[] parts = raw.split(",");
        int n = 0;
        for (String p : parts) if (!p.trim().isEmpty()) n++;
        String[] out = new String[n];
        int i = 0;
        for (String p : parts) if (!p.trim().isEmpty()) out[i++] = p.trim();
        return out;
    }

    static boolean within(Tile t, int radius) {
        if (t == null) return false;
        var local = Players.getLocal();
        if (local == null || local.getTile() == null) return false;
        return local.getTile().distance(t) <= radius;
    }

    /** Nearest NPC with the given name within {@code radius} tiles, or null. */
    /**
     * Combat-aware variant (Patch B.2): when {@code skipBusy} is true, NPCs already fighting are
     * skipped - you can't attack a cow that's in someone else's combat, so a busy target is
     * never a valid candidate; the caller soft-retries with the next one instead of failing.
     */
    /**
     * v1.31 hotfix: every "nearest X by name" helper now accepts a COMMA LIST and picks the
     * closest match across all of them - "Copper rocks, Tin rocks" mines whichever is closer.
     */
    public static boolean matchesAny(String entityName, String rawNames) {
        if (entityName == null || rawNames == null) return false;
        for (String n : names(rawNames))
            if (n != null && entityName.equalsIgnoreCase(n.trim())) return true;
        return false;
    }

    public static NPC nearestNpc(String name, int radius, boolean skipBusy) {
        return org.dreambot.api.methods.interactive.NPCs.closest(n -> {
            if (n == null || n.getName() == null || !matchesAny(n.getName(), name)) return false;
            if (!within(n.getTile(), radius)) return false;
            if (skipBusy) {
                try { if (n.isInCombat()) return false; } catch (Throwable ignored) {}
            }
            return true;
        });
    }

    public static NPC nearestNpc(String name, int radius) {
        if (name == null || name.isEmpty()) return null;
        NPC npc = NPCs.closest(n -> n != null && n.getName() != null && matchesAny(n.getName(), name));
        return (npc != null && within(npc.getTile(), radius)) ? npc : null;
    }

    /** Nearest game object matching any of the comma-separated names, within radius. */
    public static GameObject nearestObject(String name, int radius) {
        if (name == null || name.isEmpty()) return null;
        GameObject obj = GameObjects.closest(o -> o != null && o.getName() != null && matchesAny(o.getName(), name));
        return (obj != null && within(obj.getTile(), radius)) ? obj : null;
    }

    /** Nearest ground item matching any of the comma-separated names, within radius. */
    public static GroundItem nearestGroundItem(String name, int radius) {
        if (name == null || name.isEmpty()) return null;
        GroundItem gi = GroundItems.closest(g -> g != null && g.getName() != null && matchesAny(g.getName(), name));
        return (gi != null && within(gi.getTile(), radius)) ? gi : null;
    }

    /** v1.31 hotfix: inventory count SUMMED across a comma list of names. */
    public static int countAny(String rawNames) {
        int total = 0;
        if (rawNames == null) return 0;
        for (String n : names(rawNames)) {
            if (n == null || n.isBlank()) continue;
            try { total += org.dreambot.api.methods.container.impl.Inventory.count(n.trim()); }
            catch (Throwable ignored) {}
        }
        return total;
    }

    /**
     * Rough "is the player idle" check used by actions that wait for completion (e.g. Interact
     * in combat). Idle = not moving and not animating. Wrapped defensively so a client-version
     * difference degrades to "treat as idle" rather than throwing.
     */
    public static boolean isIdle() {
        try {
            var p = Players.getLocal();
            if (p == null) return true;
            boolean moving = p.isMoving();
            int anim = -1;
            try { anim = p.getAnimation(); } catch (Throwable ignored) {}
            return !moving && anim == -1;
        } catch (Throwable t) { return true; }
    }

    /** Stacks parameter sub-panels vertically into one styled panel. */
    public static JPanel stack(JComponent... panels) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);
        for (JComponent p : panels) {
            box.add(p);
            box.add(Box.createVerticalStrut(6));
        }
        styleComp(box);
        return box;
    }
}
