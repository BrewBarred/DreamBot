package main.menu.components;

import main.actions.Action;
import main.actions.Walk;
import main.actions.Interact;
import main.actions.Loot;
import main.actions.Drop;
import main.actions.Bank;
import main.actions.Wait;
import org.dreambot.api.utilities.Logger;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.util.LinkedHashMap;
import java.util.Map;

import static main.menu.MenuHandler.styleComp;

public class JActionSelector extends JComboBox<Action> {
    private static final Map<String, Action> REGISTRY = new LinkedHashMap<>();
    /**
     * The currently active "template" Action instance whose controls are shown in the UI.
     * Rebuilt whenever the selection changes.
     */
    private Action selectedAction;
    private JPanel currentPanel;

    /** Guards against rebuild storms while the model is being swapped (Patch B.3). */
    private boolean muted = false;
    /** How many built-in prototypes lead the model (library entries follow). */
    private int builtInCount = 0;

    static {
        // NOTE: each registry KEY must equal the action class's simple name, because Action.getName()
        // (used as the saved type) returns getClass().getSimpleName() and createByType() looks it up here.
        REGISTRY.put("Walk", new Walk());
        REGISTRY.put("Interact", new Interact());
        REGISTRY.put("Loot", new Loot());
        REGISTRY.put("Drop", new Drop());
        REGISTRY.put("Bank", new Bank());
        REGISTRY.put("Wait", new Wait());
        REGISTRY.put("Bury", new main.actions.Bury());
        REGISTRY.put("Chat", new main.actions.Chat());
        REGISTRY.put("FindBank", new main.actions.FindBank());
        REGISTRY.put("Shop", new main.actions.Shop());
        REGISTRY.put("TaskRef", new main.actions.TaskRef());
        REGISTRY.put("InventoryManager", new main.actions.InventoryManager());
    }

    public JActionSelector() {
        // instantiate action selector with the first item in the registry
        super(REGISTRY.values().toArray(new Action[0]));
        Logger.log(Logger.LogType.DEBUG, "Setting up action selector...");
        setSelectedIndex(0);
        builtInCount = REGISTRY.size();
        rebuildTemplate();
        Logger.log(Logger.LogType.DEBUG, "Rebuild complete!");
        currentPanel = selectedAction.getParamPanel();
        Logger.log(Logger.LogType.DEBUG, "Fetch params!");
        addActionListener(e -> { if (!muted) rebuildTemplate(); });
        Logger.log(Logger.LogType.DEBUG, "Added listener!");

        // Patch B.3: the dropdown is split into the default actions and, below a divider,
        // your library tasks - rendered gold so the two groups read at a glance.
        setRenderer(new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean sel, boolean foc) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, sel, foc);
                boolean library = value instanceof main.actions.TaskRef
                        && ((main.actions.TaskRef) value).isBoundEntry();
                if (library) {
                    l.setText("  \u00bb " + ((main.actions.TaskRef) value).getParamTarget());
                    if (!sel) l.setForeground(new java.awt.Color(212, 175, 55));
                    if (index == builtInCount && index > 0)
                        l.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createMatteBorder(1, 0, 0, 0, new java.awt.Color(90, 90, 90)),
                                BorderFactory.createEmptyBorder(2, 2, 2, 2)));
                }
                return l;
            }
        });

        styleComp(this);
    }

    /**
     * Refreshes the "library tasks" half of the dropdown (Patch B.3): every library task shows
     * up as a ready-made entry - pick one and Add to builder inserts a live reference to it
     * (resolved by id at run time, so library edits propagate into every script using it).
     * Selection is preserved across refreshes.
     */
    public void setLibraryTasks(java.util.List<main.actions.TaskRef> entries) {
        Object keepSel = getSelectedItem();
        String keepBound = keepSel instanceof main.actions.TaskRef
                ? ((main.actions.TaskRef) keepSel).getParamTarget() : null;

        muted = true;
        try {
            DefaultComboBoxModel<Action> model = new DefaultComboBoxModel<>();
            for (Action proto : REGISTRY.values()) model.addElement(proto);
            builtInCount = model.getSize();
            if (entries != null)
                for (main.actions.TaskRef t : entries)
                    if (t != null) model.addElement(t);
            setModel(model);

            int target = 0;
            for (int i = 0; i < model.getSize(); i++) {
                Action a = model.getElementAt(i);
                if (keepBound != null && a instanceof main.actions.TaskRef
                        && keepBound.equals(a.getParamTarget())) { target = i; break; }
                if (keepBound == null && keepSel != null && a.getClass() == keepSel.getClass()
                        && !(a instanceof main.actions.TaskRef)) { target = i; break; }
            }
            setSelectedIndex(target);
        } finally {
            muted = false;
        }
        rebuildTemplate();
    }

    public void setSelectedAction(Action action) {
        if (action == null)
            return;

        // Select the matching registry prototype by CLASS - the old code passed a copy that
        // isn't in the combo model, which Swing silently ignores. Note: changing the index
        // fires rebuildTemplate(), so the loaded copy must be applied AFTER selecting.
        for (int i = 0; i < getItemCount(); i++) {
            if (getItemAt(i) != null && getItemAt(i).getClass() == action.getClass()) {
                setSelectedIndex(i);
                break;
            }
        }

        selectedAction = action.copy();
        currentPanel = selectedAction.getParamPanel();
    }

    public Action getSelectedAction() {
        return selectedAction;
    }

    public Action create(String type) {
        return createByType(type);
    }

    /**
     * Instance-free action factory used by the persistence layer to rebuild a saved action
     * from its type name. Returns a fresh copy of the registry prototype, or null if the type
     * is unknown (e.g. an action that was renamed or removed since the profile was saved).
     */
    public static Action createByType(String type) {
        Action prototype = REGISTRY.get(type);
        return prototype != null ? prototype.copy() : null;
    }

    /**
     * Replaces the template instance (and therefore the live controls) for the selected action type.
     */
    private void rebuildTemplate() {
        // Read the COMBO BOX selection - previously this copied the old 'selectedAction'
        // field, so changing the dropdown never actually switched the action type.
        Object selection = getSelectedItem();
        Action prototype = selection instanceof Action
                ? (Action) selection
                : REGISTRY.get(REGISTRY.keySet().iterator().next());

        selectedAction = prototype.copy();
        currentPanel = selectedAction.getParamPanel();
        // v1.30 LAG FIX: no logging here. This runs on the EDT for EVERY dropdown change, and
        // DreamBot's Logger can block behind the client thread for seconds - which is exactly
        // the "attributes take ~3s to load" symptom. Keep this path pure Swing.
    }

    public void addSelectionListener(ActionListener l) {
        addActionListener(l);
    }

    /**
     * Returns the live parameter-controls panel for the currently selected action.
     * Embed this into your DreamBotMenu layout; it updates automatically on selection change.
     */
    public JPanel getParamsPanel() {
        return currentPanel == null ? getSelectedAction().getParamPanel() : currentPanel;
    }

//    @Override @Deprecated
//    public Object getSelectedItem() {
//        return super.getSelectedItem();
//    }

}










//    /**
//     * Builds a fully-configured Action from the current control values.
//     * Returns null (with a reason) if validation fails — caller should show a toast.
//     */
//    public Action build() {
//        if (currentAction == null)
//            return null;
//
//        // If the action has live controls, read from the existing template instance
//        if (currentPanel != null)
//            return currentAction.buildFromControls();
//
//        // Fallback for simple actions with only a target string
//        ActionLoader selectedAction = REGISTRY.get(getSelected());
//        return selectedAction != null ? selectedAction.constructor.get() : null;
//    }

//    public Set<String> scanTargets() {
//        String selected = getSelected();
//
//        if (selected == null)
//            return Set.of();
//
//        ActionLoader entry = REGISTRY.get(selected);
//        return entry != null ? entry.scanner.get() : Set.of();
//    }
//
//    public boolean updateControls() {
//        String selected = getSelected();
//
//        if (selected == null)
//            return false;
//
//        ActionLoader entry = REGISTRY.get(selected);
//        if (entry == null)
//            return false;
//
//        // call the update function
//        entry.updater.get();
//        return true;
//    }

//package main.menu;
//
//import main.actions.Action;
//import main.actions.ActionLoader;
//import main.actions.Walk;
//import main.actions.WalkToNpc;
//
//import javax.swing.*;
//import java.util.LinkedHashMap;
//import java.util.Map;
//import java.util.Set;
//
//public class JActionSelector extends JComboBox<String> {
//    // -----------------------------------------------------------------------
//    // Registry — add one line here every time you make a new Action subclass
//    // -----------------------------------------------------------------------
//    private static final Map<String, ActionLoader> REGISTRY = new LinkedHashMap<>();
//
//    static {
//        REGISTRY.put("Walk", new ActionLoader(Walk::new, Walk::scanTargets));
//        REGISTRY.put("Walk to NPC", new ActionLoader(WalkToNpc::new, WalkToNpc::scanTargets));
//        //REGISTRY.put("Attack", new ActionEntry(Attack::new, Attack::scanTargets));
//        // REGISTRY.put("Chop",   new ActionEntry(Chop::new,   Chop::scanTargets));
//        // REGISTRY.put("Mine",   new ActionEntry(Mine::new,   Mine::scanTargets));
//    }
//
//    // -----------------------------------------------------------------------
//    // Constructor — populates the combo box from the registry keys
//    // -----------------------------------------------------------------------
//    public JActionSelector() {
//        super(REGISTRY.keySet().toArray(new String[0]));
//    }
//
//    // -----------------------------------------------------------------------
//    // Called by the "Add to builder" button
//    // Builds the correct Action subclass from the selected name + target string
//    // -----------------------------------------------------------------------
//    public Action createSelectedAction(String target) {
//        if (getSelected() == null || target == null || target.isEmpty())
//            return null;
//
//        ActionLoader entry = REGISTRY.get(getSelected());
//        return entry != null ? entry.constructor.apply(target) : null;
//    }
//    public JPanel getSelectedParameterControls() {
//        if (getSelected() == null)
//            return null;
//
//        ActionLoader entry = REGISTRY.get(getSelected());
//        return (entry != null) ? entry.controller.get() : null;
//    }
//
//    private String getSelected() {
//        return (String) getSelectedItem();
//    }
//
//
//
//    // -----------------------------------------------------------------------
//    // Called by scanNearbyTargets() in DreamBotMenu
//    // Each action subclass decides what nearby targets are relevant to it
//    // -----------------------------------------------------------------------
//    public Set<String> scanTargets() {
//        String selected = (String) getSelectedItem();
//        if (selected == null)
//            return Set.of();
//
//        ActionLoader entry = REGISTRY.get(selected);
//        return entry != null ? entry.scanner.get() : Set.of();
//    }
//}