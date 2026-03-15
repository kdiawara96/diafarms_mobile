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
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.mobile.diafarms.R;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraScanActivity extends AppCompatActivity {

    private static final String TAG = "CameraScan";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};

    private PreviewView previewView;
    private ImageButton btnBack;
    private ExecutorService cameraExecutor;
    private BarcodeScanner barcodeScanner;
    private ImageButton btnFlash;
    private boolean isFlashOn = false;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_camera_scan);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_camera_scan), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        previewView = findViewById(R.id.previewView);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        // Initialiser ML Kit
        barcodeScanner = BarcodeScanning.getClient();
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Animation ligne de scan
        // Animation ligne de scan
        View scanLine = findViewById(R.id.scanLine);
        View scanFrame = findViewById(R.id.scanFrame);

       // Attendre que le layout soit mesuré
        scanFrame.post(() -> {
            int frameHeight = scanFrame.getHeight();

            ObjectAnimator animator = ObjectAnimator.ofFloat(
                    scanLine,
                    "translationY",
                    0f,
                    frameHeight - 16f  // -16f pour la marge
            );

            animator.setDuration(2500);  // Un peu plus lent pour la hauteur réduite
            animator.setRepeatCount(ObjectAnimator.INFINITE);
            animator.setRepeatMode(ObjectAnimator.REVERSE);
            animator.setInterpolator(new LinearInterpolator());
            animator.start();
        });

        // Bouton flash
        ImageButton btnFlash = findViewById(R.id.btnFlash);
        btnFlash.setOnClickListener(v -> toggleFlash());


        // Vérifier permissions
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    private void toggleFlash() {
        if (camera == null) return;

        isFlashOn = !isFlashOn;
        camera.getCameraControl().enableTorch(isFlashOn);

        // Change l'icône
        btnFlash.setImageResource(isFlashOn ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);
    }

    // Modifie startCamera() pour garder la référence camera
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

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
                // Garde la référence camera
                camera = cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageAnalysis
                );

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Erreur démarrage caméra", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }


    private void analyzeImage(ImageProxy imageProxy) {
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
                            Log.d(TAG, "QR Code trouvé: " + value);
                            runOnUiThread(() -> onQrCodeDetected(value));
                            break;
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Erreur scan", e))
                .addOnCompleteListener(task -> imageProxy.close());
    }


    private void onQrCodeDetected(String qrValue) {
        // Vibre pour confirmer (compatible API 24+)
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // API 26+ (Android 8.0)
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                // API 24-25 (deprecated mais fonctionne)
                vibrator.vibrate(50);
            }
        }

        // Renvoie le résultat
        Intent resultIntent = new Intent();
        resultIntent.putExtra("QR_RESULT", qrValue);
        setResult(RESULT_OK, resultIntent);
        finish();
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
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

