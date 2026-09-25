package com.mobile.diafarms.network;

/**
 * Serveur de production (cocorico.back.batimanager.net) — même backend que le web.
 * Reste modifiable en direct depuis l'écran Diagnostics (AppSettings.setServerUrl)
 * pour un usage en développement local, sans avoir à recompiler l'app.
 */
public class Constants {

    public static final String BASE_URL = "https://cocorico.back.batimanager.net/diafarms/api/v1/";

    // Site web (frontend) : c'est lui qui publie l'APK et sa fiche de version pour la
    // mise à jour directe depuis l'app (voir update/UpdateChecker). Voir aussi
    // AppSettings.getWebUrl, qui le déduit de l'adresse serveur quand elle a été changée.
    public static final String WEB_URL = "https://cocorico.batimanager.net/";
    public static final String UPDATE_VERSION_PATH = "app/version.json";
    public static final String UPDATE_APK_PATH = "app/cocorico.apk";

    // Doit rester identique à AES_SECRET_KEY dans diafarms_back/.env : c'est la même
    // clé qui sert à chiffrer le QR côté serveur (QRCodeController/AESService) et à le
    // déchiffrer ici. Si la clé change côté back, il faut la reporter ici.
    public static final String AES_SECRET_KEY = "csBWkF0PKbczoBQw/l9ne8G4Meq7Fxkc";

    private Constants() {
    }
}
