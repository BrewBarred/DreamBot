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
    DreamBotMenu menu;

    @Override
    public void onStart() {
        SwingUtilities.invokeLater(() -> {
            menu = new DreamBotMenu(this);
            menu.setAlwaysOnTop(true);
            menu.setVisible(true);
        });
    }

    @Override
    public int onLoop() {
        return 2134;
    }
}
