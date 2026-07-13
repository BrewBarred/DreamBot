package main.market;

import main.data.store.ScriptBundle;

import java.util.ArrayList;
import java.util.List;

/**
 * One script on the market (Patch B.11): its metadata plus the {@link ScriptBundle} payload.
 *
 * <p><b>Why the payload is JSON and not a .jar:</b> a jar is arbitrary executable code, and a
 * community market where non-technical players download and run strangers' jars is a malware
 * vector. A bundle is pure DATA - task names, action TYPES chosen from DreamMan's own registry,
 * and their parameters. Importing one can only ever construct actions DreamMan already ships, so
 * there is no way for a listing to run code on your machine. Anyone can still hit "Export as
 * script" locally afterwards to get a jar.
 */
public class ScriptListing {

    /** Stable id (uuid). */
    public String id;

    public String name = "";
    public String author = "";
    public String description = "";
    public double version = 1.0;
    public long publishedAt = System.currentTimeMillis();

    /** Free-text tags, e.g. "combat", "ironman", "f2p". */
    public List<String> tags = new ArrayList<>();

    /** The script itself. */
    public ScriptBundle bundle;

    // ── Stats. DERIVED, never authored: a repository fills these in when it lists.
    // They are deliberately NOT transient: an HTTP server computes them server-side and has to be
    // able to send them to us. (FolderRepository recomputes them from the rating files and zeroes
    // them again on publish, so a stored listing never carries stale numbers.)
    public double avgRating;
    public int ratingCount;
    public int downloads;
    /** This install's own rating (0 = hasn't rated). */
    public int myRating;

    /** Patch B.16: VIP-gated listing. The server sends the bundle as null to non-VIP callers. */
    public boolean vipOnly = false;
    public boolean locked = false;

    /** A one-line summary of what the script actually does - shown before you import it. */
    public String summarise() {
        if (bundle == null || bundle.tasks == null || bundle.tasks.isEmpty())
            return "(empty)";
        int tasks = bundle.tasks.size();
        int actions = 0;
        for (main.data.store.TaskData t : bundle.tasks)
            if (t != null && t.actions != null) actions += t.actions.size();
        return tasks + (tasks == 1 ? " task" : " tasks") + ", "
                + actions + (actions == 1 ? " action" : " actions") + ", "
                + (bundle.loops <= 0 ? "loops forever" : bundle.loops + " loop(s)");
    }

    /** Every distinct action type this script would add - so you can see what it will do. */
    public List<String> actionTypes() {
        List<String> types = new ArrayList<>();
        if (bundle == null || bundle.tasks == null) return types;
        for (main.data.store.TaskData t : bundle.tasks) {
            if (t == null || t.actions == null) continue;
            for (main.data.ActionData a : t.actions) {
                if (a == null || a.getType() == null) continue;
                if (!types.contains(a.getType())) types.add(a.getType());
            }
        }
        return types;
    }
}
