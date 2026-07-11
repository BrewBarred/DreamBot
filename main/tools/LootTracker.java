package main.tools;

import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.wrappers.items.GroundItem;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ground-loot intelligence (Patch B.2). Solves two real-world problems:
 *
 * <p><b>1. Items you can't pick up</b> (another player's drop on an ironman/GIM account): if we
 * keep failing to take a specific item, it gets blacklisted for roughly a ground-item despawn
 * cycle, so the bot stops spamming "Take" on loot that was never ours. Detection is behavioural
 * (N failed takes while the item still exists) - it needs no chat hooks, so it also covers any
 * other "can't take" reason. A chatbox hook ("You're a Group Ironman...") can be layered on
 * later to blacklist on the FIRST failure once that API is verified against live javadocs.
 *
 * <p><b>2. Preferring our own drops</b>: Interact records where and when our latched combat
 * target died. Items sitting on/next to a recent kill tile score as "ours" and are looted first,
 * which both looks human and avoids ironman-blocked piles.
 */
public final class LootTracker {

    private LootTracker() {}

    /** How long a blacklisted item stays blocked - about a public despawn cycle. */
    public static final long BLACKLIST_MS = 120_000L;
    /** Kill spots stay "fresh" (and attract looting) for this long. */
    public static final long KILL_FRESH_MS = 60_000L;

    /** name|x|y|z -> blockedUntil (ms). */
    private static final Map<String, Long> BLACKLIST = new ConcurrentHashMap<>();
    /** Recent kill spots: {x, y, z, timeMs}, plus the NPC name that died there. */
    private static final Deque<Object[]> KILLS = new ArrayDeque<>();

    // ── Patch B.4: drop-table learning ──
    /** npcName -> total kills we've recorded. */
    private static final Map<String, Integer> KILL_COUNTS = new ConcurrentHashMap<>();
    /** npcName -> (itemName -> total quantity seen on our kill tiles). */
    private static final Map<String, Map<String, Long>> DROP_COUNTS = new ConcurrentHashMap<>();
    /** Tiles already counted for a drop so the same pile isn't tallied every poll. */
    private static final Map<String, Long> COUNTED = new ConcurrentHashMap<>();

    /** Records that our combat target died on this tile (call from Interact). */
    public static void recordKill(Tile tile) { recordKill(tile, null); }

    /** Records a kill and, when known, which NPC died - feeding the drop-table learner. */
    public static void recordKill(Tile tile, String npcName) {
        if (tile == null) return;
        synchronized (KILLS) {
            KILLS.addLast(new Object[]{(long) tile.getX(), (long) tile.getY(), (long) tile.getZ(),
                    System.currentTimeMillis(), npcName});
            while (KILLS.size() > 24) KILLS.removeFirst();
        }
        if (npcName != null && !npcName.isBlank())
            KILL_COUNTS.merge(npcName, 1, Integer::sum);
    }

    /** @return the NPC name whose fresh kill tile this item sits on/next to, or null. */
    private static String killerAt(Tile t) {
        if (t == null) return null;
        long now = System.currentTimeMillis();
        synchronized (KILLS) {
            for (Object[] k : KILLS) {
                if (now - (long) k[3] > KILL_FRESH_MS) continue;
                if ((long) k[2] != t.getZ()) continue;
                if (Math.abs((long) k[0] - t.getX()) <= 1 && Math.abs((long) k[1] - t.getY()) <= 1)
                    return (String) k[4];
            }
        }
        return null;
    }

    /**
     * Attributes a ground item to the NPC that likely dropped it (a fresh nearby kill) and tallies
     * it toward that NPC's learned drop table - each distinct pile counted once. Call opportunistically
     * from Loot while scanning; harmless when nothing matches.
     */
    public static void observeDrop(GroundItem item) {
        if (item == null || item.getTile() == null || item.getName() == null) return;
        String npc = killerAt(item.getTile());
        if (npc == null) return;
        String k = key(item);
        long now = System.currentTimeMillis();
        Long seen = COUNTED.get(k);
        if (seen != null && now - seen < KILL_FRESH_MS) return;   // already tallied this pile
        COUNTED.put(k, now);
        int qty = 1;
        try { qty = Math.max(1, item.getAmount()); } catch (Throwable ignored) {}
        DROP_COUNTS.computeIfAbsent(npc, n -> new ConcurrentHashMap<>())
                .merge(item.getName(), (long) qty, Long::sum);
    }

    public static int killCount(String npc) { return KILL_COUNTS.getOrDefault(npc, 0); }
    public static Map<String, Integer> allKillCounts() { return new java.util.HashMap<>(KILL_COUNTS); }
    public static Map<String, Map<String, Long>> allDropCounts() {
        Map<String, Map<String, Long>> copy = new java.util.HashMap<>();
        for (Map.Entry<String, Map<String, Long>> e : DROP_COUNTS.entrySet())
            copy.put(e.getKey(), new java.util.HashMap<>(e.getValue()));
        return copy;
    }
    /** Clears the learned drop-table data (kills + drops), e.g. from the readout's Reset. */
    public static void resetLearning() {
        KILL_COUNTS.clear(); DROP_COUNTS.clear(); COUNTED.clear();
    }

    /** Blocks one specific ground item (this name on this tile) for a despawn cycle. */
    public static void blacklist(GroundItem item) {
        if (item == null || item.getTile() == null) return;
        BLACKLIST.put(key(item), System.currentTimeMillis() + BLACKLIST_MS);
    }

    /** @return true when this exact item (name+tile) is currently blocked. */
    public static boolean isBlacklisted(GroundItem item) {
        if (item == null || item.getTile() == null) return false;
        Long until = BLACKLIST.get(key(item));
        if (until == null) return false;
        if (until < System.currentTimeMillis()) {
            BLACKLIST.remove(key(item));
            return false;
        }
        return true;
    }

    /** Lower = loot first. 0 for items on/next to a fresh kill tile ("probably ours"), 1 otherwise. */
    public static int ownershipRank(GroundItem item) {
        if (item == null || item.getTile() == null) return 1;
        return killerAt(item.getTile()) != null ? 0 : 1;
    }

    /** Drops expired blacklist entries and stale kill spots. Cheap; call opportunistically. */
    public static void prune() {
        long now = System.currentTimeMillis();
        BLACKLIST.values().removeIf(until -> until < now);
        synchronized (KILLS) {
            Iterator<Object[]> it = KILLS.iterator();
            while (it.hasNext())
                if (now - (long) it.next()[3] > KILL_FRESH_MS) it.remove();
        }
        COUNTED.values().removeIf(seen -> now - seen > KILL_FRESH_MS);
    }

    /** Test/diagnostics: number of active blacklist entries. */
    public static int blacklistSize() {
        prune();
        return BLACKLIST.size();
    }

    private static String key(GroundItem i) {
        Tile t = i.getTile();
        return i.getName() + "|" + t.getX() + "|" + t.getY() + "|" + t.getZ();
    }
}
