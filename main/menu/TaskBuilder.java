package main.menu;

import main.actions.Action;
import main.actions.ActionUtil;
import main.menu.components.JActionSelector;
import main.menu.components.JLibraryList;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

import static main.menu.MenuHandler.*;

/**
 * TaskBuilderTab
 * ─────────────────────────────────────────────────────────────────────────────
 * Extends JPanel — drop it straight into a JTabbedPane, no getPanel() needed.
 *
 * DreamBotMenu wires it in with one line:
 *   mainTabs.addTab("Task Builder", loadTabIcon("task_builder_tab"), taskBuilderTab);
 *
 * Constructor args:
 *   actionModel         — shared DefaultListModel<Action>
 *   libraryModel        — shared DefaultListModel<Task>
 *   actionSelector      — shared JActionSelector
 *   dynamicControlPanel — shared panel mutated by actionSelector
 */
public class TaskBuilder extends JPanel {
    private final DreamBotMenu botMenu;

    JButton btnRemove;
    private JButton btnWatchers;

    // ── Injected dependencies ─────────────────────────────────────────────────
    // Patch B.5: the raw library model is no longer touched here - the menu owns a MASTER list
    // (libraryAll) and all builder mutations route through its helpers, so an active search or
    // filter can never make the builder miss (or lose) hidden tasks.
    private final DefaultListModel<Action> modelTaskBuilder;
    private final JActionSelector actionSelector;

    // ── Internal UI refs ──────────────────────────────────────────────────────
    private final JList<Action> listTaskBuilder;
    private final JLibraryList listLibrary;
    private JPanel dynamicControlPanel;
    private JTextField taskNameInput;
    private JTextField taskDescriptionInput;
    private JTextField taskStatusInput;
    private JButton    btnAddToLibrary;

    // ── Auto-delay between actions (Patch B) ─────────────────────────────────
    private JCheckBox  chkAutoDelay;

    // Patch B.2: identity of the task loaded for editing - "save changes" updates every
    // instance of it (library AND queue). Null when building something new.
    private String editingTaskId;
    private String editingTaskName;
    private JTextField autoDelayMinInput;
    private JTextField autoDelayMaxInput;
    private final List<JComponent> autoDelayControls = new ArrayList<>();
    private static final int AUTO_DELAY_DEFAULT_MIN = 600;
    private static final int AUTO_DELAY_DEFAULT_MAX = 1400;

    // ── Config flags ──────────────────────────────────────────────────────────
    private boolean requireDescription = false;
    private boolean requireStatus      = false;

    private JPanel currentParamPanel;

    // =========================================================================
    // Constructor
    // =========================================================================
    public TaskBuilder(DreamBotMenu botMenu) {
        super(new BorderLayout(15, 15));
        setBorder(new EmptyBorder(15, 15, 15, 15));
        setBackground(PANEL_SURFACE);

        this.botMenu = botMenu;
        this.actionSelector   = botMenu.actionSelector;

        this.modelTaskBuilder = botMenu.modelTaskBuilder;

        this.listTaskBuilder = new JList<>(modelTaskBuilder);
        this.listLibrary = new JLibraryList();

        ///  Override how this list displays its items to give more detailed information about each item in the builder.
        listTaskBuilder.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                if (value instanceof Action) {
                    Action action = (Action) value;
                    // write as build string to task builder display
                    setText(action.toBuildString());
                }

                return this;
            }
        });

        add(createSubtitle("Task Builder"),  BorderLayout.NORTH);
        add(buildLeft(),   BorderLayout.WEST);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildRight(),  BorderLayout.EAST);

        addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) {
                onTabShown();
            }
        });
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /** Call from scanTimer when this tab is active. */
    public void refresh() {
        listLibrary.rescanNearby();
        refreshDynamicControls();
    }

    public void onTabShown() {
        refreshList();
    }

    /** Pre-fills all inputs from an existing Task (for "View in builder"). */
    // ── Builder-draft persistence hooks (used by DreamBotMenu <-> LocalStore) ──
    /** @return the current text in the builder's name field. */
    public String getDraftName() { return taskNameInput.getText(); }
    /** @return the current text in the builder's description field. */
    public String getDraftDescription() { return taskDescriptionInput.getText(); }
    /** @return the current text in the builder's status field. */
    public String getDraftStatus() { return taskStatusInput.getText(); }

    /** @return whether the builder's auto-delay checkbox is ticked (Patch B). */
    public boolean getDraftAutoDelay() {
        return chkAutoDelay != null && chkAutoDelay.isSelected();
    }

    /** @return the builder's auto-delay minimum in ms (Patch B). */
    public int getDraftAutoDelayMin() {
        return ActionUtil.parseInt(autoDelayMinInput != null ? autoDelayMinInput.getText() : null,
                AUTO_DELAY_DEFAULT_MIN);
    }

    /** @return the builder's auto-delay maximum in ms (Patch B). */
    public int getDraftAutoDelayMax() {
        return ActionUtil.parseInt(autoDelayMaxInput != null ? autoDelayMaxInput.getText() : null,
                AUTO_DELAY_DEFAULT_MAX);
    }

    /** Restores the name/description/status fields of a saved builder draft. */
    public void applyDraft(String name, String description, String status) {
        applyDraft(name, description, status, false, AUTO_DELAY_DEFAULT_MIN, AUTO_DELAY_DEFAULT_MAX);
    }

    /** Restores a saved builder draft, including the auto-delay settings (Patch B). */
    public void applyDraft(String name, String description, String status,
                           boolean autoDelay, int autoDelayMin, int autoDelayMax) {
        taskNameInput.setText(name == null ? "" : name);
        taskDescriptionInput.setText(description == null ? "" : description);
        taskStatusInput.setText(status == null ? "" : status);
        chkAutoDelay.setSelected(autoDelay);
        int min = Math.max(0, autoDelayMin);
        autoDelayMinInput.setText(String.valueOf(min));
        autoDelayMaxInput.setText(String.valueOf(Math.max(min, autoDelayMax)));
        syncAutoDelayEnabled();
    }

    public void loadTask(DreamBotMenu.Task task) {
        if (task == null) return;
        taskNameInput.setText(task.getName());
        taskDescriptionInput.setText(task.getDescription());
        taskStatusInput.setText(task.getStatus());
        // Patch B.2: editing an existing task - remember WHICH one, so saving updates every
        // instance of it (queue included), and make the button say what it will do.
        editingTaskId = task.getId();
        editingTaskName = task.getName();
        if (btnAddToLibrary != null) btnAddToLibrary.setText("Save changes");
        modelTaskBuilder.clear();
        if (task.getActions() != null)
            // copy each action so builder edits never mutate the original task in the list/library
            task.getActions().forEach(a -> { if (a != null) modelTaskBuilder.addElement(a.copy()); });
        refreshList();
    }

    /** Pre-fills the action selector from a single Action (single-click in action list). */
    public void loadAction(Action action) {
        if (action == null)
            return;

        actionSelector.setSelectedAction(action);
        // pull the freshly-loaded action's parameter panel into view
        refreshDynamicControls();
    }

    /** Clears all inputs and the action list. */
    public void reset() {
        taskNameInput.setText("");
        taskDescriptionInput.setText("");
        taskStatusInput.setText("");
        chkAutoDelay.setSelected(false);
        autoDelayMinInput.setText(String.valueOf(AUTO_DELAY_DEFAULT_MIN));
        autoDelayMaxInput.setText(String.valueOf(AUTO_DELAY_DEFAULT_MAX));
        syncAutoDelayEnabled();
        editingTaskId = null;
        editingTaskName = null;
        if (btnAddToLibrary != null) btnAddToLibrary.setText("Add to library...");
        modelTaskBuilder.clear();
        refreshList();
    }

    ///  Panel construction

    private JPanel buildLeft() {
        JPanel left = createPanelBorderLayout(0, 10);
        JPanel config = createPanelGridBagLayout();

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        //gbc.anchor = GridBagConstraints.WEST;
        gbc.insets  = new Insets(3, 3, 3, 3);
        // fill horizontally?
        gbc.weightx = 1.0;
        gbc.gridx = 0;
        gbc.gridy = 0;
        config.add(createLabel("Select action:"), gbc);

        gbc.gridy = 1;
        styleComp(actionSelector);
        config.add(actionSelector, gbc);
        actionSelector.addSelectionListener(e -> onSelectionChanged());

        gbc.gridy = 2;
        // TODO potentially remove this control panel
        dynamicControlPanel = createPanel();
        dynamicControlPanel.setOpaque(false);

        // force it to stretch wide
        gbc.fill = GridBagConstraints.HORIZONTAL;
        // give it the horizontal priority
        gbc.weightx = 1.0;
        JPanel initialPanel = actionSelector.getParamsPanel();
        if (initialPanel != null)
            dynamicControlPanel.add(initialPanel, BorderLayout.WEST);
        config.add(dynamicControlPanel, gbc);

        gbc.gridy  = 3;
        gbc.insets = new Insets(8, 3, 3, 3);
        JButton btnAdd = createButton("Add to builder...", COLOR_BTN_ADD, null);
        btnAdd.addActionListener(e -> addTemplateToBuilder());
        config.add(btnAdd, gbc);

        // Patch B.1: tall param panels (Interact has four fields + mode) used to crush - and,
        // when the client's EDT was drowning in UI errors, paint OVER - the entity list below.
        // The params now live in a scrolling wrapper that never claims more than the column
        // height minus ~240px, so the library always keeps a usable strip and the params
        // scroll instead of colliding.
        JScrollPane cfgScroll = new JScrollPane(config,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER) {
            @Override public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                Container parent = getParent();
                int columnHeight = parent != null ? parent.getHeight() : 0;
                if (columnHeight > 0)
                    // Patch B.2: params and the entity list each get at least half the column
                    d.height = Math.min(d.height, Math.max(220, columnHeight / 2));
                return d;
            }
        };
        Theme.thinScrollbars(cfgScroll);
        cfgScroll.setBorder(BorderFactory.createEmptyBorder());
        cfgScroll.setOpaque(false);
        cfgScroll.getViewport().setOpaque(false);

        left.add(cfgScroll,   BorderLayout.NORTH);
        left.add(listLibrary, BorderLayout.CENTER);

        return left;
    }

    /** Opens a modal editor for the watchers attached to one action in the chain (B.4). */
    private void openActionWatchers(Action action) {
        if (action == null) return;
        main.menu.components.TriggerEditor editor = new main.menu.components.TriggerEditor(
                action.getTriggers(), true, botMenu::pickResponseActionPublic);
        editor.setPreferredSize(new Dimension(460, 380));
        JOptionPane.showMessageDialog(this, editor,
                "Checks for " + action.getName(), JOptionPane.PLAIN_MESSAGE);
        refreshList();   // build-string previews may now show attached-watcher counts
    }

    private void addTemplateToBuilder() {
        Action selected = actionSelector.getSelectedAction();
        // null-check BEFORE dereferencing (the old order guaranteed an NPE instead of a toast)
        if (selected == null) {
            toast("Invalid or incomplete action!", btnAddToLibrary, false);
            return;
        }

        Action newAction = selected.copy();
        modelTaskBuilder.addElement(newAction);

        // Patch B.2 (corrected semantics): the auto-wait checkbox INSERTS a visible Wait action
        // after each action you add - a real, editable step in the chain rolling a random time
        // between your Min-Max every run - instead of the invisible engine-side delay Patch B
        // used. What you see in the chain is exactly what executes.
        boolean insertedWait = false;
        if (chkAutoDelay != null && chkAutoDelay.isSelected()
                && !(newAction instanceof main.actions.Wait)) {
            main.actions.Wait w = new main.actions.Wait();
            java.util.Map<String, String> cfg = new java.util.HashMap<>();
            cfg.put("Mode", "fixed");
            cfg.put("Min", String.valueOf(getDraftAutoDelayMin()));
            cfg.put("Max", String.valueOf(getDraftAutoDelayMax()));
            w.deserialize(cfg);
            modelTaskBuilder.addElement(w);
            insertedWait = true;
        }

        toast("Added " + newAction + (insertedWait ? " + auto-wait" : "") + "!", btnAddToLibrary, true);
        refreshList(true);
    }

    /**
     * This function is called when the action selector selection changed event is triggered.
     */
    private void onSelectionChanged() {
        refresh();
    }

    private JPanel buildCenter() {
        JPanel center = new JPanel(new BorderLayout(5, 5));
        center.setOpaque(false);

        JLabel listLabel = new JLabel("Action List:", SwingConstants.CENTER);
            listLabel.setForeground(COLOR_BLOOD);
            listLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));

        listTaskBuilder.setBackground(PANEL_SURFACE);
        listTaskBuilder.setForeground(TEXT_MAIN);
        listTaskBuilder.setSelectionBackground(new Color(60, 0, 0));
//
//        JButton btnUp = createNavButton("▲", modelTaskBuilder, listTaskBuilder);
//        JButton btnDown = createNavButton("▼", modelTaskBuilder, listTaskBuilder);
//        btnUp.addActionListener(e   -> moveSelected(-1));
//        btnDown.addActionListener(e -> moveSelected(1));

        JPanel nav = new JPanel(new GridLayout(0, 1, 0, 5));
        nav.setOpaque(false);
//        nav.add(btnUp);
//        nav.add(btnDown);

        btnRemove = createButton("Remove", new Color(139, 0, 0), null);
        btnRemove.setEnabled(false);
        btnRemove.addActionListener(e -> removeSelected());

        JButton btnReset = createButton("Reset", new Color(50, 50, 50), null);
        btnReset.addActionListener(e -> {
            reset();
            toast("Task builder reset!", btnReset, true);
        });

        // Patch B.4: per-action watchers - attach conditional checks to the SELECTED action in
        // the chain (e.g. Loot carries "if inventory full -> bank instead"). Distinct from the
        // always-on Watchers tab.
        btnWatchers = createButton("Checks\u2026", new Color(70, 55, 20), null);
        btnWatchers.setEnabled(false);
        btnWatchers.addActionListener(e -> {
            int idx = listTaskBuilder.getSelectedIndex();
            if (idx < 0) { toast("Select an action first", btnWatchers, false); return; }
            // (user-facing name is "Checks"; internals keep the Trigger/Watcher class names)
            openActionWatchers(modelTaskBuilder.getElementAt(idx));
        });

        listTaskBuilder.addListSelectionListener(e -> {
            boolean has = !listTaskBuilder.isSelectionEmpty();
            btnRemove.setEnabled(has);
            btnWatchers.setEnabled(has);
        });

        listTaskBuilder.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int idx = listTaskBuilder.getSelectedIndex();
                if (idx == -1) return;
                if (e.getClickCount() == 1) {
                    loadAction(modelTaskBuilder.getElementAt(idx));
                } else if (e.getClickCount() == 2) {
                    int[] sel = listTaskBuilder.getSelectedIndices();
                    for (int i = sel.length - 1; i >= 0; i--)
                        modelTaskBuilder.remove(sel[i]);
                    toast("Removed " + sel.length + " action(s)!", btnRemove, true);
                    refreshList();
                }
            }
        });

        JPanel bottomBtns = new JPanel(new GridLayout(1, 3, 5, 5));
        bottomBtns.setOpaque(false);
        bottomBtns.add(btnRemove);
        bottomBtns.add(btnWatchers);
        bottomBtns.add(btnReset);

        center.add(listLabel,                        BorderLayout.NORTH);
        center.add(nav,                              BorderLayout.WEST);
        center.add(Theme.thinScrollbars(new JScrollPane(listTaskBuilder)), BorderLayout.CENTER);
        center.add(bottomBtns,                       BorderLayout.SOUTH);

        return center;
    }

    private void removeSelected() {
        int idx = listTaskBuilder.getSelectedIndex();
        if (idx != -1) {
            modelTaskBuilder.remove(idx);
            toast("Action removed!", btnRemove, true);
            refreshList();
        } else {
            toast("Select an action first!", btnRemove, false);
        }
    }

    private JPanel buildRight() {
        JPanel east = new JPanel(new GridBagLayout());
        east.setOpaque(false);

        GridBagConstraints g = new GridBagConstraints();
        g.fill    = GridBagConstraints.HORIZONTAL;
        g.weightx = 1.0;
        g.insets  = new Insets(5, 5, 5, 5);

        taskNameInput        = new JTextField(25);
        taskDescriptionInput = new JTextField(25);
        taskStatusInput      = new JTextField(25);

        styleComp(taskNameInput);
        styleComp(taskDescriptionInput);
        styleComp(taskStatusInput);

        g.gridy = 0; east.add(createLabel("Task name:"),   g);
        g.gridy = 1; east.add(taskNameInput,               g);
        g.gridy = 2; east.add(createLabel("Description:"), g);
        g.gridy = 3; east.add(taskDescriptionInput,        g);
        g.gridy = 4; east.add(createLabel("Status:"),      g);
        g.gridy = 5; east.add(taskStatusInput,             g);

        g.gridy = 6;
        g.insets = new Insets(12, 5, 2, 5);
        east.add(buildAutoDelayHeader(), g);
        g.gridy = 7;
        g.insets = new Insets(2, 5, 5, 5);
        east.add(buildAutoDelayControls(), g);

        btnAddToLibrary = createButton("Add to library...", COLOR_BTN_ADD, null);
        btnAddToLibrary.addActionListener(e -> handleAddToLibrary());

        g.gridy  = 8;
        g.insets = new Insets(20, 5, 5, 5);
        east.add(btnAddToLibrary, g);

        return east;
    }

    /** The "Auto-delay between actions" checkbox (Patch B). */
    private JComponent buildAutoDelayHeader() {
        chkAutoDelay = new JCheckBox("Auto-delay between actions");
        chkAutoDelay.setToolTipText("When ticked, the script pauses a random Min-Max ms after each"
                + " completed action in this task - the manual Wait you'd add anyway, automated."
                + " Waits you placed yourself are never double-delayed.");
        styleComp(chkAutoDelay);
        chkAutoDelay.addActionListener(e -> syncAutoDelayEnabled());
        return chkAutoDelay;
    }

    /** Min-Max fields plus the +/-50 and +/-100ms steppers that nudge the whole range (Patch B). */
    private JComponent buildAutoDelayControls() {
        autoDelayMinInput = new JTextField(String.valueOf(AUTO_DELAY_DEFAULT_MIN), 5);
        autoDelayMaxInput = new JTextField(String.valueOf(AUTO_DELAY_DEFAULT_MAX), 5);
        autoDelayMinInput.setToolTipText("Shortest automatic pause, in milliseconds.");
        autoDelayMaxInput.setToolTipText("Longest automatic pause, in milliseconds.");
        styleComp(autoDelayMinInput);
        styleComp(autoDelayMaxInput);

        JPanel range = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        range.setOpaque(false);
        range.add(autoDelayMinInput);
        range.add(createLabel("-"));
        range.add(autoDelayMaxInput);
        range.add(createLabel("ms"));

        JPanel steppers = new JPanel(new GridLayout(1, 4, 4, 0));
        steppers.setOpaque(false);
        autoDelayControls.clear();
        autoDelayControls.add(autoDelayMinInput);
        autoDelayControls.add(autoDelayMaxInput);
        for (int delta : new int[]{ -100, -50, 50, 100 }) {
            JButton b = createButton((delta > 0 ? "+" : "") + delta, new Color(50, 50, 50), null);
            b.setToolTipText("Nudge the whole delay range by " + delta + "ms");
            b.addActionListener(e -> nudgeAutoDelay(delta));
            steppers.add(b);
            autoDelayControls.add(b);
        }

        JPanel box = new JPanel(new GridLayout(0, 1, 0, 4));
        box.setOpaque(false);
        box.add(range);
        box.add(steppers);

        syncAutoDelayEnabled();
        return box;
    }

    /** Shifts both auto-delay bounds by {@code deltaMs}, clamped at 0 and keeping max >= min. */
    private void nudgeAutoDelay(int deltaMs) {
        int min = ActionUtil.parseInt(autoDelayMinInput.getText(), AUTO_DELAY_DEFAULT_MIN);
        int max = ActionUtil.parseInt(autoDelayMaxInput.getText(), AUTO_DELAY_DEFAULT_MAX);
        min = Math.max(0, min + deltaMs);
        max = Math.max(min, max + deltaMs);
        autoDelayMinInput.setText(String.valueOf(min));
        autoDelayMaxInput.setText(String.valueOf(max));
    }

    /** Greys the delay fields/steppers out while the checkbox is unticked. */
    private void syncAutoDelayEnabled() {
        boolean on = chkAutoDelay != null && chkAutoDelay.isSelected();
        for (JComponent c : autoDelayControls)
            c.setEnabled(on);
    }

    // =========================================================================
    // Logic
    // =========================================================================

    private void handleAddToLibrary() {
        List<Action> actions = new ArrayList<>();
        for (int i = 0; i < modelTaskBuilder.size(); i++)
            actions.add(modelTaskBuilder.get(i));

        DreamBotMenu.Task task = buildTask(actions);
        if (task == null) return;

        // ── Patch B.2: "Save changes" - editing an existing task updates EVERY instance ──
        // The old overwrite replaced one library row and left queue copies stale, which is why
        // "overwriting an action already in the task list" never updated it. Identity (Task.id)
        // now propagates the save to the library AND the live queue in one go.
        if (editingTaskId != null) {
            task.restoreId(editingTaskId);
            int touched = propagate(task, editingTaskId, editingTaskName);
            if (touched == 0) {
                botMenu.libraryAdd(task);   // original was deleted meanwhile - re-add
                touched = 1;
            }
            toast("Saved - updated " + touched + " place(s)", btnAddToLibrary, true);
            reset();
            botMenu.refreshLibraryFromBuilder();
            return;
        }

        boolean exists = botMenu.findLibraryTask(null, task.getName()) != null;

        if (!exists) {
            botMenu.libraryAdd(task);
            toast("Added to library!", btnAddToLibrary, true);
            reset();
            botMenu.refreshLibraryFromBuilder();
        } else {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    task.getName() + " already exists, overwrite it (everywhere it's used)?",
                    "Overwrite task?",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );
            if (choice == JOptionPane.YES_OPTION) {
                // adopt the existing task's identity so queue copies update too
                DreamBotMenu.Task existing = botMenu.findLibraryTask(null, task.getName());
                String existingId = existing != null ? existing.getId() : null;
                if (existingId != null) task.restoreId(existingId);
                int touched = propagate(task, existingId, task.getName());
                toast("Task updated in " + Math.max(1, touched) + " place(s)!", btnAddToLibrary, true);
                reset();
                botMenu.refreshLibraryFromBuilder();
            } else {
                toast("Cancelled.", btnAddToLibrary, false);
            }
        }
    }

    /**
     * Replaces every instance of a logical task (matched by id, or by name for pre-B.2 tasks
     * without one) in BOTH the library and the live queue with a fresh copy of {@code updated}.
     * Each slot gets its own copy so no aliasing is introduced. @return instances updated.
     */
    private int propagate(DreamBotMenu.Task updated, String id, String name) {
        int touched = 0;
        // library side goes through the MASTER list (filtered-out instances update too)
        touched += botMenu.libraryPropagate(updated, id, name);
        try {
            touched += propagateInto(botMenu.getModelTaskList(), updated, id, name);
        } catch (Throwable ignored) {}
        return touched;
    }

    private int propagateInto(DefaultListModel<DreamBotMenu.Task> model,
                              DreamBotMenu.Task updated, String id, String name) {
        int touched = 0;
        for (int i = 0; i < model.getSize(); i++) {
            DreamBotMenu.Task t = model.getElementAt(i);
            if (t == null) continue;
            boolean match = (id != null && id.equals(t.getId()))
                    || (id == null && name != null && name.equalsIgnoreCase(t.getName()));
            if (match) {
                DreamBotMenu.Task copy = new DreamBotMenu.Task(updated);  // copy keeps the id
                copy.setRepeat(t.getRepeat());   // per-instance repeat is queue state, not content
                copy.setOrigin(t.getOrigin());   // provenance survives edits (Patch B.5)
                model.set(i, copy);
                touched++;
            }
        }
        return touched;
    }

    private DreamBotMenu.Task buildTask(List<Action> actions) {
        String name        = taskNameInput.getText().trim();
        String description = taskDescriptionInput.getText().trim();
        String status      = taskStatusInput.getText().trim();

        if (actions == null || actions.isEmpty()) { toast("Add some actions first!", btnAddToLibrary, false); return null; }
        if (name.isEmpty())                        { toast("Enter a task name!",      btnAddToLibrary, false); return null; }
        if (requireDescription && description.isEmpty()) { toast("Enter a description!", btnAddToLibrary, false); return null; }
        if (requireStatus      && status.isEmpty())      { toast("Enter a status!",      btnAddToLibrary, false); return null; }

        // Patch B.2: auto-wait is now delivered as REAL Wait actions inserted while building
        // (see addTemplateToBuilder), so the task itself carries no hidden engine-side delay.
        return new DreamBotMenu.Task(name, description, actions, status);
    }

    private void moveSelected(int dir) {
        int idx    = listTaskBuilder.getSelectedIndex();
        int newIdx = idx + dir;
        if (idx == -1 || newIdx < 0 || newIdx >= modelTaskBuilder.size()) return;
        Action a = modelTaskBuilder.get(idx);
        modelTaskBuilder.set(idx,    modelTaskBuilder.get(newIdx));
        modelTaskBuilder.set(newIdx, a);
        listTaskBuilder.setSelectedIndex(newIdx);
    }

    private void refreshDynamicControls() {
        JPanel newPanel = actionSelector.getParamsPanel();

        // dont refresh if its already the same panel
        if (newPanel.equals(currentParamPanel))
            return;

        dynamicControlPanel.removeAll();
        dynamicControlPanel.setOpaque(false);
        dynamicControlPanel.add(newPanel, BorderLayout.CENTER);
        currentParamPanel = newPanel;

        dynamicControlPanel.revalidate();
        dynamicControlPanel.repaint();

        // Patch B.1: the swapped panel can change the left column's preferred HEIGHT (Interact
        // is much taller than Walk). Revalidate the whole tab so the library below is re-laid
        // out under it instead of stale bounds painting on top of each other.
        revalidate();
        repaint();
    }

//    private void refreshDynamicControls() {
//        dynamicControlPanel.removeAll();
//        dynamicControlPanel.setOpaque(false);
//
//        JPanel controls = actionSelector.getSelectedAction().getParamPanel();
//        if (controls != null) {
//            // style it to match your theme
//            styleComp(controls);
//            dynamicControlPanel.add(controls, BorderLayout.CENTER);
//        }
//
//        dynamicControlPanel.revalidate();
//        dynamicControlPanel.repaint();
//    }

    private void refreshList(boolean selectLast) {
        if (selectLast && !modelTaskBuilder.isEmpty())
            listTaskBuilder.setSelectedIndex(modelTaskBuilder.getSize() - 1);
        listTaskBuilder.repaint();

        refresh();
    }

    private void refreshList() {
        refreshList(false);
    }

    private void toast(String msg, JComponent anchor, boolean success) {
        botMenu.showToast(msg, anchor, success);
    }
}