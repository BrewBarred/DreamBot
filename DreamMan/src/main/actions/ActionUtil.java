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
    public static NPC nearestNpc(String name, int radius, boolean skipBusy) {
        return org.dreambot.api.methods.interactive.NPCs.closest(n -> {
            if (n == null || n.getName() == null || !n.getName().equalsIgnoreCase(name)) return false;
            if (!within(n.getTile(), radius)) return false;
            if (skipBusy) {
                try { if (n.isInCombat()) return false; } catch (Throwable ignored) {}
            }
            return true;
        });
    }

    public static NPC nearestNpc(String name, int radius) {
        if (name == null || name.isEmpty()) return null;
        NPC npc = NPCs.closest(n -> n != null && n.getName() != null && n.getName().equalsIgnoreCase(name));
        return (npc != null && within(npc.getTile(), radius)) ? npc : null;
    }

    /** Nearest game object with the given name within {@code radius} tiles, or null. */
    public static GameObject nearestObject(String name, int radius) {
        if (name == null || name.isEmpty()) return null;
        GameObject obj = GameObjects.closest(o -> o != null && o.getName() != null && o.getName().equalsIgnoreCase(name));
        return (obj != null && within(obj.getTile(), radius)) ? obj : null;
    }

    /** Nearest ground item with the given name within {@code radius} tiles, or null. */
    public static GroundItem nearestGroundItem(String name, int radius) {
        if (name == null || name.isEmpty()) return null;
        GroundItem gi = GroundItems.closest(g -> g != null && g.getName() != null && g.getName().equalsIgnoreCase(name));
        return (gi != null && within(gi.getTile(), radius)) ? gi : null;
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
