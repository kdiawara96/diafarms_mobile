package com.mobile.diafarms.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mobile.diafarms.crypto.LocalPasswordHasher;
import com.mobile.diafarms.models.User;

import java.lang.reflect.Type;
import java.security.GeneralSecurityException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Stocke le token de session et le profil utilisateur dans des SharedPreferences
 * chiffrées (EncryptedSharedPreferences) : le token JWT et les infos de compte ne
 * doivent jamais rester en clair sur le disque, contrairement à ce qui existait avant.
 *
 * Plusieurs comptes peuvent être mémorisés SIMULTANÉMENT sur le même appareil (voir
 * Account/getAccounts) — un agent Production et un agent Finance qui partagent un
 * téléphone de terrain doivent chacun garder leur session et leur mot de passe hors
 * ligne, indépendamment : avant, un second scan QR écrasait purement et simplement le
 * seul compte stocké (token ET mot de passe local), rendant l'accès hors ligne du
 * premier compte définitivement perdu dès qu'un second se connectait sur le même
 * appareil. getCurrentUser()/getToken()/etc. reflètent toujours le compte ACTIF
 * (voir getActiveAccountId/setActiveAccountId) ; switchToAccountMatching() est ce qui
 * permet de changer de compte actif hors ligne, en cherchant parmi tous les comptes
 * mémorisés plutôt qu'un seul.
 *
 * Sur certains appareils (Samsung notamment), la clé Android Keystore protégeant ces
 * préférences peut devenir indécryptable après un événement système (mise à jour,
 * changement de verrouillage d'écran...), ce qui faisait planter l'app en
 * SecurityException sur le moindre accès à la session — observé en conditions réelles.
 * Tous les accès passent donc par des méthodes "safe" qui, en cas d'échec de
 * déchiffrement, réinitialisent les préférences plutôt que de planter : ça équivaut à
 * une déconnexion forcée de TOUS les comptes (perte de la session locale), mais l'app
 * reste utilisable.
 */
public class SessionManager {
    private static final String TAG = "SessionManager";
    private static final String PREF_NAME = "DiaFarmsSession";
    private static final String KEY_ACCOUNTS = "accounts_v2";
    private static final String KEY_ACTIVE_ACCOUNT_ID = "active_account_id";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";

    private final Context context;
    private SharedPreferences pref;
    private final Gson gson;

    // Compte actif, gardé en mémoire pour LocalDatabase qui en a besoin à chaque lecture
    // (saisies et cache cloisonnés par compte) sans relire les préférences chiffrées à
    // chaque fois. Mis à jour à chaque changement de compte actif (voir setActiveAccountId).
    private static volatile String sActiveUserId;
    private static volatile boolean sActiveUserIdCharge;
    // Ferme du compte actif, gardée en mémoire avec lui (voir LocalDatabase.fermeCourante).
    private static volatile String sActiveFarmId;

    public SessionManager(Context context) {
        this.context = context.getApplicationContext();
        pref = buildEncryptedPrefs(this.context);
        gson = new Gson();
        if (!sActiveUserIdCharge) {
            sActiveUserId = safeGetString(KEY_ACTIVE_ACCOUNT_ID, null);
            User u = getCurrentUser();
            sActiveFarmId = u != null ? u.getFarmUniqueId() : null;
            sActiveUserIdCharge = true;
        }
    }

    /** Ferme du compte actif si connue, sans relire les préférences chiffrées. */
    public static String activeFarmId(Context context) {
        if (!sActiveUserIdCharge) new SessionManager(context);
        return sActiveFarmId;
    }

    /** Identifiant (User.getId) du compte actif de l'appareil, null si aucun. Le compte
     * reste « actif » quand l'appli est seulement verrouillée (voir lockSession). */
    public static String activeUserId(Context context) {
        if (!sActiveUserIdCharge) new SessionManager(context);
        return sActiveUserId;
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
     * le Keystore ne peut plus déchiffrer les données déjà écrites. Efface AUSSI, entre
     * autres, tous les mots de passe hors ligne de TOUS les comptes : DebugLog.notice
     * (pas juste Log.e) pour que ça apparaisse dans le journal partagé depuis
     * Diagnostics, sinon cette perte de données passe totalement inaperçue de
     * l'utilisateur qui la subit sur le terrain. */
    private void recoverFromCorruptedPrefs(Exception cause) {
        Log.e(TAG, "Préférences chiffrées corrompues (Keystore), réinitialisation forcée", cause);
        com.mobile.diafarms.util.DebugLog.notice(context, TAG, "Préférences chiffrées corrompues (Keystore), réinitialisation forcée. TOUS les comptes et mots de passe locaux de cet appareil sont perdus : "
                + cause.getClass().getSimpleName() + (cause.getMessage() != null ? ": " + cause.getMessage() : ""));
        context.deleteSharedPreferences(PREF_NAME);
        resetMasterKeyIfNeeded();
        pref = buildEncryptedPrefs(context);
        sActiveUserId = null;
        sActiveFarmId = null;
        sActiveUserIdCharge = true;
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

    // ===== COMPTES (plusieurs comptes peuvent coexister sur le même appareil) =====

    /** Un compte mémorisé sur cet appareil (un par utilisateur ayant scanné son QR ici),
     * indexé par userId (User.getId(), stable — voir CameraScanActivity.onQrLoginSuccess). */
    private static class Account {
        String userId;
        User user;
        String token;
        String refreshToken;
        String currentProjetId;
        String localPasswordHash;
        List<String> localPasswordIdentifiants = new ArrayList<>();
    }

    private List<Account> getAccounts() {
        String json = safeGetString(KEY_ACCOUNTS, null);
        if (json == null) return new ArrayList<>();
        try {
            Type type = new TypeToken<List<Account>>() {}.getType();
            List<Account> result = gson.fromJson(json, type);
            return result != null ? result : new ArrayList<>();
        } catch (com.google.gson.JsonSyntaxException e) {
            Log.e(TAG, "Format de comptes illisible, traité comme aucun compte mémorisé", e);
            return new ArrayList<>();
        }
    }

    private void saveAccounts(List<Account> accounts) {
        safeEdit(editor -> editor.putString(KEY_ACCOUNTS, gson.toJson(accounts)));
    }

    private static Account findAccount(List<Account> accounts, String userId) {
        if (userId == null) return null;
        for (Account a : accounts) {
            if (userId.equals(a.userId)) return a;
        }
        return null;
    }

    private String getActiveAccountId() {
        return safeGetString(KEY_ACTIVE_ACCOUNT_ID, null);
    }

    private void setActiveAccountId(String userId) {
        safeEdit(editor -> editor.putString(KEY_ACTIVE_ACCOUNT_ID, userId));
        sActiveUserId = userId;
        Account a = findAccount(getAccounts(), userId);
        sActiveFarmId = a != null && a.user != null ? a.user.getFarmUniqueId() : null;
        sActiveUserIdCharge = true;
    }

    /** Nombre de comptes mémorisés sur l'appareil. */
    public int accountCount() {
        return getAccounts().size();
    }

    /** Identifiant du seul compte mémorisé, null s'il y en a zéro ou plusieurs. */
    public String seulCompteId() {
        List<Account> accounts = getAccounts();
        return accounts.size() == 1 ? accounts.get(0).userId : null;
    }

    /** Ferme du compte actif si déjà connue (voir mettreAJourProfil), sinon null. */
    public String getCurrentFarmId() {
        User u = getCurrentUser();
        return u != null ? u.getFarmUniqueId() : null;
    }

    /** Complète le profil d'un compte (ferme, consultation seule) avec la réponse du
     * serveur. userId est celui du compte pour lequel la requête a été faite : si le
     * compte actif a changé entre-temps, c'est bien CE compte-là qui est mis à jour.
     * Une valeur null laisse l'ancienne en place. */
    public void mettreAJourProfil(String userId, String farmUniqueId, Boolean consultationSeule) {
        List<Account> accounts = getAccounts();
        Account a = findAccount(accounts, userId);
        if (a == null || a.user == null) return;
        if (farmUniqueId != null) a.user.setFarmUniqueId(farmUniqueId);
        if (consultationSeule != null) a.user.setConsultationSeule(consultationSeule);
        saveAccounts(accounts);
        if (userId.equals(sActiveUserId) && a.user.getFarmUniqueId() != null) sActiveFarmId = a.user.getFarmUniqueId();
    }

    private Account getActiveAccount() {
        return findAccount(getAccounts(), getActiveAccountId());
    }

    // ===== SESSION (compte actif) =====

    public void createSession(User user, String token) {
        createSession(user, token, null);
    }

    /** Enregistre/actualise la session d'un compte SANS toucher aux autres comptes déjà
     * mémorisés sur l'appareil : retrouvé par userId, mis à jour s'il existe déjà (ex:
     * nouveau scan QR du même agent après expiration), sinon ajouté. Ce compte devient
     * le compte actif. */
    public void createSession(User user, String token, String refreshToken) {
        List<Account> accounts = getAccounts();
        Account account = findAccount(accounts, user.getId());
        if (account == null) {
            account = new Account();
            account.userId = user.getId();
            accounts.add(account);
        }
        // Nouveau scan du même compte : la ferme et le drapeau "consultation seule" déjà
        // connus (voir mettreAJourProfil) sont gardés, le JWT du QR ne les porte pas.
        if (account.user != null) {
            if (user.getFarmUniqueId() == null) user.setFarmUniqueId(account.user.getFarmUniqueId());
            if (user.getConsultationSeule() == null) user.setConsultationSeule(account.user.getConsultationSeule());
        }
        account.user = user;
        account.token = token;
        if (refreshToken != null) {
            account.refreshToken = refreshToken;
        }
        if (account.currentProjetId == null && user.getProjetsAssignes() != null && !user.getProjetsAssignes().isEmpty()) {
            account.currentProjetId = user.getProjetsAssignes().get(0);
        }
        saveAccounts(accounts);
        setActiveAccountId(user.getId());
        safeEdit(editor -> editor.putBoolean(KEY_IS_LOGGED_IN, true));
    }

    public User getCurrentUser() {
        Account a = getActiveAccount();
        return a != null ? a.user : null;
    }

    public boolean isLoggedIn() {
        return safeGetBoolean(KEY_IS_LOGGED_IN, false) && getActiveAccount() != null;
    }

    public String getToken() {
        Account a = getActiveAccount();
        return a != null ? a.token : null;
    }

    public String getRefreshToken() {
        Account a = getActiveAccount();
        return a != null ? a.refreshToken : null;
    }

    public String getCurrentProjetId() {
        Account a = getActiveAccount();
        return a != null ? a.currentProjetId : null;
    }

    public void setCurrentProjetId(String projetId) {
        List<Account> accounts = getAccounts();
        Account a = findAccount(accounts, getActiveAccountId());
        if (a == null) return;
        a.currentProjetId = projetId;
        saveAccounts(accounts);
    }

    /** Déconnexion "classique" (icône profil sur l'accueil) = verrouillage, PAS un
     * effacement : on ne touche ni au token ni au mot de passe local du compte actif,
     * seulement au drapeau is_logged_in (qui n'est pas par compte : c'est un état
     * d'écran, "app déverrouillée ou non"). Retaper le mot de passe local d'un compte —
     * le même ou un AUTRE compte mémorisé sur cet appareil (voir switchToAccountMatching)
     * — suffit à revenir, sans réseau ni nouveau scan QR. Pour retirer complètement un
     * compte de l'appareil, voir deleteAccount() (écran Paramètres). */
    public void lockSession() {
        safeEdit(editor -> editor.putBoolean(KEY_IS_LOGGED_IN, false));
    }

    /** Réactive la session du compte actif après un déverrouillage réussi par mot de
     * passe local (même compte). Pour activer un AUTRE compte, voir
     * switchToAccountMatching(), qui appelle déjà ceci en interne. */
    public void unlockSession() {
        safeEdit(editor -> editor.putBoolean(KEY_IS_LOGGED_IN, true));
    }

    /** Cherche, PARMI TOUS LES COMPTES mémorisés sur l'appareil (pas seulement le compte
     * actif), celui dont l'identifiant et le mot de passe local correspondent, puis
     * l'active. C'est ce qui permet à un second (ou troisième...) utilisateur de se
     * reconnecter hors ligne après que le compte actif s'est déconnecté (voir
     * LoginActivity.tryOfflineLogin), sans jamais écraser la session de personne.
     * Retourne true si un compte correspondant a été trouvé et activé. */
    public boolean switchToAccountMatching(String identifiant, String password) {
        if (identifiant == null || password == null) return false;
        String trimmed = identifiant.trim();
        for (Account a : getAccounts()) {
            if (a.localPasswordHash == null || a.localPasswordIdentifiants == null) continue;
            boolean identifiantReconnu = false;
            for (String accepted : a.localPasswordIdentifiants) {
                if (accepted.equalsIgnoreCase(trimmed)) {
                    identifiantReconnu = true;
                    break;
                }
            }
            if (!identifiantReconnu) continue;
            if (!LocalPasswordHasher.matches(password, a.localPasswordHash)) continue;

            setActiveAccountId(a.userId);
            unlockSession();
            return true;
        }
        return false;
    }

    /** Retire complètement le compte ACTIF de l'appareil (session, token et mot de passe
     * local) — les autres comptes mémorisés ne sont pas affectés. S'il en reste un
     * autre, il devient le compte actif ; sinon plus aucun compte n'est actif (retour à
     * l'écran de connexion, nouveau scan QR nécessaire pour ce compte). Ses saisies en
     * attente restent sur l'appareil (cloisonnées par compte, voir LocalDatabase) : elles
     * ne seront envoyées que si ce compte est de nouveau scanné ici. */
    public void deleteAccount() {
        List<Account> accounts = getAccounts();
        String activeId = getActiveAccountId();
        accounts.removeIf(a -> activeId != null && activeId.equals(a.userId));
        saveAccounts(accounts);
        String nextActiveId = accounts.isEmpty() ? null : accounts.get(0).userId;
        safeEdit(editor -> {
            if (nextActiveId != null) {
                editor.putString(KEY_ACTIVE_ACCOUNT_ID, nextActiveId);
            } else {
                editor.remove(KEY_ACTIVE_ACCOUNT_ID);
            }
            editor.putBoolean(KEY_IS_LOGGED_IN, false);
        });
        sActiveUserId = nextActiveId;
        Account suivant = findAccount(accounts, nextActiveId);
        sActiveFarmId = suivant != null && suivant.user != null ? suivant.user.getFarmUniqueId() : null;
        sActiveUserIdCharge = true;
    }

    /** true s'il reste au moins un compte mémorisé sur cet appareil (avant ou après un
     * deleteAccount()) — utilisé par l'écran Paramètres pour savoir si le cache local
     * générique (projets, saisies en attente...) peut être purgé sans risque d'emporter
     * les données d'un AUTRE compte encore présent (voir DiagnosticsActivity). */
    public boolean hasAnyAccount() {
        return !getAccounts().isEmpty();
    }

    public boolean isQRValid() {
        User user = getCurrentUser();
        if (user == null || user.getQrExpiry() == 0) return false;
        return System.currentTimeMillis() < user.getQrExpiry();
    }

    // ===== MOT DE PASSE LOCAL (accès hors ligne, par compte) =====
    // Défini juste après un scan QR réussi (voir CameraScanActivity) : c'est ce mot de
    // passe, vérifié uniquement sur l'appareil, qui permet ensuite de se reconnecter par
    // le formulaire identifiant/mot de passe classique quand le réseau est indisponible,
    // en réutilisant la session déjà stockée (token). Toute l'app est pensée pour un
    // usage hors ligne : ce mot de passe local EST le moyen d'accès hors ligne, pas un
    // simple raccourci de confort. Chaque compte a le sien (voir Account) : celui du
    // compte ACTIF au moment de l'appel pour setLocalPassword/hasLocalPassword/etc. —
    // voir switchToAccountMatching() pour la recherche à travers TOUS les comptes.
    //
    // Le backend accepte indifféremment username, email OU téléphone comme identifiant
    // de connexion (voir AuthImpl.jwt côté back : findByEmailOrUsernameOrTelephone...).
    // Si on ne retenait que le username renvoyé par /auth/me (ce qui était fait avant),
    // un agent qui a l'habitude de se connecter avec son numéro de téléphone tapait un
    // identifiant qui ne correspondait jamais à celui enregistré au scan — l'accès hors
    // ligne semblait ne "jamais avoir enregistré le mot de passe" alors qu'il l'avait
    // bien fait, sous une autre forme d'identifiant. On enregistre donc les trois et on
    // accepte n'importe lequel d'entre eux à la reconnexion hors ligne.

    public void setLocalPassword(List<String> acceptedIdentifiants, String password) {
        List<String> cleaned = new ArrayList<>();
        for (String candidate : acceptedIdentifiants) {
            if (candidate != null && !candidate.trim().isEmpty()) {
                cleaned.add(candidate.trim());
            }
        }
        List<Account> accounts = getAccounts();
        Account a = findAccount(accounts, getActiveAccountId());
        if (a == null) return;
        a.localPasswordHash = LocalPasswordHasher.hash(password);
        a.localPasswordIdentifiants = cleaned;
        saveAccounts(accounts);
    }

    /** true si le compte ACTIF a déjà un mot de passe local défini (pas les autres
     * comptes mémorisés sur l'appareil — voir switchToAccountMatching pour ceux-là). */
    public boolean hasLocalPassword() {
        Account a = getActiveAccount();
        return a != null && a.localPasswordHash != null;
    }

    public boolean verifyLocalPassword(String password) {
        Account a = getActiveAccount();
        return a != null && a.localPasswordHash != null && LocalPasswordHasher.matches(password, a.localPasswordHash);
    }

    /** true si candidate correspond (insensible à la casse/aux espaces) à l'un des
     * identifiants (username/email/téléphone) enregistrés avec le mot de passe local
     * du compte ACTIF. */
    public boolean matchesLocalPasswordIdentifiant(String candidate) {
        if (candidate == null) return false;
        String trimmed = candidate.trim();
        for (String accepted : getLocalPasswordIdentifiants()) {
            if (accepted.equalsIgnoreCase(trimmed)) return true;
        }
        return false;
    }

    public List<String> getLocalPasswordIdentifiants() {
        Account a = getActiveAccount();
        if (a == null || a.localPasswordIdentifiants == null) return new ArrayList<>();
        return a.localPasswordIdentifiants;
    }

    public void clearLocalPassword() {
        List<Account> accounts = getAccounts();
        Account a = findAccount(accounts, getActiveAccountId());
        if (a == null) return;
        a.localPasswordHash = null;
        a.localPasswordIdentifiants = new ArrayList<>();
        saveAccounts(accounts);
    }
}
