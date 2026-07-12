package com.mobile.diafarms.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.google.gson.Gson;
import com.mobile.diafarms.crypto.LocalPasswordHasher;
import com.mobile.diafarms.models.User;

import java.security.GeneralSecurityException;
import java.io.IOException;

/**
 * Stocke le token de session et le profil utilisateur dans des SharedPreferences
 * chiffrées (EncryptedSharedPreferences) : le token JWT et les infos de compte ne
 * doivent jamais rester en clair sur le disque, contrairement à ce qui existait avant.
 *
 * Sur certains appareils (Samsung notamment), la clé Android Keystore protégeant ces
 * préférences peut devenir indécryptable après un événement système (mise à jour,
 * changement de verrouillage d'écran...), ce qui faisait planter l'app en
 * SecurityException sur le moindre accès à la session — observé en conditions réelles.
 * Tous les accès passent donc par des méthodes "safe" qui, en cas d'échec de
 * déchiffrement, réinitialisent les préférences plutôt que de planter : ça équivaut à
 * une déconnexion forcée (perte de la session locale), mais l'app reste utilisable.
 */
public class SessionManager {
    private static final String TAG = "SessionManager";
    private static final String PREF_NAME = "DiaFarmsSession";
    private static final String KEY_USER = "current_user";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_CURRENT_PROJET = "current_projet";
    private static final String KEY_LOCAL_PASSWORD_HASH = "local_password_hash";
    private static final String KEY_LOCAL_PASSWORD_IDENTIFIANT = "local_password_identifiant";

    private final Context context;
    private SharedPreferences pref;
    private final Gson gson;

    public SessionManager(Context context) {
        this.context = context.getApplicationContext();
        pref = buildEncryptedPrefs(this.context);
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

    /** Efface le fichier de prefs corrompu et en recrée un vide — dernier recours quand
     * le Keystore ne peut plus déchiffrer les données déjà écrites. */
    private void recoverFromCorruptedPrefs(Exception cause) {
        Log.e(TAG, "Préférences chiffrées corrompues (Keystore), réinitialisation forcée", cause);
        context.deleteSharedPreferences(PREF_NAME);
        resetMasterKeyIfNeeded();
        pref = buildEncryptedPrefs(context);
    }

    /** Sur certains appareils (Samsung notamment), c'est la clé Keystore elle-même qui
     * devient inutilisable après un événement système, pas seulement les données déjà
     * chiffrées avec elle — effacer le fichier de prefs ne suffirait pas, la prochaine
     * écriture échouerait pareil avec la même clé cassée. On supprime l'entrée pour
     * forcer sa régénération complète (MasterKeys.getOrCreate en recrée une neuve). */
    private void resetMasterKeyIfNeeded() {
        try {
            java.security.KeyStore keyStore = java.security.KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            String alias = "_androidx_security_master_key_"; // alias interne de MasterKeys, non exposé publiquement
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias);
            }
        } catch (Exception e) {
            Log.e(TAG, "Impossible de réinitialiser la clé Keystore", e);
        }
    }

    private String safeGetString(String key, String defValue) {
        try {
            return pref.getString(key, defValue);
        } catch (SecurityException | IllegalStateException e) {
            recoverFromCorruptedPrefs(e);
            return defValue;
        }
    }

    private boolean safeGetBoolean(String key, boolean defValue) {
        try {
            return pref.getBoolean(key, defValue);
        } catch (SecurityException | IllegalStateException e) {
            recoverFromCorruptedPrefs(e);
            return defValue;
        }
    }

    /** Applique l'édition ; en cas d'échec de déchiffrement (clé Keystore corrompue),
     * réinitialise les prefs puis réapplique une seule fois sur la base fraîche. */
    private void safeEdit(EditFn editFn) {
        try {
            SharedPreferences.Editor editor = pref.edit();
            editFn.apply(editor);
            editor.apply();
        } catch (SecurityException | IllegalStateException e) {
            recoverFromCorruptedPrefs(e);
            SharedPreferences.Editor editor = pref.edit();
            editFn.apply(editor);
            editor.apply();
        }
    }

    private interface EditFn {
        void apply(SharedPreferences.Editor editor);
    }

    public void createSession(User user, String token) {
        createSession(user, token, null);
    }

    public void createSession(User user, String token, String refreshToken) {
        safeEdit(editor -> {
            editor.putBoolean(KEY_IS_LOGGED_IN, true);
            editor.putString(KEY_USER, gson.toJson(user));
            editor.putString(KEY_TOKEN, token);
            if (refreshToken != null) {
                editor.putString(KEY_REFRESH_TOKEN, refreshToken);
            }
            if (user.getProjetsAssignes() != null && !user.getProjetsAssignes().isEmpty()) {
                editor.putString(KEY_CURRENT_PROJET, user.getProjetsAssignes().get(0));
            }
        });
    }

    public User getCurrentUser() {
        String userJson = safeGetString(KEY_USER, null);
        if (userJson != null) {
            return gson.fromJson(userJson, User.class);
        }
        return null;
    }

    public boolean isLoggedIn() {
        return safeGetBoolean(KEY_IS_LOGGED_IN, false);
    }

    public String getToken() {
        return safeGetString(KEY_TOKEN, null);
    }

    public String getRefreshToken() {
        return safeGetString(KEY_REFRESH_TOKEN, null);
    }

    public String getCurrentProjetId() {
        return safeGetString(KEY_CURRENT_PROJET, null);
    }

    public void setCurrentProjetId(String projetId) {
        safeEdit(editor -> editor.putString(KEY_CURRENT_PROJET, projetId));
    }

    public void clearSession() {
        safeEdit(SharedPreferences.Editor::clear);
    }

    public boolean isQRValid() {
        User user = getCurrentUser();
        if (user == null || user.getQrExpiry() == 0) return false;
        return System.currentTimeMillis() < user.getQrExpiry();
    }

    // ===== MOT DE PASSE LOCAL (accès hors ligne) =====
    // Défini juste après un scan QR réussi (voir CameraScanActivity) : c'est ce mot de
    // passe, vérifié uniquement sur l'appareil, qui permet ensuite de se reconnecter par
    // le formulaire identifiant/mot de passe classique quand le réseau est indisponible,
    // en réutilisant la session déjà stockée (token). Toute l'app est pensée pour un
    // usage hors ligne : ce mot de passe local EST le moyen d'accès hors ligne, pas un
    // simple raccourci de confort.

    public void setLocalPassword(String identifiant, String password) {
        safeEdit(editor -> {
            editor.putString(KEY_LOCAL_PASSWORD_HASH, LocalPasswordHasher.hash(password));
            editor.putString(KEY_LOCAL_PASSWORD_IDENTIFIANT, identifiant);
        });
    }

    public boolean hasLocalPassword() {
        return safeGetString(KEY_LOCAL_PASSWORD_HASH, null) != null;
    }

    public boolean verifyLocalPassword(String password) {
        String hash = safeGetString(KEY_LOCAL_PASSWORD_HASH, null);
        return hash != null && LocalPasswordHasher.matches(password, hash);
    }

    public String getLocalPasswordIdentifiant() {
        return safeGetString(KEY_LOCAL_PASSWORD_IDENTIFIANT, null);
    }

    public void clearLocalPassword() {
        safeEdit(editor -> {
            editor.remove(KEY_LOCAL_PASSWORD_HASH);
            editor.remove(KEY_LOCAL_PASSWORD_IDENTIFIANT);
        });
    }
}
