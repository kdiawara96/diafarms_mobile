package com.mobile.diafarms.network;

/**
 * IP LAN de la machine qui fait tourner diafarms_back en dev (10.0.2.2 = alias de
 * l'hôte depuis l'émulateur Android). À adapter selon le réseau de test, et à
 * synchroniser avec network_security_config.xml (cleartext HTTP autorisé pour
 * cette IP uniquement).
 */
public class Constants {

    public static final String BASE_URL = "http://192.168.1.18:8093/diafarms/api/v1/";

    // Doit rester identique à AES_SECRET_KEY dans diafarms_back/.env : c'est la même
    // clé qui sert à chiffrer le QR côté serveur (QRCodeController/AESService) et à le
    // déchiffrer ici. Si la clé change côté back, il faut la reporter ici.
    public static final String AES_SECRET_KEY = "csBWkF0PKbczoBQw/l9ne8G4Meq7Fxkc";

    private Constants() {
    }
}
