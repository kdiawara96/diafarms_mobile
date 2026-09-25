package com.mobile.diafarms.update;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.mobile.diafarms.BuildConfig;
import com.mobile.diafarms.data.AppSettings;
import com.mobile.diafarms.network.Constants;
import com.mobile.diafarms.util.DebugLog;
import com.mobile.diafarms.util.NetworkUtils;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Lit version.json sur le site web pour savoir si une version plus récente de l'APK
 * est publiée. Client OkHttp dédié, sans les intercepteurs d'ApiClient : le jeton de
 * session n'a pas à partir vers le site web, et un 404 ici (fichier pas encore publié)
 * ne doit pas remplir le journal d'erreurs de l'écran Diagnostics.
 *
 * Vérification automatique (écran d'accueil) : au plus une requête toutes les 6 h,
 * le dernier résultat est gardé en préférences pour réafficher le bandeau entre-temps.
 * Hors ligne, 404, JSON illisible : silence complet côté automatique.
 */
public final class UpdateChecker {

    public interface Listener {
        /** Appelé sur le thread principal. info non null si version.json a pu être lu
         * (à l'appelant de tester estPlusRecente()), sinon erreur décrit la cause. */
        void onResult(@Nullable UpdateInfo info, @Nullable String erreur);
    }

    static final String PREFS = "diafarms_update";
    private static final String KEY_LAST_CHECK = "last_check_at";
    private static final String KEY_CACHED_INFO = "cached_info";
    private static final long INTERVALLE_AUTO_MS = 6L * 60 * 60 * 1000;

    private static final Gson GSON = new Gson();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile OkHttpClient client;

    private static OkHttpClient client() {
        if (client == null) {
            synchronized (UpdateChecker.class) {
                if (client == null) {
                    client = new OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(15, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return client;
    }

    static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String versionJsonUrl(Context context) {
        return new AppSettings(context).getWebUrl() + Constants.UPDATE_VERSION_PATH;
    }

    /** Le paramètre v évite qu'un cache intermédiaire serve l'APK de la version précédente. */
    public static String apkUrl(Context context, int versionCode) {
        return new AppSettings(context).getWebUrl() + Constants.UPDATE_APK_PATH + "?v=" + versionCode;
    }

    /** Vérification de l'écran d'accueil : ne signale que les mises à jour disponibles,
     * jamais les erreurs, et n'interroge le réseau qu'une fois toutes les 6 h. */
    public static void checkAuto(Context context, @NonNull Listener listener) {
        Context app = context.getApplicationContext();
        UpdateController.nettoyerSiDejaInstalle(app);
        if (!NetworkUtils.isOnline(app)) return;

        SharedPreferences prefs = prefs(app);
        long now = System.currentTimeMillis();
        long last = prefs.getLong(KEY_LAST_CHECK, 0);
        if (last > 0 && last <= now && now - last < INTERVALLE_AUTO_MS) {
            UpdateInfo cached = lireCache(prefs);
            if (cached != null && cached.estPlusRecente()) {
                listener.onResult(cached, null);
            }
            return;
        }

        fetch(app, (info, erreur) -> {
            if (info != null && info.estPlusRecente()) {
                listener.onResult(info, null);
            }
        });
    }

    /** Vérification demandée explicitement (écran Paramètres) : pas de délai, erreurs remontées. */
    public static void checkManual(Context context, @NonNull Listener listener) {
        Context app = context.getApplicationContext();
        if (!NetworkUtils.isOnline(app)) {
            listener.onResult(null, "Pas de connexion internet.");
            return;
        }
        fetch(app, listener);
    }

    private static void fetch(Context app, Listener listener) {
        String url = versionJsonUrl(app);
        Request request;
        try {
            request = new Request.Builder()
                    .url(url)
                    .header("Cache-Control", "no-cache")
                    .build();
        } catch (IllegalArgumentException e) {
            MAIN.post(() -> listener.onResult(null, "Adresse de mise à jour invalide."));
            return;
        }

        client().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                DebugLog.log(app, "UpdateChecker", "version.json injoignable : " + e.getMessage());
                memoriserTentative(app, null, false);
                MAIN.post(() -> listener.onResult(null, "Serveur de mise à jour injoignable."));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                UpdateInfo info = null;
                String erreur = null;
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        erreur = "Informations de version indisponibles (HTTP " + response.code() + ").";
                    } else {
                        UpdateInfo parsed = GSON.fromJson(body.string(), UpdateInfo.class);
                        if (parsed != null && parsed.estValide()) {
                            info = parsed;
                        } else {
                            erreur = "Informations de version illisibles.";
                        }
                    }
                } catch (Exception e) {
                    // JsonSyntaxException, IOException en lecture du corps, etc.
                    erreur = "Informations de version illisibles.";
                }

                if (erreur != null) {
                    DebugLog.log(app, "UpdateChecker", "version.json : " + erreur);
                }
                memoriserTentative(app, info, info != null);
                if (info != null && !info.estPlusRecente()) {
                    UpdateController.nettoyer(app);
                }
                UpdateInfo resultat = info;
                String erreurFinale = erreur;
                MAIN.post(() -> listener.onResult(resultat, erreurFinale));
            }
        });
    }

    /** Toute tentative aboutie (même un 404) compte pour le délai de 6 h ; le résultat
     * en cache n'est remplacé que par une lecture réussie. */
    private static void memoriserTentative(Context app, @Nullable UpdateInfo info, boolean remplacerCache) {
        SharedPreferences.Editor editor = prefs(app).edit()
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis());
        if (remplacerCache) {
            editor.putString(KEY_CACHED_INFO, GSON.toJson(info));
        }
        editor.apply();
    }

    @Nullable
    private static UpdateInfo lireCache(SharedPreferences prefs) {
        String json = prefs.getString(KEY_CACHED_INFO, null);
        if (json == null) return null;
        try {
            UpdateInfo info = GSON.fromJson(json, UpdateInfo.class);
            return info != null && info.estValide() ? info : null;
        } catch (Exception e) {
            return null;
        }
    }

    static int versionInstallee() {
        return BuildConfig.VERSION_CODE;
    }

    private UpdateChecker() {
    }
}
