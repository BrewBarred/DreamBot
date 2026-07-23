package main.menu.components;

import main.market.ServerAccount;
import main.menu.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * v1.82: the Dev Console's Connection tab.
 *
 * <p>Replaces the old "Test connection" button, which proved only that <em>something</em> answered
 * and then printed the first 200 characters of an auth-salt payload at you. That told you the
 * transport worked; it never told you which part of the API was broken, which is the question you
 * actually have when the market misbehaves.
 *
 * <p>Each endpoint is probed individually and reported with its status, round-trip time and a
 * one-line verdict. Endpoints that need a newer server are listed anyway and report
 * <em>"not on this server yet"</em> rather than being hidden, so the panel doubles as a view of
 * what your deployment currently supports.
 */
public final class ConnectionPanel {

    private ConnectionPanel() {}

    /** name, method, path, whether a failure is expected on older servers, what it's for. */
    private static final String[][] ENDPOINTS = {
        {"Health",             "GET", "/health",                        "0", "Server up + version"},
        {"Settings",           "GET", "/settings",                      "1", "Moderation valve + defaults version"},
        {"Defaults version",   "GET", "/defaults/version",              "1", "The cheap poll clients use"},
        {"Defaults",           "GET", "/defaults",                      "1", "Admin-curated default tasks"},
        {"Market listings",    "GET", "/scripts",                       "0", "The market grid"},
        {"My submissions",     "GET", "/me/submissions",                "1", "Your moderation outcomes"},
        {"Pending queue",      "GET", "/admin/submissions?status=pending", "1", "Admin: awaiting approval"},
        {"Archive",            "GET", "/admin/submissions?status=archived","1", "Admin: denied submissions"},
        {"Defaults (admin)",   "GET", "/admin/defaults",                "1", "Admin: defaults with provenance"},
    };

    public static JComponent build() {
        final ServerAccount server = new ServerAccount(ServerAccount.session().baseUrl);

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        root.setBackground(Theme.SURFACE_1);

        JLabel where = new JLabel("Server: " + shortUrl(ServerAccount.session().baseUrl)
                + "   \u00b7   signed in as "
                + (ServerAccount.isLoggedIn() ? ServerAccount.username() : "nobody"));
        where.setForeground(Theme.TEXT_DIM);
        where.setFont(new Font("Consolas", Font.PLAIN, 11));

        JLabel status = new JLabel(" ");
        status.setForeground(Theme.TEXT_DIM);
        status.setFont(new Font("Segoe UI", Font.ITALIC, 11));

        // v1.83: the last response body per row. "OK" alone doesn't prove the payload is
        // right - a 200 carrying an empty array looks identical to a healthy one.
        final String[] bodies = new String[ENDPOINTS.length];

        // v1.89b: no per-row Test column. A button in every row duplicated "Test selected"
        // exactly, cost a column of width, and needed a cell editor + renderer to fake a
        // button that a JTable never really wanted to hold. Select a row and press the button
        // below - or just double-click the row.
        String[] cols = {"Endpoint", "Path", "Result", "Time", "What it's for"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (String[] e : ENDPOINTS)
            model.addRow(new Object[]{e[0], e[2], "\u2014", "\u2014", e[4]});

        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.setBackground(new Color(0x1A, 0x1A, 0x1A));
        table.setForeground(Theme.TEXT);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(1).setPreferredWidth(210);
        table.getColumnModel().getColumn(4).setPreferredWidth(240);
        JScrollPane scroll = Theme.thinScrollbars(new JScrollPane(table));
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));

        JButton testAll = new Theme.ThemedButton("Test all");
        JButton testOne = new Theme.ThemedButton("Test selected");

        // ── v1.89b: the response is ALWAYS on screen ────────────────────────────────
        // "View response..." hid the single most useful thing this panel produces behind a
        // modal you had to remember to open. Now the body of whatever row you select is just
        // there, underneath the table, and "Test all" fills it with a full scrollable sheet -
        // every endpoint, its timing and its reply in one pass you can read top to bottom.
        JTextArea out = new JTextArea();
        out.setEditable(false);
        out.setLineWrap(true);
        out.setWrapStyleWord(true);
        out.setFont(new Font("Consolas", Font.PLAIN, 12));
        out.setBackground(new Color(0x14, 0x14, 0x14));
        out.setForeground(Theme.TEXT_DIM);
        out.setBorder(new EmptyBorder(6, 8, 6, 8));
        out.setText("Select an endpoint to see its reply, or press \u201cTest all\u201d for the "
                + "full sheet.");
        JScrollPane outScroll = Theme.thinScrollbars(new JScrollPane(out));
        outScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        outScroll.setPreferredSize(new Dimension(10, 190));

        // selecting a row shows that row's last reply, with no click required
        table.getSelectionModel().addListSelectionListener(ev -> {
            if (ev.getValueIsAdjusting()) return;
            int r = table.getSelectedRow();
            if (r < 0) return;
            int i2 = table.convertRowIndexToModel(r);
            String body = bodies[i2];
            out.setText(ENDPOINTS[i2][0] + "  \u00b7  " + ENDPOINTS[i2][2] + "\n"
                    + "\u2500".repeat(60) + "\n"
                    + (body == null ? "Not tested yet." : body));
            out.setCaretPosition(0);
        });

        // double-click a row to test just that one - the per-row button's job, without the column
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent ev) {
                if (ev.getClickCount() != 2) return;
                int r = table.getSelectedRow();
                if (r >= 0) probe(server, model, table.convertRowIndexToModel(r), status, bodies);
            }
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);
        actions.add(testAll);
        actions.add(testOne);
        JLabel hint = new JLabel("double-click a row to test just that one");
        hint.setForeground(Theme.TEXT_MUTED);
        hint.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        actions.add(hint);

        JPanel header = new JPanel(new GridLayout(0, 1, 0, 5));
        header.setOpaque(false);
        header.add(where);
        header.add(status);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scroll, outScroll);
        split.setResizeWeight(0.68);   // the table gets the room, the output keeps a floor
        split.setBorder(null);
        split.setOpaque(false);

        root.add(header, BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);
        root.add(actions, BorderLayout.SOUTH);

        testAll.addActionListener(e -> {
            status.setText("Testing " + ENDPOINTS.length + " endpoints\u2026");
            for (int i = 0; i < ENDPOINTS.length; i++) probe(server, model, i, status, bodies);
            // v1.89b: assemble the sheet once every probe has had a chance to land. Each probe
            // is its own SwingWorker, so this waits rather than racing them.
            javax.swing.Timer sheet = new javax.swing.Timer(1200, a -> {
                StringBuilder sb = new StringBuilder();
                sb.append("FULL ENDPOINT SHEET  \u00b7  ")
                  .append(new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()))
                  .append('\n').append("\u2550".repeat(60)).append("\n\n");
                for (int i = 0; i < ENDPOINTS.length; i++) {
                    sb.append(ENDPOINTS[i][0]).append("   ").append(ENDPOINTS[i][1])
                      .append(' ').append(ENDPOINTS[i][2]).append('\n')
                      .append("   result : ").append(model.getValueAt(i, 2)).append('\n')
                      .append("   time   : ").append(model.getValueAt(i, 3)).append('\n')
                      .append("   for    : ").append(ENDPOINTS[i][4]).append('\n')
                      .append("   reply  : ")
                      .append(bodies[i] == null ? "(nothing recorded)" : bodies[i]).append('\n')
                      .append("\u2500".repeat(60)).append('\n');
                }
                out.setText(sb.toString());
                out.setCaretPosition(0);
            });
            sheet.setRepeats(false);
            sheet.start();
        });
        testOne.addActionListener(e -> {
            int r = table.getSelectedRow();
            if (r < 0) { status.setText("Select an endpoint first."); return; }
            probe(server, model, table.convertRowIndexToModel(r), status, bodies);
        });

        // Probe on open: the panel exists to answer "what's wrong right now", so make it answer
        // that without a click.
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < ENDPOINTS.length; i++) probe(server, model, i, status, bodies);
        });
        return root;
    }

    private static void probe(ServerAccount server, DefaultTableModel model, int rowIdx,
                              JLabel status, String[] bodies) {
        final String[] e = ENDPOINTS[rowIdx];
        model.setValueAt("testing\u2026", rowIdx, 2);
        new SwingWorker<Object[], Void>() {
            @Override protected Object[] doInBackground() {
                long t0 = System.currentTimeMillis();
                try {
                    String body = server.probe(e[1], e[2]);
                    long ms = System.currentTimeMillis() - t0;
                    bodies[rowIdx] = body;
                    return new Object[]{"OK", ms, verdict(e[0], body)};
                } catch (Throwable ex) {
                    long ms = System.currentTimeMillis() - t0;
                    String m = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                    bodies[rowIdx] = m;
                    // A 404/501 on a newer-only endpoint is information, not a failure: it tells
                    // you this deployment predates that feature.
                    boolean newerOnly = "1".equals(e[3]);
                    boolean missing = m.contains("404") || m.toLowerCase().contains("not found");
                    if (newerOnly && missing)
                        return new Object[]{"not on this server yet", ms, "needs a newer build"};
                    if (m.contains("401") || m.contains("403"))
                        return new Object[]{"needs sign-in / admin", ms, m};
                    if (m.contains("503"))
                        return new Object[]{"migration missing", ms, m};
                    return new Object[]{"FAILED", ms, m};
                }
            }
            @Override protected void done() {
                try {
                    Object[] r = get();
                    model.setValueAt(r[0], rowIdx, 2);
                    model.setValueAt(r[1] + " ms", rowIdx, 3);
                    if (!"OK".equals(r[0])) status.setText(e[0] + ": " + r[2]);
                } catch (Exception ex) {
                    model.setValueAt("FAILED", rowIdx, 2);
                }
            }
        }.execute();
    }

    /** A one-line reading of the body, rather than dumping raw JSON at the operator. */
    private static String verdict(String name, String body) {
        if (body == null) return "empty reply";
        if ("Health".equals(name)) {
            int i = body.indexOf("\"version\"");
            return i < 0 ? "up" : "up, " + body.substring(i, Math.min(body.length(), i + 22));
        }
        return body.length() + " bytes";
    }

    private static String shortUrl(String u) {
        if (u == null || u.isBlank()) return "(not configured)";
        return u.length() > 60 ? u.substring(0, 57) + "\u2026" : u;
    }
}
