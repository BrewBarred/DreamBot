package main.data.store;

import main.menu.DreamBotMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * The complete saved state for a single RuneScape character - everything the DreamMan menu
 * needs to restore itself after a restart. Serialised to one JSON file per character by
 * {@link LocalStore}.
 * <p>
 * All fields are plain data (no {@code Action} objects), so Gson handles it without custom
 * adapters. {@link ProfileCodec} converts between this and the menu's live runtime types.
 */
public class ProfileData {

    /** Bumped when the on-disk shape changes so old files can be migrated rather than rejected. */
    public static final int CURRENT_VERSION = 1;

    public int version = CURRENT_VERSION;
    public String character;
    public long savedAt;

    /** The active queue (Task List tab). */
    public List<TaskData> taskList = new ArrayList<>();
    /** The saved task library (Task Library tab). */
    public List<TaskData> library = new ArrayList<>();
    /** Saved presets (queue snapshots). */
    public List<PresetData> presets = new ArrayList<>();
    /** The in-progress builder draft. */
    public BuilderData builder = new BuilderData();
    /** Whole-queue loop count for the live queue (0 = infinite). */
    public int queueLoops = 1;

    /** Patch B.3: per-skill XP goals (skill name -> goal xp). */
    public java.util.Map<String, Integer> skillGoals;

    /** Patch B.6: remember tracked skills for this account. */
    public boolean rememberTrackers = false;
    /** Patch B.6: the tracked skill names (only saved when rememberTrackers is on). */
    public java.util.List<String> trackedSkills;

    /** Patch B.4: always-on watchers, serialized to a JSON string via TriggerCodec. */
    public String globalTriggers;

    /** Patch B.2: auto-wait between completed tasks. */
    public boolean queueAutoWait = false;
    public int queueAutoWaitMinMs = 400;
    public int queueAutoWaitMaxMs = 1200;

    /** All menu settings (reuses the menu's own snapshot type). */
    public DreamBotMenu.SettingsSnapshot settings;
}
