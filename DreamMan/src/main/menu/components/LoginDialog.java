package main.menu.components;

import main.crypto.Vault;
import main.market.AccountVault;
import main.market.ServerAccount;
import main.privacy.Consent;

import javax.crypto.SecretKey;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/**
 * The login / register / recovery UI (Patch B.15). All password crypto happens on THIS machine
 * (see {@link Vault}); the server only ever receives an auth hash and opaque blobs.
 *
 * <p>Registration shows the one-time recovery code with a copy button, an offer to download it as
 * a .txt, and an "I've saved it" gate you can't skip — because it's the only way back in if the
 * password is forgotten (the server genuinely can't reset it).
 */
public final class LoginDialog {

    private LoginDialog() {}

    /** Result handed back to the menu so it can refresh tier-dependent UI. */
    public interface Callback { void onChanged(); }

    /** Opens the login/register chooser. Returns immediately; work happens on background threads. */
    public static void open(Component parent, String serverUrl, Callback cb) {
        if (!Consent.anyNetworkConsent()) {
            int ok = JOptionPane.showConfirmDialog(parent,
                    "<html>Signing in means DreamMan talks to your server.<br><br>"
                            + "You haven't agreed to that yet. Enable it now?<br>"
                            + "<i>(You can review exactly what's sent in Settings \u2192 Privacy.)</i></html>",
                    "Enable server access?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (ok != JOptionPane.YES_OPTION) return;
            Consent.set(Consent.MARKET_BROWSE, true);
            Consent.set(Consent.CLOUD_SYNC, true);
        }

        Object[] options = {"Log in", "Create account", "Forgot password", "Cancel"};
        int choice = JOptionPane.showOptionDialog(parent,
                "DreamMan account (" + serverUrl + ")",
                "Sign in", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                options, options[0]);

        if (choice == 0) doLogin(parent, serverUrl, cb);
        else if (choice == 1) doRegister(parent, serverUrl, cb);
        else if (choice == 2) doRecovery(parent, serverUrl, cb);
    }

    // ── LOGIN ──

    private static void doLogin(Component parent, String url, Callback cb) {
        JTextField user = new JTextField(18);
        JPasswordField pass = new JPasswordField(18);
        JPanel form = form2("Username:", user, "Password:", pass);
        if (JOptionPane.showConfirmDialog(parent, form, "Log in",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

        String username = user.getText().trim();
        char[] password = pass.getPassword();
        if (username.isEmpty() || password.length == 0) { toast(parent, "Enter both fields."); return; }

        runAsync(parent, "Logging in...", ex -> loginFailed(parent, url, cb, ex), () -> {
            ServerAccount server = new ServerAccount(url);
            // step 1: salts
            Map<String, Object> salts = server.vaultSalts(username);
            String kekSalt = str(salts.get("kekSalt")), authSalt = str(salts.get("authSalt"));
            // step 2: derive auth hash locally, send it
            String authHash = Vault.authHash(password.clone(), authSalt);
            Map<String, Object> res = server.vaultLogin(username, authHash);
            // step 3: unwrap the vault key with the password-derived KEK
            SecretKey kek = Vault.deriveKek(password.clone(), kekSalt);
            String wrapped = str(res.get("wrappedByKek"));
            SecretKey vaultKey = Vault.unwrapKey(wrapped, kek);
            if (vaultKey == null) throw new IllegalStateException("Wrong password.");
            String plain = Vault.decryptData(str(res.get("ciphertext")), vaultKey);
            AccountVault.unlock(vaultKey, plain == null ? "[]" : plain);
            return null;
        }, cb, "Signed in.");
    }

    // ── REGISTER ──

    private static void doRegister(Component parent, String url, Callback cb) {
        JTextField user = new JTextField(18);
        JPasswordField pass = new JPasswordField(18);
        JPasswordField pass2 = new JPasswordField(18);
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4); g.anchor = GridBagConstraints.WEST;
        g.gridx=0; g.gridy=0; form.add(new JLabel("Username:"), g); g.gridx=1; form.add(user, g);
        g.gridx=0; g.gridy=1; form.add(new JLabel("Password:"), g); g.gridx=1; form.add(pass, g);
        g.gridx=0; g.gridy=2; form.add(new JLabel("Confirm:"), g);  g.gridx=1; form.add(pass2, g);
        g.gridx=0; g.gridy=3; g.gridwidth=2;
        JLabel warn = new JLabel("<html><i>Your password encrypts your accounts. If you forget it, "
                + "only the recovery code can get them back \u2014 the server can't reset it.</i></html>");
        warn.setForeground(new Color(210, 160, 90));
        form.add(warn, g);

        if (JOptionPane.showConfirmDialog(parent, form, "Create account",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

        String username = user.getText().trim();
        char[] p1 = pass.getPassword(), p2 = pass2.getPassword();
        if (username.length() < 3) { toast(parent, "Username must be 3+ characters."); return; }
        if (p1.length < 8) { toast(parent, "Password must be 8+ characters."); return; }
        if (!java.util.Arrays.equals(p1, p2)) { toast(parent, "Passwords don't match."); return; }

        // all crypto up-front so we can show the recovery code, then register
        final String[] recoveryHolder = new String[1];
        runThen(parent, ex -> registerFailed(parent, url, cb, ex), () -> {
            String authSalt = Vault.newSalt(), kekSalt = Vault.newSalt(), recSalt = Vault.newSalt();
            String authHash = Vault.authHash(p1.clone(), authSalt);
            SecretKey kek = Vault.deriveKek(p1.clone(), kekSalt);
            SecretKey vaultKey = Vault.newVaultKey();
            String recoveryCode = Vault.newRecoveryCode();
            recoveryHolder[0] = recoveryCode;
            String wrappedByKek = Vault.wrapKey(vaultKey, kek);
            String wrappedByRec = Vault.wrapKey(vaultKey, Vault.recoveryKey(recoveryCode, recSalt));
            String emptyAccounts = Vault.encryptData("[]", vaultKey);

            Map<String, String> fields = new java.util.LinkedHashMap<>();
            fields.put("authSalt", authSalt); fields.put("kekSalt", kekSalt);
            fields.put("recoverySalt", recSalt); fields.put("authHash", authHash);
            fields.put("wrappedByKek", wrappedByKek); fields.put("wrappedByRecovery", wrappedByRec);
            fields.put("ciphertext", emptyAccounts);

            new ServerAccount(url).vaultRegister(username, fields);
            AccountVault.unlock(vaultKey, "[]");
            return null;
        }, () -> {
            showRecoveryCode(parent, username, recoveryHolder[0]);
            if (cb != null) cb.onChanged();
        });
    }

    /** The one-time recovery-code screen: copy, download .txt, and an "I've saved it" gate. */
    private static void showRecoveryCode(Component parent, String username, String code) {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(new EmptyBorder(6, 6, 6, 6));

        JLabel head = new JLabel("<html><b>Save your recovery code now.</b><br>"
                + "This is the ONLY way back into your account if you forget your password. "
                + "We can't reset it \u2014 your data is encrypted with your password.</html>");
        panel.add(head, BorderLayout.NORTH);

        JTextField codeField = new JTextField(code);
        codeField.setEditable(false);
        codeField.setFont(new Font("Consolas", Font.BOLD, 20));
        codeField.setHorizontalAlignment(JTextField.CENTER);
        panel.add(codeField, BorderLayout.CENTER);

        JButton copy = new JButton("Copy");
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(code), null);
            copy.setText("Copied \u2713");
        });
        JButton download = new JButton("Download .txt");
        download.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("dreamman-recovery-" + username + ".txt"));
            if (fc.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
                try {
                    Files.write(fc.getSelectedFile().toPath(),
                            ("DreamMan recovery code for account: " + username + "\n\n" + code
                                    + "\n\nKeep this safe. It is the only way to recover your "
                                    + "account if you forget your password.\n")
                                    .getBytes(StandardCharsets.UTF_8));
                    download.setText("Saved \u2713");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(parent, "Couldn't save: " + ex.getMessage());
                }
            }
        });
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        btns.add(copy); btns.add(download);

        JCheckBox saved = new JCheckBox("I've saved my recovery code somewhere safe");
        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.add(btns, BorderLayout.NORTH);
        south.add(saved, BorderLayout.SOUTH);
        panel.add(south, BorderLayout.SOUTH);

        // custom option pane so OK is disabled until "I've saved it" is ticked
        JOptionPane pane = new JOptionPane(panel, JOptionPane.WARNING_MESSAGE, JOptionPane.DEFAULT_OPTION);
        JButton ok = new JButton("Continue");
        ok.setEnabled(false);
        saved.addActionListener(e -> ok.setEnabled(saved.isSelected()));
        pane.setOptions(new Object[]{ok});
        JDialog dlg = pane.createDialog(parent, "Recovery code");
        ok.addActionListener(e -> dlg.dispose());
        dlg.setVisible(true);
    }

    // ── RECOVERY ──

    private static void doRecovery(Component parent, String url, Callback cb) {
        JTextField user = new JTextField(18);
        JTextField codeField = new JTextField(18);
        JPasswordField newPass = new JPasswordField(18);
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4); g.anchor = GridBagConstraints.WEST;
        g.gridx=0; g.gridy=0; form.add(new JLabel("Username:"), g); g.gridx=1; form.add(user, g);
        g.gridx=0; g.gridy=1; form.add(new JLabel("Recovery code:"), g); g.gridx=1; form.add(codeField, g);
        g.gridx=0; g.gridy=2; form.add(new JLabel("New password:"), g); g.gridx=1; form.add(newPass, g);

        if (JOptionPane.showConfirmDialog(parent, form, "Recover account",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

        String username = user.getText().trim();
        String code = codeField.getText().trim();
        char[] np = newPass.getPassword();
        if (username.isEmpty() || code.isEmpty() || np.length < 8) {
            toast(parent, "Fill all fields; new password 8+ chars."); return;
        }

        runAsync(parent, "Recovering...", ex -> recoveryFailed(parent, url, cb, ex), () -> {
            ServerAccount server = new ServerAccount(url);
            Map<String, Object> salts = server.vaultSalts(username);
            String recSalt = str(salts.get("recoverySalt"));
            Map<String, Object> rec = server.vaultRecovery(username);
            SecretKey recKey = Vault.recoveryKey(code, recSalt);
            SecretKey vaultKey = Vault.unwrapKey(str(rec.get("wrappedByRecovery")), recKey);
            if (vaultKey == null) throw new IllegalStateException("Wrong recovery code.");
            String plain = Vault.decryptData(str(rec.get("ciphertext")), vaultKey);

            // the recovery response includes a token, so we can immediately re-wrap the SAME vault
            // key under the NEW password. The data itself is never re-encrypted - only the key.
            String token = str(rec.get("token"));
            ServerAccount.session().baseUrl = url;
            ServerAccount.session().token = token;
            ServerAccount.session().username = username;

            String newAuthSalt = Vault.newSalt(), newKekSalt = Vault.newSalt();
            String newAuthHash = Vault.authHash(np.clone(), newAuthSalt);
            SecretKey newKek = Vault.deriveKek(np.clone(), newKekSalt);
            String newWrapped = Vault.wrapKey(vaultKey, newKek);

            Map<String, String> fields = new java.util.LinkedHashMap<>();
            fields.put("authSalt", newAuthSalt); fields.put("kekSalt", newKekSalt);
            fields.put("authHash", newAuthHash); fields.put("wrappedByKek", newWrapped);
            server.rewrap(fields);   // persist the new-password wrapping on the server

            AccountVault.unlock(vaultKey, plain == null ? "[]" : plain);
            return null;
        }, cb, "Recovered - your new password is now set. You're logged in.");
    }

    // ── helpers ──

    private static JPanel form2(String l1, JComponent c1, String l2, JComponent c2) {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4); g.anchor = GridBagConstraints.WEST;
        g.gridx=0; g.gridy=0; p.add(new JLabel(l1), g); g.gridx=1; p.add(c1, g);
        g.gridx=0; g.gridy=1; p.add(new JLabel(l2), g); g.gridx=1; p.add(c2, g);
        return p;
    }

    /** Convenience: run work, then toast a success message and fire the callback. */
    private static void runAsync(Component parent, String msg,
                                 java.util.function.Consumer<Throwable> onError,
                                 ThrowingSupplier work, Callback cb, String successMsg) {
        runThen(parent, onError, work, () -> {
            if (successMsg != null) toast(parent, successMsg);
            if (cb != null) cb.onChanged();
        });
    }

    /** Run work off the EDT; on success run onSuccess on the EDT; on error hand to onError. */
    private static void runThen(Component parent, java.util.function.Consumer<Throwable> onError,
                                ThrowingSupplier work, Runnable onSuccess) {
        new Thread(() -> {
            try {
                work.get();
                SwingUtilities.invokeLater(onSuccess);
            } catch (Throwable ex) {
                SwingUtilities.invokeLater(() -> {
                    if (onError != null) onError.accept(ex);
                    else JOptionPane.showMessageDialog(parent, friendly(ex),
                            "Couldn't complete", JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "DreamMan-Auth").start();
    }

    // ── v1.30: human error handling ──────────────────────────────────────────
    // The old path surfaced raw exception text, so a dead DNS lookup showed the naked
    // hostname ("market.dreamman.app") in an error box. Failures are now classified:
    // wrong-credentials errors say so and offer the two ways forward; network failures say
    // "couldn't reach the server" - because telling someone with no internet that their
    // password is wrong sends them on a reset quest that can't help.

    /** True for "the wire is broken" failures, as opposed to "the server said no". */
    private static boolean isNetworkIssue(Throwable ex) {
        return ex instanceof java.net.UnknownHostException
                || ex instanceof java.net.ConnectException
                || ex instanceof java.net.SocketTimeoutException
                || ex instanceof java.net.NoRouteToHostException
                || ex instanceof javax.net.ssl.SSLException;
    }

    /** True when the failure means the username/password pair was rejected. */
    private static boolean isBadCredentials(Throwable ex) {
        if (ex instanceof ServerAccount.HttpError)
            return ((ServerAccount.HttpError) ex).looksLikeBadCredentials();
        // Vault.unwrapKey failing = right account, wrong password (thrown as this message)
        return ex instanceof IllegalStateException
                && String.valueOf(ex.getMessage()).toLowerCase().contains("wrong");
    }

    private static String friendly(Throwable ex) {
        if (isNetworkIssue(ex))
            return "Couldn't reach the DreamMan server.\nCheck your internet connection "
                    + "(or the server may be down) and try again.";
        String m = ex.getMessage();
        return m == null || m.isBlank() ? ex.toString() : m;
    }

    /** Login failed: invalid credentials get the requested guidance; the rest stay honest. */
    private static void loginFailed(Component parent, String url, Callback cb, Throwable ex) {
        if (isBadCredentials(ex)) {
            Object[] options = {"Try again", "Forgot password", "Create account", "Cancel"};
            int pick = JOptionPane.showOptionDialog(parent,
                    "<html><b>Invalid username or password.</b><br><br>"
                            + "Check both and try again \u2014 or use your recovery code via "
                            + "<i>Forgot password</i>, or make a new account.</html>",
                    "Couldn't sign in", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.ERROR_MESSAGE, null, options, options[0]);
            if (pick == 0) doLogin(parent, url, cb);
            else if (pick == 1) doRecovery(parent, url, cb);
            else if (pick == 2) doRegister(parent, url, cb);
            return;
        }
        JOptionPane.showMessageDialog(parent, friendly(ex),
                "Couldn't sign in", JOptionPane.ERROR_MESSAGE);
    }

    /** Register failed: taken names and bad requests show the server's reason; wire issues don't lie. */
    private static void registerFailed(Component parent, String url, Callback cb, Throwable ex) {
        if (ex instanceof ServerAccount.HttpError && ((ServerAccount.HttpError) ex).status == 409) {
            Object[] options = {"Try again", "Log in instead", "Cancel"};
            int pick = JOptionPane.showOptionDialog(parent,
                    "<html><b>That username is already taken.</b><br>"
                            + "Pick another name \u2014 or if it's yours, just log in.</html>",
                    "Couldn't create account", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.ERROR_MESSAGE, null, options, options[0]);
            if (pick == 0) doRegister(parent, url, cb);
            else if (pick == 1) doLogin(parent, url, cb);
            return;
        }
        JOptionPane.showMessageDialog(parent, friendly(ex),
                "Couldn't create account", JOptionPane.ERROR_MESSAGE);
    }

    /** Recovery failed: a wrong code says so plainly. */
    private static void recoveryFailed(Component parent, String url, Callback cb, Throwable ex) {
        if (isBadCredentials(ex) || (ex instanceof IllegalStateException)) {
            Object[] options = {"Try again", "Cancel"};
            int pick = JOptionPane.showOptionDialog(parent,
                    "<html><b>That recovery code didn't match.</b><br>"
                            + "Codes are the long dashed string shown when the account was "
                            + "created \u2014 check for typos and try again.</html>",
                    "Couldn't recover", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.ERROR_MESSAGE, null, options, options[0]);
            if (pick == 0) doRecovery(parent, url, cb);
            return;
        }
        JOptionPane.showMessageDialog(parent, friendly(ex),
                "Couldn't recover", JOptionPane.ERROR_MESSAGE);
    }

    private interface ThrowingSupplier { Object get() throws Exception; }

    private static String str(Object o) { return o == null ? "" : o.toString(); }
    private static void toast(Component parent, String m) {
        JOptionPane.showMessageDialog(parent, m);
    }
}
