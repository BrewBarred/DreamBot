package examples;

import org.dreambot.api.Client;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.methods.skills.Skills;
import org.dreambot.api.script.AbstractScript;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <h1>ETAbot Nexus Client</h1>
 * @author ETAbot Dev
 * @version 16.3.0-Elite
 */
public class DreamBotMenu_ClosestYet26 extends JFrame {

    private final AbstractScript script;

    // --- State ---
    private boolean isScriptPaused = true;
    private boolean isUserInputAllowed = true;
    private boolean isCaptureEnabled = true;
    private int currentExecutionIndex = 0;

    // --- Controls ---
    private JButton btnPlayPause, btnInputToggle, btnCaptureToggle;

    // --- Data Models ---
    private final Map<Skill, SkillData> skillRegistry = new EnumMap<>(Skill.class);
    private final DefaultListModel<Task> queueModel = new DefaultListModel<>();
    private final DefaultListModel<Task> libraryModel = new DefaultListModel<>();
    private final DefaultListModel<Action> builderActionModel = new DefaultListModel<>();
    private final DefaultListModel<String> nearbyEntitiesModel = new DefaultListModel<>();
    private final List<List<Task>> presets = new ArrayList<>(Arrays.asList(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));

    // --- UI ---
    private final JPanel trackerList, sidePanel;
    private final JLabel totalXpGainedLabel = new JLabel("Total XP Gained: 0");
    private final JLabel totalLevelsGainedLabel = new JLabel("Total Levels Gained: 0");
    private final JLabel playerStatsLabel = new JLabel("Total XP: 0 | Total Lvl: 0");
    private final JLabel lblStatus = new JLabel("Status: Idle");
    private final JSpinner projectionSpinner;
    private final long startTime;

    private JList<Task> taskQueueList, libraryList;
    private JList<String> nearbyEntitiesList;
    private JTextArea libraryEditorArea, consoleArea;
    private JTextField taskNameInput, taskDescInput, taskStatusInput, manualTargetInput, consoleSearch;
    private JComboBox<ActionType> actionCombo;

    // --- Labels ---
    private final JLabel lblUsername = new JLabel("..."), lblPassword = new JLabel("..."), lblAcctId = new JLabel("...");
    private final JLabel lblAcctStatus = new JLabel("..."), lblCharName = new JLabel("..."), lblWorld = new JLabel("-");
    private final JLabel lblCoords = new JLabel("-"), lblGameState = new JLabel("-"), lblMemberIcon = new JLabel(), lblMemberText = new JLabel("-");

    // --- Theme ---
    private final Color BG_BASE = new Color(12, 12, 12);
    private final Color PANEL_SURFACE = new Color(24, 24, 24);
    private final Color ACCENT_BLOOD = new Color(150, 0, 0);
    private final Color ACCENT_ORANGE = new Color(220, 80, 0);
    private final Color BORDER_DIM = new Color(45, 45, 45);
    private final Color TEXT_MAIN = new Color(210, 210, 210);
    private final Color TEXT_DIM = new Color(140, 140, 140);
    private final Color TAB_SELECTED = new Color(60, 0, 0);

    private static final Skill[] OSRS_ORDER = {
            Skill.ATTACK, Skill.HITPOINTS, Skill.MINING, Skill.STRENGTH, Skill.AGILITY, Skill.SMITHING,
            Skill.DEFENCE, Skill.HERBLORE, Skill.FISHING, Skill.RANGED, Skill.THIEVING, Skill.COOKING,
            Skill.PRAYER, Skill.CRAFTING, Skill.FIREMAKING, Skill.MAGIC, Skill.FLETCHING, Skill.WOODCUTTING,
            Skill.RUNECRAFTING, Skill.SLAYER, Skill.FARMING, Skill.CONSTRUCTION, Skill.HUNTER
    };

    public enum ActionType { ATTACK, BANK, BURY, CHOP, COOK, DROP, FISH, MINE, TALK_TO, USE_ON }

    public DreamBotMenu_ClosestYet26(AbstractScript script) {
        this.script = script;
        this.startTime = System.currentTimeMillis();

        setTitle("ETAbot | Nexus Client");
        setSize(1400, 950);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(BG_BASE);

        JPanel header = createHeaderPanel();

        JTabbedPane mainTabs = new JTabbedPane();
        mainTabs.addTab("Task List", createTaskListTab());
        mainTabs.addTab("Task Library", createLibraryTab());
        mainTabs.addTab("Task Builder", createBuilderTab());
        mainTabs.addTab("Output", createOutputTab());
        mainTabs.addTab("Skill Tracker", createSkillsPanel());
        mainTabs.addTab("Player", createPlayerPanel());
        mainTabs.addTab("Settings", createSettingsInterface());

        // --- Elite Live Tracker (Side) ---
        sidePanel = new JPanel(new BorderLayout());
        sidePanel.setPreferredSize(new Dimension(360, 0));
        sidePanel.setBackground(PANEL_SURFACE);
        sidePanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER_DIM));

        JPanel trackerHeader = new JPanel(new GridLayout(3, 1, 0, 2));
        trackerHeader.setBackground(BG_BASE);
        trackerHeader.setBorder(new EmptyBorder(10, 15, 10, 15));

        styleStatLabel(totalXpGainedLabel);
        styleStatLabel(totalLevelsGainedLabel);
        styleStatLabel(playerStatsLabel);

        trackerHeader.add(totalXpGainedLabel);
        trackerHeader.add(totalLevelsGainedLabel);
        trackerHeader.add(playerStatsLabel);
        sidePanel.add(trackerHeader, BorderLayout.NORTH);

        projectionSpinner = new JSpinner(new SpinnerNumberModel(24, 1, 999, 1));
        styleSpinner(projectionSpinner);
        trackerList = new JPanel(); trackerList.setLayout(new BoxLayout(trackerList, BoxLayout.Y_AXIS)); trackerList.setBackground(PANEL_SURFACE);
        JScrollPane sScroll = new JScrollPane(trackerList); sScroll.setBorder(null); sScroll.getViewport().setBackground(PANEL_SURFACE);
        sidePanel.add(sScroll, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);
        add(mainTabs, BorderLayout.CENTER);
        add(sidePanel, BorderLayout.EAST);

        initializeGenericLibrary();
        setVisible(true);

        new javax.swing.Timer(1000, e -> updateUI()).start();
        new javax.swing.Timer(5000, e -> { if(mainTabs.getSelectedIndex() == 2) fetchDynamicTargets(); }).start();
    }

    // --- Bridge Logic ---
    public boolean isScriptPaused() { return isScriptPaused; }
    public DefaultListModel<Task> getQueueModel() { return queueModel; }
    public int getCurrentExecutionIndex() { return currentExecutionIndex; }
    public void setCurrentExecutionIndex(int i) { this.currentExecutionIndex = i; if(taskQueueList != null) taskQueueList.repaint(); }
    public void setLabelStatus(String text) { SwingUtilities.invokeLater(() -> lblStatus.setText(text)); }
    public void incrementExecutionIndex() {
        if (currentExecutionIndex < queueModel.size() - 1) currentExecutionIndex++;
        else { currentExecutionIndex = 0; isScriptPaused = true; btnPlayPause.setText("▶"); }
        taskQueueList.repaint();
    }

    // --- TABS ---
    private JPanel createTaskListTab() {
        JPanel p = new JPanel(new BorderLayout(10, 10)); p.setBackground(BG_BASE); p.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel nav = new JPanel(new GridLayout(0, 1, 0, 5)); nav.setOpaque(false);
        JButton btnUp = createStyledBtn("▲", new Color(40,40,40)); btnUp.addActionListener(e -> shiftQueue(-1));
        JButton btnDown = createStyledBtn("▼", new Color(40,40,40)); btnDown.addActionListener(e -> shiftQueue(1));
        JButton btnLoad = createStyledBtn("LOAD", ACCENT_BLOOD); btnLoad.addActionListener(e -> loadIntoBuilder(taskQueueList.getSelectedValue()));
        nav.add(btnUp); nav.add(btnDown); nav.add(btnLoad);

        taskQueueList = new JList<>(queueModel);
        taskQueueList.setCellRenderer(new TaskCellRenderer());
        styleJList(taskQueueList);

        JPanel bottomControls = new JPanel(new FlowLayout(FlowLayout.RIGHT)); bottomControls.setOpaque(false);
        JButton btnSaveP = createStyledBtn("SAVE PRESET", ACCENT_BLOOD);
        btnSaveP.addActionListener(e -> {
            String slot = JOptionPane.showInputDialog("Save to slot (1-4)?");
            try { int s = Integer.parseInt(slot)-1; presets.set(s, Collections.list(queueModel.elements())); } catch(Exception ignored){}
        });
        JButton btnDel = createStyledBtn("DELETE TASK", new Color(100,0,0));
        btnDel.addActionListener(e -> { if(taskQueueList.getSelectedIndex() != -1) queueModel.remove(taskQueueList.getSelectedIndex()); });

        bottomControls.add(btnSaveP); bottomControls.add(btnDel);

        p.add(nav, BorderLayout.WEST);
        p.add(new JScrollPane(taskQueueList), BorderLayout.CENTER);
        p.add(bottomControls, BorderLayout.SOUTH);
        return p;
    }

    private JPanel createBuilderTab() {
        JPanel p = new JPanel(new BorderLayout(15, 15)); p.setBackground(BG_BASE); p.setBorder(new EmptyBorder(15, 15, 15, 15));

        // --- Left: Metadata (Widened) ---
        JPanel left = new JPanel(new GridBagLayout()); left.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints(); gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0; gbc.insets = new Insets(5, 5, 5, 5);

        taskNameInput = new JTextField(25); taskDescInput = new JTextField(25); taskStatusInput = new JTextField(25);
        styleComp(taskNameInput); styleComp(taskDescInput); styleComp(taskStatusInput);

        gbc.gridy = 0; left.add(new JLabel("TASK NAME:"), gbc); gbc.gridy = 1; left.add(taskNameInput, gbc);
        gbc.gridy = 2; left.add(new JLabel("DESCRIPTION:"), gbc); gbc.gridy = 3; left.add(taskDescInput, gbc);
        gbc.gridy = 4; left.add(new JLabel("STATUS FLAIR:"), gbc); gbc.gridy = 5; left.add(taskStatusInput, gbc);

        JButton btnRefresh = createStyledBtn("REFRESH CONTROLS", new Color(50, 50, 50));
        btnRefresh.addActionListener(e -> { taskNameInput.setText(""); taskDescInput.setText(""); taskStatusInput.setText(""); manualTargetInput.setText(""); builderActionModel.clear(); });
        gbc.gridy = 6; gbc.insets = new Insets(20, 5, 5, 5); left.add(btnRefresh, gbc);

        // --- Center: Active Task-Set ---
        JPanel center = new JPanel(new BorderLayout(5, 5)); center.setOpaque(false);
        JLabel setLabel = new JLabel("ACTIVE TASK-SET BUILDER", SwingConstants.CENTER);
        setLabel.setForeground(ACCENT_BLOOD); setLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        JList<Action> bActions = new JList<>(builderActionModel); styleJList(bActions);

        JButton btnAddLib = createStyledBtn("COMMIT TO LIBRARY", new Color(0, 100, 0));
        btnAddLib.addActionListener(e -> {
            List<Action> acts = new ArrayList<>(); for(int i=0; i<builderActionModel.size(); i++) acts.add(builderActionModel.get(i));
            libraryModel.addElement(new Task(taskNameInput.getText(), taskDescInput.getText(), acts, taskStatusInput.getText()));
        });

        center.add(setLabel, BorderLayout.NORTH); center.add(new JScrollPane(bActions), BorderLayout.CENTER); center.add(btnAddLib, BorderLayout.SOUTH);

        // --- Right: Action & Dynamic Targets ---
        JPanel right = new JPanel(new BorderLayout(0, 10)); right.setOpaque(false);
        JPanel config = new JPanel(new GridLayout(0, 1, 2, 5)); config.setOpaque(false);

        actionCombo = new JComboBox<>(ActionType.values()); styleComp(actionCombo);
        actionCombo.addActionListener(e -> fetchDynamicTargets());
        manualTargetInput = new JTextField(); styleComp(manualTargetInput);

        config.add(new JLabel("SELECT ACTION:")); config.add(actionCombo);
        config.add(new JLabel("TARGET NAME:")); config.add(manualTargetInput);

        JButton btnAddAct = createStyledBtn("ADD ACTION TO SET", ACCENT_ORANGE);
        btnAddAct.addActionListener(e -> { if(!manualTargetInput.getText().isEmpty()) builderActionModel.addElement(new Action((ActionType)actionCombo.getSelectedItem(), manualTargetInput.getText())); });
        config.add(btnAddAct);

        nearbyEntitiesList = new JList<>(nearbyEntitiesModel); styleJList(nearbyEntitiesList);
        nearbyEntitiesList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                String val = nearbyEntitiesList.getSelectedValue();
                if(val == null) return;
                if(e.getClickCount() == 1) manualTargetInput.setText(val);
                else if(e.getClickCount() == 2) builderActionModel.addElement(new Action((ActionType)actionCombo.getSelectedItem(), val));
            }
        });

        JButton btnForceRefresh = createStyledBtn("FORCE REFRESH", new Color(40,40,40));
        btnForceRefresh.addActionListener(e -> fetchDynamicTargets());

        right.add(config, BorderLayout.NORTH);
        right.add(new JScrollPane(nearbyEntitiesList), BorderLayout.CENTER);
        right.add(btnForceRefresh, BorderLayout.SOUTH);

        p.add(left, BorderLayout.WEST); p.add(center, BorderLayout.CENTER); p.add(right, BorderLayout.EAST);
        return p;
    }

    private JPanel createLibraryTab() {
        JPanel p = new JPanel(new GridLayout(1, 2, 10, 0)); p.setBackground(BG_BASE); p.setBorder(new EmptyBorder(15, 15, 15, 15));
        libraryList = new JList<>(libraryModel); styleJList(libraryList);

        JPanel editPanel = new JPanel(new BorderLayout(0, 10)); editPanel.setOpaque(false);
        libraryEditorArea = new JTextArea(); libraryEditorArea.setBackground(new Color(15,15,15)); libraryEditorArea.setForeground(TEXT_MAIN);
        libraryList.addListSelectionListener(e -> { Task t = libraryList.getSelectedValue(); if(t != null) libraryEditorArea.setText(t.getEditableString()); });

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT)); btnRow.setOpaque(false);
        JButton btnQ = createStyledBtn("QUEUE", new Color(0, 80, 0)); btnQ.addActionListener(e -> { if(libraryList.getSelectedValue() != null) queueModel.addElement(new Task(libraryList.getSelectedValue())); });
        JButton btnDel = createStyledBtn("DELETE", new Color(80, 0, 0)); btnDel.addActionListener(e -> libraryModel.removeElement(libraryList.getSelectedValue()));
        JButton btnEdit = createStyledBtn("UPDATE", ACCENT_BLOOD); btnEdit.addActionListener(e -> { Task t = libraryList.getSelectedValue(); if(t != null) { t.parseFromEditor(libraryEditorArea.getText()); libraryList.repaint(); } });

        btnRow.add(btnDel); btnRow.add(btnEdit); btnRow.add(btnQ);
        editPanel.add(new JScrollPane(libraryEditorArea), BorderLayout.CENTER); editPanel.add(btnRow, BorderLayout.SOUTH);

        p.add(new JScrollPane(libraryList)); p.add(editPanel);
        return p;
    }

    private void fetchDynamicTargets() {
        ActionType type = (ActionType) actionCombo.getSelectedItem();
        nearbyEntitiesModel.clear();
        Set<String> names = new HashSet<>();
        if(type == ActionType.ATTACK) names = NPCs.all().stream().filter(n -> n.hasAction("Attack")).map(n -> n.getName()).collect(Collectors.toSet());
        else if(type == ActionType.CHOP) names = GameObjects.all().stream().filter(o -> o.hasAction("Chop down")).map(o -> o.getName()).collect(Collectors.toSet());
        else if(type == ActionType.MINE) names = GameObjects.all().stream().filter(o -> o.hasAction("Mine")).map(o -> o.getName()).collect(Collectors.toSet());
        else names = NPCs.all().stream().map(n -> n.getName()).collect(Collectors.toSet());
        names.stream().sorted().forEach(nearbyEntitiesModel::addElement);
    }

    private void initializeGenericLibrary() {
        libraryModel.addElement(new Task("Goblin Slayer", "Kills Goblins nearby.", Arrays.asList(new Action(ActionType.ATTACK, "Goblin")), "Exterminating..."));
        libraryModel.addElement(new Task("Leather Crafter", "Uses Needle on Leather.", Arrays.asList(new Action(ActionType.USE_ON, "Needle,Leather")), "Stitching..."));
    }

    private void updateUI() {
        long tXp = 0; int tLg = 0;
        for (SkillData data : skillRegistry.values()) {
            int xp = Skills.getExperience(data.skill); int lvl = Skills.getRealLevel(data.skill);
            data.update(xp, lvl, startTime);
            if (data.isTracking) { tXp += (xp - data.startXP); tLg += (lvl - data.startLevel); }
        }
        totalXpGainedLabel.setText("Total XP Gained: " + String.format("%,d", tXp));
        totalLevelsGainedLabel.setText("Total Levels Gained: " + tLg);

        int totalExp = 0; for(Skill s : Skill.values()) totalExp += Skills.getExperience(s);
        playerStatsLabel.setText("Total XP: " + String.format("%,d", totalExp) + " | Total Lvl: " + Skills.getTotalLevel());
    }

    private void loadIntoBuilder(Task t) {
        if(t == null) return;
        taskNameInput.setText(t.name); taskDescInput.setText(t.desc); taskStatusInput.setText(t.status);
        builderActionModel.clear(); t.actions.forEach(builderActionModel::addElement);
    }

    private void shiftQueue(int d) { int i = taskQueueList.getSelectedIndex(); if (i != -1 && i+d >= 0 && i+d < queueModel.size()) { Task t = queueModel.remove(i); queueModel.add(i+d, t); taskQueueList.setSelectedIndex(i+d); } }

    private JPanel createHeaderPanel() {
        JPanel h = new JPanel(new BorderLayout()); h.setBackground(PANEL_SURFACE); h.setPreferredSize(new Dimension(0, 85)); h.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_DIM));
        JLabel t = new JLabel(" ETAbot", SwingConstants.LEFT); t.setForeground(ACCENT_BLOOD); t.setFont(new Font("Segoe UI", Font.BOLD, 32)); t.setBorder(new EmptyBorder(0, 25, 0, 0));
        JPanel c = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 20)); c.setOpaque(false);

        btnPlayPause = createIconButton("▶", "Play", e -> toggleScriptState());
        JButton btnStop = createIconButton("■", "STOP SCRIPT", e -> { if(JOptionPane.showConfirmDialog(this, "Kill Script?") == 0) script.stop(); });
        btnInputToggle = createIconButton("🖱", "Input", e -> toggleUserInput());

        c.add(btnPlayPause); c.add(btnStop); c.add(btnInputToggle);
        h.add(t, BorderLayout.WEST); h.add(c, BorderLayout.EAST); return h;
    }

    private void toggleUserInput() { isUserInputAllowed = !isUserInputAllowed; Client.getInstance().setMouseInputEnabled(isUserInputAllowed); btnInputToggle.setText(isUserInputAllowed ? "🖱" : "🚫"); }
    private void toggleScriptState() { isScriptPaused = !isScriptPaused; btnPlayPause.setText(isScriptPaused ? "▶" : "▮▮"); }
    private void styleStatLabel(JLabel l) { l.setForeground(TEXT_MAIN); l.setFont(new Font("Consolas", Font.BOLD, 14)); }
    private void styleComp(JComponent c) { c.setBackground(PANEL_SURFACE); c.setForeground(TEXT_MAIN); c.setBorder(new LineBorder(BORDER_DIM)); }
    private void styleJList(JList<?> l) { l.setBackground(new Color(15,15,15)); l.setForeground(TEXT_MAIN); }
    private JButton createStyledBtn(String t, Color c) { JButton b = new JButton(t); b.setBackground(c); b.setForeground(Color.WHITE); b.setFocusPainted(false); return b; }
    private void styleSpinner(JSpinner s) { JFormattedTextField f = ((JSpinner.DefaultEditor) s.getEditor()).getTextField(); f.setBackground(new Color(30, 30, 30)); f.setForeground(ACCENT_BLOOD); }
    private JButton createIconButton(String s, String tt, ActionListener a) { JButton b = new JButton(s); b.setPreferredSize(new Dimension(40,40)); b.setFont(new Font("Segoe UI Symbol", Font.BOLD, 18)); b.setBackground(new Color(30,0,0)); b.setForeground(ACCENT_BLOOD); b.addActionListener(a); b.setToolTipText(tt); return b; }
    private JPanel createSkillsPanel() { JPanel p = new JPanel(new BorderLayout()); p.setBackground(BG_BASE); JPanel g = new JPanel(new GridLayout(0, 3, 2, 2)); g.setBackground(BG_BASE); for (Skill s : OSRS_ORDER) { SkillData d = new SkillData(s); skillRegistry.put(s, d); g.add(createSkillTile(d)); } p.add(new JScrollPane(g), BorderLayout.CENTER); return p; }
    private JPanel createSkillTile(SkillData d) { JPanel t = new JPanel(new BorderLayout()); t.setBackground(PANEL_SURFACE); t.setBorder(new LineBorder(BORDER_DIM)); t.add(new JLabel(d.skill.name().substring(0,2)), BorderLayout.WEST); t.add(d.lblLevel, BorderLayout.EAST); t.add(d.mainBar, BorderLayout.SOUTH); t.addMouseListener(new MouseAdapter() { public void mousePressed(MouseEvent e) { d.isTracking = !d.isTracking; t.setBorder(new LineBorder(d.isTracking ? ACCENT_BLOOD : BORDER_DIM)); refreshTrackerList(); } }); return t; }
    private void refreshTrackerList() { trackerList.removeAll(); skillRegistry.values().stream().filter(d -> d.isTracking).forEach(d -> { trackerList.add(d.trackerPanel); trackerList.add(Box.createRigidArea(new Dimension(0, 5))); }); trackerList.revalidate(); trackerList.repaint(); }

    private JPanel createPlayerPanel() { return new JPanel(); }
    private JPanel createSettingsInterface() { return new JPanel(); }
    private JPanel createOutputTab() { return new JPanel(); }

    public static class Task {
        public String name, desc, status; public List<Action> actions;
        public Task(String n, String d, List<Action> a, String s) { this.name = n; this.desc = d; this.actions = a; this.status = s; }
        public Task(Task o) { this(o.name, o.desc, o.actions, o.status); }
        public String getEditableString() { return "NAME:"+name+"\nDESC:"+desc+"\nSTATUS:"+status; }
        public void parseFromEditor(String txt) { /* Logic */ }
        @Override public String toString() { return name + " | " + status; }
    }

    public static class Action {
        public ActionType type; public String target;
        public Action(ActionType t, String target) { this.type = t; this.target = target; }
        public boolean execute() {
            switch(type) {
                case ATTACK: return NPCs.closest(target) != null && NPCs.closest(target).interact("Attack");
                case CHOP: return GameObjects.closest(target) != null && GameObjects.closest(target).interact("Chop down");
                case BANK: return org.dreambot.api.methods.container.impl.bank.Bank.open();
                case FISH: return NPCs.closest(target) != null && NPCs.closest(target).interact("Net");
                case USE_ON:
                    String[] items = target.split(",");
                    if(items.length < 2) return false;
                    return Inventory.interact(items[0], "Use") && Inventory.interact(items[1], "Use");
            }
            return false;
        }
        @Override public String toString() { return type.name() + " -> " + target; }
    }

    private class SkillData {
        final Skill skill; final JProgressBar mainBar = new JProgressBar(0, 100); final JLabel lblLevel = new JLabel("1");
        final JPanel trackerPanel = new JPanel(new GridLayout(0, 1)); final JLabel lblGained = new JLabel(), lblPerHour = new JLabel();
        final int startXP, startLevel; boolean isTracking = false;
        SkillData(Skill s) {
            this.skill = s; this.startXP = Skills.getExperience(s); this.startLevel = Skills.getRealLevel(s);
            trackerPanel.setBackground(new Color(25, 25, 25));
            mainBar.setPreferredSize(new Dimension(0, 6)); mainBar.setForeground(ACCENT_ORANGE); mainBar.setBackground(Color.BLACK); mainBar.setBorder(null);
            for(JLabel l : new JLabel[]{lblGained, lblPerHour}) { l.setForeground(TEXT_DIM); l.setFont(new Font("Monospaced", Font.PLAIN, 11)); trackerPanel.add(l); }
        }
        void update(int curXp, int curLvl, long start) {
            int curMin = Skills.getExperienceForLevel(curLvl), curMax = Skills.getExperienceForLevel(curLvl + 1);
            lblLevel.setText(String.valueOf(curLvl)); mainBar.setValue((int) (((double)(curXp - curMin) / Math.max(1, curMax - curMin)) * 100));
            long elapsed = System.currentTimeMillis() - start; int xph = (int) (Math.max(0, curXp - startXP) / Math.max(0.0001, elapsed / 3600000.0));
            lblGained.setText(" +" + (curXp - startXP) + " XP"); lblPerHour.setText(" " + xph + " XP/H");
        }
    }

    private class TaskCellRenderer extends DefaultListCellRenderer { public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) { JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus); if (index == currentExecutionIndex) { l.setBackground(TAB_SELECTED); l.setText("▶ " + l.getText()); } return l; } }
}