package com.mobile.diafarms.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.google.gson.Gson;
import com.mobile.diafarms.crypto.PinHasher;
import com.mobile.diafarms.models.User;

import java.security.GeneralSecurityException;
import java.io.IOException;

/**
 * Stocke le token de session et le profil utilisateur dans des SharedPreferences
 * chiffrées (EncryptedSharedPreferences) : le token JWT et les infos de compte ne
 * doivent jamais rester en clair sur le disque, contrairement à ce qui existait avant.
 */
public class SessionManager {
    private static final String TAG = "SessionManager";
    private static final String PREF_NAME = "DiaFarmsSession";
    private static final String KEY_USER = "current_user";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_CURRENT_PROJET = "current_projet";
    private static final String KEY_LOCAL_PIN_HASH = "local_pin_hash";
    private static final String KEY_LOCAL_PIN_IDENTIFIANT = "local_pin_identifiant";

    private final SharedPreferences pref;
    private final Gson gson;

    public SessionManager(Context context) {
        pref = buildEncryptedPrefs(context.getApplicationContext());
        gson = new Gson();
    }

    private static SharedPreferences buildEncryptedPrefs(Context context) {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            return EncryptedSharedPreferences.create(
                    PREF_NAME,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            // Ne devrait pas arriver (Keystore matériel indisponible) ; on retombe sur des
            // prefs non chiffrées plutôt que de planter l'appli au démarrage.
            Log.e(TAG, "Impossible d'initialiser le stockage chiffré, repli sur SharedPreferences classiques", e);
            return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }
    }

    public void createSession(User user, String token) {
        createSession(user, token, null);
    }

    public void createSession(User user, String token, String refreshToken) {
        SharedPreferences.Editor editor = pref.edit();
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_USER, gson.toJson(user));
        editor.putString(KEY_TOKEN, token);
        if (refreshToken != null) {
            editor.putString(KEY_REFRESH_TOKEN, refreshToken);
        }
        if (user.getProjetsAssignes() != null && !user.getProjetsAssignes().isEmpty()) {
            editor.putString(KEY_CURRENT_PROJET, user.getProjetsAssignes().get(0));
        }
        editor.apply();
    }

    public User getCurrentUser() {
        String userJson = pref.getString(KEY_USER, null);
        if (userJson != null) {
            return gson.fromJson(userJson, User.class);
        }
        return null;
    }

    public boolean isLoggedIn() {
        return pref.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public String getToken() {
        return pref.getString(KEY_TOKEN, null);
    }

    public String getRefreshToken() {
        return pref.getString(KEY_REFRESH_TOKEN, null);
    }

    public String getCurrentProjetId() {
        return pref.getString(KEY_CURRENT_PROJET, null);
    }

    public void setCurrentProjetId(String projetId) {
        pref.edit().putString(KEY_CURRENT_PROJET, projetId).apply();
    }

    public void clearSession() {
        pref.edit().clear().apply();
    }

    public boolean isQRValid() {
        User user = getCurrentUser();
        if (user == null || user.getQrExpiry() == 0) return false;
        return System.currentTimeMillis() < user.getQrExpiry();
    }

    // ===== CODE D'ACCÈS RAPIDE LOCAL =====
    // Déverrouille l'app instantanément (sans réseau, sans re-scanner de QR) tant que la
    // session en cours (token) reste valide côté serveur — ce n'est pas un mot de passe
    // de compte, juste un verrou de confort sur cet appareil, inspiré de BioEnroll.

    public void setLocalPin(String identifiant, String pin) {
        pref.edit()
                .putString(KEY_LOCAL_PIN_HASH, PinHasher.hash(pin))
                .putString(KEY_LOCAL_PIN_IDENTIFIANT, identifiant)
                .apply();
    }

    public boolean hasLocalPin() {
        return pref.getString(KEY_LOCAL_PIN_HASH, null) != null;
    }

    public boolean verifyLocalPin(String pin) {
        String hash = pref.getString(KEY_LOCAL_PIN_HASH, null);
        return hash != null && PinHasher.matches(pin, hash);
    }

    public String getLocalPinIdentifiant() {
        return pref.getString(KEY_LOCAL_PIN_IDENTIFIANT, null);
    }

    public void clearLocalPin() {
        pref.edit().remove(KEY_LOCAL_PIN_HASH).remove(KEY_LOCAL_PIN_IDENTIFIANT).apply();
    }
}
