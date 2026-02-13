//package main;
//
//import main.managers.DataMan;
//import main.managers.*;
//import main.task.Action;
//import main.task.Task;
//import main.tools.ETARandom;
//import org.dreambot.api.methods.container.impl.Inventory;
//import org.dreambot.api.methods.interactive.Players;
//import org.dreambot.api.methods.map.Area;
//import org.dreambot.api.methods.map.Tile;
//import org.dreambot.api.methods.walking.impl.Walking;
//import org.dreambot.api.script.AbstractScript;
//import org.dreambot.api.script.Category;
//import org.dreambot.api.script.ScriptManifest;
//import org.dreambot.api.utilities.Sleep;
//import org.dreambot.api.utilities.impl.Condition;
//import org.dreambot.api.wrappers.interactive.Player;
//import org.dreambot.api.wrappers.items.Item;
//
//import javax.swing.*;
//import java.awt.*;
//import java.io.*;
//import java.util.Arrays;
//import java.util.function.BooleanSupplier;
//import java.util.function.Predicate;
//import java.util.function.Supplier;
//
//import static main.managers.LogMan.LogSource.DEBUG;
//
///**
// * Main handler for botting scripts, designed to minimize repeated code between scripts for common tasks such as
// * walking, inventory checking & tracking, skill tracking, banking, teleporting and equipment management.
// * <p>
// * Base class for all bots. Handles:
// * <p>- onStart / onExit lifecycle</p>
// * <p>- status updates</p>
// * <p>- safe sleep with conditions</p>
// * <p>- optional menu support</p>
// * </p>
// * Generic parameter <T> allows different bots to declare their own menu type.
// * <p>
// * BotMan Loop:
// * <p>- All child scripts inherit BotMan and run in its main loop, so BotMan is like the parent.</p>
// * <p>- Everytime BotMan has control of the thread, it performs quick checks such as low HP checks, scann.</p>
// * <p>- BotMan adds a short delay after every child script (e.g. "FishingMan") either breaks or completes.</p>
// * <p>- Scripts no longer need to return an integer value, instead they sleep/break/return, using the default delay
// * for any standard pauses.</p>
// * <p>- Additional/special delays will require an additional sleep before breaking the script loop (see functions below).</p>
// *
// * @see BotMan#sleep(int)
// * @see BotMan#sleep(long, BooleanSupplier)
// */
//@ScriptManifest(author = "Elayj", name = "DevAsword", version = 1.0, description = "Bot Manager", category = Category.MISC)
//public abstract class BotMan extends AbstractScript {
//    private static final String[] IGNORED_PREFIX = {"java."}; // "jdk.", "org.", "com.", "javax.", "kotlin."
//    private static final String[] IGNORED_FUNC = {"GetCaller", "toString", "refresh", "log"};
//
//    private static final Predicate<String> IS_JAVA_INTERNAL =
//            s -> Arrays.stream(IGNORED_PREFIX).anyMatch(s::startsWith);
//
//    private static final Predicate<String> IS_DEBUGGING_FUN =
//            s -> Arrays.stream(IGNORED_FUNC).anyMatch(s::contains);
//    ///
//    ///     PUBLIC FIELDS
//    ///
//
//    ///
//    ///     PROTECTED FINAL FIELDS
//    ///
//    /**
//     * The maximum attempts allowed to complete a task.
//     */
//    private int MAX_ATTEMPTS = 3;
//    /**
//     * The minimum delay that can be set to prevent the client from lagging out from excessive loops.
//     */
//    private int MIN_DELAY = 350;
//    /**
//     * Placeholder variable for local-hosting later if I can find a work-around osbots security policies or host a
//     * suitable server.
//     */
//    private boolean LOCAL_HOST = false;
//    private static BotMan instance;
//    /**
//     * The log manager, used to handle/display all logging messages/errors.
//     */
//    private LogMan logMan;
//    /**
//     * The window manager, used to detect, manipulate and attach listeners to various windows.
//     */
//    private WindowMan windowMan;
//    /**
//     * The graphics manager, used to draw informative/decorative on-screen graphics (e.g., bot/script overlays).
//     */
//    private GraphicsMan graphicsMan;
//    /**
//     * The task manager, used to submit tasks to the queue, or to remove/manipulate existing tasks.
//     */
//    private TaskMan taskMan;
//    /**
//     * The database manager, used to save/load data to/from the ETA Bot database.
//     */
//    private DataMan dataMan;
//    /**
//     * The settings manager, used to easily manipulate/manage in-game, bot-menu or script-specific settings.
//     */
//    private SettingsMan settingsMan;
//    /**
//     * The bot menu associated with this bot instance - protected since it gains control of player accounts.
//     */
//    private BotMenu botMenu;
//    /**
//     * A short, broad description of what the bot is currently attempting to do. (i.e., what BotMan knows)
//     */
//    private String playerStatus;
//    /**
//     * A short, detailed description of what the bot is currently attempting to do. (i.e., what BotMan's counter-parts
//     * know)
//     */
//    protected String botStatus;
//
//    ///  script settings
//    /**
//     * True if the bot should log out when the script is complete.
//     */
//    private boolean isLogOnStop = false;
//    /**
//     * True if the player is currently in developer mode, which will bypass the attempt counter and enable some extra
//     * features while BotMan is running.
//     */
//    private boolean isDevMode = false;
//    /**
//     * True if debugging mode is currently enabled, else false.
//     */
//    public boolean isDebugging = true;
//    protected boolean isRunning = true;
//    /**
//     * The type of task currently being performed (if any).
//     */
//    protected Action action = Action.WAIT;
//
//    ///
//    ///     PRIVATE FIELDS
//    ///
//    /**
//     * A really short delay which is forced after every child scripts loop (sorry). This guarantees some sort of randomization
//     * in every single script using this framework, lowering ban rates and enables lazy scripting since you don't need
//     * to manually return delays, you can just break the loop instead and a delay is automatically applied.
//     * <p>
//     */
//    private final Supplier<Integer> LOOP_DELAY = ETARandom::getRandReallyShortDelayInt;
//    /**
//     * The current number of attempts taken to perform the current task
//     */
//    //TODO: extract this out into its own class? Or at least track what task is failing for better debugging and to provide building blocks for machine learning
//    private int currentAttempt;
//    /**
//     * The delay in seconds (s) to wait between the end of the current loop, and the start of the next loop.
//     */
//    private int delay = 1;
//
//    // monitor tracking (for later user preferences)
//    private int screenCount = 1;          // total monitors detected
//    private int botScreen = 0;     // which monitor OSBot is on (best-effort)
//    private int menuScreen = 0;      // which monitor we chose for BotMenu
//    private int preferredMenuScreen = -1; // optional: user preference (0-based). -1 = auto
//
//
//    ///
//    ///     CONSTRUCTORS
//    ///
//    /**
//     * Constructs a bot instance (without a bot menu) which can be used to execute pre-written or task-based scripts, or
//     * for testing purposes.
//     *
//     * @see TaskMan
//     */
//    public BotMan() {}
//
//    ///
//    ///     PARENT FUNCTIONS: OSBOT API (SCRIPT) OVERRIDES
//    ///
//    /**
//     * The starting point of all scripts, used to initialize objects that basically all scripts will need. The more
//     * task-specific functions will have a script that inherits these base-functions and tailors them to their needs.
//     */
//    @Override
//    public final void onStart() {
//        try {
//            log("Launching ETA BotMan instance...");
//            // provide a static reference to this class on instantiation to provide access for static methods like Log()
//            instance = this;
//
//            /// setup managers
//
//            setBotStatus("Creating LogMan...");
//            logMan = new LogMan(this);
//
//            setBotStatus("Creating WindowMan...");
//            windowMan = new WindowMan();
//
//            setBotStatus("Creating GraphicsMan...");
//            // create a new graphics manager to draw on-screen graphics, passing an instance of this bot for easier value reading.
//            graphicsMan = new GraphicsMan(this);
//
//            setBotStatus("Creating TaskMan...");
//            // initiates a new task manager which can optionally queue tasks one after the other, later allowing for scripting from the menu and AI automation
//            taskMan = new TaskMan();
//
//            setBotStatus("Creating DataMan...");
//            // initiates a new database manager which can be used to save/load data to remember settings and tasks etc.
//            dataMan = new DataMan();
//
//            setBotStatus("Creating SettingsMan...");
//            // initiates a new settings manager to easily manipulate/manage settings
//            settingsMan = new SettingsMan(this);
//
//            safeRun(() -> {
//                setBotStatus("Creating BotMenu...");
//                botMenu = new BotMenu(this);
//
//                setPlayerStatus("Successfully loaded managers!");
//
//                ///  setup listeners
//
//                setupListeners();
//                setPlayerStatus("Successfully loaded listeners!");
//            });
//
//            ///  setup child classes
//
//            // force-load child scripts to prevent accidental overrides
//            // (only load children after loading managers since children use managers)
//            setBotStatus("Checking children...");
//            if (!onLoad())
//                throw new RuntimeException("Failed to load child script!");
//            setPlayerStatus("Successfully loaded children!");
//
//            ///  set default field values
//
//            // reset current attempts
//            currentAttempt = 1;
//            setPlayerStatus("Successfully loaded defaults!");
//
//            ///  setup menu items
//
//            setBotStatus("Setting up menu items...");
//            isLogOnStop = false; // TODO setup checkbox in menu or constructor to change this value
//            setPlayerStatus("Successfully loaded menu items!");
//
//            setPlayerStatus("Initialization complete!");
//
//            ///  load settings either via local host or supabase server
//
//            setBotStatus("Attempting to load settings...");
//            settingsMan.loadSettings();
//
//            //log(getCaller());
//
//            // pause the script to prevent the character prematurely taking off before scripts are set
//            onPause();
//
//        } catch (Throwable t) {
//            log("Error Initializing BotMan: " + t);
//        }
//    }
//
//    /**
//     * The main loop for everything responsible for this bot instance. This loop runs forever, checking for tasks to
//     * complete which are submitted by the script-user, to the {@link TaskMan}, via the {@link BotMenu}.
//     * <p>
//     * This class provides access to OsBot default script functions as well as some extra functions and a menu to enhance
//     * your botting experience, making it possible to create scripts with better documentation and improved functionality
//     * for simple, flexible and modular scripting all without requiring any coding knowledge.
//     * <p>
//     * This loop uses attempts to prevent scripts getting stuck in loops. A default attempt limit is preset while the
//     * attempt count. Once the attempts value exceeds MAX_ATTEMPTS this script will exit automatically.
//     *
//     * @return An integer value denoting the time in milliseconds (ms) to wait between loop cycles.
//     */
//    @Override
//    public int onLoop() {
//        if (isRunning()) {
//            try {
//                // randomize delay every loop to prevent duplicate sleeps (faster detection)
//                delay = LOOP_DELAY.get();
//                // perform safety checks to prevent penalties such as bot detection, player losses or death etc.
//                if (!isSafeToBot())
//                    throw new RuntimeException("[BotMan Error] Unsafe to bot!! Check logs for more information...");
//
//                setPlayerStatus("Reading task list...");
//                // double check attempts before attempting to complete the next stage/task
//                if (currentAttempt <= MAX_ATTEMPTS)
//                    // attempt to complete a stage/task
//                    return attempt();
//                    // if no attempts left, player must be stuck or bug found - pause bot to safe player going haywire
//                else onPause();
//
//            } catch(RuntimeException i){
//                if (i.getMessage() != null)
//                    setPlayerStatus(i.getMessage());
//                return checkAttempts();
//            } catch (InterruptedException ex) {
//                throw new RuntimeException(ex);
//            }
//
//            return delay;
//        }
//
//        return 600;
//    }
//
//    /**
//     * Attaches refresh() events to any windows that need updating, adds list handlers to menu lists for an updated
//     * display and attaches on close events to windows to trigger custom logic when they are exited by the user.
//     */
//    private void setupListeners() {
//        ///
//        ///  Setup BotMenu Listeners:
//        ///
//
//        ///  list listeners - attach listeners to lists to reflect changes in bot menu
//
//        // refresh bot menu anytime the task list is manipulated (index change, add, remove)
//        windowMan.attachMenuListListeners(getTaskList());
//        // refresh bot menu anytime the task library is manipulated (index change, add, remove)
//        windowMan.attachMenuListListeners(getTaskLibrary());
//
//        ///  onClose() events
//
//        // call the bot menu on close function whenever the user exits the menu (via window 'x' button)
//        windowMan.attachOnCloseEvent(botMenu, () -> botMenu.close());
//
//        ///  refresh() events
//
//        ///
//        ///  Setup Bot Listeners:
//        ///
//
//        ///  HP changed event
//        ///  Prayer changed event
//        ///  Player died event
//        ///  Client left click event
//        ///  Client right click event
//        ///  Client type event
//        ///  Level up event
//        ///  New loot event
//    }
//
//    public Runnable refreshLogMan() {
//        return () -> logMan.callRefresh();
//    }
//
//    /**
//     * Return the current attempt as an {@link Integer} value.
//     *
//     * @return The current attempt count.
//     */
//    public int getCurrentAttempt() {
//        return this.currentAttempt;
//    }
//
//    /**
//     * Return the remaining attempts by subtracting the current {@link BotMan#currentAttempt} from the
//     * {@link BotMan#MAX_ATTEMPTS}.
//     *
//     * @return An {@link Integer} value denoting the remaining {@link BotMan#currentAttempt} for this cycle.
//     */
//    public int getRemainingAttempts() {
//        return (getMaxAttempts() - getCurrentAttempt());
//    }
//
//    public String getRemainingAttemptsString() {
//        return getCurrentAttempt() + "/" + getMaxAttempts();
//    }
//
//    public int getRemainingTaskCount() {
//        return taskMan.getRemainingTaskCount();
//    }
//
//    public int checkAttempts() {
//        try {
//            // increment the attempt everytime it is checked for external try/catches to call
//            currentAttempt++;
//            // exit if attempt limit has been exceeded
//            if (getCurrentAttempt() > getMaxAttempts()) {
//                if (isDevMode)
//                    currentAttempt = 1;
//                else {
//                    log("[BotMan Error] Maximum attempt limit exceeded!");
//                    onPause();
//                }
//                return MIN_DELAY;
//
//                // else, increase the delay time with each failed attempt to give the user/player time to correct the mistake
//            } else delay = LOOP_DELAY.get() * (getCurrentAttempt() * 2);
//
//            setPlayerStatus("Trying again after " + delay / 1000 + "s");
//        } catch (Exception e) {
//            this.exit(e.getMessage());
//            return 0;
//        }
//
//        return delay;
//    }
//
//    public boolean walkTo(Tile position) {
//        setPlayerStatus("Walking to: " + position.getArea(1));
//        return Walking.walk(position);
//    }
//
//    /**
//     * Walks to the specified {@link Tile}.
//     *
//     * @param position The {@link Tile} to walk to.
//     * @param name The name of the {@link Area} to walk to for display purposes.
//     * @return True if the player arrives at the destination.
//     */
//    public boolean walkTo(Tile position, String name) {
//        setPlayerStatus("Walking to: " + name);
//        return Walking.walk(position);
//    }
//
//    /**
//     * Walks to the specified {@link Area}.
//     *
//     * @param area The {@link Area} to walk to.
//     * @param name The name of the {@link Area} to walk to for display purposes.
//     * @return True if the player arrives at the destination.
//     */
//    public boolean walkTo(Area area, String name) {
//        setPlayerStatus("Walking to: " + name);
//        return Walking.walk(area);
//    }
//
//    /**
//     * Check if the player's inventory currently contains the passed item.
//     *
//     * @param itemName The name of the {@link Item} to check for.
//     * @return True if the player's inventory currently contains the passed item.
//     */
//    public boolean hasInvItem(String itemName) {
//        if (itemName == null || itemName.isEmpty())
//            return false;
//
//        return Inventory.get(itemName) != null;
//    }
//
//    /**
//     * Check if the player's inventory currently contains the passed items.
//     *
//     * @param itemNames The name of each {@link Item} to check for.
//     * @return True if the player's inventory currently contains ALL of the passed items.
//     */
//    public boolean hasInvItems(String... itemNames) {
//        return Inventory.containsAll(itemNames);
//    }
//
//    /**
//     * Fetch the passed item from the players inventory.
//     *
//     * @param itemName The name of the {@link Item} to fetch.
//     * @return The {@link Item} fetched from the players inventory.
//     */
//    public Item getInvItem(String itemName) {
//        return Inventory.get(itemName);
//    }
//
//    /**
//     * Creates a custom exception to handle errors for better debugging. This will also allow me to create some
//     * functions later which create new tasks to prevent failure, which I can then plugin to machine learning models to
//     * self-train based on mistakes (with this being treated as the punishment/failure zone).
//     */
//    public static class TaskFailedException extends RuntimeException {
//        public TaskFailedException(BotMan bot, String message) {
//            super(message);
//            bot.setPlayerStatus(message);
//            bot.setBotStatus("Thinking...");
//        }
//    }
//
//    /**
//     * Overrides the default pause function to execute additional logic before pausing the script.
//     * <p>
//     * WARNING: This function is not intended to be used to pause the script, only to add additional logic to the pause.
//     */
//    public final void onPause() {
//        if (isRunning()) {
//            isRunning = false;
//            // run this logic on the swing edt using safeRun() func. to ensure bot menu button is updated
//            setBotStatus("Pausing script...");
//            pauseScript();
//            if (botMenu != null)
//                botMenu.onPause();
//            setPlayerStatus("Script paused.");
//        }
//    }
//
//    /**
//     * Overrides the default resume function to execute additional logic before resuming the script.
//     */
//    public final void onResume() {
//        // run this logic on the swing edt using safeRun() func. to ensure bot menu button is updated
//        safeRun(() -> {
//            isRunning = true;
//            setBotStatus("Resuming script...");
//            botMenu.onResume();
//            resumeScript();
//            setPlayerStatus("Thinking...");
//        });
//    }
//
//    /**
//     * Function used to execute some code before the script stops, useful for last-minute guaranteed disposal.
//     */
//    @Override
//    public final void onExit() {
//        // block menu closing twice (due to OSBot calling onExit() twice under the hood)
//        if (botMenu != null)
//            // force-close the bot menu (forcing prevents infinite loop)
//            botMenu.forceClose();
//
//        Log("Successfully exited ETA's Bot Manager");
//    }
//
//    /**
//     * Force exits the bot manager, stopping the main loop and closing any open bot menu.
//     */
//    public final void exit(String exitMsg) {
//        if (exitMsg != null && !exitMsg.isEmpty())
//            log(exitMsg);
//        stop();
//    }
//
//    public final void exit() {
//        exit(null);
//    }
//
//    ///
//    ///     CHILD FUNCTIONS: FORCED OVERRIDES
//    ///
//
//    public abstract boolean onLoad();
//
//    ///
//    ///     CHILD FUNCTIONS: OPTIONAL OVERRIDES
//    ///
//
//    /**
//     * Optional overridable function that gets called when the main script is paused, allowing children to set a 'pause'
//     * state, such as menu buttons changing text/color/availability for example, or a bot making sure it's safe? //TODO test if this, might be lying
//     */
//    public void pauseScript() {}
//    public void resumeScript() {}
//
//    ///
//    ///     STATIC HELPERS
//    ///
//
//    /**
//     * This function provides a global way to log messages to the console and menu where possible, as long as a valid
//     * instance can be created.
//     *
//     * @param msg The message to log to the console.
//     */
//    public static void Log(String msg) {
//        try {
//            BotMan bot = BotMan.getInstance();
//            bot.log(msg);
//        } catch (Exception e) {
//            throw new RuntimeException("Error logging global message: " + msg);
//        }
//    }
//
////    public static void Log(LogMan.LogEntry entry) {
////        try {
////            BotMan bot = BotMan.getInstance();
////            bot.log(entry.toString());
////        } catch (Exception e) {
////            throw new RuntimeException("Error logging global log entry: " + entry);
////        }
////    }
//
//    public static void Log(LogMan.LogSource source, String... msg) {
//        try {
//            BotMan bot = BotMan.getInstance();
//            if (bot.logMan != null)
//                bot.logMan.log(source, msg);
//
//        } catch (Exception e) {
//            throw new RuntimeException("Error logging global message");
//        }
//    }
//
//    public static String getSettingsJSON() {
//        return getInstance().settingsMan.getSettingsJSON();
//    }
//
//    ///
//    ///     GETTERS/SETTERS
//    ///
//
//    public Player getPlayer() {
//        return Players.getLocal();
//    }
//
//    public int getX() {
//        return getPlayer().getX();
//    }
//
//    public int getY() {
//        return getPlayer().getY();
//    }
//
//    public int getZ() {
//        return getPlayer().getZ();
//    }
//
//    /**
//     * @return A private reference to this {@link BotMan} instance for static access within this class.
//     */
//    static BotMan getInstance() {
//        if (instance == null)
//            throw new RuntimeException("BotMan instance fetched while BotMan was null!");
//        return instance;
//    }
//
//    /**
//     * Queries the ETA Bot server to return all settings for the current player.
//     *
//     * @return The players settings as a {@link String} in JSON format.
//     */
//    public String downloadSettings() throws IOException {
//        setGetStatus("Downloading player settings...");
//        String settings = dataMan.getServerSettings("*");
//
//        logMan.log(LogMan.LogSource.GET, "Successfully fetched player settings from server!",
//                "Player: "+ Players.getLocal().getName(),
//                "Settings: " + settings);
//
//        return settings;
//    }
//
//    /**
//     * Return true if this script is currently executing.
//     */
//    protected boolean isRunning() {
//        return isRunning;
//    }
//
//    /**
//     * Returns the current {@link BotMenu} instance associated with this {@link BotMan}
//     */
//    public final BotMenu getBotMenu() {
//        return this.botMenu;
//    }
//
//    /**
//     * Returns a short, broad description of what the bot is currently attempting to do.
//     */
//    public final String getPlayerStatus() { return playerStatus; }
//
//    public final String getBotStatus() { return botStatus; }
//
//    public final JComponent getTabLogs() {
//        return logMan.buildTabLogs();
//    }
//
//    public final JPanel getDashMenuTasks(JLabel label) {
//        return taskMan.buildDashMenuTasks(label);
//    }
//
//    public final Task getTask() {
//        return taskMan == null ? null : taskMan.getTask();
//    }
//
//    public final Task getNextTask() {
//        return taskMan.getNextTask();
//    }
//
//    public final int getCompletedTaskLoops() {
//        Task task = getTask();
//        return task == null ? -1 : task.getLoop();
//    }
//
//    public final String getTaskLoopsString() {
//        Task task = getTask();
//        return task == null ? "?" : task.getLoopsString();
//    }
//
//    public final String getListLoopsString() {
//        Task task = getTask();
//        return task == null ? "?" : taskMan.getLoopsString();
//    }
//
//    /// Getters/setters: bot menu
//
//    ///  script
//
//    public boolean isDrawing() {
//        return settingsMan.isDrawingOverlays();
//    }
//
//    public void isDrawing(boolean draw) {
//        settingsMan.setDrawingOverlays(draw);
//    }
//
//    ///  Getters/setters: dev console
//
//    public void setDevMode(boolean devMode) {
//        isDevMode = devMode;
//        setBotStatus("Dev mode: " + (isDevMode ? "Enabled" : "Disabled"));
//    }
//
//    public boolean isDevMode() {
//        return isDevMode;
//    }
//
//    public final void setMaxAttempts(int attempts) {
//        this.MAX_ATTEMPTS = attempts;
//    }
//
//    public final int getMaxAttempts() {
//        return this.MAX_ATTEMPTS;
//    }
//
//    ///  Tasks
//
//    public DefaultListModel<Task> getTaskListModel() {
//        return taskMan.getTaskListModel();
//    }
//
//    /**
//     * @return the {@link JList Jlist} that is used to display the {@link DefaultListModel task-list} model.
//     */
//    public JList<Task> getTaskList() {
//        return taskMan.getTaskList();
//    }
//
//    public DefaultListModel<Task> getTaskLibraryModel() {
//        return taskMan.getTaskLibraryModel();
//    }
//
//    public JList<Task> getTaskLibrary() {
//        return taskMan.getTaskLibrary();
//    }
//
//    public final void setTaskDescription(String description) {
//        this.getNextTask().setDescription(description);
//    }
//
//    public final String getTaskDescription() {
//        if (taskMan.hasTasks())
//            if (getNextTask() != null)
//                return getNextTask().getDescription();
//        return null;
//    }
//
//    public final float getTaskProgress() {
//        return taskMan.getTaskProgress();
//    }
//
//    public void setTaskListIndex(int index) {
//        //setBotStatus("Setting list index to : " + index);
//        taskMan.setTaskListIndex(index);
//    }
//    public final int getListIndex() {
//        return taskMan.getListIndex();
//    }
//
//    public final int getLibraryIndex() { return taskMan.getLibraryIndex(); }
//
//    ///
//    ///     MAIN FUNCTIONS
//    ///
//
//    protected boolean isSafeToBot() {
//        setBotStatus("Checking hp level...");
//        // if player hp is below threshold && check hp enabled
//        // heal
//        // check player prayer level && check prayer enabled
//        // restore prayer
//        // check player in combat
//        // avoid combat/fight back
//        // check nearby players (if enabled)
//        // hop worlds
//        // check nearby loot
//        // loot
//        // add to loot tracker/table on success
//        // check runtime below preset/maximum
//        // logout for a period of time if so
//        // draw extra things here, like penises.
//
//        return true;
//    }
//
//    /**
//     * Returns true on the completion of any task or list of tasks, regardless of their remaining loops, else returns
//     * false for completed stages. Errors should be thrown and caught in the main {@link BotMan} loop.
//     *
//     * @return An integer value denoting the recommended delay time for this task.
//     */
//    protected int attempt() throws InterruptedException {
//        // attempt to complete the next stage of this task, return true on completed task/list loops, else false.
//        if (taskMan.call(this)) {
//            ///  Logic executed after the completion of a task or list loop.
//            delay = LOOP_DELAY.get();
//        } else {
//            ///  Logic executed after the completion of a task stage.
//            delay = LOOP_DELAY.get() / 10;
//        }
//
//        // only reset attempts on success, errors should skip this step and get triggered by the attempt count,
//        currentAttempt = 1;
//
//        setPlayerStatus("Thinking...");
//        return delay;
//    }
//
//    /**
//     * Submits a task to the {@link TaskMan task manager} for execution.
//     */
//    public final void addTask(Task... tasks) {
//        if (tasks == null)
//            return;
//
//        for (Task t : tasks) {
//            if (t != null)
//                taskMan.add(t);
//        }
//    }
//
//    /**
//     * Removes a task from the {@link TaskMan task managers} task list.
//     */
//    public final void removeTask(int index) {
//        taskMan.removeTask(index);
//    }
//
//    /**
//     * Fetches the index of the {@link } currently selected in the task list in the bot menus "tasks" sub-menu.     *
//     *
//     * @return An {@link Integer} value representing the selected index of the task list.
//     */
//    public final int getSelectedTaskIndex() {
//        return taskMan.getSelectedIndex();
//    }
//
//    public void log(String message) {
//        if (message == null || message.isEmpty())
//            return;
//
//        // if message is pre-formatted, print and return early as it's probably been called by logman already distributed
//        if (isValidSource(message) || message.startsWith("\n=> ")) {
//            super.log(message.trim().replace("\t", " "));
//        }
//        // if log manager can process
//        else if (logMan != null)
//            // send to log manager for processing before returning here and getting yoinked by if statement above
//            logMan.log(DEBUG, message);
//    }
//
//    private boolean isValidSource(String message) {
//        if (logMan != null) {
//            // can safely declare a default log source now that its been null checked above
//            LogMan.LogSource source = DEBUG;
//            // try split the string
//            String[] values = message.split("[\\[\\]]");
//            // if split seems correct, try convert value into a LogSource object by comparing string against enum
//            if (values.length >= 3) {
//                // this line will throw an error if log source doesn't exist in the LogSource enum - forcing a DEBUG print
//                try {
//                    LogMan.LogSource.valueOf(values[1]);
//                    return true;
//                } catch (Exception e) {}
//            }
//        }
//
//        return false;
//    }
//
//    /**
//     * Logs the bots status updates to the console/overlay manager (if enabled).
//     * <p>
//     * This function returns a boolean value to help create single line return statements for concise code, see below:
//     *
//     * <pre>{@code
//     * if (true)
//     * // set status to "..." and return true
//     * return setStatus("...")
//     * else
//     * // set status to "..." and return false
//     * return !setStatus("...")
//     * }
//     */
//    public boolean setPlayerStatus(String playerStatus) {
//        setStatus(LogMan.LogSource.PLAYER, playerStatus);
//        // bot player status variable for later reference
//        this.playerStatus = playerStatus;
//        return true;
//    }
//
//    public boolean setBotStatus(String botStatus) {
//        setStatus(LogMan.LogSource.BOT, botStatus);
//        // update bot status variable for later reference
//        this.botStatus = botStatus;
//        return true;
//    }
//
//    public boolean setPostStatus(String postStatus) {
//        return setStatus(LogMan.LogSource.POST, postStatus);
//    }
//
//    public boolean setPatchStatus(String patchStatus) {
//        return setStatus(LogMan.LogSource.PATCH, patchStatus);
//    }
//
//    public boolean setGetStatus(String getStatus) {
//        return setStatus(LogMan.LogSource.GET, getStatus);
//    }
//
//    public boolean setStatus(LogMan.LogSource source, String status) {
//        // no point in printing nothing!
//        if (status != null && status.isEmpty() || source == null)
//            return false;
//
//        if (botMenu != null)
//            // update bot menu console log
//            logMan.log(source, status);
//
//        // always return true for one-line return statements
//        return true;
//    }
//
//    ///
//    ///     MAIN FUNCTIONS
//    ///
//
//    /**
//     * Toggles the execution mode of the script (i.e., if the script is running, this function will pause it)
//     */
//    public final void toggleExecutionMode() {
//        if (isRunning()) {
//            this.onPause();
//        } else {
//            this.onResume();
//        }
//    }
//
//    /**
//     * Repeatedly sleep for the specified timeout until the passed condition is satisfied (returns true).
//     *
//     * @param timeout The specified amount of time to sleep between checks in milliseconds (ms)
//     * @param condition A boolean condition that will be checked once the timeout is executed.
//     * @return True if the sleep is successful, else return false.
//     */
//    public boolean sleep(long timeout, BooleanSupplier condition) {
//        return Sleep.sleepUntil(condition::getAsBoolean, timeout);
//    }
//
//    /**
//     * Helper function for quick sleeps without any conditional requirement to check.
//     *
//     * @param timeout An {@link Integer int} denoting the length of time to sleep for.
//     * @return True of the sleep was successful, else returns false.
//     */
//    public boolean sleep(int timeout) {
//        Sleep.sleep(timeout);
//        return true;
//    }
//
//    ///
//    ///     Instanced helper functions
//    ///
//
//    public void safeRun(Runnable r) {
//        if (SwingUtilities.isEventDispatchThread())
//            r.run();
//        else
//            SwingUtilities.invokeLater(r);
//    }
//
//    ///
//    ///  Static helper functions
//    ///
//
//    /**
//     * Provides global access to the username of the account that this ETA bot instance is controlling.
//     *
//     * @return The player name for the account that the current ETA bot instance is controlling.
//     */
//    public static String GetPlayerName() {
//        if (instance == null)
//            throw new RuntimeException("Attempted to fetch the name of a null instance!");
//        return Players.getLocal().getName();
//    }
//
//    public String getCaller() {
//        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
//
//        for (int i = 2; i < stack.length; i++) {
//            String cls =  stack[i].getClassName();
//            String method = stack[i].getMethodName();
//
//            if (!IS_JAVA_INTERNAL.test(cls) && !IS_DEBUGGING_FUN.test(method))
//                return Format(stack[i]) + "\n raw stack: " + stack[i] + "\n  method:" + stack[i].getMethodName();
//        }
//        return "[Unknown Caller]";
//    }
//
//    public static String GetCaller() {
//        return getInstance().getCaller();
//    }
//
//    private static String Format(StackTraceElement e) {
//        String cls = e.getClassName();
//        cls = "[" + cls.substring(cls.lastIndexOf('.') + 1);
//        String method = e.getMethodName();
//        method = method.replace("lambda$", "").replace("$0", "");
//        return cls + "." + method + "():" + e.getLineNumber() + "]";
//    }
//
//    ///
//    ///  Abstract functions
//    ///
//    protected abstract void paintScriptOverlay(Graphics2D g);
//}