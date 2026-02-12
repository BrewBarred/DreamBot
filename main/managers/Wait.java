package main.managers;


import main.task.Action;
import main.BotMan;
import main.task.Task;
import main.tools.ETARandom;
import org.dreambot.api.methods.map.Tile; // Tile -> Tile

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

public class Wait extends Task {

    ///
    ///     CONSTRUCTORS
    ///
    /**
     * Default sleep task.
     */
    public Wait() {
        super(Action.WAIT, "sleepin");
    }

    ///
    ///     EXECUTION DEFINITIONS
    ///
    @Override
    protected boolean execute(BotMan bot) { // Signature updated to match Task base class
        bot.setPlayerStatus("Sleeping");
        bot.sleep(ETARandom.getRandShortDelayInt());
        return true; // mark as successful
    }

    @Override
    public JPanel getTaskSettings() {
        return null;
    }

    @Override
    protected void onTaskLoopCompletion() {}

    @Override
    protected void onTaskCompletion() {

    }

    @Override
    protected void onStageCompletion() {}

    @Override
    public int getStages() {
        return 1;
    }

    /**
     * Fetch the static test script available by calling {@link Wait#getTestScript()}, then for each task in the script,
     * execute the test, recording the results to the console.
     *
     * @param bot The bot instance performing the test
     */
    public boolean test(BotMan bot) throws InterruptedException {
        for (Task test : getTestScript()) {
            bot.setPlayerStatus("Testing : " + test.getDescription());
            boolean result = test.run(bot);
            bot.setPlayerStatus("--------------------result: " + result);
        }

        // return true since test completed without crashing
        return true;
    }

    /**
     * Returns a test set of {@link Task's} in list. This can be used to easily troubleshoot errors
     * and enables a modular test-script style.
     *
     * @return A list of Tasks that can be executed to test this class.
     */
    // TODO: complete test class once actions are coded
    public static List<Task> getTestScript() {
        Tile pos1 = new Tile(3110, 3152, 0); // Tile -> Tile

        // return a list of tasks that test each constructor
        return Arrays.asList(
                new Wait()
        );
    }

//
//    /**
//     * Helper function for getTests() to increase accessibility to this object for coding convenience.
//     *
//     * @return A list of tasks that can be used to test the {@link Wait wait} function.
//     */
//    public List<Task> test() {
//       return Wait.getTests();
//    }
}