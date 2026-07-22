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
     *
     * <p>v1.66: the console is now itself tabbed. "Users" is the original search/promote panel;
     * "Script Management" is the moderation queue + defaults library. The suppliers hand that tab
     * the menu's own library and market lists without exposing those fields to this package.
     */
    public static JComponent buildPanel(
            java.util.function.Supplier<java.util.List<main.menu.DreamBotMenu.Task>> library,
            java.util.function.Supplier<java.util.List<main.market.ScriptListing>> market) {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Users", buildUsersPanel());
        // Owners and admins both moderate; only owners can promote users, which is why the Users
        // tab keeps its own owner-gated server calls.
        tabs.addTab("Script Management", ScriptManagementPanel.build(library, market));
        return tabs;
    }

    private static final String RANK_HELP =
            "<html><b>free</b> \u2014 browse, download, publish within the free limits<br>"
            + "<b>vip</b> \u2014 higher publish limits and access to VIP-only listings<br>"
            + "<b>admin</b> \u2014 all of VIP, plus the Script Management tab: approve or deny "
            + "submissions,<br>curate default tasks, and flip the moderation valve<br>"
            + "<b>owner</b> \u2014 everything, plus user ranks. Set in the database only.</html>";

    private static String describeRank(String rank) {
        if (Tier.ADMIN.equalsIgnoreCase(rank))
            return "Admins can approve or deny submissions, curate default tasks, and turn "
                 + "publish moderation on or off.";
        if (Tier.VIP.equalsIgnoreCase(rank))
            return "VIP raises their publish limits and unlocks VIP-only listings.";
        return "Free is the default: browse, download, and publish within the free limits.";
    }

    /** The original v1.32b users panel. */
    private static JComponent buildUsersPanel() {
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
        // v1.81: a rank SELECTOR, not a one-way promote button. The old control could only ever
        // make admins - there was no way to grant VIP, and no way to demote anyone at all.
        JComboBox<String> rankBox = new JComboBox<>(new String[]{
                Tier.FREE, Tier.VIP, Tier.ADMIN});
        rankBox.setToolTipText(RANK_HELP);
        JButton btnPromote = new Theme.ThemedButton("Apply rank");
        btnPromote.putClientProperty("fillColor", new Color(30, 70, 40));
        btnPromote.setEnabled(false);
        JLabel note = new JLabel("<html>Pick a user, choose a rank, Apply. "
                + "<b>Owner</b> is set in the database only.</html>");
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
            if (row < 0) {
                JOptionPane.showMessageDialog(root, "Select a user in the list first.",
                        "No user selected", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String name = str(model.getValueAt(row, 0));
            String tier = str(model.getValueAt(row, 1));
            String want = str(rankBox.getSelectedItem());

            // v1.81: say WHY nothing will happen, in a dialog. Previously an owner-targeted
            // promote wrote a line into a small status label and looked like a dead button.
            if (Tier.OWNER.equalsIgnoreCase(tier)) {
                JOptionPane.showMessageDialog(root,
                        "<html><b>" + name + "</b> is the owner.<br><br>The owner's rank can't be "
                        + "changed from the client \u2014 it's set directly in the database, so "
                        + "the top-level<br>account can never be demoted or escalated into by "
                        + "anything running on a user's machine.</html>",
                        "Owner can't be changed", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (want.equalsIgnoreCase(tier)) {
                JOptionPane.showMessageDialog(root,
                        name + " is already " + want + ".", "No change",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            boolean demoting = Tier.ADMIN.equalsIgnoreCase(tier) && !Tier.ADMIN.equalsIgnoreCase(want);
            if (JOptionPane.showConfirmDialog(root,
                    "<html>" + (demoting ? "<b>Demote</b> " : "Set ") + "\"" + name + "\" "
                    + (demoting ? "from admin to " : "to ") + "<b>" + want + "</b>?<br><br>"
                    + describeRank(want) + "</html>",
                    demoting ? "Demote user" : "Change rank",
                    JOptionPane.YES_NO_OPTION,
                    demoting ? JOptionPane.WARNING_MESSAGE : JOptionPane.QUESTION_MESSAGE)
                    != JOptionPane.YES_OPTION) return;

            btnPromote.setEnabled(false);
            status.setText("Updating " + name + "\u2026");
            Thread t = new Thread(() -> {
                String err = null;
                try {
                    server.adminSetRole(name, want);
                } catch (Throwable ex) {
                    err = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                }
                final String fErr = err;
                SwingUtilities.invokeLater(() -> {
                    btnPromote.setEnabled(true);
                    if (fErr != null) {
                        status.setText("Couldn't change rank: " + fErr);
                    } else {
                        model.setValueAt(want, row, 1);
                        status.setText(name + " is now " + want + ".");
                    }
                });
            }, "DreamMan-AdminSetRank");
            t.setDaemon(true);
            t.start();
        });

        // v1.81: load on open. The list used to start empty and only populate once you pressed
        // Search - with an empty query, which is exactly what the panel could have done itself.
        doSearch.run();

        root.add(header, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(actions, BorderLayout.SOUTH);
        SwingUtilities.invokeLater(search::requestFocusInWindow);
        return root;
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }
}
