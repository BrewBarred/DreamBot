package examples;

import org.dreambot.api.Client;
import org.dreambot.api.ClientSettings;
import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.methods.skills.Skills;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;

public class SkillsTab_LiveSettings extends JFrame {

    private final Map<Skill, SkillData> skillRegistry = new EnumMap<>(Skill.class);
    private final JPanel trackerList;
    private final JPanel sidePanel;
    private final JLabel totalXpGainedLabel = new JLabel("Total XP Gained: 0");
    private final JLabel totalLevelsGainedLabel = new JLabel("Total Levels Gained: 0");
    private final JLabel totalLevelLabel = new JLabel("F2P Total: 0 | P2P Total: 0", SwingConstants.CENTER);
    private final JSpinner projectionSpinner;
    private final long startTime;

    // Official OSRS Order
    private static final Skill[] OSRS_ORDER = {
            Skill.ATTACK, Skill.HITPOINTS, Skill.MINING,
            Skill.STRENGTH, Skill.AGILITY, Skill.SMITHING,
            Skill.DEFENCE, Skill.HERBLORE, Skill.FISHING,
            Skill.RANGED, Skill.THIEVING, Skill.COOKING,
            Skill.PRAYER, Skill.CRAFTING, Skill.FIREMAKING,
            Skill.MAGIC, Skill.FLETCHING, Skill.WOODCUTTING,
            Skill.RUNECRAFTING, Skill.SLAYER, Skill.FARMING,
            Skill.CONSTRUCTION, Skill.HUNTER, Skill.SAILING
    };

    // F2P Skills Set
    private static final Set<Skill> F2P_SKILLS = new HashSet<>(Arrays.asList(
            Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.PRAYER, Skill.MAGIC,
            Skill.HITPOINTS, Skill.CRAFTING, Skill.MINING, Skill.SMITHING, Skill.FISHING,
            Skill.COOKING, Skill.FIREMAKING, Skill.WOODCUTTING, Skill.RUNECRAFTING
    ));

    // Theme Colors
    private final Color BG_BASE = new Color(12, 12, 12);
    private final Color PANEL_SURFACE = new Color(24, 24, 24);
    private final Color ACCENT_BLOOD = new Color(150, 0, 0);
    private final Color BORDER_DIM = new Color(45, 45, 45);
    private final Color TEXT_MAIN = new Color(210, 210, 210);
    private final Color TEXT_DIM = new Color(140, 140, 140);

    public SkillsTab_LiveSettings() {
        this.startTime = System.currentTimeMillis();
        setTitle("ETAbot | Nexus Client");
        setSize(1350, 850);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(BG_BASE);

        // --- HEADER ---
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PANEL_SURFACE);
        header.setPreferredSize(new Dimension(0, 85));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_DIM));

        JLabel titleLabel = new JLabel(" ETAbot", SwingConstants.LEFT);
        titleLabel.setForeground(ACCENT_BLOOD);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 32));
        titleLabel.setBorder(new EmptyBorder(0, 25, 0, 0));

        JPanel headerStats = new JPanel(new GridLayout(2, 1));
        headerStats.setOpaque(false);
        headerStats.setBorder(new EmptyBorder(10, 0, 10, 30));
        styleHeaderLabel(totalXpGainedLabel);
        styleHeaderLabel(totalLevelsGainedLabel);
        headerStats.add(totalXpGainedLabel);
        headerStats.add(totalLevelsGainedLabel);

        header.add(titleLabel, BorderLayout.WEST);
        header.add(headerStats, BorderLayout.EAST);

        // --- TABBED INTERFACE ---
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(PANEL_SURFACE);
        tabbedPane.setForeground(TEXT_MAIN);

        // Tab 1: Skill Tracker
        JPanel skillsMainPanel = new JPanel(new BorderLayout());
        skillsMainPanel.setBackground(BG_BASE);

        JPanel gridContainer = new JPanel(new GridLayout(0, 3, 3, 3));
        gridContainer.setBackground(BG_BASE);
        gridContainer.setBorder(new EmptyBorder(8, 8, 8, 8));

        for (Skill skill : OSRS_ORDER) {
            SkillData data = new SkillData(skill);
            skillRegistry.put(skill, data);
            gridContainer.add(createSkillTile(data));
        }

        JPanel totalBar = new JPanel(new BorderLayout());
        totalBar.setBackground(PANEL_SURFACE);
        totalBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_DIM));
        totalLevelLabel.setForeground(TEXT_MAIN);
        totalLevelLabel.setFont(new Font("Consolas", Font.BOLD, 16));
        totalLevelLabel.setBorder(new EmptyBorder(12, 0, 12, 0));
        totalBar.add(totalLevelLabel, BorderLayout.CENTER);

        skillsMainPanel.add(new JScrollPane(gridContainer), BorderLayout.CENTER);
        skillsMainPanel.add(totalBar, BorderLayout.SOUTH);

        tabbedPane.addTab("Skill Tracker", loadMiscIcon("Stats_icon"), skillsMainPanel);

        // Tab 2: Settings
        tabbedPane.addTab("Settings", null, createSettingsPanel());

        // --- SIDEBAR ---
        sidePanel = new JPanel(new BorderLayout());
        sidePanel.setPreferredSize(new Dimension(360, 0));
        sidePanel.setBackground(PANEL_SURFACE);
        sidePanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER_DIM));

        JPanel sideHeader = new JPanel();
        sideHeader.setLayout(new BoxLayout(sideHeader, BoxLayout.Y_AXIS));
        sideHeader.setOpaque(false);
        sideHeader.setBorder(new EmptyBorder(15, 15, 15, 15));

        JLabel sideTitle = new JLabel("LIVE TRACKER");
        sideTitle.setForeground(Color.WHITE);
        sideTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));

        JPanel fRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 10));
        fRow.setOpaque(false);
        projectionSpinner = new JSpinner(new SpinnerNumberModel(24, 1, 999, 1));
        styleSpinner(projectionSpinner);
        fRow.add(new JLabel("Forecast: "));
        fRow.add(projectionSpinner);
        fRow.add(new JLabel(" hrs"));

        sideHeader.add(sideTitle);
        sideHeader.add(fRow);

        trackerList = new JPanel();
        trackerList.setLayout(new BoxLayout(trackerList, BoxLayout.Y_AXIS));
        trackerList.setBackground(PANEL_SURFACE);
        JScrollPane sScroll = new JScrollPane(trackerList);
        sScroll.setBorder(null);
        sScroll.getViewport().setBackground(PANEL_SURFACE);
        sidePanel.add(sideHeader, BorderLayout.NORTH);
        sidePanel.add(sScroll, BorderLayout.CENTER);

        JButton tBtn = new JButton(">");
        tBtn.setPreferredSize(new Dimension(22, 0));
        tBtn.setBackground(PANEL_SURFACE);
        tBtn.setForeground(ACCENT_BLOOD);
        tBtn.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 1, BORDER_DIM));
        tBtn.addActionListener(e -> {
            boolean visible = sidePanel.isVisible();
            sidePanel.setVisible(!visible);
            tBtn.setText(visible ? "<" : ">");
            revalidate();
        });

        add(header, BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);
        add(tBtn, BorderLayout.EAST);
        add(sidePanel, BorderLayout.EAST);

        setVisible(true);
        new javax.swing.Timer(1000, e -> SwingUtilities.invokeLater(this::updateAll)).start();
    }

    private JPanel createSettingsPanel() {
        JPanel main = new JPanel(new GridLayout(0, 2, 10, 10));
        main.setBackground(BG_BASE);
        main.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Group 1: Display & Audio
        main.add(createSettingsGroup("Display & Audio",
                // For roofs: toggleRoofs(true) usually enables them.
                // We use areRoofsHidden() to determine initial state. If hidden, box is unchecked.
                createSettingCheck("Draw Roofs", !ClientSettings.areRoofsHidden(), e -> {
                    boolean enable = ((JCheckBox)e.getSource()).isSelected();
                    ClientSettings.toggleRoofs(enable);
                }),
                createSettingCheck("Game Audio", ClientSettings.isGameAudioOn(), e -> {
                    boolean enable = ((JCheckBox)e.getSource()).isSelected();
                    ClientSettings.toggleGameAudio(enable);
                }),
                createSettingCheck("Data Orbs", ClientSettings.areDataOrbsEnabled(), e -> {
                    boolean enable = ((JCheckBox)e.getSource()).isSelected();
                    ClientSettings.toggleDataOrbs(enable);
                }),
                createSettingCheck("Transparent Chatbox", ClientSettings.isTransparentChatboxEnabled(), e -> {
                    boolean enable = ((JCheckBox)e.getSource()).isSelected();
                    ClientSettings.toggleTransparentChatbox(enable);
                })
        ));

        // Group 2: Controls & Interaction
        main.add(createSettingsGroup("Controls & Interaction",
                createSettingCheck("Shift Click Drop", ClientSettings.isShiftClickDroppingEnabled(), e -> {
                    boolean enable = ((JCheckBox)e.getSource()).isSelected();
                    ClientSettings.toggleShiftClickDropping(enable);
                }),
                createSettingCheck("Esc Closes Interfaces", ClientSettings.isEscInterfaceClosingEnabled(), e -> {
                    boolean enable = ((JCheckBox)e.getSource()).isSelected();
                    ClientSettings.toggleEscInterfaceClosing(enable);
                }),
                createSettingCheck("Scroll to Zoom", ClientSettings.isScrollToZoomEnabled(), e -> {
                    boolean enable = ((JCheckBox)e.getSource()).isSelected();
                    ClientSettings.toggleScrollToZoom(enable);
                }),
                createSettingCheck("Accept Aid", ClientSettings.isAcceptAidEnabled(), e -> {
                    boolean enable = ((JCheckBox)e.getSource()).isSelected();
                    ClientSettings.toggleAcceptAid(enable);
                })
        ));

        // Group 3: Warnings & Notifications
        main.add(createSettingsGroup("Warnings & Chat",
                createSettingCheck("Level-Up Interfaces", ClientSettings.isLevelUpInterfaceEnabled(), e -> {
                    boolean enable = ((JCheckBox)e.getSource()).isSelected();
                    ClientSettings.toggleLevelUpInterface(enable);
                }),
                createSettingCheck("Loot Notifications", ClientSettings.areLootNotificationsEnabled(), e -> {
                    boolean enable = ((JCheckBox)e.getSource()).isSelected();
                    ClientSettings.toggleLootNotifications(enable);
                }),
                createSettingCheck("Sell Price Warning", ClientSettings.isSellPriceWarningEnabled(), e -> {
                    boolean enable = ((JCheckBox)e.getSource()).isSelected();
                    ClientSettings.toggleSellPriceWarning(enable);
                }),
                createSettingCheck("Skull Prevention", ClientSettings.isSkullPreventionActive(), e -> {
                    boolean enable = ((JCheckBox)e.getSource()).isSelected();
                    ClientSettings.toggleSkullPrevention(enable);
                })
        ));

        // Group 4: Client Performance
        // Using standard Client methods for frame/rendering control
        main.add(createSettingsGroup("Client Performance",
                createCheck("Disable Rendering", e -> {
                    boolean sel = ((JCheckBox)e.getSource()).isSelected();
                    Client.setRenderingDisabled(sel);
                }),
                createCheck("Draw Mouse", e -> {
                    boolean sel = ((JCheckBox)e.getSource()).isSelected();
                    Client.getInstance().setDrawMouse(sel);
                }),
                createCheck("Cap FPS (30)", e -> {
                    // Client.getInstance().setFps(30); // Uncomment if supported in your version
                })
        ));

        return main;
    }

    private JPanel createSettingsGroup(String title, Component... comps) {
        JPanel p = new JPanel(new GridLayout(0, 1, 5, 5));
        p.setBackground(PANEL_SURFACE);
        TitledBorder b = BorderFactory.createTitledBorder(new LineBorder(BORDER_DIM), title);
        b.setTitleColor(ACCENT_BLOOD);
        p.setBorder(BorderFactory.createCompoundBorder(b, new EmptyBorder(10, 10, 10, 10)));
        for (Component c : comps) p.add(c);
        return p;
    }

    // Standard Checkbox (Starts unchecked)
    private JCheckBox createCheck(String text, ActionListener l) {
        JCheckBox c = new JCheckBox(text);
        c.setForeground(TEXT_MAIN);
        c.setOpaque(false);
        if (l != null) c.addActionListener(l);
        return c;
    }

    // Setting Checkbox (Starts based on actual game state)
    private JCheckBox createSettingCheck(String text, boolean initialState, ActionListener l) {
        JCheckBox c = new JCheckBox(text);
        c.setForeground(TEXT_MAIN);
        c.setOpaque(false);
        c.setSelected(initialState);
        if (l != null) c.addActionListener(l);
        return c;
    }

    private void styleHeaderLabel(JLabel l) {
        l.setForeground(TEXT_MAIN);
        l.setFont(new Font("Consolas", Font.BOLD, 15));
        l.setHorizontalAlignment(SwingConstants.RIGHT);
    }

    private void styleSpinner(JSpinner s) {
        s.setPreferredSize(new Dimension(55, 24));
        JFormattedTextField field = ((JSpinner.DefaultEditor) s.getEditor()).getTextField();
        field.setBackground(new Color(30, 30, 30));
        field.setForeground(ACCENT_BLOOD);
        s.setBorder(new LineBorder(BORDER_DIM));
    }

    private ImageIcon loadMiscIcon(String name) {
        try {
            return new ImageIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/resources/icons/misc/" + name + ".png"))).getImage().getScaledInstance(18, 18, Image.SCALE_SMOOTH));
        } catch (Exception e) { return null; }
    }

    private ImageIcon loadSkillIcon(Skill skill) {
        try {
            return new ImageIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/resources/icons/skills/" + skill.name().toLowerCase() + "_icon.png"))).getImage().getScaledInstance(26, 26, Image.SCALE_SMOOTH));
        } catch (Exception e) { return new ImageIcon(); }
    }

    private JPanel createSkillTile(SkillData data) {
        JPanel tile = new JPanel(new GridBagLayout());
        tile.setBackground(PANEL_SURFACE);
        tile.setBorder(new LineBorder(BORDER_DIM));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0; gbc.gridx = 0;

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel icon = new JLabel(loadSkillIcon(data.skill));
        data.lblLevel.setForeground(ACCENT_BLOOD);
        data.lblLevel.setFont(new Font("Arial", Font.BOLD, 18));
        top.add(icon, BorderLayout.WEST); top.add(data.lblLevel, BorderLayout.EAST);
        top.setBorder(new EmptyBorder(5, 12, 0, 12));

        data.lblXpString.setForeground(TEXT_DIM);
        data.lblXpString.setFont(new Font("Monospaced", Font.PLAIN, 10));
        data.lblXpString.setHorizontalAlignment(SwingConstants.CENTER);

        data.mainBar.setPreferredSize(new Dimension(0, 6));
        data.mainBar.setForeground(ACCENT_BLOOD);
        data.mainBar.setBackground(Color.BLACK);
        data.mainBar.setBorder(null);

        gbc.gridy = 0; tile.add(top, gbc);
        gbc.gridy = 1; tile.add(data.lblXpString, gbc);
        gbc.gridy = 2; gbc.insets = new Insets(2, 12, 10, 12); tile.add(data.mainBar, gbc);

        tile.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                data.isTracking = !data.isTracking;
                tile.setBorder(new LineBorder(data.isTracking ? ACCENT_BLOOD : BORDER_DIM, 1));
                refreshTrackerList();
            }
        });
        return tile;
    }

    private void refreshTrackerList() {
        trackerList.removeAll();
        skillRegistry.values().stream().filter(d -> d.isTracking).forEach(d -> {
            trackerList.add(d.trackerPanel);
            trackerList.add(Box.createRigidArea(new Dimension(0, 10)));
        });
        trackerList.add(Box.createVerticalGlue());
        trackerList.revalidate(); trackerList.repaint();
    }

    private void updateAll() {
        int projH = (int) projectionSpinner.getValue();
        long tXp = 0; int tLg = 0;
        int p2pTotal = 0; int f2pTotal = 0;

        for (SkillData data : skillRegistry.values()) {
            int xp = Skills.getExperience(data.skill);
            int lvl = Skills.getRealLevel(data.skill);
            data.update(xp, lvl, startTime, projH);

            p2pTotal += lvl;
            if (F2P_SKILLS.contains(data.skill)) f2pTotal += lvl;

            if (data.isTracking) {
                tXp += Math.max(0, (xp - data.startXP));
                tLg += Math.max(0, (lvl - data.startLevel));
            }
        }
        totalXpGainedLabel.setText("Total XP Gained: " + String.format("%,d", tXp));
        totalLevelsGainedLabel.setText("Total Levels Gained: " + tLg);
        totalLevelLabel.setText(String.format("F2P Total: %d | P2P Total: %d", f2pTotal, p2pTotal));
    }

    private class SkillData {
        final Skill skill;
        final JProgressBar mainBar = new JProgressBar(0, 100);
        final JLabel lblLevel = new JLabel("1"), lblXpString = new JLabel("0/0");
        final JPanel trackerPanel = new JPanel(new GridLayout(0, 1, 2, 2));
        final JLabel lblGained = new JLabel(), lblPerHour = new JLabel(), lblRemaining = new JLabel(), lblTTL = new JLabel(), lblProj = new JLabel(), lblActs = new JLabel();
        final int startXP, startLevel;
        boolean isTracking = false;

        SkillData(Skill s) {
            this.skill = s;
            this.startXP = Skills.getExperience(s);
            this.startLevel = Skills.getRealLevel(s);
            trackerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 165));
            trackerPanel.setBackground(new Color(30, 30, 30));
            TitledBorder b = BorderFactory.createTitledBorder(new LineBorder(BORDER_DIM), " " + s.name() + " ");
            b.setTitleColor(ACCENT_BLOOD);
            trackerPanel.setBorder(b);
            JLabel[] ls = {lblGained, lblPerHour, lblRemaining, lblActs, lblTTL, lblProj};
            for (JLabel l : ls) {
                l.setForeground(TEXT_MAIN);
                l.setFont(new Font("Consolas", Font.PLAIN, 12));
                l.setBorder(new EmptyBorder(0, 15, 0, 0));
                trackerPanel.add(l);
            }
        }

        void update(int curXp, int curLvl, long start, int ph) {
            int curMin = Skills.getExperienceForLevel(curLvl), curMax = Skills.getExperienceForLevel(curLvl + 1);
            lblLevel.setText(String.valueOf(curLvl));
            lblXpString.setText(String.format("%,d / %,d XP", curXp, curMax));
            mainBar.setValue((int) (((double)(curXp - curMin) / Math.max(1, curMax - curMin)) * 100));
            long elapsed = System.currentTimeMillis() - start;
            int xph = (int) (Math.max(0, curXp - startXP) / Math.max(0.0001, elapsed / 3600000.0));
            int rem = Math.max(0, curMax - curXp);
            lblGained.setText(" GAINED:    " + String.format("%,d XP", curXp - startXP));
            lblPerHour.setText(" XP/HR:     " + String.format("%,d", xph));
            lblRemaining.setText(" TO LEVEL:  " + String.format("%,d XP", rem));
            lblActs.setText(" EST. ACTS: " + String.format("%,d", rem / 100));
            if (xph > 0) {
                lblTTL.setText(String.format(" TIME TO L: %.2f hrs", (double) rem / xph));
                int pXp = curXp + (xph * ph); int pLvl = curLvl;
                while (pLvl < 126 && Skills.getExperienceForLevel(pLvl + 1) <= pXp) pLvl++;
                lblProj.setText(String.format(" PROJ (%dH): Lvl %d", ph, pLvl));
                lblProj.setForeground(new Color(100, 255, 100));
            } else {
                lblTTL.setText(" TIME TO L: --:--");
                lblProj.setText(" FORECAST: STATIONARY");
                lblProj.setForeground(TEXT_DIM);
            }
        }
    }
}