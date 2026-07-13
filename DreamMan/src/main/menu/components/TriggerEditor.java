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
 * The Checks editor (Patch B.5 redesign). Left: a compact list - one line per check, with its
 * on/off state and a plain-English summary. Right: the detail editor for the SELECTED check:
 * condition, an argument field that only appears when the condition takes one (with a hint of
 * what it means), the "instead of this action" toggle (per-action checks), cooldown, and the
 * response chain - which can contain plain actions OR whole library tasks.
 *
 * <p>The editor mutates the list it's given directly, so callers hand over
 * {@code menu.getGlobalTriggers()} or {@code action.getTriggers()} and the changes stick.
 */
public class TriggerEditor extends JPanel {

    private final List<Trigger> model;
    private final boolean perAction;
    private final Supplier<Action> actionFactory;
    /** Called after a drag-reorder so the caller can persist the new priority order (B.8). */
    private Runnable orderChangedHook;

    /** Sets a callback fired whenever the check order changes (drag-reorder). */
    public void setOrderChangedHook(Runnable r) { this.orderChangedHook = r; }

    private void onOrderChanged() {
        if (orderChangedHook != null)
            try { orderChangedHook.run(); } catch (Throwable ignored) {}
    }

    private final DefaultListModel<Trigger> listModel = new DefaultListModel<>();
    private final JList<Trigger> list = new JList<>(listModel);

    // detail widgets (bound to the selected trigger)
    private final JPanel detail = new JPanel();
    private Trigger current;
    private JComboBox<Condition> cond;
    private JTextField argField;
    private JLabel argHint;
    private JPanel argRow;
    private JCheckBox chkEnabled, chkInstead;
    private JTextField cooldownField;
    private DefaultListModel<Action> respModel;

    public TriggerEditor(List<Trigger> model, boolean perAction, Supplier<Action> actionFactory) {
        this.model = model;
        this.perAction = perAction;
        this.actionFactory = actionFactory;

        setLayout(new BorderLayout(10, 0));
        setOpaque(false);

        // ── WEST: the compact list of checks ──
        for (Trigger t : model) if (t != null) listModel.addElement(t);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i,
                    boolean sel, boolean foc) {
                JLabel lab = (JLabel) super.getListCellRendererComponent(l, v, i, sel, foc);
                if (v instanceof Trigger) {
                    Trigger t = (Trigger) v;
                    // Patch B.8: the number IS the priority - drag to reorder. When two checks
                    // want the same resource (both freeing inventory, both walking), the lower
                    // number wins that cycle and the other waits.
                    lab.setText((i + 1) + ". " + (t.isEnabled() ? "\u25cf " : "\u25cb ") + t.describe());
                    lab.setForeground(sel ? Theme.ACCENT : (t.isEnabled() ? Theme.TEXT : Theme.TEXT_DIM));
                }
                lab.setBorder(new EmptyBorder(6, 6, 6, 6));
                return lab;
            }
        });
        list.setBackground(new Color(20, 20, 20));
        list.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) bind(list.getSelectedValue()); });

        // ── Patch B.8: drag to reorder checks - the order IS the priority ──
        list.setDragEnabled(true);
        list.setDropMode(DropMode.INSERT);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setTransferHandler(new TransferHandler() {
            private int fromIndex = -1;

            @Override public int getSourceActions(JComponent c) { return MOVE; }

            @Override protected java.awt.datatransfer.Transferable createTransferable(JComponent c) {
                fromIndex = list.getSelectedIndex();
                Trigger t = list.getSelectedValue();
                return new java.awt.datatransfer.StringSelection(t == null ? "" : t.describe());
            }

            @Override public boolean canImport(TransferSupport support) {
                return support.isDrop() && fromIndex >= 0;
            }

            @Override public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
                int to = dl.getIndex();
                if (to < 0 || fromIndex < 0 || fromIndex >= model.size()) return false;

                Trigger moved = model.get(fromIndex);
                model.remove(fromIndex);
                if (to > fromIndex) to--;                 // account for the removal shift
                to = Math.max(0, Math.min(to, model.size()));
                model.add(to, moved);                     // the MODEL is the source of truth

                // rebuild the visible list from the model and keep the moved check selected
                listModel.clear();
                for (Trigger t : model) if (t != null) listModel.addElement(t);
                list.setSelectedIndex(to);
                bind(moved);
                onOrderChanged();
                fromIndex = -1;
                return true;
            }

            @Override protected void exportDone(JComponent c, java.awt.datatransfer.Transferable d, int action) {
                fromIndex = -1;
            }
        });

        JButton add = new Theme.ThemedButton("+ Add check");
        add.addActionListener(e -> {
            Trigger t = new Trigger(Condition.HP_BELOW, "15");
            model.add(t);
            listModel.addElement(t);
            list.setSelectedValue(t, true);
        });
        JButton remove = new Theme.ThemedButton("\u2715 Remove");
        remove.addActionListener(e -> {
            Trigger t = list.getSelectedValue();
            if (t == null) return;
            model.remove(t);
            listModel.removeElement(t);
            bind(null);
        });
        JPanel westBtns = new JPanel(new GridLayout(1, 2, 6, 0));
        westBtns.setOpaque(false);
        westBtns.add(add);
        westBtns.add(remove);

        JPanel west = new JPanel(new BorderLayout(0, 6));
        west.setOpaque(false);
        west.setPreferredSize(new Dimension(290, 10));
        west.add(westBtns, BorderLayout.NORTH);
        JScrollPane listScroll = Theme.thinScrollbars(new JScrollPane(list));
        listScroll.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));
        west.add(listScroll, BorderLayout.CENTER);

        // ── CENTER: the detail editor ──
        detail.setLayout(new BoxLayout(detail, BoxLayout.Y_AXIS));
        detail.setOpaque(false);

        add(west, BorderLayout.WEST);
        add(detail, BorderLayout.CENTER);
        bind(model.isEmpty() ? null : model.get(0));
        if (!model.isEmpty()) list.setSelectedIndex(0);
    }

    /**
     * Re-syncs the visible list from the underlying trigger list (Patch B.5). The editor
     * snapshots the list at construction, so anything that replaces its CONTENTS afterwards -
     * most importantly a profile load restoring saved checks - must call this or the UI shows
     * an empty list while the engine happily runs the restored checks.
     */
    public void reload() {
        listModel.clear();
        for (Trigger t : model)
            if (t != null) listModel.addElement(t);
        if (model.isEmpty()) bind(null);
        else { list.setSelectedIndex(0); bind(model.get(0)); }
    }

    /** Rebuilds the detail editor for one trigger (null shows the empty-state hint). */
    private void bind(Trigger t) {
        current = t;
        detail.removeAll();

        if (t == null) {
            JLabel hint = new JLabel("Select a check on the left, or + Add check to create one.");
            hint.setForeground(Theme.TEXT_DIM);
            hint.setBorder(new EmptyBorder(12, 8, 0, 0));
            hint.setAlignmentX(LEFT_ALIGNMENT);
            detail.add(hint);
            detail.revalidate();
            detail.repaint();
            return;
        }

        // WHEN row
        cond = new JComboBox<>(Condition.values());
        cond.setSelectedItem(t.getCondition());
        cond.setMaximumSize(new Dimension(260, 30));
        cond.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i,
                    boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                if (v instanceof Condition) setText(((Condition) v).label());
                return this;
            }
        });
        cond.addActionListener(e -> {
            t.setCondition((Condition) cond.getSelectedItem());
            syncArgVisibility(t);
            list.repaint();
        });

        argField = new JTextField(t.getArg(), 12);
        argField.setMaximumSize(new Dimension(180, 30));
        argField.addCaretListener(e -> { t.setArg(argField.getText()); list.repaint(); });
        argHint = new JLabel();
        argHint.setForeground(Theme.TEXT_MUTED);
        argHint.setFont(argHint.getFont().deriveFont(Font.ITALIC, 11f));

        chkEnabled = new JCheckBox("enabled", t.isEnabled());
        chkEnabled.setOpaque(false);
        chkEnabled.setForeground(Theme.TEXT);
        chkEnabled.addActionListener(e -> { t.setEnabled(chkEnabled.isSelected()); list.repaint(); });

        JPanel whenRow = row("When:", cond, chkEnabled);
        argRow = row("Value:", argField, argHint);

        // options row
        cooldownField = new JTextField(String.valueOf(t.getCooldownMs()), 6);
        cooldownField.setMaximumSize(new Dimension(90, 30));
        cooldownField.addCaretListener(e -> {
            try { t.setCooldownMs(Long.parseLong(cooldownField.getText().trim())); }
            catch (NumberFormatException ignored) {}
        });
        JLabel cdLbl = new JLabel("ms between fires");
        cdLbl.setForeground(Theme.TEXT_MUTED);
        JPanel optRow;
        if (perAction) {
            chkInstead = new JCheckBox("run INSTEAD of this action when it fires", t.replacesAction());
            chkInstead.setOpaque(false);
            chkInstead.setForeground(new Color(230, 160, 90));
            chkInstead.setToolTipText("e.g. bank instead of loot when the inventory is full");
            chkInstead.addActionListener(e -> { t.setReplacesAction(chkInstead.isSelected()); list.repaint(); });
            optRow = row("Cooldown:", cooldownField, cdLbl, chkInstead);
        } else {
            optRow = row("Cooldown:", cooldownField, cdLbl);
        }

        // THEN: response chain (actions and/or library tasks)
        respModel = new DefaultListModel<>();
        for (Action a : t.getResponse()) respModel.addElement(a);
        JList<Action> resp = new JList<>(respModel);
        resp.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i,
                    boolean s, boolean f) {
                JLabel lab = (JLabel) super.getListCellRendererComponent(l, v, i, s, f);
                lab.setText("  " + (i + 1) + ".  " + (v instanceof Action ? ((Action) v).toBuildString() : "?"));
                lab.setBorder(new EmptyBorder(3, 3, 3, 3));
                return lab;
            }
        });
        resp.setBackground(new Color(20, 20, 20));

        JButton addAct = new Theme.ThemedButton("+ action / task");
        addAct.setToolTipText("Add a response step - plain actions and your library tasks are both in the picker");
        addAct.addActionListener(e -> {
            Action a = actionFactory != null ? actionFactory.get() : null;
            if (a != null) { t.getResponse().add(a); respModel.addElement(a); list.repaint(); }
        });
        JButton delAct = new Theme.ThemedButton("\u2715 remove");
        delAct.addActionListener(e -> {
            int idx = resp.getSelectedIndex();
            if (idx >= 0) { t.getResponse().remove(idx); respModel.remove(idx); list.repaint(); }
        });
        JPanel respBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        respBtns.setOpaque(false);
        respBtns.add(addAct);
        respBtns.add(delAct);

        JLabel thenLbl = new JLabel("Then run (several checks can run at once - see notes):");
        thenLbl.setForeground(Theme.ACCENT);
        thenLbl.setAlignmentX(LEFT_ALIGNMENT);

        JScrollPane respScroll = Theme.thinScrollbars(new JScrollPane(resp));
        respScroll.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));
        respScroll.setAlignmentX(LEFT_ALIGNMENT);
        respScroll.setPreferredSize(new Dimension(10, 170));

        respBtns.setAlignmentX(LEFT_ALIGNMENT);

        detail.add(whenRow);
        detail.add(Box.createVerticalStrut(4));
        detail.add(argRow);
        detail.add(Box.createVerticalStrut(4));
        detail.add(optRow);
        detail.add(Box.createVerticalStrut(10));
        detail.add(thenLbl);
        detail.add(Box.createVerticalStrut(4));
        detail.add(respScroll);
        detail.add(Box.createVerticalStrut(4));
        detail.add(respBtns);
        detail.add(Box.createVerticalGlue());

        syncArgVisibility(t);
        detail.revalidate();
        detail.repaint();
    }

    /** The Value row only exists when the condition takes an argument (Patch B.5). */
    private void syncArgVisibility(Trigger t) {
        Condition c = t.getCondition();
        boolean needs = c != null && c.needsArg();
        argRow.setVisible(needs);
        if (needs) argHint.setText("  " + c.argHint());
    }

    private JPanel row(String label, JComponent... comps) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        JLabel l = new JLabel(label);
        l.setForeground(Theme.TEXT_DIM);
        l.setPreferredSize(new Dimension(70, 24));
        p.add(l);
        for (JComponent c : comps) p.add(c);
        return p;
    }
}
