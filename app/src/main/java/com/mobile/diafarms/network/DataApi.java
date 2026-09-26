package com.mobile.diafarms.network;

import com.mobile.diafarms.network.dto.ApiEnvelope;
import com.mobile.diafarms.network.dto.BatimentSelectResponse;
import com.mobile.diafarms.network.dto.ClientCreateRequest;
import com.mobile.diafarms.network.dto.ClientSelectResponse;
import com.mobile.diafarms.network.dto.CollecteOeufsCreateRequest;
import com.mobile.diafarms.network.dto.CommandeCreateRequest;
import com.mobile.diafarms.network.dto.CommandeResponse;
import com.mobile.diafarms.network.dto.CompteClientResponse;
import com.mobile.diafarms.network.dto.PaiementClientRequest;
import com.mobile.diafarms.network.dto.PageResponse;
import com.mobile.diafarms.network.dto.ConsommationAlimentCreateRequest;
import com.mobile.diafarms.network.dto.CreatedEntityResponse;
import com.mobile.diafarms.network.dto.AlimentationCreateRequest;
import com.mobile.diafarms.network.dto.EffectifReformeResponse;
import com.mobile.diafarms.network.dto.DernierPoidsMoyenResponse;
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
import com.mobile.diafarms.network.dto.SessionPeseeServeur;
import com.mobile.diafarms.network.dto.SessionPeseeSyncRequest;
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
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.PUT;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/** Endpoints diafarms_back consommés par les écrans de saisie mobile. Toute écriture d'une
 * saisie porte l'en-tête Idempotency-Key (clé stable de la saisie, SaisieLocale.cleEnvoi) :
 * un renvoi après une réponse perdue est rejoué par le serveur au lieu d'être recréé. */
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
    Call<ApiEnvelope<CreatedEntityResponse>> createSoins(@Header("Idempotency-Key") String idempotencyKey, @Body SoinsCreateRequest request);

    // ============== ENTRETIEN (poulailler ou site — jamais lié à un projet, voir
    // Entretien.java côté back) ==============
    @POST("entretiens/create")
    Call<ApiEnvelope<CreatedEntityResponse>> createEntretien(@Header("Idempotency-Key") String idempotencyKey, @Body EntretienCreateRequest request);

    // ============== NOTIFICATIONS (alertes ferme entière — voir AlertCheckWorker,
    // vérification périodique en arrière-plan pour les notifications locales) ==============
    @GET("notifications/list")
    Call<ApiEnvelope<List<NotificationResponse>>> getNotifications();

    // ============== MORTALITÉ ==============
    @POST("mortalites/create")
    Call<ApiEnvelope<CreatedEntityResponse>> createMortalite(@Header("Idempotency-Key") String idempotencyKey, @Body MortaliteCreateRequest request);

    // ============== COLLECTE D'ŒUFS ==============
    @POST("collectes-oeufs/create")
    Call<ApiEnvelope<CreatedEntityResponse>> createCollecteOeufs(@Header("Idempotency-Key") String idempotencyKey, @Body CollecteOeufsCreateRequest request);

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
    Call<ApiEnvelope<CreatedEntityResponse>> createReforme(@Header("Idempotency-Key") String idempotencyKey, @Body ReformeCreateRequest request);

    // ============== VENTE D'ŒUFS (Finance : plafonnée par le stock vendable de
    // TOUTE LA FERME, pas d'un projet précis) ==============
    @GET("ventes-oeufs/stock")
    Call<ApiEnvelope<StockOeufsResponse>> getStockOeufs();

    @POST("ventes-oeufs/create")
    Call<ApiEnvelope<CreatedEntityResponse>> createVenteOeufs(@Header("Idempotency-Key") String idempotencyKey, @Body VenteOeufsCreateRequest request);

    // ============== VENTE RÉFORME (Finance : plafonnée par le total réformé de
    // TOUTE LA FERME) ==============
    @GET("ventes-reforme/stock")
    Call<ApiEnvelope<StockReformeResponse>> getStockReforme();

    @POST("ventes-reforme/create")
    Call<ApiEnvelope<CreatedEntityResponse>> createVenteReforme(@Header("Idempotency-Key") String idempotencyKey, @Body VenteReformeCreateRequest request);

    // ============== CLIENT (VENTE) ==============
    @POST("clients/create")
    Call<ApiEnvelope<CreatedEntityResponse>> createClient(@Header("Idempotency-Key") String idempotencyKey, @Body ClientCreateRequest request);

    // ============== COMMANDE (VENTE : ce qu'un client demande avant la vente) ==============
    @POST("commandes/create")
    Call<ApiEnvelope<CreatedEntityResponse>> createCommande(@Header("Idempotency-Key") String idempotencyKey, @Body CommandeCreateRequest request);

    // Commandes d'un statut donné (EN_ATTENTE, CONFIRMEE, EN_LIVRAISON...) — voir
    // CachePrefetcher.prefetchCommandes, qui ne garde que les commandes ouvertes.
    @GET("commandes/list")
    Call<ApiEnvelope<PageResponse<CommandeResponse>>> getCommandes(@Query("page") int page,
                                                                   @Query("size") int size,
                                                                   @Query("statut") String statut);

    // Livraison partielle ou totale (voir CommandeController.livrer) : paramètres d'URL,
    // pas de corps. quantite en œufs ou en sujets ; au kilo, poidsTotalKg obligatoire.
    // date (yyyy-MM-dd) / heure (HH:mm) facultatives : jour réel d'une livraison saisie hors
    // ligne (un serveur plus ancien les ignore et date la livraison du jour de l'envoi).
    @POST("commandes/{uniqueId}/livrer")
    Call<ApiEnvelope<CreatedEntityResponse>> livrerCommande(@Header("Idempotency-Key") String idempotencyKey,
                                                            @Path("uniqueId") String commandeUniqueId,
                                                            @Query("quantite") Integer quantite,
                                                            @Query("montantRecu") Double montantRecu,
                                                            @Query("mode") String mode,
                                                            @Query("poidsTotalKg") Double poidsTotalKg,
                                                            @Query("prixKg") Double prixKg,
                                                            @Query("date") String date,
                                                            @Query("heure") String heure);

    // ============== PAIEMENTS CLIENT (encaissement) ==============
    @POST("paiements-client/create")
    Call<ApiEnvelope<CreatedEntityResponse>> createPaiementClient(@Header("Idempotency-Key") String idempotencyKey,
                                                                  @Body PaiementClientRequest request);

    // Paiement réservé à une commande : client et commande déduits de l'URL.
    @POST("commandes/{uniqueId}/paiement")
    Call<ApiEnvelope<CreatedEntityResponse>> createPaiementCommande(@Header("Idempotency-Key") String idempotencyKey,
                                                                    @Path("uniqueId") String commandeUniqueId,
                                                                    @Body PaiementClientRequest request);

    // Compte d'un client (reste à payer, avance libre / réservée) : seule la partie "compte"
    // est lue, l'historique est ignoré.
    // Comptes de tous les clients de la ferme en un appel (paginé). Réponse lue souplement
    // (voir CachePrefetcher.rafraichirComptesClients) ; 404 = serveur plus ancien.
    @GET("clients/comptes")
    Call<ApiEnvelope<com.google.gson.JsonElement>> getComptesClients(@Query("page") int page, @Query("size") int size);

    @GET("clients/{uniqueId}/compte")
    Call<ApiEnvelope<CompteClientResponse>> getCompteClient(@Path("uniqueId") String clientUniqueId);

    // ============== SALAIRES (COMPTABLE : paiement uniquement, pas de gestion de la
    // grille — voir SalaireServiceImpl.payer côté back, au plus un paiement par période) ==============
    @POST("salaires/payer")
    Call<ApiEnvelope<CreatedEntityResponse>> payerSalaire(@Header("Idempotency-Key") String idempotencyKey, @Body SalairePayerRequest request);

    // ============== ALIMENTATION — ACHAT (aliment entrant, par projet) ==============
    @POST("alimentations/create/{uniqueIdProjet}")
    Call<ApiEnvelope<CreatedEntityResponse>> createAlimentationAchat(
            @Header("Idempotency-Key") String idempotencyKey, @Path("uniqueIdProjet") String projetUniqueId, @Body AlimentationCreateRequest request);

    // ============== ALIMENTATION — CONSOMMATION (aliment sortant) ==============
    @POST("consommations-aliment/create")
    Call<ApiEnvelope<CreatedEntityResponse>> createConsommationAliment(@Header("Idempotency-Key") String idempotencyKey, @Body ConsommationAlimentCreateRequest request);

    // ============== SESSIONS DE PESÉE (Production) — envoi de l'état complet de la
    // session, idempotent côté serveur (voir SessionPeseeSyncRequest). La réponse est
    // l'état serveur complet (SessionPeseeServeur), fusionné dans l'état local.
    // X-Pesee-Contrat: 2 : le serveur répond 200 + peseesRefusees (au lieu d'un 400)
    // quand des pesées arrivent sur une session déjà terminée sur le web. ==============
    @Headers("X-Pesee-Contrat: 2")
    @POST("pesees/sessions/sync")
    Call<ApiEnvelope<SessionPeseeServeur>> syncSessionPesee(@Body SessionPeseeSyncRequest request);

    /** Sessions d'un projet (page 0-based ; pesees/evenements vides dans la liste). */
    @GET("pesees/sessions/list")
    Call<ApiEnvelope<SessionPeseeServeur.Page>> listSessionsPesee(@Query("projetUniqueId") String projetUniqueId,
                                                                 @Query("statut") String statut,
                                                                 @Query("page") int page,
                                                                 @Query("size") int size);

    /** Détail complet d'une session (pesées annulées comprises + journal web). */
    // Dernière session de pesée TERMINEE du projet (poids moyen par sujet) : sert à
    // estimer le poids d'une vente/commande de réformes au kilo. data = null si aucune.
    @GET("pesees/dernier-poids-moyen")
    Call<ApiEnvelope<DernierPoidsMoyenResponse>> getDernierPoidsMoyen(@Query("projetUniqueId") String projetUniqueId);

    @GET("pesees/sessions/{uniqueId}")
    Call<ApiEnvelope<SessionPeseeServeur>> getSessionPesee(@Path("uniqueId") String uniqueId);

    // ============== TRANSACTIONS (finance) ==============
    @POST("transactions/create")
    Call<ApiEnvelope<CreatedEntityResponse>> createTransaction(@Header("Idempotency-Key") String idempotencyKey, @Body TransactionCreateRequest request);

    // ============== ALERTES (recalculées côté serveur, jamais mockées côté client —
    // même logique que le web, voir NotificationServiceImpl) ==============
    @GET("notifications/projet/{projetUniqueId}")
    Call<ApiEnvelope<List<NotificationResponse>>> getNotificationsForProjet(@Path("projetUniqueId") String projetUniqueId);

    // ============== PROFIL (ferme et "consultation seule", absents du JWT du QR) ==============
    @GET("auth/me")
    Call<ApiEnvelope<com.mobile.diafarms.network.dto.ProfilResponse>> getMonProfil();

    @GET("farms/me")
    Call<ApiEnvelope<java.util.Map<String, String>>> getMaFerme();

    @PUT("notifications/{key}/read")
    Call<ApiEnvelope<String>> markNotificationRead(@Path("key") String key);
}
