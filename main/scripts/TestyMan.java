package main.scripts;

import main.BotMan;
import main.actions.Dig;
import org.dreambot.api.script.ScriptManifest; // OSBot Manifest -> DreamBot Manifest
import org.dreambot.api.script.Category; // Added Category requirement for DreamBot manifest

import java.awt.*;

@ScriptManifest(
        author = "ETA",
        name = "BETAs Testicle Script",
        version = 0.1,
        description = "", // info -> description
        category = Category.MISC // Required for DreamBot manifest
)
public class TestyMan extends BotMan {
    /**
     * onLoad: Write logic here that should only run when the bot starts, such as gearing up or adjusting menu options.
     */
    @Override
    public boolean onLoad() {
        ///  start running basic tests
        addTask(Dig.getTests());
        return true;
    }

    @Override
    protected void paintScriptOverlay(Graphics2D g) {

    }

    @Override
    public int onLoop() {
        return 420;
    }
}