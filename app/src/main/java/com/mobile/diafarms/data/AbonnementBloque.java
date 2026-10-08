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
    // Par ferme (SessionManager.activeFarmId) : plusieurs comptes, de fermes différentes,
    // peuvent partager le téléphone ; le blocage de l'une ne doit jamais s'afficher pour l'autre.
    private static final String CLE_ETAT = "etat_";
    private static final String CLE_MESSAGE = "message_";

    private AbonnementBloque() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String ferme(Context c) {
        String f = SessionManager.activeFarmId(c);
        return f == null || f.isEmpty() ? null : f;
    }

    /** Pour écouter les changements (l'écouteur est appelé sur le fil principal). */
    public static SharedPreferences preferences(Context c) {
        return prefs(c);
    }

    public static boolean estBloque(Context c) {
        return etat(c) != null;
    }

    /** SUSPENDU, EXPIRE ou null, pour la ferme du compte actif. */
    public static String etat(Context c) {
        String f = ferme(c);
        return f == null ? null : prefs(c).getString(CLE_ETAT + f, null);
    }

    /** Message à afficher, toujours avec le contact WhatsApp. */
    public static String message(Context c) {
        String f = ferme(c);
        String m = f == null ? null : prefs(c).getString(CLE_MESSAGE + f, null);
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

    /** farmId : ferme du compte qui a fait la requête (lue AVANT l'envoi). */
    public static void marquer(Context c, String farmId, String etat, String message) {
        if (farmId == null || farmId.isEmpty()) return;
        SharedPreferences p = prefs(c);
        String e = "SUSPENDU".equalsIgnoreCase(etat) ? "SUSPENDU" : "EXPIRE";
        boolean change = !e.equals(p.getString(CLE_ETAT + farmId, null))
                || (message != null && !message.equals(p.getString(CLE_MESSAGE + farmId, null)));
        if (!change) return;
        SharedPreferences.Editor ed = p.edit();
        if (message != null) ed.putString(CLE_MESSAGE + farmId, message);
        // L'état en dernier : un écouteur de l'état lit déjà le bon message.
        ed.putString(CLE_ETAT + farmId, e).apply();
    }

    public static void lever(Context c, String farmId) {
        if (farmId == null || farmId.isEmpty()) return;
        SharedPreferences p = prefs(c);
        if (p.getString(CLE_ETAT + farmId, null) == null && p.getString(CLE_MESSAGE + farmId, null) == null) return;
        p.edit().remove(CLE_ETAT + farmId).remove(CLE_MESSAGE + farmId).apply();
    }

    /** Ferme du compte actif (déconnexion). */
    public static void lever(Context c) {
        lever(c, ferme(c));
    }

    /** true si une alerte est celle du blocage (clé « abonnement-bloque-AAAA-MM-JJ »). */
    public static boolean estAlerteBlocage(String cle) {
        return cle != null && cle.startsWith("abonnement-bloque-");
    }
}
