package com.mobile.diafarms.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Hachage du mot de passe local (accès hors ligne à l'app, défini juste après un scan
 * QR — voir CameraScanActivity). Ce n'est pas un secret protégeant un accès distant —
 * juste un verrou sur un token déjà stocké chiffré (SessionManager) — donc SHA-256 +
 * sel fixe suffit très largement ici, pas besoin de bcrypt/Argon2.
 */
public class LocalPasswordHasher {

    private static final String SALT = "diafarms-local-password-v1";

    public static String hash(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((SALT + password).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    public static boolean matches(String password, String hash) {
        if (password == null || hash == null) return false;
        return hash.equals(hash(password));
    }

    private LocalPasswordHasher() {
    }
}
