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
 * déboguer à distance. Fichier : Android/data/com.mobile.diafarms/files/diafarms_debug.txt
 */
public class DebugLog {

    // .txt (pas .log) : un testeur sur le terrain doit pouvoir ouvrir le fichier
    // directement depuis son téléphone (gestionnaire de fichiers, appli Notes...),
    // ce qu'aucune appli n'associe par défaut à l'extension .log.
    private static final String FILE_NAME = "diafarms_debug.txt";
    private static final String ERROR_FILE_NAME = "diafarms_errors.txt";
    private static final int MAX_ERROR_BODY_CHARS = 2000;
    private static final String SEPARATOR = "-------------------------------------------------------------";
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

    /**
     * Journal dédié et curaté de TOUTE erreur réseau — réponses HTTP non 2xx et échecs
     * bas niveau (timeout, hôte injoignable...), voir ApiClient.ErrorCaptureInterceptor —
     * séparé du journal de debug général pour rester lisible/partageable tel quel par
     * quelqu'un qui n'a pas besoin de tout le bruit des requêtes réussies. Un long
     * séparateur entre chaque entrée pour repérer facilement où commence/finit chaque
     * erreur en lisant le fichier brut (partagé tel quel, pas de visionneuse structurée).
     */
    public static synchronized void captureHttpError(Context context, String method, String url, int code, String bodySnippet) {
        String snippet = bodySnippet == null ? "" : bodySnippet;
        if (snippet.length() > MAX_ERROR_BODY_CHARS) {
            snippet = snippet.substring(0, MAX_ERROR_BODY_CHARS) + "... (tronqué)";
        }
        String codeLabel = code == 0 ? "ÉCHEC RÉSEAU (pas de réponse HTTP)" : "HTTP " + code;
        String entry = SEPARATOR + "\n"
                + TIMESTAMP.format(new Date()) + " " + codeLabel + " " + method + " " + url
                + "\n  Détail : " + snippet;
        log(context, "HTTP-ERROR", entry);
        try {
            File dir = context.getApplicationContext().getExternalFilesDir(null);
            if (dir == null) return;
            File file = new File(dir, ERROR_FILE_NAME);
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.write(entry + "\n");
            }
        } catch (IOException ignored) {
        }
    }

    /** Comme captureHttpError mais pour un évènement de diagnostic non-HTTP (ex: échec
     * de la connexion hors ligne) — écrit dans le MÊME journal que celui affiché/partagé
     * par l'écran Diagnostics (voir DiagnosticsActivity.readErrorLog/getErrorLogFile),
     * qui ne lit que ERROR_FILE_NAME. Un DebugLog.log() seul (journal général) ne serait
     * donc jamais vu par quelqu'un sur le terrain qui partage juste ce journal. */
    public static synchronized void notice(Context context, String tag, String message) {
        String entry = SEPARATOR + "\n" + TIMESTAMP.format(new Date()) + " [" + tag + "] " + message;
        log(context, tag, message);
        try {
            File dir = context.getApplicationContext().getExternalFilesDir(null);
            if (dir == null) return;
            File file = new File(dir, ERROR_FILE_NAME);
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.write(entry + "\n");
            }
        } catch (IOException ignored) {
        }
    }

    public static File getErrorLogFile(Context context) {
        File dir = context.getApplicationContext().getExternalFilesDir(null);
        if (dir == null) return null;
        return new File(dir, ERROR_FILE_NAME);
    }

    /** Lit le journal d'erreurs pour affichage dans l'app (les N derniers caractères seulement). */
    public static String readErrorLog(Context context) {
        File file = getErrorLogFile(context);
        if (file == null || !file.exists()) return "";
        // java.nio.file.Files nécessite l'API 26 : minSdk de l'app est 24, donc lecture
        // manuelle par flux plutôt que Files.readAllBytes().
        try (java.io.FileInputStream in = new java.io.FileInputStream(file);
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            String content = out.toString("UTF-8");
            int maxChars = 20000;
            if (content.length() > maxChars) {
                return "... (début tronqué)\n" + content.substring(content.length() - maxChars);
            }
            return content;
        } catch (IOException e) {
            return "";
        }
    }

    public static void clearErrorLog(Context context) {
        File file = getErrorLogFile(context);
        if (file != null && file.exists()) {
            file.delete();
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
