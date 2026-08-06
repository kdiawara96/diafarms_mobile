package com.mobile.diafarms.data;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.mobile.diafarms.models.SaisieLocale;
import com.mobile.diafarms.models.SaisieType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LocalDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "diafarms.db";
    private static final int DATABASE_VERSION = 4;

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

    public LocalDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
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
                + COL_CREATED_AT + " INTEGER"
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
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ACCOUNTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SAISIES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CACHE);
        db.execSQL("DROP TABLE IF EXISTS enregistrements");
        db.execSQL("DROP TABLE IF EXISTS transactions");
        onCreate(db);
    }

    // ===== CACHE LOCAL (données serveur pour affichage hors ligne) =====

    public void putCache(String key, String jsonValue) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_CACHE_KEY, key);
        values.put(COL_CACHE_VALUE, jsonValue);
        values.put(COL_CACHE_UPDATED_AT, System.currentTimeMillis());
        db.insertWithOnConflict(TABLE_CACHE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String getCache(String key) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_CACHE, new String[]{COL_CACHE_VALUE},
                COL_CACHE_KEY + "=?", new String[]{key}, null, null, null);
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
                COL_CACHE_KEY + "=?", new String[]{key}, null, null, null);
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
     * Crée une nouvelle saisie locale (statut LOCAL) et retourne son localId généré.
     */
    public void insertSaisie(SaisieType type, String projetUniqueId, String projetLabel,
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

        db.insert(TABLE_SAISIES, null, values);
    }

    /** Met à jour le contenu d'une saisie encore LOCAL/ERROR (repasse à LOCAL après modification). */
    public void updateSaisie(String localId, String projetUniqueId, String projetLabel,
                              String payloadJson, String displaySummary) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_PROJET_UNIQUE_ID, projetUniqueId);
        values.put(COL_PROJET_LABEL, projetLabel);
        values.put(COL_PAYLOAD_JSON, payloadJson);
        values.put(COL_DISPLAY_SUMMARY, displaySummary);
        values.put(COL_SYNC_STATUS, SaisieLocale.STATUT_LOCAL);
        values.putNull(COL_ERROR_MESSAGE);
        db.update(TABLE_SAISIES, values, COL_LOCAL_ID + "=?", new String[]{localId});
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
        db.update(TABLE_SAISIES, values, COL_LOCAL_ID + "=?", new String[]{localId});
    }

    public void markError(String localId, String errorMessage) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_SYNC_STATUS, SaisieLocale.STATUT_ERROR);
        values.put(COL_ERROR_MESSAGE, errorMessage);
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

    /** Toutes les saisies, les plus récentes d'abord. */
    public List<SaisieLocale> getAllSaisies() {
        return querySaisies(null, null);
    }

    /** Saisies d'un type donné (ex: pour filtrer l'écran "Mes saisies"). */
    public List<SaisieLocale> getSaisiesByType(SaisieType type) {
        return querySaisies(COL_TYPE + "=?", new String[]{type.name()});
    }

    /** Saisies pas encore synchronisées (LOCAL ou en erreur), toutes catégories confondues. */
    public List<SaisieLocale> getPendingSaisies() {
        return querySaisies(COL_SYNC_STATUS + " IN (?,?)",
                new String[]{SaisieLocale.STATUT_LOCAL, SaisieLocale.STATUT_ERROR});
    }

    public int countPending() {
        return getPendingSaisies().size();
    }

    /**
     * Vide toutes les données locales (saisies en attente/synchronisées/en erreur et
     * comptes mémorisés) — utilisé par l'écran Diagnostics pour repartir d'une base
     * propre en cas d'incohérence sur le terrain. Retourne le nombre de saisies non
     * synchronisées qui seront perdues, pour que l'appelant puisse avertir avant confirmation.
     */
    public int clearAllLocalData() {
        int pendingCount = countPending();
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
        return s;
    }
}
