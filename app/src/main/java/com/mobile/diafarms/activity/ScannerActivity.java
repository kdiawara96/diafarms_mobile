package com.mobile.diafarms.activity;

import android.os.Bundle;
import android.widget.ImageButton;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.mobile.diafarms.R;

public class ScannerActivity extends AppCompatActivity {


    private MaterialButton btnScan;
    private ImageButton btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_scanner);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_scanner_act), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Liaison des vues
        btnScan = findViewById(R.id.btnScan);
        btnBack = findViewById(R.id.btnBack);

        // Clic Scanner - Lance l'activité de scan caméra
        btnScan.setOnClickListener(v -> {
            // TODO: Lancer le scan QR (ZXing ou CameraX)
            // Intent intent = new Intent(this, CameraScanActivity.class);
            // startActivity(intent);
        });

        // Clic Retour
        btnBack.setOnClickListener(v -> {
            finish();
        });

    }
}