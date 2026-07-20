package main.menu.components;

import javax.swing.*;
import java.util.List;

/**
 * v1.62: an <b>editable</b> combo box that speaks the same {@code getParam()/setParam()} contract
 * as {@link main.components.JParamTextField}, so an action can swap a free-text parameter for a
 * pick-from-list dropdown without changing any of its serialize / apply / build-string code.
 *
 * <p>Built for TaskRef's "Library task" parameter: the dropdown is populated from the current
 * Task Library, so you pick an existing task instead of typing its name - but because it stays
 * editable, a name that isn't in the list yet (a task you haven't built) still works exactly like
 * the old text field, which keeps every existing saved reference valid.
 *
 * <p>The value is always read live from the editor, so whether you typed it or picked it, and
 * whether or not it matches a dropdown entry, {@code getParam()} returns what's actually shown.
 */
public class JParamComboField extends JComboBox<String> {

    public JParamComboField(String initial) {
        setEditable(true);          // pick OR type - the editor holds the live value
        if (initial != null && !initial.isEmpty()) {
            addItem(initial);
            setSelectedItem(initial);
        }
    }

    /**
     * Replaces the dropdown entries while keeping whatever the field currently shows - even if
     * that value isn't among the new entries (an as-yet-uncreated task name stays put).
     */
    public void setItemsPreservingValue(List<String> items) {
        String current = getParam();
        removeAllItems();
        if (items != null)
            for (String s : items)
                if (s != null && !s.isEmpty()) addItem(s);
        setParam(current);          // restore the shown value regardless of the list
    }

    /** Sets the shown value (mirrors JParamTextField.setParam). */
    public void setParam(String value) {
        setSelectedItem(value == null ? "" : value);
    }

    /** The value actually shown in the editor - typed or picked (mirrors JParamTextField.getParam). */
    public String getParam() {
        Object item = (getEditor() != null) ? getEditor().getItem() : getSelectedItem();
        return item == null ? "" : item.toString().trim();
    }
}
