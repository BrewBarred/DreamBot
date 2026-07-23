package main.menu.components;

import main.menu.Theme;
import main.privacy.Consent;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * The DreamMan-account sign-in card (v1.87) - the Account tab's face while logged out. Three
 * proper tabs (<b>Sign in</b> / <b>Create account</b> / <b>Forgot password</b>) replace the old
 * chain of stacked JOptionPanes, styled like the rest of the menu: one centred card, labelled
 * fields, inline errors that keep what you typed, and a working indicator while the network
 * round-trip runs. The crypto underneath is byte-for-byte the audited B.15 flow - this class
 * only SKINS {@link LoginDialog#performLogin}, {@link LoginDialog#performRegister} and
 * {@link LoginDialog#performRecovery}, it implements none of it.
 *
 * <p>The header also settles the two-logins confusion in writing: THIS is the DreamMan account
 * (market, cloud sync, your rank); the RuneScape login lives in the side Player panel and never
 * touches this server.
 */
public class AccountAuthPanel extends JPanel {

    /** Where the server URL comes from and what to tell when an auth state changes. */
    public interface Host {
        String serverUrl();
        void onAccountChanged();
    }

    private final Host host;
    private final CardLayout cards = new CardLayout();
    private final JPanel cardHost = new JPanel(cards);
    private final JToggleButton tabLogin = tab("Sign in");
    private final JToggleButton tabCreate = tab("Create account");
    private final JToggleButton tabForgot = tab("Forgot password");

    public AccountAuthPanel(Host host) {
        this.host = host;
        setOpaque(false);
        setLayout(new GridBagLayout());   // centres the card in the tab

        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setBackground(Theme.SURFACE_1);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                new EmptyBorder(18, 22, 18, 22)));
        card.setPreferredSize(new Dimension(420, 430));

        // ── header: identity + the two-logins explainer ──
        JLabel title = new JLabel("DreamMan Account");
        title.setForeground(Theme.ACCENT);
        title.setFont(new Font("Consolas", Font.BOLD, 20));
        JLabel sub = new JLabel("<html><div style='width:340px'>Your account on the DreamMan "
                + "server \u2014 the market, cloud sync, your rank and limits. This is <b>not</b> "
                + "your RuneScape login: that stays in the side Player panel and is never sent "
                + "here.</div></html>");
        sub.setForeground(Theme.TEXT_DIM);
        sub.setFont(Theme.font(12));
        JPanel head = new JPanel(new BorderLayout(0, 6));
        head.setOpaque(false);
        head.add(title, BorderLayout.NORTH);
        head.add(sub, BorderLayout.CENTER);

        ButtonGroup group = new ButtonGroup();
        JPanel tabs = new JPanel(new GridLayout(1, 3, 4, 0));
        tabs.setOpaque(false);
        for (JToggleButton t : new JToggleButton[]{tabLogin, tabCreate, tabForgot}) {
            group.add(t);
            tabs.add(t);
        }
        tabLogin.addActionListener(e -> cards.show(cardHost, "login"));
        tabCreate.addActionListener(e -> cards.show(cardHost, "create"));
        tabForgot.addActionListener(e -> cards.show(cardHost, "forgot"));
        tabLogin.setSelected(true);

        JPanel north = new JPanel(new BorderLayout(0, 12));
        north.setOpaque(false);
        north.add(head, BorderLayout.NORTH);
        north.add(tabs, BorderLayout.SOUTH);
        card.add(north, BorderLayout.NORTH);

        cardHost.setOpaque(false);
        cardHost.add(buildLogin(), "login");
        cardHost.add(buildCreate(), "create");
        cardHost.add(buildForgot(), "forgot");
        card.add(cardHost, BorderLayout.CENTER);

        add(card);
    }

    // ── the three forms ───────────────────────────────────────────────────────

    private JPanel buildLogin() {
        JTextField user = field();
        JPasswordField pass = passField();
        JCheckBox show = showToggle(pass);
        JLabel error = errorLabel();
        JButton go = primary("Sign in");
        JLabel busy = busyLabel();

        go.addActionListener(e -> {
            String u = user.getText().trim();
            char[] p = pass.getPassword();
            if (u.isEmpty() || p.length == 0) {
                error.setText("Enter both a username and a password.");
                return;
            }
            if (!ensureConsent()) return;
            run(go, busy, error, "Signing in\u2026",
                    () -> LoginDialog.performLogin(host.serverUrl(), u, p),
                    () -> pass.setText(""),
                    ex -> LoginDialog.isBadCredentials(ex)
                            ? "Wrong username or password \u2014 check both and try again."
                            : LoginDialog.friendly(ex));
        });
        pass.addActionListener(e -> go.doClick());

        return form(error, busy, go,
                "Username", user,
                "Password", pass,
                "", show);
    }

    private JPanel buildCreate() {
        JTextField user = field();
        JPasswordField pass = passField();
        JPasswordField pass2 = passField();
        JLabel error = errorLabel();
        JButton go = primary("Create account");
        JLabel busy = busyLabel();
        JLabel warn = new JLabel("<html><div style='width:320px'><i>Your password encrypts your "
                + "saved accounts. If you forget it, only the one-time recovery code shown next "
                + "can get them back \u2014 the server genuinely can't reset it.</i></div></html>");
        warn.setForeground(new Color(210, 160, 90));
        warn.setFont(Theme.font(11));

        go.addActionListener(e -> {
            String u = user.getText().trim();
            char[] p1 = pass.getPassword(), p2 = pass2.getPassword();
            if (u.length() < 3) { error.setText("Username must be 3+ characters."); return; }
            if (p1.length < 8) { error.setText("Password must be 8+ characters."); return; }
            if (!java.util.Arrays.equals(p1, p2)) { error.setText("Passwords don't match."); return; }
            if (!ensureConsent()) return;
            final String[] code = new String[1];
            run(go, busy, error, "Creating account\u2026",
                    () -> code[0] = LoginDialog.performRegister(host.serverUrl(), u, p1),
                    () -> {
                        pass.setText(""); pass2.setText("");
                        // the unskippable recovery-code screen, exactly as the dialog shows it
                        LoginDialog.showRecoveryCode(this, u, code[0]);
                    },
                    ex -> (ex instanceof main.market.ServerAccount.HttpError
                            && ((main.market.ServerAccount.HttpError) ex).status == 409)
                            ? "That username is already taken \u2014 pick another, or sign in."
                            : LoginDialog.friendly(ex));
        });

        return form(error, busy, go,
                "Username", user,
                "Password", pass,
                "Confirm password", pass2,
                "", warn);
    }

    private JPanel buildForgot() {
        JTextField user = field();
        JTextField code = field();
        JPasswordField newPass = passField();
        JLabel error = errorLabel();
        JButton go = primary("Recover account");
        JLabel busy = busyLabel();
        JLabel hint = new JLabel("<html><div style='width:320px'><i>The recovery code is the "
                + "long dashed string shown once when the account was created.</i></div></html>");
        hint.setForeground(Theme.TEXT_MUTED);
        hint.setFont(Theme.font(11));

        go.addActionListener(e -> {
            String u = user.getText().trim(), c = code.getText().trim();
            char[] np = newPass.getPassword();
            if (u.isEmpty() || c.isEmpty()) { error.setText("Enter your username and recovery code."); return; }
            if (np.length < 8) { error.setText("New password must be 8+ characters."); return; }
            if (!ensureConsent()) return;
            run(go, busy, error, "Recovering\u2026",
                    () -> LoginDialog.performRecovery(host.serverUrl(), u, c, np),
                    () -> newPass.setText(""),
                    ex -> LoginDialog.isBadCredentials(ex) || ex instanceof IllegalStateException
                            ? "That recovery code didn't match \u2014 check for typos and try again."
                            : LoginDialog.friendly(ex));
        });

        return form(error, busy, go,
                "Username", user,
                "Recovery code", code,
                "New password", newPass,
                "", hint);
    }

    // ── machinery ─────────────────────────────────────────────────────────────

    /** Auth needs network consent; asks once, exactly like the dialog does. */
    private boolean ensureConsent() {
        if (Consent.anyNetworkConsent()) return true;
        int ok = JOptionPane.showConfirmDialog(this,
                "<html>Signing in means DreamMan talks to your server.<br><br>"
                        + "You haven't agreed to that yet. Enable it now?<br>"
                        + "<i>(You can review exactly what's sent in the Privacy tab.)</i></html>",
                "Enable server access?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return false;
        Consent.set(Consent.MARKET_BROWSE, true);
        Consent.set(Consent.CLOUD_SYNC, true);
        return true;
    }

    private interface Work { void run() throws Exception; }

    /** Off-EDT work with the button disabled, a busy line, inline errors, and the change ping. */
    private void run(JButton go, JLabel busy, JLabel error, String busyText,
                     Work work, Runnable onSuccessUi,
                     java.util.function.Function<Throwable, String> errorText) {
        go.setEnabled(false);
        error.setText(" ");
        busy.setText(busyText);
        Thread t = new Thread(() -> {
            try {
                work.run();
                SwingUtilities.invokeLater(() -> {
                    busy.setText(" ");
                    go.setEnabled(true);
                    if (onSuccessUi != null) onSuccessUi.run();
                    host.onAccountChanged();   // the tab re-renders as the signed-in view
                });
            } catch (Throwable ex) {
                SwingUtilities.invokeLater(() -> {
                    busy.setText(" ");
                    go.setEnabled(true);
                    error.setText("<html><div style='width:320px'>"
                            + errorText.apply(ex) + "</div></html>");
                });
            }
        }, "DreamMan-Auth");
        t.setDaemon(true);
        t.start();
    }

    /** Label/field rows stacked, then the error + busy + action button. */
    private JPanel form(JLabel error, JLabel busy, JButton go, Object... labelFieldPairs) {
        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        for (int i = 0; i + 1 < labelFieldPairs.length; i += 2) {
            String label = (String) labelFieldPairs[i];
            JComponent comp = (JComponent) labelFieldPairs[i + 1];
            if (!label.isEmpty()) {
                JLabel l = new JLabel(label.toUpperCase());
                l.setForeground(Theme.TEXT_DIM);
                l.setFont(new Font("Segoe UI", Font.BOLD, 10));
                l.setAlignmentX(0f);
                l.setBorder(new EmptyBorder(6, 0, 3, 0));
                stack.add(l);
            } else {
                stack.add(Box.createVerticalStrut(6));
            }
            comp.setAlignmentX(0f);
            stack.add(comp);
        }
        stack.add(Box.createVerticalStrut(6));
        error.setAlignmentX(0f);
        stack.add(error);
        busy.setAlignmentX(0f);
        stack.add(busy);
        stack.add(Box.createVerticalStrut(8));
        go.setAlignmentX(0f);
        stack.add(go);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(stack, BorderLayout.NORTH);
        return wrap;
    }

    // ── styled pieces ─────────────────────────────────────────────────────────

    private static JToggleButton tab(String text) {
        JToggleButton t = new JToggleButton(text) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(isSelected() ? Theme.ACCENT_TINT : Theme.SURFACE_2);
                g.fillRect(0, 0, getWidth(), getHeight());
                super.paintComponent(g);
            }
        };
        t.setContentAreaFilled(false);
        t.setFocusPainted(false);
        t.setForeground(Theme.TEXT);
        t.setFont(new Font("Segoe UI", Font.BOLD, 12));
        t.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, Theme.BORDER),
                new EmptyBorder(6, 4, 6, 4)));
        t.addChangeListener(e -> t.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0,
                        t.isSelected() ? Theme.ACCENT : Theme.BORDER),
                new EmptyBorder(6, 4, 6, 4))));
        return t;
    }

    private static JTextField field() {
        JTextField f = new JTextField();
        style(f);
        return f;
    }

    private static JPasswordField passField() {
        JPasswordField f = new JPasswordField();
        style(f);
        return f;
    }

    private static void style(JTextField f) {
        f.setBackground(Theme.SURFACE_2);
        f.setForeground(Theme.TEXT);
        f.setCaretColor(Theme.ACCENT);
        f.setFont(Theme.font(13));
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(6, 8, 6, 8)));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
    }

    private static JCheckBox showToggle(JPasswordField pass) {
        JCheckBox show = new JCheckBox("Show password");
        show.setOpaque(false);
        show.setForeground(Theme.TEXT_DIM);
        show.setFont(Theme.font(11));
        final char echo = pass.getEchoChar();
        show.addActionListener(e -> pass.setEchoChar(show.isSelected() ? (char) 0 : echo));
        return show;
    }

    private static JLabel errorLabel() {
        JLabel l = new JLabel(" ");
        l.setForeground(new Color(0xE0, 0x6C, 0x6C));
        l.setFont(Theme.font(11));
        return l;
    }

    private static JLabel busyLabel() {
        JLabel l = new JLabel(" ");
        l.setForeground(Theme.TEXT_DIM);
        l.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        return l;
    }

    private static JButton primary(String text) {
        JButton b = new Theme.ThemedButton(text);
        b.putClientProperty("accent", Boolean.TRUE);
        b.setFont(new Font("Segoe UI", Font.BOLD, 13));
        b.setPreferredSize(new Dimension(0, 34));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        return b;
    }
}
