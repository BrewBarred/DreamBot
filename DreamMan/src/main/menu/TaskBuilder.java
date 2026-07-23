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
    private JButton btnChance;
    /** Patch B.17: in-place duplicate of the selected chain action. */
    private JButton btnDuplicate;
    /** Patch B.17: writes the editor's params back onto the selected chain action. */
    private JButton btnUpdateAction;
    /** The chain row currently loaded for editing (-1 = none). Mirrors the list selection. */
    private int editingActionIndex = -1;
    /** Patch B.14: admin-only "VIP task" toggle. Hidden for non-admins. */
    private JCheckBox chkVipOnly;

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
    private JTextField taskTagsInput;   // v1.62: the tags step (before Save to library)
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
                    String text = action.toBuildString();
                    // Patch B.7 (glyph-safe in B.17): surface a non-100% chance right in the row
                    // so it's obvious which actions are randomised and by how much.
                    if (action.getChancePercent() < 100)
                        text = "[" + action.getChancePercent() + "%]  " + text;
                    if (action.isOnStartOnly())
                        text = "[start]  " + text;   // v1.31: setup step, first pass only
                    setText(text);
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
        syncAutoDelayEnabled();   // v1.30: setSelected() fires no listener - sync explicitly
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
        if (taskTagsInput != null)   // v1.62: load its tags into the tags step
            taskTagsInput.setText(String.join(", ", task.getTags()));
        // v1.62: editing an existing task - remember WHICH one so a same-name save updates every
        // instance and a rename can add-new + delete-old. The button label stays "Save to
        // library" (it decides add vs update from the name now, not from a sticky mode).
        editingTaskId = task.getId();
        editingTaskName = task.getName();
        modelTaskBuilder.clear();
        if (task.getActions() != null)
            // copy each action so builder edits never mutate the original task in the list/library
            task.getActions().forEach(a -> { if (a != null) modelTaskBuilder.addElement(a.copyDeep()); });
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
        if (taskTagsInput != null) taskTagsInput.setText("");   // v1.62
        chkAutoDelay.setSelected(false);
        syncAutoDelayEnabled();   // v1.30: same - keep the fields greyed to match
        autoDelayMinInput.setText(String.valueOf(AUTO_DELAY_DEFAULT_MIN));
        autoDelayMaxInput.setText(String.valueOf(AUTO_DELAY_DEFAULT_MAX));
        syncAutoDelayEnabled();
        editingTaskId = null;
        editingTaskName = null;
        editingActionIndex = -1;                                  // B.17: nothing armed for edit
        if (btnUpdateAction != null) btnUpdateAction.setEnabled(false);
        // v1.62: button label is static "Save to library" now - no add/edit mode swap
        modelTaskBuilder.clear();
        refreshList();
    }

    ///  Panel construction

    private JPanel buildLeft() {
        // ── Patch B.17 layout rework ─────────────────────────────────────────
        // The old left column put the "Select action" dropdown INSIDE the same scroll pane as
        // the parameter fields, so a tall action (Interact) scrolled the dropdown clean out of
        // view - baffling if you didn't know it was up there. Now the column is:
        //   NORTH  - the label + dropdown, pinned, always visible
        //   CENTER - just the selected action's parameters, scrolling on their own
        //   SOUTH  - "Add to builder" / "Update action", pinned, always reachable
        // The entity list that used to squat under all this now lives in the RIGHT column
        // (above the task-name fields), where the widths match anyway.
        JPanel left = createPanelBorderLayout(0, 8);
        left.setPreferredSize(new Dimension(300, 0));

        JPanel head = new JPanel(new BorderLayout(0, 4));
        head.setOpaque(false);
        head.add(createLabel("Select action:"), BorderLayout.NORTH);
        styleComp(actionSelector);
        head.add(actionSelector, BorderLayout.CENTER);
        actionSelector.addSelectionListener(e -> onSelectionChanged());

        dynamicControlPanel = createPanel();
        dynamicControlPanel.setOpaque(false);
        JPanel initialPanel = actionSelector.getParamsPanel();
        if (initialPanel != null)
            dynamicControlPanel.add(initialPanel, BorderLayout.CENTER);

        // the params are the ONLY thing that scrolls; the wrapper keeps them pinned to the top
        JPanel paramsWrap = new JPanel(new BorderLayout());
        paramsWrap.setOpaque(false);
        paramsWrap.add(dynamicControlPanel, BorderLayout.NORTH);
        JScrollPane cfgScroll = new JScrollPane(paramsWrap,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        Theme.thinScrollbars(cfgScroll);
        cfgScroll.setBorder(BorderFactory.createEmptyBorder());
        cfgScroll.setOpaque(false);
        cfgScroll.getViewport().setOpaque(false);
        cfgScroll.getVerticalScrollBar().setUnitIncrement(14);
        cfgScroll.setMinimumSize(new Dimension(0, 140));

        JButton btnAdd = createButton("Add to builder...", COLOR_BTN_ADD, null);
        btnAdd.addActionListener(e -> addTemplateToBuilder());
        btnUpdateAction = createButton("Update action", new Color(70, 55, 20), null);
        btnUpdateAction.setEnabled(false);
        btnUpdateAction.setToolTipText("Save these parameter changes back onto the action "
                + "selected in the chain");
        btnUpdateAction.addActionListener(e -> updateSelectedAction());
        JPanel btns = new JPanel(new GridLayout(1, 2, 6, 0));
        btns.setOpaque(false);
        btns.add(btnAdd);
        btns.add(btnUpdateAction);

        // ── v1.30: the buttons HUG the params ────────────────────────────────
        // Previously they were pinned to the very bottom of the column, leaving a dead gap
        // under short forms (Wait). Now a GridBag stack gives the params scroller its
        // preferred height (weighty 0, so the buttons sit directly beneath it) and a glue row
        // soaks up the leftovers. When the params are taller than the column, GridBag
        // compresses the scroller toward its minimum instead of pushing the buttons off
        // screen - so the attributes scroll ONLY when all three genuinely can't fit, and the
        // buttons are always visible either way.
        // v1.62: the auto-delay control moved here, above the action buttons (it used to sit in
        // the right-hand task form where the tags step now lives). It belongs with the per-action
        // building controls anyway - it governs the pause inserted between actions. header() must
        // run before controls() (controls syncs against the checkbox header creates).
        JPanel delayBlock = new JPanel(new BorderLayout(0, 4));
        delayBlock.setOpaque(false);
        delayBlock.add(buildAutoDelayHeader(),   BorderLayout.NORTH);
        delayBlock.add(buildAutoDelayControls(), BorderLayout.CENTER);

        JPanel stack = new JPanel(new GridBagLayout());
        stack.setOpaque(false);
        GridBagConstraints sc = new GridBagConstraints();
        sc.gridx = 0; sc.weightx = 1.0; sc.fill = GridBagConstraints.HORIZONTAL;
        sc.gridy = 0; sc.weighty = 0; sc.fill = GridBagConstraints.BOTH;
        stack.add(cfgScroll, sc);
        sc.gridy = 1; sc.weighty = 0; sc.fill = GridBagConstraints.HORIZONTAL;
        sc.insets = new Insets(8, 0, 0, 0);
        stack.add(delayBlock, sc);                       // v1.62: auto-delay, above the buttons
        sc.gridy = 2; sc.weighty = 0; sc.fill = GridBagConstraints.HORIZONTAL;
        sc.insets = new Insets(8, 0, 0, 0);
        stack.add(btns, sc);
        sc.gridy = 3; sc.weighty = 1.0; sc.insets = new Insets(0, 0, 0, 0);
        JPanel glue = new JPanel();
        glue.setOpaque(false);
        stack.add(glue, sc);

        left.add(head,  BorderLayout.NORTH);
        left.add(stack, BorderLayout.CENTER);

        return left;
    }

    /** Opens a modal editor for the watchers attached to one action in the chain (B.4). */
    /**
     * Slider dialog for one action's chance-to-run (Patch B.7). 100% means guaranteed; drag down
     * to make the action a sometimes-thing. A live label shows the percent and a plain-English
     * hint ("runs about 3 in 10 passes").
     */
    private void openActionChance(Action action) {
        if (action == null) return;

        JSlider slider = new JSlider(1, 100, action.getChancePercent());
        slider.setMajorTickSpacing(25);
        slider.setMinorTickSpacing(5);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.setOpaque(false);

        JLabel readout = new JLabel();
        readout.setFont(new Font("Segoe UI", Font.BOLD, 14));
        readout.setHorizontalAlignment(SwingConstants.CENTER);
        Runnable upd = () -> {
            int v = slider.getValue();
            readout.setText(v >= 100
                    ? "100%  \u2014  always runs (guaranteed)"
                    : v + "%  \u2014  runs about " + Math.round(v / 10.0) + " in 10 passes");
        };
        slider.addChangeListener(e -> upd.run());
        upd.run();

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        JLabel title = new JLabel("<html>How often should <b>" + action.getName()
                + "</b> actually run when the task reaches it?</html>");
        title.setForeground(main.menu.Theme.TEXT);
        panel.add(title, BorderLayout.NORTH);
        panel.add(slider, BorderLayout.CENTER);
        panel.add(readout, BorderLayout.SOUTH);
        panel.setPreferredSize(new Dimension(420, 150));

        int r = JOptionPane.showConfirmDialog(this, panel, "Chance to run",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r == JOptionPane.OK_OPTION) {
            action.setChancePercent(slider.getValue());
            refreshList();
            toast(action.getChancePercent() < 100
                    ? action.getName() + " set to " + action.getChancePercent() + "%"
                    : action.getName() + " will always run", btnChance, true);
        }
    }

    private void openActionWatchers(Action action) {
        if (action == null) return;
        main.menu.components.TriggerEditor editor = new main.menu.components.TriggerEditor(
                action.getTriggers(), true, botMenu::pickResponseActionPublic);
        // v1.33: the old 460x380 JOptionPane squeezed the detail column to ~150px, so every
        // combo/field wrapped under its label and clipped to a sliver. Give it a real, resizable
        // dialog with room for the fields.
        editor.setPreferredSize(new Dimension(780, 560));
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this),
                "Triggers for " + action.getName(), java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setAlwaysOnTop(true);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBorder(new EmptyBorder(10, 10, 10, 10));
        wrap.add(editor, BorderLayout.CENTER);
        JButton ok = createButton("OK", new Color(30, 60, 40), null);
        ok.addActionListener(e -> dlg.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(ok);
        wrap.add(south, BorderLayout.SOUTH);
        dlg.setContentPane(wrap);
        dlg.setResizable(true);
        dlg.pack();
        dlg.setMinimumSize(new Dimension(680, 480));
        dlg.setLocationRelativeTo(this);
        dlg.toFront();
        dlg.setVisible(true);
        refreshList();   // build-string previews may now show attached-watcher counts
    }

    private void addTemplateToBuilder() {
        Action selected = actionSelector.getSelectedAction();
        // null-check BEFORE dereferencing (the old order guaranteed an NPE instead of a toast)
        if (selected == null) {
            toast("Invalid or incomplete action!", btnAddToLibrary, false);
            return;
        }

        // v1.86: copyDeep, so duplicating a step keeps its on-start flag, chance and checks.
        Action newAction = selected.copyDeep();
        main.tools.TargetHistory.record(newAction.getParamTarget());   // v1.31: MRU suggestions
        modelTaskBuilder.addElement(newAction);
        int addedIndex = modelTaskBuilder.getSize() - 1;   // B.17: reselect THIS row after add

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
        // Patch B.17: selection now means "editing", so select the action just added - not the
        // trailing auto-wait - or the editor would flip to Wait after every add.
        listTaskBuilder.setSelectedIndex(addedIndex);
        refreshList(false);
    }

    /**
     * This function is called when the action selector selection changed event is triggered.
     */
    private void onSelectionChanged() {
        // v1.30 LAG FIX: switching the action type used to call refresh(), which ALSO kicked a
        // full nearby entity rescan (DreamBot API calls) on every combo change. The entity
        // list doesn't depend on which action type is selected, so a switch now only swaps the
        // parameter panel - instant. Rescans still run on tab-shown, the timer, and Scan.
        refreshDynamicControls();
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
        btnWatchers = createButton("Triggers\u2026", new Color(70, 55, 20), null);
        btnWatchers.setEnabled(false);
        btnWatchers.addActionListener(e -> {
            int idx = listTaskBuilder.getSelectedIndex();
            if (idx < 0) { toast("Select an action first", btnWatchers, false); return; }
            // (user-facing name is "Triggers"; internal class names already match)
            openActionWatchers(modelTaskBuilder.getElementAt(idx));
        });

        // Patch B.7: per-action chance-to-run. Default 100% (guaranteed); dial down to make an
        // action fire only sometimes.
        btnChance = createButton("Chance\u2026", new Color(20, 55, 45), null);
        btnChance.setEnabled(false);
        btnChance.addActionListener(e -> {
            int idx = listTaskBuilder.getSelectedIndex();
            if (idx < 0) { toast("Select an action first", btnChance, false); return; }
            openActionChance(modelTaskBuilder.getElementAt(idx));
        });

        // Patch B.17: one-click duplicate of the selected action (also on the right-click menu).
        btnDuplicate = createButton("Duplicate", new Color(45, 45, 60), null);
        btnDuplicate.setEnabled(false);
        btnDuplicate.setToolTipText("Insert a copy of the selected action right below it "
                + "(keeps its randomness and triggers)");
        btnDuplicate.addActionListener(e -> {
            int idx = listTaskBuilder.getSelectedIndex();
            if (idx < 0) { toast("Select an action first", btnDuplicate, false); return; }
            duplicateAction(idx);
        });

        // ── Patch B.17: selecting an action EDITS it ─────────────────────────
        // The selected row's parameters load into the left column; change them and press
        // "Update action" to write them back onto that row. Chance-to-run and attached checks
        // are preserved across the update (they're edited via their own dialogs).
        listTaskBuilder.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int idx = listTaskBuilder.getSelectedIndex();
            boolean has = idx >= 0;
            btnRemove.setEnabled(has);
            btnWatchers.setEnabled(has);
            btnChance.setEnabled(has);
            btnDuplicate.setEnabled(has);
            if (btnUpdateAction != null) btnUpdateAction.setEnabled(has);
            editingActionIndex = idx;
            if (has) loadAction(modelTaskBuilder.getElementAt(idx));
        });

        // ── v1.31: drag to reorder the chain ─────────────────────────────────
        listTaskBuilder.setDragEnabled(true);
        listTaskBuilder.setDropMode(DropMode.INSERT);
        listTaskBuilder.setTransferHandler(new TransferHandler() {
            private int fromIndex = -1;
            @Override public int getSourceActions(JComponent c) { return MOVE; }
            @Override protected java.awt.datatransfer.Transferable createTransferable(JComponent c) {
                fromIndex = listTaskBuilder.getSelectedIndex();
                return new java.awt.datatransfer.StringSelection("");
            }
            @Override public boolean canImport(TransferSupport support) {
                return support.isDrop() && fromIndex >= 0;
            }
            @Override public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
                int to = dl.getIndex();
                if (to < 0 || fromIndex < 0 || fromIndex >= modelTaskBuilder.size()) return false;
                Action moved = modelTaskBuilder.getElementAt(fromIndex);
                modelTaskBuilder.remove(fromIndex);
                if (to > fromIndex) to--;
                to = Math.max(0, Math.min(to, modelTaskBuilder.size()));
                modelTaskBuilder.add(to, moved);
                listTaskBuilder.setSelectedIndex(to);
                fromIndex = -1;
                refreshList();
                return true;
            }
            @Override protected void exportDone(JComponent c, java.awt.datatransfer.Transferable d, int a) {
                fromIndex = -1;
            }
        });

        // Right-click any action for the full menu (Patch B.17). Double-click no longer
        // deletes - that was one slip away from losing work; Remove/Delete are explicit now.
        listTaskBuilder.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { maybePopup(e); }
            @Override public void mouseReleased(MouseEvent e) { maybePopup(e); }
            private void maybePopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int idx = listTaskBuilder.locationToIndex(e.getPoint());
                if (idx < 0) return;
                listTaskBuilder.setSelectedIndex(idx);
                buildActionContextMenu(idx).show(listTaskBuilder, e.getX(), e.getY());
            }
        });

        // v1.31: the Duplicate button is gone (it confused people) - duplicating lives on the
        // right-click menu now, as "Duplicate below" and "Duplicate to end".
        JPanel bottomBtns = new JPanel(new GridLayout(1, 4, 5, 5));
        bottomBtns.setOpaque(false);
        bottomBtns.add(btnRemove);
        bottomBtns.add(btnChance);
        bottomBtns.add(btnWatchers);
        bottomBtns.add(btnReset);

        center.add(listLabel,                        BorderLayout.NORTH);
        center.add(nav,                              BorderLayout.WEST);
        center.add(Theme.thinScrollbars(new JScrollPane(listTaskBuilder)), BorderLayout.CENTER);
        center.add(bottomBtns,                       BorderLayout.SOUTH);

        return center;
    }

    /** The right-click menu for one action in the chain (Patch B.17). */
    private JPopupMenu buildActionContextMenu(int idx) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem miEdit = new JMenuItem("Edit");
        miEdit.setToolTipText("Load this action's parameters into the editor on the left");
        miEdit.addActionListener(e -> {
            listTaskBuilder.setSelectedIndex(idx);   // selection listener arms the edit
            loadAction(modelTaskBuilder.getElementAt(idx));
        });
        JMenuItem miDuplicate = new JMenuItem("Duplicate below");
        miDuplicate.addActionListener(e -> duplicateAction(idx));
        JMenuItem miDuplicateEnd = new JMenuItem("Duplicate to end");
        miDuplicateEnd.addActionListener(e -> duplicateActionToEnd(idx));

        // v1.31: mark an action as SETUP - runs the first pass only (fetch the pickaxe once)
        Action target = modelTaskBuilder.getElementAt(idx);
        JCheckBoxMenuItem miOnStart = new JCheckBoxMenuItem("Only run on first loop (setup)",
                target != null && target.isOnStartOnly());
        miOnStart.setToolTipText("Runs on the task's first pass of the session and is skipped"
                + " on every later loop - preparation that shouldn't repeat.");
        miOnStart.addActionListener(e -> {
            if (target != null) {
                target.setOnStartOnly(miOnStart.isSelected());
                refreshList();
            }
        });
        JMenuItem miDelete = new JMenuItem("Delete");
        miDelete.addActionListener(e -> {
            listTaskBuilder.setSelectedIndex(idx);
            removeSelected();
        });
        JMenuItem miChance = new JMenuItem("Add randomness\u2026");
        miChance.setToolTipText("Give this action a chance-to-run below 100%");
        miChance.addActionListener(e -> openActionChance(modelTaskBuilder.getElementAt(idx)));
        JMenuItem miCheck = new JMenuItem("Add a trigger\u2026");
        miCheck.setToolTipText("Attach a conditional trigger to this action");
        miCheck.addActionListener(e -> openActionWatchers(modelTaskBuilder.getElementAt(idx)));

        menu.add(miEdit);
        menu.add(miDuplicate);
        menu.add(miDuplicateEnd);
        menu.add(miDelete);
        menu.addSeparator();
        menu.add(miOnStart);
        menu.add(miChance);
        menu.add(miCheck);
        return menu;
    }

    /** v1.31: appends a deep copy of the action at {@code idx} to the END of the chain. */
    private void duplicateActionToEnd(int idx) {
        if (idx < 0 || idx >= modelTaskBuilder.size()) return;
        Action original = modelTaskBuilder.getElementAt(idx);
        if (original == null) return;
        modelTaskBuilder.addElement(original.copyDeep());
        listTaskBuilder.setSelectedIndex(modelTaskBuilder.size() - 1);
        toast("Duplicated " + original.getName() + " to end", listTaskBuilder, true);
        refreshList();
    }

    /** Duplicates the action at {@code idx} in place (deep copy: params, chance AND checks). */
    private void duplicateAction(int idx) {
        if (idx < 0 || idx >= modelTaskBuilder.size()) return;
        Action original = modelTaskBuilder.getElementAt(idx);
        if (original == null) return;
        modelTaskBuilder.add(idx + 1, original.copyDeep());
        listTaskBuilder.setSelectedIndex(idx + 1);
        toast("Duplicated " + original.getName(), btnDuplicate, true);
        refreshList();
    }

    /**
     * Writes the editor's parameter values back onto the selected action in the chain
     * (Patch B.17). The action's chance-to-run and attached checks are taken from the CHAIN
     * copy, not the editor template - those are edited through their own dialogs and must
     * survive a parameter tweak.
     */
    private void updateSelectedAction() {
        int idx = listTaskBuilder.getSelectedIndex();
        if (idx < 0) idx = editingActionIndex;
        if (idx < 0 || idx >= modelTaskBuilder.size()) {
            toast("Select an action in the chain first", btnUpdateAction, false);
            return;
        }
        Action template = actionSelector.getSelectedAction();
        if (template == null) { toast("Nothing to save", btnUpdateAction, false); return; }

        Action current = modelTaskBuilder.getElementAt(idx);
        Action updated = template.copyDeep();
        if (current != null) {
            updated.setChancePercent(current.getChancePercent());
            updated.getTriggers().clear();
            for (main.watchers.Trigger t : current.getTriggers())
                if (t != null) updated.getTriggers().add(new main.watchers.Trigger(t));
        }
        main.tools.TargetHistory.record(updated.getParamTarget());   // v1.31
        modelTaskBuilder.set(idx, updated);
        listTaskBuilder.setSelectedIndex(idx);
        toast("Updated " + updated.getName(), btnUpdateAction, true);
        refreshList();
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
        // ── Patch B.17: the entity list lives HERE now, above the task fields ──
        // Same column width as the text inputs, and clicking an entry auto-fills the current
        // action's target: tile-targeted actions (Walk) get "X, Y, Z", name-targeted actions
        // (Interact, Loot, Attack-style...) get the name. See applyEntityToCurrentAction().
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);

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

        g.gridy = 0; form.add(createLabel("Task name:"),   g);
        g.gridy = 1; form.add(taskNameInput,               g);
        g.gridy = 2; form.add(createLabel("Description:"), g);
        g.gridy = 3; form.add(taskDescriptionInput,        g);
        g.gridy = 4; form.add(createLabel("Status:"),      g);
        g.gridy = 5; form.add(taskStatusInput,             g);

        // v1.62: the tags step, right before the save action (took the slot the auto-delay UI
        // used to occupy - auto-delay moved to the left pane, above the action buttons). Tags
        // drive the library + market search filters. Same comma-separated format as the library's
        // right-click "Edit tags".
        taskTagsInput = new JTextField(25);
        taskTagsInput.setToolTipText("Comma-separated, e.g. combat, ironman, f2p \u2014 used by "
                + "the library + market search filters");
        styleComp(taskTagsInput);
        g.gridy = 6;
        g.insets = new Insets(12, 5, 2, 5);
        form.add(createLabel("Tags (comma-separated):"), g);
        g.gridy = 7;
        g.insets = new Insets(2, 5, 5, 5);
        form.add(taskTagsInput, g);

        // Patch B.14: admins can flag a task VIP-only from here. Invisible to everyone else.
        chkVipOnly = new JCheckBox("VIP-only task");
        chkVipOnly.setOpaque(false);
        chkVipOnly.setForeground(new Color(230, 190, 90));
        chkVipOnly.setToolTipText("Admins only: mark this task as VIP-gated. It's hidden from free "
                + "users' libraries and the server withholds it from non-VIP accounts.");
        chkVipOnly.setVisible(main.market.Tier.isAdmin());

        btnAddToLibrary = createButton("Save to library", COLOR_BTN_ADD, null);
        btnAddToLibrary.setToolTipText("<html>Saves this task to your library.<br>"
                + "If a task with the same name exists it's <b>updated everywhere it's used</b>; "
                + "otherwise it's <b>added as new</b>.<br>Loaded a task and changed its name? "
                + "That's a rename (adds the new one, removes the old).</html>");
        btnAddToLibrary.addActionListener(e -> handleAddToLibrary());

        g.gridy  = 8;
        g.insets = new Insets(12, 5, 2, 5);
        form.add(chkVipOnly, g);           // Patch B.14: admin-only, above the save button

        g.gridy  = 9;
        g.insets = new Insets(6, 5, 5, 5);
        form.add(btnAddToLibrary, g);

        // the entity list matches the form width and takes all the height above it
        listLibrary.setPreferredSize(new Dimension(300, 300));
        listLibrary.addClickListener(this::applyEntityToCurrentAction);
        // v1.31: the strip's chips (name / position / area / My position) write their exact
        // value into the current action's target - no more guessing which form you wanted.
        listLibrary.setTargetSink(this::applyTargetValue);

        JPanel east = new JPanel(new BorderLayout(0, 8));
        east.setOpaque(false);
        east.setPreferredSize(new Dimension(310, 0));
        east.add(listLibrary, BorderLayout.CENTER);
        east.add(form,        BorderLayout.SOUTH);

        return east;
    }

    /**
     * Patch B.17: clicking an entity sets it as the current action's target - no re-typing.
     * Location-based actions (Walk) get the tile "X, Y, Z" (falling back to the name if the
     * entry has no known tile); everything else gets the name. Actions with no target of their
     * own (Wait, Chat, library tasks...) are left alone.
     */
    private void applyEntityToCurrentAction(JLibraryList.EntityEntry entry) {
        if (entry == null) return;
        Action template = actionSelector.getSelectedAction();
        if (template == null) return;
        String value = (template.prefersTileTarget() && entry.hasTile())
                ? entry.tileString()
                : entry.name;
        applyTargetValue(value);
    }

    /** v1.31: writes an exact value (from a strip chip or a row click) into the target. */
    private void applyTargetValue(String value) {
        if (value == null || value.isBlank()) return;
        Action template = actionSelector.getSelectedAction();
        if (template == null || template.paramTarget == null || !template.acceptsEntityTarget()) {
            toast("\"" + actionSelector.getSelectedItem() + "\" has no entity target", listLibrary, false);
            return;
        }
        template.paramTarget.setParam(value);
        toast("Target set: " + value, listLibrary, true);
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

        // v1.30: each bound gets its own [-] field [+] stepper - clearer than the old
        // "+/-50 +/-100 nudge the whole range" buttons - and typing a value still works.
        autoDelayControls.clear();
        autoDelayControls.add(autoDelayMinInput);
        autoDelayControls.add(autoDelayMaxInput);

        JPanel box = new JPanel(new GridLayout(0, 1, 0, 4));
        box.setOpaque(false);
        box.add(delayStepperRow("Min", autoDelayMinInput));
        box.add(delayStepperRow("Max", autoDelayMaxInput));

        syncAutoDelayEnabled();
        return box;
    }

    /** One "[label] [-] [field] [+] ms" row for an auto-delay bound (v1.30). */
    private JPanel delayStepperRow(String label, JTextField field) {
        JButton minus = createButton("-", new Color(50, 50, 50), null);
        JButton plus  = createButton("+", new Color(50, 50, 50), null);
        minus.setPreferredSize(new Dimension(34, 24));
        plus.setPreferredSize(new Dimension(34, 24));
        minus.setToolTipText(label + " delay: 50ms less");
        plus.setToolTipText(label + " delay: 50ms more");
        minus.addActionListener(e -> stepDelayField(field, -50));
        plus.addActionListener(e -> stepDelayField(field, +50));
        autoDelayControls.add(minus);
        autoDelayControls.add(plus);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setOpaque(false);
        JLabel l = createLabel(label + ":");
        l.setPreferredSize(new Dimension(32, 20));
        row.add(l);
        row.add(minus);
        row.add(field);
        row.add(plus);
        row.add(createLabel("ms"));
        return row;
    }

    /** Steps one bound by deltaMs (min 0), then re-orders so max >= min stays true. */
    private void stepDelayField(JTextField field, int deltaMs) {
        int v = ActionUtil.parseInt(field.getText(), AUTO_DELAY_DEFAULT_MIN);
        field.setText(String.valueOf(Math.max(0, v + deltaMs)));
        int min = ActionUtil.parseInt(autoDelayMinInput.getText(), AUTO_DELAY_DEFAULT_MIN);
        int max = ActionUtil.parseInt(autoDelayMaxInput.getText(), AUTO_DELAY_DEFAULT_MAX);
        if (max < min) {
            if (field == autoDelayMinInput) autoDelayMaxInput.setText(String.valueOf(min));
            else autoDelayMinInput.setText(String.valueOf(max));
        }
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
        String name = task.getName();

        // ── v1.62: "Save to library" decides add-new vs update on its own, keyed off the NAME ──
        // The old code keyed off a sticky editing flag, which caused the accidental overwrite the
        // roadmap flagged: load a task, wander off, build a brand-new one, and the save silently
        // rewrote the loaded task's identity in place. Now the rule is name-driven and explicit:
        //   • a task with this name already exists  -> UPDATE it (everywhere it's used)
        //   • the name is new                        -> ADD it
        //   • you'd loaded a task and changed its name -> that's a RENAME = add-new + delete-old
        //     (in-place rename isn't supported; the new task gets a FRESH id rather than silently
        //      inheriting the old one, which is what made the old behaviour surprising)
        DreamBotMenu.Task match = botMenu.findLibraryTask(null, name);   // match by name only
        String loadedId = editingTaskId;                                // set iff a task was loaded

        if (match != null) {
            // v1.62: with the tags step in place, the built task already carries the intended
            // tags (loadTask populated the field, so an edit round-trips them; clearing the field
            // removes them). So there's no tag-wipe to guard against here any more - the field is
            // the source of truth. (propagate still carries version + lifecycle from the match.)
            task.restoreId(match.getId());
            int touched = propagate(task, match.getId(), name);
            if (loadedId != null && !loadedId.equals(match.getId())) {
                // you were editing a DIFFERENT task and renamed it onto this existing name -
                // fold the content in and drop the old copy
                DreamBotMenu.Task old = botMenu.findLibraryTask(loadedId, null);
                if (old != null) botMenu.libraryRemove(old);
                toast("Merged into existing \"" + name + "\" and removed the old copy",
                        btnAddToLibrary, true);
            } else {
                toast("Updated \"" + name + "\" in " + Math.max(1, touched) + " place(s)",
                        btnAddToLibrary, true);
            }
            reset();
            botMenu.refreshLibraryFromBuilder();
            return;
        }

        // name is new
        if (loadedId != null) {
            // renaming a loaded task to a brand-new name = add-new + delete-old
            task.regenerateId();                    // its own identity, not the old one's
            botMenu.libraryAdd(task);
            DreamBotMenu.Task old = botMenu.findLibraryTask(loadedId, null);
            String oldName = old != null ? old.getName()
                    : (editingTaskName != null ? editingTaskName : "the previous task");
            if (old != null) botMenu.libraryRemove(old);
            toast("Renamed \"" + oldName + "\" to \"" + name + "\" (added new, removed old)",
                    btnAddToLibrary, true);
        } else {
            // fresh build under a new name
            botMenu.libraryAdd(task);
            toast("Added \"" + name + "\" to library", btnAddToLibrary, true);
        }
        reset();
        botMenu.refreshLibraryFromBuilder();
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
        DreamBotMenu.Task t = new DreamBotMenu.Task(name, description, actions, status);
        // v1.62: the tags step - carry the builder's tags onto the task (comma-separated, same
        // parsing as the library's Edit-tags). This is the source of truth now, which is why the
        // save handler no longer copies an existing task's tags to avoid a wipe.
        if (taskTagsInput != null) {
            java.util.List<String> parsed = new java.util.ArrayList<>();
            for (String part : taskTagsInput.getText().split(","))
                if (!part.trim().isEmpty()) parsed.add(part.trim());
            t.setTags(parsed);
        }
        // Patch B.14: only admins can mark a task VIP-only, and only if the box is showing+ticked.
        if (chkVipOnly != null && chkVipOnly.isVisible() && chkVipOnly.isSelected()
                && main.market.Tier.isAdmin())
            t.setVipOnly(true);
        return t;
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
        attachHistory(newPanel);   // v1.31: MRU target suggestions on the fields

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

    /** v1.31: attaches the target-history suggester once per field (guarded by a marker). */
    private void attachHistory(java.awt.Container root) {
        if (root == null) return;
        for (java.awt.Component c : root.getComponents()) {
            if (c instanceof main.components.JParamTextField) {
                JComponent jc = (JComponent) c;
                if (jc.getClientProperty("histAttached") == null) {
                    jc.putClientProperty("histAttached", Boolean.TRUE);
                    main.tools.TargetHistory.attach((javax.swing.JTextField) c);
                }
            } else if (c instanceof java.awt.Container) {
                attachHistory((java.awt.Container) c);
            }
        }
    }

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