package main.managers;

import main.BotMan;
import main.task.Task;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

//TODO: check this javadoc still has valid examples
/**
 * Task Manager that tracks, executes, manages, adds, removes or skips a {@link Task}.
 *
 *<pre>{@code
 * // Create some tasks
 * Task walkToBank = new WalkTo(bankArea);
 * Task mineIron = new Mine(ironRock);
 * Task attackGoblin = new Attack(goblinNpc);
 *
 * // Add them to the manager
 * TaskMan.addTask(walkToBank, mineIron);
 * TaskMan.addUrgentTask(attackGoblin); // this goes to the front of queue
 * 2. Running tasks
 *}</pre>
 */
public final class TaskMan {
    // create list/model pair to dynamically display task list in the bot menus tasks dashboard menu.
    private final DefaultListModel<Task> taskListModel = new DefaultListModel<>();
    private JList<Task> taskList;

    // create list/model pair to dynamically display all created tasks in the bot menus task library tab
    private static final DefaultListModel<Task> taskLibraryModel = new DefaultListModel<>();
    private static JList<Task> taskLibrary;

    private final int MAX_SCRIPT_LOOPS = 100;
    /**
     * The current index of the task list being executed. This is separated otherwise iterating the menu would force
     * the bot do tasks prematurely.
     */
    private int listIndex = 0;
    /**
     * The current loop for this script.
     */
    private int listLoop = 0;
    private int listLoops = 1;

    /**
     * CONSTRUCTOR: Restored and Thread-Protected
     */
    public TaskMan() {
        // Substance/DreamBot requires JList creation on EDT to prevent UiThreadingViolationException
        if (SwingUtilities.isEventDispatchThread()) {
            initializeComponents();
        } else {
            try {
                SwingUtilities.invokeAndWait(this::initializeComponents);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void initializeComponents() {
        taskList = new JList<>(taskListModel);
        if (taskLibrary == null) {
            taskLibrary = new JList<>(taskLibraryModel);
        }
    }

    /**
     * This function automatically updates the task-library on task-creation. This forces any created task to be placed
     * into this library for user selection later.
     */
    public static void updateTaskLibrary(Task... tasks) {
        // iterate all passed tasks
        for (Task task : tasks)
            // if the library model doesn't already contain this task, add it to the library
            if (!taskLibraryModel.contains(task))
                taskLibraryModel.addElement(task);
    }

    public static JPanel section(String title) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        TitledBorder border = BorderFactory.createTitledBorder(
                new LineBorder(new Color(100, 0, 0), 1), // Crimson Border
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 13)
        );
        border.setTitleColor(new Color(180, 0, 0));
        p.setBorder(border);
        return p;
    }

    public JPanel buildDashMenuTasks(JLabel label) {
        // configure list once
        taskList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JButton save = new JButton("Save Preset");
        save.addActionListener(e -> {
            //TODO implement dupe logic here
        });

        ///  create a up arrow button as an alternate way to navigate the task list
        JButton btnUp = new JButton("↑");
        btnUp.addActionListener(e -> {
            taskList.setSelectedIndex(taskList.getSelectedIndex() - 1);
        });

        ///  create a down arrow button as an alternate way to navigate the task list
        JButton btnDown = new JButton("↓");
        btnDown.addActionListener(e -> {
            taskList.setSelectedIndex(taskList.getSelectedIndex() + 1);
        });

        JButton btnRemove = new JButton("Remove");
        btnRemove.addActionListener(e -> {
            int index = getSelectedIndex();

            if (index >= 0 && index < getTaskListModel().size()) {
                removeTask(index);
                decrementListIndex();
            }
        });

        /// create a task panel to store all these controls
        // create 4x quick-action buttons which can be used to create short-cuts later for the user.
        JButton preset1 = new JButton("Preset 1");
        preset1.addActionListener(e -> JOptionPane.showMessageDialog(preset1, "Preset 1 triggered!"));
        JButton preset2 = new JButton("Preset 2");
        preset2.addActionListener(e -> JOptionPane.showMessageDialog(preset2, "Preset 2 triggered!"));
        JButton preset3 = new JButton("Preset 3");
        preset3.addActionListener(e -> JOptionPane.showMessageDialog(preset3, "Preset 3 triggered!"));
        JButton preset4 = new JButton("Preset 4");
        preset4.addActionListener(e -> JOptionPane.showMessageDialog(preset1, "Preset 4 triggered!"));

        ///  create a panel to neatly group our quick action buttons
        JPanel presetPanel = new JPanel(new GridLayout(0, 4));
        presetPanel.add(preset1);
        presetPanel.add(preset2);
        presetPanel.add(preset3);
        presetPanel.add(preset4);

        // create buttons panel
        JPanel listButtons = section("Controls");
        listButtons.add(save);
        listButtons.add(btnUp);
        listButtons.add(btnDown);
        listButtons.add(btnRemove);

        JPanel controlPanel = new JPanel(new GridLayout(2,1));
        controlPanel.add(listButtons);
        controlPanel.add(presetPanel);

        // create task panel
        JPanel taskPanel = new JPanel(new BorderLayout(12, 12));
        taskPanel.setBorder(new EmptyBorder(0, 12, 0, 0));
        taskPanel.add(label, BorderLayout.NORTH);
        taskPanel.add(new JScrollPane(taskList), BorderLayout.CENTER);
        taskPanel.add(controlPanel, BorderLayout.SOUTH);

        // return the created task panel
        return taskPanel;
    }

    /**
     * Add the passed tasks to the queue based on their priority level.
     *
     * @param tasks The {@link Task task(s)} to submit to the task queue.
     */
    public void add(Task... tasks) {
        // add each task to the task list based on their priority levels
        for (Task task : tasks) {
            if (task.isUrgent)
                taskListModel.add(0, task);
            else
                taskListModel.addElement(task);
        }
    }

    public void addToLibrary(Task... tasks) {
        for (Task task : tasks)
            if (!taskLibraryModel.contains(task))
                taskLibraryModel.addElement(task);
    }

    /**
     * Removes the passed {@link Task} from the queue.
     */
    public boolean removeTask(Task task) {
        return taskListModel.removeElement(task);
    }

    /**
     * Removes the {@link Task} at the passed index from the list, if the passed index is valid.
     */
    public void removeTask(int index) {
        if (index >= 0 && index < size())
            taskListModel.remove(index);
    }

    /**
     * Permanently removes the passed task from the task-library.
     */
    public boolean deleteTask(Task task) {
        return taskLibraryModel.removeElement(task);
    }

    /**
     * Returns a shallow copy of the item at the given index in the queue (if any exists).
     */
    public Task peekAt(int index) {
        if (index < 0 || index >= size())
            return null;

        return taskListModel.get(index);
    }

    public Task getHead() {
        return peekAt(0);
    }

    /**
     * @return {@link Boolean true} if this script still has at least 1 loop remaining.
     */
    public boolean isReadyToLoop() {
        Task task = getTask();
        return task != null && task.isComplete() && hasLoopsLeft() && isListIndexValid() && isListLoopsValid();
    }

    public boolean isListLoopsValid() {
        return listLoop < listLoops && listLoop < MAX_SCRIPT_LOOPS;
    }

    public boolean isListIndexValid() {
        return listIndex >= 0 && listIndex < size();
    }

    public boolean isTaskReadyToLoop() {
        Task task = getTask();
        return task != null && task.isReadyToLoop();
    }

    public boolean hasLoopsLeft() {
        return listLoop < listLoops - 1;
    }

    private void restartLoop(BotMan bot) {
        setTaskListIndex(0);
        if (getListLoop() >= getListLoops() || getListLoop() >= MAX_SCRIPT_LOOPS)
            bot.onPause();
    }

    public boolean hasTasks() {
        Task task = getTask();
        return !taskListModel.isEmpty() && task != null && (!task.isComplete() || hasLoopsLeft());
    }

    public boolean hasWorkToDo() {
        Task task = getTask();
        return task != null && (task.isComplete() || getRemainingTaskCount() > 0);
    }

    public int size() {
        return taskListModel.size();
    }

    public boolean call(BotMan bot) throws InterruptedException {
        bot.setBotStatus("Calling task...");

        if (getTask() == null)
            throw new RuntimeException("[TaskMan Error] Task is null!");

        if (work(bot)) {
            bot.setPlayerStatus("Finished work!");
            bot.setBotStatus("Preparing next task...");
            if (getRemainingTaskCount() <= 0) {
                incrementListLoop();
                if (isReadyToLoop())
                    restartLoop(bot);
                reset(bot);
            }
            else
                incrementListIndex();

            return true;
        }

        return false;
    }

    public void reset(BotMan bot) {
        setTaskListIndex(0);
        bot.onPause();
    }

    public synchronized Task getTask() {
        if (size() < 1) return null;
        if (!isListIndexValid()) return null;
        return taskListModel.get(listIndex);
    }

    public synchronized Task getTask(int index) {
        if (index < 0 || index >= size())
            return null;
        return getTaskListModel().get(index);
    }

    public synchronized Task getPreviousTask() {
        if (size() < 2 || listIndex < 1)
            return null;
        return getTask(listIndex - 1);
    }

    public synchronized Task getNextTask() {
        if (size() < 2 || listIndex >= size() - 1)
            return null;

        return getTask(listIndex + 1);
    }

    public int getRemainingTaskCount() {
        return (size() - 1) - listIndex;
    }

    public int getListLoop() { return listLoop; }

    public int getListLoops() { return listLoops; }

    public void setListLoops(int loops) {
        if (loops > 0 && loops < MAX_SCRIPT_LOOPS)
            listLoops = loops;
    }

    public String getLoopsString() { return getListLoop() + "/" + getListLoops(); }

    public int getRemainingLoops() { return size() - getListIndex(); }

    public int getRemainingListLoops() { return listLoops - listLoop; }

    public String getListLoopsString() { return listLoop + "/" + listLoops; }

    public int getSelectedIndex() { return taskList.getSelectedIndex(); }

    public String getStagesAsString() {
        Task task = getTask();
        return task == null ? "" : task.getStage() + "/" + task.getStages();
    }

    public float getTaskProgress() {
        if (getTask() != null)
            return getTask().getProgress();
        return 0;
    }

    public void setTaskListIndex(int index) {
        if (index < 0 || index >= size())
            listIndex = 0;
        else
            listIndex = index;

        // Wrapped in safeRun because JList selection must happen on EDT
        SwingUtilities.invokeLater(() -> {
            if (taskList != null) taskList.setSelectedIndex(listIndex);
        });
    }

    public int getListIndex() { return listIndex; }

    public int getLibraryIndex() { return taskLibrary.getSelectedIndex(); }

    public void incrementListIndex() { setTaskListIndex(listIndex + 1); }

    public void incrementListLoop() {
        listLoop++;
        if (listLoop >= listLoops || listLoop >= MAX_SCRIPT_LOOPS)
            listLoop = 0;
    }

    public void decrementListIndex() { setTaskListIndex(listIndex - 1); }

    public DefaultListModel<Task> getTaskListModel() { return taskListModel; }

    public JList<Task> getTaskList() { return taskList; }

    public DefaultListModel<Task> getTaskLibraryModel() { return taskLibraryModel; }

    public JList<Task> getTaskLibrary() { return taskLibrary; }

    private boolean work(BotMan bot) throws InterruptedException {
        Task task = getTask();
        if (task != null) {
            bot.setPlayerStatus("Attempting to " + task);
            return task.run(bot);
        }
        return false;
    }

    public void restartTask() {
        if (getTask() != null)
            getTask().restart();
    }

    @Override
    public String toString() {
        return getTask() == null ? "Invalid task!" : getTask().getDescription();
    }
}