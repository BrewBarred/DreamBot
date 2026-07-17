package main.menu.components;

import main.menu.Theme;
import main.tools.ChatLog;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.HierarchyEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

/**
 * The Logs tab (v1.31): the in-game chat log and the script's Player Log (widget reads, clue
 * text, notes), searchable and filterable instead of scrolling the tiny in-game box.
 *
 * <p><b>Nothing is saved anywhere.</b> Both logs live in memory and are scrapped when the
 * client closes; the copy and save icon buttons are the ONLY way any of it leaves - and
 * they're yours to press.
 */
public class LogsPanel extends JPanel {

    private final JTextField search = new JTextField();
    private final JComboBox<String> typeFilter = new JComboBox<>(
            new String[]{"All", "GAME", "PUBLIC", "PRIVATE", "TRADE", "CLAN"});
    private final DefaultListModel<ChatLog.Entry> chatModel = new DefaultListModel<>();
    private final DefaultListModel<ChatLog.Entry> noteModel = new DefaultListModel<>();
    private final JList<ChatLog.Entry> chatList = new JList<>(chatModel);
    private final JList<ChatLog.Entry> noteList = new JList<>(noteModel);
    private long lastRevision = -1;
    private final javax.swing.Timer timer = new javax.swing.Timer(1500, e -> maybeRefresh());

    public LogsPanel() {
        setLayout(new BorderLayout(0, 8));
        setOpaque(false);

        // ── header: search + filter + privacy note ──
        search.setToolTipText("Filter both logs by text");
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { refresh(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { refresh(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { refresh(); }
        });
        typeFilter.addActionListener(e -> refresh());

        JLabel note = new JLabel("memory only \u00b7 cleared when the client closes \u00b7 nothing is uploaded");
        note.setForeground(Theme.TEXT_MUTED);
        note.setFont(new Font("Segoe UI", Font.ITALIC, 11));

        JPanel filters = new JPanel(new BorderLayout(6, 0));
        filters.setOpaque(false);
        JLabel searchIcon = new JLabel(UIIcons.search(15, Theme.TEXT_DIM));
        filters.add(searchIcon, BorderLayout.WEST);
        filters.add(search, BorderLayout.CENTER);
        filters.add(typeFilter, BorderLayout.EAST);

        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(false);
        header.add(filters, BorderLayout.NORTH);
        header.add(note, BorderLayout.SOUTH);

        // ── the two logs side by side ──
        style(chatList);
        style(noteList);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildLogSide("Chat log", chatList,
                        () -> ChatLog.clearChat(),
                        () -> selectedOrAllText(chatList, chatModel)),
                buildLogSide("Player log (widget reads & notes)", noteList,
                        () -> ChatLog.clearPlayerLog(),
                        () -> selectedOrAllText(noteList, noteModel)));
        split.setResizeWeight(0.55);
        split.setBorder(null);
        split.setOpaque(false);

        add(header, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) { refresh(); timer.start(); } else timer.stop();
            }
        });
        refresh();
    }

    private void style(JList<ChatLog.Entry> list) {
        list.setBackground(new Color(0x14, 0x14, 0x14));
        list.setForeground(Theme.TEXT);
        list.setFont(new Font("Consolas", Font.PLAIN, 12));
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i,
                    boolean sel, boolean foc) {
                JLabel lab = (JLabel) super.getListCellRendererComponent(l, v, i, sel, foc);
                if (v instanceof ChatLog.Entry) {
                    ChatLog.Entry e = (ChatLog.Entry) v;
                    lab.setText(e.toString());
                    if (!sel) lab.setForeground(colorFor(e.type));
                }
                lab.setBorder(new EmptyBorder(2, 6, 2, 6));
                return lab;
            }
        });
    }

    private static Color colorFor(String type) {
        switch (type) {
            case "PUBLIC":  return new Color(0xE6, 0xE6, 0xE6);
            case "PRIVATE": return new Color(0x7F, 0xD4, 0xFF);
            case "TRADE":   return new Color(0xE0, 0x9A, 0xF0);
            case "CLAN":    return new Color(0x9A, 0xF0, 0xA8);
            case "NOTE":    return Theme.ACCENT;
            default:        return Theme.TEXT_DIM;   // GAME
        }
    }

    /** One log column: title + icon buttons (copy / save / clear) + the list. */
    private JPanel buildLogSide(String title, JList<ChatLog.Entry> list,
                                Runnable clear, java.util.function.Supplier<String> textSupplier) {
        JLabel t = new JLabel(title);
        t.setForeground(Theme.ACCENT);
        t.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JButton btnCopy = iconBtn(UIIcons.copy(15, Theme.TEXT_DIM),
                "Copy (selected lines, or everything shown)");
        btnCopy.addActionListener(e -> {
            String text = textSupplier.get();
            if (text.isEmpty()) return;
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
            btnCopy.setToolTipText("Copied!");
        });

        JButton btnSave = iconBtn(UIIcons.save(15, Theme.TEXT_DIM), "Save shown lines to a .txt");
        btnSave.addActionListener(e -> {
            String text = textSupplier.get();
            if (text.isEmpty()) return;
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("dreamman-log.txt"));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    Files.write(fc.getSelectedFile().toPath(), text.getBytes(StandardCharsets.UTF_8));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Couldn't save: " + ex.getMessage());
                }
            }
        });

        JButton btnClear = iconBtn(null, "Clear this log");
        btnClear.setText("x");
        btnClear.addActionListener(e -> { clear.run(); refresh(); });

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        btns.setOpaque(false);
        btns.add(btnCopy);
        btns.add(btnSave);
        btns.add(btnClear);

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(t, BorderLayout.WEST);
        head.add(btns, BorderLayout.EAST);

        JScrollPane scroll = Theme.thinScrollbars(new JScrollPane(list));
        scroll.setBorder(BorderFactory.createLineBorder(new Color(0x3C, 0x3C, 0x3C)));

        JPanel side = new JPanel(new BorderLayout(0, 4));
        side.setOpaque(false);
        side.add(head, BorderLayout.NORTH);
        side.add(scroll, BorderLayout.CENTER);
        return side;
    }

    private JButton iconBtn(Icon icon, String tip) {
        JButton b = new JButton(icon);
        b.setToolTipText(tip);
        b.setPreferredSize(new Dimension(26, 22));
        b.setFocusPainted(false);
        b.setBackground(new Color(0x2D, 0x2D, 0x2D));
        b.setForeground(Theme.TEXT_DIM);
        b.setBorder(BorderFactory.createLineBorder(new Color(0x46, 0x46, 0x46)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private String selectedOrAllText(JList<ChatLog.Entry> list, DefaultListModel<ChatLog.Entry> model) {
        StringBuilder sb = new StringBuilder();
        List<ChatLog.Entry> sel = list.getSelectedValuesList();
        if (sel != null && !sel.isEmpty()) {
            for (ChatLog.Entry e : sel) sb.append(e).append('\n');
        } else {
            for (int i = 0; i < model.size(); i++) sb.append(model.get(i)).append('\n');
        }
        return sb.toString();
    }

    private void maybeRefresh() {
        if (ChatLog.revision() != lastRevision) refresh();
    }

    private void refresh() {
        lastRevision = ChatLog.revision();
        String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        String type = String.valueOf(typeFilter.getSelectedItem());

        chatModel.clear();
        for (ChatLog.Entry e : ChatLog.chatEntries()) {
            if (!"All".equals(type) && !e.type.equals(type)) continue;
            if (!q.isEmpty() && !e.toString().toLowerCase(Locale.ROOT).contains(q)) continue;
            chatModel.addElement(e);
        }
        noteModel.clear();
        for (ChatLog.Entry e : ChatLog.playerLogEntries()) {
            if (!q.isEmpty() && !e.toString().toLowerCase(Locale.ROOT).contains(q)) continue;
            noteModel.addElement(e);
        }
        // keep tails visible (new lines arrive at the bottom)
        if (!chatModel.isEmpty()) chatList.ensureIndexIsVisible(chatModel.size() - 1);
        if (!noteModel.isEmpty()) noteList.ensureIndexIsVisible(noteModel.size() - 1);
    }
}
