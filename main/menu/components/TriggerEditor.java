package main.menu.components;

import main.actions.Action;
import main.menu.Theme;
import main.watchers.Condition;
import main.watchers.Trigger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.function.Supplier;

/**
 * A reusable editor for a list of {@link Trigger}s (Patch B.4). Used by the Watchers tab (global
 * triggers) and by the per-action "Watchers…" button in the builder. Each row is one trigger:
 * a condition dropdown, its argument, an enabled toggle, an "instead of this action" toggle
 * (per-action only), and an editable response chain built from the normal action selector.
 *
 * <p>The editor mutates the list it's given directly, so callers just hand over
 * {@code menu.getGlobalTriggers()} or {@code action.getTriggers()} and the changes stick.
 */
public class TriggerEditor extends JPanel {

    private final List<Trigger> model;
    private final boolean perAction;
    private final Supplier<Action> actionFactory;  // how to build a response action from the selector
    private final JPanel rows = new JPanel();

    /**
     * @param model         the trigger list to edit in place
     * @param perAction     true to show the "instead of this action" toggle
     * @param actionFactory supplies a fresh Action for the currently-selected type (from a selector)
     */
    public TriggerEditor(List<Trigger> model, boolean perAction, Supplier<Action> actionFactory) {
        this.model = model;
        this.perAction = perAction;
        this.actionFactory = actionFactory;

        setLayout(new BorderLayout(0, 8));
        setOpaque(false);

        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setOpaque(false);

        JButton add = new Theme.ThemedButton("+ Add watcher");
        add.addActionListener(e -> {
            model.add(new Trigger(Condition.HP_BELOW, "15"));
            rebuild();
        });

        JScrollPane scroll = Theme.thinScrollbars(new JScrollPane(rows));
        scroll.setBorder(null);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);

        add(add, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        rebuild();
    }

    private void rebuild() {
        rows.removeAll();
        for (int i = 0; i < model.size(); i++)
            rows.add(buildRow(model.get(i)));
        rows.revalidate();
        rows.repaint();
    }

    private JPanel buildRow(Trigger t) {
        JPanel card = new JPanel(new BorderLayout(6, 6));
        card.setBackground(Theme.SURFACE_1);
        card.setBorder(new EmptyBorder(8, 8, 8, 8));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 320));

        // ── top line: WHEN ──
        JPanel when = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        when.setOpaque(false);

        JComboBox<Condition> cond = new JComboBox<>(Condition.values());
        cond.setSelectedItem(t.getCondition());
        cond.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i,
                    boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                if (v instanceof Condition) setText(((Condition) v).label());
                return this;
            }
        });
        cond.addActionListener(e -> t.setCondition((Condition) cond.getSelectedItem()));

        JTextField arg = new JTextField(t.getArg(), 10);
        arg.setToolTipText("Argument: threshold (15 or 40%), area x1,y1,x2,y2[,z], coord x,y[,z], or item name");
        arg.addCaretListener(e -> t.setArg(arg.getText()));

        JCheckBox enabled = new JCheckBox("on", t.isEnabled());
        enabled.setOpaque(false);
        enabled.setForeground(Theme.TEXT);
        enabled.addActionListener(e -> t.setEnabled(enabled.isSelected()));

        JLabel when1 = new JLabel("When");
        when1.setForeground(Theme.TEXT_DIM);
        when.add(when1);
        when.add(cond);
        when.add(arg);
        when.add(enabled);

        if (perAction) {
            JCheckBox instead = new JCheckBox("instead of action", t.replacesAction());
            instead.setOpaque(false);
            instead.setForeground(new Color(230, 160, 90));
            instead.setToolTipText("Run this response INSTEAD of the action when it fires "
                    + "(e.g. bank instead of loot when full)");
            instead.addActionListener(e -> t.setReplacesAction(instead.isSelected()));
            when.add(instead);
        }

        JButton del = new Theme.ThemedButton("✕");
        del.setBorder(new EmptyBorder(2, 6, 2, 6));
        del.addActionListener(e -> { model.remove(t); rebuild(); });
        when.add(del);

        // ── response chain: THEN ──
        DefaultListModel<Action> respModel = new DefaultListModel<>();
        for (Action a : t.getResponse()) respModel.addElement(a);
        JList<Action> respList = new JList<>(respModel);
        respList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i,
                    boolean s, boolean f) {
                JLabel lab = (JLabel) super.getListCellRendererComponent(l, v, i, s, f);
                lab.setText("  " + (i + 1) + ".  " + (v instanceof Action ? ((Action) v).toBuildString() : "?"));
                return lab;
            }
        });
        respList.setVisibleRowCount(3);
        respList.setBackground(new Color(20, 20, 20));

        JButton addAct = new Theme.ThemedButton("+ action");
        addAct.setBorder(new EmptyBorder(3, 8, 3, 8));
        addAct.addActionListener(e -> {
            Action a = actionFactory != null ? actionFactory.get() : null;
            if (a != null) { t.getResponse().add(a); respModel.addElement(a); }
        });
        JButton delAct = new Theme.ThemedButton("- action");
        delAct.setBorder(new EmptyBorder(3, 8, 3, 8));
        delAct.addActionListener(e -> {
            int idx = respList.getSelectedIndex();
            if (idx >= 0) { t.getResponse().remove(idx); respModel.remove(idx); }
        });

        JPanel then = new JPanel(new BorderLayout(6, 4));
        then.setOpaque(false);
        JLabel thenLbl = new JLabel("Then run:");
        thenLbl.setForeground(Theme.ACCENT);
        JPanel thenBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        thenBtns.setOpaque(false);
        thenBtns.add(addAct);
        thenBtns.add(delAct);
        JPanel thenTop = new JPanel(new BorderLayout());
        thenTop.setOpaque(false);
        thenTop.add(thenLbl, BorderLayout.WEST);
        thenTop.add(thenBtns, BorderLayout.EAST);
        then.add(thenTop, BorderLayout.NORTH);
        then.add(new JScrollPane(respList), BorderLayout.CENTER);

        card.add(when, BorderLayout.NORTH);
        card.add(then, BorderLayout.CENTER);
        return card;
    }
}
