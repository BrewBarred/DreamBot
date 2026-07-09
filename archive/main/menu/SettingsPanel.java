//package main.menu;
//
//import main.BotMan;
//import main.task.Task;
//
//import javax.swing.*;
//import javax.swing.border.EmptyBorder;
//import javax.swing.border.LineBorder;
//import javax.swing.border.TitledBorder;
//import java.awt.*;
//
//public final class SettingsPanel extends JPanel {
//
//    public SettingsPanel(BotMan bot) {
//        super(new BorderLayout(12, 12));
//        setBorder(new EmptyBorder(12, 12, 12, 12));
//
//        JTabbedPane tabs = new JTabbedPane(JTabbedPane.BOTTOM);
//        tabs.setFont(new Font("Segoe UI", Font.PLAIN, 13));
//
//        ///  add different settings tabs here
//        tabs.addTab("General", buildGeneralSettingsTab(bot));
//        tabs.addTab("Script", buildScriptTab(bot));
//        tabs.addTab("Developer", buildDeveloperTab(bot)); // real dev/testing controls
//
//        add(tabs, BorderLayout.CENTER);
//    }
//
//    public static JPanel section(String title) {
//        JPanel p = new JPanel();
//        p.setOpaque(false);
//        TitledBorder border = BorderFactory.createTitledBorder(
//                new LineBorder(new Color(100, 0, 0), 1), // Crimson Border
//                title,
//                TitledBorder.LEFT,
//                TitledBorder.TOP,
//                new Font("Segoe UI", Font.BOLD, 13)
//        );
//        border.setTitleColor(new Color(180, 0, 0));
//        p.setBorder(border);
//        return p;
//    }
//
//    private JComponent buildGeneralSettingsTab(BotMan bot) {
//        // create a general settings tab to store all general setting menu components
//        JPanel generalSettings = new JPanel(new BorderLayout(12, 12));
//        // create a toggle section to store toggle controls
//        JPanel sectionToggles = section("Toggles");
//        //TODO add floating i. "If enabled, in-game overlays will be displayed over the client with real-time logging."
//        JCheckBox chkOverlays = new JCheckBox("Enable overlays");
//        chkOverlays.setSelected(bot.isDrawing());
//        chkOverlays.addActionListener(e -> bot.isDrawing(chkOverlays.isSelected()));
//
//        JPanel panelToggles = new JPanel();
//        panelToggles.setLayout(new BoxLayout(panelToggles, BoxLayout.Y_AXIS));
//        //        panelToggles.add(chkDevMode);
//        panelToggles.add(chkOverlays);
//        panelToggles.add(Box.createVerticalStrut(4));
//        panelToggles.add(chkOverlays);
//        panelToggles.add(chkOverlays);
//
//        ///  final construction:
//
//        // add control panel to the toggle section
//        sectionToggles.add(panelToggles);
//        // add toggle section to the general settings tab
//        generalSettings.add(sectionToggles, BorderLayout.CENTER);
//
//        return generalSettings;
//    }
//
//    private JComponent buildScriptTab(BotMan bot) {
//        JPanel scriptSettings = new JPanel(new BorderLayout(12, 12));
//        JPanel sectionToggles = section("Toggles");
//
//        // Script options:
//        // logout when script finishes successfully
//        // logout on script stop (i.e., Logout when the script stops e.g., breached attempt limit or manual stop)
//        // alternatively, have a break, change account, items, location or task-set, and continue
//        //
//
////        //TODO add floating i. "If enabled, the players account will be logged out before the script is stopped."
////        JCheckBox chkLogout = new JCheckBox("Logout on script end");
////        chkLogout.setSelected(bot.isLogOnStop());
////        chkLogout.addActionListener(e -> bot.setLogOnStop(chkLogout.isSelected()));
//
//        //TODO add floating i. "Enables/disables developer mode which gives the bot access to hidden methods and menus."
//        JCheckBox chkDevMode = new JCheckBox("Developer mode (bypass attempts)");
//        chkDevMode.setSelected(bot.isDevMode());
//        chkDevMode.addActionListener(e -> bot.setDevMode(chkDevMode.isSelected()));
//
//        JPanel row2 = new JPanel();
//        row2.setLayout(new BoxLayout(row2, BoxLayout.Y_AXIS));
//        row2.add(chkDevMode);
//        row2.add(Box.createVerticalStrut(4));
//        //row2.add(chkLogout);
//
//        sectionToggles.setLayout(new BorderLayout(8, 8));
//        sectionToggles.add(row2, BorderLayout.CENTER);
//
//        JPanel info = section("Live info");
//        info.setLayout(new GridLayout(0, 2, 8, 8));
//
//        JLabel vAttempts = new JLabel(bot.getRemainingAttemptsString());
//        JLabel vTasks = new JLabel(String.valueOf(bot.getRemainingTaskCount()));
//
//        JButton btnRefresh = new JButton("Refresh");
//        btnRefresh.addActionListener(e -> {
//            vAttempts.setText(bot.getRemainingAttemptsString());
//            vTasks.setText(String.valueOf(bot.getRemainingTaskCount()));
//        });
//
//        info.add(new JLabel("Attempts:"));
//        info.add(vAttempts);
//        info.add(new JLabel("Remaining tasks:"));
//        info.add(vTasks);
//        info.add(new JLabel(""));
//        info.add(btnRefresh);
//
//        scriptSettings.add(sectionToggles, BorderLayout.NORTH);
//        scriptSettings.add(info, BorderLayout.CENTER);
//        return scriptSettings;
//    }
//
//    private JComponent buildDeveloperTab(BotMan bot) {
//        JPanel root = new JPanel();
//        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
//        JPanel tm = section("Task Manager");
//
//        // ---------- TaskMan controls (real) ----------
//        tm.setLayout(new GridLayout(0, 2, 8, 8));
//
//        JLabel lblIdx = new JLabel("Current index:");
//        JSpinner spinner = new JSpinner(new SpinnerNumberModel(
//                bot.getListIndex(), 0, bot.getTaskListModel().size(), 1
//        ));
//        JButton btnApplyIndex = new JButton("Apply index");
//
//        JLabel lblRemain = new JLabel("Remaining tasks:");
//        JLabel vRemain = new JLabel(String.valueOf(bot.getRemainingTaskCount()));
//
//        btnApplyIndex.addActionListener(e -> {
//            int index = (Integer) spinner.getValue();
//            bot.setTaskListIndex(index);
//            vRemain.setText(String.valueOf(bot.getListIndex()));
//            bot.setBotStatus("TaskMan index set to: " + bot.getListIndex());
//            bot.getBotMenu().refresh();
//        });
//
//        tm.add(lblIdx);
//        tm.add(spinner);
//        tm.add(new JLabel(""));
//        tm.add(btnApplyIndex);
//        tm.add(new JLabel(""));
//        tm.add(lblRemain);
//        tm.add(vRemain);
//
//        // ---------- Current Task controls (real) ----------
//        JPanel task = section("Current task (testing)");
//        task.setLayout(new GridLayout(0, 2, 8, 8));
//
//        JLabel vTaskName = new JLabel("-");
//        JLabel vStage = new JLabel("-");
//        JLabel vLoops = new JLabel("-");
//
//        JSpinner spStage = new JSpinner(new SpinnerNumberModel(1, 1, 9999, 1));
//        JSpinner spStages = new JSpinner(new SpinnerNumberModel(1, 1, 9999, 1));
//        JSpinner spTaskLoops = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
//
//        JButton btnLoad = new JButton("Load current task");
//        JButton btnApply = new JButton("Apply to task");
//
//        btnLoad.addActionListener(e -> {
//            Task head = bot.getNextTask();
//            if (head == null) {
//                vTaskName.setText("(none)");
//                vStage.setText("-");
//                vLoops.setText("-");
//                return;
//            }
//
//            vTaskName.setText(head.getDescription());
//            vStage.setText(head.getStageString());
//            vLoops.setText(head.getLoop() + "/" + head.getLoops());
//
//            // populate spinners from real task state
//            spStage.setValue(Math.max(1, head.getStage()));
//            spStages.setValue(Math.max(1, head.getStages()));
//            spTaskLoops.setValue(Math.max(1, head.getLoops()));
//        });
//
//        btnApply.addActionListener(e -> {
//            Task head = bot.getNextTask();
//            if (head == null) return;
//
//            int newStage = (Integer) spStage.getValue();
//            int newStages = (Integer) spStages.getValue();
//            int newLoops = (Integer) spTaskLoops.getValue();
//
//            // stage + stages (you already expose)
//            head.betweenStages(newStage, newStages);
//
//            // loops (uses existing validation)
//            head.setLoops(newLoops);
//
//            // refresh UI
//            vStage.setText(head.getStageString());
//            vLoops.setText(head.getLoop() + "/" + head.getLoops());
//            bot.getBotMenu().refresh();
//            bot.setBotStatus("Updated task: stage=" + head.getStageString() + " loops=" + head.getLoops());
//        });
//
//        task.add(new JLabel("Task:"));
//        task.add(vTaskName);
//
//        task.add(new JLabel("Stage:"));
//        task.add(vStage);
//
//        task.add(new JLabel("Loops:"));
//        task.add(vLoops);
//
//        task.add(new JLabel("Set stage:"));
//        task.add(spStage);
//
//        task.add(new JLabel("Set total stages:"));
//        task.add(spStages);
//
//        task.add(new JLabel("Set task loops:"));
//        task.add(spTaskLoops);
//
//        task.add(btnLoad);
//        task.add(btnApply);
//
//        root.add(tm);
//        root.add(Box.createVerticalStrut(12));
//        root.add(task);
//        root.add(Box.createVerticalGlue());
//
//        return root;
//    }
//
//    private JPanel row(JComponent... comps) {
//        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
//        for (JComponent c : comps) p.add(c);
//        return p;
//    }
//}