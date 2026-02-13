//package main.menu;
//
//import org.dreambot.api.Client;
//import org.dreambot.api.ClientSettings;
//import org.dreambot.api.methods.interactive.Players;
//import org.dreambot.api.methods.settings.PlayerSettings;
//import org.dreambot.api.methods.skills.Skill;
//import org.dreambot.api.methods.skills.Skills;
//import org.dreambot.api.methods.world.Worlds;
//import org.dreambot.api.script.AbstractScript;
//
//import javax.swing.*;
//import javax.swing.border.EmptyBorder;
//import javax.swing.border.LineBorder;
//import javax.swing.border.TitledBorder;
//import java.awt.*;
//import java.awt.event.ActionListener;
//import java.awt.event.MouseAdapter;
//import java.awt.event.MouseEvent;
//import java.util.*;
//import java.util.function.Supplier;
//
///**
// * <h1>ETAbot Nexus Client</h1>
// * <p>
// * The central command dashboard for the ETAbot script suite.
// * </p>
// *
// * @author ETAbot Dev
// * @version 5.7.0-Elite
// */
//public class DreamBotMenu extends JFrame {
//
//    private final AbstractScript script;
//
//    // --- State Flags ---
//    private boolean isScriptPaused = true;
//    private boolean isUserInputAllowed = true;
//
//    // --- Controls ---
//    private JButton btnPlayPause;
//    private JButton btnInputToggle;
//
//    // --- Data ---
//    private final Map<Skill, SkillData> skillRegistry = new EnumMap<>(Skill.class);
//    private final JPanel trackerList;
//    private final JPanel sidePanel;
//    private final JLabel totalXpGainedLabel = new JLabel("Total XP Gained: 0");
//    private final JLabel totalLevelsGainedLabel = new JLabel("Total Levels Gained: 0");
//    private final JLabel totalLevelLabel = new JLabel("F2P Total: 0 | P2P Total: 0", SwingConstants.CENTER);
//    private final JSpinner projectionSpinner;
//    private final long startTime;
//
//    // --- Player Tab Labels ---
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
//    // --- Theme ---
//    private final Color BG_BASE = new Color(12, 12, 12);
//    private final Color PANEL_SURFACE = new Color(24, 24, 24);
//    private final Color ACCENT_BLOOD = new Color(150, 0, 0);
//    private final Color BORDER_DIM = new Color(45, 45, 45);
//    private final Color TEXT_MAIN = new Color(210, 210, 210);
//    private final Color TEXT_DIM = new Color(140, 140, 140);
//    private final Color TAB_SELECTED = new Color(60, 0, 0);
//
//    private static final Skill[] OSRS_ORDER = {
//            Skill.ATTACK, Skill.HITPOINTS, Skill.MINING, Skill.STRENGTH, Skill.AGILITY, Skill.SMITHING,
//            Skill.DEFENCE, Skill.HERBLORE, Skill.FISHING, Skill.RANGED, Skill.THIEVING, Skill.COOKING,
//            Skill.PRAYER, Skill.CRAFTING, Skill.FIREMAKING, Skill.MAGIC, Skill.FLETCHING, Skill.WOODCUTTING,
//            Skill.RUNECRAFTING, Skill.SLAYER, Skill.FARMING, Skill.CONSTRUCTION, Skill.HUNTER, Skill.SAILING
//    };
//
//    private static final Set<Skill> F2P_SKILLS = new HashSet<>(Arrays.asList(
//            Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.PRAYER, Skill.MAGIC,
//            Skill.HITPOINTS, Skill.CRAFTING, Skill.MINING, Skill.SMITHING, Skill.FISHING,
//            Skill.COOKING, Skill.FIREMAKING, Skill.WOODCUTTING, Skill.RUNECRAFTING
//    ));
//
//    public DreamBotMenu(AbstractScript script) {
//        this.script = script;
//        this.startTime = System.currentTimeMillis();
//
//        if (script != null) {
//            script.getScriptManager().pause();
//        }
//
//        setTitle("ETAbot | Nexus Client");
//        setSize(1350, 850);
//        setLocationRelativeTo(null);
//        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
//        setLayout(new BorderLayout(0, 0));
//        getContentPane().setBackground(BG_BASE);
//
//        JPanel header = createHeaderPanel();
//
//        JTabbedPane mainTabs = new JTabbedPane();
//        mainTabs.setBackground(PANEL_SURFACE);
//        mainTabs.setForeground(TEXT_MAIN);
//        mainTabs.setFont(new Font("Segoe UI", Font.BOLD, 12));
//
//        mainTabs.addTab("Skill Tracker", loadMiscIcon("Stats_icon"), createSkillsPanel());
//        mainTabs.addTab("Player", null, createPlayerPanel());
//        mainTabs.addTab("Settings", null, createSettingsInterface());
//        mainTabs.addTab("Breaks", null, createBreaksPanel());
//        mainTabs.addTab("Help", null, createHelpPanel());
//
//        sidePanel = new JPanel(new BorderLayout());
//        sidePanel.setPreferredSize(new Dimension(360, 0));
//        sidePanel.setBackground(PANEL_SURFACE);
//        sidePanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER_DIM));
//
//        JPanel sideHeader = new JPanel();
//        sideHeader.setLayout(new BoxLayout(sideHeader, BoxLayout.Y_AXIS));
//        sideHeader.setOpaque(false);
//        sideHeader.setBorder(new EmptyBorder(15, 15, 15, 15));
//
//        JLabel sideTitle = new JLabel("LIVE TRACKER");
//        sideTitle.setForeground(Color.WHITE);
//        sideTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
//
//        JPanel fRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 10));
//        fRow.setOpaque(false);
//        projectionSpinner = new JSpinner(new SpinnerNumberModel(24, 1, 999, 1));
//        styleSpinner(projectionSpinner);
//        fRow.add(new JLabel("Forecast: ")); fRow.add(projectionSpinner); fRow.add(new JLabel(" hrs"));
//
//        sideHeader.add(sideTitle); sideHeader.add(fRow);
//
//        trackerList = new JPanel();
//        trackerList.setLayout(new BoxLayout(trackerList, BoxLayout.Y_AXIS));
//        trackerList.setBackground(PANEL_SURFACE);
//        JScrollPane sScroll = new JScrollPane(trackerList);
//        sScroll.setBorder(null);
//        sScroll.getViewport().setBackground(PANEL_SURFACE);
//        sidePanel.add(sideHeader, BorderLayout.NORTH);
//        sidePanel.add(sScroll, BorderLayout.CENTER);
//
//        JButton tBtn = new JButton(">");
//        tBtn.setPreferredSize(new Dimension(22, 0));
//        tBtn.setBackground(PANEL_SURFACE);
//        tBtn.setForeground(ACCENT_BLOOD);
//        tBtn.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 1, BORDER_DIM));
//        tBtn.addActionListener(e -> {
//            boolean visible = sidePanel.isVisible();
//            sidePanel.setVisible(!visible);
//            tBtn.setText(visible ? "<" : ">");
//            revalidate();
//        });
//
//        add(header, BorderLayout.NORTH);
//        add(mainTabs, BorderLayout.CENTER);
//        add(tBtn, BorderLayout.EAST);
//        add(sidePanel, BorderLayout.EAST);
//
//        setVisible(true);
//        new javax.swing.Timer(1000, e -> SwingUtilities.invokeLater(this::updateAll)).start();
//    }
//
//    private JPanel createHeaderPanel() {
//        JPanel header = new JPanel(new BorderLayout());
//        header.setBackground(PANEL_SURFACE);
//        header.setPreferredSize(new Dimension(0, 85));
//        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_DIM));
//
//        JLabel titleLabel = new JLabel(" ETAbot", SwingConstants.LEFT);
//        titleLabel.setForeground(ACCENT_BLOOD);
//        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 32));
//        titleLabel.setBorder(new EmptyBorder(0, 25, 0, 0));
//
//        JPanel rightContainer = new JPanel(new BorderLayout());
//        rightContainer.setOpaque(false);
//        rightContainer.setBorder(new EmptyBorder(0, 0, 0, 20));
//
//        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 20));
//        controls.setOpaque(false);
//
//        btnPlayPause = createIconButton("▶", "Resume Script", e -> toggleScriptState());
//        JButton btnStop = createIconButton("■", "Stop Script", e -> stopScript());
//        btnInputToggle = createIconButton("🖱", "Block User Input", e -> toggleUserInput());
//
//        controls.add(btnPlayPause);
//        controls.add(btnStop);
//        controls.add(btnInputToggle);
//
//        JPanel headerStats = new JPanel(new GridLayout(2, 1));
//        headerStats.setOpaque(false);
//        headerStats.setBorder(new EmptyBorder(10, 20, 10, 10));
//        styleHeaderLabel(totalXpGainedLabel);
//        styleHeaderLabel(totalLevelsGainedLabel);
//        headerStats.add(totalXpGainedLabel);
//        headerStats.add(totalLevelsGainedLabel);
//
//        rightContainer.add(controls, BorderLayout.CENTER);
//        rightContainer.add(headerStats, BorderLayout.EAST);
//        header.add(titleLabel, BorderLayout.WEST);
//        header.add(rightContainer, BorderLayout.EAST);
//
//        return header;
//    }
//
//    private void toggleUserInput() {
//        isUserInputAllowed = !isUserInputAllowed;
//        Client.getInstance().setMouseInputEnabled(isUserInputAllowed);
//        Client.getInstance().setKeyboardInputEnabled(isUserInputAllowed);
//
//        if (isUserInputAllowed) {
//            btnInputToggle.setText("🖱");
//            btnInputToggle.setForeground(ACCENT_BLOOD);
//            btnInputToggle.setToolTipText("Block User Input");
//        } else {
//            btnInputToggle.setText("🚫");
//            btnInputToggle.setForeground(Color.GRAY);
//            btnInputToggle.setToolTipText("Enable User Input");
//        }
//    }
//
//    private void toggleScriptState() {
//        if (script == null) return;
//        if (isScriptPaused) {
//            script.getScriptManager().resume();
//            btnPlayPause.setText("▮▮");
//            btnPlayPause.setToolTipText("Pause Script");
//            isScriptPaused = false;
//        } else {
//            script.getScriptManager().pause();
//            btnPlayPause.setText("▶");
//            btnPlayPause.setToolTipText("Resume Script");
//            isScriptPaused = true;
//        }
//    }
//
//    private void stopScript() {
//        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to kill the script?", "Confirm Stop", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
//        if (confirm == JOptionPane.YES_OPTION && script != null) {
//            script.stop();
//            this.dispose();
//        }
//    }
//
//    // --- PLAYER TAB ---
//    private JPanel createPlayerPanel() {
//        JPanel container = new JPanel(new BorderLayout());
//        container.setBackground(BG_BASE);
//        container.setBorder(new EmptyBorder(20, 20, 20, 20));
//
//        JPanel content = new JPanel(new GridLayout(1, 2, 20, 0));
//        content.setBackground(BG_BASE);
//
//        // Card 1: Login Details
//        JPanel login = createInfoCard("Login Details");
//        addInfoRow(login, "Username", lblUsername);
//        addInfoRow(login, "Password", lblPassword);
//        addInfoRow(login, "Identifier", lblAcctId);
//        addInfoRow(login, "Acct Status", lblAcctStatus);
//
//        // Card 2: World (Renamed from In-Game Status)
//        JPanel game = createInfoCard("World");
//        addInfoRow(game, "Character Name", lblCharName);
//        addInfoRowWithIcon(game, "Membership", lblMemberText, lblMemberIcon); // Star Icon Row
//        addInfoRow(game, "World", lblWorld);
//        addInfoRow(game, "Coordinates", lblCoords);
//        addInfoRow(game, "Game State", lblGameState);
//
//        content.add(login);
//        content.add(game);
//
//        container.add(content, BorderLayout.NORTH);
//        return container;
//    }
//
//    private JPanel createInfoCard(String title) {
//        JPanel p = new JPanel(new GridLayout(0, 1, 5, 10));
//        p.setBackground(PANEL_SURFACE);
//        TitledBorder b = BorderFactory.createTitledBorder(new LineBorder(BORDER_DIM), " " + title + " ");
//        b.setTitleColor(ACCENT_BLOOD);
//        b.setTitleFont(new Font("Segoe UI", Font.BOLD, 16));
//        p.setBorder(BorderFactory.createCompoundBorder(b, new EmptyBorder(15, 15, 15, 15)));
//        return p;
//    }
//
//    private void addInfoRow(JPanel p, String key, JLabel valLabel) {
//        JPanel row = new JPanel(new BorderLayout());
//        row.setOpaque(false);
//        JLabel k = new JLabel(key);
//        k.setForeground(TEXT_DIM);
//        k.setFont(new Font("Segoe UI", Font.PLAIN, 14));
//        valLabel.setForeground(TEXT_MAIN);
//        valLabel.setFont(new Font("Consolas", Font.BOLD, 14));
//        valLabel.setHorizontalAlignment(SwingConstants.RIGHT);
//        row.add(k, BorderLayout.WEST);
//        row.add(valLabel, BorderLayout.EAST);
//        row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 40, 40)));
//        p.add(row);
//    }
//
//    private void addInfoRowWithIcon(JPanel p, String key, JLabel valLabel, JLabel iconLabel) {
//        JPanel row = new JPanel(new BorderLayout(5, 0));
//        row.setOpaque(false);
//        JLabel k = new JLabel(key);
//        k.setForeground(TEXT_DIM);
//        k.setFont(new Font("Segoe UI", Font.PLAIN, 14));
//        JPanel rightSide = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
//        rightSide.setOpaque(false);
//        valLabel.setForeground(TEXT_MAIN);
//        valLabel.setFont(new Font("Consolas", Font.BOLD, 14));
//        rightSide.add(valLabel);
//        rightSide.add(iconLabel);
//        row.add(k, BorderLayout.WEST);
//        row.add(rightSide, BorderLayout.EAST);
//        row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 40, 40)));
//        p.add(row);
//    }
//
//    // --- SETTINGS ---
//    private JPanel createSettingsInterface() {
//        JPanel container = new JPanel(new BorderLayout());
//        container.setBackground(BG_BASE);
//        CardLayout cardLayout = new CardLayout();
//        JPanel contentPanel = new JPanel(cardLayout);
//        contentPanel.setBackground(BG_BASE);
//        contentPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
//
//        contentPanel.add(createClientPanel(), "Client");
//        contentPanel.add(createActivitiesPanel(), "Activities");
//        contentPanel.add(createAudioPanel(), "Audio");
//        contentPanel.add(createChatPanel(), "Chat");
//        contentPanel.add(createDisplayPanel(), "Display");
//        contentPanel.add(createControlsPanel(), "Controls");
//        contentPanel.add(createWarningsPanel(), "Warnings");
//
//        JPanel menuPanel = new JPanel(new GridLayout(10, 1, 0, 2));
//        menuPanel.setPreferredSize(new Dimension(180, 0));
//        menuPanel.setBackground(PANEL_SURFACE);
//        menuPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_DIM));
//
//        ButtonGroup btnGroup = new ButtonGroup();
//        String[] categories = {"Client", "Display", "Audio", "Chat", "Controls", "Activities", "Warnings"};
//
//        for (String cat : categories) {
//            JToggleButton btn = createMenuButton(cat);
//            btn.addActionListener(e -> cardLayout.show(contentPanel, cat));
//            btnGroup.add(btn);
//            menuPanel.add(btn);
//            if (cat.equals("Client")) btn.setSelected(true);
//        }
//        container.add(menuPanel, BorderLayout.WEST);
//        container.add(contentPanel, BorderLayout.CENTER);
//        return container;
//    }
//
//    private JPanel createClientPanel() {
//        JPanel panel = new JPanel(new GridLayout(2, 1, 0, 20));
//        panel.setBackground(BG_BASE);
//
//        JPanel toggles = createSettingsGroup("Engine Toggles",
//                createSettingCheck("Disable Rendering", Client.isRenderingDisabled(), e -> Client.setRenderingDisabled(((JCheckBox)e.getSource()).isSelected())),
//                createSettingCheck("Ignore Entities (Menu)", false, e -> Client.setMenuIgnoreEntities(((JCheckBox)e.getSource()).isSelected()))
//        );
//
//        JPanel spinners = createSettingsGroup("Engine Thresholds",
//                createLabelSpinner("Forced FPS", 50, 1, 100, e -> Client.setForcedFPS((int) ((JSpinner)e.getSource()).getValue())),
//                createLabelSpinner("Idle Logout (Cycles)", Client.getIdleLogout(), 100, 50000, e -> Client.setIdleLogout((int) ((JSpinner)e.getSource()).getValue())),
//                createLabelSpinner("Forced Idle Time", Client.getIdleTime(), 0, 50000, e -> Client.setIdleTime((int) ((JSpinner)e.getSource()).getValue()))
//        );
//
//        panel.add(toggles);
//        panel.add(spinners);
//        JPanel aligner = new JPanel(new BorderLayout()); aligner.setBackground(BG_BASE); aligner.add(panel, BorderLayout.NORTH);
//        return aligner;
//    }
//
//    // --- UTILS ---
//    private JPanel createSettingsGroup(String title, Component... comps) { JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10)); p.setBackground(BG_BASE); JPanel list = new JPanel(new GridLayout(0, 1, 5, 5)); list.setBackground(BG_BASE); JLabel header = new JLabel(title); header.setForeground(ACCENT_BLOOD); header.setFont(new Font("Segoe UI", Font.BOLD, 24)); header.setBorder(new EmptyBorder(0, 0, 20, 0)); JPanel wrapper = new JPanel(new BorderLayout()); wrapper.setBackground(BG_BASE); wrapper.add(header, BorderLayout.NORTH); for (Component c : comps) list.add(c); wrapper.add(list, BorderLayout.CENTER); JPanel aligner = new JPanel(new BorderLayout()); aligner.setBackground(BG_BASE); aligner.add(wrapper, BorderLayout.NORTH); return aligner; }
//    private JCheckBox createSettingCheck(String text, boolean initialState, ActionListener l) { return createSettingCheck(text, initialState, () -> true, "", l); }
//    private JCheckBox createSettingCheck(String text, boolean initialState, Supplier<Boolean> enableCondition, String disableReason, ActionListener l) { JCheckBox c = new JCheckBox(text); c.setForeground(TEXT_MAIN); c.setFont(new Font("Segoe UI", Font.PLAIN, 14)); c.setOpaque(false); c.setSelected(initialState); c.setFocusPainted(false); if (!enableCondition.get()) { c.setEnabled(false); c.setToolTipText(disableReason); c.setForeground(Color.DARK_GRAY); } if (l != null) c.addActionListener(l); return c; }
//    private JPanel createLabelSpinner(String label, int initial, int min, int max, javax.swing.event.ChangeListener listener) { JPanel p = new JPanel(new BorderLayout()); p.setOpaque(false); JLabel l = new JLabel(label + ": "); l.setForeground(TEXT_MAIN); JSpinner s = new JSpinner(new SpinnerNumberModel(initial, min, max, 1)); styleSpinner(s); s.addChangeListener(listener); p.add(l, BorderLayout.WEST); p.add(s, BorderLayout.EAST); p.setBorder(new EmptyBorder(5, 5, 5, 5)); return p; }
//    private JToggleButton createMenuButton(String text) { JToggleButton btn = new JToggleButton(text) { @Override protected void paintComponent(Graphics g) { if (isSelected()) g.setColor(TAB_SELECTED); else g.setColor(PANEL_SURFACE); g.fillRect(0, 0, getWidth(), getHeight()); super.paintComponent(g); } }; btn.setFocusPainted(false); btn.setContentAreaFilled(false); btn.setOpaque(false); btn.setForeground(TEXT_MAIN); btn.setFont(new Font("Segoe UI", Font.BOLD, 14)); btn.setHorizontalAlignment(SwingConstants.LEFT); btn.setBorder(new EmptyBorder(0, 20, 0, 0)); btn.addChangeListener(e -> { if(btn.isSelected()) { btn.setForeground(Color.WHITE); btn.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 4, 0, 0, ACCENT_BLOOD), new EmptyBorder(0, 16, 0, 0))); } else { btn.setForeground(TEXT_MAIN); btn.setBorder(new EmptyBorder(0, 20, 0, 0)); } }); return btn; }
//    private JButton createIconButton(String symbol, String tooltip, ActionListener action) { JButton btn = new JButton(symbol); btn.setPreferredSize(new Dimension(40, 40)); btn.setFont(new Font("Segoe UI Symbol", Font.BOLD, 18)); btn.setBackground(new Color(30, 0, 0)); btn.setForeground(ACCENT_BLOOD); btn.setToolTipText(tooltip); btn.setFocusPainted(false); btn.setBorder(BorderFactory.createLineBorder(ACCENT_BLOOD)); btn.addActionListener(action); btn.addMouseListener(new MouseAdapter() { public void mouseEntered(MouseEvent e) { btn.setBackground(new Color(60, 0, 0)); } public void mouseExited(MouseEvent e) { btn.setBackground(new Color(30, 0, 0)); } }); return btn; }
//    private void styleHeaderLabel(JLabel l) { l.setForeground(TEXT_MAIN); l.setFont(new Font("Consolas", Font.BOLD, 15)); l.setHorizontalAlignment(SwingConstants.RIGHT); }
//    private void styleSpinner(JSpinner s) { s.setPreferredSize(new Dimension(55, 24)); JFormattedTextField field = ((JSpinner.DefaultEditor) s.getEditor()).getTextField(); field.setBackground(new Color(30, 30, 30)); field.setForeground(ACCENT_BLOOD); s.setBorder(new LineBorder(BORDER_DIM)); }
//    private ImageIcon loadMiscIcon(String name) { try { return new ImageIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/resources/icons/misc/" + name + ".png"))).getImage().getScaledInstance(18, 18, Image.SCALE_SMOOTH)); } catch (Exception e) { return null; } }
//    private ImageIcon loadSkillIcon(Skill skill) { try { return new ImageIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/resources/icons/skills/" + skill.name().toLowerCase() + "_icon.png"))).getImage().getScaledInstance(26, 26, Image.SCALE_SMOOTH)); } catch (Exception e) { return new ImageIcon(); } }
//
//    private ImageIcon loadStatusIcon(String name) {
//        try {
//            java.net.URL imgURL = getClass().getResource("/resources/icons/status/" + name + ".png");
//            if (imgURL != null) {
//                return new ImageIcon(new ImageIcon(imgURL).getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH));
//            }
//        } catch (Exception ignored) {}
//        return null;
//    }
//
//    private JPanel createDisplayPanel() { return createSettingsGroup("Display", createSettingCheck("Roofs", !ClientSettings.areRoofsHidden(), e -> ClientSettings.toggleRoofs(((JCheckBox)e.getSource()).isSelected())), createSettingCheck("Data orbs", ClientSettings.areDataOrbsEnabled(), e -> ClientSettings.toggleDataOrbs(((JCheckBox)e.getSource()).isSelected())), createSettingCheck("Transparent side panel", ClientSettings.isTransparentSidePanelEnabled(), e -> ClientSettings.toggleTransparentSidePanel(((JCheckBox)e.getSource()).isSelected()))); }
//    private JPanel createAudioPanel() { return createSettingsGroup("Audio", createSettingCheck("Game Audio", ClientSettings.isGameAudioOn(), e -> ClientSettings.toggleGameAudio(((JCheckBox)e.getSource()).isSelected()))); }
//    private JPanel createChatPanel() { return createSettingsGroup("Chat", createSettingCheck("Transparent chatbox", ClientSettings.isTransparentChatboxEnabled(), e -> ClientSettings.toggleTransparentChatbox(((JCheckBox)e.getSource()).isSelected())), createSettingCheck("Click through chatbox", ClientSettings.isClickThroughChatboxEnabled(), e -> ClientSettings.toggleClickThroughChatbox(((JCheckBox)e.getSource()).isSelected())), createSettingCheck("Trade delay", ClientSettings.isTradeDelayEnabled(), e -> ClientSettings.toggleTradeDelay(((JCheckBox)e.getSource()).isSelected()))); }
//    private JPanel createControlsPanel() { return createSettingsGroup("Controls", createSettingCheck("Shift click drop", ClientSettings.isShiftClickDroppingEnabled(), e -> ClientSettings.toggleShiftClickDropping(((JCheckBox)e.getSource()).isSelected())), createSettingCheck("Esc closes interface", ClientSettings.isEscInterfaceClosingEnabled(), e -> ClientSettings.toggleEscInterfaceClosing(((JCheckBox)e.getSource()).isSelected())), createSettingCheck("Scroll to zoom", ClientSettings.isScrollToZoomEnabled(), e -> ClientSettings.toggleScrollToZoom(((JCheckBox)e.getSource()).isSelected())), createSettingCheck("Accept Aid", ClientSettings.isAcceptAidEnabled(), () -> PlayerSettings.getBitValue(1777) == 0, "Unavailable: Ironmen cannot accept aid.", e -> ClientSettings.toggleAcceptAid(((JCheckBox)e.getSource()).isSelected()))); }
//    private JPanel createActivitiesPanel() { return createSettingsGroup("Activities", createSettingCheck("Level-up interface", ClientSettings.isLevelUpInterfaceEnabled(), e -> ClientSettings.toggleLevelUpInterface(((JCheckBox)e.getSource()).isSelected())), createSettingCheck("Make-x darts", ClientSettings.isMakeXDartsEnabled(), e -> ClientSettings.toggleMakeXDarts(((JCheckBox)e.getSource()).isSelected())), createSettingCheck("Skull prevention", ClientSettings.isSkullPreventionActive(), e -> ClientSettings.toggleSkullPrevention(((JCheckBox)e.getSource()).isSelected()))); }
//    private JPanel createWarningsPanel() { return createSettingsGroup("Warnings", createSettingCheck("Loot drop notifications", ClientSettings.areLootNotificationsEnabled(), e -> ClientSettings.toggleLootNotifications(((JCheckBox)e.getSource()).isSelected())), createSettingCheck("Sell price warning", ClientSettings.isSellPriceWarningEnabled(), e -> ClientSettings.toggleSellPriceWarning(((JCheckBox)e.getSource()).isSelected())), createSettingCheck("Buy price warning", ClientSettings.isBuyPriceWarningEnabled(), e -> ClientSettings.toggleBuyPriceWarning(((JCheckBox)e.getSource()).isSelected()))); }
//    private JPanel createSkillsPanel() { JPanel panel = new JPanel(new BorderLayout()); panel.setBackground(BG_BASE); JPanel grid = new JPanel(new GridLayout(0, 3, 3, 3)); grid.setBackground(BG_BASE); grid.setBorder(new EmptyBorder(8, 8, 8, 8)); for (Skill skill : OSRS_ORDER) { SkillData data = new SkillData(skill); skillRegistry.put(skill, data); grid.add(createSkillTile(data)); } JPanel totalBar = new JPanel(new BorderLayout()); totalBar.setBackground(PANEL_SURFACE); totalBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_DIM)); totalLevelLabel.setForeground(TEXT_MAIN); totalLevelLabel.setFont(new Font("Consolas", Font.BOLD, 16)); totalLevelLabel.setBorder(new EmptyBorder(12, 0, 12, 0)); totalBar.add(totalLevelLabel, BorderLayout.CENTER); panel.add(new JScrollPane(grid), BorderLayout.CENTER); panel.add(totalBar, BorderLayout.SOUTH); return panel; }
//    private JPanel createSkillTile(SkillData data) { JPanel tile = new JPanel(new GridBagLayout()); tile.setBackground(PANEL_SURFACE); tile.setBorder(new LineBorder(BORDER_DIM)); GridBagConstraints gbc = new GridBagConstraints(); gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0; gbc.gridx = 0; JPanel top = new JPanel(new BorderLayout()); top.setOpaque(false); JLabel icon = new JLabel(loadSkillIcon(data.skill)); data.lblLevel.setForeground(ACCENT_BLOOD); data.lblLevel.setFont(new Font("Arial", Font.BOLD, 18)); top.add(icon, BorderLayout.WEST); top.add(data.lblLevel, BorderLayout.EAST); top.setBorder(new EmptyBorder(5, 12, 0, 12)); data.lblXpString.setForeground(TEXT_DIM); data.lblXpString.setFont(new Font("Monospaced", Font.PLAIN, 10)); data.lblXpString.setHorizontalAlignment(SwingConstants.CENTER); data.mainBar.setPreferredSize(new Dimension(0, 6)); data.mainBar.setForeground(ACCENT_BLOOD); data.mainBar.setBackground(Color.BLACK); data.mainBar.setBorder(null); gbc.gridy = 0; tile.add(top, gbc); gbc.gridy = 1; tile.add(data.lblXpString, gbc); gbc.gridy = 2; gbc.insets = new Insets(2, 12, 10, 12); tile.add(data.mainBar, gbc); tile.addMouseListener(new MouseAdapter() { @Override public void mousePressed(MouseEvent e) { data.isTracking = !data.isTracking; tile.setBorder(new LineBorder(data.isTracking ? ACCENT_BLOOD : BORDER_DIM, 1)); refreshTrackerList(); } }); return tile; }
//    private void refreshTrackerList() { trackerList.removeAll(); skillRegistry.values().stream().filter(d -> d.isTracking).forEach(d -> { trackerList.add(d.trackerPanel); trackerList.add(Box.createRigidArea(new Dimension(0, 10))); }); trackerList.add(Box.createVerticalGlue()); trackerList.revalidate(); trackerList.repaint(); }
//    private JPanel createBreaksPanel() { JPanel p = new JPanel(new GridBagLayout()); p.setBackground(BG_BASE); JLabel l = new JLabel("Break Manager Placeholder"); l.setForeground(TEXT_DIM); p.add(l); return p; }
//    private JPanel createHelpPanel() { JPanel p = new JPanel(new GridBagLayout()); p.setBackground(BG_BASE); JLabel l = new JLabel("Help / Docs Placeholder"); l.setForeground(TEXT_DIM); p.add(l); return p; }
//
//    private void updateAll() {
//        int projH = (int) projectionSpinner.getValue(); long tXp = 0; int tLg = 0; int p2pTotal = 0; int f2pTotal = 0;
//        for (SkillData data : skillRegistry.values()) {
//            int xp = Skills.getExperience(data.skill); int lvl = Skills.getRealLevel(data.skill); data.update(xp, lvl, startTime, projH);
//            p2pTotal += lvl; if (F2P_SKILLS.contains(data.skill)) f2pTotal += lvl;
//            if (data.isTracking) { tXp += Math.max(0, (xp - data.startXP)); tLg += Math.max(0, (lvl - data.startLevel)); }
//        }
//        totalXpGainedLabel.setText("Total XP Gained: " + String.format("%,d", tXp)); totalLevelsGainedLabel.setText("Total Levels Gained: " + tLg); totalLevelLabel.setText(String.format("F2P Total: %d | P2P Total: %d", f2pTotal, p2pTotal));
//
//        if (Client.isLoggedIn()) {
//            lblUsername.setText(Optional.ofNullable(Client.getUsername()).orElse("null"));
//            lblPassword.setText(Optional.ofNullable(Client.getPassword()).orElse("null"));
//            lblAcctId.setText(Optional.ofNullable(Client.getAccountIdentifier()).orElse("-"));
//            lblAcctStatus.setText(String.valueOf(Client.getAccountStatus()));
//
//            // Fixed World Logic
//            String worldText = "?";
//            if (Worlds.getCurrent() != null) worldText = String.valueOf(Worlds.getCurrent().getWorld());
//            lblWorld.setText("World " + worldText);
//
//            // Membership logic with icons
//            boolean isMember = Client.isMembers();
//            lblMemberText.setText(isMember ? "Member" : "Free-to-Play");
//            lblMemberIcon.setIcon(loadStatusIcon(isMember ? "Member_icon" : "Free-to-play_icon"));
//
//            if (Players.getLocal() != null) {
//                lblCharName.setText(Players.getLocal().getName());
//                lblCoords.setText(Players.getLocal().getTile().toString());
//            }
//            lblGameState.setText(Client.getGameState().name());
//        } else {
//            lblUsername.setText("-"); lblPassword.setText("-"); lblAcctId.setText("-");
//            lblAcctStatus.setText("Offline"); lblGameState.setText("LOGIN_SCREEN");
//            lblMemberText.setText("-"); lblMemberIcon.setIcon(null);
//        }
//    }
//
//    private class SkillData {
//        final Skill skill; final JProgressBar mainBar = new JProgressBar(0, 100); final JLabel lblLevel = new JLabel("1"), lblXpString = new JLabel("0/0");
//        final JPanel trackerPanel = new JPanel(new GridLayout(0, 1, 2, 2)); final JLabel lblGained = new JLabel(), lblPerHour = new JLabel(), lblRemaining = new JLabel(), lblTTL = new JLabel(), lblProj = new JLabel(), lblActs = new JLabel();
//        final int startXP, startLevel; boolean isTracking = false;
//        SkillData(Skill s) { this.skill = s; this.startXP = Skills.getExperience(s); this.startLevel = Skills.getRealLevel(s); trackerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 165)); trackerPanel.setBackground(new Color(30, 30, 30)); TitledBorder b = BorderFactory.createTitledBorder(new LineBorder(BORDER_DIM), " " + s.name() + " "); b.setTitleColor(ACCENT_BLOOD); trackerPanel.setBorder(b); JLabel[] ls = {lblGained, lblPerHour, lblRemaining, lblActs, lblTTL, lblProj}; for (JLabel l : ls) { l.setForeground(TEXT_MAIN); l.setFont(new Font("Consolas", Font.PLAIN, 12)); l.setBorder(new EmptyBorder(0, 15, 0, 0)); trackerPanel.add(l); } }
//        void update(int curXp, int curLvl, long start, int ph) { int curMin = Skills.getExperienceForLevel(curLvl), curMax = Skills.getExperienceForLevel(curLvl + 1); lblLevel.setText(String.valueOf(curLvl)); lblXpString.setText(String.format("%,d / %,d XP", curXp, curMax)); mainBar.setValue((int) (((double)(curXp - curMin) / Math.max(1, curMax - curMin)) * 100)); long elapsed = System.currentTimeMillis() - start; int xph = (int) (Math.max(0, curXp - startXP) / Math.max(0.0001, elapsed / 3600000.0)); int rem = Math.max(0, curMax - curXp); lblGained.setText(" GAINED:    " + String.format("%,d XP", curXp - startXP)); lblPerHour.setText(" XP/HR:     " + String.format("%,d", xph)); lblRemaining.setText(" TO LEVEL:  " + String.format("%,d XP", rem)); lblActs.setText(" EST. ACTS: " + String.format("%,d", rem / 100)); if (xph > 0) { lblTTL.setText(String.format(" TIME TO L: %.2f hrs", (double) rem / xph)); int pXp = curXp + (xph * ph); int pLvl = curLvl; while (pLvl < 126 && Skills.getExperienceForLevel(pLvl + 1) <= pXp) pLvl++; lblProj.setText(String.format(" PROJ (%dH): Lvl %d", ph, pLvl)); lblProj.setForeground(new Color(100, 255, 100)); } else { lblTTL.setText(" TIME TO L: --:--"); lblProj.setText(" FORECAST: STATIONARY"); lblProj.setForeground(TEXT_DIM); } }
//    }
//}