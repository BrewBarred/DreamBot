package main.data.store;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything an exported script (.jar) carries inside it (Patch B.10): the queue, how many times
 * to loop it, and the always-on checks the author built it with. Serialized to {@code
 * dreamman/script.json} inside the jar and read back by {@code ExportedScript} on start.
 */
public class ScriptBundle {

    /** Format version, so a future DreamMan can still read today's exports. */
    public int format = 1;

    public String name = "DreamMan Script";
    public String author = "Anonymous";
    public double version = 1.0;
    public String description = "";
    public long exportedAt = System.currentTimeMillis();

    /** The queue, in order. */
    public List<TaskData> tasks = new ArrayList<>();

    /** How many times to run the whole queue (0 or less = forever). */
    public int loops = 1;

    /** The author's always-on checks, as TriggerCodec JSON. */
    public String globalTriggers = "";
}
