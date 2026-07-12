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
import com.mobile.diafarms.crypto.QrPayload;
import com.mobile.diafarms.data.LocalDatabase;
import com.mobile.diafarms.data.SessionManager;
import com.mobile.diafarms.models.User;
import com.mobile.diafarms.network.ApiClient;
import com.mobile.diafarms.network.dto.ApiEnvelope;
import com.mobile.diafarms.network.dto.RoleResponse;
import com.mobile.diafarms.network.dto.UtilisateurResponse;

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
     * Déchiffre le QR (même schéma AES/CBC que QRCodeController/AESService côté back),
     * puis vérifie sa validité auprès du serveur via /auth/me avec le token qu'il contient.
     * Aucun mot de passe n'est jamais affiché : le token du QR sert directement de session.
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

        QrPayload payload;
        try {
            String decrypted = AESHelper.decrypt(qrContent);
            payload = new Gson().fromJson(decrypted, QrPayload.class);
        } catch (Exception e) {
            Log.e(TAG, "QR illisible/non chiffré avec la bonne clé", e);
            showMessage(getString(R.string.error), getString(R.string.qr_invalid));
            return;
        }

        if (payload == null || !payload.isValid()) {
            showMessage(getString(R.string.error), getString(R.string.qr_invalid));
            return;
        }

        if (payload.isExpired()) {
            showMessage(getString(R.string.error), getString(R.string.qr_expired));
            return;
        }

        verifyWithServer(payload);
    }

    private void verifyWithServer(QrPayload payload) {
        AlertDialog loading = new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.qr_checking_server))
                .setCancelable(false)
                .show();

        ApiClient.authApi(this).me("Bearer " + payload.getToken())
                .enqueue(new Callback<ApiEnvelope<UtilisateurResponse>>() {
                    @Override
                    public void onResponse(Call<ApiEnvelope<UtilisateurResponse>> call, Response<ApiEnvelope<UtilisateurResponse>> response) {
                        loading.dismiss();

                        // Distingue un vrai refus serveur (401/403 : QR expiré/révoqué,
                        // compte suspendu) d'une simple absence de réseau, au lieu du
                        // message générique d'avant qui rendait les deux indiscernables.
                        if (!response.isSuccessful()) {
                            String serverMessage = readErrorMessage(response);
                            String detail = "HTTP " + response.code()
                                    + (serverMessage != null ? " : " + serverMessage : "")
                                    + "\n(QR probablement expiré, révoqué, ou compte suspendu — régénérez-le depuis le site web)";
                            Log.e(TAG, "Réponse serveur refusée pour /auth/me : " + detail);
                            showMessage(getString(R.string.error), detail);
                            return;
                        }

                        UtilisateurResponse profile = response.body() != null ? response.body().getData() : null;
                        if (profile == null || profile.getUniqueId() == null) {
                            showMessage(getString(R.string.error), "Réponse du serveur incomplète (HTTP " + response.code() + ").");
                            return;
                        }

                        onQrLoginSuccess(profile, payload.getToken());
                    }

                    @Override
                    public void onFailure(Call<ApiEnvelope<UtilisateurResponse>> call, Throwable t) {
                        loading.dismiss();
                        String detail = t.getClass().getSimpleName() + (t.getMessage() != null ? " : " + t.getMessage() : "");
                        Log.e(TAG, "Erreur réseau lors de la vérification du QR : " + detail, t);
                        showMessage(getString(R.string.error), "Impossible de contacter le serveur.\n" + detail);
                    }
                });
    }

    /** Extrait le message d'erreur du corps de réponse s'il suit l'enveloppe ApiResponse habituelle. */
    private String readErrorMessage(Response<ApiEnvelope<UtilisateurResponse>> response) {
        try {
            if (response.errorBody() != null) {
                ApiEnvelope<?> envelope = new Gson().fromJson(response.errorBody().charStream(), ApiEnvelope.class);
                if (envelope != null && envelope.getMessage() != null) {
                    return envelope.getMessage();
                }
            }
        } catch (Exception ignored) {
            // Le corps d'erreur ne suit pas forcément notre enveloppe JSON (ex: rejet direct
            // par Spring Security avant d'atteindre nos contrôleurs) — on se rabat sur le code HTTP seul.
        }
        return null;
    }

    private void onQrLoginSuccess(UtilisateurResponse profile, String token) {
        User user = new User();
        user.setId(profile.getUniqueId());
        user.setNom(profile.getFullName());
        user.setTelephone(profile.getTelephone());
        user.setEmail(profile.getEmail());
        user.setPhotoUrl(profile.getPhoto());
        user.setActif(profile.isStatut());

        List<String> roleNames = new ArrayList<>();
        if (profile.getRoles() != null) {
            for (RoleResponse role : profile.getRoles()) {
                if (role.getRole() != null) roleNames.add(role.getRole());
            }
        }
        user.setRoles(roleNames);

        sessionManager.createSession(user, token);
        if (profile.getUsername() != null) {
            localDatabase.saveAccountIdentifiant(profile.getUsername());
        }

        showSuccessDialog(user);
    }

    /** Popup de succès uniquement informative : jamais d'identifiant ni de mot de passe affichés. */
    private void showSuccessDialog(User user) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_qr_result, null);
        builder.setView(view);

        TextView tvUserName = view.findViewById(R.id.tvQrUserName);
        TextView tvUserRoles = view.findViewById(R.id.tvQrUserRoles);
        MaterialButton btnOk = view.findViewById(R.id.btnOk);

        tvUserName.setText(user.getNom());

        StringBuilder roles = new StringBuilder();
        if (user.isProduction()) roles.append("Production");
        if (user.isFinance()) {
            if (roles.length() > 0) roles.append(" · ");
            roles.append("Finance");
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
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.9),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
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
