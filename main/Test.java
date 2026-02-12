package main;

import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.methods.skills.SkillTracker;
import org.dreambot.api.methods.skills.Skills;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.URL;

@ScriptManifest(author = "User", name = "AIO All-In-One Menu", version = 1.1, category = Category.MISC)
public class Test extends AbstractScript {

    private Skill activeSkill;
    private Image skillIcon;
    private boolean isRunning = false;
    private long startTime;

    @Override
    public void onStart() {
        showGui();
    }

    private void showGui() {
        JFrame frame = new JFrame("AIO Skill Selector");
        frame.setLayout(new FlowLayout());
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Populate dropdown with all OSRS skills
        JComboBox<Skill> skillDropdown = new JComboBox<>(Skill.values());
        JButton startButton = new JButton("Start Training");

//        startButton.addActionListener(e -> {
//            activeSkill = (Skill) skillDropdown.getSelectedItem();
//            loadIcon(activeSkill);
//            getSkillTracker().start(activeSkill);
//            startTime = System.currentTimeMillis();
//            isRunning = true;
//            frame.dispose();
//        });

        frame.add(new JLabel("Select Skill:"));
        frame.add(skillDropdown);
        frame.add(startButton);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void loadIcon(Skill skill) {
        try {
            // Literal mapping: You would replace this with the direct URL from the wiki for each skill
            // Example for Mining: https://oldschool.runescape.wiki/images/Mining_icon.png
            String skillName = skill.getName().substring(0, 1).toUpperCase() + skill.getName().substring(1).toLowerCase();
            String url = "https://oldschool.runescape.wiki/images/" + skillName + "_icon.png";
            skillIcon = ImageIO.read(new URL(url));
        } catch (IOException e) {
            log("Could not load icon for: " + skill.getName());
        }
    }

    @Override
    public int onLoop() {
        if (!isRunning) return 500;

        // Literal placeholder for your training logic
        // Example: if (activeSkill == Skill.MINING) { mine(); }

        return 600;
    }

    @Override
    public void onPaint(Graphics2D g) {
        if (!isRunning || activeSkill == null) return;

        // --- Live XP Bar Rendering ---
        int x = 10;
        int y = 20;
        int barWidth = 180;
        int barHeight = 22;

        // Draw Background Panel
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRoundRect(x, y, barWidth + 50, 60, 10, 10);
        g.setColor(Color.WHITE);
        g.drawRoundRect(x, y, barWidth + 50, 60, 10, 10);

        // Draw Skill Icon
        if (skillIcon != null) {
            g.drawImage(skillIcon, x + 5, y + 5, 25, 25, null);
        }

        // Draw XP Bar
        g.setColor(Color.BLACK);
        g.fillRect(x + 40, y + 10, barWidth, barHeight);

        // We multiply by 100.0 first to turn the whole calculation into a decimal
        int progress = (int) ((Skills.getExperience(activeSkill) * 100.0) / Skills.getExperienceToLevel(activeSkill));

        int fillWidth = (int) (barWidth * (progress / 100.0));

        // XP Bar Fill (Green Gradient Style)
        g.setColor(new Color(34, 139, 34));
        g.fillRect(x + 40, y + 10, fillWidth, barHeight);

        // Border for the Bar
        g.setColor(Color.LIGHT_GRAY);
        g.drawRect(x + 40, y + 10, barWidth, barHeight);

        // Text Data
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 12));
        g.drawString(progress + "%", x + (barWidth / 2) + 30, y + 26);

        g.setFont(new Font("Arial", Font.PLAIN, 11));
        //g.drawString("Gained: " + getSkillTracker().getGainedExperience(activeSkill) + " (" + getSkillTracker().getGainedExperiencePerHour(activeSkill) + "/hr)", x + 10, y + 50);
    }
}