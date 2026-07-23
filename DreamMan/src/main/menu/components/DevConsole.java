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
        // v1.82: per-endpoint diagnostics, replacing the old "Test connection" button that
        // only proved something answered and then printed auth salts at you.
        tabs.addTab("Connection", ConnectionPanel.build());
        return tabs;
    }

    /**
     * v1.82: a user's profile. The identity half is placeholders on purpose - the server has no
     * avatar or bio to serve yet - but the MODERATION half is real UI wired to a server that
     * doesn't implement it, and says so plainly rather than pretending.
     *
     * <p>Each control is a toggle: a banned user's button reads "Unban". State is optimistic and
     * reverts if the call fails, so a fallback build can be exercised end-to-end without lying
     * about what was persisted.
     */
    private static void showUserProfile(Component parent, ServerAccount server,
                                        String username, String rank) {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setOpaque(false);

        // ── identity (placeholder until the server carries profiles) ──
        JPanel id = new JPanel(new BorderLayout(10, 0));
        id.setOpaque(false);
        JLabel avatar = new JLabel("\u25CF", SwingConstants.CENTER);
        avatar.setPreferredSize(new Dimension(64, 64));
        avatar.setForeground(Theme.BORDER_STRONG);
        avatar.setFont(new Font("Segoe UI", Font.PLAIN, 44));
        avatar.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        avatar.setToolTipText("Avatars aren't stored on the server yet");
        id.add(avatar, BorderLayout.WEST);

        JPanel facts = new JPanel(new GridLayout(0, 1, 0, 2));
        facts.setOpaque(false);
        JLabel name = new JLabel(username);
        name.setForeground(Theme.ACCENT);
        name.setFont(new Font("Segoe UI", Font.BOLD, 16));
        facts.add(name);
        facts.add(dim("Rank: " + (rank == null || rank.isBlank() ? "free" : rank)));
        JLabel seenLbl = dim("Joined: \u2014      Last seen: \u2014");
        JLabel marketLbl = dim("On the market: \u2014      Downloads: \u2014");
        JLabel subsLbl = dim("Submissions: \u2014 pending / \u2014 approved / \u2014 denied");
        JLabel cxLbl = dim("Complexity: \u2014 tasks / \u2014 actions / \u2014 loops");
        facts.add(seenLbl);
        facts.add(marketLbl);
        facts.add(subsLbl);
        facts.add(cxLbl);

        // v1.84: real numbers, computed SERVER-side from the stored bundles. A client-reported
        // figure is exactly what someone gaming their limits would falsify, so these are only
        // ever read, never sent.
        new SwingWorker<java.util.Map<String, Object>, Void>() {
            @Override protected java.util.Map<String, Object> doInBackground() throws Exception {
                return server.adminUserProfile(username);
            }
            @Override protected void done() {
                try {
                    java.util.Map<String, Object> p = get();
                    seenLbl.setText("Joined: " + when(p.get("joined"))
                            + "      Last seen: " + when(p.get("lastSeen")));
                    marketLbl.setText("On the market: " + num(p.get("onMarket"))
                            + "      Downloads: " + num(p.get("downloads")));
                    Object so = p.get("submissions");
                    if (so instanceof java.util.Map) {
                        java.util.Map<?, ?> sm = (java.util.Map<?, ?>) so;
                        subsLbl.setText("Submissions: " + num(sm.get("pending")) + " pending / "
                                + num(sm.get("approved")) + " approved / "
                                + num(sm.get("denied")) + " denied");
                    }
                    Object co = p.get("complexity");
                    if (co instanceof java.util.Map) {
                        java.util.Map<?, ?> cm = (java.util.Map<?, ?>) co;
                        long loops = (long) num(cm.get("loops"));
                        cxLbl.setText("Complexity: " + num(cm.get("tasks")) + " tasks / "
                                + num(cm.get("actions")) + " actions / " + loops + " loops");
                        // A signal to look closer, deliberately NOT an automatic judgement:
                        // a long quest routine is legitimately large.
                        if (loops > 300) {
                            cxLbl.setForeground(Theme.AMBER);
                            cxLbl.setToolTipText("Unusually large for a free account \u2014 worth "
                                    + "a look, though a long legitimate routine can reach this too.");
                        }
                    }
                } catch (Exception ex) {
                    cxLbl.setText("(stats unavailable \u2014 needs server 1.7.0+)");
                }
            }
        }.execute();
        id.add(facts, BorderLayout.CENTER);

        // ── moderation ──
        JPanel mod = new JPanel(new GridLayout(0, 1, 0, 6));
        mod.setOpaque(false);
        mod.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Theme.BORDER), " Moderation "));
        mod.add(modToggle(parent, server, username, "mute", "Mute",
                "Stops them commenting on market listings. Their scripts stay up."));
        mod.add(modToggle(parent, server, username, "publishing", "Revoke publishing",
                "Stops them putting anything new on the market. Existing listings stay."));
        mod.add(modToggle(parent, server, username, "ban", "Ban",
                "Blocks sign-in and all server access until they make a new account."));

        root.add(id, BorderLayout.NORTH);
        root.add(mod, BorderLayout.CENTER);
        JOptionPane.showMessageDialog(parent, root, "Profile \u2014 " + username,
                JOptionPane.PLAIN_MESSAGE);
    }

    /** Anyone whose last authenticated request was inside this window counts as online. */
    private static final long ONLINE_WINDOW_MS = 15 * 60_000L;

    private static int rankWeight(String tier) {
        String t = tier == null ? "" : tier.toLowerCase();
        if (t.equals("owner")) return 4;
        if (t.equals("admin")) return 3;
        if (t.equals("vip")) return 2;
        return 1;
    }

    private static String ago(long ms) {
        long mins = Math.max(1, (System.currentTimeMillis() - ms) / 60_000L);
        if (mins < 60) return mins + "m ago";
        long hrs = mins / 60;
        if (hrs < 24) return hrs + "h ago";
        return (hrs / 24) + "d ago";
    }

    private static long num(Object o) {
        try { return (long) Double.parseDouble(String.valueOf(o)); }
        catch (Throwable t) { return 0L; }
    }

    private static String when(Object o) {
        long ms = num(o);
        if (ms <= 0) return "\u2014";
        return new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date(ms));
    }

    private static JLabel dim(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(Theme.TEXT_DIM);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        return l;
    }

    /**
     * One moderation control. Flips label on success; on a 404 it reports that the server can't
     * do this yet, which is the expected answer until the moderation endpoints ship.
     */
    private static JComponent modToggle(Component parent, ServerAccount server, String username,
                                        String action, String label, String help) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        JButton b = new Theme.ThemedButton(label);
        final boolean[] on = {false};
        b.addActionListener(e -> {
            boolean want = !on[0];
            try {
                server.probe("POST", "/admin/users/" + username + "/" + action
                        + (want ? "" : "/undo"));
                on[0] = want;
                b.setText(want ? undoLabel(label) : label);
            } catch (Throwable ex) {
                String m = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                boolean missing = m.contains("404") || m.toLowerCase().contains("not found");
                JOptionPane.showMessageDialog(parent,
                        missing
                          ? "<html>Your server doesn't support this yet.<br><br>"
                            + "The moderation endpoints land in the next server build \u2014 the "
                            + "button is here so the flow<br>can be checked in the meantime. "
                            + "Nothing was changed.</html>"
                          : "Couldn't apply that: " + m,
                        missing ? "Not available yet" : "Failed",
                        missing ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
            }
        });
        row.add(b, BorderLayout.WEST);
        JLabel h = dim(help);
        row.add(h, BorderLayout.CENTER);
        return row;
    }

    private static String undoLabel(String label) {
        if (label.startsWith("Ban")) return "Unban";
        if (label.startsWith("Mute")) return "Unmute";
        return "Restore publishing";
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
        // v1.84: sort + filter. "Online" is anyone seen in the last 15 minutes - last_seen is
        // written on every authenticated request (server 1.7.0), so it's activity, not a socket.
        JComboBox<String> sortBy = new JComboBox<>(new String[]{
                "Recently seen", "A \u2192 Z", "Rank"});
        JComboBox<String> showOnly = new JComboBox<>(new String[]{
                "Everyone", "Online now", "Offline", "Admins + owner", "VIP", "Free",
                "Banned", "Muted"});
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
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        filters.setOpaque(false);
        JLabel sortLbl = new JLabel("Sort:");
        sortLbl.setForeground(Theme.TEXT_DIM);
        JLabel showLbl = new JLabel("Show:");
        showLbl.setForeground(Theme.TEXT_DIM);
        filters.add(sortLbl);
        filters.add(sortBy);
        filters.add(showLbl);
        filters.add(showOnly);
        header.add(filters, BorderLayout.CENTER);
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
        // v1.83: "Test connection" removed - the Connection tab supersedes it entirely.
        JPanel westActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        westActions.setOpaque(false);
        westActions.add(note);

        // v1.83 FIX: the rank selector was created in v1.81 but never added to any visible
        // container, so Apply always read its default and silently demoted people to free.
        // It lives next to the button it drives.
        JPanel rankGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rankGroup.setOpaque(false);
        JLabel rankLbl = new JLabel("Set rank:");
        rankLbl.setForeground(Theme.TEXT_DIM);
        rankGroup.add(rankLbl);
        rankGroup.add(rankBox);
        rankGroup.add(btnPromote);

        JPanel actions = new JPanel(new BorderLayout(8, 0));
        actions.setOpaque(false);
        actions.add(westActions, BorderLayout.WEST);
        actions.add(rankGroup, BorderLayout.EAST);

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
                    // v1.84: filter, then sort, client-side. The server already returns
                    // everything these need (lastSeen, muted, banned), so no extra round trips.
                    List<Map<String, Object>> view = new java.util.ArrayList<>();
                    String only = str(showOnly.getSelectedItem());
                    long online = System.currentTimeMillis() - ONLINE_WINDOW_MS;
                    for (Map<String, Object> u : fRows) {
                        String tier = str(u.get("tier")).toLowerCase();
                        long seen = num(u.get("lastSeen"));
                        boolean isOn = seen > online;
                        boolean keep;
                        switch (only) {
                            case "Online now":     keep = isOn; break;
                            case "Offline":        keep = !isOn; break;
                            case "Admins + owner": keep = tier.equals("admin") || tier.equals("owner"); break;
                            case "VIP":            keep = tier.equals("vip"); break;
                            case "Free":           keep = tier.isEmpty() || tier.equals("free"); break;
                            case "Banned":         keep = Boolean.TRUE.equals(u.get("banned")); break;
                            case "Muted":          keep = Boolean.TRUE.equals(u.get("muted")); break;
                            default:               keep = true;
                        }
                        if (keep) view.add(u);
                    }
                    String sort = str(sortBy.getSelectedItem());
                    view.sort((x, y) -> {
                        if ("A \u2192 Z".equals(sort))
                            return str(x.get("username")).compareToIgnoreCase(str(y.get("username")));
                        if ("Rank".equals(sort))
                            return rankWeight(str(y.get("tier"))) - rankWeight(str(x.get("tier")));
                        return Long.compare(num(y.get("lastSeen")), num(x.get("lastSeen")));
                    });

                    for (Map<String, Object> u : view) {
                        String name = str(u.get("username"));
                        String tier = str(u.get("tier"));
                        long seen = num(u.get("lastSeen"));
                        StringBuilder flags = new StringBuilder(
                                seen > online ? "online" : (seen > 0 ? ago(seen) : "\u2014"));
                        if (Boolean.TRUE.equals(u.get("banned"))) flags.append("  \u00b7 BANNED");
                        if (Boolean.TRUE.equals(u.get("muted"))) flags.append("  \u00b7 muted");
                        model.addRow(new Object[]{name, tier.isEmpty() ? "free" : tier,
                                flags.toString()});
                    }
                    status.setText(view.size() + " of " + fRows.size() + " user(s).");
                });
            }, "DreamMan-AdminSearch");
            t.setDaemon(true);
            t.start();
        };

        btnSearch.addActionListener(e -> doSearch.run());
        sortBy.addActionListener(e -> doSearch.run());
        showOnly.addActionListener(e -> doSearch.run());
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
        // v1.82: double-click a user to open their profile.
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent me) {
                if (me.getClickCount() < 2) return;
                int r = table.getSelectedRow();
                if (r < 0) return;
                showUserProfile(root, server, str(model.getValueAt(r, 0)),
                        str(model.getValueAt(r, 1)));
            }
        });

        // Selecting a user shows THEIR current rank, so pressing Apply without touching the
        // dropdown is a no-op instead of a demotion.
        table.getSelectionModel().addListSelectionListener(ev -> {
            int r = table.getSelectedRow();
            if (r < 0) return;
            String cur = str(model.getValueAt(r, 1));
            if (cur.isBlank()) cur = Tier.FREE;
            rankBox.setSelectedItem(Tier.OWNER.equalsIgnoreCase(cur) ? Tier.ADMIN : cur);
            rankBox.setEnabled(!Tier.OWNER.equalsIgnoreCase(cur));
        });

        doSearch.run();

        root.add(header, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(actions, BorderLayout.SOUTH);
        SwingUtilities.invokeLater(search::requestFocusInWindow);
        return root;
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }
}
