package com.mobile.diafarms.data;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.mobile.diafarms.models.SaisieLocale;
import com.mobile.diafarms.models.User;
import com.mobile.diafarms.network.ApiClient;
import com.mobile.diafarms.network.dto.ApiEnvelope;
import com.mobile.diafarms.network.dto.BatimentSelectResponse;
import com.mobile.diafarms.network.dto.ClientSelectResponse;
import com.mobile.diafarms.network.dto.DernierPoidsMoyenResponse;
import com.mobile.diafarms.network.dto.EffectifReformeResponse;
import com.mobile.diafarms.network.dto.MagasinSelectResponse;
import com.mobile.diafarms.network.dto.NotificationResponse;
import com.mobile.diafarms.network.dto.ProjetDetailResponse;
import com.mobile.diafarms.network.dto.ProjetSelectResponse;
import com.mobile.diafarms.network.dto.SalaireSelectResponse;
import com.mobile.diafarms.network.dto.SessionPeseeServeur;
import com.mobile.diafarms.network.dto.SessionPeseeSyncRequest;
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
    // Sessions de pesée EN_COURS du projet connues du serveur (créées sur le web ou sur un
    // autre téléphone) et leur détail : "Reprendre" peut ainsi les proposer et les importer
    // hors ligne (voir PeseeSessionActivity). Seulement celles absentes du téléphone.
    public static final String CACHE_PESEE_SESSIONS_PREFIX = "pesee_sessions_en_cours_";
    public static final String CACHE_PESEE_DETAIL_PREFIX = "pesee_session_detail_";
    // Dernière pesée TERMINEE connue du serveur, par projet (DernierPoidsMoyenResponse) :
    // estimation datée du poids d'une vente/commande de réformes au kilo hors ligne,
    // complétée par les sessions locales (voir DernierePeseeEstimation).
    public static final String CACHE_DERNIER_POIDS_MOYEN_PREFIX = "dernier_poids_moyen_";

    // Commandes OUVERTES de la ferme (en attente, confirmées, en cours de livraison) avec
    // leur état d'argent (acompte reçu/réservé...) : écran Commandes et livraison hors ligne
    // (voir CommandesHorsLigne, CommandesActivity).
    public static final String CACHE_COMMANDES_OUVERTES = "commandes_ouvertes";
    // Compte de chaque client (reste à payer, avance libre / réservée), partie "compte" de
    // GET /clients/{uid}/compte : affiché hors ligne dans "Encaissement client".
    public static final String CACHE_COMPTE_CLIENT_PREFIX = "compte_client_";
    private static final int COMPTES_CLIENTS_MAX = 150;
    private static final String[] STATUTS_COMMANDE_OUVERTE = {"EN_ATTENTE", "CONFIRMEE", "EN_LIVRAISON"};

    private static final String TAG = "CachePrefetcher";
    private static final Gson gson = new Gson();

    /** Récupère d'abord la liste des projets puis précharge chacun d'eux. À utiliser
     * juste après un login/scan QR réussi, quand HomeActivity n'a pas encore tourné. */
    public static void prefetchAll(Context context) {
        Context appContext = context.getApplicationContext();
        // Figée sur le compte qui demande : une réponse tardive ne doit pas atterrir dans
        // le cache d'un autre compte connecté entre-temps.
        LocalDatabase localDatabase = new LocalDatabase(appContext).figee();

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
                    User u = new SessionManager(appContext).getCurrentUser();
                    if (u != null && u.peutEncaisser()) {
                        List<ClientSelectResponse> clients = response.body().getData();
                        for (int i = 0; i < clients.size() && i < COMPTES_CLIENTS_MAX; i++) {
                            rafraichirCompteClient(appContext, localDatabase, clients.get(i).getUniqueId(), null);
                        }
                    }
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
    public static void prefetchProjectsDetails(Context context, LocalDatabase localDatabaseAppelant, List<ProjetSelectResponse> projets) {
        Context appContext = context.getApplicationContext();
        LocalDatabase localDatabase = localDatabaseAppelant.figee();
        int[] budgetPesees = {PESEE_DETAILS_MAX_PAR_PASSAGE}; // partagé entre les projets
        for (ProjetSelectResponse projet : projets) {
            prefetchOneProjet(appContext, localDatabase, projet.getUniqueId());
            prefetchPesees(appContext, localDatabase, projet.getUniqueId(), budgetPesees);
        }
        prefetchMagasins(appContext, localDatabase);
        prefetchMagasinsStockage(appContext, localDatabase);
        prefetchBatiments(appContext, localDatabase);
        prefetchRattachements(appContext, localDatabase);
        prefetchClients(appContext, localDatabase);
        prefetchSalaires(appContext, localDatabase);
        User u = new SessionManager(appContext).getCurrentUser();
        if (u != null && u.peutEncaisser()) {
            rafraichirCommandes(appContext, localDatabase, null);
        }
    }

    /** Recharge le compte d'un client dans le cache ; fin(true) si le cache a été mis à jour. */
    public static void rafraichirCompteClient(Context context, LocalDatabase localDatabaseAppelant, String clientUniqueId,
                                              java.util.function.Consumer<Boolean> fin) {
        if (clientUniqueId == null) return;
        LocalDatabase localDatabase = localDatabaseAppelant.figee();
        ApiClient.dataApi(context.getApplicationContext()).getCompteClient(clientUniqueId).enqueue(
                new Callback<ApiEnvelope<com.mobile.diafarms.network.dto.CompteClientResponse>>() {
                    @Override
                    public void onResponse(Call<ApiEnvelope<com.mobile.diafarms.network.dto.CompteClientResponse>> call,
                                           Response<ApiEnvelope<com.mobile.diafarms.network.dto.CompteClientResponse>> response) {
                        com.mobile.diafarms.network.dto.CompteClientResponse r = response.isSuccessful() && response.body() != null
                                ? response.body().getData() : null;
                        boolean ok = r != null && r.compte != null;
                        if (ok) localDatabase.putCache(CACHE_COMPTE_CLIENT_PREFIX + clientUniqueId, gson.toJson(r.compte));
                        if (fin != null) fin.accept(ok);
                    }

                    @Override
                    public void onFailure(Call<ApiEnvelope<com.mobile.diafarms.network.dto.CompteClientResponse>> call, Throwable t) {
                        if (fin != null) fin.accept(false);
                    }
                });
    }

    /** Recharge les commandes ouvertes (un appel par statut ouvert, 200 au plus chacun) et
     * ne remplace le cache que si les trois réponses sont arrivées : une liste partielle
     * ferait croire qu'une commande a disparu. fin(true) si le cache a été mis à jour. */
    public static void rafraichirCommandes(Context context, LocalDatabase localDatabaseAppelant,
                                           java.util.function.Consumer<Boolean> fin) {
        Context appContext = context.getApplicationContext();
        LocalDatabase localDatabase = localDatabaseAppelant.figee();
        List<com.mobile.diafarms.network.dto.CommandeResponse> toutes = new ArrayList<>();
        int[] restantes = {STATUTS_COMMANDE_OUVERTE.length};
        boolean[] echec = {false};
        for (String statut : STATUTS_COMMANDE_OUVERTE) {
            ApiClient.dataApi(appContext).getCommandes(0, 200, statut).enqueue(
                    new Callback<ApiEnvelope<com.mobile.diafarms.network.dto.PageResponse<com.mobile.diafarms.network.dto.CommandeResponse>>>() {
                        @Override
                        public void onResponse(Call<ApiEnvelope<com.mobile.diafarms.network.dto.PageResponse<com.mobile.diafarms.network.dto.CommandeResponse>>> call,
                                               Response<ApiEnvelope<com.mobile.diafarms.network.dto.PageResponse<com.mobile.diafarms.network.dto.CommandeResponse>>> response) {
                            com.mobile.diafarms.network.dto.PageResponse<com.mobile.diafarms.network.dto.CommandeResponse> page =
                                    response.isSuccessful() && response.body() != null ? response.body().getData() : null;
                            if (page == null || page.data == null) echec[0] = true;
                            else toutes.addAll(page.data);
                            terminerUn();
                        }

                        @Override
                        public void onFailure(Call<ApiEnvelope<com.mobile.diafarms.network.dto.PageResponse<com.mobile.diafarms.network.dto.CommandeResponse>>> call, Throwable t) {
                            echec[0] = true;
                            terminerUn();
                        }

                        private void terminerUn() {
                            if (--restantes[0] > 0) return;
                            if (!echec[0]) {
                                localDatabase.putCache(CACHE_COMMANDES_OUVERTES, gson.toJson(toutes));
                            }
                            if (fin != null) fin.accept(!echec[0]);
                        }
                    });
        }
    }

    /** Profil du compte que le QR ne donne pas : ferme (/farms/me) et drapeau "consultation
     * seule" (/auth/me, comptes de démonstration). Enregistrés sur CE compte (userId figé
     * au départ, voir SessionManager.mettreAJourProfil) ; onMisAJour est appelé sur le
     * thread principal quand l'une des deux réponses est arrivée. */
    public static void prefetchProfil(Context context, Runnable onMisAJour) {
        Context appContext = context.getApplicationContext();
        String userId = SessionManager.activeUserId(appContext);
        if (userId == null) return;
        ApiClient.dataApi(appContext).getMonProfil().enqueue(new Callback<ApiEnvelope<com.mobile.diafarms.network.dto.ProfilResponse>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<com.mobile.diafarms.network.dto.ProfilResponse>> call,
                                   Response<ApiEnvelope<com.mobile.diafarms.network.dto.ProfilResponse>> response) {
                com.mobile.diafarms.network.dto.ProfilResponse p = response.isSuccessful() && response.body() != null
                        ? response.body().getData() : null;
                if (p == null || !userId.equals(p.uniqueId)) return;
                new SessionManager(appContext).mettreAJourProfil(userId, null, Boolean.TRUE.equals(p.consultationSeule));
                if (onMisAJour != null) onMisAJour.run();
            }

            @Override
            public void onFailure(Call<ApiEnvelope<com.mobile.diafarms.network.dto.ProfilResponse>> call, Throwable t) { }
        });
        ApiClient.dataApi(appContext).getMaFerme().enqueue(new Callback<ApiEnvelope<java.util.Map<String, String>>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<java.util.Map<String, String>>> call, Response<ApiEnvelope<java.util.Map<String, String>>> response) {
                java.util.Map<String, String> f = response.isSuccessful() && response.body() != null ? response.body().getData() : null;
                String farm = f != null ? f.get("uniqueId") : null;
                if (farm == null || farm.isEmpty()) return;
                new SessionManager(appContext).mettreAJourProfil(userId, farm, null);
                if (onMisAJour != null) onMisAJour.run();
            }

            @Override
            public void onFailure(Call<ApiEnvelope<java.util.Map<String, String>>> call, Throwable t) { }
        });
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

    /** Au plus ce nombre de lectures de détail de session par ouverture de l'accueil
     * (après la mise à jour, toutes les sessions SYNCED ont une version inconnue). */
    private static final int PESEE_DETAILS_MAX_PAR_PASSAGE = 10;
    /** Détail en échec pour une version donnée : pas de nouvel essai avant ce délai. */
    private static final long PESEE_ESSAI_DELAI_MS = 30L * 60 * 1000;
    private static final String CACHE_PESEE_ESSAI_PREFIX = "pesee_detail_echec_";

    /**
     * Sessions de pesée du projet (page 0, EN_COURS d'abord puis les terminées récentes) :
     * <ul>
     *   <li>EN_COURS absentes du téléphone : liste + détail mis en cache (import hors ligne) ;</li>
     *   <li>présentes et SYNCED, dont la version serveur a changé (web, autre appareil) :
     *       détail relu et fusionné tout de suite, avec notification des nouveaux
     *       événements web (voir PeseeServeurSync).</li>
     * </ul>
     * {@code budget} : lectures de détail encore permises pour ce passage (partagé entre
     * les projets). Un échec est mémorisé par version (pas de nouvel essai avant 30 min).
     */
    public static void prefetchPesees(Context appContext, LocalDatabase localDatabase, String projetUniqueId, int[] budget) {
        if (projetUniqueId == null) return;
        ApiClient.dataApi(appContext).listSessionsPesee(projetUniqueId, null, 0, 50)
                .enqueue(new Callback<ApiEnvelope<SessionPeseeServeur.Page>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<SessionPeseeServeur.Page>> call, Response<ApiEnvelope<SessionPeseeServeur.Page>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().getData() == null
                        || response.body().getData().data == null) return;
                // Lignes locales lues et parsées UNE fois (thread principal).
                java.util.Map<String, SaisieLocale> lignes = new java.util.HashMap<>();
                java.util.Map<String, Long> versions = new java.util.HashMap<>();
                for (SaisieLocale l : localDatabase.getSaisiesByType(com.mobile.diafarms.models.SaisieType.PESEE_SESSION)) {
                    try {
                        SessionPeseeSyncRequest req = gson.fromJson(l.getPayloadJson(), SessionPeseeSyncRequest.class);
                        if (req == null || req.uniqueId == null) continue;
                        lignes.put(req.uniqueId, l);
                        versions.put(req.uniqueId, req.versionServeur);
                    } catch (Exception ignored) {
                        // ligne illisible : ignorée
                    }
                }
                List<SessionPeseeServeur> enCoursAbsentes = new ArrayList<>();
                for (SessionPeseeServeur s : response.body().getData().data) {
                    if (s == null || s.uniqueId == null) continue;
                    SaisieLocale ligne = lignes.get(s.uniqueId);
                    if (ligne == null) {
                        if (s.isTerminee()) continue;
                        enCoursAbsentes.add(s);
                        if (!detailEnCacheAJour(localDatabase, s)) {
                            prefetchDetailPesee(appContext, localDatabase, s, null, budget);
                        }
                    } else if (SaisieLocale.STATUT_SYNCED.equals(ligne.getSyncStatus())
                            && s.version != null && !s.version.equals(versions.get(s.uniqueId))) {
                        prefetchDetailPesee(appContext, localDatabase, s, ligne.getLocalId(), budget);
                    }
                }
                localDatabase.putCache(CACHE_PESEE_SESSIONS_PREFIX + projetUniqueId, gson.toJson(enCoursAbsentes));
            }

            @Override
            public void onFailure(Call<ApiEnvelope<SessionPeseeServeur.Page>> call, Throwable t) { }
        });
    }

    private static boolean detailEnCacheAJour(LocalDatabase localDatabase, SessionPeseeServeur s) {
        try {
            String json = localDatabase.getCache(CACHE_PESEE_DETAIL_PREFIX + s.uniqueId);
            if (json == null) return false;
            SessionPeseeServeur d = gson.fromJson(json, SessionPeseeServeur.class);
            return d != null && d.version != null && d.version.equals(s.version);
        } catch (Exception e) {
            return false;
        }
    }

    /** Détail d'une session : mis en cache ({@code localId} null) ou fusionné dans la ligne
     * {@code localId} si elle est toujours SYNCED (rien en attente d'envoi). */
    private static void prefetchDetailPesee(Context appContext, LocalDatabase localDatabase, SessionPeseeServeur s,
                                            String localId, int[] budget) {
        String cleEssai = CACHE_PESEE_ESSAI_PREFIX + s.uniqueId;
        String essai = localDatabase.getCache(cleEssai);
        if (essai != null && essai.equals(String.valueOf(s.version))
                && System.currentTimeMillis() - localDatabase.getCacheUpdatedAt(cleEssai) < PESEE_ESSAI_DELAI_MS) {
            return; // échec récent pour cette même version
        }
        if (budget[0] <= 0) return;
        budget[0]--;
        String uniqueId = s.uniqueId;
        ApiClient.dataApi(appContext).getSessionPesee(uniqueId).enqueue(new Callback<ApiEnvelope<SessionPeseeServeur>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<SessionPeseeServeur>> call, Response<ApiEnvelope<SessionPeseeServeur>> response) {
                SessionPeseeServeur d = response.isSuccessful() && response.body() != null ? response.body().getData() : null;
                if (d == null || d.uniqueId == null || d.pesees == null) {
                    localDatabase.putCache(cleEssai, String.valueOf(s.version));
                    return;
                }
                localDatabase.deleteCache(cleEssai);
                if (localId == null) {
                    // Importée entre-temps : inutile de garder le détail.
                    if (PeseeServeurSync.trouverLigne(localDatabase, uniqueId) == null) {
                        localDatabase.putCache(CACHE_PESEE_DETAIL_PREFIX + uniqueId, gson.toJson(d));
                    }
                    return;
                }
                SaisieLocale ligne = localDatabase.getSaisieById(localId);
                if (ligne != null && SaisieLocale.STATUT_SYNCED.equals(ligne.getSyncStatus())) {
                    PeseeServeurSync.appliquer(appContext, localDatabase, localId, null, d, true);
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<SessionPeseeServeur>> call, Throwable t) {
                localDatabase.putCache(cleEssai, String.valueOf(s.version));
            }
        });
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

        prefetchDernierPoidsMoyen(appContext, localDatabase, projetUniqueId);
    }

    /** Dernier poids moyen (session de pesée TERMINEE) du projet. Réponse 200 avec
     * data = null : aucune session terminée, on efface l'ancienne valeur. Échec réseau :
     * on garde la dernière valeur reçue (affichée comme estimation datée). */
    public static void prefetchDernierPoidsMoyen(Context appContext, LocalDatabase localDatabase, String projetUniqueId) {
        ApiClient.dataApi(appContext).getDernierPoidsMoyen(projetUniqueId).enqueue(new Callback<ApiEnvelope<DernierPoidsMoyenResponse>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<DernierPoidsMoyenResponse>> call, Response<ApiEnvelope<DernierPoidsMoyenResponse>> response) {
                if (!response.isSuccessful() || response.body() == null) return;
                DernierPoidsMoyenResponse data = response.body().getData();
                String key = CACHE_DERNIER_POIDS_MOYEN_PREFIX + projetUniqueId;
                if (data == null || data.poidsMoyenKg == null) localDatabase.deleteCache(key);
                else localDatabase.putCache(key, gson.toJson(data));
            }

            @Override
            public void onFailure(@NonNull Call<ApiEnvelope<DernierPoidsMoyenResponse>> call, Throwable t) { }
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
