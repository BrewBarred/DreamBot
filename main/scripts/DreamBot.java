package main.scripts;

import org.dreambot.api.data.GameState;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;

import java.awt.*;

@ScriptManifest(
        author = "ETA",
        name = "DreamBot",
        version = 2.0,
        category = Category.MISC
)
public class DreamBot extends DreamBotMan {

    @Override
    public void postResume() {

    }

    @Override
    public void postPause() {

    }

    @Override
    public void postPaint(Graphics graphics) {

    }

    @Override
    public void postStop() {

    }

    @Override
    public void postExit() {

    }

    @Override
    public void postLogin() {

    }

    @Override
    public void postLoginNew() {

    }

    @Override
    public void postGameStateChange(GameState gameState) {

    }

    @Override
    public boolean preStart() {
        return true;
    }

    @Override
    public boolean postAction() {
        return true;
    }

    @Override
    public boolean preLoop() {
        return true;
    }
}
