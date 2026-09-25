package com.mobile.diafarms.data;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.mobile.diafarms.R;
import com.mobile.diafarms.activity.HomeActivity;
import com.mobile.diafarms.network.ApiClient;
import com.mobile.diafarms.network.dto.ApiEnvelope;
import com.mobile.diafarms.network.dto.NotificationResponse;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import retrofit2.Response;

/**
 * Vérification périodique des alertes ferme (mortalité, stock, échéances — voir
 * NotificationServiceImpl côté back) en arrière-plan, pour déclencher une
 * notification locale sur le téléphone même si l'app n'est pas ouverte.
 *
 * Ce n'est PAS du vrai push (pas de Firebase/FCM) : c'est un contrôle périodique
 * (WorkManager, minimum 15 min imposé par Android) qui appelle le même endpoint que
 * la liste d'alertes déjà affichée dans l'app (GET /notifications/list), tant que le
 * téléphone a du réseau. Un délai de quelques minutes/dizaines de minutes est donc
 * normal et attendu — largement suffisant pour une alerte de ferme (pas une urgence
 * "il faut réagir à la seconde").
 *
 * "Déjà notifiée" est suivi par clé stable (NotificationResponse.key) dans des
 * SharedPreferences classiques (pas de contenu sensible) — une alerte lue (côté web
 * ou dans l'app) n'est jamais re-notifiée, et une alerte qui redevient active après
 * avoir disparu peut re-déclencher une notification plus tard.
 */
public class AlertCheckWorker extends Worker {

    static final String CHANNEL_ID = "cocorico_alertes";
    private static final String PREFS_NAME = "alert_check_prefs";
    private static final String KEY_SEEN = "seen_notification_keys";
    private static final String WORK_NAME = "alert-check";

    public AlertCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** À appeler une fois l'utilisateur connecté (voir HomeActivity.onCreate) — sans
     * effet si déjà enregistré (KEEP), pas de doublon de vérification à chaque
     * ouverture de l'app. */
    public static void enregistrer(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                AlertCheckWorker.class, 15, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (!new SessionManager(context).isLoggedIn()) {
            return Result.success();
        }

        try {
            Response<ApiEnvelope<List<NotificationResponse>>> response =
                    ApiClient.dataApi(context).getNotifications().execute();
            if (!response.isSuccessful() || response.body() == null || response.body().getData() == null) {
                return Result.retry();
            }
            notifierNouvelles(context, response.body().getData());
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private void notifierNouvelles(Context context, List<NotificationResponse> notifications) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> dejaVues = prefs.getStringSet(KEY_SEEN, new HashSet<>());
        Set<String> clesActuelles = new HashSet<>();

        creerCanalSiBesoin(context);
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        boolean permissionAccordee = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;

        int idBase = 1000;
        for (NotificationResponse n : notifications) {
            if (n.isRead() || n.getKey() == null) continue;
            clesActuelles.add(n.getKey());
            if (dejaVues.contains(n.getKey())) continue;

            if (permissionAccordee) {
                manager.notify(idBase + Math.abs(n.getKey().hashCode() % 10000), construireNotification(context, n).build());
            }
        }

        // On ne garde que les clés encore actives — une alerte qui redisparaît puis
        // revient (ex: stock repasse au-dessus du seuil puis re-descend) doit pouvoir
        // re-notifier.
        prefs.edit().putStringSet(KEY_SEEN, clesActuelles).apply();
    }

    private NotificationCompat.Builder construireNotification(Context context, NotificationResponse n) {
        Intent intent = new Intent(context, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                context, n.getKey().hashCode(), intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        String titre = "CRITIQUE".equals(n.getLevel()) ? "⚠️ Alerte Cocorico" : "Cocorico";
        String texte = n.getProjetCode() != null ? n.getProjetCode() + " : " + n.getMessage() : n.getMessage();

        boolean critique = "CRITIQUE".equals(n.getLevel());
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                // Icône dédiée (silhouette blanche) : un mipmap en couleurs (avant)
                // est rendu par Android comme un bloc blanc plein dans la barre de
                // statut, illisible — voir ic_notification_small.
                .setSmallIcon(R.drawable.ic_notification_small)
                .setLargeIcon(android.graphics.BitmapFactory.decodeResource(context.getResources(), R.drawable.logo_cocorico))
                .setColor(androidx.core.content.ContextCompat.getColor(context,
                        critique ? R.color.red_bright : R.color.green_bright))
                .setColorized(false)
                .setContentTitle(titre)
                .setContentText(texte)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(texte))
                .setPriority(critique ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);
    }

    static void creerCanalSiBesoin(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Alertes Cocorico", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Mortalité, stock bas, échéances de projet");
        manager.createNotificationChannel(channel);
    }
}
