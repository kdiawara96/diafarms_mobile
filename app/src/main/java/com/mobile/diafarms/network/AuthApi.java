package com.mobile.diafarms.network;

import com.mobile.diafarms.network.dto.ApiEnvelope;
import com.mobile.diafarms.network.dto.AuthResponse;
import com.mobile.diafarms.network.dto.UtilisateurResponse;

import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface AuthApi {

    // X-Client-Type: mobile demande au backend de renvoyer le token dans le corps JSON
    // (le web, lui, ne reçoit le token que via un cookie HttpOnly) — voir authControllers.java.
    @Headers("X-Client-Type: mobile")
    @FormUrlEncoded
    @POST("auth")
    Call<ApiEnvelope<AuthResponse>> login(
            @Field("grantType") String grantType,
            @Field("identifiant") String identifiant,
            @Field("password") String password,
            @Field("ouiRefresh") boolean ouiRefresh,
            @Field("refreshToken") String refreshToken
    );

    // Bearer explicite : utilisé juste après un scan QR, avant qu'une session ne soit
    // encore créée (sinon l'intercepteur Authorization d'ApiClient n'a rien à ajouter).
    @GET("auth/me")
    Call<ApiEnvelope<UtilisateurResponse>> me(@Header("Authorization") String bearerToken);
}
