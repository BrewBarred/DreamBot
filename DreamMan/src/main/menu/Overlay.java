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

        // v1.30: the player's live tile, shown subtly (task authors read it constantly).
        // onPaint runs on the game thread, so the API read here is safe; logged out = hidden.
        String tileText = null;
        try {
            var me = org.dreambot.api.methods.interactive.Players.getLocal();
            var t = me == null ? null : me.getTile();
            if (t != null) tileText = t.getX() + ", " + t.getY() + ", " + t.getZ();
        } catch (Throwable ignored) {}

        int titleH = 26;
        int barH = 16;
        int h = titleH + PADY + rows.size() * ROW + 6 + barH + PADY + (tileText != null ? 12 : 0);

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

        // v1.30: the live tile, bottom-right, tiny and dim - there when you're placing Walk
        // targets, invisible when you're not looking for it. (Height was reserved above.)
        if (tileText != null) {
            g2.setFont(new Font("Consolas", Font.PLAIN, 10));
            g2.setColor(new Color(0x8A, 0x84, 0x74));
            int tw = g2.getFontMetrics().stringWidth(tileText);
            g2.drawString(tileText, x + WIDTH - PADX - tw, by + barH + 11);
        }

        g2.dispose();
        return y + h;
    }

    // ── Tracked-skill overlay cards (Patch B.5: v2 with icons, bars & ETAs) ──
    /** v1.89: goal progress reads cyan; level progress reads gold. The chip relies on this. */
    private static final Color GOAL_COLOR = new Color(0x5A, 0xC8, 0xD8);
    private static final int SKILL_W = 176, SKILL_GAP = 5;
    private static final int CHIP_W = 118, CHIP_H = 22;
    /**
     * Fixed-mode fallback when the canvas size can't be read from the Graphics clip.
     *
     * <p>v1.88: this was 332 - roughly one card's worth of room below the button strip - so
     * whenever the clip couldn't be read (which is most frames; DreamBot hands onPaint a
     * clip sized to the dirty region, not the canvas) EVERY card overflowed its column
     * immediately and marched off to the right. Five tracked skills became a banner across
     * the top of the screen. The value is now a sane full-canvas guess AND is only a floor:
     * see MIN_PER_COLUMN, which guarantees a column holds a real stack regardless.
     */
    private static final int FALLBACK_BOTTOM = 700;
    /**
     * v1.88: a column always takes at least this many cards before spilling into the next one.
     * Stacking downward is the whole point - it keeps the trackers as one tidy strip down the
     * edge of the screen instead of a wall across the top of it.
     */
    private static final int MIN_PER_COLUMN = 6;
    /** Above this many tracked skills, cards auto-shrink to compact chips. */
    private static final int CHIP_THRESHOLD = 10;

    /**
     * Tracked-skill overlays v2 (Patch B.5). Each EXPANDED card is one mini canvas: the same
     * skill icon the menu uses, the login→current level, a progress bar to the next level with
     * time-to-level and remaining XP - and, when a goal is set (right-click the tracker tile),
     * a second bar with time-to-goal and remaining-to-goal. MINIMIZED is the simple 3-component
     * overlay: icon + progress bar (goal if set, else level) + floating "+xp gained" text.
     * Click a card's title to collapse it; click a chip to expand it. Columns stack down the
     * left and use the live canvas height; above {@value CHIP_THRESHOLD} tracked skills,
     * everything defaults to chips.
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
        final int top = Math.max(startY, y);

        // ── v1.88: a stable, predictable order ────────────────────────────────────────
        // Cards used to render in whatever order the tracked set happened to iterate, so
        // minimizing one in the middle left a chip sitting between two full cards and shoved
        // everything after it into a different column - the "bugs out when you minimize them
        // in a strange order" report. Now it's two alphabetical groups: everything EXPANDED
        // first, then everything MINIMIZED. Collapsing a card drops it to the bottom of the
        // list and expanding it lifts it back, so you always know where a tracker went.
        List<main.menu.skills.SkillData> order = new ArrayList<>();
        for (main.menu.skills.SkillData sd : tracked) if (sd != null) order.add(sd);
        order.sort((a, b) -> {
            boolean ca = isCompact(a, chipDefault), cb = isCompact(b, chipDefault);
            if (ca != cb) return ca ? 1 : -1;       // expanded above minimized
            return nameOf(a).compareTo(nameOf(b));  // then alphabetical within each group
        });

        int cx = x, cy = top, inColumn = 0;
        Font fName = new Font("Segoe UI", Font.BOLD, 12);
        Font fBody = new Font("Consolas", Font.PLAIN, 10);

        for (main.menu.skills.SkillData sd : order) {
            if (sd == null) continue;
            String name = nameOf(sd);
            boolean compact = isCompact(sd, chipDefault);
            boolean hasGoal = sd.getGoalXp() > 0;
            int h = compact ? CHIP_H : (hasGoal ? 108 : 82);
            int w = compact ? CHIP_W : SKILL_W;

            // v1.88: spill to the next column only when the column is genuinely full - and
            // never before MIN_PER_COLUMN cards have gone into it, whatever the clip claims.
            if (inColumn > 0 && cy + h > colBottom && inColumn >= MIN_PER_COLUMN) {
                cx += SKILL_W + SKILL_GAP;
                cy = top;
                inColumn = 0;
            }
            inColumn++;

            g2.setColor(BG);
            g2.fillRoundRect(cx, cy, w, h, 10, 10);
            g2.setColor(BORDER);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(cx, cy, w, h, 10, 10);

            final String key = name;
            Rectangle box = new Rectangle(cx, cy, w, h);
            Image icon = sd.getIconImage();

            if (compact) {
                // ── the simple 3-component overlay: icon + bar + floating "+xp" ──
                if (icon != null)
                    g2.drawImage(icon, cx + 4, cy + 3, 16, 16, null);
                double f = hasGoal ? sd.getGoalFraction() : sd.getLevelFraction();
                // v1.89: the chip's text used to run straight off the right edge - "5.5k to lvl"
                // simply didn't fit beside a 48px bar in a 118px card. Rather than widen the
                // chip (they should all stay one tidy column width), the words are gone and
                // COLOUR carries the meaning: gold = xp to the next level, cyan = xp to your
                // goal. The expanded card spells the legend out, so one click explains it.
                int barX = cx + 22, barW = 34;
                g2.setColor(new Color(60, 60, 60));
                g2.fillRoundRect(barX, cy + 9, barW, 5, 4, 4);
                g2.setColor(hasGoal ? GOAL_COLOR : ACCENT);
                g2.fillRoundRect(barX, cy + 9, (int) (barW * clamp01(f)), 5, 4, 4);
                g2.setFont(fBody);
                g2.setColor(hasGoal ? GOAL_COLOR : ACCENT);
                String toLvl = fmt(hasGoal ? sd.getGoalRemainingXp() : sd.getRemainingXp());
                // hard-clip to the card so a big number can never bleed past the border again
                Shape prevClip = g2.getClip();
                g2.clipRect(cx, cy, w - 3, h);
                g2.drawString(toLvl, barX + barW + 5, cy + 15);
                g2.setClip(prevClip);
                hit(box, () -> SKILL_MIN.put(key, Boolean.FALSE));   // click chip -> expand
            } else {
                // ── expanded mini canvas ──
                if (icon != null)
                    g2.drawImage(icon, cx + 6, cy + 4, 18, 18, null);
                g2.setFont(fName);
                Rectangle titleRect = new Rectangle(cx + 26, cy + 3,
                        g2.getFontMetrics().stringWidth(name) + 10, 16);
                clickableTitle(g2, name, cx + 28, cy + 16, titleRect);
                hit(titleRect, () -> SKILL_MIN.put(key, Boolean.TRUE)); // title -> collapse

                g2.setFont(fBody);
                g2.setColor(ACCENT);
                String lvl = sd.getCurrentLevel() > sd.getStartLevel()
                        ? "Lvl " + sd.getStartLevel() + "→" + sd.getCurrentLevel()
                        : "Lvl " + sd.getCurrentLevel();
                int lw = g2.getFontMetrics().stringWidth(lvl);
                g2.drawString(lvl, cx + w - 8 - lw, cy + 15);

                // level progress bar + "to level" stats
                int barX = cx + 8, barW = w - 16;
                g2.setColor(new Color(60, 60, 60));
                g2.fillRoundRect(barX, cy + 26, barW, 5, 4, 4);
                g2.setColor(VALUE);
                g2.fillRoundRect(barX, cy + 26, (int) (barW * clamp01(sd.getLevelFraction())), 5, 4, 4);
                g2.setColor(LABEL);
                g2.drawString("To lvl " + fmt(sd.getRemainingXp()) + " · " + fmtH(sd.getTtlHours()),
                        barX, cy + 44);
                g2.setColor(TITLE);
                String gained = "+" + fmt(sd.getGainedXp());
                g2.drawString(gained, cx + w - 8 - g2.getFontMetrics().stringWidth(gained), cy + 44);

                // Patch B.6: xp/hour on the overlay too (matches the tracker tab)
                g2.setColor(VALUE);
                g2.drawString(fmt(sd.getXpPerHour()) + " xp/hr", barX, cy + 60);

                // goal bar + "to goal" stats (only when set)
                if (hasGoal) {
                    g2.setColor(new Color(60, 60, 60));
                    g2.fillRoundRect(barX, cy + 70, barW, 5, 4, 4);
                    g2.setColor(ACCENT);
                    g2.fillRoundRect(barX, cy + 70, (int) (barW * clamp01(sd.getGoalFraction())), 5, 4, 4);
                    g2.setColor(LABEL);
                    g2.drawString("To goal " + fmt(sd.getGoalRemainingXp()) + " · "
                            + fmtH(sd.getGoalTtlHours()), barX, cy + 88);
                    g2.setColor(ACCENT);
                    String pct = String.format("%.0f%%", sd.getGoalFraction() * 100);
                    g2.drawString(pct, cx + w - 8 - g2.getFontMetrics().stringWidth(pct), cy + 88);
                }
            }

            cy += h + SKILL_GAP;
        }
        g2.dispose();
    }

    /** v1.88: the display name used for both sorting and the minimize map's key. */
    private static String nameOf(main.menu.skills.SkillData sd) {
        return sd != null && sd.getSkill() != null ? sd.getSkill().name() : "?";
    }

    /** v1.88: whether this card renders as a chip - explicit choice first, threshold second. */
    private static boolean isCompact(main.menu.skills.SkillData sd, boolean chipDefault) {
        Boolean override = SKILL_MIN.get(nameOf(sd));
        return override != null ? override : chipDefault;
    }

    private static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }

    /** "0.8h", "12h", ">99h", or "-" when the rate is unknown. */
    private static String fmtH(double hours) {
        if (hours < 0) return "-";
        if (hours > 99) return ">99h";
        if (hours >= 10) return String.format("%.0fh", hours);
        return String.format("%.1fh", hours);
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
