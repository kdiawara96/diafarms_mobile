package com.mobile.diafarms.data;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.mobile.diafarms.network.ApiClient;
import com.mobile.diafarms.network.dto.ApiEnvelope;
import com.mobile.diafarms.network.dto.EffectifReformeResponse;
import com.mobile.diafarms.network.dto.NotificationResponse;
import com.mobile.diafarms.network.dto.ProjetDetailResponse;
import com.mobile.diafarms.network.dto.ProjetSelectResponse;
import com.mobile.diafarms.network.dto.StockAlimentResponse;
import com.mobile.diafarms.network.dto.StockOeufsResponse;
import com.mobile.diafarms.network.dto.StockReformeResponse;
import com.mobile.diafarms.util.DebugLog;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Précharge en cache local (LocalDatabase.local_cache) le détail, les alertes et le
 * stock aliment de TOUS les projets de l'agent — pas seulement celui affiché à l'écran
 * — pour que le travail hors ligne sur le terrain fonctionne même sur un projet jamais
 * consulté en ligne. Déclenché juste après un login/scan QR réussi (encore en ligne à
 * ce moment précis) et à chaque ouverture de l'accueil. Chaque appel réseau est
 * indépendant (fire-and-forget) : un échec sur un projet ne bloque pas les autres, et
 * cette classe ne touche à aucune vue.
 */
public class CachePrefetcher {

    public static final String CACHE_PROJETS_SELECT = "projets_select";
    public static final String CACHE_PROJET_DETAIL_PREFIX = "projet_detail_";
    public static final String CACHE_NOTIFICATIONS_PREFIX = "notifications_";
    public static final String CACHE_STOCK_ALIMENT_PREFIX = "stock_aliment_";
    // Effectif vivant (Production, ReformeImpl) : par projet, comme le stock aliment.
    public static final String CACHE_EFFECTIF_REFORME_PREFIX = "effectif_reforme_";
    // Stock d'œufs / de réforme vendables (Finance) : à l'échelle de la ferme entière,
    // une seule clé de cache — pas de préfixe par projet.
    public static final String CACHE_STOCK_OEUFS_FARM = "stock_oeufs_farm";
    public static final String CACHE_STOCK_REFORME_FARM = "stock_reforme_farm";

    private static final String TAG = "CachePrefetcher";
    private static final Gson gson = new Gson();

    /** Récupère d'abord la liste des projets puis précharge chacun d'eux. À utiliser
     * juste après un login/scan QR réussi, quand HomeActivity n'a pas encore tourné. */
    public static void prefetchAll(Context context) {
        Context appContext = context.getApplicationContext();
        LocalDatabase localDatabase = new LocalDatabase(appContext);

        ApiClient.dataApi(appContext).getProjetsSelect().enqueue(new Callback<ApiEnvelope<List<ProjetSelectResponse>>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<List<ProjetSelectResponse>>> call, Response<ApiEnvelope<List<ProjetSelectResponse>>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().getData() == null) return;
                List<ProjetSelectResponse> projets = filterActifs(response.body().getData());
                localDatabase.putCache(CACHE_PROJETS_SELECT, gson.toJson(projets));
                prefetchProjectsDetails(appContext, localDatabase, projets);
            }

            @Override
            public void onFailure(Call<ApiEnvelope<List<ProjetSelectResponse>>> call, Throwable t) {
                DebugLog.log(appContext, TAG, "Préchargement de la liste des projets impossible (hors ligne) : " + t.getMessage());
            }
        });
    }

    /** Précharge le détail/alertes/stock de chaque projet d'une liste déjà récupérée
     * (évite de refaire l'appel /projets/select quand l'appelant l'a déjà en main). */
    public static void prefetchProjectsDetails(Context context, LocalDatabase localDatabase, List<ProjetSelectResponse> projets) {
        Context appContext = context.getApplicationContext();
        for (ProjetSelectResponse projet : projets) {
            prefetchOneProjet(appContext, localDatabase, projet.getUniqueId());
        }
        prefetchFarmWide(appContext, localDatabase);
    }

    private static void prefetchOneProjet(Context appContext, LocalDatabase localDatabase, String projetUniqueId) {
        ApiClient.dataApi(appContext).getProjetDetail(projetUniqueId).enqueue(new Callback<ApiEnvelope<ProjetDetailResponse>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<ProjetDetailResponse>> call, Response<ApiEnvelope<ProjetDetailResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    localDatabase.putCache(CACHE_PROJET_DETAIL_PREFIX + projetUniqueId, gson.toJson(response.body().getData()));
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<ProjetDetailResponse>> call, Throwable t) { }
        });

        ApiClient.dataApi(appContext).getNotificationsForProjet(projetUniqueId).enqueue(new Callback<ApiEnvelope<List<NotificationResponse>>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<List<NotificationResponse>>> call, Response<ApiEnvelope<List<NotificationResponse>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    localDatabase.putCache(CACHE_NOTIFICATIONS_PREFIX + projetUniqueId, gson.toJson(response.body().getData()));
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<List<NotificationResponse>>> call, Throwable t) { }
        });

        ApiClient.dataApi(appContext).getStockAliment(projetUniqueId).enqueue(new Callback<ApiEnvelope<StockAlimentResponse>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<StockAlimentResponse>> call, Response<ApiEnvelope<StockAlimentResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    localDatabase.putCache(CACHE_STOCK_ALIMENT_PREFIX + projetUniqueId, gson.toJson(response.body().getData()));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiEnvelope<StockAlimentResponse>> call, Throwable t) { }
        });

        ApiClient.dataApi(appContext).getEffectifReforme(projetUniqueId).enqueue(new Callback<ApiEnvelope<EffectifReformeResponse>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<EffectifReformeResponse>> call, Response<ApiEnvelope<EffectifReformeResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    localDatabase.putCache(CACHE_EFFECTIF_REFORME_PREFIX + projetUniqueId, gson.toJson(response.body().getData()));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiEnvelope<EffectifReformeResponse>> call, Throwable t) { }
        });
    }

    /** Précharge les stocks Finance à l'échelle de la ferme (vente œufs/réforme) —
     * une seule fois, pas par projet contrairement à prefetchOneProjet ci-dessus,
     * puisque ces ventes ne sont plus rattachées à un projet précis. */
    private static void prefetchFarmWide(Context appContext, LocalDatabase localDatabase) {
        ApiClient.dataApi(appContext).getStockOeufs().enqueue(new Callback<ApiEnvelope<StockOeufsResponse>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<StockOeufsResponse>> call, Response<ApiEnvelope<StockOeufsResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    localDatabase.putCache(CACHE_STOCK_OEUFS_FARM, gson.toJson(response.body().getData()));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiEnvelope<StockOeufsResponse>> call, Throwable t) { }
        });

        ApiClient.dataApi(appContext).getStockReforme().enqueue(new Callback<ApiEnvelope<StockReformeResponse>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<StockReformeResponse>> call, Response<ApiEnvelope<StockReformeResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    localDatabase.putCache(CACHE_STOCK_REFORME_FARM, gson.toJson(response.body().getData()));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiEnvelope<StockReformeResponse>> call, Throwable t) { }
        });
    }

    /** Ne garde que les projets actifs (ProjetSelectResponse.active, reflet direct de
     * !initialisation.archive côté back) — on n'amène jamais un projet archivé sur le
     * terrain, ni à l'écran ni en cache. Ne PAS se baser sur finPrevue : c'est une date
     * indicative, pas un statut — un projet reste actif même après sa fin prévue tant
     * qu'il n'a pas été explicitement archivé (ancien bug : un projet dont la date de
     * fin était dépassée disparaissait à tort du mobile alors qu'il restait actif côté web). */
    public static List<ProjetSelectResponse> filterActifs(List<ProjetSelectResponse> projets) {
        List<ProjetSelectResponse> actifs = new ArrayList<>();
        for (ProjetSelectResponse projet : projets) {
            if (projet.isActive()) actifs.add(projet);
        }
        return actifs;
    }

    private CachePrefetcher() {
    }
}
