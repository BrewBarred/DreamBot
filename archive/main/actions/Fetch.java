//package main.actions;
//
//import main.task.Action;
//import main.BotMan;
//import main.task.Task;
//import main.tools.ETARandom;
//import org.dreambot.api.methods.container.impl.bank.BankLocation;
//import org.dreambot.api.methods.map.Tile; // Tile -> Tile
//import org.dreambot.api.wrappers.items.Item; // Item remains Item but from DreamBot API
//
//import javax.swing.*;
//import java.util.List;
//
///**
// *
// *
// * TaskType: TaskType.FETCH
// */
//public class Fetch extends Task {
//    ///
//    ///     STATIC LISTS
//    ///
//    /**
//     * A list of strings containing the names of each item required to complete this task.
//     */
//    protected final static String[] REQUIRED_ITEMS = new String[]{"Spade"};
//
//
//    ///
//    ///     CONSTRUCTORS: CREATE ONE FOR EACH VARIATION OF THE TASK YOU WANT TO BE ABLE TO HAVE
//    ///
//
//    /**
//     * Fetch the passed item the easiest way possible.
//     * <p>
//     * This function will search the nearby location first, followed by the players bank, before purchasing or trying
//     * other retrieval methods.
//     */
//    public Fetch(Item item) {
//        super(Action.FETCH, "Fetching item: " + item.getName());
//    }
//
//    public Fetch(Item item, String description) {
//        super(Action.FETCH, description);
//    }
//
//    public Fetch from(BankLocation bank) {
//        area = bank.getArea(ETARandom.getRand(1,10));
//        position = null;
//        return this;
//    }
//
//    @Override
//    protected void onTaskLoopCompletion() {}
//
//    @Override
//    protected void onTaskCompletion() {
//
//    }
//
//    @Override
//    protected void onStageCompletion() {}
//
//    @Override
//    public int getStages() {
//        return 2;
//    }
//
//    /**
//     * Return a panel with script-specific settings on it. None required for this class, so null is returned.
//     */
//    @Override
//    public JPanel getTaskSettings() {
//        return null;
//    }
//
//    ///
//    ///     PARENT FUNCTIONS: OVERRIDDEN FROM TASK CLASS {@link Task#execute}
//    ///
//    @Override
//    protected boolean execute(BotMan bot) {
//        switch (stage) {
//            case 1:
//                bot.setBotStatus("Attempting to fetch item...");
//                break;
//
//            case 2:
//                //TODO insert more logic here later to fetch items better
//                return true;
//
//            default:
//                throw new FetchingException("Stage: " + getStageString());
//        }
//
//        // increment stage and return false here to save spam in all steps except the last (which should return true)
//        stage++;
//        return false;
//    }
//
//    ///
//    ///     Test scripts
//    ///
//    /**
//     * Return a test script for this class which can be executed to test the functionality of this class is as intended.
//     *
//     * @return A {@link List} of {@link Task}s that can be added to the TaskMan for execution to test the functionality
//     * of this class.
//     */
//    public static Task[] getTests() {
//        Tile wizardsTowerDigSpot = new Tile(3110, 3152, 0); // wizards tower
//
//        return new Task[]{
//                new Dig("perform a standard dig").setStage(1),
//                new Dig("dig near the wizards tower beginner clue location... (within 1 tile)").near(wizardsTowerDigSpot, 1)
////            new Dig("Testing dig at wizards tower beginner clue dig-spot...").at(wizardsTowerDigSpot),
////            new Dig("Testing dig near wizards tower beginner clue dig-spot within a 5 tile radius...").near(wizardsTowerDigSpot, 5),
////            new Dig("Testing dig on the spot, only looping once...").loop(1),
////            new Dig("Testing dig on the spot, only looping twice...").loop(2),
//        };
//    }
//
//    ///
//    ///     Error Handling
//    ///
//    /**
//     * Creates a custom exception to handle digging errors for better debugging. This will also allow me to create some
//     * Creates a custom exception to handle digging errors for better debugging. This will also allow me to create some
//     * functions later which create new tasks to prevent failure, which I can then plugin to machine learning models to
//     * self-train based on mistakes (with this being treated as the punishment/failure zone).
//     */
//    public static class FetchingException extends RuntimeException {
//        public FetchingException(String message) {
//            super("[Fetching Exception] Error encountered while digging. " + message);
//        }
//    }
//}