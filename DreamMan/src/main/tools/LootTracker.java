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

    // ── v1.30: RuneLite-style stats - lifetime totals, per character ──────────
    /** npcName -> last kill/drop activity (ms), for "recent" sorting in the tracker. */
    private static final Map<String, Long> LAST_SEEN = new ConcurrentHashMap<>();
    /** When this session's tallies started (script start / last session reset). */
    private static volatile long sessionStartMs = System.currentTimeMillis();
    /** The character the lifetime totals are attributed to (set from the status loop). */
    private static volatile String character = "";
    /** character -> npc -> lifetime kills (persisted). */
    private static Map<String, Map<String, Integer>> lifeKills = new ConcurrentHashMap<>();
    /** character -> npc -> item -> lifetime quantity (persisted). */
    private static Map<String, Map<String, Map<String, Long>>> lifeDrops = new ConcurrentHashMap<>();
    private static volatile boolean lifeLoaded = false, lifeDirty = false;
    private static volatile long lastLifeSaveAt = 0;

    /** Gson-friendly shape of the persisted file. */
    private static final class LifeData {
        Map<String, Map<String, Integer>> kills = new java.util.HashMap<>();
        Map<String, Map<String, Map<String, Long>>> drops = new java.util.HashMap<>();
    }

    private static java.io.File lifeFile() {
        return new java.io.File(main.data.store.LocalStore.getRoot(), "loot-tracker.json");
    }

    private static synchronized void loadLifetime() {
        if (lifeLoaded) return;
        lifeLoaded = true;
        try {
            java.io.File f = lifeFile();
            if (!f.isFile()) return;
            String json = new String(java.nio.file.Files.readAllBytes(f.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
            LifeData d = new com.google.gson.Gson().fromJson(json, LifeData.class);
            if (d == null) return;
            if (d.kills != null) d.kills.forEach((c, m) ->
                    lifeKills.put(c, new ConcurrentHashMap<>(m)));
            if (d.drops != null) d.drops.forEach((c, m) -> {
                Map<String, Map<String, Long>> per = new ConcurrentHashMap<>();
                m.forEach((npc, items) -> per.put(npc, new ConcurrentHashMap<>(items)));
                lifeDrops.put(c, per);
            });
        } catch (Throwable ignored) { /* corrupt file: start fresh, never crash the script */ }
    }

    /** Debounced async save (at most every ~5s); also called with force=true on resets. */
    private static void maybeSaveLifetime(boolean force) {
        long now = System.currentTimeMillis();
        if (!lifeDirty || (!force && now - lastLifeSaveAt < 5000)) return;
        lifeDirty = false;
        lastLifeSaveAt = now;
        Thread t = new Thread(() -> {
            try {
                LifeData d = new LifeData();
                lifeKills.forEach((c, m) -> d.kills.put(c, new java.util.HashMap<>(m)));
                lifeDrops.forEach((c, m) -> {
                    Map<String, Map<String, Long>> per = new java.util.HashMap<>();
                    m.forEach((npc, items) -> per.put(npc, new java.util.HashMap<>(items)));
                    d.drops.put(c, per);
                });
                java.nio.file.Files.write(lifeFile().toPath(),
                        new com.google.gson.GsonBuilder().setPrettyPrinting().create()
                                .toJson(d).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (Throwable ignored) {}
        }, "DreamMan-LootSave");
        t.setDaemon(true);
        t.start();
    }

    /** Which character lifetime totals go to. Call whenever the logged-in name is known. */
    public static void setCharacter(String name) {
        if (name == null || name.isBlank() || name.equals(character)) return;
        loadLifetime();
        character = name;
    }

    public static String getCharacter() { return character; }
    public static long sessionStartMs() { return sessionStartMs; }
    /** ms since a kill or drop last touched this NPC (Long.MAX_VALUE when never). */
    public static long lastSeen(String npc) {
        Long t = LAST_SEEN.get(npc);
        return t == null ? Long.MAX_VALUE : System.currentTimeMillis() - t;
    }

    /** Lifetime kill count for one NPC on the current character. */
    public static int lifetimeKillCount(String npc) {
        loadLifetime();
        Map<String, Integer> m = lifeKills.get(character);
        return m == null ? 0 : m.getOrDefault(npc, 0);
    }

    /** Lifetime drops for one NPC on the current character (item -> qty), never null. */
    public static Map<String, Long> lifetimeDrops(String npc) {
        loadLifetime();
        Map<String, Map<String, Long>> m = lifeDrops.get(character);
        Map<String, Long> t = m == null ? null : m.get(npc);
        return t == null ? java.util.Collections.emptyMap() : new java.util.HashMap<>(t);
    }

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
        if (npcName != null && !npcName.isBlank()) {
            KILL_COUNTS.merge(npcName, 1, Integer::sum);
            LAST_SEEN.put(npcName, System.currentTimeMillis());          // v1.30
            loadLifetime();                                              // v1.30: lifetime tally
            lifeKills.computeIfAbsent(character, c -> new ConcurrentHashMap<>())
                    .merge(npcName, 1, Integer::sum);
            lifeDirty = true;
            maybeSaveLifetime(false);
        }
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
        LAST_SEEN.put(npc, now);                                         // v1.30
        loadLifetime();                                                  // v1.30: lifetime tally
        lifeDrops.computeIfAbsent(character, c -> new ConcurrentHashMap<>())
                .computeIfAbsent(npc, n -> new ConcurrentHashMap<>())
                .merge(item.getName(), (long) qty, Long::sum);
        lifeDirty = true;
        maybeSaveLifetime(false);
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
        sessionStartMs = System.currentTimeMillis();                     // v1.30: gp/hr restarts
    }

    /** v1.30: session reset (same as resetLearning; the tracker's "Reset session"). */
    public static void resetSession() { resetLearning(); }

    /** v1.30: clears one NPC's SESSION tallies (right-click a box in the tracker). */
    public static void resetSessionNpc(String npc) {
        if (npc == null) return;
        KILL_COUNTS.remove(npc);
        DROP_COUNTS.remove(npc);
    }

    /** v1.30: wipes the CURRENT CHARACTER's lifetime totals (tracker's "Reset lifetime"). */
    public static void resetLifetime() {
        loadLifetime();
        lifeKills.remove(character);
        lifeDrops.remove(character);
        lifeDirty = true;
        maybeSaveLifetime(true);
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
