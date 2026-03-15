package com.mobile.diafarms.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.mobile.diafarms.R;

public class ScannerActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_SCAN = 1001;
    private MaterialButton btnScan, btnQrCode;
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
            Intent intent = new Intent(this, CameraScanActivity.class);
            startActivity(intent);  // ← Simple startActivity suffit !
        });

        // Clic Retour
        btnBack.setOnClickListener(v -> {
            finish();
        });
    }


}