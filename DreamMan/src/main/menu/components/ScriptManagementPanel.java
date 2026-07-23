package main.menu.components;

import main.market.ScriptListing;
import main.market.ServerAccount;
import main.menu.DreamBotMenu;
import main.menu.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * v1.66: the admin Script Management tab, inside the Dev Console.
 *
 * <p>Two jobs in one place:
 * <ol>
 *   <li><b>The moderation valve.</b> Flipping it on diverts every ordinary user's publish into a
 *       pending queue instead of the live market, so a wave of duplicate or malicious uploads can
 *       be stopped without taking publishing offline. Admins review, approve or deny; denied work
 *       is <em>archived</em>, never deleted, so there's a record of who submits what and a second
 *       chance if it turns out to be fine.</li>
 *   <li><b>The defaults library.</b> Admins push a task - from their own library or straight off
 *       the market - into the set every user receives. No approval step: an admin promoting a task
 *       IS the decision. Pushed tasks carry {@code origin = default-admin}, so the library can show
 *       them apart from the hardcoded defaults.</li>
 * </ol>
 *
 * <p>Everything except the "My library" scope lives on the SERVER, so any admin sees the same
 * state from any machine. Every call runs off the EDT on a {@link SwingWorker}; a server that's
 * down leaves the table empty with a message rather than freezing the client.
 */
public final class ScriptManagementPanel {

    private ScriptManagementPanel() {}

    // The five scopes, in the order the tab presents them.
    private static final String SCOPE_LIBRARY  = "My task library";
    private static final String SCOPE_DEFAULTS = "Default tasks (all users)";
    private static final String SCOPE_MARKET   = "On the market";
    private static final String SCOPE_PENDING  = "Awaiting approval";
    private static final String SCOPE_ARCHIVE  = "Archived (denied)";

    /** One table row plus the object it came from, so the actions can act on it. */
    private static final class Row {
        String id = "", name = "", who = "", kind = "", when = "", note = "";
        Object payload;
    }

    /**
     * @param library the admin's own task library (supplied by the menu, which owns it)
     * @param market  the market listings currently loaded in the Market tab
     */
    public static JComponent build(Supplier<List<DreamBotMenu.Task>> library,
                                   Supplier<List<ScriptListing>> market) {
        final ServerAccount server = new ServerAccount(ServerAccount.session().baseUrl);
        final List<Row> rows = new ArrayList<>();

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        root.setBackground(Theme.SURFACE_1);

        // ── the moderation valve ────────────────────────────────────────────────────────────
        JCheckBox chkModerate = new JCheckBox("Hold new user submissions for approval");
        chkModerate.setOpaque(false);
        chkModerate.setForeground(Theme.TEXT);
        chkModerate.setToolTipText("<html>While this is on, a user's publish goes into "
                + "<b>Awaiting approval</b> instead of straight to the market.<br>"
                + "Admins and owners always bypass the queue.</html>");

        JLabel valveNote = new JLabel(" ");
        valveNote.setForeground(Theme.TEXT_DIM);
        valveNote.setFont(new Font("Segoe UI", Font.ITALIC, 11));

        JPanel valveRow = new JPanel(new BorderLayout(8, 0));
        valveRow.setOpaque(false);
        valveRow.add(chkModerate, BorderLayout.WEST);
        valveRow.add(valveNote, BorderLayout.CENTER);

        // ── scope + search ──────────────────────────────────────────────────────────────────
        JComboBox<String> scope = new JComboBox<>(new String[]{
                SCOPE_LIBRARY, SCOPE_DEFAULTS, SCOPE_MARKET, SCOPE_PENDING, SCOPE_ARCHIVE});
        JTextField search = new JTextField();
        search.setToolTipText("Filter the list by name, author or tag");
        JButton btnRefresh = new Theme.ThemedButton("Refresh");

        JPanel filterRow = new JPanel(new BorderLayout(6, 0));
        filterRow.setOpaque(false);
        JPanel west = new JPanel(new BorderLayout(6, 0));
        west.setOpaque(false);
        JLabel lblShow = new JLabel("Show");
        lblShow.setForeground(Theme.TEXT_DIM);
        west.add(lblShow, BorderLayout.WEST);
        west.add(scope, BorderLayout.CENTER);
        filterRow.add(west, BorderLayout.WEST);
        filterRow.add(search, BorderLayout.CENTER);
        filterRow.add(btnRefresh, BorderLayout.EAST);

        JLabel status = new JLabel(" ");
        status.setForeground(Theme.TEXT_DIM);
        status.setFont(new Font("Segoe UI", Font.ITALIC, 11));

        JPanel header = new JPanel(new GridLayout(0, 1, 0, 6));
        header.setOpaque(false);
        header.add(valveRow);
        header.add(filterRow);
        header.add(status);

        // ── the table ───────────────────────────────────────────────────────────────────────
        String[] cols = {"Name", "Author / source", "Kind", "When", "Note"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(model);
        table.setRowHeight(26);
        table.setBackground(new Color(0x1A, 0x1A, 0x1A));
        table.setForeground(Theme.TEXT);
        table.setSelectionBackground(new Color(0x3A, 0x33, 0x18));
        table.getTableHeader().setReorderingAllowed(false);
        table.setAutoCreateRowSorter(true);
        JScrollPane scroll = Theme.thinScrollbars(new JScrollPane(table));
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));

        // ── actions ─────────────────────────────────────────────────────────────────────────
        JButton btnPromote = new Theme.ThemedButton("Make default");
        btnPromote.putClientProperty("fillColor", new Color(30, 70, 40));
        btnPromote.setToolTipText("Push the selected task into every user's library");
        JButton btnUnDefault = new Theme.ThemedButton("Remove default");
        JButton btnApprove = new Theme.ThemedButton("Approve");
        btnApprove.putClientProperty("fillColor", new Color(30, 70, 40));
        JButton btnDeny = new Theme.ThemedButton("Deny\u2026");
        btnDeny.putClientProperty("fillColor", new Color(80, 35, 25));
        JButton btnRestore = new Theme.ThemedButton("Restore to queue");
        // v1.88: EXPORT lives here now, not on every user's Task List. Packaging a queue into a
        // standalone jar is a maintenance/moderation tool - handing it to everyone mostly
        // helped people carry the app's work out of the app. Admins can export any script they
        // can select here; the exporter itself is the same one, unchanged.
        JButton btnExport = new Theme.ThemedButton("Export as script\u2026");
        btnExport.setToolTipText("Package the selected script into a .jar DreamBot can run "
                + "on its own");

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);
        actions.add(btnPromote);
        actions.add(btnUnDefault);
        actions.add(btnApprove);
        actions.add(btnDeny);
        actions.add(btnRestore);
        actions.add(btnExport);

        root.add(header, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(actions, BorderLayout.SOUTH);

        // ── behaviour ───────────────────────────────────────────────────────────────────────
        final Runnable syncButtons = () -> {
            String s = String.valueOf(scope.getSelectedItem());
            boolean any = table.getSelectedRow() >= 0;
            btnPromote.setVisible(SCOPE_LIBRARY.equals(s) || SCOPE_MARKET.equals(s));
            btnUnDefault.setVisible(SCOPE_DEFAULTS.equals(s));
            btnApprove.setVisible(SCOPE_PENDING.equals(s));
            btnDeny.setVisible(SCOPE_PENDING.equals(s));
            btnRestore.setVisible(SCOPE_ARCHIVE.equals(s));
            btnExport.setEnabled(any);   // v1.88: available in every scope that lists scripts
            btnPromote.setEnabled(any);
            btnUnDefault.setEnabled(any);
            btnApprove.setEnabled(any);
            btnDeny.setEnabled(any);
            btnRestore.setEnabled(any);
        };

        final Runnable[] reload = new Runnable[1];
        reload[0] = () -> {
            final String s = String.valueOf(scope.getSelectedItem());
            final String needle = search.getText() == null ? ""
                    : search.getText().trim().toLowerCase();
            status.setText("Loading\u2026");
            model.setRowCount(0);
            rows.clear();
            new SwingWorker<List<Row>, Void>() {
                @Override protected List<Row> doInBackground() throws Exception {
                    return collect(s, server, library, market);
                }
                @Override protected void done() {
                    try {
                        for (Row r : get()) {
                            if (!needle.isEmpty()
                                    && !(r.name + " " + r.who + " " + r.note)
                                        .toLowerCase().contains(needle))
                                continue;
                            rows.add(r);
                            model.addRow(new Object[]{r.name, r.who, r.kind, r.when, r.note});
                        }
                        status.setText(rows.isEmpty() ? "Nothing here."
                                : rows.size() + " item(s).");
                    } catch (Exception ex) {
                        status.setText("Couldn't load: " + rootMessage(ex));
                    }
                    syncButtons.run();
                }
            }.execute();
        };

        // The valve's current state, read once on open.
        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() throws Exception {
                Object v = server.fetchSettings().get("submissionsModerated");
                return Boolean.TRUE.equals(v) || "true".equals(String.valueOf(v));
            }
            @Override protected void done() {
                try {
                    boolean on = get();
                    chkModerate.setSelected(on);
                    valveNote.setText(on
                            ? "Submissions are being held for review."
                            : "Submissions go straight to the market.");
                } catch (Exception ex) {
                    valveNote.setText("Couldn't read the server setting.");
                    chkModerate.setEnabled(false);
                }
            }
        }.execute();

        chkModerate.addActionListener(e -> {
            final boolean want = chkModerate.isSelected();
            chkModerate.setEnabled(false);
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() throws Exception {
                    server.adminSetModeration(want);
                    return null;
                }
                @Override protected void done() {
                    chkModerate.setEnabled(true);
                    try {
                        get();
                        valveNote.setText(want
                                ? "Submissions are being held for review."
                                : "Submissions go straight to the market.");
                    } catch (Exception ex) {
                        chkModerate.setSelected(!want);   // the server refused - don't lie
                        valveNote.setText("Couldn't change it: " + rootMessage(ex));
                    }
                }
            }.execute();
        });

        scope.addActionListener(e -> reload[0].run());
        btnRefresh.addActionListener(e -> reload[0].run());
        search.addActionListener(e -> reload[0].run());
        table.getSelectionModel().addListSelectionListener(e -> syncButtons.run());

        btnPromote.addActionListener(e -> {
            Row r = selected(table, rows);
            if (r == null) return;
            Object taskData = toTaskData(r);
            if (taskData == null) {
                status.setText("That row has no task payload to promote.");
                return;
            }
            String src = (r.payload instanceof ScriptListing) ? "market" : "library";
            runAction(status, reload[0], () -> server.adminAddDefault(taskData, src),
                    "\"" + r.name + "\" is now a default for everyone.");
        });

        btnUnDefault.addActionListener(e -> {
            Row r = selected(table, rows);
            if (r == null) return;
            if (!confirm(root, "Remove \"" + r.name + "\" from the defaults?\n\n"
                    + "Users who already have it keep their copy - it just stops being "
                    + "pushed to new libraries."))
                return;
            runAction(status, reload[0], () -> server.adminRemoveDefault(r.id),
                    "\"" + r.name + "\" is no longer a default.");
        });

        btnApprove.addActionListener(e -> {
            Row r = selected(table, rows);
            if (r == null) return;
            runAction(status, reload[0], () -> server.adminApproveSubmission(r.id),
                    "Approved \"" + r.name + "\" \u2014 it's on the market.");
        });

        btnDeny.addActionListener(e -> {
            Row r = selected(table, rows);
            if (r == null) return;
            String reason = JOptionPane.showInputDialog(root,
                    "Why is \"" + r.name + "\" being denied?\n"
                    + "(Kept with the archived copy, so there's a record.)",
                    "Deny submission", JOptionPane.QUESTION_MESSAGE);
            if (reason == null) return;
            runAction(status, reload[0], () -> server.adminDenySubmission(r.id, reason),
                    "Archived \"" + r.name + "\".");
        });

        btnRestore.addActionListener(e -> {
            Row r = selected(table, rows);
            if (r == null) return;
            runAction(status, reload[0], () -> server.adminRestoreSubmission(r.id),
                    "\"" + r.name + "\" is back in the queue.");
        });

        btnExport.addActionListener(e -> {
            Row r = selected(table, rows);
            if (r == null) return;
            main.data.store.ScriptBundle bundle = toBundle(r);
            if (bundle == null) {
                status.setText("That row has no script payload to export.");
                return;
            }
            java.io.File dir = main.tools.ScriptExporter.dreamBotScriptsDir();
            java.io.File target;
            String fileName = main.tools.ScriptExporter.safeFileName(bundle.name) + ".jar";
            if (dir != null) {
                target = new java.io.File(dir, fileName);
            } else {
                JFileChooser fc = new JFileChooser(main.data.store.LocalStore.getExportsDir());
                fc.setDialogTitle("Save the script jar");
                fc.setSelectedFile(new java.io.File(
                        main.data.store.LocalStore.getExportsDir(), fileName));
                if (fc.showSaveDialog(root) != JFileChooser.APPROVE_OPTION) return;
                target = fc.getSelectedFile();
            }
            final java.io.File out = target;
            status.setText("Exporting \u201c" + bundle.name + "\u201d\u2026");
            new Thread(() -> {
                String err = null;
                try {
                    main.tools.ScriptExporter.export(bundle, out);
                } catch (Throwable ex) {
                    err = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                }
                final String fErr = err;
                SwingUtilities.invokeLater(() -> status.setText(fErr != null
                        ? "Export failed: " + fErr
                        : "Exported to " + out.getAbsolutePath()));
            }, "DreamMan-DevExport").start();
        });

        reload[0].run();
        return root;
    }

    // ── data ────────────────────────────────────────────────────────────────────────────────

    private static List<Row> collect(String scopeName, ServerAccount server,
                                     Supplier<List<DreamBotMenu.Task>> library,
                                     Supplier<List<ScriptListing>> market) throws Exception {
        List<Row> out = new ArrayList<>();
        if (SCOPE_LIBRARY.equals(scopeName)) {
            List<DreamBotMenu.Task> all = library == null ? null : library.get();
            if (all != null)
                for (DreamBotMenu.Task t : all) {
                    if (t == null) continue;
                    Row r = new Row();
                    r.id = t.getId();
                    r.name = t.getName();
                    r.who = "me";
                    r.kind = "task";
                    r.when = when(t.getCreatedAt());
                    r.note = t.getTags() == null || t.getTags().isEmpty() ? ""
                            : String.join(", ", t.getTags());
                    r.payload = t;
                    out.add(r);
                }
            return out;
        }
        if (SCOPE_MARKET.equals(scopeName)) {
            List<ScriptListing> all = market == null ? null : market.get();
            if (all != null)
                for (ScriptListing l : all) {
                    if (l == null) continue;
                    Row r = new Row();
                    r.id = l.id;
                    r.name = l.name;
                    r.who = l.author;
                    r.kind = l.kind;
                    r.when = when(l.publishedAt);
                    r.note = "v" + l.version;
                    r.payload = l;
                    out.add(r);
                }
            return out;
        }
        if (SCOPE_DEFAULTS.equals(scopeName)) {
            for (Map<String, Object> m : server.adminListDefaults()) {
                Row r = new Row();
                r.id = str(m.get("id"));
                r.name = str(m.get("name"));
                r.who = "added by " + str(m.get("addedBy"));
                r.kind = "default";
                r.when = when(num(m.get("createdAt")));
                r.note = "from " + str(m.get("source"));
                r.payload = m;
                out.add(r);
            }
            return out;
        }
        boolean archived = SCOPE_ARCHIVE.equals(scopeName);
        for (Map<String, Object> m : server.adminListSubmissions(archived ? "archived" : "pending")) {
            Row r = new Row();
            r.id = str(m.get("id"));
            r.name = str(m.get("name"));
            r.who = str(m.get("author"));
            r.kind = str(m.get("kind"));
            r.when = when(num(m.get("submittedAt")));
            r.note = archived
                    ? ("denied by " + str(m.get("decidedBy"))
                       + (str(m.get("reason")).isEmpty() ? "" : ": " + str(m.get("reason"))))
                    : str(m.get("description"));
            r.payload = m;
            out.add(r);
        }
        return out;
    }

    /** The TaskData to promote for a row, or null when the row carries no usable task. */
    @SuppressWarnings("unchecked")
    /**
     * v1.88: the exportable bundle behind a row. Market listings already carry one; a library
     * task is wrapped into a single-task bundle so it can be exported the same way.
     */
    private static main.data.store.ScriptBundle toBundle(Row r) {
        if (r == null) return null;
        if (r.payload instanceof ScriptListing) {
            ScriptListing l = (ScriptListing) r.payload;
            if (l.bundle != null && l.bundle.tasks != null && !l.bundle.tasks.isEmpty())
                return l.bundle;
            return null;
        }
        if (r.payload instanceof DreamBotMenu.Task) {
            DreamBotMenu.Task t = (DreamBotMenu.Task) r.payload;
            main.data.store.ScriptBundle b = new main.data.store.ScriptBundle();
            b.name = t.getName() == null || t.getName().isBlank() ? "DreamMan Script" : t.getName();
            b.author = r.who == null || r.who.isBlank() ? "Anonymous" : r.who;
            b.description = t.getDescription() == null ? "" : t.getDescription();
            b.loops = 1;
            b.tasks = new ArrayList<>();
            b.tasks.add(main.data.store.ProfileCodec.toData(t));
            return b;
        }
        return null;
    }

    private static Object toTaskData(Row r) {
        if (r.payload instanceof DreamBotMenu.Task)
            return main.data.store.ProfileCodec.toData((DreamBotMenu.Task) r.payload);
        if (r.payload instanceof ScriptListing) {
            ScriptListing l = (ScriptListing) r.payload;
            if (l.bundle != null && l.bundle.tasks != null && !l.bundle.tasks.isEmpty())
                return l.bundle.tasks.get(0);
        }
        return null;
    }

    // ── plumbing ────────────────────────────────────────────────────────────────────────────

    private static Row selected(JTable table, List<Row> rows) {
        int v = table.getSelectedRow();
        if (v < 0) return null;
        int i = table.convertRowIndexToModel(v);
        return (i >= 0 && i < rows.size()) ? rows.get(i) : null;
    }

    /** Runs a server call off the EDT, then reports and reloads. */
    private static void runAction(JLabel status, Runnable reload, ThrowingRunnable call,
                                  String okMessage) {
        status.setText("Working\u2026");
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception { call.run(); return null; }
            @Override protected void done() {
                try {
                    get();
                    status.setText(okMessage);
                    reload.run();
                } catch (Exception ex) {
                    status.setText("Failed: " + rootMessage(ex));
                }
            }
        }.execute();
    }

    private interface ThrowingRunnable { void run() throws Exception; }

    private static boolean confirm(Component parent, String message) {
        return JOptionPane.showConfirmDialog(parent, message, "Confirm",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                == JOptionPane.YES_OPTION;
    }

    private static String rootMessage(Exception ex) {
        Throwable t = ex;
        while (t.getCause() != null) t = t.getCause();
        String m = t.getMessage();
        return (m == null || m.isEmpty()) ? t.getClass().getSimpleName() : m;
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }

    private static long num(Object o) {
        try { return (long) Double.parseDouble(String.valueOf(o)); }
        catch (Throwable t) { return 0L; }
    }

    private static String when(long millis) {
        if (millis <= 0) return "\u2014";
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(millis));
    }
}
