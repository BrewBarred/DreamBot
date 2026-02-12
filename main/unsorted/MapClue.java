//package main.unsorted;
//// MAP Clue notes:
//
//
//import main.world.locations.LocationFinder;
//import locations.Spot;
//import org.dreambot.api.methods.map.Area; // OSBot Area -> DreamBot Area
//import org.dreambot.api.methods.map.Tile; // Tile -> Tile
//import main.main.BotMan;
//
//import java.util.*;
//import java.util.stream.Collectors;
//
//public enum MapClue implements ClueScroll {
//    ///
//    ///     ~ BEGINNER MAP CLUE SCROLL LOCATIONS ~
//    ///
//    FALADOR_STONES(
//            new Area(3043, 3398, 3043, 3398),
//            "Standing Stones north of Falador",
//            "Perks: Close to Falador bank. Items: Ore spawns nearby. Combat: Dwarves present. Threats: Low danger. Training: Mining.",
//            "At the standing stones north of Falador, due east of Falador's north gate. Look for the (grapple) agility shortcut icon.",
//            351
//    ),
//
//    WIZARDS_TOWER_SOUTH(
//            new Area(3110, 3152, 3110, 3152),
//            "Behind the Wizards’ Tower",
//            "Perks: Fairy ring nearby. Items: Rune spawns inside. Combat: Wizards drop runes. Threats: Aggressive low-level danger. Training: Magic.",
//            "Directly behind the Wizards' Tower, there's a fern you can use as a reference; the dig spot is one tile directly north of the fern. Fairy ring code DIS is very close.",
//            356
//    ),
//
//    DRAYNOR_BANK_SOUTH(
//            new Area(3092, 3226, 3092, 3226),
//            "South of Draynor Village Bank",
//            "Perks: Close bank access. Items: Fishing spot south. Combat: Dark wizards nearby. Threats: Dangerous for low levels. Training: Fishing, Woodcutting.",
//            "South of Draynor Village bank, south of Draynor Village bank, by the fishing spot.",
//            348
//    ),
//
//    VARROCK_EAST_MINE(
//            new Area(3289, 3374, 3289, 3374),
//            "South-east Varrock Mine",
//            "Perks: Fast bank access. Items: Copper, tin, iron. Combat: Dark wizards nearby. Threats: Aggressive NPCs. Training: Mining.",
//            "South-east Varrock mine, south-east Varrock mine. Dig one square west of the small fern.",
//            347
//    ),
//
//    CHAMPIONS_GUILD_SOUTH_WEST(
//            new Area(3166, 3361, 3166, 3361),
//            "West of the Champions’ Guild",
//            "Perks: Open training area. Items: Trees, cowhides. Combat: Goblins and cows. Threats: Very safe. Training: Combat, Woodcutting, Crafting.",
//            "West of the Champions' Guild, locate the single tree that is west of the Champions' Guild, outside Varrock. Dig two squares to the east of it.",
//            346
//    );
//
//    final Area area;
//    final String name;
//    final String description;
//    final String task;
//    final int mapId;
//
//    /**
//     * Create a MAP location enum object which provides slightly more functionality than a typical
//     * {@link Spot location} object to help with solving map-type clues.
//     *
//     * @param area The {@link Area area} associated with this {@link Spot location}.
//     * @param mapId The {@link Integer mapId} associated with this clue map object.
//     * @param name The name of this clue-map object, used for display purposes.
//     */
//    // map clue constructors
//    MapClue(Area area, String name, String description, String task, int mapId) {
//        this.area = area;
//        this.name = name;
//        this.description = description;
//        this.task = task;
//        this.mapId = mapId;
//    }
//
//    //TODO: implement the following constructor eventually including a Task class which somehow completes an predefined or dynamic action (still looking into this)
//    // ClueLocation(Area area, Tile digTile || NPC npc, String name, String description, int mapId || String hint, String task
//
//    @Override
//    public Area getArea() {
//        return area;
//    }
//
//    @Override
//    public int getMapId() {
//        return 0;
//    }
//
//    @Override
//    public String getName() {
//        return name;
//    }
//
//    @Override
//    public String getHint() {
//        return "X marks the spot! Fetch a spade and dig at the marked map location.";
//    }
//
//    @Override
//    public String getDescription() {
//        return description;
//    }
//
//    @Override
//    public String[] getRequiredItems() {
//        return null; // add spade?
//    }
//
//    @Override
//    public boolean solve(main.BotMan bot) throws InterruptedException {
//        return false;
//    }
//
//    //public static MapClue from(String hint) {
////        return (MapClue) ClueHandbook.getClueFromHint(hint);
////    }
//
//    public MapClue fromRoots(List<Integer> activeRootIds) {
//        for (Integer root : activeRootIds) {
//            if (root == this.mapId)
//                return this;
//        }
//        return null;
//    }
//
////        // TODO: may need to extend this class to take multiple widget pairs later? idk? atm this will do.
////        //  Below is an example of what I was thinking.
////        HashMap<Integer, Integer> beginnerClues = new HashMap<>();
////          beginnerClues.put(203, 2)
////          mediumClues.put(213, 3);
////
////          place this in clue handbook and query to see if widget root id matches key
//
//    /**
//     * Filter all ClueMaps by the passed mapId.
//     *
//     * @param mapId The mapId used to quickly filter the Clue Map enum.
//     * @return The {@link LocationFinder} associated if the map id is validated, else returns null.
//     */
//    public MapClue getMapClue(int mapId) {
//        return Arrays.stream(MapClue.values())
//                .filter(m -> m.getMapId() == mapId)
//                .findFirst()
//                .orElse(null);
//    }
//
//    /**
//     * Return the map clue associated with the passed widget root id (used to obtain map clues, since they have no text)
//     *
//     * @param root The root widget ID used to search this enum.
//     * @return A {@link MapClue} object that can be used to easily solve clues in clue-solving scripts.
//     */
//    public static MapClue fromRoot(int root) {
//        // stream the map clue values to return a result
//        return Arrays.stream(values()) // declare the stream and target
//                .filter(clue -> clue.getMapId() == root) // filter the target by matching root ids
//                .findFirst() // return the first result
//                .orElse(null); // else return null
//    }
//
//    public static List<MapClue> sortByDistance(Tile playerPos) { // Tile -> Tile
//        return Arrays.stream(values())
//                .sorted(Comparator.comparingDouble(hc ->
//                        Objects.requireNonNull(hc.getArea().getCenter()).distance(playerPos))) // Updated for Tile/Area center
//                .collect(Collectors.toList());
//    }
//}