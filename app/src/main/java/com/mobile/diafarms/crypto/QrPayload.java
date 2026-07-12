package com.mobile.diafarms.crypto;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Miroir de QrCodeEncrypte côté backend (com.diafarms.ml.DTO.QrCodeEncrypte) : c'est
 * le JSON obtenu après déchiffrement AES du contenu du QR (voir AESHelper). Volontairement
 * minimal (role/fullNameUser/qrGeneratedAt ont été retirés côté back) pour que le QR reste
 * scannable — le profil complet est de toute façon récupéré via /auth/me après validation.
 */
public class QrPayload {
    private String qrExpiresAt;    // "dd-MM-yy HH:mm", absent/null = QR permanent
    private String uniqueIdUser;
    private String token;          // JWT signé, utilisable tel quel comme Bearer token

    public String getQrExpiresAt() { return qrExpiresAt; }
    public String getUniqueIdUser() { return uniqueIdUser; }
    public String getToken() { return token; }

    public boolean isValid() {
        return uniqueIdUser != null && !uniqueIdUser.isEmpty()
                && token != null && !token.isEmpty();
    }

    public boolean isExpired() {
        if (qrExpiresAt == null || qrExpiresAt.trim().isEmpty()) {
            return false; // pas de date d'expiration = QR permanent
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yy HH:mm", Locale.FRANCE);
            Date expiry = sdf.parse(qrExpiresAt.trim());
            return expiry != null && expiry.before(new Date());
        } catch (ParseException e) {
            return false; // format inattendu : on ne bloque pas l'utilisateur pour ça
        }
    }
}
