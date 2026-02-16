package main.scripts;

import main.menu.DreamBotMenu4;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;

import javax.swing.*;

@ScriptManifest(
        author = "ETA",
        name = "DreamBot4",
        version = 1.0,
        category = Category.MISC
)
public class DreamBot4 extends AbstractScript {
    DreamBotMenu4 menu;

    @Override
    public void onStart() {
        SwingUtilities.invokeLater(() -> {
            menu = new DreamBotMenu4(this);
            menu.setAlwaysOnTop(true);
            menu.setVisible(true);
        });
    }

    @Override
    public int onLoop() {
        return 2134;
    }
}
