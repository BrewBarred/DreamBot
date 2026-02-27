package main.menu;

import main.actions.Action;
import main.actions.ActionLoader;
import main.actions.Walk;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class JActionSelector extends JComboBox<String> {
    private static final Map<String, ActionLoader> REGISTRY = new LinkedHashMap<>();
    /**
     * The currently active "template" Action instance whose controls are shown in the UI.
     * Rebuilt whenever the selection changes.
     */
    private Action currentAction;
    private JPanel currentPanel;

    static {
        REGISTRY.put("Walk",        new ActionLoader(Walk::new,       Walk::scanTargets));
        //REGISTRY.put("Walk to NPC", new ActionLoader(WalkToNpc::new,  WalkToNpc::scanTargets));
    }

    public JActionSelector() {
        super(REGISTRY.keySet().toArray(new String[0]));
        rebuildTemplate();
        addActionListener(e -> rebuildTemplate());
    }

    /**
     * Replaces the template instance (and therefore the live controls) for the selected action type.
     */
    private void rebuildTemplate() {
        String selected = getSelected();
        if (selected == null) return;

        if (currentAction != null && currentAction.getType().equals(selected))
            return;

        ActionLoader actionHandler = REGISTRY.get(selected);
        if (actionHandler == null) return;

        currentAction = actionHandler.constructor.get();
        currentPanel = currentAction.getParameterControls();
    }

    /**
     * Returns the live parameter-controls panel for the currently selected action.
     * Embed this into your DreamBotMenu layout; it updates automatically on selection change.
     */
    public JPanel getCurrentPanel() {
        return currentPanel; // may be null if the action has no extra params
    }

    /**
     * Builds a fully-configured Action from the current control values.
     * Returns null (with a reason) if validation fails — caller should show a toast.
     */
    public Action build() {
        if (currentAction == null)
            return null;

        // If the action has live controls, read from the existing template instance
        if (currentPanel != null)
            return currentAction.buildFromControls();

        // Fallback for simple actions with only a target string
        ActionLoader selectedAction = REGISTRY.get(getSelected());
        return selectedAction != null ? selectedAction.constructor.get() : null;
    }

    public Set<String> scanTargets() {
        String selected = getSelected();

        if (selected == null)
            return Set.of();

        ActionLoader entry = REGISTRY.get(selected);
        return entry != null ? entry.scanner.get() : Set.of();
    }

    private String getSelected() {
        return (String) getSelectedItem();
    }
}

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