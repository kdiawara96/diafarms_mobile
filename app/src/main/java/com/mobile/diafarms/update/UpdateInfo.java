package com.mobile.diafarms.update;

import com.mobile.diafarms.BuildConfig;

/**
 * Contenu de version.json publié par le site web (voir Constants.UPDATE_VERSION_PATH) :
 * {"versionCode": 30, "versionName": "1.29", "taille": octets, "date": "...", "notes": "..."}.
 */
public class UpdateInfo {

    private int versionCode;
    private String versionName;
    private Long taille;
    private String date;
    private String notes;

    public int getVersionCode() {
        return versionCode;
    }

    public String getVersionName() {
        return versionName;
    }

    /** Taille attendue de l'APK en octets, ou 0 si non fournie. */
    public long getTaille() {
        return taille != null && taille > 0 ? taille : 0;
    }

    public String getDate() {
        return date;
    }

    public String getNotes() {
        return notes != null ? notes.trim() : "";
    }

    /** Un version.json sans versionCode ni versionName est traité comme illisible. */
    boolean estValide() {
        return versionCode > 0 && versionName != null && !versionName.trim().isEmpty();
    }

    public boolean estPlusRecente() {
        return versionCode > BuildConfig.VERSION_CODE;
    }
}
