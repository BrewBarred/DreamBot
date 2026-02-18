package main.scripts;

import main.menu.DreamBotMenu;
import org.dreambot.api.data.GameState;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.listener.GameStateListener;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.wrappers.interactive.Player;

import javax.swing.*;
import java.awt.*;

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
    public abstract boolean preStart();
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

    @Override
    public final void onStart() {
        super.onStart();

        // initialize the GUI on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            menu = new DreamBotMenu(this);
            menu.setAlwaysOnTop(true);
            menu.setVisible(true);
        });

        // call child on-start for script-specific on-start setup
        if (!preStart())
            throw new RuntimeException("Error while setting up bot!");
    }

    @Override
    public final int onLoop() {
        try {
            if (!preLoop())
                throw new Exception("Error executing pre loop logic!");

            // prevent null pointer exception by waiting for the GUI to load
            if (menu == null)
                return 1000;

            // check every 1s to see if script is still paused
            if (menu.isScriptPaused())
                return 1000;

            // access the queue and return early if its invalid
            DefaultListModel<DreamBotMenu.Task> queue = menu.getModelTaskList();
            if (queue == null || queue.isEmpty()) {
                menu.setLabelStatus("Status: Queue Empty");
                return 1000;
            }

            // fetch the execution index and validate it
            int index = menu.getCurrentExecutionIndex();
            if (index < 0 || index >= queue.size()) {
                // set it to the first item in the list if its invalid
                menu.setCurrentExecutionIndex(0);
                return 100;
            }

            // fetch the current task from the head of the queue
            DreamBotMenu.Task currentTask = queue.get(index);
            if (currentTask != null) {
                // update status using task status field which is always the initial message to display
                // (develops further as actions are executed)
                menu.setLabelStatus("Status: " + currentTask.getStatus());

                // if this task still has actions to complete
                boolean taskComplete = true;
                if (currentTask.getActions() != null) {
                    // for each action in this task
                    for (DreamBotMenu.Action action : currentTask.getActions()) {
                        if (action == null){ //|| action.actionType == null) {
                            Logger.log("Skipping null/invalid action in task: " + currentTask.getName());
                            continue;
                        }

                        if (!action.execute()) {
                            taskComplete = false;
                            break;
                        }

                        if (!postAction())
                            throw new Exception("Error executing after action logic!");
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
            Player player = Players.getLocal();
            if (player.getName() != null) {
                ///  Trigger onNewLogin() logic
                postLoginNew();
                String currentPlayer = player.getName();
                if (!currentPlayer.equals(lastPlayerName)) {
                    lastPlayerName = currentPlayer;
                    log("New login detected for: " + currentPlayer);
                    updateAccountData(currentPlayer);
                }
            }
        }
        postGameStateChange(gameState);
    }

    @Override
    public final void onResume() {
        super.onResume();
        postResume();
    }

    @Override
    public final void onPause() {
        super.onPause();
        postPause();
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
    }

    @Override
    public final void stop() {
        super.stop();
        postStop();
    }

    @Override
    public final boolean isPaused() {
        return super.isPaused();
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