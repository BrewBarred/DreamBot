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
                    // v1.30: the on/off dot is DRAWN (UIIcons.dot) - the \u25cf/\u25cb glyphs
                    // were the last font-dependent icons in this tab.
                    // v1.50: chained else-if triggers read as the ladder they are
                    lab.setText((i + 1) + ". " + (t.isChainedElse() ? "else " : "") + t.describe());
                    lab.setIcon(UIIcons.dot(12,
                            t.isEnabled() ? Theme.ACCENT : Theme.TEXT_MUTED, t.isEnabled()));
                    lab.setIconTextGap(6);
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

        JButton add = new Theme.ThemedButton("+ Add trigger");
        add.addActionListener(e -> {
            Trigger t = new Trigger(Condition.HP_BELOW, "15");
            model.add(t);
            listModel.addElement(t);
            list.setSelectedValue(t, true);
        });
        // Patch B.17: plain "Remove" - the \u2715 glyph rendered as an empty box on clients
        // whose UI font doesn't carry it (same missing-glyph family as the market stars).
        JButton remove = new Theme.ThemedButton("Remove");
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
            JLabel hint = new JLabel("Select a trigger on the left, or + Add trigger to create one.");
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

        // ── v1.30: whole-check randomness ──
        // "Only bury bones ~10% of the time" - the check rolls once per eligible moment; a miss
        // consumes that moment, so the odds are per-opportunity, not per-poll.
        JSlider chance = new JSlider(1, 100, t.getChancePercent());
        chance.setOpaque(false);
        chance.setPreferredSize(new Dimension(170, 24));
        chance.setMaximumSize(new Dimension(170, 24));
        JLabel chanceLbl = new JLabel();
        chanceLbl.setForeground(Theme.TEXT_MUTED);
        Runnable chanceText = () -> {
            int v = chance.getValue();
            chanceLbl.setText(v >= 100 ? "always fires"
                    : "~" + v + "%  (about " + Math.max(1, Math.round(v / 10.0)) + " in 10 chances)");
        };
        chance.addChangeListener(e -> {
            t.setChancePercent(chance.getValue());
            chanceText.run();
            list.repaint();
        });
        chanceText.run();
        JPanel chanceRow = row("Chance:", chance, chanceLbl);

        // ── v1.30: run-every timer ──
        // "Only perform this check every H/M/S" (each 0-60). Below 5s total the timer can't
        // meaningfully gate anything, so it stays inert and says so.
        JCheckBox chkTimer = new JCheckBox("only every", t.isTimerEnabled());
        chkTimer.setOpaque(false);
        chkTimer.setForeground(Theme.TEXT);
        chkTimer.setToolTipText("Fire this trigger at most once per interval (minimum 5 seconds)");
        long iv = Math.max(0, t.getTimerIntervalMs()) / 1000;
        JSpinner spH = new JSpinner(new SpinnerNumberModel((int) Math.min(60, iv / 3600), 0, 60, 1));
        JSpinner spM = new JSpinner(new SpinnerNumberModel((int) ((iv % 3600) / 60), 0, 60, 1));
        JSpinner spS = new JSpinner(new SpinnerNumberModel((int) (iv % 60), 0, 60, 1));
        for (JSpinner sp : new JSpinner[]{spH, spM, spS})
            sp.setMaximumSize(new Dimension(52, 26));
        JLabel timerHint = new JLabel();
        timerHint.setFont(timerHint.getFont().deriveFont(Font.ITALIC, 11f));
        Runnable syncTimer = () -> {
            boolean on = chkTimer.isSelected();
            spH.setEnabled(on); spM.setEnabled(on); spS.setEnabled(on);
            long ms = (((int) spH.getValue()) * 3600L
                    + ((int) spM.getValue()) * 60L
                    + (int) spS.getValue()) * 1000L;
            t.setTimer(on, ms);
            if (!on) {
                timerHint.setText("  off - the trigger fires whenever its condition holds");
                timerHint.setForeground(Theme.TEXT_MUTED);
            } else if (ms < Trigger.MIN_TIMER_MS) {
                timerHint.setText("  needs at least 5 seconds - timer inactive");
                timerHint.setForeground(new Color(200, 90, 70));
            } else {
                timerHint.setText("  at most once per " + Trigger.formatInterval(ms));
                timerHint.setForeground(Theme.TEXT_MUTED);
            }
            list.repaint();
        };
        chkTimer.addActionListener(e -> syncTimer.run());
        spH.addChangeListener(e -> syncTimer.run());
        spM.addChangeListener(e -> syncTimer.run());
        spS.addChangeListener(e -> syncTimer.run());
        syncTimer.run();
        JPanel timerRow = row("Timer:", chkTimer, spH, dimLabel("h"), spM, dimLabel("m"),
                spS, dimLabel("s"), timerHint);

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
        JButton delAct = new Theme.ThemedButton("remove");   // B.17: \u2715 was a missing glyph
        delAct.addActionListener(e -> {
            int idx = resp.getSelectedIndex();
            if (idx >= 0) { t.getResponse().remove(idx); respModel.remove(idx); list.repaint(); }
        });
        JPanel respBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        respBtns.setOpaque(false);
        respBtns.add(addAct);
        respBtns.add(delAct);

        JLabel thenLbl = new JLabel("Then run (several triggers can run at once - see notes):");
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
        detail.add(Box.createVerticalStrut(6));
        // v1.33: ANDed extra conditions (with NOT). All must hold for the trigger to fire.
        JLabel andLbl = new JLabel("More conditions  (AND binds tighter than OR, like code):");
        andLbl.setForeground(Theme.ACCENT);
        andLbl.setFont(andLbl.getFont().deriveFont(Font.BOLD, 11f));
        andLbl.setAlignmentX(LEFT_ALIGNMENT);
        detail.add(andLbl);
        detail.add(buildClausesPanel(t));
        detail.add(Box.createVerticalStrut(6));
        detail.add(optRow);
        detail.add(Box.createVerticalStrut(4));
        detail.add(chanceRow);                      // v1.30
        detail.add(Box.createVerticalStrut(4));
        detail.add(timerRow);                       // v1.30
        detail.add(Box.createVerticalStrut(4));
        // v1.33: what the QUEUE does after this trigger's response finishes
        JComboBox<Trigger.Control> ctrl = new JComboBox<>(Trigger.Control.values());
        ctrl.setSelectedItem(t.getControl());
        ctrl.setMaximumSize(new Dimension(300, 30));
        ctrl.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i,
                    boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                if (v instanceof Trigger.Control) setText(controlLabel((Trigger.Control) v));
                return this;
            }
        });
        ctrl.addActionListener(e -> {
            t.setControl((Trigger.Control) ctrl.getSelectedItem());
            list.repaint();
        });
        detail.add(row("After run:", ctrl));
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

    /** A small muted unit label ("h", "m", "s") for the timer row (v1.30). */
    private JLabel dimLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Theme.TEXT_MUTED);
        return l;
    }

    // ── v1.33: ANDed extra-condition rows ────────────────────────────────────
    private JComponent buildClausesPanel(Trigger t) {
        JPanel wrap = new JPanel();
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.setOpaque(false);
        wrap.setAlignmentX(LEFT_ALIGNMENT);
        rebuildClauses(t, wrap);
        return wrap;
    }

    private void rebuildClauses(Trigger t, JPanel wrap) {
        wrap.removeAll();
        for (Trigger.Clause cl : t.getExtraClauses())
            wrap.add(clauseRow(t, wrap, cl));
        // v1.50: one "+" that offers the three ways to extend the logic. AND/OR add a clause to
        // THIS trigger (AND binds tighter than OR, code-style); "else if" starts a NEW trigger
        // chained below this one - it only runs when this one didn't fire, first match wins.
        JButton addC = new JButton("+");
        addC.setAlignmentX(LEFT_ALIGNMENT);
        addC.setToolTipText("Extend the logic: and if\u2026 / or if\u2026 / else if\u2026");
        addC.addActionListener(e -> {
            JPopupMenu m = new JPopupMenu();
            JMenuItem andIf = new JMenuItem("and if\u2026  (this trigger, binds tighter)");
            andIf.addActionListener(a -> {
                t.getExtraClauses().add(new Trigger.Clause(Condition.INVENTORY_FULL, "", false, false));
                rebuildClauses(t, wrap); wrap.revalidate(); wrap.repaint();
            });
            JMenuItem orIf = new JMenuItem("or if\u2026  (this trigger, alternative)");
            orIf.addActionListener(a -> {
                t.getExtraClauses().add(new Trigger.Clause(Condition.INVENTORY_FULL, "", false, true));
                rebuildClauses(t, wrap); wrap.revalidate(); wrap.repaint();
            });
            JMenuItem elseIf = new JMenuItem("else if\u2026  (new trigger, runs only if this one didn't)");
            elseIf.addActionListener(a -> {
                Trigger et = new Trigger(Condition.HP_BELOW, "15");
                et.setChainedElse(true);
                int at = listModel.indexOf(t);
                model.add(model.indexOf(t) + 1, et);
                listModel.add(at + 1, et);
                list.setSelectedValue(et, true);
            });
            m.add(andIf); m.add(orIf); m.addSeparator(); m.add(elseIf);
            m.show(addC, 0, addC.getHeight());
        });
        wrap.add(addC);
    }

    private JComponent clauseRow(Trigger t, JPanel wrap, Trigger.Clause cl) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        // v1.50: the connector to the previous term - AND binds tighter than OR (code precedence)
        JComboBox<String> conn = new JComboBox<>(new String[]{"AND", "OR"});
        conn.setSelectedIndex(cl.or ? 1 : 0);
        conn.setMaximumSize(new Dimension(64, 28));
        conn.setToolTipText("How this joins the previous condition (AND binds tighter than OR)");
        conn.addActionListener(e -> { cl.or = conn.getSelectedIndex() == 1; list.repaint(); });
        p.add(conn);

        JCheckBox not = new JCheckBox("NOT", cl.negate);
        not.setOpaque(false);
        not.setForeground(Theme.TEXT);
        not.setToolTipText("Require this condition to be FALSE");
        not.addActionListener(e -> { cl.negate = not.isSelected(); list.repaint(); });

        JComboBox<Condition> cb = new JComboBox<>(Condition.values());
        cb.setSelectedItem(cl.condition);
        cb.setMaximumSize(new Dimension(230, 28));
        cb.setRenderer(conditionRenderer());

        JTextField arg = new JTextField(cl.arg, 9);
        arg.setMaximumSize(new Dimension(150, 28));
        arg.addCaretListener(e -> { cl.arg = arg.getText(); list.repaint(); });

        cb.addActionListener(e -> {
            cl.condition = (Condition) cb.getSelectedItem();
            arg.setVisible(cl.condition != null && cl.condition.needsArg());
            list.repaint();
        });
        arg.setVisible(cl.condition != null && cl.condition.needsArg());

        // v1.50: DRAWN X icon - the \u2715 glyph is missing from the client font (rendered as a box)
        JButton rm = new JButton(drawnX(11, new Color(0xC9, 0x6A, 0x6A)));
        rm.setMargin(new Insets(2, 6, 2, 6));
        rm.setToolTipText("Remove this condition");
        rm.addActionListener(e -> {
            t.getExtraClauses().remove(cl);
            rebuildClauses(t, wrap);
            wrap.revalidate();
            wrap.repaint();
        });

        p.add(not);
        p.add(cb);
        p.add(arg);
        p.add(rm);
        return p;
    }

    /** Shared renderer that shows a Condition's friendly label. */
    private DefaultListCellRenderer conditionRenderer() {
        return new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i,
                    boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                if (v instanceof Condition) setText(((Condition) v).label());
                return this;
            }
        };
    }

    /** v1.50: a small vector-drawn X (the \u2715 glyph is missing from some client fonts). */
    private static javax.swing.Icon drawnX(int size, Color color) {
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(color);
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int m = 2;
        g.drawLine(m, m, size - m, size - m);
        g.drawLine(size - m, m, m, size - m);
        g.dispose();
        return new ImageIcon(img);
    }

    /** Friendly label for the post-run control dropdown (v1.33). */
    private static String controlLabel(Trigger.Control c) {
        switch (c) {
            case SKIP_NEXT:     return "then skip the next action";
            case RESTART_LAST:  return "then restart the last action";
            case RESTART_TASK:  return "then restart this task";
            case RESTART_QUEUE: return "then restart the whole queue";
            default:            return "nothing extra (just run the response)";
        }
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
