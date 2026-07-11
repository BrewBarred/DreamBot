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

    // Patch B.2: raw live numbers for the on-canvas overlay cards (updated in update()).
    private volatile int lastLevel, lastGainedXp, lastXpPerHour;
    // Patch B.3: absolute current xp + a per-skill XP goal (0 = none). Set via right-click on
    // the tracker tile; persisted with the profile; shown as a progress bar in tracker + overlay.
    private volatile int lastXp, goalXp;
    // Patch B.5: numbers the on-canvas overlay v2 renders directly (updated in update()).
    private volatile double lastLevelFrac;      // 0..1 within the current level
    private volatile int lastRemainingXp;       // xp to next level
    private volatile double lastTtlHours = -1;  // time to level (-1 = unknown)
    private volatile int goalRemaining;         // xp to goal (0 when no goal)
    private volatile double goalTtlHours = -1;  // time to goal (-1 = unknown)
    /** The same icon the menu's tracker tile shows - drawn on the overlay too (Patch B.5). */
    private transient javax.swing.ImageIcon icon;

    public void setIcon(javax.swing.ImageIcon i) { this.icon = i; }
    public java.awt.Image getIconImage() { return icon == null ? null : icon.getImage(); }
    public double getLevelFraction() { return lastLevelFrac; }
    public int getRemainingXp() { return lastRemainingXp; }
    public double getTtlHours() { return lastTtlHours; }
    public int getGoalRemainingXp() { return goalRemaining; }
    public double getGoalTtlHours() { return goalTtlHours; }

    final JLabel lblGoal = new JLabel();
    final JProgressBar goalBar = new JProgressBar(0, 1000) {
        @Override public void updateUI() { setUI(new main.menu.Theme.RoundProgressBarUI()); }
    };

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

        lblGoal.setForeground(new Color(230, 190, 90));
        lblGoal.setFont(new Font("Consolas", Font.PLAIN, 12));
        goalBar.setPreferredSize(new Dimension(10, 8));
        goalBar.setStringPainted(false);
        lblGoal.setVisible(false);
        goalBar.setVisible(false);
        trackerPanel.add(lblGoal);
        trackerPanel.add(goalBar);
    }

    /** Sets an XP goal (0 clears). Right-click a tracker tile to change it. */
    public void setGoalXp(int xp) { this.goalXp = Math.max(0, xp); }
    public int getGoalXp() { return goalXp; }
    public int getLastXp() { return lastXp > 0 ? lastXp : startXP; }

    /** 0..1 progress toward the goal (0 when no goal). */
    public double getGoalFraction() {
        if (goalXp <= 0) return 0;
        return Math.max(0, Math.min(1, (double) getLastXp() / goalXp));
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

    /** Live values for the canvas overlay (Patch B.2). Zero until the first update() tick. */
    public int getCurrentLevel() { return lastLevel > 0 ? lastLevel : startLevel; }
    public int getGainedXp()     { return lastGainedXp; }
    public int getXpPerHour()    { return lastXpPerHour; }

    public void update(int curXp, int curLvl, long start, int ph) {
        int curMin = Skills.getExperienceForLevel(curLvl), curMax = Skills.getExperienceForLevel(curLvl + 1);
        // Patch B.3: show the login level pointing at the current one when it has risen
        labelLevel.setText(curLvl > startLevel ? startLevel + "\u2192" + curLvl : String.valueOf(curLvl));
        labelXP.setText(String.format("%,d / %,d XP", curXp, curMax));
        progressLevel.setValue((int) (((double)(curXp - curMin) / Math.max(1, curMax - curMin)) * 100));
        long elapsed = System.currentTimeMillis() - start;
        int xph = (int) (Math.max(0, curXp - startXP) / Math.max(0.0001, elapsed / 3600000.0));
        lastLevel = curLvl;
        lastGainedXp = Math.max(0, curXp - startXP);
        lastXpPerHour = xph;
        int rem = Math.max(0, curMax - curXp);
        lastLevelFrac = Math.max(0, Math.min(1, (double) (curXp - curMin) / Math.max(1, curMax - curMin)));
        lastRemainingXp = rem;
        lastTtlHours = xph > 0 ? (double) rem / xph : -1;
        lblGained.setText(" GAINED: " + String.format("%,d XP", curXp - startXP));
        lblPerHour.setText(" XP/HR:  " + String.format("%,d", xph));
        lblRemaining.setText(" TO LEVEL: " + String.format("%,d", rem));
        if (xph > 0) {
            lblTTL.setText(String.format(" TIME TO L: %.2f hrs", (double) rem / xph));
            lblProj.setText(String.format(" PROJ (%dH): Lvl %d", ph, curLvl + (xph * ph / 100000)));
        }

        lastXp = curXp;
        boolean hasGoal = goalXp > 0;
        lblGoal.setVisible(hasGoal);
        goalBar.setVisible(hasGoal);
        if (hasGoal) {
            double f = getGoalFraction();
            lblGoal.setText(String.format(" GOAL: %,d XP \u2014 %.1f%%", goalXp, f * 100));
            goalBar.setValue((int) (f * 1000));
            goalRemaining = Math.max(0, goalXp - curXp);
            goalTtlHours = lastXpPerHour > 0 ? (double) goalRemaining / lastXpPerHour : -1;
        } else {
            goalRemaining = 0;
            goalTtlHours = -1;
        }
    }
}