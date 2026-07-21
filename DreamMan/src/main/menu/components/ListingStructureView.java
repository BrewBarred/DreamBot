package main.menu.components;

import main.market.ScriptListing;
import main.menu.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * v1.64: the tasks → actions → checks outline, extracted from MarketCard into its own component
 * for the market DETAIL view. Everything renders <b>expanded by default</b> (the owner's
 * transparency rule) and each task is individually collapsible via its ▾/▸ header. There's no
 * height cap here - the detail view scrolls as one column, so a long script just makes a long
 * outline inside it.
 *
 * <p>Trigger-kind listings (v1.64) lead with their check - the empty "Tasks (0)" section a pure
 * trigger would show is skipped, so the card's single purpose is the first thing you read.
 */
public class ListingStructureView extends JPanel {

    public ListingStructureView(ScriptListing listing) {
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(LEFT_ALIGNMENT);

        main.data.store.ScriptBundle b = listing.bundle;
        if (b == null) {
            add(row("Structure isn't available for this listing"
                    + (listing.vipOnly ? " (VIP-only - the server withholds it)." : "."),
                    0, false, Theme.TEXT_DIM));
            return;
        }

        boolean triggerKind = "trigger".equalsIgnoreCase(listing.kind);
        java.util.List<main.data.store.TaskData> tasks = b.tasks;
        boolean haveTasks = tasks != null && !tasks.isEmpty();

        // tasks first - unless this IS a trigger listing with no tasks, where the check leads
        if (haveTasks || !triggerKind) {
            int taskCount = haveTasks ? tasks.size() : 0;
            String loops = (b.loops <= 0) ? "loops forever"
                    : (b.loops == 1 ? "runs once" : "runs \u00d7" + b.loops);
            add(row("Tasks (" + taskCount + ")  \u00b7  queue " + loops, 0, true, Theme.ACCENT));
            if (haveTasks) {
                for (int i = 0; i < tasks.size(); i++) {
                    main.data.store.TaskData t = tasks.get(i);
                    if (t != null) add(taskNode(i + 1, t));
                }
            } else {
                add(row("(none)", 1, false, Theme.TEXT_MUTED));
            }
        }

        java.util.List<String> checks = describeTriggers(b.globalTriggers);
        String head = triggerKind && !haveTasks
                ? "The check (" + Math.max(1, checks.size()) + ")"
                : "Always-on checks (" + checks.size() + ")";
        add(row(head, 0, true, Theme.ACCENT));
        if (checks.isEmpty()) {
            add(row("(none)", 1, false, Theme.TEXT_MUTED));
        } else {
            for (String c : checks) add(row("\u2022 " + c, 1, false, Theme.TEXT_DIM));
        }
    }

    /** One collapsible task: a clickable ▾/▸ header over its actions, expanded by default. */
    private JPanel taskNode(int index, main.data.store.TaskData t) {
        JPanel node = new JPanel();
        node.setOpaque(false);
        node.setLayout(new BoxLayout(node, BoxLayout.Y_AXIS));
        node.setAlignmentX(LEFT_ALIGNMENT);

        String rep = (t.repeat > 1) ? "  \u00d7" + t.repeat : "";
        String nm = (t.name == null || t.name.isEmpty()) ? "(unnamed task)" : t.name;
        String label = index + ". " + nm + rep;

        JPanel acts = new JPanel();
        acts.setOpaque(false);
        acts.setLayout(new BoxLayout(acts, BoxLayout.Y_AXIS));
        acts.setAlignmentX(LEFT_ALIGNMENT);
        java.util.List<main.data.ActionData> list = t.actions;
        if (list == null || list.isEmpty()) {
            acts.add(row("(no actions)", 2, false, Theme.TEXT_MUTED));
        } else {
            for (main.data.ActionData a : list)
                acts.add(row("\u2022 " + describeAction(a), 2, false, Theme.TEXT_DIM));
        }
        acts.setVisible(true);   // the transparency default: open

        JLabel header = row("\u25be  " + label, 1, true, Theme.TEXT);
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        header.setToolTipText("Click to collapse / expand this task's actions");
        header.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                boolean show = !acts.isVisible();
                acts.setVisible(show);
                header.setText((show ? "\u25be" : "\u25b8") + "  " + label);
                revalidateUp();
            }
        });

        node.add(header);
        node.add(acts);
        return node;
    }

    /** Refresh layout caches from here up to the scroll viewport so the column reflows. */
    private void revalidateUp() {
        invalidateTree(this);
        revalidate();
        repaint();
        Container c = getParent();
        while (c != null) { c.revalidate(); c.repaint(); c = c.getParent(); }
    }

    private static void invalidateTree(Component c) {
        c.invalidate();
        if (c instanceof Container)
            for (Component ch : ((Container) c).getComponents()) invalidateTree(ch);
    }

    private JLabel row(String text, int level, boolean bold, Color fg) {
        JLabel l = new JLabel(text);
        l.setFont(bold ? Theme.fontBold(12) : Theme.font(12));
        l.setForeground(fg);
        l.setBorder(new EmptyBorder(2, 6 + level * 18, 2, 4));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    /** A compact one-line description of an action: its type plus a hint of its main parameter. */
    static String describeAction(main.data.ActionData a) {
        if (a == null) return "(action)";
        String type = a.getType() == null ? "Action" : a.getType();
        java.util.Map<String, String> p = a.getParams();
        String hint = "";
        if (p != null && !p.isEmpty()) {
            for (String k : new String[]{"Target", "Name", "Item", "Item(s)", "Object", "NPC",
                    "TaskName", "Mode", "Spell", "Bone type (exact)"}) {
                if (p.containsKey(k) && p.get(k) != null && !p.get(k).isEmpty()) {
                    hint = p.get(k);
                    break;
                }
            }
            if (hint.isEmpty())
                for (String v : p.values())
                    if (v != null && !v.isEmpty()) { hint = v; break; }
        }
        return hint.isEmpty() ? type : type + " \u2192 " + hint;
    }

    /** Parses the bundle's trigger JSON into human-readable descriptions (best-effort). */
    static java.util.List<String> describeTriggers(String json) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (json == null || json.trim().isEmpty()) return out;
        try {
            for (main.watchers.Trigger t : main.watchers.TriggerCodec.fromJson(json))
                if (t != null) out.add(t.describe());
        } catch (Exception ignored) {
            // malformed / unknown trigger payload - show nothing rather than break the view
        }
        return out;
    }
}
