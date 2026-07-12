package com.mobile.diafarms.network;

import android.content.Context;

import androidx.annotation.NonNull;

import com.mobile.diafarms.data.AppSettings;
import com.mobile.diafarms.data.SessionManager;
import com.mobile.diafarms.util.DebugLog;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    private static volatile Retrofit retrofit;
    private static volatile String retrofitBaseUrl;

    public static AuthApi authApi(Context context) {
        return getRetrofit(context).create(AuthApi.class);
    }

    public static DataApi dataApi(Context context) {
        return getRetrofit(context).create(DataApi.class);
    }

    /** Force la reconstruction du client au prochain appel — nécessaire après un
     * changement d'adresse serveur depuis l'écran Diagnostics, le Retrofit existant
     * gardant sinon l'ancienne baseUrl en cache pour toute la durée du process. */
    public static void reset() {
        synchronized (ApiClient.class) {
            retrofit = null;
            retrofitBaseUrl = null;
        }
    }

    private static Retrofit getRetrofit(Context context) {
        String currentBaseUrl = new AppSettings(context.getApplicationContext()).getServerUrl();
        if (retrofit == null || !currentBaseUrl.equals(retrofitBaseUrl)) {
            synchronized (ApiClient.class) {
                if (retrofit == null || !currentBaseUrl.equals(retrofitBaseUrl)) {
                    SessionManager sessionManager = new SessionManager(context.getApplicationContext());

                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(15, TimeUnit.SECONDS)
                            .addInterceptor(new AuthInterceptor(sessionManager))
                            .addInterceptor(new RequestLoggingInterceptor(context.getApplicationContext()))
                            .addInterceptor(new ErrorCaptureInterceptor(context.getApplicationContext()))
                            .build();

                    retrofit = new Retrofit.Builder()
                            .baseUrl(currentBaseUrl)
                            .client(client)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();
                    retrofitBaseUrl = currentBaseUrl;
                }
            }
        }
        return retrofit;
    }

    /** Ajoute le Bearer token stocké en session à chaque requête (sauf /auth, qui n'en a pas encore besoin). */
    private static class AuthInterceptor implements Interceptor {
        private final SessionManager sessionManager;

        AuthInterceptor(SessionManager sessionManager) {
            this.sessionManager = sessionManager;
        }

        @NonNull
        @Override
        public Response intercept(@NonNull Chain chain) throws IOException {
            Request original = chain.request();

            // Ne jamais écraser un header Authorization déjà posé explicitement par
            // l'appel (ex: vérification du token d'un QR fraîchement scanné, avant
            // toute session ouverte) : sinon un token de session déjà en mémoire
            // (mode test, connexion précédente...) prenait le dessus silencieusement
            // et la requête échouait avec un 401 trompeur, alors que le bon token
            // avait bien été envoyé... jusqu'à ce que cet intercepteur l'écrase.
            if (original.header("Authorization") != null) {
                return chain.proceed(original);
            }

            String token = sessionManager.getToken();
            if (token == null || token.isEmpty()) {
                return chain.proceed(original);
            }

            Request authorized = original.newBuilder()
                    .header("Authorization", "Bearer " + token)
                    .build();
            return chain.proceed(authorized);
        }
    }

    /** Journalise l'en-tête Authorization réellement envoyé, après AuthInterceptor —
     * la seule façon de vérifier qu'il n'a pas été substitué en route (voir bug corrigé
     * dans AuthInterceptor : un token de session existant écrasait un header explicite). */
    private static class RequestLoggingInterceptor implements Interceptor {
        private final Context context;

        RequestLoggingInterceptor(Context context) {
            this.context = context;
        }

        @NonNull
        @Override
        public Response intercept(@NonNull Chain chain) throws IOException {
            Request request = chain.request();
            String auth = request.header("Authorization");
            DebugLog.log(context, "ApiClient", "Requête finale envoyée : " + request.method() + " " + request.url()
                    + " Authorization=" + (auth != null ? DebugLog.reveal(auth) : "(absent)"));
            return chain.proceed(request);
        }
    }

    /** Capture TOUTE erreur — réponse HTTP non 2xx (400, 401, 403, 404, 5xx...) ET échec
     * réseau bas niveau (timeout, hôte injoignable, DNS...) — pour l'écran Diagnostics :
     * l'objectif est de pouvoir demander à un utilisateur sur le terrain de partager ce
     * journal sans avoir besoin d'un accès adb/ordinateur, quelle que soit la nature du
     * problème. Utilise peekBody() plutôt que body() pour ne pas consommer le flux dont
     * Retrofit a encore besoin pour parser la réponse en aval. */
    private static class ErrorCaptureInterceptor implements Interceptor {
        private static final long MAX_PEEK_BYTES = 4096;
        private final Context context;

        ErrorCaptureInterceptor(Context context) {
            this.context = context;
        }

        @NonNull
        @Override
        public Response intercept(@NonNull Chain chain) throws IOException {
            Request request = chain.request();

            Response response;
            try {
                response = chain.proceed(request);
            } catch (IOException e) {
                // Pas de réponse HTTP du tout : timeout, connexion refusée, hôte
                // injoignable, DNS... On journalise puis on relance, Retrofit doit
                // toujours voir l'exception pour déclencher onFailure() côté appelant.
                DebugLog.captureHttpError(context, request.method(), request.url().toString(), 0,
                        e.getClass().getSimpleName() + ": " + e.getMessage());
                throw e;
            }

            if (!response.isSuccessful()) {
                String bodySnippet = "";
                try (ResponseBody peeked = response.peekBody(MAX_PEEK_BYTES)) {
                    bodySnippet = peeked.string();
                } catch (IOException ignored) {
                }
                DebugLog.captureHttpError(context, request.method(), request.url().toString(), response.code(), bodySnippet);
            }

            return response;
        }
    }

    private ApiClient() {
    }
}
