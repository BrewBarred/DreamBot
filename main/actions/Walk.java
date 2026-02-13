//package main.actions;
//
//import main.BotMan;
//import main.task.Task;
//import main.task.Action;
//
//import javax.swing.*;
//
//public class Walk extends Task {
//    private boolean isAutoRunning = true;
//
//
//    protected Walk(Action type, String description) {
//        super(type, description);
//    }
//
//    @Override
//    protected void onTaskLoopCompletion() {}
//
//    @Override
//    protected void onTaskCompletion() {
//
//    }
//
//    @Override
//    protected void onStageCompletion() {
//
//    }
//    /**
//
//     * Define what to do when no parameters are passed to Walk().
//     *
//     * @param bot The bot instance to perform this Task.
//     * @return True if the task is successfully completed, else returns false.
//     */
//    @Override
//    protected boolean execute(BotMan bot) { // Signature updated to match Task base class
//        return true;
//    }
//
//    @Override
//    public JPanel getTaskSettings() {
//        // create a title
//        JLabel title = new JLabel("Task Settings: Walk");
//        // create a checkbox and link it to a field boolean within this class so the menu can control this tasks params
//        JCheckBox cbAutoRun = new JCheckBox("Auto-Run");
//        cbAutoRun.setSelected(true);
//        cbAutoRun.addActionListener(e -> this.isAutoRunning = (cbAutoRun.isSelected()));
//
//        // create a panel to store all of these neat controls onto
//        JPanel panel =  new JPanel();
//        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
//        // add our controls to the panel
//        panel.add(title);
//        panel.add(cbAutoRun);
//
//        // return the panel we just created
//        return panel;
//    }
//
//    /**
//     * Set a total stages value in order to track the {@link Task} progress.
//     */
//    @Override
//    public final int getStages() {
//        return 1;
//    }
//}