package main.scripts;

import main.menu.DreamBotMenu;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;

import javax.swing.*;

@ScriptManifest(
        author = "ETA",
        name = "DreamBot",
        version = 1.0,
        category = Category.MISC
)
public class DreamBot extends AbstractScript {
    private DreamBotMenu menu;

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
        DefaultListModel<DreamBotMenu.Task> queue = menu.getTaskListModel();

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
}