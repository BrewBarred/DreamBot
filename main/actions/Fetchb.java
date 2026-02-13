//package main.actions;
//
//import main.task.Task;
//import main.task.Action; // TaskType changed to Action in previous conversions
//import main.BotMan;
//import org.dreambot.api.methods.map.Tile; // Tile -> Tile
//
//
///**
// * Represents a task to fetch an item from various sources.
// * <p>
// * Fetch supports multiple strategies:
// * - fromBank: withdraw an item from the nearest accessible bank
// * - forCharlie: fetch the item from the bank and deliver to Charlie the Tramp
// * - fromShop: buy the item from a shop (shop definitions to be implemented later)
// * <p>
// * The fluent API allows chaining of repeat counts, delay strategies, and additional conditions.
// */
//public class Fetchb extends Task {
//
//    private final String itemName;
//    private final FetchType fetchType;
//
//    // TODO: later you may add a Shop enum/class
//    private Object shop;
//
//    private enum FetchType {
//        BANK, CHARLIE, SHOP
//    }
//
//    private Fetchb(Action type, String itemName, FetchType fetchType) {
//        super(type, itemName); // TaskType -> Action
//        this.itemName = itemName;
//        this.fetchType = fetchType;
//    }
//
//    private Fetchb(Action type, String itemName, FetchType fetchType, Object shop) {
//        this(type, itemName, fetchType);
//        this.shop = shop;
//    }
//
//    /* ===========================
//     * FACTORY METHODS
//     * =========================== */
//
//    /**
//     * Create a fetch task to withdraw an item from the bank.
//     *
//     * @param itemName The name of the item to withdraw.
//     * @return A Fetch task for bank retrieval.
//     */
//    public static Fetchb fromBank(String itemName) {
//        return new Fetchb(Action.FETCH, itemName, FetchType.BANK);
//    }
//
//    /**
//     * Create a fetch task to withdraw an item from the bank
//     * and deliver it to Charlie the Tramp.
//     *
//     * @param itemName The name of the item to fetch.
//     * @return A Fetch task targeting Charlie.
//     */
//    public static Fetchb forCharlie(String itemName) {
//        return new Fetchb(Action.FETCH, itemName, FetchType.CHARLIE);
//    }
//
//    /**
//     * Create a fetch task to buy an item from a shop.
//     * Shop implementation will be provided via enum/class later.
//     *
//     * @param shop The shop definition.
//     * @return A Fetch task targeting the given shop.
//     */
//    public static Fetchb fromShop(Object shop) {
//        // itemName may be optional depending on your shop API
//        return new Fetchb(Action.FETCH, null, FetchType.SHOP, shop);
//    }
//
//    /* ===========================
//     * CHAINING OPTIONS
//     * =========================== */
//
//    public Fetchb repeat(int count) {
//        this.setLoops(count); // setLoopCount -> setLoops based on TaskMan implementation
//        return this;
//    }
//
//    // delaySupplier and setCondition depend on specific fields in your Task base class
//    // Keeping logic structure but updating to match existing framework methods
//
//    /* ===========================
//     * EXECUTION LOGIC
//     * =========================== */
//
//    @Override
//    protected boolean execute(BotMan bot) { // Signature updated to match Task base class
//        bot.setPlayerStatus("Fetching " + (itemName != null ? itemName : "item") + " via " + fetchType); // setStatus -> setPlayerStatus
//
//        switch (fetchType) {
//            case BANK:
//                return fetchFromBank(bot);
//            case CHARLIE:
//                return fetchForCharlie(bot);
//            case SHOP:
//                return fetchFromShop(bot);
//            default:
//                return false;
//        }
//    }
//
//    private boolean fetchFromBank(BotMan bot) {
//        // TODO: implement actual bank logic
//        bot.log("Withdrawing " + itemName + " from bank...");
//        return true;
//    }
//
//    private boolean fetchForCharlie(BotMan bot) {
//        // First fetch from bank
//        if (!fetchFromBank(bot)) return false;
//
//        // Then deliver to Charlie (example Tile - replace with proper enum/locator)
//        Tile charliePos = new Tile(3226, 3398, 0); // Tile -> Tile
//        bot.log("Delivering " + itemName + " to Charlie...");
//        return bot.walkTo(charliePos); // webWalk -> walk
//        // TODO: complete dialogue logic
//        //return true;
//    }
//
//    private boolean fetchFromShop(BotMan bot) {
//        // TODO: implement once Shop enum/class is ready
//        bot.log("Buying item from shop: " + shop);
//        return true;
//    }
//
//    // Required overrides from Task base class
//    @Override protected void onTaskLoopCompletion() {}
//    @Override protected void onTaskCompletion() {}
//    @Override protected void onStageCompletion() {}
//    @Override public int getStages() { return 1; }
//    @Override public javax.swing.JPanel getTaskSettings() { return null; }
//
//    @Override
//    public String toString() {
//        return "Fetch{" +
//                "itemName='" + itemName + '\'' +
//                ", fetchType=" + fetchType +
//                '}';
//    }
//}