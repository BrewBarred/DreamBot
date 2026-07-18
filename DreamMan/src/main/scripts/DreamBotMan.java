package main.scripts;

import main.data.library.FileManager;
import main.data.library.JLibrary;
import main.menu.DreamBotMenu;
import main.menu.Overlay;
import main.menu.CanvasButtons;
import main.menu.ScriptControls;
import org.dreambot.api.methods.login.LoginUtility;
import org.dreambot.api.methods.tabs.Tabs;
import org.dreambot.api.script.listener.HumanMouseListener;
import java.awt.event.MouseEvent;
import main.tools.Rand;
import org.dreambot.api.data.GameState;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.listener.GameStateListener;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.wrappers.interactive.Player;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import main.actions.Action;

public abstract class DreamBotMan extends AbstractScript implements GameStateListener,
        HumanMouseListener, ScriptControls,
        org.dreambot.api.script.listener.ChatListener {   // v1.31: feeds the Logs tab

    // ── v1.31: chat capture ──────────────────────────────────────────────────
    // Every message type lands in the in-memory ChatLog (nothing touches disk or any server;
    // it evaporates on close). The Logs tab renders + searches it, and the CHAT_CONTAINS
    // check reads it to react to nearby players.
    @Override public void onGameMessage(org.dreambot.api.wrappers.widgets.message.Message m) {
        if (m != null) main.tools.ChatLog.chat("GAME", "", m.getMessage());
    }
    @Override public void onPlayerMessage(org.dreambot.api.wrappers.widgets.message.Message m) {
        if (m != null) main.tools.ChatLog.chat("PUBLIC", m.getUsername(), m.getMessage());
    }
    @Override public void onPrivateInMessage(org.dreambot.api.wrappers.widgets.message.Message m) {
        if (m != null) main.tools.ChatLog.chat("PRIVATE", m.getUsername(), m.getMessage());
    }
    @Override public void onTradeMessage(org.dreambot.api.wrappers.widgets.message.Message m) {
        if (m != null) main.tools.ChatLog.chat("TRADE", m.getUsername(), m.getMessage());
    }
    @Override public void onClanMessage(org.dreambot.api.wrappers.widgets.message.Message m) {
        if (m != null) main.tools.ChatLog.chat("CLAN", m.getUsername(), m.getMessage());
    }
    ///  Class scope fields

    /**
     * The {@link DreamBotMenu} which allows users to interface with their botting scripts and player(s).
     */
    private volatile DreamBotMenu menu;

    /** The live menu (null until the EDT has built it). Used by exported scripts (Patch B.10). */
    protected final DreamBotMenu getMenu() { return menu; }
    /**
     * A {@link String} denoting the name of the last logged in player.
     * <p>
     * This is used to check if a login is a new player so the manager knows when to reload the menu items. (Otherwise
     * the menu would show the incorrect stats and tasks whenever you change accounts)
     */
    private String lastPlayerName = "";

    /** Universal on-canvas buttons (Open settings / Skip / +30m / -30m). */
    private final CanvasButtons canvasButtons = new CanvasButtons();
    /** Optional run-time limit; when >0 and passed, the script stops (with logout). 0 = no limit. */
    private volatile long runLimitEndMs = 0L;

    ///  Abstract functions

    /**
     * Allows child inheritors to execute some setup logic before starting the main bot loop.
     *
     * @return True if the pre-loop logic returns successful, else false.
     */
    public abstract boolean postStart();
    /**
     * Provides a function which is automatically called before each task list loop, that is, once all tasks have been
     * completed in a particular task-set.
     * <p>
     * This function allows scripters to override and perform specific tasks/functions at the end of each loop and
     * determine whether the loop should continue or not based on those conditions.
     * <p>
     * Although this same functionality could have been achieved more simply by placing this logic at start of any loop,
     * this method segmentizes it better for the progress bars and overall code clarity.
     * <p>
     * This logic could be used for simple things like a brief pause before each loop, or more complex things such as
     * checking attempt counts, determining player safety, or triggering random number generators to execute a different
     * task-set.
     *
     * @return True if the pre-loop logic returns successful, else false.
     */
    public abstract boolean preLoop();
    /**
     * Allows child inheritors to execute some logic after each action, such as a small pause or even an opportunity to
     * check vitals for more reactive, but more complex bots.
     *
     * @return True if the post-action logic returns successful, else false.
     */
    public abstract boolean postAction();
    /**
     * Allows child inheritors to execute some logic everytime an account logs in. This works purely off a game state
     * change resulting in a logged in game state. To execute logic only when a 'new' account has logged in, use
     * {@link DreamBotMan#postLoginNew()} instead.
     *
     * @see DreamBotMan#postLoginNew()
     */
    public abstract void postLogin();
    /**
     * Checks the name of the logged in player to see if it matches the last, and executes some logic if a new player
     * is detected.
     * <p>
     * This may prove useful for first-time login logic without worrying about redundant code being executed on
     * world-hops.
     */
    public abstract void postLoginNew();

    public abstract void postGameStateChange(GameState gameState);

    public abstract void postResume();
    public abstract void postPause();
    public abstract void postPaint(Graphics graphics);
    public abstract void postStop();
    public abstract void postExit();

    private volatile DefaultListModel<DreamBotMenu.Task> queue;
    /**
     * Points at the next {@link Action} to execute inside the current task. Persisted across
     * loops so multi-action tasks progress one action at a time instead of re-running earlier
     * actions every loop (which caused walk "ping-pong" between two targets forever).
     */
    private int actionCursor = 0;
    /**
     * How many times the CURRENT task (at the current queue index) has finished running during
     * this pass of the queue. Compared against the task's configured repeat count so a task can
     * run N times before the queue advances. Reset whenever the queue index changes.
     */
    private int taskRunsDone = 0;
    /** v1.31: how many timed-task runs have completed this session (gates on-start actions). */
    private int timedTaskRunsEver = 0;
    /**
     * The queue index the engine last executed. If the menu changes the index behind the
     * engine's back (Skip / Run-from-here buttons), this lets us notice and reset the action
     * cursor so the newly-selected task starts cleanly from its first action.
     */
    private int lastServedIndex = -999;
    /** Patch B.4: evaluates watchers between actions (globals + the current action's own). */
    private final main.watchers.WatcherEngine watchers = new main.watchers.WatcherEngine();

    // ── Patch B.8: timed-task interludes ────────────────────────────────────
    /** The timed task currently running as an interlude (null when none). */
    private DreamBotMenu.Task timedTask;
    /** Action cursor within the running timed task. */
    private int timedCursor = 0;
    /** True while a timed task is mid-run, so the normal queue stays put. */
    private boolean timedRunning = false;
    /** True when the running timed task fired at the END of all loops (pause once it's done). */
    private boolean timedEndsRun = false;



    @Override
    public final void onStart() {
        super.onStart();

        FileManager.setCollection("default");
        JLibrary.getInstance().load();

        // initialize the GUI on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            menu = new DreamBotMenu(this);
            menu.setAlwaysOnTop(true); //TODO add to "Client" settings
            menu.setScriptControls(this); // lets the menu's Login/Logout buttons reach the script
            menu.setVisible(true);

            // fetch the task queue only after menu is instantiated
            queue = menu.getModelTaskList();

            // Universal on-canvas buttons, drawn on the game screen (same for every script).
            final DreamBotMenu m = menu;
            // All actions marshalled to the EDT (the click arrives on DreamBot's listener thread,
            // and Swing calls like setVisible/toFront must run on the EDT - this is why the old
            // "Open settings" button appeared to do nothing).
            canvasButtons.add("Menu",   m::bringToFront);
            canvasButtons.add(() -> m.isMenuPaused() ? "Resume" : "Pause",
                    () -> SwingUtilities.invokeLater(() -> {
                if (m.isMenuPaused()) m.resume("Resumed from overlay");
                else m.pause("Paused from overlay");
            }));
            canvasButtons.add("Skip",   () -> SwingUtilities.invokeLater(m::advanceQueue));
            canvasButtons.add("+30m",   () -> adjustRunLimit(30));
            canvasButtons.add("-30m",   () -> adjustRunLimit(-30));
        });

        // call child on-start for script-specific on-start setup
        if (!postStart())
            throw new RuntimeException("Error running post-start functions!");
        else
            Logger.log(Logger.LogType.DEBUG, "Script loaded!");
    }

    @Override
    public int onLoop() {
        try {
            ///  Don't start looping until the menu has been fully instantiated on the EDT
            DreamBotMenu menu = this.menu;
            DefaultListModel<DreamBotMenu.Task> queue = this.queue;
            if (menu == null || queue == null || menu.libraryPanel == null)
                return 500;

            ///  Enforce an optional run-time limit (set via the +30m/-30m overlay buttons)
            if (runLimitEndMs > 0) {
                long remaining = runLimitEndMs - System.currentTimeMillis();
                if (remaining <= 0) {
                    menu.setStatus("Run-time limit reached - stopping");
                    requestStop(true); // logout, then stop
                    return 600;
                }
                long s = remaining / 1000;
                menu.putOverlayStat("Time left", String.format("%02d:%02d", s / 60, s % 60));
            }

            ///  Pause if the menu has requested it
            if (menu.isMenuPaused())
                pause("Script paused via DreamBotMenu!");

            menu.libraryPanel.tick();

            ///  Ensure inheritors pre-loop checks are performed before every loop
            if (!preLoop())
                throw new Exception("Error executing pre loop logic!");

            if (queue.isEmpty()) {
                actionCursor = 0;
                // don't pause while learning otherwise you won't be able to learn without running a script.
                if (!JLibrary.getInstance().getLearner().isLearning())
                    pause("Task list is empty!");

                return Rand.nextInt(263, 1925);
            }

            // ── Patch B.8: timed-task interludes ──
            // A timed task never interrupts mid-task; it fires at a SAFE point (loop end, all
            // loops done, or right after another timed task) and runs to completion before the
            // normal queue resumes exactly where it left off.
            if (timedRunning) {
                Integer delay = stepTimedTask(menu);
                if (delay != null) return delay;       // still running the interlude
            } else {
                // arm any unscheduled timers, then see if a safe point has opened
                boolean loopDone = menu.consumeLoopCompleted();
                boolean allDone = menu.consumeAllLoopsCompleted();
                armTimers(menu);
                DreamBotMenu.Task due = pickDueTimedTask(menu, loopDone, allDone, false);
                if (due != null && allDone && "AFTER_ALL_LOOPS".equals(due.getTimerWhen()))
                    timedEndsRun = true;   // pause the script once this interlude finishes
                if (due != null) {
                    beginTimedTask(menu, due);
                    Integer delay = stepTimedTask(menu);
                    if (delay != null) return delay;
                }
            }

            // fetch the current execution index, correcting invalid indices back to the first task
            int index = menu.getCurrentExecutionIndex();
            if (index < 0 || index >= queue.size()) {
                // Starting a fresh run (index was -1 after a finished/empty queue, or the queue
                // was edited). Reset the per-task and whole-queue loop counters.
                index = 0;
                actionCursor = 0;
                taskRunsDone = 0;
                menu.setCurrentExecutionIndex(0);
                menu.resetLoopProgress();
            }

            // If the index changed since we last ran (e.g. the user pressed Skip or
            // Run-from-here), restart cleanly at the first action of the new task.
            boolean switchedTask = (index != lastServedIndex);   // v1.33
            if (switchedTask) {
                actionCursor = 0;
                taskRunsDone = 0;
                lastServedIndex = index;
                watchers.reset();   // Patch B.4: abandon any half-run watcher on Skip/edit
            }

            // fetch the current task (the queue can shrink on the EDT between the check and the get)
            DreamBotMenu.Task currentTask;
            try {
                currentTask = queue.get(index);
            } catch (ArrayIndexOutOfBoundsException shrunk) {
                actionCursor = 0;
                taskRunsDone = 0;
                menu.setCurrentExecutionIndex(0);
                return Rand.nextInt(263, 983);
            }

            // v1.33: announce a task is about to start so BEFORE_TASK triggers can fire before its
            // first action. Only on a genuine switch, and only at the very first action.
            if (switchedTask && currentTask != null && actionCursor == 0)
                main.watchers.TaskEventBus.taskStarting(currentTask.getName());

            List<Action> actions = currentTask != null ? currentTask.getActions() : null;
            if (currentTask == null || actions == null || actions.isEmpty()) {
                // nothing executable in this entry - move along (no repeats for an empty task)
                actionCursor = 0;
                taskRunsDone = 0;
                menu.advanceQueue();
                return Rand.nextInt(263, 983);
            }

            // update status using the task status field (always the initial message to display)
            menu.setStatus(currentTask.getStatus());

            // clamp the cursor in case the task was edited to be shorter while running
            if (actionCursor >= actions.size()) {
                actionCursor = 0;
                onTaskRunComplete(menu, currentTask);
                return Rand.nextInt(263, 983);
            }

            // Execute ONLY the current action this loop; the cursor advances when it completes.
            // (Previously every action re-ran from the start of the list each loop, so a task
            // like [Walk A, Walk B] ping-ponged between targets and never finished.)
            Action action = actions.get(actionCursor);

            // ── Patch B.4: watchers run BETWEEN actions, while the player is safe ──
            // Consult globals + this action's own triggers. If a watcher is mid-response, or an
            // "instead" watcher just fired, we do NOT run the queue action this loop.
            main.watchers.WatcherEngine.Outcome wo = watchers.service(
                    menu.getGlobalTriggers(),
                    action != null ? action.getTriggers() : null);
            if (wo == main.watchers.WatcherEngine.Outcome.RUNNING)
                return Rand.nextInt(180, 420);   // let the response chain breathe
            if (wo == main.watchers.WatcherEngine.Outcome.REPLACED) {
                // an "instead of this action" watcher handled the situation - skip the action
                // for this pass but treat it as satisfied so the task advances normally
                action.resetAttempts();
                lastPolledAction = null;
                actionCursor++;
                if (actionCursor >= actions.size()) {
                    actionCursor = 0;
                    onTaskRunComplete(menu, currentTask);
                }
                return Rand.nextInt(180, 420);
            }
            // ── v1.33: control-flow outcomes from a finished trigger ──
            if (wo == main.watchers.WatcherEngine.Outcome.SKIP_NEXT) {
                // skip the upcoming action, count it satisfied so the task moves on
                if (action != null) action.resetAttempts();
                lastPolledAction = null;
                actionCursor++;
                if (actionCursor >= actions.size()) {
                    actionCursor = 0;
                    onTaskRunComplete(menu, currentTask);
                }
                return Rand.nextInt(180, 420);
            }
            if (wo == main.watchers.WatcherEngine.Outcome.RESTART_LAST) {
                // re-run the action the queue is on (reset its progress; do NOT advance)
                if (action != null) action.resetAttempts();
                lastPolledAction = null;
                return Rand.nextInt(180, 420);
            }
            if (wo == main.watchers.WatcherEngine.Outcome.RESTART_TASK) {
                // restart the current task from its first action
                actionCursor = 0;
                lastPolledAction = null;
                watchers.reset();
                return Rand.nextInt(180, 420);
            }
            if (wo == main.watchers.WatcherEngine.Outcome.RESTART_QUEUE) {
                // jump the whole queue back to the first task
                actionCursor = 0;
                taskRunsDone = 0;
                lastPolledAction = null;
                watchers.reset();
                menu.setCurrentExecutionIndex(0);
                return Rand.nextInt(180, 420);
            }

            if (action == null) {
                Logger.log("Skipping null/invalid action in task: " + currentTask.getName());
                actionCursor++;
            } else {
                // Patch B.2: reset the attempt counter whenever the engine ENTERS an action
                // (fresh task pass, loop-around, or skipping to it) so budgets are per-run.
                if (action != lastPolledAction) {
                    action.resetAttempts();
                    lastPolledAction = action;

                    // Patch B.7: roll the action's chance-to-run ONCE on entry. At 100% (default)
                    // this always passes; below that, a miss skips the action for this pass and
                    // the task moves on - the randomness that lets niche/human detours fire only
                    // sometimes. Rolling on entry (not every poll) means a multi-poll action
                    // either runs to completion or is skipped, never half-rolled mid-way.
                    // v1.31: setup actions run the FIRST pass only - first queue loop, first
                    // repetition of this task. Later passes skip them exactly like a missed
                    // chance roll: fetch the pickaxe once, don't re-fetch it every lap.
                    if (action.isOnStartOnly()
                            && (menu.getQueueLoopCurrentValue() > 1 || taskRunsDone > 0)) {
                        action.resetAttempts();
                        actionCursor++;
                        if (actionCursor >= actions.size()) {
                            actionCursor = 0;
                            onTaskRunComplete(menu, currentTask);
                        }
                        return Rand.nextInt(120, 300);
                    }

                    if (!action.rollChance()) {
                        Logger.log(Logger.LogType.DEBUG, "Chance skip (" + action.getChancePercent()
                                + "%): " + action.toBuildString());
                        action.resetAttempts();
                        actionCursor++;
                        // if that was the last action, the run completes below on the next check
                        if (actionCursor >= actions.size()) {
                            actionCursor = 0;
                            onTaskRunComplete(menu, currentTask);
                        }
                        return Rand.nextInt(120, 300);
                    }
                }

                if (action.execute()) {
                    if (!postAction())
                        throw new Exception("Error executing after action logic!");

                    Logger.log(Logger.LogType.DEBUG, "Action complete: " + action.toBuildString());
                    action.resetAttempts();
                    actionCursor++;
                } else if (action.attemptsExhausted()) {
                    // Patch B.2: the action used up its tries (nothing in range, walks failing,
                    // clicks failing...) - the task genuinely can't complete. Fail THIS task
                    // with a clear status and move the queue on, instead of looping forever.
                    String why = action.getName() + " gave up after " + action.getAttempts() + " tries";
                    Logger.log(Logger.LogType.WARN, "Task failed: " + currentTask.getName() + " - " + why);
                    menu.setStatus("Task failed: " + why);
                    action.resetAttempts();
                    actionCursor = 0;
                    taskRunsDone = 0;
                    menu.advanceQueue();
                    return 600;
                }
            }

            // all actions completed -> this counts as one full run of the task
            if (actionCursor >= actions.size()) {
                actionCursor = 0;
                onTaskRunComplete(menu, currentTask);
            }

        } catch (Exception e) {
            String error = e.getMessage();
            Logger.log(error == null || error.isEmpty() ? "Error executing main loop!" : error);
            e.printStackTrace();
        }

        int gap = pendingTaskGapMs;
        if (gap > 0) {
            pendingTaskGapMs = 0;
            return gap;      // the between-task auto-wait (Patch B.2)
        }
        return 600;
    }

    /** Adds (or removes) minutes to the run-time limit from the +30m/-30m overlay buttons. */
    private void adjustRunLimit(int minutes) {
        long now = System.currentTimeMillis();
        long base = Math.max(now, runLimitEndMs);
        long updated = base + minutes * 60_000L;
        runLimitEndMs = (updated <= now) ? 0L : updated; // reducing below 'now' clears the limit
        if (menu != null) {
            if (runLimitEndMs == 0L) menu.removeOverlayStat("Time left");
            menu.setStatus(runLimitEndMs == 0L ? "Run-time limit cleared"
                    : "Run-time limit " + minutes + " min");
        }
    }

    ///  ── HumanMouseListener: route user clicks to the on-canvas buttons ──
    @Override
    public void onMouseClicked(MouseEvent e) {
        if (canvasButtons.handleClick(e)) // consumed silently if it hits a button
            return;
        Overlay.handleClick(e);           // overlay titles toggle minimize/restore (Patch B.3)
    }

    // Patch B.3: feeds the cursor position so overlay titles brighten on hover. Deliberately
    // no @Override: HumanMouseListener's members are default methods, and if a client build
    // ever lacked this one the annotation would break compilation - without it, worst case the
    // hover highlight simply doesn't fire while everything else keeps working.
    public void onMouseMoved(MouseEvent e) {
        Overlay.onMouseMoved(e.getPoint());
    }

    ///  ── ScriptControls: login / logout / stop (called by the menu's control bar) ──
    @Override
    public void requestLogin() {
        try { LoginUtility.login(); }
        catch (Throwable t) { Logger.log("Login failed: " + t.getMessage()); }
    }

    @Override
    public void requestLogout() {
        try { Tabs.logout(); }
        catch (Throwable t) { Logger.log("Logout failed: " + t.getMessage()); }
    }

    @Override
    public void requestStop(boolean logout) {
        if (logout) requestLogout();
        stop();
    }

    /**
     * Called when the current task has finished one full run of its action chain. Repeats the
     * task if it hasn't yet hit its configured repeat count; otherwise resets the counter and
     * asks the menu to advance the queue (which handles whole-queue looping / finishing).
     */
    // ═══════════════ Patch B.8: timed tasks ═══════════════

    /** Gives every timed task a due-time the first time we see it (so timers start ticking). */
    private void armTimers(DreamBotMenu menu) {
        for (DreamBotMenu.Task t : menu.getTimedTasks())
            if (t != null && t.getNextDueAt() == 0)
                t.scheduleNext();
    }

    /**
     * Picks a timed task that is DUE and whose safe point has just occurred.
     *
     * @param loopDone      a queue loop just finished (AFTER_LOOP tasks may fire)
     * @param allDone       the final loop just finished (AFTER_ALL_LOOPS tasks may fire)
     * @param afterTimed    we just finished a timed task (AFTER_TIMED_TASK tasks may chain)
     */
    private DreamBotMenu.Task pickDueTimedTask(DreamBotMenu menu, boolean loopDone,
                                               boolean allDone, boolean afterTimed) {
        for (DreamBotMenu.Task t : menu.getTimedTasks()) {
            if (t == null || !t.isDue()) continue;
            String when = t.getTimerWhen();
            if ("AFTER_LOOP".equals(when) && loopDone) return t;
            if ("AFTER_ALL_LOOPS".equals(when) && allDone) {
                // guard: an infinite queue never "finishes all loops", so this can never fire -
                // the UI warns about this, and we skip it here rather than starve the timer.
                if (!menu.isInfiniteLoop()) return t;
            }
            if ("AFTER_TIMED_TASK".equals(when) && afterTimed) return t;
        }
        return null;
    }

    private void beginTimedTask(DreamBotMenu menu, DreamBotMenu.Task t) {
        timedTask = t;
        timedCursor = 0;
        timedRunning = true;
        watchers.reset();   // start the interlude with a clean check slate
        Logger.log("[Timed] Running \"" + t.getName() + "\" (every ~" + t.getTimerMinutes() + "m)");
        menu.setStatus("Timed: " + t.getName());
    }

    /**
     * Steps the running timed task one action per loop, exactly like the main queue (checks still
     * apply, so you stay alive during it). @return a delay while it's still running, or null when
     * it has finished and the normal queue should resume.
     */
    private Integer stepTimedTask(DreamBotMenu menu) {
        if (timedTask == null) { timedRunning = false; return null; }
        List<Action> actions = timedTask.getActions();
        if (actions == null || actions.isEmpty()) { finishTimedTask(menu); return null; }

        if (timedCursor >= actions.size()) { finishTimedTask(menu); return null; }

        Action action = actions.get(timedCursor);

        // checks run during a timed task too (globals + the action's own)
        main.watchers.WatcherEngine.Outcome wo = watchers.service(
                menu.getGlobalTriggers(), action != null ? action.getTriggers() : null);
        if (wo == main.watchers.WatcherEngine.Outcome.RUNNING)
            return Rand.nextInt(180, 420);
        if (wo == main.watchers.WatcherEngine.Outcome.REPLACED) {
            if (action != null) action.resetAttempts();
            timedCursor++;
            return Rand.nextInt(180, 420);
        }
        // v1.33: control outcomes inside a timed task. RESTART_TASK/QUEUE restart the timed
        // task's action cursor (a timed task is one task, so both mean "back to its start");
        // SKIP_NEXT advances; RESTART_LAST re-runs the current action.
        if (wo == main.watchers.WatcherEngine.Outcome.SKIP_NEXT) {
            if (action != null) action.resetAttempts();
            timedCursor++;
            return Rand.nextInt(180, 420);
        }
        if (wo == main.watchers.WatcherEngine.Outcome.RESTART_LAST) {
            if (action != null) action.resetAttempts();
            lastPolledAction = null;
            return Rand.nextInt(180, 420);
        }
        if (wo == main.watchers.WatcherEngine.Outcome.RESTART_TASK
                || wo == main.watchers.WatcherEngine.Outcome.RESTART_QUEUE) {
            timedCursor = 0;
            lastPolledAction = null;
            watchers.reset();
            return Rand.nextInt(180, 420);
        }

        if (action == null) { timedCursor++; return Rand.nextInt(120, 300); }

        if (action != lastPolledAction) {
            action.resetAttempts();
            lastPolledAction = action;
            // v1.31: on-start actions never re-run inside a TIMED task's repeats either
            if (action.isOnStartOnly() && timedTaskRunsEver > 0) {
                action.resetAttempts();
                lastPolledAction = null;
                timedCursor++;
                return Rand.nextInt(120, 300);
            }
            if (!action.rollChance()) {   // per-action chance applies here too
                action.resetAttempts();
                timedCursor++;
                return Rand.nextInt(120, 300);
            }
        }

        if (action.execute()) {
            action.resetAttempts();
            lastPolledAction = null;
            timedCursor++;
            if (timedCursor >= actions.size()) { finishTimedTask(menu); return null; }
        } else if (action.attemptsExhausted()) {
            Logger.log(Logger.LogType.WARN, "[Timed] Action gave up: " + action.toBuildString());
            action.resetAttempts();
            lastPolledAction = null;
            timedCursor++;
            if (timedCursor >= actions.size()) { finishTimedTask(menu); return null; }
        }
        return Rand.nextInt(263, 983);
    }

    /** Reschedules the finished timed task and lets an AFTER_TIMED_TASK task chain in. */
    private void finishTimedTask(DreamBotMenu menu) {
        timedTaskRunsEver++;   // v1.31
        DreamBotMenu.Task done = timedTask;
        if (done != null) {
            done.scheduleNext();   // roll the next interval (with jitter)
            Logger.log("[Timed] Finished \"" + done.getName() + "\"; next in ~"
                    + done.getTimerMinutes() + "m");
        }
        timedTask = null;
        timedCursor = 0;
        timedRunning = false;
        lastPolledAction = null;
        watchers.reset();

        // chaining: another timed task set to AFTER_TIMED_TASK may now fire immediately
        DreamBotMenu.Task next = pickDueTimedTask(menu, false, false, true);
        if (next != null) {
            beginTimedTask(menu, next);
            return;
        }

        // Patch B.8: this interlude ran because ALL loops had finished - now actually stop.
        if (timedEndsRun) {
            timedEndsRun = false;
            menu.pauseAfterTimedCompletion();
        }
    }

    /**
     * Patch B.5: the menu calls this when a PAUSED queue is reordered and the currently-executing
     * tile changed position. Adopting the new index here (instead of letting the next loop see a
     * mismatch) preserves actionCursor and taskRunsDone - execution resumes from exactly where it
     * left off, in the task's new slot, as though it never moved.
     */
    public void remapServedIndex(int newIndex) {
        lastServedIndex = newIndex;
    }

    /** Set by onTaskRunComplete when the queue-level auto-wait is on; onLoop returns it once. */
    private volatile int pendingTaskGapMs = 0;
    /** The action the engine polled last loop - used to reset attempt budgets on entry. */
    private Action lastPolledAction;

    private void onTaskRunComplete(DreamBotMenu menu, DreamBotMenu.Task task) {
        taskRunsDone++;
        // v1.33: announce completion so AFTER_TASK triggers can fire before the next task runs.
        if (task != null) main.watchers.TaskEventBus.taskFinished(task.getName());

        // Patch B.2: the Task List's "auto-wait between tasks" - a humanised random pause after
        // every completed task pass (you basically always wait a beat between tasks anyway).
        // Delivered through onLoop's return value: non-blocking and pause-safe.
        if (menu != null && menu.isQueueAutoWait())
            pendingTaskGapMs = Rand.nextInt(menu.getQueueAutoWaitMinMs(), menu.getQueueAutoWaitMaxMs());
        // v1.49: enforce the tier loop cap at RUNTIME instead of destroying loop values on
        // publish. A downloaded task keeps its author's intended repeat, but a free user can't
        // exceed their tier's max loops by running it - the cap is applied where it matters.
        int loopCap = Math.max(1, main.market.Tier.maxLoops());
        int repeat = task != null ? Math.max(1, Math.min(task.getRepeat(), loopCap)) : 1;

        if (taskRunsDone >= repeat) {
            // task done for this pass - move on (index reset of counters handled here)
            taskRunsDone = 0;
            menu.advanceQueue();
        }
        // else: leave the index where it is; the next loop re-runs this task from action 0.
    }

    /**
     * Executes some code on game-state change, such as when the player logs in. This function is useful for ensuring
     * player data remains consistent with whichever account is logged in at all times.
     */
    @Override
    public final void onGameStateChange(GameState gameState) {
        if (gameState == GameState.LOGGED_IN) {
            ///  Trigger onLogin() logic (fires on EVERY successful login, including world-hops)
            postLogin();
            Logger.log(Logger.LogType.DEBUG, "Login success!");

            Player player = Players.getLocal();
            String currentPlayer = player != null ? player.getName() : null;
            // Only fire "new login" logic when the character actually changed (not on world-hops)
            if (currentPlayer != null && !currentPlayer.equals(lastPlayerName)) {
                lastPlayerName = currentPlayer;
                postLoginNew();
                Logger.log(Logger.LogType.DEBUG, "New login detected for: " + currentPlayer);
                updateAccountData(currentPlayer);
            }
        }
        postGameStateChange(gameState);
        Logger.log(Logger.LogType.DEBUG, "New game state change detected!");
    }

    @Override
    public final void onResume() {
        // super.onResume();
        resume("Client start triggered...");
    }

    public final void resume(String status) {
        if (menu != null)
            menu.resume(status);

        postResume();
        Logger.log(Logger.LogType.DEBUG, "Script resumed: " + status);
    }

    @Override
    public final void onPause() {
        //super.onPause();
        pause("Client pause triggered...");
    }

    public final void pause(String status) {
        ///  Scripts shouldn't be executing without a valid menu!
        if (menu != null)
            menu.pause(status);

        postPause();
        Logger.log(Logger.LogType.DEBUG, "Script paused: " + status);
    }

    @Override
    public final void onPaint(Graphics graphics) {
        super.onPaint(graphics);
        // Draw the in-game status overlay (safe: standard java.awt.Graphics).
        if (menu != null) {
            int overlayBottom = Overlay.render(graphics, menu);
            // Attach the button row directly under the overlay, spanning its width (linked layout).
            canvasButtons.renderRow(graphics, Overlay.getX(), overlayBottom + 4, Overlay.getWidth());
            // Patch B.2: tracked-skill cards in columns below - left column until the chatbox,
            // then a new column to the right. Each card (and the main panel) is minimizable.
            Overlay.renderSkills(graphics, menu, overlayBottom + 4 + 24 + 6);
        }
        postPaint(graphics);
    }

    @Override
    public final void onExit() {
        super.onExit();

        JLibrary.getInstance().saveAll();
        // call post exit before menu disposal incase script needs to dispose of menu stuff
        postExit();

        // safely dispose menu and menu components
        if (menu != null)
            menu.onExit();

        Logger.log(Logger.LogType.DEBUG, "Successfully exited DreamBotMan by ETA.");
    }

    @Override
    public final void stop() {
        super.stop();
        postStop();
        Logger.log(Logger.LogType.DEBUG, "Stopping DreamBotMan...");
    }

    private void updateAccountData(String name) {
        // Update your specific configs here
        log("Fetching player data for " + name);
        menu.updateAll();
    }

    @Override
    public final boolean isUserVIP() {
        // TODO enable special features for those supporting DreamBot or me
        // TODO add to status tab
        return super.isUserVIP();
    }

    @Override
    public final boolean isUserSponsor() {
        // TODO enable special features for those supporting DreamBot or me
        // TODO add to status tab
        return super.isUserSponsor();
    }
}