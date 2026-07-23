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

    /**
     * v1.74: the stable lineage every version of one script shares, assigned by the server at
     * first publish (1.6.0). The market groups versions on THIS, not on name+author - grouping
     * on the name would merge two independent scripts the moment one is renamed to match the
     * other. Null for listings published before 1.6.0, which fall back to name+author.
     */
    public String lineageId;
    public long publishedAt = System.currentTimeMillis();

    /** Free-text tags, e.g. "combat", "ironman", "f2p". */
    public List<String> tags = new ArrayList<>();

    /**
     * v1.60: an optional card icon as base64 image bytes (raw base64 or a full {@code data:} URI -
     * the client decodes both). 128x128 PNG recommended; the server caps it at ~300 KB and just
     * stores/echoes it, so an unreadable blob degrades to the drawn placeholder, never an error.
     */
    public String icon;

    /**
     * v1.61: "the card is finished" - set by the Card Builder, and the gate every publish path
     * checks. Only meaningful while {@code origin == "local"} (it persists in the staging JSON);
     * a server listing by definition had a finished card, and the field just rides along
     * harmlessly in the publish payload (the server stores only the columns it knows).
     */
    public boolean cardReady = false;

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
    /** v1.59: love-hearts - total count + whether I favorited it (server fills both). */
    public int favorites;
    public boolean myFavorite;

    /** Patch B.16: VIP-gated listing. The server sends the bundle as null to non-VIP callers. */
    public boolean vipOnly = false;
    /** v1.33: "task" (reusable building block) or "script" (full single-purpose routine). The
     *  server caps these separately (free: 5 + 5). Defaults to task; the staging UI will set it. */
    public String kind = "task";

    // ── v1.87: author rank cosmetics ──
    // The server doesn't send these yet; when it starts attaching the author's tier /
    // scripter mark / donation total to listings, every card and detail view grows the
    // matching RankBadge automatically (Gson ignores absent fields, so old servers cost
    // nothing). Until then they stay null and nothing renders.
    public String authorTier;
    public Boolean authorScripter;
    public Long authorDonatedCents;
    /**
     * v1.32b: where this row came from - "server" (uploaded; has live stats) or "local" (a
     * script in your local market folder that hasn't been uploaded yet). Transient: it's a view
     * concern, never part of a bundle or a publish payload.
     */
    public transient String origin = "server";
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
