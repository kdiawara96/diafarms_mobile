package com.mobile.diafarms.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.mobile.diafarms.R;
import com.mobile.diafarms.data.LocalDatabase;
import com.mobile.diafarms.data.SessionManager;
import com.mobile.diafarms.util.DebugLog;

import java.util.List;
import java.util.Objects;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private TextInputEditText editIdentifiant;
    private TextInputEditText editPassword;
    private MaterialButton btnLogin, btnQrCode;
    private ImageButton btnBack;
    private SessionManager sessionManager;
    private LocalDatabase localDatabase;

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

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.section_login_act), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        sessionManager = new SessionManager(this);
        localDatabase = new LocalDatabase(this);

        setupIdentifiantSuggestions();

        btnLogin.setOnClickListener(v -> attemptLogin());

        btnQrCode.setOnClickListener(v -> startActivity(new Intent(this, ScannerActivity.class)));

        btnBack.setOnClickListener(v -> finish());
    }

    /**
     * Propose les identifiants déjà utilisés avec succès sur cet appareil : un menu au
     * clic dès qu'il y en a plusieurs, pré-rempli directement s'il n'y en a qu'un. Le
     * champ reste un TextInputEditText classique (identique au mot de passe) — pas
     * d'AutoCompleteTextView, dont le style de boîte ne matchait pas le reste du formulaire.
     */
    private void setupIdentifiantSuggestions() {
        List<String> savedIdentifiants = localDatabase.getSavedIdentifiants();
        if (savedIdentifiants.isEmpty()) {
            return;
        }

        if (savedIdentifiants.size() == 1) {
            editIdentifiant.setText(savedIdentifiants.get(0));
            return;
        }

        editIdentifiant.setFocusable(false);
        editIdentifiant.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, editIdentifiant);
            for (String identifiant : savedIdentifiants) {
                popup.getMenu().add(identifiant);
            }
            popup.getMenu().add(getString(R.string.login_other_account));
            popup.setOnMenuItemClickListener(item -> {
                String choice = item.getTitle().toString();
                if (choice.equals(getString(R.string.login_other_account))) {
                    editIdentifiant.setFocusable(true);
                    editIdentifiant.setFocusableInTouchMode(true);
                    editIdentifiant.setText("");
                    editIdentifiant.requestFocus();
                } else {
                    editIdentifiant.setText(choice);
                    editPassword.requestFocus();
                }
                return true;
            });
            popup.show();
        });
    }

    /**
     * Écran 100% local : aucun appel réseau, jamais, quel que soit l'état de la
     * connexion (exactement comme BioEnrollApp). La seule façon d'obtenir une session
     * est le scan QR (voir CameraScanActivity) ; cet écran ne fait que déverrouiller la
     * session déjà stockée avec le mot de passe local défini à ce moment-là.
     */
    private void attemptLogin() {
        String identifiant = Objects.requireNonNull(editIdentifiant.getText()).toString().trim();
        String password = Objects.requireNonNull(editPassword.getText()).toString().trim();

        if (identifiant.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, getString(R.string.login_missing_fields), Toast.LENGTH_SHORT).show();
            return;
        }

        if (tryOfflineLogin(identifiant, password)) {
            return;
        }

        Toast.makeText(this, getString(R.string.login_error_generic), Toast.LENGTH_LONG).show();
    }

    /**
     * Vérifie l'identifiant/mot de passe local saisi contre TOUS les comptes mémorisés
     * sur cet appareil (voir SessionManager.switchToAccountMatching — plusieurs comptes
     * peuvent coexister, ex. un agent Production et un agent Finance qui partagent le
     * même téléphone de terrain) et, en cas de correspondance, active ce compte et
     * rouvre sa session déjà stockée (token du dernier scan) — c'est la SEULE
     * vérification faite ici, jamais d'appel réseau (voir attemptLogin).
     */
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            private boolean tryOfflineLogin(String identifiant, String password) {
        if (!sessionManager.switchToAccountMatching(identifiant, password)) {
            DebugLog.notice(this, TAG, "tryOfflineLogin: aucun compte mémorisé sur cet appareil ne correspond à l'identifiant \""
                    + identifiant + "\" et au mot de passe local saisi (aucun scan QR terminé jusqu'au bout pour ce compte ?)");
            return false;
        }

        DebugLog.log(this, TAG, "tryOfflineLogin: OK, réouverture de la session locale sans réseau");
        Toast.makeText(this, "Connexion hors ligne", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, HomeActivity.class));
        finish();
        return true;
    }
}
