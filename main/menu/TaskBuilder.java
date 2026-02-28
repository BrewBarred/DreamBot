package main.menu;

import main.actions.Action;
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

    // ── Injected dependencies ─────────────────────────────────────────────────
    private final DefaultListModel<DreamBotMenu.Task> modelTaskLibrary;
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
        this.modelTaskLibrary = botMenu.modelTaskLibrary;
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
    public void scanNearby() {
        listLibrary.rescanNearby();
    }

    public void onTabShown() {
        refreshList();
    }

    /** Pre-fills all inputs from an existing Task (for "View in builder"). */
    public void loadTask(DreamBotMenu.Task task) {
        if (task == null) return;
        taskNameInput.setText(task.getName());
        taskDescriptionInput.setText(task.getDescription());
        taskStatusInput.setText(task.getStatus());
        modelTaskBuilder.clear();
        task.getActions().forEach(modelTaskBuilder::addElement);
        refreshList();
    }

    /** Pre-fills the action selector from a single Action (single-click in action list). */
    public void loadAction(Action action) {
        if (action == null)
            return;

        actionSelector.setSelectedAction(action);
    }

    /** Clears all inputs and the action list. */
    public void reset() {
        taskNameInput.setText("");
        taskDescriptionInput.setText("");
        taskStatusInput.setText("");
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

        left.add(config,      BorderLayout.NORTH);
        left.add(listLibrary, BorderLayout.CENTER);

        return left;
    }

    private void addTemplateToBuilder() {
        Action newAction = actionSelector.getSelectedAction().copy();
        if (newAction != null) {
            modelTaskBuilder.addElement(newAction);
            toast("Added " + newAction + "!", btnAddToLibrary, true);
            refreshList(true);
        } else {
            toast("Invalid or incomplete action!", btnAddToLibrary, false);
        }
    }

    private void onSelectionChanged() {
        refreshDynamicControls();
        scanNearby();
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

        btnRemove = createButton("Remove", Color.RED, null);
        btnRemove.setEnabled(false);
        btnRemove.addActionListener(e -> removeSelected());

        JButton btnReset = createButton("Reset", new Color(50, 50, 50), null);
        btnReset.addActionListener(e -> {
            reset();
            toast("Task builder reset!", btnReset, true);
        });

        listTaskBuilder.addListSelectionListener(e ->
                btnRemove.setEnabled(!listTaskBuilder.isSelectionEmpty()));

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

        JPanel bottomBtns = new JPanel(new GridLayout(1, 2, 5, 5));
        bottomBtns.setOpaque(false);
        bottomBtns.add(btnRemove);
        bottomBtns.add(btnReset);

        center.add(listLabel,                        BorderLayout.NORTH);
        center.add(nav,                              BorderLayout.WEST);
        center.add(new JScrollPane(listTaskBuilder), BorderLayout.CENTER);
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

        btnAddToLibrary = createButton("Add to library...", COLOR_BTN_ADD, null);
        btnAddToLibrary.addActionListener(e -> handleAddToLibrary());

        g.gridy  = 8;
        g.insets = new Insets(20, 5, 5, 5);
        east.add(btnAddToLibrary, g);

        return east;
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

        boolean exists = false;
        for (int i = 0; i < modelTaskLibrary.getSize(); i++) {
            if (modelTaskLibrary.getElementAt(i).getName().equalsIgnoreCase(task.getName())) {
                exists = true;
                break;
            }
        }

        if (!exists) {
            modelTaskLibrary.addElement(task);
            toast("Added to library!", btnAddToLibrary, true);
            reset();
        } else {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    task.getName() + " already exists, overwrite it?",
                    "Overwrite task?",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );
            if (choice == JOptionPane.YES_OPTION) {
                for (int i = 0; i < modelTaskLibrary.getSize(); i++) {
                    if (modelTaskLibrary.getElementAt(i).getName().equalsIgnoreCase(task.getName())) {
                        modelTaskLibrary.set(i, task);
                        break;
                    }
                }
                toast("Task updated!", btnAddToLibrary, true);
                reset();
            } else {
                toast("Cancelled.", btnAddToLibrary, false);
            }
        }
    }

    private DreamBotMenu.Task buildTask(List<Action> actions) {
        String name        = taskNameInput.getText().trim();
        String description = taskDescriptionInput.getText().trim();
        String status      = taskStatusInput.getText().trim();

        if (actions == null || actions.isEmpty()) { toast("Add some actions first!", btnAddToLibrary, false); return null; }
        if (name.isEmpty())                        { toast("Enter a task name!",      btnAddToLibrary, false); return null; }
        if (requireDescription && description.isEmpty()) { toast("Enter a description!", btnAddToLibrary, false); return null; }
        if (requireStatus      && status.isEmpty())      { toast("Enter a status!",      btnAddToLibrary, false); return null; }

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

        scanNearby();
    }

    private void refreshList() {
        refreshList(false);
    }

    private void toast(String msg, JComponent anchor, boolean success) {
        botMenu.showToast(msg, anchor, success);
    }
}