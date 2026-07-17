package com.mobile.diafarms.crypto;

import android.util.Base64;

import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;

/**
 * Décode localement les claims d'un JWT (partie payload uniquement, sans vérifier la
 * signature) — utilisé pour lire fullName/role/uniqueId du token embarqué dans le QR
 * sans appeler le serveur. Ne PAS utiliser pour valider un token vis-à-vis du backend
 * (aucune vérification cryptographique de signature ici) : la confiance vient du canal
 * QR déjà chiffré en AES bout en bout par le backend, pas de ce décodage.
 */
public class JwtHelper {

    public static QrJwtClaims decodeClaims(String jwt) throws IllegalArgumentException {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("JWT invalide (pas assez de segments)");
        }
        byte[] decoded = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        String json = new String(decoded, StandardCharsets.UTF_8);
        return new Gson().fromJson(json, QrJwtClaims.class);
    }

    private JwtHelper() {
    }
}
