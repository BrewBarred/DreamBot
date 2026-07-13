package main.menu;

import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;

/**
 * Central design system for DreamMan (Patch A).
 *
 * <p>One place for the palette, fonts and spacing, plus {@link #install()} which pushes a cohesive
 * flat-dark look into every Swing component through {@code UIManager} defaults. Calling install()
 * once, before any components are created, means every tab, button, scrollbar, combo, spinner and
 * text field picks up the theme automatically - so the whole app lifts at once instead of each
 * screen being styled by hand.
 *
 * <p>It's an elevated version of the original blood-red/black identity: a cooler near-black base,
 * one confident crimson accent used sparingly, amber for "in progress", and proper text tiers.
 */
public final class Theme {

    private Theme() {}

    // ── Palette ────────────────────────────────────────────────────────────
    // OSRS gold-on-dark palette (per the OSRSMenu style reference).
    public static final Color BG_APP        = new Color(0x1C1C1C); // window canvas
    public static final Color SURFACE_1     = new Color(0x262626); // panels / bars
    public static final Color SURFACE_2     = new Color(0x323232); // inputs (raised)
    public static final Color SURFACE_2_ALT = new Color(0x2C2A24); // resting card (warm)
    public static final Color BORDER        = new Color(0x503C14); // dark gold hairline
    public static final Color BORDER_STRONG = new Color(0x644B0F); // hover / emphasis

    public static final Color TEXT          = new Color(0xDCD2BE); // warm off-white
    public static final Color TEXT_DIM      = new Color(0x968C78);
    public static final Color TEXT_MUTED    = new Color(0x6E6658);

    public static final Color ACCENT        = new Color(0xDCB43C); // OSRS gold
    public static final Color ACCENT_HOVER  = new Color(0xF0C850);
    public static final Color ACCENT_PRESS  = new Color(0xBE9628);
    public static final Color ACCENT_TINT   = new Color(0x2D2614); // gold bg wash

    public static final Color AMBER         = new Color(0xF0B84E); // running / highlight
    public static final Color AMBER_TINT    = new Color(0x2A2212);
    public static final Color GREEN         = new Color(0x8FB27F); // success (muted)
    public static final Color BLUE          = new Color(0x7FA8D8);
    public static final Color DANGER        = new Color(0xC85A3C);

    // ── Type ───────────────────────────────────────────────────────────────
    private static final String UI_FONT = "Segoe UI";
    public static Font font(int size)      { return new Font(UI_FONT, Font.PLAIN, size); }
    public static Font fontBold(int size)  { return new Font(UI_FONT, Font.BOLD, size); }
    public static Font mono(int size)      { return new Font("Consolas", Font.PLAIN, size); }
    public static Font monoBold(int size)  { return new Font("Consolas", Font.BOLD, size); }

    // ── Spacing / radius ─────────────────────────────────────────────────────
    public static final int RADIUS = 9;
    public static final int PAD    = 12;

    /** Rounded, empty border with inner padding (for cards / bars). */
    public static javax.swing.border.Border pad(int t, int l, int b, int r) {
        return BorderFactory.createEmptyBorder(t, l, b, r);
    }

    // ── App-wide install ──────────────────────────────────────────────────────
    public static void install() {
        UIManager.put("control", SURFACE_1);
        UIManager.put("text", TEXT);
        UIManager.put("nimbusLightBackground", SURFACE_1);

        put("Panel.background", BG_APP);
        put("Panel.foreground", TEXT);
        put("Label.foreground", TEXT);
        put("Label.font", font(13));

        put("Viewport.background", SURFACE_1);
        put("ScrollPane.background", SURFACE_1);
        put("ScrollPane.border", BorderFactory.createEmptyBorder());

        // Tabs
        put("TabbedPane.background", SURFACE_1);
        put("TabbedPane.foreground", TEXT_DIM);
        put("TabbedPane.selected", SURFACE_2);
        put("TabbedPane.contentAreaColor", BG_APP);
        put("TabbedPane.borderHightlightColor", BORDER);
        put("TabbedPane.darkShadow", BORDER);
        put("TabbedPane.light", BORDER);
        put("TabbedPane.shadow", BORDER);
        put("TabbedPane.focus", ACCENT);
        put("TabbedPane.contentBorderInsets", new Insets(1, 0, 0, 0));
        put("TabbedPane.tabInsets", new Insets(8, 14, 8, 14));
        put("TabbedPane.selectedForeground", TEXT);
        put("TabbedPane.font", font(13));

        // Inputs
        put("TextField.background", SURFACE_2);
        put("TextField.foreground", TEXT);
        put("TextField.caretForeground", ACCENT);
        put("TextField.border", inputBorder());
        put("TextField.font", font(13));
        put("TextArea.background", SURFACE_2);
        put("TextArea.foreground", TEXT);
        put("TextArea.caretForeground", ACCENT);
        put("FormattedTextField.background", SURFACE_2);
        put("FormattedTextField.foreground", TEXT);
        put("FormattedTextField.caretForeground", ACCENT);

        put("ComboBox.background", SURFACE_2);
        put("ComboBox.foreground", TEXT);
        put("ComboBox.selectionBackground", ACCENT_TINT);
        put("ComboBox.selectionForeground", TEXT);
        put("ComboBox.border", inputBorder());
        put("ComboBox.font", font(13));

        put("Spinner.background", SURFACE_2);
        put("Spinner.foreground", TEXT);
        put("Spinner.border", inputBorder());
        put("Spinner.font", font(13));

        put("CheckBox.background", BG_APP);
        put("CheckBox.foreground", TEXT);
        put("CheckBox.font", font(13));

        put("List.background", SURFACE_1);
        put("List.foreground", TEXT);
        put("List.selectionBackground", SURFACE_2);
        put("List.selectionForeground", TEXT);

        // Tooltips
        put("ToolTip.background", SURFACE_2);
        put("ToolTip.foreground", TEXT);
        put("ToolTip.border", BorderFactory.createLineBorder(BORDER_STRONG));
        put("ToolTip.font", font(12));

        // Dialogs
        put("OptionPane.background", SURFACE_1);
        put("OptionPane.messageForeground", TEXT);
        put("Menu.background", SURFACE_1);
        put("MenuItem.background", SURFACE_1);
        put("MenuItem.foreground", TEXT);
        put("PopupMenu.background", SURFACE_1);
        put("PopupMenu.border", BorderFactory.createLineBorder(BORDER));

        // Progress bar: flat rounded gold
        put("ProgressBar.background", SURFACE_2);
        put("ProgressBar.foreground", ACCENT);
        put("ProgressBar.border", BorderFactory.createEmptyBorder());

        // Buttons + scrollbars use our custom delegates - installed PER INSTANCE via
        // ThemedButton / thinScrollbars(), never via UIManager class-name registration.
        //
        // WHY (Patch B.1): registering "ButtonUI"/"ScrollBarUI"/"ProgressBarUI" here by class
        // NAME poisoned the whole DreamBot client. UIManager resolves those names with the
        // system/context classloader, which cannot see script-loaded classes, so EVERY button,
        // scrollbar and progress bar in the client - including the ones Substance (the client's
        // LAF) builds internally, like combo-popup scrollers - died with
        // "java.lang.Error: no ComponentUI class for: ...". That single mistake made all our
        // buttons and the bottom progress bar invisible, and the Error storm on the EDT aborted
        // pending UI work (late target-list refreshes, stale layouts painting over neighbours).
        put("Button.font", fontBold(12));
    }

    private static void put(String k, Object v) { UIManager.put(k, v); }

    /** Lighten (+) or darken (-) a colour by an amount, clamped. */
    public static Color shade(Color c, int amt) {
        return new Color(
                Math.max(0, Math.min(255, c.getRed() + amt)),
                Math.max(0, Math.min(255, c.getGreen() + amt)),
                Math.max(0, Math.min(255, c.getBlue() + amt)));
    }

    // ── Classloader-proof themed widgets (Patch B.1) ──────────────────────────
    // These bind their UI delegate as a direct INSTANCE inside updateUI(), so:
    //  (a) no UIManager class-name lookup can ever fail under the client's script classloader;
    //  (b) the client LAF (Substance) firing updateUI() on property changes can never swap the
    //      delegate back out - our override always reinstates it.

    /** A JButton permanently bound to the DreamMan flat look. */
    public static class ThemedButton extends JButton {
        public ThemedButton() { super(); }
        public ThemedButton(String text) { super(text); }
        @Override public void updateUI() { setUI(new FlatButtonUI()); }
    }

    /** A JScrollBar permanently bound to the thin flat look. */
    public static class ThemedScrollBar extends JScrollBar {
        public ThemedScrollBar(int orientation) {
            super(orientation);
            setUnitIncrement(16);
        }
        @Override public void updateUI() { setUI(new ThinScrollBarUI()); }
    }

    /**
     * Swaps a scroll pane's bars for the thin themed ones. Per-instance and classloader-safe;
     * use this instead of any UIManager scrollbar registration.
     */
    public static JScrollPane thinScrollbars(JScrollPane sp) {
        ThemedScrollBar v = new ThemedScrollBar(JScrollBar.VERTICAL);
        ThemedScrollBar h = new ThemedScrollBar(JScrollBar.HORIZONTAL);
        v.setPreferredSize(new Dimension(10, 0));
        h.setPreferredSize(new Dimension(0, 10));
        sp.setVerticalScrollBar(v);
        sp.setHorizontalScrollBar(h);
        return sp;
    }

    private static javax.swing.border.Border inputBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_STRONG),
                BorderFactory.createEmptyBorder(5, 9, 5, 9));
    }

    // ── Custom flat button: rounded fill, hover/press, subtle border ──────────
    public static class FlatButtonUI extends BasicButtonUI {
        public static javax.swing.plaf.ComponentUI createUI(JComponent c) { return new FlatButtonUI(); }

        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            AbstractButton b = (AbstractButton) c;
            b.setOpaque(false);
            // only install our padding if the caller hasn't set an explicit border - lets
            // compact buttons (the 40x40 icon controls) keep theirs through updateUI() sweeps
            if (b.getBorder() == null || b.getBorder() instanceof javax.swing.plaf.UIResource)
                b.setBorder(new javax.swing.plaf.BorderUIResource(
                        BorderFactory.createEmptyBorder(7, 14, 7, 14)));
            b.setForeground(TEXT);
            b.setRolloverEnabled(true);
            if (b.getFont() == null) b.setFont(fontBold(12));
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = c.getWidth(), h = c.getHeight();

            // A button is "filled" if it opts into the accent, or carries a semantic fillColor
            // (e.g. red Remove / green Add). Everything else is a quiet ghost button.
            Color fillColor = (c.getClientProperty("fillColor") instanceof Color)
                    ? (Color) c.getClientProperty("fillColor") : null;
            boolean accent = Boolean.TRUE.equals(c.getClientProperty("accent")) || fillColor != null;
            Color base = fillColor != null ? fillColor : ACCENT;
            ButtonModel m = b.getModel();

            Color fill;
            if (accent) {
                fill = m.isPressed() ? shade(base, -18) : (m.isRollover() ? shade(base, 18) : base);
            } else {
                fill = m.isPressed() ? SURFACE_2 : (m.isRollover() ? SURFACE_2_ALT : new Color(0, 0, 0, 0));
            }

            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w - 1, h - 1, RADIUS, RADIUS);

            if (!accent) {
                g2.setColor(m.isRollover() ? BORDER_STRONG : BORDER);
                g2.drawRoundRect(0, 0, w - 1, h - 1, RADIUS, RADIUS);
            }

            if (accent) {
                // dark text on light fills (gold), white on dark fills (red) - readable either way
                double lum = 0.299*base.getRed() + 0.587*base.getGreen() + 0.114*base.getBlue();
                b.setForeground(lum > 150 ? BG_APP : Color.WHITE);
            } else {
                b.setForeground(m.isRollover() ? TEXT : TEXT_DIM);
            }

            g2.dispose();
            super.paint(g, c);
        }
    }

    // ── Custom rounded progress bar ──────────────────────────────────────────
    public static class RoundProgressBarUI extends javax.swing.plaf.basic.BasicProgressBarUI {
        public static javax.swing.plaf.ComponentUI createUI(JComponent c) { return new RoundProgressBarUI(); }
        @Override protected void paintDeterminate(Graphics g, JComponent c) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = c.getWidth(), h = c.getHeight(), arc = h;
            g2.setColor(SURFACE_2);
            g2.fillRoundRect(0, 0, w, h, arc, arc);
            int fw = (int) Math.round(w * progressBar.getPercentComplete());
            if (fw > 0) {
                g2.setColor(ACCENT);
                g2.fillRoundRect(0, 0, Math.max(fw, h), h, arc, arc);
            }
            if (progressBar.isStringPainted() && progressBar.getString() != null) {
                g2.setFont(mono(11));
                FontMetrics fm = g2.getFontMetrics();
                String s = progressBar.getString();
                int sx = (w - fm.stringWidth(s)) / 2;
                int sy = (h + fm.getAscent() - fm.getDescent()) / 2;
                g2.setColor(new Color(0x1C1C1C));
                g2.drawString(s, sx, sy);
            }
            g2.dispose();
        }
    }

    // ── Custom thin scrollbar: flat, no arrows, rounded thumb ──────────────────
    public static class ThinScrollBarUI extends BasicScrollBarUI {
        public static javax.swing.plaf.ComponentUI createUI(JComponent c) { return new ThinScrollBarUI(); }

        @Override protected void configureScrollBarColors() {
            thumbColor = BORDER_STRONG;
            trackColor = SURFACE_1;
        }
        @Override protected JButton createDecreaseButton(int o) { return zeroButton(); }
        @Override protected JButton createIncreaseButton(int o) { return zeroButton(); }
        private JButton zeroButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(trackColor);
            g.fillRect(r.x, r.y, r.width, r.height);
        }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isThumbRollover() ? new Color(0x4A4F58) : thumbColor);
            int pad = 2;
            g2.fillRoundRect(r.x + pad, r.y + pad, r.width - pad * 2, r.height - pad * 2, 8, 8);
            g2.dispose();
        }
    }
}
