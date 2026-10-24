package com.mobile.diafarms.update;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.AppUpdateOptions;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;

/**
 * Version Play Store : mise à jour par le système officiel de Google (In-App Updates).
 * Normale = téléchargée pendant qu'on travaille, puis « Redémarrer » l'installe.
 * Obligatoire (version minimale, voir VersionMinimale) = écran Google plein écran.
 * Les données de l'app (saisies non envoyées comprises) sont gardées.
 *
 * À créer dans onCreate (registerForActivityResult doit l'être avant l'affichage).
 * Rien ne se passe si l'app ne vient pas du Play Store : Google ne répond alors pas.
 */
public class MiseAJourPlayStore {

    public interface Vue {
        void disponible();
        void telechargement(int pourcent);
        void prete();
    }

    private final AppCompatActivity activity;
    private final Vue vue;
    private final AppUpdateManager manager;
    private final ActivityResultLauncher<IntentSenderRequest> lanceur;
    @Nullable private AppUpdateInfo info;

    private final InstallStateUpdatedListener ecouteur = state -> {
        if (state.installStatus() == InstallStatus.DOWNLOADING && state.totalBytesToDownload() > 0) {
            onUi(() -> vue().telechargement((int) (state.bytesDownloaded() * 100 / state.totalBytesToDownload())));
        } else if (state.installStatus() == InstallStatus.DOWNLOADED) {
            onUi(() -> vue().prete());
        }
    };

    public MiseAJourPlayStore(AppCompatActivity activity, Vue vue) {
        this.activity = activity;
        this.vue = vue;
        this.manager = AppUpdateManagerFactory.create(activity);
        this.lanceur = activity.registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(), r -> { });
        manager.registerListener(ecouteur);
    }

    private Vue vue() { return vue; }

    private void onUi(Runnable r) {
        if (!activity.isFinishing() && !activity.isDestroyed()) activity.runOnUiThread(r);
    }

    /** Demande à Google s'il y a une nouvelle version (ou une déjà téléchargée). */
    public void verifier() {
        manager.getAppUpdateInfo().addOnSuccessListener(i -> {
            info = i;
            if (i.installStatus() == InstallStatus.DOWNLOADED) {
                onUi(vue::prete);
            } else if (i.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                onUi(vue::disponible);
            }
        });
    }

    /** Lance la mise à jour ; sans réponse de Google, ouvre la fiche Play Store. */
    public void lancer(boolean obligatoire) {
        int type = obligatoire ? AppUpdateType.IMMEDIATE : AppUpdateType.FLEXIBLE;
        manager.getAppUpdateInfo()
                .addOnSuccessListener(i -> {
                    info = i;
                    if (i.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && i.isUpdateTypeAllowed(type)) {
                        manager.startUpdateFlowForResult(i, lanceur, AppUpdateOptions.newBuilder(type).build());
                    } else {
                        ouvrirFiche(activity);
                    }
                })
                .addOnFailureListener(e -> ouvrirFiche(activity));
    }

    /** Installe la version téléchargée (l'app redémarre). */
    public void installer() {
        manager.completeUpdate();
    }

    /** Signale une version prête ; reprend une mise à jour obligatoire interrompue. */
    public void onResume(boolean obligatoire) {
        manager.getAppUpdateInfo().addOnSuccessListener(i -> {
            info = i;
            if (i.installStatus() == InstallStatus.DOWNLOADED) {
                onUi(vue::prete);
            } else if (obligatoire && i.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                manager.startUpdateFlowForResult(i, lanceur, AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build());
            }
        });
    }

    public void onDestroy() {
        manager.unregisterListener(ecouteur);
    }

    public static void ouvrirFiche(Context context) {
        String id = context.getPackageName();
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + id))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + id))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }
}
