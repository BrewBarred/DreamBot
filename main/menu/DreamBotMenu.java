package main.menu;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import main.managers.DataMan;
import org.dreambot.api.Client;
import org.dreambot.api.ClientSettings;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.methods.skills.Skills;
import org.dreambot.api.methods.world.Worlds;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.utilities.AccountManager;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.methods.container.impl.equipment.Equipment;
import org.dreambot.api.wrappers.items.Item;
import org.dreambot.api.methods.map.Tile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <h1>DreamBotMan</h1>
 * @author DreamBotMan Dev
 * @version 15.0.0-Elite
 */
public class DreamBotMenu extends JFrame {
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
    private final Color COLOR_BUTTON_BACKGROUND = Color.GRAY;

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
    private boolean isScriptPaused = true;
    private boolean isUserInputAllowed = true;
    private boolean isDataLoading = false;
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

    private final AbstractScript script;
    private final JPanel sidePanel;

    private final Color COLOR_FAILURE = new Color(100, 0, 0);
    private final Color COLOR_SUCCESS = new Color(0, 100, 0);
    private final Color COLOR_LIGHT_GREEN = new Color(150, 200, 50);
    private final Color COLOR_BTN_BACKGROUND = new Color(40,40,40);
    private final Color COLOR_BTN_FOREGROUND = Color.WHITE;

    // --- Data & Presets ---
    private final int MAX_TOAST_QUEUE = 10; // Only allow 3 toasts to be "waiting"
    private final Queue<ToastRequest> toastQueue = new LinkedList<>();
    private boolean isToastProcessing = false;

    private final DataMan dataMan = new DataMan();

    private final Map<Skill, SkillData> skillRegistry = new EnumMap<>(Skill.class);

    private final DefaultListModel<Task> modelTaskList = new DefaultListModel<>();
    private final JList<Task> listTaskList = new JList<>(modelTaskList);

    private final DefaultListModel<Task> modelTaskLibrary = new DefaultListModel<>();
    private final JList<Task> listTaskLibrary = new JList<>(modelTaskLibrary);

    private final DefaultListModel<Action> modelTaskBuilder = new DefaultListModel<>();
    private final JList<Action> listTaskBuilder = new JList<>(modelTaskBuilder);

    private final DefaultListModel<String> nearbyEntitiesModel = new DefaultListModel<>();


    // --- Preset State ---
    private final int MAX_PRESETS = 16;
    private int currentPresetPage = 0;
    private int selectedPresetIndex = -1; // Tracks the currently active/selected preset index
    private final List<Preset> allPresets = new ArrayList<>();
    private final JButton[] presetButtons = new JButton[4];
    private JButton btnPageUp, btnPageDown;

    // --- UI Components ---
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
    private JTextField taskNameInput, taskDescriptionInput, taskStatusInput, inputTargetName, consoleSearch;
    private JComboBox<ActionType> actionCombo;
    private JButton btnPlayPause, btnInputToggle, btnCaptureToggle;

    // --- Client Checkboxes ---
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
    private final Color BG_BASE = new Color(12, 12, 12);
    private final Color PANEL_SURFACE = new Color(24, 24, 24);
    private final Color COLOR_BLOOD = new Color(150, 0, 0);
    private final Color COLOR_ORANGE = new Color(220, 80, 0); // Restored from new builder
    private final Color BORDER_DIM = new Color(45, 45, 45);
    private final Color TEXT_MAIN = new Color(210, 210, 210);
    private final Color TEXT_DIM = new Color(140, 140, 140);
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

    private Toast activeToast;
    private final int TIME_FLASH = 200;


    public enum ActionType { ATTACK, BANK, BURY, CHOP, COOK, DROP, EXAMINE, FISH, MINE, OPEN, TALK_TO, USE_ON }

    private static final Skill[] OSRS_ORDER = { Skill.ATTACK, Skill.HITPOINTS, Skill.MINING, Skill.STRENGTH, Skill.AGILITY, Skill.SMITHING, Skill.DEFENCE, Skill.HERBLORE, Skill.FISHING, Skill.RANGED, Skill.THIEVING, Skill.COOKING, Skill.PRAYER, Skill.CRAFTING, Skill.FIREMAKING, Skill.MAGIC, Skill.FLETCHING, Skill.WOODCUTTING, Skill.RUNECRAFTING, Skill.SLAYER, Skill.FARMING, Skill.CONSTRUCTION, Skill.HUNTER, Skill.SAILING };
    private static final Set<Skill> F2P_SKILLS = new HashSet<>(Arrays.asList(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.PRAYER, Skill.MAGIC, Skill.HITPOINTS, Skill.CRAFTING, Skill.MINING, Skill.SMITHING, Skill.FISHING, Skill.COOKING, Skill.FIREMAKING, Skill.WOODCUTTING, Skill.RUNECRAFTING));

    public DreamBotMenu(AbstractScript script) {
        this.script = script;
        this.startTime = System.currentTimeMillis();
        this.setIconImage(Objects.requireNonNull(loadStatusIcon("Hardcore_ironman")).getImage());
        setTitle("DreamBotMan | OSRS DreamBot Manager v1");

        setSize(1400, 950);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(BG_BASE);

        JPanel header = createHeaderPanel();
        mainTabs.setBackground(PANEL_SURFACE);
        mainTabs.setForeground(TEXT_MAIN);

        mainTabs.addTab("Task List", loadTabIcon("task_list_tab"), createTaskListTab());
        mainTabs.addTab("Task Library", loadTabIcon("task_library_tab"),createTaskLibraryTab());
        mainTabs.addTab("Task Builder", loadTabIcon("task_builder_tab"),createTaskBuilderTab());
        mainTabs.addTab("Skill Tracker", loadTabIcon("skills_tracker_tab"), createSkillTrackerTab());
        mainTabs.addTab("Status", loadTabIcon("status_tab"), createStatusTab());
        mainTabs.addTab("Settings", loadTabIcon("settings_tab"), createSettingsTab());

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
        sidePanelContent.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER_DIM));

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
        sidePanelContent.add(btnToggleSidePanel, BorderLayout.EAST);
        sidePanelContent.add(totals, BorderLayout.SOUTH);
        sidePanelContent.setVisible(false);

        this.sidePanel = new JPanel(new BorderLayout());
        this.sidePanel.setOpaque(false);
        this.sidePanel.add(sidePanelContent, BorderLayout.CENTER);
        this.sidePanel.add(btnToggleSidePanel, BorderLayout.EAST);

        btnToggleSidePanel.setText(sidePanelContent.isVisible() ? ">" : "<");
        btnToggleSidePanel.setPreferredSize(new Dimension(22, 0));
        btnToggleSidePanel.setBackground(PANEL_SURFACE);
        btnToggleSidePanel.setForeground(COLOR_BLOOD);
        btnToggleSidePanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 1, BORDER_DIM));

        btnToggleSidePanel.addActionListener(e -> {
            sidePanelContent.setVisible(!sidePanelContent.isVisible());
            btnToggleSidePanel.setText(sidePanelContent.isVisible() ? ">" : "<");
            revalidate();
        });

        add(header, BorderLayout.NORTH);
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
                    scanNearbyTargets();
            });
            scanTimer.start();

            ///  Start a third timer to auto-save everything periodically
            saveTimer = new Timer(60000, e -> {
                // auto-save every n seconds (if auto-save feature is enabled in settings)
                if (chkAutoSave != null && chkAutoSave.isSelected())
                    saveAll();
            });
            saveTimer.start();

        });

        setVisible(true);
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

    private JPanel createProgressPanel() {
        // Use a GridBagLayout for precise control or a 2-row GridLayout
        JPanel persistentStatus = new JPanel(new GridBagLayout());
        persistentStatus.setBackground(PANEL_SURFACE);

        // Add a border to separate it from the tabs above
        persistentStatus.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_DIM), // Top line
                new EmptyBorder(8, 15, 8, 15) // Inner padding
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 0;

        // --- Row 1: The Progress Bar (Full Width) ---
        statusProgress.setPreferredSize(new Dimension(0, 18)); // Width 0 is fine because weightx=1.0 stretches it
        statusProgress.setForeground(COLOR_BLOOD);
        statusProgress.setBackground(BG_BASE);
        statusProgress.setBorder(new LineBorder(BORDER_DIM));
        statusProgress.setStringPainted(true); // Shows % text

        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 4, 0); // Small gap between bar and text
        persistentStatus.add(statusProgress, gbc);

        // --- Row 2: The Status Text (Below) ---
        lblStatus.setForeground(TEXT_DIM);
        lblStatus.setFont(new Font("Consolas", Font.PLAIN, 12));
        setStatus("Waiting instructions...");

        gbc.gridy = 1;
        gbc.insets = new Insets(0, 2, 0, 0); // Slight left offset for text alignment
        persistentStatus.add(lblStatus, gbc);

        return persistentStatus;
    }

    // --- Bridge Methods for Main Script ---
    public boolean isScriptPaused() { return isScriptPaused; }
    public DefaultListModel<Task> getModelTaskList() { return modelTaskList; }
    public int getCurrentExecutionIndex() { return currentExecutionIndex; }

    public void setCurrentExecutionIndex(int i) {
        this.currentExecutionIndex = i;
        if(listTaskList != null)
            listTaskList.repaint();
    }

    public void setStatus(String text) {
        SwingUtilities.invokeLater(() -> {
            lblStatus.setText("Status: " + text);

            // start a timer after the default status reset delay
            Timer timer = new Timer(DEFAULT_STATUS_RESET_DELAY, e -> {
                // if by the time this timer is triggered, the text has not changed
                if (lblStatus.getText().equals(text))
                    // revert the text back to the default status string
                    lblStatus.setText("Status: " + DEFAULT_STATUS_STRING);
            });

            // disable repeats to prevent multiple calls of the same action, i think??
            timer.setRepeats(false);
            timer.start();
        });
    }

    public void incrementExecutionIndex() {
        if (currentExecutionIndex < modelTaskList.size() - 1) {
            currentExecutionIndex++;
        } else {
            currentExecutionIndex = -1; // reset to "idle" state
            isScriptPaused = true;
            if (btnPlayPause != null)
                btnPlayPause.setText("▶");
        }
        listTaskList.repaint();
    }

    private JLabel createSubtitle(String subtitle) {
        JLabel title = new JLabel(subtitle, SwingConstants.CENTER);
        title.setForeground(COLOR_BLOOD);
        title.setFont(new Font("Consolas", Font.BOLD, 22));
        title.setBorder(BorderFactory.createMatteBorder(0, 5, 0, 0, COLOR_BLOOD));

        return title;
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
        panelTaskList.setBorder(new EmptyBorder(15, 15, 15, 15));
        panelTaskList.setBackground(BG_BASE);

        /// CENTER: Add the task queue list display to the center of the task list panel
        listTaskList.setCellRenderer(new TaskCellRenderer());
        styleJList(listTaskList);

        /// WEST: Add up/down buttons to navigate through task list
        // create west panel
        JPanel west = new JPanel(new GridLayout(0, 1, 0, 5));
        west.setOpaque(false);

        // create task list navigation buttons (up/down arrows)
        JButton btnUp = createNavButton("▲");
        btnUp.addActionListener(e -> {
            //boolean shiftPressed = (e.getModifiers() & ActionEvent.SHIFT_MASK) != 0;
        });

        JButton btnDown = createNavButton("▼");
        btnDown.addActionListener(e -> {
            //boolean shiftPressed = (e.getModifiers() & ActionEvent.SHIFT_MASK) != 0;
            boolean success = navigateQueue(1, listTaskList, modelTaskList);
            flashControl(btnDown, success ? COLOR_SUCCESS : COLOR_FAILURE);
        });

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
        JButton btnTaskListSave = createNavButton("Save");
        btnTaskListSave.addActionListener(e -> {
            this.makeToast("Saving...", btnTaskListSave, true);
            saveAll();
        });

        ///  Create Task List duplicate button
        JButton btnTaskListDuplicate = createNavButton("Duplicate");
        btnTaskListDuplicate.addActionListener(e -> {
            // create a new task using the selected value
            Task selected = listTaskList.getSelectedValue();
            if (selected == null) {
                // return early if no tasks are currently selected
                this.makeToast("Select a task first!", btnTaskListDuplicate, false);
                return;
            }

            // create a new task using the selected task
            Task task = new Task(selected);
            // add the duplicated task to the task list
            modelTaskList.addElement(task);
            // refresh the task list display
            listTaskList.repaint();
            this.makeToast("Duplication complete! Size: " + modelTaskList.size(), btnTaskListDuplicate, true);
        });

        ///  Create Task List remove button
        JButton btnTaskListRemove = createNavButton("Remove");
        // disable remove button when nothing is selected
        btnTaskListRemove.setEnabled(listTaskList.getSelectedIndex() != -1);
        btnTaskListRemove.addActionListener(e ->
                removeTask(listTaskList, modelTaskList, btnTaskListRemove)
        );

        ///  Create Task List edit button
        JButton btnTaskListView = createNavButton("View in builder...");
        btnTaskListView.addActionListener(e -> {
            loadIntoBuilder(listTaskList.getSelectedValue());
            this.makeToast("Moving to builder for viewing...", btnTaskListView, true);
            // switch to tab 2 (3rd tab = Task Builder) to edit task
            mainTabs.setSelectedIndex(2);
        });

        /// Create Reset All button
        JButton btnResetAllPresets = createButton("Reset All", new Color(139, 0, 0), null);
        btnResetAllPresets.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(this, "WARNING: This will delete ALL presets (could be 100s!). Continue?", "Nuclear Reset", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                allPresets.clear();
                currentPresetPage = 0;
                selectedPresetIndex = -1;
                btnPageUp.setEnabled(false);
                refreshPresetButtonLabels();
                this.makeToast("System Reset Complete!", btnResetAllPresets, true);
            }
        });

        listTaskList.addListSelectionListener(e -> {
            btnTaskListRemove.setEnabled(!listTaskList.isSelectionEmpty());
        });

        listTaskList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // get the index based on the specific mouse coordinates
                int index = listTaskList.locationToIndex(e.getPoint());

                // ensure the index is valid and the click bounds actually contain that item
                if (index != -1 && listTaskList.getCellBounds(index, index).contains(e.getPoint())) {

                    if (e.getClickCount() == 1) {
                        // TODO add logic to view task information? Perhaps, loop count, description, end condition, and maybe a live-action-chain? action1 -> action2 -> action3
                    }

                    // only trigger if it's a double-click AND Shift is NOT being held
                    if (e.getClickCount() == 2 && !e.isShiftDown())
                        removeTask(listTaskList, modelTaskList, btnTaskListRemove);
                }
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
        south.add(southButtons, BorderLayout.SOUTH);

        // add all panels to the main panel (task list panel)
        panelTaskList.add(createSubtitle("Task List"), BorderLayout.NORTH);
        panelTaskList.add(west, BorderLayout.WEST);
        panelTaskList.add(new JScrollPane(listTaskList), BorderLayout.CENTER);
        panelTaskList.add(south, BorderLayout.SOUTH);

        // add listener to scan for nearby targets and select the first item if none selected on show
        panelTaskList.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                super.componentShown(e);
                refreshTaskListTab();
                refreshPresetButtonLabels();
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
        JButton btnUpList = createNavButton("▲");
        JButton btnDownList = createNavButton("▼");

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
        JPanel btnSection = new JPanel(new GridLayout(1, 3, 0, 5));
        panelCenterEastLibraryTab.setOpaque(false);
        btnSection.setOpaque(false);

        ///  Create Task Library save button
        JButton btnTaskLibrarySave = createButton("Save");
        btnTaskLibrarySave.addActionListener(e -> {
            this.makeToast("Saving...", btnTaskLibrarySave, true);
            btnTaskLibrarySave.setEnabled(false);

            saveAll();

            this.makeToast("Save success!", btnTaskLibrarySave, true);
            btnTaskLibrarySave.setEnabled(true);
        });

        ///  Create Task Library add button
        JButton btnTaskLibraryAdd = createButton("Add", COLOR_LIGHT_GREEN, null);
        btnTaskLibraryAdd.addActionListener(e -> {
            if(listTaskLibrary.getSelectedValue() != null) {
                modelTaskList.addElement(listTaskLibrary.getSelectedValue());
                this.makeToast("Added to position " + modelTaskList.size() + " of the queue", btnTaskLibraryAdd, true);
            }
        });

        ///  Create Task Library delete button
        JButton btnTaskLibraryDelete = createButton("Delete", COLOR_FAILURE, null);
        btnTaskLibraryDelete.setEnabled(listTaskLibrary.getSelectedIndex() != -1);
        btnTaskLibraryDelete.addActionListener(e -> {
            int selectedIndex = listTaskLibrary.getSelectedIndex();
            if (selectedIndex != -1) {
                modelTaskLibrary.remove(selectedIndex);
                this.makeToast("Deleted Task!", btnTaskLibraryDelete, true);
            } else {
                this.makeToast("Select an Action to delete!", btnTaskLibraryDelete, false);
            }


            refreshTaskLibrary();
        });

        ///  Create Task Library edit button
        JButton btnTaskLibraryEdit = createButton("View in builder...");
        btnTaskLibraryEdit.addActionListener(e -> {
            loadIntoBuilder(listTaskLibrary.getSelectedValue());
            this.makeToast("Moving to builder for viewing...", btnTaskLibraryEdit, true);
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
                    // take a shallow copy of the selected task to pass it to the task list
                    Task task = modelTaskLibrary.getElementAt(selectedIndex);
                    modelTaskList.addElement(task);
                    DreamBotMenu.this.makeToast("Added to queue position " + modelTaskList.size(), btnTaskLibraryAdd, true);
                    refreshTaskListTab();
                }
            }
        });

        ///  Add all buttons
        btnSection.add(btnTaskLibrarySave);
        btnSection.add(btnTaskLibraryAdd);
        btnSection.add(btnTaskLibraryDelete);
        btnSection.add(btnTaskLibraryEdit);

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
        // ignore empty lists (but still refresh them in case of update after removal)
        if (!modelTaskList.isEmpty()) {
            // get the selected index (prefer passed index, default to selected, resort to first item worst case
            int selected = index >= 0 ? index : listTaskList.getSelectedIndex();
            // ensure index is still within bounds of list model
            if (selected >= 0 && selected < modelTaskList.size())
                listTaskList.setSelectedIndex(selected);

            // refresh preset button labels
            refreshPresetButtonLabels();
        }

        // repaint the whole tab to finalize gui update
        listTaskList.repaint();
    }

    private void refreshTaskListTab() {
        refreshTaskListTab(-1);
    }

    private void refreshTaskLibrary() {
        if (listTaskLibrary.getSelectedValue() == null && !modelTaskLibrary.isEmpty())
            listTaskLibrary.setSelectedIndex(modelTaskLibrary.getSize() - 1);
        listTaskLibrary.repaint();

        // scan for nearby targets to update the nearby targets list
        scanNearbyTargets();
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

    private JPanel createTaskBuilderTab() {
        ///  Create the task builders title
        JPanel panelTaskBuilder = new JPanel(new BorderLayout(15, 15));
        panelTaskBuilder.setBorder(new EmptyBorder(15, 15, 15, 15));
        panelTaskBuilder.setBackground(BG_BASE);

        ///  Create the task builders right panel
        JPanel east = new JPanel(new GridBagLayout());
        east.setOpaque(false);

        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1.0;
        g.insets = new Insets(5, 5, 5, 5);

        taskNameInput = new JTextField(25);
        taskDescriptionInput = new JTextField(25);
        taskStatusInput = new JTextField(25);

        styleComp(taskNameInput);
        styleComp(taskDescriptionInput);
        styleComp(taskStatusInput);

        g.gridy = 0;
        east.add(new JLabel("Task name:"), g);

        g.gridy = 1;
        east.add(taskNameInput, g);

        g.gridy = 2;
        east.add(new JLabel("Description:"), g);

        g.gridy = 3;
        east.add(taskDescriptionInput, g);

        g.gridy = 4;
        east.add(new JLabel("Status:"), g);

        g.gridy = 5;
        east.add(taskStatusInput, g);

        btnTaskBuilderAddToLibrary = createButton("Add to library...", COLOR_ORANGE, null);
        btnTaskBuilderAddToLibrary.addActionListener(e -> {
            List<Action> actions = new ArrayList<>();
            for(int i = 0; i< modelTaskBuilder.size(); i++)
                actions.add(modelTaskBuilder.get(i));

            Task task = createTask(actions);
            if (task != null) {
                boolean exists = false;

                for (int i = 0; i < modelTaskLibrary.getSize(); i++) {
                    if (modelTaskLibrary.getElementAt(i).getName().equalsIgnoreCase(task.getName())) {
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    // Standard logic for a new task
                    modelTaskLibrary.addElement(task);
                    this.makeToast("Added to library!", btnTaskBuilderAddToLibrary, true);
                    resetTaskBuilder();
                } else {
                    // Task name already exists, trigger the overwrite dialogue
                    int choice = JOptionPane.showConfirmDialog(
                            this,
                            task.getName() + " task already exists, would you like to overwrite it?",
                            "Overwrite task?",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE
                    );

                    if (choice == JOptionPane.YES_OPTION) {
                        // find and replace the existing task
                        for (int i = 0; i < modelTaskLibrary.getSize(); i++) {
                            if (modelTaskLibrary.getElementAt(i).toString().equals(task.toString())) {
                                modelTaskLibrary.set(i, task);
                                break;
                            }
                        }
                        this.makeToast("Task updated!", btnTaskBuilderAddToLibrary, true);
                        resetTaskBuilder();
                    } else {
                        // User clicked 'No'
                        this.makeToast("Task creation failed!", btnTaskBuilderAddToLibrary, false);
                    }
                }
            }
        });

        g.gridy = 6;
        g.insets = new Insets(20, 5, 5, 5);
        east.add(btnTaskBuilderAddToLibrary, g);

        JPanel center = new JPanel(new BorderLayout(5, 5)); center.setOpaque(false);
        JLabel setLabel = new JLabel("Action List:", SwingConstants.CENTER);
        setLabel.setForeground(COLOR_BLOOD);
        setLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        styleJList(listTaskBuilder);

        // create navigation buttons (up/down arrows)
        JButton btnUp = createButton("▲");
        JButton btnDown = createButton("▼");

        JPanel navButtons = new JPanel(new GridLayout(0, 1, 0, 5));
        navButtons.setOpaque(false);
        navButtons.add(btnUp);
        navButtons.add(btnDown);

        JButton btnTaskBuilderRemove = createButton("Remove", new Color(100, 0, 0) , null);
        btnTaskBuilderRemove.setEnabled(listTaskBuilder.getSelectedIndex() != -1);
        btnTaskBuilderRemove.addActionListener(e -> {
            int selectedIndex = listTaskBuilder.getSelectedIndex();
            if (selectedIndex != -1) {
                modelTaskBuilder.remove(selectedIndex);
                this.makeToast("Action removed!", btnTaskBuilderRemove, true);
                refreshTaskBuilderTab();
            } else {
                this.makeToast("You must select an action first!", btnTaskBuilderRemove, false);
            }
        });

        listTaskBuilder.addListSelectionListener(e -> {
            btnTaskBuilderRemove.setEnabled(!listTaskBuilder.isSelectionEmpty());
        });

        listTaskBuilder.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int selectedIndex = listTaskBuilder.getSelectedIndex();
                if (selectedIndex == -1)
                    return;

                if (e.getClickCount() == 1) {
                    // Pre-fill the action type and target name inputs from the selected action
                    loadIntoBuilder(modelTaskBuilder.getElementAt(selectedIndex));
                } else if (e.getClickCount() == 2) {
                    // Remove all selected actions
                    int[] selected = listTaskBuilder.getSelectedIndices();
                    for (int i = selected.length - 1; i >= 0; i--)
                        modelTaskBuilder.remove(selected[i]);
                    DreamBotMenu.this.makeToast("Removed " + selected.length + " action(s)!", btnTaskBuilderRemove, true);
                    refreshTaskBuilderTab();
                }
            }
        });

        // create reset button to reset task builder inputs, ready for next task to be created
        JButton btnTaskBuilderReset = createButton("Reset", new Color(50, 50, 50), null);
        btnTaskBuilderReset.addActionListener(e -> {
            this.makeToast("Task builder reset!", btnTaskBuilderReset, true);
            resetTaskBuilder();
        });

        JPanel panelActionButtons = new JPanel(new GridLayout(1, 2, 5, 5));
        panelActionButtons.setOpaque(false);
        panelActionButtons.add(btnTaskBuilderRemove);
        panelActionButtons.add(btnTaskBuilderReset);

        center.add(setLabel, BorderLayout.NORTH);
        center.add(navButtons, BorderLayout.WEST);
        center.add(new JScrollPane(listTaskBuilder), BorderLayout.CENTER);
        center.add(panelActionButtons, BorderLayout.SOUTH);

        JPanel left = new JPanel(new BorderLayout(0, 10));
        left.setOpaque(false);
        JPanel config = new JPanel(new GridLayout(0, 1, 2, 5));
        config.setOpaque(false);
        actionCombo = new JComboBox<>(ActionType.values());
        styleComp(actionCombo);
        actionCombo.addActionListener(e -> {
            scanNearbyTargets();
        });
        inputTargetName = new JTextField(); styleComp(inputTargetName);
        config.add(new JLabel("Select action:"));
        config.add(actionCombo);
        config.add(new JLabel("Target name:"));
        config.add(inputTargetName);

        JButton btnTaskBuilderAdd = createButton("Add to builder...", COLOR_ORANGE, null);
        btnTaskBuilderAdd.addActionListener(e -> {
            if(!inputTargetName.getText().isEmpty()) {
                modelTaskBuilder.addElement(
                        new Action((ActionType) actionCombo.getSelectedItem(), inputTargetName.getText())
                );
                this.makeToast("Added action to builder!", btnTaskBuilderAdd, true);
                refreshTaskBuilderTab(true);
            } else {
                this.makeToast("Enter a target name!", btnTaskBuilderAdd, false);
            }
        });

        config.add(btnTaskBuilderAdd);
        config.add(new JLabel("Nearby targets:"));

        nearbyList = new JList<>(nearbyEntitiesModel);
        styleJList(nearbyList);
        nearbyList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                String val = nearbyList.getSelectedValue();
                if(val == null)
                    return;

                // update target name on single click
                if(e.getClickCount() == 1) {
                    inputTargetName.setText(val);
                }
                else if(e.getClickCount() == 2) {
                    modelTaskBuilder.addElement(new Action((ActionType) actionCombo.getSelectedItem(), val));
                    DreamBotMenu.this.makeToast("Added action to builder!", btnTaskBuilderAdd, true);
                    refreshTaskBuilderTab(true);
                }
            }
        });

        // Wrap the list in a scroll pane and LOCK the width
        JScrollPane entitiesScroll = new JScrollPane(nearbyList);
        entitiesScroll.setPreferredSize(new Dimension(300, 0)); // 300px width, height 0 (BorderLayout will stretch height)
        entitiesScroll.setMinimumSize(new Dimension(300, 0));

        JButton btnScanNearby = createButton("Scan nearby...");
        btnScanNearby.addActionListener(e -> {
            this.makeToast("Scanning for nearby targets...",  btnScanNearby, true);
            scanNearbyTargets();
        });

        left.add(config, BorderLayout.NORTH);
        left.add(entitiesScroll, BorderLayout.CENTER);
        left.add(btnScanNearby, BorderLayout.SOUTH);

        panelTaskBuilder.add(createSubtitle("Task Builder"), BorderLayout.NORTH);
        panelTaskBuilder.add(left, BorderLayout.WEST);
        panelTaskBuilder.add(center, BorderLayout.CENTER);
        panelTaskBuilder.add(east, BorderLayout.EAST);

        ///  Add listeners
        // add component listener to update task builder on show
        panelTaskBuilder.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                refreshTaskBuilderTab();
            }
        });

        listTaskLibrary.addListSelectionListener(e -> {
            Task t = listTaskLibrary.getSelectedValue();
            if (t != null)
                libraryEditorArea.setText(t.getEditableString());
        });


        return panelTaskBuilder;
    }

    public void removeTask(JList<Task> list, DefaultListModel<Task> model, JButton btn) {
        try {
            int selectedIndex = list.getSelectedIndex();
            if (selectedIndex != -1) {
                model.remove(selectedIndex);
                this.makeToast("Removed task!", btn, true);
            } else {
                this.makeToast("Select a Task to remove!", btn, false);
            }

            // this get element is a dummy task which throws an error if index is invalid, which will trigger refresh methods
            model.getElementAt(selectedIndex);
            list.setSelectedIndex(selectedIndex);

        } catch (Exception e) {
            refreshTaskListTab();
            refreshTaskBuilderTab();
        }
    }

    private Task createTask(List<Action> actions) {
        try {
            ///  fetch user input (throws null exception if invalid)
            String name = taskNameInput.getText();
            String description = taskDescriptionInput.getText();
            String status = taskStatusInput.getText();

            if (actions == null || actions.isEmpty())
                throw new Exception("Add some actions!");

            if (name.isEmpty())
                throw new Exception("Enter a valid name!");

            if (isTaskDescriptionRequired && description.isEmpty())
                throw new Exception("Enter a valid description!");

            if (isTaskStatusRequired && status.isEmpty())
                throw new Exception("Enter a valid status!");

            return new Task(name, description, actions, status);

        } catch (Exception e) {
            this.makeToast(e.getMessage(), btnTaskBuilderAddToLibrary, false);
            return null;
        }
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
        settingGroup.add(createInterfacesPanel(), "Interfaces");
        settingGroup.add(createWarningsPanel(), "Warnings");

        JPanel menuPanel = new JPanel(new GridLayout(10, 1, 0, 2));
        menuPanel.setPreferredSize(new Dimension(180, 0));
        menuPanel.setBackground(PANEL_SURFACE); menuPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_DIM));

        String[] groups = {"Client", "Script", "Display", "Audio", "Chat", "Controls", "Activities", "Warnings"};
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

    private void resetTaskBuilder() {
        inputTargetName.setText("");

        // clear task attribute inputs
        taskNameInput.setText("");
        taskDescriptionInput.setText("");
        taskStatusInput.setText("");

        // clear the action list display
        modelTaskBuilder.clear();

        // clear/refresh target inputs
        scanNearbyTargets();
    }

    private void scanNearbyTargets() {
        ActionType type = (ActionType) actionCombo.getSelectedItem();
        if (type == null)
            return;

        // Perform the heavy API scanning on the current thread (Script Thread)
        Set<String> names;

        switch (type) {
            case ATTACK:
                names = NPCs.all().stream()
                        .filter(n -> n.hasAction("Attack"))
                        .map(NPC::getName)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                break;

            case CHOP:
                names = GameObjects.all().stream()
                        .filter(o -> o.hasAction("Chop down"))
                        .map(GameObject::getName)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                break;

            case MINE:
                names = GameObjects.all().stream()
                        .filter(o -> o.hasAction("Mine"))
                        .map(GameObject::getName)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                break;

            default:
                names = NPCs.all().stream()
                        .map(NPC::getName)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                break;
        }

        // Copy to a final list for the thread block
        final List<String> sortedNames = names.stream().sorted().collect(Collectors.toList());
        SwingUtilities.invokeLater(() -> { //TODO remove redundant? invoke later here
            nearbyEntitiesModel.clear();

            for (String name : sortedNames)
                nearbyEntitiesModel.addElement(name);

            // Use your toast logic to confirm it finished
            this.makeToast("Found " + sortedNames.size() + " targets", btnTaskBuilderScanNearby, true);
        });
    }

    // --- Inner Classes ---
    public static class Task {
        private String name;
        private String description;
        private String status;
        private List<Action> actions;

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

            // DEEP COPY logic:
            this.actions = new ArrayList<>();
            if (o.actions != null) {
                for (Action originalAction : o.actions) {
                    // Create a brand new action object for the new list
                    this.actions.add(new Action(originalAction));
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

        @Override public String toString() {
            return name;
        }
    }

    public static class Action {
        @SerializedName("type")
        public ActionType actionType;
        public String target;

        public Action(ActionType t, String target) {
            this.actionType = t;
            this.target = target;
        }

        // copy-cat constructor
        public Action(Action other) {
            this.actionType = other.actionType;
            this.target = other.target;
        }

        //TODO copy how CHOP has been done then extract out into their own action classes maybe?
        public boolean execute() {
            switch (actionType) {
                case CHOP:
                    // find the nearest target tree
                    GameObject tree = GameObjects.closest(target);
                    // return early if no nearby trees
                    if (tree == null) {
                        Logger.log(Logger.LogType.ERROR, "Unable to find a tree!");
                        return false;
                    }
                    // attempt to chop the tree down
                    return tree.interact("Chop down");

                case ATTACK:
                    // find the nearest target npc
                    NPC npc = NPCs.closest(target);

                    // return early if no npcs found
                    if (npc == null) {
                        Logger.log(Logger.LogType.ERROR, "Unable to find a npc!");
                        return false;
                    }

                    // attempt to attack the target npc
                    return  npc.interact("Attack");

                case BANK:
                    return Bank.open();

                case MINE:
                    return GameObjects.closest(target) != null && GameObjects.closest(target).interact("Mine");

                case TALK_TO:
                    return NPCs.closest(target) != null && NPCs.closest(target).interact("Talk-to");

                case DROP:
                    return Inventory.dropAll(target);

                default:
                    Logger.log("Action " + actionType + " not implemented in Action.execute()");
                    return false;
            }
        }
        @Override public String toString() { return actionType.name() + " -> " + target; }
    }

    public static class Preset {
        String name;
        List<Task> tasks;

        public Preset(String name, List<Task> tasks) {
            this.name = name;
            this.tasks = tasks;
        }
    }

    public boolean makeToast(String toastText, JComponent component, boolean success) {
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

    public void loadTaskList() {
        new Thread(() -> {
            // Use the method from your DataMan class to get the raw JSON
            String rawJson = dataMan.loadDataByPlayer("tasks");
            SwingUtilities.invokeLater(() -> {
                if (rawJson != null)
                    unpackTaskList(rawJson);
            });
        }).start();
    }

    public void loadTaskLibrary() {
        new Thread(() -> {
            // Use the method from your DataMan class to get the raw JSON
            String rawJson = dataMan.loadDataByPlayer("library");
            SwingUtilities.invokeLater(() -> {
                if (rawJson != null)
                    unpackTaskLibrary(rawJson);
            });
        }).start();
    }

    public void loadTaskBuilder() {
        new Thread(() -> {
            String rawJson = dataMan.loadDataByPlayer("builder");
            SwingUtilities.invokeLater(() -> {
                if (rawJson != null)
                    unpackTaskBuilder(rawJson);
            });
        }).start();
    }

    public void loadSettings() {
        new Thread(() -> {
            String rawJson = dataMan.loadDataByPlayer("settings");
            SwingUtilities.invokeLater(() -> {
                if (rawJson != null)
                    unpackSettings(rawJson);
            });
        }).start();
    }

    public void loadPresets() {
        new Thread(() -> {
            String rawJson = dataMan.loadDataByPlayer("presets");
            SwingUtilities.invokeLater(() -> {
                if (rawJson != null)
                    unpackPresets(rawJson);
            });
        }).start();
    }


    private void unpackTaskList(String json) {
        if (json == null || json.isEmpty() || json.equals("[]")) {
            Logger.log("No task list data found to unpack.");
            return;
        }

        try {
            Gson gson = new Gson();

            // Supabase wraps the response in an outer array: [ { "tasks": [...] } ]
            // "tasks" is now a List, not a Map
            java.lang.reflect.Type wrapperType = new TypeToken<List<Map<String, List<Task>>>>(){}.getType();
            List<Map<String, List<Task>>> responseList = gson.fromJson(json, wrapperType);

            if (responseList != null && !responseList.isEmpty()) {
                List<Task> fetchedTasks = responseList.get(0).get("tasks");

                if (fetchedTasks != null) {
                    SwingUtilities.invokeLater(() -> {
                        modelTaskList.clear();
                        for (Task task : fetchedTasks) {
                            modelTaskList.addElement(task);
                        }
                        refreshTaskListTab();
                        Logger.log("Successfully unpacked " + modelTaskList.size() + " tasks into the Task List");
                    });
                }
            }
            listTaskList.repaint();
        } catch (Exception e) {
            Logger.log(Logger.LogType.ERROR, "Failed to unpack Task List data: " + e.getMessage());
            e.printStackTrace();
        }
    }


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
                        snap.actions.forEach(modelTaskBuilder::addElement);
                    }
                    taskNameInput.setText(snap.taskName != null ? snap.taskName : "");
                    taskDescriptionInput.setText(snap.taskDescription != null ? snap.taskDescription : "");
                    taskStatusInput.setText(snap.taskStatus != null ? snap.taskStatus : "");
                    inputTargetName.setText(snap.targetName != null ? snap.targetName : "");

                    if (snap.selectedAction != null && !snap.selectedAction.isEmpty()) {
                        try {
                            actionCombo.setSelectedItem(ActionType.valueOf(snap.selectedAction));
                        } catch (Exception ignored) {}
                    }

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
                        settingClientChkStartScriptOnLoad.setSelected(snap.startScriptOnLoadDisabled);
                        // update the class field // TODO see if this field is necessary or if the checkbox can be used directly instead, perhaps reference the checkbox from a function if too inconvenient?
                        this.startScriptOnLoad = snap.startScriptOnLoadDisabled;
                    }

                    if (settingClientChkExitOnStopWarning != null) {
                        settingClientChkExitOnStopWarning.setSelected(snap.exitScriptOnStopWarningEnabled);
                        // update the class field
                        this.exitOnStopWarning = snap.exitScriptOnStopWarningEnabled;
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

                    if (snap.startScriptOnLoadDisabled) { // or your renamed field
                        Logger.log("Auto-start detected! Starting script...");
                        toggleScriptState();
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

            if (fetchedPresets != null) {
                SwingUtilities.invokeLater(() -> {
                    allPresets.clear();
                    allPresets.addAll(fetchedPresets);
                    selectedPresetIndex = -1; // Reset selection on fresh load
                    refreshPresetButtonLabels();
                    Logger.log(Logger.LogType.SCRIPT, "Successfully unpacked " + allPresets.size() + " presets from the presets column.");
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
            setStatus("Status: Syncing profile...");

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
                setStatus("Status: Profile Synced!");

                // FIX: Ensure Toast creation and UI updates happen on the EDT
                SwingUtilities.invokeLater(() -> {
                    // Check if the Settings tab is currently showing to decide where to anchor
                    // If you want to avoid "random toasts," anchor to the mainTabs or a specific header label
                    makeToast("All settings synced.", mainTabs, true);
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
        // dynamically capture the current background color to restore it later
        Color original = component.getBackground();

        component.setBackground(flashColor);
        component.repaint();

        Timer revertTimer = new Timer(TIME_FLASH, e -> {
            component.setBackground(original);
            component.repaint();
        });

        revertTimer.setRepeats(false);
        revertTimer.start();
    }

    private void loadIntoBuilder(Task task) {
        if(task == null)
            return;

        taskNameInput.setText(task.name);
        taskDescriptionInput.setText(task.description);
        taskStatusInput.setText(task.status);
        modelTaskBuilder.clear(); task.actions.forEach(modelTaskBuilder::addElement);
    }

    private void loadIntoBuilder(Action action) {
        if (action == null)
            return;

        actionCombo.setSelectedItem(action.actionType);
        inputTargetName.setText(action.target);
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PANEL_SURFACE);
        header.setPreferredSize(new Dimension(0, 85));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_DIM));
        JLabel titleLabel = new JLabel(" DreamBotMan", SwingConstants.LEFT);
        titleLabel.setForeground(COLOR_BLOOD);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 32));
        titleLabel.setBorder(new EmptyBorder(0, 25, 0, 0));
        JPanel rightContainer = new JPanel(new BorderLayout());
        rightContainer.setOpaque(false);
        rightContainer.setBorder(new EmptyBorder(0, 0, 0, 20));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 20));
        controls.setOpaque(false);
        btnPlayPause = createIconButton("▶", "Play", e -> toggleScriptState());
        JButton btnStop = createIconButton("■", "Stop", e -> stopScript());
        btnInputToggle = createIconButton("🖱", "Input", e -> toggleUserInput());
        controls.add(btnPlayPause);
        controls.add(btnStop); controls.add(btnInputToggle);
        JPanel headerStats = new JPanel(new GridLayout(2, 1));
        headerStats.setOpaque(false);
        headerStats.setBorder(new EmptyBorder(10, 20, 10, 10));
        rightContainer.add(controls, BorderLayout.CENTER);
        rightContainer.add(headerStats, BorderLayout.EAST);
        header.add(titleLabel, BorderLayout.WEST);
        header.add(rightContainer, BorderLayout.EAST);

        return header;
    }

    private void toggleScriptState() {
        if (script == null)
            return;

        if (isScriptPaused) {
            script.getScriptManager().resume();
            btnPlayPause.setText("▮▮");
            isScriptPaused = false;
            setStatus("Status: Running");
        } else {
            script.getScriptManager().pause();
            btnPlayPause.setText("▶");
            isScriptPaused = true;
            setStatus("Status: Script paused");
        }
    }

    private final void stopScript() {
        //TODO see if this needs to be called from Dream Bot when children call stop()?
        if (!exitOnStopWarning || JOptionPane.showConfirmDialog(this, "This may result in the loss of unsaved changes.\nAre you sure you want to exit?") == JOptionPane.YES_OPTION) {
            script.stop();
            dispose();
        }
    }

    private void toggleUserInput() { isUserInputAllowed = !isUserInputAllowed; Client.getInstance().setMouseInputEnabled(isUserInputAllowed); Client.getInstance().setKeyboardInputEnabled(isUserInputAllowed); btnInputToggle.setText(isUserInputAllowed ? "🖱" : "🚫"); }
    private void styleHeaderLabel(JLabel l) { l.setForeground(TEXT_MAIN); l.setFont(new Font("Consolas", Font.BOLD, 15)); l.setHorizontalAlignment(SwingConstants.RIGHT); }
    private void styleSpinner(JSpinner s) { JFormattedTextField field = ((JSpinner.DefaultEditor) s.getEditor()).getTextField(); field.setBackground(new Color(30, 30, 30)); field.setForeground(COLOR_BLOOD); s.setBorder(new LineBorder(BORDER_DIM)); }
    private JButton createIconButton(String symbol, String tooltip, ActionListener action) { JButton btn = new JButton(symbol); btn.setPreferredSize(new Dimension(40, 40)); btn.setFont(new Font("Segoe UI Symbol", Font.BOLD, 18)); btn.setBackground(new Color(30, 0, 0)); btn.setForeground(COLOR_BLOOD); btn.addActionListener(action); return btn; }

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

    private void styleComp(JComponent c) {
        c.setBackground(PANEL_SURFACE);
        c.setForeground(TEXT_MAIN);

        if(c instanceof JTextField)
            ((JTextField)c).setCaretColor(COLOR_BLOOD);
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
    private JButton createNavButton(@NotNull String btnText) {
        if (btnText.isEmpty())
            throw new IllegalArgumentException("Error creating button! Button text cannot be empty");

        JButton btn = new JButton(btnText);
            btn.setBackground(COLOR_BTN_BACKGROUND);
            btn.setForeground(COLOR_BTN_FOREGROUND);
            btn.setFocusPainted(false);
            btn.setBorder(new LineBorder(BORDER_DIM));

       return addButtonFeatures(btn);
    }

    private JButton addButtonFeatures(JButton btn) {
        // determine if this button is a navigation button or not to add extra features
        int dir = btn.getText().equals("▲") ? -1 : btn.getText().equals("▼") ? 1 : 0;
        // return early if this is not a navigation button to avoid adding features to the wrong button type
        if (dir == 0)
            return btn;

        // add the button on-click event for navigation buttons
        btn.addActionListener(e -> {
            // determine if this button event is successful or not
            boolean success = navigateQueue(dir, listTaskList, modelTaskList);
            // add a flash to this control to flicker colors on click
            flashControl(btn, success ? COLOR_SUCCESS : COLOR_FAILURE);
        });

        return btn;
    }

    private JButton createButton(@NotNull String btnText, Color backgroundColor, Color foregroundColor) {
        JButton b = createNavButton(btnText);
            b.setBackground(backgroundColor == null ? COLOR_BTN_BACKGROUND : backgroundColor);
            b.setForeground(foregroundColor == null ? COLOR_BTN_FOREGROUND : foregroundColor);

        return b;
    }

    private JButton createButton(@NotNull String btnText) {
        return createButton(btnText, null, null);
    }

    private void makeToast(String toastText, JButton component, boolean success) {
        if (toastText == null || component == null)
            throw new IllegalArgumentException("Error creating button! Invalid text or JComponent parameter(s).");

        this.makeToast(toastText, component, success);
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
                int xp = Skills.getExperience(data.skill);
                int lvl = Skills.getRealLevel(data.skill);
                data.update(xp, lvl, startTime, projH);
                p2pTotal += lvl;
                if (F2P_SKILLS.contains(data.skill))
                    f2pTotal += lvl;

                if (data.isTracking) {
                    totalXPGained += Math.max(0, (xp - data.startXP));
                    totalLevelsGained += Math.max(0, (lvl - data.startLevel));
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

        });
    }

    private JPanel createInfoCard(String title) { JPanel p = new JPanel(new GridLayout(0, 1, 5, 10)); p.setBackground(PANEL_SURFACE); TitledBorder b = BorderFactory.createTitledBorder(new LineBorder(BORDER_DIM), " " + title + " "); b.setTitleColor(COLOR_BLOOD); b.setTitleFont(new Font("Segoe UI", Font.BOLD, 16)); p.setBorder(BorderFactory.createCompoundBorder(b, new EmptyBorder(15, 15, 15, 15))); return p; }
    private void addInfoRow(JPanel p, String key, JLabel valLabel) { JPanel row = new JPanel(new BorderLayout()); row.setOpaque(false); JLabel k = new JLabel(key); k.setForeground(TEXT_DIM); valLabel.setForeground(TEXT_MAIN); valLabel.setFont(new Font("Consolas", Font.BOLD, 14)); row.add(k, BorderLayout.WEST); row.add(valLabel, BorderLayout.EAST); row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 40, 40))); p.add(row); }
    private void addInfoRowWithIcon(JPanel p, String key, JLabel valLabel, JLabel iconLabel) { JPanel row = new JPanel(new BorderLayout(5, 0)); row.setOpaque(false); JLabel k = new JLabel(key); k.setForeground(TEXT_DIM); JPanel rightSide = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0)); rightSide.setOpaque(false); valLabel.setForeground(TEXT_MAIN); rightSide.add(valLabel); rightSide.add(iconLabel); row.add(k, BorderLayout.WEST); row.add(rightSide, BorderLayout.EAST); row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 40, 40))); p.add(row); }

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
                                startScriptOnLoad = !settingClientChkStartScriptOnLoad.isSelected()
                ),

                settingClientChkExitOnStopWarning = createSettingCheck("Exit on Stop Warning",
                        exitOnStopWarning, e ->
                                exitOnStopWarning = !settingClientChkExitOnStopWarning.isSelected()
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
                        makeToast("Auto-save Disabled", chkAutoSave, false);
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

    ///  Define Interfaces Settings sub-tab
    private JPanel createInterfacesPanel() {
        return createSettingsGroup("Interfaces",
                chkDataOrbs = createSettingCheck("Show data orbs",
                        ClientSettings.areDataOrbsEnabled(), e ->
                                ClientSettings.toggleDataOrbs(((JCheckBox)e.getSource()).isSelected()))
        );
    }

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
                makeToast("Bot is busy ingame...", c, false);
                return;
            }

            isSettingProcessing = true;
            String originalStatus = lblStatus.getText();
            setStatus("Status: Adjusting " + text + " ingame...");

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
                        makeToast(text + " updated!", c, true);
                    });
                }
            }).start();
        });

        return c;
    }

    private JToggleButton createMenuButton(String text) { JToggleButton btn = new JToggleButton(text) { protected void paintComponent(Graphics g) { g.setColor(isSelected() ? TAB_SELECTED : PANEL_SURFACE); g.fillRect(0, 0, getWidth(), getHeight()); super.paintComponent(g); } }; btn.setFocusPainted(false); btn.setContentAreaFilled(false); btn.setForeground(TEXT_MAIN); btn.setFont(new Font("Segoe UI", Font.BOLD, 14)); btn.setHorizontalAlignment(SwingConstants.LEFT); btn.setBorder(new EmptyBorder(0, 20, 0, 0)); return btn; }

    private JPanel createSkillTile(SkillData data) { JPanel tile = new JPanel(new GridBagLayout()); tile.setBackground(PANEL_SURFACE); tile.setBorder(new LineBorder(BORDER_DIM)); GridBagConstraints gbc = new GridBagConstraints(); gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0; gbc.gridx = 0; JPanel top = new JPanel(new BorderLayout()); top.setOpaque(false); JLabel icon = new JLabel(loadSkillIcon(data.skill)); data.lblLevel.setForeground(COLOR_BLOOD); data.lblLevel.setFont(new Font("Arial", Font.BOLD, 18)); top.add(icon, BorderLayout.WEST); top.add(data.lblLevel, BorderLayout.EAST); data.lblXpString.setForeground(TEXT_DIM); data.lblXpString.setFont(new Font("Monospaced", Font.PLAIN, 10)); data.mainBar.setForeground(COLOR_BLOOD); data.mainBar.setBackground(Color.BLACK); gbc.gridy = 0; tile.add(top, gbc); gbc.gridy = 1; tile.add(data.lblXpString, gbc); gbc.gridy = 2; tile.add(data.mainBar, gbc);
        tile.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    data.isTracking = !data.isTracking;
                    tile.setBorder(new LineBorder(data.isTracking ? COLOR_BLOOD : BORDER_DIM, 1));
                    refreshTrackerList();
                }
            }
        });

        return tile;
    }

    private void refreshTrackerList() { trackerList.removeAll(); skillRegistry.values().stream().filter(d -> d.isTracking).forEach(d -> { trackerList.add(d.trackerPanel); trackerList.add(Box.createRigidArea(new Dimension(0, 10))); }); trackerList.add(Box.createVerticalGlue()); trackerList.revalidate(); trackerList.repaint(); }

    public void updateAll() {
        if (isDataLoading) return; // Block if already loading

        new Thread(() -> {
            isDataLoading = true;
            setStatus("Status: Waiting for login...");

            // 1. Wait for stable login
            while (!Client.isLoggedIn()) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {}
            }

            Logger.log("Player logged in. Fetching data...");

            // 2. Clear old data on the EDT before fetching new data
            SwingUtilities.invokeLater(() -> {
                modelTaskList.clear();
                modelTaskLibrary.clear();
                allPresets.clear(); // Clear presets before loading fresh from the presets column
                updateUI();
            });

            // 3. Fetch data sequentially
            loadSettings();
            loadTaskList();
            loadTaskLibrary();
            loadTaskBuilder();
            loadPresets(); // Fetching from the presets column

            isDataLoading = false;
        }).start();
    }

    /**
     * Captures the current state of all presets to be saved into the dedicated presets column.
     * @return A deep-copy list of all current Preset objects.
     */
    private List<Preset> capturePresets() {
        List<Preset> snapshot = new ArrayList<>();
        for (Preset p : allPresets) {
            snapshot.add(new Preset(p.name, new ArrayList<>(p.tasks)));
        }
        return snapshot;
    }

    public void saveAll() {
        new Thread(() -> {
            if (!Client.isLoggedIn())
                return;

            setStatus("Status: Autosaving...");

            dataMan.saveEverything(
                    listTaskList,
                    listTaskLibrary,
                    captureBuilderSnapshot(),
                    captureSettingsSnapshot(),
                    capturePresets(), // Sending this directly to your presets column
                    captureLocation(),
                    captureInventory(),
                    captureWorn(),
                    captureSkills(),
                    () -> setStatus("Status: Autosave complete!")
            );

            Logger.log(Logger.LogType.INFO, "Autosaving all data columns...");
        }).start();
    }

    /**
     * Helper function to track and highlight the tasks as they are executed.
     */
    private class TaskCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel selectedTask = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (currentExecutionIndex != -1 && index == currentExecutionIndex) {
                selectedTask.setForeground(Color.YELLOW);
                selectedTask.setText("→ " + selectedTask.getText());
                selectedTask.setFont(new Font("Consolas", Font.BOLD, 12));
            }
            return selectedTask;
        }
    }

    private class SkillData { final Skill skill; final JProgressBar mainBar = new JProgressBar(0, 100); final JLabel lblLevel = new JLabel("1"), lblXpString = new JLabel("0/0"); final JPanel trackerPanel = new JPanel(new GridLayout(0, 1, 2, 2)); final JLabel lblGained = new JLabel(), lblPerHour = new JLabel(), lblRemaining = new JLabel(), lblTTL = new JLabel(), lblProj = new JLabel(), lblActs = new JLabel(); final int startXP, startLevel; boolean isTracking = false; SkillData(Skill s) { this.skill = s; this.startXP = Skills.getExperience(s); this.startLevel = Skills.getRealLevel(s); trackerPanel.setBackground(new Color(30, 30, 30)); TitledBorder b = BorderFactory.createTitledBorder(new LineBorder(BORDER_DIM), " " + s.name() + " "); b.setTitleColor(COLOR_BLOOD); trackerPanel.setBorder(b); JLabel[] ls = {lblGained, lblPerHour, lblRemaining, lblActs, lblTTL, lblProj}; for (JLabel l : ls) { l.setForeground(TEXT_MAIN); l.setFont(new Font("Consolas", Font.PLAIN, 12)); trackerPanel.add(l); } } void update(int curXp, int curLvl, long start, int ph) { int curMin = Skills.getExperienceForLevel(curLvl), curMax = Skills.getExperienceForLevel(curLvl + 1); lblLevel.setText(String.valueOf(curLvl)); lblXpString.setText(String.format("%,d / %,d XP", curXp, curMax)); mainBar.setValue((int) (((double)(curXp - curMin) / Math.max(1, curMax - curMin)) * 100)); long elapsed = System.currentTimeMillis() - start; int xph = (int) (Math.max(0, curXp - startXP) / Math.max(0.0001, elapsed / 3600000.0)); int rem = Math.max(0, curMax - curXp); lblGained.setText(" GAINED: " + String.format("%,d XP", curXp - startXP)); lblPerHour.setText(" XP/HR:  " + String.format("%,d", xph)); lblRemaining.setText(" TO LEVEL: " + String.format("%,d", rem)); if (xph > 0) { lblTTL.setText(String.format(" TIME TO L: %.2f hrs", (double) rem / xph)); lblProj.setText(String.format(" PROJ (%dH): Lvl %d", ph, curLvl + (xph * ph / 100000))); } } }

    // --- Snapshot Classes & Capture Methods ---
    public static class BuilderSnapshot {
        public String selectedAction;
        public String targetName;
        public String taskName;
        public String taskDescription;
        public String taskStatus;
        public List<Action> actions;
    }

    private BuilderSnapshot captureBuilderSnapshot() {
        BuilderSnapshot snapshot = new BuilderSnapshot();
        snapshot.taskName = taskNameInput != null ? taskNameInput.getText() : "";
        snapshot.taskDescription = taskDescriptionInput != null ? taskDescriptionInput.getText() : "";
        snapshot.taskStatus = taskStatusInput != null ? taskStatusInput.getText() : "";
        snapshot.selectedAction = actionCombo != null && actionCombo.getSelectedItem() != null
                ? actionCombo.getSelectedItem().toString() : "";
        snapshot.targetName = inputTargetName != null ? inputTargetName.getText() : "";
        snapshot.actions = new ArrayList<>();
        for (int i = 0; i < modelTaskBuilder.getSize(); i++)
            snapshot.actions.add(modelTaskBuilder.getElementAt(i));

        return snapshot;
    }

    public static class SettingsSnapshot {
        public boolean startScriptOnLoadDisabled;
        public boolean exitScriptOnStopWarningEnabled;
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
        s.startScriptOnLoadDisabled
                = settingClientChkStartScriptOnLoad != null
                && settingClientChkStartScriptOnLoad.isSelected();

        s.exitScriptOnStopWarningEnabled
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

    // --- PRESET LOGIC ADDITIONS ---

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
        btnPageUp = createButton("▲");
        btnPageDown = createButton("▼");

        btnPageUp.setPreferredSize(new Dimension(30, 0));

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
                this.makeToast("Page " + (currentPresetPage + 1), btnPageDown, true);
            } else {
                // Determine if failure is due to hard limit or empty slots on current page
                String message = (currentPresetPage + 1) * 4 >= MAX_PRESETS ?
                        String.format("Max presets reached! (%d)", MAX_PRESETS)
                        : "Fill current slots first!";

                this.makeToast(message, btnPageDown, false);
            }
        });

        nav.add(btnPageUp);
        nav.add(btnPageDown);

        container.add(grid, BorderLayout.CENTER);
        container.add(nav, BorderLayout.EAST);
        return container;
    }

    private void handlePresetClick(int slot, ActionEvent e) {
        int actualIndex = (currentPresetPage * 4) + slot;
        boolean shift = (e.getModifiers() & ActionEvent.SHIFT_MASK) != 0;
        boolean ctrl = (e.getModifiers() & ActionEvent.CTRL_MASK) != 0;

        // Ensure list growth defaults to proper "Preset X" formatting
        while (allPresets.size() <= actualIndex) {
            allPresets.add(new Preset("Preset " + (allPresets.size() + 1), new ArrayList<>()));
        }

        ///  Ctrl + shift click preset to rename
        if (ctrl && shift) {
            String newName = JOptionPane.showInputDialog(this, "Enter preset name:");
            if (newName != null && !newName.trim().isEmpty())
                allPresets.get(actualIndex).name = newName.trim();

        /// Shift click preset to save
        } else if (shift) {
            List<Task> currentTasks = new ArrayList<>();

            for (int i = 0; i < modelTaskList.size(); i++)
                currentTasks.add(new Task(modelTaskList.getElementAt(i)));

            String currentName = allPresets.get(actualIndex).name;
            allPresets.set(actualIndex, new Preset(currentName, currentTasks));
            // TODO decide whether or not to select the preset on save
            selectedPresetIndex = actualIndex; // Select it upon saving
            refreshPresetButtonLabels();
            this.makeToast("Saved to " + currentName, presetButtons[slot], true);

        /// Normal click to load
        } else {
            // fetch the selected preset object using the preset buttons index
            Preset preset = allPresets.get(actualIndex);

            if (preset.tasks.isEmpty()) {
                this.makeToast(preset.name + " is empty!", presetButtons[slot], false);
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

                // inform the user that the preset has been loaded and how many tasks this set has.
                loadPreset(actualIndex);
                this.makeToast("Loaded " + taskCount + " tasks from " + preset.name, presetButtons[slot], true);
            }
        }
    }

    private void loadPreset(int index) {
        try {
            ///  Fetch the preset object using the passed index
            Preset p = allPresets.get(index);

            ///  Validate the passed index
            if (p == null)
                throw new IndexOutOfBoundsException("Unable to index a null preset object!");

            if (p.tasks.isEmpty())
                throw new IndexOutOfBoundsException("Unable to index a empty preset object!");

            ///  Perform the load operation
            // update selected preset index
            selectedPresetIndex = index;
            // highlight the preset button by using index modulus preset_columns
            int localButtonIndex = index % PRESET_COLUMNS;

            // highlight the specific button from your array
            presetButtons[localButtonIndex].setSelected(true);
            refreshTaskListTab();

        } catch (IndexOutOfBoundsException i) {
            Logger.log(Logger.LogType.ERROR, i.getMessage());
            i.printStackTrace();
        }
        
    }

    private void promptAndDeletePreset() {
        if (selectedPresetIndex == -1 || selectedPresetIndex >= allPresets.size()) {
            return; // Safety guard: ignore delete press if nothing valid is selected
        }

        Preset p = allPresets.get(selectedPresetIndex);
        int choice = JOptionPane.showConfirmDialog(this,
                "WARNING: You are about to delete '" + p.name + "'.\nThis will shift all subsequent presets down. Continue?",
                "Delete Preset?",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            // Squashes the ArrayList left automatically
            allPresets.remove(selectedPresetIndex);

            // Clear selection because it's been removed
            selectedPresetIndex = -1;

            // If squashing makes the current page completely empty, bump back a page
            if (currentPresetPage > 0 && (currentPresetPage * 4) >= allPresets.size()) {
                currentPresetPage--;
                if (currentPresetPage == 0) btnPageUp.setEnabled(false);
            }

            refreshPresetButtonLabels();
            makeToast("Preset Deleted & Squashed", mainTabs, true);
        }
    }

    private void refreshPresetButtonLabels() {
        for (int i = 0; i < 4; i++) {
            int actualIndex = (currentPresetPage * 4) + i;

            // Reset foreground explicitly to prevent unreadable text
            presetButtons[i].setForeground(Color.WHITE);

            if (actualIndex < allPresets.size()) {
                Preset p = allPresets.get(actualIndex);
                presetButtons[i].setText(p.name);

                // Color Logic mapped exactly to your request:
                if (actualIndex == selectedPresetIndex) {
                    presetButtons[i].setBackground(COLOR_ORANGE); // Selected
                } else if (p.tasks.isEmpty()) {
                    presetButtons[i].setBackground(COLOR_BTN_BACKGROUND);   // Empty
                } else {
                    presetButtons[i].setBackground(COLOR_BLOOD);  // Filled
                }
            } else {
                // Formatting for uninitialized presets beyond current size
                presetButtons[i].setText("Preset " + (actualIndex + 1));
                presetButtons[i].setBackground(actualIndex == selectedPresetIndex ? COLOR_ORANGE : COLOR_BTN_BACKGROUND);
            }
        }
    }

    private boolean canExpandPresets() {
        // Check if current 4 are filled
        for (int i = 0; i < 4; i++) {
            int idx = (currentPresetPage * 4) + i;
            if (idx >= allPresets.size() || allPresets.get(idx).tasks.isEmpty())
                return false;
        }

        // check if next page size exceeds preset limit
        return ((currentPresetPage + 1) * 4) < MAX_PRESETS;
    }
}