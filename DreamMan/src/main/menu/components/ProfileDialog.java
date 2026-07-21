package main.menu.components;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import main.menu.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * v1.63: a scripter's public profile, opened from the clickable author link on a market card
 * (server endpoint {@code GET /users/:name/profile}). Shows the stats the roadmap lists -
 * uploads (tasks/scripts), downloads received, ratings received + average, joined date - plus
 * the optional bio.
 *
 * <p><b>Deliberately schema-tolerant:</b> the exact JSON field names are the server's, and this
 * client was built without being able to inspect a live response - so every value is probed
 * under several plausible aliases (e.g. {@code joinedAt}/{@code createdAt}/{@code joined}) and
 * only the fields actually present are shown. If nothing recognisable comes back at all, the
 * raw JSON is shown in a small monospace box instead of a blank dialog, so the information is
 * never lost - and the alias table below is the one place to extend if the server uses a name
 * this doesn't cover yet.
 */
public class ProfileDialog extends JDialog {

    /** Parses the profile JSON and opens the dialog. Call on the EDT with a fetched body. */
    public static void show(Window owner, String username, String json) {
        new ProfileDialog(owner, username, json).setVisible(true);
    }

    private ProfileDialog(Window owner, String username, String json) {
        super(owner, "Profile \u2014 " + username, ModalityType.APPLICATION_MODAL);
        setAlwaysOnTop(true);   // the DreamBot canvas is always-on-top; match it

        JsonObject o = null;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (root != null && root.isJsonObject()) o = root.getAsJsonObject();
        } catch (Exception ignored) {}

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(Theme.BG_APP);
        root.setBorder(new EmptyBorder(14, 16, 12, 16));

        // ── header: name + joined ───────────────────────────────────────────────────────────
        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(str(o, username, "username", "name", "displayName"));
        name.setFont(Theme.fontBold(16));
        name.setForeground(Theme.ACCENT);
        name.setAlignmentX(LEFT_ALIGNMENT);
        head.add(name);
        String joined = joinedText(o);
        if (joined != null) {
            JLabel j = new JLabel("Joined " + joined);
            j.setFont(Theme.font(11));
            j.setForeground(Theme.TEXT_DIM);
            j.setAlignmentX(LEFT_ALIGNMENT);
            head.add(Box.createVerticalStrut(2));
            head.add(j);
        }
        root.add(head, BorderLayout.NORTH);

        // ── centre: bio + stats ─────────────────────────────────────────────────────────────
        JPanel centre = new JPanel();
        centre.setOpaque(false);
        centre.setLayout(new BoxLayout(centre, BoxLayout.Y_AXIS));

        String bio = str(o, null, "bio", "about", "description");
        if (bio != null && !bio.trim().isEmpty()) {
            JTextArea bioArea = new JTextArea(bio.trim());
            bioArea.setEditable(false);
            bioArea.setLineWrap(true);
            bioArea.setWrapStyleWord(true);
            bioArea.setFont(Theme.font(12));
            bioArea.setForeground(Theme.TEXT);
            bioArea.setBackground(Theme.SURFACE_2);
            bioArea.setBorder(new EmptyBorder(8, 10, 8, 10));
            JScrollPane bs = new JScrollPane(bioArea,
                    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            bs.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
            bs.setPreferredSize(new Dimension(340, Math.min(120,
                    bioArea.getPreferredSize().height + 20)));
            bs.setAlignmentX(LEFT_ALIGNMENT);
            centre.add(bs);
            centre.add(Box.createVerticalStrut(10));
        }

        Map<String, String> stats = new LinkedHashMap<>();
        putNum(stats, "Tasks uploaded", o, "tasks", "taskCount", "tasksUploaded");
        putNum(stats, "Scripts uploaded", o, "scripts", "scriptCount", "scriptsUploaded");
        putNum(stats, "Total uploads", o, "uploads", "uploadCount", "totalUploads");
        putNum(stats, "Downloads received", o, "downloads", "downloadsReceived", "totalDownloads");
        putNum(stats, "Ratings received", o, "ratings", "ratingCount", "ratingsReceived");
        Double avg = num(o, "avgRating", "averageRating", "avg_rating");
        if (avg != null) stats.put("Average rating", String.format("%.1f \u2605", avg));

        if (!stats.isEmpty()) {
            JPanel grid = new JPanel(new GridLayout(0, 2, 12, 4));
            grid.setOpaque(false);
            grid.setAlignmentX(LEFT_ALIGNMENT);
            for (Map.Entry<String, String> e : stats.entrySet()) {
                JLabel k = new JLabel(e.getKey() + ":");
                k.setFont(Theme.font(12));
                k.setForeground(Theme.TEXT_DIM);
                JLabel v = new JLabel(e.getValue());
                v.setFont(Theme.fontBold(12));
                v.setForeground(Theme.TEXT);
                grid.add(k);
                grid.add(v);
            }
            centre.add(grid);
        }

        // absolute fallback: nothing recognised - show the raw payload rather than a blank box
        if (stats.isEmpty() && (bio == null || bio.trim().isEmpty()) && joined == null) {
            JTextArea raw = new JTextArea(json == null ? "(empty response)" : json);
            raw.setEditable(false);
            raw.setLineWrap(true);
            raw.setFont(new Font("Consolas", Font.PLAIN, 11));
            raw.setForeground(Theme.TEXT_DIM);
            raw.setBackground(Theme.SURFACE_2);
            JScrollPane rs = new JScrollPane(raw);
            rs.setPreferredSize(new Dimension(340, 140));
            rs.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
            rs.setAlignmentX(LEFT_ALIGNMENT);
            JLabel note = new JLabel("Profile format not recognised \u2014 raw response:");
            note.setFont(Theme.font(11));
            note.setForeground(Theme.AMBER);
            note.setAlignmentX(LEFT_ALIGNMENT);
            centre.add(note);
            centre.add(Box.createVerticalStrut(4));
            centre.add(rs);
        }
        root.add(centre, BorderLayout.CENTER);

        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        south.setOpaque(false);
        south.add(close);
        root.add(south, BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setMinimumSize(new Dimension(380, getHeight()));
        setLocationRelativeTo(owner);
    }

    // ── tolerant readers ────────────────────────────────────────────────────────────────────

    private static String str(JsonObject o, String fallback, String... keys) {
        if (o != null)
            for (String k : keys) {
                JsonElement e = o.get(k);
                if (e != null && !e.isJsonNull() && e.isJsonPrimitive()) {
                    String s = e.getAsString();
                    if (s != null && !s.isEmpty()) return s;
                }
            }
        return fallback;
    }

    private static Double num(JsonObject o, String... keys) {
        if (o != null)
            for (String k : keys) {
                JsonElement e = o.get(k);
                if (e != null && !e.isJsonNull() && e.isJsonPrimitive()) {
                    try { return e.getAsDouble(); } catch (Exception ignored) {}
                }
            }
        return null;
    }

    private static void putNum(Map<String, String> into, String label, JsonObject o,
                               String... keys) {
        Double d = num(o, keys);
        if (d != null) into.put(label, String.valueOf((long) Math.floor(d)));
    }

    /** joinedAt as epoch ms, epoch seconds, or a date-ish string - whatever the server sent. */
    private static String joinedText(JsonObject o) {
        JsonElement e = null;
        if (o != null)
            for (String k : new String[]{"joinedAt", "joined", "createdAt", "created_at",
                    "memberSince"}) {
                e = o.get(k);
                if (e != null && !e.isJsonNull()) break;
                e = null;
            }
        if (e == null || !e.isJsonPrimitive()) return null;
        try {
            double d = e.getAsDouble();
            long ms = d > 1e12 ? (long) d : (long) (d * 1000L);   // epoch ms vs epoch seconds
            return new SimpleDateFormat("d MMM yyyy").format(new Date(ms));
        } catch (Exception ignored) {}
        String s = e.getAsString();
        if (s == null || s.isEmpty()) return null;
        return s.length() >= 10 ? s.substring(0, 10) : s;   // "2025-04-01T..." -> "2025-04-01"
    }
}
