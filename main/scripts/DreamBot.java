package main.scripts;

import main.menu.DreamBotMenu;
import org.dreambot.api.Client;
import org.dreambot.api.data.GameState;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.script.listener.GameStateListener;

import javax.swing.*;

@ScriptManifest(
        author = "ETA",
        name = "DreamBot",
        version = 1.0,
        category = Category.MISC
)
public class DreamBot extends AbstractScript implements GameStateListener {
    private DreamBotMenu menu;
    private String lastPlayerName = "";

    @Override
    public void onStart() {
        // Initialize the GUI on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            menu = new DreamBotMenu(this);
            menu.setAlwaysOnTop(true);
            menu.setVisible(true);
        });
    }

    @Override
    public int onLoop() {
        // Prevent NullPointerException by waiting for the GUI to load
        if (menu == null) {
            return 1000;
        }

        // 1. Use your isScriptPaused check
        if (menu.isScriptPaused()) {
            return 600;
        }

        // 2. Access the queue
        DefaultListModel<DreamBotMenu.Task> queue = menu.getModelTaskList();

        if (queue == null || queue.isEmpty()) {
            menu.setLabelStatus("Status: Queue Empty");
            return 1000;
        }

        // 3. Handle the execution index
        int index = menu.getCurrentExecutionIndex();

        if (index < 0 || index >= queue.size()) {
            menu.setCurrentExecutionIndex(0);
            return 100;
        }

        // 4. Access fields LITERALLY as defined in your Task class
        DreamBotMenu.Task currentTask = queue.get(index);

        if (currentTask != null) {
            // Using your actual field: .status
            menu.setLabelStatus("Status: " + currentTask.getStatus());

            boolean taskComplete = true;

            // Using your actual field: .actions
            if (currentTask.getActions() != null) {
                for (DreamBotMenu.Action action : currentTask.getActions()) {
                    // This calls the execute() method within your Action class
                    if (!action.execute()) {
                        taskComplete = false;
                        break;
                    }
                }
            }

            // 5. Progress the queue if the actions are finished
            if (taskComplete) {
                menu.incrementExecutionIndex();
            }
        }

        return 600;
    }

    /**
     * This triggers whenever the game state changes (e.g., LOGIN_SCREEN -> LOGGED_IN)
     */
    @Override
    public void onGameStateChange(GameState gameState) {
        if (gameState == GameState.LOGGED_IN) {
            // Give the client a moment to fully load the player object
            Players.getLocal();
            String currentPlayer = Players.getLocal().getName();

            if (currentPlayer != null && !currentPlayer.equals(lastPlayerName)) {
                log("New login detected for: " + currentPlayer);
                updateAccountData(currentPlayer);
                lastPlayerName = currentPlayer;
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();

    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPaint(java.awt.Graphics graphics) {
        super.onPaint(graphics);
    }

    @Override
    public void onExit() {
        super.onExit();

    }

    @Override
    public boolean isUserVIP() {
        // TODO enable special features for those supporting DreamBot or me
        // TODO add to status tab
        return super.isUserVIP();
    }

    @Override
    public boolean isUserSponsor() {
        // TODO enable special features for those supporting DreamBot or me
        // TODO add to status tab
        return super.isUserSponsor();
    }

    @Override
    public void stop() {
        super.stop();
    }

    @Override
    public boolean isPaused() {
        return super.isPaused();
    }

    private void updateAccountData(String name) {
        // Update your specific configs here
        log("Fetching data for " + name);
        menu.loadTaskList();
        menu.loadTaskLibrary();
        //menu.load
        //menu.loadTaskBuilder();
    }
}