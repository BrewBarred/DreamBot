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
    final java.util.List<Task> libraryAll = new java.util.ArrayList<>();
    final DefaultListModel<Task> modelTaskLibrary = new DefaultListModel<>();
    private final JList<Task> listTaskLibrary = new JList<>(modelTaskLibrary);
    private JTextField librarySearchField;
    /** The Checks tab's editor - reloaded after profile loads so restored checks show (B.5). */
    private main.menu.components.TriggerEditor checksEditor;
    private JComboBox<String> libraryFilterCombo;

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
    public void librarySetAll(java.util.List<Task> tasks) {
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
    private java.util.Map<String, Integer> libraryUseCounts = new java.util.HashMap<>();
    private JLabel inspName, inspMeta, inspStatus;
    private JTextArea inspDesc, inspAttrs;
    // Patch B.4: always-on watchers - checked between every action while the player is safe.
    private final java.util.List<main.watchers.Trigger> globalTriggers =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    /** Always-on watchers (the "default background checks"); read by the engine each loop. */
    public java.util.List<main.watchers.Trigger> getGlobalTriggers() { return globalTriggers; }

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
        this.setIconImage(Objects.requireNonNull(loadStatusIcon("Hardcore_ironman")).getImage());
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
        mainTabs.addTab("Checks", loadTabIcon("settings_tab"), createWatchersTab());
        mainTabs.addTab("Loot Tracker", loadTabIcon("library_tab"), createLootTrackerTab());
        mainTabs.addTab("Status", loadTabIcon("status_tab"), createStatusTab());
        mainTabs.addTab("Settings", loadTabIcon("settings_tab"), createSettingsTab());
        // Patch B.3: the Developers Console is locked to YOU specifically. It unlocks only if
        // the logged-in character is in DEV_ACCOUNTS, OR <home>/DreamMan/dev.flag contains the
        // exact DEV_TOKEN. A normal user can't guess the token, and an empty flag file (which
        // anyone might try) no longer does anything - so a release build shows it to no one.
        if (isDeveloper())
            mainTabs.addTab("Developers Console", new DevelopersConsole(libraryPanel));

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
                queueLoopTarget = (int) loopQueueSpinner.getValue();
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
            loopQueueSpinner.setEnabled(!inf);
            queueLoopTarget = inf ? 0 : (int) loopQueueSpinner.getValue();
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
        java.awt.event.KeyAdapter qwSync = new java.awt.event.KeyAdapter() {
            @Override public void keyReleased(java.awt.event.KeyEvent e) {
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
    private final java.util.Map<String, String> overlayStats =
            java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>());

    /** Add/replace a custom row shown on the in-game overlay (e.g. "Logs", "125"). */
    public void putOverlayStat(String label, String value) { overlayStats.put(label, value); }
    /** Remove one custom overlay row. */
    public void removeOverlayStat(String label) { overlayStats.remove(label); }
    /** Clear all custom overlay rows. */
    public void clearOverlayStats() { overlayStats.clear(); }
    /** Live view of the custom overlay rows (synchronized). */
    public java.util.Map<String, String> getOverlayStats() { return overlayStats; }

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
        } else {
            // finished a full pass of the queue
            boolean infinite = (queueLoopTarget <= 0);
            if (infinite || queueLoopCurrent < queueLoopTarget) {
                queueLoopCurrent++;
                currentExecutionIndex = 0;
                Logger.log(Logger.LogType.DEBUG, "Queue loop -> pass " + queueLoopCurrent
                        + (infinite ? " (infinite)" : "/" + queueLoopTarget));
            } else {
                currentExecutionIndex = -1;
                queueLoopCurrent = 1;
                pause("Queue complete!");
            }
        }

        SwingUtilities.invokeLater(() -> {
            listTaskList.repaint();
            updateQueueProgress();
        });
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
        listTaskList.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int i = listTaskList.locationToIndex(e.getPoint());
                if (i >= 0 && !listTaskList.getCellBounds(i, i).contains(e.getPoint())) i = -1;
                if (i != hoveredTaskIndex) { hoveredTaskIndex = i; listTaskList.repaint(); }
            }
        });

        ///  Add all buttons
        southButtons.add(btnTaskListSave);
        southButtons.add(btnTaskListDuplicate);
        southButtons.add(btnTaskListRemove);
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
        listTaskLibrary.setCellRenderer(new LibraryCardRenderer());
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

        JPanel listSide = new JPanel(new BorderLayout(0, 6));
        listSide.setOpaque(false);
        JPanel header = new JPanel(new BorderLayout(6, 4));
        header.setOpaque(false);
        JLabel lblSearch = new JLabel("\uD83D\uDD0D ");
        lblSearch.setForeground(TEXT_DIM);
        JPanel searchRow = new JPanel(new BorderLayout(4, 0));
        searchRow.setOpaque(false);
        searchRow.add(lblSearch, BorderLayout.WEST);
        searchRow.add(librarySearchField, BorderLayout.CENTER);
        JPanel comboRow = new JPanel(new GridLayout(1, 2, 6, 0));
        comboRow.setOpaque(false);
        comboRow.add(libraryFilterCombo);
        comboRow.add(librarySortCombo);
        header.add(searchRow, BorderLayout.NORTH);
        header.add(comboRow, BorderLayout.SOUTH);
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
            add(left, BorderLayout.CENTER);
            add(right, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Task> list, Task t,
                int index, boolean selected, boolean focus) {
            if (t == null) return this;
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
                    ? new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date(t.getCreatedAt()))
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
                ? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(t.getCreatedAt()))
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
        java.util.Map<String, String> attrs = a.serialize();
        if (attrs != null)
            for (java.util.Map.Entry<String, String> e : attrs.entrySet())
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
        java.util.Map<String, String> m = n.serialize();
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
        java.util.Map<String, Integer> byId = new java.util.HashMap<>();
        java.util.Map<String, String> nameToId = new java.util.HashMap<>();
        for (Task t : libraryAll)
            if (t != null) nameToId.put(t.getName().toLowerCase(), t.getId());
        java.util.List<Task> everywhere = new java.util.ArrayList<>();
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
                java.util.Map<String, String> m = a.serialize();
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

        java.util.List<Task> view = new java.util.ArrayList<>();
        for (Task t : libraryAll) {
            if (t == null) continue;
            if (!search.isEmpty()) {
                String hay = (t.getName() + " " + (t.getDescription() == null ? "" : t.getDescription()))
                        .toLowerCase();
                if (!hay.contains(search)) continue;
            }
            String origin = t.getOrigin();
            if ("Built by me".equals(filter) && !"user".equals(origin)) continue;
            if ("Imported".equals(filter) && !"imported".equals(origin)) continue;
            if ("Default".equals(filter) && !"default".equals(origin)) continue;
            view.add(t);
        }

        java.util.Comparator<Task> cmp;
        if ("Newest".equals(mode))
            cmp = java.util.Comparator.comparingLong(Task::getCreatedAt).reversed();
        else if ("Most used".equals(mode))
            cmp = java.util.Comparator.<Task>comparingInt(
                    t -> libraryUseCounts.getOrDefault(t.getId(), 0)).reversed()
                    .thenComparing(Task::getName, String.CASE_INSENSITIVE_ORDER);
        else
            cmp = java.util.Comparator.comparing(Task::getName, String.CASE_INSENSITIVE_ORDER);
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

        panelSkillTracker.add(createSubtitle("Skill Tracker"), BorderLayout.NORTH);
        panelSkillTracker.add(Theme.thinScrollbars(new JScrollPane(gridSkills)), BorderLayout.CENTER);
        totalLevelLabelP2P.setForeground(TEXT_MAIN);

        return panelSkillTracker;
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

        JLabel blurb = new JLabel("<html>Checks run automatically between actions - several at "
                + "once when needed (run away <i>and</i> eat). Pick one on the left to edit it. "
                + "Checks tied to a single action live on the <b>Checks…</b> button in the Task "
                + "Builder.</html>");
        blurb.setForeground(TEXT_DIM);
        blurb.setBorder(new EmptyBorder(0, 0, 8, 0));

        checksEditor = new main.menu.components.TriggerEditor(
                globalTriggers, false, this::pickResponseAction);
        main.menu.components.TriggerEditor editor = checksEditor;

        panel.add(createSubtitle("Checks"), BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setOpaque(false);
        body.add(blurb, BorderLayout.NORTH);
        body.add(editor, BorderLayout.CENTER);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    /** The Loot Tracker's own tab (Patch B.5) - kill counts and learned drop tables. */
    private JPanel createLootTrackerTab() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        panel.setBackground(BG_BASE);
        panel.add(createSubtitle("Loot Tracker"), BorderLayout.NORTH);
        panel.add(buildLootTablePanel(), BorderLayout.CENTER);
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
        java.util.Map<String, Integer> kills = main.tools.LootTracker.allKillCounts();
        java.util.Map<String, java.util.Map<String, Long>> drops = main.tools.LootTracker.allDropCounts();
        if (kills.isEmpty() && drops.isEmpty()) {
            lootTableArea.setText("No kills recorded yet.\n\nKill NPCs with an Interact "
                    + "(Attack) action and loot near them - drops seen on your kill tiles are "
                    + "tallied here per NPC.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        java.util.List<String> npcs = new java.util.ArrayList<>(
                new java.util.TreeSet<>(kills.keySet()));
        for (String npc : drops.keySet()) if (!npcs.contains(npc)) npcs.add(npc);
        for (String npc : npcs) {
            int k = kills.getOrDefault(npc, 0);
            sb.append(npc).append("  \u2014  ").append(k).append(k == 1 ? " kill" : " kills").append("\n");
            java.util.Map<String, Long> table = drops.get(npc);
            if (table != null) {
                java.util.List<java.util.Map.Entry<String, Long>> sorted =
                        new java.util.ArrayList<>(table.entrySet());
                sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
                for (java.util.Map.Entry<String, Long> en : sorted) {
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
        main.menu.components.JActionSelector sel = new main.menu.components.JActionSelector();
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
    private java.util.List<main.actions.TaskRef> buildLibraryEntries() {
        java.util.List<main.actions.TaskRef> entries = new java.util.ArrayList<>();
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

        JPanel content = new JPanel(new GridLayout(2, 3, 20, 0));
        content.setBackground(BG_BASE);

        ///  create status 'Player' section
        JPanel player = createInfoCard("Player");
        addInfoRow(player, "Character name", lblCharName);
        addInfoRowWithIcon(player, "Membership", lblMemberText, lblMemberIcon);

        JPanel account = createInfoCard("Account");
        addInfoRow(account, "Username", lblUsername);
        addInfoRow(account, "Nickname", lblNickname);
        addInfoRow(account, "Identifier", lblAcctId);
        addInfoRow(account, "Account status", lblAcctStatus);

        JPanel world = createInfoCard("World");
        addInfoRow(world, "World", lblWorld);
        addInfoRow(world, "Coordinates (x, y, z)", lblCoords);

        JPanel game = createInfoCard("Game");
        addInfoRow(game, "Game state", lblGameState);

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

        content.add(player);
        content.add(account);
        content.add(world);
        content.add(game);
        content.add(script);

        panelStatus.add(createSubtitle("Status"),  BorderLayout.NORTH);
        panelStatus.add(content, BorderLayout.CENTER);
        return panelStatus;
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
        settingGroup.add(createDreamBotClientPanel(), "DreamBot");
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

        String[] groups = {"Client", "DreamBot", "Script", "Display", "Gameplay", "Audio", "Chat", "Controls", "Activities", "Warnings"};
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
        private String id = java.util.UUID.randomUUID().toString();
        /** When this logical task was first created (Patch B.3) - drives "Newest" sorting. */
        private long createdAt = System.currentTimeMillis();
        /** Where this task came from (Patch B.5): "user" (built here), "imported", "default". */
        private String origin = "user";
        private String name;
        private String description;
        private String status;
        private List<Action> actions;
        /** How many times this task runs before the queue advances (>= 1). */
        private int repeat = 1;
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
            this.name = o.name;
            this.description = o.description;
            this.status = o.status;
            this.repeat = o.repeat;
            this.autoDelay = o.autoDelay;
            this.autoDelayMinMs = o.autoDelayMinMs;
            this.autoDelayMaxMs = o.autoDelayMaxMs;

            // DEEP COPY logic:
            this.actions = new ArrayList<>();
            if (o.actions != null) {
                for (Action originalAction : o.actions) {
                    // Create a brand new action object for the new list
                    this.actions.add(originalAction.copy());
                }
            }
        }

        /** Stable identity shared by all copies of this logical task. */
        public String getId() { return id; }

        /** Makes this task a NEW logical task (used by Duplicate). */
        public void regenerateId() {
            this.id = java.util.UUID.randomUUID().toString();
            this.createdAt = System.currentTimeMillis();
        }

        /** When this task was created (0 for pre-B.3 saves). */
        public long getCreatedAt() { return createdAt; }

        /** "user" | "imported" | "default" (Patch B.5) - drives the library filter. */
        public String getOrigin() { return origin == null || origin.isEmpty() ? "user" : origin; }
        public void setOrigin(String o) { if (o != null && !o.isEmpty()) this.origin = o; }
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
        modelTaskList.clear();
        libraryAll.clear();          // Patch B.5: master first; the view refilters below
        modelTaskLibrary.clear();
        modelPresets.clear();

        if (data != null) {
            if (data.taskList != null)
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

        // Patch B.4: restore always-on watchers
        globalTriggers.clear();
        if (data != null && data.globalTriggers != null && !data.globalTriggers.isBlank())
            globalTriggers.addAll(main.watchers.TriggerCodec.fromJson(data.globalTriggers));
        if (checksEditor != null) checksEditor.reload();   // B.5: the UI list must follow

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
                        librarySetAll(new java.util.ArrayList<>(fetchedTasks.values()));
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
    public java.util.List<main.menu.skills.SkillData> getTrackedSkills() {
        java.util.List<main.menu.skills.SkillData> out = new java.util.ArrayList<>();
        try {
            skillRegistry.values().stream()
                    .filter(main.menu.skills.SkillData::isTracking)
                    .forEach(out::add);
        } catch (Throwable ignored) {}
        return out;
    }

    /**
     * Writes a compact machine-readable status snapshot to {@code <home>/DreamMan/status.json}
     * every ~2 seconds (Patch B.3). This is the groundwork for the remote web dashboard: an
     * external process can poll the file for the account, its LIVE tile (map replica), the
     * current task/activity, queue + loop progress, pause state and uptime - without touching
     * the client. Remote CONTROL can later ride the same channel in reverse.
     */
    private void writeStatusSnapshot(String taskName) {
        long now = System.currentTimeMillis();
        if (now - lastStatusSnapshotAt < 2000)
            return;
        lastStatusSnapshotAt = now;

        java.util.Map<String, Object> snap = new java.util.LinkedHashMap<>();
        snap.put("character", safePlayerName());
        try {
            if (Client.isLoggedIn()) {
                org.dreambot.api.methods.map.Tile t =
                        org.dreambot.api.methods.interactive.Players.getLocal().getTile();
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

        final java.util.Map<String, Object> payload = snap;
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

    private void addInfoRow(JPanel p, String key, JLabel valLabel) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        JLabel k = new JLabel(key);
        k.setForeground(TEXT_DIM);
        valLabel.setForeground(TEXT_MAIN);
        valLabel.setFont(new Font("Consolas", Font.BOLD, 14));
        row.add(k, BorderLayout.WEST); row.add(valLabel, BorderLayout.EAST);
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
        java.util.List<Component> rows = new java.util.ArrayList<>();

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
    private void addReflectiveToggle(java.util.List<Component> rows, String label,
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
    private static final java.util.Set<String> DEV_ACCOUNTS = new java.util.HashSet<>(
            java.util.Arrays.asList("iamawake247"));   // lowercase; compared case-insensitively
    // Secret unlock token: put exactly this text inside <home>/DreamMan/dev.flag to unlock the
    // console on any account (handy on a fresh/alt login). CHANGE THIS to your own secret.
    private static final String DEV_TOKEN = "dm-dev-8f3a91c2";

    /** True only for you: recognised account name, or the exact secret token in dev.flag. */
    private boolean isDeveloper() {
        String character = safePlayerName();
        if (character != null && DEV_ACCOUNTS.contains(character.trim().toLowerCase()))
            return true;
        try {
            java.io.File flag = new java.io.File(main.data.store.LocalStore.getRoot(), "dev.flag");
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
        data.skillGoals = new java.util.HashMap<>();
        for (SkillData sd : skillRegistry.values())
            if (sd.getGoalXp() > 0)
                data.skillGoals.put(sd.getSkill().name(), sd.getGoalXp());
        data.globalTriggers = main.watchers.TriggerCodec.toJson(
                new java.util.ArrayList<>(globalTriggers));
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
        bd.actions = new java.util.ArrayList<>();
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

            name.setText(task.getName());
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
            badge.setText("×" + Math.max(1, rep));   // Patch B.2: always visible, ×1 included

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
            if (newName != null && !newName.trim().isEmpty())
                modelPresets.get(actualIndex).name = newName.trim();

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