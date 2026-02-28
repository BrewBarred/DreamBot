package main.menu.components;

import main.actions.Action;
import main.actions.Walk;
import org.dreambot.api.utilities.Logger;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.util.LinkedHashMap;
import java.util.Map;

public class JActionSelector extends JComboBox<Action> {
    private static final Map<String, Action> REGISTRY = new LinkedHashMap<>();
    /**
     * The currently active "template" Action instance whose controls are shown in the UI.
     * Rebuilt whenever the selection changes.
     */
    private Action selectedAction;
    private JPanel currentPanel;

    static {
        REGISTRY.put("Walk", new Walk());
        //REGISTRY.put("Walk to NPC", new ActionLoader(WalkToNpc::new,  WalkToNpc::scanTargets));
    }

    public JActionSelector() {
        // instantiate action selector with the first item in the registry
        super(REGISTRY.values().toArray(new Action[0]));
        Logger.log(Logger.LogType.DEBUG, "Setting up action selector...");
        setSelectedIndex(0);
        rebuildTemplate();
        Logger.log(Logger.LogType.DEBUG, "Rebuild complete!");
        currentPanel = selectedAction.getParamPanel();
        Logger.log(Logger.LogType.DEBUG, "Fetch params!");
        addActionListener(e -> rebuildTemplate());
        Logger.log(Logger.LogType.DEBUG, "Added listener!");
    }

    public void setSelectedAction(Action action) {
        selectedAction = action.copy();
        currentPanel = selectedAction.getParamPanel();
        setSelectedItem(selectedAction);
    }

    public Action getSelectedAction() {
        return selectedAction;
    }

    public Action create(String type) {
        Action prototype = REGISTRY.get(type);
        return prototype != null ? prototype.copy() : null;
    }

    /**
     * Replaces the template instance (and therefore the live controls) for the selected action type.
     */
    private void rebuildTemplate() {
        Logger.log(Logger.LogType.DEBUG, "Rebuilding...");
        if (selectedAction == null)
            selectedAction = REGISTRY.get(REGISTRY.keySet().iterator().next());

        Logger.log(Logger.LogType.DEBUG, "Selected action: " + selectedAction);

        selectedAction = getSelectedAction().copy();
        currentPanel = selectedAction.getParamPanel();
        Logger.log(Logger.LogType.DEBUG, "Rebuild complete!");
    }

    public void addSelectionListener(ActionListener l) {
        addActionListener(l);
    }

    /**
     * Returns the live parameter-controls panel for the currently selected action.
     * Embed this into your DreamBotMenu layout; it updates automatically on selection change.
     */
    public JPanel getCurrentPanel() {
        return currentPanel;
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