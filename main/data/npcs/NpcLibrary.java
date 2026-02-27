//package main.data.npcs;
//
//import org.dreambot.api.methods.map.Tile;
//
//import java.util.*;
//
///**
// * F2P NPC Library
// * Contains name, tile, area, and interaction options for common F2P NPCs.
// * Tile represents the most common/central spawn point for each NPC.
// */
//public class NpcLibrary {
//    public static class NpcData {
//        public final String name;
//        public final Tile tile;
//        public final String area;
//        public final String[] interactions;
//
//        public NpcData(String name, Tile tile, String area, String... interactions) {
//            this.name = name;
//            this.tile = tile;
//            this.area = area;
//            this.interactions = interactions;
//        }
//
//        @Override
//        public String toString() {
//            return name + " @ " + area + " " + tile;
//        }
//    }
//
//    private static final List<NpcData> NPCS = new ArrayList<>();
//    private static final Map<String, NpcData> BY_NAME = new LinkedHashMap<>();
//
//    static {
//        // -----------------------------------------------------------------------
//        // LUMBRIDGE
//        // -----------------------------------------------------------------------
//        register("Hans",                new Tile(3222, 3218, 0),  "Lumbridge Castle",         "Talk-to", "Examine");
//        register("Duke Horacio",        new Tile(3210, 3220, 1),  "Lumbridge Castle",         "Talk-to", "Examine");
//        register("Father Aereck",       new Tile(3243, 3208, 0),  "Lumbridge Church",         "Talk-to", "Examine");
//        register("Bob",                 new Tile(3231, 3203, 0),  "Lumbridge Bob's Axes",     "Talk-to", "Trade", "Examine");
//        register("Doric",               new Tile(2951, 3451, 0),  "North of Falador",         "Talk-to", "Examine");
//        register("Cook",                new Tile(3210, 3215, 0),  "Lumbridge Castle Kitchen", "Talk-to", "Examine");
//        register("Lumbridge Guide",     new Tile(3232, 3232, 0),  "Lumbridge",                "Talk-to", "Examine");
//        register("Magic Tutor",         new Tile(3140, 3092, 0),  "Lumbridge",                "Talk-to", "Examine");
//        register("Banker",              new Tile(3208, 3220, 2),  "Lumbridge Castle Bank",    "Bank", "Talk-to", "Examine");
//        register("Hank",                new Tile(3160, 3142, 0),  "Lumbridge Swamp",          "Talk-to", "Examine");
//
//        // -----------------------------------------------------------------------
//        // LUMBRIDGE CREATURES
//        // -----------------------------------------------------------------------
//        register("Chicken",             new Tile(3236, 3295, 0),  "Lumbridge Farms",          "Attack", "Examine");
//        register("Cow",                 new Tile(3258, 3265, 0),  "Lumbridge Cow Field",      "Attack", "Examine");
//        register("Goblin",              new Tile(3246, 3243, 0),  "Lumbridge",                "Attack", "Examine");
//        register("Giant Rat",           new Tile(3148, 3093, 0),  "Lumbridge Swamp",          "Attack", "Examine");
//        register("Rat",                 new Tile(3208, 9896, 0),  "Lumbridge Cellar",         "Attack", "Examine");
//        register("Skeleton",            new Tile(3148, 9901, 0),  "Lumbridge Cellar",         "Attack", "Examine");
//        register("Zombie",              new Tile(3148, 9906, 0),  "Lumbridge Cellar",         "Attack", "Examine");
//        register("Al-Kharid Warrior",   new Tile(3293, 3174, 0),  "Al Kharid",                "Attack", "Examine");
//
//        // -----------------------------------------------------------------------
//        // AL KHARID
//        // -----------------------------------------------------------------------
//        register("Banker (Al Kharid)",  new Tile(3270, 3167, 0),  "Al Kharid Bank",           "Bank", "Talk-to", "Examine");
//        register("Dommik",              new Tile(3284, 3189, 0),  "Al Kharid",                "Talk-to", "Trade", "Examine");
//        register("Ellis",               new Tile(3274, 3190, 0),  "Al Kharid",                "Talk-to", "Trade", "Examine");
//        register("Ranael",              new Tile(3313, 3171, 0),  "Al Kharid",                "Talk-to", "Trade", "Examine");
//        register("Zeke",                new Tile(3287, 3194, 0),  "Al Kharid",                "Talk-to", "Trade", "Examine");
//        register("Louie Legs",          new Tile(3318, 3180, 0),  "Al Kharid",                "Talk-to", "Trade", "Examine");
//        register("Gem trader",          new Tile(3291, 3210, 0),  "Al Kharid",                "Talk-to", "Trade", "Examine");
//        register("Shantay",             new Tile(3304, 3124, 0),  "Shantay Pass",             "Talk-to", "Trade", "Examine");
//        register("Toll gate guard",     new Tile(3268, 3228, 0),  "Al Kharid Toll Gate",      "Talk-to", "Pay-toll(10gp)", "Examine");
//
//        // -----------------------------------------------------------------------
//        // DRAYNOR VILLAGE
//        // -----------------------------------------------------------------------
//        register("Banker (Draynor)",    new Tile(3092, 3243, 0),  "Draynor Bank",             "Bank", "Talk-to", "Examine");
//        register("Aggie",               new Tile(3086, 3257, 0),  "Draynor Village",          "Talk-to", "Examine");
//        register("Leela",               new Tile(3085, 3252, 0),  "Draynor Village",          "Talk-to", "Examine");
//        register("Morgan",              new Tile(3099, 3268, 0),  "Draynor Village",          "Talk-to", "Examine");
//        register("Ned",                 new Tile(3097, 3258, 0),  "Draynor Village",          "Talk-to", "Trade", "Examine");
//        register("Fortunato",           new Tile(3080, 3251, 0),  "Draynor Village",          "Talk-to", "Trade", "Examine");
//        register("Dommik",              new Tile(3081, 3248, 0),  "Draynor Village",          "Talk-to", "Trade", "Examine");
//        register("Garv",                new Tile(3077, 3251, 0),  "Draynor Village",          "Talk-to", "Examine");
//        register("Martin the Master Gardener", new Tile(3080, 3258, 0), "Draynor Village",    "Talk-to", "Examine");
//        register("Man",                 new Tile(3093, 3244, 0),  "Draynor Village",          "Attack", "Examine");
//        register("Woman",               new Tile(3092, 3262, 0),  "Draynor Village",          "Attack", "Examine");
//
//        // -----------------------------------------------------------------------
//        // VARROCK
//        // -----------------------------------------------------------------------
//        register("Banker (Varrock West)",  new Tile(3185, 3436, 0), "Varrock West Bank",      "Bank", "Talk-to", "Examine");
//        register("Banker (Varrock East)",  new Tile(3253, 3420, 0), "Varrock East Bank",      "Bank", "Talk-to", "Examine");
//        register("Aubury",              new Tile(3253, 3401, 0),  "Varrock Rune Shop",        "Talk-to", "Trade", "Teleport", "Examine");
//        register("Zaff",                new Tile(3203, 3434, 0),  "Varrock Staff Shop",       "Talk-to", "Trade", "Examine");
//        register("Thessalia",           new Tile(3205, 3418, 0),  "Varrock Clothes Shop",     "Talk-to", "Trade", "Examine");
//        register("Horvik",              new Tile(3228, 3433, 0),  "Varrock Armour Shop",      "Talk-to", "Trade", "Examine");
//        register("Lowe",                new Tile(3233, 3421, 0),  "Varrock Archery Shop",     "Talk-to", "Trade", "Examine");
//        register("Varrock Guard",       new Tile(3220, 3470, 0),  "Varrock",                  "Attack", "Examine");
//        register("Mugger",              new Tile(3110, 3416, 0),  "West of Varrock",          "Attack", "Examine");
//        register("Dark wizard",         new Tile(3102, 3365, 0),  "South of Varrock",         "Attack", "Examine");
//        register("Barbarian",           new Tile(3230, 3421, 0),  "Varrock",                  "Attack", "Examine");
//        register("Apothecary",          new Tile(3195, 3404, 0),  "Varrock",                  "Talk-to", "Examine");
//        register("Rat Burgiss",         new Tile(3254, 3477, 0),  "South Varrock",            "Talk-to", "Examine");
//        register("Gertrude",            new Tile(3151, 3413, 0),  "Varrock (west)",           "Talk-to", "Examine");
//        register("Romeo",               new Tile(3211, 3422, 0),  "Varrock Square",           "Talk-to", "Examine");
//        register("Juliet",              new Tile(3159, 3426, 1),  "Varrock (west house)",     "Talk-to", "Examine");
//        register("Wally",               new Tile(3252, 3400, 0),  "Varrock",                  "Talk-to", "Examine");
//
//        // -----------------------------------------------------------------------
//        // FALADOR
//        // -----------------------------------------------------------------------
//        register("Banker (Falador)",    new Tile(2946, 3368, 0),  "Falador Bank",             "Bank", "Talk-to", "Examine");
//        register("Wayne",               new Tile(2972, 3312, 0),  "Falador",                  "Talk-to", "Trade", "Examine");
//        register("Cassie",              new Tile(2975, 3383, 0),  "Falador",                  "Talk-to", "Trade", "Examine");
//        register("Flynn",               new Tile(2950, 3387, 0),  "Falador",                  "Talk-to", "Trade", "Examine");
//        register("Peksa",               new Tile(2972, 3375, 0),  "Falador",                  "Talk-to", "Trade", "Examine");
//        register("Falador Guard",       new Tile(2967, 3379, 0),  "Falador",                  "Attack", "Examine");
//        register("White Knight",        new Tile(2977, 3343, 0),  "Falador Castle",           "Attack", "Talk-to", "Examine");
//        register("Black Knight",        new Tile(3015, 3514, 0),  "Black Knights Fortress",   "Attack", "Examine");
//        register("Sir Amik Varze",      new Tile(2978, 3338, 2),  "Falador Castle",           "Talk-to", "Examine");
//        register("Squire",              new Tile(2978, 3341, 0),  "Falador Castle",           "Talk-to", "Examine");
//
//        // -----------------------------------------------------------------------
//        // EDGEVILLE
//        // -----------------------------------------------------------------------
//        register("Banker (Edgeville)",  new Tile(3097, 3491, 0),  "Edgeville Bank",           "Bank", "Talk-to", "Examine");
//        register("Oziach",              new Tile(3068, 3516, 0),  "Edgeville",                "Talk-to", "Trade", "Examine");
//        register("Vannaka",             new Tile(3146, 9913, 0),  "Edgeville Dungeon",        "Talk-to", "Examine");
//
//        // -----------------------------------------------------------------------
//        // BARBARIAN VILLAGE
//        // -----------------------------------------------------------------------
//        register("Barbarian (village)", new Tile(3082, 3421, 0),  "Barbarian Village",        "Attack", "Examine");
//        register("Peksa (Barbarian)",   new Tile(3073, 3420, 0),  "Barbarian Village",        "Talk-to", "Trade", "Examine");
//
//        // -----------------------------------------------------------------------
//        // PORT SARIM
//        // -----------------------------------------------------------------------
//        register("Monk of Entrana",     new Tile(3044, 3237, 0),  "Port Sarim Docks",         "Talk-to", "Examine");
//        register("Seamen Thresnher",    new Tile(3027, 3218, 0),  "Port Sarim Docks",         "Talk-to", "Examine");
//        register("Betty",               new Tile(3014, 3258, 0),  "Port Sarim Magic Shop",    "Talk-to", "Trade", "Examine");
//        register("Gerrant",             new Tile(3013, 3224, 0),  "Port Sarim Fish Shop",     "Talk-to", "Trade", "Examine");
//        register("Brian",               new Tile(3027, 3247, 0),  "Port Sarim",               "Talk-to", "Trade", "Examine");
//        register("Wydin",               new Tile(3012, 3227, 0),  "Port Sarim Food Shop",     "Talk-to", "Trade", "Examine");
//        register("Klarense",            new Tile(3041, 3236, 0),  "Port Sarim Docks",         "Talk-to", "Examine");
//
//        // -----------------------------------------------------------------------
//        // RIMMINGTON
//        // -----------------------------------------------------------------------
//        register("Hetty",               new Tile(2974, 3204, 0),  "Rimmington",               "Talk-to", "Examine");
//        register("Rommik",              new Tile(2952, 3214, 0),  "Rimmington Crafting Shop", "Talk-to", "Trade", "Examine");
//
//        // -----------------------------------------------------------------------
//        // WILDERNESS
//        // -----------------------------------------------------------------------
//        register("Hill Giant",          new Tile(3117, 9849, 0),  "Edgeville Dungeon",        "Attack", "Examine");
//        register("Moss Giant",          new Tile(3156, 9907, 0),  "Edgeville Dungeon",        "Attack", "Examine");
//        register("Lesser Demon",        new Tile(3159, 3956, 0),  "Karamja Volcano",          "Attack", "Examine");
//        register("Greater Demon",       new Tile(2568, 4415, 0),  "Wilderness Volcano",       "Attack", "Examine");
//        register("Ice Giant",           new Tile(3012, 9580, 0),  "Ice Mountain Dungeon",     "Attack", "Examine");
//        register("Ice Warrior",         new Tile(3012, 9576, 0),  "Ice Mountain Dungeon",     "Attack", "Examine");
//        register("Cockroach Soldier",   new Tile(3140, 9903, 0),  "Stronghold of Security",   "Attack", "Examine");
//
//        // -----------------------------------------------------------------------
//        // STRONGHOLD OF SECURITY
//        // -----------------------------------------------------------------------
//        register("Minotaur",            new Tile(1875, 5222, 0),  "Stronghold of Security",   "Attack", "Examine");
//        register("Flesh Crawler",       new Tile(1908, 5176, 0),  "Stronghold of Security",   "Attack", "Examine");
//        register("Catablepon",          new Tile(1944, 5135, 0),  "Stronghold of Security",   "Attack", "Examine");
//        register("Ankou",               new Tile(1916, 5204, 0),  "Stronghold of Security",   "Attack", "Examine");
//
//        // -----------------------------------------------------------------------
//        // KARAMJA
//        // -----------------------------------------------------------------------
//        register("Banana plantation worker", new Tile(2914, 3167, 0), "Karamja",              "Talk-to", "Examine");
//        register("Luthas",              new Tile(2938, 3152, 0),  "Karamja",                  "Talk-to", "Examine");
//        register("Zambo",               new Tile(2926, 3144, 0),  "Karamja Pub",              "Talk-to", "Trade", "Examine");
//        register("Pirate",              new Tile(2915, 3180, 0),  "Karamja Docks",            "Attack", "Examine");
//
//        // -----------------------------------------------------------------------
//        // WIZARD'S TOWER / SOUTH
//        // -----------------------------------------------------------------------
//        register("Wizard",              new Tile(3104, 3162, 0),  "Wizard's Tower",           "Attack", "Examine");
//        register("Archmage Sedridor",   new Tile(3104, 3162, 1),  "Wizard's Tower",           "Talk-to", "Examine");
//        register("Traiborn",            new Tile(3112, 3161, 1),  "Wizard's Tower",           "Talk-to", "Examine");
//
//        // -----------------------------------------------------------------------
//        // CHAMPIONS GUILD / SOUTH VARROCK
//        // -----------------------------------------------------------------------
//        register("Guildmaster",         new Tile(3190, 3362, 0),  "Champions Guild",          "Talk-to", "Examine");
//
//        // -----------------------------------------------------------------------
//        // DWARVEN MINE / ICE MOUNTAIN
//        // -----------------------------------------------------------------------
//        register("Dwarf",               new Tile(3018, 9739, 0),  "Dwarven Mine",             "Attack", "Talk-to", "Examine");
//        register("Boot",                new Tile(2999, 9718, 0),  "Dwarven Mine",             "Talk-to", "Trade", "Examine");
//        register("Scorpion",            new Tile(3038, 9589, 0),  "Dwarven Mine",             "Attack", "Examine");
//
//        // -----------------------------------------------------------------------
//        // CRAFTING GUILD
//        // -----------------------------------------------------------------------
//        register("Master Crafter",      new Tile(2934, 3293, 0),  "Crafting Guild",           "Talk-to", "Examine");
//
//        // -----------------------------------------------------------------------
//        // FISHING SPOTS (interactive NPCs/spots)
//        // -----------------------------------------------------------------------
//        register("Fishing spot",        new Tile(3239, 3244, 0),  "Lumbridge Swamp",          "Net", "Bait", "Examine");
//        register("Fishing spot",        new Tile(3268, 3194, 0),  "Al Kharid",                "Net", "Bait", "Examine");
//        register("Fishing spot",        new Tile(3274, 3193, 0),  "Al Kharid",                "Lure", "Bait", "Examine");
//        register("Fishing spot",        new Tile(3016, 3227, 0),  "Port Sarim",               "Net", "Harpoon", "Cage", "Examine");
//        register("Fishing spot",        new Tile(2847, 3143, 0),  "Karamja Docks",            "Net", "Harpoon", "Cage", "Examine");
//        register("Fishing spot",        new Tile(3088, 3229, 0),  "Draynor Village",          "Net", "Bait", "Examine");
//        register("Barbarian Fishing spot", new Tile(3106, 3430, 0), "Barbarian Village",      "Lure", "Bait", "Examine");
//
//        // -----------------------------------------------------------------------
//        // QUEST NPCS (common F2P)
//        // -----------------------------------------------------------------------
//        register("Prince Ali",          new Tile(3301, 3163, 0),  "Al Kharid Palace",         "Talk-to", "Examine");
//        register("Osman",               new Tile(3288, 3179, 0),  "Al Kharid Palace",         "Talk-to", "Examine");
//        register("Nessa",               new Tile(3291, 3178, 0),  "Al Kharid Palace",         "Talk-to", "Examine");
//        register("Father Urhney",       new Tile(3147, 3174, 0),  "Lumbridge Swamp",          "Talk-to", "Examine");
//        register("Wizard Mizgog",       new Tile(3103, 3163, 2),  "Wizard's Tower",           "Talk-to", "Examine");
//        register("Wizard Grayzag",      new Tile(3104, 3162, 2),  "Wizard's Tower",           "Talk-to", "Examine");
//        register("Wormbrain",           new Tile(3024, 3179, 0),  "Port Sarim Jail",          "Talk-to", "Examine");
//        register("Black Knight Titan",  new Tile(2880, 9850, 0),  "Underground",              "Attack", "Examine");
//        register("Count Draynor",       new Tile(3078, 9761, 0),  "Draynor Dungeon",          "Attack", "Examine");
//        register("Elvarg",              new Tile(2270, 4655, 0),  "Crandor",                  "Attack", "Examine");
//    }
//
//    private static void register(String name, Tile tile, String area, String... interactions) {
//        NpcData data = new NpcData(name, tile, area, interactions);
//        NPCS.add(data);
//        BY_NAME.put(name.toLowerCase(), data);
//    }
//
//    // -----------------------------------------------------------------------
//    // Lookup Methods
//    // -----------------------------------------------------------------------
//
//    /**
//     * Get NPC data by name (case-insensitive).
//     */
//    public static NpcData get(String name) {
//        return BY_NAME.get(name.toLowerCase());
//    }
//
//    /**
//     * Get the tile for a given NPC name. Returns null if not found.
//     */
//    public static Tile getTile(String name) {
//        NpcData data = get(name);
//        return data != null ? data.tile : null;
//    }
//
//    /**
//     * Get the area for a given NPC name. Returns null if not found.
//     */
//    public static String getArea(String name) {
//        NpcData data = get(name);
//        return data != null ? data.area : null;
//    }
//
//    /**
//     * Get all interactions for a given NPC name.
//     */
//    public static String[] getInteractions(String name) {
//        NpcData data = get(name);
//        return data != null ? data.interactions : new String[0];
//    }
//
//    /**
//     * Check if a given interaction exists for the NPC.
//     */
//    public static boolean hasInteraction(String name, String interaction) {
//        for (String i : getInteractions(name))
//            if (i.equalsIgnoreCase(interaction))
//                return true;
//        return false;
//    }
//
//    /**
//     * Get all NPCs in a given area (case-insensitive partial match).
//     */
//    public static List<NpcData> getByArea(String area) {
//        List<NpcData> results = new ArrayList<>();
//        for (NpcData data : NPCS)
//            if (data.area.toLowerCase().contains(area.toLowerCase()))
//                results.add(data);
//        return results;
//    }
//
//    /**
//     * Returns all registered NPCs.
//     */
//    public static List<NpcData> getAll() {
//        return Collections.unmodifiableList(NPCS);
//    }
//}