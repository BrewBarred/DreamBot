//package main;
//
//import javax.swing.*;
//import javax.swing.border.EmptyBorder;
//import javax.swing.border.TitledBorder;
//
//import main.menu.SettingsPanel;
//import main.task.Task;
//import main.task.Action;
//import main.managers.WindowMan;
//
//import java.awt.*;
//import java.util.ArrayList;
//
//public class BotMenu extends JFrame {
//    /**
//     * A reference to the {@link BotMan} object that this {@link BotMenu} interacts with.
//     */
//    public BotMan bot;
//
//    /// Bot Menu components (class-scope for dynamic updates in refresh())
//
//    private final JLabel titleTaskList = new JLabel();
//    private final JLabel titleTaskLibrary = new JLabel();
//    private final JButton btnToggleExecution = new JButton("▶");
//
//    ///  Bot menu settings (Sort later)
//    // TODO sort which settings should be class-level and which shouldn't be, then extract them out into
//    //  their own 'Settings' class once it's a bit clearer what needs to be accessed globally
//
//    /**
//     * BotMenu Setting: True if the {@link BotMenu} should be hidden instead of disposed of when closed.
//     */
//    protected boolean isHidingOnExit;
//    /**
//     * BotMenu Setting: True if the {@link BotMenu} should exit the script on close instead of just closing the menu.
//     */
//    protected boolean isExitingOnClose;
//
//    private final JPanel taskTypeSettingsCard = new JPanel(new CardLayout());
//    private static final String CARD_EMPTY = "EMPTY";
//    private static final String CARD_EDITOR = "EDITOR";
//    private final JTextField tfDesc = new JTextField();
//    private final JSpinner spLoops = new JSpinner(new SpinnerNumberModel(1, 0, 100, 1));
//
//    // Position inputs
//    private final JPanel posPanel = new JPanel(new GridLayout(0, 2, 8, 8));
//    private final JSpinner spX = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 1));
//    private final JSpinner spY = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 1));
//    private final JSpinner spZ = new JSpinner(new SpinnerNumberModel(0, 0, 3, 1));
//
//    // Area inputs (two corners)
//    private final JPanel areaPanel = new JPanel(new GridLayout(0, 2, 8, 8));
//    private final JSpinner spX1 = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 1));
//    private final JSpinner spY1 = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 1));
//    private final JSpinner spX2 = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 1));
//    private final JSpinner spY2 = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 1));
//
//    /**
//     * The currently selected {@link Action} in the Task Builder menu, used to load script-specific settings in the
//     * adjacent panel.
//     */
//    private Action selectedAction;
//
//    /**
//     * Launches a bot menu for the associated bot instance (os bot script).
//     *
//     * @param bot The {@link BotMan} instance that this menu communicates with.
//     */
//    public BotMenu(BotMan bot) throws Exception {
//        // call parent constructor to ensure proper instantiation
//        super("BotMan: BotMenu");
//        this.bot = bot;
//
//        bot.setBotStatus("Starting settings client...");
//
//        try {
//            setBotStatus("Connecting to server... (local example)");
//            // example local save
//            //String data = "{ \"foodId\": 385, \"eatHp\": 25 }";
//            //bot.log((Client.saveSettingsJson(data) ? "Successfully saved" : "Failed to save") + " settings!");
//
//        } catch (Exception e) {
//            bot.log("Settings server not running / unreachable: " + e.getMessage());
//        }
//
//        // run thread-safe setup tasks
//        bot.safeRun(setup());
//    }
//
//    /**
//     * Defines the setup tasks used to set the {@link BotMenu} up, ready for display and user interaction.
//     *
//     * @return A runnable task which can be used to instantiate the {@link BotMenu}.
//     */
//    private Runnable setup() {
//        return () -> {
//            // create bot menu
//            createMenu();
//            // set default settings
//            setDefaultSettings();
//            // place the bot menu on a different screen to the bot client if possible
//            WindowMan.moveToAlternateMonitor(this);
//            // pause/play script on action press + chain with isRunning to automatically update isRunning bool
//            btnToggleExecution.addActionListener(e -> bot.toggleExecutionMode());
//            // listen to isRunning bool and automatically update button text on boolean value change
//            btnToggleExecution.addPropertyChangeListener("isRunning", e -> {
//                bot.log("Property changed! isRunning = " + bot.isRunning);
//            });
//            // display the menu
//            this.showMenu();
//            // refresh the bot menu to reflect all changes
//            BotMenu.refresh();
//        };
//    }
//
//    ///
//    ///     Getters/setters
//    ///
//
//    /**
//     * Sets whether to stop the botting script when the Bot Menu is closed.
//     *
//     * @param exit True if the script should stop on menu closure, else false.
//     */
//    protected void setExitOnClose(boolean exit) {
//        // update exit on close setting
//        this.isExitingOnClose = exit;
//        // cannot exit on close AND hide on close
//        this.isHidingOnExit = !isExitingOnClose;
//    }
//
//    /**
//     * Returns whether the botting script should stop on menu closure or not.
//     *
//     * @return True if the script should stop on menu closure, else false.
//     */
//    protected boolean getExitOnClose() {
//        return this.isExitingOnClose;
//    }
//
//    /**
//     * Sets the default close operation for this {@link BotMenu}. Set to true to hide the menu on close, else false to
//     * exit the menu instead.
//     *
//     * @param hide True if the menu should be hidden on close, else false if closed.
//     */
//    protected void setHideOnClose(boolean hide) {
//        // update class variable
//        this.isHidingOnExit = hide;
//        // cannot hide on close AND exit on close
//        this.isExitingOnClose = !isHidingOnExit;
//    }
//
//    /**
//     * Returns the default close operation for this {@link BotMenu}.
//     *
//     * @return True if the default close operation for this menu is set to hide, else false if set to dipose.
//     */
//    protected boolean getHideOnClose() {
//        return this.isHidingOnExit;
//    }
//
//    ///
//    ///     APPLY DEFAULT SETTINGS FIRST
//    ///
//    //TODO: create functions to modularize default settings later + link to reset button
//    public void setDefaultSettings() {
//        ///  Menu Settings (Client)
//        setMinimumSize(new Dimension(750, 600));
//
//        ///  Menu Settings (BotMenu): Settings that change the menu, or how it interacts with the script or client.
//        setHideOnClose(true);
//        setExitOnClose(true);
//
//        ///  Menu Settings (Task):
//        bot.setTaskListIndex(0);
//
//        // this.maxLoops = 1; // disables looping until manually set
//        // this.taskType = TaskType.SCAN; // sets default task to scan the surrounding area to decide what can be done
//        // this.target = null; // fetches nearest target based on task type?
//        // this.position = null; // fetches current position?
//        // this.area = null; // fetches current area using (position, DEFAULT_RADIUS)?
//
//        ///  Bot settings: Settings that change how the script runs prior and during execution.
//
//        // this.isRunning = true;
//        // this.isTeleporting = false; // sets the player to use teleports where possible (not setup yet)
//
//        // this.isBanking = true;
//        // this.isLooting = true;
//
//        // this.isChatting = true;
//        // this.isP2P = false; // set F2P bots by default as this has the most support
//
//        // this.isSolvingRandoms = false; // never leave randoms waiting, always dismiss them after a period of time or solve them
//        // this.isSolvingClues = true;
//
//
//        ///  Combat settings: Settings that change how the bot responds to combat
//
//        // this.isRetailing = true;
//        // this.isRetaliatingPVP = false;
//
//        // this.isHealing = true;
//
//        // this.isBurying = true;
//
//
//        ///  Advanced combat settings: Settings that adjust the default combat properties/parameters. Hidden menu - intended for advanced users only.
//
//        // this.attackStyle = AttackStyle.Slash; // set default attack style to slash (commonly available on all weps)
//
//        // this.healBelowHp = 20 // start auto-healing below this hp value (between 1-99)
//        // this.preferredFood = "Shark", "Lobster", "Tuna"; // turn csv into string list and use in order using textbox
//
//        // this.dontBury = "Dragon", "Curved"; // if auto-burying is enabled, exclude bones containing the strings in this list, such as "Frost Dragon Bones" via excluding "Dragon" (not case-sensitive)
//
//
//        ///  Advanced settings: Settings that adjust the default properties of other settings or parameters of a function. Hidden menu - intended for advanced users only.
//
//        // this.runAboveEnergy = 80; // only allow the bot to auto run when energy level is 80 or higher.
//    }
//
//    /**
//     * Makes the BotMenu open on the centre of the users 2nd monitor where more than 1 monitor is available.
//     */
//    //TODO: Test this on one monitor device
//    private void setScreenPreference() {
////        // Fetch all available graphics devices (monitors).
////        GraphicsDevice[] screens =
////                GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
////
////        // Defensive fallback: if the system reports no monitors (extremely rare),
////        // let Swing decide where to place the window.
////        if (screens == null || screens.length == 0) {
////            setLocationRelativeTo(null);
////            return;
////        }
////
////        // Decide which monitor index to use.
////        // This also updates internal tracking fields.
////        int index = chooseMenuScreenIndex();
////
////        // Clamp index to a valid range to prevent crashes.
////        if (index < 0) index = 0;
////        if (index >= screens.length) index = screens.length - 1;
////
////        // Retrieve the usable bounds of the chosen monitor.
////        Rectangle bounds = screens[index]
////                .getDefaultConfiguration()
////                .getBounds();
////
////        // Center the BotMenu within the selected monitor.
////        int x = bounds.x + (bounds.width - getWidth()) / 2;
////        int y = bounds.y + (bounds.height - getHeight()) / 2;
////
////        setLocation(x, y);
//    }
//
//    /**
//     * Sets the Task Settings panel to the passed panel. Used to display the settings for each Task as the user
//     * navigates the Task Queue in the {@link BotMenu}.
//     *
//     * @param taskSettings The {@link JPanel} object used to create the Task Settings menu for each Task.
//     */
//    protected final void setTaskSettings(JPanel taskSettings) {
//        // validate the passed task settings
//        if (taskSettings == null)
//            taskSettings = getTaskSettings();
//
//        //TODO finish this function off, still needs to add task settings to the BotMenu somewhere and load the panel
//        // with suitable settings
//
//        // update the task settings field
//
//        //this.scriptPanel = taskSettings;
//    }
//
//    /**
//     * Force the child script to produce a script-specific settings tab for easier interaction with each script.
//     *
//     * @return A {@link JPanel} object used as the "Task Settings" section in the Task Queue of the {@link BotMenu}.
//     */
//    protected JPanel getTaskSettings() {
//        // fetch the current task from the task manager
//        Task task = bot.getTask();
//        // get custom or default settings based on whether this task provides custom task settings or not
//        return task != null ? task.getTaskSettings() : getDefaultTaskSettings();
//    }
//
//    /**
//     * Creates and returns the default Task Settings panel. This function is a back-up in-case tasks have not provided
//     * any additional settings that can adjust the script.
//     *
//     * @return The default Task Settings panel.
//     */
//    protected JPanel getDefaultTaskSettings() {
//        // create a blank bordered panel
//        JPanel defaultTaskSettingsPanel = new JPanel(new BorderLayout());
//        // add a label to the centre of the blank panel explaining no settings were found for this task
//        JLabel defaultTaskSettingsLabel = new JLabel("No settings found!");
//        defaultTaskSettingsPanel.add(defaultTaskSettingsLabel, BorderLayout.CENTER);
//
//        // return the default task settings panel
//        return defaultTaskSettingsPanel;
//    }
//
//    ///
//    ///     CREATE THE BOT MENU GUI
//    ///
//    private void createMenu() {
//        ///
//        ///     Create a menu by stacking the header, on top of a set of tabs which each have their own menus, controls
//        ///     and (in some cases) their own submenus, then finally, place the status' messages along the bottom
//        ///
//
//        ///  set default size
//        setExtendedState(JFrame.MAXIMIZED_BOTH);
//
//        ///  create a bot menu panel to store all the created components
//        JPanel menu = new JPanel(new BorderLayout());
//        menu.setBorder(new EmptyBorder(12, 12, 12, 12));
//        setContentPane(menu);
//        setLocationRelativeTo(null);
//
//        ///  create and add each menu tab
//        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
//        tabs.setFont(new Font("Segoe UI", Font.PLAIN, 14));
//        tabs.addTab("Dashboard", buildTabDashboard());
//        tabs.addTab("Task Library", buildTabTaskLibrary());
//        tabs.addTab("Task Builder", buildTabTaskBuilder());
//        tabs.addTab("Travel Manager", buildTabSettings());
//        tabs.addTab("Settings", buildTabSettings());
//        tabs.addTab("Logs", bot.getTabLogs());
//        // note: CANNOT SET SELECTED INDEX BEFORE ADDING TABS!! ...or it will try and find the tab in an empty list.
//        tabs.setSelectedIndex(0);
//
//        ///  add bot menu components
//
//        // add menu header
//        menu.add(buildHeader(), BorderLayout.NORTH);
//        // add main menu tabs (dashboard, task manager, etc...)
//        menu.add(tabs, BorderLayout.CENTER);
//    }
//
//    private JComponent buildHeader() {
//        ///  create a header panel which will hold everything we create
//        JPanel header = new JPanel(new BorderLayout());
//        // remove header border
//        header.setBorder(new EmptyBorder(0, 0, 12, 0));
//
//        ///  create header left side
//        // add a header title w/big font
//        JLabel title = new JLabel("BotMan | BotMenu");
//        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
//
//        // add a header subtitle with smaller, harder to read font
//        JLabel subtitle = new JLabel("Customize task queue, view bot status, track items, setup timers & more!");
//        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
//        subtitle.setForeground(new Color(80, 80, 80));
//
//        ///  create a title panel for header/subtitle layout
//        JPanel titlePanel = new JPanel();
//        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
//        titlePanel.setOpaque(false);
//        titlePanel.add(title);
//        titlePanel.add(Box.createVerticalStrut(4));
//        titlePanel.add(subtitle);
//
//        ///  create the quick action buttons and link them to an action event
//
//        JPanel executionPanel = new JPanel();
//        executionPanel.add(getExecutionPanel(), BorderLayout.EAST);
//
//        JPanel headerPanel = new JPanel(new BorderLayout());
//        headerPanel.add(executionPanel, BorderLayout.NORTH);
//        //headerPanel.add(quickActionPanel, BorderLayout.SOUTH);
//
//        /// add header components
//
//        // add header title
//        header.add(titlePanel, BorderLayout.WEST);
//        // add header quick action buttons
//        header.add(headerPanel, BorderLayout.EAST);
//
//        // return the header we just built
//        return header;
//    }
//
//    private JPanel getExecutionPanel() {
//        JButton btnStop = new JButton("■");
//        btnStop.addActionListener(e -> {
//            try {
//                bot.exit();
//            } catch (Throwable t) {
//                bot.setPlayerStatus("Stop failed: " + t);
//            }
//        });
//        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
//        row1.add(btnToggleExecution);
//        row1.add(btnStop);
//        return row1;
//    }
//
//    /**
//     * Creates a selector for the dashboard, which allows the user to flick between different menus within the dashboard
//     * tab.
//     *
//     * @return
//     */
//    private JComponent buildTabDashboard() {
//        /// create cards and a card panel and use it to switch between various sub-menus
//        CardLayout cards = new CardLayout();
//        JPanel dashMenuPanel = new JPanel(cards);
//
//        /// add the submenus to a card-panel for easier switching later
//        dashMenuPanel.add(buildDashMenuTasks(), "Tasks");
//        dashMenuPanel.add(buildDashMenuStatus(), "Status");
//        dashMenuPanel.add(buildDashMenuTrackers(), "Trackers"); //TODO build trackers menu
//        dashMenuPanel.add(buildDashMenuTimers(), "Timers"); //TODO build timers menu
//        dashMenuPanel.add(buildDashMenuPlayer(), "Player"); //TODO build player info menu
//        dashMenuPanel.add(buildDashMenuDevConsole(), "Dev Console"); //TODO build player console menu
//        dashMenuPanel.add(buildDashMenuAbout(), "About");
//
//
//        ///  create a list model to dynamically update botmenu with contents
//        DefaultListModel<String> model = new DefaultListModel<>();
//        model.addElement("Tasks");
//        model.addElement("Status");
//        model.addElement("Trackers");
//        model.addElement("Timers");
//        model.addElement("Player");
//        model.addElement("Reference Manual");
//        model.addElement("Dev Console");
//        model.addElement("About");
//
//        // make a list using the default list model
//        JList<String> navList = new JList<>(model);
//        // force single-selection to prevent multiple menu calls
//        navList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
//        // set list properties
//        navList.setSelectedIndex(0);
//        navList.setFixedCellHeight(36);
//        navList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
//
//        // dynamically load each submenu using the nav lists item name as a reference to the card panel being displayed
//        navList.addListSelectionListener(e -> cards.show(dashMenuPanel, navList.getSelectedValue()));
//
//        /// create the dashboard panel which will hold all the controls we create
//        JPanel dashboardPanel = new JPanel(new BorderLayout());
//        dashboardPanel.setBorder(new EmptyBorder(12, 12, 12, 12));
//        // add nav list to the dashboard panel
//        dashboardPanel.add(navList, BorderLayout.WEST);
//        dashboardPanel.add(dashMenuPanel, BorderLayout.CENTER);
//        return dashboardPanel;
//    }
//
//    private JComponent buildDashMenuTasks() {
//        if (bot == null)
//            throw new RuntimeException("Bot instance has not been initialized!");
//
//        // use task manager to build this menu since the main components are there
//        return bot.getDashMenuTasks(titleTaskList);
//    }
//
//    /**
//     * Creates the main dashboard component of the {@link BotMenu}.
//     *
//     * @return The dashboard panel used to create the {@link BotMenu}.
//     */
//
//    private JComponent buildDashMenuStatus() {
//        ///  create a 2x2 grid using GridLayout, adding 4x statCards()
//        //TODO update status displays as these are just placeholders for now
//        JPanel grid = new JPanel(new GridLayout(2, 2, 12, 12));
//        grid.add(statCard("Status", bot.getPlayerStatus(), bot.getBotStatus()));
//        grid.add(statCard("Uptime", "01:42:13", "Since last restart"));
//        grid.add(statCard("Profit/hr", "?", "?"));
//        grid.add(statCard("Tasks", bot.getSelectedTaskIndex() + "/" + bot.getTaskListModel().size(), bot.getTaskProgress() + "%"));
//
//        ///  add a text area to display extra notes at the bottom of the status menu
//
//        //TODO update notes to something more interesting and also make it read only? can currently type in that box
//        JTextArea notes = new JTextArea(
//                "Notes:" +
//                        "\n- This is a 'Dashboard' card grid." +
//                        "\n- The left nav swaps panels using CardLayout." +
//                        "\n- Add your OSBot stats, timers, etc here.");
//        notes.setLineWrap(true);
//        notes.setWrapStyleWord(true);
//        notes.setFont(new Font("Consolas", Font.PLAIN, 13));
//        notes.setBorder(new EmptyBorder(10, 10, 10, 10));
//
//        ///  create a panel to store all our controls
//
//        JPanel statusPanel =  new JPanel(new GridLayout(2, 1, 12, 12));
//        statusPanel.setBorder(new EmptyBorder(0, 12, 0, 0));
//        statusPanel.add(grid, BorderLayout.CENTER);
//        statusPanel.add(new JScrollPane(notes), BorderLayout.SOUTH);
//        return statusPanel;
//    }
//
//    private JComponent buildDashMenuTrackers() {
//        return getDefaultPanel();
//    }
//
//    private JComponent buildDashMenuTimers() {
//        return getDefaultPanel();
//    }
//
//    private JComponent buildDashMenuPlayer() {
//        //TODO create a function that takes a title and returns a panel with the title added in the default font
//        JLabel title = new JLabel("Player:");
//        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
//        // create a status panel
//        JPanel statusPanel = new JPanel(new BorderLayout(12, 12));
//        statusPanel.setBorder(new EmptyBorder(0, 12, 0, 0));
//
//        // display the progress of this task
//        JProgressBar progressTask = new JProgressBar(0, 100);
//        // TODO link up xp bar properly
//        progressTask.setValue(63);
//        progressTask.setStringPainted(true);
//        progressTask.setString("Task progress");
//
//        // create an xp goal progress bar
//        JProgressBar progressXP = new JProgressBar(0, 100);
//        // TODO link xp goal bar properly
//        progressXP.setValue(22);
//        progressXP.setStringPainted(true);
//        progressXP.setString("Supplies Remaining");
//
//        // create a vertical column to stack progress bars
//        JPanel progressBarPanel = new JPanel(new GridLayout(0, 1, 8, 8));
//        // add progress bars (stacked by grid-layout above)
//        progressBarPanel.add(progressTask);
//        progressBarPanel.add(progressXP);
//
//        // add the status label panel & progress bar panel to the status panel
//        statusPanel.add(title, BorderLayout.NORTH);
//        statusPanel.add(progressBarPanel, BorderLayout.CENTER);
//        return statusPanel;
//    }
//
//    private JComponent buildDashMenuDevConsole() {
//        // this code might enable me to code while playing?
////        String input = "run";
////
////        Method method = this.getClass().getDeclaredMethod(input);
////        method.setAccessible(true);
////        method.invoke(this);
//        return getDefaultPanel();
//    }
//
//    private JComponent buildDashMenuReferenceManual() {
//        return getDefaultPanel();
//    }
//
//    private JComponent buildTabTaskLibrary() {
//        // create a simpler reference to the task managers task library (the set of all tasks)
//        JList<Task> taskLibrary = bot.getTaskLibrary();
//        taskLibrary.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
//        //actionList.setFixedCellHeight(30); //TODO check if needed?
//        //actionList.setFont(new Font("Segoe UI", Font.PLAIN, 13)); //TODO check if needed?
//
//        ///  create button to queue tasks stored in task library
//
//        JButton btnQueue = new JButton("Add");
//        btnQueue.addActionListener(e -> {
//            bot.addTask(taskLibrary.getSelectedValue());
//            BotMenu.refresh();
//            JOptionPane.showMessageDialog(this, "Item has been added to the queue!");
//        });
//
//        ///  create an up arrow button as an alternate way to navigate the task library list
//
//        JButton btnUp = new JButton("↑");
//        btnUp.addActionListener(e -> {
//            taskLibrary.setSelectedIndex(taskLibrary.getSelectedIndex() - 1);
//            BotMenu.refresh();
//        });
//
//        ///  create a down arrow button as an alternate way to navigate the task library list
//        JButton btnDown = new JButton("↓");
//        btnDown.addActionListener(e -> {
//            taskLibrary.setSelectedIndex(taskLibrary.getSelectedIndex() + 1);
//            BotMenu.refresh();
//
//        });
//
//        ///  create a button to remove any unwanted tasks from the task library
//
//        JButton btnRemove = new JButton("Remove");
//        btnRemove.addActionListener(e -> {
//            int index = taskLibrary.getSelectedIndex();
//            if (index >= 0) {
//                bot.getTaskLibraryModel().remove(index);
//                BotMenu.refresh();
//                JOptionPane.showMessageDialog(this, "Item has been deleted!");
//            } else {
//                JOptionPane.showMessageDialog(this, "No item selected!");
//            }
//        });
//
//        //TODO add save/load buttons when menu is working
//
//        ///  create a panel to store the task list buttons neatly
//
//        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
//        buttons.add(btnQueue);
//        buttons.add(btnUp);
//        buttons.add(btnDown);
//        buttons.add(btnRemove);
//
//        /// create a task panel to store all these controls
//
//        JPanel taskPanel = new JPanel(new BorderLayout(12, 12));
//        taskPanel.setBorder(new EmptyBorder(0, 12, 0, 0));
//        taskPanel.add(titleTaskLibrary, BorderLayout.NORTH);
//        taskPanel.add(new JScrollPane(taskLibrary), BorderLayout.CENTER);
//        taskPanel.add(buttons, BorderLayout.SOUTH);
//
//        // return the created task panel
//        return taskPanel;
//
////
////        JScrollPane leftScroll = new JScrollPane(actionList);
////        leftScroll.setPreferredSize(new Dimension(220, 0));
////
////        // ----- right settings card -----
////        taskTypeSettingsCard.removeAll();
////        taskTypeSettingsCard.add(buildEmptyTaskPanel(), CARD_EMPTY);
////        taskTypeSettingsCard.add(buildTabTaskBuilder(), CARD_EDITOR);
////
////        // default view
////        ((CardLayout) taskTypeSettingsCard.getLayout()).show(taskTypeSettingsCard, CARD_EMPTY);
////
////        // selection handler (fires on click + arrow keys + scrolling selection)
////        actionList.addListSelectionListener(e -> {
////            if (e.getValueIsAdjusting())
////                return;
////            Action selected = actionList.getSelectedValue();
////            showTaskTypeEditor(selected);
////        });
////
////        // ----- split pane -----
////        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, taskTypeSettingsCard);
////        split.setResizeWeight(0.20);   // left takes ~20%
////
////        JPanel root = new JPanel(new BorderLayout());
////        root.setBorder(new EmptyBorder(12, 12, 12, 12));
////        root.add(new JLabel("Task Library"), BorderLayout.NORTH);
////        root.add(split, BorderLayout.CENTER);
////
////        return root;
//    }
//
//    private void showTaskTypeEditor(Action t) {
//        selectedAction = t;
//
//        CardLayout cl = (CardLayout) taskTypeSettingsCard.getLayout();
//        if (t == null) {
//            cl.show(taskTypeSettingsCard, CARD_EMPTY);
//            return;
//        }
//        cl.show(taskTypeSettingsCard, CARD_EDITOR);
//
//        // basic defaults
//        tfDesc.setText(t.name());
//        spLoops.setValue(1);
//
//        // show/hide parameter inputs
//        posPanel.setVisible(t.isTargetTypeTile());
//        areaPanel.setVisible(t.isTargetTypeArea());
//
//        taskTypeSettingsCard.revalidate();
//        taskTypeSettingsCard.repaint();
//    }
//
//    private JComponent buildTabTaskBuilder() {
////        // ----- left list -----
////        actionModel.clear();
////        for (Action t : Action.values())
////            actionModel.addElement(t);
////
////        actionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
////        actionList.setFixedCellHeight(30);
////        actionList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
////
////        JScrollPane leftScroll = new JScrollPane(actionList);
////        leftScroll.setPreferredSize(new Dimension(220, 0));
////
////        // ----- right settings card -----
////        taskTypeSettingsCard.removeAll();
////        taskTypeSettingsCard.add(buildEmptyTaskPanel(), CARD_EMPTY);
////        taskTypeSettingsCard.add(buildTabTaskBuilder(), CARD_EDITOR);
////
////        // default view
////        ((CardLayout) taskTypeSettingsCard.getLayout()).show(taskTypeSettingsCard, CARD_EMPTY);
////
////        // selection handler (fires on click + arrow keys + scrolling selection)
////        actionList.addListSelectionListener(e -> {
////            if (e.getValueIsAdjusting())
////                return;
////            Action selected = actionList.getSelectedValue();
////            showTaskTypeEditor(selected);
////        });
////
////        // ----- split pane -----
////        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, taskTypeSettingsCard);
////        split.setResizeWeight(0.20);   // left takes ~20%
////
////        JPanel root = new JPanel(new BorderLayout());
////        root.setBorder(new EmptyBorder(12, 12, 12, 12));
////        root.add(split, BorderLayout.CENTER);
////        return root;
////        JPanel form = new JPanel(new GridLayout(0, 2, 10, 10));
////        form.setBorder(new EmptyBorder(12, 12, 12, 12));
////
////        form.add(new JLabel("Description"));
////        form.add(tfDesc);
////
////        form.add(new JLabel("Loops"));
////        form.add(spLoops);
////
////        // Position panel
////        posPanel.removeAll();
////        posPanel.add(new JLabel("X")); posPanel.add(spX);
////        posPanel.add(new JLabel("Y")); posPanel.add(spY);
////        posPanel.add(new JLabel("Z")); posPanel.add(spZ);
////
////        // Area panel
////        areaPanel.removeAll();
////        areaPanel.add(new JLabel("X1")); areaPanel.add(spX1);
////        areaPanel.add(new JLabel("Y1")); areaPanel.add(spY1);
////        areaPanel.add(new JLabel("X2")); areaPanel.add(spX2);
////        areaPanel.add(new JLabel("Y2")); areaPanel.add(spY2);
////
////        // Wrap dynamic panels so we can show/hide them
////        JPanel dyn = new JPanel(new BorderLayout(0, 10));
////        dyn.add(posPanel, BorderLayout.NORTH);
////        dyn.add(areaPanel, BorderLayout.CENTER);
////
//        JPanel right = new JPanel(new BorderLayout(12, 12));
////        right.add(form, BorderLayout.NORTH);
////        right.add(dyn, BorderLayout.CENTER);
////
//////        JButton btnCreate = new JButton("Create task");
//////        btnCreate.addActionListener(e -> );
////
////        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
////        //bottom.add(btnCreate);
////        right.add(bottom, BorderLayout.SOUTH);
//
//        return right;
//    }
//
//    private JComponent buildTabSettings() {
//        //return buildMapTab();
//        return new SettingsPanel(bot);
//    }
//
//    private JComponent buildDashMenuAbout() {
//        JLabel title = new JLabel("About");
//        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
//
//        JTextArea body = new JTextArea(
//                "\"This is a Swing UI template with:" +
//                        "\n- JTabbedPane for navigation" +
//                        "\n- CardLayout dashboard sub-pages" +
//                        "\n- GridBagLayout settings form" +
//                        "\n- BorderLayout logs table" +
//                        "\n- BoxLayout about panel\n" +
//
//                        "\nNext upgrades:" +
//                        "\n- theme (FlatLaf), icons, animations, docking sidebar" +
//                        "\n- persistence (save settings to JSON)" +
//                        "\n- real-time charts (custom paint or a chart library)\"");
//        body.setEditable(false);
//        body.setOpaque(false);
//        body.setFont(new Font("Segoe UI", Font.PLAIN, 13));
//
//        ///  create a panel to store all this information on
//
//        JPanel aboutPanel = new JPanel();
//        aboutPanel.setLayout(new BoxLayout(aboutPanel, BoxLayout.Y_AXIS));
//        aboutPanel.setBorder(new EmptyBorder(18, 18, 18, 18));
//        aboutPanel.add(title);
//        aboutPanel.add(Box.createVerticalStrut(10));
//        aboutPanel.add(body);
//        aboutPanel.add(Box.createVerticalGlue());
//
//        return aboutPanel;
//    }
//
//
//
//
//    private Runnable refreshTabTaskLibrary() {
//        return () -> {
//            this.titleTaskLibrary.setText("Task Library"
//                    + "     ||     Total tasks in library: " + bot.getTaskLibraryModel().size()
//                    + "     |     Selected index: " + bot.getLibraryIndex());
//        };
//    }
//
//    protected final void onResume() {
//        btnToggleExecution.setText("⏸");
//    }
//
//    protected final void onPause() {
//        btnToggleExecution.setText("▶");
//    }
//
//    /**
//     * Opens the {@link BotMenu}
//     */
//    protected final boolean open() {
//        //TODO: document code with more of these later @see ^^
//
//        // can't open a nothing!
//        if (bot == null) {
//            return !setBotStatus("Unable to find a valid bot instance...");
//        }
//
//        // try open the bot menu using swing utilies to delay premature loading before BotMan is instantiated.
//        setStatus("Opening BotMenu...");
//        this.showMenu();
//        return isVisible();
//    }
//
//    private void closeMenu() {
//        // can't close what ain't open
//        if (bot == null)
//            return;
//
//        setBotStatus("BotMenu closed!");
//        // dispose of this menu
//        this.dispose();
//    }
//
//
//    /**
//     * Hides the bot menu, preventing the user from interacting with the bot menu
//     */
//    public final void close() {
//        setBotStatus("Closing BotMenu...");
//        // if this is not a forced closed, do some checks before closing the bot menu
//        if (!isVisible())
//            return;
//
//        // if the setting toggle "exit on close" is enabled - stop the bot script.
//        // NOTE: this is called inside !forced closure to prevent infinite loop onExit -> botMenu.close -> onExit
//        if (this.isExitingOnClose) {
//            // exit the bot instead of just closing the menu
//            bot.exit();
//            return;
//        }
//
//        // if setting toggle "hide on exit" is enabled and this is not a forced close
//        if (this.isHidingOnExit) {
//            // hide the menu and exit early to prevent closure
//            this.hideMenu();
//            return;
//        }
//
//        this.closeMenu();
//    }
//
//    public void forceClose() {
//        this.closeMenu();
//    }
//
//    public final void showMenu() {
//        if (this.isVisible())
//            return;
//
//        this.setVisible(true);
//    }
//
//    public void hideMenu() {
//        if (!this.isVisible())
//            return;
//
//        setStatus("Hiding BotMenu...");
//        this.setVisible(false);
//    }
//
//    /**
//     * Helper function to create a titled bordered section to neatly contain components.
//     * <n>
//     * This function is intended to centralize core menu components and to easily change styles later.
//     *
//     * @param title The title of this section.
//     * @return A JPanel section which can be used to neatly contain java swing components.
//     */
//    public static JPanel section(String title) {
//        JPanel p = new JPanel();
//        p.setBorder(BorderFactory.createTitledBorder(
//                BorderFactory.createLineBorder(new Color(200, 200, 200)),
//                title,
//                TitledBorder.LEFT,
//                TitledBorder.TOP,
//                new Font("Segoe UI", Font.BOLD, 13)
//        ));
//        return p;
//    }
//
//
//    /**
//     * Helper function which constructs a stat card which can be used to neatly display information about the player/bot
//     * inside the dashboard's 'status' submenu.
//     *
//     * @param title The title of this stat card.
//     * @param big The bigger text beneath the card title.
//     * @param small The smaller text beneath the card title and big card text.
//     *
//     * @return A {@link JComponent} that can be used to display information in the dashboard "status" submenu.
//     */
//    private JComponent statCard(String title, String big, String small) {
//        JPanel card = new JPanel();
//        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
//        card.setBorder(BorderFactory.createCompoundBorder(
//                BorderFactory.createLineBorder(new Color(200, 200, 200)),
//                new EmptyBorder(12, 12, 12, 12)
//        ));
//
//        JLabel t = new JLabel(title);
//        t.setFont(new Font("Segoe UI", Font.PLAIN, 12));
//        t.setForeground(new Color(90, 90, 90));
//
//        JLabel b = new JLabel(big);
//        b.setFont(new Font("Segoe UI", Font.BOLD, 22));
//
//        JLabel s = new JLabel(small);
//        s.setFont(new Font("Segoe UI", Font.PLAIN, 12));
//        s.setForeground(new Color(90, 90, 90));
//
//        card.add(t);
//        card.add(Box.createVerticalStrut(6));
//        card.add(b);
//        card.add(Box.createVerticalStrut(4));
//        card.add(s);
//        return card;
//    }
//
//    /**
//     * Build a blank panel will an unavailable label in it as a back-up for any menus that aren't yet added.
//     *
//     * @return A blank panel mainly used during development to launch the menu before the BotMenu before submenu
//     * creation.
//     */
//    private JComponent getDefaultPanel() {
//        // create a status panel
//        JPanel defaultPanel = new JPanel(new BorderLayout(12, 12));
//        defaultPanel.setBorder(new EmptyBorder(0, 12, 0, 0));
//
//        // add a status title
//        JLabel label = new JLabel("This feature is not available yet!\nPlease check back soon...");
//        label.setFont(new Font("Segoe UI", Font.BOLD, 16));
//
//        return defaultPanel;
//    }
//
//    private JComponent buildEmptyTaskPanel() {
//        JPanel p = new JPanel(new BorderLayout());
//        JLabel l = new JLabel("Select a task type to edit parameters.");
//        l.setFont(new Font("Segoe UI", Font.PLAIN, 14));
//        p.add(l, BorderLayout.NORTH);
//        return p;
//    }
//
//    /**
//     * Refreshes the BotMenu which safely updates any dynamic labels, lists and displays.
//     * <p>
//     * Warning: This function is called by the setStatus functions, therefore, the log() function should be used to
//     * print here.
//     */
//    public static void refresh() {
//        // fetch the bot man instance (cant be null and will throw error on failure)
//        BotMan bot = BotMan.getInstance();
//
//        ///  Bot menu refresh tasks
//        ///     -- NO SETSTATUS, SETBOTSTATUS OR BOTMENU CONSOLE LOG PRINTS HERE OR IT WILL CAUSE INFINITE RECURSION!
//
//        // only refresh the sections of the bot menu that were added to the runnables list
//        for (Runnable refreshTask : bot.getBotMenu().getRefreshList())
//            BotMan.getInstance().safeRun(refreshTask);
//    }
//
//    /**
//     * Return an {@link ArrayList} of {@link Runnable} objects used to refresh the {@link BotMenu} displays and dynamic labels, etc...
//     *
//     * @return A list of runnable objects used to refresh the bot menu.
//     */
//    private ArrayList<Runnable> getRefreshList() {
//        ///  create a list to group refresh tasks for simpler execution
//        ArrayList<Runnable> refreshList = new ArrayList<>();
//
//        ///  add tasks to refresh bot menu dash menus
//
//        // refresh tasks
//        refreshList.add(refreshDashTasks());
//        // refresh logs
//        refreshList.add(refreshTabLogs());
//
//        /// add tasks to refresh each tab
//
//        // add tasks to refresh task library tab
//        refreshList.add(refreshTabTaskLibrary());
//
//        ///  return the refresh list
//        return refreshList;
//    }
//
//    /**
//     * Dynamically updates all task list information.
//     *
//     * @return A runnable task that can be executed to refresh the "Tasks" dash menu.
//     */
//    private Runnable refreshDashTasks() {
//        return () -> this.titleTaskList.setText("Task List"
//                + "     ||     Total tasks in list: " + bot.getTaskListModel().size()
//                + "     |     Selected task index: " + bot.getSelectedTaskIndex()
//                + "     |     Task progress: " + bot.getTaskProgress()
//                + "     |     Remaining attempts: " + bot.getRemainingAttempts()
//                + "     |     Remaining tasks: " + bot.getRemainingTaskCount()
//                + "     |     Next task: " + bot.getNextTask()
//                + "     |     Task loops: " + bot.getTaskLoopsString()
//                + "     |     List loops: " + bot.getListLoopsString()
//                + "     |     List index: " + bot.getListIndex());
//    }
//
//    private Runnable refreshTabLogs() {
//        return bot.refreshLogMan();
//    }
//
//    /**
//     * Update the bot status to keep the user informed about the script progress.
//     *
//     * @param status The status to display on-screen (if enabled) and in the {@link BotMenu}
//     */
//    private boolean setStatus(String status) {
//        return bot != null && bot.setPlayerStatus(status);
//    }
//
//    private boolean setBotStatus(String botStatus) {
//        return bot != null && bot.setBotStatus(botStatus);
//    }
//}