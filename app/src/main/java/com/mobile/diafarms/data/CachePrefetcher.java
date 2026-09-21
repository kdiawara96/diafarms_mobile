package com.mobile.diafarms.data;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.mobile.diafarms.network.ApiClient;
import com.mobile.diafarms.network.dto.ApiEnvelope;
import com.mobile.diafarms.network.dto.BatimentSelectResponse;
import com.mobile.diafarms.network.dto.ClientSelectResponse;
import com.mobile.diafarms.network.dto.EffectifReformeResponse;
import com.mobile.diafarms.network.dto.MagasinSelectResponse;
import com.mobile.diafarms.network.dto.NotificationResponse;
import com.mobile.diafarms.network.dto.ProjetDetailResponse;
import com.mobile.diafarms.network.dto.ProjetSelectResponse;
import com.mobile.diafarms.network.dto.SalaireSelectResponse;
import com.mobile.diafarms.network.dto.StockAlimentResponse;
import com.mobile.diafarms.network.dto.StockMagasinResponse;
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
    // Plafond de saisie (effectif vivant + œufs déjà collectés) par projet et poulailler,
    // préchargé comme le stock : les alertes du formulaire marchent ainsi hors ligne.
    public static final String CACHE_PLAFOND_PREFIX = "plafond_saisie_";
    public static final String CACHE_EFFECTIF_REFORME_PREFIX = "effectif_reforme_";
    // Magasins de vente (VENTE) : liste déjà filtrée aux magasins liés au vendeur côté
    // serveur, et stock par magasin précis (un vendeur peut être lié à plusieurs).
    public static final String CACHE_MAGASINS_SELECT = "magasins_select";
    public static final String CACHE_STOCK_MAGASIN_PREFIX = "stock_magasin_";
    // Magasins de STOCKAGE (PRODUCTION) : liste complète, filtrée côté serveur au type
    // STOCKAGE (voir SaisieFormActivity, sélecteur obligatoire de Collecte œufs), et
    // stock (œufs pas encore transférés) par magasin de stockage précis.
    public static final String CACHE_MAGASINS_STOCKAGE_SELECT = "magasins_stockage_select";
    public static final String CACHE_STOCK_MAGASIN_STOCKAGE_PREFIX = "stock_magasin_stockage_";
    // Poulaillers de la ferme (PRODUCTION) : liste complète non filtrée par le serveur —
    // bâtiment d'élevage optionnel de Collecte œufs (distinct du magasin de stockage).
    // Rattachement facultatif d'une dépense : tous les sites / tous les poulaillers (occupés ou non).
    public static final String CACHE_SITES_SELECT = "sites_select";
    public static final String CACHE_BATIMENTS_TOUS = "batiments_tous";
    public static final String CACHE_BATIMENTS_SELECT = "batiments_select";
    // Clients de la ferme (VENTE) : uniquement ceux déjà synchronisés côté serveur —
    // voir ClientSelectResponse et SaisieFormActivity (sélecteur optionnel d'une vente,
    // obligatoire d'une commande).
    public static final String CACHE_CLIENTS_SELECT = "clients_select";
    // Grille salariale de la ferme (COMPTABLE) : un Salaire par employé (mode + taux de
    // base), utilisée pour pré-remplir "Payer un salaire" hors ligne — le serveur reste
    // seul juge du taux réellement appliqué à la période payée à la synchronisation
    // (voir SalaireServiceImpl.resolveTauxPourPeriode).
    public static final String CACHE_SALAIRES_SELECT = "salaires_select";
    // Accès mobile par rôle (Production/Comptable/Vente) — voir HomeActivity.
    // loadFarmAppSettings/applyFarmAppSettings : appliqué cache d'abord pour que le
    // menu ne reste jamais vide hors ligne, y compris au tout premier écran.
    public static final String CACHE_FARM_SETTINGS = "farm_settings";

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

    /** Précharge la liste des magasins liés à ce vendeur (rôle VENTE), et le stock de
     * chacun — inoffensif pour les autres rôles : la liste sera simplement vide. Appelé
     * depuis prefetchProjectsDetails ci-dessous (donc à chaque ouverture de l'accueil,
     * pas seulement au premier bootstrap) pour que le stock par magasin reste à jour. */
    private static void prefetchMagasins(Context appContext, LocalDatabase localDatabase) {
        ApiClient.dataApi(appContext).getMagasinsSelect("VENTE").enqueue(new Callback<ApiEnvelope<List<MagasinSelectResponse>>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<List<MagasinSelectResponse>>> call, Response<ApiEnvelope<List<MagasinSelectResponse>>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().getData() == null) return;
                List<MagasinSelectResponse> magasins = response.body().getData();
                localDatabase.putCache(CACHE_MAGASINS_SELECT, gson.toJson(magasins));
                for (MagasinSelectResponse magasin : magasins) {
                    ApiClient.dataApi(appContext).getStockMagasin(magasin.getUniqueId()).enqueue(new Callback<ApiEnvelope<StockMagasinResponse>>() {
                        @Override
                        public void onResponse(Call<ApiEnvelope<StockMagasinResponse>> call, Response<ApiEnvelope<StockMagasinResponse>> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                                localDatabase.putCache(CACHE_STOCK_MAGASIN_PREFIX + magasin.getUniqueId(), gson.toJson(response.body().getData()));
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<ApiEnvelope<StockMagasinResponse>> call, Throwable t) { }
                    });
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<List<MagasinSelectResponse>>> call, Throwable t) { }
        });
    }

    /** Précharge la liste des magasins de STOCKAGE de la ferme (obligatoire pour la
     * Collecte œufs) et le stock disponible (pas encore transféré) de chacun —
     * inoffensif pour un rôle sans accès Production, la liste sera juste inutilisée. */
    private static void prefetchMagasinsStockage(Context appContext, LocalDatabase localDatabase) {
        ApiClient.dataApi(appContext).getMagasinsSelect("STOCKAGE").enqueue(new Callback<ApiEnvelope<List<MagasinSelectResponse>>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<List<MagasinSelectResponse>>> call, Response<ApiEnvelope<List<MagasinSelectResponse>>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().getData() == null) return;
                List<MagasinSelectResponse> magasins = response.body().getData();
                localDatabase.putCache(CACHE_MAGASINS_STOCKAGE_SELECT, gson.toJson(magasins));
                for (MagasinSelectResponse magasin : magasins) {
                    ApiClient.dataApi(appContext).getDisponibleMagasinStockage(magasin.getUniqueId()).enqueue(new Callback<ApiEnvelope<Integer>>() {
                        @Override
                        public void onResponse(Call<ApiEnvelope<Integer>> call, Response<ApiEnvelope<Integer>> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                                localDatabase.putCache(CACHE_STOCK_MAGASIN_STOCKAGE_PREFIX + magasin.getUniqueId(), gson.toJson(response.body().getData()));
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<ApiEnvelope<Integer>> call, Throwable t) { }
                    });
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<List<MagasinSelectResponse>>> call, Throwable t) { }
        });
    }

    /** Précharge la liste des poulaillers de la ferme — inoffensif pour un rôle sans
     * accès Production, la liste sera juste inutilisée. Voir CACHE_BATIMENTS_SELECT. */
    private static void prefetchBatiments(Context appContext, LocalDatabase localDatabase) {
        ApiClient.dataApi(appContext).getBatimentsSelect().enqueue(new Callback<ApiEnvelope<List<BatimentSelectResponse>>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<List<BatimentSelectResponse>>> call, Response<ApiEnvelope<List<BatimentSelectResponse>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    localDatabase.putCache(CACHE_BATIMENTS_SELECT, gson.toJson(response.body().getData()));
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<List<BatimentSelectResponse>>> call, Throwable t) { }
        });
    }

    /** Sites et poulaillers (tous) pour le rattachement facultatif d'une dépense, disponibles hors ligne. */
    private static void prefetchRattachements(Context appContext, LocalDatabase localDatabase) {
        ApiClient.dataApi(appContext).getSites().enqueue(new Callback<ApiEnvelope<List<com.mobile.diafarms.network.dto.SiteSelectResponse>>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<List<com.mobile.diafarms.network.dto.SiteSelectResponse>>> call,
                                   Response<ApiEnvelope<List<com.mobile.diafarms.network.dto.SiteSelectResponse>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    localDatabase.putCache(CACHE_SITES_SELECT, gson.toJson(response.body().getData()));
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<List<com.mobile.diafarms.network.dto.SiteSelectResponse>>> call, Throwable t) { }
        });
        ApiClient.dataApi(appContext).getBatimentsTous().enqueue(new Callback<ApiEnvelope<List<BatimentSelectResponse>>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<List<BatimentSelectResponse>>> call, Response<ApiEnvelope<List<BatimentSelectResponse>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    localDatabase.putCache(CACHE_BATIMENTS_TOUS, gson.toJson(response.body().getData()));
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<List<BatimentSelectResponse>>> call, Throwable t) { }
        });
    }

    /** Précharge la liste des clients déjà synchronisés côté serveur (farm-scopée, pas
     * de filtre par rôle côté back) — inoffensif pour un rôle sans accès Vente, la
     * liste sera juste inutilisée. Voir CACHE_CLIENTS_SELECT. */
    private static void prefetchClients(Context appContext, LocalDatabase localDatabase) {
        ApiClient.dataApi(appContext).getClientsSelect().enqueue(new Callback<ApiEnvelope<List<ClientSelectResponse>>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<List<ClientSelectResponse>>> call, Response<ApiEnvelope<List<ClientSelectResponse>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    localDatabase.putCache(CACHE_CLIENTS_SELECT, gson.toJson(response.body().getData()));
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<List<ClientSelectResponse>>> call, Throwable t) { }
        });
    }

    /** Précharge la grille salariale de la ferme (rôle Comptable, "Payer un salaire") —
     * inoffensif pour un rôle sans accès Comptable, la liste sera juste inutilisée.
     * Voir CACHE_SALAIRES_SELECT. */
    private static void prefetchSalaires(Context appContext, LocalDatabase localDatabase) {
        ApiClient.dataApi(appContext).getSalairesSelect().enqueue(new Callback<ApiEnvelope<List<SalaireSelectResponse>>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<List<SalaireSelectResponse>>> call, Response<ApiEnvelope<List<SalaireSelectResponse>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    localDatabase.putCache(CACHE_SALAIRES_SELECT, gson.toJson(response.body().getData()));
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<List<SalaireSelectResponse>>> call, Throwable t) { }
        });
    }

    /** Précharge le détail/alertes/stock de chaque projet d'une liste déjà récupérée
     * (évite de refaire l'appel /projets/select quand l'appelant l'a déjà en main). */
    public static void prefetchProjectsDetails(Context context, LocalDatabase localDatabase, List<ProjetSelectResponse> projets) {
        Context appContext = context.getApplicationContext();
        for (ProjetSelectResponse projet : projets) {
            prefetchOneProjet(appContext, localDatabase, projet.getUniqueId());
        }
        prefetchMagasins(appContext, localDatabase);
        prefetchMagasinsStockage(appContext, localDatabase);
        prefetchBatiments(appContext, localDatabase);
        prefetchRattachements(appContext, localDatabase);
        prefetchClients(appContext, localDatabase);
        prefetchSalaires(appContext, localDatabase);
    }

    /** Plafond d'aujourd'hui pour le projet entier et pour chaque poulailler actuellement
     * occupé : le formulaire s'en sert hors ligne (voir SaisieFormActivity.loadPlafondSaisie). */
    private static void prefetchPlafonds(Context appContext, LocalDatabase localDatabase, String projetUniqueId, ProjetDetailResponse detail) {
        String aujourdhui = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        java.util.List<String> batiments = new java.util.ArrayList<>();
        batiments.add(null); // projet entier (aucun poulailler choisi)
        if (detail.getOccupationBatiment() != null) {
            for (com.mobile.diafarms.network.dto.OccupationBatimentResponse o : detail.getOccupationBatiment()) {
                if (o.getDateSortie() == null && o.getBatimentUniqueId() != null) batiments.add(o.getBatimentUniqueId());
            }
        }
        for (String batiment : batiments) {
            ApiClient.dataApi(appContext).getPlafondSaisie(projetUniqueId, batiment, aujourdhui)
                    .enqueue(new Callback<ApiEnvelope<com.mobile.diafarms.network.dto.PlafondSaisieResponse>>() {
                        @Override
                        public void onResponse(Call<ApiEnvelope<com.mobile.diafarms.network.dto.PlafondSaisieResponse>> call,
                                               Response<ApiEnvelope<com.mobile.diafarms.network.dto.PlafondSaisieResponse>> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                                com.mobile.diafarms.network.dto.PlafondSaisieResponse p = response.body().getData();
                                p.setCacheDate(aujourdhui);
                                localDatabase.putCache(CACHE_PLAFOND_PREFIX + projetUniqueId + "_" + batiment, gson.toJson(p));
                            }
                        }

                        @Override
                        public void onFailure(Call<ApiEnvelope<com.mobile.diafarms.network.dto.PlafondSaisieResponse>> call, Throwable t) { }
                    });
        }
    }

    private static void prefetchOneProjet(Context appContext, LocalDatabase localDatabase, String projetUniqueId) {
        ApiClient.dataApi(appContext).getProjetDetail(projetUniqueId).enqueue(new Callback<ApiEnvelope<ProjetDetailResponse>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<ProjetDetailResponse>> call, Response<ApiEnvelope<ProjetDetailResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    ProjetDetailResponse detail = response.body().getData();
                    localDatabase.putCache(CACHE_PROJET_DETAIL_PREFIX + projetUniqueId, gson.toJson(detail));
                    prefetchPlafonds(appContext, localDatabase, projetUniqueId, detail);
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
