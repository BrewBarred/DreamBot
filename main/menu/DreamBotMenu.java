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
    private final Color COLOR_BUTTON_RED = Color.RED;


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

    final DefaultListModel<Task> modelTaskLibrary = new DefaultListModel<>();
    private final JList<Task> listTaskLibrary = new JList<>(modelTaskLibrary);

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
    private final JProgressBar statusProgress = new JProgressBar(0, 100);
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
        taskBuilder = new TaskBuilder(this);
        libraryPanel = new LibraryPanel();

        mainTabs.setBackground(Theme.SURFACE_1);
        mainTabs.setForeground(Theme.TEXT_DIM);
        mainTabs.setFont(Theme.font(13));
        mainTabs.addTab("Task List", loadTabIcon("task_list_tab"), createTaskListTab());
        mainTabs.addTab("Task Library", loadTabIcon("task_library_tab"), createTaskLibraryTab());
        mainTabs.addTab("Task Builder", loadTabIcon("task_builder_tab"), taskBuilder);
        mainTabs.addTab("Skill Tracker", loadTabIcon("skills_tracker_tab"), createSkillTrackerTab());
        mainTabs.addTab("Status", loadTabIcon("status_tab"), createStatusTab());
        mainTabs.addTab("Settings", loadTabIcon("settings_tab"), createSettingsTab());
        mainTabs.addTab("Developers Console", new DevelopersConsole(libraryPanel));

        Logger.log(Logger.LogType.DEBUG, "Setup main tabs...");

        projectionSpinner = new JSpinner(new SpinnerNumberModel(24, 1, 999, 1));
        styleSpinner(projectionSpinner);

        trackerList = new JPanel();
        trackerList.setLayout(new BoxLayout(trackerList, BoxLayout.Y_AXIS));
        trackerList.setBackground(PANEL_SURFACE);
        JScrollPane sScroll = new JScrollPane(trackerList);
        sScroll.setBorder(null);
        sScroll.getViewport().setBackground(PANEL_SURFACE);

        ///  Define side panel
        JPanel sidePanelContent = new JPanel(new BorderLayout());
        sidePanelContent.setPreferredSize(new Dimension(360, 0));
        sidePanelContent.setBackground(PANEL_SURFACE);
        sidePanelContent.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, COLOR_BORDER_DIM));

        ///  Add toggle button to show/hide side panel
        JButton btnToggleSidePanel = new JButton();
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
            final String character = safePlayerName();
            if (character != null) {
                final ProfileData[] box = new ProfileData[1];
                if (SwingUtilities.isEventDispatchThread())
                    box[0] = captureProfile();
                else
                    SwingUtilities.invokeAndWait(() -> box[0] = captureProfile());
                LocalStore.save(character, box[0]);
            }
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

        left.add(lblRepeat);
        left.add(taskRepeatSpinner);
        left.add(lblLoop);
        left.add(loopQueueSpinner);
        left.add(loopInfiniteCheck);

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
        JScrollPane taskScroll = new JScrollPane(listTaskList);
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

        /// CENTER WEST: Library list
        styleJList(listTaskLibrary);
        libraryEditorArea = new JTextArea();
        libraryEditorArea.setBackground(new Color(15, 15, 15));
        libraryEditorArea.setForeground(TEXT_MAIN);

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
                modelTaskLibrary.remove(selectedIndex);
                this.showToast("Deleted Task!", btnTaskLibraryDelete, true);
            } else {
                this.showToast("Select an Action to delete!", btnTaskLibraryDelete, false);
            }


            refreshTaskLibrary();
        });

        ///  Create Task Library edit button
        JButton btnTaskLibraryEdit = createButton("View in builder...");
        btnTaskLibraryEdit.addActionListener(e -> {
            loadIntoBuilder(listTaskLibrary.getSelectedValue());
            this.showToast("Moving to builder for viewing...", btnTaskLibraryEdit, true);
            // switch to tab 2 (3rd tab = Task Builder) to edit task
            mainTabs.setSelectedIndex(2);
        });

        listTaskLibrary.addListSelectionListener(e -> {
            btnTaskLibraryDelete.setEnabled(!listTaskLibrary.isSelectionEmpty());
            Task t = listTaskLibrary.getSelectedValue();
            if (t != null)
                libraryEditorArea.setText(t.getEditableString());
        });

        listTaskLibrary.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int selectedIndex = listTaskLibrary.getSelectedIndex();

                // return early if no task is currently selected in the task library
                if (selectedIndex == -1)
                    return;

                if (e.getClickCount() == 2) {
                    // deep-copy the selected task into the queue (the old shallow reference meant
                    // edits in the builder/queue silently corrupted the library original)
                    Task task = new Task(modelTaskLibrary.getElementAt(selectedIndex));
                    modelTaskList.addElement(task);
                    DreamBotMenu.this.showToast("Added to queue position " + modelTaskList.size(), btnTaskLibraryAdd, true);
                    refreshTaskListTab();
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

        panelCenterEastLibraryTab.add(new JScrollPane(libraryEditorArea), BorderLayout.CENTER);

        // 4. Add both sections to the center wrapper
        centerContent.add(new JScrollPane(listTaskLibrary));
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
        if (listTaskLibrary.getSelectedValue() == null && !modelTaskLibrary.isEmpty())
            listTaskLibrary.setSelectedIndex(modelTaskLibrary.getSize() - 1);
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

        TaskData dto = LocalStore.importFromFile(chooser.getSelectedFile(), TaskData.class);
        Task task = ProfileCodec.fromData(dto);
        if (task == null) {
            showToast("Could not read that task file", anchor, false);
            return;
        }

        modelTaskLibrary.addElement(task);
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
        panelSkillTracker.add(new JScrollPane(gridSkills), BorderLayout.CENTER);
        totalLevelLabelP2P.setForeground(TEXT_MAIN);

        return panelSkillTracker;
    }

    private JPanel createStatusTab() {
        JPanel panelStatus = new JPanel(new BorderLayout(15, 15));
        panelStatus.setBorder(new EmptyBorder(15, 15, 15, 15));
        panelStatus.setBackground(BG_BASE);

        JPanel content = new JPanel(new GridLayout(2, 2, 20, 0));
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

        content.add(player);
        content.add(account);
        content.add(world);
        content.add(game);

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
        private String name;
        private String description;
        private String status;
        private List<Action> actions;
        /** How many times this task runs before the queue advances (>= 1). */
        private int repeat = 1;

        public Task(String name, String description, List<Action> actions, String status) {
            this.name = name;
            this.description = (description == null || description.isEmpty()) ? "No description provided by Author." : description;
            this.actions = actions;
            this.status = (status == null || status.isEmpty()) ? "Executing task..." : status;
        }

        public Task(Task o) {
            this.name = o.name;
            this.description = o.description;
            this.status = o.status;
            this.repeat = o.repeat;

            // DEEP COPY logic:
            this.actions = new ArrayList<>();
            if (o.actions != null) {
                for (Action originalAction : o.actions) {
                    // Create a brand new action object for the new list
                    this.actions.add(originalAction.copy());
                }
            }
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
        final String character = safePlayerName();
        if (character == null) {
            Logger.log(Logger.LogType.DEBUG, "loadProfileFromDisk(): not logged in, skipping.");
            return;
        }

        new Thread(() -> {
            final ProfileData data = LocalStore.load(character);
            SwingUtilities.invokeLater(() -> applyProfile(data));
        }, "DreamMan-Load").start();
    }

    /** Applies a loaded profile to every model + the builder + settings (call on the EDT). */
    private void applyProfile(ProfileData data) {
        // Always start from a clean slate so a fresh character doesn't inherit stale rows.
        modelTaskList.clear();
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
                    if (task != null) modelTaskLibrary.addElement(task);
                }

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
                            data.builder.taskDescription, data.builder.taskStatus);
            }

            applySettings(data.settings);
            Logger.log(Logger.LogType.INFO, "Profile applied: "
                    + modelTaskList.size() + " queued, "
                    + modelTaskLibrary.size() + " in library, "
                    + modelPresets.size() + " presets.");
        } else {
            Logger.log(Logger.LogType.INFO, "No saved profile - starting with an empty workspace.");
        }

        queueLoopTarget = (data != null) ? Math.max(0, data.queueLoops) : 1;
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
                        modelTaskLibrary.clear();
                        for (Task task : fetchedTasks.values()) {
                            // Because GSON uses the Task constructor, these are
                            // now real objects with executable action lists.
                            modelTaskLibrary.addElement(task);
                        }
                        refreshTaskLibrary();
                        Logger.log("Successfully unpacked " + modelTaskLibrary.size() + " tasks into Task Library");
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
        if (component.getBackground().equals(flashColor))
            return;

        // Only capture the restore color if we aren't already flashing
        final Color restoreColor = component.getClientProperty("originalColor") != null
                ? (Color) component.getClientProperty("originalColor")
                : component.getBackground();

        // Store the original color on first flash only
        component.putClientProperty("originalColor", restoreColor);

        component.setBackground(flashColor);
        component.setOpaque(true);
        component.repaint();

        Timer revertTimer = new Timer(TIME_FLASH, e -> {
            component.setBackground(restoreColor);
            component.putClientProperty("originalColor", null); // Clear it after revert
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
            SwingUtilities.invokeLater(() -> btnPlayPause.setText("▮▮"));
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
                btnPlayPause.setText(isMenuPaused() ? "▶" : "▮▮");

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
        JLabel icon = new JLabel(loadSkillIcon(data.getSkill()));
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
        gbc.gridy = 2;
        tile.add(data.getTrackerPanel(), gbc);

        tile.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
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
        // to disk off the EDT so a slow disk never freezes the UI.
        final ProfileData data = captureProfile();
        final String character = safePlayerName();

        if (character == null) {
            if (verbose)
                setStatus("Log in first to save your profile");
            return;
        }

        if (verbose)
            setStatus("Saving...");

        new Thread(() -> {
            boolean ok = LocalStore.save(character, data);
            if (verbose)
                setStatus(ok ? "Saved!" : "Save failed - see client logs");
        }, "DreamMan-Save").start();
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
        data.taskList = ProfileCodec.tasksToData(modelTaskList);
        data.library = ProfileCodec.tasksToData(modelTaskLibrary);
        data.presets = ProfileCodec.presetsToData(modelPresets);

        BuilderData bd = new BuilderData();
        if (taskBuilder != null) {
            bd.taskName = taskBuilder.getDraftName();
            bd.taskDescription = taskBuilder.getDraftDescription();
            bd.taskStatus = taskBuilder.getDraftStatus();
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
            badge.setText(rep > 1 ? "×" + rep : "");

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

            if (actualIndex < modelPresets.size()) {
                Preset p = modelPresets.get(actualIndex);
                presetButtons[i].setText(p.name);

                // Color Logic mapped exactly to your request:
                if (actualIndex == selectedPresetIndex)
                    presetButtons[i].setBackground(COLOR_BTN_ADD); // Selected
                else if (p.tasks.isEmpty())
                    presetButtons[i].setBackground(COLOR_BTN_BACKGROUND); // Empty
                else
                    presetButtons[i].setBackground(COLOR_BLOOD); // Filled

            } else {
                // Formatting for uninitialized presets beyond current size
                presetButtons[i].setText("Preset " + (actualIndex + 1));
                presetButtons[i].setBackground(actualIndex == selectedPresetIndex ? COLOR_BTN_ADD : COLOR_BTN_BACKGROUND);
            }

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