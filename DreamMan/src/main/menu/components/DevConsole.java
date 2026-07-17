package main.menu.components;

import main.market.ServerAccount;
import main.market.Tier;
import main.menu.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * The owner dev console (v1.32) - the first slice: search every registered user and promote
 * them to admin.
 *
 * <p>Opens only for {@link Tier#isOwner() owners}. Search hits {@code GET /admin/users?q=}; the
 * promote button hits {@code POST /admin/users/<name>/role}. Both are owner-gated on the server,
 * which is what actually enforces access - this dialog just refuses to open for non-owners and
 * keeps the surface small.
 *
 * <p>Roadmap (next patch, per the plan): view each user's published scripts, restrict a user
 * from publishing, ban an account, and demote admins back to normal users. The layout leaves an
 * actions column ready for those; this release ships search + promote.
 */
public final class DevConsole {

    private DevConsole() {}

    /**
     * v1.32b: the console is an embeddable PANEL (added as a hidden owner-only tab), not a
     * pop-up dialog - a real tab is what the developer tools want, and a right-click context
     * menu was the wrong home for it.
     */
    public static JComponent buildPanel() {
        String url = ServerAccount.session().baseUrl;
        ServerAccount server = new ServerAccount(url);

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        root.setBackground(Theme.SURFACE_1);

        // ── search row ──
        JTextField search = new JTextField();
        search.setToolTipText("Search all registered users by username or email");
        JButton btnSearch = new Theme.ThemedButton("Search");
        JLabel status = new JLabel(" ");
        status.setForeground(Theme.TEXT_DIM);
        status.setFont(new Font("Segoe UI", Font.ITALIC, 11));

        JPanel searchRow = new JPanel(new BorderLayout(6, 0));
        searchRow.setOpaque(false);
        JLabel find = new JLabel("Find user");
        find.setForeground(Theme.TEXT_DIM);
        searchRow.add(find, BorderLayout.WEST);
        searchRow.add(search, BorderLayout.CENTER);
        searchRow.add(btnSearch, BorderLayout.EAST);

        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(false);
        header.add(searchRow, BorderLayout.NORTH);
        header.add(status, BorderLayout.SOUTH);

        // ── results table ──
        String[] cols = {"Username", "Tier", "Action"};
        javax.swing.table.DefaultTableModel model =
                new javax.swing.table.DefaultTableModel(cols, 0) {
                    @Override public boolean isCellEditable(int r, int c) { return false; }
                };
        JTable table = new JTable(model);
        table.setRowHeight(28);
        table.setBackground(new Color(0x1A, 0x1A, 0x1A));
        table.setForeground(Theme.TEXT);
        table.setSelectionBackground(new Color(0x3A, 0x33, 0x18));
        table.getTableHeader().setReorderingAllowed(false);

        JScrollPane scroll = Theme.thinScrollbars(new JScrollPane(table));
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));

        // ── promote controls (act on the selected row) ──
        JButton btnPromote = new Theme.ThemedButton("Promote to admin");
        btnPromote.putClientProperty("fillColor", new Color(30, 70, 40));
        btnPromote.setEnabled(false);
        JLabel note = new JLabel("Select a user, then promote. Owner is set in the database only.");
        note.setForeground(Theme.TEXT_MUTED);
        note.setFont(new Font("Segoe UI", Font.ITALIC, 11));

        // v1.32b: the full connection diagnostic moved here from the Status tab (Status now has
        // a simple live green/red dot). Owners get the classified report on demand.
        JButton btnTest = new Theme.ThemedButton("Test connection");
        btnTest.setToolTipText("Run the full connection diagnostic against the configured server");
        btnTest.addActionListener(e -> {
            btnTest.setEnabled(false);
            btnTest.setText("Testing\u2026");
            final String target = url == null || url.isEmpty()
                    ? "https://ghost-server.nz/ghost-bot" : url;
            Thread t = new Thread(() -> {
                main.tools.ConnectionDiagnostics.Result r =
                        main.tools.ConnectionDiagnostics.probe(target);
                SwingUtilities.invokeLater(() -> {
                    btnTest.setEnabled(true);
                    btnTest.setText("Test connection");
                    JTextArea area = new JTextArea(r.detail);
                    area.setEditable(false);
                    area.setLineWrap(true);
                    area.setWrapStyleWord(true);
                    area.setColumns(46);
                    area.setRows(Math.min(16, Math.max(6, r.detail.length() / 46 + 3)));
                    area.setCaretPosition(0);
                    area.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                    JOptionPane.showMessageDialog(root, new JScrollPane(area),
                            r.headline + "  (" + target + ")",
                            r.ok() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
                });
            }, "DreamMan-ConnTest");
            t.setDaemon(true);
            t.start();
        });

        JPanel westActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        westActions.setOpaque(false);
        westActions.add(btnTest);
        westActions.add(note);

        JPanel actions = new JPanel(new BorderLayout(8, 0));
        actions.setOpaque(false);
        actions.add(westActions, BorderLayout.WEST);
        actions.add(btnPromote, BorderLayout.EAST);

        table.getSelectionModel().addListSelectionListener(e ->
                btnPromote.setEnabled(table.getSelectedRow() >= 0));

        // ── behaviour ──
        Runnable doSearch = () -> {
            status.setText("Searching\u2026");
            btnSearch.setEnabled(false);
            String q = search.getText();
            Thread t = new Thread(() -> {
                String err = null;
                List<Map<String, Object>> rows = null;
                try {
                    rows = server.adminSearchUsers(q);
                } catch (Throwable ex) {
                    err = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                }
                final String fErr = err;
                final List<Map<String, Object>> fRows = rows;
                SwingUtilities.invokeLater(() -> {
                    btnSearch.setEnabled(true);
                    model.setRowCount(0);
                    if (fErr != null) {
                        status.setText("Error: " + fErr);
                        return;
                    }
                    if (fRows == null || fRows.isEmpty()) {
                        status.setText("No users matched.");
                        return;
                    }
                    for (Map<String, Object> u : fRows) {
                        String name = str(u.get("username"));
                        String tier = str(u.get("tier"));
                        model.addRow(new Object[]{name, tier.isEmpty() ? "free" : tier,
                                "admin".equalsIgnoreCase(tier) || "owner".equalsIgnoreCase(tier)
                                        ? "\u2014" : "promotable"});
                    }
                    status.setText(fRows.size() + " user(s).");
                });
            }, "DreamMan-AdminSearch");
            t.setDaemon(true);
            t.start();
        };

        btnSearch.addActionListener(e -> doSearch.run());
        search.addActionListener(e -> doSearch.run());

        btnPromote.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            String name = str(model.getValueAt(row, 0));
            String tier = str(model.getValueAt(row, 1));
            if ("admin".equalsIgnoreCase(tier) || "owner".equalsIgnoreCase(tier)) {
                status.setText(name + " is already " + tier + ".");
                return;
            }
            if (JOptionPane.showConfirmDialog(root,
                    "Promote \"" + name + "\" to admin?\nThey'll be able to manage the marketplace.",
                    "Promote to admin", JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION) return;

            btnPromote.setEnabled(false);
            status.setText("Promoting " + name + "\u2026");
            Thread t = new Thread(() -> {
                String err = null;
                try {
                    server.adminSetRole(name, Tier.ADMIN);
                } catch (Throwable ex) {
                    err = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                }
                final String fErr = err;
                SwingUtilities.invokeLater(() -> {
                    if (fErr != null) {
                        status.setText("Couldn't promote: " + fErr);
                        btnPromote.setEnabled(true);
                    } else {
                        model.setValueAt("admin", row, 1);
                        model.setValueAt("\u2014", row, 2);
                        status.setText(name + " is now an admin.");
                    }
                });
            }, "DreamMan-AdminPromote");
            t.setDaemon(true);
            t.start();
        });

        root.add(header, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(actions, BorderLayout.SOUTH);
        SwingUtilities.invokeLater(search::requestFocusInWindow);
        return root;
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }
}
