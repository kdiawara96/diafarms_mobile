package com.mobile.diafarms.data;

import android.content.Context;

import com.google.gson.Gson;
import com.mobile.diafarms.models.SaisieLocale;
import com.mobile.diafarms.models.SaisieType;
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
import com.mobile.diafarms.network.dto.SessionPeseeSyncRequest;
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
                    // Une session de pesée modifiée pendant l'envoi reste LOCAL : elle
                    // n'est comptée ni comme synchronisée ni comme en échec.
                    boolean synced = markSynced(saisie, data.getUniqueId());
                    syncNext(list, index + 1, synced ? success + 1 : success, failed, callback);
                } else {
                    String message = extractServerMessage(response);
                    markError(saisie, message);
                    syncNext(list, index + 1, success, failed + 1, callback);
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<CreatedEntityResponse>> call, Throwable t) {
                markError(saisie, "Réseau indisponible");
                syncNext(list, index + 1, success, failed + 1, callback);
            }
        };

        // Un type que dispatch ne sait pas envoyer ne doit JAMAIS bloquer la suite : avant,
        // aucun callback n'était appelé pour lui (ex: Vente de fientes) et toute la
        // synchronisation restait suspendue. On le marque en erreur et on continue.
        if (!dispatch(saisie, retrofitCallback)) {
            markError(saisie, "Ce type de saisie ne peut pas être envoyé par cette version de l'application");
            syncNext(list, index + 1, success, failed + 1, callback);
        }
    }

    /** Une session de pesée peut être réécrite (nouvelle pesée, annulation, clôture)
     * PENDANT son envoi : on ne marque alors pas le nouvel état comme synchronisé (ni en
     * erreur) — la ligne reste LOCAL et repart au prochain envoi. Seulement pour ce type :
     * les autres ne sont pas idempotents côté serveur, un renvoi y créerait un doublon. */
    private boolean markSynced(SaisieLocale saisie, String serverUniqueId) {
        if (saisie.getType() == SaisieType.PESEE_SESSION) {
            return localDatabase.markSyncedIfPayloadUnchanged(saisie.getLocalId(), serverUniqueId, saisie.getPayloadJson());
        }
        localDatabase.markSynced(saisie.getLocalId(), serverUniqueId);
        return true;
    }

    private void markError(SaisieLocale saisie, String message) {
        if (saisie.getType() == SaisieType.PESEE_SESSION) {
            localDatabase.markErrorIfPayloadUnchanged(saisie.getLocalId(), message, saisie.getPayloadJson());
        } else {
            localDatabase.markError(saisie.getLocalId(), message);
        }
    }

    /** Raison réelle du refus. Sur un 400 le corps est dans errorBody() (response.body()
     * est alors null) : le serveur y explique pourquoi (ex: "dépasserait l'effectif
     * vivant"), message qui n'était jamais affiché, seulement "Échec de l'envoi". */
    private String extractServerMessage(Response<ApiEnvelope<CreatedEntityResponse>> response) {
        try {
            if (response.body() != null && response.body().getMessage() != null) {
                return response.body().getMessage();
            }
            if (response.errorBody() != null) {
                ApiEnvelope<?> envelope = gson.fromJson(response.errorBody().string(), ApiEnvelope.class);
                if (envelope != null) {
                    if (envelope.getErrors() != null && !envelope.getErrors().isEmpty()) return envelope.getErrors().get(0);
                    if (envelope.getMessage() != null) return envelope.getMessage();
                }
            }
        } catch (Exception ignored) {
            // corps illisible : message générique ci-dessous
        }
        return "Échec de l'envoi";
    }

    /** true si la saisie a été mise en file d'envoi (le callback sera appelé), false si ce type n'est pas géré. */
    private boolean dispatch(SaisieLocale saisie, Callback<ApiEnvelope<CreatedEntityResponse>> callback) {
        String json = saisie.getPayloadJson();

        switch (saisie.getType()) {
            case SOINS:
                // Un seul endpoint côté back pour Médicament/Autre/Vaccination — le
                // payload local porte déjà le bon "type" (VACCINATION/MEDICAMENT/AUTRE),
                // voir SaisieFormActivity.onValider.
                api.createSoins(gson.fromJson(json, SoinsCreateRequest.class)).enqueue(callback);
                break;
            case ENTRETIEN:
                api.createEntretien(gson.fromJson(json, com.mobile.diafarms.network.dto.EntretienCreateRequest.class)).enqueue(callback);
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
            // Vente de fientes = simple transaction "entrée" commune, catégorie fixe (voir
            // SaisieType.VENTE_FIENTES) : même endpoint et même payload que les deux ci-dessus.
            case VENTE_FIENTES:
                api.createTransaction(gson.fromJson(json, TransactionCreateRequest.class)).enqueue(callback);
                break;
            case CLIENT_CREATE:
                api.createClient(gson.fromJson(json, ClientCreateRequest.class)).enqueue(callback);
                break;
            case COMMANDE_CREATE:
                api.createCommande(gson.fromJson(json, CommandeCreateRequest.class)).enqueue(callback);
                break;
            case PESEE_SESSION:
                api.syncSessionPesee(gson.fromJson(json, SessionPeseeSyncRequest.class)).enqueue(callback);
                break;
            case SALAIRE_PAYER:
                api.payerSalaire(gson.fromJson(json, SalairePayerRequest.class)).enqueue(callback);
                break;
            default:
                return false;
        }
        return true;
    }
}
