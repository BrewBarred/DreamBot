package main.scripts;

import main.menu.DreamBotMenu5;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;

import javax.swing.*;

@ScriptManifest(
        author = "ETA",
        name = "DreamBot5",
        version = 1.0,
        category = Category.MISC
)
public class DreamBot5 extends AbstractScript {
    DreamBotMenu5 menu;

    @Override
    public void onStart() {
        SwingUtilities.invokeLater(() -> {
            menu = new DreamBotMenu5(this);
            menu.setAlwaysOnTop(true);
            menu.setVisible(true);
        });
    }

    @Override
    public int onLoop() {
        return 2134;
    }
}
