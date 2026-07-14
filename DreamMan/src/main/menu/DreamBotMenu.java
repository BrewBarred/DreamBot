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

    // ── Patch B.11: the script market ──
    private main.market.ScriptRepository marketRepo;
    private final DefaultListModel<main.market.ScriptListing> modelMarket = new DefaultListModel<>();
    private final JList<main.market.ScriptListing> listMarket = new JList<>(modelMarket);
    private final List<main.market.ScriptListing> marketAll = new ArrayList<>();
    private JTextField marketSearchField;
    private JComboBox<String> marketSortCombo;
    private JLabel lblMarketSource;
    private JComboBox<String> libraryFilterCombo;
    /** Patch B.15: explicit "hide VIP tasks" toggle (default on for non-VIP). */
    private JCheckBox libraryHideVipCheck;

    /** Adds to the master library and refreshes the view (Patch B.5). */
    public void libraryAdd(Task t) {
        if (t == null) return;
        libraryAll.add(t);
        refilterLibrary();
    }

    /** Removes from the master library and refreshes the view. @return removed? */
    public boolean libraryRemove(Task t) {
        boolean ok = libraryAll.remove(t);
        if (ok) refilterLibrary();
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
                libraryAll.set(i, copy);
                touched++;
            }
        }
        if (touched > 0) refilterLibrary();
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
    private JLabel inspName, inspMeta, inspStatus;
    private JTextArea inspDesc, inspAttrs;
    // Patch B.4: always-on watchers - checked between every action while the player is safe.
    private final List<main.watchers.Trigger> globalTriggers =
            Collections.synchronizedList(new ArrayList<>());

    /** Always-on watchers (the "default background checks"); read by the engine each loop. */
    public List<main.watchers.Trigger> getGlobalTriggers() { return globalTriggers; }

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
    private final JLabel totalXpGainedLabel = new JLabel();
    private final JLabel totalLevelsGainedLabel = new JLabel();
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
        taskBuilder = new TaskBuilder(this);
        libraryPanel = new LibraryPanel();

        mainTabs.setBackground(Theme.SURFACE_1);
        mainTabs.setForeground(Theme.TEXT_DIM);
        mainTabs.setFont(Theme.font(13));
        mainTabs.addTab("Task List", loadTabIcon("task_list_tab"), createTaskListTab());
        mainTabs.addTab("Task Library", loadTabIcon("task_library_tab"), createTaskLibraryTab());
        mainTabs.addTab("Task Builder", loadTabIcon("task_builder_tab"), taskBuilder);
        mainTabs.addTab("Skill Tracker", loadTabIcon("skills_tracker_tab"), createSkillTrackerTab());
        mainTabs.addTab("Checks", loadTabIcon("checks_tab"), createWatchersTab());
        mainTabs.addTab("Loot Tracker", loadTabIcon("loot_tracker_tab"), createLootTrackerTab());
        mainTabs.addTab("Market", loadTabIcon("market_tab"), createMarketTab());
        mainTabs.addTab("Status", loadTabIcon("status_tab"), createStatusTab());
        mainTabs.addTab("Settings", loadTabIcon("settings_tab"), createSettingsTab());
        // Patch B.3: the Developers Console is locked to YOU specifically. It unlocks only if
        // the logged-in character is in DEV_ACCOUNTS, OR <home>/DreamMan/dev.flag contains the
        // exact DEV_TOKEN. A normal user can't guess the token, and an empty flag file (which
        // anyone might try) no longer does anything - so a release build shows it to no one.
        if (isDeveloper())
            mainTabs.addTab("Developers Console", loadTabIcon("developers_console_tab"),
                    new DevelopersConsole(libraryPanel));

        Logger.log(Logger.LogType.DEBUG, "Setup main tabs...");

        projectionSpinner = new JSpinner(new SpinnerNumberModel(24, 1, 999, 1));
        styleSpinner(projectionSpinner);

        trackerList = new JPanel();
        trackerList.setLayout(new BoxLayout(trackerList, BoxLayout.Y_AXIS));
        trackerList.setBackground(PANEL_SURFACE);
        JScrollPane sScroll = Theme.thinScrollbars(new JScrollPane(trackerList));
        sScroll.setBorder(null);
        sScroll.getViewport().setBackground(PANEL_SURFACE);

        ///  Define side panel
        JPanel sidePanelContent = new JPanel(new BorderLayout());
        sidePanelContent.setPreferredSize(new Dimension(360, 0));
        sidePanelContent.setBackground(PANEL_SURFACE);
        sidePanelContent.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, COLOR_BORDER_DIM));

        ///  Add toggle button to show/hide side panel
        JButton btnToggleSidePanel = new Theme.ThemedButton();
        styleHeaderLabel(totalXpGainedLabel);
        styleHeaderLabel(totalLevelsGainedLabel);

        JPanel totals = new JPanel(new GridLayout(3, 1, 5, 10));
        totals.setOpaque(false);

        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        topRow.setOpaque(false);
        topRow.add(totalXpGainedLabel);

        JPanel middleRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        middleRow.setOpaque(false);
        middleRow.add(totalLevelsGainedLabel);

        JPanel bottomRow = new JPanel(new GridLayout(1, 2));
        bottomRow.setOpaque(false);
        bottomRow.add(totalLevelLabelF2P);
        bottomRow.add(totalLevelLabelP2P);

        totals.add(topRow);
        totals.add(middleRow);
        totals.add(bottomRow);

        ///  Add side panel controls
        sidePanelContent.add(createSubtitle("Live Tracker"), BorderLayout.NORTH);
        sidePanelContent.add(sScroll, BorderLayout.CENTER);
        sidePanelContent.add(totals, BorderLayout.SOUTH);
        sidePanelContent.setVisible(false);
        // cap the tracker width so it can never push the window wider
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

            ///  Start a third timer to auto-save everything periodically
            saveTimer = new Timer(60000, e -> {
                // auto-save every n seconds (if auto-save feature is enabled in settings)
                if (chkAutoSave != null && chkAutoSave.isSelected())
                    saveAll(false);
            });
            saveTimer.start();

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
        // after every task anyway. Range in ms; persisted with the profile.
        chkQueueWait = new JCheckBox("Auto-wait");
        chkQueueWait.setOpaque(false);
        chkQueueWait.setForeground(TEXT_MAIN);
        chkQueueWait.setToolTipText("Pause a random amount (below, in ms) after each completed task");
        chkQueueWait.addActionListener(e -> {
            queueAutoWait = chkQueueWait.isSelected();
            queueWaitMinInput.setEnabled(queueAutoWait);
            queueWaitMaxInput.setEnabled(queueAutoWait);
        });
        queueWaitMinInput = new JTextField("400", 4);
        queueWaitMaxInput = new JTextField("1200", 4);
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
        left.add(chkQueueWait);
        left.add(queueWaitMinInput);
        left.add(queueWaitMaxInput);

        // ---- right: skip / run-from-here + live indicator ----
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        JButton btnRunHere = createButton("Run from here");
        btnRunHere.setToolTipText("Start the queue at the selected task");
        btnRunHere.addActionListener(e -> {
            int idx = listTaskList.getSelectedIndex();
            if (idx < 0) {
                showToast("Select a task first", btnRunHere, false);
                return;
            }
            setCurrentExecutionIndex(idx);
            resetLoopProgress();
            listTaskList.repaint();
            showToast("Running from task " + (idx + 1), btnRunHere, true);
        });

        JButton btnSkip = createButton("Skip");
        btnSkip.setToolTipText("Skip the current task and move to the next");
        btnSkip.addActionListener(e -> {
            if (modelTaskList.isEmpty()) return;
            advanceQueue();
            showToast("Skipped", btnSkip, true);
        });

        lblLoopIndicator = new JLabel("—");
        lblLoopIndicator.setForeground(COLOR_EXECUTING);
        lblLoopIndicator.setFont(new Font("Consolas", Font.BOLD, 13));
        lblLoopIndicator.setBorder(new EmptyBorder(0, 10, 0, 4));

        right.add(btnRunHere);
        right.add(btnSkip);
        right.add(lblLoopIndicator);

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);

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

        JButton btnLogin = createButton("Login");
        btnLogin.setToolTipText("Log in to the configured account");
        btnLogin.addActionListener(e -> { if (scriptControls != null) scriptControls.requestLogin(); });
        JButton btnLogout = createButton("Logout");
        btnLogout.setToolTipText("Log out");
        btnLogout.addActionListener(e -> { if (scriptControls != null) scriptControls.requestLogout(); });

        controls.add(btnPlayPause);
        controls.add(btnStop);
        controls.add(btnLogin);
        controls.add(btnLogout);
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
        btnAddAccount.setToolTipText("Add another Jagex account");
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

        JPanel right = new JPanel(new BorderLayout(4, 0));
        right.setOpaque(false);
        right.add(accountSwitcher, BorderLayout.CENTER);
        right.add(btnAddAccount, BorderLayout.EAST);

        JLabel lbl = new JLabel("Accounts");
        lbl.setForeground(TEXT_DIM);
        row.add(lbl, BorderLayout.WEST);
        row.add(right, BorderLayout.CENTER);
        row.add(btnLoginRow, BorderLayout.EAST);

        refreshAccountSwitcher();
        return row;
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
        if (!main.market.AccountVault.isUnlocked()) { showToast("Log in first", btnAddAccount, false); return; }
        JTextField name = new JTextField(16);
        JTextField label = new JTextField(16);
        JPasswordField pin = new JPasswordField(16);
        JPanel form = new JPanel(new GridLayout(3, 2, 4, 4));
        form.add(new JLabel("Account name:")); form.add(name);
        form.add(new JLabel("Label (e.g. Ironman):")); form.add(label);
        form.add(new JLabel("Bank PIN (optional):")); form.add(pin);
        if (JOptionPane.showConfirmDialog(this, form, "Add account",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

        main.market.AccountVault.Account a = new main.market.AccountVault.Account(
                name.getText().trim(), label.getText().trim());
        a.pin = new String(pin.getPassword());
        String err = main.market.AccountVault.addAccount(a);
        if (err != null) { showToast(err, btnAddAccount, false); return; }
        if (marketRepo instanceof main.market.HttpRepository)
            main.market.AccountVault.sync(new main.market.ServerAccount(
                    ((main.market.HttpRepository) marketRepo).baseUrl()));
        refreshAccountSwitcher();
        showToast("Added " + a.name, btnAddAccount, true);
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
        if (marketRepo instanceof main.market.HttpRepository) { reloadMarket(); return; }
        if (!main.privacy.Consent.has(main.privacy.Consent.MARKET_BROWSE)) return;
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
    private void openExportDialog(JComponent anchor) {
        if (modelTaskList.isEmpty()) {
            showToast("Add some tasks to the queue first", anchor, false);
            return;
        }

        JTextField txtName = new JTextField("My DreamMan Script", 22);
        String who = safePlayerName();
        JTextField txtAuthor = new JTextField(who == null || who.isEmpty() ? "Anonymous" : who, 22);
        JSpinner spVersion = new JSpinner(new SpinnerNumberModel(1.0, 0.1, 999.0, 0.1));
        JTextArea txtDesc = new JTextArea(3, 22);
        txtDesc.setLineWrap(true);
        txtDesc.setWrapStyleWord(true);

        JCheckBox chkChecks = new JCheckBox("Include my always-on checks (" + globalTriggers.size() + ")",
                !globalTriggers.isEmpty());
        chkChecks.setOpaque(false);
        chkChecks.setForeground(TEXT_MAIN);

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
        form.add(chkChecks, c);
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
        bundle.loops = queueLoopTarget;
        bundle.tasks = ProfileCodec.tasksToData(modelTaskList);
        if (chkChecks.isSelected())
            bundle.globalTriggers = main.watchers.TriggerCodec.toJson(
                    new ArrayList<>(globalTriggers));

        // where to write it
        java.io.File target;
        if (scriptsDir != null) {
            target = new java.io.File(scriptsDir,
                    main.tools.ScriptExporter.safeFileName(bundle.name) + ".jar");
        } else {
            JFileChooser chooser = new JFileChooser();
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
        listTaskList.setCellRenderer(new TaskCardRenderer());

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

        // Sub-panel for the original buttons
        JPanel southButtons = new JPanel(new GridLayout(1, 5, 5, 0)); // Adjusted from 6 to 5 since Del Preset is removed
        southButtons.setOpaque(false);

        ///  Create Task List save button
        JButton btnTaskListSave = createButton("Save");
        btnTaskListSave.addActionListener(e -> saveAll());

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

        /// Create Reset All button
        JButton btnResetAllPresets = createButton("Reset All", new Color(139, 0, 0), null);
        btnResetAllPresets.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(this, "WARNING: This will delete ALL presets (could be 100s!). Continue?", "Nuclear Reset", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                modelPresets.clear();
                currentPresetPage = 0;
                selectedPresetIndex = -1;
                btnPageUp.setEnabled(false);
                refreshPresetButtonLabels();
                this.showToast("System Reset Complete!", btnResetAllPresets, true);
            }
        });

        listTaskList.addListSelectionListener(e -> {
            btnTaskListRemove.setEnabled(!listTaskList.isSelectionEmpty());
            syncTaskRepeatSpinner();
        });

        listTaskList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // get the index based on the specific mouse coordinates
                int index = listTaskList.locationToIndex(e.getPoint());

                // ensure the index is valid and the click bounds actually contain that item
                if (index != -1 && listTaskList.getCellBounds(index, index).contains(e.getPoint())) {

                    listTaskList.setSelectedIndex(index);
                    if (e.getClickCount() == 2 && !e.isShiftDown()) {
                        loadIntoBuilder(listTaskList.getSelectedValue());
                        mainTabs.setSelectedIndex(2);
                    }
                }
            }
            @Override public void mousePressed(MouseEvent e)  { maybePopup(e); }
            @Override public void mouseReleased(MouseEvent e) { maybePopup(e); }
            @Override public void mouseExited(MouseEvent e)   { hoveredTaskIndex = -1; listTaskList.repaint(); }
            private void maybePopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int index = listTaskList.locationToIndex(e.getPoint());
                if (index < 0 || !listTaskList.getCellBounds(index, index).contains(e.getPoint())) return;
                listTaskList.setSelectedIndex(index);
                showTaskContextMenu(e, index);
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

        // Patch B.8: mark a queued task as TIMED - it leaves the normal rotation and fires on a
        // rough interval at a safe point instead.
        JButton btnTaskListTimer = createButton("Timer\u2026", new Color(25, 60, 75), null);
        btnTaskListTimer.addActionListener(e -> {
            Task sel = listTaskList.getSelectedValue();
            if (sel == null) { showToast("Select a task first!", btnTaskListTimer, false); return; }
            openTimerDialog(sel, btnTaskListTimer);
        });

        // Patch B.10: one-click compile - turn the whole queue into a standalone DreamBot script
        JButton btnExportScript = createButton("Export as script\u2026", new Color(30, 70, 40), null);
        btnExportScript.setToolTipText("Package this queue into a .jar DreamBot can run on its own");
        btnExportScript.addActionListener(e -> openExportDialog(btnExportScript));

        ///  Add all buttons
        southButtons.add(btnTaskListSave);
        southButtons.add(btnTaskListDuplicate);
        southButtons.add(btnTaskListRemove);
        southButtons.add(btnTaskListTimer);
        southButtons.add(btnExportScript);
        southButtons.add(btnTaskListView);
        // UI preset delete button was removed here as per instructions
        southButtons.add(btnResetAllPresets);

        south.add(createPresetControlPanel(), BorderLayout.NORTH);
        south.add(createLoopBar(), BorderLayout.CENTER);
        south.add(southButtons, BorderLayout.SOUTH);

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
            @Override public void mouseClicked(MouseEvent me) {
                if (!main.market.Tier.isAdmin()) return;
                int idx = listTaskLibrary.locationToIndex(me.getPoint());
                if (idx < 0) return;
                Rectangle b = listTaskLibrary.getCellBounds(idx, idx);
                if (b == null || !b.contains(me.getPoint())) return;
                Task t = modelTaskLibrary.getElementAt(idx);
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
        libraryFilterCombo = new JComboBox<>(new String[]{"All", "Built by me", "Imported", "Default"});
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
        JPanel comboRow = new JPanel(new GridLayout(1, 2, 6, 0));
        comboRow.setOpaque(false);
        comboRow.add(libraryFilterCombo);
        comboRow.add(librarySortCombo);
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

        ///  Create Task Library save button
        JButton btnTaskLibrarySave = createButton("Save");
        btnTaskLibrarySave.addActionListener(e -> saveAll());

        ///  Create Task Library add button
        JButton btnTaskLibraryAdd = createButton("Add", COLOR_LIGHT_GREEN, null);
        btnTaskLibraryAdd.addActionListener(e -> {
            if(listTaskLibrary.getSelectedValue() != null) {
                // deep-copy so editing/executing the queued task never mutates the library original
                modelTaskList.addElement(new Task(listTaskLibrary.getSelectedValue()));
                this.showToast("Added to position " + modelTaskList.size() + " of the queue", btnTaskLibraryAdd, true);
            }
        });

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

        listTaskLibrary.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int selectedIndex = listTaskLibrary.getSelectedIndex();

                // return early if no task is currently selected in the task library
                if (selectedIndex == -1)
                    return;

                if (e.getClickCount() == 2) {
                    // Patch B.3: double-click opens the task in the BUILDER (same as the queue's
                    // double-click) ready to Save changes. Adding to the queue is the Add button.
                    loadIntoBuilder(modelTaskLibrary.getElementAt(selectedIndex));
                    mainTabs.setSelectedIndex(2);
                }
            }
        });

        ///  Add all buttons
        ///  Export the selected library task to a shareable .json file
        JButton btnTaskLibraryExport = createButton("Export...");
        btnTaskLibraryExport.addActionListener(e -> exportSelectedTask(btnTaskLibraryExport));

        ///  Import a task from a .json file into the library
        JButton btnTaskLibraryImport = createButton("Import...");
        btnTaskLibraryImport.addActionListener(e -> importTaskFromFile(btnTaskLibraryImport));

        btnSection.add(btnTaskLibrarySave);
        btnSection.add(btnTaskLibraryAdd);
        btnSection.add(btnTaskLibraryDelete);
        btnSection.add(btnTaskLibraryEdit);
        btnSection.add(btnTaskLibraryExport);
        btnSection.add(btnTaskLibraryImport);

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

    private void refreshTaskLibrary() {
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
            if (isDefault)
                star.setIcon(main.menu.components.UIIcons.star(15, Theme.ACCENT, true));
            else if (admin)
                star.setIcon(main.menu.components.UIIcons.star(15, Theme.TEXT_MUTED, false));
            else
                star.setIcon(null);
            star.setToolTipText(admin
                    ? (isDefault ? "Default task - ships to every user. Click to remove."
                                 : "Click to make this a default task for every user.")
                    : (isDefault ? "Default task - included with DreamMan." : null));
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
            date.setText(t.getCreatedAt() > 0
                    ? new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date(t.getCreatedAt()))
                    : " ");
            date.setForeground(TEXT_DIM);
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
        head.add(inspName);
        head.add(Box.createVerticalStrut(2));
        head.add(inspMeta);
        head.add(Box.createVerticalStrut(2));
        head.add(inspStatus);

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
        top.add(head, BorderLayout.NORTH);
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
            if (inspDesc != null) inspDesc.setText("");
            if (inspAttrs != null) inspAttrs.setText("");
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
        inspDesc.setText(t.getDescription() == null ? "" : t.getDescription());
        inspDesc.setCaretPosition(0);
        if (t.getActions() != null)
            for (Action a : t.getActions())
                if (a != null) inspActionsModel.addElement(a);
        inspAttrs.setText("Click an action above to see its attributes.");
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
                String hay = (t.getName() + " " + (t.getDescription() == null ? "" : t.getDescription()))
                        .toLowerCase();
                if (!hay.contains(search)) continue;
            }
            // Patch B.15: VIP-only tasks are filtered by an explicit checkbox now (not auto-
            // hidden). Default-ticked for non-VIP so free users don't see content they can't run,
            // but it's a control they can untick to browse. (The server still withholds the actual
            // VIP bundle data from non-VIP accounts, so this is purely a browsing convenience.)
            if (t.isVipOnly() && libraryHideVipCheck != null && libraryHideVipCheck.isSelected())
                continue;
            String origin = t.getOrigin();
            if ("Built by me".equals(filter) && !"user".equals(origin)) continue;
            if ("Imported".equals(filter) && !"imported".equals(origin)) continue;
            if ("Default".equals(filter) && !"default".equals(origin)) continue;
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
        JFileChooser chooser = new JFileChooser();
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

        JFileChooser chooser = new JFileChooser();
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
        showToast(ok ? "Exported " + selected.getName() : "Export failed", anchor, ok);
    }

    /** Imports a task from a user-chosen .json file into the library. */
    private void importTaskFromFile(JComponent anchor) {
        JFileChooser chooser = new JFileChooser();
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

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(createSubtitle("Skill Tracker"), BorderLayout.WEST);
        JPanel headerCtl = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        headerCtl.setOpaque(false);
        headerCtl.add(rememberTrackersCheck);
        headerCtl.add(resetTrackers);
        header.add(headerCtl, BorderLayout.EAST);

        panelSkillTracker.add(header, BorderLayout.NORTH);
        panelSkillTracker.add(Theme.thinScrollbars(new JScrollPane(gridSkills)), BorderLayout.CENTER);
        totalLevelLabelP2P.setForeground(TEXT_MAIN);

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
        // user-facing to "Checks".
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        panel.setBackground(BG_BASE);

        JLabel blurb = new JLabel("<html>Checks run automatically between actions - independent "
                + "ones run together (run away <i>and</i> eat). When two checks want the same "
                + "thing (both freeing inventory, both walking), the one <b>higher in the list "
                + "wins</b> that cycle and the other waits - <b>drag to reorder</b> and set that "
                + "priority. Per-action checks live on the <b>Checks\u2026</b> button in the Task "
                + "Builder.</html>");
        blurb.setForeground(TEXT_DIM);
        blurb.setBorder(new EmptyBorder(0, 0, 8, 0));

        checksEditor = new main.menu.components.TriggerEditor(
                globalTriggers, false, this::pickResponseAction);
        checksEditor.setOrderChangedHook(() -> saveAll(false));   // B.8: persist new priority
        main.menu.components.TriggerEditor editor = checksEditor;

        panel.add(createSubtitle("Checks"), BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setOpaque(false);
        body.add(blurb, BorderLayout.NORTH);
        body.add(editor, BorderLayout.CENTER);
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

    private JToggleButton iconToggle(Icon icon, String tooltip) {
        JToggleButton b = new JToggleButton(icon);
        b.setToolTipText(tooltip);
        b.setPreferredSize(new Dimension(34, 30));
        b.setFocusPainted(false);
        b.setMargin(new Insets(2, 2, 2, 2));
        return b;
    }

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

    /**
     * The default local market folder (Patch B.17): lives NEXT TO the running DreamMan build -
     * the script jar's folder when deployed, the project output folder when run from an IDE -
     * so it's portable with the project and never depends on a user-home path that may not
     * exist. Falls back to {@code <home>/DreamMan/market} only if the code location can't be
     * resolved or isn't writable. Always created, so no file chooser is ever forced on you.
     */
    private java.io.File defaultMarketFolder() {
        try {
            java.net.URL src = getClass().getProtectionDomain().getCodeSource().getLocation();
            java.io.File loc = new java.io.File(src.toURI());
            // a jar -> use its parent dir; a classes dir -> use the dir itself
            java.io.File base = loc.isFile() ? loc.getParentFile() : loc;
            if (base != null) {
                java.io.File f = new java.io.File(base, "DreamMan-market");
                if (f.isDirectory() || f.mkdirs()) return f;
            }
        } catch (Throwable ignored) { /* unusual classloader - fall through */ }
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

        btnFolderSource.addActionListener(e -> useFolderSource(false));
        btnServerSource.addActionListener(e -> useServerSource());
        // right-click to change folder path / server url
        btnFolderSource.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent me) {
                if (SwingUtilities.isRightMouseButton(me)) useFolderSource(true);
            }
        });
        btnServerSource.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent me) {
                if (SwingUtilities.isRightMouseButton(me)) changeServerUrl();
            }
        });

        // ── filter + sort as icon toggles/menus with tooltips ──
        marketSortCombo = new JComboBox<>(new String[]{"Top rated", "Newest", "Most downloaded", "A-Z"});
        marketSortCombo.addActionListener(e -> refilterMarket());
        JButton btnSortIcon = iconButton(main.menu.components.UIIcons.sort(20, ic),
                "Sort the list", null);
        btnSortIcon.addActionListener(e -> {
            JPopupMenu m = new JPopupMenu();
            for (String opt : new String[]{"Top rated", "Newest", "Most downloaded", "A-Z"}) {
                JMenuItem mi = new JMenuItem(opt);
                mi.addActionListener(a -> { marketSortCombo.setSelectedItem(opt); refilterMarket(); });
                m.add(mi);
            }
            m.show(btnSortIcon, 0, btnSortIcon.getHeight());
        });

        // ── search field ──
        marketSearchField = new JTextField();
        marketSearchField.setToolTipText("Search by name, author, description or tag");
        marketSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { refilterMarket(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { refilterMarket(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { refilterMarket(); }
        });

        // ── top-right action icons: refresh + import ──
        JButton btnRefresh = iconButton(main.menu.components.UIIcons.refresh(20, ic),
                "Refresh the list", this::reloadMarket);
        JButton btnImport = iconButton(main.menu.components.UIIcons.importIcon(20, ic),
                "Import the selected script into your library", () -> importFromMarket(listMarket));

        // header row: [folder|server] [sort] [search .......] [refresh] [import]
        JPanel head = new JPanel(new BorderLayout(6, 4));
        head.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setOpaque(false);
        left.add(btnFolderSource);
        left.add(btnServerSource);
        left.add(Box.createHorizontalStrut(8));
        left.add(btnSortIcon);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.setOpaque(false);
        right.add(btnRefresh);
        right.add(btnImport);

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

        head.add(topRow, BorderLayout.NORTH);
        head.add(lblMarketSource, BorderLayout.SOUTH);

        // ── the listings, with per-row stars + publish ──
        marketCardRenderer = new MarketCardRenderer();
        listMarket.setCellRenderer(marketCardRenderer);
        listMarket.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listMarket.setBackground(BG_BASE);
        // clicking a row's star strip rates it; the renderer itself does the hit-testing so the
        // clickable zones always line up with the drawn stars (Patch B.17)
        listMarket.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent me) {
                handleMarketRowClick(me);
            }
            @Override public void mouseExited(MouseEvent me) {
                setMarketHover(-1, -1);
            }
        });
        listMarket.addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent me) {
                int idx = listMarket.locationToIndex(me.getPoint());
                int star = -1;
                if (idx >= 0) {
                    Rectangle b = listMarket.getCellBounds(idx, idx);
                    if (b != null && b.contains(me.getPoint())) {
                        marketCardRenderer.getListCellRendererComponent(
                                listMarket, modelMarket.getElementAt(idx), idx, false, false);
                        star = marketCardRenderer.starAt(
                                new Point(me.getX() - b.x, me.getY() - b.y), b);
                    } else idx = -1;
                }
                setMarketHover(idx, star);
                listMarket.setCursor(Cursor.getPredefinedCursor(
                        star > 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        });
        JScrollPane scroll = Theme.thinScrollbars(new JScrollPane(listMarket));
        scroll.setBorder(BorderFactory.createLineBorder(COLOR_BORDER_DIM));

        // ── bottom: a single "publish current item" control (one script/task/preset at a time) ──
        JButton btnPublish = createButton("Publish selected item\u2026", new Color(25, 60, 75), null);
        btnPublish.setToolTipText("Publish ONE thing at a time: the selected task, or the whole queue");
        btnPublish.addActionListener(e -> publishOneItem(btnPublish));
        JButton btnRemove = createButton("Remove mine", COLOR_BUTTON_RED, null);
        btnRemove.addActionListener(e -> removeFromMarket(btnRemove));
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bottom.setOpaque(false);
        bottom.add(btnPublish);
        bottom.add(btnRemove);

        panel.add(createSubtitle("Market"), BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setOpaque(false);
        body.add(head, BorderLayout.NORTH);
        body.add(scroll, BorderLayout.CENTER);
        body.add(bottom, BorderLayout.SOUTH);
        panel.add(body, BorderLayout.CENTER);

        reloadMarket();
        return panel;
    }

    /** Pulls everything from the repository (off the EDT - a shared folder can be slow). */
    private void reloadMarket() {
        if (marketRepo == null) return;
        // Patch B.12: a folder market is entirely local - nothing is sent, so nothing is asked.
        // A SERVER market is a network call, and that needs consent first.
        if (marketRepo instanceof main.market.HttpRepository
                && !main.privacy.Consent.has(main.privacy.Consent.MARKET_BROWSE)) {
            lblMarketSource.setText("Not connected - you haven't agreed to send anything yet.");
            modelMarket.clear();
            marketAll.clear();
            return;
        }
        final main.market.ScriptRepository repo = marketRepo;
        setStatus("Loading market...");
        new Thread(() -> {
            List<main.market.ScriptListing> found = repo.list();
            SwingUtilities.invokeLater(() -> {
                marketAll.clear();
                marketAll.addAll(found);
                if (found.isEmpty() && repo instanceof main.market.HttpRepository) {
                    // Patch B.17: an auto-connected server that returns nothing is either empty
                    // or unreachable - say so instead of a bare count, and point at the offline
                    // fallback that always works.
                    lblMarketSource.setText(repo.describe()
                            + "  \u00b7  nothing loaded (offline or empty server)"
                            + "  \u00b7  the folder icon switches to the local market");
                } else {
                    lblMarketSource.setText(repo.describe() + "  \u00b7  " + found.size()
                            + (found.size() == 1 ? " script" : " scripts"));
                }
                refilterMarket();
                setStatus(found.isEmpty() ? "Market is empty" : "Market loaded");
            });
        }, "DreamMan-Market").start();
    }

    /** Applies the search box + sort to the listing view. */
    private void refilterMarket() {
        String q = marketSearchField == null ? "" : marketSearchField.getText().trim().toLowerCase();
        String sort = marketSortCombo == null ? "Top rated" : (String) marketSortCombo.getSelectedItem();

        List<main.market.ScriptListing> view = new ArrayList<>();
        for (main.market.ScriptListing l : marketAll) {
            if (l == null) continue;
            if (!q.isEmpty()) {
                String hay = (l.name + " " + l.author + " " + l.description + " "
                        + String.join(" ", l.tags == null ? List.<String>of() : l.tags))
                        .toLowerCase();
                if (!hay.contains(q)) continue;
            }
            view.add(l);
        }

        if ("Newest".equals(sort))
            view.sort(Comparator.comparingLong((main.market.ScriptListing l) -> l.publishedAt).reversed());
        else if ("Most downloaded".equals(sort))
            view.sort(Comparator.comparingInt((main.market.ScriptListing l) -> l.downloads).reversed());
        else if ("A-Z".equals(sort))
            view.sort(Comparator.comparing(l -> l.name, String.CASE_INSENSITIVE_ORDER));
        else   // Top rated: average, then how many people rated it (so 1x5* doesn't beat 50x4.9*)
            view.sort(Comparator
                    .comparingDouble((main.market.ScriptListing l) -> l.avgRating)
                    .thenComparingInt(l -> l.ratingCount)
                    .reversed());

        modelMarket.clear();
        for (main.market.ScriptListing l : view) modelMarket.addElement(l);
    }

    private JTextArea privacyStatusArea;

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

        privacyStatusArea = new JTextArea();
        privacyStatusArea.setEditable(false);
        privacyStatusArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        privacyStatusArea.setBackground(new Color(15, 15, 15));
        privacyStatusArea.setForeground(TEXT_MAIN);
        privacyStatusArea.setBorder(new EmptyBorder(10, 10, 10, 10));

        JButton btnChange = createButton("Change what I've agreed to\u2026");
        btnChange.addActionListener(e -> { askConsent(null); refreshPrivacy(); });

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

        JPanel btns = new JPanel(new GridLayout(1, 5, 6, 0));
        btns.setOpaque(false);
        btns.add(btnChange);
        btns.add(btnWithdraw);
        btns.add(btnExport);
        btns.add(btnWipeLocal);
        btns.add(btnDeleteServer);

        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setOpaque(false);
        body.add(blurb, BorderLayout.NORTH);
        body.add(Theme.thinScrollbars(new JScrollPane(privacyStatusArea)), BorderLayout.CENTER);
        body.add(btns, BorderLayout.SOUTH);

        panel.add(createSubtitle("Privacy"), BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);

        refreshPrivacy();
        return panel;
    }

    /** Shows exactly what is (and isn't) being shared. */
    private void refreshPrivacy() {
        if (privacyStatusArea == null) return;
        StringBuilder sb = new StringBuilder();

        sb.append("WHAT YOU'VE AGREED TO\n");
        sb.append("─────────────────────────────────────────────────────────────\n");
        String[][] rows = {
                {main.privacy.Consent.MARKET_BROWSE,        "Browse & download scripts"},
                {main.privacy.Consent.MARKET_PUBLISH,       "Publish my scripts"},
                {main.privacy.Consent.CLOUD_SYNC,           "Cloud-sync my setup"},
                {main.privacy.Consent.LINK_CHARACTER_NAME,  "Send REAL character names"},
        };
        for (String[] r : rows) {
            boolean on = main.privacy.Consent.has(r[0]);
            sb.append(String.format("  [%s]  %-32s %s%n",
                    on ? "\u2713" : " ", r[1],
                    on ? "since " + new java.text.SimpleDateFormat("yyyy-MM-dd")
                            .format(new Date(main.privacy.Consent.grantedAt(r[0])))
                       : "off"));
        }
        if (!main.privacy.Consent.anyNetworkConsent())
            sb.append("\n  DreamMan is currently OFFLINE. No data is being sent anywhere.\n");

        sb.append("\n\nWHAT NEVER LEAVES THIS PC (regardless of the above)\n");
        sb.append("─────────────────────────────────────────────────────────────\n");
        sb.append("  \u2022 Your bank PIN - held in memory only, never written to disk,\n");
        sb.append("    never part of a profile, never uploaded. Checked before every upload.\n");
        sb.append("  \u2022 Your OSRS character names - mapped to local labels; only the\n");
        sb.append("    label is ever sent (unless you explicitly turn that option on).\n");
        sb.append("  \u2022 status.json (your live position/task) - written locally for your\n");
        sb.append("    own dashboard. Never uploaded.\n");

        sb.append("\n\nYOUR CHARACTERS, AS THE SERVER WOULD SEE THEM\n");
        sb.append("─────────────────────────────────────────────────────────────\n");
        Map<String, String> map = main.privacy.CharacterMap.all();
        if (map.isEmpty()) {
            sb.append("  (none yet)\n");
        } else {
            boolean realNames = main.privacy.Consent.has(main.privacy.Consent.LINK_CHARACTER_NAME);
            for (Map.Entry<String, String> e : map.entrySet())
                sb.append(String.format("  %-20s \u2192 sent as \"%s\"%n",
                        e.getKey(), realNames ? e.getKey() : e.getValue()));
            if (!realNames)
                sb.append("\n  The names on the left stay on this PC. Only the labels on the\n"
                        + "  right are ever sent - so even a breached server holds no\n"
                        + "  record linking a real game account to botting.\n");
            else
                sb.append("\n  \u26a0 You have turned ON sending real names. The server will hold a\n"
                        + "  permanent record tying these accounts to botting. You can turn this\n"
                        + "  off above, but anything already sent is already there.\n");
        }

        sb.append("\n\nYOUR ACCOUNT\n");
        sb.append("─────────────────────────────────────────────────────────────\n");
        sb.append(main.market.ServerAccount.isLoggedIn()
                ? "  Signed in as " + main.market.ServerAccount.username() + "\n"
                : "  Not signed in to any server.\n");
        sb.append("  Local files: ").append(LocalStore.getRoot().getAbsolutePath()).append("\n");

        privacyStatusArea.setText(sb.toString());
        privacyStatusArea.setCaretPosition(0);
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

    /** Card renderer for a market listing: name, author, stars, downloads, what it does. */
    private class MarketCardRenderer extends JPanel implements ListCellRenderer<main.market.ScriptListing> {
        private final JLabel name = new JLabel();
        private final JLabel meta = new JLabel();
        /** Patch B.17: vector stars - font glyphs (\u2605) rendered as boxes on some systems. */
        final main.menu.components.StarRating stars = new main.menu.components.StarRating(15, false);
        private final JLabel starText = new JLabel();
        private final JLabel dl = new JLabel("", SwingConstants.RIGHT);
        /** The row the mouse is over and the star it's over, driven by the list's listeners. */
        int hoverRow = -1, hoverStar = -1;

        MarketCardRenderer() {
            setLayout(new BorderLayout(10, 0));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_BORDER_DIM),
                    new EmptyBorder(9, 12, 9, 12)));
            name.setFont(new Font("Segoe UI", Font.BOLD, 14));
            meta.setFont(new Font("Consolas", Font.PLAIN, 11));
            starText.setFont(new Font("Segoe UI", Font.BOLD, 13));
            dl.setFont(new Font("Consolas", Font.PLAIN, 11));
            JPanel left = new JPanel(new GridLayout(2, 1));
            left.setOpaque(false);
            left.add(name);
            left.add(meta);
            // top-right: the clickable star strip + "4.8 (12)" text beside it
            JPanel starRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            starRow.setOpaque(false);
            starRow.add(stars);
            starRow.add(starText);
            JPanel right = new JPanel(new GridLayout(2, 1));
            right.setOpaque(false);
            right.add(starRow);
            right.add(dl);
            add(left, BorderLayout.CENTER);
            add(right, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends main.market.ScriptListing> list,
                main.market.ScriptListing l, int index, boolean selected, boolean focus) {
            if (l == null) return this;
            // Patch B.16: VIP-gated listings show a marker and no bundle preview for non-VIP.
            // (Plain text, not the padlock emoji - that also fell back to a missing-glyph box.)
            boolean locked = l.vipOnly && (l.bundle == null);
            name.setText((l.vipOnly ? "[VIP] " : "") + l.name + "  v" + l.version);
            String tags = (l.tags == null || l.tags.isEmpty()) ? "" : "  \u00b7  " + String.join(", ", l.tags);
            meta.setText("by " + l.author + "  \u00b7  "
                    + (locked ? "VIP only \u2014 upgrade to view" : l.summarise()) + tags);

            stars.setValues(l.avgRating, l.myRating);
            stars.setHoverPreview(index == hoverRow ? hoverStar : -1);
            if (l.ratingCount > 0) {
                starText.setText(String.format("%.1f (%d)", l.avgRating, l.ratingCount));
                starText.setForeground(Theme.ACCENT);
            } else {
                starText.setText("rate \u2192");
                starText.setForeground(TEXT_DIM);
            }
            dl.setText(l.downloads + (l.downloads == 1 ? " download" : " downloads")
                    + (l.myRating > 0 ? "   \u00b7 you: " + l.myRating + "/5" : ""));
            dl.setForeground(TEXT_DIM);

            setBackground(selected ? new Color(46, 42, 30) : Theme.SURFACE_1);
            name.setForeground(selected ? Theme.ACCENT : TEXT_MAIN);
            meta.setForeground(TEXT_DIM);
            setOpaque(true);
            return this;
        }

        /**
         * Precise star hit-test (Patch B.17). Lays this renderer out at the actual cell size and
         * asks which star (1-5) sits under the point - so the click zones ARE the drawn shapes,
         * instead of the old hand-tuned "right 130px / 22px each" guess that drifted whenever
         * fonts, padding or the scrollbar changed.
         *
         * @param p point in CELL coordinates. @return star 1..5, or -1 when not on the strip.
         */
        int starAt(Point p, Rectangle cellBounds) {
            setBounds(0, 0, cellBounds.width, cellBounds.height);
            layoutTree(this);
            Component deepest = SwingUtilities.getDeepestComponentAt(this, p.x, p.y);
            if (deepest != stars) return -1;
            Point sp = SwingUtilities.convertPoint(this, p, stars);
            return stars.starIndexAt(sp.x);
        }

        private void layoutTree(Component c) {
            c.doLayout();
            if (c instanceof Container)
                for (Component k : ((Container) c).getComponents()) layoutTree(k);
        }
    }

    /** Import a listing into the Task Library, after showing exactly what it will add. */
    private void importFromMarket(JComponent anchor) {
        main.market.ScriptListing l = listMarket.getSelectedValue();
        if (l == null) { showToast("Pick a script first", anchor, false); return; }
        if (l.bundle == null || l.bundle.tasks == null || l.bundle.tasks.isEmpty()) {
            showToast("That listing has no tasks", anchor, false);
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
            sb.append("<br>It also brings the author's always-on <b>checks</b>.<br>");
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
        libraryAdd(merged);
        refreshTaskLibrary();
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
        return merged;
    }

    /** Rate the selected script 1-5. One rating per install; re-rating replaces it. */
    private void rateSelected(JComponent anchor) {
        main.market.ScriptListing l = listMarket.getSelectedValue();
        if (l == null) { showToast("Pick a script first", anchor, false); return; }
        if (marketRepo instanceof main.market.HttpRepository
                && !ensureConsent(main.privacy.Consent.MARKET_BROWSE)) {
            showToast("Nothing was sent", anchor, false);
            return;
        }

        Object[] options = {"1 star", "2 stars", "3 stars", "4 stars", "5 stars"};
        int choice = JOptionPane.showOptionDialog(this,
                "How would you rate \"" + l.name + "\"?"
                        + (l.myRating > 0 ? "\n(You rated it " + l.myRating + " before - this replaces it.)" : ""),
                "Rate script", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                options, options[Math.max(0, l.myRating - 1)]);
        if (choice < 0) return;

        try {
            marketRepo.rate(l.id, choice + 1);
            reloadMarket();
            showToast("Rated " + (choice + 1) + " stars", anchor, true);
        } catch (Exception ex) {
            showToast("Couldn't rate: " + ex.getMessage(), anchor, false);
        }
    }

    /** Publish the current queue to the market. */
    /**
     * Publishes ONE item (Patch B.16): a single library task when {@code single} is non-null, or
     * the whole queue when it's null. Builds a one-task bundle for the single case so a listing is
     * always self-contained.
     */
    private void publishItem(Task single, JComponent anchor) {
        boolean queueMode = (single == null);
        if (queueMode && modelTaskList.isEmpty()) {
            showToast("Your queue is empty - build something first", anchor, false);
            return;
        }
        if (marketRepo instanceof main.market.HttpRepository
                && !ensureConsent(main.privacy.Consent.MARKET_PUBLISH)) {
            showToast("Nothing was sent", anchor, false);
            return;
        }

        JTextField txtName = new JTextField(queueMode ? "My Script" : single.getName(), 22);
        String who = safePlayerName();
        JTextField txtAuthor = new JTextField(who == null || who.isEmpty() ? "Anonymous" : who, 22);
        JSpinner spVersion = new JSpinner(new SpinnerNumberModel(1.0, 0.1, 999.0, 0.1));
        JTextField txtTags = new JTextField("", 22);
        txtTags.setToolTipText("Comma-separated, e.g. combat, ironman, f2p");
        JTextArea txtDesc = new JTextArea(3, 22);
        txtDesc.setLineWrap(true);
        txtDesc.setWrapStyleWord(true);
        JCheckBox chkChecks = new JCheckBox("Include my always-on checks (" + globalTriggers.size() + ")",
                !globalTriggers.isEmpty());
        chkChecks.setOpaque(false);
        chkChecks.setForeground(TEXT_MAIN);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.gridy = 0;
        form.add(styledLabel("Name:"), c);        c.gridx = 1; form.add(txtName, c);
        c.gridx = 0; c.gridy++;
        form.add(styledLabel("Author:"), c);      c.gridx = 1; form.add(txtAuthor, c);
        c.gridx = 0; c.gridy++;
        form.add(styledLabel("Version:"), c);     c.gridx = 1; form.add(spVersion, c);
        c.gridx = 0; c.gridy++;
        form.add(styledLabel("Tags:"), c);        c.gridx = 1; form.add(txtTags, c);
        c.gridx = 0; c.gridy++;
        form.add(styledLabel("Description:"), c); c.gridx = 1; form.add(new JScrollPane(txtDesc), c);
        c.gridx = 0; c.gridy++; c.gridwidth = 2;
        form.add(chkChecks, c);
        c.gridy++;
        JLabel note = new JLabel("Publishes to: " + marketRepo.describe());
        note.setForeground(TEXT_DIM);
        form.add(note, c);

        int r = JOptionPane.showConfirmDialog(this, form, "Publish to the market",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;

        main.market.ScriptListing listing = new main.market.ScriptListing();
        listing.name = txtName.getText().trim();
        listing.author = txtAuthor.getText().trim();
        listing.version = ((Number) spVersion.getValue()).doubleValue();
        listing.description = txtDesc.getText().trim();
        listing.tags = new ArrayList<>();
        for (String t : txtTags.getText().split(","))
            if (!t.trim().isEmpty()) listing.tags.add(t.trim());

        main.data.store.ScriptBundle b = new main.data.store.ScriptBundle();
        b.name = listing.name;
        b.author = listing.author;
        b.version = listing.version;
        b.description = listing.description;
        b.loops = queueMode ? queueLoopTarget : 1;
        if (queueMode) {
            b.tasks = ProfileCodec.tasksToData(modelTaskList);
        } else {
            List<Task> one = new ArrayList<>();
            one.add(single);
            b.tasks = ProfileCodec.tasksToData(one);
        }
        if (chkChecks.isSelected())
            b.globalTriggers = main.watchers.TriggerCodec.toJson(new ArrayList<>(globalTriggers));
        listing.bundle = b;

        try {
            marketRepo.publish(listing);
            reloadMarket();
            showToast("Published \"" + listing.name + "\"", anchor, true);
        } catch (Exception ex) {
            showToast("Publish failed: " + ex.getMessage(), anchor, false);
        }
    }

    /** Remove a listing (a shared folder has no ownership - hence the warning). */
    private void removeFromMarket(JComponent anchor) {
        main.market.ScriptListing l = listMarket.getSelectedValue();
        if (l == null) { showToast("Pick a script first", anchor, false); return; }
        int ok = JOptionPane.showConfirmDialog(this,
                "Remove \"" + l.name + "\" from the market?\n\n"
                        + "A shared folder has no logins, so this deletes it for everyone.",
                "Remove listing", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;
        try {
            marketRepo.remove(l.id);
            reloadMarket();
            showToast("Removed", anchor, true);
        } catch (Exception ex) {
            showToast("Couldn't remove: " + ex.getMessage(), anchor, false);
        }
    }

    /** Point the market at a different folder (or a server, when one exists). */
    // ── Patch B.16: source switching via the folder/server icons ──

    /** Switch to the local-folder source. If forcePick (right-click) or no folder chosen, ask. */
    private void useFolderSource(boolean forcePick) {
        // Patch B.17: the default folder is created for you, next to the project files. The
        // (painfully slow) file chooser only appears on an explicit right-click, or if the
        // folder genuinely can't be created (e.g. a read-only install location).
        if (!forcePick && (marketFolder == null || !marketFolder.isDirectory())) {
            marketFolder = defaultMarketFolder();
        }
        if (forcePick || marketFolder == null || !marketFolder.isDirectory()) {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setDialogTitle("Pick the shared market folder");
            if (marketFolder != null) fc.setSelectedFile(marketFolder);
            if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
                // user cancelled - keep whatever source we had, re-sync the toggle
                if (btnServerSource != null && marketRepo instanceof main.market.HttpRepository)
                    btnServerSource.setSelected(true);
                return;
            }
            marketFolder = fc.getSelectedFile();
        }
        marketRepo = new main.market.FolderRepository(marketFolder);
        if (btnFolderSource != null) btnFolderSource.setSelected(true);
        reloadMarket();
        showToast("Market: local folder", btnFolderSource, true);
    }

    /** Switch to the server source (auto-linked; URL only changeable by admins via right-click). */
    private void useServerSource() {
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

    /** The one renderer instance for the market list - also does the star hit-testing (B.17). */
    private MarketCardRenderer marketCardRenderer;
    /** The one renderer instance for the task library - hosts the default-task star (v1.30). */
    private LibraryCardRenderer libraryCardRenderer;

    /** Updates the renderer's hover row/star and repaints only when it actually changed. */
    private void setMarketHover(int row, int star) {
        if (marketCardRenderer == null) return;
        if (marketCardRenderer.hoverRow == row && marketCardRenderer.hoverStar == star) return;
        marketCardRenderer.hoverRow = row;
        marketCardRenderer.hoverStar = star;
        listMarket.repaint();
    }

    /**
     * Click handling on a market row: clicking a star rates the script (B.16, fixed B.17).
     * The renderer is laid out at the real cell size and asked which star was hit, so the
     * clickable zones are exactly the star shapes the user sees.
     */
    private void handleMarketRowClick(MouseEvent me) {
        int idx = listMarket.locationToIndex(me.getPoint());
        if (idx < 0) return;
        Rectangle bounds = listMarket.getCellBounds(idx, idx);
        if (bounds == null || !bounds.contains(me.getPoint())) return;
        listMarket.setSelectedIndex(idx);
        if (marketCardRenderer == null) return;
        main.market.ScriptListing l = modelMarket.getElementAt(idx);
        // configure the renderer for THIS row, then hit-test in cell coordinates
        marketCardRenderer.getListCellRendererComponent(listMarket, l, idx, false, false);
        int star = marketCardRenderer.starAt(
                new Point(me.getX() - bounds.x, me.getY() - bounds.y), bounds);
        if (star >= 1 && star <= 5) rateListing(l, star);
    }

    /** Submit a rating for a listing (Patch B.16 - inline on the row). */
    private void rateListing(main.market.ScriptListing l, int stars) {
        if (l == null) return;
        if (marketRepo instanceof main.market.HttpRepository
                && !ensureConsent(main.privacy.Consent.MARKET_BROWSE)) return;
        new Thread(() -> {
            try {
                marketRepo.rate(l.id, stars);
                SwingUtilities.invokeLater(() -> { reloadMarket(); showToast("Rated " + stars + "\u2605", listMarket, true); });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> showToast("Couldn't rate: " + ex.getMessage(), listMarket, false));
            }
        }, "DreamMan-Rate").start();
    }

    /** Publish ONE item at a time (Patch B.16): the selected library task, or the whole queue. */
    private void publishOneItem(JComponent anchor) {
        // offer the choice: selected task, or the current queue
        Task selectedTask = listTaskLibrary != null ? listTaskLibrary.getSelectedValue() : null;
        List<String> opts = new ArrayList<>();
        if (selectedTask != null) opts.add("Selected task: " + selectedTask.getName());
        if (!modelTaskList.isEmpty()) opts.add("The current queue (" + modelTaskList.size() + " task(s))");
        if (opts.isEmpty()) { showToast("Select a library task or build a queue first", anchor, false); return; }

        String choice = (String) JOptionPane.showInputDialog(this,
                "Publish which item? (one at a time)", "Publish",
                JOptionPane.PLAIN_MESSAGE, null, opts.toArray(), opts.get(0));
        if (choice == null) return;

        boolean queueMode = choice.startsWith("The current queue");
        publishItem(queueMode ? null : selectedTask, anchor);
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
        return a == null ? null : a.copy();
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

    private JPanel createStatusTab() {
        JPanel panelStatus = new JPanel(new BorderLayout(15, 15));
        panelStatus.setBorder(new EmptyBorder(15, 15, 15, 15));
        panelStatus.setBackground(BG_BASE);

        // ── Patch B.17 overhaul ──────────────────────────────────────────────
        // One rule: everything about YOU - who's logged in, your DreamMan account, your bank
        // PIN, switching Jagex accounts - lives in the Player card. The old "Session" card
        // (PIN + switching, orphaned on the far side of the grid) is gone. The read-only
        // telemetry (Account / World / Game / Script) sits in a tidy 2x2 grid beside it.
        JPanel player = buildPlayerCard();

        JPanel account = createInfoCard("Account");
        addInfoRow(account, "Username", lblUsername);
        addInfoRow(account, "Nickname", lblNickname);
        addInfoRow(account, "Identifier", lblAcctId);
        addInfoRow(account, "Account status", lblAcctStatus);

        JPanel world = createInfoCard("World");
        addInfoRow(world, "World", lblWorld);
        addInfoRow(world, "Coordinates (x, y, z)", lblCoords);
        addInfoRow(world, "Game state", lblGameState);

        // Patch B.3: everything a remote dashboard would want, live. The same snapshot is
        // written to <home>/DreamMan/status.json every ~2s - a web UI can poll that file to
        // show account status, live tile coordinates (map replica), current task and progress.
        JPanel script = createInfoCard("Script");
        addInfoRow(script, "Current task", lblScriptTask);
        addInfoRow(script, "Activity", lblScriptActivity);
        addInfoRow(script, "Queue", lblScriptQueue);
        addInfoRow(script, "Loop", lblScriptLoop);
        addInfoRow(script, "Uptime", lblScriptUptime);
        addInfoRow(script, "Paused", lblScriptPaused);

        JPanel session = createInfoCard("Session");
        session.add(createSessionSummary());

        JPanel grid = new JPanel(new GridLayout(2, 2, 14, 14));
        grid.setOpaque(false);
        grid.add(account);
        grid.add(world);
        grid.add(script);
        grid.add(session);

        JPanel content = new JPanel(new BorderLayout(14, 14));
        content.setOpaque(false);
        player.setPreferredSize(new Dimension(340, 0));
        content.add(player, BorderLayout.WEST);
        content.add(grid, BorderLayout.CENTER);

        // ── v1.30: responsive layout ─────────────────────────────────────────
        // At full width: Player on the left, a 2x2 grid beside it. Squeezed (the client's
        // docked/minimised widths), the fixed columns used to CLIP the right-hand cards - now
        // the grid drops to one column below ~1000px and the Player card stacks on top below
        // ~760px, so everything stays readable and the scrollbar does the rest.
        java.awt.event.ComponentAdapter reflow = new java.awt.event.ComponentAdapter() {
            private int mode = -1;   // 0 wide · 1 medium · 2 narrow
            @Override public void componentResized(ComponentEvent e) {
                int w = content.getWidth();
                if (w <= 0) return;
                int want = w >= 1000 ? 0 : w >= 760 ? 1 : 2;
                if (want == mode) return;
                mode = want;
                content.remove(player);
                ((GridLayout) grid.getLayout()).setColumns(want == 0 ? 2 : 1);
                ((GridLayout) grid.getLayout()).setRows(0);
                if (want == 2) {
                    player.setPreferredSize(null);
                    content.add(player, BorderLayout.NORTH);
                } else {
                    player.setPreferredSize(new Dimension(340, 0));
                    content.add(player, BorderLayout.WEST);
                }
                content.revalidate();
                content.repaint();
            }
        };
        content.addComponentListener(reflow);

        // Patch B.16: Privacy folded into Status (all "this account" info). Cards on top, the
        // privacy panel below, scrollable so it fits.
        JPanel stacked = new JPanel(new BorderLayout(0, 14));
        stacked.setOpaque(false);
        stacked.add(content, BorderLayout.NORTH);
        JPanel privacySection = createPrivacyTab();
        privacySection.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, COLOR_BORDER_DIM),
                new EmptyBorder(12, 0, 0, 0)));
        stacked.add(privacySection, BorderLayout.CENTER);

        JScrollPane scroll = Theme.thinScrollbars(new JScrollPane(stacked));
        scroll.setBorder(null);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        panelStatus.add(createSubtitle("Status"),  BorderLayout.NORTH);
        panelStatus.add(scroll, BorderLayout.CENTER);
        return panelStatus;
    }

    /**
     * The Player card (Patch B.17): the one place for everything that is about the person at
     * the keyboard - character identity, the DreamMan account, this session's bank PIN, and
     * Jagex-account switching. These used to be split between here and a separate "Session"
     * card, which is exactly where nobody looked for them.
     */
    private JPanel buildPlayerCard() {
        JPanel player = createInfoCard("Player");

        addInfoRow(player, "Character name", lblCharName);
        addInfoRowWithIcon(player, "Membership", lblMemberText, lblMemberIcon);

        // ── DreamMan account ──
        player.add(playerSectionHeader("DreamMan account"));
        addInfoRow(player, "Tier", lblAccountTier);   // Patch B.14: Free/VIP/Admin
        player.add(buildAccountSwitcherRow());        // Patch B.15: sign-in + account switcher

        // ── Bank PIN (session only) ──
        player.add(playerSectionHeader("Bank PIN"));
        addInfoRow(player, "This session", lblBankPin);
        player.add(buildBankPinRow());
        JLabel pinNote = new JLabel("<html><i>Kept in memory only - never saved to disk.</i></html>");
        pinNote.setForeground(TEXT_DIM);
        pinNote.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        player.add(pinNote);

        // ── Switch Jagex account (DreamBot's Account Manager) ──
        player.add(playerSectionHeader("Switch account"));
        addInfoRow(player, "DreamBot accounts", lblAccountSupport);
        player.add(buildAccountSwitchRow());

        return player;
    }

    /** A small gold divider header inside the Player card. */
    private JComponent playerSectionHeader(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setForeground(Theme.ACCENT);
        l.setFont(new Font("Segoe UI", Font.BOLD, 11));
        l.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_BORDER_DIM),
                new EmptyBorder(10, 0, 3, 0)));
        return l;
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

        panelSettings.add(createSubtitle("Settings"), BorderLayout.NORTH);
        panelSettings.add(menuPanel, BorderLayout.WEST);
        panelSettings.add(settingGroup, BorderLayout.CENTER);

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
            this.name = o.name;
            this.description = o.description;
            this.status = o.status;
            this.repeat = o.repeat;
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

                Timer timer = new Timer(TOAST_DELAY, e -> {
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
        // duplicates). Shipped defaults come from /resources/default-tasks.json; the admin's
        // starred set from <root>/default-tasks.json. See DefaultTasks for the release flow.
        if (!embeddedScriptMode) {
            try {
                if (main.data.store.DefaultTasks.mergeInto(this, libraryAll) > 0)
                    refreshTaskLibrary();
            } catch (Throwable e) {
                Logger.log(Logger.LogType.WARN, "[DefaultTasks] merge failed: " + e);
            }
        }

        // Patch B.4: restore always-on watchers
        // Patch B.10: an exported script ships its own checks - don't overwrite them either.
        if (!embeddedScriptMode) {
            globalTriggers.clear();
            if (data != null && data.globalTriggers != null && !data.globalTriggers.isBlank())
                globalTriggers.addAll(main.watchers.TriggerCodec.fromJson(data.globalTriggers));
        }
        if (checksEditor != null) checksEditor.reload();   // B.5: the UI list must follow

        // Patch B.6: restore the remember-trackers preference and, if on, the tracked set
        if (data != null) {
            rememberTrackers = data.rememberTrackers;
            if (rememberTrackersCheck != null) rememberTrackersCheck.setSelected(rememberTrackers);
            if (rememberTrackers && data.trackedSkills != null) {
                Set<String> want = new HashSet<>(data.trackedSkills);
                for (SkillData sd : skillRegistry.values())
                    sd.setTracking(want.contains(sd.getSkill().name()));
                refreshTrackerList();   // the side detail-list follows the restored set
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
//            e.printStackTrace();
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
            e.printStackTrace();
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
            e.printStackTrace();
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
            e.printStackTrace();
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
                e.printStackTrace();

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

    /** Right-click menu for a queued task card: edit / duplicate / set repeat / remove. */
    private void showTaskContextMenu(MouseEvent e, int index) {
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

        JMenuItem runHere = new JMenuItem("Run from here");
        runHere.addActionListener(a -> { setCurrentExecutionIndex(index); resetLoopProgress(); listTaskList.repaint(); });

        JMenuItem remove = new JMenuItem("Remove");
        remove.addActionListener(a -> {
            modelTaskList.remove(index);
            refreshTaskListTab();
        });

        menu.add(edit);
        menu.add(dup);
        menu.add(repeat);
        menu.add(runHere);
        menu.addSeparator();
        menu.add(remove);
        menu.show(listTaskList, e.getX(), e.getY());
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

    private void styleHeaderLabel(JLabel l) {
        l.setForeground(TEXT_MAIN); l.setFont(new Font("Consolas", Font.BOLD, 15));
        l.setHorizontalAlignment(SwingConstants.RIGHT);
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
        try {
            return new ImageIcon(new ImageIcon(
                    Objects.requireNonNull(getClass()
                            .getResource("/resources/icons/misc/" + name + ".png")))
                    .getImage()
                    .getScaledInstance(18, 18, Image.SCALE_SMOOTH));
        } catch (Exception e) {
            return null;
        }
    }

    private ImageIcon loadSkillIcon(Skill skill) {
        try {
            return new ImageIcon(new ImageIcon(
                    Objects.requireNonNull(getClass()
                            .getResource("/resources/icons/skills/" + skill.name().toLowerCase() + "_icon.png")))
                    .getImage()
                    .getScaledInstance(26, 26, Image.SCALE_SMOOTH)
            );

        } catch (Exception e) {
            return new ImageIcon();
        }
    }

    private ImageIcon loadTabIcon(String name) {
        try {
            return new ImageIcon(new ImageIcon(
                    Objects.requireNonNull(getClass()
                            .getResource("/resources/icons/tabs/" + name + ".png")))
                    .getImage()
                    .getScaledInstance(16, 16, Image.SCALE_SMOOTH));
        } catch (Exception e) {
            return null;
        }
    }

    private ImageIcon loadStatusIcon(String name) {
        try {
            return new ImageIcon(new ImageIcon(
                    Objects.requireNonNull(getClass()
                            .getResource("/resources/icons/status/" + name + ".png")))
                    .getImage()
                    .getScaledInstance(16, 16, Image.SCALE_SMOOTH)
            );

        } catch (Exception ignored) {}

        return null;
    }

    private void updateUI() {
        SwingUtilities.invokeLater(() -> {
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

            ///  Update Live Tracker
            //  Update level total display
            totalLevelsGainedLabel.setText("▲ " + totalLevelsGained + " level(s) gained");
            totalLevelsGainedLabel.setForeground(new Color(0, 180, 0));
            totalLevelsGainedLabel.setFont(new Font("Consolas", Font.BOLD, 14));

            //  Update XP total display
            totalXpGainedLabel.setText("▲ " + totalXPGained + " xp gained");
            totalXpGainedLabel.setForeground(new Color(0, 180, 0));
            totalXpGainedLabel.setFont(new Font("Consolas", Font.BOLD, 14));

            //  Update F2P total level display
            totalLevelLabelF2P.setText("Total: " + f2pTotal);
            totalLevelLabelF2P.setIcon(loadStatusIcon("F2P_icon"));
            totalLevelLabelF2P.setIconTextGap(8);
            totalLevelLabelF2P.setHorizontalAlignment(SwingConstants.CENTER);

            //  Update P2P total level display
            totalLevelLabelP2P.setText("Total: " + p2pTotal);
            totalLevelLabelP2P.setIcon(loadStatusIcon("P2P_icon"));
            totalLevelLabelP2P.setIconTextGap(8);
            totalLevelLabelP2P.setHorizontalAlignment(SwingConstants.CENTER);

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
                chkDisableRendering = createSettingCheck("Disable Rendering", Client.isRenderingDisabled(), e -> {
                    Client.setRenderingDisabled(((JCheckBox)e.getSource()).isSelected());
                })
        );
    }

    private JPanel createScriptPanel() {
        return createSettingsGroup("Script",
                settingClientChkStartScriptOnLoad = createSettingCheck("Start Script on Load",
                        startScriptOnLoad, e ->
                                // fixed: was inverted ("= !isSelected()"), so ticking DISABLED the feature
                                startScriptOnLoad = settingClientChkStartScriptOnLoad.isSelected()
                ),

                settingClientChkExitOnStopWarning = createSettingCheck("Warn Before Exit",
                        exitOnStopWarning, e ->
                                // fixed: was inverted; label renamed to match what it actually does
                                exitOnStopWarning = settingClientChkExitOnStopWarning.isSelected()
                ),

                chkAutoSave = createSettingCheck("Auto Save", true, e -> {
                    // Check the ACTUAL checkmark state
                    boolean isChecked = ((JCheckBox)e.getSource()).isSelected();

                    if (isChecked) {
                        saveAll(); // Only trigger the heavy save when turned ON
                        if (saveTimer != null)
                            saveTimer.start();
                    } else {
                        if (saveTimer != null)
                            saveTimer.stop();
                        showToast("Auto-save Disabled", chkAutoSave, false);
                    }
                })
        );
    }

    ///  Define Display Settings sub-tab
    private JPanel createDisplayPanel() {
        return createSettingsGroup("Display",
                chkHideRoofs = createSettingCheck("Hide Roofs",
                        ClientSettings.areRoofsHidden(), e ->
                                ClientSettings.toggleRoofs(((JCheckBox)e.getSource()).isSelected())),

                chkTransparentSidePanel = createSettingCheck("Transparent side panel",
                        ClientSettings.isTransparentSidePanelEnabled(), e ->
                                ClientSettings.toggleTransparentSidePanel(((JCheckBox)e.getSource()).isSelected()))
        );
    }

    ///  Define Gameplay Settings sub-tab
    private JPanel createGameplayPanel() {
        return createSettingsGroup("Gameplay",
                chkDataOrbs = createSettingCheck("Show data orbs",
                        ClientSettings.areDataOrbsEnabled(), e ->
                                ClientSettings.toggleDataOrbs(((JCheckBox)e.getSource()).isSelected())
                ),

                chkAmmoPickingBehaviour = createSettingCheck("Ammo-picking behaviour",
                        ClientSettings.isAmmoAutoEquipping(), e ->
                                ClientSettings.toggleAmmoAutoEquipping(((JCheckBox)e.getSource()).isSelected())
                )
        );
    }

    // (The old "Interfaces" sub-tab was removed - it only contained a duplicate of the
    //  "Show data orbs" checkbox, and its second assignment to chkDataOrbs corrupted the
    //  Gameplay tab's save/load behaviour.)

    ///  Define Audio Settings sub-tab
    private JPanel createAudioPanel() {
        return createSettingsGroup("Audio",
                chkGameAudio = createSettingCheck("Game Audio", ClientSettings.isGameAudioOn(), e -> ClientSettings.toggleGameAudio(((JCheckBox)e.getSource()).isSelected()))
        );
    }

    ///  Define Chat Settings sub-tab
    private JPanel createChatPanel() {
        return createSettingsGroup("Chat",
                chkTransparentChatbox = createSettingCheck("Transparent chatbox",
                        ClientSettings.isTransparentChatboxEnabled(), e ->
                                ClientSettings.toggleTransparentChatbox(((JCheckBox)e.getSource()).isSelected())),

                chkClickThroughChatbox = createSettingCheck("Click through chatbox",
                        ClientSettings.isClickThroughChatboxEnabled(), e ->
                                ClientSettings.toggleClickThroughChatbox(((JCheckBox)e.getSource()).isSelected()))
        );
    }

    ///  Define Controls Settings sub-tab
    private JPanel createControlsPanel() {
        return createSettingsGroup("Controls",
                chkShiftClickDrop = createSettingCheck("Shift click drop",
                        ClientSettings.isShiftClickDroppingEnabled(), e ->
                                ClientSettings.toggleShiftClickDropping(((JCheckBox)e.getSource()).isSelected())),

                chkEscClosesInterface = createSettingCheck("Esc closes interface",
                        ClientSettings.isEscInterfaceClosingEnabled(), e ->
                                ClientSettings.toggleEscInterfaceClosing(((JCheckBox)e.getSource()).isSelected()))
        );
    }

    ///  Define Activities Settings sub-tab
    private JPanel createActivitiesPanel() {
        return createSettingsGroup("Activities",
                chkLevelUpInterface = createSettingCheck("Level-up interface",
                        ClientSettings.isLevelUpInterfaceEnabled(), e ->
                                ClientSettings.toggleLevelUpInterface(((JCheckBox)e.getSource()).isSelected()))
        );
    }

    ///  Define Warnings Settings sub-tab
    private JPanel createWarningsPanel() {
        return createSettingsGroup(
                "Warnings",
                chkLootNotifications = createSettingCheck("Loot notifications",
                        ClientSettings.areLootNotificationsEnabled(), e ->
                                ClientSettings.toggleLootNotifications(((JCheckBox)e.getSource()).isSelected()))
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

    private JPanel createSettingsGroup(String title, Component... comps) { JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10)); p.setBackground(BG_BASE); JPanel list = new JPanel(new GridLayout(0, 1, 5, 5)); list.setBackground(BG_BASE); JLabel header = new JLabel(title); header.setForeground(COLOR_BLOOD); header.setFont(new Font("Segoe UI", Font.BOLD, 24)); JPanel wrapper = new JPanel(new BorderLayout()); wrapper.setBackground(BG_BASE); wrapper.add(header, BorderLayout.NORTH); for (Component c : comps) list.add(c); wrapper.add(list, BorderLayout.CENTER); return wrapper; }

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

    private JPanel createSkillTile(SkillData data) {
        JPanel tile = new JPanel(new GridBagLayout());
        tile.setBackground(PANEL_SURFACE);
        tile.setBorder(new LineBorder(COLOR_BORDER_DIM));
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

            add(chipWrap, BorderLayout.WEST);
            add(textWrap, BorderLayout.CENTER);
            add(badge, BorderLayout.EAST);
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
            name.setText(task.isTimed()
                    ? "\u23F1 " + task.getName() + "   (every ~" + task.getTimerMinutes() + "m)"
                    : task.getName());
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

            setToolTipText(task.getDescription());
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

    private void handlePresetClick(int slot, ActionEvent e) {
        int actualIndex = (currentPresetPage * 4) + slot;
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
            modelPresets.set(actualIndex, new Preset(currentName, currentTasks, queueLoopTarget));
            // TODO decide whether or not to select the preset on save
            selectedPresetIndex = actualIndex; // Select it upon saving
            refreshPresetButtonLabels();
            this.showToast("Saved to " + currentName, presetButtons[slot], true);

            /// Normal click to load
        } else {
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
            i.printStackTrace();
        }
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