package main.scripts;

import main.menu.DreamBotMenu;
import main.tools.ScriptExporter;
import org.dreambot.api.data.GameState;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.utilities.Logger;

import javax.swing.SwingUtilities;
import java.awt.Graphics;

/**
 * The runner inside every exported script jar (Patch B.10).
 *
 * <p>The manifest values below are PLACEHOLDERS. When you export a script, DreamMan copies its own
 * compiled classes into a new jar and rewrites the string constants in this class's bytecode, so
 * the jar shows up in DreamBot's script list under YOUR script's name and author. That means an
 * export needs no Java compiler at all - which matters, because most players run DreamBot on a JRE
 * and could never compile anything.
 *
 * <p>On start it loads {@code dreamman/script.json} from its own jar, drops the queue into the
 * menu, applies the author's checks and loop count, and runs. The full DreamMan menu is still
 * there, so whoever runs your script can watch it, pause it, or tweak it.
 */
@ScriptManifest(
        author = "__DM_AUTHOR__",
        name = "__DM_NAME__",
        version = 1234.5678,          // sentinel: patched to the real version at export time
        category = Category.MISC
)
public class ExportedScript extends DreamBotMan {

    @Override
    public boolean postStart() {
        // onStart() builds the menu inside an invokeLater; the EDT queue is FIFO, so this one
        // runs straight after it and the menu is guaranteed to exist by then.
        SwingUtilities.invokeLater(() -> {
            DreamBotMenu m = getMenu();
            if (m == null) {
                Logger.log(Logger.LogType.WARN, "[Exported] Menu unavailable - cannot load script.");
                return;
            }
            m.setEmbeddedScriptMode(true);   // the jar owns the queue + checks; profiles won't clobber them
            if (!ScriptExporter.loadEmbeddedInto(m))
                Logger.log(Logger.LogType.WARN, "[Exported] No embedded script found in this jar.");
        });
        return true;
    }

    @Override public void postResume() {}
    @Override public void postPause() {}
    @Override public void postPaint(Graphics graphics) {}
    @Override public void postStop() {}
    @Override public void postExit() {}
    @Override public void postLogin() {}
    @Override public void postLoginNew() {}
    @Override public void postGameStateChange(GameState gameState) {}
    @Override public boolean postAction() { return true; }
    @Override public boolean preLoop() { return true; }
}
