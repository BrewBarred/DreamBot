package main.data.store;

import java.util.ArrayList;
import java.util.List;

/** Serialisable mirror of {@code DreamBotMenu.Preset} (a named list of tasks). */
public class PresetData {
    public String name;
    /** Whole-queue loop count this preset should run with (0 = infinite). */
    public int loops = 1;
    public List<TaskData> tasks = new ArrayList<>();
}
