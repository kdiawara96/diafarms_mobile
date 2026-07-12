package com.mobile.diafarms.crypto;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Miroir de QrCodeEncrypte côté backend (com.diafarms.ml.DTO.QrCodeEncrypte) : c'est
 * le JSON obtenu après déchiffrement AES du contenu du QR (voir AESHelper).
 */
public class QrPayload {
    private String qrGeneratedAt;  // "dd-MM-yy HH:mm"
    private String qrExpiresAt;    // "dd-MM-yy HH:mm", absent/null = QR permanent
    private String role;           // rôles séparés par "|", ex: "PRODUCTEUR|FINANCIER"
    private String uniqueIdUser;
    private String fullNameUser;
    private String token;          // JWT signé, utilisable tel quel comme Bearer token

    public String getQrGeneratedAt() { return qrGeneratedAt; }
    public String getQrExpiresAt() { return qrExpiresAt; }
    public String getRole() { return role; }
    public String getUniqueIdUser() { return uniqueIdUser; }
    public String getFullNameUser() { return fullNameUser; }
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
