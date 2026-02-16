package main.menu;

import org.dreambot.api.Client;
import org.dreambot.api.ClientSettings;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.methods.skills.Skills;
import org.dreambot.api.methods.world.Worlds;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.utilities.Logger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.text.DecimalFormat;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <h1>ETAbot</h1>
 * @author ETAbot Dev
 * @version 15.0.0-Elite
 */
public class DreamBotMenu3 extends JFrame {

    private final AbstractScript script;

    // --- State ---
    private boolean isScriptPaused = true;
    private boolean isUserInputAllowed = true;
    private boolean isCaptureEnabled = true; // kept for future use if needed
    private int currentExecutionIndex = 0;

    // --- Data & Presets ---
    private final Map<Skill, SkillData> skillRegistry = new EnumMap<>(Skill.class);
    private final DefaultListModel<Task> queueModel = new DefaultListModel<>();
    private final DefaultListModel<Task> libraryModel = new DefaultListModel<>();
    private final DefaultListModel<Action> builderActionModel = new DefaultListModel<>();
    private final DefaultListModel<String> nearbyEntitiesModel = new DefaultListModel<>();
    private final List<List<Task>> presets = new ArrayList<>(Arrays.asList(
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()
    ));

    // --- UI Components ---
    private final JPanel trackerList;
    private final JPanel sidePanel;
    private final JLabel totalXpGainedLabel = new JLabel("Total XP Gained: 0");
    private final JLabel totalLevelsGainedLabel = new JLabel("Total Levels Gained: 0");
    private final JLabel totalLevelLabel = new JLabel("F2P Total: 0 | P2P Total: 0", SwingConstants.CENTER);
    private final JProgressBar statusProgress = new JProgressBar(0, 100);
    private final JLabel lblStatus = new JLabel("Status: Idle");
    private final JSpinner projectionSpinner;
    private final long startTime;

    private JList<Task> taskQueueList;
    private JList<Task> libraryList;
    private JList<String> nearbyEntitiesList;
    private JTextArea libraryEditorArea;
    private JTextField taskNameInput;
    private JTextField taskDescInput;
    private JTextField taskStatusInput;
    private JTextField manualTargetInput;
    private JComboBox<ActionType> actionCombo;
    private JButton btnPlayPause;
    private JButton btnInputToggle;

    // --- Theme ---
    private final Color BG_BASE = new Color(12, 12, 12);
    private final Color PANEL_SURFACE = new Color(24, 24, 24);
    private final Color ACCENT_BLOOD = new Color(150, 0, 0);
    private final Color ACCENT_ORANGE = new Color(220, 80, 0);
    private final Color BORDER_DIM = new Color(45, 45, 45);
    private final Color TEXT_MAIN = new Color(210, 210, 210);
    private final Color TEXT_DIM = new Color(140, 140, 140);
    private final Color TAB_SELECTED = new Color(60, 0, 0);

    // --- Labels ---
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

    public enum ActionType {
        ATTACK, BANK, BURY, CHOP, COOK, DROP, EXAMINE, FISH, MINE, OPEN, TALK_TO, USE_ON
    }

    private static final Skill[] OSRS_ORDER = {
            Skill.ATTACK, Skill.HITPOINTS, Skill.MINING, Skill.STRENGTH, Skill.AGILITY,
            Skill.SMITHING, Skill.DEFENCE, Skill.HERBLORE, Skill.FISHING, Skill.RANGED,
            Skill.THIEVING, Skill.COOKING, Skill.PRAYER, Skill.CRAFTING, Skill.FIREMAKING,
            Skill.MAGIC, Skill.FLETCHING, Skill.WOODCUTTING, Skill.RUNECRAFTING, Skill.SLAYER,
            Skill.FARMING, Skill.CONSTRUCTION, Skill.HUNTER, Skill.SAILING
    };

    private static final Set<Skill> F2P_SKILLS = new HashSet<>(Arrays.asList(
            Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.PRAYER,
            Skill.MAGIC, Skill.HITPOINTS, Skill.CRAFTING, Skill.MINING, Skill.SMITHING,
            Skill.FISHING, Skill.COOKING, Skill.FIREMAKING, Skill.WOODCUTTING, Skill.RUNECRAFTING
    ));

    public DreamBotMenu3(AbstractScript script) {
        this.script = script;
        this.startTime = System.currentTimeMillis();

        setTitle("ETAbot | DreamBot Manager v3");
        setSize(1400, 950);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(BG_BASE);

        initSkillRegistry();

        JPanel header = createHeaderPanel();

        JTabbedPane mainTabs = new JTabbedPane();
        mainTabs.setBackground(PANEL_SURFACE);
        mainTabs.setForeground(TEXT_MAIN);

        mainTabs.addTab("Task List", createTaskListTab());
        mainTabs.addTab("Task Library", createLibraryTab());
        mainTabs.addTab("Task Builder", createBuilderTab());
        mainTabs.addTab("Skill Tracker", loadMiscIcon("Stats_icon"), createSkillsPanel());
        mainTabs.addTab("Player", null, createPlayerPanel());
        mainTabs.addTab("Settings", null, createSettingsInterface());

        sidePanel = new JPanel(new BorderLayout());
        sidePanel.setPreferredSize(new Dimension(360, 0));
        sidePanel.setBackground(PANEL_SURFACE);
        sidePanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER_DIM));

        projectionSpinner = new JSpinner(new SpinnerNumberModel(24, 1, 999, 1));
        styleSpinner(projectionSpinner);

        trackerList = new JPanel();
        trackerList.setLayout(new BoxLayout(trackerList, BoxLayout.Y_AXIS));
        trackerList.setBackground(PANEL_SURFACE);

        JScrollPane trackerScroll = new JScrollPane(trackerList);
        trackerScroll.setBorder(null);
        trackerScroll.getViewport().setBackground(PANEL_SURFACE);

        JPanel trackerHeader = new JPanel(new BorderLayout());
        trackerHeader.setBackground(PANEL_SURFACE);
        trackerHeader.setBorder(new EmptyBorder(5, 5, 5, 5));

        JLabel trackerTitle = new JLabel("Live Skill Tracker");
        trackerTitle.setForeground(ACCENT_BLOOD);
        trackerTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));

        JPanel trackerTopRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        trackerTopRight.setOpaque(false);
        JLabel projLabel = new JLabel("Projection (hrs):");
        projLabel.setForeground(TEXT_DIM);
        trackerTopRight.add(projLabel);
        trackerTopRight.add(projectionSpinner);

        trackerHeader.add(trackerTitle, BorderLayout.WEST);
        trackerHeader.add(trackerTopRight, BorderLayout.EAST);

        JPanel trackerFooter = new JPanel(new GridLayout(0, 1));
        trackerFooter.setBackground(PANEL_SURFACE);
        trackerFooter.setBorder(new EmptyBorder(5, 5, 5, 5));
        totalLevelLabel.setForeground(TEXT_MAIN);
        totalLevelLabel.setFont(new Font("Consolas", Font.BOLD, 13));
        trackerFooter.add(totalLevelLabel);

        sidePanel.add(trackerHeader, BorderLayout.NORTH);
        sidePanel.add(trackerScroll, BorderLayout.CENTER);
        sidePanel.add(trackerFooter, BorderLayout.SOUTH);

        JButton toggleTrackerBtn = new JButton(">");
        toggleTrackerBtn.setPreferredSize(new Dimension(22, 0));
        toggleTrackerBtn.setBackground(PANEL_SURFACE);
        toggleTrackerBtn.setForeground(ACCENT_BLOOD);
        toggleTrackerBtn.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 1, BORDER_DIM));
        toggleTrackerBtn.addActionListener(e -> {
            sidePanel.setVisible(!sidePanel.isVisible());
            toggleTrackerBtn.setText(sidePanel.isVisible() ? "<" : ">");
            revalidate();
        });

        add(header, BorderLayout.NORTH);
        add(mainTabs, BorderLayout.CENTER);
        add(toggleTrackerBtn, BorderLayout.EAST);
        add(sidePanel, BorderLayout.EAST);

        initializeGenericLibrary();

        setVisible(true);

        new javax.swing.Timer(1000, e -> updateAll()).start();
        new javax.swing.Timer(5000, e -> {
            if (mainTabs.getSelectedIndex() == 2) {
                fetchDynamicTargets();
            }
        }).start();
    }

    // --- Bridge Methods for Main Script ---

    public boolean isScriptPaused() {
        return isScriptPaused;
    }

    public DefaultListModel<Task> getQueueModel() {
        return queueModel;
    }

    public int getCurrentExecutionIndex() {
        return currentExecutionIndex;
    }

    public void setCurrentExecutionIndex(int i) {
        this.currentExecutionIndex = i;
        if (taskQueueList != null) {
            taskQueueList.repaint();
        }
    }

    public void setLabelStatus(String text) {
        SwingUtilities.invokeLater(() -> lblStatus.setText(text));
    }

    public void incrementExecutionIndex() {
        if (currentExecutionIndex < queueModel.size() - 1) {
            currentExecutionIndex++;
        } else {
            currentExecutionIndex = 0;
            isScriptPaused = true;
            if (btnPlayPause != null) {
                btnPlayPause.setText("▶");
            }
        }
        if (taskQueueList != null) {
            taskQueueList.repaint();
        }
    }

    // --- Task List Tab ---

    private JPanel createTaskListTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(BG_BASE);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel nav = new JPanel(new GridLayout(0, 1, 0, 5));
        nav.setOpaque(false);

        JButton btnUp = createStyledBtn("▲", new Color(40, 40, 40));
        btnUp.addActionListener(e -> shiftQueue(-1));

        JButton btnDown = createStyledBtn("▼", new Color(40, 40, 40));
        btnDown.addActionListener(e -> shiftQueue(1));

        JButton btnLoad = createStyledBtn("LOAD", ACCENT_BLOOD);
        btnLoad.addActionListener(e -> loadIntoBuilder(taskQueueList.getSelectedValue()));

        nav.add(btnUp);
        nav.add(btnDown);
        nav.add(btnLoad);

        taskQueueList = new JList<>(queueModel);
        taskQueueList.setCellRenderer(new TaskCellRenderer());
        styleJList(taskQueueList);

        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomRow.setOpaque(false);

        JButton btnDelete = createStyledBtn("DELETE TASK", new Color(100, 0, 0));
        btnDelete.addActionListener(e -> {
            int idx = taskQueueList.getSelectedIndex();
            if (idx != -1) {
                queueModel.remove(idx);
            }
        });

        bottomRow.add(btnDelete);

        panel.add(nav, BorderLayout.WEST);
        panel.add(new JScrollPane(taskQueueList), BorderLayout.CENTER);
        panel.add(bottomRow, BorderLayout.SOUTH);

        return panel;
    }

    // --- Task Builder Tab ---

    private JPanel createBuilderTab() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(BG_BASE);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Left: Task meta
        JPanel left = new JPanel(new GridBagLayout());
        left.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(5, 5, 5, 5);

        taskNameInput = new JTextField(25);
        taskDescInput = new JTextField(25);
        taskStatusInput = new JTextField(25);

        styleComp(taskNameInput);
        styleComp(taskDescInput);
        styleComp(taskStatusInput);

        gbc.gridy = 0;
        left.add(createLabel("TASK NAME:"), gbc);
        gbc.gridy = 1;
        left.add(taskNameInput, gbc);

        gbc.gridy = 2;
        left.add(createLabel("DESCRIPTION:"), gbc);
        gbc.gridy = 3;
        left.add(taskDescInput, gbc);

        gbc.gridy = 4;
        left.add(createLabel("STATUS FLAIR:"), gbc);
        gbc.gridy = 5;
        left.add(taskStatusInput, gbc);

        JButton btnRefresh = createStyledBtn("REFRESH CONTROLS", new Color(50, 50, 50));
        btnRefresh.addActionListener(e -> {
            taskNameInput.setText("");
            taskDescInput.setText("");
            taskStatusInput.setText("");
            builderActionModel.clear();
        });

        gbc.gridy = 6;
        gbc.insets = new Insets(20, 5, 5, 5);
        left.add(btnRefresh, gbc);

        // Center: Active task-set builder
        JPanel center = new JPanel(new BorderLayout(5, 5));
        center.setOpaque(false);

        JLabel setLabel = new JLabel("ACTIVE TASK-SET BUILDER", SwingConstants.CENTER);
        setLabel.setForeground(ACCENT_BLOOD);
        setLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));

        JList<Action> builderActionsList = new JList<>(builderActionModel);
        styleJList(builderActionsList);

        JButton btnAddToLibrary = createStyledBtn("ADD TO LIBRARY", new Color(0, 100, 0));
        btnAddToLibrary.addActionListener(e -> {
            String name = taskNameInput.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Task name cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            List<Action> actions = new ArrayList<>();
            for (int i = 0; i < builderActionModel.size(); i++) {
                actions.add(builderActionModel.get(i));
            }
            Task newTask = new Task(name, taskDescInput.getText(), actions, taskStatusInput.getText());
            libraryModel.addElement(newTask);
        });

        center.add(setLabel, BorderLayout.NORTH);
        center.add(new JScrollPane(builderActionsList), BorderLayout.CENTER);
        center.add(btnAddToLibrary, BorderLayout.SOUTH);

        // Right: Action config + nearby entities
        JPanel right = new JPanel(new BorderLayout(0, 10));
        right.setOpaque(false);

        JPanel config = new JPanel(new GridLayout(0, 1, 2, 5));
        config.setOpaque(false);

        actionCombo = new JComboBox<>(ActionType.values());
        styleComp(actionCombo);
        actionCombo.addActionListener(e -> fetchDynamicTargets());

        manualTargetInput = new JTextField();
        styleComp(manualTargetInput);

        config.add(createLabel("SELECT ACTION:"));
        config.add(actionCombo);
        config.add(createLabel("TARGET NAME:"));
        config.add(manualTargetInput);

        JButton btnAddAction = createStyledBtn("ADD ACTION TO SET", ACCENT_ORANGE);
        btnAddAction.addActionListener(e -> {
            String target = manualTargetInput.getText().trim();
            if (!target.isEmpty()) {
                builderActionModel.addElement(new Action((ActionType) actionCombo.getSelectedItem(), target));
            }
        });

        config.add(btnAddAction);

        nearbyEntitiesList = new JList<>(nearbyEntitiesModel);
        styleJList(nearbyEntitiesList);
        nearbyEntitiesList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String val = nearbyEntitiesList.getSelectedValue();
                if (val == null) {
                    return;
                }
                if (e.getClickCount() == 1) {
                    manualTargetInput.setText(val);
                } else if (e.getClickCount() == 2) {
                    builderActionModel.addElement(new Action((ActionType) actionCombo.getSelectedItem(), val));
                }
            }
        });

        JButton btnForceRefresh = createStyledBtn("FORCE REFRESH", new Color(40, 40, 40));
        btnForceRefresh.addActionListener(e -> fetchDynamicTargets());

        right.add(config, BorderLayout.NORTH);
        right.add(new JScrollPane(nearbyEntitiesList), BorderLayout.CENTER);
        right.add(btnForceRefresh, BorderLayout.SOUTH);

        panel.add(left, BorderLayout.WEST);
        panel.add(center, BorderLayout.CENTER);
        panel.add(right, BorderLayout.EAST);

        return panel;
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT_MAIN);
        return label;
    }

    private void fetchDynamicTargets() {
        if (actionCombo == null) {
            return;
        }

        ActionType type = (ActionType) actionCombo.getSelectedItem();
        nearbyEntitiesModel.clear();

        Set<String> names;
        if (type == ActionType.ATTACK || type == ActionType.TALK_TO) {
            names = NPCs.all().stream()
                    .filter(Objects::nonNull)
                    .filter(n -> type == ActionType.ATTACK ? n.hasAction("Attack") : n.hasAction("Talk-to"))
                    .map(n -> n.getName())
                    .collect(Collectors.toSet());
        } else if (type == ActionType.CHOP) {
            names = GameObjects.all().stream()
                    .filter(Objects::nonNull)
                    .filter(o -> o.hasAction("Chop down"))
                    .map(o -> o.getName())
                    .collect(Collectors.toSet());
        } else if (type == ActionType.MINE) {
            names = GameObjects.all().stream()
                    .filter(Objects::nonNull)
                    .filter(o -> o.hasAction("Mine"))
                    .map(o -> o.getName())
                    .collect(Collectors.toSet());
        } else if (type == ActionType.FISH) {
            names = NPCs.all().stream()
                    .filter(Objects::nonNull)
                    .filter(n -> n.hasAction("Net") || n.hasAction("Bait") || n.hasAction("Lure") || n.hasAction("Harpoon"))
                    .map(n -> n.getName())
                    .collect(Collectors.toSet());
        } else {
            names = NPCs.all().stream()
                    .filter(Objects::nonNull)
                    .map(n -> n.getName())
                    .collect(Collectors.toSet());
        }

        names.stream().sorted().forEach(nearbyEntitiesModel::addElement);
    }

    // --- Inner Classes ---

    public static class Task {
        public String name;
        public String desc;
        public String status;
        public List<Action> actions;

        private static final String[] HUMAN_FLAIR = {"Thinking...", "Working...", "Analyzing..."};

        public Task(String name, String desc, List<Action> actions, String status) {
            this.name = name;
            this.desc = desc;
            this.actions = actions;
            this.status = (status == null || status.isEmpty())
                    ? HUMAN_FLAIR[new Random().nextInt(HUMAN_FLAIR.length)]
                    : status;
        }

        public Task(Task other) {
            this(other.name, other.desc, new ArrayList<>(other.actions), other.status);
        }

        public String getEditableString() {
            return "NAME:" + name + "\nDESC:" + desc + "\nSTATUS:" + status;
        }

        @Override
        public String toString() {
            return name + " | " + status;
        }
    }

    public static class Action {
        public ActionType type;
        public String target;

        public Action(ActionType type, String target) {
            this.type = type;
            this.target = target;
        }

        public boolean execute() {
            switch (type) {
                case CHOP:
                    return GameObjects.closest(target) != null
                            && GameObjects.closest(target).interact("Chop down");
                case ATTACK:
                    return NPCs.closest(target) != null
                            && NPCs.closest(target).interact("Attack");
                case BANK:
                    return org.dreambot.api.methods.container.impl.bank.Bank.open();
                case MINE:
                    return GameObjects.closest(target) != null
                            && GameObjects.closest(target).interact("Mine");
                case FISH:
                    return NPCs.closest(target) != null
                            && (NPCs.closest(target).interact("Net")
                            || NPCs.closest(target).interact("Bait")
                            || NPCs.closest(target).interact("Lure")
                            || NPCs.closest(target).interact("Harpoon"));
                case COOK:
                    return GameObjects.closest(target) != null
                            && (GameObjects.closest(target).interact("Cook")
                            || GameObjects.closest(target).interact("Use"));
                case DROP:
                    return Inventory.contains(target)
                            && Inventory.get(target).interact("Drop");
                case BURY:
                    return Inventory.contains(target)
                            && Inventory.get(target).interact("Bury");
                case EXAMINE:
                    if (NPCs.closest(target) != null) {
                        return NPCs.closest(target).interact("Examine");
                    }
                    if (GameObjects.closest(target) != null) {
                        return GameObjects.closest(target).interact("Examine");
                    }
                    if (Inventory.contains(target)) {
                        return Inventory.get(target).interact("Examine");
                    }
                    return false;
                case OPEN:
                    return GameObjects.closest(target) != null
                            && GameObjects.closest(target).interact("Open");
                case TALK_TO:
                    return NPCs.closest(target) != null
                            && NPCs.closest(target).interact("Talk-to");
                case USE_ON:
                    // Simple implementation: use item on nearest object with same name
                    if (Inventory.contains(target) && GameObjects.closest(target) != null) {
                        if (Inventory.get(target).interact("Use")) {
                            return GameObjects.closest(target).interact("Use");
                        }
                    }
                    return false;
                default:
                    Logger.log("Action " + type + " not implemented in Action.execute()");
                    return false;
            }
        }

        @Override
        public String toString() {
            return type.name() + " -> " + target;
        }
    }

    // --- Library Tab ---

    private JPanel createLibraryTab() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 10, 0));
        panel.setBackground(BG_BASE);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        libraryList = new JList<>(libraryModel);
        styleJList(libraryList);

        libraryEditorArea = new JTextArea();
        libraryEditorArea.setBackground(new Color(15, 15, 15));
        libraryEditorArea.setForeground(TEXT_MAIN);
        libraryEditorArea.setFont(new Font("Consolas", Font.PLAIN, 12));

        libraryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                Task t = libraryList.getSelectedValue();
                if (t != null) {
                    libraryEditorArea.setText(t.getEditableString());
                }
            }
        });

        JPanel editPanel = new JPanel(new BorderLayout(0, 10));
        editPanel.setOpaque(false);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnRow.setOpaque(false);

        JButton btnAddToQueue = createStyledBtn("Add to Queue", new Color(0, 100, 0));
        btnAddToQueue.addActionListener(e -> {
            Task selected = libraryList.getSelectedValue();
            if (selected != null) {
                queueModel.addElement(new Task(selected));
            }
        });

        JButton btnSaveChanges = createStyledBtn("Save Changes", new Color(60, 60, 120));
        btnSaveChanges.addActionListener(e -> saveLibraryEdits());

        JButton btnDeleteTask = createStyledBtn("Delete Task", new Color(100, 0, 0));
        btnDeleteTask.addActionListener(e -> {
            int idx = libraryList.getSelectedIndex();
            if (idx != -1) {
                libraryModel.remove(idx);
                libraryEditorArea.setText("");
            }
        });

        btnRow.add(btnAddToQueue);
        btnRow.add(btnSaveChanges);
        btnRow.add(btnDeleteTask);

        editPanel.add(new JScrollPane(libraryEditorArea), BorderLayout.CENTER);
        editPanel.add(btnRow, BorderLayout.SOUTH);

        panel.add(new JScrollPane(libraryList));
        panel.add(editPanel);

        return panel;
    }

    private void saveLibraryEdits() {
        int idx = libraryList.getSelectedIndex();
        if (idx == -1) {
            return;
        }

        Task t = libraryModel.get(idx);
        String text = libraryEditorArea.getText();
        if (text == null) {
            return;
        }

        String[] lines = text.split("\\r?\\n");
        String name = t.name;
        String desc = t.desc;
        String status = t.status;

        for (String line : lines) {
            if (line.startsWith("NAME:")) {
                name = line.substring("NAME:".length()).trim();
            } else if (line.startsWith("DESC:")) {
                desc = line.substring("DESC:".length()).trim();
            } else if (line.startsWith("STATUS:")) {
                status = line.substring("STATUS:".length()).trim();
            }
        }

        t.name = name;
        t.desc = desc;
        t.status = status.isEmpty() ? t.status : status;

        libraryModel.set(idx, t);
        libraryList.repaint();
    }

    private void initializeGenericLibrary() {
        libraryModel.addElement(new Task(
                "Woodcutter",
                "Chops Trees",
                Collections.singletonList(new Action(ActionType.CHOP, "Tree")),
                "Chopping..."
        ));
    }

    private void loadIntoBuilder(Task t) {
        if (t == null) {
            return;
        }
        taskNameInput.setText(t.name);
        taskDescInput.setText(t.desc);
        taskStatusInput.setText(t.status);
        builderActionModel.clear();
        t.actions.forEach(builderActionModel::addElement);
    }

    // --- Header ---

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PANEL_SURFACE);
        header.setPreferredSize(new Dimension(0, 85));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_DIM));

        JLabel titleLabel = new JLabel(" ETAbot", SwingConstants.LEFT);
        titleLabel.setForeground(ACCENT_BLOOD);
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
        controls.add(btnStop);
        controls.add(btnInputToggle);

        JPanel headerStats = new JPanel(new GridLayout(2, 1));
        headerStats.setOpaque(false);
        headerStats.setBorder(new EmptyBorder(10, 20, 10, 10));

        styleHeaderLabel(totalXpGainedLabel);
        styleHeaderLabel(totalLevelsGainedLabel);

        headerStats.add(totalXpGainedLabel);
        headerStats.add(totalLevelsGainedLabel);

        rightContainer.add(controls, BorderLayout.CENTER);
        rightContainer.add(headerStats, BorderLayout.EAST);

        header.add(titleLabel, BorderLayout.WEST);
        header.add(rightContainer, BorderLayout.EAST);

        return header;
    }

    private void toggleScriptState() {
        if (script == null) {
            return;
        }
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

    private void stopScript() {
        int result = JOptionPane.showConfirmDialog(this, "Stop?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
            script.stop();
            dispose();
        }
    }

    private void toggleUserInput() {
        isUserInputAllowed = !isUserInputAllowed;
        Client.getInstance().setMouseInputEnabled(isUserInputAllowed);
        Client.getInstance().setKeyboardInputEnabled(isUserInputAllowed);
        btnInputToggle.setText(isUserInputAllowed ? "🖱" : "🚫");
    }

    private void styleHeaderLabel(JLabel label) {
        label.setForeground(TEXT_MAIN);
        label.setFont(new Font("Consolas", Font.BOLD, 15));
        label.setHorizontalAlignment(SwingConstants.RIGHT);
    }

    private void styleSpinner(JSpinner spinner) {
        JFormattedTextField field = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
        field.setBackground(new Color(30, 30, 30));
        field.setForeground(ACCENT_BLOOD);
        spinner.setBorder(new LineBorder(BORDER_DIM));
    }

    private JButton createIconButton(String symbol, String tooltip, ActionListener action) {
        JButton btn = new JButton(symbol);
        btn.setPreferredSize(new Dimension(40, 40));
        btn.setFont(new Font("Segoe UI Symbol", Font.BOLD, 18));
        btn.setBackground(new Color(30, 0, 0));
        btn.setForeground(ACCENT_BLOOD);
        btn.setToolTipText(tooltip);
        btn.addActionListener(action);
        return btn;
    }

    // --- Queue Helpers ---

    private void shiftQueue(int dir) {
        int idx = taskQueueList.getSelectedIndex();
        if (idx == -1) {
            return;
        }
        int newIdx = idx + dir;
        if (newIdx < 0 || newIdx >= queueModel.size()) {
            return;
        }
        Task t = queueModel.remove(idx);
        queueModel.add(newIdx, t);
        taskQueueList.setSelectedIndex(newIdx);
    }

    // --- Styling Helpers ---

    private void styleComp(JComponent c) {
        c.setBackground(PANEL_SURFACE);
        c.setForeground(TEXT_MAIN);
        if (c instanceof JTextField) {
            ((JTextField) c).setCaretColor(ACCENT_BLOOD);
        }
    }

    private void styleJList(JList<?> list) {
        list.setBackground(PANEL_SURFACE);
        list.setForeground(TEXT_MAIN);
        list.setSelectionBackground(TAB_SELECTED);
    }

    private JButton createStyledBtn(String text, Color color) {
        JButton btn = new JButton(text);
        btn.setBackground(color);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(new LineBorder(BORDER_DIM));
        return btn;
    }

    private ImageIcon loadMiscIcon(String name) {
        try {
            return new ImageIcon(new ImageIcon(Objects.requireNonNull(
                    getClass().getResource("/resources/icons/misc/" + name + ".png")))
                    .getImage()
                    .getScaledInstance(18, 18, Image.SCALE_SMOOTH));
        } catch (Exception e) {
            return null;
        }
    }

    private ImageIcon loadSkillIcon(Skill skill) {
        try {
            return new ImageIcon(new ImageIcon(Objects.requireNonNull(
                    getClass().getResource("/resources/icons/skills/" + skill.name().toLowerCase() + "_icon.png")))
                    .getImage()
                    .getScaledInstance(26, 26, Image.SCALE_SMOOTH));
        } catch (Exception e) {
            return new ImageIcon();
        }
    }

    private ImageIcon loadStatusIcon(String name) {
        try {
            return new ImageIcon(new ImageIcon(Objects.requireNonNull(
                    getClass().getResource("/resources/icons/status/" + name.toLowerCase() + ".png")))
                    .getImage()
                    .getScaledInstance(16, 16, Image.SCALE_SMOOTH));
        } catch (Exception ignored) {
        }
        return null;
    }

    // --- Skill Tracker ---

    private static class SkillData {
        final Skill skill;
        final int startLevel;
        final int startXP;
        int currentLevel;
        int currentXP;
        int goalLevel = -1;
        int goalXP = -1;

        SkillData(Skill skill) {
            this.skill = skill;
            this.startLevel = Skills.getRealLevel(skill);
            this.startXP = Skills.getExperience(skill);
            this.currentLevel = startLevel;
            this.currentXP = startXP;
        }

        int getXpGained() {
            return currentXP - startXP;
        }

        int getLevelsGained() {
            return currentLevel - startLevel;
        }

        double getXpPerHour(long runtimeMillis) {
            if (runtimeMillis <= 0) {
                return 0.0;
            }
            double hours = runtimeMillis / 3_600_000.0;
            return getXpGained() / hours;
        }

        boolean hasGoal() {
            return goalLevel > 0 || goalXP > 0;
        }

        int getRemainingToGoal() {
            if (goalXP > 0) {
                return Math.max(0, goalXP - currentXP);
            }
            if (goalLevel > 0 && goalLevel > currentLevel) {
                int targetXP = Skills.getExperienceForLevel(goalLevel);
                return Math.max(0, targetXP - currentXP);
            }
            return 0;
        }

        long getMillisToGoal(long runtimeMillis) {
            double xpPerHour = getXpPerHour(runtimeMillis);
            if (xpPerHour <= 0) {
                return -1;
            }
            int remaining = getRemainingToGoal();
            if (remaining <= 0) {
                return 0;
            }
            double hours = remaining / xpPerHour;
            return (long) (hours * 3_600_000L);
        }
    }

    private void initSkillRegistry() {
        for (Skill skill : Skill.values()) {
            skillRegistry.put(skill, new SkillData(skill));
        }
    }

    private JPanel createSkillsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_BASE);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel grid = new JPanel(new GridLayout(0, 4, 10, 10));
        grid.setBackground(BG_BASE);

        for (Skill skill : OSRS_ORDER) {
            SkillData data = skillRegistry.get(skill);
            JPanel card = createSkillCard(data);
            grid.add(card);
        }

        panel.add(new JScrollPane(grid), BorderLayout.CENTER);

        return panel;
    }

    private JPanel createSkillCard(SkillData data) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(PANEL_SURFACE);
        card.setBorder(new LineBorder(BORDER_DIM));

        JLabel iconLabel = new JLabel(loadSkillIcon(data.skill));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel nameLabel = new JLabel(data.skill.name(), SwingConstants.CENTER);
        nameLabel.setForeground(TEXT_MAIN);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JLabel levelLabel = new JLabel("Lvl: " + data.currentLevel, SwingConstants.CENTER);
        levelLabel.setForeground(TEXT_DIM);
        levelLabel.setFont(new Font("Consolas", Font.PLAIN, 11));

        JLabel xpLabel = new JLabel("XP: " + data.currentXP, SwingConstants.CENTER);
        xpLabel.setForeground(TEXT_DIM);
        xpLabel.setFont(new Font("Consolas", Font.PLAIN, 11));

        JPanel center = new JPanel(new GridLayout(0, 1));
        center.setOpaque(false);
        center.add(nameLabel);
        center.add(levelLabel);
        center.add(xpLabel);

        card.add(iconLabel, BorderLayout.WEST);
        card.add(center, BorderLayout.CENTER);

        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showSkillGoalDialog(data);
                }
            }
        });

        card.putClientProperty("levelLabel", levelLabel);
        card.putClientProperty("xpLabel", xpLabel);

        return card;
    }

    private void showSkillGoalDialog(SkillData data) {
        String[] options = {"Set Goal Level", "Set Goal XP", "Clear Goal", "Cancel"};
        int choice = JOptionPane.showOptionDialog(
                this,
                "Set a goal for " + data.skill.name(),
                "Skill Goal",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]
        );

        if (choice == 0) {
            String input = JOptionPane.showInputDialog(this, "Goal Level:", data.currentLevel + 1);
            if (input != null && !input.trim().isEmpty()) {
                try {
                    int level = Integer.parseInt(input.trim());
                    if (level > 0) {
                        data.goalLevel = level;
                        data.goalXP = -1;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        } else if (choice == 1) {
            String input = JOptionPane.showInputDialog(this, "Goal XP:", data.currentXP + 1000);
            if (input != null && !input.trim().isEmpty()) {
                try {
                    int xp = Integer.parseInt(input.trim());
                    if (xp > 0) {
                        data.goalXP = xp;
                        data.goalLevel = -1;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        } else if (choice == 2) {
            data.goalLevel = -1;
            data.goalXP = -1;
        }

        rebuildTrackerList();
    }

    private void updateAll() {
        long runtime = System.currentTimeMillis() - startTime;

        int totalXpGained = 0;
        int totalLevelsGained = 0;
        int f2pTotal = 0;
        int p2pTotal = 0;

        for (Skill skill : OSRS_ORDER) {
            SkillData data = skillRegistry.get(skill);
            if (data == null) {
                continue;
            }

            data.currentLevel = Skills.getRealLevel(skill);
            data.currentXP = Skills.getExperience(skill);

            totalXpGained += data.getXpGained();
            totalLevelsGained += data.getLevelsGained();

            if (F2P_SKILLS.contains(skill)) {
                f2pTotal += data.currentLevel;
            } else {
                p2pTotal += data.currentLevel;
            }
        }

        totalXpGainedLabel.setText("Total XP Gained: " + totalXpGained);
        totalLevelsGainedLabel.setText("Total Levels Gained: " + totalLevelsGained);
        totalLevelLabel.setText("F2P Total: " + f2pTotal + " | P2P Total: " + p2pTotal);

        rebuildTrackerList();
        updatePlayerInfo();
    }

    private void rebuildTrackerList() {
        trackerList.removeAll();

        long runtime = System.currentTimeMillis() - startTime;
        double projectionHours = ((Number) projectionSpinner.getValue()).doubleValue();
        long projectionMillis = (long) (projectionHours * 3_600_000L);

        DecimalFormat df = new DecimalFormat("#,###");
        DecimalFormat oneDecimal = new DecimalFormat("#,##0.0");

        for (Skill skill : OSRS_ORDER) {
            SkillData data = skillRegistry.get(skill);
            if (data == null) {
                continue;
            }

            boolean hasProgress = data.getXpGained() > 0 || data.hasGoal();
            if (!hasProgress) {
                continue;
            }

            JPanel row = new JPanel(new BorderLayout());
            row.setBackground(PANEL_SURFACE);
            row.setBorder(new EmptyBorder(4, 4, 4, 4));

            JLabel nameLabel = new JLabel(skill.name());
            nameLabel.setForeground(TEXT_MAIN);
            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));

            double xpPerHour = data.getXpPerHour(runtime);
            long etaMillis = data.getMillisToGoal(runtime);

            StringBuilder info = new StringBuilder();
            info.append("Lvl ").append(data.currentLevel)
                    .append(" (").append(df.format(data.getXpGained())).append(" xp, ")
                    .append(oneDecimal.format(xpPerHour)).append(" xp/h)");

            if (data.hasGoal()) {
                int remaining = data.getRemainingToGoal();
                info.append(" | Goal: ");
                if (data.goalLevel > 0) {
                    info.append("Lvl ").append(data.goalLevel);
                } else if (data.goalXP > 0) {
                    info.append(df.format(data.goalXP)).append(" xp");
                }
                info.append(" (").append(df.format(remaining)).append(" xp left)");

                if (etaMillis > 0) {
                    long etaMinutes = etaMillis / 60000;
                    long etaHours = etaMinutes / 60;
                    long etaMins = etaMinutes % 60;
                    info.append(" ~ ETA: ").append(etaHours).append("h ").append(etaMins).append("m");
                } else if (etaMillis == 0) {
                    info.append(" (Reached)");
                }
            } else {
                // projection only
                int projectedXp = data.currentXP + (int) (xpPerHour * projectionHours);
                info.append(" | Projected XP in ").append(oneDecimal.format(projectionHours))
                        .append("h: ").append(df.format(projectedXp));
            }

            JLabel infoLabel = new JLabel(info.toString());
            infoLabel.setForeground(TEXT_DIM);
            infoLabel.setFont(new Font("Consolas", Font.PLAIN, 11));

            row.add(nameLabel, BorderLayout.NORTH);
            row.add(infoLabel, BorderLayout.CENTER);

            trackerList.add(row);
        }

        trackerList.revalidate();
        trackerList.repaint();
    }

    // --- Player Panel ---

    private JPanel createPlayerPanel() {
        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(BG_BASE);
        container.setBorder(new EmptyBorder(20, 20, 20, 20));

        JPanel content = new JPanel(new GridLayout(1, 2, 20, 0));
        content.setBackground(BG_BASE);

        JPanel login = createInfoCard("Login Details");
        addInfoRow(login, "Username", lblUsername);
        addInfoRow(login, "Password", lblPassword);
        addInfoRow(login, "Identifier", lblAcctId);
        addInfoRow(login, "Acct Status", lblAcctStatus);

        JPanel game = createInfoCard("World");
        addInfoRow(game, "Character Name", lblCharName);
        addInfoRowWithIcon(game, "Membership", lblMemberText, lblMemberIcon);
        addInfoRow(game, "World", lblWorld);
        addInfoRow(game, "Coordinates", lblCoords);
        addInfoRow(game, "GameState", lblGameState);

        content.add(login);
        content.add(game);

        container.add(content, BorderLayout.NORTH);

        return container;
    }

    private JPanel createInfoCard(String title) {
        JPanel panel = new JPanel(new GridLayout(0, 1, 5, 10));
        panel.setBackground(PANEL_SURFACE);

        TitledBorder border = BorderFactory.createTitledBorder(new LineBorder(BORDER_DIM), " " + title + " ");
        border.setTitleColor(ACCENT_BLOOD);
        border.setTitleFont(new Font("Segoe UI", Font.BOLD, 16));

        panel.setBorder(BorderFactory.createCompoundBorder(border, new EmptyBorder(15, 15, 15, 15)));

        return panel;
    }

    private void addInfoRow(JPanel panel, String key, JLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);

        JLabel keyLabel = new JLabel(key);
        keyLabel.setForeground(TEXT_DIM);

        valueLabel.setForeground(TEXT_MAIN);
        valueLabel.setFont(new Font("Consolas", Font.BOLD, 14));

        row.add(keyLabel, BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.EAST);
        row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 40, 40)));

        panel.add(row);
    }

    private void addInfoRowWithIcon(JPanel panel, String key, JLabel valueLabel, JLabel iconLabel) {
        JPanel row = new JPanel(new BorderLayout(5, 0));
        row.setOpaque(false);

        JLabel keyLabel = new JLabel(key);
        keyLabel.setForeground(TEXT_DIM);

        JPanel rightSide = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        rightSide.setOpaque(false);

        valueLabel.setForeground(TEXT_MAIN);

        rightSide.add(valueLabel);
        rightSide.add(iconLabel);

        row.add(keyLabel, BorderLayout.WEST);
        row.add(rightSide, BorderLayout.EAST);
        row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 40, 40)));

        panel.add(row);
    }

    private void updatePlayerInfo() {
        if (Players.getLocal() != null) {
            lblCharName.setText(Players.getLocal().getName());
            lblCoords.setText(Players.getLocal().getTile().toString());
        }

        if (Worlds.getCurrentWorld() > 0) {
            lblWorld.setText(String.valueOf(Worlds.getCurrentWorld()));
        }

        lblGameState.setText(String.valueOf(Client.getGameState()));

        boolean isMember = Client.isMembers();
        lblMemberText.setText(isMember ? "Member" : "Free");
        lblMemberIcon.setIcon(loadStatusIcon(lblMemberText.getText()));
    }

    // --- Settings ---

    private JPanel createSettingsInterface() {
        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(BG_BASE);

        CardLayout cardLayout = new CardLayout();
        JPanel contentPanel = new JPanel(cardLayout);
        contentPanel.setBackground(BG_BASE);
        contentPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        contentPanel.add(createClientPanel(), "Client");
        contentPanel.add(createActivitiesPanel(), "Activities");
        contentPanel.add(createAudioPanel(), "Audio");
        contentPanel.add(createChatPanel(), "Chat");
        contentPanel.add(createDisplayPanel(), "Display");
        contentPanel.add(createControlsPanel(), "Controls");
        contentPanel.add(createWarningsPanel(), "Warnings");

        JPanel menuPanel = new JPanel(new GridLayout(10, 1, 0, 2));
        menuPanel.setPreferredSize(new Dimension(180, 0));
        menuPanel.setBackground(PANEL_SURFACE);
        menuPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_DIM));

        String[] categories = {"Client", "Display", "Audio", "Chat", "Controls", "Activities", "Warnings"};
        ButtonGroup btnGroup = new ButtonGroup();

        for (String cat : categories) {
            JToggleButton btn = createMenuButton(cat);
            btn.addActionListener(e -> cardLayout.show(contentPanel, cat));
            btnGroup.add(btn);
            menuPanel.add(btn);
            if (cat.equals("Client")) {
                btn.setSelected(true);
            }
        }

        container.add(menuPanel, BorderLayout.WEST);
        container.add(contentPanel, BorderLayout.CENTER);

        return container;
    }

    private JToggleButton createMenuButton(String text) {
        JToggleButton btn = new JToggleButton(text);
        btn.setBackground(PANEL_SURFACE);
        btn.setForeground(TEXT_MAIN);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.addChangeListener(e -> {
            if (btn.isSelected()) {
                btn.setBackground(TAB_SELECTED);
            } else {
                btn.setBackground(PANEL_SURFACE);
            }
        });
        return btn;
    }

    private JPanel createDisplayPanel() {
        return createSettingsGroup("Display",
                createSettingCheck("Roofs", !ClientSettings.areRoofsHidden(),
                        e -> ClientSettings.toggleRoofs(((JCheckBox) e.getSource()).isSelected())),
                createSettingCheck("Data orbs", ClientSettings.areDataOrbsEnabled(),
                        e -> ClientSettings.toggleDataOrbs(((JCheckBox) e.getSource()).isSelected())),
                createSettingCheck("Transparent side panel", ClientSettings.isTransparentSidePanelEnabled(),
                        e -> ClientSettings.toggleTransparentSidePanel(((JCheckBox) e.getSource()).isSelected()))
        );
    }

    private JPanel createAudioPanel() {
        return createSettingsGroup("Audio",
                createSettingCheck("Game Audio", ClientSettings.isGameAudioOn(),
                        e -> ClientSettings.toggleGameAudio(((JCheckBox) e.getSource()).isSelected()))
        );
    }

    private JPanel createChatPanel() {
        return createSettingsGroup("Chat",
                createSettingCheck("Transparent chatbox", ClientSettings.isTransparentChatboxEnabled(),
                        e -> ClientSettings.toggleTransparentChatbox(((JCheckBox) e.getSource()).isSelected())),
                createSettingCheck("Click through chatbox", ClientSettings.isClickThroughChatboxEnabled(),
                        e -> ClientSettings.toggleClickThroughChatbox(((JCheckBox) e.getSource()).isSelected()))
        );
    }

    private JPanel createControlsPanel() {
        return createSettingsGroup("Controls",
                createSettingCheck("Shift click drop", ClientSettings.isShiftClickDroppingEnabled(),
                        e -> ClientSettings.toggleShiftClickDropping(((JCheckBox) e.getSource()).isSelected())),
                createSettingCheck("Esc closes interface", ClientSettings.isEscInterfaceClosingEnabled(),
                        e -> ClientSettings.toggleEscInterfaceClosing(((JCheckBox) e.getSource()).isSelected()))
        );
    }

    private JPanel createWarningsPanel() {
        return createSettingsGroup("Warnings",
                createSettingCheck("Loot notifications", ClientSettings.areLootNotificationsEnabled(),
                        e -> ClientSettings.toggleLootNotifications(((JCheckBox) e.getSource()).isSelected()))
        );
    }

    private JPanel createClientPanel() {
        return createSettingsGroup("Client",
                createSettingCheck("Disable Rendering", Client.isRenderingDisabled(),
                        e -> Client.setRenderingDisabled(((JCheckBox) e.getSource()).isSelected()))
        );
    }

    private JPanel createActivitiesPanel() {
        return createSettingsGroup("Activities",
                createSettingCheck("Level-up interface", ClientSettings.isLevelUpInterfaceEnabled(),
                        e -> ClientSettings.toggleLevelUpInterface(((JCheckBox) e.getSource()).isSelected()))
        );
    }

    private JPanel createSettingsGroup(String title, Component... components) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_BASE);

        JLabel header = new JLabel(title);
        header.setForeground(ACCENT_BLOOD);
        header.setFont(new Font("Segoe UI", Font.BOLD, 24));

        JPanel list = new JPanel(new GridLayout(0, 1, 5, 5));
        list.setBackground(BG_BASE);

        for (Component c : components) {
            list.add(c);
        }

        panel.add(header, BorderLayout.NORTH);
        panel.add(list, BorderLayout.CENTER);

        return panel;
    }

    private JCheckBox createSettingCheck(String label, boolean initial, ItemListener listener) {
        JCheckBox box = new JCheckBox(label, initial);
        box.setBackground(BG_BASE);
        box.setForeground(TEXT_MAIN);
        box.addItemListener(listener);
        return box;
    }

    // --- Task Renderer ---

    private static class TaskCellRenderer extends JLabel implements ListCellRenderer<Task> {
        TaskCellRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Task> list, Task value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            setText(value.toString());
            setBackground(isSelected ? new Color(60, 0, 0) : new Color(24, 24, 24));
            setForeground(new Color(210, 210, 210));
            setBorder(new EmptyBorder(3, 5, 3, 5));
            return this;
        }
    }
}
