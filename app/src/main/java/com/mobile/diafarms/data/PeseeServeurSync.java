package com.mobile.diafarms.data;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;
import com.mobile.diafarms.R;
import com.mobile.diafarms.activity.PeseeSessionActivity;
import com.mobile.diafarms.models.SaisieLocale;
import com.mobile.diafarms.models.SaisieType;
import com.mobile.diafarms.network.dto.SessionPeseeServeur;
import com.mobile.diafarms.network.dto.SessionPeseeSyncRequest;

import java.util.List;

/**
 * Application de l'état serveur d'une session de pesée sur sa ligne locale
 * (saisies_locales, type PESEE_SESSION), après un envoi (SyncManager) ou une lecture du
 * détail (PeseeSessionActivity). Toute la logique de fusion est dans
 * SessionPeseeSyncRequest.fusionner ; ici : lecture de la ligne actuelle, écriture du
 * résultat (SYNCED s'il ne reste rien à envoyer, LOCAL sinon) et notifications.
 * Appelé uniquement sur le thread principal (callbacks Retrofit), comme l'écran de pesée :
 * lecture + écriture sans entrelacement.
 */
public final class PeseeServeurSync {

    private static final Gson GSON = new Gson();
    // AlertCheckWorker notifie sans tag avec des id 1000..10999 : ici un tag par session
    // (localId complet), aucun chevauchement possible.
    private static final String TAG_MODIFS = "pesee_modifs_";
    private static final String TAG_REFUS = "pesee_refus_";
    private static final int ID_PESEE = 1;

    private PeseeServeurSync() {}

    /**
     * Fusionne {@code serveur} dans la ligne {@code localId}. {@code envoye} = instantané
     * envoyé (synchro) ou null (simple lecture). {@code notifier} : notification Android
     * pour les nouveaux événements web et les pesées refusées (false quand l'écran de la
     * session est affiché : il montre déjà tout). Retourne null si la ligne n'existe plus
     * ou est illisible.
     */
    public static SessionPeseeSyncRequest.Fusion appliquer(Context context, LocalDatabase db, String localId,
                                                          SessionPeseeSyncRequest envoye, SessionPeseeServeur serveur,
                                                          boolean notifier) {
        SaisieLocale actuelle = db.getSaisieById(localId);
        if (actuelle == null) return null; // supprimée du téléphone entre-temps
        SessionPeseeSyncRequest courant;
        try {
            courant = GSON.fromJson(actuelle.getPayloadJson(), SessionPeseeSyncRequest.class);
        } catch (Exception e) {
            courant = null;
        }
        if (courant == null) return null;
        SessionPeseeSyncRequest.Fusion f = SessionPeseeSyncRequest.fusionner(envoye, courant, serveur);
        if (f.ignoree) return f; // lecture périmée : rien à écrire ni à notifier
        String json = GSON.toJson(f.etat);
        String uidServeur = serveur != null && serveur.uniqueId != null ? serveur.uniqueId : courant.uniqueId;
        boolean dejaAJour = json.equals(actuelle.getPayloadJson())
                && SaisieLocale.STATUT_SYNCED.equals(actuelle.getSyncStatus()) == f.aJour
                && java.util.Objects.equals(uidServeur, actuelle.getServerUniqueId());
        if (!dejaAJour) {
            db.enregistrerApresEnvoi(localId, uidServeur, json, PeseeSessionActivity.resume(f.etat), f.aJour);
        }
        if (notifier) notifier(context, localId, f);
        return f;
    }

    /** Ligne locale d'une session (par uniqueId de session), ou null. */
    public static SaisieLocale trouverLigne(LocalDatabase db, String sessionUniqueId) {
        if (sessionUniqueId == null) return null;
        for (SaisieLocale s : db.getSaisiesByType(SaisieType.PESEE_SESSION)) {
            try {
                SessionPeseeSyncRequest req = GSON.fromJson(s.getPayloadJson(), SessionPeseeSyncRequest.class);
                if (req != null && sessionUniqueId.equals(req.uniqueId)) return s;
            } catch (Exception ignored) {
                // ligne illisible : ignorée
            }
        }
        return null;
    }

    /** Importe une session connue du serveur seulement (créée sur le web ou sur un autre
     * téléphone) : nouvelle ligne SYNCED, toutes ses pesées envoyées ; elle fonctionne
     * ensuite hors ligne comme une session créée ici. Retourne son localId (celui de la
     * ligne existante si elle a déjà été importée). */
    public static String importer(LocalDatabase db, SessionPeseeServeur serveur, String projetLabel) {
        SaisieLocale existante = trouverLigne(db, serveur.uniqueId);
        if (existante != null) return existante.getLocalId();
        SessionPeseeSyncRequest req = SessionPeseeSyncRequest.depuisServeur(serveur);
        String json = GSON.toJson(req);
        String resume = PeseeSessionActivity.resume(req);
        String localId = db.insertSaisie(SaisieType.PESEE_SESSION, serveur.projetUniqueId, projetLabel, json, resume);
        db.enregistrerApresEnvoi(localId, serveur.uniqueId, json, resume, true);
        db.deleteCache(CachePrefetcher.CACHE_PESEE_DETAIL_PREFIX + serveur.uniqueId);
        return localId;
    }

    // ===================== NOTIFICATIONS =====================

    private static void notifier(Context context, String localId, SessionPeseeSyncRequest.Fusion f) {
        List<SessionPeseeSyncRequest.EvenementWeb> nouveaux = f.nouveauxEvenements;
        if (!nouveaux.isEmpty()) {
            SessionPeseeSyncRequest.EvenementWeb dernier = nouveaux.get(nouveaux.size() - 1);
            String texte = dernier.description != null ? dernier.description : "Modification faite sur le web";
            if (nouveaux.size() > 1) {
                int autres = nouveaux.size() - 1;
                texte += " et " + autres + (autres > 1 ? " autres modifications" : " autre modification");
            }
            poster(context, localId, TAG_MODIFS, "Session de pesée modifiée", texte);
        }
        if (f.nouvellesRefusees > 0) {
            // Terminée par le web (ou un autre appareil) pendant qu'on pesait, ou déjà
            // terminée par ce téléphone (pesées ajoutées pendant l'envoi de la clôture).
            String titre = f.termineeParServeur ? "Session de pesée terminée sur le web" : "Session de pesée déjà terminée";
            String texte = (f.termineeParServeur ? "Session terminée sur le web : " : "Session déjà terminée : ")
                    + f.nouvellesRefusees + " pesée(s) non enregistrée(s)";
            poster(context, localId, TAG_REFUS, titre, texte);
        }
    }

    private static void poster(Context context, String localId, String tagBase, String titre, String texte) {
        Context app = context.getApplicationContext();
        boolean permission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ActivityCompat.checkSelfPermission(app, android.Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        if (!permission) return;
        AlertCheckWorker.creerCanalSiBesoin(app);

        Intent intent = new Intent(app, PeseeSessionActivity.class);
        intent.putExtra(PeseeSessionActivity.EXTRA_LOCAL_ID, localId);
        // data unique par session : deux sessions n'écrasent jamais le PendingIntent l'une
        // de l'autre (les extras ne comptent pas dans l'égalité des Intent).
        intent.setData(android.net.Uri.parse("diafarms://pesee/" + tagBase + localId));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(app, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(app, AlertCheckWorker.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_small)
                .setLargeIcon(android.graphics.BitmapFactory.decodeResource(app.getResources(), R.drawable.ic_pesee))
                .setColor(ContextCompat.getColor(app, R.color.green_bright))
                .setContentTitle(titre)
                .setContentText(texte)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(texte))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pi);
        try {
            NotificationManagerCompat.from(app).notify(tagBase + localId, ID_PESEE, b.build());
        } catch (SecurityException ignored) {
            // permission retirée entre-temps
        }
    }
}
