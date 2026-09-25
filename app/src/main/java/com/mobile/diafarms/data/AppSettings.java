package com.mobile.diafarms.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.mobile.diafarms.network.Constants;

/**
 * Préférences non sensibles de l'app. Sert notamment à rendre l'adresse du serveur
 * backend configurable depuis l'écran Diagnostics, sans devoir recompiler l'app à
 * chaque changement d'IP réseau (Constants.BASE_URL reste la valeur par défaut).
 */
public class AppSettings {

    private static final String PREFS_NAME = "diafarms_settings";
    private static final String KEY_SERVER_URL = "server_url";

    private final SharedPreferences prefs;

    public AppSettings(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getServerUrl() {
        return prefs.getString(KEY_SERVER_URL, Constants.BASE_URL);
    }

    public void setServerUrl(String url) {
        String normalized = url.trim();
        if (!normalized.isEmpty() && !normalized.endsWith("/")) {
            normalized += "/";
        }
        prefs.edit().putString(KEY_SERVER_URL, normalized).apply();
    }

    public void resetServerUrl() {
        prefs.edit().remove(KEY_SERVER_URL).apply();
    }

    /**
     * Adresse du site web qui publie les mises à jour de l'APK. Déduite du serveur
     * configuré quand il suit la convention "xxx.back.domaine" (le web étant alors
     * "xxx.domaine"), sinon (serveur par défaut, IP de développement locale...) le site
     * de production : une mise à jour doit toujours pouvoir être récupérée.
     */
    public String getWebUrl() {
        if (isDefaultServerUrl()) return Constants.WEB_URL;
        try {
            android.net.Uri uri = android.net.Uri.parse(getServerUrl());
            String host = uri.getHost();
            String scheme = uri.getScheme();
            if (host != null && scheme != null && host.contains(".back.")) {
                return scheme + "://" + host.replace(".back.", ".") + "/";
            }
        } catch (Exception ignored) {
        }
        return Constants.WEB_URL;
    }

    public boolean isDefaultServerUrl() {
        return !prefs.contains(KEY_SERVER_URL);
    }
}
