package main.scripts;

import main.menu.DreamBotMenu;
import main.tools.Rand;
import org.dreambot.api.data.GameState;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.listener.GameStateListener;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.wrappers.interactive.Player;

import javax.swing.*;
import java.awt.*;

import main.actions.Action;

public abstract class DreamBotMan extends AbstractScript implements GameStateListener {
    ///  Class scope fields

    /**
     * The {@link DreamBotMenu} which allows users to interface with their botting scripts and player(s).
     */
    private DreamBotMenu menu;
    /**
     * A {@link String} denoting the name of the last logged in player.
     * <p>
     * This is used to check if a login is a new player so the manager knows when to reload the menu items. (Otherwise
     * the menu would show the incorrect stats and tasks whenever you change accounts)
     */
    private String lastPlayerName = "";

    ///  Abstract functions

    /**
     * Allows child inheritors to execute some setup logic before starting the main bot loop.
     *
     * @return True if the pre-loop logic returns successful, else false.
     */
    public abstract boolean postStart();
    /**
     * Provides a function which is automatically called before each task list loop, that is, once all tasks have been
     * completed in a particular task-set.
     * <p>
     * This function allows scripters to override and perform specific tasks/functions at the end of each loop and
     * determine whether the loop should continue or not based on those conditions.
     * <p>
     * Although this same functionality could have been achieved more simply by placing this logic at start of any loop,
     * this method segmentizes it better for the progress bars and overall code clarity.
     * <p>
     * This logic could be used for simple things like a brief pause before each loop, or more complex things such as
     * checking attempt counts, determining player safety, or triggering random number generators to execute a different
     * task-set.
     *
     * @return True if the pre-loop logic returns successful, else false.
     */
    public abstract boolean preLoop();
    /**
     * Allows child inheritors to execute some logic after each action, such as a small pause or even an opportunity to
     * check vitals for more reactive, but more complex bots.
     *
     * @return True if the post-action logic returns successful, else false.
     */
    public abstract boolean postAction();
    /**
     * Allows child inheritors to execute some logic everytime an account logs in. This works purely off a game state
     * change resulting in a logged in game state. To execute logic only when a 'new' account has logged in, use
     * {@link DreamBotMan#postLoginNew()} instead.
     *
     * @see DreamBotMan#postLoginNew()
     */
    public abstract void postLogin();
    /**
     * Checks the name of the logged in player to see if it matches the last, and executes some logic if a new player
     * is detected.
     * <p>
     * This may prove useful for first-time login logic without worrying about redundant code being executed on
     * world-hops.
     */
    public abstract void postLoginNew();

    public abstract void postGameStateChange(GameState gameState);

    public abstract void postResume();
    public abstract void postPause();
    public abstract void postPaint(Graphics graphics);
    public abstract void postStop();
    public abstract void postExit();

    private DefaultListModel<DreamBotMenu.Task> queue;

    @Override
    public final void onStart() {
        super.onStart();

        // initialize the GUI on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            menu = new DreamBotMenu(this);
            menu.setAlwaysOnTop(true); //TODO add to "Client" settings
            menu.setVisible(true);

            // fetch the task queue only after menu is instantiated
            queue = menu.getModelTaskList();
        });

        // call child on-start for script-specific on-start setup
        if (!postStart())
            throw new RuntimeException("Error running post-start functions!");
        else
            Logger.log(Logger.LogType.DEBUG, "Loading script...");
    }

    @Override
    public int onLoop() {
        try {
            ///  Don't start looping until the menu has been fully instantiated
            if (menu == null)
                return 1000;

            ///  Pause if the menu has
            if (menu.isMenuPaused)
                pause("Script paused via DreamBotMenu!");

            ///  Ensure inheritors pre-loop checks are performed before every loop
            if (!preLoop())
                throw new Exception("Error executing pre loop logic!");

//            ///  Check if the menu has been paused, if so, run the pause function. //TODO check possibility of this, i think its impossible?
//            if (getScriptManager().isPaused())
//                pause("Script paused.");

            if (queue == null || queue.isEmpty()) {
                pause("Task list is empty!");
                return 1000;
            }

            // fetch the current execution index, automatically correcting setting invalid indices to the first item
            int index = Math.max(0, menu.getCurrentExecutionIndex());

            // reset the list pointer
            menu.setCurrentExecutionIndex(index);

            // TODO add loop check here to continue from the top
            if (index >= queue.size()) {
                //if (menu.getTaskList().getLoopsLeft() > 0) {
                    // set it to the first item in the list if its invalid
                    menu.setCurrentExecutionIndex(0);
                    // pause the script
                    pause("Tasks complete!");
                    return Rand.nextInt(983);
                //}
            }

            // fetch the current task from the head of the queue
            DreamBotMenu.Task currentTask = queue.get(index);
            if (currentTask != null) {
                // update status using task status field which is always the initial message to display
                // (develops further as actions are executed)
                menu.setStatus(currentTask.getStatus());

                // if this task still has actions to complete
                boolean taskComplete = true;
                if (currentTask.getActions() != null) {
                    // for each action in this task
                    for (Action action : currentTask.getActions()) {
                        if (action == null){
                            Logger.log("Skipping null/invalid action in task: " + currentTask.getName());
                            continue;
                        }

                        if (!action.execute()) {
                            taskComplete = false;
                            break;
                        }

                        if (postAction())
                            Logger.log(Logger.LogType.DEBUG, "Action complete!");
                        else throw new Exception("Error executing after action logic!");
                    }
                }

                // increment the execution index to complete this tasks next action on the next cycle instead of repeating
                if (taskComplete)
                    menu.incrementExecutionIndex();
            }

        } catch (Exception e) {
            String error = e.getMessage();
            Logger.log(error != null && error.isEmpty() ? "Error executing main loop!" : error);
            e.printStackTrace();
        }

        return 600;
    }

    /**
     * Executes some code on game-state change, such as when the player logs in. This function is useful for ensuring
     * player data remains consistent with whichever account is logged in at all times.
     */
    @Override
    public final void onGameStateChange(GameState gameState) {
        if (gameState == GameState.LOGGED_IN) {
            ///  Trigger onLogin() logic
            postLogin();
            Logger.log(Logger.LogType.DEBUG, "Login success!");

            Player player = Players.getLocal();
            if (player.getName() != null) {
                ///  Trigger onNewLogin() logic
                postLoginNew();
                Logger.log(Logger.LogType.DEBUG, "New login detected!");
                String currentPlayer = player.getName();
                if (!currentPlayer.equals(lastPlayerName)) {
                    lastPlayerName = currentPlayer;
                    log("New login detected for: " + currentPlayer);
                    updateAccountData(currentPlayer);
                }
            }
        }
        postGameStateChange(gameState);
        Logger.log(Logger.LogType.DEBUG, "New game state change detected!");
    }

    @Override
    public final void onResume() {
        // super.onResume();
        resume("Client start triggered...");
    }

    public final void resume(String status) {
        if (menu != null)
            menu.resume(status);

        postResume();
        Logger.log(Logger.LogType.DEBUG, "Script resumed: " + status);
    }

    @Override
    public final void onPause() {
        //super.onPause();
        pause("Client pause triggered...");
    }

    public final void pause(String status) {
        ///  Scripts shouldn't be executing without a valid menu!
        if (menu != null)
            menu.pause(status);

        postPause();
        Logger.log(Logger.LogType.DEBUG, "Script paused: " + status);
    }

    @Override
    public final void onPaint(Graphics graphics) {
        super.onPaint(graphics);
        postPaint(graphics);
    }

    @Override
    public final void onExit() {
        super.onExit();

        // call post exit before menu disposal incase script needs to dispose of menu stuff
        postExit();

        // safely dispose menu and menu components
        if (menu != null)
            menu.onExit();

        Logger.log(Logger.LogType.DEBUG, "Successfully exited DreamBotMan by ETA.");
    }

    @Override
    public final void stop() {
        super.stop();
        postStop();
        Logger.log(Logger.LogType.DEBUG, "Stopping DreamBotMan...");
    }

    private void updateAccountData(String name) {
        // Update your specific configs here
        log("Fetching player data for " + name);
        menu.updateAll();
    }

    @Override
    public final boolean isUserVIP() {
        // TODO enable special features for those supporting DreamBot or me
        // TODO add to status tab
        return super.isUserVIP();
    }

    @Override
    public final boolean isUserSponsor() {
        // TODO enable special features for those supporting DreamBot or me
        // TODO add to status tab
        return super.isUserSponsor();
    }
}