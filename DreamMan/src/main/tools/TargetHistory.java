package main.tools;

import main.data.store.LocalStore;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Target-field history (v1.31): remembers the last ~30 targets you've used (names, tiles) and
 * offers them as a suggestion popup while you type in any target field. Values are recorded
 * when an action is added/updated in the builder, persisted to {@code <root>/target-history.txt}.
 */
public final class TargetHistory {

    private TargetHistory() {}

    private static final int MAX = 30;
    private static List<String> items;

    private static File file() { return new File(LocalStore.getRoot(), "target-history.txt"); }

    private static synchronized List<String> items() {
        if (items != null) return items;
        items = new ArrayList<>();
        try {
            File f = file();
            if (f.isFile())
                for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8))
                    if (line != null && !line.isBlank()) items.add(line.trim());
        } catch (Throwable ignored) {}
        return items;
    }

    /** Records a used target at the front of the MRU (deduped, capped, saved). */
    public static synchronized void record(String value) {
        if (value == null) return;
        String v = value.trim();
        if (v.isEmpty() || v.length() > 60) return;
        List<String> l = items();
        l.removeIf(s -> s.equalsIgnoreCase(v));
        l.add(0, v);
        while (l.size() > MAX) l.remove(l.size() - 1);
        try {
            Files.write(file().toPath(),
                    String.join("\n", l).getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {}
    }

    /** Attaches a lightweight suggestion popup to a text field (prefix/contains matching). */
    public static void attach(JTextField field) {
        if (field == null) return;
        JPopupMenu popup = new JPopupMenu();
        popup.setFocusable(false);   // typing stays in the field

        Runnable refresh = () -> {
            String typed = field.getText() == null ? "" : field.getText().trim().toLowerCase(Locale.ROOT);
            popup.removeAll();
            int shown = 0;
            for (String s : items()) {
                if (!typed.isEmpty() && !s.toLowerCase(Locale.ROOT).contains(typed)) continue;
                if (typed.isEmpty() || !s.equalsIgnoreCase(typed)) {
                    JMenuItem mi = new JMenuItem(s);
                    mi.addActionListener(e -> {
                        if (field instanceof main.components.JParamTextField)
                            ((main.components.JParamTextField) field).setParam(s);
                        else field.setText(s);
                        popup.setVisible(false);
                    });
                    popup.add(mi);
                    if (++shown >= 8) break;
                }
            }
            if (shown == 0) { popup.setVisible(false); return; }
            popup.show(field, 0, field.getHeight());
            field.requestFocusInWindow();
        };

        field.addKeyListener(new KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) {
                int c = e.getKeyCode();
                if (c == KeyEvent.VK_ESCAPE || c == KeyEvent.VK_ENTER) { popup.setVisible(false); return; }
                if (c == KeyEvent.VK_DOWN && popup.isVisible()) {
                    // hand focus to the popup so arrows/enter select a suggestion
                    popup.setFocusable(true);
                    popup.requestFocusInWindow();
                    return;
                }
                refresh.run();
            }
        });
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                SwingUtilities.invokeLater(() -> { if (!popup.isFocusOwner()) popup.setVisible(false); });
            }
        });
    }
}
