package com.mobile.diafarms.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.mobile.diafarms.BuildConfig;
import com.mobile.diafarms.R;
import com.mobile.diafarms.data.AppSettings;
import com.mobile.diafarms.data.LocalDatabase;
import com.mobile.diafarms.data.SessionManager;
import com.mobile.diafarms.models.User;
import com.mobile.diafarms.network.ApiClient;
import com.mobile.diafarms.network.dto.ApiEnvelope;
import com.mobile.diafarms.util.DebugLog;

import java.io.File;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Écran de dépannage sur le terrain : changer l'adresse du serveur sans recompiler,
 * vérifier la connectivité, vider les données locales en cas d'incohérence, et
 * consulter/partager le journal des erreurs 401/serveur — utile car logcat s'est
 * révélé filtré sur les builds "retail" et adb n'est pas toujours disponible sur site.
 */
public class DiagnosticsActivity extends AppCompatActivity {

    private AppSettings appSettings;
    private LocalDatabase localDatabase;
    private SessionManager sessionManager;

    private EditText etServerUrl;
    private TextView tvConnectiviteResult;
    private TextView tvPendingCount;
    private TextView tvErrorLog;
    private TextView tvVersion;
    private MaterialButton btnTestConnectivite;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diagnostics);

        appSettings = new AppSettings(this);
        localDatabase = new LocalDatabase(this);
        sessionManager = new SessionManager(this);

        findViewById(R.id.btnBackDiagnostics).setOnClickListener(v -> finish());

        TextView tvCompteIdentifiant = findViewById(R.id.tvCompteIdentifiant);
        User currentUser = sessionManager.getCurrentUser();
        tvCompteIdentifiant.setText(currentUser != null && currentUser.getNom() != null
                ? "Connecté en tant que " + currentUser.getNom()
                : "Non connecté");
        findViewById(R.id.btnSupprimerCompte).setOnClickListener(v -> confirmSuppressionCompte());

        etServerUrl = findViewById(R.id.etServerUrl);
        tvConnectiviteResult = findViewById(R.id.tvConnectiviteResult);
        tvPendingCount = findViewById(R.id.tvPendingCount);
        tvErrorLog = findViewById(R.id.tvErrorLog);
        tvVersion = findViewById(R.id.tvVersion);
        btnTestConnectivite = findViewById(R.id.btnTestConnectivite);

        etServerUrl.setText(appSettings.getServerUrl());

        btnTestConnectivite.setOnClickListener(v -> testConnectivite());
        findViewById(R.id.btnResetServerUrl).setOnClickListener(v -> {
            appSettings.resetServerUrl();
            etServerUrl.setText(appSettings.getServerUrl());
            ApiClient.reset();
            Toast.makeText(this, "Adresse par défaut restaurée", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnViderBase).setOnClickListener(v -> confirmViderBase());
        findViewById(R.id.btnViderCache).setOnClickListener(v -> confirmViderCache());

        findViewById(R.id.btnPartagerLog).setOnClickListener(v -> partagerLog());
        findViewById(R.id.btnViderLog).setOnClickListener(v -> {
            DebugLog.clearErrorLog(this);
            refreshErrorLog();
            Toast.makeText(this, "Journal vidé", Toast.LENGTH_SHORT).show();
        });

        tvVersion.setText("Cocorico v" + BuildConfig.VERSION_NAME
                + " (build " + BuildConfig.VERSION_CODE + ") — "
                + (BuildConfig.DEBUG ? "debug" : "release"));

        refreshPendingCount();
        refreshErrorLog();
    }

    /** Enregistre l'URL saisie puis interroge /test : la seule façon fiable de savoir
     * si CE serveur précis est joignable, pas seulement si le port répond. */
    private void testConnectivite() {
        String url = etServerUrl.getText() != null ? etServerUrl.getText().toString().trim() : "";
        if (url.isEmpty() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            Toast.makeText(this, "Adresse invalide (doit commencer par http:// ou https://)", Toast.LENGTH_LONG).show();
            return;
        }

        appSettings.setServerUrl(url);
        etServerUrl.setText(appSettings.getServerUrl());
        ApiClient.reset();

        btnTestConnectivite.setEnabled(false);
        tvConnectiviteResult.setVisibility(View.VISIBLE);
        tvConnectiviteResult.setText("Test en cours...");
        tvConnectiviteResult.setTextColor(0xFF6B7280);

        long start = System.currentTimeMillis();
        ApiClient.authApi(this).ping().enqueue(new Callback<ApiEnvelope<Object>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<Object>> call, Response<ApiEnvelope<Object>> response) {
                btnTestConnectivite.setEnabled(true);
                long elapsed = System.currentTimeMillis() - start;
                if (response.isSuccessful()) {
                    tvConnectiviteResult.setText("✓ Serveur joignable (" + elapsed + " ms)");
                    tvConnectiviteResult.setTextColor(0xFF059669);
                } else {
                    tvConnectiviteResult.setText("✗ Réponse inattendue (HTTP " + response.code() + ")");
                    tvConnectiviteResult.setTextColor(0xFFDC2626);
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<Object>> call, Throwable t) {
                btnTestConnectivite.setEnabled(true);
                tvConnectiviteResult.setText("✗ Injoignable : " + t.getMessage());
                tvConnectiviteResult.setTextColor(0xFFDC2626);
            }
        });
    }

    /**
     * Action destructrice et irréversible : retire complètement le compte ACTIF de cet
     * appareil (session, token, mot de passe local) — pas la déconnexion classique du
     * terrain (voir HomeActivity, icône profil), qui elle ne fait que verrouiller sans
     * rien effacer. Plusieurs comptes pouvant coexister sur le même appareil (voir
     * SessionManager), le cache local générique (projets, identifiants, saisies en
     * attente) n'est purgé que s'il ne reste plus aucun autre compte après suppression —
     * sinon on emporterait par erreur les données d'un compte encore présent.
     */
    private void confirmSuppressionCompte() {
        int pending = localDatabase.countPending();
        String message = "Cette action est irréversible : la session et le mot de passe hors ligne de ce compte "
                + "seront supprimés de cet appareil. Un nouveau scan QR sera nécessaire pour s'y reconnecter."
                + (pending > 0 ? "\n\nSi c'est le seul compte de l'appareil, " + pending
                    + " saisie(s) non encore synchronisée(s) seront aussi définitivement perdues." : "")
                + "\n\nConfirmez avec votre mot de passe hors ligne.";

        confirmAvecMotDePasse("Supprimer ce compte de l'appareil ?", message, "Supprimer définitivement", () -> {
            sessionManager.deleteAccount();
            if (!sessionManager.hasAnyAccount()) {
                localDatabase.clearAllLocalData();
            }
            ApiClient.reset();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void confirmViderBase() {
        int pending = localDatabase.countPending();
        String message = (pending > 0
                ? "Cette action supprime toutes les données locales, y compris "
                    + pending + " saisie(s) non encore synchronisée(s) qui seront définitivement perdues."
                : "Cette action supprime toutes les données locales (aucune saisie en attente actuellement).")
                + "\n\nConfirmez avec votre mot de passe hors ligne.";

        confirmAvecMotDePasse("Vider la base locale ?", message, "Vider", () -> {
            int lost = localDatabase.clearAllLocalData();
            Toast.makeText(this, lost > 0
                    ? lost + " saisie(s) non synchronisée(s) supprimée(s)"
                    : "Données locales vidées", Toast.LENGTH_LONG).show();
            refreshPendingCount();
        });
    }

    /**
     * "Vider le cache" ne touche ni aux saisies en attente ni à la session (voir
     * LocalDatabase.clearCacheOnly), mais reste risqué sur le terrain : tant qu'aucun
     * accès réseau n'a eu lieu pour le repeupler, les projets/alertes/stock ne sont
     * plus disponibles hors ligne — l'agent peut se retrouver bloqué sans données en
     * pleine ferme, sans connexion. D'où la même confirmation par mot de passe que les
     * autres actions de la zone dangereuse, avec un avertissement explicite du risque
     * plutôt qu'un simple bouton déclenchant l'action au premier tap.
     */
    private void confirmViderCache() {
        String message = "Les données serveur en cache (projets, alertes, stock...) seront supprimées.\n\n"
                + "Tant que l'appareil ne se sera pas reconnecté au réseau au moins une fois pour les "
                + "recharger, elles ne seront plus disponibles hors ligne — vous risquez de vous retrouver "
                + "sans données sur le terrain si vous perdez la connexion avant.\n\n"
                + "Confirmez avec votre mot de passe hors ligne.";

        confirmAvecMotDePasse("Vider le cache ?", message, "Vider le cache", () -> {
            localDatabase.clearCacheOnly();
            Toast.makeText(this, "Cache vidé, les données seront rechargées au prochain accès réseau", Toast.LENGTH_LONG).show();
        });
    }

    /**
     * Action de la Zone dangereuse, confirmée par le mot de passe hors ligne du compte
     * ACTIF (celui défini au scan QR, voir SessionManager.setLocalPassword) plutôt
     * qu'un simple "Confirmer/Annuler" — ces deux actions (suppression de compte,
     * vidage complet de la base) sont irréversibles et perdent potentiellement des
     * saisies non synchronisées, donc trop faciles à déclencher par erreur avec un
     * simple bouton. Vérifié hors ligne (pas d'appel réseau) pour rester utilisable
     * sur le terrain sans connexion, là où cet écran est justement le plus utile.
     * Le dialogue reste ouvert (pas de dismiss automatique) tant que le mot de passe
     * saisi est incorrect, pour permettre de réessayer.
     */
    private void confirmAvecMotDePasse(String title, String message, String actionLabel, Runnable action) {
        View passwordView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_password, null);
        TextInputEditText etPassword = passwordView.findViewById(R.id.etConfirmPassword);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setView(passwordView)
                .setPositiveButton(actionLabel, null) // listener posé après show() pour contrôler le dismiss
                .setNegativeButton("Annuler", null)
                .create();

        dialog.setOnShowListener(d -> {
            // Rouge comme "Vider les données locales"/"Supprimer ce compte" plus bas :
            // ces deux actions sont irréversibles, le bouton ne doit pas se confondre
            // avec une confirmation anodine (couleur par défaut du thème).
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getColor(R.color.red_error));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String entered = etPassword.getText() != null ? etPassword.getText().toString() : "";
                if (!sessionManager.verifyLocalPassword(entered)) {
                    etPassword.setError("Mot de passe incorrect");
                    return;
                }
                dialog.dismiss();
                action.run();
            });
        });

        dialog.show();
    }

    private void refreshPendingCount() {
        tvPendingCount.setText("Saisies en attente de synchronisation : " + localDatabase.countPending());
    }

    private void refreshErrorLog() {
        String content = DebugLog.readErrorLog(this);
        tvErrorLog.setText(content.isEmpty() ? "Aucune erreur enregistrée." : content);
    }

    private void partagerLog() {
        File file = DebugLog.getErrorLogFile(this);
        if (file == null || !file.exists() || file.length() == 0) {
            Toast.makeText(this, "Aucun journal à partager", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Partager le journal d'erreurs"));
    }
}
