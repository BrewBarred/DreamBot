package main.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import main.data.store.ProfileCodec;
import main.data.store.ScriptBundle;
import main.data.store.TaskData;
import main.menu.DreamBotMenu;
import org.dreambot.api.utilities.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * Turns a DreamMan queue into a standalone DreamBot script jar (Patch B.10) - the "one-click
 * compile" from the original plan.
 *
 * <p><b>The key idea: no Java compiler is involved.</b> Most players run DreamBot on a plain JRE,
 * where {@code javax.tools.JavaCompiler} simply doesn't exist - a compile-based exporter would fail
 * for exactly the non-technical users this whole project is for. Instead we:
 *
 * <ol>
 *   <li>copy DreamMan's own already-compiled classes out of the running jar,</li>
 *   <li>rewrite two string constants inside {@code ExportedScript.class} so its
 *       {@code @ScriptManifest} carries YOUR script's name and author (a class file references its
 *       constants by index, never by byte offset, so resizing a UTF-8 constant is safe),</li>
 *   <li>drop the queue in as {@code dreamman/script.json},</li>
 *   <li>and leave out DreamMan's own {@code DreamBot} entry class so the jar advertises exactly one
 *       script - yours.</li>
 * </ol>
 *
 * The result is a self-contained jar the player drops into their DreamBot Scripts folder. It runs
 * on the same engine, with the same checks, and still shows the DreamMan menu so they can watch or
 * tweak it.
 */
public final class ScriptExporter {

    private ScriptExporter() {}

    /** Where the bundle lives inside an exported jar. */
    public static final String BUNDLE_PATH = "dreamman/script.json";

    /** The class we rewrite, and the placeholders inside it. */
    private static final String RUNNER_CLASS = "main/scripts/ExportedScript.class";
    private static final String PH_NAME = "__DM_NAME__";
    private static final String PH_AUTHOR = "__DM_AUTHOR__";
    /** The sentinel version compiled into the runner (patched to the real one at export). */
    private static final double PH_VERSION = 1234.5678;

    /** DreamMan's own entry script - excluded so an export advertises only ONE script. */
    private static final String OWN_SCRIPT = "main/scripts/DreamBot.class";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ══════════════════════════ export ══════════════════════════

    /**
     * Writes a runnable DreamBot script jar.
     *
     * @param bundle  the queue + loops + checks to embed
     * @param outFile where to write (parent dirs are created)
     * @throws IOException with a human-readable reason if it can't be built
     */
    public static void export(ScriptBundle bundle, File outFile) throws IOException {
        if (bundle == null) throw new IOException("Nothing to export.");
        if (bundle.tasks == null || bundle.tasks.isEmpty())
            throw new IOException("The queue is empty - add some tasks first.");

        File self = ownCodeSource();
        if (self == null)
            throw new IOException("Could not locate DreamMan's own jar to copy classes from.");

        String json = GSON.toJson(bundle);
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs())
            throw new IOException("Could not create " + parent);

        File tmp = new File(outFile.getAbsolutePath() + ".tmp");
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(tmp))) {
            if (self.isDirectory())
                copyFromDirectory(self, out, bundle);
            else
                copyFromJar(self, out, bundle);

            // the embedded script itself
            out.putNextEntry(new JarEntry(BUNDLE_PATH));
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        // atomic-ish swap so a failed export never leaves a broken jar behind
        if (outFile.exists() && !outFile.delete())
            throw new IOException("Could not overwrite " + outFile.getName() + " (is it in use?)");
        if (!tmp.renameTo(outFile))
            throw new IOException("Could not finalise " + outFile.getName());

        Logger.log("[Export] Wrote " + outFile.getName() + " (" + bundle.tasks.size()
                + " task(s), " + (outFile.length() / 1024) + " KB)");
    }

    /** Copies entries out of the running jar, patching the runner and skipping what we replace. */
    private static void copyFromJar(File jar, JarOutputStream out, ScriptBundle b) throws IOException {
        try (JarFile in = new JarFile(jar)) {
            Enumeration<JarEntry> entries = in.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                String name = e.getName();
                if (e.isDirectory() || skip(name)) continue;

                byte[] data = readAll(in.getInputStream(e));
                if (RUNNER_CLASS.equals(name))
                    data = patchRunner(data, b);

                out.putNextEntry(new JarEntry(name));
                out.write(data);
                out.closeEntry();
            }
        }
    }

    /** Same, for when DreamMan is running from a classes directory (IDE / dev runs). */
    private static void copyFromDirectory(File root, JarOutputStream out, ScriptBundle b) throws IOException {
        Path base = root.toPath();
        List<Path> files = new ArrayList<>();
        try (java.util.stream.Stream<Path> walk = Files.walk(base)) {
            walk.filter(Files::isRegularFile).forEach(files::add);
        }
        for (Path p : files) {
            String name = base.relativize(p).toString().replace(File.separatorChar, '/');
            if (skip(name)) continue;

            byte[] data = Files.readAllBytes(p);
            if (RUNNER_CLASS.equals(name))
                data = patchRunner(data, b);

            out.putNextEntry(new JarEntry(name));
            out.write(data);
            out.closeEntry();
        }
    }

    /** Entries we never copy into an export. */
    private static boolean skip(String name) {
        if (name == null) return true;
        if (OWN_SCRIPT.equals(name)) return true;          // don't advertise DreamMan itself
        if (name.equals(BUNDLE_PATH)) return true;         // a re-export must not carry the old one
        if (name.startsWith("META-INF/")) {
            // drop signatures (they'd be invalid once we change bytes) but keep nothing else -
            // a script jar needs no manifest of its own; DreamBot scans for @ScriptManifest.
            return true;
        }
        return false;
    }

    // ══════════════════════════ bytecode patching ══════════════════════════

    /**
     * Rewrites the runner's manifest constants. A class file addresses its constant pool by INDEX,
     * never by byte offset, and every attribute carries its own length - so growing or shrinking a
     * UTF-8 constant is safe as long as its 2-byte length prefix is updated with it. That's all we
     * do here, which is why this needs no compiler and can't corrupt the class.
     */
    static byte[] patchRunner(byte[] cls, ScriptBundle b) throws IOException {
        byte[] out = cls;
        out = replaceUtf8(out, PH_NAME, safe(b.name, "DreamMan Script"));
        out = replaceUtf8(out, PH_AUTHOR, safe(b.author, "Anonymous"));
        out = replaceDouble(out, PH_VERSION, b.version <= 0 ? 1.0 : b.version);
        return out;
    }

    /** Replaces a CONSTANT_Utf8 entry's contents (tag 0x01, u2 length, bytes). */
    static byte[] replaceUtf8(byte[] cls, String from, String to) throws IOException {
        byte[] fromBytes = from.getBytes(StandardCharsets.UTF_8);
        byte[] toBytes = to.getBytes(StandardCharsets.UTF_8);
        if (toBytes.length > 0xFFFF)
            throw new IOException("Name or author is far too long.");

        // find: 0x01, len_hi, len_lo, <fromBytes>
        for (int i = 0; i + 3 + fromBytes.length <= cls.length; i++) {
            if (cls[i] != 0x01) continue;
            int len = ((cls[i + 1] & 0xFF) << 8) | (cls[i + 2] & 0xFF);
            if (len != fromBytes.length) continue;
            if (!regionEquals(cls, i + 3, fromBytes)) continue;

            ByteArrayOutputStream bos = new ByteArrayOutputStream(cls.length + toBytes.length);
            bos.write(cls, 0, i);                       // everything before the entry
            bos.write(0x01);                            // CONSTANT_Utf8 tag
            bos.write((toBytes.length >>> 8) & 0xFF);   // new length, high byte
            bos.write(toBytes.length & 0xFF);           // new length, low byte
            bos.write(toBytes, 0, toBytes.length);      // new contents
            int after = i + 3 + fromBytes.length;
            bos.write(cls, after, cls.length - after);  // everything after
            return bos.toByteArray();
        }
        throw new IOException("Could not find the placeholder \"" + from + "\" to patch. "
                + "(Was ExportedScript rebuilt?)");
    }

    /** Replaces a CONSTANT_Double (tag 0x06 + 8 bytes) - same size, so a straight overwrite. */
    static byte[] replaceDouble(byte[] cls, double from, double to) throws IOException {
        long fromBits = Double.doubleToRawLongBits(from);
        long toBits = Double.doubleToRawLongBits(to);
        for (int i = 0; i + 9 <= cls.length; i++) {
            if (cls[i] != 0x06) continue;
            long bits = 0;
            for (int k = 0; k < 8; k++) bits = (bits << 8) | (cls[i + 1 + k] & 0xFFL);
            if (bits != fromBits) continue;
            byte[] copy = cls.clone();
            for (int k = 0; k < 8; k++)
                copy[i + 1 + k] = (byte) ((toBits >>> (56 - 8 * k)) & 0xFF);
            return copy;
        }
        // a missing version sentinel isn't fatal - the script still runs, just at 1.0
        Logger.log(Logger.LogType.DEBUG, "[Export] Version sentinel not found; leaving as-is.");
        return cls;
    }

    private static boolean regionEquals(byte[] a, int off, byte[] b) {
        for (int i = 0; i < b.length; i++)
            if (a[off + i] != b[i]) return false;
        return true;
    }

    // ══════════════════════════ import (runtime side) ══════════════════════════

    /** Reads the bundle embedded in the running jar, or null when there isn't one. */
    public static ScriptBundle readEmbedded() {
        try (InputStream in = ScriptExporter.class.getClassLoader().getResourceAsStream(BUNDLE_PATH)) {
            if (in == null) return null;
            return GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), ScriptBundle.class);
        } catch (Throwable t) {
            Logger.log(Logger.LogType.WARN, "[Exported] Could not read the embedded script: " + t);
            return null;
        }
    }

    /**
     * Loads the embedded script into the menu: queue, loop count and the author's checks. Called by
     * {@code ExportedScript} on start. @return true if a script was found and loaded.
     */
    public static boolean loadEmbeddedInto(DreamBotMenu menu) {
        ScriptBundle b = readEmbedded();
        if (b == null || menu == null) return false;

        try {
            javax.swing.DefaultListModel<DreamBotMenu.Task> queue = menu.getModelTaskList();
            queue.clear();
            int loaded = 0;
            if (b.tasks != null)
                for (TaskData td : b.tasks) {
                    DreamBotMenu.Task t = ProfileCodec.fromData(td);
                    if (t != null) { queue.addElement(t); loaded++; }
                }

            menu.setQueueLoopTarget(b.loops);

            if (b.globalTriggers != null && !b.globalTriggers.isBlank()) {
                menu.getGlobalTriggers().clear();
                menu.getGlobalTriggers().addAll(main.watchers.TriggerCodec.fromJson(b.globalTriggers));
                menu.reloadChecksEditor();
            }

            menu.setCurrentExecutionIndex(0);
            menu.setStatus("Loaded \"" + b.name + "\" by " + b.author);
            Logger.log("[Exported] Loaded \"" + b.name + "\" - " + loaded + " task(s), "
                    + (b.loops <= 0 ? "looping forever" : b.loops + " loop(s)"));
            return loaded > 0;
        } catch (Throwable t) {
            Logger.log(Logger.LogType.WARN, "[Exported] Failed to load the embedded script: " + t);
            return false;
        }
    }

    /** True when we're running from an exported jar (i.e. one with a script inside). */
    public static boolean isExportedJar() { return readEmbedded() != null; }

    // ══════════════════════════ plumbing ══════════════════════════

    /** The jar (or classes dir) DreamMan is running from. */
    static File ownCodeSource() {
        try {
            java.net.URL loc = ScriptExporter.class.getProtectionDomain().getCodeSource().getLocation();
            return new File(loc.toURI());
        } catch (Throwable t) {
            Logger.log(Logger.LogType.WARN, "[Export] Could not locate our own code source: " + t);
            return null;
        }
    }

    /**
     * DreamBot's Scripts folder, so an export can land where the client will actually find it.
     * Falls back to null when the usual location doesn't exist (the UI then asks where to save).
     */
    public static File dreamBotScriptsDir() {
        // v1.89 (SDN compliance): derived from scripts.path, never user.home. The
        // guidelines name user.home explicitly as a path scripts must not use, so the
        // exporter asks the CLIENT where its scripts live instead of guessing.
        String home = System.getProperty("scripts.path");
        if (home == null) return null;
        File dir = new File(home.trim());
        return dir.isDirectory() ? dir : null;
    }

    /** Strips anything that would make a bad file name or a confusing script name. */
    public static String safeFileName(String name) {
        String s = safe(name, "DreamMan Script").replaceAll("[^A-Za-z0-9 _.-]", "").trim();
        return s.isEmpty() ? "DreamMan Script" : s;
    }

    private static String safe(String s, String fallback) {
        return (s == null || s.trim().isEmpty()) ? fallback : s.trim();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(64, in.available()));
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}
