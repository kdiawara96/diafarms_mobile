package com.mobile.diafarms.data;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.mobile.diafarms.models.SaisieLocale;
import com.mobile.diafarms.models.SaisieType;
import com.mobile.diafarms.models.User;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LocalDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "diafarms.db";
    // v6 : fusion Soins/Vaccination côté UI — SaisieType.VACCINATION supprimé (un seul
    // bouton/écran "Soins" désormais, voir HomeActivity/SaisieFormActivity). Une
    // saisie VACCINATION en attente, créée avant cette mise à jour, a sa colonne
    // "type" qui ne correspond plus à aucune constante de l'enum : SaisieType.valueOf
    // lèverait IllegalArgumentException à la lecture — destruction simple (déjà le
    // pattern de ce fichier, voir onUpgrade), acceptable en développement (voir v5
    // ci-dessous pour le précédent similaire lors de la fusion côté back).
    // v5 : fusion Soins/Vaccination côté back — le payload_json d'une saisie
    // VACCINATION en attente change de forme (VaccinCreateRequest -> SoinsCreateRequest
    // unifié : nomVaccin -> produit, quantite Integer -> Double, date/heure ajoutés).
    // Une saisie VACCINATION non encore synchronisée avant cette mise à jour serait
    // désérialisée avec l'ancienne forme et enverrait un payload invalide au nouvel
    // endpoint /soins/create — destruction simple (déjà le pattern de ce fichier,
    // voir onUpgrade), acceptable en développement.
    // v7 (1.31) : saisies et cache cloisonnés par compte. saisies_locales gagne owner_user_id
    // / owner_farm_id (compte et ferme qui ont saisi), cle_envoi (clé stable par saisie,
    // future clé d'idempotence) et http_code (dernier échec d'envoi). Migration SANS perte
    // (voir onUpgrade) : les saisies en attente sont gardées.
    private static final int DATABASE_VERSION = 7;

    // Tables
    private static final String TABLE_ACCOUNTS = "accounts";
    private static final String TABLE_SAISIES = "saisies_locales";
    private static final String TABLE_CACHE = "local_cache";

    // Colonnes local_cache : cache générique clé/valeur (JSON) pour les données lues du
    // serveur (projets, détail projet, alertes...) — permet à l'app de démarrer et
    // afficher les dernières données connues même hors ligne, plutôt que de dépendre
    // d'un appel réseau réussi à chaque ouverture. Rafraîchi à chaque appel réseau
    // réussi et après chaque synchronisation (voir HomeActivity/SyncManager).
    private static final String COL_CACHE_KEY = "cache_key";
    private static final String COL_CACHE_VALUE = "cache_value";
    private static final String COL_CACHE_UPDATED_AT = "cache_updated_at";

    // Colonnes accounts (comptes déjà utilisés pour se connecter sur cet appareil,
    // afin de proposer l'identifiant en sélection plutôt qu'en ressaisie systématique)
    private static final String COL_IDENTIFIANT = "identifiant";
    private static final String COL_LAST_USED = "last_used";

    // Colonnes saisies_locales : une ligne = une saisie (soins, mortalité, collecte,
    // aliment, transaction...) en attente de synchronisation ou déjà synchronisée.
    // Le détail métier (quantités, montant...) est porté par payload_json, qui
    // correspond exactement au *Create DTO backend du type concerné.
    private static final String COL_LOCAL_ID = "local_id";
    private static final String COL_TYPE = "type";
    private static final String COL_PROJET_UNIQUE_ID = "projet_unique_id";
    private static final String COL_PROJET_LABEL = "projet_label";
    private static final String COL_PAYLOAD_JSON = "payload_json";
    private static final String COL_DISPLAY_SUMMARY = "display_summary";
    private static final String COL_SYNC_STATUS = "sync_status";
    private static final String COL_SERVER_UNIQUE_ID = "server_unique_id";
    private static final String COL_ERROR_MESSAGE = "error_message";
    private static final String COL_CREATED_AT = "created_at";
    private static final String COL_OWNER_USER_ID = "owner_user_id";
    private static final String COL_OWNER_FARM_ID = "owner_farm_id";
    private static final String COL_CLE_ENVOI = "cle_envoi";
    private static final String COL_HTTP_CODE = "http_code";

    private final Context appContext;
    // Compte figé (voir figee()) : null = toujours le compte actif au moment de l'appel.
    private final String compteFige;
    private final boolean estFigee;

    public LocalDatabase(Context context) {
        this(context, null, false);
    }

    private LocalDatabase(Context context, String compteFige, boolean estFigee) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.appContext = context.getApplicationContext();
        this.compteFige = compteFige;
        this.estFigee = estFigee;
    }

    /** Même base, mais liée au compte actif AU MOMENT de l'appel, même s'il change
     * ensuite : pour les réponses réseau qui arrivent tard (préchargement), afin qu'elles
     * soient rangées dans le cache du compte qui les a demandées. */
    public LocalDatabase figee() {
        return new LocalDatabase(appContext, compteCourant(), true);
    }

    /** Compte dont cette instance lit/écrit les saisies et le cache. */
    public String compteCourant() {
        return estFigee ? compteFige : SessionManager.activeUserId(appContext);
    }

    /** Clé réelle en base : le cache est rangé par compte (u:<id>|clé). */
    private String cleCache(String key) {
        String compte = compteCourant();
        return (compte != null ? "u:" + compte : "aucun") + "|" + key;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createAccounts = "CREATE TABLE " + TABLE_ACCOUNTS + "("
                + COL_IDENTIFIANT + " TEXT PRIMARY KEY,"
                + COL_LAST_USED + " INTEGER"
                + ")";

        String createSaisies = "CREATE TABLE " + TABLE_SAISIES + "("
                + COL_LOCAL_ID + " TEXT PRIMARY KEY,"
                + COL_TYPE + " TEXT NOT NULL,"
                + COL_PROJET_UNIQUE_ID + " TEXT,"
                + COL_PROJET_LABEL + " TEXT,"
                + COL_PAYLOAD_JSON + " TEXT NOT NULL,"
                + COL_DISPLAY_SUMMARY + " TEXT,"
                + COL_SYNC_STATUS + " TEXT NOT NULL,"
                + COL_SERVER_UNIQUE_ID + " TEXT,"
                + COL_ERROR_MESSAGE + " TEXT,"
                + COL_CREATED_AT + " INTEGER,"
                + COL_OWNER_USER_ID + " TEXT,"
                + COL_OWNER_FARM_ID + " TEXT,"
                + COL_CLE_ENVOI + " TEXT,"
                + COL_HTTP_CODE + " INTEGER"
                + ")";

        String createCache = "CREATE TABLE " + TABLE_CACHE + "("
                + COL_CACHE_KEY + " TEXT PRIMARY KEY,"
                + COL_CACHE_VALUE + " TEXT NOT NULL,"
                + COL_CACHE_UPDATED_AT + " INTEGER"
                + ")";

        db.execSQL(createAccounts);
        db.execSQL(createSaisies);
        db.execSQL(createCache);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion >= 6) {
            migrerVersV7(db);
            return;
        }
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ACCOUNTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SAISIES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CACHE);
        db.execSQL("DROP TABLE IF EXISTS enregistrements");
        db.execSQL("DROP TABLE IF EXISTS transactions");
        onCreate(db);
    }

    /** 6 -> 7, sans perdre de saisie. Les saisies existantes n'avaient pas de compte :
     * s'il n'y a qu'un compte sur l'appareil, elles sont à lui (c'était de toute façon le
     * seul jeton possible pour les envoyer). Sinon elles restent "compte inconnu" et ne
     * partent pas tant qu'un utilisateur ne les a pas reconnues comme siennes (voir
     * HomeActivity, adopterSaisiesSansCompte). Le cache (données serveur) du seul compte
     * est rangé à son nom ; avec plusieurs comptes on ne sait pas à qui il appartient,
     * il est effacé et sera rechargé à la prochaine connexion. */
    private void migrerVersV7(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE_SAISIES + " ADD COLUMN " + COL_OWNER_USER_ID + " TEXT");
        db.execSQL("ALTER TABLE " + TABLE_SAISIES + " ADD COLUMN " + COL_OWNER_FARM_ID + " TEXT");
        db.execSQL("ALTER TABLE " + TABLE_SAISIES + " ADD COLUMN " + COL_CLE_ENVOI + " TEXT");
        db.execSQL("ALTER TABLE " + TABLE_SAISIES + " ADD COLUMN " + COL_HTTP_CODE + " INTEGER");
        // local_id est déjà un UUID fixe par saisie : il sert de clé stable aux saisies existantes.
        db.execSQL("UPDATE " + TABLE_SAISIES + " SET " + COL_CLE_ENVOI + " = " + COL_LOCAL_ID
                + " WHERE " + COL_CLE_ENVOI + " IS NULL");

        String seulCompte = null;
        try {
            seulCompte = new SessionManager(appContext).seulCompteId();
        } catch (Exception ignored) {
            // préférences illisibles : on reste prudent (saisies "compte inconnu", cache effacé)
        }
        if (seulCompte != null) {
            db.execSQL("UPDATE " + TABLE_SAISIES + " SET " + COL_OWNER_USER_ID + " = ? WHERE "
                    + COL_OWNER_USER_ID + " IS NULL", new Object[]{seulCompte});
            db.execSQL("UPDATE " + TABLE_CACHE + " SET " + COL_CACHE_KEY + " = ? || " + COL_CACHE_KEY,
                    new Object[]{"u:" + seulCompte + "|"});
        } else {
            db.delete(TABLE_CACHE, null, null);
        }
    }

    // ===== CACHE LOCAL (données serveur pour affichage hors ligne) =====

    public void putCache(String key, String jsonValue) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_CACHE_KEY, cleCache(key));
        values.put(COL_CACHE_VALUE, jsonValue);
        values.put(COL_CACHE_UPDATED_AT, System.currentTimeMillis());
        db.insertWithOnConflict(TABLE_CACHE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void deleteCache(String key) {
        getWritableDatabase().delete(TABLE_CACHE, COL_CACHE_KEY + "=?", new String[]{cleCache(key)});
    }

    public String getCache(String key) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_CACHE, new String[]{COL_CACHE_VALUE},
                COL_CACHE_KEY + "=?", new String[]{cleCache(key)}, null, null, null);
        String result = null;
        if (cursor.moveToFirst()) {
            result = cursor.getString(0);
        }
        cursor.close();
        return result;
    }

    /** Horodatage (epoch ms) de la dernière mise à jour de cette entrée, 0 si absente —
     * utilisé pour afficher "données du ..." quand on retombe sur le cache hors ligne. */
    public long getCacheUpdatedAt(String key) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_CACHE, new String[]{COL_CACHE_UPDATED_AT},
                COL_CACHE_KEY + "=?", new String[]{cleCache(key)}, null, null, null);
        long result = 0;
        if (cursor.moveToFirst() && !cursor.isNull(0)) {
            result = cursor.getLong(0);
        }
        cursor.close();
        return result;
    }

    // ===== COMPTES LOCAUX (sélecteur d'identifiant) =====

    /** Enregistre/rafraîchit un identifiant utilisé avec succès, pour l'afficher au prochain login. */
    public void saveAccountIdentifiant(String identifiant) {
        if (identifiant == null || identifiant.trim().isEmpty()) return;

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_IDENTIFIANT, identifiant.trim());
        values.put(COL_LAST_USED, System.currentTimeMillis());
        db.insertWithOnConflict(TABLE_ACCOUNTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Identifiants déjà utilisés sur cet appareil, du plus récent au plus ancien. */
    public List<String> getSavedIdentifiants() {
        List<String> identifiants = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_ACCOUNTS, new String[]{COL_IDENTIFIANT},
                null, null, null, null, COL_LAST_USED + " DESC");

        if (cursor.moveToFirst()) {
            do {
                identifiants.add(cursor.getString(cursor.getColumnIndexOrThrow(COL_IDENTIFIANT)));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return identifiants;
    }

    // ===== SAISIES LOCALES =====

    /**
     * Crée une nouvelle saisie locale (statut LOCAL) et retourne son localId généré. Elle
     * appartient au compte actif (et à sa ferme si connue) : seul ce compte l'enverra.
     */
    public String insertSaisie(SaisieType type, String projetUniqueId, String projetLabel,
                               String payloadJson, String displaySummary) {
        String localId = UUID.randomUUID().toString();

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_LOCAL_ID, localId);
        values.put(COL_TYPE, type.name());
        values.put(COL_PROJET_UNIQUE_ID, projetUniqueId);
        values.put(COL_PROJET_LABEL, projetLabel);
        values.put(COL_PAYLOAD_JSON, payloadJson);
        values.put(COL_DISPLAY_SUMMARY, displaySummary);
        values.put(COL_SYNC_STATUS, SaisieLocale.STATUT_LOCAL);
        values.put(COL_CREATED_AT, System.currentTimeMillis());
        String compte = compteCourant();
        values.put(COL_OWNER_USER_ID, compte);
        values.put(COL_OWNER_FARM_ID, fermeCourante(compte));
        values.put(COL_CLE_ENVOI, UUID.randomUUID().toString());

        db.insert(TABLE_SAISIES, null, values);
        return localId;
    }

    /** Remplace le contenu d'une saisie et la repasse à LOCAL (message d'erreur effacé).
     * Appelée pour une saisie LOCAL/ERROR modifiée, et aussi pour une session de pesée
     * déjà SYNCED qui reçoit une nouvelle pesée/annulation/clôture (elle repart alors
     * au prochain envoi). server_unique_id n'est pas touché. */
    public void updateSaisie(String localId, String projetUniqueId, String projetLabel,
                              String payloadJson, String displaySummary) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        // Corrigée après un refus définitif (4xx) : c'est une nouvelle demande pour le
        // serveur, elle reçoit une nouvelle clé (l'ancienne donnerait 422). Dans tous les
        // autres cas la clé est gardée (renvoi de la même saisie).
        SaisieLocale avant = getSaisieById(localId);
        if (avant != null && SaisieLocale.STATUT_ERROR.equals(avant.getSyncStatus()) && estRefusDefinitif(avant.getHttpCode())) {
            values.put(COL_CLE_ENVOI, UUID.randomUUID().toString());
        }
        values.putNull(COL_HTTP_CODE);
        values.put(COL_PROJET_UNIQUE_ID, projetUniqueId);
        values.put(COL_PROJET_LABEL, projetLabel);
        values.put(COL_PAYLOAD_JSON, payloadJson);
        values.put(COL_DISPLAY_SUMMARY, displaySummary);
        values.put(COL_SYNC_STATUS, SaisieLocale.STATUT_LOCAL);
        values.putNull(COL_ERROR_MESSAGE);
        db.update(TABLE_SAISIES, values, COL_LOCAL_ID + "=?", new String[]{localId});
    }

    /** Refus 4xx définitif : ni authentification (401/403), ni « réessayez » (408/409/429). */
    private static boolean estRefusDefinitif(Integer code) {
        return code != null && code >= 400 && code < 500
                && code != 401 && code != 403 && code != 408 && code != 409 && code != 429;
    }

    /** Échec temporaire (réseau, 5xx, 409) : la saisie reste EN ATTENTE, même clé, avec une
     * note affichée dans « Mes saisies ». */
    public void marquerARenvoyer(String localId, String note, int httpCode) {
        ContentValues values = new ContentValues();
        values.put(COL_SYNC_STATUS, SaisieLocale.STATUT_LOCAL);
        values.put(COL_ERROR_MESSAGE, note);
        values.put(COL_HTTP_CODE, httpCode);
        getWritableDatabase().update(TABLE_SAISIES, values, COL_LOCAL_ID + "=?", new String[]{localId});
    }

    public void deleteSaisie(String localId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_SAISIES, COL_LOCAL_ID + "=?", new String[]{localId});
    }

    public void markSynced(String localId, String serverUniqueId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_SYNC_STATUS, SaisieLocale.STATUT_SYNCED);
        values.put(COL_SERVER_UNIQUE_ID, serverUniqueId);
        values.putNull(COL_ERROR_MESSAGE);
        values.putNull(COL_HTTP_CODE);
        db.update(TABLE_SAISIES, values, COL_LOCAL_ID + "=?", new String[]{localId});
    }

    /**
     * Comme markSynced, mais seulement si payload_json est encore celui qui a été envoyé.
     * Pour une saisie réécrite au fil de l'eau (session de pesée) : si une pesée a été
     * ajoutée pendant l'envoi, la ligne doit rester LOCAL pour que ce nouvel état parte
     * au prochain envoi. Retourne true si la ligne a été marquée synchronisée.
     * server_unique_id est enregistré dans tous les cas (réponse 2xx = la session existe
     * côté serveur), même si la ligne reste LOCAL.
     */
    public boolean markSyncedIfPayloadUnchanged(String localId, String serverUniqueId, String sentPayloadJson) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues idValues = new ContentValues();
        idValues.put(COL_SERVER_UNIQUE_ID, serverUniqueId);
        db.update(TABLE_SAISIES, idValues, COL_LOCAL_ID + "=?", new String[]{localId});

        ContentValues values = new ContentValues();
        values.put(COL_SYNC_STATUS, SaisieLocale.STATUT_SYNCED);
        values.put(COL_SERVER_UNIQUE_ID, serverUniqueId);
        values.putNull(COL_ERROR_MESSAGE);
        return db.update(TABLE_SAISIES, values, COL_LOCAL_ID + "=? AND " + COL_PAYLOAD_JSON + "=?",
                new String[]{localId, sentPayloadJson}) > 0;
    }

    /** Après un envoi réussi d'une session de pesée : nouvel état local (indicateurs
     * « envoyée » posés, voir SyncManager.marquerSessionPeseeEnvoyee), server_unique_id,
     * et statut SYNCED si cet état est celui du serveur, LOCAL sinon (reste à envoyer). */
    public void enregistrerApresEnvoi(String localId, String serverUniqueId, String payloadJson,
                                      String displaySummary, boolean synchronisee) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_PAYLOAD_JSON, payloadJson);
        values.put(COL_DISPLAY_SUMMARY, displaySummary);
        values.put(COL_SERVER_UNIQUE_ID, serverUniqueId);
        values.put(COL_SYNC_STATUS, synchronisee ? SaisieLocale.STATUT_SYNCED : SaisieLocale.STATUT_LOCAL);
        values.putNull(COL_ERROR_MESSAGE);
        values.putNull(COL_HTTP_CODE);
        db.update(TABLE_SAISIES, values, COL_LOCAL_ID + "=?", new String[]{localId});
    }

    /** Comme markError, mais sans effet si le contenu a changé depuis l'envoi (voir markSyncedIfPayloadUnchanged). */
    public void markErrorIfPayloadUnchanged(String localId, String errorMessage, String sentPayloadJson, Integer httpCode) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_SYNC_STATUS, SaisieLocale.STATUT_ERROR);
        values.put(COL_ERROR_MESSAGE, errorMessage);
        if (httpCode != null) values.put(COL_HTTP_CODE, httpCode); else values.putNull(COL_HTTP_CODE);
        db.update(TABLE_SAISIES, values, COL_LOCAL_ID + "=? AND " + COL_PAYLOAD_JSON + "=?",
                new String[]{localId, sentPayloadJson});
    }

    public void markError(String localId, String errorMessage) {
        markError(localId, errorMessage, null);
    }

    /** httpCode : code HTTP de la réponse, 0 si le réseau a échoué (voir SaisieLocale.seraRenvoyee). */
    public void markError(String localId, String errorMessage, Integer httpCode) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_SYNC_STATUS, SaisieLocale.STATUT_ERROR);
        values.put(COL_ERROR_MESSAGE, errorMessage);
        if (httpCode != null) values.put(COL_HTTP_CODE, httpCode); else values.putNull(COL_HTTP_CODE);
        db.update(TABLE_SAISIES, values, COL_LOCAL_ID + "=?", new String[]{localId});
    }

    public SaisieLocale getSaisieById(String localId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_SAISIES, null, COL_LOCAL_ID + "=?", new String[]{localId},
                null, null, null);
        SaisieLocale result = null;
        if (cursor.moveToFirst()) {
            result = cursorToSaisie(cursor);
        }
        cursor.close();
        return result;
    }

    /** Filtre SQL "saisies du compte courant" (aucune si aucun compte actif). */
    private String filtreCompte() {
        return COL_OWNER_USER_ID + "=?";
    }

    private String argCompte() {
        String compte = compteCourant();
        return compte != null ? compte : "-aucun-compte-";
    }

    /** Toutes les saisies du compte courant, les plus récentes d'abord. */
    public List<SaisieLocale> getAllSaisies() {
        return querySaisies(filtreCompte(), new String[]{argCompte()});
    }

    /** Saisies du compte courant d'un type donné (ex: pour filtrer l'écran "Mes saisies"). */
    public List<SaisieLocale> getSaisiesByType(SaisieType type) {
        return querySaisies(COL_TYPE + "=? AND " + filtreCompte(), new String[]{type.name(), argCompte()});
    }

    /** Saisies d'un type à prendre en compte dans les contrôles locaux (stock, plafonds) :
     * celles du compte courant, plus celles des autres comptes de LA MÊME ferme présentes
     * sur ce téléphone (elles partiront aussi, et consommeront le même stock). */
    public List<SaisieLocale> getSaisiesPourControles(SaisieType type) {
        String compte = compteCourant();
        String ferme = fermeCourante(compte);
        if (ferme == null) return getSaisiesByType(type);
        return querySaisies(COL_TYPE + "=? AND (" + filtreCompte() + " OR " + COL_OWNER_FARM_ID + "=?)",
                new String[]{type.name(), argCompte(), ferme});
    }

    /** Saisies du compte courant pas encore synchronisées (LOCAL ou en erreur) : les
     * seules que SyncManager envoie, avec le jeton de ce compte. */
    public List<SaisieLocale> getPendingSaisies() {
        return querySaisies(COL_SYNC_STATUS + " IN (?,?) AND " + filtreCompte(),
                new String[]{SaisieLocale.STATUT_LOCAL, SaisieLocale.STATUT_ERROR, argCompte()});
    }

    public int countPending() {
        return getPendingSaisies().size();
    }

    /** Saisies en attente de TOUS les comptes de l'appareil (avertissements avant un effacement). */
    public int countPendingTous() {
        return compter(COL_SYNC_STATUS + " IN (?,?)",
                new String[]{SaisieLocale.STATUT_LOCAL, SaisieLocale.STATUT_ERROR});
    }

    /** Saisies en attente appartenant à un autre compte (ou à un compte inconnu) : le
     * compte courant ne peut pas les envoyer. */
    public int countPendingAutresComptes() {
        return compter(COL_SYNC_STATUS + " IN (?,?) AND (" + COL_OWNER_USER_ID + " IS NULL OR "
                        + COL_OWNER_USER_ID + "<>?)",
                new String[]{SaisieLocale.STATUT_LOCAL, SaisieLocale.STATUT_ERROR, argCompte()});
    }

    /** Saisies en attente créées avant la 1.31 sans compte connu (voir migrerVersV7). */
    public int countPendingSansCompte() {
        return compter(COL_SYNC_STATUS + " IN (?,?) AND " + COL_OWNER_USER_ID + " IS NULL",
                new String[]{SaisieLocale.STATUT_LOCAL, SaisieLocale.STATUT_ERROR});
    }

    /** L'utilisateur actif reconnaît les saisies "compte inconnu" comme les siennes. */
    public void adopterSaisiesSansCompte() {
        String compte = compteCourant();
        if (compte == null) return;
        ContentValues values = new ContentValues();
        values.put(COL_OWNER_USER_ID, compte);
        values.put(COL_OWNER_FARM_ID, fermeCourante(compte));
        getWritableDatabase().update(TABLE_SAISIES, values, COL_OWNER_USER_ID + " IS NULL", null);
    }

    private int compter(String selection, String[] args) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + TABLE_SAISIES + " WHERE " + selection, args);
        int n = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return n;
    }

    /** Ferme connue du compte donné, seulement si c'est le compte actif (sinon inconnue). */
    private String fermeCourante(String compte) {
        if (compte == null) return null;
        try {
            SessionManager sm = new SessionManager(appContext);
            User u = sm.getCurrentUser();
            return u != null && compte.equals(u.getId()) ? u.getFarmUniqueId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Vide toutes les données locales (saisies en attente/synchronisées/en erreur et
     * comptes mémorisés) — utilisé par l'écran Diagnostics pour repartir d'une base
     * propre en cas d'incohérence sur le terrain. Retourne le nombre de saisies non
     * synchronisées qui seront perdues, pour que l'appelant puisse avertir avant confirmation.
     */
    public int clearAllLocalData() {
        int pendingCount = countPendingTous();
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_SAISIES, null, null);
        db.delete(TABLE_ACCOUNTS, null, null);
        db.delete(TABLE_CACHE, null, null);
        return pendingCount;
    }

    /**
     * Ne vide que le cache serveur générique (projets, détail projet, alertes, stock...)
     * — contrairement à clearAllLocalData(), ne touche ni aux saisies en attente/déjà
     * synchronisées, ni aux comptes/session enregistrés. Action sans risque de perte de
     * données : force juste un re-téléchargement complet au prochain accès réseau, utile
     * quand les données affichées semblent incohérentes/périmées sans vouloir perdre de
     * saisies ni se reconnecter.
     */
    public void clearCacheOnly() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_CACHE, null, null);
    }

    private List<SaisieLocale> querySaisies(String selection, String[] selectionArgs) {
        List<SaisieLocale> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_SAISIES, null, selection, selectionArgs,
                null, null, COL_CREATED_AT + " DESC");

        if (cursor.moveToFirst()) {
            do {
                list.add(cursorToSaisie(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    private SaisieLocale cursorToSaisie(Cursor c) {
        SaisieLocale s = new SaisieLocale();
        s.setLocalId(c.getString(c.getColumnIndexOrThrow(COL_LOCAL_ID)));
        s.setType(SaisieType.valueOf(c.getString(c.getColumnIndexOrThrow(COL_TYPE))));
        s.setProjetUniqueId(c.getString(c.getColumnIndexOrThrow(COL_PROJET_UNIQUE_ID)));
        s.setProjetLabel(c.getString(c.getColumnIndexOrThrow(COL_PROJET_LABEL)));
        s.setPayloadJson(c.getString(c.getColumnIndexOrThrow(COL_PAYLOAD_JSON)));
        s.setDisplaySummary(c.getString(c.getColumnIndexOrThrow(COL_DISPLAY_SUMMARY)));
        s.setSyncStatus(c.getString(c.getColumnIndexOrThrow(COL_SYNC_STATUS)));
        s.setServerUniqueId(c.getString(c.getColumnIndexOrThrow(COL_SERVER_UNIQUE_ID)));
        s.setErrorMessage(c.getString(c.getColumnIndexOrThrow(COL_ERROR_MESSAGE)));
        s.setCreatedAt(c.getLong(c.getColumnIndexOrThrow(COL_CREATED_AT)));
        s.setOwnerUserId(c.getString(c.getColumnIndexOrThrow(COL_OWNER_USER_ID)));
        s.setOwnerFarmId(c.getString(c.getColumnIndexOrThrow(COL_OWNER_FARM_ID)));
        s.setCleEnvoi(c.getString(c.getColumnIndexOrThrow(COL_CLE_ENVOI)));
        int iCode = c.getColumnIndexOrThrow(COL_HTTP_CODE);
        s.setHttpCode(c.isNull(iCode) ? null : c.getInt(iCode));
        return s;
    }
}
