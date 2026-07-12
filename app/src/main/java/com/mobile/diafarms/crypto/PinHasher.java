package com.mobile.diafarms.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Hachage du code d'accès rapide local (déverrouillage de l'app sans re-scanner un QR
 * ni réseau). Ce n'est pas un secret protégeant un accès distant — juste un verrou de
 * confort sur un token déjà stocké chiffré (SessionManager) — donc SHA-256 + sel fixe
 * suffit très largement ici, pas besoin de bcrypt/Argon2.
 */
public class PinHasher {

    private static final String SALT = "diafarms-local-pin-v1";

    public static String hash(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((SALT + pin).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    public static boolean matches(String pin, String hash) {
        if (pin == null || hash == null) return false;
        return hash.equals(hash(pin));
    }

    private PinHasher() {
    }
}
