package com.mobile.diafarms.update;

import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mobile.diafarms.R;
import com.mobile.diafarms.data.LocalDatabase;
import com.mobile.diafarms.util.DebugLog;

import java.io.File;
import java.util.Locale;

/**
 * Téléchargement (DownloadManager, dans getExternalFilesDir("updates") : aucune
 * permission de stockage) puis installation (FileProvider + ACTION_VIEW) de la
 * nouvelle version décrite par un UpdateInfo. Une instance par écran (accueil,
 * Paramètres), branchée sur un texte d'état, une barre de progression et un bouton.
 *
 * L'état du téléchargement vit en préférences (identifiant DownloadManager, version,
 * taille attendue) : il survit à la fermeture de l'écran, voire du process, et
 * l'écran suivant reprend le suivi là où il en était.
 *
 * Une mise à jour garde les données de l'app (même signature) : les saisies non
 * envoyées ne sont pas perdues, mais on propose quand même de synchroniser avant.
 */
public class UpdateController {

    private static final String DOSSIER = "updates";
    private static final String MIME_APK = "application/vnd.android.package-archive";
    private static final String KEY_DOWNLOAD_ID = "download_id";
    private static final String KEY_DOWNLOAD_VERSION = "download_version_code";
    private static final String KEY_DOWNLOAD_TAILLE = "download_taille";
    private static final String KEY_DOWNLOAD_FICHIER = "download_fichier";
    private static final String KEY_ATTENTE_PERMISSION = "attente_permission_install";
    private static final long INTERVALLE_SUIVI_MS = 700;

    private enum Etat { A_TELECHARGER, TELECHARGEMENT, A_INSTALLER, ERREUR }

    private final AppCompatActivity activity;
    private final TextView tvStatut;
    private final ProgressBar progress;
    private final Button btnAction;
    private final Runnable synchroniserDabord;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Nullable private UpdateInfo info;
    private Etat etat = Etat.A_TELECHARGER;
    private boolean visible;
    private boolean suiviEnCours;
    /** Vrai si ce téléchargement a été vu en cours ici : l'installeur ne s'ouvre alors
     * tout seul qu'une fois, à la fin, et pas à chaque retour sur l'écran si
     * l'utilisateur l'avait fermé (il reste le bouton "Installer"). */
    private boolean installerAutomatiquement;

    private final Runnable suivi = this::suivreTelechargement;

    public UpdateController(AppCompatActivity activity, TextView tvStatut, ProgressBar progress,
                            Button btnAction, Runnable synchroniserDabord) {
        this.activity = activity;
        this.tvStatut = tvStatut;
        this.progress = progress;
        this.btnAction = btnAction;
        this.synchroniserDabord = synchroniserDabord;
        this.prefs = UpdateChecker.prefs(activity);
        btnAction.setOnClickListener(v -> onAction());
    }

    @Nullable
    public UpdateInfo getInfo() {
        return info;
    }

    /** Associe la version proposée. Reprend un téléchargement déjà lancé pour cette
     * même version (écran rouvert, app relancée), sinon repart de zéro. */
    public void bind(UpdateInfo nouvelle) {
        if (info != null && info.getVersionCode() == nouvelle.getVersionCode()) {
            info = nouvelle;
            return;
        }
        info = nouvelle;
        stopperSuivi();
        btnAction.setVisibility(View.VISIBLE);
        if (prefs.getLong(KEY_DOWNLOAD_ID, -1) != -1
                && prefs.getInt(KEY_DOWNLOAD_VERSION, 0) == nouvelle.getVersionCode()) {
            etat = Etat.TELECHARGEMENT;
            afficher(Etat.TELECHARGEMENT, "Téléchargement…");
            if (visible) demarrerSuivi();
        } else {
            afficher(Etat.A_TELECHARGER, null);
        }
    }

    public void onResume() {
        visible = true;
        if (etat == Etat.TELECHARGEMENT) {
            demarrerSuivi();
        }
        // Retour de l'écran système "Installer des applis inconnues".
        if (prefs.getBoolean(KEY_ATTENTE_PERMISSION, false) && etat == Etat.A_INSTALLER) {
            prefs.edit().remove(KEY_ATTENTE_PERMISSION).apply();
            if (peutInstaller()) {
                installer();
            } else {
                Toast.makeText(activity, "Autorisation non accordée : la mise à jour n'a pas été installée.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    public void onPause() {
        visible = false;
        stopperSuivi();
    }

    private void onAction() {
        if (info == null) return;
        switch (etat) {
            case A_TELECHARGER:
            case ERREUR:
                verifierSaisiesPuis(this::telecharger);
                break;
            case A_INSTALLER:
                verifierSaisiesPuis(this::installer);
                break;
            case TELECHARGEMENT:
            default:
                break;
        }
    }

    /** Les données locales sont conservées par la mise à jour, mais une saisie non
     * envoyée reste plus sûre sur le serveur : on le signale avant d'aller plus loin. */
    private void verifierSaisiesPuis(Runnable suite) {
        int enAttente = new LocalDatabase(activity).getPendingSaisies().size();
        if (enAttente == 0) {
            suite.run();
            return;
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Saisies non envoyées")
                .setMessage("Vous avez " + enAttente + " saisie(s) non envoyée(s). Elles seront conservées, "
                        + "mais synchronisez d'abord si possible.")
                .setPositiveButton("Synchroniser d'abord", (d, w) -> synchroniserDabord.run())
                .setNegativeButton("Mettre à jour quand même", (d, w) -> suite.run())
                .show();
    }

    // Téléchargement

    private void telecharger() {
        if (info == null) return;
        nettoyer(activity);

        File dossier = activity.getExternalFilesDir(DOSSIER);
        if (dossier == null) {
            erreur("Stockage de l'appareil indisponible.");
            return;
        }
        String nom = "cocorico-" + info.getVersionName().replaceAll("[^0-9A-Za-z._]", "_") + ".apk";
        File fichier = new File(dossier, nom);

        DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) {
            erreur("Gestionnaire de téléchargements indisponible sur cet appareil.");
            return;
        }
        long id;
        try {
            DownloadManager.Request request = new DownloadManager.Request(
                    Uri.parse(UpdateChecker.apkUrl(activity, info.getVersionCode())))
                    .setTitle("Cocorico " + info.getVersionName())
                    .setDescription("Mise à jour de l'application")
                    .setMimeType(MIME_APK)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationInExternalFilesDir(activity, DOSSIER, nom);
            id = dm.enqueue(request);
        } catch (Exception e) {
            // Gestionnaire de téléchargements désactivé par l'utilisateur, URL refusée...
            DebugLog.log(activity, "UpdateController", "Échec enqueue : " + e);
            erreur("Impossible de lancer le téléchargement.");
            return;
        }

        prefs.edit()
                .putLong(KEY_DOWNLOAD_ID, id)
                .putInt(KEY_DOWNLOAD_VERSION, info.getVersionCode())
                .putLong(KEY_DOWNLOAD_TAILLE, info.getTaille())
                .putString(KEY_DOWNLOAD_FICHIER, fichier.getAbsolutePath())
                .apply();
        installerAutomatiquement = true;
        afficher(Etat.TELECHARGEMENT, "Téléchargement…");
        demarrerSuivi();
    }

    private void demarrerSuivi() {
        if (suiviEnCours) return;
        suiviEnCours = true;
        handler.post(suivi);
    }

    private void stopperSuivi() {
        suiviEnCours = false;
        handler.removeCallbacks(suivi);
    }

    private void suivreTelechargement() {
        if (!suiviEnCours) return;
        long id = prefs.getLong(KEY_DOWNLOAD_ID, -1);
        DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if (id == -1 || dm == null) {
            stopperSuivi();
            afficher(Etat.A_TELECHARGER, null);
            return;
        }

        int statut;
        long recus;
        long total;
        int raison;
        try (Cursor c = dm.query(new DownloadManager.Query().setFilterById(id))) {
            if (c == null || !c.moveToFirst()) {
                // Téléchargement annulé depuis la notification, ou purgé par le système.
                stopperSuivi();
                oublierTelechargement();
                erreur("Téléchargement interrompu.");
                return;
            }
            statut = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            recus = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            raison = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
        } catch (Exception e) {
            stopperSuivi();
            erreur("Suivi du téléchargement impossible.");
            return;
        }

        switch (statut) {
            case DownloadManager.STATUS_SUCCESSFUL:
                stopperSuivi();
                verifierFichier();
                return;
            case DownloadManager.STATUS_FAILED:
                stopperSuivi();
                DebugLog.log(activity, "UpdateController", "Téléchargement en échec, raison " + raison);
                dm.remove(id);
                oublierTelechargement();
                erreur(raison == 404
                        ? "Fichier de mise à jour introuvable sur le serveur."
                        : "Échec du téléchargement (code " + raison + ").");
                return;
            case DownloadManager.STATUS_PAUSED:
                installerAutomatiquement = true;
                afficherProgression(recus, total, "Téléchargement en pause (attente du réseau)…");
                break;
            default: // PENDING, RUNNING
                installerAutomatiquement = true;
                afficherProgression(recus, total, null);
                break;
        }
        handler.postDelayed(suivi, INTERVALLE_SUIVI_MS);
    }

    private void afficherProgression(long recus, long total, @Nullable String message) {
        long attendu = total > 0 ? total : prefs.getLong(KEY_DOWNLOAD_TAILLE, 0);
        String texte;
        if (attendu > 0 && recus >= 0) {
            int pourcent = (int) Math.min(100, recus * 100 / attendu);
            progress.setIndeterminate(false);
            progress.setMax(100);
            progress.setProgress(pourcent);
            texte = "Téléchargement… " + pourcent + " % (" + mo(recus) + " / " + mo(attendu) + ")";
        } else {
            progress.setIndeterminate(true);
            texte = "Téléchargement…";
        }
        tvStatut.setText(message != null ? message : texte);
    }

    /** Taille contrôlée contre "taille" de version.json : un APK tronqué ou remplacé en
     * route (portail captif, proxy) serait sinon refusé par l'installeur avec un
     * message peu clair, voire pas du tout signalé. */
    private void verifierFichier() {
        String chemin = prefs.getString(KEY_DOWNLOAD_FICHIER, null);
        File fichier = chemin != null ? new File(chemin) : null;
        if (fichier == null || !fichier.isFile() || fichier.length() == 0) {
            oublierTelechargement();
            erreur("Fichier téléchargé introuvable.");
            return;
        }
        long attendu = prefs.getLong(KEY_DOWNLOAD_TAILLE, 0);
        if (attendu > 0 && fichier.length() != attendu) {
            DebugLog.log(activity, "UpdateController", "Taille APK " + fichier.length() + " au lieu de " + attendu);
            nettoyer(activity);
            erreur("Fichier incomplet ou altéré (" + mo(fichier.length()) + " reçus, " + mo(attendu) + " attendus).");
            return;
        }
        // Le site web renvoie sa page d'accueil (HTTP 200) pour tout fichier absent :
        // sans "taille" dans version.json, seul ce contrôle distingue un vrai APK.
        String anomalie = verifierArchive(fichier);
        if (anomalie != null) {
            DebugLog.log(activity, "UpdateController", "APK refusé : " + anomalie);
            nettoyer(activity);
            erreur(anomalie);
            return;
        }
        afficher(Etat.A_INSTALLER, "Téléchargement terminé.");
        boolean attentePermission = prefs.getBoolean(KEY_ATTENTE_PERMISSION, false);
        if ((installerAutomatiquement || attentePermission) && visible) {
            installerAutomatiquement = false;
            if (attentePermission) {
                prefs.edit().remove(KEY_ATTENTE_PERMISSION).apply();
                if (!peutInstaller()) return; // bouton "Installer" toujours disponible
            }
            installer();
        }
    }

    /** null si le fichier est bien un APK de Cocorico plus récent que la version installée. */
    @Nullable
    private String verifierArchive(File fichier) {
        android.content.pm.PackageInfo pi;
        try {
            pi = activity.getPackageManager().getPackageArchiveInfo(fichier.getAbsolutePath(), 0);
        } catch (Exception e) {
            pi = null;
        }
        if (pi == null) {
            return "Le fichier téléchargé n'est pas une application valide.";
        }
        if (!activity.getPackageName().equals(pi.packageName)) {
            return "Le fichier téléchargé n'est pas l'application Cocorico.";
        }
        long versionFichier = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? pi.getLongVersionCode() : pi.versionCode;
        if (versionFichier <= UpdateChecker.versionInstallee()) {
            return "Le fichier en ligne n'est pas encore la nouvelle version, réessayez plus tard.";
        }
        return null;
    }

    // Installation

    private boolean peutInstaller() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || activity.getPackageManager().canRequestPackageInstalls();
    }

    private void installer() {
        String chemin = prefs.getString(KEY_DOWNLOAD_FICHIER, null);
        File fichier = chemin != null ? new File(chemin) : null;
        if (fichier == null || !fichier.isFile()) {
            oublierTelechargement();
            erreur("Fichier téléchargé introuvable.");
            return;
        }

        if (!peutInstaller()) {
            demanderPermissionInstallation();
            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", fichier);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, MIME_APK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            DebugLog.log(activity, "UpdateController", "Ouverture de l'installeur impossible : " + e);
            erreur("Impossible d'ouvrir l'installeur Android.");
        }
    }

    private void demanderPermissionInstallation() {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Autorisation nécessaire")
                .setMessage("Pour installer la mise à jour, Android doit autoriser Cocorico à installer des "
                        + "applications.\n\nSur l'écran suivant, activez « Autoriser cette source », puis revenez "
                        + "avec la flèche retour : l'installation reprendra.")
                .setPositiveButton("Ouvrir les réglages", (d, w) -> {
                    prefs.edit().putBoolean(KEY_ATTENTE_PERMISSION, true).apply();
                    Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName()));
                    try {
                        activity.startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        try {
                            activity.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
                        } catch (ActivityNotFoundException ignored) {
                            prefs.edit().remove(KEY_ATTENTE_PERMISSION).apply();
                            erreur("Réglage introuvable : autorisez Cocorico à installer des applications "
                                    + "dans les paramètres Android.");
                        }
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    // Affichage

    private void afficher(Etat nouvelEtat, @Nullable String statut) {
        etat = nouvelEtat;
        tvStatut.setTextColor(activity.getColor(R.color.gray_text_medium));
        switch (nouvelEtat) {
            case TELECHARGEMENT:
                progress.setVisibility(View.VISIBLE);
                progress.setIndeterminate(true);
                btnAction.setEnabled(false);
                btnAction.setText("Téléchargement…");
                break;
            case A_INSTALLER:
                progress.setVisibility(View.GONE);
                btnAction.setEnabled(true);
                btnAction.setText("Installer");
                break;
            case ERREUR:
                progress.setVisibility(View.GONE);
                btnAction.setEnabled(true);
                btnAction.setText("Réessayer");
                tvStatut.setTextColor(activity.getColor(R.color.red_error));
                break;
            case A_TELECHARGER:
            default:
                progress.setVisibility(View.GONE);
                btnAction.setEnabled(true);
                btnAction.setText("Mettre à jour");
                break;
        }
        tvStatut.setText(statut != null ? statut : "");
        tvStatut.setVisibility(statut != null ? View.VISIBLE : View.GONE);
    }

    private void erreur(String message) {
        afficher(Etat.ERREUR, message);
    }

    private static String mo(long octets) {
        return String.format(Locale.FRANCE, "%.1f Mo", octets / (1024.0 * 1024.0));
    }

    // Nettoyage

    private void oublierTelechargement() {
        prefs.edit()
                .remove(KEY_DOWNLOAD_ID)
                .remove(KEY_DOWNLOAD_VERSION)
                .remove(KEY_DOWNLOAD_TAILLE)
                .remove(KEY_DOWNLOAD_FICHIER)
                .remove(KEY_ATTENTE_PERMISSION)
                .apply();
    }

    /** Annule le téléchargement mémorisé et supprime tous les APK du dossier. */
    static void nettoyer(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = UpdateChecker.prefs(app);
        long id = prefs.getLong(KEY_DOWNLOAD_ID, -1);
        if (id != -1) {
            DownloadManager dm = (DownloadManager) app.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                try {
                    dm.remove(id);
                } catch (Exception ignored) {
                }
            }
        }
        prefs.edit()
                .remove(KEY_DOWNLOAD_ID)
                .remove(KEY_DOWNLOAD_VERSION)
                .remove(KEY_DOWNLOAD_TAILLE)
                .remove(KEY_DOWNLOAD_FICHIER)
                .remove(KEY_ATTENTE_PERMISSION)
                .apply();

        File dossier = app.getExternalFilesDir(DOSSIER);
        File[] fichiers = dossier != null ? dossier.listFiles() : null;
        if (fichiers != null) {
            for (File f : fichiers) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    /** Après installation de la nouvelle version, l'APK téléchargé ne sert plus. */
    static void nettoyerSiDejaInstalle(Context context) {
        SharedPreferences prefs = UpdateChecker.prefs(context);
        int version = prefs.getInt(KEY_DOWNLOAD_VERSION, 0);
        if (version > 0 && version <= UpdateChecker.versionInstallee()) {
            nettoyer(context);
        }
    }
}
