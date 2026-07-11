package main.menu;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The in-game status overlay (Patch A2), drawn on the game canvas from the script's
 * {@code onPaint(Graphics)}. It renders a compact, fitted panel showing the script name + uptime,
 * the current state/task, loop progress, and any custom stat rows a script pushes via
 * {@link DreamBotMenu#putOverlayStat(String, String)} — plus a progress bar.
 *
 * <p>"Adjustable per script" is the point: position, title and visibility are settable here, and
 * the custom rows let each script surface whatever it tracks (logs cut, kills, xp/hr, …) without
 * changing this class. Uses only {@code java.awt.Graphics}, so it's client-version-safe.
 */
public final class Overlay {

    private Overlay() {}

    // ── Config (adjust per script) ───────────────────────────────────────────
    private static volatile boolean visible = true;
    private static volatile int x = 8, y = 44;      // top-left on the game canvas
    private static volatile String title = "DreamMan";

    // ── Minimize state + click routing (Patch B.2) ───────────────────────────
    /** Main panel collapsed to a slim pill when true. */
    private static volatile boolean mainMinimized = false;
    /** Per-skill collapsed state, keyed by skill name. */
    private static final Map<String, Boolean> SKILL_MIN = new HashMap<>();
    /** Clickable regions rebuilt every frame: [Rectangle, Runnable]. */
    private static final List<Object[]> HITS = new ArrayList<>();

    /** Last known mouse position on the canvas (fed by the script's mouse-moved hook). */
    private static volatile Point lastMouse;

    /** Feed the live cursor position so titles can light up on hover (Patch B.3). */
    public static void onMouseMoved(Point p) { lastMouse = p; }

    private static boolean hovered(Rectangle r) {
        Point m = lastMouse;
        return m != null && r.contains(m);
    }

    /** Draws clickable title text: gold, underlined, brighter under the cursor (Patch B.3). */
    private static void clickableTitle(Graphics2D g2, String text, int tx, int ty, Rectangle hitRect) {
        Color base = Theme.ACCENT;
        g2.setColor(hovered(hitRect) ? Theme.shade(base, 55) : base);
        g2.drawString(text, tx, ty);
        int w = g2.getFontMetrics().stringWidth(text);
        g2.drawLine(tx, ty + 2, tx + w, ty + 2);
    }

    private static void hit(Rectangle r, Runnable action) {
        synchronized (HITS) { HITS.add(new Object[]{r, action}); }
    }

    /**
     * Routes a canvas click to the overlay's minimize/restore glyphs (called from the script's
     * mouse listener AFTER the button row). @return true when consumed.
     */
    public static boolean handleClick(MouseEvent e) {
        if (e == null) return false;
        List<Object[]> snap;
        synchronized (HITS) { snap = new ArrayList<>(HITS); }
        for (Object[] h : snap) {
            if (((Rectangle) h[0]).contains(e.getPoint())) {
                try { ((Runnable) h[1]).run(); } catch (Throwable ignored) {}
                return true;
            }
        }
        return false;
    }

    public static void setVisible(boolean v) { visible = v; }
    public static boolean isVisible() { return visible; }
    public static void setPosition(int px, int py) { x = px; y = py; }
    public static void setTitle(String t) { if (t != null) title = t; }
    public static int getX() { return x; }
    public static int getWidth() { return WIDTH; }

    // ── Palette (bright, game-canvas friendly) ────────────────────────────────
    private static final Color BG      = new Color(14, 15, 18, 225);
    private static final Color BORDER  = Theme.ACCENT;
    private static final Color TITLE   = Color.WHITE;
    private static final Color LABEL   = new Color(0x9AA0AA);
    private static final Color VALUE   = new Color(0xE6E8EC);
    private static final Color ACCENT  = Theme.AMBER;
    private static final Color BAR_BG  = new Color(0x1C1F26);
    private static final Color BAR_FG  = Theme.ACCENT;

    private static final int PADX = 12, PADY = 10, ROW = 18, WIDTH = 312; // ~1/3 wider for longer lines

    private static final Font F_TITLE = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font F_LABEL = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font F_MONO  = new Font("Consolas", Font.BOLD, 12);

    /** Draws the overlay for the given menu's live state; returns the panel's bottom Y (for
     *  attaching the button row directly beneath it). Returns y when not drawn. */
    public static int render(Graphics g, DreamBotMenu menu) {
        synchronized (HITS) { HITS.clear(); }   // rebuilt fresh every frame

        if (!visible || menu == null || !(g instanceof Graphics2D))
            return y;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // ── minimized: a slim pill that stays out of your vision; click to restore ──
        if (mainMinimized) {
            // Patch B.3: the pill keeps the progress bar, and its TITLE is the restore control
            // (click the title of any overlay to toggle it - no separate buttons).
            int pw = 170, ph = 22;
            Rectangle pillRect = new Rectangle(x, y, pw, ph);
            g2.setColor(BG);
            g2.fillRoundRect(x, y, pw, ph, 9, 9);
            g2.setColor(BORDER);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(x, y, pw, ph, 9, 9);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            clickableTitle(g2, title + " \u25B8", x + 8, y + 13, pillRect);
            g2.setColor(ACCENT);
            g2.setFont(F_MONO);
            String up = uptime(menu.getUptimeMillis());
            g2.drawString(up, x + pw - 8 - g2.getFontMetrics().stringWidth(up), y + 13);
            // slim progress strip along the bottom of the pill
            double frac = progressFraction(menu);
            g2.setColor(new Color(60, 60, 60));
            g2.fillRoundRect(x + 6, y + ph - 5, pw - 12, 3, 3, 3);
            g2.setColor(ACCENT);
            g2.fillRoundRect(x + 6, y + ph - 5, (int) ((pw - 12) * Math.max(0, Math.min(1, frac))), 3, 3, 3);
            hit(pillRect, () -> mainMinimized = false);
            g2.dispose();
            return y + ph;
        }

        // Build the rows first so we can size the panel to fit.
        List<String[]> rows = new ArrayList<>(); // [label, value]
        String task = menu.getCurrentTaskName();
        String state = menu.getStatusText();
        rows.add(new String[]{"State", state == null || state.isEmpty() ? "Idle" : state});
        if (task != null)
            rows.add(new String[]{"Task", task + "  (" + (menu.getCurrentExecutionIndex() + 1)
                    + "/" + Math.max(1, menu.getQueueSize()) + ")"});

        int loopTarget = menu.getQueueLoopTarget();
        rows.add(new String[]{"Loop", menu.getQueueLoopCurrentValue() + "/"
                + (loopTarget <= 0 ? "\u221e" : String.valueOf(loopTarget))});

        // custom rows pushed by the running script
        Map<String, String> stats = menu.getOverlayStats();
        synchronized (stats) {
            for (Map.Entry<String, String> e : stats.entrySet())
                rows.add(new String[]{e.getKey(), e.getValue()});
        }

        int titleH = 26;
        int barH = 16;
        int h = titleH + PADY + rows.size() * ROW + 6 + barH + PADY;

        // Panel
        g2.setColor(BG);
        g2.fillRoundRect(x, y, WIDTH, h, 12, 12);
        g2.setColor(BORDER);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(x, y, WIDTH, h, 12, 12);

        // Title bar
        g2.setColor(TITLE);
        g2.setFont(F_TITLE);
        // Patch B.3: the title IS the minimize control - gold + underlined so it reads as
        // clickable, brighter under the cursor. Click it to collapse to the pill.
        Rectangle titleRect = new Rectangle(x + PADX - 4, y + 4,
                g2.getFontMetrics().stringWidth(title) + 12, 20);
        clickableTitle(g2, title, x + PADX, y + 18, titleRect);
        hit(titleRect, () -> mainMinimized = true);
        g2.setColor(ACCENT);
        g2.setFont(F_MONO);
        String up = uptime(menu.getUptimeMillis());
        int upW = g2.getFontMetrics().stringWidth(up);
        g2.drawString(up, x + WIDTH - PADX - upW, y + 18);
        g2.setColor(Theme.BORDER);
        g2.drawLine(x + PADX, y + titleH, x + WIDTH - PADX, y + titleH);

        // Rows
        int ry = y + titleH + PADY + 8;
        for (String[] r : rows) {
            g2.setFont(F_LABEL);
            g2.setColor(LABEL);
            g2.drawString(r[0], x + PADX, ry);
            g2.setFont(F_MONO);
            g2.setColor("State".equals(r[0]) ? ACCENT : VALUE);
            // Keep the value from overlapping the label: cap its width and ellipsize.
            int labelW = 0;
            g2.setFont(F_LABEL); labelW = g2.getFontMetrics().stringWidth(r[0]); g2.setFont(F_MONO);
            int maxValW = WIDTH - PADX * 2 - labelW - 10;
            String val = ellipsize(g2, r[1] == null ? "-" : r[1], maxValW);
            int vw = g2.getFontMetrics().stringWidth(val);
            g2.drawString(val, x + WIDTH - PADX - vw, ry);
            ry += ROW;
        }

        // Progress bar (queue+loop progress)
        int by = ry - ROW + 12;
        int bw = WIDTH - PADX * 2;
        double frac = progressFraction(menu);
        g2.setColor(BAR_BG);
        g2.fillRoundRect(x + PADX, by, bw, barH, 8, 8);
        g2.setColor(BAR_FG);
        g2.fillRoundRect(x + PADX, by, (int) Math.round(bw * frac), barH, 8, 8);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Consolas", Font.BOLD, 11));
        String pct = (int) Math.round(frac * 100) + "%";
        int pw = g2.getFontMetrics().stringWidth(pct);
        g2.drawString(pct, x + PADX + (bw - pw) / 2, by + 12);

        g2.dispose();
        return y + h;
    }

    // ── Tracked-skill overlay cards (Patch B.3 rework) ───────────────────────
    private static final int SKILL_W = 170, SKILL_GAP = 5;
    private static final int CARD_H = 58, CARD_GOAL_H = 74, CHIP_W = 84, CHIP_H = 22;
    /** Fixed-mode fallback when the canvas size can't be read from the Graphics clip. */
    private static final int FALLBACK_BOTTOM = 332;
    /** Above this many tracked skills, cards auto-shrink to compact chips. */
    private static final int CHIP_THRESHOLD = 10;

    /**
     * Tracked-skill cards, stacked DOWN the left side of the screen (Patch B.3). The column
     * height comes from the live canvas (so resizable clients use the full height instead of
     * wrapping into a grid across the top); a second column only starts when the first truly
     * runs out of room. More than {@value CHIP_THRESHOLD} tracked skills auto-shrink to compact
     * chips. Every card/chip TITLE is the minimize/restore control - gold and underlined like
     * the main overlay's - and goals render as a progress bar (kept even in compact mode).
     */
    public static void renderSkills(Graphics g, DreamBotMenu menu, int startY) {
        if (!visible || menu == null || !(g instanceof Graphics2D)) return;
        List<main.menu.skills.SkillData> tracked;
        try { tracked = menu.getTrackedSkills(); } catch (Throwable t) { return; }
        if (tracked == null || tracked.isEmpty()) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Rectangle clip = g2.getClipBounds();
        int colBottom = clip != null && clip.height > 200
                ? clip.y + clip.height - 12
                : FALLBACK_BOTTOM;

        boolean chipDefault = tracked.size() > CHIP_THRESHOLD;
        int cx = x, cy = Math.max(startY, y);

        for (main.menu.skills.SkillData sd : tracked) {
            if (sd == null) continue;
            String name = sd.getSkill() != null ? sd.getSkill().name() : "?";
            // SKILL_MIN overrides the default: users can expand a chip or collapse a card.
            Boolean override = SKILL_MIN.get(name);
            boolean compact = override != null ? override : chipDefault;
            boolean hasGoal = sd.getGoalXp() > 0;
            int h = compact ? CHIP_H : (hasGoal ? CARD_GOAL_H : CARD_H);
            int w = compact ? CHIP_W : SKILL_W;

            if (cy + h > colBottom) {           // out of vertical room -> next column
                cx += SKILL_W + SKILL_GAP;
                cy = Math.max(startY, y);
            }

            g2.setColor(BG);
            g2.fillRoundRect(cx, cy, w, h, 10, 10);
            g2.setColor(BORDER);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(cx, cy, w, h, 10, 10);

            final String key = name;
            final boolean nowCompact = compact;
            Rectangle box = new Rectangle(cx, cy, w, h);

            if (compact) {
                // chip: "ATK 38>40" (or level) + the goal strip along the bottom
                g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
                String abbrev = name.length() > 3 ? name.substring(0, 3) : name;
                String lvl = sd.getCurrentLevel() > sd.getStartLevel()
                        ? sd.getStartLevel() + "→" + sd.getCurrentLevel()
                        : String.valueOf(sd.getCurrentLevel());
                clickableTitle(g2, abbrev + " " + lvl, cx + 7, cy + 13, box);
                if (hasGoal) {
                    double f = sd.getGoalFraction();
                    g2.setColor(new Color(60, 60, 60));
                    g2.fillRoundRect(cx + 5, cy + h - 5, w - 10, 3, 3, 3);
                    g2.setColor(ACCENT);
                    g2.fillRoundRect(cx + 5, cy + h - 5, (int) ((w - 10) * f), 3, 3, 3);
                }
                hit(box, () -> SKILL_MIN.put(key, !nowCompact ? Boolean.TRUE : Boolean.FALSE));
            } else {
                // full card: clickable skill-name title, login->current level, xp, xp/hr, goal
                g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                Rectangle titleRect = new Rectangle(cx + 6, cy + 3,
                        g2.getFontMetrics().stringWidth(name) + 10, 16);
                clickableTitle(g2, name, cx + 9, cy + 15, titleRect);
                hit(titleRect, () -> SKILL_MIN.put(key, Boolean.TRUE));

                g2.setFont(new Font("Consolas", Font.PLAIN, 11));
                g2.setColor(ACCENT);
                String lvl = sd.getCurrentLevel() > sd.getStartLevel()
                        ? "Lvl " + sd.getStartLevel() + "→" + sd.getCurrentLevel()
                        : "Lvl " + sd.getCurrentLevel();
                int lw = g2.getFontMetrics().stringWidth(lvl);
                g2.drawString(lvl, cx + SKILL_W - 9 - lw, cy + 15);

                g2.setColor(VALUE);
                g2.drawString("XP  +" + fmt(sd.getGainedXp()), cx + 9, cy + 32);
                g2.setColor(LABEL);
                g2.drawString(fmt(sd.getXpPerHour()) + " xp/hr", cx + 9, cy + 47);

                if (hasGoal) {
                    double f = sd.getGoalFraction();
                    g2.setColor(LABEL);
                    g2.setFont(new Font("Consolas", Font.PLAIN, 10));
                    String pct = String.format("%.0f%%", f * 100);
                    g2.drawString("Goal " + fmt(sd.getGoalXp()), cx + 9, cy + 61);
                    g2.drawString(pct, cx + SKILL_W - 9 - g2.getFontMetrics().stringWidth(pct), cy + 61);
                    g2.setColor(new Color(60, 60, 60));
                    g2.fillRoundRect(cx + 9, cy + 65, SKILL_W - 18, 4, 4, 4);
                    g2.setColor(ACCENT);
                    g2.fillRoundRect(cx + 9, cy + 65, (int) ((SKILL_W - 18) * f), 4, 4, 4);
                }
            }

            cy += h + SKILL_GAP;
        }
        g2.dispose();
    }

    private static String fmt(int n) {
        if (n >= 1_000_000) return String.format("%.1fm", n / 1_000_000.0);
        if (n >= 10_000)    return String.format("%.0fk", n / 1_000.0);
        if (n >= 1_000)     return String.format("%.1fk", n / 1_000.0);
        return String.valueOf(n);
    }

    private static double progressFraction(DreamBotMenu menu) {
        int size = menu.getQueueSize();
        if (size <= 0) return 0;
        int idx = Math.max(0, menu.getCurrentExecutionIndex());
        int target = menu.getQueueLoopTarget();
        if (target <= 0) // infinite: show progress within the current pass
            return Math.min(1.0, (double) idx / size);
        int done = (menu.getQueueLoopCurrentValue() - 1) * size + idx;
        return Math.max(0, Math.min(1.0, (double) done / (target * size)));
    }

    /** Truncates text with a trailing ellipsis so it fits within maxWidth px. */
    private static String ellipsize(Graphics2D g2, String s, int maxWidth) {
        FontMetrics fm = g2.getFontMetrics();
        if (s == null || fm.stringWidth(s) <= maxWidth) return s;
        String ell = "\u2026";
        int i = s.length();
        while (i > 0 && fm.stringWidth(s.substring(0, i) + ell) > maxWidth) i--;
        return i <= 0 ? ell : s.substring(0, i) + ell;
    }

    private static String uptime(long ms) {
        long s = ms / 1000;
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }
}
