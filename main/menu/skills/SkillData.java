package main.menu.skills;

import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.methods.skills.Skills;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

import static main.menu.MenuHandler.*;

public class SkillData {
    private final Skill skill;

    final JPanel trackerPanel = new JPanel(new GridLayout(0, 1, 2, 2));

    final JProgressBar progressLevel = new JProgressBar(0, 100);
    final JLabel labelLevel = new JLabel("1"),
            labelXP = new JLabel("0/0");

    final JLabel lblGained = new JLabel(),
            lblPerHour = new JLabel(),
            lblRemaining = new JLabel(),
            lblTTL = new JLabel(),
            lblProj = new JLabel(),
            lblActs = new JLabel();

    final int startXP, startLevel;

    boolean isTracking = false;

    /**
     * Constructs a new {@link SkillData} object, used by the Skill tiles to track player stats.
     *
     * @param s The skill to retri
     */
    public SkillData(Skill s) {
        this.skill = s;
        this.startXP = Skills.getExperience(s);
        this.startLevel = Skills.getRealLevel(s);
        trackerPanel.setBackground(new Color(30, 30, 30));
        TitledBorder b = BorderFactory.createTitledBorder(new LineBorder(COLOR_BORDER_DIM), " " + s.name() + " ");
        b.setTitleColor(COLOR_BLOOD);
        trackerPanel.setBorder(b);
        JLabel[] ls = {lblGained, lblPerHour, lblRemaining, lblActs, lblTTL, lblProj};

        for (JLabel l : ls) {
            l.setForeground(TEXT_MAIN);
            l.setFont(new Font("Consolas", Font.PLAIN, 12));
            trackerPanel.add(l);
        }
    }

    public Skill getSkill() {
        return skill;
    }

    public boolean isTracking() {
        return isTracking;
    }

    public int getStartXP() {
        return startXP;
    }

    public int getStartLevel() {
        return startLevel;
    }

    public void toggleTracking() {
        isTracking = !isTracking;
    }

    public JLabel getLabelLevel() {
        return labelLevel;
    }

    public JLabel getLabelXP() {
        return labelXP;
    }

    public JPanel getTrackerPanel() {
        return trackerPanel;
    }

    public void update(int curXp, int curLvl, long start, int ph) {
        int curMin = Skills.getExperienceForLevel(curLvl), curMax = Skills.getExperienceForLevel(curLvl + 1);
        labelLevel.setText(String.valueOf(curLvl));
        labelXP.setText(String.format("%,d / %,d XP", curXp, curMax));
        progressLevel.setValue((int) (((double)(curXp - curMin) / Math.max(1, curMax - curMin)) * 100));
        long elapsed = System.currentTimeMillis() - start;
        int xph = (int) (Math.max(0, curXp - startXP) / Math.max(0.0001, elapsed / 3600000.0));
        int rem = Math.max(0, curMax - curXp);
        lblGained.setText(" GAINED: " + String.format("%,d XP", curXp - startXP));
        lblPerHour.setText(" XP/HR:  " + String.format("%,d", xph));
        lblRemaining.setText(" TO LEVEL: " + String.format("%,d", rem));
        if (xph > 0) {
            lblTTL.setText(String.format(" TIME TO L: %.2f hrs", (double) rem / xph));
            lblProj.setText(String.format(" PROJ (%dH): Lvl %d", ph, curLvl + (xph * ph / 100000)));
        }
    }
}