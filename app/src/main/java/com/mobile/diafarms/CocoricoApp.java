package com.mobile.diafarms;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

/** Mode clair forcé dès le démarrage du processus, avant toute activité : l'appli garde
 * les mêmes couleurs quand le téléphone est en mode sombre, y compris quand Android la
 * rouvre directement sur un écran autre que LauncherScreen. */
public class CocoricoApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }
}
