package com.mobile.diafarms.crypto;

import java.util.ArrayList;
import java.util.List;

/**
 * Miroir des claims du JWT émis pour le QR (voir QRCodeService.generateAndEncryptQRCode
 * côté backend) : sub=username, uniqueId, fullName, role (rôles séparés par "|"), exp
 * (expiration epoch secondes). Lu directement depuis le JWT décodé localement (voir
 * JwtHelper) — on fait confiance à ce contenu sans vérifier la signature, car le QR
 * qui le porte a déjà été chiffré de bout en bout par le backend (même clé AES que
 * AESHelper/Constants.AES_SECRET_KEY) : le canal est déjà de confiance.
 */
public class QrJwtClaims {
    private String sub;
    private String uniqueId;
    private String fullName;
    private String role;
    private long exp;

    public String getSub() { return sub; }
    public String getUniqueId() { return uniqueId; }
    public String getFullName() { return fullName; }
    public String getRole() { return role; }
    public long getExp() { return exp; }

    public List<String> getRoles() {
        List<String> roles = new ArrayList<>();
        if (role != null) {
            for (String r : role.split("\\|")) {
                if (!r.trim().isEmpty()) roles.add(r.trim());
            }
        }
        return roles;
    }

    public boolean isExpired() {
        return exp > 0 && System.currentTimeMillis() / 1000 >= exp;
    }
}
