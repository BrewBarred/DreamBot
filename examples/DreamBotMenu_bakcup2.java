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
// * @author DreamBotMan Dev
// * @version 15.0.0-Elite
// */
//public class DreamBotMenu extends JFrame {
//
//    private final AbstractScript script;
//
//    // --- State ---
//    private boolean isScriptPaused = true;
//    private boolean isUserInputAllowed = true;
//    private boolean isCaptureEnabled = true;
//    private int currentExecutionIndex = 0;
//
//    // --- Data & Presets ---
//    private final Map<Skill, SkillData> skillRegistry = new EnumMap<>(Skill.class);
//    private final DefaultListModel<Task> queueModel = new DefaultListModel<>();
//    private final DefaultListModel<Task> libraryModel = new DefaultListModel<>();
//    private final DefaultListModel<Action> builderActionModel = new DefaultListModel<>();
//    private final DefaultListModel<String> nearbyEntitiesModel = new DefaultListModel<>();
//    private final List<List<Task>> presets = new ArrayList<>(Arrays.asList(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
//
//    // --- UI Components ---
//    private final JPanel trackerList, sidePanel;
//    private final JLabel totalXpGainedLabel = new JLabel("Total XP Gained: 0");
//    private final JLabel totalLevelsGainedLabel = new JLabel("Total Levels Gained: 0");
//    private final JLabel totalLevelLabel = new JLabel("F2P Total: 0 | P2P Total: 0", SwingConstants.CENTER);
//    private final JProgressBar statusProgress = new JProgressBar(0, 100);
//    private final JLabel lblStatus = new JLabel("Status: Idle");
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
//    // --- Theme ---
//    private final Color BG_BASE = new Color(12, 12, 12);
//    private final Color PANEL_SURFACE = new Color(24, 24, 24);
//    private final Color ACCENT_BLOOD = new Color(150, 0, 0);
//    private final Color BORDER_DIM = new Color(45, 45, 45);
//    private final Color TEXT_MAIN = new Color(210, 210, 210);
//    private final Color TEXT_DIM = new Color(140, 140, 140);
//    private final Color TAB_SELECTED = new Color(60, 0, 0);
//
//    // --- Labels (Restored) ---
//    private final JLabel lblUsername = new JLabel("..."), lblPassword = new JLabel("..."), lblAcctId = new JLabel("...");
//    private final JLabel lblAcctStatus = new JLabel("..."), lblCharName = new JLabel("..."), lblWorld = new JLabel("-");
//    private final JLabel lblCoords = new JLabel("-"), lblGameState = new JLabel("-"), lblMemberIcon = new JLabel(), lblMemberText = new JLabel("-");
//
//    public enum ActionType { ATTACK, BANK, BURY, CHOP, COOK, DROP, EXAMINE, FISH, MINE, OPEN, PAY_CHARTER, PAY_TOLL, PICK_UP, PRAY_AT, SMELT, SMITH, TALK_TO, TRADE, USE }
//
//    private static final Skill[] OSRS_ORDER = { Skill.ATTACK, Skill.HITPOINTS, Skill.MINING, Skill.STRENGTH, Skill.AGILITY, Skill.SMITHING, Skill.DEFENCE, Skill.HERBLORE, Skill.FISHING, Skill.RANGED, Skill.THIEVING, Skill.COOKING, Skill.PRAYER, Skill.CRAFTING, Skill.FIREMAKING, Skill.MAGIC, Skill.FLETCHING, Skill.WOODCUTTING, Skill.RUNECRAFTING, Skill.SLAYER, Skill.FARMING, Skill.CONSTRUCTION, Skill.HUNTER, Skill.SAILING };
//    private static final Set<Skill> F2P_SKILLS = new HashSet<>(Arrays.asList(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.PRAYER, Skill.MAGIC, Skill.HITPOINTS, Skill.CRAFTING, Skill.MINING, Skill.SMITHING, Skill.FISHING, Skill.COOKING, Skill.FIREMAKING, Skill.WOODCUTTING, Skill.RUNECRAFTING));
//
//    public DreamBotMenu(AbstractScript script) {
//        this.script = script;
//        this.startTime = System.currentTimeMillis();
//
//        setTitle("DreamBotMan | Nexus Client");
//        setSize(1400, 950);
//        setLocationRelativeTo(null);
//        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
//        setLayout(new BorderLayout(0, 0));
//        getContentPane().setBackground(BG_BASE);
//
//        JPanel header = createHeaderPanel();
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
//        sidePanel = new JPanel(new BorderLayout());
//        sidePanel.setPreferredSize(new Dimension(360, 0));
//        sidePanel.setBackground(PANEL_SURFACE);
//        sidePanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER_DIM));
//
//        projectionSpinner = new JSpinner(new SpinnerNumberModel(24, 1, 999, 1));
//        styleSpinner(projectionSpinner);
//        trackerList = new JPanel(); trackerList.setLayout(new BoxLayout(trackerList, BoxLayout.Y_AXIS)); trackerList.setBackground(PANEL_SURFACE);
//        JScrollPane sScroll = new JScrollPane(trackerList); sScroll.setBorder(null); sScroll.getViewport().setBackground(PANEL_SURFACE);
//        sidePanel.add(sScroll, BorderLayout.CENTER);
//
//        JButton tBtn = new JButton(">");
//        tBtn.setPreferredSize(new Dimension(22, 0));
//        tBtn.setBackground(PANEL_SURFACE);
//        tBtn.setForeground(ACCENT_BLOOD);
//        tBtn.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 1, BORDER_DIM));
//        tBtn.addActionListener(e -> { sidePanel.setVisible(!sidePanel.isVisible()); tBtn.setText(sidePanel.isVisible() ? "<" : ">"); revalidate(); });
//
//        add(header, BorderLayout.NORTH);
//        add(mainTabs, BorderLayout.CENTER);
//        add(tBtn, BorderLayout.EAST);
//        add(sidePanel, BorderLayout.EAST);
//
//        initializeGenericLibrary();
//        setVisible(true);
//        new javax.swing.Timer(1000, e -> updateUI()).start();
//    }
//
//    // --- Bridge Methods for Main Script ---
//    public boolean isScriptPaused() { return isScriptPaused; }
//    public DefaultListModel<Task> getQueueModel() { return queueModel; }
//    public int getCurrentExecutionIndex() { return currentExecutionIndex; }
//    public void setCurrentExecutionIndex(int i) { this.currentExecutionIndex = i; }
//    public void setLabelStatus(String text) { SwingUtilities.invokeLater(() -> lblStatus.setText(text)); }
//
//    public void incrementExecutionIndex() {
//        if (currentExecutionIndex < queueModel.size() - 1) {
//            currentExecutionIndex++;
//        } else {
//            currentExecutionIndex = 0;
//            isScriptPaused = true;
//            if (btnPlayPause != null) btnPlayPause.setText("▶");
//        }
//        taskQueueList.repaint();
//    }
//
//    // --- TABS (Fixed & Restored) ---
//    private JPanel createTaskListTab() {
//        JPanel p = new JPanel(new BorderLayout(10, 10)); p.setBackground(BG_BASE); p.setBorder(new EmptyBorder(15, 15, 15, 15));
//        JPanel presetTop = new JPanel(new GridLayout(1, 6, 5, 0)); presetTop.setOpaque(false);
//        for (int i = 0; i < 4; i++) {
//            int idx = i;
//            JButton b = createStyledBtn("Slot " + (i + 1), new Color(35, 35, 35));
//            b.addActionListener(e -> { queueModel.clear(); presets.get(idx).forEach(queueModel::addElement); });
//            presetTop.add(b);
//        }
//        JButton btnSaveP = createStyledBtn("SAVE", ACCENT_BLOOD);
//        btnSaveP.addActionListener(e -> {
//            String slot = JOptionPane.showInputDialog("Save to slot (1-4)?");
//            try { int s = Integer.parseInt(slot)-1; presets.set(s, Collections.list(queueModel.elements())); } catch(Exception ignored){}
//        });
//        JButton btnClearQ = createStyledBtn("CLEAR", new Color(100, 0, 0));
//        btnClearQ.addActionListener(e -> queueModel.clear());
//        presetTop.add(btnSaveP); presetTop.add(btnClearQ);
//
//        JPanel arrowCol = new JPanel(new GridLayout(2, 1, 0, 5)); arrowCol.setOpaque(false);
//        JButton btnUp = createStyledBtn("▲", new Color(40, 40, 40)); btnUp.addActionListener(e -> shiftQueue(-1));
//        JButton btnDown = createStyledBtn("▼", new Color(40, 40, 40)); btnDown.addActionListener(e -> shiftQueue(1));
//        arrowCol.add(btnUp); arrowCol.add(btnDown);
//
//        taskQueueList = new JList<>(queueModel);
//        taskQueueList.setCellRenderer(new TaskCellRenderer());
//        styleJList(taskQueueList);
//
//        JPanel south = new JPanel(new BorderLayout(10, 10)); south.setOpaque(false);
//        lblStatus.setForeground(TEXT_MAIN); statusProgress.setForeground(ACCENT_BLOOD);
//        south.add(lblStatus, BorderLayout.NORTH); south.add(statusProgress, BorderLayout.CENTER);
//
//        p.add(presetTop, BorderLayout.NORTH);
//        p.add(arrowCol, BorderLayout.WEST); p.add(new JScrollPane(taskQueueList), BorderLayout.CENTER); p.add(south, BorderLayout.SOUTH);
//        return p;
//    }
//
//    private JPanel createBuilderTab() {
//        JPanel p = new JPanel(new BorderLayout(15, 0)); p.setBackground(BG_BASE); p.setBorder(new EmptyBorder(15, 15, 15, 15));
//        JPanel left = new JPanel(new GridBagLayout()); left.setOpaque(false); GridBagConstraints g = new GridBagConstraints(); g.fill = GridBagConstraints.HORIZONTAL; g.insets = new Insets(5,5,5,5);
//        actionCombo = new JComboBox<>(Arrays.stream(ActionType.values()).sorted(Comparator.comparing(Enum::name)).toArray(ActionType[]::new));
//        styleComp(actionCombo);
//        g.gridx = 0; g.gridy = 0; left.add(new JLabel("Action:"), g); g.gridx = 1; left.add(actionCombo, g);
//        JButton btnAddAct = createStyledBtn("ADD ACTION TO BUILDER", ACCENT_BLOOD);
//        btnAddAct.addActionListener(e -> {
//            String target = manualTargetInput.getText();
//            if(!target.isEmpty()) builderActionModel.addElement(new Action((ActionType)actionCombo.getSelectedItem(), target));
//        });
//        g.gridy = 1; g.gridwidth = 2; left.add(btnAddAct, g);
//
//        JPanel rightSide = new JPanel(new BorderLayout(0, 10)); rightSide.setOpaque(false);
//        JPanel inputStack = new JPanel(new GridLayout(0, 1, 2, 5)); inputStack.setOpaque(false);
//        manualTargetInput = new JTextField(12); styleComp(manualTargetInput);
//        nearbyEntitiesList = new JList<>(nearbyEntitiesModel); styleJList(nearbyEntitiesList);
//        nearbyEntitiesList.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) { String sel = nearbyEntitiesList.getSelectedValue(); if(sel != null) manualTargetInput.setText(sel); } } });
//        inputStack.add(new JLabel("TARGET CONFIGURATION")); inputStack.add(new JLabel("Manual Target:")); inputStack.add(manualTargetInput); inputStack.add(new JLabel("Double-Click Nearby:"));
//        JButton btnRefreshNearby = createStyledBtn("REFRESH NEARBY", new Color(50, 50, 50));
//        btnRefreshNearby.addActionListener(e -> fetchDynamicTargets());
//        rightSide.add(inputStack, BorderLayout.NORTH); rightSide.add(new JScrollPane(nearbyEntitiesList), BorderLayout.CENTER); rightSide.add(btnRefreshNearby, BorderLayout.SOUTH);
//
//        JPanel bottomMetadata = new JPanel(new GridLayout(0, 2, 10, 5)); bottomMetadata.setOpaque(false);
//        taskNameInput = new JTextField(); taskDescInput = new JTextField(); taskStatusInput = new JTextField();
//        styleComp(taskNameInput); styleComp(taskDescInput); styleComp(taskStatusInput);
//        bottomMetadata.add(new JLabel("Task Name:")); bottomMetadata.add(taskNameInput); bottomMetadata.add(new JLabel("Description:")); bottomMetadata.add(taskDescInput); bottomMetadata.add(new JLabel("Status Flair:")); bottomMetadata.add(taskStatusInput);
//
//        JList<Action> bActions = new JList<>(builderActionModel); styleJList(bActions);
//        JButton btnCommit = createStyledBtn("COMMIT TO LIBRARY", ACCENT_BLOOD);
//        btnCommit.addActionListener(e -> {
//            List<Action> acts = new ArrayList<>(); for(int i=0; i<builderActionModel.size(); i++) acts.add(builderActionModel.get(i));
//            libraryModel.addElement(new Task(taskNameInput.getText(), taskDescInput.getText(), acts, taskStatusInput.getText()));
//            builderActionModel.clear();
//        });
//        JPanel centerLayout = new JPanel(new BorderLayout(10, 10)); centerLayout.setOpaque(false);
//        centerLayout.add(new JScrollPane(bActions), BorderLayout.CENTER); centerLayout.add(bottomMetadata, BorderLayout.SOUTH);
//        p.add(left, BorderLayout.WEST); p.add(centerLayout, BorderLayout.CENTER); p.add(rightSide, BorderLayout.EAST);
//        return p;
//    }
//
//    private void fetchDynamicTargets() {
//        ActionType type = (ActionType) actionCombo.getSelectedItem();
//        nearbyEntitiesModel.clear();
//        Set<String> names = new HashSet<>();
//        if (type == ActionType.ATTACK) names = NPCs.all().stream().filter(n -> n != null && n.hasAction("Attack")).map(n -> n.getName()).collect(Collectors.toSet());
//        else if (type == ActionType.CHOP) names = GameObjects.all().stream().filter(o -> o != null && o.hasAction("Chop down")).map(o -> o.getName()).collect(Collectors.toSet());
//        else {
//            names.addAll(NPCs.all().stream().filter(Objects::nonNull).map(n -> n.getName()).collect(Collectors.toSet()));
//            names.addAll(GameObjects.all().stream().filter(Objects::nonNull).map(o -> o.getName()).collect(Collectors.toSet()));
//        }
//        names.forEach(nearbyEntitiesModel::addElement);
//    }
//
//    // --- Inner Classes (Upgraded) ---
//    public static class Task {
//        public String name, desc, status; public List<Action> actions;
//        private static final String[] HUMAN_FLAIR = {"Thinking...", "Working...", "Analyzing..."};
//        public Task(String n, String d, List<Action> a, String s) {
//            this.name = n; this.desc = d; this.actions = a;
//            this.status = (s == null || s.isEmpty()) ? HUMAN_FLAIR[new Random().nextInt(HUMAN_FLAIR.length)] : s;
//        }
//        public Task(Task o) { this(o.name, o.desc, o.actions, o.status); }
//        public String getEditableString() { return "NAME:"+name+"\nDESC:"+desc+"\nSTATUS:"+status; }
//        public void parseFromEditor(String text) { /* Simplified parsing */ }
//        @Override public String toString() { return name + " | " + status; }
//    }
//
//    public static class Action {
//        public ActionType type; public String target;
//        public Action(ActionType t, String target) { this.type = t; this.target = target; }
//        public boolean execute() {
//            switch (type) {
//                case CHOP: return GameObjects.closest(target) != null && GameObjects.closest(target).interact("Chop down");
//                case ATTACK: return NPCs.closest(target) != null && NPCs.closest(target).interact("Attack");
//                case BANK: return org.dreambot.api.methods.container.impl.bank.Bank.open();
//                default: Logger.log("Action " + type + " not implemented in Action.execute()"); return false;
//            }
//        }
//        @Override public String toString() { return type.name() + " -> " + target; }
//    }
//
//    // --- (Paste the rest of your original styling/header methods here exactly as they were) ---
//    private JPanel createLibraryTab() { JPanel p = new JPanel(new GridLayout(1, 2, 10, 0)); p.setBackground(BG_BASE); p.setBorder(new EmptyBorder(15, 15, 15, 15)); libraryList = new JList<>(libraryModel); styleJList(libraryList); JPanel editPanel = new JPanel(new BorderLayout(0, 10)); editPanel.setOpaque(false); libraryEditorArea = new JTextArea(); libraryEditorArea.setBackground(new Color(15, 15, 15)); libraryEditorArea.setForeground(TEXT_MAIN); libraryList.addListSelectionListener(e -> { Task t = libraryList.getSelectedValue(); if (t != null) libraryEditorArea.setText(t.getEditableString()); }); JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT)); btnRow.setOpaque(false); JButton btnAdd = createStyledBtn("Add to Queue", new Color(0, 100, 0)); btnAdd.addActionListener(e -> { if(libraryList.getSelectedValue() != null) queueModel.addElement(new Task(libraryList.getSelectedValue())); }); editPanel.add(new JScrollPane(libraryEditorArea), BorderLayout.CENTER); editPanel.add(btnRow, BorderLayout.SOUTH); p.add(new JScrollPane(libraryList)); p.add(editPanel); return p; }
//    private void initializeGenericLibrary() { libraryModel.addElement(new Task("Woodcutter", "Chops Trees", Arrays.asList(new Action(ActionType.CHOP, "Tree")), "Chopping...")); }
//    private JPanel createHeaderPanel() { JPanel header = new JPanel(new BorderLayout()); header.setBackground(PANEL_SURFACE); header.setPreferredSize(new Dimension(0, 85)); header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_DIM)); JLabel titleLabel = new JLabel(" DreamBotMan", SwingConstants.LEFT); titleLabel.setForeground(ACCENT_BLOOD); titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 32)); titleLabel.setBorder(new EmptyBorder(0, 25, 0, 0)); JPanel rightContainer = new JPanel(new BorderLayout()); rightContainer.setOpaque(false); rightContainer.setBorder(new EmptyBorder(0, 0, 0, 20)); JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 20)); controls.setOpaque(false); btnPlayPause = createIconButton("▶", "Play", e -> toggleScriptState()); JButton btnStop = createIconButton("■", "Stop", e -> stopScript()); btnInputToggle = createIconButton("🖱", "Input", e -> toggleUserInput()); controls.add(btnPlayPause); controls.add(btnStop); controls.add(btnInputToggle); JPanel headerStats = new JPanel(new GridLayout(2, 1)); headerStats.setOpaque(false); headerStats.setBorder(new EmptyBorder(10, 20, 10, 10)); styleHeaderLabel(totalXpGainedLabel); styleHeaderLabel(totalLevelsGainedLabel); headerStats.add(totalXpGainedLabel); headerStats.add(totalLevelsGainedLabel); rightContainer.add(controls, BorderLayout.CENTER); rightContainer.add(headerStats, BorderLayout.EAST); header.add(titleLabel, BorderLayout.WEST); header.add(rightContainer, BorderLayout.EAST); return header; }
//    private void toggleScriptState() { if (script == null) return; if (isScriptPaused) { script.getScriptManager().resume(); btnPlayPause.setText("▮▮"); isScriptPaused = false; } else { script.getScriptManager().pause(); btnPlayPause.setText("▶"); isScriptPaused = true; } }
//    private void stopScript() { if (JOptionPane.showConfirmDialog(this, "Stop?") == JOptionPane.YES_OPTION) { script.stop(); dispose(); } }
//    private void toggleUserInput() { isUserInputAllowed = !isUserInputAllowed; Client.getInstance().setMouseInputEnabled(isUserInputAllowed); Client.getInstance().setKeyboardInputEnabled(isUserInputAllowed); btnInputToggle.setText(isUserInputAllowed ? "🖱" : "🚫"); }
//    private void styleHeaderLabel(JLabel l) { l.setForeground(TEXT_MAIN); l.setFont(new Font("Consolas", Font.BOLD, 15)); l.setHorizontalAlignment(SwingConstants.RIGHT); }
//    private void styleSpinner(JSpinner s) { JFormattedTextField field = ((JSpinner.DefaultEditor) s.getEditor()).getTextField(); field.setBackground(new Color(30, 30, 30)); field.setForeground(ACCENT_BLOOD); s.setBorder(new LineBorder(BORDER_DIM)); }
//    private JButton createIconButton(String symbol, String tooltip, ActionListener action) { JButton btn = new JButton(symbol); btn.setPreferredSize(new Dimension(40, 40)); btn.setFont(new Font("Segoe UI Symbol", Font.BOLD, 18)); btn.setBackground(new Color(30, 0, 0)); btn.setForeground(ACCENT_BLOOD); btn.addActionListener(action); return btn; }
//    private JPanel createOutputTab() { JPanel p = new JPanel(new BorderLayout()); p.setBackground(BG_BASE); consoleArea = new JTextArea(); consoleArea.setBackground(Color.BLACK); consoleArea.setForeground(new Color(0, 255, 0)); consoleArea.setFont(new Font("Consolas", Font.PLAIN, 12)); ((DefaultCaret)consoleArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE); consoleSearch = new JTextField(); styleComp(consoleSearch); btnCaptureToggle = createStyledBtn("Capture: ON", new Color(0, 80, 0)); btnCaptureToggle.addActionListener(e -> { isCaptureEnabled = !isCaptureEnabled; btnCaptureToggle.setText("Capture: " + (isCaptureEnabled ? "ON" : "OFF")); }); JPanel t = new JPanel(new BorderLayout()); t.setOpaque(false); t.add(consoleSearch, BorderLayout.CENTER); t.add(btnCaptureToggle, BorderLayout.EAST); p.add(t, BorderLayout.NORTH); p.add(new JScrollPane(consoleArea), BorderLayout.CENTER); return p; }
//    private void shiftQueue(int dir) { int idx = taskQueueList.getSelectedIndex(); if (idx == -1 || idx+dir < 0 || idx+dir >= queueModel.size()) return; Task t = queueModel.remove(idx); queueModel.add(idx+dir, t); taskQueueList.setSelectedIndex(idx+dir); }
//    private void styleComp(JComponent c) { c.setBackground(PANEL_SURFACE); c.setForeground(TEXT_MAIN); if(c instanceof JTextField) ((JTextField)c).setCaretColor(ACCENT_BLOOD); }
//    private void styleJList(JList<?> l) { l.setBackground(PANEL_SURFACE); l.setForeground(TEXT_MAIN); l.setSelectionBackground(TAB_SELECTED); }
//    private JButton createStyledBtn(String t, Color c) { JButton b = new JButton(t); b.setBackground(c); b.setForeground(Color.WHITE); b.setFocusPainted(false); b.setBorder(new LineBorder(BORDER_DIM)); return b; }
//    private ImageIcon loadMiscIcon(String name) { try { return new ImageIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/resources/icons/misc/" + name + ".png"))).getImage().getScaledInstance(18, 18, Image.SCALE_SMOOTH)); } catch (Exception e) { return null; } }
//    private ImageIcon loadSkillIcon(Skill skill) { try { return new ImageIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/resources/icons/skills/" + skill.name().toLowerCase() + "_icon.png"))).getImage().getScaledInstance(26, 26, Image.SCALE_SMOOTH)); } catch (Exception e) { return new ImageIcon(); } }
//    private ImageIcon loadStatusIcon(String name) { try { return new ImageIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/resources/icons/status/" + name + ".png"))).getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH)); } catch (Exception ignored) {} return null; }
//    private void updateUI() { updateAll(); }
//    private JPanel createPlayerPanel() { JPanel container = new JPanel(new BorderLayout()); container.setBackground(BG_BASE); container.setBorder(new EmptyBorder(20, 20, 20, 20)); JPanel content = new JPanel(new GridLayout(1, 2, 20, 0)); content.setBackground(BG_BASE); JPanel login = createInfoCard("Login Details"); addInfoRow(login, "Username", lblUsername); addInfoRow(login, "Password", lblPassword); addInfoRow(login, "Identifier", lblAcctId); addInfoRow(login, "Acct Status", lblAcctStatus); JPanel game = createInfoCard("World"); addInfoRow(game, "Character Name", lblCharName); addInfoRowWithIcon(game, "Membership", lblMemberText, lblMemberIcon); addInfoRow(game, "World", lblWorld); addInfoRow(game, "Coordinates", lblCoords); addInfoRow(game, "GameState", lblGameState); content.add(login); content.add(game); container.add(content, BorderLayout.NORTH); return container; }
//    private JPanel createInfoCard(String title) { JPanel p = new JPanel(new GridLayout(0, 1, 5, 10)); p.setBackground(PANEL_SURFACE); TitledBorder b = BorderFactory.createTitledBorder(new LineBorder(BORDER_DIM), " " + title + " "); b.setTitleColor(ACCENT_BLOOD); b.setTitleFont(new Font("Segoe UI", Font.BOLD, 16)); p.setBorder(BorderFactory.createCompoundBorder(b, new EmptyBorder(15, 15, 15, 15))); return p; }
//    private void addInfoRow(JPanel p, String key, JLabel valLabel) { JPanel row = new JPanel(new BorderLayout()); row.setOpaque(false); JLabel k = new JLabel(key); k.setForeground(TEXT_DIM); valLabel.setForeground(TEXT_MAIN); valLabel.setFont(new Font("Consolas", Font.BOLD, 14)); row.add(k, BorderLayout.WEST); row.add(valLabel, BorderLayout.EAST); row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 40, 40))); p.add(row); }
//    private void addInfoRowWithIcon(JPanel p, String key, JLabel valLabel, JLabel iconLabel) { JPanel row = new JPanel(new BorderLayout(5, 0)); row.setOpaque(false); JLabel k = new JLabel(key); k.setForeground(TEXT_DIM); JPanel rightSide = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0)); rightSide.setOpaque(false); valLabel.setForeground(TEXT_MAIN); rightSide.add(valLabel); rightSide.add(iconLabel); row.add(k, BorderLayout.WEST); row.add(rightSide, BorderLayout.EAST); row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 40, 40))); p.add(row); }
//    private JPanel createSettingsInterface() { JPanel container = new JPanel(new BorderLayout()); container.setBackground(BG_BASE); CardLayout cardLayout = new CardLayout(); JPanel contentPanel = new JPanel(cardLayout); contentPanel.setBackground(BG_BASE); contentPanel.setBorder(new EmptyBorder(20, 20, 20, 20)); contentPanel.add(createClientPanel(), "Client"); contentPanel.add(createActivitiesPanel(), "Activities"); contentPanel.add(createAudioPanel(), "Audio"); contentPanel.add(createChatPanel(), "Chat"); contentPanel.add(createDisplayPanel(), "Display"); contentPanel.add(createControlsPanel(), "Controls"); contentPanel.add(createWarningsPanel(), "Warnings"); JPanel menuPanel = new JPanel(new GridLayout(10, 1, 0, 2)); menuPanel.setPreferredSize(new Dimension(180, 0)); menuPanel.setBackground(PANEL_SURFACE); menuPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_DIM)); String[] cats = {"Client", "Display", "Audio", "Chat", "Controls", "Activities", "Warnings"}; ButtonGroup btnGroup = new ButtonGroup(); for (String cat : cats) { JToggleButton btn = createMenuButton(cat); btn.addActionListener(e -> cardLayout.show(contentPanel, cat)); btnGroup.add(btn); menuPanel.add(btn); if (cat.equals("Client")) btn.setSelected(true); } container.add(menuPanel, BorderLayout.WEST); container.add(contentPanel, BorderLayout.CENTER); return container; }
//    private JPanel createDisplayPanel() { return createSettingsGroup("Display", createSettingCheck("Roofs", !ClientSettings.areRoofsHidden(), e -> ClientSettings.toggleRoofs(((JCheckBox)e.getSource()).isSelected())), createSettingCheck("Data orbs", ClientSettings.areDataOrbsEnabled(), e -> ClientSettings.toggleDataOrbs(((JCheckBox)e.getSource()).isSelected())), createSettingCheck("Transparent side panel", ClientSettings.isTransparentSidePanelEnabled(), e -> ClientSettings.toggleTransparentSidePanel(((JCheckBox)e.getSource()).isSelected()))); }
//    private JPanel createAudioPanel() { return createSettingsGroup("Audio", createSettingCheck("Game Audio", ClientSettings.isGameAudioOn(), e -> ClientSettings.toggleGameAudio(((JCheckBox)e.getSource()).isSelected()))); }
//    private JPanel createChatPanel() { return createSettingsGroup("Chat", createSettingCheck("Transparent chatbox", ClientSettings.isTransparentChatboxEnabled(), e -> ClientSettings.toggleTransparentChatbox(((JCheckBox)e.getSource()).isSelected())), createSettingCheck("Click through chatbox", ClientSettings.isClickThroughChatboxEnabled(), e -> ClientSettings.toggleClickThroughChatbox(((JCheckBox)e.getSource()).isSelected()))); }
//    private JPanel createControlsPanel() { return createSettingsGroup("Controls", createSettingCheck("Shift click drop", ClientSettings.isShiftClickDroppingEnabled(), e -> ClientSettings.toggleShiftClickDropping(((JCheckBox)e.getSource()).isSelected())), createSettingCheck("Esc closes interface", ClientSettings.isEscInterfaceClosingEnabled(), e -> ClientSettings.toggleEscInterfaceClosing(((JCheckBox)e.getSource()).isSelected()))); }
//    private JPanel createWarningsPanel() { return createSettingsGroup("Warnings", createSettingCheck("Loot notifications", ClientSettings.areLootNotificationsEnabled(), e -> ClientSettings.toggleLootNotifications(((JCheckBox)e.getSource()).isSelected()))); }
//    private JPanel createClientPanel() { return createSettingsGroup("Client", createSettingCheck("Disable Rendering", Client.isRenderingDisabled(), e -> Client.setRenderingDisabled(((JCheckBox)e.getSource()).isSelected()))); }
//    private JPanel createActivitiesPanel() { return createSettingsGroup("Activities", createSettingCheck("Level-up interface", ClientSettings.isLevelUpInterfaceEnabled(), e -> ClientSettings.toggleLevelUpInterface(((JCheckBox)e.getSource()).isSelected()))); }
//    private JPanel createSettingsGroup(String title, Component... comps) { JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10)); p.setBackground(BG_BASE); JPanel list = new JPanel(new GridLayout(0, 1, 5, 5)); list.setBackground(BG_BASE); JLabel header = new JLabel(title); header.setForeground(ACCENT_BLOOD); header.setFont(new Font("Segoe UI", Font.BOLD, 24)); JPanel wrapper = new JPanel(new BorderLayout()); wrapper.setBackground(BG_BASE); wrapper.add(header, BorderLayout.NORTH); for (Component c : comps) list.add(c); wrapper.add(list, BorderLayout.CENTER); return wrapper; }
//    private JCheckBox createSettingCheck(String text, boolean initialState, ActionListener l) { JCheckBox c = new JCheckBox(text); c.setForeground(TEXT_MAIN); c.setOpaque(false); c.setSelected(initialState); if (l != null) c.addActionListener(l); return c; }
//    private JToggleButton createMenuButton(String text) { JToggleButton btn = new JToggleButton(text) { protected void paintComponent(Graphics g) { g.setColor(isSelected() ? TAB_SELECTED : PANEL_SURFACE); g.fillRect(0, 0, getWidth(), getHeight()); super.paintComponent(g); } }; btn.setFocusPainted(false); btn.setContentAreaFilled(false); btn.setForeground(TEXT_MAIN); btn.setFont(new Font("Segoe UI", Font.BOLD, 14)); btn.setHorizontalAlignment(SwingConstants.LEFT); btn.setBorder(new EmptyBorder(0, 20, 0, 0)); return btn; }
//    private JPanel createSkillsPanel() { JPanel panel = new JPanel(new BorderLayout()); panel.setBackground(BG_BASE); JPanel grid = new JPanel(new GridLayout(0, 3, 3, 3)); grid.setBackground(BG_BASE); grid.setBorder(new EmptyBorder(8, 8, 8, 8)); for (Skill skill : OSRS_ORDER) { SkillData data = new SkillData(skill); skillRegistry.put(skill, data); grid.add(createSkillTile(data)); } panel.add(new JScrollPane(grid), BorderLayout.CENTER); totalLevelLabel.setForeground(TEXT_MAIN); panel.add(totalLevelLabel, BorderLayout.SOUTH); return panel; }
//    private JPanel createSkillTile(SkillData data) { JPanel tile = new JPanel(new GridBagLayout()); tile.setBackground(PANEL_SURFACE); tile.setBorder(new LineBorder(BORDER_DIM)); GridBagConstraints gbc = new GridBagConstraints(); gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0; gbc.gridx = 0; JPanel top = new JPanel(new BorderLayout()); top.setOpaque(false); JLabel icon = new JLabel(loadSkillIcon(data.skill)); data.lblLevel.setForeground(ACCENT_BLOOD); data.lblLevel.setFont(new Font("Arial", Font.BOLD, 18)); top.add(icon, BorderLayout.WEST); top.add(data.lblLevel, BorderLayout.EAST); data.lblXpString.setForeground(TEXT_DIM); data.lblXpString.setFont(new Font("Monospaced", Font.PLAIN, 10)); data.mainBar.setForeground(ACCENT_BLOOD); data.mainBar.setBackground(Color.BLACK); gbc.gridy = 0; tile.add(top, gbc); gbc.gridy = 1; tile.add(data.lblXpString, gbc); gbc.gridy = 2; tile.add(data.mainBar, gbc); tile.addMouseListener(new MouseAdapter() { @Override public void mousePressed(MouseEvent e) { data.isTracking = !data.isTracking; tile.setBorder(new LineBorder(data.isTracking ? ACCENT_BLOOD : BORDER_DIM, 1)); refreshTrackerList(); } }); return tile; }
//    private void refreshTrackerList() { trackerList.removeAll(); skillRegistry.values().stream().filter(d -> d.isTracking).forEach(d -> { trackerList.add(d.trackerPanel); trackerList.add(Box.createRigidArea(new Dimension(0, 10))); }); trackerList.add(Box.createVerticalGlue()); trackerList.revalidate(); trackerList.repaint(); }
//    private void updateAll() { int projH = (int) projectionSpinner.getValue(); long tXp = 0; int tLg = 0; int p2pTotal = 0; int f2pTotal = 0; for (SkillData data : skillRegistry.values()) { int xp = Skills.getExperience(data.skill); int lvl = Skills.getRealLevel(data.skill); data.update(xp, lvl, startTime, projH); p2pTotal += lvl; if (F2P_SKILLS.contains(data.skill)) f2pTotal += lvl; if (data.isTracking) { tXp += Math.max(0, (xp - data.startXP)); tLg += Math.max(0, (lvl - data.startLevel)); } } totalXpGainedLabel.setText("Total XP Gained: " + String.format("%,d", tXp)); totalLevelsGainedLabel.setText("Total Levels Gained: " + tLg); totalLevelLabel.setText(String.format("F2P Total: %d | P2P Total: %d", f2pTotal, p2pTotal)); if (Client.isLoggedIn() && Players.getLocal() != null) { lblUsername.setText(Players.getLocal().getName()); lblPassword.setText(Optional.ofNullable(Client.getPassword()).orElse("null")); lblWorld.setText("World " + (Worlds.getCurrent() != null ? Worlds.getCurrent().getWorld() : "?")); boolean isMember = Client.isMembers(); lblMemberText.setText(isMember ? P2P : "F2P"); lblMemberIcon.setIcon(loadStatusIcon(isMember ? "Member_icon" : "Free-to-play_icon")); lblCharName.setText(Players.getLocal().getName()); lblCoords.setText(Players.getLocal().getTile().toString()); lblGameState.setText(Client.getGameState().name()); } }
//    private class TaskCellRenderer extends DefaultListCellRenderer { @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) { JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus); if (index == currentExecutionIndex) { l.setBackground(TAB_SELECTED); l.setForeground(Color.WHITE); l.setText("▶ " + l.getText()); } return l; } }
//    private class SkillData { final Skill skill; final JProgressBar mainBar = new JProgressBar(0, 100); final JLabel lblLevel = new JLabel("1"), lblXpString = new JLabel("0/0"); final JPanel trackerPanel = new JPanel(new GridLayout(0, 1, 2, 2)); final JLabel lblGained = new JLabel(), lblPerHour = new JLabel(), lblRemaining = new JLabel(), lblTTL = new JLabel(), lblProj = new JLabel(), lblActs = new JLabel(); final int startXP, startLevel; boolean isTracking = false; SkillData(Skill s) { this.skill = s; this.startXP = Skills.getExperience(s); this.startLevel = Skills.getRealLevel(s); trackerPanel.setBackground(new Color(30, 30, 30)); TitledBorder b = BorderFactory.createTitledBorder(new LineBorder(BORDER_DIM), " " + s.name() + " "); b.setTitleColor(ACCENT_BLOOD); trackerPanel.setBorder(b); JLabel[] ls = {lblGained, lblPerHour, lblRemaining, lblActs, lblTTL, lblProj}; for (JLabel l : ls) { l.setForeground(TEXT_MAIN); l.setFont(new Font("Consolas", Font.PLAIN, 12)); trackerPanel.add(l); } } void update(int curXp, int curLvl, long start, int ph) { int curMin = Skills.getExperienceForLevel(curLvl), curMax = Skills.getExperienceForLevel(curLvl + 1); lblLevel.setText(String.valueOf(curLvl)); lblXpString.setText(String.format("%,d / %,d XP", curXp, curMax)); mainBar.setValue((int) (((double)(curXp - curMin) / Math.max(1, curMax - curMin)) * 100)); long elapsed = System.currentTimeMillis() - start; int xph = (int) (Math.max(0, curXp - startXP) / Math.max(0.0001, elapsed / 3600000.0)); int rem = Math.max(0, curMax - curXp); lblGained.setText(" GAINED: " + String.format("%,d XP", curXp - startXP)); lblPerHour.setText(" XP/HR:  " + String.format("%,d", xph)); lblRemaining.setText(" TO LEVEL: " + String.format("%,d", rem)); if (xph > 0) { lblTTL.setText(String.format(" TIME TO L: %.2f hrs", (double) rem / xph)); lblProj.setText(String.format(" PROJ (%dH): Lvl %d", ph, curLvl + (xph * ph / 100000))); } } }
//}