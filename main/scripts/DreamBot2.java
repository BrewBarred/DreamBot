package main.scripts;

import main.menu.DreamBotMenu2;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;

import javax.swing.*;

@ScriptManifest(
        author = "ETA",
        name = "DreamBot2",
        version = 1.0,
        category = Category.MISC
)
public class DreamBot2 extends AbstractScript {
    DreamBotMenu2 menu;

    @Override
    public void onStart() {
        SwingUtilities.invokeLater(() -> {
            menu = new DreamBotMenu2(this);
            menu.setAlwaysOnTop(true);
            menu.setVisible(true);
        });
    }

    @Override
    public int onLoop() {
        // 1. Check if the menu exists and isn't paused
        if (menu == null || menu.isScriptPaused()) {
            return 600;
        }

        // 2. Get the current task from the QueueModel
        if (menu.getQueueModel().isEmpty()) {
            menu.setLabelStatus("Status: Queue Empty");
            return 1000;
        }

        // Ensure we don't go out of bounds
        if (menu.getCurrentExecutionIndex() >= menu.getQueueModel().size()) {
            menu.setCurrentExecutionIndex(0); // Loop back or stop
            return 1000;
        }

        DreamBotMenu2.Task currentTask = menu.getQueueModel().get(menu.getCurrentExecutionIndex());
        menu.setLabelStatus("Status: Executing " + currentTask.name);

        // 3. Execute the actions within the task
        for (DreamBotMenu2.Action action : currentTask.actions) {
            if (action.execute()) {
                // Wait for the action to complete (basic sleep)
                return (int) (Math.random() * 600) + 1200;
            }
        }

        // 4. If task is done, move to next task
        // Note: You'll need logic to determine when a Task is "finished"
        // For now, we move to the next index
        menu.incrementExecutionIndex();

        return 600;
    }
}
