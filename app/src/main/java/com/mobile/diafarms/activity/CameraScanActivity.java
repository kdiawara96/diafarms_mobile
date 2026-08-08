package com.mobile.diafarms.activity;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.mobile.diafarms.R;
import com.mobile.diafarms.crypto.AESHelper;
import com.mobile.diafarms.crypto.JwtHelper;
import com.mobile.diafarms.crypto.QrJwtClaims;
import com.mobile.diafarms.crypto.QrPayload;
import com.mobile.diafarms.data.CachePrefetcher;
import com.mobile.diafarms.data.LocalDatabase;
import com.mobile.diafarms.data.SessionManager;
import com.mobile.diafarms.models.User;
import com.mobile.diafarms.network.ApiClient;
import com.mobile.diafarms.network.dto.ApiEnvelope;
import com.mobile.diafarms.network.dto.ProjetSelectResponse;
import com.mobile.diafarms.util.DebugLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CameraScanActivity extends AppCompatActivity {

    private static final String TAG = "CameraScan";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};
    private static final long QR_COOLDOWN_MS = 3000; // délai minimum entre 2 traitements du même QR

    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private BarcodeScanner barcodeScanner;
    private ImageButton btnFlash;
    private boolean isFlashOn = false;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private SessionManager sessionManager;
    private LocalDatabase localDatabase;

    private boolean isProcessingQr = false;
    private String lastProcessedQr = null;
    private long lastProcessTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_camera_scan);

        sessionManager = new SessionManager(this);
        localDatabase = new LocalDatabase(this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_camera_scan), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        previewView = findViewById(R.id.previewView);
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        barcodeScanner = BarcodeScanning.getClient();
        cameraExecutor = Executors.newSingleThreadExecutor();

        View scanLine = findViewById(R.id.scanLine);
        View scanFrame = findViewById(R.id.scanFrame);
        scanFrame.post(() -> {
            int frameHeight = scanFrame.getHeight();
            ObjectAnimator animator = ObjectAnimator.ofFloat(scanLine, "translationY", 0f, frameHeight - 16f);
            animator.setDuration(2500);
            animator.setRepeatCount(ObjectAnimator.INFINITE);
            animator.setRepeatMode(ObjectAnimator.REVERSE);
            animator.setInterpolator(new LinearInterpolator());
            animator.start();
        });

        btnFlash = findViewById(R.id.btnFlash);
        btnFlash.setOnClickListener(v -> toggleFlash());

        // QR déjà décodé depuis une image de galerie (ScannerActivity) : on saute la
        // caméra et on traite directement le contenu via le même pipeline qu'un scan live.
        String galleryQrContent = getIntent().getStringExtra("GALLERY_QR_CONTENT");
        if (galleryQrContent != null && !galleryQrContent.isEmpty()) {
            isProcessingQr = true;
            processQr(galleryQrContent);
            return;
        }

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    private void toggleFlash() {
        if (camera == null) {
            Toast.makeText(this, "Caméra non disponible", Toast.LENGTH_SHORT).show();
            return;
        }
        CameraInfo cameraInfo = camera.getCameraInfo();
        if (!cameraInfo.hasFlashUnit()) {
            Toast.makeText(this, "Flash non disponible", Toast.LENGTH_SHORT).show();
            return;
        }
        isFlashOn = !isFlashOn;
        ListenableFuture<Void> future = camera.getCameraControl().enableTorch(isFlashOn);
        future.addListener(() -> runOnUiThread(() ->
                btnFlash.setImageResource(isFlashOn ? R.drawable.ic_flash_on : R.drawable.ic_flash_off)
        ), ContextCompat.getMainExecutor(this));
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Erreur démarrage caméra", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeImage(ImageProxy imageProxy) {
        if (isProcessingQr) {
            imageProxy.close();
            return;
        }

        @SuppressWarnings("UnsafeOptInUsageError")
        InputImage image = InputImage.fromMediaImage(
                Objects.requireNonNull(imageProxy.getImage()),
                imageProxy.getImageInfo().getRotationDegrees()
        );

        barcodeScanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    for (Barcode barcode : barcodes) {
                        String value = barcode.getRawValue();
                        if (value != null) {
                            long now = System.currentTimeMillis();
                            if (value.equals(lastProcessedQr) && (now - lastProcessTime) < QR_COOLDOWN_MS) {
                                break;
                            }
                            isProcessingQr = true;
                            lastProcessedQr = value;
                            lastProcessTime = now;
                            runOnUiThread(() -> processQr(value));
                            break;
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Erreur scan", e))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    /**
     * Déchiffre le QR (même schéma AES/CBC que QRCodeController/AESService côté back) et
     * lit l'identité directement dans les claims du JWT qu'il contient (fullName/role/
     * uniqueId, voir QrJwtClaims/JwtHelper) — aucun appel réseau n'est nécessaire pour se
     * connecter, exactement comme le login classique de BioEnrollApp qui ne consulte
     * jamais le back. Le réseau ne sert qu'ensuite, pour récupérer les projets actifs
     * liés au compte (voir CachePrefetcher), pas pour valider la connexion elle-même.
     *
     * Contrepartie assumée : un compte désactivé ou un rôle changé côté serveur après la
     * génération du QR ne sera détecté qu'à la prochaine synchronisation réseau, pas
     * immédiatement au scan (le backend faisait cette vérification via /auth/me avant).
     */
    private void processQr(String qrContent) {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(50);
            }
        }

        DebugLog.log(this, TAG, "=== Nouveau scan === contenu brut lu, longueur=" + qrContent.length());

        QrPayload payload;
        try {
            String decrypted = AESHelper.decrypt(qrContent);
            DebugLog.log(this, TAG, "Déchiffrement AES réussi. JSON déchiffré (longueur=" + decrypted.length() + "): " + decrypted);
            payload = new Gson().fromJson(decrypted, QrPayload.class);
        } catch (Exception e) {
            DebugLog.error(this, TAG, "Échec du déchiffrement/parsing du QR (contenu brut base64 loggé ci-dessous pour comparaison)", e);
            DebugLog.log(this, TAG, "Contenu brut scanné : " + qrContent);
            showMessage(getString(R.string.error), getString(R.string.qr_invalid));
            return;
        }

        if (payload == null || !payload.isValid()) {
            DebugLog.log(this, TAG, "Payload invalide après parsing : uniqueIdUser=" + (payload != null ? payload.getUniqueIdUser() : "null")
                    + " token=" + (payload != null ? DebugLog.reveal(payload.getToken()) : "null"));
            showMessage(getString(R.string.error), getString(R.string.qr_invalid));
            return;
        }

        DebugLog.log(this, TAG, "Payload valide : uniqueIdUser=" + payload.getUniqueIdUser()
                + " qrExpiresAt=" + payload.getQrExpiresAt()
                + " token=" + DebugLog.reveal(payload.getToken()));

        if (payload.isExpired()) {
            DebugLog.log(this, TAG, "QR jugé expiré côté mobile (qrExpiresAt=" + payload.getQrExpiresAt() + ")");
            showMessage(getString(R.string.error), getString(R.string.qr_expired));
            return;
        }

        QrJwtClaims claims;
        try {
            claims = JwtHelper.decodeClaims(payload.getToken());
        } catch (Exception e) {
            DebugLog.error(this, TAG, "Impossible de décoder les claims du JWT du QR", e);
            showMessage(getString(R.string.error), getString(R.string.qr_invalid));
            return;
        }

        if (claims == null || claims.getUniqueId() == null || claims.isExpired()) {
            DebugLog.log(this, TAG, "Claims JWT invalides ou expirées : uniqueId=" + (claims != null ? claims.getUniqueId() : "null"));
            showMessage(getString(R.string.error), getString(R.string.qr_expired));
            return;
        }

        onQrLoginSuccess(claims, payload.getToken());
    }

    private void onQrLoginSuccess(QrJwtClaims claims, String token) {
        User user = new User();
        user.setId(claims.getUniqueId());
        user.setNom(claims.getFullName());
        user.setActif(true); // pas de vérification serveur au scan, voir le commentaire de processQr()
        user.setRoles(claims.getRoles());

        sessionManager.createSession(user, token);
        String identifiant = claims.getSub();
        if (identifiant != null) {
            localDatabase.saveAccountIdentifiant(identifiant);
        }

        // Contrairement à avant, le JWT du QR ne porte que le username (sub), pas le
        // téléphone/email (voir QRCodeService côté back) : un seul identifiant possible
        // ici pour la reconnexion hors ligne, faute de mieux tant que le profil complet
        // n'a pas été récupéré une fois en ligne.
        List<String> identifiantsAcceptes = new ArrayList<>();
        if (identifiant != null) identifiantsAcceptes.add(identifiant);

        boolean dejaBootstrappe = localDatabase.getCache(CachePrefetcher.CACHE_PROJETS_SELECT) != null;
        if (dejaBootstrappe) {
            // Cet appareil a déjà les métadonnées d'un scan/login précédent (base
            // locale non vide) : pas besoin de réseau pour continuer, on rafraîchit
            // juste en best-effort, sans bloquer.
            CachePrefetcher.prefetchAll(this);
            showSuccessDialog(user, identifiantsAcceptes);
        } else {
            // Premier scan sur cet appareil : la base locale est vide, impossible de
            // travailler hors ligne ensuite sans avoir récupéré au moins une fois les
            // projets actifs du compte. On exige donc ici une connexion réussie, une
            // seule fois — c'est la SEULE situation où le scan requiert le réseau.
            requireFirstMetadataFetch(user, identifiantsAcceptes);
        }
    }

    /** Ne s'exécute que lors du tout premier scan sur cet appareil (base locale vide,
     * voir onQrLoginSuccess) : bloque jusqu'à récupérer avec succès les projets actifs
     * du compte, faute de quoi il n'y aurait rien à afficher hors ligne ensuite. */
    private void requireFirstMetadataFetch(User user, List<String> identifiantsAcceptes) {
        AlertDialog loading = new MaterialAlertDialogBuilder(this)
                .setMessage("Première connexion : récupération des données de votre compte…")
                .setCancelable(false)
                .show();

        ApiClient.dataApi(this).getProjetsSelect().enqueue(new Callback<ApiEnvelope<List<ProjetSelectResponse>>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<List<ProjetSelectResponse>>> call, Response<ApiEnvelope<List<ProjetSelectResponse>>> response) {
                loading.dismiss();
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    List<ProjetSelectResponse> projets = CachePrefetcher.filterActifs(response.body().getData());
                    localDatabase.putCache(CachePrefetcher.CACHE_PROJETS_SELECT, new Gson().toJson(projets));
                    CachePrefetcher.prefetchProjectsDetails(CameraScanActivity.this, localDatabase, projets);
                    showSuccessDialog(user, identifiantsAcceptes);
                } else {
                    DebugLog.notice(CameraScanActivity.this, TAG, "Premier scan : réponse serveur non exploitable (HTTP " + response.code() + ")");
                    showFirstFetchError(user, identifiantsAcceptes);
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<List<ProjetSelectResponse>>> call, Throwable t) {
                loading.dismiss();
                DebugLog.error(CameraScanActivity.this, TAG, "Premier scan : impossible de récupérer les données du compte", t);
                showFirstFetchError(user, identifiantsAcceptes);
            }
        });
    }

    private void showFirstFetchError(User user, List<String> identifiantsAcceptes) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.error))
                .setMessage("Ce premier scan sur cet appareil nécessite une connexion pour récupérer les données de votre compte (projets actifs...). Vérifiez votre réseau puis réessayez.")
                .setCancelable(false)
                .setPositiveButton("Réessayer", (d, w) -> requireFirstMetadataFetch(user, identifiantsAcceptes))
                .setNegativeButton("Annuler", (d, w) -> resetQrProcessing())
                .show();
    }

    /** Popup de succès uniquement informative : jamais d'identifiant ni de mot de passe affichés. */
    private void showSuccessDialog(User user, List<String> identifiantsAcceptes) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_qr_result, null);
        builder.setView(view);

        TextView tvUserName = view.findViewById(R.id.tvQrUserName);
        TextView tvUserRoles = view.findViewById(R.id.tvQrUserRoles);
        MaterialButton btnOk = view.findViewById(R.id.btnOk);

        tvUserName.setText(user.getNom());

        StringBuilder roles = new StringBuilder();
        if (user.isProduction()) roles.append("Production");
        if (user.isComptable()) {
            if (roles.length() > 0) roles.append(" · ");
            roles.append("Comptable");
        }
        if (user.isVente()) {
            if (roles.length() > 0) roles.append(" · ");
            roles.append("Vente");
        }
        if (user.isAdmin()) {
            if (roles.length() > 0) roles.append(" · ");
            roles.append("Administration");
        }
        tvUserRoles.setText(roles.length() > 0 ? roles.toString() : "");

        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);

        btnOk.setOnClickListener(v -> {
            dialog.dismiss();
            // Le mot de passe local est CE QUI permet l'accès hors ligne ensuite (via le
            // formulaire identifiant/mot de passe classique, voir LoginActivity.tryOfflineLogin) —
            // pas un simple raccourci de confort, donc pas d'option "plus tard". Chaque
            // compte a désormais son propre emplacement (voir SessionManager.createSession,
            // qui vient d'activer CE compte, celui tout juste scanné) : hasLocalPassword()
            // ne peut donc plus, comme avant, refléter le mot de passe d'un AUTRE compte —
            // on ne reprompte que si CE compte n'a pas encore le sien.
            if (!sessionManager.hasLocalPassword()) {
                showSetPasswordDialog(identifiantsAcceptes);
            } else {
                goHome();
            }
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.9),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }

    private void showSetPasswordDialog(List<String> identifiantsAcceptes) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_set_local_password, null);
        builder.setView(view);

        com.google.android.material.textfield.TextInputEditText etPassword = view.findViewById(R.id.etPassword);
        com.google.android.material.textfield.TextInputEditText etPasswordConfirm = view.findViewById(R.id.etPasswordConfirm);
        MaterialButton btnDefinir = view.findViewById(R.id.btnDefinirPassword);

        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);

        btnDefinir.setOnClickListener(v -> {
            String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
            String confirm = etPasswordConfirm.getText() != null ? etPasswordConfirm.getText().toString() : "";

            if (password.length() < 4) {
                Toast.makeText(this, "Le mot de passe doit contenir au moins 4 caractères", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!password.equals(confirm)) {
                Toast.makeText(this, "Les deux mots de passe ne correspondent pas", Toast.LENGTH_SHORT).show();
                return;
            }

            sessionManager.setLocalPassword(identifiantsAcceptes, password);
            // Relecture immédiate pour confirmer que l'écriture a bien persisté (et pas
            // été silencieusement perdue par une réinitialisation Keystore, voir
            // SessionManager.recoverFromCorruptedPrefs) — sans ce log on ne peut pas
            // distinguer "jamais écrit" de "écrit puis reperdu" en cas de souci.
            DebugLog.notice(this, TAG, "Mot de passe local enregistré pour " + identifiantsAcceptes
                    + " — relecture immédiate : hasLocalPassword=" + sessionManager.hasLocalPassword()
                    + " identifiantsRelus=" + sessionManager.getLocalPasswordIdentifiants());
            Toast.makeText(this, "Mot de passe hors ligne enregistré", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            goHome();
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.9),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }

    private void goHome() {
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }

    private void showMessage(String title, String message) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(getString(R.string.ok), (dia, which) -> {
                    dia.dismiss();
                    resetQrProcessing();
                })
                .setCancelable(false);

        AlertDialog dialog = builder.create();
        dialog.setOnCancelListener(d -> resetQrProcessing());
        dialog.setOnShowListener(d -> {
            Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            button.setTextColor(ContextCompat.getColor(this, R.color.primary));
        });
        dialog.show();
    }

    private void resetQrProcessing() {
        isProcessingQr = false;
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permission caméra requise", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        barcodeScanner.close();
    }
}
