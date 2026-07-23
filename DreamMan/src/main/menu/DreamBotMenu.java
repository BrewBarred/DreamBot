package main.menu;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import main.data.ActionData;
import main.data.store.LocalStore;
import main.data.store.ProfileData;
import main.data.store.ProfileCodec;
import main.data.store.LibraryData;
import main.data.store.TaskData;
import main.data.store.PresetData;
import main.data.store.BuilderData;
import main.data.BuilderSnapshot;
import main.data.library.LibraryPanel;
import main.managers.DataMan;
import main.menu.components.JActionSelector;
import main.menu.skills.SkillData;
import main.tools.Rand;
import org.dreambot.api.Client;
import org.dreambot.api.ClientSettings;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.methods.skills.Skills;
import org.dreambot.api.methods.world.Worlds;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.ScriptManager;
import org.dreambot.api.utilities.AccountManager;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.methods.container.impl.equipment.Equipment;
import org.dreambot.api.wrappers.items.Item;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.core.Instance;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

import main.actions.Action;

import static main.menu.MenuHandler.*;

/**
 * <h1>DreamBotMan</h1>
 * @author DreamBotMan Dev
 * @version 15.0.0-Elite
 */
public class DreamBotMenu extends JFrame {
    private final ScriptManager scriptManager;
    /** Bridge to the running script for login/logout/stop (Patch A2). */
    private ScriptControls scriptControls;
    public void setScriptControls(ScriptControls c) { this.scriptControls = c; }

    /** Forces the menu window to the front (used by the in-game "Menu" button). */
    public void bringToFront() {
        SwingUtilities.invokeLater(() -> {
            setVisible(true);
            if ((getExtendedState() & Frame.ICONIFIED) != 0)
                setExtendedState(getExtendedState() & ~Frame.ICONIFIED);
            // toggling always-on-top nudges most window managers to actually raise it
            setAlwaysOnTop(false);
            setAlwaysOnTop(true);
            toFront();
            requestFocus();
        });
    }
    private volatile boolean isMenuPaused;
    /** The owning script - lets the menu coordinate with the engine (Patch B.5). */
    private AbstractScript script;

    /**
     * Patch B.10: true when running from an EXPORTED script jar. The script ships its own queue
     * and checks, so a profile load must not overwrite them - but the player's own settings
     * (skill goals, tracked skills, preferences) still load as normal.
     */
    private volatile boolean embeddedScriptMode = false;
    public void setEmbeddedScriptMode(boolean b) { embeddedScriptMode = b; }
    public boolean isEmbeddedScriptMode() { return embeddedScriptMode; }

    /** Refreshes the Checks tab list after its contents are replaced programmatically (B.10). */
    public void reloadChecksEditor() {
        if (checksEditor != null) checksEditor.reload();
    }
    private final TaskBuilder taskBuilder;
    public final LibraryPanel libraryPanel;
    private static final int PRESET_COLUMNS = 4;
    /**
     * The amount of time (ms) before the status is automatically reverted to the {@link #DEFAULT_STATUS_STRING}
     */
    private final int DEFAULT_STATUS_RESET_DELAY = 3000;
    private final String DEFAULT_STATUS_STRING = "Day-dreaming...";
    /**
     * The color of all styled buttons
     */

    ///  THEME VARIABLES
    private final Color COLOR_BUTTON_TEXT = Color.WHITE;
    private final Color COLOR_BUTTON_DEFAULT = Color.GRAY;
    private final Color COLOR_BUTTON_RED = new Color(139, 0, 0); // matches "Reset All" (Patch B.2)


    //TODO SETTINGS:
    ///  DEV Settings:
    ///
    /// import org.dreambot.api.ClientSettings;
    ///
    /// // Enable delete-on-ban
    /// ClientSettings.setDeleteBannedAccounts(true);
    ///
    /// // Disable delete-on-ban
    /// ClientSettings.setDeleteBannedAccounts(false);

    // --- Task ---
    boolean isTaskDescriptionRequired = false;
    boolean isTaskStatusRequired = false;

    // --- State ---
    private static boolean isMouseInput;
    private static boolean isKeyboardInput;
    private volatile boolean isDataLoading = false;

    /** Patch B.1: ensures the "default" profile loads exactly once while waiting at login. */
    private volatile boolean defaultProfileLoaded = false;
    private int currentExecutionIndex = -1;

    // --- Script ---
    public boolean startScriptOnLoad;
    public boolean exitOnStopWarning;
    /**
     * Timer used to rapidly refresh the GUI to keep it updated.
     * <p>
     * Recommended default = 1000ms (keep GUI updated, low-cost only on this thread!!)
     */
    private Timer uiTimer;
    /**
     * Timer used to scan for nearby targets in short intervals while the Task Builder tab is open.
     * <p>
     * Recommended default = 4000ms (balance between overkill updating and up-to-date list)
     */
    private Timer scanTimer;
    /**
     * Timer used to periodically auto-save the players details to the server to back up their script settings.
     * <p>
     * Recommended default = 60000ms (avoids server congestion and lag)
     */
    private Timer saveTimer;
    private boolean isSettingProcessing = false;
    /**
     * Flag to stop the snap-back from re-triggered listeners
     */
    private boolean isReverting = false;
    private final int TOAST_DELAY = 300;

    private final JPanel sidePanel;

    private final Color COLOR_FAILURE = new Color(100, 0, 0);
    private final Color COLOR_SUCCESS = new Color(0, 100, 0);
    private final Color COLOR_LIGHT_GREEN = new Color(150, 200, 50);
    private final Color COLOR_EXECUTING = Theme.AMBER;
    private final String ICON_EXECUTING = "→ ";
    private final Font FONT_EXECUTING = new Font("Consolas", Font.BOLD, 12);

    // --- Data & Presets ---
    private final int MAX_TOAST_QUEUE = 1;
    private final Queue<ToastRequest> toastQueue = new LinkedList<>();
    private boolean isToastProcessing = false;

    private final DataMan dataMan = new DataMan();
    // Local persistence (Patch 2): the offline-first source of truth. The Supabase-backed
    // DataMan above is left untouched and becomes an optional sync layer in a later patch.

    private final Map<Skill, SkillData> skillRegistry = new EnumMap<>(Skill.class);

    private final DefaultListModel<Task> modelTaskList = new DefaultListModel<>();
    private final JList<Task> listTaskList = new JList<>(modelTaskList);
    /** v1.68: on-start-only toggle for the selected queue entry, plus its listener guard. */
    private JCheckBox chkTaskOnStart;
    private boolean taskOnStartSyncing = false;

    /**
     * Patch B.5: the library's single source of truth. {@link #modelTaskLibrary} is only the
     * filtered/sorted VIEW the JList shows - search and the origin filter rebuild the view from
     * here, so hiding tasks can never lose them: saves, exports, use-counts, TaskRef resolution
     * and builder propagation all read THIS list.
     */
    final List<Task> libraryAll = new ArrayList<>();
    final DefaultListModel<Task> modelTaskLibrary = new DefaultListModel<>();
    private final JList<Task> listTaskLibrary = new JList<>(modelTaskLibrary);
    private JTextField librarySearchField;
    /** The Checks tab's editor - reloaded after profile loads so restored checks show (B.5). */
    private main.menu.components.TriggerEditor checksEditor;
    /** Patch B.14: the DreamMan account tier shown on the Status card (Guest/Free/VIP/Admin). */
    private final JLabel lblAccountTier = new JLabel("Guest");
    // Patch B.6: remember tracked skills per account.
    private boolean rememberTrackers = false;
    private JCheckBox rememberTrackersCheck;

    // ── Patch B.11: the script market (v1.60: rebuilt as a card grid) ──
    private main.market.ScriptRepository marketRepo;
    // v1.64: the DETAIL view replaced the v1.50/v1.63 in-card accordions. One listing's detail
    // shows at a time, swapped over the grid via a CardLayout; these track that state.
    private JPanel marketCenterCards;                                  // "grid" | "detail"
    private main.menu.components.ListingDetailPanel marketDetail;      // the open detail, or null
    private String detailListingId;                                    // its listing id, or null
    private final java.util.Map<String, String> marketCommentsCache = new java.util.HashMap<>();
    private final List<main.market.ScriptListing> marketAll = new ArrayList<>();
    private JTextField marketSearchField;
    private JLabel lblMarketSource;
    /** v1.79: hide-my-own, surfaced under the grid instead of buried in the filter menu. */
    private JCheckBox chkHideOwn;
    // v1.60: the TOP GRID - everyone's published scripts as real card components (no more JList
    // renderer + hit-testing maths; each star/heart/button is a live component of its own).
    private JPanel marketGridPanel;
    private JScrollPane marketGridScroll;
    private final java.util.Map<String, main.menu.components.MarketCard> marketCardById =
            new java.util.HashMap<>();
    private String marketSort = "Top rated";
    private static final String[] MARKET_SORTS = {"Top rated", "Most downloaded", "Newest",
            "A-Z", "Version (highest)", "Version (lowest)"};
    private boolean fltHideVip, fltHideFree, fltLovedOnly;
    /**
     * v1.70: hide your OWN listings in the market grid, on by default. Replaces the "My
     * uploads" dialog - browsing the market is for finding other people's work, and your own
     * scripts are already in your library and market-ready strip.
     */
    private boolean fltHideOwn = false;   // v1.79: opt-IN, see the checkbox under the grid
    /** Roadmap default: only the highest version of each name+author shows. Coexistence of two
     *  versions stays possible - untick the filter to see every version side by side. */
    private boolean fltHideOlder = true;
    /** v1.75 (item 14): the clickable tag pills under the market search field. */
    private main.menu.components.TagFilterBar tagBar;
    private final java.util.Set<String> fltTags =
            new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private JButton btnMarketSort, btnMarketFilter;
    private static final Color MARKET_ICON_GOLD = new Color(212, 175, 55);
    // v1.60: the BOTTOM STRIP - your own market-ready staging, one bounded row of cards.
    private JPanel readyCardsPanel;
    private JLabel readySortLabel, readyCountLabel;
    private JButton readyPrev, readyNext;
    private int readyStart = 0;
    private boolean readySortAZ = false;   // false = newest first (the default)
    private final List<main.market.ScriptListing> readyAll = new ArrayList<>();
    private static final int READY_VISIBLE = 3;
    // v1.60: listing icons - the picker's cap mirrors the server's, and the resize link is the
    // exact one the roadmap asks the upload dialog to carry.
    private static final String ILOVEIMG_RESIZE_URL =
            "https://www.iloveimg.com/resize-image#resize-options,pixels";
    private static final int LISTING_ICON_MAX_BYTES = 300 * 1024;
    private JComboBox<String> libraryFilterCombo;
    /** Patch B.15: explicit "hide VIP tasks" toggle (default on for non-VIP). */
    private JCheckBox libraryHideVipCheck;

    /** Adds to the master library and refreshes the view (Patch B.5). */
    public void libraryAdd(Task t) {
        if (t == null) return;
        libraryAll.add(t);
        refilterLibrary();
        requestAutosave();   // v1.62: autosave on change (no Save button)
    }

    /** Removes from the master library and refreshes the view. @return removed? */
    public boolean libraryRemove(Task t) {
        boolean ok = libraryAll.remove(t);
        if (ok) { refilterLibrary(); requestAutosave(); }   // v1.62: autosave on change
        return ok;
    }

    /** Replaces the whole master library (profile load / overwrite import). */
    public void librarySetAll(List<Task> tasks) {
        libraryAll.clear();
        if (tasks != null)
            for (Task t : tasks)
                if (t != null) libraryAll.add(t);
        refilterLibrary();
    }

    /** Replaces every master entry matching id (or name for legacy) with a copy of updated. */
    public int libraryPropagate(Task updated, String id, String name) {
        int touched = 0;
        for (int i = 0; i < libraryAll.size(); i++) {
            Task t = libraryAll.get(i);
            if (t == null) continue;
            boolean match = (id != null && id.equals(t.getId()))
                    || (id == null && name != null && name.equalsIgnoreCase(t.getName()));
            if (match) {
                Task copy = new Task(updated);
                copy.setRepeat(t.getRepeat());
                copy.setOrigin(t.getOrigin());   // editing doesn't change where it came from
                // v1.49: an edit produces a new version, and the lifecycle tags carry across so
                // you can still see this task was exported/published (just at an earlier version).
                copy.setVersion(t.getVersion());
                copy.bumpVersion();
                copy.setExported(t.isExported());
                copy.setMarketReady(t.isMarketReady());
                copy.setPublished(t.isPublished());
                copy.setDownloaded(t.isDownloaded());
                libraryAll.set(i, copy);
                touched++;
            }
        }
        if (touched > 0) refilterLibrary();
        if (touched > 0) requestAutosave();   // v1.62: an edit is a change - autosave it
        return touched;
    }

    final DefaultListModel<Action> modelTaskBuilder = new DefaultListModel<>();
    private final JList<Action> listTaskBuilder = new JList<>(modelTaskBuilder);

    private final DefaultListModel<String> nearbyEntitiesModel = new DefaultListModel<>();

    // --- Preset State ---
    private final int MAX_PRESETS = 16;
    private int currentPresetPage = 0;
    private int selectedPresetIndex = -1; // Tracks the currently active/selected preset index

    // ── Queue looping (Patch 3) ──────────────────────────────────────────────
    /** How many times the whole queue should run. 0 = infinite. */
    private volatile int queueLoopTarget = 1;

    // ── Patch B.2: auto-wait between tasks (queue level) ──
    private volatile boolean queueAutoWait = false;
    private volatile int queueAutoWaitMinMs = 400;
    private volatile int queueAutoWaitMaxMs = 1200;
    private JCheckBox chkQueueWait;

    // ── Patch B.3: Task Library modernization ──
    private JComboBox<String> librarySortCombo;
    private Map<String, Integer> libraryUseCounts = new HashMap<>();
    private JLabel inspName, inspMeta, inspStatus, inspTags;   // v1.62: inspTags = full tag list
    private JTextArea inspDesc, inspAttrs;

    // v1.65: market-ready is no longer a one-way button + modal. The inspector carries an inline
    // three-state selector, so the task's market state is VISIBLE at a glance and reversible in
    // place: you can unstage, or flip task <-> script, without hunting through the Market tab.
    private static final String MK_NOT    = "Not market ready";
    private static final String MK_TASK   = "Market ready (task)";
    private static final String MK_SCRIPT = "Market ready (script)";
    private JComboBox<String> inspMarketCombo;
    private JLabel inspMarketBlurb;
    /** Guards the selector's listener while populateInspector() sets it programmatically. */
    private boolean inspMarketSyncing = false;
    // Patch B.4: always-on watchers - checked between every action while the player is safe.
    private final List<main.watchers.Trigger> globalTriggers =
            Collections.synchronizedList(new ArrayList<>());

    /** Always-on watchers (the "default background triggers"); read by the engine each loop. */
    public List<main.watchers.Trigger> getGlobalTriggers() { return globalTriggers; }

    /**
     * v1.64: SCRIPT triggers - always-on checks that apply only while THE CURRENT QUEUE is running
     * (set in the Task List tab), drawn from the global set. Copies, not references, so a script's
     * checks are a snapshot that persists with its preset and travels when it's published. They
     * save with the queue draft and per-preset, and load back when a preset is loaded.
     */
    private final List<main.watchers.Trigger> scriptTriggers =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    public List<main.watchers.Trigger> getScriptTriggers() { return scriptTriggers; }

    /**
     * v1.64: the trigger set the runtime should evaluate - the always-on globals PLUS the running
     * script's own script triggers. DreamBotMan calls this only while executing the queue, so the
     * script triggers are correctly scoped: they never fire when no script is running.
     */
    /**
     * v1.64: choose which always-on global checks apply while THIS task list runs (script
     * triggers). Pick from the global set; the picks are stored as a snapshot on the current
     * queue (a COPY per check, so editing the global later doesn't silently change a saved
     * script). They save with the queue draft and per-preset, and travel when the script is
     * published. Global checks still run always; these run additionally, only while this list is
     * executing.
     */
    private void openScriptTriggersDialog(JComponent anchor) {
        java.util.List<main.watchers.Trigger> globals = new ArrayList<>();
        for (main.watchers.Trigger t : globalTriggers) if (t != null) globals.add(t);
        if (globals.isEmpty()) {
            showToast("You have no always-on checks yet \u2014 build some on the Checks tab first, "
                    + "then pick which apply to this script", anchor, false);
            return;
        }
        // a script trigger "matches" a global if it's the same check (by description snapshot)
        java.util.Set<String> active = new java.util.HashSet<>();
        for (main.watchers.Trigger st : scriptTriggers)
            if (st != null) active.add(st.describe());

        java.util.List<JCheckBox> boxes = new ArrayList<>();
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        for (main.watchers.Trigger t : globals) {
            JCheckBox cb = new JCheckBox(t.describe(), active.contains(t.describe()));
            cb.setOpaque(false);
            cb.setForeground(TEXT_MAIN);
            cb.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            cb.setAlignmentX(0f);
            cb.setBorder(new EmptyBorder(5, 2, 5, 2));   // v1.88: breathing room per row
            boxes.add(cb);
            list.add(cb);
        }
        // v1.88: room to breathe. The picker was 380px wide with rows jammed at 26px, so a
        // trigger with a real description wrapped into a cramped two-line smear. Wider, taller,
        // padded rows, and the explanation gets space above it.
        JScrollPane sp = Theme.thinScrollbars(new JScrollPane(list));
        sp.setPreferredSize(new Dimension(560, Math.max(220, Math.min(460, 32 * globals.size() + 24))));
        sp.setBorder(BorderFactory.createLineBorder(COLOR_BORDER_DIM));
        list.setBorder(new EmptyBorder(8, 10, 8, 10));

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(new EmptyBorder(4, 4, 4, 4));
        JLabel head = styledLabel("<html><div style='width:520px'>Checks that run <b>only while "
                + "this task list is running</b>:<br><span style='color:#999'>(Global checks on "
                + "the Checks tab always run; these run additionally, just for this "
                + "script.)</span></div></html>");
        head.setBorder(new EmptyBorder(2, 2, 6, 2));
        root.add(head, BorderLayout.NORTH);
        root.add(sp, BorderLayout.CENTER);

        int r = JOptionPane.showConfirmDialog(this, root, "Script triggers",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;

        // rebuild the script-trigger set from the ticked globals (fresh enabled copies)
        scriptTriggers.clear();
        int n = 0;
        for (int i = 0; i < boxes.size(); i++) {
            if (!boxes.get(i).isSelected()) continue;
            main.watchers.Trigger copy = new main.watchers.Trigger(globals.get(i));
            copy.setEnabled(true);   // chosen to apply -> active while this script runs
            scriptTriggers.add(copy);
            n++;
        }
        requestAutosave();
        showToast(n == 0 ? "No script triggers \u2014 this list runs with global checks only"
                : n + " script trigger" + (n == 1 ? "" : "s") + " will run while this list plays",
                anchor, true);
    }

    /** v1.64: the live script-trigger set as JSON, for saving into a preset/bundle. */
    private String currentScriptTriggersJson() {
        return main.watchers.TriggerCodec.toJson(new ArrayList<>(scriptTriggers));
    }

    /**
     * v1.64: let the author choose WHICH of their always-on global checks to fold into a publish
     * (the roadmap's "allow the user to select which of the global triggers are included if they
     * want to include any at all"). Returns the picked list (possibly empty = include none), or
     * null if they cancelled the publish entirely.
     */
    private java.util.List<main.watchers.Trigger> pickGlobalTriggersToBundle() {
        java.util.List<main.watchers.Trigger> live = new ArrayList<>();
        for (main.watchers.Trigger t : globalTriggers) if (t != null) live.add(t);
        java.util.List<JCheckBox> boxes = new ArrayList<>();
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        for (main.watchers.Trigger t : live) {
            JCheckBox cb = new JCheckBox(t.describe(), false);   // default: include none
            cb.setOpaque(false);
            cb.setForeground(TEXT_MAIN);
            boxes.add(cb);
            list.add(cb);
        }
        JScrollPane sp = Theme.thinScrollbars(new JScrollPane(list));
        sp.setPreferredSize(new Dimension(360, Math.min(300, 26 * live.size() + 16)));
        JPanel root = new JPanel(new BorderLayout(0, 6));
        root.add(styledLabel("Also include which of your always-on global checks? "
                + "(they run for whoever downloads it)"), BorderLayout.NORTH);
        root.add(sp, BorderLayout.CENTER);
        Object[] opts = {"Include selected", "Include none", "Cancel publish"};
        int r = JOptionPane.showOptionDialog(this, root, "Include global checks?",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, opts, opts[1]);
        if (r == 2 || r == JOptionPane.CLOSED_OPTION) return null;
        java.util.List<main.watchers.Trigger> picked = new ArrayList<>();
        if (r == 0)
            for (int i = 0; i < boxes.size(); i++)
                if (boxes.get(i).isSelected()) picked.add(live.get(i));
        return picked;
    }

    public List<main.watchers.Trigger> getRuntimeTriggers() {
        if (scriptTriggers.isEmpty()) return globalTriggers;
        List<main.watchers.Trigger> all = new ArrayList<>(globalTriggers);
        all.addAll(scriptTriggers);
        return all;
    }

    private final DefaultListModel<Action> inspActionsModel = new DefaultListModel<>();
    private JList<Action> inspActionsList;

    // ── Patch B.3: live Script card on the Status tab + status.json snapshot ──
    private final JLabel lblScriptTask = new JLabel("—");
    private final JLabel lblScriptActivity = new JLabel("—");
    private final JLabel lblScriptQueue = new JLabel("—");
    private final JLabel lblScriptLoop = new JLabel("—");
    private final JLabel lblScriptUptime = new JLabel("—");
    private final JLabel lblScriptPaused = new JLabel("—");
    private long lastStatusSnapshotAt = 0;
    /** Single background writer for status.json - never blocks the EDT, never stacks threads. */
    private final java.util.concurrent.ExecutorService statusWriter =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "DreamMan-Status");
                t.setDaemon(true);
                return t;
            });
    private JTextField queueWaitMinInput, queueWaitMaxInput;

    /** @return whether the engine should pause between completed tasks (Patch B.2). */
    public boolean isQueueAutoWait() { return queueAutoWait; }
    public int getQueueAutoWaitMinMs() { return Math.max(0, queueAutoWaitMinMs); }
    public int getQueueAutoWaitMaxMs() { return Math.max(getQueueAutoWaitMinMs(), queueAutoWaitMaxMs); }
    /** Which pass we're on (1-based), shown live as "Loop x/y". */
    private volatile int queueLoopCurrent = 1;
    // Live loop-control widgets (created in the Task List tab).
    private JSpinner loopQueueSpinner;
    private JCheckBox loopInfiniteCheck;
    private JSpinner taskRepeatSpinner;
    private JLabel lblLoopIndicator;
    private JLabel lblTaskCount;
    private int hoveredTaskIndex = -1;
    /** Prevents spinner/checkbox listeners from firing while we programmatically sync them. */
    private boolean suppressLoopEvents = false;
    private final DefaultListModel<Preset> modelPresets = new DefaultListModel<>();
    private final JButton[] presetButtons = new JButton[4];
    private JButton btnPageUp, btnPageDown;

    ///
    /// --- UI Components ---
    ///
    private final JTabbedPane mainTabs = new JTabbedPane();
    private final JPanel trackerList;
    // v1.86: the side panel is the live PLAYER panel - a login card and a live card
    private CardLayout sideCards;
    private JPanel sideCardHost;
    private final JLabel sidePlayerName = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel sidePlayerStats = new JLabel(" ", SwingConstants.CENTER);
    /** One sleek line replacing the old green "xp gained / levels gained" pair (v1.86). */
    private final JLabel sessionSummaryLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel totalLevelLabelP2P = new JLabel();
    private final JLabel totalLevelLabelF2P = new JLabel();
    // Patch B.1: the UI delegate is bound per-instance in updateUI(), so the bar can never be
    // blanked by a failed UIManager class-name lookup or a Substance updateUI() sweep again.
    private final JProgressBar statusProgress = new JProgressBar(0, 100) {
        @Override public void updateUI() { setUI(new Theme.RoundProgressBarUI()); }
    };
    private final JLabel lblStatus = new JLabel();
    private final JSpinner projectionSpinner;
    private final long startTime;
    private JButton btnTaskBuilderAddToLibrary;
    private JButton btnTaskBuilderScanNearby;

    private JList<String> nearbyList;
    private JTextArea libraryEditorArea, consoleArea;
    private JTextField taskNameInput, taskDescriptionInput, taskStatusInput;
    JActionSelector actionSelector;
    private static JButton btnPlayPause, btnMouseToggle, btnKeyboardToggle;

    ///
    /// --- Client Checkboxes ---
    ///
    private JCheckBox chkAutoSave;
    private JCheckBox settingClientChkStartScriptOnLoad;
    private JCheckBox settingClientChkExitOnStopWarning;
    private JCheckBox chkDisableRendering;
    private JCheckBox chkHideRoofs;
    private JCheckBox chkDataOrbs;
    private JCheckBox chkAmmoPickingBehaviour;
    private JCheckBox chkTransparentSidePanel;
    private JCheckBox chkGameAudio;
    private JCheckBox chkTransparentChatbox;
    private JCheckBox chkClickThroughChatbox;
    private JCheckBox chkShiftClickDrop;
    private JCheckBox chkEscClosesInterface;
    private JCheckBox chkLevelUpInterface;
    private JCheckBox chkLootNotifications;


    private JCheckBox chkVisualEffects;
    // --- Theme ---
    private final Map<JComponent, Color> originalColors = new WeakHashMap<>(); // Kept as requested, but no longer used by flashControl to prevent color bugs
    private final Color BG_BASE = Theme.BG_APP;
    private final Color TEXT_DIM = Theme.TEXT_DIM;
    private final Color TAB_SELECTED = new Color(60, 0, 0);

    // --- Labels (Restored) ---
    private final JLabel lblUsername = new JLabel("...");
    private final JLabel lblNickname = new JLabel("...");
    private final JLabel lblAcctId = new JLabel("...");
    private final JLabel lblAcctStatus = new JLabel("...");
    private final JLabel lblCharName = new JLabel("...");
    private final JLabel lblWorld = new JLabel("-");
    private final JLabel lblCoords = new JLabel("-");
    private final JLabel lblGameState = new JLabel("-");
    private final JLabel lblMemberIcon = new JLabel();
    private final JLabel lblMemberText = new JLabel("-");

    private int TIME_FLASH = 200;

    private static final Skill[] OSRS_ORDER = { Skill.ATTACK, Skill.HITPOINTS, Skill.MINING, Skill.STRENGTH, Skill.AGILITY, Skill.SMITHING, Skill.DEFENCE, Skill.HERBLORE, Skill.FISHING, Skill.RANGED, Skill.THIEVING, Skill.COOKING, Skill.PRAYER, Skill.CRAFTING, Skill.FIREMAKING, Skill.MAGIC, Skill.FLETCHING, Skill.WOODCUTTING, Skill.RUNECRAFTING, Skill.SLAYER, Skill.FARMING, Skill.CONSTRUCTION, Skill.HUNTER, Skill.SAILING };
    private static final Set<Skill> F2P_SKILLS = new HashSet<>(Arrays.asList(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.PRAYER, Skill.MAGIC, Skill.HITPOINTS, Skill.CRAFTING, Skill.MINING, Skill.SMITHING, Skill.FISHING, Skill.COOKING, Skill.FIREMAKING, Skill.WOODCUTTING, Skill.RUNECRAFTING));

    public DreamBotMenu(AbstractScript script) {
        Logger.log(Logger.LogType.DEBUG, "Instantiating DreamBotMenu...");
        ///  Provide a reference to the original script manager objects, this clouds the user from the original script
        ///     object as well, to avoid confusion, anything this class needs will be pulled in the constructor.
        // Install the app-wide flat-dark design system before ANY component is created,
        // so every tab/button/scrollbar/input picks it up automatically (Patch A).
        Theme.install();

        this.script = script;   // Patch B.5: kept for engine coordination (pause-drag remap)
        this.scriptManager = script.getScriptManager();
        this.isMenuPaused(scriptManager.isPaused());
        this.startTime = System.currentTimeMillis();
        // Patch B.10: a missing decorative window icon must NEVER stop the bot from starting.
        // This used to requireNonNull and take the whole menu down with it - which matters far
        // more now that exported jars carry their own copy of the resources.
        ImageIcon windowIcon = loadStatusIcon("Hardcore_ironman");
        if (windowIcon != null) this.setIconImage(windowIcon.getImage());
        setTitle("DreamMan · OSRS Task Manager");

        setSize(1400, 950);
        setMinimumSize(new Dimension(940, 620)); // stops the runaway width-growth on shrink
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(Theme.BG_APP);

        Logger.log(Logger.LogType.DEBUG, "Setup main GUI parameters...");

        actionSelector = new JActionSelector();
        // Patch B.2: TaskRef actions resolve library tasks live, by id, through the menu
        main.actions.TaskRef.setResolver(this::findLibraryTask);
        // v1.62: TaskRef's parameter is a pick-from-library dropdown - feed it current task names
        main.actions.TaskRef.setNamesSupplier(this::libraryTaskNames);
        taskBuilder = new TaskBuilder(this);
        libraryPanel = new LibraryPanel();

        // v1.86: the tracked-skill DETAIL panels leave the side panel (which becomes the live
        // PLAYER panel below) for the Skill Tracker tab's east column - so the list must exist
        // BEFORE the tabs build, because createSkillTrackerTab() now mounts it.
        trackerList = new JPanel();
        trackerList.setLayout(new BoxLayout(trackerList, BoxLayout.Y_AXIS));
        trackerList.setBackground(PANEL_SURFACE);

        mainTabs.setBackground(Theme.SURFACE_1);
        mainTabs.setForeground(Theme.TEXT_DIM);
        mainTabs.setFont(Theme.font(13));
        mainTabs.addTab("Task List", loadTabIcon("task_list_tab"), createTaskListTab());
        mainTabs.addTab("Task Library", loadTabIcon("task_library_tab"), createTaskLibraryTab());
        mainTabs.addTab("Task Builder", loadTabIcon("task_builder_tab"), taskBuilder);
        mainTabs.addTab("Triggers", loadTabIcon("triggers_tab"), createWatchersTab());
        mainTabs.addTab("Skill Tracker", loadTabIcon("skills_tracker_tab"), createSkillTrackerTab());
        mainTabs.addTab("Loot Tracker", loadTabIcon("loot_tracker_tab"), createLootTrackerTab());
        // v1.88: Logs sits with the other things you WATCH (skills, loot) instead of being
        // stranded past the account/settings block at the end of the strip.
        mainTabs.addTab("Logs", loadTabIcon("logs_tab"), createLogsTab());                  // v1.31
        // v1.86: the Equipment tab is gone - the doll, inventory and presets live in the
        // always-reachable side panel now (buildSideLiveCard), per the sketch.
        mainTabs.addTab("Market", loadTabIcon("market_tab"), createMarketTab());
        // v1.87: the Status tab is dismantled. Everything ABOUT THE PLAYER (character,
        // membership, account type, bank PIN, Jagex-account switching) lives in the side
        // Player panel now; the DreamMan-server half became the Account tab below; Privacy is
        // its own tab; and the read-only Script/World/Session telemetry cards are gone - the
        // bottom bar, the overlay and status.json already show all of it, live.
        mainTabs.addTab("Account", loadTabIcon("status_tab"), createAccountTab());
        mainTabs.addTab("Settings", loadTabIcon("settings_tab"), createSettingsTab());
        // v1.90: a drawn shield-and-keyhole, matching the strip's flat style instead of
        // falling back to whatever loadTabIcon() could find for "privacy_tab".
        mainTabs.addTab("Privacy",
                new javax.swing.ImageIcon(iconToImage(
                        main.menu.components.UIIcons.shield(16, Theme.ACCENT), 16)),
                createPrivacyTabStandalone());
        // v1.77 BUGFIX: apply the RESTORED session to the UI at startup.
        //
        // The session was being read back from session.json correctly all along - nothing ever
        // applied it. onAccountChanged() was reachable from exactly two places: a consent change,
        // and the Log out button's own listener. So a launch with a perfectly valid token left
        // every account-dependent surface in its logged-out default:
        //   · the account switcher never named you        -> "it never remembers who's logged in"
        //   · applyTierLimits/refreshTierStatusLabel never ran -> VIP + rank (owner/admin/vip/free)
        //                                                        showed as guest/free
        //   · refreshAccountLogoutVisibility never ran     -> Log out sat visible while logged
        //                                                     out, and only corrected itself once
        //                                                     you pressed it (its listener being
        //                                                     the sole caller)
        // Guarded, because a failure here must never stop the menu from opening.
        try {
            onAccountChanged();
        } catch (Throwable t) {
            Logger.log(Logger.LogType.WARN, "[Account] startup sync failed: " + t);
        }
        syncDevConsoleTab();   // v1.32b: owner-only Dev Console tab, if already signed in as owner
        checkSubmissionOutcomes();   // v1.67: report any moderation outcomes from last session
        // Patch B.3 / v1.32: the Developers Console is no longer a top-level tab - it made the
        // tab strip overflow for developers. It now lives as a developer-only button inside the
        // Settings tab (see createSettingsTab), opened in its own window. Same isDeveloper() gate.

        Logger.log(Logger.LogType.DEBUG, "Setup main tabs...");

        projectionSpinner = new JSpinner(new SpinnerNumberModel(24, 1, 999, 1));
        styleSpinner(projectionSpinner);

        ///  Define side panel (v1.86: the Live Tracker becomes the live PLAYER panel - the
        ///  player header, minimap, equipment doll, inventory and session totals, gated on
        ///  being logged in; the tracked-skill details moved to the Skill Tracker tab)
        JPanel sidePanelContent = new JPanel(new BorderLayout());
        sidePanelContent.setPreferredSize(new Dimension(360, 0));
        sidePanelContent.setBackground(PANEL_SURFACE);
        sidePanelContent.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, COLOR_BORDER_DIM));

        ///  Add toggle button to show/hide side panel
        JButton btnToggleSidePanel = new Theme.ThemedButton();

        sideCards = new CardLayout();
        sideCardHost = new JPanel(sideCards);
        sideCardHost.setOpaque(false);
        sideCardHost.add(buildSideLoginCard(), "login");
        sideCardHost.add(buildSideLiveCard(), "live");

        sidePanelContent.add(sideCardHost, BorderLayout.CENTER);
        sidePanelContent.setVisible(false);
        // cap the panel width so it can never push the window wider
        sidePanelContent.setMaximumSize(new Dimension(360, Integer.MAX_VALUE));

        this.sidePanel = new JPanel(new BorderLayout());
        this.sidePanel.setOpaque(false);
        this.sidePanel.add(sidePanelContent, BorderLayout.CENTER);
        this.sidePanel.add(btnToggleSidePanel, BorderLayout.EAST);

        btnToggleSidePanel.setText(sidePanelContent.isVisible() ? ">" : "<");
        btnToggleSidePanel.setPreferredSize(new Dimension(22, 0));
        btnToggleSidePanel.setBackground(PANEL_SURFACE);
        btnToggleSidePanel.setForeground(COLOR_BLOOD);
        btnToggleSidePanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 1, COLOR_BORDER_DIM));

        btnToggleSidePanel.addActionListener(e -> {
            sidePanelContent.setVisible(!sidePanelContent.isVisible());
            btnToggleSidePanel.setText(sidePanelContent.isVisible() ? ">" : "<");
            revalidate();
        });

        add(mainTabs, BorderLayout.CENTER);
        add(this.sidePanel, BorderLayout.EAST);
        add(createProgressPanel(), BorderLayout.SOUTH);

        updateAll();

        SwingUtilities.invokeLater(() -> {
            ///  Start a timer to update the UI every 1 second
            uiTimer = new Timer(1000, e ->
                    // refresh GUI every n seconds
                    updateUI()
            );
            uiTimer.start();

            ///  Start another timer to scan for nearby targets every n seconds while task builder is open.
            scanTimer = new Timer(4000, e -> {
                // scan for nearby targets every n seconds while the task builder tab is open
                if (mainTabs.getSelectedIndex() == 2)
                    taskBuilder.refresh();
            });
            scanTimer.start();

            ///  Start a third timer to auto-save everything periodically (v1.62: always on - the
            ///  manual Save buttons are gone, so autosave is no longer an opt-in toggle). This is
            ///  the BACKSTOP; most saves happen promptly via requestAutosave() on change.
            saveTimer = new Timer(60000, e -> saveAll(false));
            saveTimer.start();

            // v1.62: queue add/remove/reorder autosaves (the Task List Save button is gone). The
            // per-Task field edits (repeat, loops, tags) already call saveAll(false)/requestAutosave
            // at their own sites; this covers the structural changes the Save button used to catch.
            modelTaskList.addListDataListener(new javax.swing.event.ListDataListener() {
                public void intervalAdded(javax.swing.event.ListDataEvent e)   { requestAutosave(); }
                public void intervalRemoved(javax.swing.event.ListDataEvent e) { requestAutosave(); }
                public void contentsChanged(javax.swing.event.ListDataEvent e) { requestAutosave(); }
            });

            autosaveReady = true;   // v1.62: from here on, model changes trigger a debounced save

        });

        // NOTE: setVisible() is intentionally NOT called here - DreamBotMan.onStart() shows the
        // frame once construction completes (previously the frame was shown twice).
        Logger.log(Logger.LogType.DEBUG, "DreamBotMenu instantiation complete!");
    }

    /**
     * Ensures the proper disposal of the {@link DreamBotMenu} on exit.
     */
    public void onExit() {
        // disable gui refresh timer if its running
        if (uiTimer != null)
            uiTimer.stop();

        // disable scan nearby timer if its running
        if (scanTimer != null)
            scanTimer.stop();

        // disable auto-save timer if its running
        if (saveTimer != null)
            saveTimer.stop();

        // Persist the profile one last time so nothing built this session is lost on close.
        // Capture on the EDT (models are Swing state) then write synchronously so the save
        // finishes before the client tears the script down.
        try {
            final String key = profileKey();
            final ProfileData[] box = new ProfileData[1];
            if (SwingUtilities.isEventDispatchThread())
                box[0] = captureProfile();
            else
                SwingUtilities.invokeAndWait(() -> box[0] = captureProfile());
            // same guard as the auto-save: never let an empty exit snapshot flatten real data
            boolean skip = isEmptyProfile(box[0]);
            if (skip) {
                ProfileData onDisk = LocalStore.load(key);
                skip = onDisk != null && !isEmptyProfile(onDisk);
            }
            if (!skip)
                LocalStore.save(key, box[0]);
            else
                Logger.log(Logger.LogType.INFO, "[ExitSave] Workspace empty - keeping saved profile.");
        } catch (Exception e) {
            Logger.log(Logger.LogType.ERROR, "Exit save failed: " + e.getMessage());
        }

        // safely dispose of this JFrame object
        this.dispose();
    }

    private static class ToastRequest {
        final String message;
        final JComponent anchor;
        final boolean success;

        ToastRequest(String message, JComponent anchor, boolean success) {
            this.message = message;
            this.anchor = anchor;
            this.success = success;
        }
    }

    /**
     * The loop / repeat control strip shown under the queue: set a per-task repeat count, set the
     * whole-queue loop count (or infinite), skip the current task, or run from the selected task.
     */
    private JPanel createLoopBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(6, 2, 6, 2));

        // ---- left: repeat + loop spinners ----
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);

        JLabel lblRepeat = new JLabel("Repeat task ×");
        lblRepeat.setForeground(TEXT_DIM);
        taskRepeatSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
        taskRepeatSpinner.setPreferredSize(new Dimension(64, 26));
        taskRepeatSpinner.setToolTipText("How many times the selected task runs before the queue moves on");
        taskRepeatSpinner.setEnabled(false);
        taskRepeatSpinner.addChangeListener(e -> {
            if (suppressLoopEvents) return;
            Task sel = listTaskList.getSelectedValue();
            if (sel != null) {
                sel.setRepeat((int) taskRepeatSpinner.getValue());
                listTaskList.repaint();
                updateQueueProgress();
            }
        });

        JLabel lblLoop = new JLabel("     Loop queue ×");
        lblLoop.setForeground(TEXT_DIM);
        loopQueueSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 9999, 1));
        loopQueueSpinner.setPreferredSize(new Dimension(72, 26));
        loopQueueSpinner.setToolTipText("How many times to run the whole queue");
        loopQueueSpinner.addChangeListener(e -> {
            if (suppressLoopEvents) return;
            if (loopInfiniteCheck == null || !loopInfiniteCheck.isSelected()) {
                queueLoopTarget = clampLoops((int) loopQueueSpinner.getValue());
                updateQueueProgress();
            }
        });

        loopInfiniteCheck = new JCheckBox("∞");
        loopInfiniteCheck.setOpaque(false);
        loopInfiniteCheck.setForeground(TEXT_MAIN);
        loopInfiniteCheck.setToolTipText("Loop the queue forever");
        loopInfiniteCheck.addActionListener(e -> {
            if (suppressLoopEvents) return;
            boolean inf = loopInfiniteCheck.isSelected();
            // Patch B.14: infinite loops are a VIP perk. A free/guest account ticking ∞ gets a
            // friendly nudge and is clamped to its cap instead. (The server also refuses to grant
            // more, so this is just the UI being upfront rather than letting them hit a wall.)
            if (inf && !main.market.Tier.allowsInfinite()) {
                loopInfiniteCheck.setSelected(false);
                inf = false;
                showToast("Infinite loops are a VIP feature - capped at "
                        + main.market.Tier.maxLoops() + " loops", loopInfiniteCheck, false);
            }
            loopQueueSpinner.setEnabled(!inf);
            queueLoopTarget = inf ? 0 : clampLoops((int) loopQueueSpinner.getValue());
            updateQueueProgress();
        });

        // Patch B.2: auto-wait between tasks - the small humanised pause you'd add manually
        // after every task anyway. Range in ms; persisted with the profile. v1.62: confirmed
        // live (it feeds a real gap via onTaskRunComplete), so kept - just labelled clearly as
        // an automatic background pause rather than the ambiguous "Auto-wait".
        chkQueueWait = new JCheckBox("Pause between tasks");
        chkQueueWait.setOpaque(false);
        chkQueueWait.setForeground(TEXT_MAIN);
        chkQueueWait.setToolTipText("<html>When on, a random pause (the ms range at right) is "
                + "added <b>automatically in the background after every completed task</b>,<br>"
                + "so the queue looks less robotic. Applies to the whole queue while it runs.</html>");
        chkQueueWait.addActionListener(e -> {
            queueAutoWait = chkQueueWait.isSelected();
            queueWaitMinInput.setEnabled(queueAutoWait);
            queueWaitMaxInput.setEnabled(queueAutoWait);
        });
        queueWaitMinInput = new JTextField("400", 4);
        queueWaitMaxInput = new JTextField("1200", 4);
        queueWaitMinInput.setToolTipText("Shortest auto-pause after a task (ms)");
        queueWaitMaxInput.setToolTipText("Longest auto-pause after a task (ms)");
        KeyAdapter qwSync = new KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) {
                try { queueAutoWaitMinMs = Integer.parseInt(queueWaitMinInput.getText().trim()); } catch (Exception ignored) {}
                try { queueAutoWaitMaxMs = Integer.parseInt(queueWaitMaxInput.getText().trim()); } catch (Exception ignored) {}
            }
        };
        queueWaitMinInput.addKeyListener(qwSync);
        queueWaitMaxInput.addKeyListener(qwSync);
        queueWaitMinInput.setEnabled(false);
        queueWaitMaxInput.setEnabled(false);

        left.add(lblRepeat);
        left.add(taskRepeatSpinner);
        left.add(lblLoop);
        left.add(loopQueueSpinner);
        left.add(loopInfiniteCheck);
        JLabel lblWaitTo = new JLabel("to");
        lblWaitTo.setForeground(TEXT_DIM);
        JLabel lblWaitMs = new JLabel("ms");
        lblWaitMs.setForeground(TEXT_DIM);
        // v1.90: "Pause between tasks" moved UP to the task-button row. It describes what
        // happens between the TASKS in the list above, so it belongs with the controls that
        // build that list - and moving it frees the width this row needed for the two
        // script-level buttons coming down from there.
        pauseBetweenGroup.setOpaque(false);
        pauseBetweenGroup.removeAll();
        pauseBetweenGroup.add(chkQueueWait);
        pauseBetweenGroup.add(queueWaitMinInput);
        pauseBetweenGroup.add(lblWaitTo);
        pauseBetweenGroup.add(queueWaitMaxInput);
        pauseBetweenGroup.add(lblWaitMs);
        // v1.64: script triggers - always-on checks that run only while THIS list/script runs.
        btnScriptTriggers = createButton("Script triggers\u2026", new Color(55, 45, 65), null);
        btnScriptTriggers.setToolTipText("Pick always-on checks that apply only while this task "
                + "list is running (they save with its preset and travel when you publish it)");
        btnScriptTriggers.addActionListener(e -> openScriptTriggersDialog(btnScriptTriggers));
        left.add(Box.createHorizontalStrut(6));
        left.add(btnScriptTriggers);
        // v1.88: the Timer moved here from the task-button row. It's a SCRIPT-level control
        // (when this entry fires on its own schedule), so it belongs beside Script triggers,
        // not among the buttons that build the queue.
        btnTaskListTimer = createButton("Timer\u2026", new Color(25, 60, 75), null);
        btnTaskListTimer.setToolTipText("Take the selected task out of the normal rotation and "
                + "fire it on a rough interval instead");
        btnTaskListTimer.addActionListener(e -> {
            Task sel = listTaskList.getSelectedValue();
            if (sel == null) { showToast("Select a task first!", btnTaskListTimer, false); return; }
            openTimerDialog(sel, btnTaskListTimer);
        });
        left.add(btnTaskListTimer);
        // v1.90: the two whole-script controls, arriving from the task row above.
        if (btnPublishPresetRef != null) {
            left.add(Box.createHorizontalStrut(10));
            left.add(btnPublishPresetRef);
        }
        if (btnResetPresetRef != null) left.add(btnResetPresetRef);

        // ---- right: skip / run-from-here + live indicator ----
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        // v1.88: these three are ICONS now - the row was carrying three word-buttons for
        // controls you press constantly, and the space is better spent on the queue itself.
        JButton btnRunStart = iconButton(
                main.menu.components.UIIcons.runFromStart(18, Theme.ACCENT),
                "Run from the top - restart the queue at task 1", null);
        btnRunStart.addActionListener(e -> {
            if (modelTaskList.isEmpty()) {
                showToast("The queue is empty", btnRunStart, false);
                return;
            }
            listTaskList.setSelectedIndex(0);
            setCurrentExecutionIndex(0);
            resetLoopProgress();
            listTaskList.repaint();
            isMenuPaused(false);
            showToast("Running from the top", btnRunStart, true);
        });

        JButton btnRunHere = iconButton(
                main.menu.components.UIIcons.runFromHere(18, Theme.ACCENT),
                "Run from here - start the queue at the selected task", null);
        btnRunHere.addActionListener(e -> {
            int idx = listTaskList.getSelectedIndex();
            if (idx < 0) {
                showToast("Select a task first", btnRunHere, false);
                return;
            }
            setCurrentExecutionIndex(idx);
            resetLoopProgress();
            listTaskList.repaint();
            isMenuPaused(false);   // v1.31: "run from here" actually RUNS - no second click
            showToast("Running from task " + (idx + 1), btnRunHere, true);
        });

        JButton btnSkip = iconButton(
                main.menu.components.UIIcons.skip(18, Theme.ACCENT),
                "Skip the current task and move to the next", null);
        btnSkip.addActionListener(e -> {
            if (modelTaskList.isEmpty()) return;
            advanceQueue();
            showToast("Skipped", btnSkip, true);
        });

        lblLoopIndicator = new JLabel("—");
        lblLoopIndicator.setForeground(COLOR_EXECUTING);
        lblLoopIndicator.setFont(new Font("Consolas", Font.BOLD, 13));
        lblLoopIndicator.setBorder(new EmptyBorder(0, 10, 0, 4));

        right.add(btnRunStart);
        right.add(btnRunHere);
        right.add(btnSkip);
        right.add(lblLoopIndicator);

        // v1.89b: WRAP instead of overlap. BorderLayout hands WEST and EAST their preferred
        // widths and simply lets them collide once the window is narrower than the sum - which
        // is the overlap in the report, and my own v1.88 regression: moving Timer next to
        // Script triggers and adding a third icon to the right group pushed this row past the
        // width a docked client gives it. WrapLayout (the same one the tag pills use) drops the
        // right-hand group onto a second line instead, so the controls stay readable at any
        // width and nothing ever sits on top of anything else.
        JPanel wrapRow = new JPanel(new main.menu.components.WrapLayout(FlowLayout.LEFT, 10, 4));
        wrapRow.setOpaque(false);
        wrapRow.add(left);
        wrapRow.add(right);
        bar.add(wrapRow, BorderLayout.CENTER);

        // initialise widget state from current fields
        SwingUtilities.invokeLater(() -> { syncLoopControls(); syncTaskRepeatSpinner(); updateQueueProgress(); });
        return bar;
    }

    /** Reflects the selected task's repeat count in the spinner (and disables it if no selection). */
    private void syncTaskRepeatSpinner() {
        if (taskRepeatSpinner == null) return;
        Task sel = listTaskList.getSelectedValue();
        suppressLoopEvents = true;
        try {
            if (sel != null) {
                taskRepeatSpinner.setEnabled(true);
                taskRepeatSpinner.setValue(sel.getRepeat());
            } else {
                taskRepeatSpinner.setEnabled(false);
                taskRepeatSpinner.setValue(1);
            }
        } finally {
            suppressLoopEvents = false;
        }
    }

    private JPanel createProgressPanel() {
        JPanel persistentStatus = new JPanel(new GridBagLayout());
        persistentStatus.setBackground(Theme.SURFACE_1);

        persistentStatus.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                new EmptyBorder(10, 16, 10, 16)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // --- Row 0: The Progress Bar (Full Width) ---
        statusProgress.setPreferredSize(new Dimension(0, 16));
        statusProgress.setForeground(Theme.ACCENT);
        statusProgress.setBackground(Theme.SURFACE_2);
        statusProgress.setBorder(null);
        statusProgress.setPreferredSize(new Dimension(0, 14));
        statusProgress.setFont(Theme.font(11));
        statusProgress.setStringPainted(true);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2; // Spans across both the label and buttons columns
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 8, 0); // Gap below the bar
        persistentStatus.add(statusProgress, gbc);

        // --- Row 1, Left: The Status Text ---
        lblStatus.setForeground(TEXT_DIM);
        lblStatus.setFont(new Font("Consolas", Font.PLAIN, 12));
        setStatus("Waiting instructions...");

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1; // Occupies only the left column
        gbc.weightx = 1.0; // Pushes the buttons to the right
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 2, 0, 0);
        persistentStatus.add(lblStatus, gbc);

        ///  Create play/pause, stop and enable/disable mouse buttons
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        controls.setOpaque(false);
        btnPlayPause = createIconButton("▶", "Play the script", e -> toggleScriptState());
        btnPlayPause.putClientProperty("accent", Boolean.TRUE); // crimson primary
        JButton btnStop = createIconButton("■", "Stop the script", e -> stop());
        btnMouseToggle = createIconButton("🖱", "Toggle mouse input", e -> toggleMouseInput());
        btnKeyboardToggle = createIconButton("⌨", "Toggle keyboard input", e -> toggleKeyboardInput());

        // v1.86: Login/Logout left this bar for the side panel - the login card IS the Login
        // button (logged out), and Log out sits under the live card's totals (logged in).

        controls.add(btnPlayPause);
        controls.add(btnStop);
        controls.add(btnMouseToggle);
        controls.add(btnKeyboardToggle);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 0;    // Buttons only take as much space as they need
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(0, 0, 0, 0);
        persistentStatus.add(controls, gbc);

        return persistentStatus;
    }

    public DefaultListModel<Task> getModelTaskList() {
        return modelTaskList;
    }

    // ── In-game overlay support (Patch A2) ──────────────────────────────────
    /** Custom stat rows a script/task can push to the on-screen overlay (label -> value). */
    private final Map<String, String> overlayStats =
            Collections.synchronizedMap(new LinkedHashMap<>());

    /** Add/replace a custom row shown on the in-game overlay (e.g. "Logs", "125"). */
    public void putOverlayStat(String label, String value) { overlayStats.put(label, value); }
    /** Remove one custom overlay row. */
    public void removeOverlayStat(String label) { overlayStats.remove(label); }
    /** Clear all custom overlay rows. */
    public void clearOverlayStats() { overlayStats.clear(); }
    /** Live view of the custom overlay rows (synchronized). */
    public Map<String, String> getOverlayStats() { return overlayStats; }

    public long getUptimeMillis() { return System.currentTimeMillis() - startTime; }
    public int getQueueSize() { return modelTaskList.size(); }
    public int getQueueLoopCurrentValue() { return queueLoopCurrent; }

    /** Status text without the "Status: " prefix. */
    public String getStatusText() {
        String t = lblStatus.getText();
        return (t != null && t.startsWith("Status: ")) ? t.substring(8) : t;
    }

    /** Name of the task currently executing, or null. */
    public String getCurrentTaskName() {
        int i = getCurrentExecutionIndex();
        if (i >= 0 && i < modelTaskList.size()) {
            Task t = modelTaskList.get(i);
            return t != null ? t.getName() : null;
        }
        return null;
    }

    public int getCurrentExecutionIndex() {
        return currentExecutionIndex;
    }

    public void setCurrentExecutionIndex(int i) {
        if (this.currentExecutionIndex == i)
            return;

        this.currentExecutionIndex = i;
        // repaint must happen on the EDT (this is called from the script thread)
        if (listTaskList != null)
            SwingUtilities.invokeLater(listTaskList::repaint);
    }

    public void setStatus(String text) {
        SwingUtilities.invokeLater(() -> {
            final String display = "Status: " + text;
            lblStatus.setText(display);

            // start a timer after the default status reset delay
            Timer timer = new Timer(DEFAULT_STATUS_RESET_DELAY, e -> {
                // if by the time this timer is triggered, the text has not changed
                // (must compare against the FULL displayed string, prefix included)
                if (lblStatus.getText().equals(display))
                    // revert the text back to the default status string
                    lblStatus.setText("Status: " + DEFAULT_STATUS_STRING);
            });

            // disable repeats to prevent multiple calls of the same action, i think??
            timer.setRepeats(false);
            timer.start();
        });
    }

    /** Back-compat shim: the engine now calls {@link #advanceQueue()}. */
    public void incrementExecutionIndex() {
        advanceQueue();
    }

    /**
     * Advances the queue after a task has finished all its repeats. If more tasks remain in the
     * current pass we move to the next one; if we've reached the end, we either start another
     * whole-queue loop (finite target not yet reached, or infinite) or stop and reset.
     * <p>
     * Called from the script thread, so all Swing touches are marshalled to the EDT.
     */
    public void advanceQueue() {
        int size = modelTaskList.size();

        if (currentExecutionIndex < size - 1) {
            // more tasks left in this pass
            currentExecutionIndex++;
            // Patch B.8: timed tasks sit OUT of the normal rotation - step over them
            currentExecutionIndex = skipTimedForward(currentExecutionIndex);
        } else {
            currentExecutionIndex = size;   // force the pass-end branch below
        }

        if (currentExecutionIndex >= size) {
            // finished a full pass of the queue
            boolean infinite = (queueLoopTarget <= 0);
            if (infinite || queueLoopCurrent < queueLoopTarget) {
                queueLoopCurrent++;
                currentExecutionIndex = skipTimedForward(0);
                Logger.log(Logger.LogType.DEBUG, "Queue loop -> pass " + queueLoopCurrent
                        + (infinite ? " (infinite)" : "/" + queueLoopTarget));
                loopJustCompleted = true;          // Patch B.8: a safe point for AFTER_LOOP timers
                if (currentExecutionIndex < 0) {   // queue is ALL timed tasks - nothing to rotate
                    currentExecutionIndex = -1;
                }
            } else {
                currentExecutionIndex = -1;
                queueLoopCurrent = 1;
                allLoopsJustCompleted = true;      // Patch B.8: safe point for AFTER_ALL_LOOPS

                // Patch B.8: if an AFTER_ALL_LOOPS timed task is due, let the engine run it
                // BEFORE we pause - otherwise the queue would stop and the timer never fire.
                if (!hasDueAfterAllLoopsTask())
                    pause("Queue complete!");
                else
                    setStatus("Queue complete - running timed task...");
            }
        }

        SwingUtilities.invokeLater(() -> {
            listTaskList.repaint();
            updateQueueProgress();
        });
    }

    // ── Patch B.8: timed-task scheduling state (read/cleared by the engine) ──
    private volatile boolean loopJustCompleted = false;
    private volatile boolean allLoopsJustCompleted = false;

    /** True (and CLEARS) if a queue loop just finished - an AFTER_LOOP safe point. */
    public boolean consumeLoopCompleted() {
        boolean v = loopJustCompleted; loopJustCompleted = false; return v;
    }
    /** True (and CLEARS) if the final loop just finished - an AFTER_ALL_LOOPS safe point. */
    public boolean consumeAllLoopsCompleted() {
        boolean v = allLoopsJustCompleted; allLoopsJustCompleted = false; return v;
    }

    /** @return the next non-timed index at/after i, or -1 if there are none. */
    private int skipTimedForward(int i) {
        int size = modelTaskList.size();
        for (int k = Math.max(0, i); k < size; k++) {
            Task t = modelTaskList.get(k);
            if (t != null && !t.isTimed()) return k;
        }
        return -1;
    }

    /** @return the first non-timed task index, or -1 when every task is timed. */
    public int firstRunnableIndex() { return skipTimedForward(0); }

    /** All timed tasks in the queue (Patch B.8). */
    public List<Task> getTimedTasks() {
        List<Task> out = new ArrayList<>();
        for (int i = 0; i < modelTaskList.size(); i++) {
            Task t = modelTaskList.get(i);
            if (t != null && t.isTimed()) out.add(t);
        }
        return out;
    }

    /** True when the queue loops forever (AFTER_ALL_LOOPS timers can never fire then). */
    public boolean isInfiniteLoop() { return queueLoopTarget <= 0; }

    /**
     * Clamps a requested loop count to the current account's tier cap (Patch B.14). Free/guest =
     * 50, VIP = 150. This is the UI being honest; the server independently clamps any loop grant,
     * so a modified client can't exceed it either way.
     */
    private int clampLoops(int requested) {
        int cap = main.market.Tier.maxLoops();
        if (requested <= 0) return main.market.Tier.allowsInfinite() ? 0 : cap;  // 0 = infinite
        if (requested > cap) {
            showToast("Your tier allows up to " + cap + " loops"
                    + (main.market.Tier.isGuest() ? " - log in / upgrade for more" : ""), listTaskList, false);
            return cap;
        }
        return requested;
    }

    /** Re-applies the loop cap after the account tier changes (login/logout/upgrade). */
    public void applyTierLimits() {
        SwingUtilities.invokeLater(() -> {
            int cap = main.market.Tier.maxLoops();
            // resize the spinner's ceiling
            if (loopQueueSpinner != null)
                ((SpinnerNumberModel) loopQueueSpinner.getModel()).setMaximum(
                        main.market.Tier.allowsInfinite() ? 9999 : cap);
            // pull an over-cap value back down
            if (queueLoopTarget > cap && cap > 0) {
                queueLoopTarget = cap;
                suppressLoopEvents = true;
                if (loopQueueSpinner != null) loopQueueSpinner.setValue(cap);
                suppressLoopEvents = false;
                updateQueueProgress();
            }
            // ∞ is VIP-only
            if (loopInfiniteCheck != null && loopInfiniteCheck.isSelected()
                    && !main.market.Tier.allowsInfinite()) {
                loopInfiniteCheck.setSelected(false);
                loopQueueSpinner.setEnabled(true);
                queueLoopTarget = cap;
            }
            refreshTierStatusLabel();
        });
    }


    private JComboBox<String> accountSwitcher;
    private JButton btnAddAccount;
    private JButton btnLoginRow;
    private JLabel lblServerDot;                 // v1.32b: live server status (green/red)
    private javax.swing.Timer connPollTimer;     // v1.32b: 30s health poll driving the dot
    private JButton btnAccountLogout;    // v1.32b: DreamMan-account logout (separate from game)
    private JButton btnEditProfile;      // v1.63: public-profile (bio) editor, login-gated
    private JButton btnScriptTriggers;   // v1.64: per-script trigger selection (Task List tab)
    private JButton btnTaskListTimer;    // v1.88: moved beside Script triggers (script-level)
    /**
     * v1.90: the "pause between tasks" controls. Created HERE rather than inside either builder,
     * because the task-button row and the loop bar are built in the opposite order to the one
     * you'd guess - the row that DISPLAYS this group is assembled before the method that used
     * to create it, so building it there handed the row a null. Eager creation makes the order
     * irrelevant, which is the only way this stays fixed.
     */
    private final JPanel pauseBetweenGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    private JButton btnPublishPresetRef, btnResetPresetRef;   // v1.90: hosted by the loop bar
    private boolean suppressAccountEvents = false;

    /** Builds the sign-in / account-switcher row for the Player card (Patch B.15). */
    private JPanel buildAccountSwitcherRow() {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(6, 0, 0, 0));

        accountSwitcher = new JComboBox<>();
        accountSwitcher.setToolTipText("Switch between your saved accounts");
        accountSwitcher.addActionListener(e -> {
            if (suppressAccountEvents) return;
            Object sel = accountSwitcher.getSelectedItem();
            if (sel != null) switchToAccount(String.valueOf(sel));
        });

        btnAddAccount = createButton("+");
        btnAddAccount.setToolTipText("Multiple accounts (DreamBot VIP) - coming soon");
        btnAddAccount.setPreferredSize(new Dimension(34, 26));
        btnAddAccount.addActionListener(e -> addAnotherAccount());

        btnLoginRow = createButton("Log in");
        btnLoginRow.setToolTipText("Sign in to switch accounts and use the marketplace");
        btnLoginRow.addActionListener(e -> {
            String url = marketRepo instanceof main.market.HttpRepository
                    ? ((main.market.HttpRepository) marketRepo).baseUrl()
                    : main.market.ServerAccount.session().baseUrl;
            // Patch B.17: the server is preset - nobody should ever have to "set a server first"
            if (url == null || url.isEmpty()) url = marketServerUrl;
            if (url == null || url.isEmpty()) url = DEFAULT_MARKET_SERVER_URL;
            main.menu.components.LoginDialog.open(this, url, this::onAccountChanged);
        });

        // v1.32b: the Test-connection BUTTON moved into the Dev Console; here it's replaced by
        // a small live dot - green when /health answers, red when it doesn't, grey while unknown.
        lblServerDot = new JLabel(dotIcon(new Color(0x77, 0x77, 0x77)));
        lblServerDot.setToolTipText("Server status (checked every 30s)");
        startServerPolling();

        JPanel right = new JPanel(new BorderLayout(4, 0));
        right.setOpaque(false);
        right.add(accountSwitcher, BorderLayout.CENTER);
        right.add(btnAddAccount, BorderLayout.EAST);

        JLabel lbl = new JLabel("Accounts");
        lbl.setForeground(TEXT_DIM);
        row.add(lbl, BorderLayout.WEST);
        row.add(right, BorderLayout.CENTER);
        JPanel loginBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        loginBtns.setOpaque(false);
        loginBtns.add(lblServerDot);
        loginBtns.add(btnLoginRow);
        row.add(loginBtns, BorderLayout.EAST);

        refreshAccountSwitcher();
        return row;
    }

    /** v1.32b: a solid status dot (used by the live server indicator). */
    private static Icon dotIcon(Color color) {
        final int s = 10;
        return new Icon() {
            public int getIconWidth() { return s; }
            public int getIconHeight() { return s; }
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillOval(x, y, s - 1, s - 1);
                g2.setColor(color.darker());
                g2.drawOval(x, y, s - 1, s - 1);
                g2.dispose();
            }
        };
    }

    /**
     * v1.32b: lightweight background health poll driving the status dot - one GET to
     * {@code <server>/health} every 30 seconds (plus one immediately). The network call never
     * touches the EDT and never opens dialogs; the full classified diagnostic (the old Test
     * connection button) now lives in the Dev Console.
     */
    private void startServerPolling() {
        if (connPollTimer != null) return;
        Runnable check = () -> new Thread(() -> {
            boolean ok = false;
            try {
                String base = marketServerUrl == null || marketServerUrl.isEmpty()
                        ? DEFAULT_MARKET_SERVER_URL : marketServerUrl;
                java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                        new java.net.URL(base + "/health").openConnection();
                c.setConnectTimeout(4000);
                c.setReadTimeout(4000);
                c.setRequestProperty("User-Agent", "DreamMan/1.32 (DreamBot script)");
                ok = c.getResponseCode() >= 200 && c.getResponseCode() < 300;
                c.disconnect();
            } catch (Throwable ignored) {}
            final boolean up = ok;
            SwingUtilities.invokeLater(() -> {
                if (lblServerDot == null) return;
                lblServerDot.setIcon(dotIcon(up ? new Color(0x3F, 0xB9, 0x50)
                                                : new Color(0xCC, 0x45, 0x45)));
                lblServerDot.setToolTipText(up
                        ? "Server online (checked every 30s)"
                        : "Server unreachable - details: Dev Console \u2192 Test connection");
            });
        }, "DreamMan-HealthPoll").start();
        connPollTimer = new javax.swing.Timer(30_000, e -> check.run());
        connPollTimer.setRepeats(true);
        connPollTimer.start();
        check.run();   // first reading immediately
    }

    /** Shows either the account dropdown (logged in) or a Log in button (guest). */
    private void refreshAccountSwitcher() {
        if (accountSwitcher == null) return;
        suppressAccountEvents = true;
        accountSwitcher.removeAllItems();
        boolean loggedIn = main.market.Tier.isLoggedIn() && main.market.AccountVault.isUnlocked();
        if (loggedIn) {
            for (main.market.AccountVault.Account a : main.market.AccountVault.accounts())
                accountSwitcher.addItem(a.name);
            accountSwitcher.setVisible(true);
            if (btnAddAccount != null) btnAddAccount.setVisible(true);
            if (btnLoginRow != null) btnLoginRow.setVisible(false);
        } else {
            accountSwitcher.setVisible(false);
            if (btnAddAccount != null) btnAddAccount.setVisible(false);
            if (btnLoginRow != null) btnLoginRow.setVisible(true);
        }
        suppressAccountEvents = false;
    }

    /** Adds another Jagex account to the vault (respecting the tier cap). */
    private void addAnotherAccount() {
        // v1.32b: multi-account switching relies on DreamBot's AccountManager, which is a
        // DreamBot VIP feature (and may need extra API access on our side). It isn't wired up
        // yet, so instead of a half-working form we show a teaser that points at DreamBot VIP.
        JEditorPane pane = new JEditorPane("text/html",
                "<html><body style='width:340px;font-family:sans-serif;font-size:11px;color:#DDD'>"
                + "<h3 style='color:#E0B341;margin:2px 0'>Multiple accounts &mdash; coming soon</h3>"
                + "<p>Running DreamMan across several accounts is a <b>future release</b>. It builds"
                + " on DreamBot's account manager, which is part of <b>DreamBot VIP</b>, so you'll"
                + " need VIP to use it once it's ready.</p>"
                + "<p>You can get VIP here:<br>"
                + "<a href='https://dreambot.org/forums/index.php?/store/product/6-vip/'>"
                + "dreambot.org &rsaquo; store &rsaquo; VIP</a></p>"
                + "<p style='color:#9A9A9A'><i>Note: this isn't available in DreamMan yet &mdash;"
                + " we may also need to arrange extra API access before enabling it. The link is"
                + " just so you know what it'll require.</i></p>"
                + "</body></html>");
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.addHyperlinkListener(ev -> {
            if (ev.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                String u = ev.getURL() != null ? ev.getURL().toString()
                        : "https://dreambot.org/forums/index.php?/store/product/6-vip/";
                try {
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(u));
                } catch (Throwable t) {
                    try {
                        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                                .setContents(new java.awt.datatransfer.StringSelection(u), null);
                        showToast("Link copied to clipboard", btnAddAccount, true);
                    } catch (Throwable ignored) {}
                }
            }
        });
        JOptionPane.showMessageDialog(this, pane, "Multiple accounts (VIP)",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void switchToAccount(String name) {
        main.market.AccountVault.Account a = main.market.AccountVault.find(name);
        if (a == null) return;
        if (a.pin != null && !a.pin.isEmpty()) main.tools.BankPin.setPin(a.pin);
        showToast("Active account: " + name, accountSwitcher, true);
    }

    /** Called after any login/logout/account change to refresh tier-dependent UI. */
    private void onAccountChanged() {
        applyTierLimits();
        refreshAccountSwitcher();
        refreshTierStatusLabel();
        refreshAccountTab();   // v1.87: flip auth <-> signed-in, rebuild limits + badges
        refreshMarketGate();   // v1.88: the market tab is signed-in only
        refreshAccountLogoutVisibility();   // v1.32b: show/hide the account Log out button
        refilterLibrary();   // VIP tasks may now be visible
        maybeAutoConnectMarket();   // Patch B.17: logging in grants consent -> market goes live
    }

    /**
     * Patch B.17: connects the market to the preset server automatically whenever that's
     * allowed - a server is configured, the user has consented to market browsing (signing in
     * grants this), and we aren't already on it. No dialogs, no setup step: if you have
     * internet, the market just loads.
     */
    private void maybeAutoConnectMarket() {
        if (marketServerUrl == null || marketServerUrl.isEmpty()) return;
        if (!main.privacy.Consent.has(main.privacy.Consent.MARKET_BROWSE)) return;
        // v1.32b: ALWAYS rebuild the repo with the CURRENT token here. The old code early-returned
        // when the repo was already an HttpRepository - but at startup that repo was created with
        // an EMPTY token (not logged in yet), so after login it kept sending unauthenticated
        // requests and every publish/rate/remove failed with 401. Recreating it with the fresh
        // session token is the fix for "I published but the server has nothing".
        marketRepo = new main.market.HttpRepository(marketServerUrl,
                main.market.ServerAccount.session().token);
        if (btnServerSource != null) btnServerSource.setSelected(true);
        reloadMarket();
    }

    /** Updates the tier label on the Status card (Patch B.14). */
    private void refreshTierStatusLabel() {
        if (lblAccountTier != null) {
            lblAccountTier.setText(main.market.Tier.label());
            lblAccountTier.setForeground(main.market.Tier.isVip()
                    ? new Color(230, 190, 90) : TEXT_MAIN);
        }
        // v1.32b: the Dev Console is a hidden tab again (owner-only), added/removed as the
        // account tier changes - not a right-click context menu, which was the wrong home.
        syncDevConsoleTab();
        refreshAccountLogoutVisibility();
        checkSubmissionOutcomes();   // v1.67
    }

    /**
     * v1.67: tells an author what actually happened to anything they submitted while the
     * moderation valve was on. Without it the valve is a black hole from the submitter's side -
     * their listing simply never appears and nothing explains why, which reads as a broken market.
     *
     * <p>Runs off the EDT once per sign-in. Each outcome is acknowledged on the server, so it's
     * reported exactly once and never nags. Every failure is silent by design: an older server
     * (no {@code /me/submissions}) or an offline start should cost nothing.
     */
    private void checkSubmissionOutcomes() {
        if (!main.market.ServerAccount.isLoggedIn()) return;
        Thread t = new Thread(() -> {
            try {
                main.market.ServerAccount server = new main.market.ServerAccount(
                        main.market.ServerAccount.session().baseUrl);
                List<String> lines = new ArrayList<>();
                List<String> ack = new ArrayList<>();
                for (Map<String, Object> m : server.mySubmissions()) {
                    if (m == null) continue;
                    String status = String.valueOf(m.get("status"));
                    if ("pending".equals(status)) continue;   // still waiting - nothing to say yet
                    Object seen = m.get("seen");
                    if (Boolean.TRUE.equals(seen) || "true".equals(String.valueOf(seen))) continue;
                    String name = String.valueOf(m.get("name"));
                    if ("approved".equals(status)) {
                        lines.add("\u2713  \"" + name + "\" was approved \u2014 it's on the market now.");
                    } else {
                        String reason = m.get("reason") == null ? "" : String.valueOf(m.get("reason"));
                        lines.add("\u2717  \"" + name + "\" wasn't accepted"
                                + (reason.trim().isEmpty() ? "." : ": " + reason));
                    }
                    ack.add(String.valueOf(m.get("id")));
                }
                if (lines.isEmpty()) return;
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        String.join("\n", lines) + "\n\nYour tasks are still in your library "
                        + "either way \u2014 nothing was deleted.",
                        "Your submissions", JOptionPane.INFORMATION_MESSAGE));
                // Acknowledge only after it's been put on screen.
                for (String id : ack) {
                    try { server.ackSubmission(id); } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {
                // offline, or a server older than 1.5.1 - not worth surfacing
            }
        }, "DreamMan-submission-outcomes");
        t.setDaemon(true);
        t.start();
    }

    /** Adds the Dev Console tab for owners and admins; removes it otherwise. */
    private void syncDevConsoleTab() {
        if (mainTabs == null) return;
        int idx = indexOfTab("Dev Console");
        // v1.66: admins get the console too - moderation and the defaults library are admin work,
        // and gating them behind the single owner account defeats the point of having admins.
        boolean shouldShow = main.market.Tier.isOwner() || main.market.Tier.isAdmin();
        if (shouldShow && idx < 0) {
            // v1.88: its own terminal glyph - Dev Console and Settings wore the same gear.
            mainTabs.addTab("Dev Console",
                    new javax.swing.ImageIcon(iconToImage(
                            main.menu.components.UIIcons.devConsole(16, Theme.ACCENT), 16)),
                    main.menu.components.DevConsole.buildPanel(
                            () -> new ArrayList<>(libraryAll),
                            () -> new ArrayList<>(marketAll)));
        } else if (!shouldShow && idx >= 0) {
            mainTabs.removeTabAt(idx);
        }
    }

    /** Index of a main tab by title, or -1. */
    private int indexOfTab(String title) {
        if (mainTabs == null) return -1;
        for (int i = 0; i < mainTabs.getTabCount(); i++)
            if (title.equals(mainTabs.getTitleAt(i))) return i;
        return -1;
    }

    /**
     * Timed-task editor (Patch B.8). A timed task steps OUT of the normal queue rotation and runs
     * on a rough interval instead - handy for "collect runes from the tutors every ~30 min". It
     * never interrupts mid-task: you choose the safe point where it's allowed to fire.
     */
    private void openTimerDialog(Task task, JComponent anchor) {
        JCheckBox chkTimed = new JCheckBox("Run this task on a timer (removes it from the normal rotation)",
                task.isTimed());
        chkTimed.setOpaque(false);
        chkTimed.setForeground(TEXT_MAIN);

        JSpinner spMinutes = new JSpinner(new SpinnerNumberModel(task.getTimerMinutes(), 1, 600, 1));
        JSpinner spJitter = new JSpinner(new SpinnerNumberModel(task.getTimerJitterPct(), 0, 50, 5));

        JComboBox<String> cboWhen = new JComboBox<>(new String[]{
                "After each queue loop",
                "After ALL loops finish",
                "Right after another timed task (chain)"
        });
        String when = task.getTimerWhen();
        cboWhen.setSelectedIndex("AFTER_ALL_LOOPS".equals(when) ? 1
                : "AFTER_TIMED_TASK".equals(when) ? 2 : 0);

        JLabel warn = new JLabel(" ");
        warn.setForeground(new Color(230, 160, 90));
        Runnable checkWarn = () -> {
            boolean afterAll = cboWhen.getSelectedIndex() == 1;
            if (afterAll && isInfiniteLoop())
                warn.setText("\u26a0 Your queue loops forever, so \"after ALL loops\" will never fire.");
            else if (cboWhen.getSelectedIndex() == 2)
                warn.setText("Chains on: fires right after any other timed task completes.");
            else
                warn.setText(" ");
        };
        cboWhen.addActionListener(e -> checkWarn.run());
        checkWarn.run();

        JLabel preview = new JLabel();
        preview.setForeground(TEXT_DIM);
        Runnable upPreview = () -> {
            int m = (int) spMinutes.getValue(), j = (int) spJitter.getValue();
            int lo = (int) Math.round(m * (1 - j / 100.0)), hi = (int) Math.round(m * (1 + j / 100.0));
            preview.setText("Fires roughly every " + m + " min (actually " + lo + "-" + hi + " min)");
        };
        spMinutes.addChangeListener(e -> upPreview.run());
        spJitter.addChangeListener(e -> upPreview.run());
        upPreview.run();

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
        form.add(chkTimed, c);
        c.gridwidth = 1; c.gridy++;
        form.add(styledLabel("Every (minutes):"), c);
        c.gridx = 1; form.add(spMinutes, c);
        c.gridx = 0; c.gridy++;
        form.add(styledLabel("Randomness (\u00b1%):"), c);
        c.gridx = 1; form.add(spJitter, c);
        c.gridx = 0; c.gridy++; c.gridwidth = 2;
        form.add(preview, c);
        c.gridy++; c.gridwidth = 1;
        form.add(styledLabel("Fire when:"), c);
        c.gridx = 1; form.add(cboWhen, c);
        c.gridx = 0; c.gridy++; c.gridwidth = 2;
        form.add(warn, c);
        form.setPreferredSize(new Dimension(470, 210));

        int r = JOptionPane.showConfirmDialog(this, form, "Timer for \"" + task.getName() + "\"",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;

        task.setTimed(chkTimed.isSelected());
        task.setTimerMinutes((int) spMinutes.getValue());
        task.setTimerJitterPct((int) spJitter.getValue());
        task.setTimerWhen(cboWhen.getSelectedIndex() == 1 ? "AFTER_ALL_LOOPS"
                : cboWhen.getSelectedIndex() == 2 ? "AFTER_TIMED_TASK" : "AFTER_LOOP");
        task.setNextDueAt(0);   // re-arm on the next loop with the new settings

        // if the running index now points at a timed task, step past it
        if (currentExecutionIndex >= 0 && currentExecutionIndex < modelTaskList.size()) {
            Task cur = modelTaskList.get(currentExecutionIndex);
            if (cur != null && cur.isTimed()) {
                int next = firstRunnableIndex();
                setCurrentExecutionIndex(next);
            }
        }

        listTaskList.repaint();
        updateQueueProgress();
        saveAll(false);
        showToast(task.isTimed()
                ? task.getName() + " runs every ~" + task.getTimerMinutes() + " min"
                : task.getName() + " back in the normal rotation", anchor, true);
    }

    private JLabel styledLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT_MAIN);
        return l;
    }

    /**
     * One-click script export (Patch B.10). Packages the current queue - tasks, loop count and
     * your always-on checks - into a standalone .jar that DreamBot lists and runs by itself.
     */
    /**
     * v1.62: the export dialog's "Triggers..." picker, split out of the cramped export form into
     * its own dialog. The caller owns the checkbox list (their selected state IS the choice); this
     * just presents the same boxes at a readable size with select-all / none helpers. Returns when
     * closed - the caller re-reads the checkboxes.
     */
    private void openExportTriggersDialog(java.util.List<JCheckBox> trigPicks, JComponent anchor) {
        if (trigPicks.isEmpty()) {
            showToast("You have no always-on checks to include", anchor, false);
            return;
        }
        JPanel trigPanel = new JPanel();
        trigPanel.setLayout(new BoxLayout(trigPanel, BoxLayout.Y_AXIS));
        trigPanel.setOpaque(false);
        for (JCheckBox cb : trigPicks) trigPanel.add(cb);   // same instances the caller reads
        JScrollPane trigScroll = Theme.thinScrollbars(new JScrollPane(trigPanel));
        trigScroll.setPreferredSize(new Dimension(360,
                Math.min(320, 26 * trigPicks.size() + 12)));

        JPanel bulk = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        bulk.setOpaque(false);
        JButton all = createButton("Select all", new Color(35, 55, 45), null);
        all.addActionListener(e -> { for (JCheckBox cb : trigPicks) cb.setSelected(true); });
        JButton none = createButton("Select none", new Color(55, 45, 45), null);
        none.addActionListener(e -> { for (JCheckBox cb : trigPicks) cb.setSelected(false); });
        bulk.add(all);
        bulk.add(none);

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setOpaque(false);
        JLabel head = styledLabel("Ticked checks travel with the exported script:");
        root.add(head, BorderLayout.NORTH);
        root.add(trigScroll, BorderLayout.CENTER);
        root.add(bulk, BorderLayout.SOUTH);

        JOptionPane.showOptionDialog(this, root, "Checks to include",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                new Object[]{"Done"}, "Done");
    }

    private void openExportDialog(JComponent anchor) {
        if (modelTaskList.isEmpty()) {
            showToast("Add some tasks to the queue first", anchor, false);
            return;
        }

        JTextField txtName = new JTextField("My DreamMan Script", 22);
        // v1.32: the author is LINKED TO THE ACCOUNT and not editable - one less field, and it
        // guarantees a script is credited to whoever's signed in (the server sets it from the
        // token on publish anyway). Falls back to the character name / Anonymous for local
        // exports when you're not signed in.
        String acctName = main.market.ServerAccount.isLoggedIn()
                ? main.market.ServerAccount.username() : null;
        String who = safePlayerName();
        String authorName = (acctName != null && !acctName.isEmpty()) ? acctName
                : (who == null || who.isEmpty() ? "Anonymous" : who);
        JTextField txtAuthor = new JTextField(authorName, 22);
        txtAuthor.setEditable(false);
        txtAuthor.setToolTipText("Linked to your account - can't be changed here.");
        JSpinner spVersion = new JSpinner(new SpinnerNumberModel(1.0, 0.1, 999.0, 0.1));
        JTextArea txtDesc = new JTextArea(3, 22);
        txtDesc.setLineWrap(true);
        txtDesc.setWrapStyleWord(true);

        // v1.51: pick WHICH checks travel with the export - all listed, the currently-enabled
        // ones pre-ticked (the old single checkbox blindly included everything).
        // v1.62: the checkbox list was unreadable squashed into this form, so it now lives behind
        // a "Triggers..." button that opens it as its own dialog. The checkboxes are built once
        // here and their state is read after the export dialog closes, exactly as before.
        final java.util.List<JCheckBox> trigPicks = new ArrayList<>();
        for (main.watchers.Trigger gt : globalTriggers) {
            if (gt == null) continue;
            JCheckBox cb = new JCheckBox(gt.describe(), gt.isEnabled());
            cb.setOpaque(false);
            cb.setForeground(TEXT_MAIN);
            cb.putClientProperty("trigger", gt);
            trigPicks.add(cb);
        }
        final int[] trigChosen = { (int) trigPicks.stream().filter(AbstractButton::isSelected).count() };
        JButton btnTriggers = createButton("Triggers\u2026", new Color(55, 45, 65), null);
        Runnable updateTrigBtn = () -> btnTriggers.setText(trigPicks.isEmpty()
                ? "Triggers\u2026 (none)"
                : "Triggers\u2026 (" + trigChosen[0] + " of " + trigPicks.size() + ")");
        updateTrigBtn.run();
        btnTriggers.setToolTipText("Choose which always-on checks travel with the script");
        btnTriggers.addActionListener(ev -> {
            openExportTriggersDialog(trigPicks, btnTriggers);
            trigChosen[0] = (int) trigPicks.stream().filter(AbstractButton::isSelected).count();
            updateTrigBtn.run();
        });
        // v1.88: the "also stage as TASK / SCRIPT (market-ready)" combo is gone. It predates
        // the Publish preset button and the card builder, and it staged a listing with no card
        // - which the mandatory-card publish gate then refused. Publishing has one road now.

        java.io.File scriptsDir = main.tools.ScriptExporter.dreamBotScriptsDir();
        JLabel lblWhere = new JLabel(scriptsDir != null
                ? "Saves straight into your DreamBot Scripts folder."
                : "DreamBot's Scripts folder wasn't found - you'll pick a location.");
        lblWhere.setForeground(TEXT_DIM);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.gridy = 0;
        form.add(styledLabel("Script name:"), c);
        c.gridx = 1; form.add(txtName, c);
        c.gridx = 0; c.gridy++;
        form.add(styledLabel("Author:"), c);
        c.gridx = 1; form.add(txtAuthor, c);
        c.gridx = 0; c.gridy++;
        form.add(styledLabel("Version:"), c);
        c.gridx = 1; form.add(spVersion, c);
        c.gridx = 0; c.gridy++;
        form.add(styledLabel("Description:"), c);
        c.gridx = 1; form.add(new JScrollPane(txtDesc), c);
        c.gridx = 0; c.gridy++; c.gridwidth = 2;
        form.add(styledLabel("Always-on checks that travel with the script:"), c);
        c.gridy++;
        form.add(btnTriggers, c);
        c.gridy++;
        form.add(styledLabel(modelTaskList.size() + " task(s), "
                + (queueLoopTarget <= 0 ? "looping forever" : queueLoopTarget + " loop(s)")), c);
        c.gridy++;
        form.add(lblWhere, c);

        int r = JOptionPane.showConfirmDialog(this, form, "Export as a DreamBot script",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;

        // build the bundle from the live queue
        main.data.store.ScriptBundle bundle = new main.data.store.ScriptBundle();
        bundle.name = txtName.getText().trim();
        bundle.author = txtAuthor.getText().trim();
        bundle.version = ((Number) spVersion.getValue()).doubleValue();
        bundle.description = txtDesc.getText().trim();
        // v1.49: preserve each task's own loop count on export (the runtime tier cap clamps a
        // task's repeat to the runner's max loops when it executes, so a shared script can't push
        // any loop dimension past the cap - which is all a free user could do manually anyway).
        // Queue-level loops still default to 1 so the importer picks their own.
        bundle.loops = 1;
        bundle.tasks = ProfileCodec.tasksToData(modelTaskList);
        // v1.64: three tiers of triggers travel. Per-ACTION triggers are already inside the tasks
        // above (Action's __triggers). SCRIPT triggers - the checks set to run while THIS script
        // runs - all travel. GLOBAL triggers are opt-in via the picker below.
        if (!scriptTriggers.isEmpty())
            bundle.scriptTriggers = currentScriptTriggersJson();
        java.util.List<main.watchers.Trigger> pickedTrigs = new ArrayList<>();
        for (JCheckBox cb : trigPicks)
            if (cb.isSelected())
                pickedTrigs.add((main.watchers.Trigger) cb.getClientProperty("trigger"));
        if (!pickedTrigs.isEmpty())
            bundle.globalTriggers = main.watchers.TriggerCodec.toJson(pickedTrigs);

        // (v1.88: the optional stage-to-market-ready step was removed with the combo above.)

        // where to write it
        java.io.File target;
        if (scriptsDir != null) {
            target = new java.io.File(scriptsDir,
                    main.tools.ScriptExporter.safeFileName(bundle.name) + ".jar");
        } else {
            JFileChooser chooser = new JFileChooser(main.data.store.LocalStore.getExportsDir());
            chooser.setDialogTitle("Save the script jar");
            chooser.setSelectedFile(new java.io.File(
                    main.tools.ScriptExporter.safeFileName(bundle.name) + ".jar"));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            target = chooser.getSelectedFile();
        }

        final java.io.File out = target;
        setStatus("Exporting " + bundle.name + "...");
        new Thread(() -> {
            try {
                main.tools.ScriptExporter.export(bundle, out);
                SwingUtilities.invokeLater(() -> {
                    setStatus("Exported " + out.getName());
                    JOptionPane.showMessageDialog(this,
                            "<html><b>" + bundle.name + "</b> is ready.<br><br>"
                                    + "Saved to:<br>" + out.getAbsolutePath() + "<br><br>"
                                    + "Restart DreamBot (or refresh its script list) and it will "
                                    + "appear as its own script - no compiler needed.</html>",
                            "Script exported", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Throwable ex) {
                SwingUtilities.invokeLater(() -> {
                    setStatus("Export failed");
                    JOptionPane.showMessageDialog(this,
                            "Could not export:\n" + ex.getMessage(),
                            "Export failed", JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "DreamMan-Export").start();
    }

    /** True when a due AFTER_ALL_LOOPS timed task is waiting (so we delay the queue-complete pause). */
    private boolean hasDueAfterAllLoopsTask() {
        if (isInfiniteLoop()) return false;
        for (Task t : getTimedTasks())
            if (t != null && "AFTER_ALL_LOOPS".equals(t.getTimerWhen()) && t.isDue())
                return true;
        return false;
    }

    /** The engine calls this once a post-queue timed task has finished, to finally stop. */
    public void pauseAfterTimedCompletion() {
        currentExecutionIndex = -1;
        queueLoopCurrent = 1;
        pause("Queue complete!");
    }

    /** Resets the whole-queue loop counter to the first pass (called when a fresh run begins). */
    public void resetLoopProgress() {
        queueLoopCurrent = 1;
        SwingUtilities.invokeLater(this::updateQueueProgress);
    }

    public int getQueueLoopTarget() { return queueLoopTarget; }

    /** Sets the whole-queue loop target (0 = infinite) and refreshes the loop widgets. */
    public void setQueueLoopTarget(int target) {
        queueLoopTarget = Math.max(0, target);
        SwingUtilities.invokeLater(() -> { syncLoopControls(); updateQueueProgress(); });
    }

    /** Pushes the current loop target into the spinner / infinite checkbox without re-firing them. */
    private void syncLoopControls() {
        if (loopInfiniteCheck == null) return;
        suppressLoopEvents = true;
        try {
            boolean inf = (queueLoopTarget <= 0);
            loopInfiniteCheck.setSelected(inf);
            if (loopQueueSpinner != null) {
                loopQueueSpinner.setEnabled(!inf);
                if (!inf)
                    loopQueueSpinner.setValue(Math.max(1, queueLoopTarget));
            }
        } finally {
            suppressLoopEvents = false;
        }
    }

    /**
     * Updates the bottom progress bar + the "Loop x/y" indicator to reflect how far through the
     * queue (and its loops) we are. Safe to call any time; no-ops sensibly on an empty queue.
     */
    private void updateQueueProgress() {
        int size = modelTaskList.size();
        boolean infinite = (queueLoopTarget <= 0);

        // 0-based tasks completed in the current pass (index -1 means "not started / idle")
        int done = currentExecutionIndex < 0 ? 0 : currentExecutionIndex;
        int taskDisplay = size == 0 ? 0 : Math.min(done + 1, size);

        int pct;
        if (size == 0) {
            pct = 0;
        } else if (infinite) {
            pct = (int) Math.round(100.0 * done / size);
        } else {
            int total = Math.max(1, queueLoopTarget) * size;
            int completed = (queueLoopCurrent - 1) * size + done;
            pct = (int) Math.round(100.0 * completed / total);
        }
        pct = Math.max(0, Math.min(100, pct));

        String loopText = infinite
                ? ("Loop " + queueLoopCurrent + "/∞")
                : ("Loop " + queueLoopCurrent + "/" + Math.max(1, queueLoopTarget));

        final int fpct = pct;
        final String barText = size == 0
                ? "No tasks queued"
                : (loopText + "  ·  task " + taskDisplay + "/" + size);
        final String indicator = size == 0 ? "—" : loopText;

        Runnable r = () -> {
            statusProgress.setValue(fpct);
            statusProgress.setString(barText);
            if (lblLoopIndicator != null)
                lblLoopIndicator.setText(indicator);
        };
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }

    /**
     * Creates the 'Task List' tab which may be used to view and manipulate the current task queue.
     *
     * @return
     */
    private JPanel createTaskListTab() {
        /// Set up the main task list panel
        // create main panel
        JPanel panelTaskList = new JPanel(new BorderLayout(10, 10));
        panelTaskList.setBorder(new EmptyBorder(14, 14, 14, 14));
        panelTaskList.setBackground(Theme.BG_APP);

        /// CENTER: Add the task queue list display to the center of the task list panel
        taskCardRenderer = new TaskCardRenderer();
        listTaskList.setCellRenderer(taskCardRenderer);

        // Patch B.2: drag a task card to reorder the queue. Disabled while the queue is
        // executing (indexes are live) - a toast explains instead of silently ignoring.
        listTaskList.setDragEnabled(true);
        listTaskList.setDropMode(DropMode.INSERT);
        listTaskList.setTransferHandler(new TransferHandler() {
            private int fromIndex = -1;

            @Override public int getSourceActions(JComponent c) { return MOVE; }

            @Override protected java.awt.datatransfer.Transferable createTransferable(JComponent c) {
                fromIndex = listTaskList.getSelectedIndex();
                return new java.awt.datatransfer.StringSelection(String.valueOf(fromIndex));
            }

            @Override public boolean canImport(TransferSupport support) {
                return support.isDrop() && fromIndex >= 0
                        && support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor);
            }

            @Override public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                // Patch B.5: reordering is allowed while the script is PAUSED - only a live,
                // actively-executing queue blocks it. If the currently-executing tile itself is
                // moved, execution follows it to its new position and resumes from the same
                // action, as though it never moved.
                if (currentExecutionIndex != -1 && !isMenuPaused()) {
                    showToast("Pause or stop before reordering", listTaskList, false);
                    return false;
                }
                JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
                int to = dl.getIndex();
                if (to < 0 || fromIndex < 0 || fromIndex >= modelTaskList.size()) return false;
                Task moved = modelTaskList.get(fromIndex);
                modelTaskList.remove(fromIndex);
                if (to > fromIndex) to--;              // account for the removal shift
                to = Math.max(0, Math.min(to, modelTaskList.size()));
                modelTaskList.add(to, moved);

                // remap the execution pointer around the move (paused case)
                if (currentExecutionIndex != -1) {
                    int exec = currentExecutionIndex;
                    int newExec;
                    if (fromIndex == exec)                     newExec = to;      // moved the live tile
                    else if (fromIndex < exec && to >= exec)   newExec = exec - 1; // pulled one out from above
                    else if (fromIndex > exec && to <= exec)   newExec = exec + 1; // pushed one in above
                    else                                        newExec = exec;    // move was fully below/above
                    if (newExec != exec) {
                        setCurrentExecutionIndex(newExec);
                        // tell the engine its served index moved WITHOUT resetting the action
                        // cursor - "continue as though it never moved"
                        if (script instanceof main.scripts.DreamBotMan)
                            ((main.scripts.DreamBotMan) script).remapServedIndex(newExec);
                    }
                }

                listTaskList.setSelectedIndex(to);
                updateQueueProgress();
                fromIndex = -1;
                return true;
            }
        });
        listTaskList.setFixedCellHeight(62);
        listTaskList.setBackground(Theme.SURFACE_1);
        listTaskList.setSelectionBackground(Theme.SURFACE_1);
        listTaskList.setBorder(null);
        styleJList(listTaskList);


        /// WEST: Add up/down buttons to navigate through task list
        // create task list navigation buttons (up/down arrows)
        JButton btnUp = createNavButton("▲", modelTaskList, listTaskList);
        JButton btnDown = createNavButton("▼", modelTaskList, listTaskList);

        // create west panel
        JPanel west = new JPanel(new GridLayout(0, 1, 0, 5));
        west.setOpaque(false);
        // add navigation buttons (up/down arrows)
        west.add(btnUp);
        west.add(btnDown);

        /// SOUTH: Add bottom panel and delete task button
        // create status label/progress bar
        setStatus("Idle");
        lblStatus.setForeground(TEXT_MAIN);

        // create south panel with rows
        JPanel south = new JPanel(new BorderLayout(0, 10));
        south.setOpaque(false);

        // v1.88: BoxLayout, not GridLayout. The grid forced every control to the same width,
        // which is why the insert-mode icon sat in a button as wide as "Publish preset..." and
        // why a spacer was impossible. Now each control takes the width it needs and the gap
        // between the build half and the tools half is real.
        // v1.89b: WrapLayout, not BoxLayout. X_AXIS keeps everything on one line forever and
        // just squeezes components past legibility when the client is docked narrow; wrapping
        // to a second row is what a toolbar should do. The 18px gap between the "build" half
        // and the "tools" half still reads, because a wrap only ever happens between buttons.
        JPanel southButtons = new JPanel(new main.menu.components.WrapLayout(FlowLayout.LEFT, 5, 3));
        southButtons.setOpaque(false);
        southButtons.setBorder(new EmptyBorder(0, 0, 2, 0));

        // v1.62: the Task List "Save" button is gone - the queue autosaves on change (add / remove
        // / reorder via the model listener) plus a 60s backstop and a save on exit.

        ///  Create Task List duplicate button
        JButton btnTaskListDuplicate = createButton("Duplicate");
        btnTaskListDuplicate.addActionListener(e -> {
            // create a new task using the selected value
            Task selected = listTaskList.getSelectedValue();
            if (selected == null) {
                // return early if no tasks are currently selected
                this.showToast("Select a task first!", btnTaskListDuplicate, false);
                return;
            }

            // create a new task using the selected task
            Task task = new Task(selected);
            // Patch B.2: a duplicate is its own logical task - give it a fresh identity so
            // editing the original no longer rewrites the duplicate (and vice versa).
            task.regenerateId();
            // add the duplicated task to the task list
            modelTaskList.addElement(task);
            // refresh the task list display
            //listTaskList.repaint();
            this.showToast("Duplication complete! Size: " + modelTaskList.size(), btnTaskListDuplicate, true);
        });

        ///  Create Task List remove button
        JButton btnTaskListRemove = createButton("Remove", COLOR_BUTTON_RED, null);
        // disable remove button when nothing is selected
        btnTaskListRemove.setEnabled(listTaskList.getSelectedIndex() != -1);
        btnTaskListRemove.addActionListener(e -> {
            removeTask(listTaskList, modelTaskList, btnTaskListRemove);
        });

        ///  Create Task List edit button
        JButton btnTaskListView = createButton("View in builder...");
        btnTaskListView.addActionListener(e -> {
            loadIntoBuilder(listTaskList.getSelectedValue());
            this.showToast("Moving to builder for viewing...", btnTaskListView, true);
            // switch to tab 2 (3rd tab = Task Builder) to edit task
            mainTabs.setSelectedIndex(2);
        });

        // v1.88: "Reset All" was a one-click way to destroy every preset you own - a nuclear
        // button sitting in a row you use constantly. It's now a per-preset RESET: it clears
        // the SELECTED preset and restores its default name, leaving the slot open to refill.
        // If you walk away without putting anything in it, the empty slot is squashed out
        // (see compactPresets) so the strip never ends up patchy - preset 1, 2, gap, 4.
        JButton btnResetPreset = createButton("Reset", new Color(120, 60, 20), null);
        btnResetPreset.setToolTipText("<html>Clear the <b>selected preset</b> and reset its name, "
                + "ready to save something new into it.<br>Leave it empty and it closes up on its "
                + "own \u2014 no gaps in the preset strip.</html>");
        btnResetPreset.addActionListener(e -> resetSelectedPreset(btnResetPreset));

        listTaskList.addListSelectionListener(e -> {
            btnTaskListRemove.setEnabled(!listTaskList.isSelectionEmpty());
            syncTaskRepeatSpinner();
            syncTaskOnStartCheckbox();   // v1.68
        });

        listTaskList.addMouseListener(new MouseAdapter() {
            /** v1.31: the context menu waits ~280ms so a double-RIGHT-click can land first. */
            private javax.swing.Timer popupTimer;

            @Override
            public void mouseClicked(MouseEvent e) {
                int index = listTaskList.locationToIndex(e.getPoint());
                if (index == -1 || !listTaskList.getCellBounds(index, index).contains(e.getPoint()))
                    return;
                listTaskList.setSelectedIndex(index);

                // v1.32: a click on the -/+ loop steppers is handled FIRST, for ANY click count,
                // and consumed - so double-clicking a stepper just adjusts loops twice and can
                // never be read as a row double-click. This is the "disable double-click while
                // stepping loops" fix.
                if (SwingUtilities.isLeftMouseButton(e) && handleRepeatStepperClick(e, index))
                    return;

                // v1.32: double-LEFT a TASK LIST row opens it in the builder for editing (this is
                // the "double-click the task list = editor" behaviour). Duplicating a queue entry
                // now lives on the right-click menu (Duplicate below / to end).
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2 && !e.isShiftDown()) {
                    if (popupTimer != null) popupTimer.stop();
                    loadIntoBuilder(listTaskList.getSelectedValue());
                    selectMainTab("Task Builder");
                }
            }
            @Override public void mousePressed(MouseEvent e)  { maybePopup(e); }
            @Override public void mouseReleased(MouseEvent e) { maybePopup(e); }
            @Override public void mouseExited(MouseEvent e)   { hoveredTaskIndex = -1; listTaskList.repaint(); }
            private void maybePopup(MouseEvent e) {
                if (!e.isPopupTrigger() || e.getClickCount() != 1) return;
                int index = listTaskList.locationToIndex(e.getPoint());
                if (index < 0 || !listTaskList.getCellBounds(index, index).contains(e.getPoint())) return;
                listTaskList.setSelectedIndex(index);
                final int px = e.getX(), py = e.getY();
                if (popupTimer != null) popupTimer.stop();
                popupTimer = new javax.swing.Timer(280, ev ->
                        showTaskContextMenu(px, py, index));
                popupTimer.setRepeats(false);
                popupTimer.start();
            }
        });
        // hover highlight
        listTaskList.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int i = listTaskList.locationToIndex(e.getPoint());
                if (i >= 0 && !listTaskList.getCellBounds(i, i).contains(e.getPoint())) i = -1;
                if (i != hoveredTaskIndex) { hoveredTaskIndex = i; listTaskList.repaint(); }
            }
        });

        // v1.88: the Timer button moved to the loop bar, beside Script triggers - it's a
        // script-level scheduling control, not one of the queue-building buttons.

        // v1.68: on-start-only for the SELECTED queue entry. A real checkbox rather than a button,
        // because it reports state as much as it sets it - the whole point is being able to see at
        // a glance which entries are setup-only before starting a long session.
        chkTaskOnStart = new JCheckBox("On start only");
        chkTaskOnStart.setOpaque(false);
        chkTaskOnStart.setForeground(Theme.TEXT);
        chkTaskOnStart.setEnabled(false);
        chkTaskOnStart.setToolTipText("<html>Run this task on the <b>first queue loop only</b>, "
                + "then skip it on every later lap.<br>Applies to <b>this entry</b> only \u2014 the "
                + "same task elsewhere in the queue keeps running.</html>");
        chkTaskOnStart.addActionListener(e -> {
            if (taskOnStartSyncing) return;
            Task sel = listTaskList.getSelectedValue();
            if (sel == null) { chkTaskOnStart.setSelected(false); return; }
            sel.setOnStartOnly(chkTaskOnStart.isSelected());
            saveAll(false);
            listTaskList.repaint();
            showToast(chkTaskOnStart.isSelected()
                    ? "\"" + sel.getName() + "\" now runs on the first loop only"
                    : "\"" + sel.getName() + "\" runs every loop again", chkTaskOnStart, true);
        });

        // v1.88: "Export as script" is GONE from here. Handing every user a one-click way to
        // rip their queue out as a standalone jar mostly enabled taking the app's work
        // elsewhere; it now lives in the Dev Console's Script Management tab, where admins can
        // export any script they select. The exporter itself is unchanged.

        // v1.62: "+" opens the library picker popup - double-click a task to add it to THIS list.
        // This is what lets the Task Library's own "add" button and the Quick-add menu retire.
        JButton btnAddFromLib = createButton("+ Add\u2026", new Color(35, 55, 70), null);
        btnAddFromLib.setToolTipText("Add tasks from your library (and append or start presets)");
        btnAddFromLib.addActionListener(e -> openLibraryQuickAdd());

        // v1.62: insert position as a single toggle icon button - "insert last" and "insert
        // after" swap in place (only one shown at a time, minimal noise). Whatever's showing is
        // the mode a library add uses. Replaces the old "Insert: End" text dropdown.
        btnInsertToggle = iconButton(
                insertAfterSelected ? main.menu.components.UIIcons.insertAfter(18, Theme.ACCENT)
                                    : main.menu.components.UIIcons.insertEnd(18, Theme.ACCENT),
                insertAfterSelected ? "Adding AFTER the selected task \u2014 click for: at the end"
                                    : "Adding at the END \u2014 click for: after the selected task",
                this::toggleInsertMode);

        ///  v1.88: the row reads left to right as the order you actually work in -
        ///  BUILD the queue (add / duplicate / remove / this entry's triggers / on-start),
        ///  then a gap, then the TOOLS you reach for once it's built (publish, insert mode,
        ///  open in builder, reset the preset).
        southButtons.add(btnAddFromLib);
        southButtons.add(btnTaskListDuplicate);
        southButtons.add(btnTaskListRemove);
        // v1.79: publishing a preset moved here from the market. The queue in front of you IS
        // the thing being published, triggers and on-start flags included - they ride inside each
        // task's snapshot, so nothing extra has to be gathered.
        JButton btnPublishPreset = createButton("Publish preset\u2026", new Color(25, 60, 75), null);
        btnPublishPreset.setToolTipText("<html>Stage the <b>current queue</b> as one market "
                + "script and open its card builder.<br>Each task's own triggers and "
                + "\"first loop only\" flags travel with it.</html>");
        btnPublishPreset.addActionListener(e -> {
            if (modelTaskList.isEmpty()) {
                showToast("Build a queue first \u2014 there's nothing to publish", btnPublishPreset, false);
                return;
            }
            List<Task> q = new ArrayList<>();
            for (int i = 0; i < modelTaskList.size(); i++) q.add(modelTaskList.get(i));
            // Name it after the selected preset when there is one, so the market card matches
            // what the user calls it; otherwise it's just the queue they've assembled.
            // Name it after the active preset when there is one, so the market card matches what
            // the user calls it; otherwise it's just the queue they've assembled.
            Preset sel = (selectedPresetIndex >= 0 && selectedPresetIndex < modelPresets.size())
                    ? modelPresets.get(selectedPresetIndex) : null;
            String name = (sel != null && sel.getName() != null && !sel.getName().isBlank())
                    ? sel.getName() : "Current queue";
            stageForCardBuilder(name, q, btnPublishPreset);
        });
        // v1.80: per-instance task triggers, next to the entry they bind to.
        // v1.88: renamed "Task triggers..." - "Triggers..." next to a "Script triggers..." button
        // one row up told you nothing about which of the two you were opening.
        JButton btnEntryTriggers = createButton("Task triggers\u2026", new Color(60, 40, 75), null);
        btnEntryTriggers.setToolTipText("<html>Triggers that run while <b>this queue entry</b> "
                + "executes.<br>Starts from the library default; your global checks can be "
                + "opted in per entry.</html>");
        btnEntryTriggers.addActionListener(e ->
                openInstanceTaskTriggers(listTaskList.getSelectedValue(), btnEntryTriggers));
        southButtons.add(btnEntryTriggers);
        southButtons.add(chkTaskOnStart);   // v1.68
        // the gap: everything left of it builds the queue, everything right of it acts on the
        // finished thing. A plain spacer, but it's what makes the row readable at a glance.
        southButtons.add(Box.createHorizontalStrut(18));
        // v1.90: Publish preset and Reset moved DOWN to the row under the presets. Both act on
        // the whole preset - the saved script - not on the task you have selected, so sitting
        // among Add / Duplicate / Remove implied a scope they never had. They trade places with
        // "Pause between tasks", which came up here for the same reason in reverse.
        southButtons.add(pauseBetweenGroup);
        southButtons.add(btnInsertToggle);
        southButtons.add(btnTaskListView);
        btnPublishPresetRef = btnPublishPreset;
        btnResetPresetRef = btnResetPreset;

        // ── v1.88: the three rows, reordered to match what they belong to ───────────────
        // The task BUTTONS act on the queue directly above them, so they sit closest to it.
        // The PRESETS save/load that queue plus those controls, so they come next. The
        // repeat/loop/pause bar configures the whole SCRIPT, so it anchors the bottom.
        south.add(southButtons, BorderLayout.NORTH);
        south.add(createPresetControlPanel(), BorderLayout.CENTER);
        south.add(createLoopBar(), BorderLayout.SOUTH);

        // add all panels to the main panel (task list panel)
        lblTaskCount = pill("0 tasks");
        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        headerRight.setOpaque(false);
        headerRight.add(lblTaskCount);
        panelTaskList.add(createTabHeader("Task queue", headerRight), BorderLayout.NORTH);
        panelTaskList.add(west, BorderLayout.WEST);
        JScrollPane taskScroll = Theme.thinScrollbars(new JScrollPane(listTaskList));
        taskScroll.setBorder(null);
        taskScroll.getViewport().setBackground(Theme.SURFACE_1);
        panelTaskList.add(taskScroll, BorderLayout.CENTER);
        panelTaskList.add(south, BorderLayout.SOUTH);

        // add listener to scan for nearby targets and select the first item if none selected on show
        panelTaskList.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                super.componentShown(e);
                refreshTaskListTab();
                refreshPresetButtonLabels();
                refreshDynamicControls();
            }
            /** v1.88: leaving the tab settles any preset slot that was reset and left empty. */
            @Override
            public void componentHidden(ComponentEvent e) {
                super.componentHidden(e);
                if (provisionalPresetIndex >= 0) {
                    selectedPresetIndex = -1;
                    compactPresets();
                }
            }
        });

        return panelTaskList;
    }

    private JPanel createTaskLibraryTab() {
        // 1. Changed to BorderLayout to allow title to span the top
        JPanel panelLibraryTab = new JPanel(new BorderLayout(10, 10));
        panelLibraryTab.setBorder(new EmptyBorder(15, 15, 15, 15));
        panelLibraryTab.setBackground(BG_BASE);

        // create navigation buttons (up/down arrows)
        JButton btnUpList = createNavButton("▲", modelTaskLibrary,listTaskLibrary);
        JButton btnDownList = createNavButton("▼", modelTaskLibrary,listTaskLibrary);

        JPanel panelNavButtons = new JPanel(new GridLayout(0, 1, 0, 5));
        panelNavButtons.setOpaque(false);
        panelNavButtons.add(btnUpList);
        panelNavButtons.add(btnDownList);

        // 3. Create a wrapper for the center content (List + Editor)
        JPanel centerContent = new JPanel(new GridLayout(1, 2, 10, 0));
        centerContent.setOpaque(false);

        /// CENTER WEST: Library list - card-styled like the Task List (Patch B.3), with a
        /// sort selector: alphabetical, newest first, or most used (TaskRef references across
        /// the queue, other library tasks and presets - recomputed live).
        styleJList(listTaskLibrary);
        libraryCardRenderer = new LibraryCardRenderer();
        listTaskLibrary.setCellRenderer(libraryCardRenderer);
        // v1.30: admin star clicks toggle default-task status; laid-out hit test so the
        // clickable zone is exactly the drawn star.
        listTaskLibrary.addMouseListener(new MouseAdapter() {
            /** v1.62: like the Task List, the context menu waits ~280ms so a double-RIGHT can win. */
            private javax.swing.Timer popupTimer;

            @Override public void mouseClicked(MouseEvent me) {
                int idx = listTaskLibrary.locationToIndex(me.getPoint());
                if (idx < 0) return;
                Rectangle b = listTaskLibrary.getCellBounds(idx, idx);
                if (b == null || !b.contains(me.getPoint())) return;
                Task t = modelTaskLibrary.getElementAt(idx);
                if (t == null) return;

                // v1.32: double-click a LIBRARY task -> add it to the queue and jump to it there
                // (previously it opened the builder; the builder is now the TASK LIST's double-
                // click). A queue entry is a deep copy so editing/running it never mutates the
                // library original.
                if (SwingUtilities.isLeftMouseButton(me) && me.getClickCount() == 2) {
                    int pos = insertIntoQueue(t);
                    selectMainTab("Task List");
                    listTaskList.setSelectedIndex(pos);
                    listTaskList.ensureIndexIsVisible(pos);
                    showToast("Added \"" + t.getName() + "\" at position " + (pos + 1)
                            + " of the queue", listTaskList, true);
                    return;
                }

                // v1.62: double-RIGHT-click opens the task in the builder for editing (the
                // shortcut kept from the old behaviour). The single-right context menu below is
                // held back briefly so this can land first.
                if (SwingUtilities.isRightMouseButton(me) && me.getClickCount() == 2) {
                    if (popupTimer != null) popupTimer.stop();
                    listTaskLibrary.setSelectedIndex(idx);
                    loadIntoBuilder(t);
                    mainTabs.setSelectedIndex(2);
                    return;
                }

                // admins: single-click the star to toggle a default task
                if (!SwingUtilities.isLeftMouseButton(me)) return;
                if (!main.market.Tier.isAdmin()) return;
                libraryCardRenderer.getListCellRendererComponent(listTaskLibrary, t, idx, false, false);
                libraryCardRenderer.setBounds(0, 0, b.width, b.height);
                libraryCardRenderer.doLayout();
                Component deep = SwingUtilities.getDeepestComponentAt(
                        libraryCardRenderer, me.getX() - b.x, me.getY() - b.y);
                if (deep != libraryCardRenderer.star) return;
                boolean now = main.data.store.DefaultTasks.toggle(t);
                listTaskLibrary.repaint();
                showToast(now ? "\"" + t.getName() + "\" is now a DEFAULT task"
                              : "\"" + t.getName() + "\" removed from defaults", listTaskLibrary, true);
            }

            @Override public void mousePressed(MouseEvent me)  { maybePopup(me); }
            @Override public void mouseReleased(MouseEvent me) { maybePopup(me); }
            private void maybePopup(MouseEvent me) {
                if (!me.isPopupTrigger() || me.getClickCount() != 1) return;
                int idx = listTaskLibrary.locationToIndex(me.getPoint());
                if (idx < 0) return;
                Rectangle b = listTaskLibrary.getCellBounds(idx, idx);
                if (b == null || !b.contains(me.getPoint())) return;
                listTaskLibrary.setSelectedIndex(idx);
                final int px = me.getX(), py = me.getY();
                if (popupTimer != null) popupTimer.stop();
                popupTimer = new javax.swing.Timer(280, ev -> showLibraryContextMenu(idx, px, py));
                popupTimer.setRepeats(false);
                popupTimer.start();
            }
        });
        listTaskLibrary.setFixedCellHeight(-1);

        librarySortCombo = new JComboBox<>(new String[]{"A-Z", "Newest", "Most used"});
        librarySortCombo.setToolTipText("Sort the library");
        librarySortCombo.addActionListener(e -> refilterLibrary());

        // Patch B.5: search + origin filter. Both only change the VIEW - hidden tasks remain in
        // the master list, so saving/exporting while filtered never loses anything.
        librarySearchField = new JTextField();
        librarySearchField.setToolTipText("Search by name or description");
        librarySearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { refilterLibrary(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { refilterLibrary(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { refilterLibrary(); }
        });
        libraryFilterCombo = new JComboBox<>(new String[]{
                "All", "Local", "Exported", "Market-Ready", "Published", "Downloaded"});
        libraryFilterCombo.setToolTipText("Filter by where tasks came from");
        libraryFilterCombo.addActionListener(e -> refilterLibrary());

        // Patch B.15: explicit VIP filter. Ticked by default for non-VIP (so free users don't see
        // tasks they can't run), but it's a control they can untick to browse what VIP offers.
        libraryHideVipCheck = new JCheckBox("Hide VIP tasks", !main.market.Tier.isVip());
        libraryHideVipCheck.setOpaque(false);
        libraryHideVipCheck.setForeground(new Color(230, 190, 90));
        libraryHideVipCheck.setToolTipText("Hide VIP-only tasks from the list. The actual VIP task "
                + "data is only sent to VIP accounts regardless of this toggle.");
        libraryHideVipCheck.addActionListener(e -> refilterLibrary());

        JPanel listSide = new JPanel(new BorderLayout(0, 6));
        listSide.setOpaque(false);
        JPanel header = new JPanel(new BorderLayout(6, 4));
        header.setOpaque(false);
        JLabel lblSearch = new JLabel(main.menu.components.UIIcons.search(16, TEXT_DIM));
        lblSearch.setBorder(new EmptyBorder(0, 2, 0, 2));   // B.17: drawn icon, not the emoji
        JPanel searchRow = new JPanel(new BorderLayout(4, 0));
        searchRow.setOpaque(false);
        searchRow.add(lblSearch, BorderLayout.WEST);
        searchRow.add(librarySearchField, BorderLayout.CENTER);
        // v1.49: sort as single-select toggle buttons (only one active) instead of a dropdown.
        // The hidden librarySortCombo stays as the state the filter reads; the buttons drive it.
        JPanel sortBtns = new JPanel(new GridLayout(1, 3, 3, 0));
        sortBtns.setOpaque(false);
        String[] sorts = {"A-Z", "Newest", "Most used"};
        ButtonGroup sortGroup = new ButtonGroup();
        for (String s : sorts) {
            JToggleButton b = new JToggleButton(s);
            b.setFocusPainted(false);
            b.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            b.setToolTipText("Sort by " + s);
            b.setSelected("A-Z".equals(s));
            b.addActionListener(e -> librarySortCombo.setSelectedItem(s));   // fires refilterLibrary
            sortGroup.add(b);
            sortBtns.add(b);
        }

        JPanel comboRow = new JPanel(new GridLayout(1, 2, 6, 0));
        comboRow.setOpaque(false);
        comboRow.add(libraryFilterCombo);
        comboRow.add(sortBtns);
        JPanel filterRow = new JPanel(new BorderLayout());
        filterRow.setOpaque(false);
        filterRow.add(comboRow, BorderLayout.CENTER);
        filterRow.add(libraryHideVipCheck, BorderLayout.EAST);
        header.add(searchRow, BorderLayout.NORTH);
        header.add(filterRow, BorderLayout.SOUTH);
        listSide.add(header, BorderLayout.NORTH);

        /// CENTER EAST: Edit panel + buttons
        JPanel panelCenterEastLibraryTab = new JPanel(new BorderLayout(0, 10));
        JPanel btnSection = new JPanel(new GridLayout(0, 3, 5, 5)); // rows grow as needed
        panelCenterEastLibraryTab.setOpaque(false);
        btnSection.setOpaque(false);

        // v1.62: the Task Library "Save" button is gone too - library add / delete / duplicate /
        // tag edits all autosave on change (via libraryAdd/Remove/propagate + the tag editor).

        // v1.62: the library's "Add" button is gone - adding a library task to the current list
        // now happens by double-clicking it (below) or from the Task List's "+" picker. Its
        // deep-copy-into-queue behaviour lives in insertIntoQueue, shared by both those paths.

        // v1.62: the "Insert: End/After" text dropdown moved to the Task List panel as a single
        // insert-mode toggle icon button. The shared insertAfterSelected flag still drives adds;
        // this button is gone from the Library.

        ///  Create Task Library delete button
        JButton btnTaskLibraryDelete = createButton("Delete", COLOR_FAILURE, null);
        btnTaskLibraryDelete.setEnabled(listTaskLibrary.getSelectedIndex() != -1);
        btnTaskLibraryDelete.addActionListener(e -> {
            int selectedIndex = listTaskLibrary.getSelectedIndex();
            if (selectedIndex != -1) {
                libraryRemove(modelTaskLibrary.getElementAt(selectedIndex));
                this.showToast("Deleted Task!", btnTaskLibraryDelete, true);
            } else {
                this.showToast("Select an Action to delete!", btnTaskLibraryDelete, false);
            }


            refreshTaskLibrary();
        });

        ///  Patch B.3: whole-library export (double-click now covers "view in builder")
        JButton btnTaskLibraryEdit = createButton("Export all...");
        btnTaskLibraryEdit.addActionListener(e -> exportWholeLibrary(btnTaskLibraryEdit));

        listTaskLibrary.addListSelectionListener(e -> {
            btnTaskLibraryDelete.setEnabled(!listTaskLibrary.isSelectionEmpty());
            populateInspector(listTaskLibrary.getSelectedValue());
        });

        // v1.33: (removed) a second double-click listener used to ALSO open the builder, so a
        // double-click both added to the queue AND edited. Double-click now only adds to the
        // queue (handled above); editing is the Edit button.

        ///  Add all buttons
        ///  Export the selected library task to a shareable .json file
        JButton btnTaskLibraryExport = createButton("Export...");
        btnTaskLibraryExport.addActionListener(e -> exportSelectedTask(btnTaskLibraryExport));

        ///  Import a task from a .json file into the library
        JButton btnTaskLibraryImport = createButton("Import...");
        btnTaskLibraryImport.addActionListener(e -> importTaskFromFile(btnTaskLibraryImport));

        // v1.33: stage the selected library task to the LOCAL market (market-ready), without
        // removing it from the library. From there it shows in the market-ready row at the
        // bottom of the Market tab (v1.60) and can be uploaded to the server.
        // v1.65: the "\u2191 Market-ready" button is gone - staging is now the inspector's inline
        // three-state selector (see buildLibraryInspector). The button could only ever stage, so
        // unstaging meant going to the Market tab, and nothing showed the current kind.

        // v1.62: "Duplicate" - deep-copy the selected library task under an incremented name, so
        // you can extend a task without rebuilding it. Shared with the right-click context menu.
        JButton btnTaskLibraryDuplicate = createButton("Duplicate", COLOR_LIGHT_GREEN, null);
        btnTaskLibraryDuplicate.setToolTipText("Deep-copy the selected task under a new name");
        btnTaskLibraryDuplicate.addActionListener(e ->
                duplicateLibraryTask(listTaskLibrary.getSelectedValue(), btnTaskLibraryDuplicate));

        btnSection.add(btnTaskLibraryDuplicate);
        // v1.80: the task's DEFAULT triggers - new copies inherit these, and existing copies
        // are updated with the user's consent (see propagateTaskTriggers).
        JButton btnLibTriggers = createButton("Triggers\u2026", new Color(60, 40, 75), null);
        btnLibTriggers.setToolTipText("<html>Triggers that run whenever this task executes."
                + "<br>This is the <b>default</b> \u2014 queue entries inherit it.</html>");
        btnLibTriggers.addActionListener(e ->
                openLibraryTaskTriggers(listTaskLibrary.getSelectedValue(), btnLibTriggers));
        btnSection.add(btnLibTriggers);
        btnSection.add(btnTaskLibraryDelete);
        btnSection.add(btnTaskLibraryEdit);
        btnSection.add(btnTaskLibraryExport);
        btnSection.add(btnTaskLibraryImport);
        // v1.62: the "Tags…" button moved to the library's right-click context menu (and, later,
        // a Task Builder step). editLibraryTags holds the logic both entry points call.

        panelCenterEastLibraryTab.add(buildLibraryInspector(), BorderLayout.CENTER);

        // 4. Add both sections to the center wrapper
        listSide.add(Theme.thinScrollbars(new JScrollPane(listTaskLibrary)), BorderLayout.CENTER);
        centerContent.add(listSide);
        centerContent.add(panelCenterEastLibraryTab);

        panelLibraryTab.add(createSubtitle("Task Library"), BorderLayout.NORTH);
        panelLibraryTab.add(panelNavButtons, BorderLayout.WEST);
        panelLibraryTab.add(centerContent, BorderLayout.CENTER);
        panelLibraryTab.add(btnSection, BorderLayout.SOUTH);

        // add listener to scan for nearby targets and select the first item if none selected on show
        panelLibraryTab.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                refreshTaskLibrary();
            }
        });

        return panelLibraryTab;
    }

    private void refreshTaskListTab(int index) {
        if (lblTaskCount != null) {
            int n = modelTaskList.size();
            lblTaskCount.setText(n + (n == 1 ? " task" : " tasks"));
        }
        // ignore empty lists (but still refresh them in case of update after removal)
        if (!modelTaskList.isEmpty()) {
            // prefer the passed index, fall back to the current selection, resort to the first item
            // (the old ternary was inverted, discarding any explicitly-passed index, and the
            //  ensureIndexIsVisible call below targeted the BUILDER list instead of this one)
            if (index < 0)
                index = listTaskList.getSelectedIndex();
            if (index < 0)
                index = 0;
            // clamp to the bounds of the list model
            index = Math.min(index, modelTaskList.size() - 1);
            listTaskList.setSelectedIndex(index);
            listTaskList.ensureIndexIsVisible(index);
        }

        // refresh preset button labels
        refreshPresetButtonLabels();
    }

    private void refreshTaskListTab() {
        refreshTaskListTab(-1);
    }

    /**
     * v1.33: where library adds land in the queue - false = end, true = right after the currently
     * selected queue item. v1.62: toggled by the Task List's insert-mode icon button (the old
     * "Insert: End" text dropdown in the Library is gone).
     */
    private boolean insertAfterSelected = false;

    /** v1.62: the Task List's insert-mode toggle - swaps its own icon/tooltip between the modes. */
    private JButton btnInsertToggle;

    /** v1.62: flip end <-> after-selected, repainting the single toggle button to match. */
    private void toggleInsertMode() {
        insertAfterSelected = !insertAfterSelected;
        if (btnInsertToggle != null) {
            btnInsertToggle.setIcon(insertAfterSelected
                    ? main.menu.components.UIIcons.insertAfter(18, Theme.ACCENT)
                    : main.menu.components.UIIcons.insertEnd(18, Theme.ACCENT));
            btnInsertToggle.setToolTipText(insertAfterSelected
                    ? "Adding AFTER the selected task \u2014 click for: at the end"
                    : "Adding at the END \u2014 click for: after the selected task");
        }
        showToast(insertAfterSelected
                ? "Library adds now land AFTER the selected task"
                : "Library adds now land at the END of the list", btnInsertToggle, true);
    }

    /** Deep-copies {@code t} into the queue at the chosen position; returns the landed index. */
    private int insertIntoQueue(Task t) {
        Task copy = new Task(t);
        int pos;
        if (insertAfterSelected) {
            int sel = listTaskList.getSelectedIndex();
            pos = (sel >= 0 && sel < modelTaskList.size()) ? sel + 1 : modelTaskList.size();
        } else {
            pos = modelTaskList.size();
        }
        modelTaskList.add(pos, copy);
        return pos;
    }

    /**
     * v1.33: a sleek pop-up for fast queue-building - a search box over the whole library where
     * double-clicking a task adds it to the queue WITHOUT closing, so you can rattle off a preset
     * quickly. A live label shows the queue size and how many you've added this session.
     */
    /**
     * v1.62: the Task List's "+" popup - the single place to build the current list. It carries
     * forward the old Quick-add search-and-double-click-to-add flow (the window stays open so you
     * can rattle off several), and adds the two things that let the standalone Quick-add button
     * AND the Library's own "add" button retire: it can also <b>append a saved preset</b> into the
     * current list, or <b>start a new preset in a fresh slot</b> by naming it. Adds respect the
     * Task List's insert-mode toggle (end vs after-selected).
     */
    private void openLibraryQuickAdd() {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this),
                "Add to Task List", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setAlwaysOnTop(true);

        JTextField search = new JTextField();
        DefaultListModel<Task> qModel = new DefaultListModel<>();
        JList<Task> qList = new JList<>(qModel);
        qList.setCellRenderer(new LibraryCardRenderer());
        qList.setFixedCellHeight(-1);

        JLabel count = new JLabel();
        count.setForeground(Theme.ACCENT);
        count.setBorder(new EmptyBorder(6, 4, 6, 4));
        final int[] added = {0};
        Runnable updateCount = () -> count.setText("Task List: " + modelTaskList.size()
                + " tasks   \u00b7   added this session: " + added[0]
                + "   \u00b7   adding at: " + (insertAfterSelected ? "after selected" : "end"));
        updateCount.run();

        Runnable refill = () -> {
            String q = search.getText().trim().toLowerCase();
            qModel.clear();
            for (Task t : libraryAll) {
                if (t == null) continue;
                if (t.isVipOnly() && libraryHideVipCheck != null && libraryHideVipCheck.isSelected())
                    continue;
                if (!q.isEmpty()) {
                    String hay = (t.getName() + " "
                            + (t.getDescription() == null ? "" : t.getDescription())).toLowerCase();
                    if (!hay.contains(q)) continue;
                }
                qModel.addElement(t);
            }
        };
        refill.run();
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { refill.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { refill.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { refill.run(); }
        });

        qList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent me) {
                if (me.getClickCount() != 2) return;
                Task t = qList.getSelectedValue();
                if (t == null) return;
                int pos = insertIntoQueue(t);
                added[0]++;
                updateCount.run();
                showToast("Added \"" + t.getName() + "\" (pos " + (pos + 1) + ")", count, true);
            }
        });

        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.setOpaque(false);
        top.setBorder(new EmptyBorder(8, 8, 4, 8));
        top.add(new JLabel("Search:"), BorderLayout.WEST);
        top.add(search, BorderLayout.CENTER);

        // v1.62: preset controls - append a saved preset into the current list, or spin the
        // current list out into a brand-new preset slot. This is what replaces the Quick-add menu.
        JPanel presetRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        presetRow.setOpaque(false);
        presetRow.setBorder(new EmptyBorder(0, 8, 2, 8));
        JLabel presetLbl = new JLabel("Presets:");
        presetLbl.setForeground(Theme.TEXT_DIM);
        JComboBox<String> cmbPreset = new JComboBox<>();
        Runnable fillPresets = () -> {
            cmbPreset.removeAllItems();
            for (int i = 0; i < modelPresets.size(); i++) {
                Preset p = modelPresets.get(i);
                if (p != null && p.tasks != null && !p.tasks.isEmpty())
                    cmbPreset.addItem((i + 1) + ": " + p.name + "  (" + p.tasks.size() + ")");
            }
        };
        fillPresets.run();
        JButton btnAppendPreset = createButton("Append", new Color(35, 55, 70), null);
        btnAppendPreset.setToolTipText("Add every task from the chosen preset onto the current list");
        btnAppendPreset.addActionListener(e -> {
            int sel = cmbPreset.getSelectedIndex();
            if (sel < 0) { showToast("No saved presets to append", btnAppendPreset, false); return; }
            // map the visible (non-empty) index back to the real preset index
            int realIdx = -1, seen = -1;
            for (int i = 0; i < modelPresets.size(); i++) {
                Preset p = modelPresets.get(i);
                if (p != null && p.tasks != null && !p.tasks.isEmpty()) {
                    seen++;
                    if (seen == sel) { realIdx = i; break; }
                }
            }
            if (realIdx < 0) return;
            Preset p = modelPresets.get(realIdx);
            int n = 0;
            for (Task t : p.tasks) { modelTaskList.addElement(new Task(t)); n++; }
            added[0] += n;
            updateCount.run();
            refreshTaskListTab();
            showToast("Appended " + n + " task" + (n == 1 ? "" : "s") + " from " + p.name,
                    btnAppendPreset, true);
        });
        JButton btnNewPreset = createButton("New slot\u2026", new Color(45, 55, 45), null);
        btnNewPreset.setToolTipText("Save the current Task List as a new preset in the next free slot");
        btnNewPreset.addActionListener(e -> {
            if (modelTaskList.isEmpty()) {
                showToast("The Task List is empty - nothing to save as a preset", btnNewPreset, false);
                return;
            }
            String name = JOptionPane.showInputDialog(dlg, "Name the new preset:",
                    "New preset", JOptionPane.PLAIN_MESSAGE);
            if (name == null || name.trim().isEmpty()) return;
            List<Task> tasks = new ArrayList<>();
            for (int i = 0; i < modelTaskList.size(); i++)
                tasks.add(new Task(modelTaskList.getElementAt(i)));
            Preset np = new Preset(name.trim(), tasks, queueLoopTarget);
            np.setScriptTriggers(currentScriptTriggersJson());   // v1.64
            modelPresets.addElement(np);
            selectedPresetIndex = modelPresets.size() - 1;
            saveAll(false);
            refreshPresetButtonLabels();
            fillPresets.run();
            showToast("Saved \"" + name.trim() + "\" as preset " + modelPresets.size(),
                    btnNewPreset, true);
        });
        presetRow.add(presetLbl);
        presetRow.add(cmbPreset);
        presetRow.add(btnAppendPreset);
        presetRow.add(btnNewPreset);

        JButton close = createButton("Done", new Color(30, 60, 40), null);
        close.addActionListener(e -> dlg.dispose());
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.setBorder(new EmptyBorder(4, 8, 8, 8));
        bottom.add(count, BorderLayout.WEST);
        JPanel closeWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        closeWrap.setOpaque(false);
        closeWrap.add(close);
        bottom.add(closeWrap, BorderLayout.EAST);

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(presetRow, BorderLayout.NORTH);
        south.add(bottom, BorderLayout.CENTER);
        JLabel hint = new JLabel("  Double-click a task to add it - the window stays open so you can add several.");
        hint.setForeground(Theme.TEXT_DIM);
        hint.setBorder(new EmptyBorder(0, 8, 6, 8));
        south.add(hint, BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_BASE);
        root.add(top, BorderLayout.NORTH);
        root.add(Theme.thinScrollbars(new JScrollPane(qList)), BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);

        dlg.setContentPane(root);
        dlg.setSize(560, 600);
        dlg.setLocationRelativeTo(this);
        dlg.toFront();
        SwingUtilities.invokeLater(search::requestFocusInWindow);
        dlg.setVisible(true);
        refreshTaskListTab();
    }

    private void refreshTaskLibrary() {
        // Patch B.3: keep usage counts, the dropdown's library entries and the inspector live
        computeLibraryUseCounts();
        // Patch B.3: keep usage counts, the dropdown's library entries and the inspector live
        computeLibraryUseCounts();
        syncSelectorLibraryEntries();
        if (listTaskLibrary.getSelectedValue() == null && !modelTaskLibrary.isEmpty())
            listTaskLibrary.setSelectedIndex(modelTaskLibrary.getSize() - 1);
        populateInspector(listTaskLibrary.getSelectedValue());
        listTaskLibrary.repaint();

        // scan for nearby targets to update the nearby targets list
        //scanNearbyTargets();
    }

    private void refreshTaskBuilderTab(boolean forceSelectLast) {
        // if the list is empty or an invalid item is selected OR if force selecting the last item...
        if (listTaskBuilder.getSelectedValue() == null && !modelTaskBuilder.isEmpty() || forceSelectLast)
            // temp fix the index error by setting the index to the last item in the list (likely most relevant)
            listTaskBuilder.setSelectedIndex(modelTaskBuilder.getSize() - 1);
        listTaskBuilder.repaint();
    }

    private void refreshTaskBuilderTab() {
        refreshTaskBuilderTab(false);
    }

    private void refreshSkillTrackerTab(JPanel gridSkills) {
        for (Skill skill : OSRS_ORDER) {
            SkillData data = new SkillData(skill);
            skillRegistry.put(skill, data);
            gridSkills.add(createSkillTile(data));
        }
    }

    public void removeTask(JList<Task> list, DefaultListModel<Task> model, JButton btn) {
        try {
            int selectedIndex = list.getSelectedIndex();
            if (selectedIndex != -1) {
                model.remove(selectedIndex);
                list.ensureIndexIsVisible(selectedIndex);
                showToast("Removed task!", btn, true);
            } else {
                showToast("Select a Task to remove!", btn, false);
            }

            // keep the selection near where the user was working (not jumping to the end)
            if (!model.isEmpty())
                list.setSelectedIndex(Math.min(selectedIndex, model.getSize() - 1));

        } catch (Exception e) {
            // refresh all lists to be safe
            //TODO override list and perform these functions on the list themselves?
            refreshTaskListTab();
            refreshTaskLibrary();
            refreshTaskBuilderTab();
        }
    }

    // (Removed dead createTask(List<Action>) helper: it read taskNameInput/taskDescriptionInput/
    //  taskStatusInput, which are declared but never initialised in this class -> guaranteed NPE.
    //  Task creation lives in TaskBuilder, which owns the real input fields.)

    /** Exports the currently-selected library task to a user-chosen .json file. */
    /**
     * Card-styled renderer for the Task Library (Patch B.3) - same visual language as the Task
     * List: bold name + action-chain preview, with usage count and creation date on the right.
     */
    private class LibraryCardRenderer extends JPanel implements ListCellRenderer<Task> {
        private final JLabel name = new JLabel();
        private final JLabel chain = new JLabel();
        private final JLabel uses = new JLabel();
        private final JLabel date = new JLabel();
        /** v1.30: the default-task star. Gold = ships to every user. Admins click to toggle. */
        final JLabel star = new JLabel();

        LibraryCardRenderer() {
            setLayout(new BorderLayout(8, 0));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_BORDER_DIM),
                    new EmptyBorder(8, 10, 8, 10)));
            name.setFont(new Font("Segoe UI", Font.BOLD, 14));
            chain.setFont(new Font("Consolas", Font.PLAIN, 11));
            uses.setFont(new Font("Segoe UI", Font.BOLD, 12));
            date.setFont(new Font("Consolas", Font.PLAIN, 10));
            JPanel left = new JPanel(new GridLayout(2, 1));
            left.setOpaque(false);
            left.add(name);
            left.add(chain);
            JPanel right = new JPanel(new GridLayout(2, 1));
            right.setOpaque(false);
            uses.setHorizontalAlignment(SwingConstants.RIGHT);
            date.setHorizontalAlignment(SwingConstants.RIGHT);
            right.add(uses);
            right.add(date);
            star.setBorder(new EmptyBorder(0, 0, 0, 2));
            add(star, BorderLayout.WEST);
            add(left, BorderLayout.CENTER);
            add(right, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Task> list, Task t,
                int index, boolean selected, boolean focus) {
            if (t == null) return this;
            // v1.30: default-task marker. Everyone sees which tasks are defaults; only admins
            // get the hollow "make this a default" star on the rest (clicking it toggles).
            boolean isDefault = main.data.store.DefaultTasks.isDefault(t.getId());
            boolean admin = main.market.Tier.isAdmin();
            // v1.66: an admin-pushed default (origin "default-admin") is drawn in blue rather
            // than gold, so a user can tell a task an admin added from one that ships with the
            // client - and can filter on the origin if they care.
            boolean adminDefault = main.data.store.DefaultTasks.ADMIN_ORIGIN.equals(t.getOrigin());
            if (isDefault || adminDefault)
                star.setIcon(main.menu.components.UIIcons.star(15,
                        adminDefault ? Theme.BLUE : Theme.ACCENT, true));
            else if (admin)
                star.setIcon(main.menu.components.UIIcons.star(15, Theme.TEXT_MUTED, false));
            else
                star.setIcon(null);
            star.setToolTipText(adminDefault
                    ? "Default task - added by an admin, shared with every user."
                    : admin
                    ? (isDefault ? "Default task - ships to every user. Click to remove."
                                 : "Click to make this a default task for every user.")
                    : (isDefault ? "Default task - included with DreamMan." : null));
            // v1.49: show the version beside the name, and the strongest lifecycle tag on the date row
            name.setText(t.getName());
            StringBuilder sb = new StringBuilder();
            if (t.getActions() != null) {
                int shown = 0;
                for (Action a : t.getActions()) {
                    if (a == null) continue;
                    if (shown++ > 0) sb.append("  →  ");
                    sb.append(a.getName());
                    if (shown >= 5) { sb.append("  …"); break; }
                }
            }
            chain.setText(sb.length() == 0 ? "(no actions)" : sb.toString());
            int useCount = libraryUseCounts.getOrDefault(t.getId(), 0);
            uses.setText(useCount > 0 ? "×" + useCount + " uses" : " ");
            uses.setForeground(useCount > 0 ? Theme.ACCENT : TEXT_DIM);
            String tag = t.isPublished() ? "published"
                    : t.isMarketReady() ? "market-ready"
                    : t.isDownloaded() ? "downloaded"
                    : t.isExported() ? "exported" : null;
            String dateStr = t.getCreatedAt() > 0
                    ? new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date(t.getCreatedAt()))
                    : "";
            // v1.51: version + tags on the small line under the title. v1.62: cap the card to a
            // few tags (the full set lives in the inspector) so long tag lists don't blow out the
            // row - the first three, then a "+N" marker.
            java.util.List<String> bits = new java.util.ArrayList<>();
            bits.add("v" + String.format("%.1f", t.getVersion()));
            java.util.List<String> tg = t.getTags();
            if (!tg.isEmpty()) {
                if (tg.size() <= 3) bits.add(String.join(", ", tg));
                else bits.add(String.join(", ", tg.subList(0, 3)) + " +" + (tg.size() - 3));
            }
            if (tag != null) bits.add(tag);
            if (!dateStr.isEmpty()) bits.add(dateStr);
            date.setText(String.join("  ·  ", bits));
            date.setForeground(tag != null ? Theme.ACCENT : TEXT_DIM);
            setBackground(selected ? new Color(46, 42, 30) : Theme.SURFACE_1);
            name.setForeground(selected ? Theme.ACCENT : TEXT_MAIN);
            chain.setForeground(TEXT_DIM);
            setOpaque(true);
            return this;
        }
    }

    // ═══════════════ Patch B.3: Task Library inspector / sorting / sharing ═══════════════

    /**
     * The read-only inspector: everything there is to know about a task without being able to
     * touch it - name, description, status, creation date, how many scripts use it, the full
     * action chain, and per-action attributes including the humanised delay on either side.
     * This is where creators explain their work, not just store it. [Edit in builder] hands the
     * task to the builder ready to overwrite everywhere ("Save changes").
     */
    /**
     * v1.62: deep-copy a library task under an incremented, collision-free name and drop the copy
     * into the library. Everything else is carried over verbatim; the copy gets a fresh id so it's
     * its own logical task (editing it never rewrites the original). Shared by the Duplicate button
     * and the right-click menu.
     */
    private void duplicateLibraryTask(Task src, JComponent anchor) {
        if (src == null) { showToast("Select a library task first", anchor, false); return; }
        Task copy = new Task(src);              // deep copy (same ctor the queue-duplicate uses)
        copy.regenerateId();                    // its own identity, so edits don't bleed across
        copy.setName(uniqueLibraryName(src.getName()));   // "Name" -> "Name (2)" -> "Name (3)"...
        copy.setMarketReady(false);             // a fresh copy isn't staged/published/exported yet
        libraryAdd(copy);
        saveAll(false);
        refreshTaskLibrary();
        // land the selection on the new copy so it's obvious what happened
        listTaskLibrary.setSelectedValue(copy, true);
        showToast("Duplicated as \"" + copy.getName() + "\"", anchor, true);
    }

    /**
     * v1.62: edit a library task's tags (comma-separated). Extracted from the old "Tags…" button
     * so the right-click menu - and, later, a Task Builder step - can share one implementation.
     * Tags drive the library + market search filters.
     */
    private void editLibraryTags(Task sel, JComponent anchor) {
        if (sel == null) { showToast("Select a library task first", anchor, false); return; }
        String cur = String.join(", ", sel.getTags());
        String in = (String) JOptionPane.showInputDialog(this,
                "Tags for \"" + sel.getName() + "\" (comma-separated):",
                "Edit tags", JOptionPane.PLAIN_MESSAGE, null, null, cur);
        if (in == null) return;
        java.util.List<String> parsed = new ArrayList<>();
        for (String part : in.split(","))
            if (!part.trim().isEmpty()) parsed.add(part.trim());
        sel.setTags(parsed);
        saveAll(false);
        refilterLibrary();
        populateInspector(listTaskLibrary.getSelectedValue());   // reflect new tags immediately
        showToast(parsed.isEmpty() ? "Tags cleared" : "Tags: " + String.join(", ", parsed),
                anchor, true);
    }

    /** v1.62: the library list's right-click menu - edit, tag, duplicate, delete in one place. */
    private void showLibraryContextMenu(int index, int x, int y) {
        if (index < 0 || index >= modelTaskLibrary.getSize()) return;
        Task t = modelTaskLibrary.getElementAt(index);
        if (t == null) return;
        listTaskLibrary.setSelectedIndex(index);

        JPopupMenu menu = new JPopupMenu();
        JMenuItem miEdit = new JMenuItem("Edit in builder\u2026");
        miEdit.addActionListener(a -> { loadIntoBuilder(t); mainTabs.setSelectedIndex(2); });
        JMenuItem miTags = new JMenuItem(t.getTags().isEmpty()
                ? "Add tags\u2026" : "Edit tags\u2026");
        miTags.addActionListener(a -> editLibraryTags(t, listTaskLibrary));
        JMenuItem miDup = new JMenuItem("Duplicate");
        miDup.addActionListener(a -> duplicateLibraryTask(t, listTaskLibrary));
        JMenuItem miDelete = new JMenuItem("Delete");
        miDelete.addActionListener(a -> {
            libraryRemove(t);
            refreshTaskLibrary();
            showToast("Deleted \"" + t.getName() + "\"", listTaskLibrary, true);
        });
        menu.add(miEdit);
        menu.add(miTags);
        menu.add(miDup);
        menu.addSeparator();
        menu.add(miDelete);
        menu.show(listTaskLibrary, x, y);
    }

    private JPanel buildLibraryInspector() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setOpaque(false);

        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.setOpaque(false);
        inspName = new JLabel("Select a task");
        inspName.setForeground(Theme.ACCENT);
        inspName.setFont(new Font("Segoe UI", Font.BOLD, 18));
        inspMeta = new JLabel(" ");
        inspMeta.setForeground(TEXT_DIM);
        inspMeta.setFont(new Font("Consolas", Font.PLAIN, 11));
        inspStatus = new JLabel(" ");
        inspStatus.setForeground(TEXT_MAIN);
        inspStatus.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        // v1.62: the full tag list (the card only shows a few) - the "breakdown" tag view
        inspTags = new JLabel(" ");
        inspTags.setForeground(Theme.ACCENT);
        inspTags.setFont(new Font("Consolas", Font.PLAIN, 11));
        head.add(inspName);
        head.add(Box.createVerticalStrut(2));
        head.add(inspMeta);
        head.add(Box.createVerticalStrut(2));
        head.add(inspStatus);
        head.add(Box.createVerticalStrut(2));
        head.add(inspTags);

        // v1.65: the inline market-ready selector. It lives here rather than in the button row
        // because it reports STATE as much as it takes a command - the blurb underneath says
        // whether this task is local-only or staged, and what still has to happen to publish it.
        inspMarketCombo = new JComboBox<>(new String[]{MK_NOT, MK_TASK, MK_SCRIPT});
        inspMarketCombo.setEnabled(false);
        inspMarketCombo.setToolTipText("<html>Stage this task to your local market, or take it "
                + "back off.<br><b>Task</b> = a reusable building block (banking, a walk, a skill "
                + "loop).<br><b>Script</b> = a complete single-purpose routine."
                + "<br>It stays in your Task Library either way.</html>");
        inspMarketCombo.addActionListener(e -> onMarketSelectorChanged());

        inspMarketBlurb = new JLabel(" ");
        inspMarketBlurb.setForeground(TEXT_DIM);
        inspMarketBlurb.setFont(new Font("Segoe UI", Font.ITALIC, 11));

        JLabel lblMarket = new JLabel("Market:");
        lblMarket.setForeground(TEXT_DIM);
        lblMarket.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblMarket.setBorder(new EmptyBorder(0, 0, 0, 6));

        JPanel marketRow = new JPanel(new BorderLayout(0, 0));
        marketRow.setOpaque(false);
        marketRow.add(lblMarket, BorderLayout.WEST);
        marketRow.add(inspMarketCombo, BorderLayout.CENTER);

        JPanel marketBox = new JPanel(new BorderLayout(0, 3));
        marketBox.setOpaque(false);
        marketBox.setBorder(new EmptyBorder(6, 0, 0, 0));
        marketBox.add(marketRow, BorderLayout.NORTH);
        marketBox.add(inspMarketBlurb, BorderLayout.CENTER);

        inspDesc = new JTextArea(3, 20);
        inspDesc.setEditable(false);
        inspDesc.setLineWrap(true);
        inspDesc.setWrapStyleWord(true);
        inspDesc.setBackground(new Color(15, 15, 15));
        inspDesc.setForeground(TEXT_MAIN);
        inspDesc.setBorder(new EmptyBorder(6, 8, 6, 8));

        inspActionsList = new JList<>(inspActionsModel);
        styleJList(inspActionsList);
        inspActionsList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i,
                    boolean sel, boolean foc) {
                JLabel lab = (JLabel) super.getListCellRendererComponent(l, v, i, sel, foc);
                lab.setText("  " + (i + 1) + ".  " + (v instanceof Action ? ((Action) v).toBuildString() : "?"));
                lab.setBorder(new EmptyBorder(4, 4, 4, 4));
                return lab;
            }
        });
        inspActionsList.addListSelectionListener(e ->
                populateActionAttributes(listTaskLibrary.getSelectedValue(),
                        inspActionsList.getSelectedIndex()));

        inspAttrs = new JTextArea(7, 20);
        inspAttrs.setEditable(false);
        inspAttrs.setFont(new Font("Consolas", Font.PLAIN, 12));
        inspAttrs.setBackground(new Color(15, 15, 15));
        inspAttrs.setForeground(TEXT_MAIN);
        inspAttrs.setBorder(new EmptyBorder(6, 8, 6, 8));

        JButton btnEdit = createButton("Edit in builder...");
        btnEdit.addActionListener(e -> {
            Task t = listTaskLibrary.getSelectedValue();
            if (t == null) { showToast("Select a task first", btnEdit, false); return; }
            loadIntoBuilder(t);   // arrives editing: the builder's button reads "Save changes"
            mainTabs.setSelectedIndex(2);
        });

        JPanel mid = new JPanel(new GridLayout(2, 1, 0, 8));
        mid.setOpaque(false);
        JScrollPane actionsScroll = Theme.thinScrollbars(new JScrollPane(inspActionsList));
        actionsScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(COLOR_BORDER_DIM), " Actions (click one) "));
        JScrollPane attrsScroll = Theme.thinScrollbars(new JScrollPane(inspAttrs));
        attrsScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(COLOR_BORDER_DIM), " Attributes "));
        mid.add(actionsScroll);
        mid.add(attrsScroll);

        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.setOpaque(false);
        // v1.65: head + the market selector share the north slot. Nesting them in a BorderLayout
        // (rather than adding the selector into head's BoxLayout) keeps the selector at its
        // natural height and left-aligned, instead of stretching or centring.
        JPanel topNorth = new JPanel(new BorderLayout(0, 0));
        topNorth.setOpaque(false);
        topNorth.add(head, BorderLayout.NORTH);
        topNorth.add(marketBox, BorderLayout.CENTER);
        top.add(topNorth, BorderLayout.NORTH);
        JScrollPane descScroll = Theme.thinScrollbars(new JScrollPane(inspDesc));
        descScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(COLOR_BORDER_DIM), " Description "));
        descScroll.setPreferredSize(new Dimension(10, 84));
        top.add(descScroll, BorderLayout.CENTER);

        root.add(top, BorderLayout.NORTH);
        root.add(mid, BorderLayout.CENTER);
        root.add(btnEdit, BorderLayout.SOUTH);
        return root;
    }

    /** Fills the inspector for a task (null clears it). */
    private void populateInspector(Task t) {
        inspActionsModel.clear();
        if (t == null) {
            if (inspName != null) inspName.setText("Select a task");
            if (inspMeta != null) inspMeta.setText(" ");
            if (inspStatus != null) inspStatus.setText(" ");
            if (inspTags != null) inspTags.setText(" ");
            if (inspDesc != null) inspDesc.setText("");
            if (inspAttrs != null) inspAttrs.setText("");
            syncMarketSelector(null);   // v1.65
            return;
        }
        inspName.setText(t.getName());
        int uses = libraryUseCounts.getOrDefault(t.getId(), 0);
        String created = t.getCreatedAt() > 0
                ? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(t.getCreatedAt()))
                : "\u2014";
        inspMeta.setText("created " + created + "   \u00b7   used \u00d7" + uses
                + "   \u00b7   " + (t.getActions() == null ? 0 : t.getActions().size()) + " action(s)"
                + "   \u00b7   repeat \u00d7" + Math.max(1, t.getRepeat()));
        inspStatus.setText("Status while running: \"" + t.getStatus() + "\"");
        if (inspTags != null)
            inspTags.setText(t.getTags().isEmpty() ? "Tags: (none)"
                    : "Tags: " + String.join(", ", t.getTags()));
        inspDesc.setText(t.getDescription() == null ? "" : t.getDescription());
        inspDesc.setCaretPosition(0);
        if (t.getActions() != null)
            for (Action a : t.getActions())
                if (a != null) inspActionsModel.addElement(a);
        inspAttrs.setText("Click an action above to see its attributes.");
        syncMarketSelector(t);   // v1.65
    }

    // ── v1.65: market-ready selector ─────────────────────────────────────────────────────────
    // The staged listing is the source of truth, not Task.marketReady: the staging card can be
    // deleted from the Market tab's strip, which would otherwise leave the flag lying about.

    /**
     * The local staging listing for a library task, or null if it isn't staged. Matched on the
     * bundle's task id (ProfileCodec carries it through), falling back to an exact name match so
     * listings staged before ids travelled still resolve.
     */
    private main.market.ScriptListing findLocalListingFor(Task t) {
        if (t == null || localMarketRepo == null) return null;
        main.market.ScriptListing byName = null;
        try {
            for (main.market.ScriptListing l : localMarketRepo.list()) {
                if (l == null) continue;
                if (l.bundle != null && l.bundle.tasks != null)
                    for (main.data.store.TaskData d : l.bundle.tasks)
                        if (d != null && d.id != null && d.id.equals(t.getId())) return l;
                if (byName == null && l.name != null && l.name.equalsIgnoreCase(t.getName()))
                    byName = l;
            }
        } catch (Exception ignored) {
            // an unreadable staging folder just means "not staged" for display purposes
        }
        return byName;
    }

    /** The one-line explanation under the selector. */
    private String marketBlurbFor(main.market.ScriptListing l) {
        if (l == null)
            return "Local only \u2014 not staged to your market.";
        String kind = "script".equalsIgnoreCase(l.kind) ? "script" : "task";
        return l.cardReady
                ? "Staged as a " + kind + " \u2014 card built, ready to publish."
                : "Staged as a " + kind + " \u2014 build its card to publish.";
    }

    /** Points the selector + blurb at a task's real state without firing the change handler. */
    private void syncMarketSelector(Task t) {
        if (inspMarketCombo == null) return;
        inspMarketSyncing = true;
        try {
            if (t == null) {
                inspMarketCombo.setSelectedItem(MK_NOT);
                inspMarketCombo.setEnabled(false);
                if (inspMarketBlurb != null) inspMarketBlurb.setText(" ");
                return;
            }
            inspMarketCombo.setEnabled(true);
            main.market.ScriptListing l = findLocalListingFor(t);
            boolean staged = l != null;
            inspMarketCombo.setSelectedItem(!staged ? MK_NOT
                    : ("script".equalsIgnoreCase(l.kind) ? MK_SCRIPT : MK_TASK));
            // heal a flag left behind by a staging copy deleted from the Market strip. In memory
            // only - the next real save persists it, so browsing the library never writes to disk.
            if (t.isMarketReady() != staged) t.setMarketReady(staged);
            if (inspMarketBlurb != null) {
                inspMarketBlurb.setText(marketBlurbFor(l));
                inspMarketBlurb.setForeground(staged ? Theme.GREEN : TEXT_DIM);
            }
        } finally {
            inspMarketSyncing = false;
        }
    }

    /** Applies whatever the user just picked in the selector. */
    private void onMarketSelectorChanged() {
        if (inspMarketSyncing) return;
        Task t = listTaskLibrary == null ? null : listTaskLibrary.getSelectedValue();
        if (t == null) { syncMarketSelector(null); return; }

        Object sel = inspMarketCombo.getSelectedItem();
        String want = MK_SCRIPT.equals(sel) ? "script" : MK_TASK.equals(sel) ? "task" : null;
        main.market.ScriptListing cur = findLocalListingFor(t);
        String curKind = cur == null ? null
                : ("script".equalsIgnoreCase(cur.kind) ? "script" : "task");

        if (want == null) {
            if (cur != null) unstageMarketReady(t, cur);
        } else if (cur == null) {
            makeMarketReady(t, want, inspMarketCombo);
        } else if (!want.equals(curKind)) {
            restageMarketKind(t, cur, want);
        }
        syncMarketSelector(t);   // snap back if the user cancelled a confirm
    }

    /** Takes a task back off the local market (the task itself stays in the library). */
    private void unstageMarketReady(Task t, main.market.ScriptListing l) {
        if (JOptionPane.showConfirmDialog(this,
                "<html>Take <b>" + escapeHtml(t.getName()) + "</b> off your local market?"
                + "<br><br>The task stays in your Task Library \u2014 only the staged market copy"
                + "<br>and its card are deleted.</html>",
                "Not market-ready", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                != JOptionPane.YES_OPTION)
            return;
        try {
            localMarketRepo.remove(l.id);
            t.setMarketReady(false);
            saveAll(false);
            refilterLibrary();
            reloadMarket();
            showToast("\"" + t.getName() + "\" is no longer market-ready", inspMarketCombo, true);
        } catch (Exception ex) {
            showToast("Couldn't unstage: " + ex.getMessage(), inspMarketCombo, false);
        }
    }

    /** Flips a staged listing between "task" and "script" in place, keeping its card and icon. */
    private void restageMarketKind(Task t, main.market.ScriptListing l, String kind) {
        String was = l.kind;
        try {
            l.kind = kind;
            localMarketRepo.publish(l);   // same id = save-in-place, so the card survives
            saveAll(false);
            refilterLibrary();
            reloadMarket();
            showToast("\"" + t.getName() + "\" is now staged as a " + kind, inspMarketCombo, true);
        } catch (Exception ex) {
            l.kind = was;
            showToast("Couldn't change kind: " + ex.getMessage(), inspMarketCombo, false);
        }
    }

    /** Shows one action's attributes + the humanised delay on either side of it. */
    private void populateActionAttributes(Task t, int idx) {
        if (t == null || idx < 0 || t.getActions() == null || idx >= t.getActions().size()) return;
        Action a = t.getActions().get(idx);
        if (a == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append(a.getName()).append("\n");
        sb.append("\u2500".repeat(30)).append("\n");
        Map<String, String> attrs = a.serialize();
        if (attrs != null)
            for (Map.Entry<String, String> e : attrs.entrySet())
                sb.append(String.format("%-12s %s%n", e.getKey() + ":", e.getValue()));

        sb.append("\n");
        sb.append(String.format("%-12s %s%n", "Delay before:", describeDelayNeighbor(t, idx - 1)));
        sb.append(String.format("%-12s %s%n", "Delay after:", describeDelayNeighbor(t, idx + 1)));
        if (Math.max(1, t.getRepeat()) > 1)
            sb.append("\n(This task repeats \u00d7").append(t.getRepeat()).append(" per queue pass.)");
        inspAttrs.setText(sb.toString());
        inspAttrs.setCaretPosition(0);
    }

    /** Humanises the Wait next to an action ("600-1400ms", "Quick", "AFK x2") or "none". */
    private String describeDelayNeighbor(Task t, int idx) {
        if (idx < 0 || t.getActions() == null || idx >= t.getActions().size()) return "none";
        Action n = t.getActions().get(idx);
        if (!(n instanceof main.actions.Wait)) return "none";
        Map<String, String> m = n.serialize();
        String mode = m == null ? null : m.get("Mode");
        if (mode == null || mode.equalsIgnoreCase("fixed"))
            return (m == null ? "?" : m.getOrDefault("Min", "?") + "-" + m.getOrDefault("Max", "?") + "ms");
        if (mode.equalsIgnoreCase("quick")) return "Quick (~60-220ms)";
        if (mode.equalsIgnoreCase("realistic")) return "Realistic (~0.3-3s)";
        if (mode.equalsIgnoreCase("afk")) return "Semi-AFK \u00d7" + m.getOrDefault("Afk", "1");
        return mode;
    }

    /** Recomputes how many times each library task is referenced by TaskRef actions anywhere. */
    private void computeLibraryUseCounts() {
        Map<String, Integer> byId = new HashMap<>();
        Map<String, String> nameToId = new HashMap<>();
        for (Task t : libraryAll)
            if (t != null) nameToId.put(t.getName().toLowerCase(), t.getId());
        List<Task> everywhere = new ArrayList<>();
        for (int i = 0; i < modelTaskList.size(); i++) everywhere.add(modelTaskList.get(i));
        everywhere.addAll(libraryAll);
        for (int i = 0; i < modelPresets.size(); i++) {
            Preset pr = modelPresets.get(i);
            if (pr != null && pr.tasks != null) everywhere.addAll(pr.tasks);
        }
        for (Task t : everywhere) {
            if (t == null || t.getActions() == null) continue;
            for (Action a : t.getActions()) {
                if (!(a instanceof main.actions.TaskRef)) continue;
                Map<String, String> m = a.serialize();
                String refId = m == null ? null : m.get("TaskId");
                if (refId == null && m != null && m.get("TaskName") != null)
                    refId = nameToId.get(m.get("TaskName").toLowerCase());
                if (refId != null)
                    byId.merge(refId, 1, Integer::sum);
            }
        }
        libraryUseCounts = byId;
    }

    /** Legacy alias - the combo/search/filter all funnel into {@link #refilterLibrary()}. */
    private void sortLibrary() { refilterLibrary(); }

    /**
     * Rebuilds the library VIEW from the master list (Patch B.5): applies the search text
     * (name/description contains), the origin filter (All / Built by me / Imported / Default),
     * then the chosen sort. Hidden tasks stay safely in {@link #libraryAll}.
     */
    private void refilterLibrary() {
        computeLibraryUseCounts();
        String mode = librarySortCombo == null ? "A-Z" : (String) librarySortCombo.getSelectedItem();
        String search = librarySearchField == null ? "" : librarySearchField.getText().trim().toLowerCase();
        String filter = libraryFilterCombo == null ? "All" : (String) libraryFilterCombo.getSelectedItem();

        List<Task> view = new ArrayList<>();
        for (Task t : libraryAll) {
            if (t == null) continue;
            if (!search.isEmpty()) {
                String hay = (t.getName() + " " + (t.getDescription() == null ? "" : t.getDescription())
                        + " " + String.join(" ", t.getTags())).toLowerCase();
                if (!hay.contains(search)) continue;
            }
            // Patch B.15: VIP-only tasks are filtered by an explicit checkbox now (not auto-
            // hidden). Default-ticked for non-VIP so free users don't see content they can't run,
            // but it's a control they can untick to browse. (The server still withholds the actual
            // VIP bundle data from non-VIP accounts, so this is purely a browsing convenience.)
            if (t.isVipOnly() && libraryHideVipCheck != null && libraryHideVipCheck.isSelected())
                continue;
            String origin = t.getOrigin();
            // v1.49 six-filter set
            if ("Local".equals(filter)
                    && !("user".equals(origin) || "default".equals(origin)) && !t.isMarketReady())
                continue;
            if ("Exported".equals(filter) && !t.isExported()) continue;
            if ("Market-Ready".equals(filter) && !t.isMarketReady()) continue;
            if ("Published".equals(filter) && !t.isPublished()) continue;
            if ("Downloaded".equals(filter)
                    && !(t.isDownloaded() || "imported".equals(origin))) continue;
            view.add(t);
        }

        Comparator<Task> cmp;
        if ("Newest".equals(mode))
            cmp = Comparator.comparingLong(Task::getCreatedAt).reversed();
        else if ("Most used".equals(mode))
            cmp = Comparator.<Task>comparingInt(
                    t -> libraryUseCounts.getOrDefault(t.getId(), 0)).reversed()
                    .thenComparing(Task::getName, String.CASE_INSENSITIVE_ORDER);
        else
            cmp = Comparator.comparing(Task::getName, String.CASE_INSENSITIVE_ORDER);
        view.sort(cmp);

        Task selected = listTaskLibrary.getSelectedValue();
        modelTaskLibrary.clear();
        for (Task t : view) modelTaskLibrary.addElement(t);
        if (selected != null) listTaskLibrary.setSelectedValue(selected, true);
        listTaskLibrary.repaint();
    }

    /** Exports the ENTIRE library to one shareable .json file (Patch B.3). */
    private void exportWholeLibrary(JComponent anchor) {
        if (libraryAll.isEmpty()) { showToast("Library is empty", anchor, false); return; }
        JFileChooser chooser = new JFileChooser(main.data.store.LocalStore.getExportsDir());
        chooser.setDialogTitle("Export whole library");
        chooser.setSelectedFile(new java.io.File("DreamMan_library.json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.io.File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".json"))
            file = new java.io.File(file.getParentFile(), file.getName() + ".json");

        LibraryData lib = new LibraryData();
        lib.exportedAt = System.currentTimeMillis();
        for (Task t : libraryAll)
            if (t != null) lib.tasks.add(ProfileCodec.toData(t));
        boolean ok = LocalStore.exportToFile(lib, file);
        showToast(ok ? "Exported " + lib.tasks.size() + " task(s)" : "Export failed", anchor, ok);
    }

    private void exportSelectedTask(JComponent anchor) {
        Task selected = listTaskLibrary.getSelectedValue();
        if (selected == null) {
            showToast("Select a task to export", anchor, false);
            return;
        }

        JFileChooser chooser = new JFileChooser(main.data.store.LocalStore.getExportsDir());
        chooser.setDialogTitle("Export task");
        String suggested = LocalStore.sanitize(selected.getName()) + ".json";
        chooser.setSelectedFile(new java.io.File(suggested));

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
            return;

        java.io.File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".json"))
            file = new java.io.File(file.getParentFile(), file.getName() + ".json");

        TaskData dto = ProfileCodec.toData(selected);
        boolean ok = LocalStore.exportToFile(dto, file);
        if (ok) { selected.setExported(true); saveAll(false); refilterLibrary(); }   // v1.49
        showToast(ok ? "Exported " + selected.getName() : "Export failed", anchor, ok);
    }

    /** Imports a task from a user-chosen .json file into the library. */
    private void importTaskFromFile(JComponent anchor) {
        JFileChooser chooser = new JFileChooser(main.data.store.LocalStore.getImportsDir());
        chooser.setDialogTitle("Import task (.json)");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON task files", "json"));

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
            return;

        java.io.File file = chooser.getSelectedFile();

        // Patch B.3: whole-library files first - Extend the current library (default) or
        // Overwrite it entirely. Single-task files keep working exactly as before.
        LibraryData lib = LocalStore.importFromFile(file, LibraryData.class);
        if (lib != null && lib.tasks != null && !lib.tasks.isEmpty()) {
            int choice = JOptionPane.showOptionDialog(this,
                    "This file holds a whole library (" + lib.tasks.size() + " task(s)).\n"
                            + "Extend your current library, or overwrite it?",
                    "Import library", JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE, null,
                    new Object[]{"Extend (keep mine too)", "Overwrite mine", "Cancel"},
                    "Extend (keep mine too)");
            if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) return;
            if (choice == 1) libraryAll.clear();
            int added = 0;
            for (TaskData td : lib.tasks) {
                Task t = ProfileCodec.fromData(td);
                if (t != null) {
                    t.setOrigin("imported");   // Patch B.5: drives the origin filter
                    libraryAll.add(t);
                    added++;
                }
            }
            refilterLibrary();
            refreshTaskLibrary();
            sortLibrary();
            showToast((choice == 1 ? "Library replaced: " : "Library extended: +") + added + " task(s)",
                    anchor, true);
            return;
        }

        TaskData dto = LocalStore.importFromFile(file, TaskData.class);
        Task task = ProfileCodec.fromData(dto);
        if (task == null) {
            showToast("Could not read that task file", anchor, false);
            return;
        }

        task.setOrigin("imported");
        libraryAdd(task);
        refreshTaskLibrary();
        showToast("Imported " + task.getName(), anchor, true);
    }

    private JPanel createSkillTrackerTab() {
        JPanel panelSkillTracker = new JPanel(new BorderLayout(15, 15));
        panelSkillTracker.setBorder(new EmptyBorder(15, 15, 15, 15));
        panelSkillTracker.setBackground(BG_BASE);

        JPanel gridSkills = new JPanel(new GridLayout(0, 3, 3, 3));
        gridSkills.setBackground(BG_BASE);
        gridSkills.setBorder(new EmptyBorder(8, 8, 8, 8));

        refreshSkillTrackerTab(gridSkills);

        // Patch B.6: remember which skills are tracked per account, + a reset that restarts all
        // trackers from the current state (start-from-now instead of from login).
        rememberTrackersCheck = new JCheckBox("Remember tracked skills for this account",
                rememberTrackers);
        rememberTrackersCheck.setOpaque(false);
        rememberTrackersCheck.setForeground(TEXT_MAIN);
        rememberTrackersCheck.setToolTipText("Save the tracked-skill selection with this "
                + "character's profile, so you don't re-enable them every session");
        rememberTrackersCheck.addActionListener(e -> {
            rememberTrackers = rememberTrackersCheck.isSelected();
            saveAll(false);   // persist the preference immediately (silent)
        });

        JButton resetTrackers = createButton("Reset trackers", new Color(90, 55, 20), null);
        resetTrackers.setToolTipText("Restart every tracker from the CURRENT level/XP - gained, "
                + "xp/hr and time-to-level all restart from now");
        resetTrackers.addActionListener(e -> resetAllTrackers());

        // v1.89: CLEAR is the other half of RESET, and they're deliberately different verbs.
        // Reset keeps your selection and restarts the numbers; Clear keeps the numbers and drops
        // the selection. Previously the only way to stop tracking six skills was to click all
        // six tiles off one at a time - and doing that with Reset would also have thrown away
        // the session's XP, which is usually the last thing you want.
        JButton clearTrackers = createButton("Clear trackers", new Color(60, 60, 70), null);
        clearTrackers.setToolTipText("Stop tracking every skill - the overlays and detail cards "
                + "clear, but gained XP and xp/hr are left exactly as they are");
        clearTrackers.addActionListener(e -> {
            int n = 0;
            for (SkillData sd : skillRegistry.values())
                if (sd.isTracking()) { sd.setTracking(false); n++; }
            syncSkillTileBorders();
            refreshTrackerList();
            requestAutosave();
            showToast(n == 0 ? "Nothing was being tracked"
                    : "Cleared " + n + " tracker" + (n == 1 ? "" : "s") + " (XP kept)",
                    clearTrackers, true);
        });

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(createSubtitle("Skill Tracker"), BorderLayout.WEST);
        JPanel headerCtl = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        headerCtl.setOpaque(false);
        headerCtl.add(rememberTrackersCheck);
        headerCtl.add(clearTrackers);
        headerCtl.add(resetTrackers);
        header.add(headerCtl, BorderLayout.EAST);

        panelSkillTracker.add(header, BorderLayout.NORTH);
        panelSkillTracker.add(Theme.thinScrollbars(new JScrollPane(gridSkills)), BorderLayout.CENTER);

        // v1.86: the tracked-skill DETAIL panels (gained / xp/h / TTL / projection) moved here
        // from the old side panel - the east column, next to the grid that toggles them.
        JLabel detailHead = new JLabel("Tracked details");
        detailHead.setForeground(TEXT_DIM);
        detailHead.setFont(Theme.fontBold(12));
        detailHead.setBorder(new EmptyBorder(0, 4, 6, 0));
        JScrollPane detailScroll = Theme.thinScrollbars(new JScrollPane(trackerList));
        detailScroll.setBorder(null);
        detailScroll.getViewport().setBackground(PANEL_SURFACE);
        JPanel detailCol = new JPanel(new BorderLayout());
        detailCol.setOpaque(false);
        detailCol.setPreferredSize(new Dimension(300, 0));
        detailCol.add(detailHead, BorderLayout.NORTH);
        detailCol.add(detailScroll, BorderLayout.CENTER);
        panelSkillTracker.add(detailCol, BorderLayout.EAST);

        return panelSkillTracker;
    }

    /** Restarts every tracker from the current state (Patch B.6). */
    private void resetAllTrackers() {
        for (SkillData sd : skillRegistry.values()) {
            try {
                if (Client.isLoggedIn())
                    sd.reset(Skills.getExperience(sd.getSkill()), Skills.getRealLevel(sd.getSkill()));
                else
                    sd.reset(sd.getLastXp(), sd.getCurrentLevel());
            } catch (Throwable t) {
                sd.reset(sd.getLastXp(), sd.getCurrentLevel());
            }
        }
        refreshTrackerList();
        showToast("Trackers restarted from current stats", trackerList, true);
    }

    /**
     * The Watchers tab (Patch B.4): the always-on background checks. Add a watcher, pick a
     * condition and its argument, then build the response chain that runs when it trips - while
     * the player is safe. This is where "keep me alive" rules live: HP &lt; 15 → eat, prayer
     * &lt; 10 → drink, run &lt; 20% → rest, and so on. Per-ACTION watchers are edited from the
     * builder's Watchers… button instead.
     */
    private JPanel createWatchersTab() {
        // Patch B.5 redesign: the old layout was one chunky card per watcher inside a split
        // pane you had to drag before anything was visible. Now the tab is full-width (the loot
        // readout moved to its own Loot Tracker tab): a compact LIST of checks on the left, one
        // line each, and a clear DETAIL editor for the selected check on the right. Renamed
        // user-facing to "Triggers".
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        panel.setBackground(BG_BASE);

        JLabel blurb = new JLabel("<html>Triggers run automatically between actions - independent "
                + "ones run together (run away <i>and</i> eat). When two triggers want the same "
                + "thing (both freeing inventory, both walking), the one <b>higher in the list "
                + "wins</b> that cycle and the other waits - <b>drag to reorder</b> and set that "
                + "priority. Per-action triggers live on the <b>Triggers\u2026</b> button in the Task "
                + "Builder.</html>");
        blurb.setForeground(TEXT_DIM);
        blurb.setBorder(new EmptyBorder(0, 0, 8, 0));

        checksEditor = new main.menu.components.TriggerEditor(
                globalTriggers, false, this::pickResponseAction);
        checksEditor.setOrderChangedHook(() -> saveAll(false));   // B.8: persist new priority
        main.menu.components.TriggerEditor editor = checksEditor;

        panel.add(createSubtitle("Triggers"), BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setOpaque(false);
        body.add(blurb, BorderLayout.NORTH);
        body.add(editor, BorderLayout.CENTER);
        // v1.64: triggers are publishable now - stage one for the market from here. Each listing
        // carries exactly ONE check (a trigger has one main intention), and the v1.61 card gate
        // applies as ever: it stages into the market-ready strip and opens the Card Builder.
        JButton btnPublishCheck = createButton("Publish a check\u2026", new Color(25, 60, 75), null);
        btnPublishCheck.setToolTipText("Stage one of your always-on checks as a market listing "
                + "(kind: trigger) and build its card");
        btnPublishCheck.addActionListener(e -> pickTriggerToStage(btnPublishCheck));
        JPanel pubRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        pubRow.setOpaque(false);
        pubRow.setBorder(new EmptyBorder(6, 0, 0, 0));
        pubRow.add(btnPublishCheck);
        body.add(pubRow, BorderLayout.SOUTH);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    /**
     * The script Market (Patch B.11) - browse, rate, download and publish DreamMan scripts.
     *
     * <p>Listings carry the script as DATA (a bundle of tasks + action types from DreamMan's own
     * registry), never as a jar - so downloading one can't execute code on your machine. You still
     * see exactly which actions a script will add before you import it.
     */
    /** A compact, square icon button with a hover tooltip (Patch B.16). */
    private JButton iconButton(Icon icon, String tooltip, Runnable onClick) {
        JButton b = new JButton(icon);
        b.setToolTipText(tooltip);
        b.setPreferredSize(new Dimension(34, 30));
        b.setFocusPainted(false);
        b.setMargin(new Insets(2, 2, 2, 2));
        if (onClick != null) b.addActionListener(e -> onClick.run());
        return b;
    }

    /**
     * v1.73 (item 11): the market's icon toggles paint their own chrome. They used to fall
     * through to the platform look-and-feel, which rendered a bright blue selected/rollover
     * fill that belonged to no part of this theme and fought the gold icon sitting on it.
     * Flat dark surface, gold rule and a gold-tinted fill when active.
     */
    private JToggleButton iconToggle(Icon icon, String tooltip) {
        JToggleButton b = new JToggleButton(icon) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                boolean on = isSelected();
                boolean hot = getModel().isRollover();
                g2.setColor(on ? ICON_BTN_ON : hot ? ICON_BTN_HOT : ICON_BTN_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(on ? Theme.ACCENT : Theme.BORDER);
                g2.setStroke(new BasicStroke(on ? 1.8f : 1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setToolTipText(tooltip);
        b.setPreferredSize(new Dimension(34, 30));
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setOpaque(false);
        b.setRolloverEnabled(true);
        b.setMargin(new Insets(2, 2, 2, 2));
        return b;
    }

    private static final Color ICON_BTN_BG  = new Color(0x24, 0x24, 0x24);
    private static final Color ICON_BTN_HOT = new Color(0x2E, 0x2E, 0x2E);
    /** A dark gold wash - readable as "active" without drowning the gold glyph on top. */
    private static final Color ICON_BTN_ON  = new Color(0x3A, 0x33, 0x18);

    // Patch B.16: the source toggle + the configured server URL (admins can change it).
    private JToggleButton btnFolderSource, btnServerSource;

    /**
     * Patch B.17: the DreamMan marketplace server every build ships with. The user never has to
     * "set a server" - signing in and the market both use this out of the box (admins can still
     * right-click the server icon to point a build somewhere else, e.g. a staging box).
     * <p>CHANGE THIS ONE CONSTANT when the production endpoint moves.
     */
    public static final String DEFAULT_MARKET_SERVER_URL = "https://ghost-server.nz/ghost-bot";

    /**
     * v1.30: where "Forgot password" points people. The recovery-code flow lives in the app,
     * but this page can host the human-readable walkthrough. Adjust once the site page exists.
     */
    public static final String ACCOUNT_HELP_URL = "https://ghost-server.nz/ghost-bot/account-help";

    /** The server the market points at. Preset for everyone; only admins change it. */
    private String marketServerUrl = DEFAULT_MARKET_SERVER_URL;
    /** The local folder the market points at (auto-created, right-click the icon to change). */
    private java.io.File marketFolder;
    /** v1.32b: the local market folder, ALWAYS loaded alongside the server (merged view). */
    private main.market.FolderRepository localMarketRepo;

    /**
     * v1.33: the default local market folder lives under the sanctioned script dir
     * ({@code scripts.path/DreamMan/market}). It used to sit next to the jar, which in DreamBot
     * resolves to a temp path outside the allowed tree - which SDN forbids. Always created.
     */
    private java.io.File defaultMarketFolder() {
        // ALWAYS under the script dir. No user.home, no temp dir.
        java.io.File f = new java.io.File(LocalStore.getRoot(), "market");
        f.mkdirs();
        return f;
    }

    private JPanel createMarketTab() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        panel.setBackground(BG_BASE);

        // default folder location (used when the folder source is picked)
        marketFolder = defaultMarketFolder();
        localMarketRepo = new main.market.FolderRepository(marketFolder);   // v1.32b: merged view
        // Start on the SERVER automatically when we're allowed to talk to it (a server browse
        // is a network call, so it still needs the one-time privacy consent); otherwise the
        // local folder market loads instantly and the server is one click away.
        if (marketServerUrl != null && !marketServerUrl.isEmpty()
                && main.privacy.Consent.has(main.privacy.Consent.MARKET_BROWSE))
            marketRepo = new main.market.HttpRepository(marketServerUrl,
                    main.market.ServerAccount.session().token);
        else
            marketRepo = new main.market.FolderRepository(marketFolder);

        lblMarketSource = new JLabel(marketRepo.describe());
        lblMarketSource.setForeground(TEXT_DIM);
        lblMarketSource.setFont(new Font("Consolas", Font.PLAIN, 11));

        Color ic = new Color(212, 175, 55);

        // ── source toggle: folder | server, as icons ──
        btnFolderSource = iconToggle(main.menu.components.UIIcons.folder(20, ic),
                "<html><b>Local folder</b><br>A shared Dropbox/Drive/network folder.<br>"
                        + "Click to use it \u00b7 right-click to change the path.</html>");
        btnServerSource = iconToggle(main.menu.components.UIIcons.server(20, ic),
                "<html><b>Server</b><br>The DreamMan marketplace on your server."
                        + (main.market.Tier.isAdmin() ? "<br>Right-click to change the URL (admin)." : "")
                        + "</html>");
        ButtonGroup sourceGroup = new ButtonGroup();
        sourceGroup.add(btnFolderSource);
        sourceGroup.add(btnServerSource);
        (marketRepo instanceof main.market.HttpRepository ? btnServerSource : btnFolderSource).setSelected(true);

        btnFolderSource.addActionListener(e -> useFolderSource());
        btnServerSource.addActionListener(e -> useServerSource());
        // v1.70: the folder is FIXED. Right-click used to open a JFileChooser that took
        // ~10s per interaction and let users point the market at a path outside the sanctioned
        // scripts.path root, which breaks SDN compliance. There is one correct folder, so
        // there is now no way to change it.
        btnServerSource.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent me) {
                if (SwingUtilities.isRightMouseButton(me)) changeServerUrl();
            }
        });

        // ── v1.60: sort + filter over the CARD GRID, as popup menus behind two icons. The old
        // view dropdown (Live market / My uploads / Local / All / Loved) is gone: Local is now
        // the permanent strip at the bottom, Loved is a filter, and My uploads keeps its own
        // manager dialog behind the header button.
        btnMarketSort = iconButton(main.menu.components.UIIcons.sort(20, ic),
                sortTooltip(), null);
        btnMarketSort.addActionListener(e -> showSortMenu());
        btnMarketFilter = iconButton(main.menu.components.UIIcons.filter(20, ic),
                filterTooltip(), null);
        btnMarketFilter.addActionListener(e -> showFilterMenu());

        // ── search field ──
        marketSearchField = new JTextField();
        marketSearchField.setToolTipText("Search by name, author, description or tag");
        marketSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { refreshMarketGrid(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { refreshMarketGrid(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { refreshMarketGrid(); }
        });

        // ── top-right actions: refresh + My uploads (import is per-card now) ──
        JButton btnRefresh = iconButton(main.menu.components.UIIcons.refresh(20, ic),
                "Refresh the market", this::reloadMarket);

        // header row: [folder|server] [sort] [filter] [search .......] [refresh] [My uploads]
        JPanel head = new JPanel(new BorderLayout(6, 4));
        head.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setOpaque(false);
        left.add(btnFolderSource);
        left.add(btnServerSource);
        left.add(Box.createHorizontalStrut(8));
        left.add(btnMarketSort);
        left.add(btnMarketFilter);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.setOpaque(false);
        right.add(btnRefresh);

        JPanel searchWrap = new JPanel(new BorderLayout(4, 0));
        searchWrap.setOpaque(false);
        // Patch B.17: a drawn magnifier - the \uD83D\uDD0D emoji rendered as an empty rectangle
        // on clients whose UI font has no colour-emoji fallback.
        JLabel mag = new JLabel(main.menu.components.UIIcons.search(16, TEXT_DIM));
        mag.setBorder(new EmptyBorder(0, 2, 0, 2));
        searchWrap.add(mag, BorderLayout.WEST);
        searchWrap.add(marketSearchField, BorderLayout.CENTER);

        JPanel topRow = new JPanel(new BorderLayout(8, 0));
        topRow.setOpaque(false);
        topRow.add(left, BorderLayout.WEST);
        topRow.add(searchWrap, BorderLayout.CENTER);
        topRow.add(right, BorderLayout.EAST);

        // v1.75 (item 14): tags as clickable pills directly under the search field. They used to
        // be checkboxes buried in the filter popup, which meant you had to already know the
        // feature existed to find it. Collapsible, because a market with fifty tags is noise for
        // anyone who doesn't use them.
        tagBar = new main.menu.components.TagFilterBar(fltTags,
                tag -> {
                    if (!fltTags.remove(tag)) fltTags.add(tag);
                    filtersChanged();
                },
                () -> { fltTags.clear(); filtersChanged(); });
        // Hiding the bar changes what the search matches, so re-run it immediately rather than
        // waiting for the next keystroke - otherwise the toggle looks like it did nothing.
        tagBar.onVisibilityChanged = this::filtersChanged;

        JPanel headStack = new JPanel(new BorderLayout(0, 0));
        headStack.setOpaque(false);
        headStack.add(topRow, BorderLayout.NORTH);
        headStack.add(tagBar, BorderLayout.CENTER);

        head.add(headStack, BorderLayout.NORTH);
        // v1.79: lblMarketSource used to sit here, above the grid - connection plumbing was
        // the first thing anyone saw of the market. It now sits under the cards.

        // ── v1.60 TOP: everyone's published scripts as a wrapping CARD GRID. Real components,
        // not a renderer: every star, heart and button is live, and comments expand inside the
        // card itself (the v1.50 accordion carries forward - one open at a time).
        marketGridPanel = new JPanel(new main.menu.components.WrapLayout(FlowLayout.LEFT, 10, 10));
        marketGridPanel.setOpaque(false);
        marketGridScroll = Theme.thinScrollbars(new JScrollPane(marketGridPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER));
        marketGridScroll.setBorder(BorderFactory.createLineBorder(COLOR_BORDER_DIM));
        marketGridScroll.getVerticalScrollBar().setUnitIncrement(24);
        marketGridScroll.getViewport().setBackground(BG_BASE);

        // ── v1.60 BOTTOM: my market-ready staging as one bounded row of compact cards.
        // [<] and [>] step through them and simply disable at the ends - no wrap-around.
        JPanel strip = new JPanel(new BorderLayout(0, 4));
        strip.setOpaque(false);
        strip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, COLOR_BORDER_DIM),
                new EmptyBorder(8, 0, 0, 0)));

        readyCountLabel = new JLabel("MY MARKET-READY");
        readyCountLabel.setFont(new Font("Consolas", Font.BOLD, 11));
        readyCountLabel.setForeground(Theme.ACCENT);
        readySortLabel = new JLabel("newest first \u25be");
        readySortLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        readySortLabel.setForeground(TEXT_DIM);
        readySortLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        readySortLabel.setToolTipText("Click to switch between newest-first and A-Z");
        readySortLabel.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent me) {
                readySortAZ = !readySortAZ;
                refreshReadyStrip();
            }
        });
        JPanel stripLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        stripLeft.setOpaque(false);
        stripLeft.add(readyCountLabel);
        stripLeft.add(readySortLabel);
        stripLeft.add(lblMarketSource);   // v1.79: under the grid, not above it

        // v1.79: the market's "Publish..." button is gone. It opened a picker that duplicated
        // what the cards themselves now do, and publishing a PRESET belongs next to the queue you
        // built - so it moved to the Task List. Every market-ready card publishes itself.
        //
        // This row sits directly under the card grid, which is where the two things people
        // actually want after looking at the market go: what they're connected to, and whether
        // their own work is being hidden from them.
        chkHideOwn = new JCheckBox("Hide my own scripts", fltHideOwn);
        chkHideOwn.setOpaque(false);
        chkHideOwn.setForeground(TEXT_DIM);
        chkHideOwn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        chkHideOwn.setToolTipText("<html>Off by default, so a script you just published is "
                + "visibly <b>on the market</b> rather than seeming to vanish.<br>"
                + "Turn it on once you'd rather browse other people's work.</html>");
        chkHideOwn.addActionListener(e -> {
            fltHideOwn = chkHideOwn.isSelected();
            filtersChanged();
        });
        JPanel stripRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        stripRight.setOpaque(false);
        stripRight.add(chkHideOwn);

        JPanel stripHead = new JPanel(new BorderLayout(8, 0));
        stripHead.setOpaque(false);
        stripHead.add(stripLeft, BorderLayout.WEST);
        stripHead.add(stripRight, BorderLayout.EAST);

        readyPrev = iconButton(main.menu.components.UIIcons.chevron(18, ic, true),
                "Earlier cards", () -> { readyStart--; refreshReadyStrip(); });
        readyNext = iconButton(main.menu.components.UIIcons.chevron(18, ic, false),
                "Later cards", () -> { readyStart++; refreshReadyStrip(); });
        readyCardsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        readyCardsPanel.setOpaque(false);
        JPanel stripMid = new JPanel(new BorderLayout(6, 0));
        stripMid.setOpaque(false);
        stripMid.add(readyPrev, BorderLayout.WEST);
        stripMid.add(readyCardsPanel, BorderLayout.CENTER);
        stripMid.add(readyNext, BorderLayout.EAST);

        strip.add(stripHead, BorderLayout.NORTH);
        strip.add(stripMid, BorderLayout.CENTER);

        panel.add(createSubtitle("Market"), BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setOpaque(false);
        body.add(head, BorderLayout.NORTH);
        // v1.64: the centre is a CardLayout - the grid, or ONE listing's full detail view
        // (opened from a card's (i)/comment buttons or right-click; closed by its X or Esc).
        marketCenterCards = new JPanel(new CardLayout());
        marketCenterCards.setOpaque(false);
        marketCenterCards.add(marketGridScroll, "grid");
        body.add(marketCenterCards, BorderLayout.CENTER);
        body.add(strip, BorderLayout.SOUTH);

        // ── v1.88: the market is SIGNED-IN ONLY ────────────────────────────────────────
        // Scripts live on the server now, so the market is a view of your account's world -
        // browsing, publishing, rating and commenting all need to know who you are. Rather
        // than letting people poke at a market that can't answer them, the whole tab shows a
        // sign-in wall until they're authenticated. What you've ALREADY downloaded is yours
        // offline forever: imported scripts land in your local library and never come back
        // through here.
        marketGateCards = new CardLayout();
        marketGateHost = new JPanel(marketGateCards);
        marketGateHost.setOpaque(false);
        marketGateHost.add(buildMarketSignInWall(), "gate");
        marketGateHost.add(body, "market");
        panel.add(marketGateHost, BorderLayout.CENTER);

        refreshMarketGate();
        if (main.market.ServerAccount.isLoggedIn()) reloadMarket();
        return panel;
    }

    private CardLayout marketGateCards;
    private JPanel marketGateHost;

    /** v1.88: what the Market tab shows when nobody's signed in - an explanation and a way in. */
    private JPanel buildMarketSignInWall() {
        JPanel wrap = new JPanel(new GridBagLayout());
        wrap.setOpaque(false);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(PANEL_SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_BORDER_DIM),
                new EmptyBorder(22, 26, 22, 26)));

        JLabel title = new JLabel("Sign in to browse the market");
        title.setForeground(Theme.ACCENT);
        title.setFont(new Font("Consolas", Font.BOLD, 19));
        title.setAlignmentX(0f);
        card.add(title);

        JLabel blurb = new JLabel("<html><div style='width:400px'>Market scripts are stored on "
                + "the DreamMan server, so browsing, publishing, rating and commenting all need "
                + "your account.<br><br>Anything you've <b>already downloaded is yours offline</b> "
                + "\u2014 it's in your Task Library and runs with or without a connection.</div></html>");
        blurb.setForeground(TEXT_DIM);
        blurb.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        blurb.setAlignmentX(0f);
        blurb.setBorder(new EmptyBorder(10, 0, 14, 0));
        card.add(blurb);

        JButton go = createButton("Go to the Account tab", new Color(45, 70, 45), null);
        go.setAlignmentX(0f);
        go.setToolTipText("Sign in (or create an account) on the Account tab");
        go.addActionListener(e -> {
            int idx = indexOfTab("Account");
            if (idx >= 0) mainTabs.setSelectedIndex(idx);
        });
        card.add(go);

        wrap.add(card);
        return wrap;
    }

    /** v1.88: flips the Market tab between the sign-in wall and the market itself. */
    private void refreshMarketGate() {
        if (marketGateHost == null || marketGateCards == null) return;
        boolean in = main.market.ServerAccount.isLoggedIn();
        marketGateCards.show(marketGateHost, in ? "market" : "gate");
    }

    /** v1.60: the sort menu over the card grid - one radio per option, roadmap's full set. */
    private void showSortMenu() {
        JPopupMenu m = new JPopupMenu();
        for (String opt : MARKET_SORTS) {
            JRadioButtonMenuItem mi = new JRadioButtonMenuItem(opt, opt.equals(marketSort));
            mi.addActionListener(a -> {
                marketSort = opt;
                btnMarketSort.setToolTipText(sortTooltip());
                refreshMarketGrid();
            });
            m.add(mi);
        }
        m.show(btnMarketSort, 0, btnMarketSort.getHeight());
    }

    private String sortTooltip() {
        return "<html><b>Sort</b> \u00b7 " + marketSort + "<br>Top rated is weighted: average "
                + "first, then how many people rated (v1.59, computed server-side).</html>";
    }

    /**
     * v1.60: the filter menu - hide VIP, hide free, loved-only, hide-older-versions (default ON)
     * and a tag section where any ticked tag matches. Each click applies immediately.
     */
    private void showFilterMenu() {
        JPopupMenu m = new JPopupMenu();

        JCheckBoxMenuItem vip = new JCheckBoxMenuItem("Hide VIP scripts", fltHideVip);
        vip.addActionListener(a -> { fltHideVip = vip.isSelected(); filtersChanged(); });
        m.add(vip);
        JCheckBoxMenuItem free = new JCheckBoxMenuItem("Hide free scripts", fltHideFree);
        free.addActionListener(a -> { fltHideFree = free.isSelected(); filtersChanged(); });
        m.add(free);
        JCheckBoxMenuItem loved = new JCheckBoxMenuItem("Loved only \u2665", fltLovedOnly);
        loved.setToolTipText("Only the scripts you've hearted");
        loved.addActionListener(a -> { fltLovedOnly = loved.isSelected(); filtersChanged(); });
        m.add(loved);
        JCheckBoxMenuItem older = new JCheckBoxMenuItem("Hide older versions", fltHideOlder);
        older.setToolTipText("<html>On (the default): only the highest version of each script "
                + "shows.<br>Two versions of the same script CAN coexist on the market \u2014 "
                + "untick this to see<br>every version side by side.</html>");
        older.addActionListener(a -> { fltHideOlder = older.isSelected(); filtersChanged(); });
        m.add(older);

        // v1.88: the tag checkboxes are GONE from this menu. Every tag is already a
        // clickable pill under the search field (TagFilterBar), so this list was a second,
        // capped, un-scrollable copy of the same control - two places to set one filter, and
        // the menu's "...and 12 more" cut-off made it the worse of the two.

        m.show(btnMarketFilter, 0, btnMarketFilter.getHeight());
    }

    /** v1.60: re-applies filters and re-tints the funnel gold while any non-default one is on. */
    private void filtersChanged() {
        boolean active = fltHideVip || fltHideFree || fltLovedOnly
                || !fltTags.isEmpty() || !fltHideOlder || fltHideOwn;
        btnMarketFilter.setIcon(main.menu.components.UIIcons.filter(20,
                active ? Theme.ACCENT : MARKET_ICON_GOLD));
        btnMarketFilter.setToolTipText(filterTooltip());
        refreshMarketGrid();
    }

    private String filterTooltip() {
        List<String> on = new ArrayList<>();
        if (fltHideVip) on.add("hiding VIP");
        if (fltHideFree) on.add("hiding free");
        if (fltHideOwn) on.add("hiding my own");
        if (fltLovedOnly) on.add("loved only");
        if (!fltHideOlder) on.add("showing older versions");
        if (!fltTags.isEmpty()) on.add("tags: " + String.join(", ", fltTags));
        return "<html><b>Filter</b>" + (on.isEmpty() ? " \u00b7 none active"
                : " \u00b7 " + String.join(" \u00b7 ", on))
                + "<br>Older versions are hidden by default \u2014 two versions of the same "
                + "script can still coexist.</html>";
    }

    /** A safe toast/dialog anchor for market actions, whatever built first. */
    private JComponent marketAnchor() {
        return marketGridScroll != null ? marketGridScroll : mainTabs;
    }

    /**
     * v1.60: rebuilds the card GRID from marketAll + the active search, filters and sort. The
     * open-comments card and the scroll position both survive a rebuild, so rating or hearting
     * something doesn't yank the view around.
     */
    /**
     * v1.75: repopulates the tag bar from whatever the loaded listings actually carry, so a newly
     * published tag appears on its own without being registered anywhere.
     */
    private void refreshTagBar() {
        if (tagBar == null) return;
        java.util.TreeSet<String> tags = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (main.market.ScriptListing l : marketAll)
            if (l != null && l.tags != null && !"local".equals(l.origin)) tags.addAll(l.tags);
        tagBar.setTags(tags);
    }

    private void refreshMarketGrid() {
        refreshTagBar();   // v1.75: the pill row tracks the loaded listings
        if (marketGridPanel == null) return;
        String q = marketSearchField == null ? ""
                : marketSearchField.getText().trim().toLowerCase();

        List<main.market.ScriptListing> view = new ArrayList<>();
        for (main.market.ScriptListing l : marketAll) {
            if (l == null) continue;
            // v1.71: the grid shows the live market on the server page, and your UNBUILT
            // local cards on the folder page - built ones have moved down to the strip.
            boolean serverPage = marketRepo instanceof main.market.HttpRepository;
            boolean isStaged = "local".equals(l.origin);
            if (serverPage && isStaged) continue;
            if (!serverPage && (!isStaged || l.cardReady)) continue;
            if (fltHideVip && l.vipOnly) continue;
            if (fltHideFree && !l.vipOnly) continue;
            if (fltLovedOnly && !l.myFavorite) continue;
            if (fltHideOwn && isOwnListing(l)) continue;   // v1.70
            if (!fltTags.isEmpty()) {
                // v1.78: several tags now mean AND, not OR. Picking #mining and #f2p asks for
                // things that are both, which is what narrowing a list is for - OR made every
                // extra tag return MORE results, so the filter got less useful the more you used.
                boolean all = true;
                for (String want : fltTags) {
                    boolean has = false;
                    if (l.tags != null)
                        for (String t : l.tags)
                            if (t != null && t.equalsIgnoreCase(want)) { has = true; break; }
                    if (!has) { all = false; break; }
                }
                if (!all) continue;
            }
            if (!q.isEmpty()) {
                // v1.76 (item 14): hiding the tag bar also stops tags being searched. With a
                // hundred things tagged #furnace, searching "furnace" with tags off returns only
                // the two or three that actually say furnace in their name or description.
                boolean searchTags = tagBar == null || !tagBar.isCollapsed();
                String hay = (l.name + " " + l.author + " " + l.description
                        + (searchTags && l.tags != null ? " " + String.join(" ", l.tags) : ""))
                        .toLowerCase();
                if (!hay.contains(q)) continue;
            }
            view.add(l);
        }

        if (fltHideOlder) {
            // v1.74: collapse each LINEAGE to its newest version (server 1.6.0 stacking). Keyed
            // on lineageId, so renaming a script can't merge it into another of your own; rows
            // published before 1.6.0 have no lineage and fall back to the old author+name key.
            java.util.Map<String, main.market.ScriptListing> best = new LinkedHashMap<>();
            for (main.market.ScriptListing l : view) {
                String k = lineageKey(l);
                main.market.ScriptListing b = best.get(k);
                if (b == null || l.version > b.version) best.put(k, l);
            }
            view = new ArrayList<>(best.values());
        }

        switch (marketSort) {
            case "Most downloaded":
                view.sort((a, b) -> Integer.compare(b.downloads, a.downloads)); break;
            case "Newest":
                view.sort((a, b) -> Long.compare(b.publishedAt, a.publishedAt)); break;
            case "A-Z":
                view.sort(Comparator.comparing(l -> l.name == null ? "" : l.name.toLowerCase()));
                break;
            case "Version (highest)":
                view.sort((a, b) -> Double.compare(b.version, a.version)); break;
            case "Version (lowest)":
                view.sort(Comparator.comparingDouble(l -> l.version)); break;
            default:   // "Top rated" - weighted: average first, then how many rated (v1.59)
                view.sort((a, b) -> {
                    int byAvg = Double.compare(b.avgRating, a.avgRating);
                    if (byAvg != 0) return byAvg;
                    return Integer.compare(b.ratingCount, a.ratingCount);
                });
        }
        // v1.59: loved cards float to the top of whichever sort is active (stable sort)
        view.sort(Comparator.comparingInt(l -> l.myFavorite ? 0 : 1));

        int scroll = marketGridScroll == null ? 0
                : marketGridScroll.getVerticalScrollBar().getValue();
        marketGridPanel.removeAll();
        marketCardById.clear();
        if (view.isEmpty()) {
            JLabel empty = new JLabel(marketAll.isEmpty()
                    ? "Nothing here yet \u2014 hit refresh, or publish something below."
                    : "No scripts match the search/filters.");
            empty.setForeground(TEXT_DIM);
            empty.setFont(new Font("Segoe UI", Font.ITALIC, 12));
            empty.setBorder(new EmptyBorder(18, 8, 18, 8));
            marketGridPanel.add(empty);
        }
        for (main.market.ScriptListing l : view) {
            main.menu.components.MarketCard card = new main.menu.components.MarketCard(
                    l, main.menu.components.MarketCard.Mode.GRID, cardCallbacks);
            if (l.id != null) marketCardById.put(l.id, card);
            // v1.64: if this listing's DETAIL is open, refresh its live stats from the reload
            if (marketDetail != null && l.id != null && l.id.equals(detailListingId))
                marketDetail.refreshFrom(l);
            marketGridPanel.add(card);
        }
        marketGridPanel.revalidate();
        marketGridPanel.repaint();
        final int keep = scroll;
        SwingUtilities.invokeLater(() ->
                marketGridScroll.getVerticalScrollBar().setValue(keep));
    }

    /**
     * v1.60: rebuilds the market-ready STRIP - your local staging as compact cards, newest first
     * (click the label to switch to A-Z), stepped through with bounded [<] [>] arrows.
     */
    private void refreshReadyStrip() {
        if (readyCardsPanel == null) return;
        readyAll.clear();
        for (main.market.ScriptListing l : marketAll)
            // v1.71: market-ready == the card is BUILT. Same items on the local and server
            // pages; only the button on them differs (unbuild locally / publish on server).
            if (l != null && "local".equals(l.origin) && l.cardReady) readyAll.add(l);
        if (readySortAZ)
            readyAll.sort(Comparator.comparing(l -> l.name == null ? "" : l.name.toLowerCase()));
        else
            readyAll.sort((a, b) -> Long.compare(b.publishedAt, a.publishedAt));

        readyStart = Math.max(0, Math.min(readyStart, readyAll.size() - READY_VISIBLE));
        if (readyAll.size() <= READY_VISIBLE) readyStart = 0;

        readyCardsPanel.removeAll();
        if (readyAll.isEmpty()) {
            JLabel hint = new JLabel("Nothing market-ready yet \u2014 mark a task market-ready "
                    + "in the Library, then build its card in your local folder above.");
            hint.setForeground(TEXT_DIM);
            hint.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            hint.setBorder(new EmptyBorder(10, 4, 10, 4));
            readyCardsPanel.add(hint);
        } else {
            int end = Math.min(readyAll.size(), readyStart + READY_VISIBLE);
            for (int i = readyStart; i < end; i++)
                readyCardsPanel.add(new main.menu.components.MarketCard(readyAll.get(i),
                        main.menu.components.MarketCard.Mode.STRIP, cardCallbacks));
        }
        readyPrev.setEnabled(readyStart > 0);
        readyNext.setEnabled(readyStart + READY_VISIBLE < readyAll.size());
        readyCountLabel.setText("MY MARKET-READY \u00b7 " + readyAll.size()
                + (readyAll.size() == 1 ? " item" : " items"));
        readySortLabel.setText(readySortAZ ? "A-Z \u25be" : "newest first \u25be");
        readyCardsPanel.revalidate();
        readyCardsPanel.repaint();
    }

    /**
     * v1.73 (item 8): the card's X. A published listing has two very different "removes" - take
     * it off the market but keep your copy, or destroy the copy too - so it asks rather than
     * guessing. Unpublishing keeps the staged copy, which is what makes it recoverable.
     */
    private void removeListingWithChoice(main.market.ScriptListing l, JComponent src) {
        if (l == null) return;
        boolean live = "server".equals(l.origin);
        if (!live) {
            // A local card: only one thing removal can mean.
            if (JOptionPane.showConfirmDialog(this,
                    "<html>Delete <b>" + escapeHtml(l.name) + "</b> from your local folder?"
                    + "<br><br>This is the market's own deep copy \u2014 the original task in your"
                    + "<br>Task Library is untouched.</html>",
                    "Delete local copy", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION)
                deleteLocalListing(l);
            return;
        }
        if (!isOwnListing(l) && !main.market.Tier.isAdmin()) {
            showToast("That isn't yours to remove", src, false);
            return;
        }
        Object[] opts = {"Unpublish (keep my copy)", "Delete everywhere", "Cancel"};
        int k = JOptionPane.showOptionDialog(this,
                "<html>What should happen to <b>" + escapeHtml(l.name) + "</b>?"
                + "<br><br><b>Unpublish</b> \u2014 taken off the market, your market-ready copy stays,"
                + "<br>so you can edit and re-publish it."
                + "<br><b>Delete everywhere</b> \u2014 also removes your local copy. The original"
                + "<br>task in your Task Library is untouched either way.</html>",
                "Remove listing", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                null, opts, opts[0]);
        if (k != 0 && k != 1) return;
        final boolean alsoLocal = (k == 1);
        onUnpublishRequested(l, src, alsoLocal);
    }

    /** Unpublishes, then optionally deletes the staged copy too. */
    private void onUnpublishRequested(main.market.ScriptListing l, JComponent src,
                                      boolean alsoLocal) {
        new Thread(() -> {
            try {
                marketRepo.remove(l.id);
                if (alsoLocal && localMarketRepo != null) {
                    try { localMarketRepo.remove(l.id); } catch (Exception ignored) {}
                }
                SwingUtilities.invokeLater(() -> {
                    reloadMarket();
                    showToast(alsoLocal
                            ? "\"" + l.name + "\" deleted everywhere"
                            : "\"" + l.name + "\" unpublished \u2014 your copy is in market-ready",
                            marketAnchor(), true);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                        showToast("Remove failed: " + ex.getMessage(), src, false));
            }
        }, "DreamMan-RemoveListing").start();
    }

    /**
     * v1.85: unpublish. Takes the listing off the market AND returns it to the local folder
     * unbuilt, so it can be edited. Previously this only cleared the built flag - a live listing
     * went "back to local" while still being downloadable by everyone, which is the worst of both
     * states and made it look like two different objects.
     */
    private void unpublishToLocal(main.market.ScriptListing l) {
        if (l == null) return;
        boolean live = marketRepo instanceof main.market.HttpRepository;
        if (JOptionPane.showConfirmDialog(this,
                "<html>Unpublish <b>" + escapeHtml(l.name) + "</b>?"
                + "<br><br>It comes off the market and returns to your local folder, where you"
                + "<br>can edit it and bump its version before publishing again."
                + "<br>Only you will be able to see it.</html>",
                "Unpublish", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                != JOptionPane.YES_OPTION)
            return;
        new Thread(() -> {
            String err = null;
            try {
                // Remove from the market FIRST. If that fails we leave the built flag alone,
                // so the card never claims to be private while it's still listed.
                if (live) marketRepo.remove(l.id);
            } catch (Exception ex) {
                err = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            }
            final String fErr = err;
            SwingUtilities.invokeLater(() -> {
                if (fErr != null) {
                    showToast("Couldn't unpublish: " + fErr, marketAnchor(), false);
                    return;
                }
                setCardBuilt(l, false);
            });
        }, "DreamMan-Unpublish").start();
    }

    /**
     * v1.85: keep a private copy on the user's server profile. The endpoint doesn't exist yet, so
     * this reports that plainly rather than pretending something was stored - the same fallback
     * the moderation controls use.
     */
    private void vaultListing(main.market.ScriptListing l) {
        if (l == null) return;
        if (!main.market.ServerAccount.isLoggedIn()) {
            showToast("Sign in first \u2014 vaulting stores the copy on your profile",
                    marketAnchor(), false);
            return;
        }
        new Thread(() -> {
            String err = null;
            try {
                new main.market.ServerAccount(main.market.ServerAccount.session().baseUrl)
                        .vaultScript(l);
            } catch (Throwable ex) {
                err = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            }
            final String fErr = err;
            SwingUtilities.invokeLater(() -> {
                if (fErr == null) {
                    showToast("\"" + l.name + "\" vaulted to your profile", marketAnchor(), true);
                    return;
                }
                boolean missing = fErr.contains("404") || fErr.toLowerCase().contains("not found");
                if (missing)
                    JOptionPane.showMessageDialog(this,
                            "<html>Script vaulting isn't on your server yet.<br><br>"
                            + "The button is here so the flow can be checked \u2014 <b>nothing was "
                            + "stored</b>.<br>Your local copy is untouched.</html>",
                            "Not available yet", JOptionPane.INFORMATION_MESSAGE);
                else
                    showToast("Couldn't vault that: " + fErr, marketAnchor(), false);
            });
        }, "DreamMan-Vault").start();
    }

    // ── v1.71: the built/unbuilt gate ───────────────────────────────────────────────────────

    /** Shortest and longest description a card may have before it counts as built. */
    private static final int CARD_DESC_MIN = 20, CARD_DESC_MAX = 600;
    /**
     * Placeholder text people leave in by accident. This is a courtesy check, not moderation -
     * the server-side valve (v1.66) is what actually polices content.
     */
    private static final String[] CARD_BANNED = {
            "lorem ipsum", "todo", "tbd", "asdf", "test test", "xxx",
            "no description provided"
    };

    /**
     * @return null when the listing is fit to publish, otherwise the reason it isn't.
     * Checked when marking a card built rather than at publish time, so the problem surfaces
     * while you're still looking at the card that has it.
     */
    private String cardBuildProblem(main.market.ScriptListing l) {
        if (l == null) return "That card no longer exists.";
        if (l.name == null || l.name.trim().length() < 3)
            return "Give it a name of at least 3 characters.";
        if (l.icon == null || l.icon.isBlank())
            return "It needs an image \u2014 pick one in the card builder.";
        String d = l.description == null ? "" : l.description.trim();
        if (d.length() < CARD_DESC_MIN)
            return "The description is too short (" + d.length() + " characters, minimum "
                    + CARD_DESC_MIN + ").";
        if (d.length() > CARD_DESC_MAX)
            return "The description is too long (" + d.length() + " characters, maximum "
                    + CARD_DESC_MAX + ").";
        String low = d.toLowerCase();
        for (String bad : CARD_BANNED)
            if (low.contains(bad))
                return "The description still contains placeholder text (\"" + bad + "\").";
        if (l.bundle == null || l.bundle.tasks == null || l.bundle.tasks.isEmpty())
            return "There's nothing inside this card to publish.";
        // v1.85: catch a version collision HERE, on the way into the queue, rather than at the
        // publish call. The server would replace the live listing silently (1.6.0 stacking), so
        // without this check you can mark a card ready believing it's a new version and quietly
        // overwrite the one people already downloaded.
        for (main.market.ScriptListing o : marketAll) {
            if (o == null || o == l) continue;
            if (!"server".equals(o.origin)) continue;
            if (!isOwnListing(o)) continue;
            if (o.name == null || !o.name.equalsIgnoreCase(l.name)) continue;
            if (Math.abs(o.version - l.version) < 0.0001)
                return "You already have \"" + o.name + "\" v" + String.format("%.1f", o.version)
                        + " on the market. Bump this card's version, or unpublish that one first "
                        + "\u2014 publishing at the same version replaces it, ratings and all.";
        }
        return null;
    }

    /**
     * Moves a staged listing between the local folder (unbuilt) and My Market-Ready (built).
     * Marking built runs {@link #cardBuildProblem}; unbuilding is always allowed, because the
     * whole point is being able to pull something back out of the publish queue.
     */
    private void setCardBuilt(main.market.ScriptListing l, boolean built) {
        if (l == null || localMarketRepo == null) return;
        if (built) {
            String problem = cardBuildProblem(l);
            if (problem != null) {
                JOptionPane.showMessageDialog(this,
                        "<html><b>" + escapeHtml(l.name) + "</b> isn't ready yet.<br><br>"
                        + escapeHtml(problem) + "</html>",
                        "Not market-ready", JOptionPane.WARNING_MESSAGE);
                openCardBuilder(l);   // land them where the fix is
                return;
            }
        }
        boolean was = l.cardReady;
        try {
            l.cardReady = built;
            localMarketRepo.publish(l);   // same id = save-in-place
            reloadMarket();
            showToast(built
                    ? "\"" + l.name + "\" is market-ready \u2014 publish it from the server page"
                    : "\"" + l.name + "\" moved back to your local folder",
                    marketAnchor(), true);
        } catch (Exception ex) {
            l.cardReady = was;
            showToast("Couldn't update that card: " + ex.getMessage(), marketAnchor(), false);
        }
    }

    /**
     * v1.74: the grouping key for version stacking. Prefers the server-assigned lineage; falls
     * back to author+name only for listings published before server 1.6.0 existed.
     */
    private static String lineageKey(main.market.ScriptListing l) {
        if (l == null) return "";
        if (l.lineageId != null && !l.lineageId.isBlank()) return "L:" + l.lineageId;
        return "N:" + (String.valueOf(l.author) + "\u0000" + l.name).toLowerCase();
    }

    /** Every loaded version of a listing's lineage, newest first. */
    private List<main.market.ScriptListing> versionsOf(main.market.ScriptListing l) {
        List<main.market.ScriptListing> out = new ArrayList<>();
        if (l == null) return out;
        String k = lineageKey(l);
        for (main.market.ScriptListing o : marketAll)
            if (o != null && !"local".equals(o.origin) && lineageKey(o).equals(k)) out.add(o);
        out.sort((a, b) -> Double.compare(b.version, a.version));
        return out;
    }

    // ── v1.80: TASK triggers, editable in two places with deliberately different meanings ──

    /**
     * LIBRARY edit: authors the task's DEFAULT triggers. New copies inherit them, and the user
     * chooses how existing copies in the queue are treated - because silently overwriting an
     * instance somebody customised in the Task List would destroy work with no warning.
     */
    private void openLibraryTaskTriggers(Task libTask, JComponent anchor) {
        if (libTask == null) { showToast("Select a library task first", anchor, false); return; }
        java.util.List<main.watchers.Trigger> before = new ArrayList<>();
        for (main.watchers.Trigger t : libTask.getTaskTriggers())
            if (t != null) before.add(new main.watchers.Trigger(t));

        main.menu.components.TriggerEditor editor = new main.menu.components.TriggerEditor(
                libTask.getTaskTriggers(), false, this::pickResponseAction);
        editor.setPreferredSize(new Dimension(780, 560));
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this),
                "Task triggers \u2014 " + libTask.getName() + " (library default)",
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setAlwaysOnTop(true);
        JPanel wrap = new JPanel(new BorderLayout(0, 8));
        wrap.setBorder(new EmptyBorder(10, 10, 10, 10));
        JLabel note = new JLabel("<html>These run for as long as this task is executing \u2014 "
                + "wider than an action trigger, narrower than a script trigger.<br>"
                + "This is the <b>library default</b>; queue entries inherit it.</html>");
        note.setForeground(TEXT_DIM);
        wrap.add(note, BorderLayout.NORTH);
        wrap.add(editor, BorderLayout.CENTER);
        dlg.setContentPane(wrap);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);

        if (sameTriggers(before, libTask.getTaskTriggers())) return;   // nothing to propagate
        propagateTaskTriggers(libTask, before, anchor);
        saveAll(false);
    }

    /** True when two trigger lists describe the same checks in the same order. */
    private static boolean sameTriggers(java.util.List<main.watchers.Trigger> a,
                                        java.util.List<main.watchers.Trigger> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            String x = a.get(i) == null ? "" : a.get(i).describe();
            String y = b.get(i) == null ? "" : b.get(i).describe();
            if (!x.equals(y)) return false;
        }
        return true;
    }

    /**
     * Pushes the library default onto queue entries of the same task. Entries whose triggers were
     * customised in the Task List are only touched if the user explicitly says so, and either way
     * they're told how many were affected - a propagation that silently rewrites work is how
     * people lose an afternoon.
     */
    private void propagateTaskTriggers(Task libTask, java.util.List<main.watchers.Trigger> oldDefault,
                                       JComponent anchor) {
        java.util.List<Task> same = new ArrayList<>();
        java.util.List<Task> customised = new ArrayList<>();
        for (int i = 0; i < modelTaskList.size(); i++) {
            Task q = modelTaskList.get(i);
            if (q == null || q.getId() == null || !q.getId().equals(libTask.getId())) continue;
            (sameTriggers(oldDefault, q.getTaskTriggers()) ? same : customised).add(q);
        }
        if (same.isEmpty() && customised.isEmpty()) return;

        boolean alsoCustomised = false;
        if (!customised.isEmpty()) {
            Object[] opts = {"Update all " + (same.size() + customised.size()),
                    "Keep my " + customised.size() + " customised", "Cancel"};
            int k = JOptionPane.showOptionDialog(this,
                    "<html>" + customised.size() + " queue entr"
                    + (customised.size() == 1 ? "y has" : "ies have")
                    + " triggers you changed in the Task List.<br><br>"
                    + "<b>Update all</b> \u2014 those edits are replaced by the new default."
                    + "<br><b>Keep customised</b> \u2014 only the " + same.size()
                    + " untouched entr" + (same.size() == 1 ? "y is" : "ies are") + " updated."
                    + "</html>",
                    "Apply to existing copies", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE, null, opts, opts[1]);
            if (k == 2 || k < 0) return;
            alsoCustomised = (k == 0);
        }
        java.util.List<Task> targets = new ArrayList<>(same);
        if (alsoCustomised) targets.addAll(customised);
        for (Task q : targets) {
            q.getTaskTriggers().clear();
            for (main.watchers.Trigger t : libTask.getTaskTriggers())
                if (t != null) q.getTaskTriggers().add(new main.watchers.Trigger(t));
        }
        listTaskList.repaint();
        showToast("Updated " + targets.size() + " queue entr" + (targets.size() == 1 ? "y" : "ies")
                + (alsoCustomised || customised.isEmpty() ? ""
                   : ", kept " + customised.size() + " customised"), anchor, true);
    }

    /**
     * TASK LIST edit: binds to THIS queue entry only. Shows the library default as ticked boxes
     * you can untick, plus every global check as an unticked box - opting one in here makes it a
     * task trigger for this entry, without it becoming always-on.
     */
    private void openInstanceTaskTriggers(Task entry, JComponent anchor) {
        if (entry == null) { showToast("Select a queued task first", anchor, false); return; }
        Task lib = findLibraryTaskById(entry.getId());

        java.util.LinkedHashMap<String, main.watchers.Trigger> offer = new java.util.LinkedHashMap<>();
        if (lib != null)
            for (main.watchers.Trigger t : lib.getTaskTriggers())
                if (t != null) offer.put(t.describe(), t);
        for (main.watchers.Trigger t : globalTriggers)
            if (t != null) offer.putIfAbsent(t.describe(), t);
        if (offer.isEmpty()) {
            showToast("No task defaults or global checks to draw from yet", anchor, false);
            return;
        }
        java.util.Set<String> on = new java.util.HashSet<>();
        for (main.watchers.Trigger t : entry.getTaskTriggers())
            if (t != null) on.add(t.describe());

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        java.util.List<JCheckBox> boxes = new ArrayList<>();
        java.util.List<main.watchers.Trigger> order = new ArrayList<>();
        int libCount = lib == null ? 0 : lib.getTaskTriggers().size();
        int idx = 0;
        for (java.util.Map.Entry<String, main.watchers.Trigger> e : offer.entrySet()) {
            if (idx == 0 && libCount > 0) list.add(sectionLabel("From this task's library default"));
            if (idx == libCount) list.add(sectionLabel("Your global checks \u2014 opt in for this entry"));
            JCheckBox cb = new JCheckBox(e.getKey(), on.contains(e.getKey()));
            cb.setOpaque(false);
            cb.setForeground(TEXT_MAIN);
            boxes.add(cb);
            order.add(e.getValue());
            list.add(cb);
            idx++;
        }
        JScrollPane sp = Theme.thinScrollbars(new JScrollPane(list));
        sp.setPreferredSize(new Dimension(520, 340));
        sp.setBorder(BorderFactory.createLineBorder(COLOR_BORDER_DIM));

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setOpaque(false);
        JLabel note = new JLabel("<html>These apply to <b>this queue entry only</b> \u2014 another "
                + "copy of the same task keeps its own set.<br>They run for as long as this task "
                + "is executing.</html>");
        note.setForeground(TEXT_DIM);
        root.add(note, BorderLayout.NORTH);
        root.add(sp, BorderLayout.CENTER);

        if (JOptionPane.showConfirmDialog(this, root,
                "Task triggers \u2014 " + entry.getName() + " (this entry)",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
            return;

        entry.getTaskTriggers().clear();
        for (int i = 0; i < boxes.size(); i++)
            if (boxes.get(i).isSelected())
                entry.getTaskTriggers().add(new main.watchers.Trigger(order.get(i)));
        saveAll(false);
        listTaskList.repaint();
        showToast(entry.getTaskTriggers().size() + " task trigger(s) on this entry", anchor, true);
    }

    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT_DIM);
        l.setFont(new Font("Segoe UI", Font.BOLD, 10));
        l.setBorder(new EmptyBorder(8, 0, 2, 0));
        return l;
    }

    /** The library task an entry was copied from, or null if it's been removed since. */
    private Task findLibraryTaskById(String id) {
        if (id == null) return null;
        for (Task t : libraryAll) if (t != null && id.equals(t.getId())) return t;
        return null;
    }

    /** v1.60: everything a card can ask the menu to do - one shared wiring for grid + strip. */
    private final main.menu.components.MarketCard.Callbacks cardCallbacks =
            new main.menu.components.MarketCard.Callbacks() {
        @Override public void onDownload(main.market.ScriptListing l) {
            importListing(l, marketAnchor());
        }
        @Override public void onPublish(main.market.ScriptListing l) {
            publishStagedListing(l);
        }
        @Override public void onUnpublish(main.market.ScriptListing l, JComponent src) {
            unpublishListing(l, src);
        }
        @Override public void onRate(main.market.ScriptListing l, int stars) {
            rateListing(l, stars);
        }
        @Override public void onToggleFavorite(main.market.ScriptListing l) {
            toggleFavorite(l);
        }
        @Override public void onOpenDetails(main.market.ScriptListing l, boolean focusComments) {
            openListingDetail(l, focusComments);
        }
        @Override public void onOpenProfile(main.market.ScriptListing l) {
            if (l != null) openUserProfile(l.author);
        }
        @Override public void onPostComment(main.market.ScriptListing l, String body,
                                            main.menu.components.MarketCard card) {
            postCardComment(l, body, card);
        }
        @Override public void onSetIcon(main.market.ScriptListing l) {
            // v1.61: the icon lives on the card now - one surface for everything card-shaped
            openCardBuilder(l);
        }
        @Override public void onBuildCard(main.market.ScriptListing l) {
            openCardBuilder(l);
        }
        @Override public void onDeleteLocal(main.market.ScriptListing l) {
            deleteLocalListing(l);
        }
        @Override public void onContextMenu(main.market.ScriptListing l, MouseEvent e,
                                            JComponent src) {
            showMarketRowMenu(l, e, src);
        }
        @Override public void onRemoveListing(main.market.ScriptListing l, JComponent src) {
            removeListingWithChoice(l, src);
        }
        @Override public java.util.List<main.market.ScriptListing> onListVersions(
                main.market.ScriptListing l) {
            return versionsOf(l);
        }
        @Override public void onShowVersion(main.market.ScriptListing l) {
            // Showing another version is just opening its detail - the grid keeps showing the
            // newest, so picking an old build never silently changes what "the" card means.
            openListingDetail(l, false);
        }
        @Override public void onUnpublishToLocal(main.market.ScriptListing l) {
            unpublishToLocal(l);
        }
        @Override public void onVault(main.market.ScriptListing l) {
            vaultListing(l);
        }
        @Override public boolean isServerPage() {
            return marketRepo instanceof main.market.HttpRepository;
        }
        @Override public void onSetBuilt(main.market.ScriptListing l, boolean built) {
            setCardBuilt(l, built);
        }
        @Override public boolean isOwn(main.market.ScriptListing l) {
            return isOwnListing(l);
        }
        @Override public boolean canRate(main.market.ScriptListing l) {
            // published rows only (server or shared folder), never your own listing
            return l != null && !"local".equals(l.origin) && !isOwnListing(l);
        }
        @Override public boolean canComment(main.market.ScriptListing l) {
            return l != null && "server".equals(l.origin)
                    && marketRepo instanceof main.market.HttpRepository
                    && main.market.ServerAccount.isLoggedIn();
        }
    };

    /** v1.63: the market server to talk to for profiles - active repo, else session, else default. */
    private String profileServerUrl() {
        if (marketRepo instanceof main.market.HttpRepository)
            return ((main.market.HttpRepository) marketRepo).baseUrl();
        String s = main.market.ServerAccount.session().baseUrl;
        return (s == null || s.isEmpty()) ? DEFAULT_MARKET_SERVER_URL : s;
    }

    /** v1.63: fetch + show a scripter's public profile (author-link click on a card). */
    private void openUserProfile(String author) {
        if (author == null || author.trim().isEmpty()) return;
        final String name = author.trim();
        final String url = profileServerUrl();
        new Thread(() -> {
            try {
                String json = new main.market.ServerAccount(url).fetchUserProfile(name);
                SwingUtilities.invokeLater(() -> main.menu.components.ProfileDialog.show(
                        SwingUtilities.getWindowAncestor(this), name, json));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> showToast(
                        "Couldn't load " + name + "'s profile: " + ex.getMessage(),
                        marketAnchor(), false));
            }
        }, "DreamMan-Profile").start();
    }

    /**
     * v1.63: edit MY public bio (Status tab, next to the account card - it's account-adjacent).
     * Prefills from the live profile, saves via PUT /me/profile. A soft 500-char client cap keeps
     * the payload sane; the server's own limit (if stricter) surfaces through its error message.
     */
    private void openMyProfileEditor(JComponent anchor) {
        if (!main.market.ServerAccount.isLoggedIn()) {
            showToast("Log in to your DreamMan account first", anchor, false);
            return;
        }
        final String me = main.market.ServerAccount.username();
        final String url = profileServerUrl();
        new Thread(() -> {
            String bio = "";
            String fetched = null;
            try {
                fetched = new main.market.ServerAccount(url).fetchUserProfile(me);
                com.google.gson.JsonElement root = com.google.gson.JsonParser.parseString(fetched);
                if (root != null && root.isJsonObject()) {
                    com.google.gson.JsonElement b = root.getAsJsonObject().get("bio");
                    if (b != null && !b.isJsonNull() && b.isJsonPrimitive()) bio = b.getAsString();
                }
            } catch (Exception ignored) { /* no profile yet / fetch failed -> start empty */ }
            final String bio0 = bio == null ? "" : bio;
            final String fetchedJson = fetched;
            SwingUtilities.invokeLater(() -> {
                JTextArea area = new JTextArea(bio0, 6, 30);
                area.setLineWrap(true);
                area.setWrapStyleWord(true);
                JLabel count = new JLabel();
                Runnable upd = () -> {
                    int n = area.getText().length();
                    count.setText(n + " / 500");
                    count.setForeground(n > 500 ? Theme.AMBER : TEXT_DIM);
                };
                area.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                    public void insertUpdate(javax.swing.event.DocumentEvent e) { upd.run(); }
                    public void removeUpdate(javax.swing.event.DocumentEvent e) { upd.run(); }
                    public void changedUpdate(javax.swing.event.DocumentEvent e) { upd.run(); }
                });
                upd.run();
                JPanel p = new JPanel(new BorderLayout(0, 6));
                p.add(new JLabel("Your public bio (shown on your scripter profile):"),
                        BorderLayout.NORTH);
                p.add(Theme.thinScrollbars(new JScrollPane(area)), BorderLayout.CENTER);
                JPanel foot = new JPanel(new BorderLayout());
                foot.setOpaque(false);
                foot.add(count, BorderLayout.EAST);
                p.add(foot, BorderLayout.SOUTH);

                Object[] opts = {"Save", "View my profile", "Cancel"};
                int r = JOptionPane.showOptionDialog(this, p, "Public profile \u2014 " + me,
                        JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, opts, opts[0]);
                if (r == 1) {   // view as the public sees it (uses the fetch we already did)
                    if (fetchedJson != null)
                        main.menu.components.ProfileDialog.show(
                                SwingUtilities.getWindowAncestor(this), me, fetchedJson);
                    else openUserProfile(me);
                    return;
                }
                if (r != 0) return;
                String newBio = area.getText().trim();
                if (newBio.length() > 500) {
                    showToast("Bio is over 500 characters \u2014 trim it a little", anchor, false);
                    return;
                }
                new Thread(() -> {
                    try {
                        new main.market.ServerAccount(url).updateMyProfile(newBio);
                        SwingUtilities.invokeLater(() ->
                                showToast("Profile saved", anchor, true));
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> showToast(
                                "Couldn't save: " + ex.getMessage(), anchor, false));
                    }
                }, "DreamMan-ProfileSave").start();
            });
        }, "DreamMan-ProfileLoad").start();
    }

    /**
     * v1.64: open a listing's DETAIL view - it takes over the whole grid area (the owner's call:
     * one item's detail at a time, covering the other cards is fine, with an explicit way out).
     * The market header and the staging strip stay visible; ✕ or Esc returns to the grid with
     * the scroll position untouched (the grid component is merely hidden, not rebuilt).
     */
    private void openListingDetail(main.market.ScriptListing l, boolean focusComments) {
        if (l == null || marketCenterCards == null) return;
        if (marketDetail != null) marketCenterCards.remove(marketDetail);
        marketDetail = new main.menu.components.ListingDetailPanel(
                l, cardCallbacks, this::closeListingDetail, focusComments);
        detailListingId = l.id;
        marketCenterCards.add(marketDetail, "detail");
        ((CardLayout) marketCenterCards.getLayout()).show(marketCenterCards, "detail");
        // comments load lazily, same cache as before
        if ("server".equals(l.origin) && l.id != null) {
            marketDetail.setCommentsText(
                    marketCommentsCache.getOrDefault(l.id, "Loading comments\u2026"));
            if (!marketCommentsCache.containsKey(l.id)) fetchCommentsInto(l.id);
        }
    }

    /** v1.64: back to the grid (the detail's ✕ / Esc). */
    private void closeListingDetail() {
        if (marketCenterCards == null) return;
        detailListingId = null;
        ((CardLayout) marketCenterCards.getLayout()).show(marketCenterCards, "grid");
        if (marketDetail != null) {
            marketCenterCards.remove(marketDetail);
            marketDetail = null;
        }
    }

    /** Loads a script's comment thread into the cache, then refreshes its row. */
    private void fetchCommentsInto(String id) {
        if (!(marketRepo instanceof main.market.HttpRepository)) return;
        final main.market.HttpRepository repo = (main.market.HttpRepository) marketRepo;
        new Thread(() -> {
            String text;
            try {
                main.market.HttpRepository.CommentPage cp = repo.comments(id, 0, 30);
                if (cp.comments.isEmpty()) {
                    text = "No comments yet - be the first.";
                } else {
                    StringBuilder sb = new StringBuilder();
                    java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("d MMM HH:mm");
                    for (main.market.HttpRepository.Comment c : cp.comments)
                        sb.append(c.author).append("  \u00b7  ")
                          .append(fmt.format(new java.util.Date(c.at))).append("\n")
                          .append(c.body).append("\n\n");
                    text = sb.toString().trim();
                }
            } catch (Exception ex) {
                text = "Couldn't load comments: " + ex.getMessage();
            }
            final String out = text;
            SwingUtilities.invokeLater(() -> {
                marketCommentsCache.put(id, out);
                // v1.60: hand the text straight to the card, if it's still the open one
                if (marketDetail != null && id.equals(detailListingId))
                    marketDetail.setCommentsText(out);
            });
        }, "DreamMan-RowComments").start();
    }

    /** v1.60: posts a comment from a card's own input row (each expanded card has one). */
    private void postCardComment(main.market.ScriptListing l, String body,
                                 main.menu.components.MarketCard card) {
        if (l == null || l.id == null || body == null || body.trim().isEmpty()) return;
        if (!(marketRepo instanceof main.market.HttpRepository)) return;
        JComponent anchor = card != null ? card : marketAnchor();   // v1.64: detail passes null
        if (!main.market.ServerAccount.isLoggedIn()) {
            showToast("Log in to comment", anchor, false);
            return;
        }
        final String id = l.id;
        final String text = body.trim();
        final main.market.HttpRepository repo = (main.market.HttpRepository) marketRepo;
        new Thread(() -> {
            try {
                repo.addComment(id, text);
                marketCommentsCache.remove(id);   // stale - refetch with the new comment
                SwingUtilities.invokeLater(() -> fetchCommentsInto(id));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                        showToast("Comment failed: " + ex.getMessage(), anchor, false));
            }
        }, "DreamMan-PostComment").start();
    }

    /** v1.32b: the per-row market menu - actions depend on where the row lives and whose it is. */
    /** v1.60: the per-CARD market menu - actions depend on where the listing lives and whose it is. */
    private void showMarketRowMenu(main.market.ScriptListing l, MouseEvent me, JComponent src) {
        if (l == null) return;
        JPopupMenu menu = new JPopupMenu();

        JMenuItem miImport = new JMenuItem("Download / import into library");
        miImport.addActionListener(a -> importListing(l, src));
        menu.add(miImport);

        if ("local".equals(l.origin)) {
            JMenuItem miUpload = new JMenuItem("Publish\u2026");
            miUpload.addActionListener(a -> publishStagedListing(l));
            menu.add(miUpload);
            // v1.61: icon setting/removal moved into the Card Builder - one surface for the card
            JMenuItem miCard = new JMenuItem(l.cardReady
                    ? "Edit card\u2026" : "Build card\u2026 (required to publish)");
            miCard.addActionListener(a -> openCardBuilder(l));
            menu.add(miCard);
            JMenuItem miDeleteLocal = new JMenuItem("Delete local copy");
            miDeleteLocal.addActionListener(a -> deleteLocalListing(l));
            menu.add(miDeleteLocal);
        } else if ("folder".equals(l.origin)) {
            // v1.60: a shared folder has no logins, so removal is open to anyone with the folder
            menu.addSeparator();
            JMenuItem miRemove = new JMenuItem("Remove listing");
            miRemove.addActionListener(a -> {
                if (JOptionPane.showConfirmDialog(this,
                        "Remove \"" + l.name + "\" from the market?\n\n"
                                + "A shared folder has no logins, so this deletes it for everyone.",
                        "Remove listing", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
                try {
                    marketRepo.remove(l.id);
                    reloadMarket();
                } catch (Exception ex) {
                    showToast("Remove failed: " + ex.getMessage(), src, false);
                }
            });
            menu.add(miRemove);
        } else {
            // v1.73 (item 8): renaming a LIVE listing is gone. A name change on the market
            // breaks the link users have already seen and, with version stacking keyed on
            // name+author for display, silently re-groups lineages. Rename in the card builder
            // or the local folder instead - i.e. only while it isn't public.
}
        // v1.63: the "..." more-button passes a null event - anchor the popup under the button.
        if (me != null) menu.show(src, me.getX(), me.getY());
        else menu.show(src, 0, src.getHeight());
    }

    /** v1.32b: pushes a LOCAL script to the server (quota + auth enforced server-side). */
    /**
     * v1.61: THE publish gate. Every path that pushes a staged item to a market lands here first,
     * and an item without a finished card doesn't get past it - the roadmap's rule is that a
     * built card is a prerequisite for publishing, surfaced up front rather than failing at
     * upload time. Cardless items are offered the Card Builder on the spot (where a default or
     * random icon is one click), and card-ready items are dispatched to the right worker for
     * the current market source: the server, or a shared folder.
     */
    private void publishStagedListing(main.market.ScriptListing l) {
        if (l == null || !"local".equals(l.origin)) return;
        if (!l.cardReady) {
            Object[] opts = {"Open card builder", "Not now"};
            int r = JOptionPane.showOptionDialog(this,
                    "<html><b>\u201c" + escapeHtml(l.name) + "\u201d doesn't have a finished card "
                    + "yet.</b><br><br>Every market listing needs a built card before it can be "
                    + "published \u2014 an icon plus the details the market grid shows.<br>"
                    + "The builder makes the icon a one-click job (defaults, or a random one)."
                    + "</html>",
                    "Card required to publish", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.WARNING_MESSAGE, null, opts, opts[0]);
            if (r == 0) openCardBuilder(l);
            return;
        }
        if (marketRepo instanceof main.market.HttpRepository) {
            uploadToServer(l);
        } else if (marketRepo instanceof main.market.FolderRepository) {
            if (isStagingFolderTarget()) {
                showToast("The market source IS your staging folder \u2014 it's already there. "
                        + "Pick the server or a shared folder to publish somewhere.",
                        marketAnchor(), false);
                return;
            }
            publishToFolderMarket(l);
        } else {
            showToast("Pick a market source first (the server or folder icon)",
                    marketAnchor(), false);
        }
    }

    /** True when the folder market points at the local staging folder itself (v1.32b default). */
    private boolean isStagingFolderTarget() {
        return marketRepo instanceof main.market.FolderRepository && localMarketRepo != null
                && marketRepo.describe().equals(localMarketRepo.describe());
    }

    /** The server worker - assumes the card gate has already passed. */
    private void uploadToServer(main.market.ScriptListing l) {
        if (!main.market.ServerAccount.isLoggedIn()) {
            showToast("Log in to upload scripts", marketAnchor(), false);
            return;
        }
        if (!ensureConsent(main.privacy.Consent.MARKET_PUBLISH)) return;
        if (l.bundle == null) {
            showToast("That local file has no bundle to upload", marketAnchor(), false);
            return;
        }
        final String stagedId = l.id;
        new Thread(() -> {
            try {
                marketRepo.publish(l);   // adopts the server's id onto l (v1.76)
                // ── v1.88: publishing MOVES the script; it doesn't copy it ──────────────
                // Until now the staged file stayed on disk and was merely HIDDEN while a
                // listing with the same id was live. That produced the exact confusion this
                // patch is fixing: the same script appearing (or not) in "my market-ready"
                // and in the local folder depending on whether you happened to be signed in.
                // One home per script: once the server has it, the local staging copy goes.
                // Nothing is at risk - the delete only runs AFTER publish() returned
                // successfully, and Unpublish stages a full copy straight back.
                if (localMarketRepo != null && stagedId != null) {
                    try {
                        localMarketRepo.remove(stagedId);
                    } catch (Exception rm) {
                        Logger.log(Logger.LogType.WARN,
                                "[Market] published, but couldn't clear the local copy: " + rm);
                    }
                }
                SwingUtilities.invokeLater(() -> {
                    markLibraryPublished(l.name);   // v1.49: drives the Published filter/tag
                    reloadMarket();
                    showToast("Published \"" + l.name + "\" \u2014 it lives on the server now "
                            + "(unpublish brings it back)", marketAnchor(), true);
                });
            } catch (Exception ex) {
                // v1.60: 409 (version integrity) and 403 (anti-plagiarism) come back with a real
                // explanation from the server - show it in full instead of a clipped toast.
                SwingUtilities.invokeLater(() -> showPublishError(ex, marketAnchor()));
            }
        }, "DreamMan-Upload").start();
    }

    /**
     * The shared-folder worker (v1.61) - assumes the card gate has already passed. Before this,
     * a staged item could only reach a folder market through the old direct-publish dialog;
     * routing it here keeps folder publishing alive under the mandatory-card rule. Publishing
     * keeps the staging id on purpose: the merged market view dedupes on id, so the item shows
     * once (as the folder card with its live stats), exactly like a server upload does.
     */
    private void publishToFolderMarket(main.market.ScriptListing l) {
        if (l.bundle == null) {
            showToast("That local file has no bundle to publish", marketAnchor(), false);
            return;
        }
        try {
            marketRepo.publish(l);
            markLibraryPublished(l.name);   // v1.49: drives the Published filter/tag
            reloadMarket();
            showToast("Published \"" + l.name + "\" to the shared folder", marketAnchor(), true);
        } catch (Exception ex) {
            showToast("Publish failed: " + ex.getMessage(), marketAnchor(), false);
        }
    }

    /**
     * v1.61: opens the Card Builder on a staged listing - the one surface for the icon, the
     * card's details, and the ready/not-ready switch the publish gate reads. The dialog stays a
     * pure view; this Host wiring is where its buttons actually touch the app.
     */
    private void openCardBuilder(main.market.ScriptListing l) {
        if (l == null || !"local".equals(l.origin)) return;
        main.menu.components.CardBuilderDialog.Host host =
                new main.menu.components.CardBuilderDialog.Host() {
            @Override public void saveListing(main.market.ScriptListing x) throws Exception {
                localMarketRepo.publish(x);   // same id = save-in-place in the staging folder
                reloadMarket();
            }
            @Override public void publishListing(main.market.ScriptListing x) {
                publishStagedListing(x);      // re-runs the gate (now card-ready) and dispatches
            }
            @Override public boolean canPublishNow() {
                if (marketRepo instanceof main.market.HttpRepository)
                    return main.market.ServerAccount.isLoggedIn();
                return marketRepo instanceof main.market.FolderRepository
                        && !isStagingFolderTarget();
            }
            @Override public String publishTargetName() {
                return marketRepo == null ? "\u2014" : marketRepo.describe();
            }
            @Override public String pickIconFile(JComponent anchor) {
                return pickListingIcon(anchor);
            }
        };
        new main.menu.components.CardBuilderDialog(
                SwingUtilities.getWindowAncestor(this), l, host).setVisible(true);
    }

    /**
     * v1.32b: the "My uploads" manager - your uploaded scripts with their download/rating stats,
     * how much of your quota you've used, and per-script Rename + Delete. Backed by the server's
     * GET /me/scripts (owner-scoped) and PUT/DELETE /scripts/{id}.
     */
/** Pulls everything from the repository (off the EDT - a shared folder can be slow). */
    private void reloadMarket() {
        if (marketRepo == null) return;
        // A SERVER market is a network call, and that needs consent first.
        if (marketRepo instanceof main.market.HttpRepository
                && !main.privacy.Consent.has(main.privacy.Consent.MARKET_BROWSE)) {
            lblMarketSource.setText("Not connected - you haven't agreed to send anything yet.");
            marketAll.clear();
            refreshMarketGrid();
            refreshReadyStrip();
            return;
        }
        final main.market.ScriptRepository repo = marketRepo;
        setStatus("Loading market...");
        new Thread(() -> {
            // ── v1.71: the split is BUILT vs UNBUILT, not which folder a file sits in.
            //
            // It used to be folder-based, and that was incoherent: localMarketRepo and the
            // folder-mode repo are built over the SAME directory, so in folder mode every staged
            // row id-matched a "published" row, got deduped away, and the market-ready strip was
            // permanently empty ("5 in the shared folder, 0 staged locally").
            //
            // The model now matches the actual workflow. One staging folder on disk, two views:
            //   · card not built yet -> the LOCAL FOLDER grid, where you edit it
            //   · card built         -> the MY MARKET-READY strip, on BOTH pages
            // and on the server page the grid is the live market. A staged copy whose id is
            // already on the server is hidden (not deleted), so it reappears intact if the server
            // ever loses it - matched on ID, never name+author, so renaming a local copy cannot
            // make it pop back as a duplicate.
            List<main.market.ScriptListing> published = new ArrayList<>();
            List<main.market.ScriptListing> staged = new ArrayList<>();
            final boolean serverMode = repo instanceof main.market.HttpRepository;
            // In folder mode the "published" repo IS the staging folder, so listing it again
            // would double every card. A live market only exists server-side.
            if (serverMode) published = repo.list();
            if (localMarketRepo != null) staged = localMarketRepo.list();
            for (main.market.ScriptListing l : published)
                if (l != null) l.origin = "server";

            // v1.88: publishing now MOVES the file to the server, so a staged copy sharing a
            // published id should no longer exist at all. This stays as a one-way tidy-up for
            // installs upgrading from v1.87 and earlier, where the copy was kept and hidden:
            // the leftovers are deleted on the first load rather than lingering invisibly.
            java.util.Set<String> publishedIds = new java.util.HashSet<>();
            for (main.market.ScriptListing p2 : published)
                if (p2 != null && p2.id != null) publishedIds.add(p2.id);

            List<main.market.ScriptListing> merged = new ArrayList<>(published);
            int builtCount = 0;
            for (main.market.ScriptListing l : staged) {
                if (l == null) continue;
                l.origin = "local";
                if (publishedIds.contains(l.id)) {
                    // legacy leftover from the copy-and-hide era - remove it for good (v1.88)
                    try {
                        if (localMarketRepo != null) localMarketRepo.remove(l.id);
                    } catch (Exception ignored) {}
                    continue;
                }
                if (l.cardReady) builtCount++;
                merged.add(l);
            }
            final List<main.market.ScriptListing> found = merged;
            final int nPublished = published.size();
            final int nBuilt = builtCount;
            final int nUnbuilt = found.size() - published.size() - builtCount;
            SwingUtilities.invokeLater(() -> {
                marketAll.clear();
                marketAll.addAll(found);
                if (serverMode) {
                    lblMarketSource.setText(repo.describe() + "  \u00b7  " + nPublished
                            + " on the market, " + nBuilt + " market-ready"
                            + (nPublished == 0 ? "  \u00b7  (server empty or unreachable)" : ""));
                } else {
                    lblMarketSource.setText(repo.describe() + "  \u00b7  " + nUnbuilt
                            + " in your local folder, " + nBuilt + " market-ready");
                }
                refreshMarketGrid();
                refreshReadyStrip();
                updateMarketButtons();
                setStatus(found.isEmpty() ? "Market is empty" : "Market loaded");
            });
        }, "DreamMan-Market").start();
    }

    /** v1.32b: hide buttons that can't do anything in the current view (your notes). */
    private void updateMarketButtons() {
        boolean serverMode = marketRepo instanceof main.market.HttpRepository;
    }

    private JTextArea privacyStatusArea;   // legacy (unused after the v1.32b checkbox redesign)
    private JPanel privacyContent;         // v1.32b: checkbox toggles + info, rebuilt on refresh

    /**
     * The Privacy tab (Patch B.12): what DreamMan holds, what you've agreed to, and the buttons to
     * take it all back. Everything here is reversible and immediate.
     */
    private JPanel createPrivacyTab() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(0, 0, 0, 0));   // B.16: embedded in Status
        panel.setOpaque(false);

        JLabel blurb = new JLabel("<html>DreamMan is <b>offline by default</b> - nothing about you "
                + "or your game leaves this PC unless you turn something on below."
                + "<br>Your OSRS character names are <b>never sent</b>: they're mapped to local "
                + "labels (\"Main\", \"Alt 1\") and only the label is used. Your bank PIN is "
                + "never saved to disk or sent anywhere, full stop.</html>");
        blurb.setForeground(TEXT_DIM);
        blurb.setBorder(new EmptyBorder(0, 0, 8, 0));

        privacyContent = new JPanel();
        privacyContent.setOpaque(false);
        privacyContent.setLayout(new BoxLayout(privacyContent, BoxLayout.Y_AXIS));
        privacyContent.setBorder(new EmptyBorder(4, 2, 4, 8));

        JButton btnWithdraw = createButton("Withdraw everything", new Color(90, 55, 20), null);
        btnWithdraw.setToolTipText("DreamMan goes back to being fully offline, immediately");
        btnWithdraw.addActionListener(e -> {
            main.privacy.Consent.withdrawAll();
            refreshPrivacy();
            showToast("All consent withdrawn - DreamMan is offline again", btnWithdraw, true);
        });

        JButton btnExport = createButton("Download my server data");
        btnExport.setToolTipText("Everything the server holds about you, as a JSON file");
        btnExport.addActionListener(e -> exportServerData(btnExport));

        JButton btnDeleteServer = createButton("Delete my server account", COLOR_BUTTON_RED, null);
        btnDeleteServer.addActionListener(e -> deleteServerAccount(btnDeleteServer));

        JButton btnWipeLocal = createButton("Forget my characters", new Color(90, 55, 20), null);
        btnWipeLocal.setToolTipText("Clears the local OSRS-name-to-label map on this PC");
        btnWipeLocal.addActionListener(e -> {
            main.privacy.CharacterMap.forgetAll();
            refreshPrivacy();
            showToast("Local character labels cleared", btnWipeLocal, true);
        });

        JPanel btns = new JPanel(new GridLayout(1, 4, 6, 0));
        btns.setOpaque(false);
        btns.add(btnWithdraw);
        btns.add(btnExport);
        btns.add(btnWipeLocal);
        btns.add(btnDeleteServer);

        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setOpaque(false);
        body.add(blurb, BorderLayout.NORTH);
        body.add(Theme.thinScrollbars(new JScrollPane(privacyContent)), BorderLayout.CENTER);
        body.add(btns, BorderLayout.SOUTH);

        panel.add(createSubtitle("Privacy"), BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);

        refreshPrivacy();
        return panel;
    }

    /** v1.32b: rebuilds the privacy panel as real toggles with a risk blurb under each. */
    private void refreshPrivacy() {
        if (privacyContent == null) return;
        privacyContent.removeAll();

        privacyContent.add(privacySectionLabel("What you've agreed to"));
        if (!main.privacy.Consent.anyNetworkConsent()) {
            JLabel offline = new JLabel("DreamMan is currently OFFLINE \u2014 nothing is sent anywhere.");
            offline.setForeground(new Color(0x7FBF7F));
            offline.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            offline.setAlignmentX(Component.LEFT_ALIGNMENT);
            offline.setBorder(new EmptyBorder(2, 2, 6, 0));
            privacyContent.add(offline);
        }

        privacyContent.add(consentToggle(main.privacy.Consent.MARKET_BROWSE,
                "Browse & download scripts",
                "Lets DreamMan load the market list and download others' scripts. Sends only your "
                + "anonymous install id and what you search for \u2014 never your account, character "
                + "or game data. Turning this off hides the online market (the local folder market "
                + "still works).", false));

        privacyContent.add(consentToggle(main.privacy.Consent.MARKET_PUBLISH,
                "Publish my scripts",
                "Lets you upload your own tasks/presets to the market under your account name. Only "
                + "what you explicitly choose to publish is sent. Off means you can browse but not "
                + "publish.", false));

        privacyContent.add(consentToggle(main.privacy.Consent.CLOUD_SYNC,
                "Cloud-sync my setup",
                "Backs up your task profiles to the server so you can restore them on another PC. "
                + "The data is encrypted on THIS machine first \u2014 the server stores only "
                + "ciphertext it can't read. Off keeps everything on this PC only.", false));

        privacyContent.add(consentToggle(main.privacy.Consent.LINK_CHARACTER_NAME,
                "Send REAL character names  (not recommended)",
                "\u26a0 Sends your actual OSRS character names to the server instead of local labels "
                + "(\"Main\", \"Alt 1\"). This creates a permanent server-side record linking your real "
                + "game accounts to botting. Leave this OFF unless you have a specific reason \u2014 "
                + "with it off, your names never leave this PC.", true));

        privacyContent.add(Box.createVerticalStrut(6));
        privacyContent.add(privacySectionLabel("What never leaves this PC (regardless of the above)"));
        privacyContent.add(privacyInfoBullet("Your bank PIN \u2014 held in memory only, never written "
                + "to disk or uploaded. Re-checked before every upload."));
        privacyContent.add(privacyInfoBullet("Your OSRS character names \u2014 mapped to local labels; "
                + "only the label is ever sent (unless you turn the option above on)."));
        privacyContent.add(privacyInfoBullet("status.json (your live position/task) \u2014 written "
                + "locally for your own dashboard, never uploaded."));

        privacyContent.add(Box.createVerticalStrut(6));
        privacyContent.add(privacySectionLabel("Your characters, as the server would see them"));
        Map<String, String> map = main.privacy.CharacterMap.all();
        if (map.isEmpty()) {
            privacyContent.add(privacyInfoBullet("(none yet)"));
        } else {
            boolean realNames = main.privacy.Consent.has(main.privacy.Consent.LINK_CHARACTER_NAME);
            for (Map.Entry<String, String> e : map.entrySet())
                privacyContent.add(privacyInfoBullet(e.getKey() + "  \u2192  sent as \""
                        + (realNames ? e.getKey() : e.getValue()) + "\""));
        }

        privacyContent.add(Box.createVerticalStrut(6));
        privacyContent.add(privacySectionLabel("Your account"));
        privacyContent.add(privacyInfoBullet(main.market.ServerAccount.isLoggedIn()
                ? "Signed in as " + main.market.ServerAccount.username()
                : "Not signed in to any server."));
        privacyContent.add(privacyInfoBullet("Local files: " + LocalStore.getRoot().getAbsolutePath()));

        privacyContent.revalidate();
        privacyContent.repaint();
    }

    /** One consent toggle: a bold checkbox plus a wrapped risk blurb beneath it. */
    private JComponent consentToggle(String purpose, String title, String blurb, boolean warn) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBorder(new EmptyBorder(6, 0, 6, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JCheckBox cb = new JCheckBox(title, main.privacy.Consent.has(purpose));
        cb.setOpaque(false);
        cb.setForeground(warn ? new Color(0xE0, 0x9A, 0x4B) : TEXT_MAIN);
        cb.setFont(new Font("Segoe UI", Font.BOLD, 12));
        cb.setAlignmentX(Component.LEFT_ALIGNMENT);
        cb.addActionListener(e -> {
            boolean want = cb.isSelected();
            if (want && warn) {
                int ok = JOptionPane.showConfirmDialog(this,
                        "<html><body style='width:360px'><b>" + title + "</b><br><br>"
                                + blurb + "<br><br>Are you sure you want to turn this on?</body></html>",
                        "Turn on real character names?", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (ok != JOptionPane.YES_OPTION) { cb.setSelected(false); return; }
            }
            main.privacy.Consent.set(purpose, want);
            onAccountChanged();   // market-browse consent gates the online market
            refreshPrivacy();
        });
        row.add(cb);

        JLabel b = new JLabel("<html><div style='width:540px'>" + blurb + "</div></html>");
        b.setForeground(TEXT_DIM);
        b.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        b.setBorder(new EmptyBorder(1, 22, 0, 0));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(b);
        return row;
    }

    private JComponent privacySectionLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setForeground(Theme.ACCENT);
        l.setFont(new Font("Segoe UI", Font.BOLD, 11));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_BORDER_DIM),
                new EmptyBorder(8, 0, 3, 0)));
        return l;
    }

    private JComponent privacyInfoBullet(String text) {
        JLabel l = new JLabel("<html><div style='width:540px'>\u2022 " + text + "</div></html>");
        l.setForeground(TEXT_DIM);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(2, 6, 0, 0));
        return l;
    }

    /** Downloads everything the server holds about you. */
    private void exportServerData(JComponent anchor) {
        if (!main.market.ServerAccount.isLoggedIn()) {
            showToast("Sign in to your DreamMan account first", anchor, false);
            return;
        }
        new Thread(() -> {
            try {
                String json = new main.market.ServerAccount(
                        main.market.ServerAccount.session().baseUrl).exportMyData();
                java.io.File out = new java.io.File(LocalStore.getRoot(),
                        "my-server-data.json");
                java.nio.file.Files.write(out.toPath(),
                        json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "Everything the server holds about you was saved to:\n"
                                + out.getAbsolutePath(),
                        "Your data", JOptionPane.INFORMATION_MESSAGE));
            } catch (Throwable ex) {
                SwingUtilities.invokeLater(() ->
                        showToast("Couldn't export: " + ex.getMessage(), anchor, false));
            }
        }, "DreamMan-Export-Data").start();
    }

    /** Erases the account and everything attached to it. */
    private void deleteServerAccount(JComponent anchor) {
        if (!main.market.ServerAccount.isLoggedIn()) {
            showToast("Sign in first", anchor, false);
            return;
        }
        int ok = JOptionPane.showConfirmDialog(this,
                "<html>Permanently delete your DreamMan server account?<br><br>"
                        + "This erases your account, your cloud profiles, your ratings and "
                        + "<b>every script you've published</b>.<br>It cannot be undone.</html>",
                "Delete everything", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;

        new Thread(() -> {
            try {
                new main.market.ServerAccount(main.market.ServerAccount.session().baseUrl)
                        .deleteEverything();
                SwingUtilities.invokeLater(() -> {
                    refreshPrivacy();
                    showToast("Account and all server data deleted", anchor, true);
                });
            } catch (Throwable ex) {
                SwingUtilities.invokeLater(() ->
                        showToast("Couldn't delete: " + ex.getMessage(), anchor, false));
            }
        }, "DreamMan-Delete-Account").start();
    }

    /**
     * The consent dialog (Patch B.12). Shown BEFORE anything is ever sent, itemised, opt-in, with
     * every box unticked. Declining is a first-class outcome: DreamMan simply stays offline.
     *
     * @return true if the person granted at least the purpose we needed.
     */
    private boolean askConsent(String neededPurpose) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);

        JLabel intro = new JLabel("<html><b>Before DreamMan sends anything, anywhere.</b><br><br>"
                + "By default DreamMan is completely offline - nothing about you or your game "
                + "leaves this PC. Each item below is a separate choice, and they all start OFF."
                + "<br>You can change or withdraw any of them later in <b>Settings \u2192 "
                + "Privacy</b>, and withdrawal takes effect immediately.<br>&nbsp;</html>");
        intro.setForeground(TEXT_MAIN);
        intro.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(intro);

        String[] purposes = {
                main.privacy.Consent.MARKET_BROWSE,
                main.privacy.Consent.MARKET_PUBLISH,
                main.privacy.Consent.CLOUD_SYNC,
                main.privacy.Consent.LINK_CHARACTER_NAME,
        };
        String[] titles = {
                "Browse & download community scripts",
                "Publish my own scripts",
                "Save my DreamMan setup to my account (cloud sync)",
                "Send my REAL character names (not recommended)",
        };

        Map<String, JCheckBox> boxes = new LinkedHashMap<>();
        for (int i = 0; i < purposes.length; i++) {
            JCheckBox cb = new JCheckBox(titles[i], main.privacy.Consent.has(purposes[i]));
            cb.setOpaque(false);
            cb.setForeground(purposes[i].equals(main.privacy.Consent.LINK_CHARACTER_NAME)
                    ? new Color(230, 140, 90) : TEXT_MAIN);
            cb.setFont(new Font("Segoe UI", Font.BOLD, 13));
            cb.setAlignmentX(Component.LEFT_ALIGNMENT);

            JTextArea detail = new JTextArea(main.privacy.Consent.describe(purposes[i]));
            detail.setEditable(false);
            detail.setOpaque(false);
            detail.setForeground(TEXT_DIM);
            detail.setFont(new Font("Consolas", Font.PLAIN, 11));
            detail.setBorder(new EmptyBorder(0, 26, 10, 0));
            detail.setAlignmentX(Component.LEFT_ALIGNMENT);

            panel.add(cb);
            panel.add(detail);
            boxes.put(purposes[i], cb);
        }

        JScrollPane scroll = new JScrollPane(panel);
        scroll.setPreferredSize(new Dimension(640, 460));
        scroll.setBorder(null);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);

        int r = JOptionPane.showConfirmDialog(this, scroll,
                "Your data \u2014 your choice", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        main.privacy.Consent.markAsked();
        if (r != JOptionPane.OK_OPTION) return false;   // declining is fine; we stay offline

        for (Map.Entry<String, JCheckBox> e : boxes.entrySet())
            main.privacy.Consent.set(e.getKey(), e.getValue().isSelected());

        return neededPurpose == null || main.privacy.Consent.has(neededPurpose);
    }

    /** Asks only if we haven't got the consent this action needs. @return may we proceed? */
    private boolean ensureConsent(String purpose) {
        if (main.privacy.Consent.has(purpose)) return true;
        return askConsent(purpose);
    }

    /** Import a listing into the Task Library, after showing exactly what it will add. */
    private void importListing(main.market.ScriptListing l, JComponent anchor) {
        if (l == null) { showToast("Pick a script first", anchor, false); return; }
        // v1.64: a trigger listing installs into your always-on checks, not the library
        if ("trigger".equalsIgnoreCase(l.kind)) {
            if (l.bundle == null || l.bundle.globalTriggers == null
                    || l.bundle.globalTriggers.isBlank()) {
                showToast(l.vipOnly ? "VIP only \u2014 upgrade to download this one"
                        : "That listing has no check inside it", anchor, false);
                return;
            }
            importTriggerListing(l, anchor);
            return;
        }
        // v1.49: you CAN now download your own uploads (e.g. onto another device). Name-collision
        // handling below stops it from silently duplicating what you already have.
        if (l.bundle == null || l.bundle.tasks == null || l.bundle.tasks.isEmpty()) {
            // Patch B.16: the server strips the bundle from VIP listings for non-VIP callers.
            showToast(l.vipOnly ? "VIP only \u2014 upgrade to download this one"
                    : "That listing has no tasks", anchor, false);
            return;
        }

        // Show what it actually does BEFORE importing. A bundle can't run code - it can only build
        // actions DreamMan already ships - but it could still do something you don't want (drop
        // items, say), so the action types are laid out plainly.
        StringBuilder sb = new StringBuilder("<html><b>" + l.name + "</b> by " + l.author + "<br><br>");
        if (l.description != null && !l.description.isEmpty())
            sb.append("<i>").append(l.description).append("</i><br><br>");
        sb.append(l.summarise()).append("<br><br><b>It will add these kinds of action:</b><br>");
        for (String t : l.actionTypes()) sb.append("&nbsp;&nbsp;\u2022 ").append(t).append("<br>");
        if (l.bundle.globalTriggers != null && !l.bundle.globalTriggers.isBlank())
            sb.append("<br>It also brings the author's always-on <b>triggers</b>.<br>");
        sb.append("<br>It arrives as <b>one</b> library task: \u201c").append(l.name).append("\u201d.");
        sb.append("<br><br>Import into your Task Library?</html>");

        int ok = JOptionPane.showConfirmDialog(this, new JLabel(sb.toString()),
                "Import \"" + l.name + "\"", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;

        // ── Patch B.17: a download is ONE task, whatever the author's queue looked like ──
        // The old loop added every TaskData in the bundle to the library separately, so a
        // published queue of 9 steps dumped 9 rows ("Wait", "Wait", "Loot Bones"...) instead of
        // the script you actually downloaded. Now the whole bundle is flattened - in order,
        // honouring each step's xN repeat - into a single task named after the listing.
        Task merged = mergeBundleIntoOneTask(l);
        if (merged == null) { showToast("Couldn't rebuild that script's actions", anchor, false); return; }
        merged.setVersion(l.version <= 0 ? 1.0 : l.version);   // v1.49: carry the market version

        // v1.49: name-collision handling. If a library task already has this name, don't silently
        // duplicate - offer to overwrite yours, keep both (rename the download), or cancel. The
        // version numbers are shown so you can tell which copy is which.
        Task existing = null;
        for (Task t : libraryAll)
            if (t != null && merged.getName().equalsIgnoreCase(t.getName())) { existing = t; break; }
        if (existing != null) {
            Object[] opts = {"Overwrite mine", "Keep both (rename)", "Cancel"};
            int choice = JOptionPane.showOptionDialog(this,
                    "<html>You already have a task named <b>" + merged.getName() + "</b> (v"
                    + String.format("%.1f", existing.getVersion()) + ").<br>"
                    + "This download is <b>v" + String.format("%.1f", merged.getVersion())
                    + "</b>.<br><br>Overwrite your copy, keep both (the download is renamed), or "
                    + "cancel?</html>",
                    "You already have \"" + merged.getName() + "\"",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, opts, opts[0]);
            if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) return;
            if (choice == 0) libraryAll.remove(existing);                 // overwrite
            else merged.setName(uniqueLibraryName(merged.getName()));     // keep both
        }

        libraryAdd(merged);
        refreshTaskLibrary();
        // v1.64: a downloaded script may carry SCRIPT triggers (and optionally the author's global
        // checks). A download flattens to ONE library task, which has no script-scope of its own,
        // so offer these as always-on checks - installed DISABLED, nothing runs until you turn it
        // on. Per-action triggers already rode in with the task's actions and need no prompt.
        offerBundledChecksOnImport(l, anchor);
        marketRepo.noteDownload(l.id);
        reloadMarket();
        saveAll(false);
        showToast("Imported \"" + merged.getName() + "\" ("
                + (merged.getActions() == null ? 0 : merged.getActions().size())
                + " actions) into your library", anchor, true);
    }

    /** Hard ceiling on actions produced by repeat-expansion during an import (sanity guard). */
    private static final int IMPORT_MAX_ACTIONS = 400;

    /**
     * Flattens a listing's bundle into ONE library task (Patch B.17).
     *
     * <p>Single-task bundles import as-is (their own repeat preserved), renamed to the listing
     * so the library row matches what was downloaded. Multi-task bundles - published queues -
     * are flattened in queue order; a step with repeat xN contributes its actions N times, so
     * the merged task plays out exactly like the author's queue did on one loop. Expansion is
     * capped at {@link #IMPORT_MAX_ACTIONS} so a pathological "repeat x9999" listing can't
     * balloon the library.
     */
    private Task mergeBundleIntoOneTask(main.market.ScriptListing l) {
        List<Task> parts = new ArrayList<>();
        for (TaskData td : l.bundle.tasks) {
            Task t = ProfileCodec.fromData(td);
            if (t != null) parts.add(t);
        }
        if (parts.isEmpty()) return null;

        String name = (l.name == null || l.name.trim().isEmpty())
                ? parts.get(0).getName() : l.name.trim();
        String desc = (l.description == null || l.description.trim().isEmpty())
                ? ("Downloaded from the market \u00b7 by " + l.author) : l.description.trim();

        Task merged;
        if (parts.size() == 1) {
            merged = parts.get(0);              // already one task - keep its repeat/timer/etc.
            merged.setName(name);
            merged.setDescription(desc);
        } else {
            List<Action> actions = new ArrayList<>();
            outer:
            for (Task part : parts) {
                int passes = Math.max(1, part.getRepeat());
                for (int p = 0; p < passes; p++) {
                    if (part.getActions() != null)
                        for (Action a : part.getActions()) {
                            if (a == null) continue;
                            if (actions.size() >= IMPORT_MAX_ACTIONS) break outer;
                            actions.add(a.copyDeep());
                        }
                }
            }
            merged = new Task(name, desc, actions, "Running " + name + "...");
        }
        merged.regenerateId();          // a fresh identity so it can't collide with your own tasks
        merged.setOrigin("imported");
        merged.setDownloaded(true);     // v1.49: drives the Downloaded filter
        return merged;
    }

    /**
     * v1.33: stage a library task into the LOCAL market (your notes' "make market-ready").
     * Copies the task into local staging - it stays in the library - and lets you tag it as a
     * task (reusable building block) or a script (full single-purpose routine), which the server
     * caps separately. It lands in the market-ready row at the bottom of the Market tab
     * (v1.60), and v1.61 drops you straight into its Card Builder, since a finished card is
     * now what makes it publishable.
     */
    /**
     * Stages a library task to the local market as {@code kind} ("task" or "script").
     *
     * <p>v1.65: the kind used to come from a modal fired by the "\u2191 Market-ready" button. It
     * now arrives from the inspector's inline selector, which also shows the resulting state, so
     * this method just stages - no question to ask, and nothing to cancel out of.
     */
    private void makeMarketReady(Task t, String kind, JComponent anchor) {
        if (t == null) { showToast("Select a library task first", anchor, false); return; }
        kind = "script".equalsIgnoreCase(kind) ? "script" : "task";

        String acctName = main.market.ServerAccount.isLoggedIn()
                ? main.market.ServerAccount.username() : null;
        String who = safePlayerName();
        String author = (acctName != null && !acctName.isEmpty()) ? acctName
                : (who == null || who.isEmpty() ? "Anonymous" : who);

        main.market.ScriptListing listing = new main.market.ScriptListing();
        listing.name = t.getName();
        listing.author = author;
        listing.description = t.getDescription() == null ? "" : t.getDescription();
        listing.kind = kind;
        listing.origin = "local";
        listing.tags = new ArrayList<>(t.getTags());   // v1.51: tags travel to the market

        main.data.store.ScriptBundle b = new main.data.store.ScriptBundle();
        b.name = listing.name;
        b.author = author;
        b.version = 1.0;
        b.description = listing.description;
        b.loops = 1;
        List<Task> one = new ArrayList<>();
        one.add(t);
        b.tasks = ProfileCodec.tasksToData(one);
        // v1.49: keep the task's own loop count (repeat) - the runtime tier cap prevents abuse,
        // so we no longer flatten it to 1 and break the script.
        listing.bundle = b;

        try {
            localMarketRepo.publish(listing);
            t.setMarketReady(true);   // v1.49: drives the Market-Ready filter
            saveAll(false);
            refilterLibrary();
            reloadMarket();
            // v1.61: publishing now requires a finished card, so go straight into building it -
            // the builder's Cancel still leaves the item staged (just card-less, shown as such)
            showToast("\"" + t.getName() + "\" is staged \u2014 build its card to make it "
                    + "publishable", anchor, true);
            openCardBuilder(listing);
        } catch (Exception ex) {
            showToast("Couldn't stage: " + ex.getMessage(), anchor, false);
        }
    }

    /**
     * v1.64: pick which always-on check to stage for the market. One check per listing - a
     * trigger has one main intention, and the card should make that single purpose obvious.
     */
    private void pickTriggerToStage(JComponent anchor) {
        List<main.watchers.Trigger> live = new ArrayList<>();
        for (main.watchers.Trigger t : globalTriggers) if (t != null) live.add(t);
        if (live.isEmpty()) {
            showToast("You have no always-on checks yet \u2014 build one above first", anchor, false);
            return;
        }
        String[] opts = new String[live.size()];
        for (int i = 0; i < live.size(); i++) opts[i] = live.get(i).describe();
        String pick = (String) JOptionPane.showInputDialog(this,
                "Which check do you want to publish?", "Publish a check",
                JOptionPane.PLAIN_MESSAGE, null, opts, opts[0]);
        if (pick == null) return;
        for (int i = 0; i < opts.length; i++)
            if (opts[i].equals(pick)) { stageTriggerForCardBuilder(live.get(i), anchor); return; }
    }

    /**
     * v1.64: stage ONE trigger as a market listing (kind = "trigger") and open its Card Builder -
     * the same v1.61 staging + mandatory-card flow tasks and scripts use. The publish payload is
     * the existing ScriptBundle shape with an empty task list and the trigger's JSON in
     * {@code globalTriggers} (via TriggerCodec), so every bit of publish/download/staging
     * plumbing - and the card's structure view - works on it unchanged. NOTE: uploading a
     * kind="trigger" listing to the CURRENT server (1.4.1) depends on the server accepting a
     * third kind - see SERVER-NOTES-v1_64-trigger-kind.md; shared-folder markets need nothing.
     */
    private void stageTriggerForCardBuilder(main.watchers.Trigger trig, JComponent anchor) {
        if (trig == null) return;
        String suggested = trig.describe();
        if (suggested.length() > 40) suggested = suggested.substring(0, 40).trim();
        String name = (String) JOptionPane.showInputDialog(this,
                "Name this check's market listing:", "Publish a check",
                JOptionPane.PLAIN_MESSAGE, null, null, suggested);
        if (name == null || name.trim().isEmpty()) return;
        name = name.trim();

        // same-name staging dedupe, exactly like task staging - open the existing card instead
        for (main.market.ScriptListing ex : marketAll) {
            if (ex != null && "local".equals(ex.origin) && ex.name != null
                    && ex.name.equalsIgnoreCase(name)) {
                showToast("\"" + name + "\" is already staged - opening its card", anchor, true);
                openCardBuilder(ex);
                return;
            }
        }

        String acctName = main.market.ServerAccount.isLoggedIn()
                ? main.market.ServerAccount.username() : null;
        String who = safePlayerName();
        String author = (acctName != null && !acctName.isEmpty()) ? acctName
                : (who == null || who.isEmpty() ? "Anonymous" : who);

        main.market.ScriptListing listing = new main.market.ScriptListing();
        listing.name = name;
        listing.author = author;
        listing.version = 1.0;
        listing.kind = "trigger";
        listing.origin = "local";
        listing.description = trig.describe();   // seed the card with what the check does

        main.data.store.ScriptBundle b = new main.data.store.ScriptBundle();
        b.name = listing.name;
        b.author = author;
        b.version = 1.0;
        b.description = listing.description;
        b.loops = 1;
        b.tasks = new ArrayList<>();             // a trigger listing carries no tasks
        List<main.watchers.Trigger> one = new ArrayList<>();
        one.add(trig);
        b.globalTriggers = main.watchers.TriggerCodec.toJson(one);
        listing.bundle = b;

        try {
            localMarketRepo.publish(listing);
            reloadMarket();
            openCardBuilder(listing);            // finish the card, then publish from there
        } catch (Exception ex) {
            showToast("Couldn't stage: " + ex.getMessage(), anchor, false);
        }
    }

    /**
     * v1.64: after importing a script, install any triggers its bundle carried - the author's
     * SCRIPT triggers (ran while their script ran) and any GLOBAL checks they chose to include -
     * as always-on checks, DISABLED. A downloaded script becomes one library task with no
     * script-scope, so always-on (off until you enable it) is the safe home; you can re-scope them
     * to a script yourself via the Task List tab's "Script triggers..." once you've built a queue.
     */
    private void offerBundledChecksOnImport(main.market.ScriptListing l, JComponent anchor) {
        if (l.bundle == null) return;
        java.util.List<main.watchers.Trigger> incoming = new ArrayList<>();
        try {
            if (l.bundle.scriptTriggers != null && !l.bundle.scriptTriggers.isBlank())
                incoming.addAll(main.watchers.TriggerCodec.fromJson(l.bundle.scriptTriggers));
            if (l.bundle.globalTriggers != null && !l.bundle.globalTriggers.isBlank())
                incoming.addAll(main.watchers.TriggerCodec.fromJson(l.bundle.globalTriggers));
        } catch (Exception ignored) { return; }
        if (incoming.isEmpty()) return;

        StringBuilder sb = new StringBuilder("<html>\u201c" + escapeHtml(l.name) + "\u201d also "
                + "brings " + incoming.size() + " always-on check"
                + (incoming.size() == 1 ? "" : "s") + ":<br>");
        for (main.watchers.Trigger t : incoming)
            if (t != null) sb.append("&nbsp;&nbsp;\u2022 ").append(escapeHtml(t.describe()))
                    .append("<br>");
        sb.append("<br>Install "
                + (incoming.size() == 1 ? "it" : "them") + " into your Checks tab? They'll be "
                + "<b>disabled</b> until you switch "
                + (incoming.size() == 1 ? "it" : "them") + " on.</html>");
        int ok = JOptionPane.showConfirmDialog(this, new JLabel(sb.toString()),
                "Install the script's checks?", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;
        int n = 0;
        for (main.watchers.Trigger t : incoming) {
            if (t == null) continue;
            t.setEnabled(false);
            globalTriggers.add(t);
            n++;
        }
        if (checksEditor != null) checksEditor.reload();
        saveAll(false);
        showToast("Added " + n + " check" + (n == 1 ? "" : "s")
                + " (disabled) to your Checks tab", anchor, true);
    }

    /**
     * v1.64: importing a kind="trigger" listing installs its check into your always-on checks
     * (the Checks tab) instead of the Task Library. Imported checks arrive DISABLED - nothing a
     * download brings should start acting on your account until you've read it and switched it
     * on yourself.
     */
    private void importTriggerListing(main.market.ScriptListing l, JComponent anchor) {
        List<main.watchers.Trigger> incoming;
        try {
            incoming = main.watchers.TriggerCodec.fromJson(l.bundle.globalTriggers);
        } catch (Exception ex) {
            showToast("Couldn't read that check: " + ex.getMessage(), anchor, false);
            return;
        }
        if (incoming == null || incoming.isEmpty()) {
            showToast("That listing has no check inside it", anchor, false);
            return;
        }
        StringBuilder sb = new StringBuilder("<html><b>" + escapeHtml(l.name) + "</b> by "
                + escapeHtml(l.author) + "<br><br>Installs "
                + (incoming.size() == 1 ? "this always-on check" : "these always-on checks")
                + " into your Checks tab:<br>");
        for (main.watchers.Trigger t : incoming)
            if (t != null) sb.append("&nbsp;&nbsp;\u2022 ").append(escapeHtml(t.describe()))
                    .append("<br>");
        sb.append("<br>It arrives <b>disabled</b> \u2014 review it, then switch it on yourself."
                + "<br><br>Install?</html>");
        int ok = JOptionPane.showConfirmDialog(this, new JLabel(sb.toString()),
                "Install \"" + l.name + "\"", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;

        int n = 0;
        for (main.watchers.Trigger t : incoming) {
            if (t == null) continue;
            t.setEnabled(false);                 // the safety default: nothing runs until YOU say
            globalTriggers.add(t);
            n++;
        }
        if (checksEditor != null) checksEditor.reload();
        saveAll(false);
        marketRepo.noteDownload(l.id);
        reloadMarket();
        showToast("Installed " + n + " check" + (n == 1 ? "" : "s")
                + " (disabled) \u2014 see the Checks tab", anchor, true);
    }

    /**
     * v1.61: the direct-publish path is retired - publishing anything now goes through staging
     * and the Card Builder, because a listing without a finished card can't be published at all.
     * This replaces the old publishItem/publishTasks pair: it stages a named set of tasks (a
     * preset, the queue, one library task) into the market-ready strip and drops straight into
     * the Card Builder, whose "Save & publish" completes what used to be the one-dialog flow.
     * Kind defaults sensibly (one task = task, several = script) and stays editable in the
     * builder; loop counts travel intact per the v1.49 runtime-clamp rule.
     */
    private void stageForCardBuilder(String defaultName, List<Task> tasks, JComponent anchor) {
        if (tasks == null || tasks.isEmpty()) {
            showToast("Nothing to stage - it's empty", anchor, false);
            return;
        }
        String name = defaultName == null || defaultName.isBlank() ? "My Script" : defaultName;

        // already staged under this name? then this IS its card-builder shortcut - never
        // silently pile up a second staging copy of the same thing
        for (main.market.ScriptListing ex : marketAll) {
            if (ex != null && "local".equals(ex.origin) && ex.name != null
                    && ex.name.equalsIgnoreCase(name)) {
                showToast("\"" + name + "\" is already staged - opening its card",
                        anchor, true);
                openCardBuilder(ex);
                return;
            }
        }

        // v1.32: the author is the signed-in account (the server authors from the token anyway)
        String acctName = main.market.ServerAccount.isLoggedIn()
                ? main.market.ServerAccount.username() : null;
        String who = safePlayerName();
        String author = (acctName != null && !acctName.isEmpty()) ? acctName
                : (who == null || who.isEmpty() ? "Anonymous" : who);

        main.market.ScriptListing listing = new main.market.ScriptListing();
        listing.name = name;
        listing.author = author;
        listing.version = 1.0;
        listing.kind = tasks.size() > 1 ? "script" : "task";   // editable in the builder
        listing.origin = "local";
        // v1.51: tags travel - seed the card with the union of the tasks' own tags
        java.util.LinkedHashSet<String> tagset = new java.util.LinkedHashSet<>();
        for (Task qt : tasks)
            if (qt != null) tagset.addAll(qt.getTags());
        listing.tags = new ArrayList<>(tagset);

        main.data.store.ScriptBundle b = new main.data.store.ScriptBundle();
        b.name = listing.name;
        b.author = author;
        b.version = listing.version;
        b.description = "";
        // v1.49: preserve the author's loop counts (queue loops + per-task repeat) - the tier
        // cap is enforced at RUNTIME on the runner's side, so intended loops travel with the
        // script and only over-cap use is prevented. Queue-level loops still default to 1 so
        // the importer picks their own.
        b.loops = 1;
        b.tasks = ProfileCodec.tasksToData(tasks);
        // v1.64: per-action triggers travel inside the tasks; the script's own SCRIPT triggers all
        // travel; global checks are opt-in (now a per-check pick, not all-or-nothing).
        if (!scriptTriggers.isEmpty())
            b.scriptTriggers = currentScriptTriggersJson();
        if (!globalTriggers.isEmpty()) {
            java.util.List<main.watchers.Trigger> picked = pickGlobalTriggersToBundle();
            if (picked == null) return;   // cancelled the whole publish
            if (!picked.isEmpty())
                b.globalTriggers = main.watchers.TriggerCodec.toJson(picked);
        }
        listing.bundle = b;

        try {
            localMarketRepo.publish(listing);
            reloadMarket();
            openCardBuilder(listing);   // publish happens from here, once the card is finished
        } catch (Exception ex) {
            showToast("Couldn't stage: " + ex.getMessage(), anchor, false);
        }
    }


    /** v1.49: a library name not already in use - appends " (2)", " (3)"… until unique. */
    private String uniqueLibraryName(String base) {
        String name = base;
        int n = 2;
        outer:
        while (true) {
            for (Task t : libraryAll)
                if (t != null && name.equalsIgnoreCase(t.getName())) {
                    name = base + " (" + (n++) + ")";
                    continue outer;
                }
            return name;
        }
    }

    /** v1.59: love/unlove a market listing on the server, then refresh the grid. */
    private void toggleFavorite(main.market.ScriptListing l) {
        if (l == null || !"server".equals(l.origin)) return;
        if (!(marketRepo instanceof main.market.HttpRepository)) return;
        if (!main.market.ServerAccount.isLoggedIn()) {
            showToast("Log in to favorite scripts", marketAnchor(), false);
            return;
        }
        final main.market.HttpRepository repo = (main.market.HttpRepository) marketRepo;
        new Thread(() -> {
            try {
                String resp = repo.favorite(l.id);
                boolean fav = resp != null && resp.contains("\"myFavorite\":true");
                int count = Math.max(0, l.favorites + (fav ? 1 : -1));
                try {
                    java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("\"favorites\":(\\d+)").matcher(resp == null ? "" : resp);
                    if (m.find()) count = Integer.parseInt(m.group(1));
                } catch (Throwable ignored) {}
                final boolean ff = fav; final int fc = count;
                SwingUtilities.invokeLater(() -> {
                    l.myFavorite = ff;
                    l.favorites = fc;
                    // v1.60: a grid rebuild both updates the heart and re-floats loved cards to
                    // the top (v1.59 behaviour); the open-comments card + scroll position survive.
                    refreshMarketGrid();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                        showToast("Favorite failed: " + ex.getMessage(), marketAnchor(), false));
            }
        }, "DreamMan-Favorite").start();
    }

    /** v1.49: flag any library task with this name as published (best-effort name match). */
    private void markLibraryPublished(String name) {
        if (name == null || name.isBlank()) return;
        boolean any = false;
        for (Task t : libraryAll)
            if (t != null && name.equalsIgnoreCase(t.getName()) && !t.isPublished()) {
                t.setPublished(true);
                any = true;
            }
        if (any) { saveAll(false); refilterLibrary(); }
    }

    /** Point the market at a different folder (or a server, when one exists). */
    // ── Patch B.16: source switching via the folder/server icons ──

    /** Switch to the local-folder source. If forcePick (right-click) or no folder chosen, ask. */
    /**
     * v1.70: switches the market to the local folder. There is no longer any way to choose a
     * different one: the old right-click JFileChooser was punishingly slow (~10s per action) and
     * let the market point outside the sanctioned scripts.path root, which SDN rules forbid.
     * The folder is created for you and is always the same place.
     */
    private void useFolderSource() {
        if (marketFolder == null || !marketFolder.isDirectory())
            marketFolder = defaultMarketFolder();
        if (marketFolder == null || !marketFolder.isDirectory()) {
            // A read-only install is the only way to land here; say so rather than silently
            // falling back to a picker that would produce a non-compliant path.
            showToast("Couldn't create the market folder under the script directory",
                    btnFolderSource, false);
            if (btnServerSource != null && marketRepo instanceof main.market.HttpRepository)
                btnServerSource.setSelected(true);
            return;
        }
        closeListingDetail();   // v1.70 (item 5): never leave a detail open across a source switch
        marketRepo = new main.market.FolderRepository(marketFolder);
        if (btnFolderSource != null) btnFolderSource.setSelected(true);
        reloadMarket();
        showToast("Market: local folder", btnFolderSource, true);
    }

    /** Switch to the server source (auto-linked; URL only changeable by admins via right-click). */
    private void useServerSource() {
        closeListingDetail();   // v1.70 (item 5): close any open card on switch
        String url = marketServerUrl;
        if (url == null || url.isEmpty()) {
            // no server configured yet - if admin, prompt; else explain
            if (main.market.Tier.isAdmin()) { changeServerUrl(); return; }
            showToast("No marketplace server is set for this build", btnServerSource, false);
            if (btnFolderSource != null) btnFolderSource.setSelected(true);
            return;
        }
        if (!ensureConsent(main.privacy.Consent.MARKET_BROWSE)) {
            if (btnFolderSource != null) btnFolderSource.setSelected(true);
            return;
        }
        marketRepo = new main.market.HttpRepository(url, main.market.ServerAccount.session().token);
        if (btnServerSource != null) btnServerSource.setSelected(true);
        reloadMarket();
        showToast("Market: server", btnServerSource, true);
    }

    /** Admins only: set/change the marketplace server URL. */
    private void changeServerUrl() {
        if (!main.market.Tier.isAdmin()) {
            showToast("Only admins can change the server URL", btnServerSource, false);
            return;
        }
        String url = JOptionPane.showInputDialog(this,
                "Marketplace server URL (admin):", marketServerUrl);
        if (url == null || url.trim().isEmpty()) return;
        marketServerUrl = url.trim();
        marketRepo = new main.market.HttpRepository(marketServerUrl, main.market.ServerAccount.session().token);
        if (btnServerSource != null) btnServerSource.setSelected(true);
        reloadMarket();
        showToast("Server URL set", btnServerSource, true);
    }

    /** The one renderer instance for the task library - hosts the default-task star (v1.30). */
    private LibraryCardRenderer libraryCardRenderer;
    /** The one renderer instance for the queue - hosts the -/+ loop steppers (v1.31). */
    private TaskCardRenderer taskCardRenderer;

    /** Updates the renderer's hover row/star and repaints only when it actually changed. */
    /**
     * Click handling on a market row: clicking a star rates the script (B.16, fixed B.17).
     * The renderer is laid out at the real cell size and asked which star was hit, so the
     * clickable zones are exactly the star shapes the user sees.
     */
    /** True when this listing was uploaded by the signed-in user (can't rate/needs owner tools). */
    private boolean isOwnListing(main.market.ScriptListing l) {
        if (l == null || l.author == null) return false;
        String me = main.market.ServerAccount.session().username;
        return me != null && !me.isEmpty() && me.equalsIgnoreCase(l.author);
    }

    /** Submit a rating for a listing (Patch B.16 - inline on the row). */
    private void rateListing(main.market.ScriptListing l, int stars) {
        if (l == null) return;
        // v1.60: rating is for PUBLISHED listings - the server's, or a shared folder's (the
        // folder market keeps one rating file per install) - never your local staging or your own.
        if ("local".equals(l.origin)) {
            showToast("Rating is for published listings (not your staging)", marketAnchor(), false);
            return;
        }
        if (isOwnListing(l)) {
            showToast("You can't rate your own script", marketAnchor(), false);
            return;
        }
        if (marketRepo instanceof main.market.HttpRepository
                && !ensureConsent(main.privacy.Consent.MARKET_BROWSE)) return;
        new Thread(() -> {
            try {
                marketRepo.rate(l.id, stars);
                SwingUtilities.invokeLater(() -> {
                    reloadMarket();
                    showToast("Rated " + stars + "\u2605", marketAnchor(), true);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                        showToast("Couldn't rate: " + ex.getMessage(), marketAnchor(), false));
            }
        }, "DreamMan-Rate").start();
    }

    /**
     * v1.60: unpublish one of YOUR server listings - it comes off the market immediately and a
     * copy is staged back into the market-ready strip, so nothing is lost. The dialog warns about
     * the one genuinely surprising rule: the server keeps the stats for this name+version, so an
     * edited script re-uploaded WITHOUT a version bump re-attaches the old numbers.
     *
     * <p>Order matters: stage the local copy FIRST and abort if that fails - we never delete the
     * only copy of someone's script.
     */
    private void unpublishListing(main.market.ScriptListing l, JComponent src) {
        if (l == null || !"server".equals(l.origin) || !isOwnListing(l)) return;
        if (!(marketRepo instanceof main.market.HttpRepository)) return;
        int ok = JOptionPane.showConfirmDialog(this,
                "<html><b>Unpublish \u201c" + escapeHtml(l.name) + "\u201d v" + l.version + "?</b>"
                + "<br><br>\u00b7 Other players stop seeing it immediately."
                + "<br>\u00b7 A copy is staged back into your MARKET-READY row, so nothing is lost."
                + "<br><br><b>Heads up about stats:</b> its downloads \u00b7 ratings \u00b7 "
                + "favorites stay stored on the server for this name + version. If you edit the "
                + "script and re-upload it <b>without bumping the version</b>, those old stats "
                + "re-attach to the changed script \u2014 bump the version when you change it."
                + "</html>",
                "Unpublish \u2014 back to market-ready",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;

        // stage the copy locally first (fresh local id - the server assigns its own on republish)
        main.market.ScriptListing copy = new main.market.ScriptListing();
        copy.id = null;
        copy.name = l.name;
        copy.author = l.author;
        copy.description = l.description;
        copy.version = l.version;
        copy.tags = l.tags == null ? new ArrayList<>() : new ArrayList<>(l.tags);
        copy.icon = l.icon;
        copy.kind = l.kind;
        copy.vipOnly = l.vipOnly;
        // v1.61: it was live, so its card was finished - keep it publishable in one click. The
        // only exception is a pre-1.61 listing published before icons were mandatory: that one
        // comes back card-less and goes through the builder like anything else.
        copy.cardReady = l.icon != null && !l.icon.isEmpty();
        copy.bundle = l.bundle;
        copy.origin = "local";
        try {
            localMarketRepo.publish(copy);
        } catch (Exception ex) {
            showToast("Couldn't stage a local copy - NOT unpublished: " + ex.getMessage(),
                    src, false);
            return;
        }
        new Thread(() -> {
            try {
                marketRepo.remove(l.id);
                SwingUtilities.invokeLater(() -> {
                    reloadMarket();
                    showToast("\u201c" + l.name + "\u201d is back in your market-ready row",
                            marketAnchor(), true);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    reloadMarket();   // the staged copy exists either way
                    showToast("Couldn't remove from the server: " + ex.getMessage(),
                            marketAnchor(), false);
                });
            }
        }, "DreamMan-Unpublish").start();
    }

    /** v1.60: delete a market-ready staging copy (the strip card's x, and the context menu). */
    private void deleteLocalListing(main.market.ScriptListing l) {
        if (l == null || !"local".equals(l.origin)) return;
        if (JOptionPane.showConfirmDialog(this,
                "Delete the local file for \"" + l.name + "\"?", "Delete local script",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION)
            return;
        try {
            localMarketRepo.remove(l.id);
            reloadMarket();
            showToast("Deleted local copy of \"" + l.name + "\"", marketAnchor(), true);
        } catch (Exception ex) {
            showToast("Couldn't delete: " + ex.getMessage(), marketAnchor(), false);
        }
    }

    // v1.61: chooseIconForLocal is gone - the strip icon click and the context menu both open
    // the Card Builder now, so the icon, the card's details and the ready switch live in one
    // place instead of three. pickListingIcon below stays: it's the builder's file picker.

    /**
     * v1.60: pick an image file and return it as base64 (or null). Enforces the server's ~300 KB
     * icon cap up front, and the too-big dialog carries the roadmap's free-resizer link so the
     * fix is one click away.
     */
    private String pickListingIcon(JComponent anchor) {
        // v1.72 PERF: `new JFileChooser()` with no start directory makes Windows enumerate the
        // whole shell namespace - My Computer, mapped network drives, cloud folders - on the EDT,
        // which is the multi-second freeze people hit here (and the same root cause as the old
        // market-folder picker). Starting from a known local directory and switching off
        // ShellFolder resolution keeps it off the network entirely.
        JFileChooser fc = new JFileChooser(main.data.store.LocalStore.getExportsDir());
        fc.putClientProperty("FileChooser.useShellFolder", Boolean.FALSE);
        fc.setFileHidingEnabled(true);
        fc.setMultiSelectionEnabled(false);
        fc.setAcceptAllFileFilterUsed(false);
        fc.setDialogTitle("Pick a listing icon (128\u00d7128 recommended)");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Images (png, jpg)", "png", "jpg", "jpeg"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return null;
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(fc.getSelectedFile().toPath());
            if (bytes.length > LISTING_ICON_MAX_BYTES) {
                JPanel p = new JPanel(new GridLayout(0, 1, 0, 4));
                p.setOpaque(false);
                p.add(new JLabel("That image is " + (bytes.length / 1024)
                        + " KB - the market caps icons at 300 KB."));
                p.add(new JLabel("Resize it to about 128\u00d7128 first - free online:"));
                p.add(linkLabel("iloveimg.com \u2192 resize image (pixels)", ILOVEIMG_RESIZE_URL));
                JOptionPane.showMessageDialog(this, p, "Icon too big",
                        JOptionPane.WARNING_MESSAGE);
                return null;
            }
            return java.util.Base64.getEncoder().encodeToString(bytes);
        } catch (Exception ex) {
            showToast("Couldn't read that image: " + ex.getMessage(), anchor, false);
            return null;
        }
    }

    /** v1.60: a clickable link label - opens the browser, or copies the URL if that's blocked. */
    private JLabel linkLabel(String text, String url) {
        JLabel l = new JLabel("<html><u>" + escapeHtml(text) + "</u></html>");
        l.setForeground(Theme.BLUE);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.setToolTipText(url);
        l.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(new java.net.URI(url));
                } catch (Throwable t) {
                    // headless / sandboxed launcher: put the URL on the clipboard instead
                    try {
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                                new java.awt.datatransfer.StringSelection(url), null);
                        showToast("Link copied to clipboard", l, true);
                    } catch (Throwable ignored) {}
                }
            }
        });
        return l;
    }

    /**
     * v1.60: publish failures the server explains - 409 (that name+version already exists, the
     * version-integrity rule) and 403 (the anti-plagiarism fingerprint matched someone else's
     * listing) - get a real dialog with the server's own message; everything else stays a toast.
     * The client never tries to work around either: bumping the version or actually changing the
     * script is the fix, and the dialog says so.
     */
    private void showPublishError(Exception ex, JComponent anchor) {
        String msg = ex == null || ex.getMessage() == null ? "Unknown error" : ex.getMessage();
        boolean conflict = msg.contains("Server said 409");
        boolean blocked = msg.contains("Server said 403");
        if (!conflict && !blocked) {
            showToast("Publish failed: " + msg, anchor, false);
            return;
        }
        String body = msg;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"(?:error|message)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(msg);
        if (m.find())
            body = m.group(1).replace("\\\"", "\"").replace("\\n", "\n");
        JOptionPane.showMessageDialog(this,
                "<html><b>" + (conflict ? "Version conflict (409)" : "Publish blocked (403)")
                + "</b><br><br>" + escapeHtml(body) + "<br><br>"
                + (conflict
                    ? "Bump the version number, or unpublish the existing copy first \u2014 two "
                        + "<i>different</i> versions of the same name can coexist."
                    : "The server matched this content to someone else's listing. Any real change "
                        + "to the tasks publishes fine \u2014 renaming alone doesn't.")
                + "</html>",
                conflict ? "Already on the market at this version" : "Publish blocked",
                JOptionPane.WARNING_MESSAGE);
    }

    /** Minimal HTML escape for text we place inside JOptionPane html. */
    private static String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Publish ONE item at a time (Patch B.16): the selected library task, or the whole queue. */
    /**
     * v1.32b: the publish chooser is a LIST of your publishable items - every non-empty
     * preset, the current queue, and the selected library task. v1.61: the up-arrow no longer
     * publishes directly - it stages the item and opens its Card Builder, because a finished
     * card is now a prerequisite for publishing. Items already on the server under your name
     * show a server badge and a disabled button instead, so what's published is visible at a
     * glance.
     */
    private void publishOneItem(JComponent anchor) {
        // what's already mine on the server? (from the last market load - no extra request)
        java.util.Set<String> published = new java.util.HashSet<>();
        String me = main.market.ServerAccount.session().username;
        if (me != null && !me.isEmpty())
            for (main.market.ScriptListing l : marketAll)
                if (l != null && "server".equals(l.origin) && me.equalsIgnoreCase(l.author)
                        && l.name != null)
                    published.add(l.name.toLowerCase());

        // rows: [name, count, tasks-supplier]
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < modelPresets.size(); i++) {
            Preset pr = modelPresets.get(i);
            if (pr == null || pr.getTasks() == null || pr.getTasks().isEmpty()) continue;
            rows.add(new Object[]{pr.getName(), pr.getTasks().size(),
                    new ArrayList<Task>(pr.getTasks())});
        }
        if (!modelTaskList.isEmpty()) {
            List<Task> q = new ArrayList<>();
            for (int i = 0; i < modelTaskList.size(); i++) q.add(modelTaskList.get(i));
            rows.add(new Object[]{"Current queue", q.size(), q});
        }
        Task sel = listTaskLibrary != null ? listTaskLibrary.getSelectedValue() : null;
        if (sel != null) {
            List<Task> one = new ArrayList<>();
            one.add(sel);
            rows.add(new Object[]{sel.getName(), 1, one});
        }
        if (rows.isEmpty()) {
            showToast("Nothing publishable yet - save a preset or build a queue", anchor, false);
            return;
        }

        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this),
                "Publish to the market", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setAlwaysOnTop(true);   // the DreamBot canvas is always-on-top; match it or we hide behind
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        root.setBackground(BG_BASE);
        JLabel head = new JLabel("Your publishable items \u2014 the arrow stages one and opens "
                + "its card builder:");
        head.setForeground(TEXT_DIM);
        root.add(head, BorderLayout.NORTH);

        JPanel list = new JPanel(new GridLayout(0, 1, 0, 4));
        list.setOpaque(false);
        for (Object[] row : rows) {
            String name = (String) row[0];
            int count = (Integer) row[1];
            @SuppressWarnings("unchecked")
            List<Task> tasks = (List<Task>) row[2];
            boolean already = name != null && published.contains(name.toLowerCase());

            JPanel r = new JPanel(new BorderLayout(8, 0));
            r.setOpaque(true);
            r.setBackground(new Color(0x20, 0x20, 0x20));
            r.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(COLOR_BORDER_DIM),
                    new EmptyBorder(6, 10, 6, 6)));
            JLabel lbl = new JLabel(name + "   \u00b7   " + count + " task" + (count == 1 ? "" : "s"));
            lbl.setForeground(TEXT_MAIN);
            if (already) {
                lbl.setIcon(main.menu.components.UIIcons.server(13, new Color(0x5F, 0xB4, 0x66)));
                lbl.setIconTextGap(7);
                lbl.setToolTipText("Already on the server under your name");
            }
            r.add(lbl, BorderLayout.CENTER);
            JButton up = iconButton(main.menu.components.UIIcons.publish(18,
                    already ? TEXT_DIM : new Color(0x6F, 0xC2, 0x76)),
                    already ? "Already published"
                            : "Stage \"" + name + "\" & build its card",
                    () -> { dlg.dispose(); stageForCardBuilder(name, tasks, anchor); });
            up.setEnabled(!already);
            r.add(up, BorderLayout.EAST);
            list.add(r);
        }
        JScrollPane sp = Theme.thinScrollbars(new JScrollPane(list));
        sp.setBorder(BorderFactory.createEmptyBorder());
        root.add(sp, BorderLayout.CENTER);

        dlg.setContentPane(root);
        dlg.setResizable(true);
        dlg.setSize(560, Math.min(520, 140 + rows.size() * 46));
        dlg.setLocationRelativeTo(this);
        dlg.toFront();
        dlg.setVisible(true);
    }

    private void chooseMarketSource(JComponent anchor) {
        Object[] opts = {"Shared folder", "Server (URL)", "Cancel"};
        int which = JOptionPane.showOptionDialog(this,
                "<html>Where should the market read from?<br><br>"
                        + "<b>Shared folder</b> - a Dropbox/Drive/network folder everyone can see. "
                        + "Works right now, no server needed.<br>"
                        + "<b>Server</b> - a REST endpoint (see HttpRepository for the contract). "
                        + "There isn't a public DreamMan market yet - this is for when you run one."
                        + "</html>",
                "Market source", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                opts, opts[0]);

        if (which == 0) {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Pick the shared market folder");
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            marketRepo = new main.market.FolderRepository(chooser.getSelectedFile());
            reloadMarket();
            showToast("Market source changed", anchor, true);
        } else if (which == 1) {
            // Patch B.12: connecting to a server means data will leave this PC. Ask FIRST.
            if (!ensureConsent(main.privacy.Consent.MARKET_BROWSE)) {
                showToast("Staying offline - nothing was sent", anchor, false);
                return;
            }
            String url = JOptionPane.showInputDialog(this,
                    "Server base URL (e.g. https://example.com/dreamman)", "");
            if (url == null || url.trim().isEmpty()) return;
            String token = JOptionPane.showInputDialog(this,
                    "Access token (optional - leave blank if the server doesn't need one)", "");
            marketRepo = new main.market.HttpRepository(url.trim(), token);
            reloadMarket();
            showToast("Market source changed", anchor, true);
        }
    }

    /** The Loot Tracker's own tab (Patch B.5) - kill counts and learned drop tables. */
    private JPanel createLootTrackerTab() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        panel.setBackground(BG_BASE);
        panel.add(createSubtitle("Loot Tracker"), BorderLayout.NORTH);
        // v1.30: the plain-text readout became a RuneLite-style tracker - NPC boxes with
        // images, item icon grids with quantities and GE values, session + lifetime kill
        // counts, sorting and reset options. Icons/prices are an explicit opt-in inside it.
        panel.add(new main.menu.components.LootTrackerPanel(), BorderLayout.CENTER);
        return panel;
    }

    private JTextArea lootTableArea;

    /**
     * A live readout of the drop-table data the bot has learned this session (Patch B.4): per
     * NPC, how many we've killed and the items seen on our kill tiles, as counts and a rough
     * per-kill rate. It's a basic readout over {@link main.tools.LootTracker} - a foundation for
     * a fuller drop-rate analysis later - refreshed on demand and auto-updated while visible.
     */
    private JPanel buildLootTablePanel() {
        JPanel wrap = new JPanel(new BorderLayout(0, 6));
        wrap.setOpaque(false);

        JLabel title = new JLabel("Learned loot tables (this session)");
        title.setForeground(Theme.ACCENT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));

        lootTableArea = new JTextArea();
        lootTableArea.setEditable(false);
        lootTableArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        lootTableArea.setBackground(new Color(15, 15, 15));
        lootTableArea.setForeground(TEXT_MAIN);
        lootTableArea.setBorder(new EmptyBorder(8, 8, 8, 8));

        JButton refresh = createButton("Refresh");
        refresh.addActionListener(e -> refreshLootTable());
        JButton reset = createButton("Reset", new Color(90, 30, 30), null);
        reset.addActionListener(e -> { main.tools.LootTracker.resetLearning(); refreshLootTable(); });
        JPanel btns = new JPanel(new GridLayout(1, 2, 5, 5));
        btns.setOpaque(false);
        btns.add(refresh);
        btns.add(reset);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(title, BorderLayout.WEST);

        wrap.add(top, BorderLayout.NORTH);
        wrap.add(Theme.thinScrollbars(new JScrollPane(lootTableArea)), BorderLayout.CENTER);
        wrap.add(btns, BorderLayout.SOUTH);

        wrap.addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) { refreshLootTable(); }
        });
        refreshLootTable();
        return wrap;
    }

    private void refreshLootTable() {
        if (lootTableArea == null) return;
        Map<String, Integer> kills = main.tools.LootTracker.allKillCounts();
        Map<String, Map<String, Long>> drops = main.tools.LootTracker.allDropCounts();
        if (kills.isEmpty() && drops.isEmpty()) {
            lootTableArea.setText("No kills recorded yet.\n\nKill NPCs with an Interact "
                    + "(Attack) action and loot near them - drops seen on your kill tiles are "
                    + "tallied here per NPC.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        List<String> npcs = new ArrayList<>(
                new TreeSet<>(kills.keySet()));
        for (String npc : drops.keySet()) if (!npcs.contains(npc)) npcs.add(npc);
        for (String npc : npcs) {
            int k = kills.getOrDefault(npc, 0);
            sb.append(npc).append("  \u2014  ").append(k).append(k == 1 ? " kill" : " kills").append("\n");
            Map<String, Long> table = drops.get(npc);
            if (table != null) {
                List<Map.Entry<String, Long>> sorted =
                        new ArrayList<>(table.entrySet());
                sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
                for (Map.Entry<String, Long> en : sorted) {
                    double perKill = k > 0 ? (double) en.getValue() / k : 0;
                    sb.append(String.format("    %-22s %5d   (%.2f/kill)%n",
                            trunc(en.getKey(), 22), en.getValue(), perKill));
                }
            }
            sb.append("\n");
        }
        lootTableArea.setText(sb.toString());
        lootTableArea.setCaretPosition(0);
    }

    private static String trunc(String s, int n) {
        return s == null ? "" : (s.length() <= n ? s : s.substring(0, n - 1) + "\u2026");
    }

    /**
     * Prompts for one action to add to a watcher's response chain (Patch B.4). A compact combo
     * of the same action registry the builder uses; the chosen action is created fresh and its
     * parameters can then be edited by clicking it in the builder-style flow later. Returns null
     * if cancelled.
     */
    /** Public wrapper so the builder can reuse the response-action picker (Patch B.4). */
    public Action pickResponseActionPublic() { return pickResponseAction(); }

    private Action pickResponseAction() {
        JActionSelector sel = new JActionSelector();
        // Patch B.5: checks can respond with library TASKS too - populate the picker with the
        // same gold task entries the builder's dropdown gets.
        sel.setLibraryTasks(buildLibraryEntries());
        int r = JOptionPane.showConfirmDialog(this, sel, "Add response (action or library task)",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return null;
        Action a = sel.getSelectedAction();
        // v1.86: copyDeep(), not copy() - copy() relies on each subclass ctor remembering to
        // carry chance/on-start/triggers, which is exactly the assumption that failed.
        return a == null ? null : a.copyDeep();
    }

    /** v1.62: current library task names (sorted, de-duped) for the TaskRef param dropdown. */
    private List<String> libraryTaskNames() {
        java.util.TreeSet<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Task t : libraryAll)
            if (t != null && t.getName() != null && !t.getName().isEmpty())
                names.add(t.getName());
        return new ArrayList<>(names);
    }

    /** Bound TaskRef entries for every library task (shared by all selector instances, B.5). */
    private List<main.actions.TaskRef> buildLibraryEntries() {
        List<main.actions.TaskRef> entries = new ArrayList<>();
        for (Task t : libraryAll) {           // master: filtered-out tasks stay selectable
            if (t == null) continue;
            main.actions.TaskRef ref = new main.actions.TaskRef();
            ref.bind(t.getName(), t.getId());
            entries.add(ref);
        }
        return entries;
    }

    /**
     * The Account tab (v1.87) - everything DREAMMAN-SERVER related, and only that. Logged out
     * it IS the sign-in experience: the proper in-tab Sign in / Create account / Forgot
     * password card ({@link main.menu.components.AccountAuthPanel}), replacing the old chain
     * of option panes. Logged in it's your account at a glance: rank badges, your tier's
     * actual limits, your donation ledger with the (placeholder) Donate button, your public
     * profile, the account switcher and Log out.
     *
     * <p>The old Status tab this replaces was three things wearing one name; its pieces went
     * where they belonged - the PLAYER half into the side Player panel, Privacy into its own
     * tab, and the telemetry cards retired (the bottom bar / overlay / status.json already
     * carry them).
     */
    private CardLayout accountCards;
    private JPanel accountCardHost;
    private JPanel accountMeContent;   // the signed-in view, rebuilt on account changes

    private JPanel createAccountTab() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        panel.setBackground(BG_BASE);

        accountCards = new CardLayout();
        accountCardHost = new JPanel(accountCards);
        accountCardHost.setOpaque(false);

        main.menu.components.AccountAuthPanel auth = new main.menu.components.AccountAuthPanel(
                new main.menu.components.AccountAuthPanel.Host() {
                    @Override public String serverUrl() {
                        String url = marketRepo instanceof main.market.HttpRepository
                                ? ((main.market.HttpRepository) marketRepo).baseUrl()
                                : main.market.ServerAccount.session().baseUrl;
                        if (url == null || url.isEmpty()) url = marketServerUrl;
                        if (url == null || url.isEmpty()) url = DEFAULT_MARKET_SERVER_URL;
                        return url;
                    }
                    @Override public void onAccountChanged() {
                        DreamBotMenu.this.onAccountChanged();
                    }
                });

        accountMeContent = new JPanel(new GridBagLayout());
        accountMeContent.setOpaque(false);

        accountCardHost.add(auth, "auth");
        accountCardHost.add(accountMeContent, "me");

        panel.add(createSubtitle("Account"), BorderLayout.NORTH);
        JScrollPane scroll = Theme.thinScrollbars(new JScrollPane(accountCardHost));
        scroll.setBorder(null);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(scroll, BorderLayout.CENTER);

        refreshAccountTab();
        return panel;
    }

    /** Rebuilds the Account tab for the CURRENT sign-in state (called from onAccountChanged). */
    private void refreshAccountTab() {
        if (accountCardHost == null) return;
        boolean in = main.market.ServerAccount.isLoggedIn();
        if (in) rebuildAccountMeCard();
        accountCards.show(accountCardHost, in ? "me" : "auth");
    }

    /** The signed-in Account card: identity + badges, limits, donations, profile, log out. */
    private void rebuildAccountMeCard() {
        accountMeContent.removeAll();

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(PANEL_SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_BORDER_DIM),
                new EmptyBorder(18, 22, 18, 22)));

        // ── who you are ──
        JLabel name = new JLabel(main.market.ServerAccount.session().username);
        name.setForeground(TEXT_MAIN);
        name.setFont(new Font("Consolas", Font.BOLD, 22));
        JComponent badges = main.menu.components.RankBadge.mine(13);
        JPanel who = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        who.setOpaque(false);
        who.add(name);
        who.add(badges);
        addLeft(card, who);

        JLabel explain = new JLabel("<html><div style='width:430px'>Your DreamMan account - the "
                + "market, cloud sync, your rank. Your RuneScape login is separate: it lives in "
                + "the side <b>Player</b> panel and is never sent to this server.</div></html>");
        explain.setForeground(TEXT_DIM);
        explain.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        explain.setBorder(new EmptyBorder(6, 0, 10, 0));
        addLeft(card, explain);

        // ── the tier row (the shared label refreshTierStatusLabel keeps current) ──
        JPanel tierRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        tierRow.setOpaque(false);
        JLabel tierKey = new JLabel("Tier:");
        tierKey.setForeground(TEXT_DIM);
        lblAccountTier.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tierRow.add(tierKey);
        tierRow.add(lblAccountTier);
        addLeft(card, tierRow);

        // ── what your rank actually gets you, honestly labelled ──
        addLeft(card, accountSectionHeader("Your limits"));
        int presets = main.market.Tier.presetLimit();
        int uploads = main.market.Tier.marketUploadLimit();
        JLabel limits = new JLabel("<html><table cellpadding='2' cellspacing='0'>"
                + "<tr><td>Queue loops</td><td><b>" + main.market.Tier.maxLoops()
                + (main.market.Tier.allowsInfinite() ? " (\u221e allowed)" : "") + "</b></td></tr>"
                + "<tr><td>Saved Jagex accounts&nbsp;&nbsp;</td><td><b>"
                + main.market.Tier.maxExtraAccounts() + "</b></td></tr>"
                + "<tr><td>Equipment presets</td><td><b>"
                + (presets == Integer.MAX_VALUE ? "unlimited" : presets) + "</b></td></tr>"
                + "<tr><td>Market uploads</td><td><b>"
                + (uploads == Integer.MAX_VALUE ? "unlimited (Scripter)" : uploads)
                + "</b></td></tr></table></html>");
        limits.setForeground(TEXT_MAIN);
        limits.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        addLeft(card, limits);
        JLabel limitNote = new JLabel("<html><i>Shown from your login - the server enforces "
                + "the real numbers either way.</i></html>");
        limitNote.setForeground(TEXT_DIM);
        limitNote.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        addLeft(card, limitNote);

        // ── donations: the ledger + the honest placeholder button ──
        addLeft(card, accountSectionHeader("Donations"));
        long donated = main.market.Tier.donatedCents();
        String donorTag = main.market.DonorRanks.nameFor(donated);
        JLabel donatedLbl = new JLabel(donated <= 0
                ? "You haven't donated (nothing wrong with that - the app is meant to be free)."
                : "Lifetime: " + main.market.DonorRanks.dollars(donated)
                        + (donorTag == null ? "" : "  \u00b7  " + donorTag + " - thank you."));
        donatedLbl.setForeground(TEXT_MAIN);
        donatedLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        addLeft(card, donatedLbl);
        JButton btnDonate = createButton("Donate\u2026", new Color(45, 70, 45), null);
        btnDonate.setToolTipText("Support DreamMan (not wired up yet)");
        btnDonate.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "You can't donate yet - the donation system isn't set up.\n\n"
                        + "Thank you for wanting to support DreamMan! When it exists, every\n"
                        + "donation counts toward the donor ranks you can see in the market.",
                "Donations aren't set up yet", JOptionPane.INFORMATION_MESSAGE));
        addLeft(card, rowOf(btnDonate));

        // ── account actions: profile, the multi-account switcher, log out ──
        addLeft(card, accountSectionHeader("Account"));
        addLeft(card, buildAccountSwitcherRow());
        btnEditProfile = createButton("Public profile\u2026", new Color(45, 55, 70), null);
        btnEditProfile.setToolTipText("View and edit the bio shown on your public scripter "
                + "profile (what others see when they click your name on a market card)");
        btnEditProfile.addActionListener(e -> openMyProfileEditor(btnEditProfile));
        btnAccountLogout = createButton("Log out of DreamMan account");
        btnAccountLogout.setToolTipText("Sign out of your DreamMan account (separate from the "
                + "game logout in the side Player panel)");
        btnAccountLogout.addActionListener(e -> {
            if (!main.market.ServerAccount.isLoggedIn()) {
                refreshAccountLogoutVisibility();
                showToast("You're not signed in", lblAccountTier, false);
                return;
            }
            try {
                String url = marketRepo instanceof main.market.HttpRepository
                        ? ((main.market.HttpRepository) marketRepo).baseUrl()
                        : main.market.ServerAccount.session().baseUrl;
                new main.market.ServerAccount(url == null || url.isEmpty()
                        ? DEFAULT_MARKET_SERVER_URL : url).logout();
                main.market.AccountVault.lock();
            } catch (Throwable ignored) {}
            onAccountChanged();
            showToast("Signed out of your DreamMan account", mainTabs, true);
        });
        addLeft(card, rowOf(btnEditProfile, btnAccountLogout));
        refreshAccountLogoutVisibility();

        card.setMaximumSize(new Dimension(520, Integer.MAX_VALUE));
        accountMeContent.add(card);
        accountMeContent.revalidate();
        accountMeContent.repaint();
    }

    private static void addLeft(JPanel column, JComponent c) {
        c.setAlignmentX(0f);
        column.add(c);
    }

    private JComponent rowOf(JComponent... comps) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        for (JComponent c : comps) row.add(c);
        return row;
    }

    /** A gold divider header inside the Account card (same look the Player card had). */
    private JComponent accountSectionHeader(String text) {
        return playerSectionHeader(text);
    }

    /** v1.87: Privacy as its OWN tab - the same panel it always was, un-buried from Status. */
    private JPanel createPrivacyTabStandalone() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        panel.setBackground(BG_BASE);
        // v1.89: NO subtitle here. createPrivacyTab() already adds its own "Privacy" header, and
        // wrapping it in v1.87 stacked a second one on top - the duplicate in the report.
        JScrollPane scroll = Theme.thinScrollbars(new JScrollPane(createPrivacyTab()));
        scroll.setBorder(null);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    /** A small gold divider header (the Player card look, reused by Account + side panel). */
    private JComponent playerSectionHeader(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setForeground(Theme.ACCENT);
        l.setFont(new Font("Segoe UI", Font.BOLD, 11));
        l.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_BORDER_DIM),
                new EmptyBorder(10, 0, 3, 0)));
        return l;
    }

    /**
     * v1.32b: the DreamMan account, split out of the Player card into its own card on the right
     * (the Player card is now just the in-game person). Holds the tier, the sign-in / account
     * switcher, and - finally - a real DreamMan-account <b>Log out</b> button (the bottom-bar
     * Logout only logs out the game, which is why "I can't log out of my account" happened).
     */
    private JPanel buildDreamManAccountCard() {
        JPanel card = createInfoCard("DreamMan Account");
        addInfoRow(card, "Tier", lblAccountTier);
        card.add(buildAccountSwitcherRow());

        // v1.63: edit your public scripter profile (bio) - account-adjacent, so it lives here.
        btnEditProfile = createButton("Public profile\u2026", new Color(45, 55, 70), null);
        btnEditProfile.setToolTipText("View and edit the bio shown on your public scripter "
                + "profile (what others see when they click your name on a market card)");
        btnEditProfile.addActionListener(e -> openMyProfileEditor(btnEditProfile));
        JPanel profileRow = new JPanel(new BorderLayout());
        profileRow.setOpaque(false);
        profileRow.setBorder(new EmptyBorder(8, 0, 0, 0));
        profileRow.add(btnEditProfile, BorderLayout.CENTER);
        card.add(profileRow);

        btnAccountLogout = createButton("Log out of DreamMan account");
        btnAccountLogout.setToolTipText("Sign out of your DreamMan account (separate from the "
                + "game logout at the bottom of the window)");
        btnAccountLogout.addActionListener(e -> {
            // v1.32b: guard the "logout before ever logging in" case - previously this ran the
            // whole logout path, flashed an unreadable toast and yanked the button away.
            if (!main.market.ServerAccount.isLoggedIn()) {
                refreshAccountLogoutVisibility();
                showToast("You're not signed in", lblAccountTier, false);
                return;
            }
            try {
                String url = marketRepo instanceof main.market.HttpRepository
                        ? ((main.market.HttpRepository) marketRepo).baseUrl()
                        : main.market.ServerAccount.session().baseUrl;
                new main.market.ServerAccount(url == null || url.isEmpty()
                        ? DEFAULT_MARKET_SERVER_URL : url).logout();
                main.market.AccountVault.lock();
            } catch (Throwable ignored) {}
            onAccountChanged();
            // anchor the toast on the tier label (always visible) - the button itself hides on
            // logout, which made the old toast vanish with it
            showToast("Signed out of your DreamMan account", lblAccountTier, true);
        });
        JPanel logoutRow = new JPanel(new BorderLayout());
        logoutRow.setOpaque(false);
        logoutRow.setBorder(new EmptyBorder(8, 0, 0, 0));
        logoutRow.add(btnAccountLogout, BorderLayout.CENTER);
        card.add(logoutRow);

        refreshAccountLogoutVisibility();
        return card;
    }

    /** Shows the account Log out button (and the profile editor, v1.63) only while signed in. */
    private void refreshAccountLogoutVisibility() {
        boolean in = main.market.ServerAccount.isLoggedIn();
        if (btnAccountLogout != null) btnAccountLogout.setVisible(in);
        if (btnEditProfile != null) btnEditProfile.setVisible(in);
    }

    /** The PIN field + Set button (moved into the Player card in B.17). */
    private JPanel buildBankPinRow() {
        JPasswordField pinField = new JPasswordField();
        pinField.setColumns(6);
        pinField.setToolTipText("Your bank PIN. Held in memory for this session only - never "
                + "written to your profile or any file.");
        JButton pinSet = createButton("Set");
        pinSet.addActionListener(e -> {
            String p = new String(pinField.getPassword()).trim();
            if (p.isEmpty()) {
                main.tools.BankPin.setPin("");
                lblBankPin.setText("(none)");
                showToast("Bank PIN cleared", pinSet, true);
                return;
            }
            if (!p.matches("\\d{4,10}")) {
                showToast("PIN should be 4-10 digits", pinSet, false);
                return;
            }
            main.tools.BankPin.setPin(p);
            pinField.setText("");
            lblBankPin.setText(main.tools.BankPin.masked());
            showToast("Bank PIN set for this session", pinSet, true);
        });

        JPanel pinRow = new JPanel(new BorderLayout(6, 0));
        pinRow.setOpaque(false);
        pinRow.add(pinField, BorderLayout.CENTER);
        pinRow.add(pinSet, BorderLayout.EAST);
        // v1.89: a BoxLayout column hands every child its MAXIMUM size, and a JPanel's default
        // maximum is unbounded - so this little PIN row grew into a slab that shoved the whole
        // details section apart (the tall teal block in the report). Capping the height is the
        // fix; the width cap keeps the field from swallowing the column too.
        pinRow.setMaximumSize(new Dimension(260, 30));
        pinRow.setPreferredSize(new Dimension(220, 26));
        return pinRow;
    }

    /** The DreamBot Account Manager combo + Refresh/Switch (moved into the Player card, B.17). */
    private JPanel buildAccountSwitchRow() {
        accountCombo = new JComboBox<>();
        accountCombo.setToolTipText("Accounts from DreamBot's Account Manager");
        JButton acctSwitch = createButton("Switch");
        acctSwitch.addActionListener(e -> {
            Object sel = accountCombo.getSelectedItem();
            if (sel == null) { showToast("No account selected", acctSwitch, false); return; }
            String name = String.valueOf(sel);
            int ok = JOptionPane.showConfirmDialog(this,
                    "Log out and switch to \"" + name + "\"?\n"
                            + "The script will pause, log out, and the next login uses that account.",
                    "Switch account", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (ok != JOptionPane.YES_OPTION) return;

            isMenuPaused(true);                       // stop the queue before we change accounts
            try { if (scriptControls != null) scriptControls.requestLogout(); } catch (Throwable ignored) {}
            if (main.tools.AccountSwitcher.switchTo(name))
                showToast("Switched to " + name + " - press Play to log in", acctSwitch, true);
            else
                showToast("This client build doesn't expose account switching", acctSwitch, false);
        });

        JButton acctRefresh = createButton("Refresh");
        acctRefresh.addActionListener(e -> refreshAccountList());

        JPanel acctRow = new JPanel(new BorderLayout(6, 0));
        acctRow.setOpaque(false);
        acctRow.add(accountCombo, BorderLayout.CENTER);
        JPanel acctBtns = new JPanel(new GridLayout(1, 2, 4, 0));
        acctBtns.setOpaque(false);
        acctBtns.add(acctRefresh);
        acctBtns.add(acctSwitch);
        acctRow.add(acctBtns, BorderLayout.EAST);

        refreshAccountList();
        return acctRow;
    }

    /** Small read-only session facts for the grid (uptime etc. live in the Script card). */
    private JComponent createSessionSummary() {
        JLabel l = new JLabel("<html>Bank PIN and account switching moved to the "
                + "<b>Player</b> card on the left \u2014 everything about you, in one place."
                + "<br><br>Local files: " + LocalStore.getRoot().getAbsolutePath() + "</html>");
        l.setForeground(TEXT_DIM);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        return l;
    }

    private JPanel createSettingsTab() {
        ///  Create the main settings panel
        JPanel panelSettings = new JPanel(new BorderLayout(15, 15));
        panelSettings.setBorder(new EmptyBorder(15, 15, 15, 15));
        panelSettings.setBackground(BG_BASE);

        // add a card layout to the settings panel to switch between setting groups
        CardLayout cardLayout = new CardLayout();

        ///  Create a panel to group similar settings
        JPanel settingGroup = new JPanel(cardLayout);
        settingGroup.setBackground(BG_BASE);
        settingGroup.setBorder(new EmptyBorder(20, 20, 20, 20));

        ///  Define each setting group
        settingGroup.add(createClientPanel(), "Client");
        settingGroup.add(createScriptPanel(), "Script");
        settingGroup.add(createActivitiesPanel(), "Activities");
        settingGroup.add(createAudioPanel(), "Audio");
        settingGroup.add(createChatPanel(), "Chat");
        settingGroup.add(createControlsPanel(), "Controls");
        settingGroup.add(createDisplayPanel(), "Display");
        settingGroup.add(createGameplayPanel(), "Gameplay");
        settingGroup.add(createWarningsPanel(), "Warnings");

        JPanel menuPanel = new JPanel(new GridLayout(0, 1, 0, 2));
        menuPanel.setPreferredSize(new Dimension(180, 0));
        menuPanel.setBackground(PANEL_SURFACE); menuPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, COLOR_BORDER_DIM));

        String[] groups = {"Client", "Script", "Display", "Gameplay", "Audio", "Chat", "Controls", "Activities", "Warnings"};
        ButtonGroup btnGroup = new ButtonGroup();

        for (String cat : groups) {
            JToggleButton btn = createMenuButton(cat);
            btn.addActionListener(e -> cardLayout.show(settingGroup, cat));
            btnGroup.add(btn);
            menuPanel.add(btn);
            if (cat.equals("Client"))
                btn.setSelected(true);
        }

        // v1.87: say what this tab actually IS, once, at the top - these switches drive the
        // real in-game settings live, which surprised people when a toggle "did something".
        JLabel warn = new JLabel("<html><b>These are your LIVE in-game settings.</b> Flipping a "
                + "switch changes it in the game immediately \u2014 and if the bot is busy "
                + "mid-action the game may briefly refuse (you'll see a toast; try again). "
                + "Groups follow the same top-to-bottom order as the in-game settings menu. "
                + "Only settings DreamBot's API exposes appear here.</html>");
        warn.setForeground(new Color(220, 180, 100));
        warn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JPanel warnCard = new JPanel(new BorderLayout());
        warnCard.setBackground(new Color(0x2A, 0x24, 0x16));
        warnCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, new Color(220, 180, 100)),
                new EmptyBorder(8, 10, 8, 10)));
        warnCard.add(warn, BorderLayout.CENTER);
        JPanel north = new JPanel(new BorderLayout(0, 10));
        north.setOpaque(false);
        north.add(createSubtitle("Settings"), BorderLayout.NORTH);
        north.add(warnCard, BorderLayout.CENTER);

        panelSettings.add(north, BorderLayout.NORTH);
        panelSettings.add(menuPanel, BorderLayout.WEST);
        panelSettings.add(settingGroup, BorderLayout.CENTER);

        // v1.32: the Developers Console is a developer-only button here now (was a tab that
        // overflowed the strip). Opens in its own resizable window. Hidden for everyone else.
        if (isDeveloper()) {
            JButton btnDevConsole = createButton("Open Developers Console\u2026");
            btnDevConsole.setToolTipText("Developer tools (visible only to developers)");
            btnDevConsole.addActionListener(e -> {
                JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this),
                        "Developers Console", java.awt.Dialog.ModalityType.MODELESS);
                dlg.setContentPane(new DevelopersConsole(libraryPanel));
                dlg.setSize(900, 600);
                dlg.setLocationRelativeTo(this);
                dlg.setVisible(true);
            });
            JPanel devRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            devRow.setOpaque(false);
            devRow.add(btnDevConsole);
            panelSettings.add(devRow, BorderLayout.SOUTH);
        }

        return panelSettings;
    }

    // (Removed dead resetTaskBuilder() helper for the same reason as createTask above.)

//    private void scanNearbyTargets() {
//        // Each action subclass decides what to scan — no switch needed here
//        Set<String> names = actionSelector.scanTargets();
//        List<String> sortedNames = names.stream().sorted().collect(Collectors.toList());
//
//        SwingUtilities.invokeLater(() -> {
//            nearbyEntitiesModel.clear();
//            sortedNames.forEach(nearbyEntitiesModel::addElement);
//            showToast("Found " + sortedNames.size() + " targets", btnTaskBuilderScanNearby, true);
//        });
//    }

    // --- Inner Classes ---
    public static class Task {
        /**
         * Patch B.2: stable identity. Copies (queue entries, preset snapshots) share the id of
         * the task they came from, so "save changes" in the builder can update every instance
         * of a task at once - and TaskRef actions resolve the freshest library version by id
         * at run time. The Duplicate button regenerates the id (a duplicate is a NEW task).
         */
        private String id = UUID.randomUUID().toString();
        /** When this logical task was first created (Patch B.3) - drives "Newest" sorting. */
        private long createdAt = System.currentTimeMillis();
        /** Where this task came from (Patch B.5): "user" (built here), "imported", "default". */
        private String origin = "user";
        /** v1.49: version number, shown in lists and bumped when a task is saved over. */
        private double version = 1.0;
        /** v1.49: lifecycle flags that drive the library filters. */
        private boolean exported = false;     // saved to disk explicitly
        private boolean marketReady = false;  // staged to the local market
        private boolean published = false;    // uploaded to the server market
        private boolean downloaded = false;   // pulled down from the market
        /** v1.51: comma-entered tags - search filters for the library + market. */
        private java.util.List<String> tags = new java.util.ArrayList<>();
        /** Patch B.14: VIP-gated task. Admins tick this; the library hides it from free users. */
        private boolean vipOnly = false;
        private String name;
        private String description;
        private String status;
        private List<Action> actions;
        /** How many times this task runs before the queue advances (>= 1). */
        private int repeat = 1;

        // ── Timed tasks (Patch B.8) ──────────────────────────────────────────
        // A task can be marked "timed": it doesn't sit in the normal rotation, but fires on a
        // rough interval at a SAFE moment you choose - never mid-task. Everything below is
        // per-task and persists with it.
        /** True when this task runs on a timer instead of in the normal queue rotation. */
        private boolean timed = false;
        /** Rough interval in minutes; the engine adds jitter so it isn't clockwork. */
        private int timerMinutes = 30;
        /** +/- jitter percent applied to the interval (0-50). 20 => 30min becomes ~24-36min. */
        private int timerJitterPct = 20;
        /** When it may fire: AFTER_LOOP | AFTER_ALL_LOOPS | AFTER_TIMED_TASK. */
        private String timerWhen = "AFTER_LOOP";
        /** Live state: when this timed task is next DUE (epoch ms, 0 = not scheduled yet). */
        private transient long nextDueAt = 0;
        /** Patch B: when true, the engine pauses autoDelayMinMs-autoDelayMaxMs after each completed action. */
        private boolean autoDelay = false;
        private int autoDelayMinMs = 600;
        private int autoDelayMaxMs = 1400;

        /**
         * v1.68: run this queue entry on the FIRST queue loop only, then skip it on every later
         * lap - the task-level counterpart of an action's "only run on first loop (setup)".
         *
         * <p>Deliberately PER INSTANCE, not per library task: queue entries are deep copies
         * (see the copy constructor), so the same logical task can sit in a script twice with
         * only one of the two gated. That's why this lives on Task/TaskData and travels inside
         * the task's own snapshot, the way action and task triggers do, rather than in any
         * global side-table keyed by task id - a side-table keyed by id could not tell the two
         * instances apart.
         */
        private boolean onStartOnly = false;

        /**
         * v1.80: TASK triggers - the fourth tier, between action and script.
         *
         * <p>The tiers, narrowest first: an ACTION trigger runs during one step of one task; a
         * TASK trigger runs for the whole time this task is executing; a SCRIPT trigger runs for
         * the whole queue; a GLOBAL check runs always. This list is the task tier.
         *
         * <p>It lives on the Task, so like {@code onStartOnly} it is PER INSTANCE - editing the
         * copy in your Task List binds to that queue entry alone, while editing the library task
         * sets the default that new copies inherit. It rides inside the task's own snapshot, so
         * exports and market bundles carry it with no extra plumbing.
         */
        private final List<main.watchers.Trigger> taskTriggers = new ArrayList<>();

        public Task(String name, String description, List<Action> actions, String status) {
            this.name = name;
            this.description = (description == null || description.isEmpty()) ? "No description provided by Author." : description;
            this.actions = actions;
            this.status = (status == null || status.isEmpty()) ? "Executing task..." : status;
        }

        public Task(Task o) {
            this.id = o.id;
            this.createdAt = o.createdAt;
            this.origin = o.origin;
            this.vipOnly = o.vipOnly;
            this.version = o.version;           // v1.49
            this.exported = o.exported;
            this.marketReady = o.marketReady;
            this.published = o.published;
            this.downloaded = o.downloaded;
            this.tags = new ArrayList<>(o.getTags());   // v1.51
            this.name = o.name;
            this.description = o.description;
            this.status = o.status;
            this.repeat = o.repeat;
            this.onStartOnly = o.onStartOnly;   // v1.68: travels with the instance
            // v1.80: DEEP copy - a new queue entry must not share trigger objects with the
            // library task, or editing one instance would silently edit every other.
            for (main.watchers.Trigger t : o.taskTriggers)
                if (t != null) this.taskTriggers.add(new main.watchers.Trigger(t));
            this.autoDelay = o.autoDelay;
            this.autoDelayMinMs = o.autoDelayMinMs;
            this.autoDelayMaxMs = o.autoDelayMaxMs;
            // Patch B.8: timer config travels with copies (the live due-time does not - a copy
            // schedules itself fresh when it's armed)
            this.timed = o.timed;
            this.timerMinutes = o.timerMinutes;
            this.timerJitterPct = o.timerJitterPct;
            this.timerWhen = o.timerWhen;

            // DEEP COPY logic:
            this.actions = new ArrayList<>();
            if (o.actions != null) {
                for (Action originalAction : o.actions) {
                    // Patch B.7: copyDeep() guarantees the action's chance-to-run and attached
                    // checks come along, even for subclasses whose copy() ctor doesn't copy them.
                    this.actions.add(originalAction.copyDeep());
                }
            }
        }

        /** Stable identity shared by all copies of this logical task. */
        public String getId() { return id; }

        /** Makes this task a NEW logical task (used by Duplicate). */
        public void regenerateId() {
            this.id = UUID.randomUUID().toString();
            this.createdAt = System.currentTimeMillis();
        }

        /** When this task was created (0 for pre-B.3 saves). */
        public long getCreatedAt() { return createdAt; }

        /** "user" | "imported" | "default" (Patch B.5) - drives the library filter. */
        public String getOrigin() { return origin == null || origin.isEmpty() ? "user" : origin; }
        public void setOrigin(String o) { if (o != null && !o.isEmpty()) this.origin = o; }
        public double getVersion() { return version <= 0 ? 1.0 : version; }
        public void setVersion(double v) { this.version = v <= 0 ? 1.0 : v; }
        public void bumpVersion() { this.version = Math.round((getVersion() + 0.1) * 10.0) / 10.0; }
        public boolean isExported() { return exported; }
        public void setExported(boolean b) { this.exported = b; }
        public boolean isMarketReady() { return marketReady; }
        public void setMarketReady(boolean b) { this.marketReady = b; }
        public boolean isPublished() { return published; }
        public void setPublished(boolean b) { this.published = b; }
        public boolean isDownloaded() { return downloaded; }
        public void setDownloaded(boolean b) { this.downloaded = b; }
        public java.util.List<String> getTags() { return tags == null ? new java.util.ArrayList<>() : tags; }
        public void setTags(java.util.List<String> t) { this.tags = t == null ? new java.util.ArrayList<>() : t; }

        /** Patch B.14: whether this is a VIP-only task. */
        public boolean isVipOnly() { return vipOnly; }
        public void setVipOnly(boolean v) { this.vipOnly = v; }
        /** Persistence only. */
        public void restoreCreatedAt(long t) { if (t > 0) this.createdAt = t; }

        /** Persistence only: restores the id saved on disk. */
        public void restoreId(String saved) {
            if (saved != null && !saved.isEmpty()) this.id = saved;
        }

        public String getEditableString() {
            return "NAME:" + name
                    + "\nDESC:" + description
                    + "\nSTATUS:" + status;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public void setActions(List<Action> actions) {
            this.actions = actions;
        }

        public List<Action> getActions() {
            return actions;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getStatus() {
            return status;
        }

        /** @return this task's repeat count (always >= 1). */
        // ── timed-task accessors (Patch B.8) ──
        public boolean isTimed() { return timed; }
        public void setTimed(boolean t) { this.timed = t; }
        public int getTimerMinutes() { return timerMinutes; }
        public void setTimerMinutes(int m) { this.timerMinutes = Math.max(1, m); }
        public int getTimerJitterPct() { return timerJitterPct; }
        public void setTimerJitterPct(int j) { this.timerJitterPct = Math.max(0, Math.min(50, j)); }
        public String getTimerWhen() { return timerWhen == null ? "AFTER_LOOP" : timerWhen; }
        public void setTimerWhen(String w) { if (w != null && !w.isEmpty()) this.timerWhen = w; }
        public long getNextDueAt() { return nextDueAt; }
        public void setNextDueAt(long t) { this.nextDueAt = t; }

        /** True when the timer has elapsed (and it's been scheduled at least once). */
        public boolean isDue() { return timed && nextDueAt > 0 && System.currentTimeMillis() >= nextDueAt; }

        /**
         * Schedules the next fire from NOW, applying the jitter so it isn't clockwork - a "every
         * 30 minutes" task really lands somewhere in ~24-36 min with the default 20% jitter.
         */
        public void scheduleNext() {
            long base = timerMinutes * 60_000L;
            double j = timerJitterPct / 100.0;
            double factor = 1.0 + ((java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 2 - 1) * j);
            this.nextDueAt = System.currentTimeMillis() + Math.max(1000L, (long) (base * factor));
        }

        public int getRepeat() {
            return Math.max(1, repeat);
        }

        /** Sets how many times this task runs before the queue advances (clamped to >= 1). */
        public void setRepeat(int repeat) {
            this.repeat = Math.max(1, repeat);
        }

        /** v1.80: triggers that run for as long as this task is executing. Live list. */
        public List<main.watchers.Trigger> getTaskTriggers() { return taskTriggers; }

        /** v1.68: @return true when this queue entry runs on the first queue loop only. */
        public boolean isOnStartOnly() {
            return onStartOnly;
        }

        /** v1.68: marks this queue entry as setup-only (first queue loop, then skipped). */
        public void setOnStartOnly(boolean b) {
            this.onStartOnly = b;
        }

        /** @return true when the engine should insert a humanised pause after each completed action (Patch B). */
        public boolean isAutoDelay() {
            return autoDelay;
        }

        /** @return the shortest auto-delay in ms (clamped to >= 0). */
        public int getAutoDelayMinMs() {
            return Math.max(0, autoDelayMinMs);
        }

        /** @return the longest auto-delay in ms (clamped to >= min). */
        public int getAutoDelayMaxMs() {
            return Math.max(getAutoDelayMinMs(), autoDelayMaxMs);
        }

        /** Configures the automatic between-action delay for this task (Patch B). */
        public void setAutoDelay(boolean enabled, int minMs, int maxMs) {
            this.autoDelay = enabled;
            this.autoDelayMinMs = Math.max(0, minMs);
            this.autoDelayMaxMs = Math.max(this.autoDelayMinMs, maxMs);
        }

        @Override public String toString() {
            return name;
        }
    }

//    public static class Action {
//        @SerializedName("type")
//        public ActionType actionType;
//        public String target;
//
//        public Action(ActionType t, String target) {
//            this.actionType = t;
//            this.target = target;
//        }
//
//        // copy-cat constructor
//        public Action(Action other) {
//            this.actionType = other.actionType;
//            this.target = other.target;
//        }
//
//        //TODO copy how CHOP has been done then extract out into their own action classes maybe?
//        public boolean execute() {
//            switch (actionType) {
//                case CHOP:
//                    // find the nearest target tree
//                    GameObject tree = GameObjects.closest(target);
//                    // return early if no nearby trees
//                    if (tree == null) {
//                        Logger.log(Logger.LogType.ERROR, "Unable to find a tree!");
//                        return false;
//                    }
//                    // attempt to chop the tree down
//                    return tree.interact("Chop down");
//
//                case ATTACK:
//                    // find the nearest target npc
//                    NPC npc = NPCs.closest(target);
//
//                    // return early if no npcs found
//                    if (npc == null) {
//                        Logger.log(Logger.LogType.ERROR, "Unable to find a npc!");
//                        return false;
//                    }
//
//                    // attempt to attack the target npc
//                    return  npc.interact("Attack");
//
//                case BANK:
//                    return Bank.open();
//
//                case MINE:
//                    return GameObjects.closest(target) != null && GameObjects.closest(target).interact("Mine");
//
//                case TALK_TO:
//                    return NPCs.closest(target) != null && NPCs.closest(target).interact("Talk-to");
//
//                case DROP:
//                    return Inventory.dropAll(target);
//
//                default:
//                    Logger.log("Action " + actionType + " not implemented in Action.execute()");
//                    return false;
//            }
//        }
//        @Override public String toString() { return actionType.name() + " -> " + target; }
//    }

    public static class Preset {
        String name;
        List<Task> tasks;
        /** Whole-queue loop count to apply when this preset is loaded (0 = infinite). */
        int loops = 1;
        /** v1.64: this preset's script triggers (JSON) - loaded into the live set with the preset. */
        String scriptTriggers;

        public Preset(String name, List<Task> tasks) {
            this.name = name;
            this.tasks = tasks;
        }

        public Preset(String name, List<Task> tasks, int loops) {
            this(name, tasks);
            this.loops = loops;
        }

        // Public accessors so the persistence codec (in main.data.store) can read presets.
        public String getName() { return name; }
        public List<Task> getTasks() { return tasks; }
        public int getLoops() { return loops; }
        public void setLoops(int loops) { this.loops = loops; }
        public String getScriptTriggers() { return scriptTriggers; }
        public void setScriptTriggers(String s) { this.scriptTriggers = s; }
    }

    public boolean showToast(String toastText, JComponent component, boolean success) {
        if (toastText == null || component == null)
            throw new IllegalArgumentException("Error creating button! Invalid text or JComponent parameter(s).");

        // add a flashing effect to the button
        Color flashColor = success ? COLOR_SUCCESS : COLOR_FAILURE;
        flashControl(component, flashColor);

        // ignore any new toasts once queue limit is reached to prevent spam and GUI delays
        if (toastQueue.size() < MAX_TOAST_QUEUE) {
            // add the toast request to the queue
            toastQueue.add(new ToastRequest(toastText, component, success));
            // kick off processing
            processNextToast();
        }

        // return the result for one-line toast/exit capability.
        return success;
    }

    private void processNextToast() {
        if (isToastProcessing || toastQueue.isEmpty()) {
            return;
        }

        isToastProcessing = true;
        ToastRequest request = toastQueue.poll();

        // FIX: Component creation MUST be on the EDT
        SwingUtilities.invokeLater(() -> {
            if (request.message == null || request.message.isEmpty()) {
                isToastProcessing = false;
                processNextToast();
                return;
            }

            // Only show toast if the anchor component is actually visible to the user
            // This prevents "ghost toasts" from background tabs
            if (!request.anchor.isShowing()) {
                isToastProcessing = false;
                processNextToast();
                return;
            }

            try {
                Point location = SwingUtilities.convertPoint(request.anchor, 0, 0, getLayeredPane());
                int x = location.x + (request.anchor.getWidth() / 2);
                int y = location.y - 30;

                final Toast toast = new Toast(request.message, x, y);
                getLayeredPane().add(toast, JLayeredPane.POPUP_LAYER);
                getLayeredPane().revalidate();
                getLayeredPane().repaint();

                // v1.33: errors linger so they're actually readable (they were vanishing in ~1s).
                int showFor = request.success ? 2200 : 5000;
                Timer timer = new Timer(showFor, e -> {
                    getLayeredPane().remove(toast);
                    getLayeredPane().revalidate();
                    getLayeredPane().repaint();
                    isToastProcessing = false;
                    processNextToast();
                });

                timer.setRepeats(false);
                timer.start();
            } catch (Exception e) {
                // Fallback to prevent queue locking if location conversion fails
                isToastProcessing = false;
                processNextToast();
            }
        });
    }

    public String getPlayerName() {
        return Players.getLocal().getName();
    }

    // The four load*() methods below used to fetch individual Supabase columns. They now all
    // route through the single local-profile loader (Patch 2) and are kept only so any external
    // caller keeps working. A full reload is cheap - the profile is one small local JSON file.
    public void loadSettings()     { loadProfileFromDisk(); }

//    // todo FIX
//    public void loadTaskList() {
//        new Thread(() -> {
//            // Use the method from your DataMan class to get the raw JSON
//            String rawJson = dataMan.loadDataByPlayer("tasks");
//            SwingUtilities.invokeLater(() -> {
//                if (rawJson != null)
//                    unpackTaskList(rawJson);
//            });
//        }).start();
//    }

    public void loadTaskLibrary()  { loadProfileFromDisk(); }

    public void loadTaskBuilder()  { loadProfileFromDisk(); }
    public void loadPresets()      { loadProfileFromDisk(); }

    /**
     * Reads this character's profile from disk (off the EDT) and applies it to the UI (on the
     * EDT). If the character has no saved profile yet, the UI is reset to an empty/default state.
     */
    private void loadProfileFromDisk() {
        // Patch B.1: never skip. Logged out -> the shared "default" profile; logged in -> the
        // character's own. (This used to silently return when not logged in, which is why the
        // queue/library appeared to stop loading.)
        final String key = profileKey();
        new Thread(() -> {
            final ProfileData data = LocalStore.load(key);
            SwingUtilities.invokeLater(() -> applyProfile(data));
        }, "DreamMan-Load").start();
    }

    /** Applies a loaded profile to every model + the builder + settings (call on the EDT). */
    private void applyProfile(ProfileData data) {
        if (data == null) {
            // Patch B.1: a missing profile no longer clears the UI. (Logging into a character
            // with no saved profile used to wipe everything built at the login screen.)
            Logger.log(Logger.LogType.INFO,
                    "No saved profile for this key - keeping the current workspace.");
            return;
        }

        // Start from a clean slate so a character doesn't inherit another profile's rows.
        // Patch B.10: in embedded-script mode the JAR owns the queue - leave it alone.
        if (!embeddedScriptMode) modelTaskList.clear();
        libraryAll.clear();          // Patch B.5: master first; the view refilters below
        modelTaskLibrary.clear();
        modelPresets.clear();

        if (data != null) {
            if (data.taskList != null && !embeddedScriptMode)
                for (TaskData td : data.taskList) {
                    Task task = ProfileCodec.fromData(td);
                    if (task != null) modelTaskList.addElement(task);
                }

            if (data.library != null)
                for (TaskData td : data.library) {
                    Task task = ProfileCodec.fromData(td);
                    if (task != null) libraryAll.add(task);
                }
            refilterLibrary();

            if (data.presets != null)
                for (PresetData pd : data.presets) {
                    Preset preset = ProfileCodec.fromData(pd);
                    if (preset != null) modelPresets.addElement(preset);
                }

            // builder draft
            if (data.builder != null) {
                modelTaskBuilder.clear();
                if (data.builder.actions != null)
                    for (ActionData ad : data.builder.actions) {
                        Action a = actionSelector.create(ad != null ? ad.getType() : null);
                        if (a != null) {
                            if (ad.getParams() != null) a.deserialize(ad.getParams());
                            modelTaskBuilder.addElement(a);
                        }
                    }
                if (taskBuilder != null)
                    taskBuilder.applyDraft(data.builder.taskName,
                            data.builder.taskDescription, data.builder.taskStatus,
                            data.builder.autoDelay, data.builder.autoDelayMinMs,
                            data.builder.autoDelayMaxMs);
            }

            applySettings(data.settings);
            Logger.log(Logger.LogType.INFO, "Profile applied: "
                    + modelTaskList.size() + " queued, "
                    + libraryAll.size() + " in library, "
                    + modelPresets.size() + " presets.");
        } else {
            Logger.log(Logger.LogType.INFO, "No saved profile - starting with an empty workspace.");
        }

        queueLoopTarget = (data != null) ? Math.max(0, data.queueLoops) : 1;

        refreshTaskLibrary();   // Patch B.3: counts + selector entries after a profile load

        // v1.30: seed any default tasks the user doesn't have yet (matched by id - never
        // duplicates). Shipped defaults are fetched from <assets>/default-tasks.json; the admin's
        // starred set from <root>/default-tasks.json. See DefaultTasks for the release flow.
        if (!embeddedScriptMode) {
            try {
                if (main.data.store.DefaultTasks.mergeInto(this, libraryAll) > 0)
                    refreshTaskLibrary();
            } catch (Throwable e) {
                Logger.log(Logger.LogType.WARN, "[DefaultTasks] merge failed: " + e);
            }
            // v1.66: keep watching. Admins can push a default at any time, so a running client
            // polls the server's defaults version every half hour and merges anything new - a
            // user who leaves the client open for days still gets them without a restart.
            try {
                main.data.store.DefaultTasks.startSync(this,
                        () -> new ArrayList<>(libraryAll), this::refreshTaskLibrary);
            } catch (Throwable e) {
                Logger.log(Logger.LogType.WARN, "[DefaultTasks] sync start failed: " + e);
            }
        }

        // Patch B.4: restore always-on watchers
        // Patch B.10: an exported script ships its own checks - don't overwrite them either.
        if (!embeddedScriptMode) {
            globalTriggers.clear();
            if (data != null && data.globalTriggers != null && !data.globalTriggers.isBlank())
                globalTriggers.addAll(main.watchers.TriggerCodec.fromJson(data.globalTriggers));
        }
        // v1.64: restore the current queue's script triggers (always - they're queue state, not
        // the embedded script's own global checks)
        scriptTriggers.clear();
        if (data != null && data.scriptTriggers != null && !data.scriptTriggers.isBlank())
            scriptTriggers.addAll(main.watchers.TriggerCodec.fromJson(data.scriptTriggers));
        if (checksEditor != null) checksEditor.reload();   // B.5: the UI list must follow

        // Patch B.6: restore the remember-trackers preference and, if on, the tracked set
        if (data != null) {
            rememberTrackers = data.rememberTrackers;
            if (rememberTrackersCheck != null) rememberTrackersCheck.setSelected(rememberTrackers);
            if (rememberTrackers && data.trackedSkills != null) {
                Set<String> want = new HashSet<>(data.trackedSkills);
                for (SkillData sd : skillRegistry.values())
                    sd.setTracking(want.contains(sd.getSkill().name()));
                refreshTrackerList();     // the side detail-list follows the restored set
                syncSkillTileBorders();   // v1.89: and so do the tiles' highlights
            }
        }

        // Patch B.3: restore per-skill XP goals
        if (data != null && data.skillGoals != null)
            for (SkillData sd : skillRegistry.values()) {
                Integer g = data.skillGoals.get(sd.getSkill().name());
                sd.setGoalXp(g == null ? 0 : g);
            }

        // Patch B.2: restore the between-task auto-wait
        queueAutoWait = data != null && data.queueAutoWait;
        queueAutoWaitMinMs = data != null ? Math.max(0, data.queueAutoWaitMinMs) : 400;
        queueAutoWaitMaxMs = data != null ? Math.max(queueAutoWaitMinMs, data.queueAutoWaitMaxMs) : 1200;
        if (chkQueueWait != null) {
            chkQueueWait.setSelected(queueAutoWait);
            queueWaitMinInput.setText(String.valueOf(queueAutoWaitMinMs));
            queueWaitMaxInput.setText(String.valueOf(queueAutoWaitMaxMs));
            queueWaitMinInput.setEnabled(queueAutoWait);
            queueWaitMaxInput.setEnabled(queueAutoWait);
        }
        selectedPresetIndex = -1;
        currentExecutionIndex = -1;
        queueLoopCurrent = 1;
        refreshTaskListTab();
        refreshTaskLibrary();
        refreshTaskBuilderTab();
        refreshPresetButtonLabels();
        syncLoopControls();
        syncTaskRepeatSpinner();
        updateQueueProgress();
        updateUI();
    }

    /** Pushes a saved {@link SettingsSnapshot} into the settings checkboxes, then syncs the game. */
    private void applySettings(SettingsSnapshot snap) {
        if (snap == null)
            return;

        if (chkAutoSave != null)                 chkAutoSave.setSelected(snap.autoSave);
        if (settingClientChkStartScriptOnLoad != null) {
            settingClientChkStartScriptOnLoad.setSelected(snap.startScriptOnLoad);
            this.startScriptOnLoad = snap.startScriptOnLoad;
        }
        if (settingClientChkExitOnStopWarning != null) {
            settingClientChkExitOnStopWarning.setSelected(snap.exitOnStopWarning);
            this.exitOnStopWarning = snap.exitOnStopWarning;
        }
        if (chkDisableRendering != null)         chkDisableRendering.setSelected(snap.renderingDisabled);
        if (chkHideRoofs != null)                chkHideRoofs.setSelected(snap.hideRoofsEnabled);
        if (chkDataOrbs != null)                 chkDataOrbs.setSelected(snap.dataOrbsEnabled);
        if (chkTransparentSidePanel != null)     chkTransparentSidePanel.setSelected(snap.transparentSidePanel);
        if (chkGameAudio != null)                chkGameAudio.setSelected(snap.gameAudioOn);
        if (chkTransparentChatbox != null)       chkTransparentChatbox.setSelected(snap.transparentChatbox);
        if (chkClickThroughChatbox != null)      chkClickThroughChatbox.setSelected(snap.clickThroughChatbox);
        if (chkShiftClickDrop != null)           chkShiftClickDrop.setSelected(snap.shiftClickDrop);
        if (chkEscClosesInterface != null)       chkEscClosesInterface.setSelected(snap.escClosesInterface);
        if (chkLevelUpInterface != null)         chkLevelUpInterface.setSelected(snap.levelUpInterface);
        if (chkLootNotifications != null)        chkLootNotifications.setSelected(snap.lootNotifications);

        // Make the running client match the restored settings (guards login internally).
        syncSettings();

        if (snap.startScriptOnLoad) {
            Logger.log("Auto-start enabled - resuming script...");
            resume("Auto-start enabled - resuming script...");
        }
    }

//    private void unpackTaskList(String json) {
//        if (json == null || json.isEmpty() || json.equals("[]")) {
//            Logger.log("No task list data found to unpack.");
//            return;
//        }
//
//        try {
//            Gson gson = new Gson();
//
//            // Supabase wraps the response in an outer array: [ { "tasks": [...] } ]
//            // "tasks" is now a List, not a Map
//            List<Map<String, List<Task>>> responseList = gson.fromJson(json, Action.Class);
//
//            if (responseList != null && !responseList.isEmpty()) {
//                List<Task> fetchedTasks = responseList.get(0).get("tasks");
//
//                if (fetchedTasks != null) {
//                    SwingUtilities.invokeLater(() -> {
//                        modelTaskList.clear();
//                        for (Task task : fetchedTasks) {
//                            modelTaskList.addElement(task);
//                        }
//                        refreshTaskListTab();
//                        Logger.log("Successfully unpacked " + modelTaskList.size() + " tasks into the Task List");
//                    });
//                }
//            }
//            listTaskList.repaint();
//        } catch (Exception e) {
//            Logger.log(Logger.LogType.ERROR, "Failed to unpack Task List data: " + e.getMessage());
//            Logger.log(Logger.LogType.WARN, "[DreamMan] " + e.getClass().getSimpleName());   // v1.89 (SDN): no stack traces to console
//        }
//    }


    private void unpackTaskLibrary(String json) {
        if (json == null || json.isEmpty() || json.equals("[]")) {
            Logger.log("No task list data found to unpack.");
            return;
        }

        try {
            Gson gson = new Gson();

            // 1. Supabase returns a List of Objects. Define that type.
            // We are looking for: List<Map<String, Map<String, Task>>>
            java.lang.reflect.Type wrapperType = new TypeToken<List<Map<String, Map<String, Task>>>>(){}.getType();
            List<Map<String, Map<String, Task>>> responseList = gson.fromJson(json, wrapperType);

            if (responseList != null && !responseList.isEmpty()) {
                // 2. Get the "library" map from the first result
                Map<String, Task> fetchedTasks = responseList.get(0).get("library");

                if (fetchedTasks != null) {
                    // 3. Update the UI on the Swing thread
                    SwingUtilities.invokeLater(() -> {
                        librarySetAll(new ArrayList<>(fetchedTasks.values()));
                        refreshTaskLibrary();
                        Logger.log("Successfully unpacked " + libraryAll.size() + " tasks into Task Library");
                    });
                }
            }
            listTaskLibrary.repaint();
        } catch (Exception e) {
            Logger.log(Logger.LogType.ERROR, "Failed to unpack Task Library data: " + e.getMessage());
            Logger.log(Logger.LogType.WARN, "[DreamMan] " + e.getClass().getSimpleName());   // v1.89 (SDN): no stack traces to console
        }
    }

    private void unpackTaskBuilder(String json) {
        if (json == null || json.isEmpty() || json.equals("[]")) return;

        try {
            Gson gson = new Gson();
            JsonArray outerArray = JsonParser.parseString(json).getAsJsonArray();
            if (outerArray.isEmpty()) return;

            JsonElement columnData = outerArray.get(0).getAsJsonObject().get("builder");
            if (columnData == null || columnData.isJsonNull()) return;

            BuilderSnapshot snap = gson.fromJson(columnData, BuilderSnapshot.class);
            if (snap != null) {
                SwingUtilities.invokeLater(() -> {
                    modelTaskBuilder.clear();
                    if (snap.actions != null) {
                        for (ActionData data : snap.actions) {
                            if (data == null || data.getType() == null)
                                continue;

                            Action action = actionSelector.create(data.getType());
                            if (action == null) {
                                Logger.log(Logger.LogType.ERROR, "Unknown action type in saved builder: " + data.getType());
                                continue;
                            }

                            action.deserialize(data.getParams());
                            modelTaskBuilder.addElement(action);
                        }
                    }

                    // (The old restore here passed a TARGET string where an action TYPE was
                    //  expected -> null -> NPE, and pushed a raw Map into a JComboBox<Action>.
                    //  The task name/description/status fields it wrote to were never even
                    //  initialised in this class. A proper builder-draft restore ships with the
                    //  local persistence rework in Patch 2.)

                    refreshTaskBuilderTab();
                    Logger.log(Logger.LogType.SCRIPT, "Successfully unpacked Task Builder data");
                });
            }
        } catch (Exception e) {
            Logger.log(Logger.LogType.ERROR, "Failed to unpack Task Builder data: " + e.getMessage());
            Logger.log(Logger.LogType.WARN, "[DreamMan] " + e.getClass().getSimpleName());   // v1.89 (SDN): no stack traces to console
        }
    }

    private void unpackSettings(String json) {
        if (json == null || json.isEmpty() || json.equals("[]")) return;

        try {
            Gson gson = new Gson();
            JsonArray outerArray = JsonParser.parseString(json).getAsJsonArray();
            if (outerArray.isEmpty()) return;

            JsonElement columnData = outerArray.get(0).getAsJsonObject().get("settings");
            if (columnData == null || columnData.isJsonNull()) return;

            SettingsSnapshot snap = gson.fromJson(columnData, SettingsSnapshot.class);
            if (snap != null) {
                SwingUtilities.invokeLater(() -> {
                    if (chkAutoSave != null)
                        chkAutoSave.setSelected(snap.autoSave);

                    if (settingClientChkStartScriptOnLoad != null) {
                        settingClientChkStartScriptOnLoad.setSelected(snap.startScriptOnLoad);
                        this.startScriptOnLoad = snap.startScriptOnLoad;
                    }

                    if (settingClientChkExitOnStopWarning != null) {
                        settingClientChkExitOnStopWarning.setSelected(snap.exitOnStopWarning);
                        this.exitOnStopWarning = snap.exitOnStopWarning;
                    }

                    if (chkDisableRendering != null)
                        chkDisableRendering.setSelected(snap.renderingDisabled);
                    if (chkHideRoofs != null)
                        chkHideRoofs.setSelected(snap.hideRoofsEnabled);
                    if (chkDataOrbs != null)
                        chkDataOrbs.setSelected(snap.dataOrbsEnabled);
                    if (chkTransparentSidePanel != null)
                        chkTransparentSidePanel.setSelected(snap.transparentSidePanel);
                    if (chkGameAudio != null)
                        chkGameAudio.setSelected(snap.gameAudioOn);
                    if (chkTransparentChatbox != null)
                        chkTransparentChatbox.setSelected(snap.transparentChatbox);
                    if (chkClickThroughChatbox != null)
                        chkClickThroughChatbox.setSelected(snap.clickThroughChatbox);
                    if (chkShiftClickDrop != null)
                        chkShiftClickDrop.setSelected(snap.shiftClickDrop);
                    if (chkEscClosesInterface != null)
                        chkEscClosesInterface.setSelected(snap.escClosesInterface);
                    if (chkLevelUpInterface != null)
                        chkLevelUpInterface.setSelected(snap.levelUpInterface);
                    if (chkLootNotifications != null)
                        chkLootNotifications.setSelected(snap.lootNotifications);

                    // Note: We update the checkboxes here. To apply these directly to the
                    // DreamBot client on startup, you would add the API calls here
                    // (e.g., ClientSettings.toggleDataOrbs(snap.dataOrbsEnabled);)
                    // but it's safest to ensure Client.isLoggedIn() first.

                    Logger.log(Logger.LogType.SCRIPT, "Unpacked settings successfully! Syncing game settings...");

                    // Now that the UI matches the server data, force the game to match the UI
                    syncSettings();

                    if (snap.startScriptOnLoad) {
                        Logger.log("Auto-start detected! Starting script...");
                        resume("Auto-start detected! Resuming script...");
                    }
                });
            }
        } catch (Exception e) {
            Logger.log(Logger.LogType.ERROR, "Failed to unpack settings: " + e.getMessage());
            Logger.log(Logger.LogType.WARN, "[DreamMan] " + e.getClass().getSimpleName());   // v1.89 (SDN): no stack traces to console
        }
    }

    private void unpackPresets(String json) {
        if (json == null || json.isEmpty() || json.equals("[]")) return;

        try {
            Gson gson = new Gson();
            JsonArray outerArray = JsonParser.parseString(json).getAsJsonArray();
            if (outerArray.isEmpty()) return;

            JsonElement columnData = outerArray.get(0).getAsJsonObject().get("presets");
            if (columnData == null || columnData.isJsonNull()) return;

            java.lang.reflect.Type presetType = new TypeToken<List<Preset>>(){}.getType();
            List<Preset> fetchedPresets = gson.fromJson(columnData, presetType);

            if (fetchedPresets != null && !fetchedPresets.isEmpty()) {
                SwingUtilities.invokeLater(() -> {
                    modelPresets.clear();
                    for (Preset preset : fetchedPresets)
                        modelPresets.addElement(preset);
                    selectedPresetIndex = -1;
                    refreshPresetButtonLabels();
                    Logger.log(Logger.LogType.SCRIPT, "Successfully unpacked " + modelPresets.size() + " presets.");
                });
            }
        } catch (Exception e) {
            Logger.log(Logger.LogType.ERROR, "Failed to unpack presets column: " + e.getMessage());
        }
    }

    public void syncSettings() {
        // Don't try to click menus if we aren't in the game world
        if (!Client.isLoggedIn()) {
            Logger.log("Sync skipped: Not logged in.");
            return;
        }

        new Thread(() -> {
            // Guard the UI
            isSettingProcessing = true;
            setStatus("Syncing profile...");

            try {
                // 1. Hide Roofs
                if (chkHideRoofs != null) {
                    boolean target = chkHideRoofs.isSelected();
                    // Only act if the game state differs from our checkbox
                    if (ClientSettings.areRoofsHidden() != target) {
                        ClientSettings.toggleRoofs(target);
                        Thread.sleep(600);
                    }
                }

                // 2. Data Orbs
                if (chkDataOrbs != null) {
                    boolean target = chkDataOrbs.isSelected();
                    if (ClientSettings.areDataOrbsEnabled() != target) {
                        ClientSettings.toggleDataOrbs(target);
                        Thread.sleep(600);
                    }
                }

                // 3. Game Audio
                if (chkGameAudio != null) {
                    boolean target = chkGameAudio.isSelected();
                    if (ClientSettings.isGameAudioOn() != target) {
                        ClientSettings.toggleGameAudio(target);
                        Thread.sleep(600);
                    }
                }

                // TODO Add any other ClientSettings checks here following the same pattern...

            } catch (InterruptedException e) {
                Logger.log(Logger.LogType.ERROR, "Error synchronizing in-game settings!");
                Logger.log(Logger.LogType.WARN, "[DreamMan] " + e.getClass().getSimpleName());   // v1.89 (SDN): no stack traces to console

            } finally {
                isSettingProcessing = false;
                setStatus("Profile Synced!");

                // FIX: Ensure Toast creation and UI updates happen on the EDT
                SwingUtilities.invokeLater(() -> {
                    // Check if the Settings tab is currently showing to decide where to anchor
                    // If you want to avoid "random toasts," anchor to the mainTabs or a specific header label
                    showToast("All settings synced.", mainTabs, true);
                });
            }
        }).start();
    }

    /**
     * Flashes a UI component a specific color and displays a message,
     * then reverts to original state exactly without corrupting background colors.
     *
     * @param component The JComponent to flash (e.g., btnTriggerBreak).
     * @param flashColor The color to flash (e.g., new Color(100, 0, 0) for red).
     */
    private void flashControl(JComponent component, Color flashColor) {
        // Patch B.5 fix: FlatButtonUI paints from the "fillColor" client property, NOT from
        // setBackground(). The old code set an opaque background the UI ignored, so the raw
        // component background showed through and STAYED after the flash - the "arrows change
        // colour and stick" bug. Flash the property the UI reads, then clear it to restore the
        // button's normal look exactly.
        if (component == null) return;
        component.putClientProperty("fillColor", flashColor);
        component.repaint();

        Timer revertTimer = new Timer(TIME_FLASH, e -> {
            component.putClientProperty("fillColor", null);
            component.repaint();
        });
        revertTimer.setRepeats(false);
        revertTimer.start();
    }

    /**
     * v1.68: points the on-start checkbox at the selected queue entry without firing its
     * listener (which would otherwise write the old value straight back onto the new task).
     */
    private void syncTaskOnStartCheckbox() {
        if (chkTaskOnStart == null) return;
        Task sel = listTaskList.getSelectedValue();
        taskOnStartSyncing = true;
        try {
            chkTaskOnStart.setEnabled(sel != null);
            chkTaskOnStart.setSelected(sel != null && sel.isOnStartOnly());
        } finally {
            taskOnStartSyncing = false;
        }
    }

    /** Right-click menu for a queued task card: edit / duplicate / set repeat / remove. */
    private void showTaskContextMenu(int px, int py, int index) {
        if (index < 0 || index >= modelTaskList.size()) return;
        Task task = modelTaskList.getElementAt(index);
        if (task == null) return;

        JPopupMenu menu = new JPopupMenu();

        JMenuItem edit = new JMenuItem("Edit in builder");
        edit.addActionListener(a -> { loadIntoBuilder(task); mainTabs.setSelectedIndex(2); });

        JMenuItem dup = new JMenuItem("Duplicate");
        dup.addActionListener(a -> {
            modelTaskList.add(index + 1, new Task(task));
            refreshTaskListTab(index + 1);
        });

        JMenuItem repeat = new JMenuItem("Set repeat ×N…");
        repeat.addActionListener(a -> promptRepeat(task));

        // v1.68: same toggle as the checkbox, where the action-level one already lives.
        JCheckBoxMenuItem miOnStart = new JCheckBoxMenuItem(
                "Only run on first queue loop (setup)", task.isOnStartOnly());
        miOnStart.setToolTipText("Applies to THIS entry only — the same task elsewhere "
                + "in the queue keeps running every loop.");
        miOnStart.addActionListener(a -> {
            task.setOnStartOnly(miOnStart.isSelected());
            saveAll(false);
            syncTaskOnStartCheckbox();
            listTaskList.repaint();
        });

        JMenuItem runHere = new JMenuItem("Run from here");
        runHere.addActionListener(a -> {
            setCurrentExecutionIndex(index);
            resetLoopProgress();
            listTaskList.repaint();
            isMenuPaused(false);   // v1.31: starts immediately
        });

        JMenuItem remove = new JMenuItem("Remove");
        remove.addActionListener(a -> {
            modelTaskList.remove(index);
            refreshTaskListTab();
        });

        menu.add(edit);
        menu.add(dup);
        menu.add(repeat);
        menu.add(miOnStart);   // v1.68
        menu.add(runHere);
        menu.addSeparator();
        menu.add(remove);
        menu.show(listTaskList, px, py);
    }

    /**
     * v1.31: laid-out hit-test for the row's -/+ loop steppers. @return true when consumed.
     */
    private boolean handleRepeatStepperClick(MouseEvent e, int index) {
        if (taskCardRenderer == null) return false;
        Task t = modelTaskList.getElementAt(index);
        if (t == null || t.isTimed()) return false;
        Rectangle b = listTaskList.getCellBounds(index, index);
        if (b == null) return false;
        taskCardRenderer.getListCellRendererComponent(listTaskList, t, index, false, false);
        taskCardRenderer.setBounds(0, 0, b.width, b.height);
        taskCardRenderer.doLayout();
        // FlowLayout panels need a second pass for nested children
        for (Component c : taskCardRenderer.getComponents())
            if (c instanceof Container) ((Container) c).doLayout();
        Component deep = SwingUtilities.getDeepestComponentAt(
                taskCardRenderer, e.getX() - b.x, e.getY() - b.y);
        int delta = 0;
        if (deep == taskCardRenderer.repMinus) delta = -1;
        else if (deep == taskCardRenderer.repPlus) delta = +1;
        if (delta == 0) return false;
        // v1.32: a task's repeat is another loop multiplier, so it's capped at the tier's max
        // loops too (∞-tier keeps the 999 ceiling). This is half of closing the free-loop
        // loophole; the other half is resetting exported/published loops to 1.
        int ceiling = main.market.Tier.allowsInfinite() ? 999
                : Math.max(1, main.market.Tier.maxLoops());
        int now = Math.max(1, Math.min(ceiling, Math.max(1, t.getRepeat()) + delta));
        if (delta > 0 && now == t.getRepeat())   // already at the ceiling
            showToast("Your tier caps task loops at " + ceiling, listTaskList, false);
        t.setRepeat(now);
        listTaskList.repaint();
        syncTaskRepeatSpinner();
        saveAll(false);
        return true;
    }

    /** Popup spinner to set a task's per-task repeat count. */
    private void promptRepeat(Task task) {
        JSpinner sp = new JSpinner(new SpinnerNumberModel(task.getRepeat(), 1, 999, 1));
        sp.setPreferredSize(new Dimension(90, 28));
        int r = JOptionPane.showConfirmDialog(this, sp,
                "Repeat ×N for \"" + task.getName() + "\"", JOptionPane.OK_CANCEL_OPTION);
        if (r == JOptionPane.OK_OPTION) {
            task.setRepeat((int) sp.getValue());
            listTaskList.repaint();
            syncTaskRepeatSpinner();
        }
    }

    private void loadIntoBuilder(Task task) {
        if(task == null)
            return;

        taskBuilder.loadTask(task);
    }

    private void refreshDynamicControls() {
        if (taskBuilder != null)
            taskBuilder.refresh();
    }

    public void isMenuPaused(boolean paused) {
        isMenuPaused = paused;
    }

    public boolean isMenuPaused() {
        return isMenuPaused;
    }

    /** @return live SkillData for every skill currently being tracked (Patch B.2 overlay). */
    public List<SkillData> getTrackedSkills() {
        List<SkillData> out = new ArrayList<>();
        try {
            skillRegistry.values().stream()
                    .filter(SkillData::isTracking)
                    .forEach(out::add);
        } catch (Throwable ignored) {}
        return out;
    }

    /*
     * Patch B.9's Session card (bank PIN + account switching) was dissolved in B.17: both
     * controls belong to the person, so they live in the Player card now - see
     * buildPlayerCard() / buildBankPinRow() / buildAccountSwitchRow().
     */

    private JComboBox<String> accountCombo;
    private final JLabel lblBankPin = new JLabel("(none)");
    private final JLabel lblAccountSupport = new JLabel("checking...");

    /** Pulls the account list from DreamBot's Account Manager (when this build exposes it). */
    private void refreshAccountList() {
        if (accountCombo == null) return;
        List<String> accounts = main.tools.AccountSwitcher.listAccounts();
        accountCombo.removeAllItems();
        for (String a : accounts) accountCombo.addItem(a);

        boolean supported = main.tools.AccountSwitcher.isSupported();
        String current = main.tools.AccountSwitcher.currentAccount();

        // Patch B.17: an empty list usually means this DreamBot build doesn't expose its
        // Account Manager list - not that the user did anything wrong. Say so, and at least
        // show the account that IS logged in so the row isn't a dead end.
        if (accounts.isEmpty()) {
            if (current != null && !current.isEmpty()) {
                accountCombo.addItem(current);
                lblAccountSupport.setText("live only");   // v1.30: short - long text overlapped
            } else {
                lblAccountSupport.setText("none visible");
            }
            accountCombo.setEnabled(false);
            lblAccountSupport.setToolTipText("<html>DreamMan reads DreamBot's Account Manager."
                    + "<br>This client build doesn't expose the saved list, so switching from"
                    + "<br>here isn't available - use DreamBot's own account picker.</html>");
        } else {
            accountCombo.setEnabled(supported);
            lblAccountSupport.setText(supported
                    ? accounts.size() + " available"
                    : accounts.size() + " found (switch unsupported)");
            lblAccountSupport.setToolTipText(null);
        }

        if (current != null && !current.isEmpty()) accountCombo.setSelectedItem(current);
    }

    /**
     * Writes a compact machine-readable status snapshot to {@code <home>/DreamMan/status.json}
     * every ~2 seconds (Patch B.3). This is the groundwork for the remote web dashboard: an
     * external process can poll the file for the account, its LIVE tile (map replica), the
     * current task/activity, queue + loop progress, pause state and uptime - without touching
     * the client. Remote CONTROL can later ride the same channel in reverse.
     */
    private void writeStatusSnapshot(String taskName) {
        // v1.30: keep the Loot Tracker's lifetime totals attributed to the right character
        try { main.tools.LootTracker.setCharacter(safePlayerName()); } catch (Throwable ignored) {}
        long now = System.currentTimeMillis();
        if (now - lastStatusSnapshotAt < 2000)
            return;
        lastStatusSnapshotAt = now;

        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("character", safePlayerName());
        try {
            if (Client.isLoggedIn()) {
                Tile t =
                        Players.getLocal().getTile();
                if (t != null) {
                    snap.put("x", t.getX());
                    snap.put("y", t.getY());
                    snap.put("z", t.getZ());
                }
            }
        } catch (Throwable ignored) {}
        snap.put("task", taskName == null || taskName.isEmpty() ? null : taskName);
        snap.put("activity", getStatusText());
        snap.put("queueIndex", currentExecutionIndex);
        snap.put("queueSize", modelTaskList.size());
        snap.put("loopCurrent", getQueueLoopCurrentValue());
        snap.put("loopTarget", queueLoopTarget);
        snap.put("paused", isMenuPaused());
        snap.put("uptimeMs", getUptimeMillis());
        snap.put("updatedAt", now);

        final Map<String, Object> payload = snap;
        statusWriter.submit(() -> {
            try {
                LocalStore.exportToFile(payload,
                        new java.io.File(LocalStore.getRoot(), "status.json"));
            } catch (Throwable ignored) {}
        });
    }

    /** Called by the builder after saves so counts/selector/inspector stay current (B.3). */
    public void refreshLibraryFromBuilder() {
        refreshTaskLibrary();
    }

    /** Rebuilds the action dropdown's "library tasks" group from the current library (B.3). */
    private void syncSelectorLibraryEntries() {
        if (actionSelector == null) return;
        actionSelector.setLibraryTasks(buildLibraryEntries());
    }

    /** Resolves a library task by id first, then by name (case-insensitive). Null if absent. */
    public Task findLibraryTask(String id, String name) {
        for (Task t : libraryAll)
            if (t != null && id != null && id.equals(t.getId())) return t;
        if (name != null && !name.isEmpty())
            for (Task t : libraryAll)
                if (t != null && name.equalsIgnoreCase(t.getName())) return t;
        return null;
    }

    private void toggleScriptState() {
        if (scriptManager == null)
            return;

        if (isMenuPaused())
            resume("Script resumed!");
        else
            pause("Script paused!");
    }

    public void resume(String status) {
        if (isMenuPaused()) {
            // Swing components may only be touched on the EDT (this is called from the script thread too)
            SwingUtilities.invokeLater(() -> btnPlayPause.setText("\u23F8"));  // ⏸ single glyph
            setStatus(status);
            ///  MUST SET MENU STATE BEFORE SCRIPT STATE TO PREVENT INFINITE LOOPS
            isMenuPaused(false);
            scriptManager.resume();
        }
    }

    public boolean pause(String status) {
        if (!isMenuPaused()) {
            // Swing components may only be touched on the EDT (this is called from the script thread too)
            SwingUtilities.invokeLater(() -> btnPlayPause.setText("▶"));
            setStatus(status);
            ///  MUST SET MENU STATE BEFORE SCRIPT STATE TO PREVENT INFINITE LOOPS
            isMenuPaused(true);
            scriptManager.pause();
        }

        return isMenuPaused;
    }

    private void stop() {
        //TODO see if this needs to be called from Dream Bot when children call stop()?
        if (!exitOnStopWarning
                || JOptionPane.showConfirmDialog(
                this,
                "This may result in the loss of unsaved changes.\nAre you sure you want to exit?"
        ) == JOptionPane.YES_OPTION) {

            scriptManager.stop();
            dispose();
        }
    }

    /**
     * Set whether mouse input is enabled or not.
     *
     * @param enabled True if mouse input is enabled, else false to disable user mouse input.
     */
    private static boolean setMouseInput(boolean enabled) {
        if (isMouseInput != enabled) {
            // update user mouse toggle
            Client.getInstance().setMouseInputEnabled(enabled);
            // update mouse input button icon
            btnMouseToggle.setText(enabled ? "🖱" : "🚫");
            // update input enabled field to prevent circular loops
            isMouseInput = enabled;
        }

        return isMouseInput;
    }

    public static boolean getMouseInput() {
        // fetch client mouse input state
        boolean isClientEnabled = Instance.isMouseInputEnabled();

        // check if menu/client input states match and return true
        if (isMouseInput && isClientEnabled)
            return true;

        // if mismatch detected, sync menu with client and return result
        return setMouseInput(isClientEnabled);
    }

    /**
     * Toggles the mouse input on/off based to swap its current execution state.
     */
    private void toggleMouseInput() {
        setMouseInput(!isMouseInput);
    }

    /**
     * Set whether keyboard input is enabled or not.
     *
     * @param enabled True if keyboard input is enabled, else false to disable user keyboard input.
     */
    private boolean setKeyboardInput(boolean enabled) {
        // if menu isn't already synced
        if (isKeyboardInput != enabled) {
            // update user keyboard toggle
            Client.getInstance().setKeyboardInputEnabled(enabled);
            // update keyboard input button icon
            btnKeyboardToggle.setText(enabled ? "⌨" : "🚫");
            // update input enabled field to prevent circular loops
            isKeyboardInput = enabled;
        }

        return isKeyboardInput;
    }

    public final boolean getKeyboardInput() {
        // fetch client keyboard input state
        boolean isClientEnabled = Instance.getInstance().isKeyboardInputEnabled();

        // check if menu/client input states match and return true
        if (isKeyboardInput && isClientEnabled)
            return true;

        // if mismatch detected, sync menu with client and return result
        return setKeyboardInput(isClientEnabled);
    }

    /**
     * Toggles the keyboard input on/off based to swap its current execution state.
     */
    private void toggleKeyboardInput() {
        setKeyboardInput(!isKeyboardInput);
    }

    private void styleSpinner(JSpinner s) {
        JFormattedTextField field = ((JSpinner.DefaultEditor) s.getEditor()).getTextField();
        field.setBackground(new Color(30, 30, 30));
        field.setForeground(COLOR_BLOOD);
        s.setBorder(new LineBorder(COLOR_BORDER_DIM));
    }

    private <T> boolean navigateQueue(int dir, JList<T> list, DefaultListModel<T> model) {
        // fetch the selected preset index to calculate which page we are on
        int index = list.getSelectedIndex();
        // ensure an index exists for the provided list
        if (index == -1 || index + dir < 0 || index + dir >= model.size())
            return false;

        // update and validate selected index
        list.setSelectedIndex(index + dir);
        list.ensureIndexIsVisible(index + dir);

        return true;
    }

    private void styleJList(JList<?> l) {
        l.setBackground(PANEL_SURFACE);
        l.setForeground(TEXT_MAIN);
        l.setSelectionBackground(TAB_SELECTED);
    }

    /**
     * Creates a standard button which matches the GUI theme's default style.
     *
     * @param btnText The text displayed on the button.
     * @return A {@link JButton} component styled to match the default theme.
     */
    private <T> JButton createNavButton(String btnText, DefaultListModel<T> model, JList<T> list) {
        JButton btn = createButton(btnText);
        // determine if this button is a navigation button or not to add extra features
        int dir = btn.getText().equals("▲") ? -1 : btn.getText().equals("▼") ? 1 : 0;
        // return early if this is not a navigation button to avoid adding features to the wrong button type
        if (dir == 0)
            return btn;

        // add the button on-click event for navigation buttons
        btn.addActionListener(e -> {
            // determine if this button event is successful or not
            boolean success = list == null || navigateQueue(dir, list, model);
            // add a flash to this control to flicker colors on click
            flashControl(btn, success ? COLOR_SUCCESS : COLOR_FAILURE);
        });

        return btn;
    }

    private <T> JButton createNavButton(String btnText, DefaultListModel<Preset> model) {
        return createNavButton(btnText, model, null);
    }

    private ImageIcon loadMiscIcon(String name) {
        // v1.32 (SDN): fetched at runtime + cached, not bundled. Repaints when the real icon lands.
        return main.tools.Assets.icon("misc", name, 18, name, this::repaint);
    }

    private ImageIcon loadSkillIcon(Skill skill) {
        // v1.32 (SDN): runtime fetch + cache with a drawn fallback (the skill's initial).
        return main.tools.Assets.icon("skills", skill.name().toLowerCase() + "_icon", 26,
                skill.name(), this::repaint);
    }

    /** v1.31: the Logs tab - searchable chat + player logs, memory only. */
    private JPanel createLogsTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        panel.add(createSubtitle("Logs"), BorderLayout.NORTH);
        panel.add(new main.menu.components.LogsPanel(), BorderLayout.CENTER);
        return panel;
    }

    // ── v1.86: the live side panel ───────────────────────────────────────────

    /**
     * The side panel while LOGGED OUT: just the relocated Login button (auto-login through the
     * script, exactly what the old control-bar button did) with a line saying what appears
     * once you're in. The panel is gated because everything on the live card - position, worn
     * gear, inventory, quest points - only exists with a logged-in player behind it.
     */
    private JPanel buildSideLoginCard() {
        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));

        JLabel head = new JLabel("Live Panel");
        head.setForeground(COLOR_BLOOD);
        head.setFont(new Font("Consolas", Font.BOLD, 20));

        JLabel blurb = new JLabel("<html><div style='width:210px;text-align:center'>"
                + "Log in and your player appears here - minimap, worn equipment, inventory "
                + "and session totals.</div></html>");
        blurb.setForeground(TEXT_DIM);
        blurb.setFont(Theme.font(12));

        JButton btnLogin = createButton("Login");
        btnLogin.putClientProperty("accent", Boolean.TRUE);
        btnLogin.setToolTipText("Log in to the configured account");
        btnLogin.addActionListener(e -> { if (scriptControls != null) scriptControls.requestLogin(); });

        for (JComponent c : new JComponent[]{head, blurb, btnLogin}) c.setAlignmentX(0.5f);
        stack.add(head);
        stack.add(Box.createVerticalStrut(10));
        stack.add(blurb);
        stack.add(Box.createVerticalStrut(18));
        stack.add(btnLogin);

        JPanel card = new JPanel(new GridBagLayout());   // centres the stack both ways
        card.setOpaque(false);
        card.add(stack);
        return card;
    }

    /**
     * The side panel while LOGGED IN (v1.86) - the sketch, top to bottom: username, then the
     * "Cbt · Total · QP" line, the circular minimap (globe button opens the full Explv world
     * map), the equipment doll + inventory with their preset bars (scrolling as one block),
     * and at the bottom the session summary line, the F2P/P2P totals with their stars, and the
     * relocated Log out.
     */
    private JPanel buildSideLiveCard() {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // ── header: who + the three numbers + the minimap ──
        sidePlayerName.setForeground(TEXT_MAIN);
        sidePlayerName.setFont(new Font("Consolas", Font.BOLD, 17));
        sidePlayerStats.setForeground(TEXT_DIM);
        sidePlayerStats.setFont(new Font("Consolas", Font.PLAIN, 12));

        // v1.87: the minimap face now matches the real one - HP / Prayer / Run / Spec orbs
        // arc down its left side and the world-map button is a little planet Earth on the
        // bottom-right rim. The orb numbers come from this supplier; every read is guarded so
        // a logged-out tick just dims the orbs.
        main.menu.components.MiniMapPanel miniMap = new main.menu.components.MiniMapPanel(() -> {
            try {
                if (!Client.isLoggedIn()) return null;
                org.dreambot.api.wrappers.interactive.Player me = Players.getLocal();
                if (me == null) return null;
                Tile t = me.getTile();
                return t == null ? null : new int[]{t.getX(), t.getY(), t.getZ()};
            } catch (Throwable ignored) {
                return null;
            }
        }, () -> {
            try {
                if (!Client.isLoggedIn()) return null;
                return new int[]{
                        Skills.getBoostedLevel(Skill.HITPOINTS), Skills.getRealLevel(Skill.HITPOINTS),
                        Skills.getBoostedLevel(Skill.PRAYER), Skills.getRealLevel(Skill.PRAYER),
                        (int) org.dreambot.api.methods.walking.impl.Walking.getRunEnergy(),
                        specPercent()};
            } catch (Throwable ignored) {
                return null;
            }
        });
        miniMap.setPreferredSize(new Dimension(210, 240));

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        for (JComponent c : new JComponent[]{sidePlayerName, sidePlayerStats, miniMap})
            c.setAlignmentX(0.5f);
        head.add(sidePlayerName);
        head.add(Box.createVerticalStrut(2));
        head.add(sidePlayerStats);
        head.add(Box.createVerticalStrut(6));
        head.add(miniMap);
        // v1.89d: head is NOT pinned any more - it goes into the scroll below with everything
        // else. A docked client is tall and narrow, and pinning the name + minimap + a
        // three-row footer left the equipment doll fighting for whatever was left. Only Log
        // out stays put now, because that's the one control you may need in a hurry.

        // ── centre: the doll + inventory + preset bars, scrolling as one ──
        main.menu.components.EquipmentPanel eq = new main.menu.components.EquipmentPanel();
        // Equipping a preset = a real Task built from FindBank/Bank/Wield, injected at the
        // CURRENT queue position and started immediately. When it finishes, the queue simply
        // continues with whatever was next - your original task order is untouched.
        eq.wire(new main.menu.components.JActionSelector(),
                task -> {
                    int at = currentExecutionIndex >= 0
                            ? Math.min(currentExecutionIndex, modelTaskList.size()) : 0;
                    modelTaskList.add(at, task);
                    setCurrentExecutionIndex(at);
                    refreshTaskListTab(at);
                    isMenuPaused(false);
                    showToast("Equipping now: " + task.getName(), listTaskList, true);
                });
        // v1.87: the scroll column is the doll + inventory + presets AND, folded beneath
        // them, the PLAYER DETAILS section - everything the old Status tab knew about the
        // person at the keyboard (membership, account type, world, bank PIN, Jagex-account
        // switching), collapsed by default so the tidy panel stays tidy.
        JPanel scrollColumn = new JPanel();
        scrollColumn.setOpaque(false);
        scrollColumn.setLayout(new BoxLayout(scrollColumn, BoxLayout.Y_AXIS));
        head.setAlignmentX(0f);
        scrollColumn.add(head);                    // v1.89d: name + stats + minimap scroll too
        scrollColumn.add(Box.createVerticalStrut(8));
        eq.setAlignmentX(0f);
        scrollColumn.add(eq);
        JComponent details = buildSidePlayerDetails();
        details.setAlignmentX(0f);
        scrollColumn.add(details);
        JComponent supportBlock = buildSupportPanel();   // v1.89d
        supportBlock.setAlignmentX(0f);
        scrollColumn.add(supportBlock);
        JScrollPane eqScroll = Theme.thinScrollbars(new JScrollPane(scrollColumn));
        eqScroll.setBorder(null);
        eqScroll.getViewport().setBackground(PANEL_SURFACE);
        eqScroll.getVerticalScrollBar().setUnitIncrement(14);
        card.add(eqScroll, BorderLayout.CENTER);

        // ── bottom: session line, the starred totals, Log out ──
        sessionSummaryLabel.setFont(new Font("Consolas", Font.PLAIN, 12));
        sessionSummaryLabel.setForeground(TEXT_DIM);

        for (JLabel star : new JLabel[]{totalLevelLabelF2P, totalLevelLabelP2P}) {
            star.setForeground(TEXT_MAIN);
            star.setFont(new Font("Consolas", Font.BOLD, 14));
            star.setHorizontalAlignment(SwingConstants.CENTER);
        }
        JPanel stars = new JPanel(new GridLayout(1, 2));
        stars.setOpaque(false);
        stars.add(totalLevelLabelF2P);
        stars.add(totalLevelLabelP2P);

        JButton btnLogout = createButton("Log out");
        btnLogout.setToolTipText("Log this character out");
        btnLogout.addActionListener(e -> { if (scriptControls != null) scriptControls.requestLogout(); });
        JPanel logoutRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        logoutRow.setOpaque(false);
        logoutRow.add(btnLogout);

        // v1.89d: the session line and the level totals join the scroll; ONLY Log out is
        // pinned. Those two are glanceable status, not controls - losing them off the bottom
        // of a scroll costs nothing, whereas losing the logout button when a script misbehaves
        // is exactly the moment you want it.
        JPanel scrollFooter = new JPanel(new GridLayout(2, 1, 0, 6));
        scrollFooter.setOpaque(false);
        scrollFooter.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
        scrollFooter.add(sessionSummaryLabel);
        scrollFooter.add(stars);
        scrollFooter.setAlignmentX(0f);
        scrollColumn.add(scrollFooter);

        logoutRow.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, COLOR_BORDER_DIM),
                BorderFactory.createEmptyBorder(6, 0, 2, 0)));
        card.add(logoutRow, BorderLayout.SOUTH);

        return card;
    }

    /**
     * v1.89d: the two services DreamMan sits on top of, in a collapsible block at the bottom of
     * the Player panel.
     *
     * <p>Deliberately <b>collapsed by default and out of the way</b>: an advert that interrupts
     * you is one you resent, and this is the panel people keep open all session. Expanded, it
     * explains what each service actually buys and - the part worth saying plainly - that these
     * subscriptions are what fund the client and the game DreamMan is built on. Without them
     * there is no botting API to write against, and no way for someone who can't code to build
     * and share a script.
     *
     * <p>No affiliate tracking, no fetching, no per-user targeting. Two links and an honest
     * explanation.
     */
    private JComponent buildSupportPanel() {
        JPanel section = new JPanel(new BorderLayout());
        section.setOpaque(false);
        section.setBorder(new EmptyBorder(10, 2, 4, 2));

        JLabel header = new JLabel("SUPPORTING THE TOOLS");
        header.setForeground(Theme.ACCENT);
        header.setFont(new Font("Segoe UI", Font.BOLD, 11));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, COLOR_BORDER_DIM),
                new EmptyBorder(8, 0, 4, 0)));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        header.setToolTipText("What DreamBot VIP and Jagex membership are for");

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setVisible(false);

        header.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                boolean show = !content.isVisible();
                content.setVisible(show);
                header.setText(show ? "HIDE" : "SUPPORTING THE TOOLS");
                section.revalidate();
                section.repaint();
            }
        });

        JLabel why = new JLabel("<html><div style='width:200px'>DreamMan is free and always will "
                + "be. It runs on two things that aren't:</div></html>");
        why.setForeground(TEXT_DIM);
        why.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        why.setAlignmentX(0f);
        why.setBorder(new EmptyBorder(0, 0, 6, 0));
        content.add(why);

        content.add(supportRow("DreamBot VIP",
                "The client DreamMan is a script for. VIP unlocks more accounts at once, "
                        + "the script queue and better performance.",
                "https://dreambot.org/vip"));
        content.add(supportRow("Jagex membership",
                "Members' skills, quests and areas. Some VIP DreamMan scripts in later "
                        + "releases will need a members' world to run at all.",
                "https://www.runescape.com/membership"));

        JLabel closing = new JLabel("<html><div style='width:200px'><i>Paying for these keeps the "
                + "client and the game going \u2014 which is what lets someone who has never "
                + "written a line of code build a script here and share it.</i></div></html>");
        closing.setForeground(Theme.TEXT_MUTED);
        closing.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        closing.setAlignmentX(0f);
        closing.setBorder(new EmptyBorder(6, 0, 2, 0));
        content.add(closing);

        section.add(header, BorderLayout.NORTH);
        section.add(content, BorderLayout.CENTER);
        return section;
    }

    /** One service: name (a link), and one line on what it actually buys. */
    private JComponent supportRow(String name, String blurb, String url) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBorder(new EmptyBorder(4, 0, 6, 0));
        row.setAlignmentX(0f);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));

        JLabel link = new JLabel(name);
        link.setForeground(Theme.ACCENT);
        link.setFont(new Font("Segoe UI", Font.BOLD, 12));
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.setToolTipText(url);
        link.setAlignmentX(0f);
        link.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(new java.net.URI(url));
                } catch (Throwable t) {
                    // headless, or no browser - hand over the address instead of failing silently
                    try {
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                                new java.awt.datatransfer.StringSelection(url), null);
                        showToast("Link copied: " + url, link, true);
                    } catch (Throwable ignored) {}
                }
            }
        });
        row.add(link);

        JLabel text = new JLabel("<html><div style='width:200px'>" + blurb + "</div></html>");
        text.setForeground(TEXT_DIM);
        text.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        text.setAlignmentX(0f);
        row.add(text);
        return row;
    }

    /** v1.88: renders any drawn {@link main.menu.components.UIIcons} glyph into a tab icon. */
    private static java.awt.Image iconToImage(Icon icon, int size) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        icon.paintIcon(null, g, 0, 0);
        g.dispose();
        return img;
    }

    private ImageIcon loadTabIcon(String name) {
        // v1.32 (SDN): runtime fetch + cache; repaint the tab strip when the icon arrives.
        return main.tools.Assets.icon("tabs", name, 16, name,
                () -> { if (mainTabs != null) mainTabs.repaint(); });
    }

    private ImageIcon loadStatusIcon(String name) {
        // v1.32 (SDN): runtime fetch + cache with a drawn fallback.
        return main.tools.Assets.icon("status", name, 16, name, this::repaint);
    }

    private void updateUI() {
        SwingUtilities.invokeLater(() -> {
            // v1.87: automatic dialogue capture for the Player Log (self rate-limited,
            // change-detected, every read guarded - see DialogueWatcher).
            try { main.tools.DialogueWatcher.poll(); } catch (Throwable ignored) {}
            // ── Patch B.3: live Script card + status.json (the future web-UI's data feed) ──
            String taskName = getCurrentTaskName();
            lblScriptTask.setText(taskName == null || taskName.isEmpty() ? "—" : taskName);
            lblScriptActivity.setText(getStatusText() == null ? "—" : getStatusText());
            lblScriptQueue.setText(currentExecutionIndex >= 0
                    ? (currentExecutionIndex + 1) + " / " + modelTaskList.size()
                    : "idle (" + modelTaskList.size() + " queued)");
            lblScriptLoop.setText(queueLoopTarget <= 0
                    ? getQueueLoopCurrentValue() + " / ∞"
                    : getQueueLoopCurrentValue() + " / " + queueLoopTarget);
            long up = getUptimeMillis();
            lblScriptUptime.setText(String.format("%02d:%02d:%02d",
                    up / 3600000, (up / 60000) % 60, (up / 1000) % 60));
            lblScriptPaused.setText(isMenuPaused() ? "yes" : "no");
            writeStatusSnapshot(taskName);
            if (lootTableArea != null && lootTableArea.isShowing()) refreshLootTable();

            int projH = (int) projectionSpinner.getValue();
            long totalXPGained = 0;
            int totalLevelsGained = 0;
            int p2pTotal = 0;
            int f2pTotal = 0;

            ///  Calculate total xp/levels
            for (SkillData data : skillRegistry.values()) {
                int xp = Skills.getExperience(data.getSkill());
                int lvl = Skills.getRealLevel(data.getSkill());
                data.update(xp, lvl, startTime, projH);
                p2pTotal += lvl;
                if (F2P_SKILLS.contains(data.getSkill()))
                    f2pTotal += lvl;

                if (data.isTracking()) {
                    totalXPGained += Math.max(0, (xp - data.getStartXP()));
                    totalLevelsGained += Math.max(0, (lvl - data.getStartLevel()));
                }
            }

            ///  Update the live side panel (v1.86)
            updateSidePanel(totalXPGained, totalLevelsGained, f2pTotal, p2pTotal);

            ///  Update Status tab
            boolean isMember = Client.isMembers();
            // only update if player is logged in or there wont be much to load!
            if (Client.isLoggedIn()) {
                // load world tab?
                lblUsername.setText(AccountManager.getAccountUsername());
                lblNickname.setText(AccountManager.getAccountNickname());
                lblAcctId.setText(Client.getAccountIdentifier());
                lblAcctStatus.setText(String.valueOf(Client.getAccountStatus()));

                lblCharName.setText(Players.getLocal().getName());
                lblMemberIcon.setIcon(loadStatusIcon(isMember ? "P2P_icon" : "F2P_icon"));
                lblMemberText.setText(isMember ? "Pay-to-play" : "Free-to-play");
                lblWorld.setText("World " + (Worlds.getCurrent() != null ? Worlds.getCurrent().getWorld() : "?"));
                lblCoords.setText(Players.getLocal().getTile().toString());
                lblGameState.setText(Client.getGameState().name());
            }

            // keep the queue progress bar + loop indicator live
            updateQueueProgress();

            // keep the play/pause button icon in sync with the actual paused state
            // (so pausing from the in-game overlay flips the Swing button too)
            if (btnPlayPause != null)
                btnPlayPause.setText(isMenuPaused() ? "\u25B6" : "\u23F8");

            // update keyboard/mouse inputs as mock listeners. This will keep UI in sync at least every 1 second
            setMouseInput(getMouseInput());
            setKeyboardInput(getKeyboardInput());
        });
    }

    /**
     * Refreshes the live side panel each UI tick (v1.86): flips the login/live card on the
     * actual login state, fills the player header (name; combat level computed from live
     * stats; the P2P total the loop just summed; quest points via varp 101), rebuilds the
     * session summary line, and keeps the starred F2P/P2P totals current. Fully guarded -
     * a mid-hop client read can never take the UI timer down.
     */
    /** Hides side-panel detail rows whose values are unknown (set by buildSidePlayerDetails). */
    private Runnable detailRowsSync;
    /** The account-type combo in the side details (auto-detect updates it quietly). */
    private JComboBox<main.tools.AccountTypes> sideAccountTypeCombo;
    private boolean suppressAccountTypeEvents;
    /** Login edge detection for the one-shot account-type auto-detect (v1.87). */
    private boolean sideWasLoggedIn;

    /**
     * v1.87: the PLAYER DETAILS block under the equipment column - the Player half of the old
     * Status tab, rehomed. Anything needing a logged-in character lives here on purpose: the
     * login is what makes these rows mean something. Collapsed by default; the header is the
     * toggle (plain words, no glyphs - the tag bar taught that lesson).
     */
    /**
     * v1.90: the button that opens the player details. It used to be a collapsible section
     * that pushed the whole side panel around every time you looked at it - and since these
     * are reference values you check occasionally rather than watch, a floating card is the
     * right shape. Same card proportions as the Account tab, so the two read as siblings.
     */
    private JComponent buildSidePlayerDetails() {
        JPanel section = new JPanel(new BorderLayout());
        section.setOpaque(false);
        section.setBorder(new EmptyBorder(10, 2, 4, 2));

        JButton open = createButton("Player details\u2026", new Color(40, 48, 62), null);
        open.setToolTipText("Membership, account type, world, DreamBot account and bank PIN "
                + "- everything about the character you're logged in as");
        open.addActionListener(e -> openPlayerDetailsDialog());
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, COLOR_BORDER_DIM),
                new EmptyBorder(8, 0, 2, 0)));
        row.add(open);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        section.add(row, BorderLayout.CENTER);
        return section;
    }

    /** The floating player-details card (v1.90). Built fresh each time so it's always current. */
    private void openPlayerDetailsDialog() {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        // ── membership + world (live labels updateUI already feeds) ──
        content.add(sideDetailRow("Membership", lblMemberText, lblMemberIcon));
        content.add(sideDetailRow("World", lblWorld, null));

        // ── account type: auto-detected on login when the client allows, manual always ──
        sideAccountTypeCombo = new JComboBox<>(main.tools.AccountTypes.values());
        sideAccountTypeCombo.setSelectedItem(main.tools.AccountTypes.current());
        sideAccountTypeCombo.setToolTipText("<html>Your account's game mode. v1.87 tries to "
                + "detect it automatically right after you log in;<br>picking one here always "
                + "wins. It tailors the Task Builder:<br>"
                + "\u2022 Ultimate Ironman - the Bank action is hidden; use the banker "
                + "Note-Exchange action instead<br>"
                + "\u2022 Group Ironman - unlocks the Group Storage action</html>");
        sideAccountTypeCombo.addActionListener(e -> {
            if (suppressAccountTypeEvents) return;
            main.tools.AccountTypes picked =
                    (main.tools.AccountTypes) sideAccountTypeCombo.getSelectedItem();
            if (picked != null && picked != main.tools.AccountTypes.current()) {
                main.tools.AccountTypes.set(picked);
                refreshTaskLibrary();   // re-gates the action picker's built-in list
                showToast("Account type: " + picked.label, sideAccountTypeCombo, true);
            }
        });
        JPanel typeRow = new JPanel(new BorderLayout(8, 0));
        typeRow.setOpaque(false);
        JLabel typeLbl = new JLabel("Account type");
        typeLbl.setForeground(TEXT_DIM);
        typeRow.add(typeLbl, BorderLayout.WEST);
        typeRow.add(sideAccountTypeCombo, BorderLayout.EAST);
        typeRow.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 40, 40)));
        typeRow.setAlignmentX(0f);
        typeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));   // v1.89
        content.add(typeRow);

        // ── the DreamBot account, shown only when the client actually tells us ──
        JComponent rowUser = sideDetailRow("DreamBot user", lblUsername, null);
        JComponent rowNick = sideDetailRow("Nickname", lblNickname, null);
        JComponent rowId = sideDetailRow("Identifier", lblAcctId, null);
        JComponent rowStatus = sideDetailRow("Account status", lblAcctStatus, null);
        content.add(rowUser);
        content.add(rowNick);
        content.add(rowId);
        content.add(rowStatus);
        detailRowsSync = () -> {
            // v1.87: no junk. A build that can't expose these reads leaves the rows hidden
            // instead of parading "..." placeholders.
            rowUser.setVisible(hasValue(lblUsername));
            rowNick.setVisible(hasValue(lblNickname));
            rowId.setVisible(hasValue(lblAcctId));
            rowStatus.setVisible(hasValue(lblAcctStatus));
        };
        detailRowsSync.run();

        // ── bank PIN: one Update input; still memory-only by design ──
        content.add(playerSectionHeader("Bank PIN"));
        content.add(sideDetailRow("Current", lblBankPin, null));
        JComponent pinRow = buildBankPinRow();
        pinRow.setAlignmentX(0f);
        content.add(pinRow);
        JLabel pinNote = new JLabel("<html><i>Held in memory and handed to the client when a "
                + "PIN screen appears - never saved to disk or sent anywhere.</i></html>");
        pinNote.setForeground(TEXT_DIM);
        pinNote.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        pinNote.setAlignmentX(0f);
        content.add(pinNote);

        // ── v1.89: account switching is NOT duplicated here ──────────────────────────
        // DreamBot's own Account Manager owns this and can't be removed from the client, so
        // mirroring it in DreamMan just meant two controls for one job - and it was the widest
        // block in the column, which is half of why the section grew so much. The client's
        // switcher is where switching lives; buildAccountSwitchRow() stays in the file for the
        // Account tab and anything that wants it later.

        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(PANEL_SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_BORDER_DIM),
                new EmptyBorder(16, 20, 16, 20)));
        card.setPreferredSize(new Dimension(420, 430));   // the Account tab's proportions

        JLabel title = new JLabel("Player details");
        title.setForeground(Theme.ACCENT);
        title.setFont(new Font("Consolas", Font.BOLD, 18));
        JLabel sub = new JLabel("<html><div style='width:360px'>Everything about the character "
                + "you're logged in as. These are read from the client, so they fill in once "
                + "you're in game.</div></html>");
        sub.setForeground(TEXT_DIM);
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JPanel head = new JPanel(new BorderLayout(0, 6));
        head.setOpaque(false);
        head.add(title, BorderLayout.NORTH);
        head.add(sub, BorderLayout.CENTER);
        card.add(head, BorderLayout.NORTH);

        JScrollPane sp = Theme.thinScrollbars(new JScrollPane(content));
        sp.setBorder(null);
        sp.getViewport().setOpaque(false);
        sp.setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(14);
        card.add(sp, BorderLayout.CENTER);

        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this),
                "Player details", Dialog.ModalityType.MODELESS);
        JButton close = createButton("Close");
        close.addActionListener(a -> dlg.dispose());
        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        foot.setOpaque(false);
        foot.add(close);
        card.add(foot, BorderLayout.SOUTH);

        dlg.setContentPane(card);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private static boolean hasValue(JLabel l) {
        String t = l.getText();
        return t != null && !t.isBlank() && !"...".equals(t) && !"\u2014".equals(t)
                && !"null".equalsIgnoreCase(t);
    }

    private JComponent sideDetailRow(String key, JLabel value, JLabel icon) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        JLabel k = new JLabel(key);
        k.setForeground(TEXT_DIM);
        row.add(k, BorderLayout.WEST);
        value.setForeground(TEXT_MAIN);
        value.setHorizontalAlignment(SwingConstants.RIGHT);
        if (icon != null) {
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            right.setOpaque(false);
            right.add(icon);
            right.add(value);
            row.add(right, BorderLayout.EAST);
        } else {
            row.add(value, BorderLayout.EAST);
        }
        row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 40, 40)));
        row.setAlignmentX(0f);
        // v1.89: same lesson as the PIN row - unbounded maximums are what let this column
        // balloon. A detail row is one line of text; it never needs more than this.
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        return row;
    }

    // Special attack energy lives in varp 300 (value/10 = percent). Same reflective pattern
    // as questPoints below, for the same reason - PlayerSettings has moved between builds.
    private static int specPercent() {
        try {
            if (qpGetConfig == null && !qpLookupFailed) {
                Class<?> ps = Class.forName("org.dreambot.api.methods.settings.PlayerSettings");
                qpGetConfig = ps.getMethod("getConfig", int.class);
            }
            if (qpGetConfig != null) {
                Object v = qpGetConfig.invoke(null, 300);
                if (v instanceof Number) return ((Number) v).intValue() / 10;
            }
        } catch (Throwable t) {
            qpLookupFailed = true;
        }
        return -1;
    }

    private void updateSidePanel(long totalXPGained, int totalLevelsGained,
                                 int f2pTotal, int p2pTotal) {
        boolean in = false;
        try { in = Client.isLoggedIn(); } catch (Throwable ignored) {}
        if (sideCards != null && sideCardHost != null)
            sideCards.show(sideCardHost, in ? "live" : "login");

        totalLevelLabelF2P.setText("Total: " + f2pTotal);
        totalLevelLabelF2P.setIcon(loadStatusIcon("F2P_icon"));
        totalLevelLabelF2P.setIconTextGap(8);
        totalLevelLabelP2P.setText("Total: " + p2pTotal);
        totalLevelLabelP2P.setIcon(loadStatusIcon("P2P_icon"));
        totalLevelLabelP2P.setIconTextGap(8);

        if (!in) { sideWasLoggedIn = false; return; }

        // v1.87: the moment a login lands, try the account-type auto-detect ONCE (varbit 1777,
        // reflective, silent on builds that can't) and refresh which detail rows have values.
        if (!sideWasLoggedIn) {
            sideWasLoggedIn = true;
            main.tools.AccountTypes found = main.tools.AccountTypes.applyDetected();
            if (found != null && sideAccountTypeCombo != null) {
                suppressAccountTypeEvents = true;
                sideAccountTypeCombo.setSelectedItem(found);
                suppressAccountTypeEvents = false;
                refreshTaskLibrary();
                showToast("Account type detected: " + found.label, sideAccountTypeCombo, true);
            }
        }
        if (detailRowsSync != null) detailRowsSync.run();

        try {
            org.dreambot.api.wrappers.interactive.Player me = Players.getLocal();
            String name = me == null ? null : me.getName();
            sidePlayerName.setText(name == null || name.isEmpty() ? "\u2014" : name);
        } catch (Throwable t) {
            sidePlayerName.setText("\u2014");
        }

        int qp = questPoints();
        sidePlayerStats.setText("Cbt " + combatLevel() + "  \u00b7  Total " + p2pTotal
                + "  \u00b7  QP " + (qp < 0 ? "\u2014" : qp));

        // the sleek one-liner that replaced "▲ xp gained / ▲ levels gained" (v1.86): what this
        // session's TRACKED skills earned, plus their combined pace (SkillData's own xp/h, so
        // Reset trackers restarts this line too)
        long xph = 0;
        boolean tracking = false;
        for (SkillData d : skillRegistry.values())
            if (d.isTracking()) {
                tracking = true;
                xph += Math.max(0, d.getXpPerHour());
            }
        sessionSummaryLabel.setText(!tracking
                ? "session: no skills tracked yet"
                : "+" + String.format("%,d", totalXPGained) + " xp  \u00b7  +" + totalLevelsGained
                        + " lvl  \u00b7  " + compact(xph) + " xp/h");
    }

    /** OSRS combat level from live stats; 0 when the client can't be read. */
    private static int combatLevel() {
        try {
            int att = Skills.getRealLevel(Skill.ATTACK), str = Skills.getRealLevel(Skill.STRENGTH);
            int def = Skills.getRealLevel(Skill.DEFENCE), hp = Skills.getRealLevel(Skill.HITPOINTS);
            int pray = Skills.getRealLevel(Skill.PRAYER), rng = Skills.getRealLevel(Skill.RANGED);
            int mag = Skills.getRealLevel(Skill.MAGIC);
            double base = 0.25 * (def + hp + Math.floor(pray / 2.0));
            double melee = 0.325 * (att + str);
            double range = 0.325 * Math.floor(rng * 1.5);
            double mage  = 0.325 * Math.floor(mag * 1.5);
            return (int) Math.floor(base + Math.max(melee, Math.max(range, mage)));
        } catch (Throwable t) {
            return 0;
        }
    }

    // Quest points live in varp 101. PlayerSettings is resolved reflectively because its
    // package has moved between client builds (same defensive pattern as BankPin, v1.44).
    private static java.lang.reflect.Method qpGetConfig;
    private static boolean qpLookupFailed;

    /** Quest points from varp 101, or -1 when the client offers no way to read them. */
    private static int questPoints() {
        try {
            if (qpGetConfig == null && !qpLookupFailed) {
                Class<?> ps = Class.forName("org.dreambot.api.methods.settings.PlayerSettings");
                qpGetConfig = ps.getMethod("getConfig", int.class);
            }
            if (qpGetConfig != null) {
                Object v = qpGetConfig.invoke(null, 101);
                if (v instanceof Number) return ((Number) v).intValue();
            }
        } catch (Throwable t) {
            qpLookupFailed = true;
        }
        return -1;
    }

    /** 1234 -> "1.2k", 1480000 -> "1.48m" - the side panel's xp/h formatter. */
    private static String compact(long n) {
        if (n >= 1_000_000) return String.format("%.2fm", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fk", n / 1_000.0);
        return String.valueOf(n);
    }

    private JPanel createInfoCard(String title) {
        JPanel p = new JPanel(new GridLayout(0, 1, 5, 10));
        p.setBackground(PANEL_SURFACE);
        TitledBorder b = BorderFactory.createTitledBorder(new LineBorder(COLOR_BORDER_DIM), " " + title + " ");
        b.setTitleColor(COLOR_BLOOD);
        b.setTitleFont(new Font("Segoe UI", Font.BOLD, 16));
        p.setBorder(BorderFactory.createCompoundBorder(b, new EmptyBorder(15, 15, 15, 15)));
        return p;
    }

    /** Row with an arbitrary component as the value (Patch B.15 - the character picker row). */
    private void addInfoRow(JPanel p, String key, JComponent valComp) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        JLabel k = new JLabel(key);
        k.setForeground(TEXT_DIM);
        k.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        row.add(k, BorderLayout.WEST);
        row.add(valComp, BorderLayout.CENTER);
        row.setBorder(new EmptyBorder(4, 0, 4, 0));
        p.add(row);
    }

    private void addInfoRow(JPanel p, String key, JLabel valLabel) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        JLabel k = new JLabel(key);
        k.setForeground(TEXT_DIM);
        valLabel.setForeground(TEXT_MAIN);
        valLabel.setFont(new Font("Consolas", Font.BOLD, 14));
        // v1.30: the value sits in CENTER, right-aligned - BorderLayout.EAST always got its
        // full preferred width, so long values ("only the live account visible") painted right
        // over the key label. In CENTER a too-long JLabel ellipsizes ("...") instead.
        valLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        row.add(k, BorderLayout.WEST); row.add(valLabel, BorderLayout.CENTER);
        row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 40, 40)));
        p.add(row);
    }

    private void addInfoRowWithIcon(JPanel p, String key, JLabel valLabel, JLabel iconLabel) {
        JPanel row = new JPanel(new BorderLayout(5, 0));
        row.setOpaque(false);
        JLabel k = new JLabel(key);
        k.setForeground(TEXT_DIM);
        JPanel rightSide = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        rightSide.setOpaque(false);
        valLabel.setForeground(TEXT_MAIN);
        rightSide.add(valLabel);
        rightSide.add(iconLabel);
        row.add(k, BorderLayout.WEST);
        row.add(rightSide, BorderLayout.EAST);
        row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 40, 40)));
        p.add(row);
    }

    ///  Define Client Settings sub-tab
    private JPanel createClientPanel() {
        return createSettingsGroup("Client",
                settingRow("Disable rendering",
                        "Stops the client drawing the game world - the biggest CPU and GPU "
                                + "saver there is for long sessions. The bot keeps playing; "
                                + "you just can't watch it.",
                        chkDisableRendering = createSettingCheck("Disable Rendering", Client.isRenderingDisabled(), e -> {
                            Client.setRenderingDisabled(((JCheckBox)e.getSource()).isSelected());
                        }))
        );
    }

    private JPanel createScriptPanel() {
        return createSettingsGroup("Script",
                settingRow("Start script on load",
                        "Begins running your queue the moment the menu finishes loading - no "
                                + "Play press needed. (A DreamMan setting; nothing in the game "
                                + "changes when you flip it.)",
                        settingClientChkStartScriptOnLoad = createSettingCheck("Start Script on Load",
                                startScriptOnLoad, e ->
                                        // fixed: was inverted ("= !isSelected()"), so ticking DISABLED the feature
                                        startScriptOnLoad = settingClientChkStartScriptOnLoad.isSelected()
                        )),

                settingRow("Warn before exit",
                        "Asks for confirmation before exiting, so one stray click can't end a "
                                + "long run unnoticed. (Also a DreamMan setting.)",
                        settingClientChkExitOnStopWarning = createSettingCheck("Warn Before Exit",
                                exitOnStopWarning, e ->
                                        // fixed: was inverted; label renamed to match what it actually does
                                        exitOnStopWarning = settingClientChkExitOnStopWarning.isSelected()
                        ))
                // v1.62: the "Auto Save" toggle is gone - autosave is always on now (the manual
                // Save buttons were removed), so there's no opt-out to expose. Changes persist via
                // requestAutosave() on edit plus a 60s backstop and a save on exit.
        );
    }

    ///  Define Display Settings sub-tab
    private JPanel createDisplayPanel() {
        return createSettingsGroup("Display",
                settingRow("Hide roofs",
                        "Removes building roofs so indoor tiles stay visible - the classic "
                                + "quality-of-life toggle, and it keeps scripts' clicks honest "
                                + "inside buildings.",
                        chkHideRoofs = createSettingCheck("Hide Roofs",
                                ClientSettings.areRoofsHidden(), e ->
                                        ClientSettings.toggleRoofs(((JCheckBox)e.getSource()).isSelected()))),

                settingRow("Transparent side panel",
                        "Makes the game's side panel see-through in resizable mode, so more of "
                                + "the world shows behind your inventory and tabs.",
                        chkTransparentSidePanel = createSettingCheck("Transparent side panel",
                                ClientSettings.isTransparentSidePanelEnabled(), e ->
                                        ClientSettings.toggleTransparentSidePanel(((JCheckBox)e.getSource()).isSelected())))
        );
    }

    ///  Define Gameplay Settings sub-tab
    private JPanel createGameplayPanel() {
        return createSettingsGroup("Gameplay",
                settingRow("Show data orbs",
                        "The in-game HP, prayer, run and special-attack orbs around the "
                                + "minimap. (DreamMan's side panel draws its own copy of these "
                                + "either way, so turning the game's off costs you nothing here.)",
                        chkDataOrbs = createSettingCheck("Show data orbs",
                                ClientSettings.areDataOrbsEnabled(), e ->
                                        ClientSettings.toggleDataOrbs(((JCheckBox)e.getSource()).isSelected())
                        )),

                settingRow("Ammo auto-equip",
                        "Picked-up ammo that matches what you're wielding goes straight back "
                                + "into the equipped stack instead of your inventory.",
                        chkAmmoPickingBehaviour = createSettingCheck("Ammo-picking behaviour",
                                ClientSettings.isAmmoAutoEquipping(), e ->
                                        ClientSettings.toggleAmmoAutoEquipping(((JCheckBox)e.getSource()).isSelected())
                        ))
        );
    }

    // (The old "Interfaces" sub-tab was removed - it only contained a duplicate of the
    //  "Show data orbs" checkbox, and its second assignment to chkDataOrbs corrupted the
    //  Gameplay tab's save/load behaviour.)

    ///  Define Audio Settings sub-tab
    private JPanel createAudioPanel() {
        return createSettingsGroup("Audio",
                settingRow("Game audio",
                        "All game sound. Off is the usual botting default - quieter for you, "
                                + "lighter on the machine.",
                        chkGameAudio = createSettingCheck("Game Audio", ClientSettings.isGameAudioOn(),
                                e -> ClientSettings.toggleGameAudio(((JCheckBox)e.getSource()).isSelected())))
        );
    }

    ///  Define Chat Settings sub-tab
    private JPanel createChatPanel() {
        return createSettingsGroup("Chat",
                settingRow("Transparent chatbox",
                        "See-through chatbox in resizable mode - more of the world shows "
                                + "behind your messages. (The Logs tab captures every channel "
                                + "regardless of how the game's box looks.)",
                        chkTransparentChatbox = createSettingCheck("Transparent chatbox",
                                ClientSettings.isTransparentChatboxEnabled(), e ->
                                        ClientSettings.toggleTransparentChatbox(((JCheckBox)e.getSource()).isSelected()))),

                settingRow("Click through chatbox",
                        "With the transparent chatbox on, clicks pass through it to the world "
                                + "behind - walk where the box is instead of poking it.",
                        chkClickThroughChatbox = createSettingCheck("Click through chatbox",
                                ClientSettings.isClickThroughChatboxEnabled(), e ->
                                        ClientSettings.toggleClickThroughChatbox(((JCheckBox)e.getSource()).isSelected())))
        );
    }

    ///  Define Controls Settings sub-tab
    private JPanel createControlsPanel() {
        return createSettingsGroup("Controls",
                settingRow("Shift-click drop",
                        "Hold Shift to left-click-drop items - the setting every powermining "
                                + "and drop-heavy script assumes is on.",
                        chkShiftClickDrop = createSettingCheck("Shift click drop",
                                ClientSettings.isShiftClickDroppingEnabled(), e ->
                                        ClientSettings.toggleShiftClickDropping(((JCheckBox)e.getSource()).isSelected()))),

                settingRow("Esc closes interface",
                        "The Escape key closes whatever interface is open - the bank, shops, "
                                + "the lot. Scripts close things faster with it on.",
                        chkEscClosesInterface = createSettingCheck("Esc closes interface",
                                ClientSettings.isEscInterfaceClosingEnabled(), e ->
                                        ClientSettings.toggleEscInterfaceClosing(((JCheckBox)e.getSource()).isSelected())))
        );
    }

    ///  Define Activities Settings sub-tab
    private JPanel createActivitiesPanel() {
        return createSettingsGroup("Activities",
                settingRow("Level-up interface",
                        "The congratulations popup when a skill levels. Off means one less "
                                + "interruption for a script to click through mid-run.",
                        chkLevelUpInterface = createSettingCheck("Level-up interface",
                                ClientSettings.isLevelUpInterfaceEnabled(), e ->
                                        ClientSettings.toggleLevelUpInterface(((JCheckBox)e.getSource()).isSelected())))
        );
    }

    ///  Define Warnings Settings sub-tab
    private JPanel createWarningsPanel() {
        return createSettingsGroup(
                "Warnings",
                settingRow("Loot notifications",
                        "Highlights valuable drops with a chat notification, so the good ones "
                                + "don't vanish into the kill-loop unnoticed.",
                        chkLootNotifications = createSettingCheck("Loot notifications",
                                ClientSettings.areLootNotificationsEnabled(), e ->
                                        ClientSettings.toggleLootNotifications(((JCheckBox)e.getSource()).isSelected())))
        );
    }

    /**
     * DreamBot CLIENT settings (Patch B.3) - linked the same way as the in-game client
     * settings on the left, but resolved through REFLECTION: this patch round couldn't reach
     * the live javadocs, and a guessed method name must never break the build. Whichever
     * hooks this client version exposes appear as live toggles; the rest simply don't render,
     * and a note explains when none resolve.
     */
    private JPanel createDreamBotClientPanel() {
        List<Component> rows = new ArrayList<>();

        addReflectiveToggle(rows, "FPS unlock / render toggles",
                "org.dreambot.api.Client",
                new String[]{"isDrawingEnabled", "isRenderingEnabled"},
                new String[]{"setDrawingEnabled", "setRenderingEnabled"});
        addReflectiveToggle(rows, "Low CPU mode",
                "org.dreambot.api.Client",
                new String[]{"isLowCpuModeEnabled", "isCPUSaverEnabled", "isLowCpuMode"},
                new String[]{"setLowCpuMode", "setCPUSaver", "setLowCpuModeEnabled"});
        addReflectiveToggle(rows, "Fresh start (new game instance)",
                "org.dreambot.api.Client",
                new String[]{"isFreshStartEnabled"},
                new String[]{"setFreshStart", "setFreshStartEnabled"});

        if (rows.isEmpty()) {
            JLabel none = new JLabel("<html>This DreamBot build doesn't expose client settings"
                    + " through its public API.<br>The in-game client settings on the left are"
                    + " fully linked; DreamBot-level hooks will light up here automatically on"
                    + " a client version that publishes them.</html>");
            none.setForeground(TEXT_DIM);
            rows.add(none);
        }
        return createSettingsGroup("DreamBot", rows.toArray(new Component[0]));
    }

    /** Adds a working toggle only when both a getter and setter resolve reflectively. */
    private void addReflectiveToggle(List<Component> rows, String label,
                                     String className, String[] getters, String[] setters) {
        try {
            Class<?> cls = Class.forName(className);
            java.lang.reflect.Method getter = null, setter = null;
            for (String g : getters) {
                try { getter = cls.getMethod(g); break; } catch (NoSuchMethodException ignored) {}
            }
            for (String st : setters) {
                try { setter = cls.getMethod(st, boolean.class); break; } catch (NoSuchMethodException ignored) {}
            }
            if (getter == null || setter == null) return;

            boolean initial = false;
            try { initial = Boolean.TRUE.equals(getter.invoke(null)); } catch (Throwable ignored) {}
            final java.lang.reflect.Method set = setter;
            rows.add(createSettingCheck(label, initial, e -> {
                try { set.invoke(null, ((JCheckBox) e.getSource()).isSelected()); }
                catch (Throwable t) {
                    Logger.log(Logger.LogType.WARN, "[Settings] " + label + " failed: " + t);
                }
            }));
        } catch (Throwable ignored) {
            // class absent in this client version - the row simply doesn't render
        }
    }

    /**
     * v1.87: one settings group, restyled. The old version was a 24px header over bare
     * checkboxes floating in a FlowLayout - "spaced out and plain and not well explained" was
     * a fair review. Now it's a dense column of {@link #settingRow} cards (name, what it does,
     * the switch), clamped to a readable width and scrolling when a group outgrows the tab.
     * The group ORDER across the nav is untouched - it still mirrors the in-game settings
     * menu top to bottom, so the two screens navigate the same.
     */
    private JPanel createSettingsGroup(String title, Component... comps) {
        JLabel header = new JLabel(title);
        header.setForeground(COLOR_BLOOD);
        header.setFont(new Font("Segoe UI", Font.BOLD, 16));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_BORDER_DIM),
                new EmptyBorder(0, 0, 6, 0)));
        header.setAlignmentX(0f);
        header.setMaximumSize(new Dimension(560, Short.MAX_VALUE));

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.add(header);
        column.add(Box.createVerticalStrut(4));
        for (Component c : comps) {
            if (c instanceof JComponent) ((JComponent) c).setAlignmentX(0f);
            column.add(c);
        }

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(BG_BASE);
        wrap.setBorder(new EmptyBorder(4, 4, 4, 4));
        wrap.add(column, BorderLayout.NORTH);
        JScrollPane scroll = Theme.thinScrollbars(new JScrollPane(wrap));
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG_BASE);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        JPanel out = new JPanel(new BorderLayout());
        out.setBackground(BG_BASE);
        out.add(scroll, BorderLayout.CENTER);
        return out;
    }

    /**
     * v1.87: one setting as a proper row - bold name, a plain-words line on what it actually
     * does, the switch on the right. The checkbox keeps ALL its live-toggle machinery from
     * {@link #createSettingCheck} (the busy-bot semaphore, the toasts); only its label text
     * moves into the row title so nothing reads twice.
     */
    private JComponent settingRow(String title, String description, JCheckBox check) {
        JLabel t = new JLabel(title);
        t.setForeground(TEXT_MAIN);
        t.setFont(new Font("Segoe UI", Font.BOLD, 13));
        JLabel d = new JLabel("<html><div style='width:420px'>" + description + "</div></html>");
        d.setForeground(TEXT_DIM);
        d.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        t.setAlignmentX(0f);
        d.setAlignmentX(0f);
        text.add(t);
        text.add(Box.createVerticalStrut(2));
        text.add(d);

        check.setText("");   // the row names it; the box just flips it (toasts keep the name)
        JPanel east = new JPanel(new GridBagLayout());   // vertically centres the switch
        east.setOpaque(false);
        east.add(check);

        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.add(text, BorderLayout.CENTER);
        row.add(east, BorderLayout.EAST);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 40, 40)),
                new EmptyBorder(10, 0, 10, 4)));
        row.setMaximumSize(new Dimension(560, Short.MAX_VALUE));
        return row;
    }

    private JCheckBox createSettingCheck(String text, boolean initialState, ActionListener l) {
        JCheckBox c = new JCheckBox(text);
        c.setForeground(TEXT_MAIN);
        c.setOpaque(false);
        c.setSelected(initialState);

        c.addItemListener(e -> {
            if (isReverting) return;

            if (isSettingProcessing) {
                isReverting = true;
                c.setSelected(e.getStateChange() != ItemEvent.SELECTED);
                isReverting = false;
                showToast("Bot is busy ingame...", c, false);
                return;
            }

            isSettingProcessing = true;
            String originalStatus = lblStatus.getText();
            setStatus("Adjusting " + text + " ingame...");

            new Thread(() -> {
                try {
                    // Execute the actual DreamBot ClientSettings toggle
                    if (l != null) {
                        l.actionPerformed(new ActionEvent(c, ActionEvent.ACTION_PERFORMED, null));
                    }
                } catch (Exception ex) {
                    Logger.log("Error updating setting: " + ex.getMessage());
                } finally {
                    // Unlock the semaphore so the user can click other things
                    isSettingProcessing = false;

                    // Substance UI FIX: Move all UI feedback to the EDT
                    SwingUtilities.invokeLater(() -> {
                        setStatus(originalStatus);
                        showToast(text + " updated!", c, true);
                    });
                }
            }).start();
        });

        return c;
    }

    private JToggleButton createMenuButton(String text) { JToggleButton btn = new JToggleButton(text) { protected void paintComponent(Graphics g) { g.setColor(isSelected() ? TAB_SELECTED : PANEL_SURFACE); g.fillRect(0, 0, getWidth(), getHeight()); super.paintComponent(g); } }; btn.setFocusPainted(false); btn.setContentAreaFilled(false); btn.setForeground(TEXT_MAIN); btn.setFont(new Font("Segoe UI", Font.BOLD, 14)); btn.setHorizontalAlignment(SwingConstants.LEFT); btn.setBorder(new EmptyBorder(0, 20, 0, 0)); return btn; }

    /**
     * v1.89: every skill tile, by skill, so their borders can be re-synced to the tracking data.
     *
     * <p>THE BUG this fixes: restoring a profile called {@code setTracking(...)} on the DATA, and
     * the overlays duly appeared - but a tile's border is only ever painted at creation (dim) or
     * inside its own click handler. So after a reload every tile LOOKED untracked while actually
     * being tracked. Clicking one to "highlight" it therefore toggled tracking OFF, the overlay
     * vanished, and the side panel's session line fell back to "no skills tracked yet" - which is
     * why the XP counters looked like they'd stopped. One missing repaint, two reported bugs.
     */
    private final Map<Skill, JPanel> skillTiles = new EnumMap<>(Skill.class);

    /** Repaints every tile's border from its live tracking state. Safe to call any time. */
    private void syncSkillTileBorders() {
        for (Map.Entry<Skill, JPanel> e : skillTiles.entrySet()) {
            SkillData d = skillRegistry.get(e.getKey());
            if (d == null || e.getValue() == null) continue;
            e.getValue().setBorder(new LineBorder(
                    d.isTracking() ? COLOR_BLOOD : COLOR_BORDER_DIM, 1));
        }
    }

    private JPanel createSkillTile(SkillData data) {
        JPanel tile = new JPanel(new GridBagLayout());
        tile.setBackground(PANEL_SURFACE);
        // v1.89: start from the DATA, not always-dim - a tile built after a profile restore
        // (or after Clear trackers) now shows the truth immediately.
        tile.setBorder(new LineBorder(
                data.isTracking() ? COLOR_BLOOD : COLOR_BORDER_DIM, 1));
        skillTiles.put(data.getSkill(), tile);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0; gbc.gridx = 0;
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        ImageIcon skillIcon = loadSkillIcon(data.getSkill());
        data.setIcon(skillIcon);   // Patch B.5: the overlay draws the same icon
        JLabel icon = new JLabel(skillIcon);
        data.getLabelLevel().setForeground(COLOR_BLOOD);
        data.getLabelLevel().setFont(new Font("Arial", Font.BOLD, 18));
        top.add(icon, BorderLayout.WEST);
        top.add(data.getLabelLevel(), BorderLayout.EAST);
        data.getLabelXP().setForeground(TEXT_DIM);
        data.getLabelXP().setFont(new Font("Monospaced", Font.PLAIN, 10));
        data.getTrackerPanel().setForeground(COLOR_BLOOD);
        data.getTrackerPanel().setBackground(Color.BLACK);
        gbc.gridy = 0; tile.add(top, gbc); gbc.gridy = 1;
        tile.add(data.getLabelXP(), gbc);
        // Patch B.2 FIX: the detail tracker panel is NOT added to the tile any more. A Swing
        // component can only have ONE parent - refreshTrackerList() moved this exact panel to
        // the side list on the first click, ripping it out of the tile mid-layout. That was
        // the "skill tracker bugs out when I click to track" report. The tile keeps its icon,
        // level and XP line; the detail block lives in the side tracker list (and the tracked
        // skill now also gets an on-screen overlay card).

        tile.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                // Patch B.3: right-click sets an XP goal for this skill - enter a target level
                // (2-126, converted through the client's own XP table) or an exact XP amount;
                // 0 clears. Shown as a progress bar in the tracker and the on-screen card.
                if (SwingUtilities.isRightMouseButton(e)) {
                    String current = data.getGoalXp() > 0 ? String.valueOf(data.getGoalXp()) : "";
                    String in = JOptionPane.showInputDialog(tile,
                            "Goal for " + data.getSkill().name()
                                    + "\nEnter a target level (2-126) or exact XP. 0 clears.",
                            current);
                    if (in == null) return;
                    try {
                        long v = Long.parseLong(in.trim().replace(",", ""));
                        int goal;
                        if (v <= 0) goal = 0;
                        else if (v <= 126) goal = Skills.getExperienceForLevel((int) v);
                        else goal = (int) Math.min(v, 200_000_000L);
                        data.setGoalXp(goal);
                        showToast(goal > 0
                                ? "Goal set: " + String.format("%,d", goal) + " XP"
                                : "Goal cleared", tile, true);
                    } catch (NumberFormatException bad) {
                        showToast("Enter a number (level or XP)", tile, false);
                    }
                    return;
                }
                if (e.getButton() == MouseEvent.BUTTON1) {
                    // toggles skill tracking on/off on single mouse click
                    data.toggleTracking();
                    tile.setBorder(new LineBorder(data.isTracking() ? COLOR_BLOOD : COLOR_BORDER_DIM, 1));
                    refreshTrackerList();
                }
            }
        });

        return tile;
    }

    private void refreshTrackerList() {
        trackerList.removeAll();
        skillRegistry.values().stream().filter(SkillData::isTracking).forEach(d -> {
            trackerList.add(d.getTrackerPanel());
            trackerList.add(Box.createRigidArea(new Dimension(0, 10)));
        }); trackerList.add(Box.createVerticalGlue()); trackerList.revalidate(); trackerList.repaint(); }

    public void updateAll() {
        // block other updates while updating
        if (isDataLoading)
            return;

        isDataLoading = true;
        new Thread(() -> {
            try {
                // if the player is not yet logged in
                if (!Client.isLoggedIn()) {
                    // Patch B.1: don't sit empty at the login screen - load the shared "default"
                    // profile once, immediately, so the queue/library are usable while logged
                    // out. When the player logs in, their per-character profile loads (below).
                    if (!defaultProfileLoaded) {
                        defaultProfileLoaded = true;
                        loadProfileFromDisk();
                    }
                    // Retry again shortly. The flag is released in 'finally' so the retry actually
                    // runs - previously it stayed true forever after this early return, and data
                    // never loaded if the menu opened before login.
                    Timer t = new Timer(Rand.nextInt(523, 2303), e -> updateAll());
                    // ensure only one of these timers exist at a 'time', hehe, get it?
                    t.setRepeats(false);
                    t.start();
                    Logger.log(Logger.LogType.DEBUG, "Waiting for player to login...");
                    setStatus("Awaiting login...");
                    return;
                }

                Logger.log("Player logged in. Loading local profile...");
                // Read the whole per-character profile from disk in ONE go (small JSON file),
                // then apply it on the EDT. Replaces the old four-column Supabase fetch.
                loadProfileFromDisk();
            } finally {
                isDataLoading = false;
            }
        }).start();
    }

    /**
     * Captures the current state of all presets to be saved into the dedicated presets column.
     * @return A deep-copy list of all current Preset objects.
     */
    private List<Preset> capturePresets() {
        List<Preset> snapshot = new ArrayList<>();
        for (Object obj : modelPresets.toArray()) {
            Preset p = (Preset) obj;
            snapshot.add(new Preset(p.name, new ArrayList<>(p.tasks)));
        }
        return snapshot;
    }

    public void saveAll() {
        saveAll(true);
    }

    /** v1.62: coalesces bursts of edits into a single write ~1.5s after the last change. */
    private Timer autosaveDebounce;
    /** v1.62: false until construction finishes, so model wiring during startup doesn't save. */
    private volatile boolean autosaveReady = false;

    /**
     * v1.62: the on-change autosave. The manual Save buttons are gone, so every meaningful change
     * (queue add/remove/reorder, library add/remove/edit) calls this. It debounces - a rapid burst
     * of changes results in one save shortly after the last one - and routes through saveAll(false),
     * which writes via LocalStore under {@code <scripts.path>/DreamMan} (SDN-compliant) and is
     * guarded so an empty workspace never overwrites a non-empty saved profile.
     */
    public void requestAutosave() {
        if (!autosaveReady) return;                 // ignore churn during construction/load wiring
        if (autosaveDebounce == null) {
            autosaveDebounce = new Timer(1500, e -> saveAll(false));
            autosaveDebounce.setRepeats(false);
        }
        autosaveDebounce.restart();                 // fire ~1.5s after the LAST change
    }

    /**
     * HONESTY FIX: this method used to spin up a thread, report success and save nothing
     * (the real save call was commented out and the Supabase backend is gone). Local JSON
     * persistence lands in Patch 2 and plugs in right here; until then the UI tells the truth.
     *
     * @param verbose True to surface the result to the user (manual saves); false for the
     *                background auto-save timer, which would otherwise spam the status bar.
     */
    public void saveAll(boolean verbose) {
        // Snapshot everything on the EDT (models/inputs must be read on the EDT), then write
        // to disk off the EDT so a slow disk never freezes the UI. Patch B.1: saves work logged
        // out too (shared "default" profile) - "log in first" is gone.
        final ProfileData data = captureProfile();
        final String key = profileKey();

        if (verbose)
            setStatus("Saving...");

        new Thread(() -> {
            // Patch B.1 data-loss guard: a SILENT save (auto-save timer) never overwrites a
            // non-empty saved profile with an empty workspace - e.g. right after a failed load.
            // Manual saves still can (clearing on purpose is legitimate), and LocalStore now
            // writes profile.json.bak before every overwrite, so even that is recoverable.
            if (!verbose && isEmptyProfile(data)) {
                ProfileData onDisk = LocalStore.load(key);
                if (onDisk != null && !isEmptyProfile(onDisk)) {
                    Logger.log(Logger.LogType.INFO,
                            "[AutoSave] Workspace is empty but the saved profile isn't - skipping.");
                    return;
                }
            }
            boolean ok = LocalStore.save(key, data);
            if (verbose)
                setStatus(ok ? "Saved!" : "Save failed - see client logs");
        }, "DreamMan-Save").start();
    }

    /** True when a snapshot holds nothing worth keeping (no tasks, library, presets or draft actions). */
    private static boolean isEmptyProfile(ProfileData d) {
        if (d == null) return true;
        boolean noTasks   = d.taskList == null || d.taskList.isEmpty();
        boolean noLibrary = d.library  == null || d.library.isEmpty();
        boolean noPresets = d.presets  == null || d.presets.isEmpty();
        boolean noDraft   = d.builder  == null || d.builder.actions == null || d.builder.actions.isEmpty();
        return noTasks && noLibrary && noPresets && noDraft;
    }

    // ─── Patch B.3: developer lockdown ───
    // Your OSRS character name(s). Case-insensitive. EDIT THESE to your accounts.
    private static final Set<String> DEV_ACCOUNTS = new HashSet<>(
            Arrays.asList("iamawake247"));   // lowercase; compared case-insensitively
    // Secret unlock token: put exactly this text inside <home>/DreamMan/dev.flag to unlock the
    // console on any account (handy on a fresh/alt login). CHANGE THIS to your own secret.
    private static final String DEV_TOKEN = "dm-dev-8f3a91c2";

    /** True only for you: recognised account name, or the exact secret token in dev.flag. */
    /**
     * v1.32: a crisp drawn +/- glyph for the per-row loop steppers. Replaces text labels, whose
     * "+" rendered as an ellipsis in the client font. {@code plus} true = "+", false = "-".
     */
    private static Icon plusMinusIcon(boolean plus) {
        final int s = 11;
        return new Icon() {
            public int getIconWidth() { return s; }
            public int getIconHeight() { return s; }
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0xC9, 0xCD, 0xD4));
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int mid = s / 2;
                g2.drawLine(x + 1, y + mid, x + s - 1, y + mid);            // horizontal bar
                if (plus) g2.drawLine(x + mid, y + 1, x + mid, y + s - 1);  // vertical bar
                g2.dispose();
            }
        };
    }

    /** v1.32: select a main tab by its title (robust to tab order changes). */
    private void selectMainTab(String title) {
        if (mainTabs == null || title == null) return;
        for (int i = 0; i < mainTabs.getTabCount(); i++)
            if (title.equals(mainTabs.getTitleAt(i))) { mainTabs.setSelectedIndex(i); return; }
    }

    private boolean isDeveloper() {
        String character = safePlayerName();
        if (character != null && DEV_ACCOUNTS.contains(character.trim().toLowerCase()))
            return true;
        try {
            java.io.File flag = new java.io.File(LocalStore.getRoot(), "dev.flag");
            if (flag.isFile()) {
                String body = new String(java.nio.file.Files.readAllBytes(flag.toPath()),
                        java.nio.charset.StandardCharsets.UTF_8).trim();
                if (DEV_TOKEN.equals(body)) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * The key this session's profile is stored under: the character's name when logged in,
     * otherwise the shared {@code "default"} profile (Patch B.1). Work done at the login screen
     * is no longer lost - it saves to and loads from the default profile, and the first save
     * after logging in carries it forward under the character's own name.
     */
    private String profileKey() {
        String character = safePlayerName();
        return character != null ? character : "default";
    }

    /** Player name, or null if not logged in (never throws, unlike getPlayerName()). */
    private String safePlayerName() {
        if (!Client.isLoggedIn())
            return null;
        var local = Players.getLocal();
        String name = local != null ? local.getName() : null;
        return (name == null || name.isEmpty()) ? null : name;
    }

    /** Builds a full {@link ProfileData} snapshot from the current UI state (call on the EDT). */
    private ProfileData captureProfile() {
        ProfileData data = new ProfileData();
        data.settings = captureSettingsSnapshot();
        data.queueLoops = queueLoopTarget;
        data.queueAutoWait = queueAutoWait;
        // Patch B.6: remember-trackers preference + the tracked set
        data.rememberTrackers = rememberTrackers;
        if (rememberTrackers) {
            data.trackedSkills = new ArrayList<>();
            for (SkillData sd : skillRegistry.values())
                if (sd.isTracking())
                    data.trackedSkills.add(sd.getSkill().name());
        }
        data.skillGoals = new HashMap<>();
        for (SkillData sd : skillRegistry.values())
            if (sd.getGoalXp() > 0)
                data.skillGoals.put(sd.getSkill().name(), sd.getGoalXp());
        data.globalTriggers = main.watchers.TriggerCodec.toJson(
                new ArrayList<>(globalTriggers));
        // v1.64: the current queue's script triggers travel with the draft
        data.scriptTriggers = main.watchers.TriggerCodec.toJson(
                new ArrayList<>(scriptTriggers));
        data.queueAutoWaitMinMs = queueAutoWaitMinMs;
        data.queueAutoWaitMaxMs = queueAutoWaitMaxMs;
        data.taskList = ProfileCodec.tasksToData(modelTaskList);
        // Patch B.5: capture from the MASTER list - an active search/filter must never cause
        // hidden tasks to be dropped from the save.
        data.library = ProfileCodec.tasksToData(libraryAll);
        data.presets = ProfileCodec.presetsToData(modelPresets);

        BuilderData bd = new BuilderData();
        if (taskBuilder != null) {
            bd.taskName = taskBuilder.getDraftName();
            bd.taskDescription = taskBuilder.getDraftDescription();
            bd.taskStatus = taskBuilder.getDraftStatus();
            bd.autoDelay = taskBuilder.getDraftAutoDelay();
            bd.autoDelayMinMs = taskBuilder.getDraftAutoDelayMin();
            bd.autoDelayMaxMs = taskBuilder.getDraftAutoDelayMax();
        }
        bd.actions = new ArrayList<>();
        for (int i = 0; i < modelTaskBuilder.size(); i++) {
            Action a = modelTaskBuilder.get(i);
            if (a != null)
                bd.actions.add(ProfileCodec.toData(a));
        }
        data.builder = bd;
        return data;
    }

    /**
     * Helper function to track and highlight the tasks as they are executed in the task list.
     */
    /** A themed section header row: bold title on the left, optional components on the right. */
    private JPanel createTabHeader(String title, JComponent right) {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(2, 4, 12, 4));

        JLabel lbl = new JLabel(title);
        lbl.setFont(Theme.fontBold(16));
        lbl.setForeground(Theme.TEXT);
        // subtle crimson tick to the left of the title
        lbl.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, Theme.ACCENT),
                new EmptyBorder(0, 10, 0, 0)));
        header.add(lbl, BorderLayout.WEST);
        if (right != null) header.add(right, BorderLayout.EAST);
        return header;
    }

    /** A small rounded pill label (e.g. "3 tasks"). */
    private JLabel pill(String text) {
        JLabel l = new JLabel(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.SURFACE_2);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                g2.setColor(Theme.BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        l.setForeground(Theme.TEXT_DIM);
        l.setFont(Theme.font(11));
        l.setBorder(new EmptyBorder(3, 11, 3, 11));
        return l;
    }

    /** A small rounded colour chip with a single letter, used as each task card's icon. */
    private static class RoundChip extends JComponent {
        Color color = Theme.BLUE; String letter = "?";
        RoundChip() { setPreferredSize(new Dimension(32, 32)); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillRoundRect(0, (getHeight()-30)/2, 30, 30, 9, 9);
            g2.setColor(Color.WHITE);
            g2.setFont(Theme.fontBold(14));
            FontMetrics fm = g2.getFontMetrics();
            int tx = (30 - fm.stringWidth(letter)) / 2;
            int ty = (getHeight()/2) + (fm.getAscent() - fm.getDescent())/2;
            g2.drawString(letter, tx, ty);
            g2.dispose();
        }
    }

    /** Colour + letter for a task's first action, so the chip reflects what the task does. */
    private static Color chipColorFor(String actionName) {
        if (actionName == null) return Theme.TEXT_DIM;
        switch (actionName) {
            case "Walk":     return Theme.BLUE;
            case "Interact": return Theme.ACCENT;
            case "Loot":     return Theme.AMBER;
            case "Drop":     return Theme.GREEN;
            case "Bank":     return new Color(0x5DA0E8);
            case "Wait":     return Theme.TEXT_MUTED;
            default:          return Theme.TEXT_DIM;
        }
    }

    /** Card-style renderer for the task queue (Patch A). */
    private class TaskCardRenderer extends JPanel implements ListCellRenderer<Task> {
        private final RoundChip chip = new RoundChip();
        private final JLabel name = new JLabel();
        private final JLabel preview = new JLabel();
        private final JLabel badge = new JLabel("", SwingConstants.CENTER);
        /** v1.31: tiny -/+ loop steppers beside the xN badge (hit-tested from the list). */
        final JLabel repMinus = new JLabel("", SwingConstants.CENTER);
        final JLabel repPlus = new JLabel("", SwingConstants.CENTER);
        private boolean running, selected, hovered;

        TaskCardRenderer() {
            setLayout(new BorderLayout(12, 0));
            setBorder(BorderFactory.createEmptyBorder(11, 15, 13, 15));
            setOpaque(false);

            JPanel chipWrap = new JPanel(new GridBagLayout());
            chipWrap.setOpaque(false);
            chipWrap.add(chip);

            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            name.setFont(Theme.fontBold(13));
            name.setAlignmentX(LEFT_ALIGNMENT);
            preview.setFont(Theme.font(11));
            preview.setForeground(Theme.TEXT_MUTED);
            preview.setAlignmentX(LEFT_ALIGNMENT);
            text.add(name);
            text.add(Box.createVerticalStrut(3));
            text.add(preview);

            JPanel textWrap = new JPanel(new GridBagLayout());
            textWrap.setOpaque(false);
            GridBagConstraints gc = new GridBagConstraints();
            gc.anchor = GridBagConstraints.WEST; gc.weightx = 1; gc.fill = GridBagConstraints.HORIZONTAL;
            textWrap.add(text, gc);

            badge.setFont(Theme.fontBold(11));
            badge.setForeground(new Color(0xC9CDD4));
            badge.setBorder(BorderFactory.createEmptyBorder(3, 9, 3, 9));

            // v1.31: the loop count gets its own -/+ right on the row - no dialog needed.
            // v1.32: drawn as VECTOR icons, not text - the client font renders "+" as an
            // ellipsis, so a literal "+"/"-" label was unreliable. These always look right.
            repMinus.setText("");
            repPlus.setText("");
            repMinus.setIcon(plusMinusIcon(false));
            repPlus.setIcon(plusMinusIcon(true));
            for (JLabel step : new JLabel[]{repMinus, repPlus}) {
                step.setOpaque(true);
                step.setBackground(new Color(0x2D, 0x2D, 0x2D));
                step.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(0x46, 0x46, 0x46)),
                        BorderFactory.createEmptyBorder(2, 6, 2, 6)));
            }
            repMinus.setToolTipText("One loop fewer for this task");
            repPlus.setToolTipText("One loop more for this task");
            JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
            east.setOpaque(false);
            east.add(repMinus);
            east.add(badge);
            east.add(repPlus);

            add(chipWrap, BorderLayout.WEST);
            add(textWrap, BorderLayout.CENTER);
            add(east, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Task> list, Task task,
                int index, boolean isSelected, boolean cellHasFocus) {
            selected = isSelected;
            running = (currentExecutionIndex != -1 && index == currentExecutionIndex);
            hovered = (index == hoveredTaskIndex);

            String first = (task.getActions() != null && !task.getActions().isEmpty() && task.getActions().get(0) != null)
                    ? task.getActions().get(0).getName() : null;
            chip.color = running ? Theme.ACCENT : chipColorFor(first);
            chip.letter = (first != null && !first.isEmpty()) ? first.substring(0, 1).toUpperCase() : "·";

            // Patch B.8: a timed task is visibly out of the rotation - clock + its interval
            // v1.69: and a setup-only entry is marked the same way, so you can see which
            // entries drop out after the first lap without selecting each one in turn.
            String base = task.isTimed()
                    ? "\u23F1 " + task.getName() + "   (every ~" + task.getTimerMinutes() + "m)"
                    : task.getName();
            name.setText(task.isOnStartOnly()
                    ? "\u2191 " + base + "   (first loop only)"
                    : base);
            name.setForeground(running ? Theme.AMBER : Theme.TEXT);

            // action-chain preview, e.g. "Walk → Interact → Wait"
            StringBuilder sb = new StringBuilder();
            if (task.getActions() != null) {
                int shown = 0;
                for (Action a : task.getActions()) {
                    if (a == null) continue;
                    if (shown > 0) sb.append("  →  ");
                    sb.append(a.getName());
                    if (++shown >= 5) { sb.append(" …"); break; }
                }
            }
            preview.setText(sb.length() == 0 ? "empty task" : sb.toString());

            int rep = task.getRepeat();
            badge.setText(task.isTimed()
                    ? "TIMED"                                // Patch B.8: not part of the rotation
                    : "×" + Math.max(1, rep));               // Patch B.2: always visible, ×1 included
            repMinus.setVisible(!task.isTimed());            // v1.31: steppers only for looped tasks
            repPlus.setVisible(!task.isTimed());

            setToolTipText(task.isOnStartOnly()
                    ? "<html>Runs on the first queue loop only, then skipped.<br>"
                      + escapeHtml(String.valueOf(task.getDescription())) + "</html>"
                    : task.getDescription());
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            int x = 2, y = 2, cw = w - 4, ch = h - 6; // leave a small gap between cards

            g2.setColor(running ? Theme.SURFACE_2 : (selected ? Theme.SURFACE_2 : Theme.SURFACE_2_ALT));
            g2.fillRoundRect(x, y, cw, ch, 11, 11);

            g2.setColor(running ? new Color(0x2F2023)
                    : (selected ? Theme.BORDER_STRONG : (hovered ? Theme.BORDER_STRONG : Theme.BORDER)));
            g2.drawRoundRect(x, y, cw, ch, 11, 11);

            if (running) { // left accent bar
                g2.setColor(Theme.ACCENT);
                g2.fillRoundRect(x, y + 4, 4, ch - 8, 3, 3);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static class SettingsSnapshot {
        public boolean startScriptOnLoad;
        public boolean exitOnStopWarning;
        public boolean renderingDisabled;
        public boolean hideRoofsEnabled;
        public boolean dataOrbsEnabled;
        public boolean transparentSidePanel;
        public boolean gameAudioOn;
        public boolean transparentChatbox;
        public boolean clickThroughChatbox;
        public boolean shiftClickDrop;
        public boolean escClosesInterface;
        public boolean levelUpInterface;
        public boolean lootNotifications;
        public boolean autoSave;
    }

    private SettingsSnapshot captureSettingsSnapshot() {
        SettingsSnapshot s = new SettingsSnapshot();
        s.startScriptOnLoad
                = settingClientChkStartScriptOnLoad != null
                && settingClientChkStartScriptOnLoad.isSelected();

        s.exitOnStopWarning
                = settingClientChkExitOnStopWarning != null
                && settingClientChkExitOnStopWarning.isSelected();

        s.renderingDisabled
                = chkDisableRendering != null
                && chkDisableRendering.isSelected();

        s.autoSave =
                chkAutoSave != null
                        && chkAutoSave.isSelected();

        s.hideRoofsEnabled
                = chkHideRoofs != null
                && chkHideRoofs.isSelected();

        s.dataOrbsEnabled
                = chkDataOrbs != null
                && chkDataOrbs.isSelected();

        s.transparentSidePanel
                = chkTransparentSidePanel != null
                && chkTransparentSidePanel.isSelected();

        s.gameAudioOn
                = chkGameAudio != null
                && chkGameAudio.isSelected();

        s.transparentChatbox
                = chkTransparentChatbox != null
                && chkTransparentChatbox.isSelected();

        s.clickThroughChatbox
                = chkClickThroughChatbox != null
                && chkClickThroughChatbox.isSelected();

        s.shiftClickDrop
                = chkShiftClickDrop != null
                && chkShiftClickDrop.isSelected();

        s.escClosesInterface
                = chkEscClosesInterface != null
                && chkEscClosesInterface.isSelected();

        s.levelUpInterface
                = chkLevelUpInterface != null
                && chkLevelUpInterface.isSelected();

        s.lootNotifications
                = chkLootNotifications != null
                && chkLootNotifications.isSelected();

        return s;
    }

    private int[] captureLocation() {
        Tile tile = Players.getLocal().getTile();
        return new int[]{tile.getX(), tile.getY(), tile.getZ()};
    }

    private int[] captureInventory() {
        return Inventory.all().stream()
                .filter(Objects::nonNull)
                .mapToInt(Item::getID)
                .toArray();
    }

    private int[] captureWorn() {
        return Equipment.all().stream()
                .filter(Objects::nonNull)
                .mapToInt(Item::getID)
                .toArray();
    }

    private int[] captureSkills() {
        int[] xp = new int[Skill.values().length];
        for (Skill s : Skill.values()) {
            xp[s.ordinal()] = Skills.getExperience(s);
        }
        return xp;
    }

    private JPanel createPresetControlPanel() {
        JPanel container = new JPanel(new BorderLayout(5, 0));
        container.setOpaque(false);
        container.setBorder(new EmptyBorder(5, 0, 5, 0));

        JPanel grid = new JPanel(new GridLayout(1, 4, 5, 0));
        grid.setOpaque(false);

        for (int i = 0; i < 4; i++) {
            final int slot = i;
            presetButtons[i] = createButton("Preset " + (i + 1));
            presetButtons[i].setFont(new Font("Segoe UI", Font.BOLD, 10));
            presetButtons[i].addActionListener(e -> handlePresetClick(slot, e));
            // v1.31: right-click a preset for Rename / Save queue here / Duplicate to slot -
            // the ctrl+shift / shift shortcuts still work, this just makes them findable.
            presetButtons[i].addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent me)  { maybe(me); }
                @Override public void mouseReleased(MouseEvent me) { maybe(me); }
                private void maybe(MouseEvent me) {
                    if (me.isPopupTrigger()) showPresetContextMenu(slot, me.getX(), me.getY());
                }
            });

            // Map the physical 'Delete' key to securely prompt and squash the list
            presetButtons[i].addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_DELETE)
                        promptAndDeletePreset();
                }
            });

            grid.add(presetButtons[i]);
        }

        JPanel nav = new JPanel(new GridLayout(2, 1, 0, 2));
        nav.setOpaque(false);

        // Navigation Up (Previous Page)
        btnPageUp = createButton("▲");
        btnPageUp.setPreferredSize(new Dimension(30, 0));
        // Ensure the button remains enabled so the user can click it to see the toast
        btnPageUp.setEnabled(true);
        btnPageUp.addActionListener(e -> {
            if (provisionalPresetIndex >= 0) compactPresets();   // v1.88
            if (currentPresetPage > 0) {
                currentPresetPage--;
                refreshPresetButtonLabels();
                this.showToast("Page " + (currentPresetPage + 1), btnPageUp, true);
            } else {
                // Literal requirement: feedback when no more pages exist upwards
                this.showToast("No more presets!", btnPageUp, false);
            }
        });

        // Navigation Down (Next Page)
        // FIXED: this button previously had TWO ActionListeners attached (one here, one below),
        // so a single click advanced two pages. The single listener below is the survivor.
        btnPageDown = createButton("▼");
        btnPageDown.setEnabled(true);

        /*
         * Listener for navigating to the previous page of presets.
         * Decrements the page index and updates UI state, or flashes if already at the first page.
         */
//        btnPageUp.addActionListener(e -> {
//            // if this is not the first page
//            if (currentPresetPage > 0) {
//                // move to the previous page
//                currentPresetPage--;
//
//                // if we are now on the first page, disable back navigation
//                if (currentPresetPage == 0)
//                    btnPageUp.setEnabled(false);
//
//                // update button labels to refresh names/colors
//                refreshPresetButtonLabels();
//                this.makeToast("Page " + (currentPresetPage + 1), btnPageUp, true);
//            } else {
//                // Visual feedback for invalid navigation attempt
//                flashControl(btnPageUp, COLOR_FAILURE);
//            }
//        });

        /*
         * Listener for navigating to the next page of presets.
         * Increments the page index if expansion is possible; otherwise, triggers a warning toast.
         */
        btnPageDown.addActionListener(e -> {
            if (provisionalPresetIndex >= 0) compactPresets();   // v1.88
            if (canExpandPresets()) {
                currentPresetPage++;
                btnPageUp.setEnabled(true);
                refreshPresetButtonLabels();
                this.showToast("Page " + (currentPresetPage + 1), btnPageDown, true);
            } else {
                // Determine if failure is due to hard limit or empty slots on current page
                String message = (currentPresetPage + 1) * 4 >= MAX_PRESETS ?
                        String.format("Max presets reached! (%d)", MAX_PRESETS)
                        : "Fill current slots first!";

                this.showToast(message, btnPageDown, false);
            }
        });

        nav.add(btnPageUp);
        nav.add(btnPageDown);

        container.add(grid, BorderLayout.CENTER);
        container.add(nav, BorderLayout.EAST);
        return container;
    }

    private String getPresetsForDatabase() {
        if (modelPresets == null || modelPresets.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < modelPresets.size(); i++) {
            Preset p = modelPresets.get(i);
            // Using the pipe (|) to separate Name and Script, and semicolon (;) to separate Presets
            sb.append(p.name).append("|").append(p.tasks);
            if (i < modelPresets.size() - 1)
                sb.append(";");

        }
        return sb.toString();
    }

    /** v1.31: the preset button context menu (Rename / Save here / Duplicate to slot). */
    private void showPresetContextMenu(int slot, int x, int y) {
        int actualIndex = (currentPresetPage * 4) + slot;
        while (modelPresets.size() <= actualIndex)
            modelPresets.addElement(new Preset("Preset " + (modelPresets.size() + 1), new ArrayList<>()));
        Preset preset = modelPresets.get(actualIndex);

        JPopupMenu menu = new JPopupMenu();

        JMenuItem miRename = new JMenuItem("Rename…");
        miRename.addActionListener(a -> {
            String newName = JOptionPane.showInputDialog(this, "Enter preset name:", preset.name);
            if (newName != null && !newName.trim().isEmpty()) {
                preset.name = newName.trim();
                refreshPresetButtonLabels();
                saveAll(false);
                showToast("Renamed to " + preset.name, presetButtons[slot], true);
            }
        });

        JMenuItem miSave = new JMenuItem("Save current queue here");
        miSave.addActionListener(a -> {
            List<Task> currentTasks = new ArrayList<>();
            for (int i = 0; i < modelTaskList.size(); i++)
                currentTasks.add(new Task(modelTaskList.getElementAt(i)));
            Preset saved = new Preset(preset.name, currentTasks, queueLoopTarget);
            saved.setScriptTriggers(currentScriptTriggersJson());   // v1.64
            modelPresets.set(actualIndex, saved);
            selectedPresetIndex = actualIndex;
            refreshPresetButtonLabels();
            saveAll(false);
            showToast("Saved " + currentTasks.size() + " task(s) to " + preset.name,
                    presetButtons[slot], true);
        });

        JMenuItem miDuplicate = new JMenuItem("Duplicate to slot…");
        miDuplicate.setEnabled(!preset.tasks.isEmpty());
        miDuplicate.addActionListener(a -> {
            JSpinner sp = new JSpinner(new SpinnerNumberModel(
                    Math.min(MAX_PRESETS, actualIndex + 2), 1, MAX_PRESETS, 1));
            if (JOptionPane.showConfirmDialog(this, sp,
                    "Copy \"" + preset.name + "\" into which slot (1-" + MAX_PRESETS + ")?",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                    != JOptionPane.OK_OPTION) return;
            int target = (int) sp.getValue() - 1;
            while (modelPresets.size() <= target)
                modelPresets.addElement(new Preset("Preset " + (modelPresets.size() + 1), new ArrayList<>()));
            Preset existing = modelPresets.get(target);
            if (!existing.tasks.isEmpty() && JOptionPane.showConfirmDialog(this,
                    "Slot " + (target + 1) + " (\"" + existing.name + "\") isn't empty. Overwrite?",
                    "Overwrite preset", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
            List<Task> copy = new ArrayList<>();
            for (Task t : preset.tasks) copy.add(new Task(t));
            Preset dup = new Preset(preset.name + " (copy)", copy, preset.getLoops());
            dup.setScriptTriggers(preset.getScriptTriggers());   // v1.64
            modelPresets.set(target, dup);
            refreshPresetButtonLabels();
            saveAll(false);
            showToast("Copied to slot " + (target + 1), presetButtons[slot], true);
        });

        // v1.88: a real Delete item. The behaviour existed (promptAndDeletePreset, squash and
        // all) but the only way to reach it was pressing Delete while the button happened to
        // hold keyboard focus - which nobody would ever discover.
        JMenuItem miDelete = new JMenuItem("Delete preset\u2026",
                main.menu.components.UIIcons.trash(14, Theme.DANGER));
        miDelete.setEnabled(actualIndex < modelPresets.size());
        miDelete.addActionListener(a -> {
            selectedPresetIndex = actualIndex;
            promptAndDeletePreset();
        });

        menu.add(miRename);
        menu.add(miSave);
        menu.add(miDuplicate);
        menu.addSeparator();
        menu.add(miDelete);
        menu.show(presetButtons[slot], x, y);
    }

    private void handlePresetClick(int slot, ActionEvent e) {
        int actualIndex = (currentPresetPage * 4) + slot;
        // v1.88: clicking a DIFFERENT preset is attention moving on - drop any slot that was
        // reset and left untouched before the indices shift under us.
        if (provisionalPresetIndex >= 0 && provisionalPresetIndex != actualIndex) {
            int wasSel = selectedPresetIndex;
            selectedPresetIndex = -1;         // let the provisional slot be considered
            compactPresets();
            selectedPresetIndex = Math.min(wasSel, modelPresets.size() - 1);
            actualIndex = (currentPresetPage * 4) + slot;
        }
        boolean shift = (e.getModifiers() & ActionEvent.SHIFT_MASK) != 0;
        boolean ctrl = (e.getModifiers() & ActionEvent.CTRL_MASK) != 0;

        // Ensure list growth defaults to proper "Preset X" formatting
        while (modelPresets.size() <= actualIndex)
            modelPresets.addElement(new Preset("Preset " + (modelPresets.size() + 1), new ArrayList<>()));

        ///  Ctrl + shift click preset to rename
        if (ctrl && shift) {
            String newName = JOptionPane.showInputDialog(this, "Enter preset name:");
            if (newName != null && !newName.trim().isEmpty()) {
                modelPresets.get(actualIndex).name = newName.trim();
                // v1.30: the button label refreshes the moment you rename - previously the new
                // name only appeared after something else happened to redraw the row.
                refreshPresetButtonLabels();
                saveAll(false);
                this.showToast("Renamed to " + newName.trim(), presetButtons[slot], true);
            }

            /// Shift click preset to save
        } else if (shift) {
            List<Task> currentTasks = new ArrayList<>();

            for (int i = 0; i < modelTaskList.size(); i++)
                currentTasks.add(new Task(modelTaskList.getElementAt(i)));

            String currentName = modelPresets.get(actualIndex).name;
            Preset saved2 = new Preset(currentName, currentTasks, queueLoopTarget);
            saved2.setScriptTriggers(currentScriptTriggersJson());   // v1.64
            modelPresets.set(actualIndex, saved2);
            // TODO decide whether or not to select the preset on save
            selectedPresetIndex = actualIndex; // Select it upon saving
            refreshPresetButtonLabels();
            this.showToast("Saved to " + currentName, presetButtons[slot], true);

            /// Normal click to load
        } else {
            // v1.62: don't yank the list out from under a running script. Loading a preset
            // REPLACES the whole Task List; doing that mid-run would desync the live execution
            // pointer, so while a script is actively playing (not merely paused) a preset click
            // asks first. Paused/idle loads straight through as before.
            if (currentExecutionIndex != -1 && !isMenuPaused()) {
                int go = JOptionPane.showConfirmDialog(this,
                        "A script is running. Loading a preset will stop it and replace the "
                        + "whole Task List.\nStop and load anyway?",
                        "Script is running", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (go != JOptionPane.YES_OPTION) return;
                isMenuPaused(true);            // halt the queue cleanly (same as the account-switch
                resetLoopProgress();          // path) before the list is swapped out from under it
                setCurrentExecutionIndex(-1);
            }

            // fetch the selected preset object using the preset buttons index
            Preset preset = modelPresets.get(actualIndex);

            if (preset.tasks.isEmpty()) {
                this.showToast(preset.name + " is empty!", presetButtons[slot], false);
            } else {
                // store the total task count for this set
                int taskCount = 0;
                // clear the old task
                modelTaskList.clear();

                // load in the preset tasks
                for (Task t : preset.tasks) {
                    modelTaskList.addElement(new Task(t));
                    taskCount++;
                }

                // apply the preset's saved whole-queue loop count
                queueLoopTarget = Math.max(0, preset.getLoops());
                queueLoopCurrent = 1;
                // v1.64: swap in this preset's script triggers as the live script-trigger set
                scriptTriggers.clear();
                if (preset.getScriptTriggers() != null && !preset.getScriptTriggers().isBlank())
                    scriptTriggers.addAll(
                            main.watchers.TriggerCodec.fromJson(preset.getScriptTriggers()));
                syncLoopControls();
                updateQueueProgress();

                // inform the user that the preset has been loaded and how many tasks this set has.
                loadPreset(actualIndex);
                this.showToast("Loaded " + taskCount + " tasks from " + preset.name, presetButtons[slot], true);
            }
        }
    }

    private void loadPreset(int index) {
        try {
            Preset p = modelPresets.get(index);

            if (p == null)
                throw new IndexOutOfBoundsException("Unable to index a null preset object!");

            if (p.tasks.isEmpty())
                throw new IndexOutOfBoundsException("Unable to index a empty preset object!");

            selectedPresetIndex = index;

            // Force color update on EDT
            SwingUtilities.invokeLater(() -> {
                refreshPresetButtonLabels();
                refreshTaskListTab();
            });

        } catch (IndexOutOfBoundsException i) {
            Logger.log(Logger.LogType.ERROR, i.getMessage());
            Logger.log(Logger.LogType.WARN, "[DreamMan] " + i.getClass().getSimpleName());   // v1.89 (SDN): no stack traces to console
        }
    }

    /**
     * v1.88: the per-preset RESET (what replaced the nuclear "Reset All"). Clears the SELECTED
     * preset's tasks and restores its default "Preset N" name, so the slot is immediately
     * reusable - save a different queue into it and it's yours again.
     *
     * <p>A reset slot is left <b>provisional</b>: still there, still clickable, but if you
     * wander off without putting anything in it or renaming it, {@link #compactPresets()}
     * closes the space so the strip never reads "preset 1, 2, nothing, 4".
     */
    private void resetSelectedPreset(JComponent anchor) {
        if (selectedPresetIndex < 0 || selectedPresetIndex >= modelPresets.size()) {
            showToast("Select a preset to reset", anchor, false);
            return;
        }
        Preset p = modelPresets.get(selectedPresetIndex);
        if (!p.tasks.isEmpty() && JOptionPane.showConfirmDialog(this,
                "<html>Clear <b>\u201c" + escapeHtml(p.name) + "\u201d</b> ("
                + p.tasks.size() + " task" + (p.tasks.size() == 1 ? "" : "s") + ")?<br><br>"
                + "The slot stays open so you can save something new into it. Leave it empty "
                + "and it closes up on its own.<br>"
                + "<i>Your tasks themselves are untouched \u2014 they live in the library.</i></html>",
                "Reset preset", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE)
                != JOptionPane.OK_OPTION) return;

        Preset blank = new Preset(defaultPresetName(selectedPresetIndex), new ArrayList<>());
        modelPresets.set(selectedPresetIndex, blank);
        provisionalPresetIndex = selectedPresetIndex;   // watched by compactPresets()
        refreshPresetButtonLabels();
        saveAll(false);
        showToast("Reset \u2014 save a queue here, or leave it and the slot closes up",
                anchor, true);
    }

    /** The name an untouched slot carries ("Preset 3"), used to spot ones nobody has claimed. */
    private static String defaultPresetName(int index) { return "Preset " + (index + 1); }

    /** True for a slot with no tasks and its factory name - i.e. nobody has put anything in it. */
    private boolean isUnclaimed(int index) {
        if (index < 0 || index >= modelPresets.size()) return false;
        Preset p = modelPresets.get(index);
        return p != null && (p.tasks == null || p.tasks.isEmpty())
                && (p.name == null || p.name.isBlank() || p.name.equals(defaultPresetName(index)));
    }

    /** Set by a reset; the slot is dropped if it's still unclaimed when attention moves away. */
    private int provisionalPresetIndex = -1;

    /**
     * v1.88: closes the gaps. Any preset that's empty AND still wearing its default name is
     * removed and the rest shuffle down, so the strip is always a solid run of real presets.
     * Called when attention leaves a slot - clicking another preset, changing page, or leaving
     * the tab - never mid-edit, so a slot you're actively filling can't vanish under you.
     */
    private void compactPresets() {
        boolean changed = false;
        for (int i = modelPresets.size() - 1; i >= 0; i--) {
            if (i == selectedPresetIndex) continue;   // never squash what's open in front of you
            if (!isUnclaimed(i)) continue;
            modelPresets.remove(i);
            if (selectedPresetIndex > i) selectedPresetIndex--;
            changed = true;
        }
        provisionalPresetIndex = -1;
        if (!changed) return;
        // a page that just emptied itself shouldn't strand you looking at nothing
        while (currentPresetPage > 0 && currentPresetPage * 4 >= modelPresets.size())
            currentPresetPage--;
        if (btnPageUp != null) btnPageUp.setEnabled(currentPresetPage > 0);
        refreshPresetButtonLabels();
        saveAll(false);
    }

    private void promptAndDeletePreset() {
        if (selectedPresetIndex == -1 || selectedPresetIndex >= modelPresets.size()) {
            return; // Safety guard: ignore delete press if nothing valid is selected
        }

        Preset p = modelPresets.get(selectedPresetIndex);
        int choice = JOptionPane.showConfirmDialog(this,
                "WARNING: You are about to delete '" + p.name + "'.\nThis will shift all subsequent presets down. Continue?",
                "Delete Preset?",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            // Squashes the ArrayList left automatically
            modelPresets.remove(selectedPresetIndex);

            // Clear selection because it's been removed
            selectedPresetIndex = -1;

            // If squashing makes the current page completely empty, bump back a page
            if (currentPresetPage > 0 && (currentPresetPage * 4) >= modelPresets.size()) {
                currentPresetPage--;
                if (currentPresetPage == 0) btnPageUp.setEnabled(false);
            }

            refreshPresetButtonLabels();
            showToast("Preset Deleted & Squashed", mainTabs, true);
        }
    }

    private void refreshPresetButtonLabels() {
        for (int i = 0; i < 4; i++) {
            int actualIndex = (currentPresetPage * 4) + i;
            // Reset foreground explicitly to prevent unreadable text
            presetButtons[i].setForeground(Color.WHITE);

            // Patch B.2: FlatButtonUI paints from client properties, not setBackground - the
            // old code set backgrounds the UI never read, so saved/selected/empty presets all
            // looked identical (why presets felt "broken"). States now:
            //   selected -> gold accent · has tasks -> crimson fill + task count · empty -> ghost
            if (actualIndex < modelPresets.size()) {
                Preset p = modelPresets.get(actualIndex);
                boolean sel = actualIndex == selectedPresetIndex;
                boolean filled = !p.tasks.isEmpty();
                presetButtons[i].setText(filled ? p.name + "  (" + p.tasks.size() + ")" : p.name);
                presetButtons[i].putClientProperty("accent", sel ? Boolean.TRUE : null);
                presetButtons[i].putClientProperty("fillColor", (!sel && filled) ? COLOR_BLOOD : null);
            } else {
                presetButtons[i].setText("Preset " + (actualIndex + 1));
                presetButtons[i].putClientProperty("accent",
                        actualIndex == selectedPresetIndex ? Boolean.TRUE : null);
                presetButtons[i].putClientProperty("fillColor", null);
            }
            presetButtons[i].repaint();

            // repaint the whole tab to finalize gui update
            listTaskList.repaint();
        }
    }

    private boolean canExpandPresets() {
        // Check if current 4 are filled
        for (int i = 0; i < 4; i++) {
            int idx = (currentPresetPage * 4) + i;
            if (idx >= modelPresets.size() || modelPresets.get(idx).tasks.isEmpty())
                return false;
        }

        // check if next page size exceeds preset limit
        return ((currentPresetPage + 1) * 4) < MAX_PRESETS;
    }
}