package com.mobile.diafarms.data;

import android.content.Context;

import com.google.gson.Gson;
import com.mobile.diafarms.models.SaisieLocale;
import com.mobile.diafarms.network.ApiClient;
import com.mobile.diafarms.network.DataApi;
import com.mobile.diafarms.network.dto.AlimentationCreateRequest;
import com.mobile.diafarms.network.dto.ApiEnvelope;
import com.mobile.diafarms.network.dto.ClientCreateRequest;
import com.mobile.diafarms.network.dto.CollecteOeufsCreateRequest;
import com.mobile.diafarms.network.dto.CommandeCreateRequest;
import com.mobile.diafarms.network.dto.ConsommationAlimentCreateRequest;
import com.mobile.diafarms.network.dto.CreatedEntityResponse;
import com.mobile.diafarms.network.dto.MortaliteCreateRequest;
import com.mobile.diafarms.network.dto.ReformeCreateRequest;
import com.mobile.diafarms.network.dto.SalairePayerRequest;
import com.mobile.diafarms.network.dto.SoinsCreateRequest;
import com.mobile.diafarms.network.dto.TransactionCreateRequest;
import com.mobile.diafarms.network.dto.VenteOeufsCreateRequest;
import com.mobile.diafarms.network.dto.VenteReformeCreateRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Pousse vers diafarms_back les saisies enregistrées localement (statut LOCAL ou
 * ERROR). Traite une saisie à la fois, dans l'ordre, pour rester simple à suivre et
 * éviter toute écriture concurrente sur la base locale.
 */
public class SyncManager {

    public interface SyncCallback {
        default void onProgress(int done, int total) {}
        void onComplete(int success, int failed);
    }

    private final LocalDatabase localDatabase;
    private final DataApi api;
    private final Gson gson = new Gson();

    public SyncManager(Context context) {
        Context appContext = context.getApplicationContext();
        this.localDatabase = new LocalDatabase(appContext);
        this.api = ApiClient.dataApi(appContext);
    }

    public void syncAll(SyncCallback callback) {
        List<SaisieLocale> pending = localDatabase.getPendingSaisies();
        syncNext(pending, 0, 0, 0, callback);
    }

    private void syncNext(List<SaisieLocale> list, int index, int success, int failed, SyncCallback callback) {
        if (index >= list.size()) {
            callback.onComplete(success, failed);
            return;
        }

        SaisieLocale saisie = list.get(index);
        callback.onProgress(index, list.size());

        Callback<ApiEnvelope<CreatedEntityResponse>> retrofitCallback = new Callback<ApiEnvelope<CreatedEntityResponse>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<CreatedEntityResponse>> call, Response<ApiEnvelope<CreatedEntityResponse>> response) {
                CreatedEntityResponse data = response.isSuccessful() && response.body() != null
                        ? response.body().getData() : null;

                if (data != null && data.getUniqueId() != null) {
                    localDatabase.markSynced(saisie.getLocalId(), data.getUniqueId());
                    syncNext(list, index + 1, success + 1, failed, callback);
                } else {
                    String message = response.body() != null ? response.body().getMessage() : "Échec de l'envoi";
                    localDatabase.markError(saisie.getLocalId(), message);
                    syncNext(list, index + 1, success, failed + 1, callback);
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<CreatedEntityResponse>> call, Throwable t) {
                localDatabase.markError(saisie.getLocalId(), "Réseau indisponible");
                syncNext(list, index + 1, success, failed + 1, callback);
            }
        };

        dispatch(saisie, retrofitCallback);
    }

    private void dispatch(SaisieLocale saisie, Callback<ApiEnvelope<CreatedEntityResponse>> callback) {
        String json = saisie.getPayloadJson();

        switch (saisie.getType()) {
            case SOINS:
            case VACCINATION:
                // Même entité/endpoint côté back depuis la fusion Soins/Vaccination — le
                // payload local porte déjà le bon "type" (VACCINATION/MEDICAMENT/AUTRE),
                // voir SaisieFormActivity.onValider.
                api.createSoins(gson.fromJson(json, SoinsCreateRequest.class)).enqueue(callback);
                break;
            case MORTALITE:
                api.createMortalite(gson.fromJson(json, MortaliteCreateRequest.class)).enqueue(callback);
                break;
            case REFORME:
                api.createReforme(gson.fromJson(json, ReformeCreateRequest.class)).enqueue(callback);
                break;
            case COLLECTE_OEUFS:
                api.createCollecteOeufs(gson.fromJson(json, CollecteOeufsCreateRequest.class)).enqueue(callback);
                break;
            case ALIMENTATION_ACHAT:
                api.createAlimentationAchat(saisie.getProjetUniqueId(), gson.fromJson(json, AlimentationCreateRequest.class))
                        .enqueue(callback);
                break;
            case ALIMENTATION_CONSOMMATION:
                api.createConsommationAliment(gson.fromJson(json, ConsommationAlimentCreateRequest.class)).enqueue(callback);
                break;
            case VENTE_OEUFS:
                api.createVenteOeufs(gson.fromJson(json, VenteOeufsCreateRequest.class)).enqueue(callback);
                break;
            case VENTE_REFORME:
                api.createVenteReforme(gson.fromJson(json, VenteReformeCreateRequest.class)).enqueue(callback);
                break;
            case TRANSACTION_ENTREE:
            case TRANSACTION_SORTIE:
                api.createTransaction(gson.fromJson(json, TransactionCreateRequest.class)).enqueue(callback);
                break;
            case CLIENT_CREATE:
                api.createClient(gson.fromJson(json, ClientCreateRequest.class)).enqueue(callback);
                break;
            case COMMANDE_CREATE:
                api.createCommande(gson.fromJson(json, CommandeCreateRequest.class)).enqueue(callback);
                break;
            case SALAIRE_PAYER:
                api.payerSalaire(gson.fromJson(json, SalairePayerRequest.class)).enqueue(callback);
                break;
        }
    }
}
