package main.scripts;

import main.menu.DreamBotMenu3;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;

import javax.swing.*;

@ScriptManifest(
        author = "ETA",
        name = "DreamBot3",
        version = 1.0,
        category = Category.MISC
)
public class DreamBot3 extends AbstractScript {
    DreamBotMenu3 menu;

    @Override
    public void onStart() {
        SwingUtilities.invokeLater(() -> {
            menu = new DreamBotMenu3(this);
            menu.setAlwaysOnTop(true);
            menu.setVisible(true);
        });
    }

    @Override
    public int onLoop() {
        return 2134;
    }
}
