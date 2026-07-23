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
    /**
     * v1.87: which chat channels are showing - the in-game chatbox filter strip, rebuilt here.
     * Every channel starts ON; click a button to hide that channel, click again to bring it
     * back, and All flips everything back on. (These filter the VIEW - capture is always
     * everything, so flipping a channel back on reveals what it said while hidden.)
     */
    private final java.util.Set<String> shownChannels = new java.util.LinkedHashSet<>(
            java.util.Arrays.asList(CHANNELS));
    private static final String[] CHANNELS =
            {"GAME", "PUBLIC", "PRIVATE", "CHANNEL", "CLAN", "GROUP", "TRADE", "OTHER"};
    private final java.util.List<ChannelButton> channelButtons = new java.util.ArrayList<>();
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
        JLabel note = new JLabel("memory only \u00b7 cleared when the client closes \u00b7 nothing is uploaded"
                + "  \u00b7  double-click a line to copy its text");
        note.setForeground(Theme.TEXT_MUTED);
        note.setFont(new Font("Segoe UI", Font.ITALIC, 11));

        JPanel filters = new JPanel(new BorderLayout(6, 0));
        filters.setOpaque(false);
        JLabel searchIcon = new JLabel(UIIcons.search(15, Theme.TEXT_DIM));
        filters.add(searchIcon, BorderLayout.WEST);
        filters.add(search, BorderLayout.CENTER);

        // v1.87: the chat-filter strip, shaped like the in-game chatbox bar: an All button,
        // then one two-line button per channel showing its name over a green On / red Off.
        JPanel filterBar = new JPanel(new GridLayout(1, 0, 3, 0));
        filterBar.setOpaque(false);
        JButton all = new JButton("All");
        all.setFont(new Font("Segoe UI", Font.BOLD, 11));
        all.setForeground(Theme.TEXT);
        all.setBackground(new Color(0x2D, 0x2D, 0x2D));
        all.setFocusPainted(false);
        all.setBorder(BorderFactory.createLineBorder(new Color(0x46, 0x46, 0x46)));
        all.setToolTipText("Show every channel again");
        all.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        all.addActionListener(e -> {
            shownChannels.clear();
            shownChannels.addAll(java.util.Arrays.asList(CHANNELS));
            for (ChannelButton b : channelButtons) b.repaint();
            refresh();
        });
        filterBar.add(all);
        for (String ch : CHANNELS) {
            ChannelButton b = new ChannelButton(ch);
            channelButtons.add(b);
            filterBar.add(b);
        }

        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(false);
        header.add(filters, BorderLayout.NORTH);
        header.add(filterBar, BorderLayout.CENTER);
        header.add(note, BorderLayout.SOUTH);

        // ── the two logs side by side ──
        style(chatList);
        style(noteList);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildLogSide("Chat log", chatList,
                        () -> ChatLog.clearChat(),
                        () -> selectedOrAllText(chatList, chatModel), null),
                buildLogSide("Player log (dialogue, widget reads & notes)", noteList,
                        () -> ChatLog.clearPlayerLog(),
                        () -> selectedOrAllText(noteList, noteModel), snapshotButton()));
        installCopyOnDoubleClick(chatList);
        installCopyOnDoubleClick(noteList);
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
            case "PUBLIC":   return new Color(0xE6, 0xE6, 0xE6);
            case "PRIVATE":  return new Color(0x7F, 0xD4, 0xFF);
            case "TRADE":    return new Color(0xE0, 0x9A, 0xF0);
            case "CLAN":     return new Color(0x9A, 0xF0, 0xA8);
            case "CHANNEL":  return new Color(0xC8, 0xE0, 0x7F);   // v1.87: friends channel
            case "GROUP":    return new Color(0x7F, 0xE0, 0xC8);   // v1.87: group ironman
            case "NOTE":     return Theme.ACCENT;
            case "DIALOGUE": return new Color(0x9F, 0xC4, 0xF0);   // v1.87
            case "WIDGET":   return new Color(0xF0, 0xC8, 0x7F);   // v1.87
            case "READ":     return new Color(0xF0, 0xC8, 0x7F);   // v1.87
            case "OTHER":    return Theme.TEXT_MUTED;              // v1.87
            default:         return Theme.TEXT_DIM;   // GAME
        }
    }

    /** One log column: title + icon buttons (copy / save / clear) + the list. */
    private JPanel buildLogSide(String title, JList<ChatLog.Entry> list,
                                Runnable clear, java.util.function.Supplier<String> textSupplier,
                                JComponent extraButton) {
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
        if (extraButton != null) btns.add(extraButton);   // v1.87: the widget snapshot
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

    /**
     * v1.87: one channel toggle, drawn like the in-game chatbox buttons - the channel name over
     * a green On / red Off. Click flips just that channel's visibility.
     */
    private final class ChannelButton extends JComponent {
        private final String channel;

        ChannelButton(String channel) {
            this.channel = channel;
            setPreferredSize(new Dimension(58, 30));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Show or hide " + pretty() + " messages");
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mousePressed(java.awt.event.MouseEvent e) {
                    if (!shownChannels.remove(channel)) shownChannels.add(channel);
                    repaint();
                    refresh();
                }
            });
        }

        private String pretty() {
            return channel.charAt(0) + channel.substring(1).toLowerCase(Locale.ROOT);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean on = shownChannels.contains(channel);
            g2.setColor(on ? new Color(0x33, 0x30, 0x28) : new Color(0x22, 0x22, 0x22));
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
            g2.setColor(on ? Theme.BORDER : new Color(0x3A, 0x3A, 0x3A));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g2.setColor(on ? Theme.TEXT : Theme.TEXT_MUTED);
            FontMetrics fm = g2.getFontMetrics();
            String name = pretty();
            g2.drawString(name, (getWidth() - fm.stringWidth(name)) / 2, 13);
            String state = on ? "On" : "Off";
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 9));
            fm = g2.getFontMetrics();
            g2.setColor(on ? new Color(0x58, 0xC8, 0x50) : new Color(0xD0, 0x5A, 0x46));
            g2.drawString(state, (getWidth() - fm.stringWidth(state)) / 2, 25);
            g2.dispose();
        }
    }

    /** v1.87: double-click any line to copy its PAYLOAD (options list, widget text, the line). */
    private void installCopyOnDoubleClick(JList<ChatLog.Entry> list) {
        list.setToolTipText("Double-click a line to copy its text (a dialogue line copies its "
                + "options, a widget read copies the raw text)");
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() != 2) return;
                int i = list.locationToIndex(e.getPoint());
                if (i < 0) return;
                ChatLog.Entry entry = list.getModel().getElementAt(i);
                if (entry == null) return;
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(entry.payload), null);
                flash(list, "Copied: " + (entry.payload.length() > 40
                        ? entry.payload.substring(0, 40) + "\u2026" : entry.payload));
            }
        });
    }

    /**
     * v1.87: the Player Log's "read the screen now" button - every visible widget holding text
     * lands as a WIDGET entry with the ids it lives at. Read a clue, press this, and both the
     * clue text and its widget address are on record for building the solver task.
     */
    private JComponent snapshotButton() {
        JButton b = new JButton("Snapshot widgets");
        b.setToolTipText("Capture every visible widget's text (with its widget ids) into the "
                + "Player Log - open a clue scroll or interface first, then press this");
        b.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        b.setForeground(Theme.TEXT_DIM);
        b.setBackground(new Color(0x2D, 0x2D, 0x2D));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x46, 0x46, 0x46)),
                new EmptyBorder(2, 8, 2, 8)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> {
            b.setEnabled(false);
            Thread t = new Thread(() -> {   // widget walking is client work - keep it off the EDT
                int n = 0;
                try {
                    for (String[] pair : main.tools.WidgetFinder.visibleTexts()) {
                        ChatLog.widget(pair[0], pair[1]);
                        n++;
                    }
                } catch (Throwable ignored) {}
                final int captured = n;
                SwingUtilities.invokeLater(() -> {
                    b.setEnabled(true);
                    flash(b, captured == 0 ? "Nothing readable on screen"
                            : "Captured " + captured + " widget text(s)");
                });
            }, "DreamMan-WidgetSnapshot");
            t.setDaemon(true);
            t.start();
        });
        return b;
    }

    /** A small self-dismissing confirmation bubble by the anchor (copy / snapshot feedback). */
    private static void flash(Component anchor, String msg) {
        try {
            JLabel l = new JLabel(msg);
            l.setOpaque(true);
            l.setBackground(new Color(0x2E, 0x2A, 0x1C));
            l.setForeground(Theme.ACCENT);
            l.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            l.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(4, 8, 4, 8)));
            Point at = anchor.getLocationOnScreen();
            JWindow w = new JWindow(SwingUtilities.getWindowAncestor(anchor));
            w.add(l);
            w.pack();
            w.setLocation(at.x + 12, at.y + 12);
            w.setVisible(true);
            javax.swing.Timer t = new javax.swing.Timer(1400, e -> w.dispose());
            t.setRepeats(false);
            t.start();
        } catch (Throwable ignored) {
            // headless or the anchor left the screen - the copy still happened
        }
    }

    private void maybeRefresh() {
        if (ChatLog.revision() != lastRevision) refresh();
    }

    private void refresh() {
        lastRevision = ChatLog.revision();
        String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);

        chatModel.clear();
        for (ChatLog.Entry e : ChatLog.chatEntries()) {
            // v1.87: unknown channel names ride with OTHER, so nothing can become unviewable
            String ch = shownChannels.contains(e.type) ? e.type
                    : java.util.Arrays.asList(CHANNELS).contains(e.type) ? e.type : "OTHER";
            if (!shownChannels.contains(ch)) continue;
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
