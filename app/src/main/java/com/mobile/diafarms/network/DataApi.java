package com.mobile.diafarms.network;

import com.mobile.diafarms.network.dto.ApiEnvelope;
import com.mobile.diafarms.network.dto.BatimentSelectResponse;
import com.mobile.diafarms.network.dto.ClientCreateRequest;
import com.mobile.diafarms.network.dto.ClientSelectResponse;
import com.mobile.diafarms.network.dto.CollecteOeufsCreateRequest;
import com.mobile.diafarms.network.dto.CommandeCreateRequest;
import com.mobile.diafarms.network.dto.ConsommationAlimentCreateRequest;
import com.mobile.diafarms.network.dto.CreatedEntityResponse;
import com.mobile.diafarms.network.dto.AlimentationCreateRequest;
import com.mobile.diafarms.network.dto.EffectifReformeResponse;
import com.mobile.diafarms.network.dto.PlafondSaisieResponse;
import com.mobile.diafarms.network.dto.SiteSelectResponse;
import com.mobile.diafarms.network.dto.FarmAppSettingsResponse;
import com.mobile.diafarms.network.dto.MagasinSelectResponse;
import com.mobile.diafarms.network.dto.MortaliteCreateRequest;
import com.mobile.diafarms.network.dto.NotificationResponse;
import com.mobile.diafarms.network.dto.ProjetDetailResponse;
import com.mobile.diafarms.network.dto.ProjetSelectResponse;
import com.mobile.diafarms.network.dto.ReformeCreateRequest;
import com.mobile.diafarms.network.dto.SalairePayerRequest;
import com.mobile.diafarms.network.dto.SalaireSelectResponse;
import com.mobile.diafarms.network.dto.SoinsCreateRequest;
import com.mobile.diafarms.network.dto.EntretienCreateRequest;
import com.mobile.diafarms.network.dto.StockAlimentResponse;
import com.mobile.diafarms.network.dto.StockMagasinResponse;
import com.mobile.diafarms.network.dto.StockOeufsResponse;
import com.mobile.diafarms.network.dto.StockReformeResponse;
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
import retrofit2.http.Query;

/** Endpoints diafarms_back consommés par les écrans de saisie mobile. */
public interface DataApi {

    // ============== SÉLECTEURS ==============
    @GET("projets/select")
    Call<ApiEnvelope<List<ProjetSelectResponse>>> getProjetsSelect();

    // Quelles actions Finance sont ouvertes à ce rôle (voir HomeActivity.applyFarmAppSettings) —
    // imposé aussi côté serveur à la connexion/au scan QR (AppAccessRules), pas qu'un masquage d'écran.
    @GET("farm-settings")
    Call<ApiEnvelope<FarmAppSettingsResponse>> getFarmAppSettings();

    // Poulaillers uniquement (élevage) — le stockage/la vente vivent désormais dans
    // Magasin (voir getMagasinsSelect ci-dessous, type VENTE/STOCKAGE).
    @GET("batiments/select")
    Call<ApiEnvelope<List<BatimentSelectResponse>>> getBatimentsSelect();

    // TOUS les poulaillers actifs (occupés ou non), pour rattacher une dépense à un poulailler.
    // batiments/select ne renvoie que les poulaillers LIBRES : inutilisable pour ça.
    @GET("batiments/tous")
    Call<ApiEnvelope<List<BatimentSelectResponse>>> getBatimentsTous();

    // Sites (emplacements) de la ferme, pour rattacher facultativement une dépense à un site.
    @GET("sites/list")
    Call<ApiEnvelope<List<SiteSelectResponse>>> getSites();

    // Stock d'œufs pas encore transféré vers un magasin de vente, dans ce magasin de
    // stockage précis — pure info contextuelle à la collecte (voir SaisieFormActivity),
    // ne plafonne rien : une collecte AJOUTE au stock, elle ne le consomme pas.
    @GET("magasin-transferts/disponible-batiment")
    Call<ApiEnvelope<Integer>> getDisponibleMagasinStockage(@Query("magasinStockageUniqueId") String magasinStockageUniqueId);

    // Un vendeur (rôle VENTE) ne voit que les magasins de vente auxquels il est lié —
    // voir MagasinServiceImpl.list() côté back, déjà filtré côté serveur. type =
    // "VENTE" ou "STOCKAGE" (voir Magasin.TypeMagasin), omis = tous types confondus.
    @GET("magasins/list")
    Call<ApiEnvelope<List<MagasinSelectResponse>>> getMagasinsSelect(@Query("type") String type);

    @GET("magasins/{uniqueId}/stock")
    Call<ApiEnvelope<StockMagasinResponse>> getStockMagasin(@Path("uniqueId") String magasinUniqueId);

    // Clients déjà synchronisés côté serveur, farm-scopée — voir ClientSelectResponse.
    @GET("clients/select")
    Call<ApiEnvelope<List<ClientSelectResponse>>> getClientsSelect();

    // Grille salariale de la ferme (rôle Comptable, "Payer un salaire") — voir
    // SalaireSelectResponse.
    @GET("salaires/select")
    Call<ApiEnvelope<List<SalaireSelectResponse>>> getSalairesSelect();

    @GET("projets/findbyUniqueId/{uniqueId}")
    Call<ApiEnvelope<ProjetDetailResponse>> getProjetDetail(@Path("uniqueId") String uniqueId);

    @GET("consommations-aliment/stock/{projetUniqueId}")
    Call<ApiEnvelope<StockAlimentResponse>> getStockAliment(@Path("projetUniqueId") String projetUniqueId);

    // ============== SOINS (entité unifiée : Soins générique — Médicament/Autre — ET
    // Vaccination, fusionnées côté back en une seule table/entité "Soins", distinguées
    // par le champ type. Vaccination utilise ce même endpoint, avec
    // type=VACCINATION et les champs quantite/prixUnitaire/modeAdministration
    // renseignés — voir SaisieFormActivity.onValider). ==============
    @POST("soins/create")
    Call<ApiEnvelope<CreatedEntityResponse>> createSoins(@Body SoinsCreateRequest request);

    // ============== ENTRETIEN (poulailler ou site — jamais lié à un projet, voir
    // Entretien.java côté back) ==============
    @POST("entretiens/create")
    Call<ApiEnvelope<CreatedEntityResponse>> createEntretien(@Body EntretienCreateRequest request);

    // ============== NOTIFICATIONS (alertes ferme entière — voir AlertCheckWorker,
    // vérification périodique en arrière-plan pour les notifications locales) ==============
    @GET("notifications/list")
    Call<ApiEnvelope<List<NotificationResponse>>> getNotifications();

    // ============== MORTALITÉ ==============
    @POST("mortalites/create")
    Call<ApiEnvelope<CreatedEntityResponse>> createMortalite(@Body MortaliteCreateRequest request);

    // ============== COLLECTE D'ŒUFS ==============
    @POST("collectes-oeufs/create")
    Call<ApiEnvelope<CreatedEntityResponse>> createCollecteOeufs(@Body CollecteOeufsCreateRequest request);

    // ============== RÉFORME (Production : comptage pur, plafonné par l'effectif
    // vivant DU PROJET — aucun prix, voir diafarms_back ReformeImpl) ==============
    // Plafond d'une saisie (effectif vivant du poulailler/projet, œufs encore collectables
    // ce jour-là) : alimente les alertes de cohérence en direct du formulaire.
    @GET("plafond-saisie")
    Call<ApiEnvelope<PlafondSaisieResponse>> getPlafondSaisie(@Query("projetUniqueId") String projetUniqueId,
                                                              @Query("batimentUniqueId") String batimentUniqueId,
                                                              @Query("date") String date);

    @GET("reformes/effectif/{projetUniqueId}")
    Call<ApiEnvelope<EffectifReformeResponse>> getEffectifReforme(@Path("projetUniqueId") String projetUniqueId);

    @POST("reformes/create")
    Call<ApiEnvelope<CreatedEntityResponse>> createReforme(@Body ReformeCreateRequest request);

    // ============== VENTE D'ŒUFS (Finance : plafonnée par le stock vendable de
    // TOUTE LA FERME, pas d'un projet précis) ==============
    @GET("ventes-oeufs/stock")
    Call<ApiEnvelope<StockOeufsResponse>> getStockOeufs();

    @POST("ventes-oeufs/create")
    Call<ApiEnvelope<CreatedEntityResponse>> createVenteOeufs(@Body VenteOeufsCreateRequest request);

    // ============== VENTE RÉFORME (Finance : plafonnée par le total réformé de
    // TOUTE LA FERME) ==============
    @GET("ventes-reforme/stock")
    Call<ApiEnvelope<StockReformeResponse>> getStockReforme();

    @POST("ventes-reforme/create")
    Call<ApiEnvelope<CreatedEntityResponse>> createVenteReforme(@Body VenteReformeCreateRequest request);

    // ============== CLIENT (VENTE) ==============
    @POST("clients/create")
    Call<ApiEnvelope<CreatedEntityResponse>> createClient(@Body ClientCreateRequest request);

    // ============== COMMANDE (VENTE : ce qu'un client demande avant la vente) ==============
    @POST("commandes/create")
    Call<ApiEnvelope<CreatedEntityResponse>> createCommande(@Body CommandeCreateRequest request);

    // ============== SALAIRES (COMPTABLE : paiement uniquement, pas de gestion de la
    // grille — voir SalaireServiceImpl.payer côté back, au plus un paiement par période) ==============
    @POST("salaires/payer")
    Call<ApiEnvelope<CreatedEntityResponse>> payerSalaire(@Body SalairePayerRequest request);

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
