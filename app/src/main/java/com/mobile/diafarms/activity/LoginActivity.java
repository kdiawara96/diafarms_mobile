package com.mobile.diafarms.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mobile.diafarms.BuildConfig;
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
    private MaterialButton btnLogin, btnQrCode, btnTestMode, btnUnlockPin, btnDiagnostics;
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
        btnTestMode = findViewById(R.id.btnTestMode);
        btnUnlockPin = findViewById(R.id.btnUnlockPin);
        btnDiagnostics = findViewById(R.id.btnDiagnostics);
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

        btnDiagnostics.setOnClickListener(v -> startActivity(new Intent(this, DiagnosticsActivity.class)));

        // Porte de secours pour tester l'appli même si le backend n'est pas joignable :
        // uniquement visible en build debug, jamais en release.
        if (BuildConfig.DEBUG) {
            btnTestMode.setVisibility(View.VISIBLE);
            btnTestMode.setOnClickListener(v -> loginWithFakeData());
        }

        // Déverrouillage rapide (code local défini après un scan QR) : réutilise la
        // session déjà stockée sans réseau ni re-scan, tant que le token n'a pas expiré.
        if (sessionManager.hasLocalPin() && sessionManager.isLoggedIn()) {
            btnUnlockPin.setVisibility(View.VISIBLE);
            btnUnlockPin.setOnClickListener(v -> showUnlockPinDialog());
        }
    }

    private void showUnlockPinDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_unlock_pin, null);
        builder.setView(view);

        android.widget.TextView tvIdentifiant = view.findViewById(R.id.tvUnlockIdentifiant);
        TextInputEditText etPin = view.findViewById(R.id.etUnlockPin);
        MaterialButton btnUnlock = view.findViewById(R.id.btnUnlock);
        MaterialButton btnCancel = view.findViewById(R.id.btnUnlockCancel);

        String identifiant = sessionManager.getLocalPinIdentifiant();
        tvIdentifiant.setText(identifiant != null ? identifiant : "");

        AlertDialog dialog = builder.create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnUnlock.setOnClickListener(v -> {
            String pin = etPin.getText() != null ? etPin.getText().toString().trim() : "";
            if (sessionManager.verifyLocalPin(pin)) {
                dialog.dismiss();
                startActivity(new Intent(this, HomeActivity.class));
                finish();
            } else {
                Toast.makeText(this, "Code incorrect", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.9),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }

    /** Crée une session locale avec un utilisateur fictif (tous rôles) sans passer par le réseau. */
    private void loginWithFakeData() {
        User user = new User();
        user.setId("test-user-local");
        user.setNom("Agent Test");
        user.setTelephone("+225 0123456789");
        user.setEmail("test@diafarms.local");
        user.setActif(true);

        List<String> roles = new ArrayList<>();
        roles.add("PRODUCTEUR");
        roles.add("FINANCIER");
        user.setRoles(roles);

        sessionManager.createSession(user, "fake-token-test-mode");

        Toast.makeText(this, "Mode test : données fictives, aucun serveur contacté", Toast.LENGTH_LONG).show();
        startActivity(new Intent(this, HomeActivity.class));
        finish();
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
                        Toast.makeText(LoginActivity.this, getString(R.string.login_error_network), Toast.LENGTH_LONG).show();
                    }
                });
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
