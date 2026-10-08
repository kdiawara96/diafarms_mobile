package com.mobile.diafarms.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Ferme bloquée (abonnement suspendu, ou terminé après la grâce), vue par ce téléphone.
 *
 * Le serveur (MobileAbonnementFilter) refuse alors les LECTURES : 403 sur un GET, ou une
 * seule alerte sur /notifications/list, avec l'en-tête X-Abonnement-Bloque: SUSPENDU|EXPIRE.
 * Les ENVOIS de saisies restent acceptés : rien ici ne touche aux saisies locales ni à la
 * synchronisation. Détecté et levé par ApiClient (AbonnementInterceptor) : posé à la
 * première lecture refusée, retiré dès qu'une lecture normale réussit. L'accueil
 * (HomeActivity) affiche alors un bandeau et masque les chiffres en cache, devenus anciens.
 */
public final class AbonnementBloque {

    public static final String WHATSAPP = "+223 83 91 86 99";
    public static final String WHATSAPP_LIEN = "https://wa.me/22383918699";

    private static final String PREFS = "abonnement_bloque";
    public static final String CLE_ETAT = "etat";
    private static final String CLE_MESSAGE = "message";

    private AbonnementBloque() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Pour écouter les changements (l'écouteur est appelé sur le fil principal). */
    public static SharedPreferences preferences(Context c) {
        return prefs(c);
    }

    public static boolean estBloque(Context c) {
        return prefs(c).getString(CLE_ETAT, null) != null;
    }

    /** SUSPENDU, EXPIRE ou null. */
    public static String etat(Context c) {
        return prefs(c).getString(CLE_ETAT, null);
    }

    /** Message à afficher, toujours avec le contact WhatsApp. */
    public static String message(Context c) {
        String m = prefs(c).getString(CLE_MESSAGE, null);
        if (m == null || m.trim().isEmpty()) {
            m = "SUSPENDU".equals(etat(c))
                    ? "L'accès de votre ferme est suspendu : les données ne sont plus mises à jour sur ce téléphone. "
                        + "Vos saisies sont toujours envoyées, rien n'est perdu."
                    : "L'abonnement de votre ferme est terminé : les données ne sont plus mises à jour sur ce téléphone. "
                        + "Vos saisies sont toujours envoyées, rien n'est perdu.";
        }
        if (!m.contains(WHATSAPP)) m = m + "\nWhatsApp : " + WHATSAPP;
        return m;
    }

    public static void marquer(Context c, String etat, String message) {
        SharedPreferences p = prefs(c);
        String e = "SUSPENDU".equalsIgnoreCase(etat) ? "SUSPENDU" : "EXPIRE";
        boolean change = !e.equals(p.getString(CLE_ETAT, null))
                || (message != null && !message.equals(p.getString(CLE_MESSAGE, null)));
        if (!change) return;
        SharedPreferences.Editor ed = p.edit();
        if (message != null) ed.putString(CLE_MESSAGE, message);
        // L'état en dernier : un écouteur de CLE_ETAT lit déjà le bon message.
        ed.putString(CLE_ETAT, e).apply();
    }

    public static void lever(Context c) {
        SharedPreferences p = prefs(c);
        if (p.getString(CLE_ETAT, null) == null) return;
        p.edit().remove(CLE_ETAT).remove(CLE_MESSAGE).apply();
    }
}
