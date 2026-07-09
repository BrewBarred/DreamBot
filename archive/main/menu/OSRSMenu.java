package main.menu;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

// =============================================================================
// CONDITION SYSTEM
// =============================================================================

/**
 * Describes one parameter a Condition needs.
 */
class ConditionParam {
    final String key;
    final String label;
    final String defaultValue;

    ConditionParam(String key, String label, String defaultValue) {
        this.key          = key;
        this.label        = label;
        this.defaultValue = defaultValue;
    }
}

/**
 * All built-in condition types plus CUSTOM.
 * Each entry declares which params it needs so the UI renders them dynamically.
 */
enum ConditionType {

    INV_CONTAINS     ("Inventory Contains Item",    cp("Item Name", "Axe"),            cp("Min Quantity", "1")),
    INV_NOT_CONTAINS ("Inventory Missing Item",     cp("Item Name", "Axe")),
    INV_FULL         ("Inventory Full"),
    INV_EMPTY        ("Inventory Empty"),
    HP_BELOW         ("HP Below %",                 cp("HP Threshold %", "50")),
    HP_ABOVE         ("HP Above %",                 cp("HP Threshold %", "90")),
    NPC_NEARBY       ("NPC Nearby",                 cp("NPC Name", "Cow"),             cp("Max Distance", "10")),
    OBJECT_NEARBY    ("Object Nearby",              cp("Object Name", "Oak tree"),     cp("Max Distance", "8")),
    PLAYER_MOVING    ("Player is Moving"),
    IS_ANIMATING     ("Player is Animating"),
    RUN_ENERGY_BELOW ("Run Energy Below %",         cp("Energy Threshold %", "20")),
    IN_COMBAT        ("Player is in Combat"),
    ITEM_EQUIPPED    ("Item Equipped",              cp("Item Name", "Dragon scimitar")),
    CUSTOM           ("Custom (DreamBot expression)", cp("Expression", "Inventory.contains(\"Axe\")"));

    private final String               displayName;
    private final List<ConditionParam> params;

    ConditionType(String displayName, ConditionParam... params) {
        this.displayName = displayName;
        this.params      = Arrays.asList(params);
    }

    public String               getDisplayName() { return displayName; }
    public List<ConditionParam> getParams()      { return params; }

    private static ConditionParam cp(String label, String defaultValue) {
        return new ConditionParam(label, label, defaultValue);
    }

    @Override
    public String toString() { return displayName; }
}

/**
 * Holds a chosen ConditionType and the user-supplied param values.
 * evaluate() is the DreamBot hook — returns a preview value in the builder.
 */
class Condition {
    private ConditionType        type;
    private Map<String, String>  paramValues = new LinkedHashMap<>();

    public Condition(ConditionType type) {
        this.type = type;
        for (ConditionParam cp : type.getParams()) {
            paramValues.put(cp.key, cp.defaultValue);
        }
    }

    public ConditionType       getType()        { return type; }
    public Map<String, String> getParamValues() { return paramValues; }
    public void setParam(String key, String val){ paramValues.put(key, val); }

    /**
     * Runtime evaluation stub.
     * Replace each case body with the real DreamBot API call.
     */
    public boolean evaluate() {
        switch (type) {
            case INV_CONTAINS:
                // DreamBot: return Inventory.contains(paramValues.get("Item Name"));
                return true;
            case INV_NOT_CONTAINS:
                // DreamBot: return !Inventory.contains(paramValues.get("Item Name"));
                return false;
            case INV_FULL:
                // DreamBot: return Inventory.isFull();
                return false;
            case INV_EMPTY:
                // DreamBot: return Inventory.isEmpty();
                return false;
            case HP_BELOW:
                // DreamBot: return Skills.getBoostedLevel(Skill.HITPOINTS) < Integer.parseInt(paramValues.get("HP Threshold %"));
                return false;
            case HP_ABOVE:
                // DreamBot: return Skills.getBoostedLevel(Skill.HITPOINTS) > Integer.parseInt(paramValues.get("HP Threshold %"));
                return true;
            case NPC_NEARBY:
                // DreamBot: return NPCs.closest(paramValues.get("NPC Name")) != null;
                return true;
            case OBJECT_NEARBY:
                // DreamBot: return GameObjects.closest(paramValues.get("Object Name")) != null;
                return true;
            case PLAYER_MOVING:
                // DreamBot: return Players.getLocal().isMoving();
                return false;
            case IS_ANIMATING:
                // DreamBot: return Players.getLocal().isAnimating();
                return false;
            case RUN_ENERGY_BELOW:
                // DreamBot: return Combat.getRunEnergy() < Integer.parseInt(paramValues.get("Energy Threshold %"));
                return false;
            case IN_COMBAT:
                // DreamBot: return Players.getLocal().isInCombat();
                return false;
            case ITEM_EQUIPPED:
                // DreamBot: return Equipment.contains(paramValues.get("Item Name"));
                return false;
            case CUSTOM:
                // Evaluated by DreamBot script engine at runtime via expression string
                return false;
            default:
                return false;
        }
    }

    /** Compact one-line summary shown in the sequence list and task cards. */
    public String summary() {
        if (type.getParams().isEmpty()) return type.getDisplayName();
        StringBuilder sb = new StringBuilder(type.getDisplayName()).append("(");
        boolean first = true;
        for (String v : paramValues.values()) {
            if (!first) sb.append(", ");
            sb.append(v);
            first = false;
        }
        return sb.append(")").toString();
    }
}

// =============================================================================
// CORE ACTION MODEL
// =============================================================================

abstract class OSRSAction {
    protected String name;
    public OSRSAction(String name) { this.name = name; }
    public abstract Map<String, String> getParameters();
    public abstract void setParameters(Map<String, String> params);
    @Override public String toString() { return name.toUpperCase() + " " + getParameters(); }
}

class WalkAction extends OSRSAction {
    private String loc = "Varrock";
    public WalkAction() { super("Walk"); }
    public Map<String, String> getParameters() {
        Map<String, String> m = new LinkedHashMap<>(); m.put("Location", loc); return m;
    }
    public void setParameters(Map<String, String> p) {
        if (p.containsKey("Location")) loc = p.get("Location");
    }
}

class ChopAction extends OSRSAction {
    private String tree = "Oak";
    public ChopAction() { super("Chop"); }
    public Map<String, String> getParameters() {
        Map<String, String> m = new LinkedHashMap<>(); m.put("Tree", tree); return m;
    }
    public void setParameters(Map<String, String> p) {
        if (p.containsKey("Tree")) tree = p.get("Tree");
    }
}

class BankAction extends OSRSAction {
    private String item = "Logs", amount = "All";
    public BankAction() { super("Bank"); }
    public Map<String, String> getParameters() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Item", item); m.put("Amount", amount); return m;
    }
    public void setParameters(Map<String, String> p) {
        if (p.containsKey("Item"))   item   = p.get("Item");
        if (p.containsKey("Amount")) amount = p.get("Amount");
    }
}

class AttackAction extends OSRSAction {
    private String target = "Cow", style = "Aggressive";
    public AttackAction() { super("Attack"); }
    public Map<String, String> getParameters() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Target", target); m.put("Style", style); return m;
    }
    public void setParameters(Map<String, String> p) {
        if (p.containsKey("Target")) target = p.get("Target");
        if (p.containsKey("Style"))  style  = p.get("Style");
    }
}

/**
 * A Check action.  Now owns a real Condition that is evaluated at runtime.
 * trueTask / falseTask are executed depending on condition.evaluate().
 */
class LogicAction extends OSRSAction {
    public Condition condition;
    public Task      trueTask  = null;
    public Task      falseTask = null;

    public LogicAction() {
        super("Check");
        condition = new Condition(ConditionType.INV_CONTAINS);
    }

    public Map<String, String> getParameters() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Condition", condition != null ? condition.summary() : "?");
        m.put("IF_TRUE",   trueTask  != null ? trueTask.getName()  : "None");
        m.put("IF_FALSE",  falseTask != null ? falseTask.getName() : "None");
        return m;
    }

    public void setParameters(Map<String, String> p) { /* condition set directly */ }
}

// =============================================================================
// TASK
// =============================================================================

class Task {
    private final String           name;
    private final List<OSRSAction> actions;

    public Task(String name, List<OSRSAction> actions) {
        this.name    = name;
        this.actions = new ArrayList<>(actions);
    }

    public String           getName()    { return name; }
    public List<OSRSAction> getActions() { return Collections.unmodifiableList(actions); }
    @Override public String toString()   { return name; }
}

// =============================================================================
// MAIN UI
// =============================================================================

public class OSRSMenu extends JFrame {

    // Palette
    private static final Color BG       = new Color(28, 28, 28);
    private static final Color PANEL_BG = new Color(38, 38, 38);
    private static final Color BORDER_C = new Color(80, 60, 20);
    private static final Color GOLD     = new Color(220, 180, 60);
    private static final Color TEXT     = new Color(220, 210, 190);
    private static final Color TEXT_DIM = new Color(150, 140, 120);
    private static final Color INPUT_BG = new Color(50, 50, 50);
    private static final Color BTN_BG   = new Color(70, 50, 10);
    private static final Color BTN_HVR  = new Color(100, 75, 15);
    private static final Color TASK_HDR = new Color(45, 38, 20);
    private static final Color COND_BG  = new Color(28, 36, 28);   // subtle green tint for condition

    private static final String NO_TASKS_SENTINEL = "\u2014 Create a Task first \u2014";

    // State
    private final List<Task>               taskRegistry    = new ArrayList<>();
    private final DefaultListModel<String> actionListModel = new DefaultListModel<>();
    private final List<OSRSAction>         pendingActions  = new ArrayList<>();

    // UI refs
    private JComboBox<String>        actionSelector;
    private JPanel                   dynamicParamsPanel;
    private JPanel                   logicPanel;
    private JPanel                   conditionParamsPanel;
    private JComboBox<ConditionType> conditionTypeCombo;
    private JComboBox<Object>        trueSelector;
    private JComboBox<Object>        falseSelector;
    private JList<String>            actionList;
    private JLabel                   seqLabel;
    private JPanel                   taskListPanel;
    private JLabel                   statusLabel;

    private final Map<String, JTextField> actionFields    = new LinkedHashMap<>();
    private final Map<String, JTextField> conditionFields = new LinkedHashMap<>();

    // =========================================================================
    public OSRSMenu() {
        setTitle("OSRS Script Builder");
        setSize(720, 820);
        setMinimumSize(new Dimension(620, 660));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);
        getContentPane().setLayout(new BorderLayout(0, 0));
        buildUI();
        onActionTypeChanged();
    }

    // =========================================================================
    // UI CONSTRUCTION
    // =========================================================================

    private void buildUI() {
        // Header
        JPanel header = darkPanel(BG);
        header.setLayout(new BorderLayout());
        header.setBorder(new CompoundBorder(new MatteBorder(0, 0, 2, 0, BORDER_C),
                new EmptyBorder(12, 16, 12, 16)));
        header.add(label("\u2694  OSRS Script Builder", GOLD, Font.BOLD, 16), BorderLayout.WEST);
        getContentPane().add(header, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildActionBuilderPanel(), buildTaskListPanel());
        split.setDividerLocation(420);
        split.setBackground(BG);
        split.setBorder(null);
        split.setDividerSize(4);
        getContentPane().add(split, BorderLayout.CENTER);

        JPanel statusBar = darkPanel(BG);
        statusBar.setLayout(new BorderLayout());
        statusBar.setBorder(new CompoundBorder(new MatteBorder(2, 0, 0, 0, BORDER_C),
                new EmptyBorder(6, 14, 6, 14)));
        statusLabel = label(" ", TEXT_DIM, Font.PLAIN, 11);
        statusLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
        statusBar.add(statusLabel, BorderLayout.CENTER);
        getContentPane().add(statusBar, BorderLayout.SOUTH);
    }

    // ── Left panel ────────────────────────────────────────────────────────────

    private JPanel buildActionBuilderPanel() {
        JPanel panel = darkPanel(BG);
        panel.setLayout(new BorderLayout(0, 0));
        panel.setBorder(new EmptyBorder(12, 12, 12, 6));

        // Action type selector row
        JPanel selectorRow = darkPanel(BG);
        selectorRow.setLayout(new BorderLayout(8, 0));
        selectorRow.setBorder(new EmptyBorder(0, 0, 8, 0));
        selectorRow.add(label("Action:", TEXT_DIM, Font.PLAIN, 12), BorderLayout.WEST);
        actionSelector = new JComboBox<>(new String[]{"Walk", "Chop", "Bank", "Attack", "Check"});
        styleCombo(actionSelector);
        actionSelector.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { onActionTypeChanged(); }
        });
        selectorRow.add(actionSelector, BorderLayout.CENTER);
        panel.add(selectorRow, BorderLayout.NORTH);

        // Centre stack: action params above, logic panel below
        JPanel centreStack = darkPanel(BG);
        centreStack.setLayout(new BoxLayout(centreStack, BoxLayout.Y_AXIS));

        dynamicParamsPanel = darkPanel(PANEL_BG);
        dynamicParamsPanel.setLayout(new BoxLayout(dynamicParamsPanel, BoxLayout.Y_AXIS));
        dynamicParamsPanel.setBorder(titledBorder("Parameters"));
        centreStack.add(dynamicParamsPanel);
        centreStack.add(Box.createVerticalStrut(8));

        logicPanel = buildLogicPanel();
        logicPanel.setVisible(false);
        centreStack.add(logicPanel);

        panel.add(centreStack, BorderLayout.CENTER);
        panel.add(buildSequencePanel(), BorderLayout.SOUTH);
        return panel;
    }

    /**
     * The logic panel contains three sub-sections:
     *   1. Condition type dropdown
     *   2. Dynamic condition params (swaps out when type changes)
     *   3. On-True / On-False task selectors
     */
    private JPanel buildLogicPanel() {
        JPanel lp = darkPanel(COND_BG);
        lp.setLayout(new BoxLayout(lp, BoxLayout.Y_AXIS));
        lp.setBorder(titledBorder("Branching Logic  \u2014  IF condition THEN task ELSE task"));

        // ── 1. Condition type selector ────────────────────────────────────────
        JPanel condTypeRow = darkPanel(COND_BG);
        condTypeRow.setLayout(new BorderLayout(8, 0));
        condTypeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        condTypeRow.setBorder(new EmptyBorder(2, 6, 2, 6));
        JLabel condLbl = label("Condition:", TEXT, Font.BOLD, 12);
        condLbl.setPreferredSize(new Dimension(90, 24));
        condTypeRow.add(condLbl, BorderLayout.WEST);
        conditionTypeCombo = new JComboBox<>(ConditionType.values());
        styleCombo(conditionTypeCombo);
        conditionTypeCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { refreshConditionParams(); }
        });
        condTypeRow.add(conditionTypeCombo, BorderLayout.CENTER);
        lp.add(condTypeRow);
        lp.add(Box.createVerticalStrut(4));

        // ── 2. Dynamic condition params ───────────────────────────────────────
        conditionParamsPanel = darkPanel(COND_BG);
        conditionParamsPanel.setLayout(new BoxLayout(conditionParamsPanel, BoxLayout.Y_AXIS));
        lp.add(conditionParamsPanel);
        lp.add(Box.createVerticalStrut(6));

        // ── Divider ───────────────────────────────────────────────────────────
        JSeparator sep = new JSeparator();
        sep.setForeground(BORDER_C);
        sep.setBackground(COND_BG);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        lp.add(sep);
        lp.add(Box.createVerticalStrut(6));

        // ── 3. True / False task selectors ────────────────────────────────────
        trueSelector  = new JComboBox<>();
        falseSelector = new JComboBox<>();
        styleCombo(trueSelector);
        styleCombo(falseSelector);
        lp.add(taskSelectorRow(COND_BG, "On True  \u2192", trueSelector));
        lp.add(Box.createVerticalStrut(4));
        lp.add(taskSelectorRow(COND_BG, "On False \u2192", falseSelector));
        lp.add(Box.createVerticalStrut(6));

        refreshTaskSelectors();
        refreshConditionParams();
        return lp;
    }

    private JPanel taskSelectorRow(Color bg, String labelText, JComboBox<Object> combo) {
        JPanel row = darkPanel(bg);
        row.setLayout(new BorderLayout(8, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        row.setBorder(new EmptyBorder(2, 6, 2, 6));
        JLabel lbl = label(labelText, TEXT, Font.PLAIN, 12);
        lbl.setPreferredSize(new Dimension(90, 24));
        row.add(lbl, BorderLayout.WEST);
        row.add(combo, BorderLayout.CENTER);
        return row;
    }

    private JPanel buildSequencePanel() {
        JPanel sp = darkPanel(BG);
        sp.setLayout(new BorderLayout(0, 4));
        sp.setBorder(new EmptyBorder(8, 0, 0, 0));

        seqLabel = label("Current Sequence  (0 actions)", TEXT_DIM, Font.BOLD, 11);
        seqLabel.setBorder(new EmptyBorder(0, 0, 4, 0));
        sp.add(seqLabel, BorderLayout.NORTH);

        actionList = new JList<>(actionListModel);
        actionList.setBackground(INPUT_BG);
        actionList.setForeground(TEXT);
        actionList.setFont(new Font("Monospaced", Font.PLAIN, 11));
        actionList.setSelectionBackground(BTN_HVR);
        actionList.setSelectionForeground(GOLD);
        actionList.setBorder(new EmptyBorder(4, 6, 4, 6));

        JScrollPane scroll = new JScrollPane(actionList);
        scroll.setPreferredSize(new Dimension(0, 120));
        scroll.setBorder(new LineBorder(BORDER_C, 1));
        scroll.getViewport().setBackground(INPUT_BG);
        sp.add(scroll, BorderLayout.CENTER);

        JPanel btnRow = darkPanel(BG);
        btnRow.setLayout(new GridLayout(1, 3, 6, 0));
        btnRow.setBorder(new EmptyBorder(6, 0, 0, 0));

        JButton addBtn    = styledBtn("+ Add Action");
        JButton removeBtn = styledBtn("- Remove");
        JButton taskBtn   = styledBtn("\u2714 Save as Task");
        taskBtn.setForeground(GOLD);
        taskBtn.setFont(new Font("SansSerif", Font.BOLD, 12));

        addBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { addActionToSequence(); }
        });
        removeBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { removeSelectedAction(); }
        });
        taskBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { promptSaveAsTask(); }
        });

        btnRow.add(addBtn);
        btnRow.add(removeBtn);
        btnRow.add(taskBtn);
        sp.add(btnRow, BorderLayout.SOUTH);
        return sp;
    }

    // ── Right panel ───────────────────────────────────────────────────────────

    private JPanel buildTaskListPanel() {
        JPanel panel = darkPanel(BG);
        panel.setLayout(new BorderLayout(0, 0));
        panel.setBorder(new EmptyBorder(12, 6, 12, 12));

        JLabel hdr = label("Created Tasks", GOLD, Font.BOLD, 13);
        hdr.setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, BORDER_C),
                new EmptyBorder(0, 0, 8, 0)));
        panel.add(hdr, BorderLayout.NORTH);

        taskListPanel = new JPanel();
        taskListPanel.setBackground(PANEL_BG);
        taskListPanel.setLayout(new BoxLayout(taskListPanel, BoxLayout.Y_AXIS));

        JScrollPane scroll = new JScrollPane(taskListPanel);
        scroll.setBackground(PANEL_BG);
        scroll.getViewport().setBackground(PANEL_BG);
        scroll.setBorder(new LineBorder(BORDER_C, 1));
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        panel.add(scroll, BorderLayout.CENTER);

        showNoTasksPlaceholder();
        return panel;
    }

    // =========================================================================
    // DYNAMIC PANEL REFRESH
    // =========================================================================

    private void onActionTypeChanged() {
        boolean isCheck = "Check".equals(actionSelector.getSelectedItem());
        if (logicPanel != null) logicPanel.setVisible(isCheck);
        rebuildActionParams();
        revalidate();
        repaint();
    }

    private void rebuildActionParams() {
        dynamicParamsPanel.removeAll();
        actionFields.clear();
        OSRSAction template = makeTemplate((String) actionSelector.getSelectedItem());
        for (Map.Entry<String, String> e : template.getParameters().entrySet()) {
            String key = e.getKey();
            if (key.equals("IF_TRUE") || key.equals("IF_FALSE") || key.equals("Condition")) continue;
            dynamicParamsPanel.add(makeParamRow(PANEL_BG, key, e.getValue(), actionFields));
        }
        dynamicParamsPanel.revalidate();
        dynamicParamsPanel.repaint();
    }

    /**
     * Rebuilds condition param fields whenever the condition type combo changes.
     * If the selected type has no params, shows a "(no parameters needed)" hint.
     */
    private void refreshConditionParams() {
        if (conditionParamsPanel == null || conditionTypeCombo == null) return;
        conditionParamsPanel.removeAll();
        conditionFields.clear();

        ConditionType selected = (ConditionType) conditionTypeCombo.getSelectedItem();
        if (selected == null) { conditionParamsPanel.revalidate(); return; }

        if (selected.getParams().isEmpty()) {
            JLabel note = label("  (no parameters needed)", TEXT_DIM, Font.ITALIC, 11);
            note.setBorder(new EmptyBorder(3, 8, 3, 8));
            conditionParamsPanel.add(note);
        } else {
            for (ConditionParam cp : selected.getParams()) {
                conditionParamsPanel.add(makeParamRow(COND_BG, cp.label, cp.defaultValue, conditionFields));
            }
        }

        conditionParamsPanel.revalidate();
        conditionParamsPanel.repaint();
    }

    /** One labelled text-field row, registers the field into the given map. */
    private JPanel makeParamRow(Color bg, String key, String defaultVal,
                                Map<String, JTextField> registry) {
        JPanel row = darkPanel(bg);
        row.setLayout(new BorderLayout(8, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        row.setBorder(new EmptyBorder(3, 8, 3, 8));

        JLabel lbl = label(key + ":", TEXT, Font.PLAIN, 12);
        lbl.setPreferredSize(new Dimension(160, 22));
        row.add(lbl, BorderLayout.WEST);

        JTextField tf = new JTextField(defaultVal);
        tf.setBackground(INPUT_BG);
        tf.setForeground(TEXT);
        tf.setCaretColor(GOLD);
        tf.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tf.setBorder(new CompoundBorder(new LineBorder(BORDER_C, 1),
                new EmptyBorder(2, 6, 2, 6)));
        registry.put(key, tf);
        row.add(tf, BorderLayout.CENTER);
        return row;
    }

    // =========================================================================
    // SEQUENCE LOGIC
    // =========================================================================

    private void addActionToSequence() {
        String type  = (String) actionSelector.getSelectedItem();
        OSRSAction a = makeTemplate(type);

        Map<String, String> params = new LinkedHashMap<>();
        for (Map.Entry<String, JTextField> e : actionFields.entrySet()) {
            params.put(e.getKey(), e.getValue().getText().trim());
        }
        a.setParameters(params);

        if (a instanceof LogicAction) {
            LogicAction la = (LogicAction) a;

            // Build condition from UI
            ConditionType ct   = (ConditionType) conditionTypeCombo.getSelectedItem();
            Condition     cond = new Condition(ct);
            for (Map.Entry<String, JTextField> e : conditionFields.entrySet()) {
                cond.setParam(e.getKey(), e.getValue().getText().trim());
            }
            la.condition = cond;

            // Wire tasks
            Object tv = trueSelector.getSelectedItem();
            Object fv = falseSelector.getSelectedItem();
            la.trueTask  = (tv instanceof Task) ? (Task) tv : null;
            la.falseTask = (fv instanceof Task) ? (Task) fv : null;

            if (la.trueTask == null && la.falseTask == null) {
                setStatus("\u26A0 Check added with no tasks wired — create tasks first");
            }
        }

        pendingActions.add(a);
        actionListModel.addElement(formatAction(a));
        updateSeqLabel();
        setStatus("Added: " + type);
    }

    private void removeSelectedAction() {
        int idx = actionList.getSelectedIndex();
        if (idx < 0) { setStatus("Select an action to remove."); return; }
        pendingActions.remove(idx);
        actionListModel.remove(idx);
        updateSeqLabel();
        setStatus("Removed action at position " + (idx + 1));
    }

    private void promptSaveAsTask() {
        if (pendingActions.isEmpty()) {
            setStatus("\u26A0 Add at least one action before saving a task.");
            return;
        }

        JTextField nameField = new JTextField(14);
        nameField.setBackground(INPUT_BG);
        nameField.setForeground(TEXT);
        nameField.setCaretColor(GOLD);
        nameField.setBorder(new CompoundBorder(
                new LineBorder(BORDER_C, 1), new EmptyBorder(4, 6, 4, 6)));

        JPanel dlgPanel = new JPanel(new BorderLayout(8, 0));
        dlgPanel.setBackground(PANEL_BG);
        dlgPanel.setBorder(new EmptyBorder(10, 12, 10, 12));
        JLabel lbl = label("Task name:", TEXT, Font.PLAIN, 12);
        lbl.setPreferredSize(new Dimension(80, 26));
        dlgPanel.add(lbl, BorderLayout.WEST);
        dlgPanel.add(nameField, BorderLayout.CENTER);

        UIManager.put("OptionPane.background",        PANEL_BG);
        UIManager.put("Panel.background",             PANEL_BG);
        UIManager.put("OptionPane.messageForeground", TEXT);

        int result = JOptionPane.showConfirmDialog(this, dlgPanel, "Save as Task",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String taskName = nameField.getText().trim();
        if (taskName.isEmpty()) taskName = "Task " + (taskRegistry.size() + 1);
        for (Task t : taskRegistry) {
            if (t.getName().equalsIgnoreCase(taskName)) {
                taskName = taskName + " (" + (taskRegistry.size() + 1) + ")";
                break;
            }
        }

        Task task = new Task(taskName, pendingActions);
        taskRegistry.add(task);
        pendingActions.clear();
        actionListModel.clear();
        updateSeqLabel();
        refreshTaskListPanel();
        refreshTaskSelectors();
        setStatus("\u2714 Task saved: \"" + task.getName() + "\"  (" + task.getActions().size() + " actions)");
    }

    // =========================================================================
    // TASK LIST PANEL
    // =========================================================================

    private void refreshTaskListPanel() {
        taskListPanel.removeAll();
        if (taskRegistry.isEmpty()) {
            showNoTasksPlaceholder();
        } else {
            for (int i = 0; i < taskRegistry.size(); i++) {
                taskListPanel.add(buildTaskCard(taskRegistry.get(i), i));
                taskListPanel.add(Box.createVerticalStrut(4));
            }
        }
        taskListPanel.revalidate();
        taskListPanel.repaint();
    }

    private JPanel buildTaskCard(Task task, int index) {
        JPanel card = new JPanel(new BorderLayout(0, 0));
        card.setBackground(TASK_HDR);
        card.setBorder(new CompoundBorder(new LineBorder(BORDER_C, 1),
                new EmptyBorder(6, 8, 6, 8)));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        card.add(label((index + 1) + ".  " + task.getName(), GOLD, Font.BOLD, 12), BorderLayout.NORTH);

        JPanel actionsPanel = darkPanel(TASK_HDR);
        actionsPanel.setLayout(new BoxLayout(actionsPanel, BoxLayout.Y_AXIS));
        actionsPanel.setBorder(new EmptyBorder(4, 8, 0, 0));
        for (OSRSAction a : task.getActions()) {
            JLabel al = label("\u2023  " + formatAction(a), TEXT_DIM, Font.PLAIN, 11);
            al.setFont(new Font("Monospaced", Font.PLAIN, 11));
            actionsPanel.add(al);
        }
        card.add(actionsPanel, BorderLayout.CENTER);
        return card;
    }

    private void showNoTasksPlaceholder() {
        JPanel ph = darkPanel(PANEL_BG);
        ph.setLayout(new BoxLayout(ph, BoxLayout.Y_AXIS));
        ph.setBorder(new EmptyBorder(20, 10, 20, 10));
        JLabel icon = label("\u29C9", TEXT_DIM, Font.PLAIN, 28);
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel msg  = label("No tasks yet", TEXT_DIM, Font.ITALIC, 12);
        msg.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel hint = label("Build a sequence then click  \u2714 Save as Task", TEXT_DIM, Font.PLAIN, 11);
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        ph.add(icon); ph.add(Box.createVerticalStrut(6));
        ph.add(msg);  ph.add(Box.createVerticalStrut(4));
        ph.add(hint);
        taskListPanel.add(ph);
    }

    // =========================================================================
    // TASK SELECTORS
    // =========================================================================

    private void refreshTaskSelectors() {
        if (trueSelector == null || falseSelector == null) return;
        trueSelector.removeAllItems();
        falseSelector.removeAllItems();
        if (taskRegistry.isEmpty()) {
            trueSelector.addItem(NO_TASKS_SENTINEL);
            falseSelector.addItem(NO_TASKS_SENTINEL);
            trueSelector.setEnabled(false);
            falseSelector.setEnabled(false);
        } else {
            trueSelector.setEnabled(true);
            falseSelector.setEnabled(true);
            trueSelector.addItem("None");
            falseSelector.addItem("None");
            for (Task t : taskRegistry) {
                trueSelector.addItem(t);
                falseSelector.addItem(t);
            }
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private OSRSAction makeTemplate(String type) {
        if (type == null) return new WalkAction();
        switch (type) {
            case "Chop":   return new ChopAction();
            case "Bank":   return new BankAction();
            case "Attack": return new AttackAction();
            case "Check":  return new LogicAction();
            default:       return new WalkAction();
        }
    }

    private String formatAction(OSRSAction a) {
        if (a instanceof LogicAction) {
            LogicAction la = (LogicAction) a;
            String cond  = la.condition != null ? la.condition.summary() : "?";
            String tName = la.trueTask  != null ? la.trueTask.getName()  : "None";
            String fName = la.falseTask != null ? la.falseTask.getName() : "None";
            return "IF " + cond + "  \u2192  T:" + tName + "  F:" + fName;
        }
        StringBuilder sb = new StringBuilder(a.name.toUpperCase());
        for (Map.Entry<String, String> e : a.getParameters().entrySet()) {
            sb.append("  ").append(e.getKey()).append(":").append(e.getValue());
        }
        return sb.toString();
    }

    private void updateSeqLabel() {
        int n = pendingActions.size();
        seqLabel.setText("Current Sequence  (" + n + " action" + (n == 1 ? "" : "s") + ")");
    }

    private void setStatus(String msg) { statusLabel.setText(msg); }

    // ── Styling ───────────────────────────────────────────────────────────────

    private JPanel darkPanel(Color bg) {
        JPanel p = new JPanel(); p.setBackground(bg); return p;
    }

    private JLabel label(String text, Color color, int style, int size) {
        JLabel l = new JLabel(text);
        l.setForeground(color);
        l.setFont(new Font("SansSerif", style, size));
        return l;
    }

    private Border titledBorder(String title) {
        TitledBorder tb = BorderFactory.createTitledBorder(
                new LineBorder(BORDER_C, 1), title,
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 11), TEXT_DIM);
        return new CompoundBorder(tb, new EmptyBorder(4, 4, 4, 4));
    }

    private JButton styledBtn(String text) {
        final JButton btn = new JButton(text);
        btn.setBackground(BTN_BG);
        btn.setForeground(TEXT);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btn.setFocusPainted(false);
        btn.setBorder(new CompoundBorder(new LineBorder(BORDER_C, 1),
                new EmptyBorder(5, 10, 5, 10)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(BTN_HVR); }
            public void mouseExited(MouseEvent e)  { btn.setBackground(BTN_BG); }
        });
        return btn;
    }

    private <T> void styleCombo(JComboBox<T> cb) {
        cb.setBackground(INPUT_BG);
        cb.setForeground(TEXT);
        cb.setFont(new Font("SansSerif", Font.PLAIN, 12));
        cb.setBorder(new LineBorder(BORDER_C, 1));
        cb.setRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(
                    JList<?> list, Object val, int idx, boolean sel, boolean foc) {
                super.getListCellRendererComponent(list, val, idx, sel, foc);
                boolean isSentinel = NO_TASKS_SENTINEL.equals(val);
                setBackground(sel && !isSentinel ? BTN_HVR : INPUT_BG);
                setForeground(isSentinel ? TEXT_DIM : (sel ? GOLD : TEXT));
                setFont(new Font("SansSerif", isSentinel ? Font.ITALIC : Font.PLAIN, 12));
                setBorder(new EmptyBorder(3, 8, 3, 8));
                return this;
            }
        });
    }

    // =========================================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() { new OSRSMenu().setVisible(true); }
        });
    }
}