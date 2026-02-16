//package main.menu;
//
//import org.dreambot.api.Client;
//import org.dreambot.api.ClientSettings;
//import org.dreambot.api.methods.interactive.GameObjects;
//import org.dreambot.api.methods.interactive.NPCs;
//import org.dreambot.api.methods.interactive.Players;
//import org.dreambot.api.methods.skills.Skill;
//import org.dreambot.api.methods.skills.Skills;
//import org.dreambot.api.methods.world.Worlds;
//import org.dreambot.api.script.AbstractScript;
//import org.dreambot.api.utilities.Logger;
//
//import javax.swing.*;
//import javax.swing.border.EmptyBorder;
//import javax.swing.border.LineBorder;
//import javax.swing.border.TitledBorder;
//import javax.swing.text.DefaultCaret;
//import java.awt.*;
//import java.awt.event.ActionListener;
//import java.awt.event.MouseAdapter;
//import java.awt.event.MouseEvent;
//import java.util.*;
//import java.util.List;
//import java.util.stream.Collectors;
//
///**
// * <h1>DreamBotMan Nexus Client</h1>
// *
// * @author DreamBotMan Dev
// * @version 15.0.0-Elite
// */
//public class DreamBotMenu4 extends JFrame {
//
//    // ========================================
//    // FIELDS
//    // ========================================
//
//    private final AbstractScript script;
//
//    // --- State Management ---
//    private boolean isScriptPaused = true;
//    private boolean isUserInputAllowed = true;
//    private boolean isCaptureEnabled = true;
//    private int currentExecutionIndex = 0;
//
//    // --- Data Structures & Models ---
//    private final Map<Skill, SkillData> skillRegistry = new EnumMap<>(Skill.class);
//    private final DefaultListModel<Task> queueModel = new DefaultListModel<>();
//    private final DefaultListModel<Task> libraryModel = new DefaultListModel<>();
//    private final DefaultListModel<Action> builderActionModel = new DefaultListModel<>();
//    private final DefaultListModel<String> nearbyEntitiesModel = new DefaultListModel<>();
//    private final List<List<Task>> presets = new ArrayList<>(Arrays.asList(
//            new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()
//    ));
//
//    // --- UI Components - Main Panels ---
//    private final JPanel trackerList, sidePanel;
//
//    // --- UI Components - Labels ---
//    private final JLabel totalXpGainedLabel = new JLabel("Total XP Gained: 0");
//    private final JLabel totalLevelsGainedLabel = new JLabel("Total Levels Gained: 0");
//    private final JLabel totalLevelLabel = new JLabel("F2P Total: 0 | P2P Total: 0", SwingConstants.CENTER);
//    private final JLabel lblStatus = new JLabel("Status: Idle");
//    private final JLabel lblUsername = new JLabel("...");
//    private final JLabel lblPassword = new JLabel("...");
//    private final JLabel lblAcctId = new JLabel("...");
//    private final JLabel lblAcctStatus = new JLabel("...");
//    private final JLabel lblCharName = new JLabel("...");
//    private final JLabel lblWorld = new JLabel("-");
//    private final JLabel lblCoords = new JLabel("-");
//    private final JLabel lblGameState = new JLabel("-");
//    private final JLabel lblMemberIcon = new JLabel();
//    private final JLabel lblMemberText = new JLabel("-");
//
//    // --- UI Components - Other Controls ---
//    private final JProgressBar statusProgress = new JProgressBar(0, 100);
//    private final JSpinner projectionSpinner;
//    private final long startTime;
//
//    private JList<Task> taskQueueList, libraryList;
//    private JList<String> nearbyEntitiesList;
//    private JTextArea libraryEditorArea, consoleArea;
//    private JTextField taskNameInput, taskDescInput, taskStatusInput, manualTargetInput, consoleSearch;
//    private JComboBox<ActionType> actionCombo;
//    private JButton btnPlayPause, btnInputToggle, btnCaptureToggle;
//
//    // --- Theme Colors ---
//    private final Color BG_BASE = new Color(12, 12, 12);
//    private final Color PANEL_SURFACE = new Color(24, 24, 24);
//    private final Color ACCENT_BLOOD = new Color(150, 0, 0);
//    private final Color ACCENT_ORANGE = new Color(220, 80, 0);
//    private final Color BORDER_DIM = new Color(45, 45, 45);
//    private final Color TEXT_MAIN = new Color(210, 210, 210);
//    private final Color TEXT_DIM = new Color(140, 140, 140);
//    private final Color TAB_SELECTED = new Color(60, 0, 0);
//
//    // --- Constants ---
//    private static final Skill[] OSRS_ORDER = {
//            Skill.ATTACK, Skill.HITPOINTS, Skill.MINING,
//            Skill.STRENGTH, Skill.AGILITY, Skill.SMITHING,
//            Skill.DEFENCE, Skill.HERBLORE, Skill.FISHING,
//            Skill.RANGED, Skill.THIEVING, Skill.COOKING,
//            Skill.PRAYER, Skill.CRAFTING, Skill.FIREMAKING,
//            Skill.MAGIC, Skill.FLETCHING, Skill.WOODCUTTING,
//            Skill.RUNECRAFTING, Skill.SLAYER, Skill.FARMING,
//            Skill.CONSTRUCTION, Skill.HUNTER, Skill.SAILING
//    };
//
//    private static final Set<Skill> F2P_SKILLS = new HashSet<>(Arrays.asList(
//            Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED,
//            Skill.PRAYER, Skill.MAGIC, Skill.HITPOINTS, Skill.CRAFTING,
//            Skill.MINING, Skill.SMITHING, Skill.FISHING, Skill.COOKING,
//            Skill.FIREMAKING, Skill.WOODCUTTING, Skill.RUNECRAFTING
//    ));
//
//    // ========================================
//    // ENUMS
//    // ========================================
//
//    public enum ActionType {
//        ATTACK, BANK, BURY, CHOP, COOK, DROP, EXAMINE, FISH, MINE, OPEN, TALK_TO, USE_ON
//    }
//
//    // ========================================
//    // CONSTRUCTOR
//    // ========================================
//
//    public DreamBotMenu4(AbstractScript script) {
//        this.script = script;
//        this.startTime = System.currentTimeMillis();
//
//        // Initialize spinner before UI creation
//        projectionSpinner = new JSpinner(new SpinnerNumberModel(24, 1, 999, 1));
//        styleSpinner(projectionSpinner);
//
//        // Configure main window
//        setTitle("DreamBotMan | Nexus Client");
//        setSize(1400, 950);
//        setLocationRelativeTo(null);
//        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
//        setLayout(new BorderLayout(0, 0));
//        getContentPane().setBackground(BG_BASE);
//
//        // Create main UI components
//        JPanel header = createHeaderPanel();
//        JTabbedPane mainTabs = createMainTabs();
//
//        // Create side panel with tracker list
//        trackerList = new JPanel();
//        trackerList.setLayout(new BoxLayout(trackerList, BoxLayout.Y_AXIS));
//        trackerList.setBackground(PANEL_SURFACE);
//
//        JScrollPane trackerScroll = new JScrollPane(trackerList);
//        trackerScroll.setBorder(null);
//        trackerScroll.getViewport().setBackground(PANEL_SURFACE);
//
//        sidePanel = new JPanel(new BorderLayout());
//        sidePanel.setPreferredSize(new Dimension(360, 0));
//        sidePanel.setBackground(PANEL_SURFACE);
//        sidePanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER_DIM));
//        sidePanel.add(trackerScroll, BorderLayout.CENTER);
//
//        // Create toggle button for side panel
//        JButton toggleBtn = new JButton(">");
//        toggleBtn.setPreferredSize(new Dimension(22, 0));
//        toggleBtn.setBackground(PANEL_SURFACE);
//        toggleBtn.setForeground(ACCENT_BLOOD);
//        toggleBtn.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 1, BORDER_DIM));
//        toggleBtn.addActionListener(e -> {
//            sidePanel.setVisible(!sidePanel.isVisible());
//            toggleBtn.setText(sidePanel.isVisible() ? "<" : ">");
//            revalidate();
//        });
//
//        // Add components to main window
//        add(header, BorderLayout.NORTH);
//        add(mainTabs, BorderLayout.CENTER);
//        add(toggleBtn, BorderLayout.EAST);
//        add(sidePanel, BorderLayout.EAST);
//
//        // Initialize data
//        initializeGenericLibrary();
//
//        // Make visible and start timers
//        setVisible(true);
//        startUpdateTimers(mainTabs);
//    }
//
//    // ========================================
//    // PUBLIC API METHODS (Bridge to Main Script)
//    // ========================================
//
//    public boolean isScriptPaused() {
//        return isScriptPaused;
//    }
//
//    public DefaultListModel<Task> getQueueModel() {
//        return queueModel;
//    }
//
//    public int getCurrentExecutionIndex() {
//        return currentExecutionIndex;
//    }
//
//    public void setCurrentExecutionIndex(int index) {
//        this.currentExecutionIndex = index;
//        if (taskQueueList != null) {
//            taskQueueList.repaint();
//        }
//    }
//
//    public void setLabelStatus(String text) {
//        SwingUtilities.invokeLater(() -> lblStatus.setText(text));
//    }
//
//    public void incrementExecutionIndex() {
//        if (currentExecutionIndex < queueModel.size() - 1) {
//            currentExecutionIndex++;
//        } else {
//            currentExecutionIndex = 0;
//            isScriptPaused = true;
//            if (btnPlayPause != null) {
//                btnPlayPause.setText("▶");
//            }
//        }
//        if (taskQueueList != null) {
//            taskQueueList.repaint();
//        }
//    }
//
//    // ========================================
//    // UI CREATION METHODS - MAIN TABS
//    // ========================================
//
//    private JTabbedPane createMainTabs() {
//        JTabbedPane mainTabs = new JTabbedPane();
//        mainTabs.setBackground(PANEL_SURFACE);
//        mainTabs.setForeground(TEXT_MAIN);
//
//        mainTabs.addTab("Task List", createTaskListTab());
//        mainTabs.addTab("Task Library", createLibraryTab());
//        mainTabs.addTab("Task Builder", createBuilderTab());
//        mainTabs.addTab("Output", createOutputTab());
//        mainTabs.addTab("Skill Tracker", loadMiscIcon("Stats_icon"), createSkillsPanel());
//        mainTabs.addTab("Player", null, createPlayerPanel());
//        mainTabs.addTab("Settings", null, createSettingsInterface());
//
//        return mainTabs;
//    }
//
//    private JPanel createHeaderPanel() {
//        JPanel header = new JPanel(new BorderLayout());
//        header.setBackground(PANEL_SURFACE);
//        header.setPreferredSize(new Dimension(0, 85));
//        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_DIM));
//
//        // Title
//        JLabel titleLabel = new JLabel(" DreamBotMan", SwingConstants.LEFT);
//        titleLabel.setForeground(ACCENT_BLOOD);
//        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 32));
//        titleLabel.setBorder(new EmptyBorder(0, 25, 0, 0));
//
//        // Right container with controls and stats
//        JPanel rightContainer = new JPanel(new BorderLayout());
//        rightContainer.setOpaque(false);
//        rightContainer.setBorder(new EmptyBorder(0, 0, 0, 20));
//
//        // Control buttons
//        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 20));
//        controls.setOpaque(false);
//
//        btnPlayPause = createIconButton("▶", "Play", e -> toggleScriptState());
//        JButton btnStop = createIconButton("■", "Stop", e -> stopScript());
//        btnInputToggle = createIconButton("🖱", "Input", e -> toggleUserInput());
//
//        controls.add(btnPlayPause);
//        controls.add(btnStop);
//        controls.add(btnInputToggle);
//
//        // Header stats
//        JPanel headerStats = new JPanel(new GridLayout(2, 1));
//        headerStats.setOpaque(false);
//        headerStats.setBorder(new EmptyBorder(10, 20, 10, 10));
//
//        styleHeaderLabel(totalXpGainedLabel);
//        styleHeaderLabel(totalLevelsGainedLabel);
//
//        headerStats.add(totalXpGainedLabel);
//        headerStats.add(totalLevelsGainedLabel);
//
//        rightContainer.add(controls, BorderLayout.CENTER);
//        rightContainer.add(headerStats, BorderLayout.EAST);
//
//        header.add(titleLabel, BorderLayout.WEST);
//        header.add(rightContainer, BorderLayout.EAST);
//
//        return header;
//    }
//
//    private JPanel createTaskListTab() {
//        JPanel panel = new JPanel(new BorderLayout(10, 10));
//        panel.setBackground(BG_BASE);
//        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
//
//        // Navigation buttons
//        JPanel nav = new JPanel(new GridLayout(0, 1, 0, 5));
//        nav.setOpaque(false);
//
//        JButton btnUp = createStyledBtn("▲", new Color(40, 40, 40));
//        btnUp.addActionListener(e -> shiftQueue(-1));
//
//        JButton btnDown = createStyledBtn("▼", new Color(40, 40, 40));
//        btnDown.addActionListener(e -> shiftQueue(1));
//
//        JButton btnLoad = createStyledBtn("LOAD", ACCENT_BLOOD);
//        btnLoad.addActionListener(e -> loadIntoBuilder(taskQueueList.getSelectedValue()));
//
//        nav.add(btnUp);
//        nav.add(btnDown);
//        nav.add(btnLoad);
//
//        // Task queue list
//        taskQueueList = new JList<>(queueModel);
//        taskQueueList.setCellRenderer(new TaskCellRenderer());
//        styleJList(taskQueueList);
//
//        // Bottom controls
//        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
//        bottomRow.setOpaque(false);
//
//        JButton btnDelete = createStyledBtn("DELETE TASK", new Color(100, 0, 0));
//        btnDelete.addActionListener(e -> {
//            int selectedIndex = taskQueueList.getSelectedIndex();
//            if (selectedIndex != -1) {
//                queueModel.remove(selectedIndex);
//            }
//        });
//
//        bottomRow.add(btnDelete);
//
//        panel.add(nav, BorderLayout.WEST);
//        panel.add(new JScrollPane(taskQueueList), BorderLayout.CENTER);
//        panel.add(bottomRow, BorderLayout.SOUTH);
//
//        return panel;
//    }
//
//    private JPanel createLibraryTab() {
//        JPanel panel = new JPanel(new GridLayout(1, 2, 10, 0));
//        panel.setBackground(BG_BASE);
//        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
//
//        // Library list
//        libraryList = new JList<>(libraryModel);
//        styleJList(libraryList);
//
//        // Edit panel
//        JPanel editPanel = new JPanel(new BorderLayout(0, 10));
//        editPanel.setOpaque(false);
//
//        libraryEditorArea = new JTextArea();
//        libraryEditorArea.setBackground(new Color(15, 15, 15));
//        libraryEditorArea.setForeground(TEXT_MAIN);
//
//        libraryList.addListSelectionListener(e -> {
//            Task selectedTask = libraryList.getSelectedValue();
//            if (selectedTask != null) {
//                libraryEditorArea.setText(selectedTask.getEditableString());
//            }
//        });
//
//        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
//        btnRow.setOpaque(false);
//
//        JButton btnAddToQueue = createStyledBtn("Add to Queue", new Color(0, 100, 0));
//        btnAddToQueue.addActionListener(e -> {
//            Task selectedTask = libraryList.getSelectedValue();
//            if (selectedTask != null) {
//                queueModel.addElement(new Task(selectedTask));
//            }
//        });
//
//        btnRow.add(btnAddToQueue);
//
//        editPanel.add(new JScrollPane(libraryEditorArea), BorderLayout.CENTER);
//        editPanel.add(btnRow, BorderLayout.SOUTH);
//
//        panel.add(new JScrollPane(libraryList));
//        panel.add(editPanel);
//
//        return panel;
//    }
//
//    private JPanel createBuilderTab() {
//        JPanel panel = new JPanel(new BorderLayout(15, 15));
//        panel.setBackground(BG_BASE);
//        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
//
//        // Left panel - Task metadata
//        JPanel leftPanel = createBuilderLeftPanel();
//
//        // Center panel - Action list
//        JPanel centerPanel = createBuilderCenterPanel();
//
//        // Right panel - Action configuration
//        JPanel rightPanel = createBuilderRightPanel();
//
//        panel.add(leftPanel, BorderLayout.WEST);
//        panel.add(centerPanel, BorderLayout.CENTER);
//        panel.add(rightPanel, BorderLayout.EAST);
//
//        return panel;
//    }
//
//    private JPanel createBuilderLeftPanel() {
//        JPanel panel = new JPanel(new GridBagLayout());
//        panel.setOpaque(false);
//
//        GridBagConstraints gbc = new GridBagConstraints();
//        gbc.fill = GridBagConstraints.HORIZONTAL;
//        gbc.weightx = 1.0;
//        gbc.insets = new Insets(5, 5, 5, 5);
//
//        taskNameInput = new JTextField(25);
//        taskDescInput = new JTextField(25);
//        taskStatusInput = new JTextField(25);
//
//        styleComp(taskNameInput);
//        styleComp(taskDescInput);
//        styleComp(taskStatusInput);
//
//        // Add components
//        gbc.gridy = 0;
//        panel.add(createLabel("TASK NAME:"), gbc);
//        gbc.gridy = 1;
//        panel.add(taskNameInput, gbc);
//
//        gbc.gridy = 2;
//        panel.add(createLabel("DESCRIPTION:"), gbc);
//        gbc.gridy = 3;
//        panel.add(taskDescInput, gbc);
//
//        gbc.gridy = 4;
//        panel.add(createLabel("STATUS FLAIR:"), gbc);
//        gbc.gridy = 5;
//        panel.add(taskStatusInput, gbc);
//
//        JButton btnReset = createStyledBtn("REFRESH CONTROLS", new Color(50, 50, 50));
//        btnReset.addActionListener(e -> {
//            taskNameInput.setText("");
//            taskDescInput.setText("");
//            taskStatusInput.setText("");
//            builderActionModel.clear();
//        });
//
//        gbc.gridy = 6;
//        gbc.insets = new Insets(20, 5, 5, 5);
//        panel.add(btnReset, gbc);
//
//        return panel;
//    }
//
//    private JPanel createBuilderCenterPanel() {
//        JPanel panel = new JPanel(new BorderLayout(5, 5));
//        panel.setOpaque(false);
//
//        JLabel setLabel = new JLabel("ACTIVE TASK-SET BUILDER", SwingConstants.CENTER);
//        setLabel.setForeground(ACCENT_BLOOD);
//        setLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
//
//        JList<Action> actionList = new JList<>(builderActionModel);
//        styleJList(actionList);
//
//        JButton btnAddToLibrary = createStyledBtn("ADD TO LIBRARY", new Color(0, 100, 0));
//        btnAddToLibrary.addActionListener(e -> {
//            List<Action> actions = new ArrayList<>();
//            for (int i = 0; i < builderActionModel.size(); i++) {
//                actions.add(builderActionModel.get(i));
//            }
//            libraryModel.addElement(new Task(
//                    taskNameInput.getText(),
//                    taskDescInput.getText(),
//                    actions,
//                    taskStatusInput.getText()
//            ));
//        });
//
//        panel.add(setLabel, BorderLayout.NORTH);
//        panel.add(new JScrollPane(actionList), BorderLayout.CENTER);
//        panel.add(btnAddToLibrary, BorderLayout.SOUTH);
//
//        return panel;
//    }
//
//    private JPanel createBuilderRightPanel() {
//        JPanel panel = new JPanel(new BorderLayout(0, 10));
//        panel.setOpaque(false);
//
//        // Configuration panel
//        JPanel configPanel = new JPanel(new GridLayout(0, 1, 2, 5));
//        configPanel.setOpaque(false);
//
//        actionCombo = new JComboBox<>(ActionType.values());
//        styleComp(actionCombo);
//        actionCombo.addActionListener(e -> fetchDynamicTargets());
//
//        manualTargetInput = new JTextField();
//        styleComp(manualTargetInput);
//
//        configPanel.add(createLabel("SELECT ACTION:"));
//        configPanel.add(actionCombo);
//        configPanel.add(createLabel("TARGET NAME:"));
//        configPanel.add(manualTargetInput);
//
//        JButton btnAddAction = createStyledBtn("ADD ACTION TO SET", ACCENT_ORANGE);
//        btnAddAction.addActionListener(e -> {
//            if (!manualTargetInput.getText().isEmpty()) {
//                builderActionModel.addElement(new Action(
//                        (ActionType) actionCombo.getSelectedItem(),
//                        manualTargetInput.getText()
//                ));
//            }
//        });
//
//        configPanel.add(btnAddAction);
//
//        // Nearby entities list
//        nearbyEntitiesList = new JList<>(nearbyEntitiesModel);
//        styleJList(nearbyEntitiesList);
//        nearbyEntitiesList.addMouseListener(new MouseAdapter() {
//            @Override
//            public void mouseClicked(MouseEvent e) {
//                String selectedValue = nearbyEntitiesList.getSelectedValue();
//                if (selectedValue == null) return;
//
//                if (e.getClickCount() == 1) {
//                    manualTargetInput.setText(selectedValue);
//                } else if (e.getClickCount() == 2) {
//                    builderActionModel.addElement(new Action(
//                            (ActionType) actionCombo.getSelectedItem(),
//                            selectedValue
//                    ));
//                }
//            }
//        });
//
//        JButton btnForceRefresh = createStyledBtn("FORCE REFRESH", new Color(40, 40, 40));
//        btnForceRefresh.addActionListener(e -> fetchDynamicTargets());
//
//        panel.add(configPanel, BorderLayout.NORTH);
//        panel.add(new JScrollPane(nearbyEntitiesList), BorderLayout.CENTER);
//        panel.add(btnForceRefresh, BorderLayout.SOUTH);
//
//        return panel;
//    }
//
//    private JPanel createOutputTab() {
//        JPanel panel = new JPanel(new BorderLayout());
//        panel.setBackground(BG_BASE);
//
//        consoleArea = new JTextArea();
//        consoleArea.setBackground(Color.BLACK);
//        consoleArea.setForeground(new Color(0, 255, 0));
//        consoleArea.setFont(new Font("Consolas", Font.PLAIN, 12));
//        consoleArea.setEditable(false);
//
//        DefaultCaret caret = (DefaultCaret) consoleArea.getCaret();
//        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
//
//        consoleSearch = new JTextField();
//        styleComp(consoleSearch);
//
//        btnCaptureToggle = createStyledBtn("Capture: ON", new Color(0, 80, 0));
//        btnCaptureToggle.addActionListener(e -> {
//            isCaptureEnabled = !isCaptureEnabled;
//            btnCaptureToggle.setText("Capture: " + (isCaptureEnabled ? "ON" : "OFF"));
//            btnCaptureToggle.setBackground(isCaptureEnabled ? new Color(0, 80, 0) : new Color(80, 0, 0));
//        });
//
//        JPanel topPanel = new JPanel(new BorderLayout());
//        topPanel.setOpaque(false);
//        topPanel.add(consoleSearch, BorderLayout.CENTER);
//        topPanel.add(btnCaptureToggle, BorderLayout.EAST);
//
//        panel.add(topPanel, BorderLayout.NORTH);
//        panel.add(new JScrollPane(consoleArea), BorderLayout.CENTER);
//
//        return panel;
//    }
//
//    // ========================================
//    // UI CREATION METHODS - SKILLS PANEL
//    // ========================================
//
//    private JPanel createSkillsPanel() {
//        JPanel panel = new JPanel(new BorderLayout());
//        panel.setBackground(BG_BASE);
//
//        JPanel grid = new JPanel(new GridLayout(0, 3, 3, 3));
//        grid.setBackground(BG_BASE);
//        grid.setBorder(new EmptyBorder(8, 8, 8, 8));
//
//        for (Skill skill : OSRS_ORDER) {
//            SkillData data = new SkillData(skill);
//            skillRegistry.put(skill, data);
//            grid.add(createSkillTile(data));
//        }
//
//        totalLevelLabel.setForeground(TEXT_MAIN);
//        totalLevelLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
//
//        panel.add(new JScrollPane(grid), BorderLayout.CENTER);
//        panel.add(totalLevelLabel, BorderLayout.SOUTH);
//
//        return panel;
//    }
//
//    private JPanel createSkillTile(SkillData data) {
//        JPanel tile = new JPanel(new GridBagLayout());
//        tile.setBackground(PANEL_SURFACE);
//        tile.setBorder(new LineBorder(BORDER_DIM));
//
//        GridBagConstraints gbc = new GridBagConstraints();
//        gbc.fill = GridBagConstraints.HORIZONTAL;
//        gbc.weightx = 1.0;
//        gbc.gridx = 0;
//
//        // Top section - Icon and level
//        JPanel topPanel = new JPanel(new BorderLayout());
//        topPanel.setOpaque(false);
//
//        JLabel iconLabel = new JLabel(loadSkillIcon(data.skill));
//        data.lblLevel.setForeground(ACCENT_BLOOD);
//        data.lblLevel.setFont(new Font("Arial", Font.BOLD, 18));
//
//        topPanel.add(iconLabel, BorderLayout.WEST);
//        topPanel.add(data.lblLevel, BorderLayout.EAST);
//
//        // XP string
//        data.lblXpString.setForeground(TEXT_DIM);
//        data.lblXpString.setFont(new Font("Monospaced", Font.PLAIN, 10));
//
//        // Progress bar
//        data.mainBar.setForeground(ACCENT_BLOOD);
//        data.mainBar.setBackground(Color.BLACK);
//
//        // Add components
//        gbc.gridy = 0;
//        tile.add(topPanel, gbc);
//        gbc.gridy = 1;
//        tile.add(data.lblXpString, gbc);
//        gbc.gridy = 2;
//        tile.add(data.mainBar, gbc);
//
//        // Add click listener for tracking
//        tile.addMouseListener(new MouseAdapter() {
//            @Override
//            public void mousePressed(MouseEvent e) {
//                data.isTracking = !data.isTracking;
//                tile.setBorder(new LineBorder(data.isTracking ? ACCENT_BLOOD : BORDER_DIM, 1));
//                refreshTrackerList();
//            }
//        });
//
//        return tile;
//    }
//
//    private void refreshTrackerList() {
//        trackerList.removeAll();
//
//        skillRegistry.values().stream()
//                .filter(data -> data.isTracking)
//                .forEach(data -> {
//                    trackerList.add(data.trackerPanel);
//                    trackerList.add(Box.createRigidArea(new Dimension(0, 10)));
//                });
//
//        trackerList.add(Box.createVerticalGlue());
//        trackerList.revalidate();
//        trackerList.repaint();
//    }
//
//    // ========================================
//    // UI CREATION METHODS - PLAYER PANEL
//    // ========================================
//
//    private JPanel createPlayerPanel() {
//        JPanel container = new JPanel(new BorderLayout());
//        container.setBackground(BG_BASE);
//        container.setBorder(new EmptyBorder(20, 20, 20, 20));
//
//        JPanel content = new JPanel(new GridLayout(1, 2, 20, 0));
//        content.setBackground(BG_BASE);
//
//        // Login details card
//        JPanel loginCard = createInfoCard("Login Details");
//        addInfoRow(loginCard, "Username", lblUsername);
//        addInfoRow(loginCard, "Password", lblPassword);
//        addInfoRow(loginCard, "Identifier", lblAcctId);
//        addInfoRow(loginCard, "Acct Status", lblAcctStatus);
//
//        // World/game card
//        JPanel gameCard = createInfoCard("World");
//        addInfoRow(gameCard, "Character Name", lblCharName);
//        addInfoRowWithIcon(gameCard, "Membership", lblMemberText, lblMemberIcon);
//        addInfoRow(gameCard, "World", lblWorld);
//        addInfoRow(gameCard, "Coordinates", lblCoords);
//        addInfoRow(gameCard, "GameState", lblGameState);
//
//        content.add(loginCard);
//        content.add(gameCard);
//
//        container.add(content, BorderLayout.NORTH);
//
//        return container;
//    }
//
//    private JPanel createInfoCard(String title) {
//        JPanel panel = new JPanel(new GridLayout(0, 1, 5, 10));
//        panel.setBackground(PANEL_SURFACE);
//
//        TitledBorder border = BorderFactory.createTitledBorder(
//                new LineBorder(BORDER_DIM),
//                " " + title + " "
//        );
//        border.setTitleColor(ACCENT_BLOOD);
//        border.setTitleFont(new Font("Segoe UI", Font.BOLD, 16));
//
//        panel.setBorder(BorderFactory.createCompoundBorder(
//                border,
//                new EmptyBorder(15, 15, 15, 15)
//        ));
//
//        return panel;
//    }
//
//    private void addInfoRow(JPanel panel, String key, JLabel valueLabel) {
//        JPanel row = new JPanel(new BorderLayout());
//        row.setOpaque(false);
//
//        JLabel keyLabel = new JLabel(key);
//        keyLabel.setForeground(TEXT_DIM);
//
//        valueLabel.setForeground(TEXT_MAIN);
//        valueLabel.setFont(new Font("Consolas", Font.BOLD, 14));
//
//        row.add(keyLabel, BorderLayout.WEST);
//        row.add(valueLabel, BorderLayout.EAST);
//        row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 40, 40)));
//
//        panel.add(row);
//    }
//
//    private void addInfoRowWithIcon(JPanel panel, String key, JLabel valueLabel, JLabel iconLabel) {
//        JPanel row = new JPanel(new BorderLayout(5, 0));
//        row.setOpaque(false);
//
//        JLabel keyLabel = new JLabel(key);
//        keyLabel.setForeground(TEXT_DIM);
//
//        JPanel rightSide = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
//        rightSide.setOpaque(false);
//
//        valueLabel.setForeground(TEXT_MAIN);
//        valueLabel.setFont(new Font("Consolas", Font.BOLD, 14));
//
//        rightSide.add(valueLabel);
//        rightSide.add(iconLabel);
//
//        row.add(keyLabel, BorderLayout.WEST);
//        row.add(rightSide, BorderLayout.EAST);
//        row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 40, 40)));
//
//        panel.add(row);
//    }
//
//    // ========================================
//    // UI CREATION METHODS - SETTINGS PANEL
//    // ========================================
//
//    private JPanel createSettingsInterface() {
//        JPanel container = new JPanel(new BorderLayout());
//        container.setBackground(BG_BASE);
//
//        CardLayout cardLayout = new CardLayout();
//        JPanel contentPanel = new JPanel(cardLayout);
//        contentPanel.setBackground(BG_BASE);
//        contentPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
//
//        // Add all setting panels
//        contentPanel.add(createClientPanel(), "Client");
//        contentPanel.add(createActivitiesPanel(), "Activities");
//        contentPanel.add(createAudioPanel(), "Audio");
//        contentPanel.add(createChatPanel(), "Chat");
//        contentPanel.add(createDisplayPanel(), "Display");
//        contentPanel.add(createControlsPanel(), "Controls");
//        contentPanel.add(createWarningsPanel(), "Warnings");
//
//        // Create menu panel
//        JPanel menuPanel = new JPanel(new GridLayout(10, 1, 0, 2));
//        menuPanel.setPreferredSize(new Dimension(180, 0));
//        menuPanel.setBackground(PANEL_SURFACE);
//        menuPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_DIM));
//
//        String[] categories = {"Client", "Display", "Audio", "Chat", "Controls", "Activities", "Warnings"};
//        ButtonGroup buttonGroup = new ButtonGroup();
//
//        for (String category : categories) {
//            JToggleButton button = createMenuButton(category);
//            button.addActionListener(e -> cardLayout.show(contentPanel, category));
//            buttonGroup.add(button);
//            menuPanel.add(button);
//
//            if (category.equals("Client")) {
//                button.setSelected(true);
//            }
//        }
//
//        container.add(menuPanel, BorderLayout.WEST);
//        container.add(contentPanel, BorderLayout.CENTER);
//
//        return container;
//    }
//
//    private JPanel createClientPanel() {
//        return createSettingsGroup("Client",
//                createSettingCheck("Disable Rendering",
//                        Client.isRenderingDisabled(),
//                        e -> Client.setRenderingDisabled(((JCheckBox) e.getSource()).isSelected())
//                )
//        );
//    }
//
//    private JPanel createDisplayPanel() {
//        return createSettingsGroup("Display",
//                createSettingCheck("Roofs",
//                        !ClientSettings.areRoofsHidden(),
//                        e -> ClientSettings.toggleRoofs(((JCheckBox) e.getSource()).isSelected())
//                ),
//                createSettingCheck("Data orbs",
//                        ClientSettings.areDataOrbsEnabled(),
//                        e -> ClientSettings.toggleDataOrbs(((JCheckBox) e.getSource()).isSelected())
//                ),
//                createSettingCheck("Transparent side panel",
//                        ClientSettings.isTransparentSidePanelEnabled(),
//                        e -> ClientSettings.toggleTransparentSidePanel(((JCheckBox) e.getSource()).isSelected())
//                )
//        );
//    }
//
//    private JPanel createAudioPanel() {
//        return createSettingsGroup("Audio",
//                createSettingCheck("Game Audio",
//                        ClientSettings.isGameAudioOn(),
//                        e -> ClientSettings.toggleGameAudio(((JCheckBox) e.getSource()).isSelected())
//                )
//        );
//    }
//
//    private JPanel createChatPanel() {
//        return createSettingsGroup("Chat",
//                createSettingCheck("Transparent chatbox",
//                        ClientSettings.isTransparentChatboxEnabled(),
//                        e -> ClientSettings.toggleTransparentChatbox(((JCheckBox) e.getSource()).isSelected())
//                ),
//                createSettingCheck("Click through chatbox",
//                        ClientSettings.isClickThroughChatboxEnabled(),
//                        e -> ClientSettings.toggleClickThroughChatbox(((JCheckBox) e.getSource()).isSelected())
//                )
//        );
//    }
//
//    private JPanel createControlsPanel() {
//        return createSettingsGroup("Controls",
//                createSettingCheck("Shift click drop",
//                        ClientSettings.isShiftClickDroppingEnabled(),
//                        e -> ClientSettings.toggleShiftClickDropping(((JCheckBox) e.getSource()).isSelected())
//                ),
//                createSettingCheck("Esc closes interface",
//                        ClientSettings.isEscInterfaceClosingEnabled(),
//                        e -> ClientSettings.toggleEscInterfaceClosing(((JCheckBox) e.getSource()).isSelected())
//                )
//        );
//    }
//
//    private JPanel createWarningsPanel() {
//        return createSettingsGroup("Warnings",
//                createSettingCheck("Loot notifications",
//                        ClientSettings.areLootNotificationsEnabled(),
//                        e -> ClientSettings.toggleLootNotifications(((JCheckBox) e.getSource()).isSelected())
//                )
//        );
//    }
//
//    private JPanel createActivitiesPanel() {
//        return createSettingsGroup("Activities",
//                createSettingCheck("Level-up interface",
//                        ClientSettings.isLevelUpInterfaceEnabled(),
//                        e -> ClientSettings.toggleLevelUpInterface(((JCheckBox) e.getSource()).isSelected())
//                )
//        );
//    }
//
//    private JPanel createSettingsGroup(String title, Component... components) {
//        JPanel wrapper = new JPanel(new BorderLayout());
//        wrapper.setBackground(BG_BASE);
//
//        JLabel header = new JLabel(title);
//        header.setForeground(ACCENT_BLOOD);
//        header.setFont(new Font("Segoe UI", Font.BOLD, 24));
//        header.setBorder(new EmptyBorder(0, 0, 20, 0));
//
//        JPanel list = new JPanel(new GridLayout(0, 1, 5, 5));
//        list.setBackground(BG_BASE);
//
//        for (Component component : components) {
//            list.add(component);
//        }
//
//        wrapper.add(header, BorderLayout.NORTH);
//        wrapper.add(list, BorderLayout.CENTER);
//
//        return wrapper;
//    }
//
//    private JCheckBox createSettingCheck(String text, boolean initialState, ActionListener listener) {
//        JCheckBox checkBox = new JCheckBox(text);
//        checkBox.setForeground(TEXT_MAIN);
//        checkBox.setOpaque(false);
//        checkBox.setSelected(initialState);
//        checkBox.setFont(new Font("Segoe UI", Font.PLAIN, 14));
//
//        if (listener != null) {
//            checkBox.addActionListener(listener);
//        }
//
//        return checkBox;
//    }
//
//    private JToggleButton createMenuButton(String text) {
//        JToggleButton button = new JToggleButton(text) {
//            @Override
//            protected void paintComponent(Graphics g) {
//                g.setColor(isSelected() ? TAB_SELECTED : PANEL_SURFACE);
//                g.fillRect(0, 0, getWidth(), getHeight());
//                super.paintComponent(g);
//            }
//        };
//
//        button.setFocusPainted(false);
//        button.setContentAreaFilled(false);
//        button.setForeground(TEXT_MAIN);
//        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
//        button.setHorizontalAlignment(SwingConstants.LEFT);
//        button.setBorder(new EmptyBorder(10, 20, 10, 0));
//
//        return button;
//    }
//
//    // ========================================
//    // HELPER METHODS - DATA
//    // ========================================
//
//    private void fetchDynamicTargets() {
//        ActionType selectedType = (ActionType) actionCombo.getSelectedItem();
//        nearbyEntitiesModel.clear();
//
//        Set<String> names = new HashSet<>();
//
//        try {
//            switch (selectedType) {
//                case ATTACK:
//                    names = NPCs.all().stream()
//                            .filter(npc -> npc != null && npc.hasAction("Attack"))
//                            .map(npc -> npc.getName())
//                            .filter(Objects::nonNull)
//                            .collect(Collectors.toSet());
//                    break;
//
//                case CHOP:
//                    names = GameObjects.all().stream()
//                            .filter(obj -> obj != null && obj.hasAction("Chop down"))
//                            .map(obj -> obj.getName())
//                            .filter(Objects::nonNull)
//                            .collect(Collectors.toSet());
//                    break;
//
//                case MINE:
//                    names = GameObjects.all().stream()
//                            .filter(obj -> obj != null && obj.hasAction("Mine"))
//                            .map(obj -> obj.getName())
//                            .filter(Objects::nonNull)
//                            .collect(Collectors.toSet());
//                    break;
//
//                case FISH:
//                    names = NPCs.all().stream()
//                            .filter(npc -> npc != null && npc.hasAction("Net"))
//                            .map(npc -> npc.getName())
//                            .filter(Objects::nonNull)
//                            .collect(Collectors.toSet());
//                    break;
//
//                case TALK_TO:
//                    names = NPCs.all().stream()
//                            .filter(npc -> npc != null && npc.hasAction("Talk-to"))
//                            .map(npc -> npc.getName())
//                            .filter(Objects::nonNull)
//                            .collect(Collectors.toSet());
//                    break;
//
//                default:
//                    names = NPCs.all().stream()
//                            .filter(Objects::nonNull)
//                            .map(npc -> npc.getName())
//                            .filter(Objects::nonNull)
//                            .collect(Collectors.toSet());
//                    break;
//            }
//
//            names.stream()
//                    .sorted()
//                    .forEach(nearbyEntitiesModel::addElement);
//
//        } catch (Exception e) {
//            Logger.log("Error fetching dynamic targets: " + e.getMessage());
//        }
//    }
//
//    private void initializeGenericLibrary() {
//        libraryModel.addElement(new Task(
//                "Woodcutter",
//                "Chops Trees",
//                Arrays.asList(new Action(ActionType.CHOP, "Tree")),
//                "Chopping..."
//        ));
//
//        libraryModel.addElement(new Task(
//                "Miner",
//                "Mines Copper ore",
//                Arrays.asList(new Action(ActionType.MINE, "Copper rocks")),
//                "Mining..."
//        ));
//
//        libraryModel.addElement(new Task(
//                "Fisher",
//                "Fishes at net spot",
//                Arrays.asList(new Action(ActionType.FISH, "Fishing spot")),
//                "Fishing..."
//        ));
//    }
//
//    private void loadIntoBuilder(Task task) {
//        if (task == null) return;
//
//        taskNameInput.setText(task.name);
//        taskDescInput.setText(task.desc);
//        taskStatusInput.setText(task.status);
//
//        builderActionModel.clear();
//        task.actions.forEach(builderActionModel::addElement);
//    }
//
//    private void shiftQueue(int direction) {
//        int selectedIndex = taskQueueList.getSelectedIndex();
//        int newIndex = selectedIndex + direction;
//
//        if (selectedIndex == -1 || newIndex < 0 || newIndex >= queueModel.size()) {
//            return;
//        }
//
//        Task task = queueModel.remove(selectedIndex);
//        queueModel.add(newIndex, task);
//        taskQueueList.setSelectedIndex(newIndex);
//    }
//
//    // ========================================
//    // HELPER METHODS - UI CONTROLS
//    // ========================================
//
//    private void toggleScriptState() {
//        if (script == null) return;
//
//        if (isScriptPaused) {
//            script.getScriptManager().resume();
//            btnPlayPause.setText("▮▮");
//            isScriptPaused = false;
//        } else {
//            script.getScriptManager().pause();
//            btnPlayPause.setText("▶");
//            isScriptPaused = true;
//        }
//    }
//
//    private void stopScript() {
//        int result = JOptionPane.showConfirmDialog(
//                this,
//                "Are you sure you want to stop the script?",
//                "Stop Script",
//                JOptionPane.YES_NO_OPTION
//        );
//
//        if (result == JOptionPane.YES_OPTION) {
//            script.stop();
//            dispose();
//        }
//    }
//
//    private void toggleUserInput() {
//        isUserInputAllowed = !isUserInputAllowed;
//
//        Client.getInstance().setMouseInputEnabled(isUserInputAllowed);
//        Client.getInstance().setKeyboardInputEnabled(isUserInputAllowed);
//
//        btnInputToggle.setText(isUserInputAllowed ? "🖱" : "🚫");
//        btnInputToggle.setBackground(isUserInputAllowed ? new Color(30, 0, 0) : new Color(80, 0, 0));
//    }
//
//    // ========================================
//    // HELPER METHODS - UI STYLING
//    // ========================================
//
//    private void styleComp(JComponent component) {
//        component.setBackground(PANEL_SURFACE);
//        component.setForeground(TEXT_MAIN);
//
//        if (component instanceof JTextField) {
//            ((JTextField) component).setCaretColor(ACCENT_BLOOD);
//        }
//
//        if (component instanceof JComboBox) {
//            component.setFont(new Font("Segoe UI", Font.PLAIN, 12));
//        }
//    }
//
//    private void styleJList(JList<?> list) {
//        list.setBackground(PANEL_SURFACE);
//        list.setForeground(TEXT_MAIN);
//        list.setSelectionBackground(TAB_SELECTED);
//        list.setSelectionForeground(Color.WHITE);
//        list.setFont(new Font("Segoe UI", Font.PLAIN, 13));
//    }
//
//    private void styleHeaderLabel(JLabel label) {
//        label.setForeground(TEXT_MAIN);
//        label.setFont(new Font("Consolas", Font.BOLD, 15));
//        label.setHorizontalAlignment(SwingConstants.RIGHT);
//    }
//
//    private void styleSpinner(JSpinner spinner) {
//        JFormattedTextField field = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
//        field.setBackground(new Color(30, 30, 30));
//        field.setForeground(ACCENT_BLOOD);
//        field.setFont(new Font("Segoe UI", Font.PLAIN, 12));
//        spinner.setBorder(new LineBorder(BORDER_DIM));
//    }
//
//    private JButton createStyledBtn(String text, Color backgroundColor) {
//        JButton button = new JButton(text);
//        button.setBackground(backgroundColor);
//        button.setForeground(Color.WHITE);
//        button.setFocusPainted(false);
//        button.setBorder(new LineBorder(BORDER_DIM));
//        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
//        return button;
//    }
//
//    private JButton createIconButton(String symbol, String tooltip, ActionListener action) {
//        JButton button = new JButton(symbol);
//        button.setPreferredSize(new Dimension(40, 40));
//        button.setFont(new Font("Segoe UI Symbol", Font.BOLD, 18));
//        button.setBackground(new Color(30, 0, 0));
//        button.setForeground(ACCENT_BLOOD);
//        button.setToolTipText(tooltip);
//        button.setFocusPainted(false);
//        button.addActionListener(action);
//        return button;
//    }
//
//    private JLabel createLabel(String text) {
//        JLabel label = new JLabel(text);
//        label.setForeground(TEXT_MAIN);
//        label.setFont(new Font("Segoe UI", Font.PLAIN, 12));
//        return label;
//    }
//
//    // ========================================
//    // HELPER METHODS - RESOURCES
//    // ========================================
//
//    private ImageIcon loadMiscIcon(String name) {
//        try {
//            return new ImageIcon(
//                    new ImageIcon(Objects.requireNonNull(
//                            getClass().getResource("/resources/icons/misc/" + name + ".png")
//                    )).getImage().getScaledInstance(18, 18, Image.SCALE_SMOOTH)
//            );
//        } catch (Exception e) {
//            Logger.log("Failed to load misc icon: " + name);
//            return null;
//        }
//    }
//
//    private ImageIcon loadSkillIcon(Skill skill) {
//        try {
//            return new ImageIcon(
//                    new ImageIcon(Objects.requireNonNull(
//                            getClass().getResource("/resources/icons/skills/" + skill.name().toLowerCase() + "_icon.png")
//                    )).getImage().getScaledInstance(26, 26, Image.SCALE_SMOOTH)
//            );
//        } catch (Exception e) {
//            Logger.log("Failed to load skill icon: " + skill.name());
//            return new ImageIcon();
//        }
//    }
//
//    private ImageIcon loadStatusIcon(String name) {
//        try {
//            return new ImageIcon(
//                    new ImageIcon(Objects.requireNonNull(
//                            getClass().getResource("/resources/icons/status/" + name + ".png")
//                    )).getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH)
//            );
//        } catch (Exception e) {
//            Logger.log("Failed to load status icon: " + name);
//            return null;
//        }
//    }
//
//    // ========================================
//    // UPDATE METHODS
//    // ========================================
//
//    private void startUpdateTimers(JTabbedPane mainTabs) {
//        // Main UI update timer (1 second)
//        new javax.swing.Timer(1000, e -> updateAll()).start();
//
//        // Dynamic targets refresh timer (5 seconds, only for Task Builder tab)
//        new javax.swing.Timer(5000, e -> {
//            if (mainTabs.getSelectedIndex() == 2) { // Task Builder is tab index 2
//                fetchDynamicTargets();
//            }
//        }).start();
//    }
//
//    private void updateAll() {
//        updateSkills();
//        updatePlayerInfo();
//    }
//
//    private void updateSkills() {
//        int projectionHours = (int) projectionSpinner.getValue();
//        long totalXpGained = 0;
//        int totalLevelsGained = 0;
//        int p2pTotal = 0;
//        int f2pTotal = 0;
//
//        for (SkillData data : skillRegistry.values()) {
//            int currentXp = Skills.getExperience(data.skill);
//            int currentLevel = Skills.getRealLevel(data.skill);
//
//            data.update(currentXp, currentLevel, startTime, projectionHours);
//
//            p2pTotal += currentLevel;
//            if (F2P_SKILLS.contains(data.skill)) {
//                f2pTotal += currentLevel;
//            }
//
//            if (data.isTracking) {
//                totalXpGained += Math.max(0, currentXp - data.startXP);
//                totalLevelsGained += Math.max(0, currentLevel - data.startLevel);
//            }
//        }
//
//        totalXpGainedLabel.setText("Total XP Gained: " + String.format("%,d", totalXpGained));
//        totalLevelsGainedLabel.setText("Total Levels Gained: " + totalLevelsGained);
//        totalLevelLabel.setText(String.format("F2P Total: %d | P2P Total: %d", f2pTotal, p2pTotal));
//    }
//
//    private void updatePlayerInfo() {
//        if (!Client.isLoggedIn() || Players.getLocal() == null) {
//            return;
//        }
//
//        try {
//            // Update player name
//            lblUsername.setText(Players.getLocal().getName());
//            lblCharName.setText(Players.getLocal().getName());
//
//            // Update password (if available)
//            lblPassword.setText(Optional.ofNullable(Client.getPassword()).orElse("***"));
//
//            // Update world
//            lblWorld.setText("World " + (Worlds.getCurrent() != null ? Worlds.getCurrent().getWorld() : "?"));
//
//            // Update membership status
//            boolean isMember = Client.isMembers();
//            lblMemberText.setText(isMember ? P2P : "F2P");
//            lblMemberIcon.setIcon(loadStatusIcon(isMember ? "Member_icon" : "Free-to-play_icon"));
//
//            // Update coordinates
//            lblCoords.setText(Players.getLocal().getTile().toString());
//
//            // Update game state
//            lblGameState.setText(Client.getGameState().name());
//
//        } catch (Exception e) {
//            Logger.log("Error updating player info: " + e.getMessage());
//        }
//    }
//
//    // ========================================
//    // INNER CLASSES - DATA MODELS
//    // ========================================
//
//    /**
//     * Represents a task with actions to perform
//     */
//    public static class Task {
//        public String name;
//        public String desc;
//        public String status;
//        public List<Action> actions;
//
//        private static final String[] HUMAN_FLAIR = {
//                "Thinking...", "Working...", "Analyzing...", "Processing...", "Computing..."
//        };
//
//        public Task(String name, String desc, List<Action> actions, String status) {
//            this.name = name;
//            this.desc = desc;
//            this.actions = actions;
//            this.status = (status == null || status.isEmpty())
//                    ? HUMAN_FLAIR[new Random().nextInt(HUMAN_FLAIR.length)]
//                    : status;
//        }
//
//        public Task(Task other) {
//            this(other.name, other.desc, new ArrayList<>(other.actions), other.status);
//        }
//
//        public String getEditableString() {
//            return "NAME: " + name + "\nDESC: " + desc + "\nSTATUS: " + status;
//        }
//
//        @Override
//        public String toString() {
//            return name + " | " + status;
//        }
//    }
//
//    /**
//     * Represents a single action within a task
//     */
//    public static class Action {
//        public ActionType type;
//        public String target;
//
//        public Action(ActionType type, String target) {
//            this.type = type;
//            this.target = target;
//        }
//
//        public boolean execute() {
//            try {
//                switch (type) {
//                    case CHOP:
//                        return GameObjects.closest(target) != null &&
//                                GameObjects.closest(target).interact("Chop down");
//
//                    case ATTACK:
//                        return NPCs.closest(target) != null &&
//                                NPCs.closest(target).interact("Attack");
//
//                    case BANK:
//                        return org.dreambot.api.methods.container.impl.bank.Bank.open();
//
//                    case MINE:
//                        return GameObjects.closest(target) != null &&
//                                GameObjects.closest(target).interact("Mine");
//
//                    case FISH:
//                        return NPCs.closest(target) != null &&
//                                NPCs.closest(target).interact("Net");
//
//                    case TALK_TO:
//                        return NPCs.closest(target) != null &&
//                                NPCs.closest(target).interact("Talk-to");
//
//                    default:
//                        Logger.log("Action " + type + " not implemented in Action.execute()");
//                        return false;
//                }
//            } catch (Exception e) {
//                Logger.log("Error executing action " + type + ": " + e.getMessage());
//                return false;
//            }
//        }
//
//        @Override
//        public String toString() {
//            return type.name() + " -> " + target;
//        }
//    }
//
//    // ========================================
//    // INNER CLASSES - UI RENDERERS
//    // ========================================
//
//    /**
//     * Custom renderer for task list showing current execution
//     */
//    private class TaskCellRenderer extends DefaultListCellRenderer {
//        @Override
//        public Component getListCellRendererComponent(JList<?> list, Object value,
//                                                      int index, boolean isSelected, boolean cellHasFocus) {
//            JLabel label = (JLabel) super.getListCellRendererComponent(
//                    list, value, index, isSelected, cellHasFocus
//            );
//
//            if (index == currentExecutionIndex) {
//                label.setBackground(TAB_SELECTED);
//                label.setForeground(Color.WHITE);
//                label.setText("▶ " + label.getText());
//            }
//
//            return label;
//        }
//    }
//
//    // ========================================
//    // INNER CLASSES - SKILL TRACKING
//    // ========================================
//
//    /**
//     * Tracks skill progress and displays statistics
//     */
//    private class SkillData {
//        final Skill skill;
//        final JProgressBar mainBar = new JProgressBar(0, 100);
//        final JLabel lblLevel = new JLabel("1");
//        final JLabel lblXpString = new JLabel("0/0");
//        final JPanel trackerPanel = new JPanel(new GridLayout(0, 1, 2, 2));
//
//        final JLabel lblGained = new JLabel();
//        final JLabel lblPerHour = new JLabel();
//        final JLabel lblRemaining = new JLabel();
//        final JLabel lblTTL = new JLabel();
//        final JLabel lblProj = new JLabel();
//        final JLabel lblActs = new JLabel();
//
//        final int startXP;
//        final int startLevel;
//        boolean isTracking = false;
//
//        SkillData(Skill skill) {
//            this.skill = skill;
//            this.startXP = Skills.getExperience(skill);
//            this.startLevel = Skills.getRealLevel(skill);
//
//            trackerPanel.setBackground(new Color(30, 30, 30));
//
//            TitledBorder border = BorderFactory.createTitledBorder(
//                    new LineBorder(BORDER_DIM),
//                    " " + skill.name() + " "
//            );
//            border.setTitleColor(ACCENT_BLOOD);
//            trackerPanel.setBorder(border);
//
//            JLabel[] labels = {lblGained, lblPerHour, lblRemaining, lblActs, lblTTL, lblProj};
//            for (JLabel label : labels) {
//                label.setForeground(TEXT_MAIN);
//                label.setFont(new Font("Consolas", Font.PLAIN, 12));
//                trackerPanel.add(label);
//            }
//        }
//
//        void update(int currentXp, int currentLevel, long startTime, int projectionHours) {
//            int currentMin = Skills.getExperienceForLevel(currentLevel);
//            int currentMax = Skills.getExperienceForLevel(currentLevel + 1);
//
//            // Update level display
//            lblLevel.setText(String.valueOf(currentLevel));
//
//            // Update XP string
//            lblXpString.setText(String.format("%,d / %,d XP", currentXp, currentMax));
//
//            // Update progress bar
//            int progress = (int) (((double) (currentXp - currentMin) / Math.max(1, currentMax - currentMin)) * 100);
//            mainBar.setValue(progress);
//
//            // Calculate rates
//            long elapsedMs = System.currentTimeMillis() - startTime;
//            double elapsedHours = elapsedMs / 3600000.0;
//            int xpGained = Math.max(0, currentXp - startXP);
//            int xpPerHour = (int) (xpGained / Math.max(0.0001, elapsedHours));
//            int xpRemaining = Math.max(0, currentMax - currentXp);
//
//            // Update tracker labels
//            lblGained.setText(" GAINED: " + String.format("%,d XP", xpGained));
//            lblPerHour.setText(" XP/HR:  " + String.format("%,d", xpPerHour));
//            lblRemaining.setText(" TO LEVEL: " + String.format("%,d", xpRemaining));
//
//            if (xpPerHour > 0) {
//                double hoursToLevel = (double) xpRemaining / xpPerHour;
//                lblTTL.setText(String.format(" TIME TO L: %.2f hrs", hoursToLevel));
//
//                int projectedXpGain = xpPerHour * projectionHours;
//                int projectedLevel = currentLevel;
//
//                // Calculate projected level
//                int tempXp = currentXp;
//                while (projectedXpGain > 0 && projectedLevel < 99) {
//                    int nextLevelXp = Skills.getExperienceForLevel(projectedLevel + 1);
//                    int xpToNextLevel = nextLevelXp - tempXp;
//
//                    if (projectedXpGain >= xpToNextLevel) {
//                        projectedXpGain -= xpToNextLevel;
//                        tempXp = nextLevelXp;
//                        projectedLevel++;
//                    } else {
//                        break;
//                    }
//                }
//
//                lblProj.setText(String.format(" PROJ (%dH): Lvl %d", projectionHours, projectedLevel));
//            } else {
//                lblTTL.setText(" TIME TO L: --");
//                lblProj.setText(String.format(" PROJ (%dH): --", projectionHours));
//            }
//        }
//    }
//}