package com.mobile.diafarms.util;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Journal de diagnostic écrit sur le stockage externe propre à l'app (pas besoin de
 * permission particulière, récupérable via `adb pull` sans root) — Logcat s'est révélé
 * filtré pour les apps tierces sur ce build "User" (retail), donc inutilisable pour
 * déboguer à distance. Fichier : Android/data/com.mobile.diafarms/files/diafarms_debug.log
 */
public class DebugLog {

    private static final String FILE_NAME = "diafarms_debug.log";
    private static final SimpleDateFormat TIMESTAMP = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.FRANCE);

    public static synchronized void log(Context context, String tag, String message) {
        Log.d(tag, message); // au cas où logcat serait visible dans certains contextes
        try {
            File dir = context.getApplicationContext().getExternalFilesDir(null);
            if (dir == null) return;
            File file = new File(dir, FILE_NAME);
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.write(TIMESTAMP.format(new Date()) + " [" + tag + "] " + message + "\n");
            }
        } catch (IOException ignored) {
        }
    }

    public static void error(Context context, String tag, String message, Throwable t) {
        String detail = message;
        if (t != null) {
            detail += " :: " + t.getClass().getName() + ": " + t.getMessage();
        }
        log(context, tag, "ERREUR " + detail);
    }

    /** Révèle assez d'un secret (token) pour repérer une corruption, sans le logger en entier. */
    public static String reveal(String secret) {
        if (secret == null) return "null";
        int len = secret.length();
        if (len <= 60) return secret + " (len=" + len + ")";
        return secret.substring(0, 30) + "..." + secret.substring(len - 20) + " (len=" + len + ")";
    }

    private DebugLog() {
    }
}
