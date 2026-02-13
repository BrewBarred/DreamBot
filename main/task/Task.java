//package main.task;
//
//import main.BotMan;
//import main.BotMenu;
//import main.managers.TaskMan;
//import org.dreambot.api.methods.map.Area; // OSBot Area -> DreamBot Area
//import org.dreambot.api.methods.map.Tile; // Tile -> Tile
//import org.dreambot.api.methods.skills.Skill; // Skill remains Skill but from DreamBot API
//import org.dreambot.api.methods.skills.Skills;
//
//import javax.swing.*;
//import java.util.function.BooleanSupplier;
//
//public abstract class Task {
//    /**
//     * The maximum number of loops allowed per task.
//     */
//    private static final int MAX_TASK_LOOPS = 100;
//    /**
//     * The default radius for the target area (measured in tiles in every direction of the player).
//     */
//    private static final int DEFAULT_RADIUS = 20;
//    /**
//     * The type of task currently being performed.
//     */
//    private final Action type;
//    /**
//     * A short description broadly describing the {@link Task} at hand.
//     */
//    private String description = null;
//    /**
//     * The target {@link Area} in which this {@link Task} should be performed.
//     */
//    protected Area area;
//    /**
//     * The condition attached to this task, if this condition become true, the loop will be broken.
//     */
//    private BooleanSupplier condition;;
//
//    // optional attributes adjusted by children
//    protected Tile position; // Tile -> Tile
//
//    // menu items
//    /**
//     * Loop index is 0-based for direct list references. (i.e., loop 0 = completing first loop, loop 1 = 1 loop
//     * completed. MAX_TASK_LOOPS = 2 will execute loop 0 and 1, then the loop 2 will not be less than 2, and break.)
//     *
//     * @see #MAX_TASK_LOOPS
//     */
//    private int loop = 0;
//    /**
//     * Loops must be a minimum of 1, as there is no point in adding a Task if you want to complete it 0 times.
//     */
//    private int loops = 1;
//
//    /**
//     * The stage that this {@link Task} is currently up to. This stage feature allows the bot to stop in the middle of a
//     * task, then pick up (at least, roughly) where it left off, allowing for smoother, less detectable, more versatile,
//     * task, then pick up (at least, roughly) where it left off, allowing for smoother, less detectable, more versatile,
//     * and safer botting.
//     */
//    protected int stage = 1;
//
//    /**
//     * The total number of stages involved with this particular {@link Task}. If the current {@link Task#stage} is equal
//     * to the maximum stages, then this {@link Task} must be on its last step. Once the {@link Task#stage} value exceeds
//     * the stages value, the Task must have been executed successfully.
//     */
//    private int stages = 1;
//    public boolean isUrgent = false;
//
//    /**
//     * Every task has a type and a description for on-screen overlays. The task type helps describe the status of the
//     * bot, e.g., "Walking to...", "Checking stock...", "Selling x swordfish...". The description is more independent
//     * and provides broader information to the user, e.g., "Selling fish to Gerrant's shop".
//     *
//     * @param type The type of action being performed.
//     * @param description An informative description of the action being performed.
//     */
//    protected Task(Action type, String description) {
//        ///  define default variables (bot menu settings)
//        this.type = type;
//        this.description = description;
//        this.stages = getStages();
//        // get loops
//        // get condition
//        // get currentLoop
//        // get maxLoop
//        // get isComplete
//        // get taskProgress
//        // get stage (action/index in the action list)
//
//        //TODO: (consider adding) this.stageOneLoops = loops[1]; // repeat stage one of a given task x amount of times
//
//        // add this new task into the task library so the user can add it to the task list
//        TaskMan.updateTaskLibrary(this);
//    }
//
//    public final Action getType() {
//        return type;
//    }
//
//    public final void setCondition(BooleanSupplier condition) {
//        this.condition = condition;
//    }
//
//    public final BooleanSupplier getCondition() {
//        return this.condition;
//    }
//
//    /**
//     * Set the remaining loop count for this {@link Task}.
//     *
//     * @param loops An {@link Integer} value denoting the number of times in which this task will be repeated.
//     */
//    public final void setLoops(int loops) {
//        // maximum loops must be set to at least one, because there is no point in adding something and doing it 0 times.
//        if (loops < 1)
//            throw new RuntimeException("[Task Error] Failed to set task loops, value too low: " + loops);
//
//        // check loops less than max loop count (either free version limit e.g. 100 loops or MAX_INTEGER)
//        if (loops > MAX_TASK_LOOPS)
//            throw new RuntimeException("[Task Error] Failed to set task loops, maximum loops (" + MAX_TASK_LOOPS + ") exceeded!");
//
//        // update loop count/reset current loop to start loop count again
//        this.loops = loops;
//        this.loop = 0;
//    }
//
//    public final int getLoop() {
//        return loop;
//    }
//
//    public final int getLoops() {
//        return loops;
//    }
//
//    /**
//     * @return A string containing the current/total loops remaining for this task.
//     */
//    public final String getLoopsString() {
//        return getLoop() + "/" + getLoops();
//    }
//
//    /**
//     * Returns the number of loops left for this task until it should be reset or flagged as complete based on the
//     * {@link Task#MAX_TASK_LOOPS}
//     *
//     * @return The loop count as an int.
//     */
//    public final int getRemainingTaskLoops() {
//        return getLoops() - getLoop();
//    }
//
//    /**
//     * @return True if this task has completed all of its loops or if the end condition is satisfied.
//     */
//    public final boolean isComplete() {
//        // tasks are complete if the end condition is satisfied, or all stages and task loops are done
//        return hasMetEndCondition() || hasCompletedStages() && hasCompletedLoops();
//    }
//
//    /**
//     * @return True if this task has loops left to execute, else returns false.
//     */
//    public final boolean hasCompletedLoops() {
//        return getLoop() >= getLoops();
//    }
//
//    public final boolean isReadyToLoop() {
//        return hasCompletedStages() && !hasCompletedLoops();
//    }
//
//    /**
//     * @return True if this task loop has completed execution, else returns false.
//     */
//    public final boolean hasCompletedStages() {
//        // task is complete when current stage exceeds or equals total stages since this is checked AFTER execution.
//        return getStage() >= getStages();
//    }
//
//    /**
//     * @return The number of stages required to complete this task.
//     */
//    public final int getRemainingStages() {
//        return stages - stage;
//    }
//
//    /**
//     * @return True if there are stages left to complete this task loop, else returns false if all stages are complete.
//     */
//    public final boolean hasStagesLeft() {
//        return getRemainingStages() < 1;
//    }
//
//    /**
//     * @return True if this task has satisfied its end condition, else returns false.
//     */
//    public final boolean hasMetEndCondition() {
//        return getCondition() != null && getCondition().getAsBoolean();
//    }
//
//    /**
//     * Returns the progress of this task as a {@link Integer} value representing the completion percentage out of 100.
//     */
//    public final int getProgress() {
//        // TODO: get graphicsMan to call this progress value and print it
//        return Math.min((int) ((stage * 100.0) / Math.max(1, stages)), 100);
//
//    }
//
//    /**
//     * Repeat the task X times
//     * */
//    public final Task loop(int times) {
//        setLoops(times);
//        return this;
//    }
//
//    /** Run until a custom condition has been met */
//    public Task until(BooleanSupplier condition) {
//        setCondition(condition);
//        return this;
//    }
//
//    /**
//     * Run this {@link Task} until the passed {@link Skill} reaches the passed level.
//     *
//     * @param bot The bot performing the {@link Task}.
//     * @param skill The target {@link Skill} to level.
//     * @param target The desired level for that skill (between 1-126).
//     * @return An executable {@link Task}.
//     */
//    public Task until(BotMan bot, Skill skill, int target) {
//        if (target > 1 && target < 126) {
//            this.setCondition(() -> Skills.getRealLevel(skill) >= target); // virtualLevel -> getRealLevel
//            return this;
//        }
//
//        else throw new IllegalArgumentException("Level must be between 1 and 126");
//    }
//
//    /**
//     * Runs the task, first by relocating to the error, validating the players location, and then executing a one of the
//     * {@link Task}s stages - as defined by the task-maker.
//     *
//     * @return True on successful execution, else returns false.
//     */
//    public boolean run(BotMan bot) throws InterruptedException {
//        if (bot == null)
//            throw new RuntimeException("[Task Error] Failed to run task! Bot was null");
//
//        // if a target area has been provided for this task, ensure the player is inside
//        if (this.area != null && !this.area.contains(bot.getPlayer().getTile())) { // myTile() -> getLocalPlayer().getTile()
//            Tile targetTile = this.area.getRandomTile(); // getRandomTile -> getRandomTile
//            bot.setBotStatus("Walking to " + targetTile);
//            bot.walkTo(targetTile); // webWalk -> walk
//        }
//
//        // ensure the player is in the correct position before completing task.
//        else if (this.position != null && !this.position.equals(bot.getPlayer().getTile())) { // myTile() -> getLocalPlayer().getTile()
//            bot.setBotStatus("Tileing player at " + this.position);
//            bot.walkTo(position.getArea(1).getRandomTile()); // webWalk -> walk
//        }
//
//        ///
//        ///     Reset task loops and execute logic after/between stages
//        ///
//
//        // execution returns true when last stage is successfully compeleted
//        if (execute(bot)) {
//            // increment loops here as this is where we know the task successfully finished.
//            incrementTaskLoop();
//            if (isComplete()) {
//                bot.setBotStatus("Task complete!");
//                onTaskCompletion();
//                restart();
//                // refresh botMenu to update any loop/attempt counters
//                bot.getBotMenu().refresh();
//                return true;
//            } else {
//                bot.setBotStatus("Task loop complete!");
//                ///  logic on task completion
//                onTaskLoopCompletion();
//            }
//            // else, on stage completion the bot returns false and comes here, any errors should be caught by exceptions
//        } else {
//            bot.setBotStatus("Task stage complete!");
//            ///  logic on stage completion (everything else should throw an error)
//            onStageCompletion();
//        }
//
//        // refresh botMenu to update any loop/attempt counters
//        bot.getBotMenu().refresh();
//        bot.log("Task stage (after): " + getStageString()
//                + "  |  Task Loops: " + getLoopsString()
//                + "  |  List Loops: " + bot.getListLoopsString()
//                + "  |  List index: " + bot.getListIndex()
//                + "  |  Task Progress:  " + getProgress()
//                + "  |  Tasks remaining: " + bot.getRemainingTaskCount()
//                + "  |  Task Complete: " + isComplete()
//                + "  |  Attempts: " + bot.getRemainingAttemptsString()
//                + "  |  Selected Index: " + bot.getSelectedTaskIndex()
//                + "  |  List Index: " + bot.getListIndex());
//
//        return false;
//    }
//
//    public void incrementTaskLoop() {
//        loop++;
//    }
//
//    /**
//     * Override to execute some extra logic after a task has completed all stages (in-between task loops).
//     */
//    protected abstract void onTaskLoopCompletion();
//
//    /**
//     * Override to execute some extra logic after a task has completed all stages/loops (in-between task switches).
//     */
//    protected abstract void onTaskCompletion();
//    /**
//     * Override to execute some extra logic after each stage of a task.
//     */
//    protected abstract void onStageCompletion();
//
//    /**
//     * Travel to the specified {@link Tile} before executing this task. // Tile -> Tile
//     *
//     * @param position The position to travel to.
//     * @return True if the task was completed successfully, else returns false
//     */
//    public Task at(Tile position) { // Tile -> Tile
//        area = null;
//        this.position = position;
//        return this;
//    }
//
//    /**
//     * Creates an executable task which digs somewhere around the passed {@link Area}.
//     *
//     * @param area The approximate {@link Area} in which to perform this task.
//     * @return True on successful execution, else returns false.
//     */
//    public Task around(Area area) {
//        this.area = area;
//        position = null;
//        return this;
//    }
//
//    /**
//     * Executes this {@link Task} somewhere near the passed position, based on the passed radius.
//     *
//     * @param position The centre of the area to perform this task.
//     * @param radius The radius (i.e., radius of 3 = 3 tiles in each direction) in which to dig.
//     * @return True if this task is complete, else returns false.
//     */
//    public Task near(Tile position, int radius) { // Tile -> Tile
//        area = position.getArea(radius);
//        this.position = null;
//        return this;
//    }
//
//    /**
//     * Executes this {@link Task} until the passed condition is met.
//     *
//     * @param bot The {@link BotMan bot} performing this task.
//     * @param condition The {@link BooleanSupplier condition} which must be true for this {@link Task} to end.
//     * @return True if this Task is complete, else returns false.
//     */
//    public boolean until(BotMan bot, BooleanSupplier condition) throws InterruptedException {
//        bot.setBotStatus("Performing task until: " + condition);
//
//        if (condition.getAsBoolean())
//            return true;
//
//        return run(bot);
//    }
//
//    /**
//     * Manually set which stage this {@link Task} executes from, only intended for developers to test various parts of a
//     * function.
//     */
//    public Task fromStage(int stage) {
//        setStage(stage);
//        return this;
//    }
//
//    /**
//     * Manually set which stages this function will execute, starting execution from the first stage, continuing until
//     * the task is completed, interrupted, or the last stage is executed.
//     *
//     * @param firstStage The first stage of this task to execute.
//     * @param lastStage The last stage of this task to execute.
//     */
//    public Task betweenStages(int firstStage, int lastStage) {
//        setStage(firstStage);
//        if (firstStage > 0 && lastStage <= getStages() && firstStage < lastStage) {
//            this.stages = lastStage;
//            return this;
//        }
//
//        throw new RuntimeException("Error creating between-stage task! Invalid stages passed...");
//    }
//
//    public void restart() {
//        // restart the task
//        setStage(1);
//        // reset task loops
//        loop = 0;
//        // reset task progress
//        getProgress();
//    }
//
//    ///
//    ///  Getters/setters
//    ///
//
//    public final void setDescription(String description) {
//        this.description = description;
//    }
//
//    /**
//     * @return A short description of this task.
//     */
//    public final String getDescription() {
//        return description;
//    }
//
//    public final Task setStage(int stage) {
//        this.stage = stage;
//        return this;
//    }
//
//    public final Task setStages(int stages) {
//        this.stages = stages;
//        return this;
//    }
//
//    public final int getStage() {
//        return stage;
//    }
//
//    ///  see abstract functions for getStages() - abstracted to force children to provide on creation when coding.
//
//    public final String getStageString() {
//        return stage + "/" + stages;
//    }
//
//    ///
//    ///  Abstract functions
//    ///
//
//    /**
//     * Forces children to provide the total stages for this {@link Task} for the progress bar calculations.
//     */
//    public abstract int getStages();
//
//    /**
//     * Forces children to provide the logic used to execute this task function.
//     * <p>
//     * Each stage should represent a unique part of the task function and can be manually overridden by adjusting the
//     * current {@link Task#stage stage} of this task using {@link Task#setStage(int)}.
//     * <p>
//     * The number of unique cases in this function should match the number provided to {@link Task#getStages() stage} as
//     * that is the value that will be used to calculate the {@link Task#getProgress() task progress}.
//     * <p>
//     * Each case should break unless an error is thrown or an early escape (stage override or task completion) is
//     * triggered. Stages are automatically incremented after each case and a small random-delay is forced.
//     */
//    protected abstract boolean execute(BotMan bot) throws InterruptedException;
//
//    /**
//     * Forces children to define a {@link JPanel panel} with script-specific settings for easier interaction.
//     *
//     * @return A {@link JPanel} object used as a script-settings menu tab in the {@link BotMenu}.
//     */
//    public abstract JPanel getTaskSettings();
//
//    /**
//     * Return information on this task instead of a meaningless reference.
//     */
//    public String toString() {
//        return getDescription();
//    }
//
//    /**
//     * Define how to compare this object against other objects or itself.
//     *
//     * @param o   the reference object with which to compare.
//     *
//     * @return True or false based on the comparison evaluation. //TODO update this function and this comment
//     */
//    @Override
//    public final boolean equals(Object o) {
//        if (this == o)
//            return true;
//        if (o == null)
//            return false;
//
//        // compare by class type
//        if (this.getClass() != o.getClass())
//            return false;
//
//        Task other = (Task) o;
//        // compare by description
//        return java.util.Objects.equals(this.description, other.description);
//    }
//}