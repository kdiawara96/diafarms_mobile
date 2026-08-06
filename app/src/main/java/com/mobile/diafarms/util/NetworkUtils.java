package com.mobile.diafarms.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

/**
 * Statut réseau réel de l'appareil (pas juste "l'appel a échoué") — utilisé pour
 * l'indicateur En ligne/Hors ligne de l'accueil. NET_CAPABILITY_VALIDATED exclut un
 * Wi-Fi connecté mais sans sortie internet effective (portail captif, routeur en
 * panne), sinon l'indicateur resterait vert à tort.
 */
public class NetworkUtils {

    public static boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private NetworkUtils() {
    }
}
