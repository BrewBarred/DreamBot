//package main.world.locations.bankLocations;
//
//import locations.TravelMan;
//import org.dreambot.api.methods.container.impl.bank.BankLocation; // Banks -> BankLocation
//import org.dreambot.api.methods.map.Area; // OSBot Area -> DreamBot Area
//import org.dreambot.api.methods.map.Tile; // Tile -> Tile
//
///**
// * Enum representing various bank locations with their corresponding areas.
// * Each bank location has three defined areas: exactArea, clickArea, and extendedArea.
// */
//public enum Bank implements TravelMan {
//    /////
//    /////     ~ F2P BANK LOCATIONS ~
//    /////
//
//    ///
//    /// Add properties to existing locations provided by osbot api
//    ///
//    AL_KHARID(BankLocation.AL_KHARID.getArea(), "Al'kharid Bank"), // Updated to DreamBot BankLocation
//    DRAYNOR_VILLAGE(BankLocation.DRAYNOR.getArea(), "Draynor: Bank"),
//    FALADOR_EAST(BankLocation.FALADOR_EAST.getArea(), "Falador: Bank (east)"),
//    FALADOR_WEST(BankLocation.FALADOR_WEST.getArea(), "Falador: Bank (west)"),
//    GRAND_EXCHANGE(BankLocation.GRAND_EXCHANGE.getArea(), "Varrock: Grand Exchange"),
//    LUMBRIDGE_CASTLE(BankLocation.LUMBRIDGE.getArea(), "Lumbridge: Castle (Top floor)"),
//    VARROCK_EAST(BankLocation.VARROCK_EAST.getArea(), "Varrock: Bank (east)"),
//    VARROCK_WEST(BankLocation.VARROCK_WEST.getArea(), "Varrock: Bank (west)"),
//    ///
//    /// Add locations that osbot api doesn't provide
//    ///
//    EDGEVILLE_NORTH(new Area(3092, 3498, 3097, 3494), "Edgeville: Bank (north)"),
//    EDGEVILLE_SOUTH(new Area(3092, 3492, 3094, 3488), "Edgeville: Bank (south)");
//
//    /**
//     * The {@link Area area} associated with this bank space.
//     */
//    public final Area area;
//    /**
//     * The {@link Bank bank} name of this bank, for display purposes. E.g., "Varrock west-bank"
//     */
//    public final String name;
//
//    /**
//     * Constructs a {@link Bank} enum object with unique features that enable to you to quickly locate and reference
//     * a {@link Bank}.
//     */
//    Bank(Area area, String name) {
//        this.name = name;
//        this.area = area;
//    }
//
//    public Area getArea() { return area; }
//    public String getName() {
//        return name;
//    }
//
//    /**
//     * A seemingly mundane formality that will later become a fundamental building-block for this whole bot framework.
//     * <p>
//     * It is important to include details descriptions as these can later be used to help an AI bot decide what to do, how, and why.
//     *
//     * @return A detailed description of this location. This will later be used to feed an AI bot some information.
//     */
//    @Override
//    public String getDescription() {
//        return "Banks are used to store/retrieve items to use or safe-guard from accidental loss or on death." +
//                "They are also a common place to do bank-standing skills, such as Crafting or Fletching.";
//    }
//
//    //TODO: assess this function and see how I can use it elsewhere or maybe in Locations
//    /**
//     * Return the BankLocation closest to the passed target Tile.
//     *
//     * @param target The {@link Area area} used for the distance calculation.
//     * @return The {@link Bank bank} closest to the target Tile, else returns null.
//     */
//    public static Bank getNearestTo(Tile target) { // Tile -> Tile
//        Bank closest = null;
//        int bestDistance = Integer.MAX_VALUE;
//
//        for (Bank bank : values()) {
//            int distance = (int) bank.getArea().getCenter().distance(target); // getCentralTile -> getCenter
//            if (distance < bestDistance) {
//                bestDistance = distance;
//                closest = bank;
//            }
//        }
//        return closest;
//    }
//
//    public Bank[] getAll() {
//        return values();
//    }
//
//}