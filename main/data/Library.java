package main.data;

import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.wrappers.interactive.NPC;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static main.actions.Action.parseStringIntoTile;

/**
 * F2P OSRS Game Library
 *
 * A single-file reference library for common F2P entities.
 * Usage examples:
 *   Library.Npcs.BANKER.walkTo();
 *   Library.GameObjects.BANK_BOOTH.walkTo();
 *   Library.GroundItems.CHAOS_RUNE_DARK_WARRIORS_FORTRESS.walkTo();
 *   Library.Locations.Varrock.GENERAL_STORE.walkTo();
 *   Library.Npcs.find("Banker").walkTo();
 */
public final class Library {

    private Library() {}

    // =========================================================================
    // NPCs
    // =========================================================================
    public enum Npcs {

        // --- Bankers ---
        BANKER                          ("Banker", new Tile(3218, 3218, 0), "Lumbridge", "Bank"),
        BANK_TUTOR                      ("Bank Tutor", new Tile(3232, 3226, 0), "Lumbridge", "Talk-to"),

        // --- Shops ---
        GRAND_EXCHANGE_CLERK            ("Exchange Worker", new Tile(3164, 3487, 0), "Grand Exchange", "Exchange"),
        GENERAL_STORE_OWNER             ("Shop keeper", new Tile(3212, 3415, 0), "Varrock", "Trade"),
        VARROCK_SWORD_SHOP              ("Scavvo", new Tile(3214, 3396, 0), "Varrock", "Trade"),
        VARROCK_STAFF_SHOP              ("Zaff", new Tile(3202, 3433, 0), "Varrock", "Trade"),
        VARROCK_ARMOUR_SHOP             ("Horvik", new Tile(3229, 3436, 0), "Varrock", "Trade"),
        VARROCK_RUNE_SHOP               ("Aubury", new Tile(3253, 3401, 0), "Varrock", "Trade"),
        LUMBRIDGE_GENERAL_STORE         ("Shop keeper", new Tile(3211, 3246, 0), "Lumbridge", "Trade"), // Dave is an assistant
        LUMBRIDGE_FISHING_SHOP          ("Hank", new Tile(3240, 3246, 0), "Lumbridge", "Trade"),
        AL_KHARID_WEAPON_SHOP           ("Zeke", new Tile(3289, 3189, 0), "Al Kharid", "Trade"),
        AL_KHARID_ARMOUR_SHOP           ("Louie Legs", new Tile(3290, 3175, 0), "Al Kharid", "Trade"),
        AL_KHARID_SILK_MERCHANT         ("Silk trader", new Tile(3299, 3155, 0), "Al Kharid", "Trade"),
        FALADOR_SHIELD_SHOP             ("Cassie", new Tile(2977, 3385, 0), "Falador", "Trade"),
        FALADOR_MINING_SHOP             ("Prospector Percy", new Tile(3061, 3375, 0), "Motherlode Mine", "Trade"),
        DRAYNOR_FISHING_SHOP            ("Gerrant", new Tile(3081, 3225, 0), "Draynor Village", "Trade"),
        PORT_SARIM_FISHING_SHOP         ("Gerrant", new Tile(3014, 3224, 0), "Port Sarim", "Trade"),
        PORT_SARIM_RUNE_SHOP            ("Betty", new Tile(3013, 3258, 0), "Port Sarim", "Trade"),

        // --- Quest / Key NPCs ---
        DUKE_HORACIO                    ("Duke Horacio", new Tile(3210, 3220, 1), "Lumbridge Castle", "Talk-to"),
        FATHER_AERECK                   ("Father Aereck", new Tile(3243, 3206, 0), "Lumbridge Church", "Talk-to"),
        WIZARD_MIZGOG                   ("Wizard Mizgog", new Tile(3103, 3163, 2), "Wizards' Tower", "Talk-to"),
        WIZARD_TRAIBORN                 ("Wizard Traiborn", new Tile(3112, 3163, 1), "Wizards' Tower", "Talk-to"),
        ROMEO                           ("Romeo", new Tile(3211, 3422, 0), "Varrock Square", "Talk-to"),
        JULIET                          ("Juliet", new Tile(3158, 3425, 1), "Juliet's House", "Talk-to"),
        VARROCK_GUARD                   ("Guard", new Tile(3212, 3461, 0), "Varrock", "Attack"),
        COOK                            ("Cook", new Tile(3207, 3214, 0), "Lumbridge Kitchen", "Talk-to"),

        // --- Skilling ---
        FISHING_SPOT_SHRIMP             ("Fishing spot", new Tile(3242, 3151, 0), "Lumbridge Swamp", "Net"),
        FISHING_SPOT_LOBSTER            ("Fishing spot", new Tile(2925, 3175, 0), "Karamja", "Cage"),

        // --- Combat / Training ---
        CHICKEN                         ("Chicken", new Tile(3230, 3297, 0), "Lumbridge Farm", "Attack"),
        COW                             ("Cow", new Tile(3258, 3271, 0), "Lumbridge Farm", "Attack"),
        GOBLIN                          ("Goblin", new Tile(3252, 3236, 0), "Lumbridge", "Attack"),
        GUARD                           ("Guard", new Tile(2964, 3387, 0), "Falador", "Attack"),
        DARK_WIZARD                     ("Dark wizard", new Tile(3224, 3369, 0), "Varrock South Entrance", "Attack"),
        HILL_GIANT                      ("Hill Giant", new Tile(3117, 9854, 0), "Edgeville Dungeon", "Attack"),
        MOSS_GIANT                      ("Moss giant", new Tile(3145, 9913, 0), "Varrock Sewers", "Attack"),
        LESSER_DEMON                    ("Lesser demon", new Tile(2838, 9518, 0), "Karamja Dungeon", "Attack"),
        GREATER_DEMON                   ("Greater demon", new Tile(3285, 3881, 0), "Wilderness", "Attack"),
        BLACK_KNIGHT                    ("Black Knight", new Tile(3015, 3514, 0), "Ice Mountain", "Attack"),
        WHITE_KNIGHT                    ("White Knight", new Tile(2960, 3338, 0), "Falador Castle", "Attack"),
        SKELETON                        ("Skeleton", new Tile(3112, 9832, 0), "Edgeville Dungeon", "Attack"),

        // --- Misc ---
        HANS                            ("Hans", new Tile(3221, 3218, 0), "Lumbridge Castle", "Talk-to");

        public final String npcName;
        public final Tile approxTile;
        public final String approxArea;
        public final String primaryInteraction;

        Npcs(String npcName, Tile spawnTile, String area, String primaryInteraction) {
            this.npcName = npcName;
            this.approxTile = spawnTile;
            this.approxArea = area;
            this.primaryInteraction = primaryInteraction;
        }

        /**
         * Gets the current tile of the nearest matching NPC in the game world.
         * Falls back to the static spawnTile if the NPC is not found.
         */
        public Tile getTile() {
            NPC npc = NPCs.closest(n -> n != null && n.getName() != null
                    && n.getName().equalsIgnoreCase(npcName));
            return npc != null ? npc.getTile() : approxTile;
        }

        public boolean walkTo() {
            Tile tile = getTile();
            if (tile != null)
                return Walking.walk(tile);

            return false;
        }

        public static Npcs find(String name) {
            return Arrays.stream(values())
                    .filter(n -> n.npcName.equalsIgnoreCase(name))
                    .findFirst().orElse(null);
        }
    }

    // =========================================================================
    // GAME OBJECTS
    // =========================================================================

    public enum GameObjects {

        // --- Banking ---
        BANK_BOOTH                      ("Bank booth"),
        BANK_CHEST                      ("Bank chest"),
        BANK_TABLE                      ("Banker"),          // Draynor bank table

        // --- Doors / Gates ---
        AL_KHARID_TOLL_GATE             ("Gate"),
        LUMBRIDGE_CASTLE_DOOR           ("Door"),
        EDGEVILLE_DUNGEON_TRAPDOOR      ("Trapdoor"),
        STRONGHOLD_SECURITY_DOOR_1      ("Door"),

        // --- Furnaces / Anvils / Ranges ---
        FURNACE                         ("Furnace"),
        ANVIL                           ("Anvil"),
        RANGE                           ("Range"),
        COOKING_RANGE                   ("Cooking range"),
        FIRE                            ("Fire"),

        // --- Mining rocks ---
        COPPER_ROCK                     ("Copper rocks"),
        TIN_ROCK                        ("Tin rocks"),
        IRON_ROCK                       ("Iron rocks"),
        COAL_ROCK                       ("Coal rocks"),
        GOLD_ROCK                       ("Gold rocks"),
        MITHRIL_ROCK                    ("Mithril rocks"),
        ADAMANTITE_ROCK                 ("Adamantite rocks"),
        RUNITE_ROCK                     ("Runite rocks"),    // Lava Maze, F2P wilderness
        CLAY_ROCK                       ("Clay rocks"),

        // --- Trees ---
        TREE                            ("Tree"),
        OAK_TREE                        ("Oak"),
        WILLOW_TREE                     ("Willow"),
        MAPLE_TREE                      ("Maple tree"),
        YEW_TREE                        ("Yew"),
        MAGIC_TREE                      ("Magic tree"),

        // --- Altars ---
        ALTAR                           ("Altar"),
        AIR_ALTAR                       ("Mysterious ruins"),
        MIND_ALTAR                      ("Mysterious ruins"),
        WATER_ALTAR                     ("Mysterious ruins"),
        EARTH_ALTAR                     ("Mysterious ruins"),
        FIRE_ALTAR                      ("Mysterious ruins"),
        BODY_ALTAR                      ("Mysterious ruins"),

        // --- Fishing ---
        ROD_FISHING_SPOT                ("Rod Fishing spot"),
        NET_FISHING_SPOT                ("Net fishing spot"),
        CAGE_FISHING_SPOT               ("Cage"),
        HARPOON_FISHING_SPOT            ("Fishing spot"),

        // --- Ladders / Stairs ---
        LADDER_DOWN                     ("Ladder"),
        LADDER_UP                       ("Ladder"),
        STAIRCASE_UP                    ("Staircase"),
        STAIRCASE_DOWN                  ("Staircase"),

        // --- Misc ---
        GRAND_EXCHANGE_BOOTH            ("Grand Exchange Booth"),
        MILL                            ("Mill"),
        SPINNING_WHEEL                  ("Spinning wheel"),
        LOOM                            ("Loom"),
        POTTERY_WHEEL                   ("Pottery wheel"),
        POTTERY_OVEN                    ("Pottery oven"),
        WELL                            ("Well"),
        CHEST                           ("Chest"),
        CRATE                           ("Crate"),
        BARREL                          ("Barrel");

        public final String objectName;

        GameObjects(String objectName) {
            this.objectName = objectName;
        }

        public Tile getTile() {
            var obj = org.dreambot.api.methods.interactive.GameObjects.closest(o -> o != null && o.getName() != null
                    && o.getName().equalsIgnoreCase(objectName));
            return obj != null ? obj.getTile() : null;
        }

        public void walkTo() {
            Tile tile = getTile();
            if (tile != null) Walking.walk(tile);
        }

        public static GameObjects find(String name) {
            return Arrays.stream(values())
                    .filter(o -> o.objectName.equalsIgnoreCase(name))
                    .findFirst().orElse(null);
        }
    }

    // =========================================================================
    // GROUND ITEM SPAWNS  (F2P only, static spawns — not monster drops)
    // Tile coordinates: (x, y, plane)
    // Respawn times are approximate base rates on a standard world.
    // =========================================================================

    public enum GroundItems {

        // --- Runes ---
        // Air runes — just outside Lumbridge Castle, near the Lumbridge Swamp
        AIR_RUNE_LUMBRIDGE              ("Air rune",    new Tile(3244, 3162, 0), 30),
        // Mind runes — ground floor of Lumbridge Castle tower
        MIND_RUNE_LUMBRIDGE_TOWER       ("Mind rune",   new Tile(3228, 3213, 2), 30),
        // Chaos runes — Dark Warriors' Fortress, level 15 Wilderness
        CHAOS_RUNE_DARK_WARRIORS_FORTRESS("Chaos rune", new Tile(3029, 3632, 0), 120),
        // Body runes — South of Chaos Altar, level 11 Wilderness
        BODY_RUNE_WILDERNESS            ("Body rune",   new Tile(2980, 3614, 0), 30),
        // Earth runes — Al Kharid mine
        EARTH_RUNE_AL_KHARID            ("Earth rune",  new Tile(3299, 3284, 0), 30),
        // Fire runes — Stronghold of Security floor 1 (Barbarian Village dungeon)
        FIRE_RUNE_STRONGHOLD            ("Fire rune",   new Tile(1914, 5228, 0), 30),
        // Water runes — Al Kharid mine, near scorpions
        WATER_RUNE_AL_KHARID            ("Water rune",  new Tile(3300, 3283, 0), 30),

        // --- Weapons & Armour ---
        BRONZE_DAGGER_LUMBRIDGE         ("Bronze dagger",   new Tile(3211, 3214, 2), 30),  // upstairs, Lumbridge Castle
        BRONZE_PICKAXE_LUMBRIDGE        ("Bronze pickaxe",  new Tile(3207, 3229, 1), 30),  // above portcullis, Lumbridge Castle
        BRONZE_PICKAXE_RIMMINGTON       ("Bronze pickaxe",  new Tile(2976, 3362, 0), 30),  // Rimmington, opp. house portal
        BRONZE_SCIMITAR_RIMMINGTON      ("Bronze scimitar", new Tile(2976, 3363, 1), 30),  // upstairs, same Rimmington building
        IRON_DAGGER_GOBLIN_HUT          ("Iron dagger",     new Tile(3245, 3238, 0), 30),  // goblin hut east of River Lum
        IRON_MACE_EDGEVILLE             ("Iron mace",       new Tile(3093, 3470, 0), 30),  // Edgeville guards' house
        BRONZE_MED_HELM_DRAYNOR_MANOR   ("Bronze med helm", new Tile(3107, 3369, 0), 30),  // Draynor Manor, ground floor east room
        BLACK_DAGGER_LAVA_MAZE          ("Black dagger",    new Tile(3073, 3857, 0), 120), // inside Lava Maze, Wilderness

        // --- Food ---
        CABBAGE_DRAYNOR                 ("Cabbage",         new Tile(3053, 3289, 0), 30),  // Draynor Manor grounds / Monastery
        CABBAGE_FALADOR_FARM            ("Cabbage",         new Tile(3053, 3289, 0), 30),  // Falador cabbage patch
        COOKED_CHICKEN_LUMBRIDGE        ("Cooked chicken",  new Tile(3209, 3215, 0), 30),  // Lumbridge Castle kitchen
        COOKED_MEAT_LUMBRIDGE           ("Cooked meat",     new Tile(3209, 3213, 0), 30),  // Lumbridge Castle kitchen
        BREAD_VARROCK                   ("Bread",           new Tile(3229, 3409, 0), 30),  // Varrock, house south of east bank
        CHEESE_VARROCK                  ("Cheese",          new Tile(3230, 3408, 0), 30),  // same Varrock house

        // --- Useful misc ---
        COINS_DWARVEN_MINE              ("Coins",           new Tile(3025, 9806, 0), 30),  // Dwarven Mine near chests
        BUCKET_LUMBRIDGE                ("Bucket",          new Tile(3208, 3218, 0), 0),   // Lumbridge Castle kitchen (no respawn, always there)
        POT_LUMBRIDGE                   ("Pot",             new Tile(3210, 3215, 0), 0),   // Lumbridge Castle kitchen
        JUG_LUMBRIDGE                   ("Jug",             new Tile(3208, 3216, 0), 0),   // Lumbridge Castle kitchen
        TINDERBOX_LUMBRIDGE             ("Tinderbox",       new Tile(3209, 3217, 0), 30),  // Lumbridge Castle kitchen
        SHEARS_LUMBRIDGE                ("Shears",          new Tile(3189, 3272, 0), 30),  // Fred the Farmer's house
        HAMMER_VARROCK                  ("Hammer",          new Tile(3257, 3401, 0), 30),  // Varrock west, building by anvil
        CHISEL_VARROCK                  ("Chisel",          new Tile(3257, 3400, 0), 30),  // same Varrock building
        NEEDLE_LUMBRIDGE                ("Needle",          new Tile(3209, 3209, 0), 30),  // Lumbridge basement
        THREAD_LUMBRIDGE                ("Thread",          new Tile(3209, 3208, 0), 30),  // Lumbridge basement
        FISHING_ROD_LUMBRIDGE           ("Fishing rod",     new Tile(3238, 3165, 0), 30),  // near Lumbridge Swamp fishing spot
        FLY_FISHING_ROD_BARBARIAN       ("Fly fishing rod", new Tile(3101, 3434, 0), 30),  // Barbarian Village hall
        FEATHER_BARBARIAN               ("Feather",         new Tile(3101, 3435, 0), 30),  // Barbarian Village hall
        FISHING_BAIT_PORT_SARIM         ("Fishing bait",    new Tile(3014, 3223, 0), 30),  // Port Sarim fishing shop area
        KNIFE_LUMBRIDGE                 ("Knife",           new Tile(3209, 3219, 0), 30),  // Lumbridge Castle kitchen
        GARLIC_DRAYNOR                  ("Garlic",          new Tile(3109, 3353, 0), 30),  // house in Draynor Village
        BONES_LUMBRIDGE                 ("Bones",           new Tile(3231, 3209, 0), 30),  // graveyard east of Lumbridge

        // --- Logs ---
        LOGS_LUMBRIDGE_TOWER            ("Logs",            new Tile(3228, 3213, 3), 120); // top floor Lumbridge Castle (4 logs)

        public final String itemName;
        public final Tile spawnTile;
        /** Base respawn time in seconds (on a standard world). 0 = always present. */
        public final int respawnSeconds;

        GroundItems(String itemName, Tile spawnTile, int respawnSeconds) {
            this.itemName       = itemName;
            this.spawnTile      = spawnTile;
            this.respawnSeconds = respawnSeconds;
        }

        /** Walk to the known static spawn tile. */
        public void walkTo() {
            Walking.walk(spawnTile);
        }

        /**
         * Try to find the item as a live GroundItem near the spawn tile, then walk to it.
         * Falls back to the static spawn tile if not currently visible.
         */
        public void walkToLive() {
            var item = org.dreambot.api.methods.item.GroundItems.closest(i -> i != null && i.getName() != null
                    && i.getName().equalsIgnoreCase(itemName));
            if (item != null) Walking.walk(item.getTile());
            else Walking.walk(spawnTile);
        }

        public static GroundItems find(String name) {
            return Arrays.stream(values())
                    .filter(i -> i.itemName.equalsIgnoreCase(name))
                    .findFirst().orElse(null);
        }
    }

    // =========================================================================
    // LOCATIONS — cities and named points of interest within them
    // Named exactly as they appear on the in-game map where possible.
    // =========================================================================

    /** Top-level city/region tiles (central reference points). */
    public enum Cities {
        LUMBRIDGE        (3222, 3218, 0),
        VARROCK          (3210, 3424, 0),
        FALADOR          (2964, 3378, 0),
        DRAYNOR_VILLAGE  (3093, 3244, 0),
        AL_KHARID        (3293, 3163, 0),
        EDGEVILLE        (3087, 3490, 0),
        BARBARIAN_VILLAGE(3105, 3421, 0),
        PORT_SARIM       (3015, 3193, 0),
        RIMMINGTON       (2954, 3211, 0),
        WIZARDS_TOWER    (3104, 3162, 0),
        DWARVEN_MINE     (3025, 3339, 0), // surface entrance
        GRAND_EXCHANGE   (3164, 3479, 0);

        public final Tile tile;
        Cities(int x, int y, int z) { this.tile = new Tile(x, y, z); }
        public void walkTo() { Walking.walk(tile); }
    }

    // -------------------------------------------------------------------------

    public static final class Lumbridge {
        private Lumbridge() {}
        public enum Location {
            CASTLE                  (3222, 3218, 0),
            CASTLE_KITCHEN          (3209, 3215, 0),
            CASTLE_BASEMENT         (3209, 3209, 0),
            BANK                    (3208, 3220, 2),  // 2nd floor
            GENERAL_STORE           (3212, 3247, 0),
            LUMBRIDGE_SWAMP         (3183, 3175, 0),
            LUMBRIDGE_SWAMP_FISHING (3238, 3165, 0),
            CHICKEN_FARM            (3185, 3268, 0),
            COW_FIELD               (3258, 3266, 0),
            GRAVEYARD               (3231, 3190, 0),
            MILL                    (3166, 3308, 0),
            FISHING_SHOP            (3239, 3166, 0),
            GOBLIN_HUT              (3245, 3238, 0),
            RIVER_LUM_CROSSING      (3229, 3230, 0),
            LUMBRIDGE_GUIDE         (3232, 3232, 0);

            public final Tile tile;
            Location(int x, int y, int z) { this.tile = new Tile(x, y, z); }
            public void walkTo() { Walking.walk(tile); }
        }
    }

    // -------------------------------------------------------------------------

    public static final class Varrock {
        private Varrock() {}
        public enum Location {
            CENTRE                  (3210, 3424, 0),
            EAST_BANK               (3253, 3420, 0),
            WEST_BANK               (3185, 3436, 0),
            GRAND_EXCHANGE          (3164, 3479, 0),
            GENERAL_STORE           (3212, 3415, 0),
            SWORD_SHOP              (3252, 3402, 0),  // Scavvo's Rune Store (also swords)
            STAFF_SHOP              (3203, 3419, 0),  // Zaff's Superior Staffs
            ARMOUR_SHOP             (3258, 3432, 0),  // Horvik's Armour Shop
            RUNE_SHOP               (3253, 3401, 0),  // Aubury's Rune Shop
            VARROCK_PALACE          (3210, 3468, 0),
            PALACE_LIBRARY          (3209, 3494, 0),
            VARROCK_MUSEUM          (3256, 3449, 0),
            CHAMPIONS_GUILD         (3190, 3363, 0),
            DARK_WIZARDS_SOUTH      (3183, 3368, 0),
            ANVIL_WEST              (3188, 3426, 0),
            CLOTHING_STORE          (3213, 3416, 0),  // Thessalia's Fine Clothes
            ROMEO_LOCATION          (3211, 3422, 0),
            JULIET_HOUSE            (3159, 3427, 1);  // upstairs, west Varrock

            public final Tile tile;
            Location(int x, int y, int z) { this.tile = new Tile(x, y, z); }
            public void walkTo() { Walking.walk(tile); }
        }
    }

    // -------------------------------------------------------------------------

    public static final class Falador {
        private Falador() {}
        public enum Location {
            CENTRE                  (2964, 3378, 0),
            EAST_BANK               (3013, 3355, 0),
            WEST_BANK               (2946, 3368, 0),
            GENERAL_STORE           (2955, 3390, 0),
            SHIELD_SHOP             (2971, 3387, 0),  // Cassie's Shield Shop
            MINING_SHOP             (3013, 3376, 0),  // Nurmof's Pickaxe Shop (Dwarven Mine area)
            PARTY_ROOM              (3046, 3376, 0),
            FALADOR_PARK            (2990, 3374, 0),
            WHITE_KNIGHTS_CASTLE    (2976, 3343, 0),
            DWARVEN_MINE_ENTRANCE   (3025, 3339, 0),
            HAIRDRESSER             (2944, 3381, 0),
            CHEMIST                 (2932, 3213, 0),  // south, near Rimmington road
            ESTATE_AGENT            (3040, 3334, 0),
            CRAFTING_SHOP           (2971, 3405, 0),  // Peksa's Helmet Shop
            ANVIL_SOUTH             (2974, 3313, 0);

            public final Tile tile;
            Location(int x, int y, int z) { this.tile = new Tile(x, y, z); }
            public void walkTo() { Walking.walk(tile); }
        }
    }

    // -------------------------------------------------------------------------

    public static final class Draynor {
        private Draynor() {}
        public enum Location {
            CENTRE                  (3093, 3244, 0),
            BANK                    (3092, 3243, 0),
            DRAYNOR_MANOR           (3108, 3353, 0),
            MARKET                  (3083, 3253, 0),
            FISHING_SPOT_WILLOWS    (3087, 3229, 0),
            JAIL                    (3128, 3245, 0),
            CHAMPIONS_CAPE_SHOP     (3083, 3254, 0),
            SKULLS_FISHING_SHOP     (3086, 3232, 0),
            WIZARD_TOWER_ROAD       (3104, 3200, 0);

            public final Tile tile;
            Location(int x, int y, int z) { this.tile = new Tile(x, y, z); }
            public void walkTo() { Walking.walk(tile); }
        }
    }

    // -------------------------------------------------------------------------

    public static final class AlKharid {
        private AlKharid() {}
        public enum Location {
            CENTRE                  (3293, 3163, 0),
            BANK                    (3269, 3167, 0),
            GENERAL_STORE           (3286, 3185, 0),
            WEAPON_SHOP             (3300, 3185, 0),  // Zeke's Superior Scimitars
            ARMOUR_SHOP             (3301, 3172, 0),  // Louie Legs' Armoured Legs
            SILK_MERCHANT           (3312, 3170, 0),
            CRAFTING_SHOP           (3285, 3190, 0),  // Dommik's Crafting Store
            MINING_SITE             (3299, 3283, 0),
            TOLL_GATE               (3268, 3229, 0),
            PALACE                  (3293, 3173, 0),
            RANGE_SHOP              (3232, 3171, 0),  // Ranael's Super Skirt Store
            DUEL_ARENA              (3315, 3236, 0);

            public final Tile tile;
            Location(int x, int y, int z) { this.tile = new Tile(x, y, z); }
            public void walkTo() { Walking.walk(tile); }
        }
    }

    // -------------------------------------------------------------------------

    public static final class Edgeville {
        private Edgeville() {}
        public enum Location {
            CENTRE                  (3087, 3490, 0),
            BANK                    (3094, 3491, 0),
            GENERAL_STORE           (3080, 3509, 0),
            DUNGEON_ENTRANCE        (3096, 3468, 0),  // trapdoor
            EDGEVILLE_MONASTERY     (3048, 3488, 0),
            RIVER_CROSSING          (3088, 3488, 0),
            WILDERNESS_DITCH        (3087, 3522, 0);

            public final Tile tile;
            Location(int x, int y, int z) { this.tile = new Tile(x, y, z); }
            public void walkTo() { Walking.walk(tile); }
        }
    }

    // -------------------------------------------------------------------------

    public static final class PortSarim {
        private PortSarim() {}
        public enum Location {
            CENTRE                  (3015, 3193, 0),
            GENERAL_STORE           (3012, 3213, 0),
            FISHING_SHOP            (3014, 3223, 0),  // Gerrant's Fishy Business
            RUNE_SHOP               (3013, 3228, 0),  // Betty's Magic Emporium
            FOOD_STORE              (3013, 3222, 0),  // Wydin's Food Store
            DOCK_TO_KARAMJA         (3029, 3217, 0),
            DOCK_TO_ENTRANA         (3041, 3236, 0),
            PUB_RUSTY_ANCHOR        (3045, 3256, 0),
            JAIL                    (3013, 3179, 0);

            public final Tile tile;
            Location(int x, int y, int z) { this.tile = new Tile(x, y, z); }
            public void walkTo() { Walking.walk(tile); }
        }
    }

    // -------------------------------------------------------------------------

    public static final class BarbarianVillage {
        private BarbarianVillage() {}
        public enum Location {
            CENTRE                  (3105, 3421, 0),
            BARBARIAN_HALL          (3101, 3434, 0),
            FISHING_SPOT            (3106, 3434, 0),
            STRONGHOLD_ENTRANCE     (3082, 3421, 0);

            public final Tile tile;
            Location(int x, int y, int z) { this.tile = new Tile(x, y, z); }
            public void walkTo() { Walking.walk(tile); }
        }
    }

    // -------------------------------------------------------------------------

    public static final class Rimmington {
        private Rimmington() {}
        public enum Location {
            CENTRE                  (2954, 3211, 0),
            GENERAL_STORE           (2969, 3212, 0),  // Rommik's Crafty Supplies
            MINING_SITE             (2975, 3240, 0),
            HOUSE_PORTAL            (2954, 3224, 0),
            SPAWN_BUILDING          (2976, 3362, 0);  // Bronze pickaxe / scimitar spawn building

            public final Tile tile;
            Location(int x, int y, int z) { this.tile = new Tile(x, y, z); }
            public void walkTo() { Walking.walk(tile); }
        }
    }

    // -------------------------------------------------------------------------

    public static final class WizardsTower {
        private WizardsTower() {}
        public enum Location {
            ENTRANCE                (3104, 3162, 0),
            GROUND_FLOOR            (3104, 3162, 0),
            FIRST_FLOOR             (3104, 3162, 1),
            SECOND_FLOOR            (3104, 3162, 2), // Wizard Mizgog
            ISLAND_SOUTH            (3104, 3155, 0);

            public final Tile tile;
            Location(int x, int y, int z) { this.tile = new Tile(x, y, z); }
            public void walkTo() { Walking.walk(tile); }
        }
    }

    // -------------------------------------------------------------------------

    public static final class DwarvenMine {
        private DwarvenMine() {}
        public enum Location {
            SURFACE_ENTRANCE        (3025, 3339, 0),
            UNDERGROUND_ENTRANCE    (3025, 9806, 0),
            IRON_ROCKS              (3017, 9805, 0),
            COAL_ROCKS              (3036, 9820, 0),
            MITHRIL_ROCKS           (3045, 9849, 0),
            GOLD_ROCKS              (3026, 9849, 0),
            NURMOF_PICKAXE_SHOP     (3013, 9796, 0);

            public final Tile tile;
            Location(int x, int y, int z) { this.tile = new Tile(x, y, z); }
            public void walkTo() { Walking.walk(tile); }
        }
    }

    public static Npcs nearestNpc() {
        return Arrays.stream(Npcs.values())
                .filter(n -> n.getTile() != null)
                .min(Comparator.comparingInt(n -> (int) n.getTile().distance(Players.getLocal().getTile())))
                .orElse(null);
    }

    public static GameObjects nearestObject() {
        return Arrays.stream(GameObjects.values())
                .filter(o -> o.getTile() != null)
                .min(Comparator.comparingInt(o -> (int) o.getTile().distance(Players.getLocal().getTile())))
                .orElse(null);
    }

    public static GroundItems nearestGroundItem() {
        return Arrays.stream(GroundItems.values())
                .filter(i -> Players.getLocal().getTile().distance(i.spawnTile) < 20)
                .min(Comparator.comparingInt(i -> (int) Players.getLocal().getTile().distance(i.spawnTile)))
                .orElse(null);
    }

    // --- Find all currently loaded/visible entries ---

    public static List<Npcs> visibleNpcs() {
        return Arrays.stream(Npcs.values())
                .filter(n -> n.getTile() != null)
                .collect(Collectors.toList());
    }

    public static List<GameObjects> visibleObjects() {
        return Arrays.stream(GameObjects.values())
                .filter(o -> o.getTile() != null)
                .collect(Collectors.toList());
    }

    public static List<GroundItems> nearbySpawns(int radius) {
        Tile local = Players.getLocal().getTile();
        return Arrays.stream(GroundItems.values())
                .filter(i -> local.distance(i.spawnTile) <= radius)
                .collect(Collectors.toList());
    }

    // --- Search by name across ALL enum types ---

    public static void walkToAny(String name) {
        // Try NPCs first, then objects, then ground items
        Npcs npc = Npcs.find(name);
        if (npc != null) { npc.walkTo(); return; }

        GameObjects obj = GameObjects.find(name);
        if (obj != null) { obj.walkTo(); return; }

        GroundItems item = GroundItems.find(name);
        if (item != null) { item.walkTo(); }
    }

    // --- Dump everything to console (useful for debugging) ---

    public static void printAll() {
        System.out.println("=== NPCs ===");
        Arrays.stream(Npcs.values()).forEach(n ->
                System.out.printf("%-40s visible=%-5s%n", n.name(), n.getTile() != null));

        System.out.println("\n=== GAME OBJECTS ===");
        Arrays.stream(GameObjects.values()).forEach(o ->
                System.out.printf("%-40s visible=%-5s%n", o.name(), o.getTile() != null));

        System.out.println("\n=== GROUND ITEM SPAWNS ===");
        Arrays.stream(GroundItems.values()).forEach(i ->
                System.out.printf("%-50s tile=%-20s respawn=%ds%n",
                        i.name(), i.spawnTile, i.respawnSeconds));
    }

    public static Tile resolveToTile(String target) {

        // 1. Coordinate
        Tile coord = parseStringIntoTile(target);
        if (coord != null) return coord;

        // 2. Live scene
        var player = Players.closest(p -> p != null && p.getName() != null && p.getName().equalsIgnoreCase(target));
        if (player != null) return player.getTile();

        var npc = NPCs.closest(n -> n != null && n.getName() != null && n.getName().equalsIgnoreCase(target));
        if (npc != null) return npc.getTile();

        var obj = org.dreambot.api.methods.interactive.GameObjects.closest(o -> o != null && o.getName() != null && o.getName().equalsIgnoreCase(target));
        if (obj != null) return obj.getTile();

        var item = org.dreambot.api.methods.item.GroundItems.closest(i -> i != null && i.getName() != null && i.getName().equalsIgnoreCase(target));
        if (item != null) return item.getTile();

        // 3. Library fallback
        Npcs libNpc = Npcs.find(target);
        if (libNpc != null)
            return libNpc.getTile();

        GameObjects libObj = GameObjects.find(target);
        if (libObj != null)
            return libObj.getTile();

        GroundItems libItem = GroundItems.find(target);
        if (libItem != null)
            return libItem.spawnTile;

        Logger.log(Logger.LogType.ERROR, "[RESOLVE] unable to find target: " + target);
        return null;
    }
}