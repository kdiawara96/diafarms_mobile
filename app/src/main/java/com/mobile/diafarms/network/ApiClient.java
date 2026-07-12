package com.mobile.diafarms.network;

import android.content.Context;

import androidx.annotation.NonNull;

import com.mobile.diafarms.data.SessionManager;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    private static volatile Retrofit retrofit;

    public static AuthApi authApi(Context context) {
        return getRetrofit(context).create(AuthApi.class);
    }

    private static Retrofit getRetrofit(Context context) {
        if (retrofit == null) {
            synchronized (ApiClient.class) {
                if (retrofit == null) {
                    SessionManager sessionManager = new SessionManager(context.getApplicationContext());

                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(15, TimeUnit.SECONDS)
                            .addInterceptor(new AuthInterceptor(sessionManager))
                            .build();

                    retrofit = new Retrofit.Builder()
                            .baseUrl(Constants.BASE_URL)
                            .client(client)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();
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

    private ApiClient() {
    }
}
