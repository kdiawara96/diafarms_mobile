package com.mobile.diafarms.data;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.mobile.diafarms.models.Enregistrement;
import com.mobile.diafarms.models.Transaction;

import java.util.ArrayList;
import java.util.List;

public class LocalDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "diafarms.db";
    private static final int DATABASE_VERSION = 1;

    // Tables
    private static final String TABLE_ENREGISTREMENTS = "enregistrements";
    private static final String TABLE_TRANSACTIONS = "transactions";

    // Colonnes communes
    private static final String COL_ID = "id";
    private static final String COL_PROJET_ID = "projet_id";
    private static final String COL_TYPE = "type";
    private static final String COL_DATE = "date";
    private static final String COL_HEURE = "heure";
    private static final String COL_SAISI_PAR = "saisi_par";
    private static final String COL_SYNC_STATUS = "sync_status";

    // Colonnes enregistrements
    private static final String COL_NB_OEUFS_TOTAL = "nb_oeufs_total";
    private static final String COL_NB_OEUFS_CASSES = "nb_oeufs_casses";
    private static final String COL_NB_ALVEOLES = "nb_alveoles";
    private static final String COL_TYPE_ALIMENT = "type_aliment";
    private static final String COL_QTE_ALIMENT = "qte_aliment_kg";
    private static final String COL_NB_POULES_MORTES = "nb_poules_mortes";
    private static final String COL_CAUSE_MORT = "cause_mort";
    private static final String COL_PHOTOS = "photos";

    // Colonnes transactions
    private static final String COL_MONTANT = "montant";
    private static final String COL_CATEGORIE = "categorie";
    private static final String COL_DESCRIPTION = "description";
    private static final String COL_CLIENT_ID = "client_id";
    private static final String COL_MODE_PAIEMENT = "mode_paiement";

    public LocalDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createEnregistrements = "CREATE TABLE " + TABLE_ENREGISTREMENTS + "("
                + COL_ID + " TEXT PRIMARY KEY,"
                + COL_PROJET_ID + " TEXT,"
                + COL_TYPE + " TEXT,"
                + COL_DATE + " TEXT,"
                + COL_HEURE + " TEXT,"
                + COL_SAISI_PAR + " TEXT,"
                + COL_SYNC_STATUS + " TEXT,"
                + COL_NB_OEUFS_TOTAL + " INTEGER,"
                + COL_NB_OEUFS_CASSES + " INTEGER,"
                + COL_NB_ALVEOLES + " INTEGER,"
                + COL_TYPE_ALIMENT + " TEXT,"
                + COL_QTE_ALIMENT + " INTEGER,"
                + COL_NB_POULES_MORTES + " INTEGER,"
                + COL_CAUSE_MORT + " TEXT,"
                + COL_PHOTOS + " TEXT"
                + ")";

        String createTransactions = "CREATE TABLE " + TABLE_TRANSACTIONS + "("
                + COL_ID + " TEXT PRIMARY KEY,"
                + COL_PROJET_ID + " TEXT,"
                + COL_TYPE + " TEXT,"
                + COL_CATEGORIE + " TEXT,"
                + COL_MONTANT + " REAL,"
                + COL_DATE + " TEXT,"
                + COL_DESCRIPTION + " TEXT,"
                + COL_CLIENT_ID + " TEXT,"
                + COL_MODE_PAIEMENT + " TEXT,"
                + COL_SAISI_PAR + " TEXT,"
                + COL_SYNC_STATUS + " TEXT"
                + ")";

        db.execSQL(createEnregistrements);
        db.execSQL(createTransactions);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ENREGISTREMENTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRANSACTIONS);
        onCreate(db);
    }

    // ===== ENREGISTREMENTS =====

    public long insertEnregistrement(Enregistrement e) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COL_ID, e.getId());
        values.put(COL_PROJET_ID, e.getProjetId());
        values.put(COL_TYPE, e.getType());
        values.put(COL_DATE, e.getDate());
        values.put(COL_HEURE, e.getHeure());
        values.put(COL_SAISI_PAR, e.getSaisiPar());
        values.put(COL_SYNC_STATUS, e.getSyncStatus());
        values.put(COL_NB_OEUFS_TOTAL, e.getNbOeufsTotal());
        values.put(COL_NB_OEUFS_CASSES, e.getNbOeufsCasses());
        values.put(COL_NB_ALVEOLES, e.getNbAlveoles());
        values.put(COL_TYPE_ALIMENT, e.getTypeAliment());
        values.put(COL_QTE_ALIMENT, e.getQuantiteAlimentKg());
        values.put(COL_NB_POULES_MORTES, e.getNbPoulesMortes());
        values.put(COL_CAUSE_MORT, e.getCauseMort());
        values.put(COL_PHOTOS, e.getPhotos());

        return db.insert(TABLE_ENREGISTREMENTS, null, values);
    }

    public List<Enregistrement> getEnregistrementsByProjet(String projetId) {
        List<Enregistrement> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_ENREGISTREMENTS, null,
                COL_PROJET_ID + "=?", new String[]{projetId},
                null, null, COL_DATE + " DESC, " + COL_HEURE + " DESC");

        if (cursor.moveToFirst()) {
            do {
                Enregistrement e = cursorToEnregistrement(cursor);
                list.add(e);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    public List<Enregistrement> getEnregistrementsNonSync() {
        List<Enregistrement> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_ENREGISTREMENTS, null,
                COL_SYNC_STATUS + "=?", new String[]{"local"},
                null, null, null);

        if (cursor.moveToFirst()) {
            do {
                Enregistrement e = cursorToEnregistrement(cursor);
                list.add(e);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    public int updateSyncStatus(String id, String status) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_SYNC_STATUS, status);
        return db.update(TABLE_ENREGISTREMENTS, values, COL_ID + "=?", new String[]{id});
    }

    private Enregistrement cursorToEnregistrement(Cursor c) {
        Enregistrement e = new Enregistrement();
        e.setId(c.getString(c.getColumnIndexOrThrow(COL_ID)));
        e.setProjetId(c.getString(c.getColumnIndexOrThrow(COL_PROJET_ID)));
        e.setType(c.getString(c.getColumnIndexOrThrow(COL_TYPE)));
        e.setDate(c.getString(c.getColumnIndexOrThrow(COL_DATE)));
        e.setHeure(c.getString(c.getColumnIndexOrThrow(COL_HEURE)));
        e.setSaisiPar(c.getString(c.getColumnIndexOrThrow(COL_SAISI_PAR)));
        e.setSyncStatus(c.getString(c.getColumnIndexOrThrow(COL_SYNC_STATUS)));
        e.setNbOeufsTotal(c.getInt(c.getColumnIndexOrThrow(COL_NB_OEUFS_TOTAL)));
        e.setNbOeufsCasses(c.getInt(c.getColumnIndexOrThrow(COL_NB_OEUFS_CASSES)));
        e.setNbAlveoles(c.getInt(c.getColumnIndexOrThrow(COL_NB_ALVEOLES)));
        e.setTypeAliment(c.getString(c.getColumnIndexOrThrow(COL_TYPE_ALIMENT)));
        e.setQuantiteAlimentKg(c.getInt(c.getColumnIndexOrThrow(COL_QTE_ALIMENT)));
        e.setNbPoulesMortes(c.getInt(c.getColumnIndexOrThrow(COL_NB_POULES_MORTES)));
        e.setCauseMort(c.getString(c.getColumnIndexOrThrow(COL_CAUSE_MORT)));
        e.setPhotos(c.getString(c.getColumnIndexOrThrow(COL_PHOTOS)));
        return e;
    }

    // ===== TRANSACTIONS =====

    public long insertTransaction(Transaction t) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COL_ID, t.getId());
        values.put(COL_PROJET_ID, t.getProjetId());
        values.put(COL_TYPE, t.getType());
        values.put(COL_CATEGORIE, t.getCategorie());
        values.put(COL_MONTANT, t.getMontant());
        values.put(COL_DATE, t.getDate());
        values.put(COL_DESCRIPTION, t.getDescription());
        values.put(COL_CLIENT_ID, t.getClientId());
        values.put(COL_MODE_PAIEMENT, t.getModePaiement());
        values.put(COL_SAISI_PAR, t.getSaisiPar());
        values.put(COL_SYNC_STATUS, t.getSyncStatus());

        return db.insert(TABLE_TRANSACTIONS, null, values);
    }

    public List<Transaction> getTransactionsNonSync() {
        List<Transaction> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_TRANSACTIONS, null,
                COL_SYNC_STATUS + "=?", new String[]{"local"},
                null, null, null);

        if (cursor.moveToFirst()) {
            do {
                Transaction t = cursorToTransaction(cursor);
                list.add(t);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    public double getTotalEntreesAujourdhui(String userId) {
        // Simplifié - à compléter avec date du jour
        return 245000; // Mock
    }

    public double getTotalSortiesAujourdhui(String userId) {
        // Simplifié - à compléter avec date du jour
        return 120000; // Mock
    }

    private Transaction cursorToTransaction(Cursor c) {
        Transaction t = new Transaction();
        t.setId(c.getString(c.getColumnIndexOrThrow(COL_ID)));
        t.setProjetId(c.getString(c.getColumnIndexOrThrow(COL_PROJET_ID)));
        t.setType(c.getString(c.getColumnIndexOrThrow(COL_TYPE)));
        t.setCategorie(c.getString(c.getColumnIndexOrThrow(COL_CATEGORIE)));
        t.setMontant(c.getDouble(c.getColumnIndexOrThrow(COL_MONTANT)));
        t.setDate(c.getString(c.getColumnIndexOrThrow(COL_DATE)));
        t.setDescription(c.getString(c.getColumnIndexOrThrow(COL_DESCRIPTION)));
        t.setClientId(c.getString(c.getColumnIndexOrThrow(COL_CLIENT_ID)));
        t.setModePaiement(c.getString(c.getColumnIndexOrThrow(COL_MODE_PAIEMENT)));
        t.setSaisiPar(c.getString(c.getColumnIndexOrThrow(COL_SAISI_PAR)));
        t.setSyncStatus(c.getString(c.getColumnIndexOrThrow(COL_SYNC_STATUS)));
        return t;
    }
}