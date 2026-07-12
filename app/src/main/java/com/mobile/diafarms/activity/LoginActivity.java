package com.mobile.diafarms.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mobile.diafarms.R;
import com.mobile.diafarms.data.LocalDatabase;
import com.mobile.diafarms.data.SessionManager;
import com.mobile.diafarms.models.User;
import com.mobile.diafarms.network.ApiClient;
import com.mobile.diafarms.network.dto.ApiEnvelope;
import com.mobile.diafarms.network.dto.AuthResponse;
import com.mobile.diafarms.network.dto.RoleResponse;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private TextInputEditText editIdentifiant;
    private TextInputEditText editPassword;
    private MaterialButton btnLogin, btnQrCode;
    private ImageButton btnBack;
    private ProgressBar progressBar;
    private boolean isLoading = false;
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
        progressBar = findViewById(R.id.progressBar);

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

    private void attemptLogin() {
        if (isLoading) return;

        String identifiant = Objects.requireNonNull(editIdentifiant.getText()).toString().trim();
        String password = Objects.requireNonNull(editPassword.getText()).toString().trim();

        if (identifiant.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, getString(R.string.login_missing_fields), Toast.LENGTH_SHORT).show();
            return;
        }

        isLoading = true;
        updateLoadingState();

        ApiClient.authApi(this).login("password", identifiant, password, true, "")
                .enqueue(new Callback<ApiEnvelope<AuthResponse>>() {
                    @Override
                    public void onResponse(Call<ApiEnvelope<AuthResponse>> call, Response<ApiEnvelope<AuthResponse>> response) {
                        isLoading = false;
                        updateLoadingState();

                        ApiEnvelope<AuthResponse> envelope = response.body();
                        if (envelope == null && response.errorBody() != null) {
                            envelope = parseErrorEnvelope(response.errorBody());
                        }

                        AuthResponse data = envelope != null ? envelope.getData() : null;

                        if (data != null && data.getAccessToken() != null && data.getUniqueId() != null) {
                            onLoginSuccess(data, identifiant);
                        } else {
                            String message = (data != null && data.getErrorMessage() != null)
                                    ? data.getErrorMessage()
                                    : getString(R.string.login_error_generic);
                            Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiEnvelope<AuthResponse>> call, Throwable t) {
                        isLoading = false;
                        updateLoadingState();
                        Log.e(TAG, "Erreur réseau lors de la connexion", t);

                        if (tryOfflineLogin(identifiant, password)) {
                            return;
                        }
                        Toast.makeText(LoginActivity.this, getString(R.string.login_error_network), Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Toute l'app est pensée pour un usage hors ligne : quand le réseau est indisponible,
     * on ne bloque pas sur l'échec réseau — on vérifie le mot de passe local défini après
     * le dernier scan QR (voir CameraScanActivity.showSetPasswordDialog) et, s'il
     * correspond, on rouvre la session déjà stockée (token du dernier scan/connexion)
     * sans tenter de renégocier un token frais, impossible sans réseau.
     */
    private boolean tryOfflineLogin(String identifiant, String password) {
        if (!sessionManager.hasLocalPassword() || !sessionManager.isLoggedIn()) return false;
        if (!identifiant.equals(sessionManager.getLocalPasswordIdentifiant())) return false;
        if (!sessionManager.verifyLocalPassword(password)) return false;

        Toast.makeText(this, "Connexion hors ligne", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, HomeActivity.class));
        finish();
        return true;
    }

    private ApiEnvelope<AuthResponse> parseErrorEnvelope(ResponseBody errorBody) {
        try {
            Type type = new TypeToken<ApiEnvelope<AuthResponse>>() {}.getType();
            return new Gson().fromJson(errorBody.charStream(), type);
        } catch (Exception e) {
            Log.e(TAG, "Réponse d'erreur illisible", e);
            return null;
        }
    }

    private void onLoginSuccess(AuthResponse data, String identifiant) {
        User user = new User();
        user.setId(data.getUniqueId());
        user.setNom(data.getFullName());
        user.setTelephone(data.getTelephone());
        user.setEmail(data.getEmail());
        user.setPhotoUrl(data.getPhoto());
        user.setActif(true);

        List<String> roles = new ArrayList<>();
        if (data.getRoles() != null) {
            for (RoleResponse role : data.getRoles()) {
                if (role.getRole() != null) roles.add(role.getRole());
            }
        }
        user.setRoles(roles);

        sessionManager.createSession(user, data.getAccessToken(), data.getRefreshToken());
        localDatabase.saveAccountIdentifiant(identifiant);

        if (Boolean.TRUE.equals(data.getMustChangePassword())) {
            Toast.makeText(this, getString(R.string.login_must_change_password), Toast.LENGTH_LONG).show();
        }

        startActivity(new Intent(LoginActivity.this, HomeActivity.class));
        finish();
    }

    private void updateLoadingState() {
        btnLogin.setEnabled(!isLoading);
        progressBar.setBackgroundColor(Color.WHITE);
        if (isLoading) {
            btnLogin.setText("");
            progressBar.setVisibility(View.VISIBLE);
        } else {
            btnLogin.setText(getString(R.string.login_submit));
            progressBar.setVisibility(View.GONE);
        }
    }
}
