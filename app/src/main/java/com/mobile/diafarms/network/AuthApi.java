package com.mobile.diafarms.network;

import com.mobile.diafarms.network.dto.ApiEnvelope;

import retrofit2.Call;
import retrofit2.http.GET;

/**
 * Le login (LoginActivity) et le scan QR (CameraScanActivity) sont 100% locaux, sans
 * appel réseau — voir leurs commentaires respectifs. Le seul endpoint réellement utilisé
 * ici sert uniquement au test de connectivité de l'écran Diagnostics.
 */
public interface AuthApi {

    // Endpoint public (pas de JWT requis) utilisé uniquement pour le test de
    // connectivité de l'écran Diagnostics.
    @GET("test")
    Call<ApiEnvelope<Object>> ping();
}
