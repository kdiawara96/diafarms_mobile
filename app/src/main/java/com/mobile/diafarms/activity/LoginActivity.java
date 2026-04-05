package com.mobile.diafarms.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.mobile.diafarms.R;
import com.mobile.diafarms.data.SessionManager;
import com.mobile.diafarms.models.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class LoginActivity extends AppCompatActivity {

    private String TAG = "tigui_LoginAct";
    private TextInputEditText editIdentifiant, editPassword;
    private MaterialButton btnLogin, btnQrCode;
    private ImageButton btnBack;
    private ProgressBar progressBar;
    private boolean isLoading = false;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);


        // Liaison des vues
        editIdentifiant = findViewById(R.id.editIdentifiant);
        editPassword = findViewById(R.id.editPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnQrCode = findViewById(R.id.btnQrCode);
        btnBack = findViewById(R.id.btnBack);

        progressBar = findViewById(R.id.progressBar);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.section_login_act), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialisation SessionManager
        sessionManager = new SessionManager(this);

        // Clic Connexion
        btnLogin.setOnClickListener(v -> {
            String identifiant = Objects.requireNonNull(editIdentifiant.getText()).toString().trim();
            String password = Objects.requireNonNull(editPassword.getText()).toString().trim();

            if (identifiant.isEmpty() || password.isEmpty()) {
                return;
            }
            Log.d(TAG, "identifiant: " + identifiant);
            Log.d(TAG, "password: " + password);

            isLoading = true;
            updateLoadingState();

            new Handler().postDelayed(() -> {

                isLoading = false;
                updateLoadingState();

                // Création d'un utilisateur par défaut pour la session
                User defaultUser = createDefaultUser();
                sessionManager.createSession(defaultUser, "default_token_" + System.currentTimeMillis());

                // TODO: Appel API login
                Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
                startActivity(intent);

            }, 1000); // 1 secondes



        });

        // Clic QR Code
        btnQrCode.setOnClickListener(v -> {
            Intent intent = new Intent(this, ScannerActivity.class);
            startActivity(intent);
        });

        // Clic Retour
        btnBack.setOnClickListener(v -> {
            finish();
        });
    }

    /**
     * Crée un utilisateur par défaut avec tous les rôles et projets
     */
    private User createDefaultUser() {
        User user = new User();
        user.setId("user_default_001");
        user.setNom("Agent Test");
        user.setTelephone("+225 0123456789");
        user.setEmail("agent@test.com");

        // Tous les rôles activés
        List<String> roles = new ArrayList<>();
        roles.add("production");
        roles.add("finance");
//        roles.add("admin");
        user.setRoles(roles);

        // Projets assignés par défaut
        List<String> projets = new ArrayList<>();
        projets.add("proj_001");
        projets.add("proj_002");
        user.setProjetsAssignes(projets);

        // QR Code valide pour 24h
        user.setQrCode("DEFAULT_QR_" + System.currentTimeMillis());
        user.setQrExpiry(System.currentTimeMillis() + (24 * 60 * 60 * 1000)); // +24h

        user.setActif(true);
        user.setPhotoUrl(null); // Pas de photo par défaut

        return user;
    }

    private void updateLoadingState() {
        btnLogin.setEnabled(!isLoading);
        progressBar.setBackgroundColor(Color.WHITE);
        if (isLoading) {
            btnLogin.setText("Patiente...");           // Vide le texte du bouton
            progressBar.setVisibility(View.VISIBLE);  // AFFICHE le loader
        } else {
            btnLogin.setText("Se connecter");
            progressBar.setVisibility(View.GONE);     // CACHE le loader
        }
    }
}