package com.mobile.diafarms.activity;

import android.app.LauncherActivity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mobile.diafarms.R;
import com.mobile.diafarms.network.Constants;

public class LauncherScreen extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher_screen);

        // Force light mode AVANT tout
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_launcher_act), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Liaison des vues
        CardView cardLogin = findViewById(R.id.cardLogin);
        CardView cardQr = findViewById(R.id.cardQr);
//        ImageView imgEntete = findViewById(R.id.imgEntete);
//        ImageView imgBanner = findViewById(R.id.imgBanner);

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

        findViewById(R.id.tvPasDeCompte).setOnClickListener(v -> afficherPremiersPas());
    }

    /** Quelqu'un qui installe l'app depuis le Play Store n'a pas encore de compte : la
     * ferme se crée sur le web, puis le QR code du propriétaire ouvre l'app. */
    private void afficherPremiersPas() {
        String message = "1. Créez votre ferme sur app.cocoricoferme.com (14 jours d'essai gratuit).\n\n"
                + "2. Sur le site, ouvrez la page Utilisateurs, choisissez votre nom et affichez votre QR code.\n\n"
                + "3. Revenez ici, appuyez sur QR Code et scannez-le.\n\n"
                + "Vous êtes employé d'une ferme ? Demandez votre QR code au propriétaire.";
        new MaterialAlertDialogBuilder(this)
                .setTitle("Bienvenue sur Cocorico")
                .setMessage(message)
                .setPositiveButton("Créer ma ferme", (d, w) -> ouvrirLien(Constants.WEB_URL + "inscription"))
                .setNegativeButton("Fermer", null)
                .show();
    }

    private void ouvrirLien(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Ouvrez " + url + " dans votre navigateur", Toast.LENGTH_LONG).show();
        }
    }
}