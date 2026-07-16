package com.mobile.diafarms.network;

import com.mobile.diafarms.network.dto.ApiEnvelope;
import com.mobile.diafarms.network.dto.BatimentSelectResponse;
import com.mobile.diafarms.network.dto.CollecteOeufsCreateRequest;
import com.mobile.diafarms.network.dto.ConsommationAlimentCreateRequest;
import com.mobile.diafarms.network.dto.CreatedEntityResponse;
import com.mobile.diafarms.network.dto.AlimentationCreateRequest;
import com.mobile.diafarms.network.dto.EffectifReformeResponse;
import com.mobile.diafarms.network.dto.MortaliteCreateRequest;
import com.mobile.diafarms.network.dto.NotificationResponse;
import com.mobile.diafarms.network.dto.ProjetDetailResponse;
import com.mobile.diafarms.network.dto.ProjetSelectResponse;
import com.mobile.diafarms.network.dto.SoinsCreateRequest;
import com.mobile.diafarms.network.dto.StockAlimentResponse;
import com.mobile.diafarms.network.dto.StockOeufsResponse;
import com.mobile.diafarms.network.dto.TransactionCreateRequest;
import com.mobile.diafarms.network.dto.VenteOeufsCreateRequest;
import com.mobile.diafarms.network.dto.VenteReformeCreateRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PUT;
import retrofit2.http.POST;
import retrofit2.http.Path;

/** Endpoints diafarms_back consommés par les écrans de saisie mobile. */
public interface DataApi {

    // ============== SÉLECTEURS ==============
    @GET("projets/select")
    Call<ApiEnvelope<List<ProjetSelectResponse>>> getProjetsSelect();

    @GET("batiments/select")
    Call<ApiEnvelope<List<BatimentSelectResponse>>> getBatimentsSelect();

    @GET("projets/findbyUniqueId/{uniqueId}")
    Call<ApiEnvelope<ProjetDetailResponse>> getProjetDetail(@Path("uniqueId") String uniqueId);

    @GET("consommations-aliment/stock/{projetUniqueId}")
    Call<ApiEnvelope<StockAlimentResponse>> getStockAliment(@Path("projetUniqueId") String projetUniqueId);

    // ============== SOINS ==============
    @POST("soins/create")
    Call<ApiEnvelope<CreatedEntityResponse>> createSoins(@Body SoinsCreateRequest request);

    // ============== MORTALITÉ ==============
    @POST("mortalites/create")
    Call<ApiEnvelope<CreatedEntityResponse>> createMortalite(@Body MortaliteCreateRequest request);

    // ============== COLLECTE D'ŒUFS ==============
    @POST("collectes-oeufs/create")
    Call<ApiEnvelope<CreatedEntityResponse>> createCollecteOeufs(@Body CollecteOeufsCreateRequest request);

    // ============== VENTE D'ŒUFS (plafonnée par le stock vendable côté serveur) ==============
    @GET("ventes-oeufs/stock/{projetUniqueId}")
    Call<ApiEnvelope<StockOeufsResponse>> getStockOeufs(@Path("projetUniqueId") String projetUniqueId);

    @POST("ventes-oeufs/create")
    Call<ApiEnvelope<CreatedEntityResponse>> createVenteOeufs(@Body VenteOeufsCreateRequest request);

    // ============== VENTE RÉFORME (plafonnée par l'effectif vivant côté serveur) ==============
    @GET("ventes-reforme/effectif/{projetUniqueId}")
    Call<ApiEnvelope<EffectifReformeResponse>> getEffectifReforme(@Path("projetUniqueId") String projetUniqueId);

    @POST("ventes-reforme/create")
    Call<ApiEnvelope<CreatedEntityResponse>> createVenteReforme(@Body VenteReformeCreateRequest request);

    // ============== ALIMENTATION — ACHAT (aliment entrant, par projet) ==============
    @POST("alimentations/create/{uniqueIdProjet}")
    Call<ApiEnvelope<CreatedEntityResponse>> createAlimentationAchat(
            @Path("uniqueIdProjet") String projetUniqueId, @Body AlimentationCreateRequest request);

    // ============== ALIMENTATION — CONSOMMATION (aliment sortant) ==============
    @POST("consommations-aliment/create")
    Call<ApiEnvelope<CreatedEntityResponse>> createConsommationAliment(@Body ConsommationAlimentCreateRequest request);

    // ============== TRANSACTIONS (finance) ==============
    @POST("transactions/create")
    Call<ApiEnvelope<CreatedEntityResponse>> createTransaction(@Body TransactionCreateRequest request);

    // ============== ALERTES (recalculées côté serveur, jamais mockées côté client —
    // même logique que le web, voir NotificationServiceImpl) ==============
    @GET("notifications/projet/{projetUniqueId}")
    Call<ApiEnvelope<List<NotificationResponse>>> getNotificationsForProjet(@Path("projetUniqueId") String projetUniqueId);

    @PUT("notifications/{key}/read")
    Call<ApiEnvelope<String>> markNotificationRead(@Path("key") String key);
}
