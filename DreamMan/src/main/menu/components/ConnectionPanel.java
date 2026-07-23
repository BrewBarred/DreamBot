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

        String[] cols = {"Endpoint", "Path", "Result", "Time", "What it's for", ""};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (String[] e : ENDPOINTS)
            model.addRow(new Object[]{e[0], e[2], "\u2014", "\u2014", e[4], "Test"});

        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.setBackground(new Color(0x1A, 0x1A, 0x1A));
        table.setForeground(Theme.TEXT);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(1).setPreferredWidth(210);
        table.getColumnModel().getColumn(4).setPreferredWidth(240);
        // v1.83: a real Test button on every row, as well as the bulk actions below.
        javax.swing.table.TableColumn testCol = table.getColumnModel().getColumn(5);
        testCol.setPreferredWidth(64);
        testCol.setMaxWidth(80);
        testCol.setCellRenderer((t, val, sel, foc, r, c) -> {
            JButton b = new JButton("Test");
            b.setMargin(new Insets(1, 4, 1, 4));
            return b;
        });
        testCol.setCellEditor(new javax.swing.DefaultCellEditor(new JCheckBox()) {
            private final JButton b = new JButton("Test");
            {
                b.setMargin(new Insets(1, 4, 1, 4));
                b.addActionListener(a -> {
                    fireEditingStopped();
                    int r = table.getSelectedRow();
                    if (r >= 0) probe(server, model, table.convertRowIndexToModel(r),
                            status, bodies);
                });
            }
            @Override public Component getTableCellEditorComponent(JTable t, Object v,
                    boolean sel, int r, int c) { return b; }
        });

        JScrollPane scroll = Theme.thinScrollbars(new JScrollPane(table));
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));

        JButton testAll = new Theme.ThemedButton("Test all");
        JButton testOne = new Theme.ThemedButton("Test selected");

        JButton viewBody = new Theme.ThemedButton("View response\u2026");
        viewBody.setToolTipText("Show exactly what the server sent back for the selected row");

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);
        actions.add(testAll);
        actions.add(testOne);
        actions.add(viewBody);

        viewBody.addActionListener(e -> {
            int r = table.getSelectedRow();
            if (r < 0) { status.setText("Select an endpoint first."); return; }
            int i = table.convertRowIndexToModel(r);
            String body = bodies[i];
            JTextArea ta = new JTextArea(body == null
                    ? "Nothing recorded yet - run the test for this endpoint first." : body);
            ta.setEditable(false);
            ta.setLineWrap(true);
            ta.setWrapStyleWord(true);
            ta.setFont(new Font("Consolas", Font.PLAIN, 12));
            ta.setCaretPosition(0);
            JScrollPane sp = new JScrollPane(ta);
            sp.setPreferredSize(new Dimension(680, 380));
            JOptionPane.showMessageDialog(root, sp,
                    ENDPOINTS[i][0] + "  \u00b7  " + ENDPOINTS[i][2], JOptionPane.PLAIN_MESSAGE);
        });

        JPanel header = new JPanel(new GridLayout(0, 1, 0, 5));
        header.setOpaque(false);
        header.add(where);
        header.add(status);

        root.add(header, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(actions, BorderLayout.SOUTH);

        testAll.addActionListener(e -> {
            status.setText("Testing " + ENDPOINTS.length + " endpoints\u2026");
            for (int i = 0; i < ENDPOINTS.length; i++) probe(server, model, i, status, bodies);
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
