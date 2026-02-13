//package main;
//
//import org.dreambot.api.methods.skills.Skill;
//import org.dreambot.api.methods.skills.Skills;
//import org.dreambot.api.script.AbstractScript;
//import org.dreambot.api.script.Category;
//import org.dreambot.api.script.ScriptManifest;
//import java.awt.*;
//
//@ScriptManifest(author = "User", name = "Example: On-screen XP bar", version = 1.0, category = Category.MISC)
//public class Menu extends AbstractScript {
//
//    // Literal Skill Toggle - change this to test different skills
//    private Skill activeSkill = Skill.MINING;
//
//    @Override
//    public int onLoop() {
//        return 600;
//    }
//
//    @Override
//    public void onPaint(Graphics2D g) {
//        // --- THE MATH FIX ---
//        // By using 100.0, we prevent the result from being stuck at 0.
//        double currentXp = Skills.getExperience(activeSkill);
//        double xpToLevel = Skills.getExperienceToLevel(activeSkill);
//        int progress = (int) ((currentXp * 100.0) / xpToLevel);
//
//        // --- THE UI ---
//        int x = 10;
//        int y = 40;
//        int barWidth = 200;
//        int barHeight = 20;
//
//        // 1. Draw Background Box
//        g.setColor(new Color(0, 0, 0, 150));
//        g.fillRect(x, y, barWidth + 20, 50);
//
//        // 2. Draw Empty Bar (Black)
//        g.setColor(Color.BLACK);
//        g.fillRect(x + 10, y + 10, barWidth, barHeight);
//
//        // 3. Draw Fill (Green)
//        // We calculate how many pixels wide the green fill should be
//        int fillWidth = (int) (barWidth * (progress / 100.0));
//        g.setColor(Color.GREEN);
//        g.fillRect(x + 10, y + 10, fillWidth, barHeight);
//
//        // 4. Draw Percentage Text
//        g.setColor(Color.WHITE);
//        g.drawString(activeSkill.getName() + ": " + progress + "% to level", x + 15, y + 25);
//    }
//}