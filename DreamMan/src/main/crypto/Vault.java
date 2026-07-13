package main.crypto;

import org.dreambot.api.utilities.Logger;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Zero-knowledge vault (Patch B.13). The server stores only ciphertext it cannot read; your
 * password never leaves your machine.
 *
 * <p><b>The scheme (this is the whole security model, so it's written out):</b>
 * <ol>
 *   <li>Your password is stretched with PBKDF2 twice, under two different salts:
 *       <ul>
 *         <li><b>auth_hash</b> — proves you're you. This is all the server ever sees of your
 *             password, and it's a one-way hash of a hash, useless for decryption.</li>
 *         <li><b>KEK</b> (key-encrypting key) — never leaves this device, never stored anywhere.</li>
 *       </ul></li>
 *   <li>A random <b>vault key</b> encrypts your actual data (AES-256-GCM). The vault key is
 *       <i>wrapped</i> (encrypted) by the KEK and the wrapped form is stored on the server.</li>
 *   <li>New device: same password → same KEK → unwrap the vault key → decrypt. <b>Portable</b> —
 *       this is why we derive the key from the password instead of generating a device key that
 *       would be stranded when you switch machines (which was the right worry).</li>
 *   <li>Change password: re-wrap the vault key under a new KEK. The data itself isn't touched.</li>
 *   <li><b>Recovery code:</b> the vault key is <i>also</i> wrapped by a random recovery code shown
 *       once at signup. It's the only other way in. Lose both password and recovery code and the
 *       data is unrecoverable — that's not a flaw, it's what "the server can't read it" costs.</li>
 * </ol>
 *
 * <p>Everything here is plain JDK (PBKDF2-SHA256 + AES-GCM) — no BouncyCastle, so nothing new in
 * the jar.
 */
public final class Vault {

    private Vault() {}

    private static final SecureRandom RNG = new SecureRandom();
    private static final int PBKDF2_ITERS = 210_000;   // OWASP floor for PBKDF2-SHA256
    private static final int KEY_BITS = 256;
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;
    private static final int SALT_BYTES = 16;

    // ── salts (generated once per account, stored server-side; they're not secret) ──

    public static String newSalt() {
        byte[] s = new byte[SALT_BYTES];
        RNG.nextBytes(s);
        return b64(s);
    }

    // ── the two derivations from the password ──

    /**
     * The value the server stores to check your password. Derived under a DIFFERENT salt/context
     * than the KEK, so even the server's copy tells an attacker nothing about the encryption key.
     */
    public static String authHash(char[] password, String authSalt) {
        byte[] h = pbkdf2(password, decode(authSalt), PBKDF2_ITERS, KEY_BITS);
        // one more hash so what the server holds isn't even the raw PBKDF2 output
        return b64(pbkdf2(b64(h).toCharArray(), decode(authSalt), 1, KEY_BITS));
    }

    /** The key-encrypting key. NEVER leaves the device, NEVER stored. Held only in memory. */
    public static SecretKey deriveKek(char[] password, String kekSalt) {
        byte[] k = pbkdf2(password, decode(kekSalt), PBKDF2_ITERS, KEY_BITS);
        return new SecretKeySpec(k, "AES");
    }

    // ── the vault key ──

    /** A fresh random data-encryption key, created once when the account is set up. */
    public static SecretKey newVaultKey() {
        byte[] k = new byte[KEY_BITS / 8];
        RNG.nextBytes(k);
        return new SecretKeySpec(k, "AES");
    }

    /** Wraps (encrypts) the vault key with a wrapping key (the KEK, or a recovery key). */
    public static String wrapKey(SecretKey vaultKey, SecretKey wrappingKey) {
        return encrypt(vaultKey.getEncoded(), wrappingKey);
    }

    /** Unwraps the vault key. Returns null if the wrapping key is wrong (bad password). */
    public static SecretKey unwrapKey(String wrapped, SecretKey wrappingKey) {
        byte[] raw = decrypt(wrapped, wrappingKey);
        return raw == null ? null : new SecretKeySpec(raw, "AES");
    }

    // ── recovery code ──

    /** A human-transcribable recovery code, shown once. e.g. "K7QM-2F9X-...". */
    public static String newRecoveryCode() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no I/O/0/1
        StringBuilder sb = new StringBuilder();
        for (int g = 0; g < 4; g++) {
            if (g > 0) sb.append('-');
            for (int i = 0; i < 4; i++) sb.append(alphabet.charAt(RNG.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /** Turns a recovery code into a wrapping key (own salt/context). */
    public static SecretKey recoveryKey(String recoveryCode, String recSalt) {
        byte[] k = pbkdf2(recoveryCode.replace("-", "").toCharArray(), decode(recSalt), PBKDF2_ITERS, KEY_BITS);
        return new SecretKeySpec(k, "AES");
    }

    // ── the actual data encryption (what protects your account names) ──

    /** Encrypts plaintext with the vault key. Output is IV+ciphertext, base64. */
    public static String encryptData(String plaintext, SecretKey vaultKey) {
        return encrypt(plaintext.getBytes(StandardCharsets.UTF_8), vaultKey);
    }

    /** Decrypts. Returns null on any failure (wrong key, tampered data — GCM catches both). */
    public static String decryptData(String blob, SecretKey vaultKey) {
        byte[] raw = decrypt(blob, vaultKey);
        return raw == null ? null : new String(raw, StandardCharsets.UTF_8);
    }

    // ── primitives ──

    private static String encrypt(byte[] plain, SecretKey key) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            RNG.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = c.doFinal(plain);
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return b64(out);
        } catch (Exception e) {
            Logger.log(Logger.LogType.WARN, "[Vault] encrypt failed: " + e);
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    private static byte[] decrypt(String blob, SecretKey key) {
        try {
            byte[] in = decode(blob);
            byte[] iv = new byte[GCM_IV_BYTES];
            byte[] ct = new byte[in.length - GCM_IV_BYTES];
            System.arraycopy(in, 0, iv, 0, GCM_IV_BYTES);
            System.arraycopy(in, GCM_IV_BYTES, ct, 0, ct.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return c.doFinal(ct);
        } catch (Exception e) {
            // wrong key or tampered ciphertext both land here — that's the point of GCM
            return null;
        }
    }

    private static byte[] pbkdf2(char[] pw, byte[] salt, int iters, int bits) {
        try {
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return f.generateSecret(new PBEKeySpec(pw, salt, iters, bits)).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 unavailable", e);
        }
    }

    private static String b64(byte[] b) { return Base64.getEncoder().encodeToString(b); }
    private static byte[] decode(String s) { return Base64.getDecoder().decode(s); }
}
