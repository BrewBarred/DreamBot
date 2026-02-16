package main.menu;

import com.google.gson.Gson;
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
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.NPC;

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
    //TODO SETTINGS:
    ///  DEV Settings:
    // --- Task ---
    boolean isTaskDescriptionRequired = false;
    boolean isTaskStatusRequired = false;
    // --- State ---
    private boolean isScriptPaused = true;
    private boolean isUserInputAllowed = true;
    private int currentExecutionIndex = 0;

    private final AbstractScript script;
    private final JPanel sidePanel;

    private final Color COLOR_RED = new Color(100, 0, 0);
    private final Color COLOR_GREEN = new Color(0, 100, 0);
    private final Color COLOR_GREY = new Color(40,40,40);

    // --- Data & Presets ---
    private final DataMan dataMan = new DataMan();

    private final Map<Skill, SkillData> skillRegistry = new EnumMap<>(Skill.class);

    private final DefaultListModel<Task> modelTaskList = new DefaultListModel<>();
    private final JList<Task> listTaskList = new JList<>(modelTaskList);

    private final DefaultListModel<Task> modelTaskLibrary = new DefaultListModel<>();
    private final JList<Task> listTaskLibrary = new JList<>(modelTaskLibrary);

    private final DefaultListModel<Action> modelTaskBuilder = new DefaultListModel<>();
    private final JList<Action> listTaskBuilder = new JList<>(modelTaskBuilder);

    private final DefaultListModel<String> nearbyEntitiesModel = new DefaultListModel<>();


    private final List<List<Task>> presets = new ArrayList<>(Arrays.asList(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));

    // --- UI Components ---
    private final JTabbedPane mainTabs = new JTabbedPane();
    private final JButton[] presetButtons = new JButton[4];
    private final JPanel trackerList;
    private final JLabel totalXpGainedLabel = new JLabel();
    private final JLabel totalLevelsGainedLabel = new JLabel();
    private final JLabel totalLevelLabelP2P = new JLabel();
    private final JLabel totalLevelLabelF2P = new JLabel();
    private final JProgressBar statusProgress = new JProgressBar(0, 100);
    private final JLabel lblStatus = new JLabel("Status: Idle");
    private final JSpinner projectionSpinner;
    private final long startTime;
    private JButton btnTaskBuilderCreateTask;
    private JButton btnTaskBuilderRefreshList;

    private JList<String> nearbyList;
    private JTextArea libraryEditorArea, consoleArea;
    private JTextField taskNameInput, taskDescriptionInput, taskStatusInput, manualTargetInput, consoleSearch;
    private JComboBox<ActionType> actionCombo;
    private JButton btnPlayPause, btnInputToggle, btnCaptureToggle;

    // --- Theme ---
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
    private final JLabel lblPassword = new JLabel("...");
    private final JLabel lblAcctId = new JLabel("...");
    private final JLabel lblAcctStatus = new JLabel("...");
    private final JLabel lblCharName = new JLabel("...");
    private final JLabel lblWorld = new JLabel("-");
    private final JLabel lblCoords = new JLabel("-");
    private final JLabel lblGameState = new JLabel("-");
    private final JLabel lblMemberIcon = new JLabel();
    private final JLabel lblMemberText = new JLabel("-");

    public enum ActionType { ATTACK, BANK, BURY, CHOP, COOK, DROP, EXAMINE, FISH, MINE, OPEN, TALK_TO, USE_ON }

    private static final Skill[] OSRS_ORDER = { Skill.ATTACK, Skill.HITPOINTS, Skill.MINING, Skill.STRENGTH, Skill.AGILITY, Skill.SMITHING, Skill.DEFENCE, Skill.HERBLORE, Skill.FISHING, Skill.RANGED, Skill.THIEVING, Skill.COOKING, Skill.PRAYER, Skill.CRAFTING, Skill.FIREMAKING, Skill.MAGIC, Skill.FLETCHING, Skill.WOODCUTTING, Skill.RUNECRAFTING, Skill.SLAYER, Skill.FARMING, Skill.CONSTRUCTION, Skill.HUNTER, Skill.SAILING };
    private static final Set<Skill> F2P_SKILLS = new HashSet<>(Arrays.asList(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.PRAYER, Skill.MAGIC, Skill.HITPOINTS, Skill.CRAFTING, Skill.MINING, Skill.SMITHING, Skill.FISHING, Skill.COOKING, Skill.FIREMAKING, Skill.WOODCUTTING, Skill.RUNECRAFTING));

    private final java.util.Queue<ToastRequest> toastQueue = new java.util.LinkedList<>();
    private boolean isProcessingToasts = false;

    // Inner class to hold the data for each toast
    private static class ToastRequest {
        String message;
        JButton anchor;
        boolean success;

        ToastRequest(String m, JButton a, boolean s) {
            this.message = m;
            this.anchor = a;
            this.success = s;
        }
    }

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

        Logger.log(Logger.LogType.INFO, "Loading task list..");
        loadTaskList();
        Logger.log(Logger.LogType.INFO, "Loading task library...");
        loadTaskLibrary();
        setVisible(true);

        SwingUtilities.invokeLater(() -> {
            new Timer(1000, e -> updateUI()).start();
            // every 5 seconds scan for nearby targets if task builder is active
            new Timer(5000, e -> {
                if (mainTabs.getSelectedIndex() == 2)
                    scanNearbyTargets();
            }).start();
        });
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

    public void setLabelStatus(String text) { SwingUtilities.invokeLater(() -> lblStatus.setText(text)); }

    public void incrementExecutionIndex() {
        if (currentExecutionIndex <= modelTaskList.size()) {
            currentExecutionIndex++;
        } else {
            currentExecutionIndex = 0;
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

        // create navigation buttons (up/down arrows)
        JButton btnUp = createStyledBtn("▲", new Color(40, 40, 40));
        btnUp.addActionListener(e -> {
            boolean shiftPressed = (e.getModifiers() & ActionEvent.SHIFT_MASK) != 0;
            shiftQueue(-1, listTaskList, modelTaskList, shiftPressed);
        });
        JButton btnDown = createStyledBtn("▼", new Color(40, 40, 40));
        btnDown.addActionListener(e -> {
            boolean shiftPressed = (e.getModifiers() & ActionEvent.SHIFT_MASK) != 0;
            shiftQueue(1, listTaskList, modelTaskList, shiftPressed);
        });

        // add navigation buttons (up/down arrows)
        west.add(btnUp);
        west.add(btnDown);

        /// SOUTH: Add bottom panel and delete task button
        // create status label/progress bar
        lblStatus.setText("Status: Idle");
        lblStatus.setForeground(TEXT_MAIN);

        // create south panel with 3 rows (Status, Progress, Buttons)
        JPanel southTaskList = new JPanel(new GridLayout(1, 3, 0, 5));
        southTaskList.setOpaque(false);

//        // create the presets panel
//        JPanel panelPresets = new JPanel(new GridLayout(1, 4, 5, 0));
//        panelPresets.setOpaque(false);
//        // create each preset button
//        for (int i = 0; i < presetButtons.length; i++) {
//            final int index = i;
//            // Corrected line: removed "JButton[]" to use the class field
//            presetButtons[index] = createStyledBtn("Preset " + (index + 1), PANEL_SURFACE);
//
//            presetButtons[index].addMouseListener(new MouseAdapter() {
//                @Override
//                public void mouseClicked(MouseEvent e) {
//                    if (e.getClickCount() == 1) {
//                        loadPreset(index + 1);
//                    } else if (e.getClickCount() == 2) {
//                        renamePreset(index);
//                    }
//                }
//            });
//            panelPresets.add(presetButtons[index]);
//        }
//        panelTaskList.add(panelPresets, BorderLayout.NORTH);
//
//        // create a panel to store the buttons next to each other
//        JPanel panelButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
//        panelButtons.setOpaque(false);
//

        ///  Create Task List edit button
        JButton btnTaskListEdit = createStyledBtn("Edit", new Color(100, 100, 0));
        btnTaskListEdit.addActionListener(e -> {
            loadIntoBuilder(listTaskList.getSelectedValue());
            // switch to tab 2 (3rd tab = Task Builder) to edit task
            mainTabs.setSelectedIndex(2);
        });

        ///  Create Task List delete button
        JButton btnTaskListRemove = createStyledBtn("Remove", new Color(100, 0, 0));
        btnTaskListRemove.setEnabled(listTaskList.getSelectedIndex() != -1);
        btnTaskListRemove.addActionListener(e -> {
            int selectedIndex = listTaskList.getSelectedIndex();
            if (selectedIndex != -1) {
                modelTaskLibrary.remove(selectedIndex);
                showToast("Removed task!", btnTaskListRemove, true);
                refreshTaskList();
            } else {
                showToast("Select a Task to remove!", btnTaskListRemove, false);
            }
        });

        listTaskList.addListSelectionListener(e -> {
            btnTaskListRemove.setEnabled(!listTaskLibrary.isSelectionEmpty());
        });

        ///  Create Task List save button
        JButton btnTaskListSave = createStyledBtn("Save", new Color(100, 100, 0));
        btnTaskListSave.addActionListener(e -> {
            dataMan.saveTaskLibrary(listTaskLibrary);
            showToast("Saving...", btnTaskListSave, true);
        });

        ///  Add all buttons
        southTaskList.add(btnTaskListEdit);
        southTaskList.add(btnTaskListRemove);
        southTaskList.add(btnTaskListSave);

        // add all panels to the main panel (task list panel)
        panelTaskList.add(createSubtitle("Task List"), BorderLayout.NORTH);
        panelTaskList.add(west, BorderLayout.WEST);
        panelTaskList.add(new JScrollPane(listTaskList), BorderLayout.CENTER);
        panelTaskList.add(southTaskList, BorderLayout.SOUTH);

        return panelTaskList;
    }

    private JPanel createTaskLibraryTab() {
        // 1. Changed to BorderLayout to allow title to span the top
        JPanel panelLibraryTab = new JPanel(new BorderLayout(10, 10));
        panelLibraryTab.setBorder(new EmptyBorder(15, 15, 15, 15));
        panelLibraryTab.setBackground(BG_BASE);

        JButton btnUp = createStyledBtn("▲", new Color(40, 40, 40));
        btnUp.addActionListener(e -> {
            boolean shiftPressed = (e.getModifiers() & ActionEvent.SHIFT_MASK) != 0;
            shiftQueue(-1, listTaskLibrary, modelTaskLibrary, shiftPressed);
        });

        JButton btnDown = createStyledBtn("▼", new Color(40, 40, 40));
        btnDown.addActionListener(e -> {
            boolean shiftPressed = (e.getModifiers() & ActionEvent.SHIFT_MASK) != 0;
            shiftQueue(1, listTaskLibrary, modelTaskLibrary, shiftPressed);
        });

        JPanel panelNavButtons = new JPanel(new GridLayout(0, 1, 0, 5));
        panelNavButtons.setOpaque(false);
        panelNavButtons.add(btnUp);
        panelNavButtons.add(btnDown);

        // 3. Create a wrapper for the center content (List + Editor)
        JPanel centerContent = new JPanel(new GridLayout(1, 2, 10, 0));
        centerContent.setOpaque(false);

        /// CENTER WEST: Library list
        styleJList(listTaskLibrary);
        libraryEditorArea = new JTextArea();
        libraryEditorArea.setBackground(new Color(15, 15, 15));
        libraryEditorArea.setForeground(TEXT_MAIN);

        listTaskLibrary.addListSelectionListener(e -> {
            Task t = listTaskLibrary.getSelectedValue();
            if (t != null) libraryEditorArea.setText(t.getEditableString());
        });

        /// CENTER EAST: Edit panel + buttons
        JPanel panelCenterEastLibraryTab = new JPanel(new BorderLayout(0, 10));
        JPanel btnSection = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 20));
        panelCenterEastLibraryTab.setOpaque(false);
        btnSection.setOpaque(false);

        ///  Create Task Library add button
        JButton btnTaskLibraryAdd = createStyledBtn("Add", new Color(0, 100, 0));
        btnTaskLibraryAdd.addActionListener(e -> {
            if(listTaskLibrary.getSelectedValue() != null) {
                modelTaskList.addElement(listTaskLibrary.getSelectedValue());
                showToast("Added to position " + modelTaskList.size() + " of the queue", btnTaskLibraryAdd, true);
            }
        });

        ///  Create Task Library delete button
        JButton btnTaskLibraryDelete = createStyledBtn("Delete", new Color(100, 0, 0));
        btnTaskLibraryDelete.setEnabled(listTaskLibrary.getSelectedIndex() != -1);
        btnTaskLibraryDelete.addActionListener(e -> {
            int selectedIndex = listTaskLibrary.getSelectedIndex();
            if (selectedIndex != -1) {
                modelTaskLibrary.remove(selectedIndex);
                showToast("Deleted Task!", btnTaskLibraryDelete, true);
            } else {
                showToast("Select an Action to delete!", btnTaskLibraryDelete, false);
            }


            refreshTaskLibrary();
        });

        listTaskLibrary.addListSelectionListener(e -> {
            btnTaskLibraryDelete.setEnabled(!listTaskLibrary.isSelectionEmpty());
        });

        ///  Create Task Library save button
        JButton btnTaskLibrarySave = createStyledBtn("Save", new Color(150, 200, 50));
        btnTaskLibrarySave.addActionListener(e -> {
            showToast("Saving...", btnTaskLibrarySave, true);
            btnTaskLibrarySave.setEnabled(false);

            dataMan.saveTaskLibrary(listTaskLibrary);

            showToast("Save success!", btnTaskLibrarySave, true);
            btnTaskLibrarySave.setEnabled(true);
        });

        ///  Add all buttons
        btnSection.add(btnTaskLibraryAdd);
        btnSection.add(btnTaskLibraryDelete);
        btnSection.add(btnTaskLibrarySave);

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

    private void refreshTaskList() {
        if (listTaskList.getSelectedValue() == null && !modelTaskList.isEmpty())
            listTaskList.setSelectedIndex(modelTaskList.size() - 1);
    }

    private void refreshTaskLibrary() {
        if (listTaskLibrary.getSelectedValue() == null && !modelTaskLibrary.isEmpty())
            listTaskLibrary.setSelectedIndex(modelTaskLibrary.getSize() - 1);
    }

    private void refreshTaskBuilder() {
        if (listTaskBuilder.getSelectedValue() == null && !modelTaskBuilder.isEmpty())
            listTaskBuilder.setSelectedIndex(modelTaskBuilder.getSize() - 1);
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
        east.add(new JLabel("Task Name:"), g);

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

        btnTaskBuilderCreateTask = createStyledBtn("Create task", COLOR_ORANGE);
        btnTaskBuilderCreateTask.addActionListener(e -> {
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
                    resetTaskBuilder();
                    showToast("Added to library!", btnTaskBuilderCreateTask, true);
                } else {
                    // Task name already exists, trigger the overwrite dialogue
                    int choice = JOptionPane.showConfirmDialog(
                            this,
                            "A task with this name already exists. Would you like to overwrite it?",
                            "Overwrite Task?",
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
                        showToast("Task updated!", btnTaskBuilderCreateTask, true);
                        resetTaskBuilder();
                    } else {
                        // User clicked 'No'
                        showToast("Action cancelled", btnTaskBuilderCreateTask, false);
                    }
                }
            }
        });

        g.gridy = 6;
        g.insets = new Insets(20, 5, 5, 5);
        east.add(btnTaskBuilderCreateTask, g);

        JPanel center = new JPanel(new BorderLayout(5, 5)); center.setOpaque(false);
        JLabel setLabel = new JLabel("Action List:", SwingConstants.CENTER);
        setLabel.setForeground(COLOR_BLOOD);
        setLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        styleJList(listTaskBuilder);

        JButton btnUp = createStyledBtn("▲", new Color(40, 40, 40));
        btnUp.addActionListener(e -> {
            boolean shiftPressed = (e.getModifiers() & ActionEvent.SHIFT_MASK) != 0;
            shiftQueue(-1, listTaskBuilder, modelTaskBuilder, shiftPressed);
        });

        JButton btnDown = createStyledBtn("▼", new Color(40, 40, 40));
        btnDown.addActionListener(e -> {
            boolean shiftPressed = (e.getModifiers() & ActionEvent.SHIFT_MASK) != 0;
            shiftQueue(1, listTaskBuilder, modelTaskBuilder, shiftPressed);
        });

        JPanel navButtons = new JPanel(new GridLayout(0, 1, 0, 5));
        navButtons.setOpaque(false);
        navButtons.add(btnUp);
        navButtons.add(btnDown);

        JButton btnTaskBuilderRemove = createStyledBtn("Remove", new Color(100, 0, 0));
        btnTaskBuilderRemove.setEnabled(listTaskBuilder.getSelectedIndex() != -1);
        btnTaskBuilderRemove.addActionListener(e -> {
            int selectedIndex = listTaskBuilder.getSelectedIndex();
            if (selectedIndex != -1) {
                modelTaskBuilder.remove(selectedIndex);
                showToast("Removed action!", btnTaskBuilderRemove, true);
                refreshTaskBuilder();
            } else {
                showToast("You must select an action first!", btnTaskBuilderRemove, false);
            }
        });

        listTaskBuilder.addListSelectionListener(e -> {
            btnTaskBuilderRemove.setEnabled(!listTaskBuilder.isSelectionEmpty());
        });

        // create reset button to reset task builder inputs, ready for next task to be created
        JButton btnTaskBuilderReset = createStyledBtn("Reset", new Color(50, 50, 50));
        btnTaskBuilderReset.addActionListener(e -> {
            resetTaskBuilder();
            showToast("Resetting...", btnTaskBuilderReset, true);
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
        actionCombo.addActionListener(e -> scanNearbyTargets());
        manualTargetInput = new JTextField(); styleComp(manualTargetInput);
        config.add(new JLabel("Select Action:"));
        config.add(actionCombo);
        config.add(new JLabel("Target Name:"));
        config.add(manualTargetInput);

        JButton btnTaskBuilderAdd = createStyledBtn("Add", COLOR_ORANGE);
        btnTaskBuilderAdd.addActionListener(e -> {
            if(!manualTargetInput.getText().isEmpty()) {
                modelTaskBuilder.addElement(
                        new Action((ActionType) actionCombo.getSelectedItem(), manualTargetInput.getText())
                );
                showToast("Added action!", btnTaskBuilderAdd, true);
            } else {
                showToast("Enter a valid target!", btnTaskBuilderAdd, false);
            }
        });

        config.add(btnTaskBuilderAdd);
        config.add(new JLabel("Nearby targets:"));

        nearbyList = new JList<>(nearbyEntitiesModel);
        styleJList(nearbyList);
        nearbyList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                String val = nearbyList.getSelectedValue(); if(val == null)
                    return;
                if(e.getClickCount() == 1)
                    manualTargetInput.setText(val);
                else if(e.getClickCount() == 2)
                    modelTaskBuilder.addElement(new Action((ActionType) actionCombo.getSelectedItem(), val));
            }
        });

        // Wrap the list in a scroll pane and LOCK the width
        JScrollPane entitiesScroll = new JScrollPane(nearbyList);
        entitiesScroll.setPreferredSize(new Dimension(300, 0)); // 300px width, height 0 (BorderLayout will stretch height)
        entitiesScroll.setMinimumSize(new Dimension(300, 0));

        JButton btnScanNearby = createStyledBtn("Refresh list", COLOR_GREY);
        btnScanNearby.addActionListener(e -> {
            showToast("Scanning for nearby targets...",  btnScanNearby, true);
            scanNearbyTargets();
        });

        left.add(config, BorderLayout.NORTH);
        left.add(entitiesScroll, BorderLayout.CENTER);
        left.add(btnScanNearby, BorderLayout.SOUTH);

        panelTaskBuilder.add(createSubtitle("Task Builder"), BorderLayout.NORTH);
        panelTaskBuilder.add(left, BorderLayout.WEST);
        panelTaskBuilder.add(center, BorderLayout.CENTER);
        panelTaskBuilder.add(east, BorderLayout.EAST);

        panelTaskBuilder.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentShown(ComponentEvent e) {
                    scanNearbyTargets();
                    if (listTaskBuilder.getSelectedValue() == null && !modelTaskBuilder.isEmpty())
                        listTaskBuilder.setSelectedIndex(modelTaskBuilder.getSize() - 1);
                }
        });


        return panelTaskBuilder;
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
            showToast(e.getMessage(), btnTaskBuilderCreateTask, false);
            return null;
        }
    }

    private JPanel createSkillTrackerTab() {
        JPanel panelSkillTracker = new JPanel(new BorderLayout(15, 15));
        panelSkillTracker.setBorder(new EmptyBorder(15, 15, 15, 15));
        panelSkillTracker.setBackground(BG_BASE);

        JPanel grid = new JPanel(new GridLayout(0, 3, 3, 3));
        grid.setBackground(BG_BASE);
        grid.setBorder(new EmptyBorder(8, 8, 8, 8));

        for (Skill skill : OSRS_ORDER) {
            SkillData data = new SkillData(skill);
            skillRegistry.put(skill, data);
            grid.add(createSkillTile(data));
        }

        panelSkillTracker.add(createSubtitle("Skill Tracker"), BorderLayout.NORTH);
        panelSkillTracker.add(new JScrollPane(grid), BorderLayout.CENTER);
        totalLevelLabelP2P.setForeground(TEXT_MAIN);

        return panelSkillTracker;
    }

    private JPanel createStatusTab() {
        JPanel panelStatus = new JPanel(new BorderLayout(15, 15));
        panelStatus.setBorder(new EmptyBorder(15, 15, 15, 15));
        panelStatus.setBackground(BG_BASE);

        JPanel content = new JPanel(new GridLayout(1, 2, 20, 0));
        content.setBackground(BG_BASE);

        ///  create status 'Player' section
        JPanel login = createInfoCard("Player");
        addInfoRow(login, "Username", lblUsername);
        addInfoRow(login, "Password", lblPassword);
        addInfoRow(login, "Identifier", lblAcctId);
        addInfoRow(login, "Acct Status", lblAcctStatus);

        JPanel game = createInfoCard("World");
        addInfoRow(game, "Character Name", lblCharName);
        addInfoRowWithIcon(game, "Membership", lblMemberText, lblMemberIcon);
        addInfoRow(game, "World", lblWorld); addInfoRow(game, "Coordinates", lblCoords);
        addInfoRow(game, "GameState", lblGameState);

        content.add(login);
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
        settingGroup.add(createActivitiesPanel(), "Activities");
        settingGroup.add(createAudioPanel(), "Audio");
        settingGroup.add(createChatPanel(), "Chat");
        settingGroup.add(createDisplayPanel(), "Display");
        settingGroup.add(createControlsPanel(), "Controls");
        settingGroup.add(createWarningsPanel(), "Warnings");

        JPanel menuPanel = new JPanel(new GridLayout(10, 1, 0, 2));
        menuPanel.setPreferredSize(new Dimension(180, 0));
        menuPanel.setBackground(PANEL_SURFACE); menuPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_DIM));

        String[] groups = {"Client", "Display", "Audio", "Chat", "Controls", "Activities", "Warnings"};
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
        // clear/refresh target inputs
        scanNearbyTargets();
        manualTargetInput.setText("");

        // clear task attribute inputs
        taskNameInput.setText("");
        taskDescriptionInput.setText("");
        taskStatusInput.setText("");

        // clear the action list display
        modelTaskBuilder.clear();
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


        nearbyEntitiesModel.clear();

        for (String name : sortedNames)
            nearbyEntitiesModel.addElement(name);

        //btnTaskBuilderRefreshList.setEnabled(true);

        // Use your toast logic to confirm it finished
        showToast("Found " + sortedNames.size() + " targets", btnTaskBuilderRefreshList, true);
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
            this(o.name, o.description, o.actions, o.status);
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
        public ActionType type;
        public String target;

        public Action(ActionType t, String target) {
            this.type = t;
            this.target = target;
        }

        //TODO copy how CHOP has been done then extract out into their own action classes maybe?
        public boolean execute() {
            switch (type) {
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
                    Logger.log("Action " + type + " not implemented in Action.execute()");
                    return false;
            }
        }
        @Override public String toString() { return type.name() + " -> " + target; }
    }

//    private boolean saveTaskLibrary(Task... tasks) {
//        btnTaskLibrarySave.setEnabled(false);
//        Logger.log(Logger.LogType.INFO, "Uploading task library...");
//        Logger.log(Logger.LogType.DEBUG, "Saving:\n");
//        for (Task t: tasks) {
//            Logger.log(Logger.LogType.DEBUG, t.getEditableString());
//        }
//        return true;
//    }

    public void showToast(String message, JComponent anchor, boolean success) {
        // 1. Find the location of the button relative to the entire window
        Point location = SwingUtilities.convertPoint(anchor, 0, 0, getLayeredPane());

        // 2. Center the toast horizontally over the button and push it up 30px
        int x = location.x + (anchor.getWidth() / 2);
        int y = location.y - 30;

        ///  Trigger toast visual
        Toast toast = new Toast(message, x, y);
        Toast activeToast = toast;
        getLayeredPane().add(toast, JLayeredPane.POPUP_LAYER);
        getLayeredPane().revalidate();

        ///  Trigger button flash (red/green)
        Color flashColor = success ? COLOR_GREEN : COLOR_RED;
        flashControl(anchor, flashColor);
    }

    public String getPlayerName() {
        return Players.getLocal().getName();
    }

    private void loadTaskList() {
        new Thread(() -> {
            // Use the method from your DataMan class to get the raw JSON
            String rawJson = dataMan.loadDataByPlayer("tasks");
            SwingUtilities.invokeLater(() -> {
                if (rawJson != null)
                    unpackTaskList(rawJson);
            });
        }).start();
    }

    private void loadTaskLibrary() {
//        // load some default tasks as examples
//        libraryModel.addElement(new Task("Tree Cutter", "Chops Trees", Arrays.asList(new Action(ActionType.CHOP, "Tree")), "Chopping tree..."));
//        libraryModel.addElement(new Task("Oak Cutter", "Chops Oak Trees", Arrays.asList(new Action(ActionType.CHOP, "Oak tree")), "Chopping oak tree..."));
//        libraryModel.addElement(new Task("Guard Killer", "Attacks nearby guards", Arrays.asList(new Action(ActionType.ATTACK, "Guard")), "Chopping oak tree..."));
//        libraryModel.addElement(new Task("Imp Killer", "Attacks nearby imps", Arrays.asList(new Action(ActionType.ATTACK, "Imp")), "Chopping oak tree..."));
        new Thread(() -> {
            // Use the method from your DataMan class to get the raw JSON
            String rawJson = dataMan.loadDataByPlayer("library");
            SwingUtilities.invokeLater(() -> {
                if (rawJson != null)
                    unpackTaskLibrary(rawJson);
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
                        modelTaskList.clear();
                        for (Task task : fetchedTasks.values()) {
                            // Because GSON uses the Task constructor, these are
                            // now real objects with executable action lists.
                            modelTaskList.addElement(task);
                        }
                        Logger.log("Successfully loaded " + modelTaskList.size() + " tasks into the task list.");
                    });
                }
            }
        } catch (Exception e) {
            Logger.log(Logger.LogType.ERROR, "Failed to unpack library: " + e.getMessage());
            e.printStackTrace();
        }
    }
    /**
     * Flashes a UI component a specific color and displays a message,
     * then reverts to original state after 2 seconds.
     *
     * @param component The JComponent to flash (e.g., btnTriggerBreak).
     * @param flashColor The color to flash (e.g., new Color(100, 0, 0) for red).
     */
    private void flashControl(JComponent component, Color flashColor) {
        // Capture the original background color to revert to later
        final Color originalColor = component.getBackground();

        // 1. Apply Flash State
        component.setBackground(flashColor);

        // 2. Setup Revert Timer
        Timer revertTimer = new Timer(200, e -> {
            component.setBackground(originalColor);
            component.repaint();
        });

        revertTimer.setRepeats(false); // Ensure it only runs once
        revertTimer.start();
    }


    private void unpackTaskLibrary(String json) {
        if (json == null || json.isEmpty() || json.equals("[]")) {
            Logger.log("No library data found to unpack.");
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
                Map<String, Task> fetchedLibrary = responseList.get(0).get("library");

                if (fetchedLibrary != null) {
                    // 3. Update the UI on the Swing thread
                    SwingUtilities.invokeLater(() -> {
                        modelTaskLibrary.clear();
                        for (Task task : fetchedLibrary.values()) {
                            // Because GSON uses the Task constructor, these are
                            // now real objects with executable action lists.
                            modelTaskLibrary.addElement(task);
                        }
                        Logger.log("Successfully loaded " + modelTaskLibrary.size() + " tasks into the library.");
                    });
                }
            }
        } catch (Exception e) {
            Logger.log(Logger.LogType.ERROR, "Failed to unpack library: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadIntoBuilder(Task t) {
        if(t == null)
            return;

        taskNameInput.setText(t.name);
        taskDescriptionInput.setText(t.description);
        taskStatusInput.setText(t.status);
        modelTaskBuilder.clear(); t.actions.forEach(modelTaskBuilder::addElement);
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
        } else {
            script.getScriptManager().pause();
            btnPlayPause.setText("▶");
            isScriptPaused = true;
        }
    }

    private void stopScript() { if (JOptionPane.showConfirmDialog(this, "Stop?") == JOptionPane.YES_OPTION) { script.stop(); dispose(); } }
    private void toggleUserInput() { isUserInputAllowed = !isUserInputAllowed; Client.getInstance().setMouseInputEnabled(isUserInputAllowed); Client.getInstance().setKeyboardInputEnabled(isUserInputAllowed); btnInputToggle.setText(isUserInputAllowed ? "🖱" : "🚫"); }
    private void styleHeaderLabel(JLabel l) { l.setForeground(TEXT_MAIN); l.setFont(new Font("Consolas", Font.BOLD, 15)); l.setHorizontalAlignment(SwingConstants.RIGHT); }
    private void styleSpinner(JSpinner s) { JFormattedTextField field = ((JSpinner.DefaultEditor) s.getEditor()).getTextField(); field.setBackground(new Color(30, 30, 30)); field.setForeground(COLOR_BLOOD); s.setBorder(new LineBorder(BORDER_DIM)); }
    private JButton createIconButton(String symbol, String tooltip, ActionListener action) { JButton btn = new JButton(symbol); btn.setPreferredSize(new Dimension(40, 40)); btn.setFont(new Font("Segoe UI Symbol", Font.BOLD, 18)); btn.setBackground(new Color(30, 0, 0)); btn.setForeground(COLOR_BLOOD); btn.addActionListener(action); return btn; }
    private <T> void shiftQueue(int dir, JList<T> list, DefaultListModel<T> model, boolean isShiftHeld) {
        int idx = list.getSelectedIndex();
        if (idx == -1 || idx + dir < 0 || idx + dir >= model.size())
            return;

        if (isShiftHeld) {
            // Shift + Click: Move the actual data
            T item = model.remove(idx);
            model.add(idx + dir, item);
            list.setSelectedIndex(idx + dir);
        } else {
            // Regular Click: Just move the visual selection
            list.setSelectedIndex(idx + dir);
            list.ensureIndexIsVisible(idx + dir);
        }
    }
    private void styleComp(JComponent c) { c.setBackground(PANEL_SURFACE); c.setForeground(TEXT_MAIN); if(c instanceof JTextField) ((JTextField)c).setCaretColor(COLOR_BLOOD); }
    private void styleJList(JList<?> l) {
        l.setBackground(PANEL_SURFACE);
        l.setForeground(TEXT_MAIN);
        l.setSelectionBackground(TAB_SELECTED);
    }

    private JButton createStyledBtn(String t, Color c) {
        JButton b = new JButton(t);
        b.setBackground(c);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorder(new LineBorder(BORDER_DIM));
        return b;
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
        updateAll();
    }

    private JPanel createInfoCard(String title) { JPanel p = new JPanel(new GridLayout(0, 1, 5, 10)); p.setBackground(PANEL_SURFACE); TitledBorder b = BorderFactory.createTitledBorder(new LineBorder(BORDER_DIM), " " + title + " "); b.setTitleColor(COLOR_BLOOD); b.setTitleFont(new Font("Segoe UI", Font.BOLD, 16)); p.setBorder(BorderFactory.createCompoundBorder(b, new EmptyBorder(15, 15, 15, 15))); return p; }
    private void addInfoRow(JPanel p, String key, JLabel valLabel) { JPanel row = new JPanel(new BorderLayout()); row.setOpaque(false); JLabel k = new JLabel(key); k.setForeground(TEXT_DIM); valLabel.setForeground(TEXT_MAIN); valLabel.setFont(new Font("Consolas", Font.BOLD, 14)); row.add(k, BorderLayout.WEST); row.add(valLabel, BorderLayout.EAST); row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 40, 40))); p.add(row); }
    private void addInfoRowWithIcon(JPanel p, String key, JLabel valLabel, JLabel iconLabel) { JPanel row = new JPanel(new BorderLayout(5, 0)); row.setOpaque(false); JLabel k = new JLabel(key); k.setForeground(TEXT_DIM); JPanel rightSide = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0)); rightSide.setOpaque(false); valLabel.setForeground(TEXT_MAIN); rightSide.add(valLabel); rightSide.add(iconLabel); row.add(k, BorderLayout.WEST); row.add(rightSide, BorderLayout.EAST); row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 40, 40))); p.add(row); }
    private JPanel createDisplayPanel() { return createSettingsGroup("Display", createSettingCheck("Roofs", !ClientSettings.areRoofsHidden(), e -> ClientSettings.toggleRoofs(((JCheckBox)e.getSource()).isSelected())), createSettingCheck("Data orbs", ClientSettings.areDataOrbsEnabled(), e -> ClientSettings.toggleDataOrbs(((JCheckBox)e.getSource()).isSelected())), createSettingCheck("Transparent side panel", ClientSettings.isTransparentSidePanelEnabled(), e -> ClientSettings.toggleTransparentSidePanel(((JCheckBox)e.getSource()).isSelected()))); }
    private JPanel createAudioPanel() { return createSettingsGroup("Audio", createSettingCheck("Game Audio", ClientSettings.isGameAudioOn(), e -> ClientSettings.toggleGameAudio(((JCheckBox)e.getSource()).isSelected()))); }
    private JPanel createChatPanel() { return createSettingsGroup("Chat", createSettingCheck("Transparent chatbox", ClientSettings.isTransparentChatboxEnabled(), e -> ClientSettings.toggleTransparentChatbox(((JCheckBox)e.getSource()).isSelected())), createSettingCheck("Click through chatbox", ClientSettings.isClickThroughChatboxEnabled(), e -> ClientSettings.toggleClickThroughChatbox(((JCheckBox)e.getSource()).isSelected()))); }
    private JPanel createControlsPanel() { return createSettingsGroup("Controls", createSettingCheck("Shift click drop", ClientSettings.isShiftClickDroppingEnabled(), e -> ClientSettings.toggleShiftClickDropping(((JCheckBox)e.getSource()).isSelected())), createSettingCheck("Esc closes interface", ClientSettings.isEscInterfaceClosingEnabled(), e -> ClientSettings.toggleEscInterfaceClosing(((JCheckBox)e.getSource()).isSelected()))); }
    private JPanel createWarningsPanel() { return createSettingsGroup("Warnings", createSettingCheck("Loot notifications", ClientSettings.areLootNotificationsEnabled(), e -> ClientSettings.toggleLootNotifications(((JCheckBox)e.getSource()).isSelected()))); }
    private JPanel createClientPanel() { return createSettingsGroup("Client", createSettingCheck("Disable Rendering", Client.isRenderingDisabled(), e -> Client.setRenderingDisabled(((JCheckBox)e.getSource()).isSelected()))); }
    private JPanel createActivitiesPanel() { return createSettingsGroup("Activities", createSettingCheck("Level-up interface", ClientSettings.isLevelUpInterfaceEnabled(), e -> ClientSettings.toggleLevelUpInterface(((JCheckBox)e.getSource()).isSelected()))); }
    private JPanel createSettingsGroup(String title, Component... comps) { JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10)); p.setBackground(BG_BASE); JPanel list = new JPanel(new GridLayout(0, 1, 5, 5)); list.setBackground(BG_BASE); JLabel header = new JLabel(title); header.setForeground(COLOR_BLOOD); header.setFont(new Font("Segoe UI", Font.BOLD, 24)); JPanel wrapper = new JPanel(new BorderLayout()); wrapper.setBackground(BG_BASE); wrapper.add(header, BorderLayout.NORTH); for (Component c : comps) list.add(c); wrapper.add(list, BorderLayout.CENTER); return wrapper; }
    private JCheckBox createSettingCheck(String text, boolean initialState, ActionListener l) { JCheckBox c = new JCheckBox(text); c.setForeground(TEXT_MAIN); c.setOpaque(false); c.setSelected(initialState); if (l != null) c.addActionListener(l); return c; }
    private JToggleButton createMenuButton(String text) { JToggleButton btn = new JToggleButton(text) { protected void paintComponent(Graphics g) { g.setColor(isSelected() ? TAB_SELECTED : PANEL_SURFACE); g.fillRect(0, 0, getWidth(), getHeight()); super.paintComponent(g); } }; btn.setFocusPainted(false); btn.setContentAreaFilled(false); btn.setForeground(TEXT_MAIN); btn.setFont(new Font("Segoe UI", Font.BOLD, 14)); btn.setHorizontalAlignment(SwingConstants.LEFT); btn.setBorder(new EmptyBorder(0, 20, 0, 0)); return btn; }

    private JPanel createSkillTile(SkillData data) { JPanel tile = new JPanel(new GridBagLayout()); tile.setBackground(PANEL_SURFACE); tile.setBorder(new LineBorder(BORDER_DIM)); GridBagConstraints gbc = new GridBagConstraints(); gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0; gbc.gridx = 0; JPanel top = new JPanel(new BorderLayout()); top.setOpaque(false); JLabel icon = new JLabel(loadSkillIcon(data.skill)); data.lblLevel.setForeground(COLOR_BLOOD); data.lblLevel.setFont(new Font("Arial", Font.BOLD, 18)); top.add(icon, BorderLayout.WEST); top.add(data.lblLevel, BorderLayout.EAST); data.lblXpString.setForeground(TEXT_DIM); data.lblXpString.setFont(new Font("Monospaced", Font.PLAIN, 10)); data.mainBar.setForeground(COLOR_BLOOD); data.mainBar.setBackground(Color.BLACK); gbc.gridy = 0; tile.add(top, gbc); gbc.gridy = 1; tile.add(data.lblXpString, gbc); gbc.gridy = 2; tile.add(data.mainBar, gbc); tile.addMouseListener(new MouseAdapter() { @Override public void mousePressed(MouseEvent e) { data.isTracking = !data.isTracking; tile.setBorder(new LineBorder(data.isTracking ? COLOR_BLOOD : BORDER_DIM, 1)); refreshTrackerList(); } }); return tile; }
    private void refreshTrackerList() { trackerList.removeAll(); skillRegistry.values().stream().filter(d -> d.isTracking).forEach(d -> { trackerList.add(d.trackerPanel); trackerList.add(Box.createRigidArea(new Dimension(0, 10))); }); trackerList.add(Box.createVerticalGlue()); trackerList.revalidate(); trackerList.repaint(); }
    private void updateAll() {
        int projH = (int) projectionSpinner.getValue();
        long totalXPGained = 0;
        int totalLevelsGained = 0;
        int p2pTotal = 0;
        int f2pTotal = 0;

        for (SkillData data : skillRegistry.values()) {
            int xp = Skills.getExperience(data.skill);
            int lvl = Skills.getRealLevel(data.skill);
            data.update(xp, lvl, startTime, projH);
            p2pTotal += lvl; if (F2P_SKILLS.contains(data.skill)) f2pTotal += lvl;

            if (data.isTracking) {
                totalXPGained += Math.max(0, (xp - data.startXP));
                totalLevelsGained += Math.max(0, (lvl - data.startLevel));
            }
        }


        totalLevelsGainedLabel.setText("▲ " + totalLevelsGained + " level(s) gained");
        totalLevelsGainedLabel.setForeground(new Color(0, 180, 0));
        totalLevelsGainedLabel.setFont(new Font("Consolas", Font.BOLD, 14));

        totalXpGainedLabel.setText("▲ " + totalXPGained + " xp gained");
        totalXpGainedLabel.setForeground(new Color(0, 180, 0));
        totalXpGainedLabel.setFont(new Font("Consolas", Font.BOLD, 14));

        totalLevelLabelF2P.setText("Total: " + f2pTotal);
        totalLevelLabelF2P.setIcon(loadStatusIcon("F2P_icon"));

        totalLevelLabelP2P.setText("Total: " + p2pTotal);
        totalLevelLabelP2P.setIcon(loadStatusIcon("P2P_icon"));


        totalLevelLabelP2P.setIconTextGap(8);
        totalLevelLabelF2P.setIconTextGap(8);
        totalLevelLabelP2P.setHorizontalAlignment(SwingConstants.CENTER);
        totalLevelLabelF2P.setHorizontalAlignment(SwingConstants.CENTER);

        boolean isMember = Client.isMembers();
        if (Client.isLoggedIn()) {
            Players.getLocal();
            lblUsername.setText(Players.getLocal().getName());
            lblPassword.setText(String.valueOf(Client.getPassword()));
            lblWorld.setText("World " + (Worlds.getCurrent() != null ? Worlds.getCurrent().getWorld() : "?"));

            lblMemberText.setText(isMember ? "Pay-to-play" : "Free-to-play");
            lblMemberIcon.setIcon(loadStatusIcon(isMember ? "P2P_icon" : "F2P_icon"));
            lblCharName.setText(Players.getLocal().getName());
            lblCoords.setText(Players.getLocal().getTile().toString());
            lblGameState.setText(Client.getGameState().name());
        }
    }

    private class TaskCellRenderer extends DefaultListCellRenderer { @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) { JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus); if (index == currentExecutionIndex) { l.setBackground(TAB_SELECTED); l.setForeground(Color.WHITE); l.setText("▶ " + l.getText()); } return l; } }
    private class SkillData { final Skill skill; final JProgressBar mainBar = new JProgressBar(0, 100); final JLabel lblLevel = new JLabel("1"), lblXpString = new JLabel("0/0"); final JPanel trackerPanel = new JPanel(new GridLayout(0, 1, 2, 2)); final JLabel lblGained = new JLabel(), lblPerHour = new JLabel(), lblRemaining = new JLabel(), lblTTL = new JLabel(), lblProj = new JLabel(), lblActs = new JLabel(); final int startXP, startLevel; boolean isTracking = false; SkillData(Skill s) { this.skill = s; this.startXP = Skills.getExperience(s); this.startLevel = Skills.getRealLevel(s); trackerPanel.setBackground(new Color(30, 30, 30)); TitledBorder b = BorderFactory.createTitledBorder(new LineBorder(BORDER_DIM), " " + s.name() + " "); b.setTitleColor(COLOR_BLOOD); trackerPanel.setBorder(b); JLabel[] ls = {lblGained, lblPerHour, lblRemaining, lblActs, lblTTL, lblProj}; for (JLabel l : ls) { l.setForeground(TEXT_MAIN); l.setFont(new Font("Consolas", Font.PLAIN, 12)); trackerPanel.add(l); } } void update(int curXp, int curLvl, long start, int ph) { int curMin = Skills.getExperienceForLevel(curLvl), curMax = Skills.getExperienceForLevel(curLvl + 1); lblLevel.setText(String.valueOf(curLvl)); lblXpString.setText(String.format("%,d / %,d XP", curXp, curMax)); mainBar.setValue((int) (((double)(curXp - curMin) / Math.max(1, curMax - curMin)) * 100)); long elapsed = System.currentTimeMillis() - start; int xph = (int) (Math.max(0, curXp - startXP) / Math.max(0.0001, elapsed / 3600000.0)); int rem = Math.max(0, curMax - curXp); lblGained.setText(" GAINED: " + String.format("%,d XP", curXp - startXP)); lblPerHour.setText(" XP/HR:  " + String.format("%,d", xph)); lblRemaining.setText(" TO LEVEL: " + String.format("%,d", rem)); if (xph > 0) { lblTTL.setText(String.format(" TIME TO L: %.2f hrs", (double) rem / xph)); lblProj.setText(String.format(" PROJ (%dH): Lvl %d", ph, curLvl + (xph * ph / 100000))); } } }

//    // Replace your old listToJson with this one-liner approach
//    private String modelToJson(DefaultListModel<Task> model) {
//        List<Task> list = Collections.list(model.elements());
//        return new Gson().toJson(list);
//    }

//    private String packagePreset(int slotIndex) {
//        String name = presetButtons[slotIndex - 1].getText();
//
//        // Safety check: if the queue is empty, package an empty array string
//        String taskData = queueModel.isEmpty() ? "[]" : modelToJson(queueModel);
//
//        // We must escape the internal quotes of the taskData so it fits inside the preset object
//        String escapedData = taskData.replace("\"", "\\\"");
//
//        // Construct the JSON slot object
//        return "{\"name\":\"" + name + "\", \"data\":\"" + escapedData + "\"}";
//    }

//    private void savePresets() {
//        StringBuilder fullJson = new StringBuilder("{");
//        for (int i = 1; i <= 4; i++) {
//            fullJson.append("\"p").append(i).append("\":").append(packagePreset(i));
//            if (i < 4) fullJson.append(",");
//        }
//        fullJson.append("}");
//
//        // Push the entire object to the "presets" column
//        dataMan.postThreaded("presets", fullJson.toString(), resp -> {
//            Logger.log("All 4 Preset Slots synced to server.");
//        });
//    }

//    public void saveAllData() {
//        // 1. Save Presets (Names + Task Data)
//        StringBuilder sb = new StringBuilder("{");
//        for (int i = 1; i <= 4; i++) {
//            sb.append("\"p").append(i).append("\":").append(packagePreset(i));
//            if (i < 4) sb.append(",");
//        }
//
//        sb.append("}");
//        dataMan.postThreaded("presets", sb.toString(), resp -> Logger.log("Presets Saved."));
//
//        // 2. Save Library (The Library Tab contents)
//        dataMan.postThreaded("settings", listToJson(libraryModel), resp -> Logger.log("Library Saved."));
//
//        lblStatus.setText("Status: All Data Synced");
//    }
//
//    private void renamePreset(int index) {
//        String currentName = presetButtons[index].getText();
//        String newName = JOptionPane.showInputDialog(this, "Rename Preset:", currentName);
//
//        if (newName != null && !newName.trim().isEmpty()) {
//            presetButtons[index].setText(newName);
//            // Optional: Auto-save after renaming
//            savePresets();
//        }
//    }

//    private void loadPreset(int slotIndex) {
//        DataMan dm = new DataMan();
//        // Using "presets" column
//        dm.getThreaded("presets", json -> {
//            if (json == null || json.isEmpty()) {
//                Logger.log("No preset data found on server.");
//                return;
//            }
//
//            String key = "p" + slotIndex;
//            // Updated regex to be more literal with potential JSON spacing
//            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + key + "\":\\s*\\{\"name\":\"(.*?)\",\"data\":\"(.*?)\"\\}");
//            java.util.regex.Matcher m = p.matcher(json);
//
//            if (m.find()) {
//                // Unescape the nested JSON string
//                String tasksJson = m.group(2).replace("\\\"", "\"");
//
//                // Rebuild into the queueModel
//                rebuildTasksFromJson(tasksJson, queueModel);
//
//                // Update UI safely
//                SwingUtilities.invokeLater(() -> {
//                    lblStatus.setText("Status: Loaded Preset " + slotIndex);
//                    taskQueueList.repaint();
//                });
//            } else {
//                Logger.log("Could not find slot " + key + " in the database response.");
//            }
//        });
//    }
//
//    private void rebuildTasksFromJson(String json, DefaultListModel<Task> model) {
//        model.clear();
//        try {
//            // Regex to find each Task block: {"name":"...","desc":"...","status":"...","actions":[...]}
//            java.util.regex.Pattern taskPat = java.util.regex.Pattern.compile("\\{\"name\":\"(.*?)\",\"desc\":\"(.*?)\",\"status\":\"(.*?)\",\"actions\":\\[(.*?)\\]\\}");
//            java.util.regex.Matcher taskMat = taskPat.matcher(json);
//
//            while (taskMat.find()) {
//                String name = taskMat.group(1);
//                String desc = taskMat.group(2);
//                String status = taskMat.group(3);
//                String actionsRaw = taskMat.group(4);
//
//                List<Action> actions = new ArrayList<>();
//                // Regex to find each Action block: {"type":"...","target":"..."}
//                java.util.regex.Pattern actPat = java.util.regex.Pattern.compile("\\{\"type\":\"(.*?)\",\"target\":\"(.*?)\"\\}");
//                java.util.regex.Matcher actMat = actPat.matcher(actionsRaw);
//
//                while (actMat.find()) {
//                    ActionType type = ActionType.valueOf(actMat.group(1));
//                    String target = actMat.group(2);
//                    actions.add(new Action(type, target));
//                }
//
//                model.addElement(new Task(name, desc, actions, status));
//            }
//        } catch (Exception e) {
//            Logger.log("Parsing error: " + e.getMessage());
//        }
//    }
}