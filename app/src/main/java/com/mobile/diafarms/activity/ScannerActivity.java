package com.mobile.diafarms.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.mobile.diafarms.R;

import java.io.IOException;

public class ScannerActivity extends AppCompatActivity {

    private final ActivityResultLauncher<String> pickQrImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    decodeQrFromImage(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_scanner);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_section_scanner_act), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Liaison des vues
        MaterialButton btnScan = findViewById(R.id.btnScan);
        MaterialButton btnGallery = findViewById(R.id.btnGallery);
        ImageButton btnBack = findViewById(R.id.btnBack);

        // Clic Scanner - Lance l'activité de scan caméra
        btnScan.setOnClickListener(v -> startActivity(new Intent(this, CameraScanActivity.class)));

        // Clic Importer depuis la galerie
        btnGallery.setOnClickListener(v -> pickQrImageLauncher.launch("image/*"));

        // Clic Retour
        btnBack.setOnClickListener(v -> finish());
    }

    /**
     * Décode un QR code contenu dans une image choisie dans la galerie (même client ML Kit
     * que le scan caméra), puis délègue le traitement (déchiffrement, vérification serveur,
     * popup de succès) à CameraScanActivity plutôt que de dupliquer sa logique.
     */
    private void decodeQrFromImage(Uri uri) {
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            BarcodeScanner scanner = BarcodeScanning.getClient();

            scanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        String value = null;
                        for (Barcode barcode : barcodes) {
                            if (barcode.getRawValue() != null) {
                                value = barcode.getRawValue();
                                break;
                            }
                        }

                        if (value == null) {
                            Toast.makeText(this, getString(R.string.qr_gallery_not_found), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Intent intent = new Intent(this, CameraScanActivity.class);
                        intent.putExtra("GALLERY_QR_CONTENT", value);
                        startActivity(intent);
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, getString(R.string.qr_gallery_not_found), Toast.LENGTH_SHORT).show());
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.qr_gallery_read_error), Toast.LENGTH_SHORT).show();
        }
    }
}
