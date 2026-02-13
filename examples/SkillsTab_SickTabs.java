package examples;

import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.methods.skills.Skills;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public class SkillsTab_SickTabs extends JFrame {

    private final Map<Skill, SkillData> skillRegistry = new EnumMap<>(Skill.class);
    private final JPanel trackerList;
    private final JPanel sidePanel;
    private final JLabel totalXpGainedLabel = new JLabel("Total XP Gained: 0");
    private final JLabel totalLevelsGainedLabel = new JLabel("Total Levels Gained: 0");
    private final JLabel bottomTotalLevelLabel = new JLabel("Total Level: 0", SwingConstants.CENTER);
    private final JSpinner projectionSpinner;
    private final long startTime;

    // Official OSRS Grid Order
    static final Skill[] OSRS_ORDER = {
            Skill.ATTACK, Skill.HITPOINTS, Skill.MINING,
            Skill.STRENGTH, Skill.AGILITY, Skill.SMITHING,
            Skill.DEFENCE, Skill.HERBLORE, Skill.FISHING,
            Skill.RANGED, Skill.THIEVING, Skill.COOKING,
            Skill.PRAYER, Skill.CRAFTING, Skill.FIREMAKING,
            Skill.MAGIC, Skill.FLETCHING, Skill.WOODCUTTING,
            Skill.RUNECRAFTING, Skill.SLAYER, Skill.FARMING,
            Skill.CONSTRUCTION, Skill.HUNTER, Skill.SAILING
    };

    private final Color BG_BASE = new Color(10, 10, 10);
    private final Color PANEL_SURFACE = new Color(20, 20, 20);
    private final Color BLOOD_RED = new Color(180, 0, 0);
    private final Color TEXT_MAIN = new Color(220, 220, 220);
    private final Color TEXT_DIM = new Color(140, 140, 140);

    public SkillsTab_SickTabs() {
        this.startTime = System.currentTimeMillis();
        setTitle("ETAbot | Nexus Client");
        setSize(1300, 850);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(BG_BASE);

        // --- HEADER ---
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PANEL_SURFACE);
        header.setPreferredSize(new Dimension(0, 85));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, BLOOD_RED));

        JLabel titleLabel = new JLabel(" ETAbot", SwingConstants.LEFT);
        titleLabel.setForeground(BLOOD_RED);
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

        // --- TABBED PANE ---
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(PANEL_SURFACE);
        tabbedPane.setForeground(TEXT_MAIN);

        // Skills Tracker Tab Construction
        JPanel skillsMainPanel = new JPanel(new BorderLayout());
        skillsMainPanel.setBackground(BG_BASE);

        JPanel gridContainer = new JPanel(new GridLayout(0, 3, 4, 4));
        gridContainer.setBackground(BG_BASE);
        gridContainer.setBorder(new EmptyBorder(10, 10, 10, 10));

        for (Skill skill : OSRS_ORDER) {
            SkillData data = new SkillData(skill);
            skillRegistry.put(skill, data);
            gridContainer.add(createSkillTile(data));
        }

        // Bottom Total Level Bar
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBackground(PANEL_SURFACE);
        bottomBar.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, BLOOD_RED));
        bottomTotalLevelLabel.setForeground(Color.WHITE);
        bottomTotalLevelLabel.setFont(new Font("Consolas", Font.BOLD, 18));
        bottomTotalLevelLabel.setBorder(new EmptyBorder(10, 0, 10, 0));
        bottomBar.add(bottomTotalLevelLabel, BorderLayout.CENTER);

        skillsMainPanel.add(new JScrollPane(gridContainer), BorderLayout.CENTER);
        skillsMainPanel.add(bottomBar, BorderLayout.SOUTH);

        // Add Skills Tab with Stats Icon
        tabbedPane.addTab("Skill Tracker", loadMiscIcon("Stats_icon"), skillsMainPanel);
        tabbedPane.addTab("Settings", null, new JPanel());

        // --- SIDE TRACKER ---
        sidePanel = new JPanel(new BorderLayout());
        sidePanel.setPreferredSize(new Dimension(380, 0));
        sidePanel.setBackground(PANEL_SURFACE);
        sidePanel.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, BLOOD_RED));

        JPanel sideHeader = new JPanel();
        sideHeader.setLayout(new BoxLayout(sideHeader, BoxLayout.Y_AXIS));
        sideHeader.setOpaque(false);
        sideHeader.setBorder(new EmptyBorder(15, 15, 15, 15));

        JLabel sideTitle = new JLabel("LIVE TRACKER");
        sideTitle.setForeground(Color.WHITE);
        sideTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));

        JPanel forecastRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 10));
        forecastRow.setOpaque(false);
        projectionSpinner = new JSpinner(new SpinnerNumberModel(24, 1, 999, 1));
        styleSpinner(projectionSpinner);
        forecastRow.add(new JLabel("Forecast: "));
        forecastRow.add(projectionSpinner);
        forecastRow.add(new JLabel(" hrs"));

        sideHeader.add(sideTitle);
        sideHeader.add(forecastRow);

        trackerList = new JPanel();
        trackerList.setLayout(new BoxLayout(trackerList, BoxLayout.Y_AXIS));
        trackerList.setBackground(PANEL_SURFACE);

        JScrollPane sideScroll = new JScrollPane(trackerList);
        sideScroll.setBorder(null);
        sideScroll.getViewport().setBackground(PANEL_SURFACE);

        sidePanel.add(sideHeader, BorderLayout.NORTH);
        sidePanel.add(sideScroll, BorderLayout.CENTER);

        JButton toggleBtn = new JButton(">");
        toggleBtn.setPreferredSize(new Dimension(20, 0));
        toggleBtn.setBackground(PANEL_SURFACE);
        toggleBtn.setForeground(BLOOD_RED);
        toggleBtn.setFocusPainted(false);
        toggleBtn.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, BLOOD_RED));
        toggleBtn.addActionListener(e -> {
            boolean visible = sidePanel.isVisible();
            sidePanel.setVisible(!visible);
            toggleBtn.setText(visible ? "<" : ">");
            revalidate();
        });

        add(header, BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);
        add(toggleBtn, BorderLayout.WEST);
        add(sidePanel, BorderLayout.EAST);

        setVisible(true);
        new Timer(1000, e -> SwingUtilities.invokeLater(this::updateAll)).start();
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
        field.setForeground(BLOOD_RED);
        s.setBorder(new LineBorder(BLOOD_RED));
    }

    private ImageIcon loadMiscIcon(String name) {
        try {
            String path = "/resources/icons/misc/" + name + ".png";
            return new ImageIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource(path))).getImage().getScaledInstance(18, 18, Image.SCALE_SMOOTH));
        } catch (Exception e) { return null; }
    }

    private JPanel createSkillTile(SkillData data) {
        JPanel tile = new JPanel(new GridBagLayout());
        tile.setBackground(PANEL_SURFACE);
        tile.setBorder(new LineBorder(new Color(40, 40, 40)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0; gbc.gridx = 0;

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel icon = new JLabel(loadSkillIcon(data.skill));
        data.lblLevel.setForeground(BLOOD_RED);
        data.lblLevel.setFont(new Font("Arial", Font.BOLD, 18));
        top.add(icon, BorderLayout.WEST); top.add(data.lblLevel, BorderLayout.EAST);
        top.setBorder(new EmptyBorder(5, 12, 0, 12));

        data.lblXpString.setForeground(TEXT_DIM);
        data.lblXpString.setFont(new Font("Monospaced", Font.PLAIN, 10));
        data.lblXpString.setHorizontalAlignment(SwingConstants.CENTER);

        data.mainBar.setPreferredSize(new Dimension(0, 8));
        data.mainBar.setForeground(BLOOD_RED);
        data.mainBar.setBackground(new Color(40, 0, 0));
        data.mainBar.setBorder(null);

        gbc.gridy = 0; tile.add(top, gbc);
        gbc.gridy = 1; tile.add(data.lblXpString, gbc);
        gbc.gridy = 2; gbc.insets = new Insets(2, 12, 12, 12); tile.add(data.mainBar, gbc);

        tile.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                data.isTracking = !data.isTracking;
                tile.setBorder(new LineBorder(data.isTracking ? BLOOD_RED : new Color(40, 40, 40), data.isTracking ? 2 : 1));
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
        long totalXp = 0; int totalLvlGained = 0;
        for (SkillData data : skillRegistry.values()) {
            int xp = Skills.getExperience(data.skill);
            int lvl = Skills.getRealLevel(data.skill);
            data.update(xp, lvl, startTime, projH);
            if (data.isTracking) {
                totalXp += Math.max(0, (xp - data.startXP));
                totalLvlGained += Math.max(0, (lvl - data.startLevel));
            }
        }
        totalXpGainedLabel.setText("Total XP Gained: " + String.format("%,d", totalXp));
        totalLevelsGainedLabel.setText("Total Levels Gained: " + totalLvlGained);
        bottomTotalLevelLabel.setText("Total Level: " + Skills.getTotalLevel());
    }

    private ImageIcon loadSkillIcon(Skill skill) {
        try {
            String path = "/resources/icons/skills/" + skill.name().toLowerCase() + "_icon.png";
            return new ImageIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource(path))).getImage().getScaledInstance(26, 26, Image.SCALE_SMOOTH));
        } catch (Exception e) { return new ImageIcon(); }
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
            trackerPanel.setBackground(new Color(25, 25, 25));
            TitledBorder b = BorderFactory.createTitledBorder(new LineBorder(BLOOD_RED), " " + s.name() + " ");
            b.setTitleColor(Color.WHITE);
            trackerPanel.setBorder(b);
            JLabel[] ls = {lblGained, lblPerHour, lblRemaining, lblActs, lblTTL, lblProj};
            for (JLabel l : ls) {
                l.setForeground(new Color(190, 190, 190));
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