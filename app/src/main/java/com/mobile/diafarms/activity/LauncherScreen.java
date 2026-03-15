package com.mobile.diafarms.activity;

import android.app.LauncherActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.mobile.diafarms.R;

public class LauncherScreen extends AppCompatActivity {

    private CardView cardLogin, cardQr;
    private ImageView imgEntete, imgBanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher_screen);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_launcher_act), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Liaison des vues
        cardLogin = findViewById(R.id.cardLogin);
        cardQr = findViewById(R.id.cardQr);
        imgEntete = findViewById(R.id.imgEntete);
        imgBanner = findViewById(R.id.imgBanner);

        // Clic Connexion
        cardLogin.setOnClickListener(v -> {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
        });

        // Clic QR Code
        cardQr.setOnClickListener(v -> {
            Intent intent = new Intent(this, ScannerActivity.class);
            startActivity(intent);
        });
    }
}