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

public class SkillsTabBackup extends JFrame {

    private final Map<Skill, SkillData> skillRegistry = new EnumMap<>(Skill.class);
    private final JPanel trackerList;
    private final JLabel totalXpGainedLabel = new JLabel("SESSION XP: 0");
    private final JLabel totalLevelsGainedLabel = new JLabel("LEVELS GAINED: 0");
    private final long startTime;

    // Pro Theme Palette
    private final Color BG_BASE = new Color(12, 12, 12);
    private final Color PANEL_SURFACE = new Color(22, 22, 22);
    private final Color BLOOD_RED = new Color(190, 0, 0);
    private final Color DARK_RED = new Color(80, 0, 0);
    private final Color TEXT_MAIN = new Color(230, 230, 230);
    private final Color TEXT_DIM = new Color(150, 150, 150);

    public SkillsTabBackup() {
        this.startTime = System.currentTimeMillis();
        setTitle("ETAbot | Professional Analytics Dashboard");
        setSize(1100, 750);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(BG_BASE);

        // --- TOP HEADER ---
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PANEL_SURFACE);
        header.setPreferredSize(new Dimension(0, 80));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 3, 0, BLOOD_RED));

        JLabel titleLabel = new JLabel(" ETAbot", SwingConstants.LEFT);
        titleLabel.setForeground(BLOOD_RED);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        titleLabel.setBorder(new EmptyBorder(0, 25, 0, 0));

        JPanel statsContainer = new JPanel(new GridLayout(2, 1));
        statsContainer.setOpaque(false);
        statsContainer.setBorder(new EmptyBorder(15, 0, 15, 30));
        styleGlobalLabel(totalXpGainedLabel);
        styleGlobalLabel(totalLevelsGainedLabel);
        statsContainer.add(totalXpGainedLabel);
        statsContainer.add(totalLevelsGainedLabel);

        header.add(titleLabel, BorderLayout.WEST);
        header.add(statsContainer, BorderLayout.EAST);

        // --- CENTER: Skills Grid ---
        JPanel gridContainer = new JPanel(new GridLayout(0, 3, 4, 4));
        gridContainer.setBackground(BG_BASE);
        gridContainer.setBorder(new EmptyBorder(10, 10, 10, 10));

        for (Skill skill : Skill.values()) {
            SkillData data = new SkillData(skill);
            skillRegistry.put(skill, data);
            gridContainer.add(createSkillTile(data));
        }

        // --- RIGHT: Live Tracker ---
        JPanel sidePanel = new JPanel(new BorderLayout());
        sidePanel.setPreferredSize(new Dimension(340, 0));
        sidePanel.setBackground(PANEL_SURFACE);
        sidePanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(45, 45, 45)));

        JLabel trackerHeader = new JLabel("LIVE ANALYTICS", SwingConstants.CENTER);
        trackerHeader.setForeground(TEXT_MAIN);
        trackerHeader.setFont(new Font("Segoe UI", Font.BOLD, 14));
        trackerHeader.setBorder(new EmptyBorder(15, 0, 15, 0));

        trackerList = new JPanel();
        trackerList.setLayout(new BoxLayout(trackerList, BoxLayout.Y_AXIS));
        trackerList.setBackground(PANEL_SURFACE);

        JScrollPane sideScroll = new JScrollPane(trackerList);
        sideScroll.setBorder(null);
        sideScroll.getVerticalScrollBar().setUnitIncrement(16);
        sideScroll.getViewport().setBackground(PANEL_SURFACE);

        sidePanel.add(trackerHeader, BorderLayout.NORTH);
        sidePanel.add(sideScroll, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);
        add(gridContainer, BorderLayout.CENTER);
        add(sidePanel, BorderLayout.EAST);

        setVisible(true);
        new Timer(1000, e -> updateAll()).start();
    }

    private void styleGlobalLabel(JLabel l) {
        l.setForeground(Color.WHITE);
        l.setFont(new Font("Consolas", Font.BOLD, 14));
        l.setHorizontalAlignment(SwingConstants.RIGHT);
    }

    private JPanel createSkillTile(SkillData data) {
        JPanel tile = new JPanel(new GridBagLayout());
        tile.setBackground(PANEL_SURFACE);
        tile.setBorder(new LineBorder(new Color(40, 40, 40)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.weightx = 1.0;

        // Level + Icon Row
        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        JLabel icon = new JLabel(loadIcon(data.skill));
        data.lblLevel.setForeground(BLOOD_RED);
        data.lblLevel.setFont(new Font("Arial", Font.BOLD, 16));
        topRow.add(icon, BorderLayout.WEST);
        topRow.add(data.lblLevel, BorderLayout.EAST);
        topRow.setBorder(new EmptyBorder(5, 10, 0, 10));

        // XP Text (Current / Total)
        data.lblXpString.setForeground(TEXT_DIM);
        data.lblXpString.setFont(new Font("Monospaced", Font.PLAIN, 10));
        data.lblXpString.setHorizontalAlignment(SwingConstants.CENTER);

        // Progress Bar
        data.mainBar.setPreferredSize(new Dimension(0, 12));
        data.mainBar.setForeground(BLOOD_RED);
        data.mainBar.setBackground(new Color(40, 0, 0));
        data.mainBar.setBorder(new LineBorder(Color.BLACK));
        data.mainBar.setStringPainted(false);

        gbc.gridy = 0; tile.add(topRow, gbc);
        gbc.gridy = 1; tile.add(data.lblXpString, gbc);
        gbc.gridy = 2; gbc.insets = new Insets(2, 10, 10, 10); tile.add(data.mainBar, gbc);

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
            trackerList.add(Box.createRigidArea(new Dimension(0, 8)));
        });
        trackerList.add(Box.createVerticalGlue());
        trackerList.revalidate(); trackerList.repaint();
    }

    private void updateAll() {
        long totalGained = 0; int totalLevels = 0;
        for (SkillData data : skillRegistry.values()) {
            int xp = Skills.getExperience(data.skill);
            int lvl = Skills.getRealLevel(data.skill);
            data.update(xp, lvl, startTime);
            if (data.isTracking) {
                totalGained += Math.max(0, (xp - data.startXP));
                totalLevels += Math.max(0, (lvl - data.startLevel));
            }
        }
        totalXpGainedLabel.setText("SESSION XP: " + String.format("%,d", totalGained));
        totalLevelsGainedLabel.setText("LEVELS GAINED: " + totalLevels);
    }

    private ImageIcon loadIcon(Skill skill) {
        try {
            String path = "/resources/icons/skills/" + skill.name().toLowerCase() + "_icon.png";
            ImageIcon img = new ImageIcon(Objects.requireNonNull(getClass().getResource(path)));
            return new ImageIcon(img.getImage().getScaledInstance(24, 24, Image.SCALE_SMOOTH));
        } catch (Exception e) { return new ImageIcon(); }
    }

    private class SkillData {
        final Skill skill;
        final JProgressBar mainBar = new JProgressBar(0, 100);
        final JLabel lblLevel = new JLabel("1");
        final JLabel lblXpString = new JLabel("0 / 0");

        final JPanel trackerPanel = new JPanel(new GridLayout(0, 1, 2, 2));
        final JLabel lblGained = new JLabel();
        final JLabel lblPerHour = new JLabel();
        final JLabel lblRemaining = new JLabel(); // XP until Level Up
        final JLabel lblTTL = new JLabel();

        final int startXP, startLevel;
        boolean isTracking = false;

        SkillData(Skill s) {
            this.skill = s;
            this.startXP = Skills.getExperience(s);
            this.startLevel = Skills.getRealLevel(s);

            trackerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
            trackerPanel.setBackground(new Color(30, 30, 30));
            TitledBorder b = BorderFactory.createTitledBorder(new LineBorder(BLOOD_RED), s.name());
            b.setTitleColor(Color.WHITE);
            trackerPanel.setBorder(b);

            setupTrackerLabel(lblGained); setupTrackerLabel(lblPerHour);
            setupTrackerLabel(lblRemaining); setupTrackerLabel(lblTTL);

            trackerPanel.add(lblGained); trackerPanel.add(lblPerHour);
            trackerPanel.add(lblRemaining); trackerPanel.add(lblTTL);
        }

        private void setupTrackerLabel(JLabel l) {
            l.setForeground(new Color(200, 200, 200));
            l.setFont(new Font("Consolas", Font.PLAIN, 12));
            l.setBorder(new EmptyBorder(0, 12, 0, 0));
        }

        void update(int curXp, int curLvl, long sessionStartTime) {
            int xpForCur = Skills.getExperienceForLevel(curLvl);
            int xpForNext = Skills.getExperienceForLevel(curLvl + 1);
            int remaining = xpForNext - curXp;
            int gained = Math.max(0, curXp - startXP);

            // Grid Tile Updates
            lblLevel.setText(String.valueOf(curLvl));
            lblXpString.setText(String.format("%,d / %,d XP", curXp, xpForNext));
            mainBar.setValue((int) (((double)(curXp - xpForCur) / Math.max(1, xpForNext - xpForCur)) * 100));

            // Tracker Panel Updates
            long elapsed = System.currentTimeMillis() - sessionStartTime;
            int xpPerHour = (int) (gained / Math.max(0.0001, elapsed / 3600000.0));

            lblGained.setText(" GAINED: " + String.format("%,d XP", gained));
            lblPerHour.setText(" XP/HR:  " + String.format("%,d", xpPerHour));
            lblRemaining.setText(" TO LVL: " + String.format("%,d XP", remaining));

            if (xpPerHour > 0) {
                lblTTL.setText(String.format(" TTL:    %.2f hrs", (double) remaining / xpPerHour));
            } else {
                lblTTL.setText(" TTL:    --:--");
            }
        }
    }
}