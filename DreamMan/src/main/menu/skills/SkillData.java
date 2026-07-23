package main.menu.skills;

import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.methods.skills.Skills;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

import static main.menu.MenuHandler.*;

public class SkillData {
    private final Skill skill;

    final JPanel trackerPanel = new JPanel(new GridLayout(0, 1, 2, 2));

    final JProgressBar progressLevel = new JProgressBar(0, 100);
    final JLabel labelLevel = new JLabel("1"),
            labelXP = new JLabel("0/0");

    /** v1.88: the overlay-style header line (skill icon + name). */
    final JLabel lblIcon = new JLabel();
    final JLabel lblName = new JLabel();

    final JLabel lblGained = new JLabel(),
            lblPerHour = new JLabel(),
            lblRemaining = new JLabel(),
            lblTTL = new JLabel(),
            lblProj = new JLabel(),
            lblActs = new JLabel();

    // Patch B.6: the tracking baseline. Set at construction to the login state, but rebaseable
    // via reset() to "start from now" (current level/xp, xp/hr counter restarted). No longer
    // final so a menu Reset can restart all trackers without recreating them.
    int startXP, startLevel;
    private long baseTime = System.currentTimeMillis();

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

    public void setIcon(javax.swing.ImageIcon i) {
        this.icon = i;
        // v1.88: the details card wears the same icon the tile and the overlay do
        if (lblIcon != null && i != null) {
            java.awt.Image im = i.getImage();
            if (im != null)
                lblIcon.setIcon(new javax.swing.ImageIcon(
                        im.getScaledInstance(14, 14, java.awt.Image.SCALE_SMOOTH)));
        }
    }
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
        // ── v1.88: the tracked-details card, rebuilt to read like the in-game overlay ──
        // It used to be a titled box of ALL-CAPS "GAINED: / XP/HR: / TO LEVEL:" lines stacked
        // in a GridLayout, which stretched one skill down half the panel and looked nothing
        // like the card the same data draws on the game canvas. Now it's the same shape as the
        // overlay: icon + name + level badge on one line, the progress bar under it, then two
        // tight stat rows. Same numbers, a third of the height, and the two surfaces finally
        // match each other.
        trackerPanel.setLayout(new BorderLayout(0, 0));
        trackerPanel.setBackground(new Color(0x16, 0x16, 0x16));
        trackerPanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_BORDER_DIM), new EmptyBorder(6, 8, 6, 8)));
        trackerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));

        // top line: icon · NAME · Lvl badge
        lblIcon.setBorder(new EmptyBorder(0, 0, 0, 6));
        lblName.setText(s.name());
        lblName.setForeground(COLOR_BLOOD);
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 12));
        labelLevel.setForeground(TEXT_MAIN);
        labelLevel.setFont(new Font("Consolas", Font.BOLD, 12));
        labelLevel.setHorizontalAlignment(SwingConstants.RIGHT);
        JPanel top = new JPanel(new BorderLayout(0, 0));
        top.setOpaque(false);
        JPanel topLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        topLeft.setOpaque(false);
        topLeft.add(lblIcon);
        topLeft.add(lblName);
        top.add(topLeft, BorderLayout.WEST);
        top.add(labelLevel, BorderLayout.EAST);

        progressLevel.setUI(new main.menu.Theme.RoundProgressBarUI());
        progressLevel.setPreferredSize(new Dimension(10, 6));
        progressLevel.setStringPainted(false);
        progressLevel.setBorder(new EmptyBorder(4, 0, 4, 0));

        // stat row 1: "To lvl 748 · 1.8h"      right: "+400"
        // stat row 2: "423 xp/hr"              right: goal % when a goal is set
        lblRemaining.setForeground(new Color(0xB8, 0xB8, 0xB8));
        lblGained.setForeground(COLOR_BLOOD);
        lblPerHour.setForeground(new Color(0xC8, 0xA8, 0x50));
        lblGoal.setForeground(new Color(230, 190, 90));
        for (JLabel l : new JLabel[]{lblRemaining, lblGained, lblPerHour, lblGoal})
            l.setFont(new Font("Consolas", Font.PLAIN, 11));
        lblGained.setHorizontalAlignment(SwingConstants.RIGHT);
        lblGoal.setHorizontalAlignment(SwingConstants.RIGHT);

        JPanel row1 = new JPanel(new BorderLayout(6, 0));
        row1.setOpaque(false);
        row1.add(lblRemaining, BorderLayout.CENTER);
        row1.add(lblGained, BorderLayout.EAST);

        JPanel row2 = new JPanel(new BorderLayout(6, 0));
        row2.setOpaque(false);
        row2.add(lblPerHour, BorderLayout.CENTER);
        row2.add(lblGoal, BorderLayout.EAST);

        goalBar.setPreferredSize(new Dimension(10, 5));
        goalBar.setStringPainted(false);
        goalBar.setVisible(false);
        lblGoal.setVisible(false);

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        for (JComponent c : new JComponent[]{top, progressLevel, row1, row2, goalBar})
            c.setAlignmentX(0f);
        stack.add(top);
        stack.add(progressLevel);
        stack.add(row1);
        stack.add(row2);
        stack.add(goalBar);
        trackerPanel.add(stack, BorderLayout.CENTER);

        // v1.88: the projection line ("PROJ (24H)") is dropped from the card. It only ever had
        // a value once xp/hr had settled, it was the widest line in the box, and it's a
        // curiosity rather than something you steer by. lblProj/lblActs stay as fields so
        // update() keeps working untouched - they're simply not shown.
    }

    /** v1.88: "5.5k" / "1.2m" - the same shorthand the canvas overlay uses. */
    private static String compactXp(long n) {
        if (n >= 1_000_000) return String.format("%.1fm", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fk", n / 1_000.0);
        return String.valueOf(n);
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

    /**
     * Restarts this tracker from the CURRENT state (Patch B.6): the "start" level and XP become
     * the current ones, gained resets to 0, and the xp/hr window restarts from now. Used by the
     * menu's "Reset trackers" button - like closing and reopening the session at this moment,
     * without losing your tracked-skill selection or goals.
     */
    public void reset(int curXp, int curLvl) {
        this.startXP = curXp;
        this.startLevel = curLvl;
        this.baseTime = System.currentTimeMillis();
        this.lastGainedXp = 0;
        this.lastXpPerHour = 0;
        this.lastLevel = curLvl;
        this.lastXp = curXp;
        this.lastRemainingXp = 0;
        this.lastTtlHours = -1;
        this.lastLevelFrac = 0;
    }

    /** Overload used when the client can be read directly. */
    public void reset() {
        try { reset(Skills.getExperience(skill), Skills.getRealLevel(skill)); }
        catch (Throwable t) { reset(startXP, startLevel); }
    }

    /** Directly sets tracking (Patch B.6 - persistence restores the saved set). */
    public void setTracking(boolean t) { this.isTracking = t; }

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
        // Patch B.6: xp/hr and "gained" are measured from THIS skill's own baseline, so a reset
        // restarts the rate cleanly (the shared script start-time is ignored here on purpose).
        long elapsed = System.currentTimeMillis() - baseTime;
        int xph = (int) (Math.max(0, curXp - startXP) / Math.max(0.0001, elapsed / 3600000.0));
        lastLevel = curLvl;
        lastGainedXp = Math.max(0, curXp - startXP);
        lastXpPerHour = xph;
        int rem = Math.max(0, curMax - curXp);
        lastLevelFrac = Math.max(0, Math.min(1, (double) (curXp - curMin) / Math.max(1, curMax - curMin)));
        lastRemainingXp = rem;
        lastTtlHours = xph > 0 ? (double) rem / xph : -1;
        // v1.88: overlay-shaped strings - "To lvl 748 · 1.8h", "+400", "423 xp/hr"
        lblGained.setText("+" + String.format("%,d", curXp - startXP));
        lblPerHour.setText(String.format("%,d xp/hr", xph));
        lblRemaining.setText("To lvl " + compactXp(rem) + " \u00b7 "
                + (xph > 0 ? String.format("%.1fh", (double) rem / xph) : "-"));
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
            lblGoal.setText(String.format("goal %.0f%%", f * 100));
            goalBar.setValue((int) (f * 1000));
            goalRemaining = Math.max(0, goalXp - curXp);
            goalTtlHours = lastXpPerHour > 0 ? (double) goalRemaining / lastXpPerHour : -1;
        } else {
            goalRemaining = 0;
            goalTtlHours = -1;
        }
    }
}