package com.mobile.diafarms.update;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mobile.diafarms.BuildConfig;
import com.mobile.diafarms.data.AppSettings;
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
 * Plus petite version de l'app acceptée, réglée dans la console super admin
 * (GET abonnements/version-mobile, public). En dessous, l'accueil demande d'envoyer
 * les saisies puis de mettre à jour avant de continuer.
 *
 * La dernière valeur lue est gardée : hors ligne, on garde la décision précédente.
 * Client OkHttp dédié, sans jeton ni intercepteurs (route publique).
 */
public final class VersionMinimale {

    public interface Listener {
        /** Thread principal. obsolete = cette version est en dessous du minimum. */
        void onResult(boolean obsolete);
    }

    private static final String PREFS = "diafarms_version_min";
    private static final String KEY_MIN = "version_min";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile OkHttpClient client;

    private VersionMinimale() { }

    private static OkHttpClient client() {
        if (client == null) {
            synchronized (VersionMinimale.class) {
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

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Décision connue, sans réseau. */
    public static boolean estObsolete(Context context) {
        return BuildConfig.VERSION_CODE < prefs(context).getInt(KEY_MIN, 0);
    }

    /** Relit le minimum si le téléphone a du réseau ; répond toujours (valeur gardée sinon). */
    public static void verifier(@NonNull Context context, @NonNull Listener listener) {
        Context app = context.getApplicationContext();
        if (!NetworkUtils.isOnline(app)) {
            listener.onResult(estObsolete(app));
            return;
        }
        Request request = new Request.Builder()
                .url(new AppSettings(app).getServerUrl() + "abonnements/version-mobile")
                .get().build();
        client().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                MAIN.post(() -> listener.onResult(estObsolete(app)));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        JsonObject data = JsonParser.parseString(body.string()).getAsJsonObject().getAsJsonObject("data");
                        if (data != null && data.has("versionMinimale")) {
                            prefs(app).edit().putInt(KEY_MIN, data.get("versionMinimale").getAsInt()).apply();
                        }
                    }
                } catch (Exception ignore) {
                    // Réponse illisible : on garde la valeur précédente.
                }
                MAIN.post(() -> listener.onResult(estObsolete(app)));
            }
        });
    }
}
