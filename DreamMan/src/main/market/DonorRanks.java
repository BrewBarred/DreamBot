package main.market;

import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import main.data.store.LocalStore;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The donor-rank ladder (v1.87): cosmetic levels earned by donating, shown as a coin badge next
 * to a person's name wherever names appear. ORTHOGONAL to the tier ladder on purpose - a free
 * user who chipped in wears their coin exactly like a lifetime buyer would.
 *
 * <p>Admins shape the ladder in the Dev Console's <i>Donor Ranks</i> tab: set the dollar
 * threshold for each level and NAME it whatever fits the community ("$100 = The Quiet Donor").
 * The names are the cosmetic - the tag someone gets beside their name - so they're meant to be
 * renamed. Edits persist locally at {@code <root>/donor-ranks.json} and are pushed to
 * {@code /admin/donor-ranks} when the server grows that endpoint; until then the local ladder
 * is the ladder (and these shipped defaults are what a fresh install shows).
 *
 * <p>No money moves through any of this yet - donations aren't set up. The Donate button says
 * so, and the "donated" numbers only exist where an admin has recorded them by hand.
 */
public final class DonorRanks {

    private DonorRanks() {}

    /** One rung: donate at least {@code minCents} total and you wear {@code name}. */
    public static final class Level {
        public long minCents;
        public String name;

        public Level() {}   // Gson
        public Level(long minCents, String name) { this.minCents = minCents; this.name = name; }
    }

    private static final Type LIST_TYPE = new TypeToken<List<Level>>() {}.getType();

    private static volatile List<Level> levels;

    private static File file() { return new File(LocalStore.getRoot(), "donor-ranks.json"); }

    /** The shipped ladder - reasonable rungs an owner will rename to taste. */
    private static List<Level> defaults() {
        List<Level> d = new ArrayList<>();
        d.add(new Level(500,    "Supporter"));        // $5+
        d.add(new Level(2_500,  "Generous Donor"));   // $25+
        d.add(new Level(10_000, "The Quiet Donor"));  // $100+
        d.add(new Level(25_000, "Patron of Dreams")); // $250+
        return d;
    }

    /** The current ladder, ascending by threshold. Never empty, never null. */
    public static synchronized List<Level> levels() {
        if (levels != null) return new ArrayList<>(levels);
        List<Level> loaded = null;
        try {
            File f = file();
            if (f.isFile())
                loaded = new GsonBuilder().create().fromJson(
                        new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8), LIST_TYPE);
        } catch (Throwable ignored) {}
        levels = sane(loaded);
        return new ArrayList<>(levels);
    }

    /** Replaces the ladder (Dev Console editor). Persists locally; the caller syncs the server. */
    public static synchronized void save(List<Level> newLevels) {
        levels = sane(newLevels);
        try {
            file().getParentFile().mkdirs();
            Files.write(file().toPath(), new GsonBuilder().setPrettyPrinting().create()
                    .toJson(levels, LIST_TYPE).getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {}
    }

    /** Adopts a server-published ladder (JSON list) without writing it back up. */
    public static synchronized void adopt(String json) {
        try {
            List<Level> got = new GsonBuilder().create().fromJson(json, LIST_TYPE);
            if (got != null && !got.isEmpty()) save(got);
        } catch (Throwable ignored) {}
    }

    /** Drops junk rows, sorts ascending, and falls back to the defaults when nothing survives. */
    private static List<Level> sane(List<Level> in) {
        List<Level> out = new ArrayList<>();
        if (in != null)
            for (Level l : in)
                if (l != null && l.minCents > 0 && l.name != null && !l.name.isBlank())
                    out.add(new Level(l.minCents, l.name.trim()));
        if (out.isEmpty()) return defaults();
        out.sort((a, b) -> Long.compare(a.minCents, b.minCents));
        return out;
    }

    /** The 1-based donor level for a lifetime total, or 0 for "not a donor yet". */
    public static int levelFor(long donatedCents) {
        int lvl = 0;
        for (Level l : levels())
            if (donatedCents >= l.minCents) lvl++;
        return lvl;
    }

    /** The cosmetic tag for a lifetime total, or null when below every rung. */
    public static String nameFor(long donatedCents) {
        String name = null;
        for (Level l : levels())
            if (donatedCents >= l.minCents) name = l.name;
        return name;
    }

    /** "$12.50" - the ledger formatting used everywhere donations show. */
    public static String dollars(long cents) {
        return String.format("$%,.2f", cents / 100.0);
    }
}
