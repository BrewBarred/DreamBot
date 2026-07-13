package main.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.dreambot.api.utilities.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * A market backed by a plain folder (Patch B.11). This is the version that works <b>today</b>,
 * with no server and no hosting: point it at a Dropbox / Google Drive / OneDrive / network folder
 * that a group of people share, and they have a working script market between them.
 *
 * <p><b>Layout</b> (all plain JSON, so it's inspectable and portable):
 * <pre>
 *   &lt;repo&gt;/scripts/&lt;id&gt;.json              one listing (metadata + the script bundle)
 *   &lt;repo&gt;/ratings/&lt;id&gt;__&lt;install&gt;.json   one person's rating of one script
 *   &lt;repo&gt;/downloads/&lt;id&gt;__&lt;install&gt;      an empty marker file
 * </pre>
 *
 * <p><b>Why one file per person per script</b> rather than a shared counter: two people rating at
 * the same moment would otherwise clobber each other's write. With a file each, there is nothing
 * to race on - and counting unique files gives unique-person ratings and downloads for free, which
 * is the more honest metric anyway.
 *
 * <p><b>Being straight about the limits:</b> a shared folder has no authentication. Anyone with
 * write access can edit or delete anything, and could rate their own scripts from several installs.
 * That's fine for a group of people who know each other, and not fine for a public market - which
 * is what {@link HttpRepository} is for.
 */
public class FolderRepository implements ScriptRepository {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File root;

    public FolderRepository(File root) {
        this.root = root;
        scriptsDir().mkdirs();
        ratingsDir().mkdirs();
        downloadsDir().mkdirs();
    }

    private File scriptsDir()   { return new File(root, "scripts"); }
    private File ratingsDir()   { return new File(root, "ratings"); }
    private File downloadsDir() { return new File(root, "downloads"); }

    @Override
    public String describe() {
        return "Folder: " + root.getAbsolutePath();
    }

    @Override
    public List<ScriptListing> list() {
        List<ScriptListing> out = new ArrayList<>();
        File[] files = scriptsDir().listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return out;

        String me = MarketIdentity.installId();
        for (File f : files) {
            try {
                String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                ScriptListing l = GSON.fromJson(json, ScriptListing.class);
                if (l == null || l.id == null) continue;

                // fold in the ratings/downloads that live beside it
                int sum = 0, count = 0, mine = 0;
                File[] rs = ratingsDir().listFiles((d, n) -> n.startsWith(l.id + "__"));
                if (rs != null)
                    for (File r : rs) {
                        try {
                            Rating rating = GSON.fromJson(
                                    new String(Files.readAllBytes(r.toPath()), StandardCharsets.UTF_8),
                                    Rating.class);
                            if (rating == null || rating.stars < 1 || rating.stars > 5) continue;
                            sum += rating.stars;
                            count++;
                            if (r.getName().equals(l.id + "__" + me + ".json")) mine = rating.stars;
                        } catch (Throwable ignored) {}
                    }
                l.ratingCount = count;
                l.avgRating = count == 0 ? 0 : (double) sum / count;
                l.myRating = mine;

                File[] ds = downloadsDir().listFiles((d, n) -> n.startsWith(l.id + "__"));
                l.downloads = ds == null ? 0 : ds.length;

                out.add(l);
            } catch (Throwable t) {
                Logger.log(Logger.LogType.WARN, "[Market] Skipping unreadable listing "
                        + f.getName() + ": " + t);
            }
        }
        out.sort(Comparator.comparingLong((ScriptListing l) -> l.publishedAt).reversed());
        return out;
    }

    @Override
    public void publish(ScriptListing listing) throws Exception {
        if (listing == null || listing.bundle == null)
            throw new IOException("Nothing to publish.");
        if (listing.name == null || listing.name.trim().isEmpty())
            throw new IOException("Give the script a name.");
        if (listing.bundle.tasks == null || listing.bundle.tasks.isEmpty())
            throw new IOException("The script has no tasks.");
        if (!scriptsDir().isDirectory() && !scriptsDir().mkdirs())
            throw new IOException("Can't write to " + root.getAbsolutePath()
                    + " - is the folder shared and available?");

        if (listing.id == null || listing.id.isEmpty())
            listing.id = UUID.randomUUID().toString();

        // the stats are derived from the rating/download files - never store them, or a listing
        // would carry stale numbers around and could be edited to fake its own rating
        listing.avgRating = 0;
        listing.ratingCount = 0;
        listing.downloads = 0;
        listing.myRating = 0;

        File out = new File(scriptsDir(), listing.id + ".json");
        writeAtomically(out, GSON.toJson(listing));
        Logger.log("[Market] Published \"" + listing.name + "\" to " + root.getName());
    }

    @Override
    public void rate(String scriptId, int stars) throws Exception {
        if (scriptId == null || scriptId.isEmpty()) throw new IOException("Unknown script.");
        if (stars < 1 || stars > 5) throw new IOException("Rating must be 1-5 stars.");
        ratingsDir().mkdirs();

        Rating r = new Rating();
        r.stars = stars;
        r.at = System.currentTimeMillis();
        r.by = MarketIdentity.displayName();

        File out = new File(ratingsDir(), scriptId + "__" + MarketIdentity.installId() + ".json");
        writeAtomically(out, GSON.toJson(r));   // one file per install: re-rating just overwrites
    }

    @Override
    public void noteDownload(String scriptId) {
        try {
            downloadsDir().mkdirs();
            File marker = new File(downloadsDir(), scriptId + "__" + MarketIdentity.installId());
            if (!marker.exists()) marker.createNewFile();
        } catch (Throwable ignored) {
            // a download that can't be counted is not worth failing the download over
        }
    }

    @Override
    public void remove(String scriptId) throws Exception {
        File f = new File(scriptsDir(), scriptId + ".json");
        if (f.exists() && !f.delete())
            throw new IOException("Could not remove the listing (is the file open?).");
    }

    /** Writes via a temp file + rename so a half-written listing can never appear. */
    private static void writeAtomically(File target, String json) throws IOException {
        File tmp = new File(target.getAbsolutePath() + ".tmp");
        Files.write(tmp.toPath(), json.getBytes(StandardCharsets.UTF_8));
        if (target.exists() && !target.delete())
            throw new IOException("Could not overwrite " + target.getName());
        if (!tmp.renameTo(target))
            throw new IOException("Could not finalise " + target.getName());
    }

    /** One person's rating of one script. */
    static class Rating {
        int stars;
        long at;
        String by;
    }
}
