package com.mobile.diafarms.ui.saisie;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.mobile.diafarms.util.AlveoleUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mobile.diafarms.R;
import com.mobile.diafarms.data.CachePrefetcher;
import com.mobile.diafarms.data.CommandesHorsLigne;
import com.mobile.diafarms.network.dto.CommandeResponse;
import com.mobile.diafarms.network.dto.LivraisonCommandeRequest;
import com.mobile.diafarms.network.dto.CompteClientResponse;
import com.mobile.diafarms.network.dto.PaiementClientRequest;
import com.mobile.diafarms.data.DernierePeseeEstimation;
import com.mobile.diafarms.network.dto.DernierPoidsMoyenResponse;
import com.mobile.diafarms.data.LocalDatabase;
import com.mobile.diafarms.models.SaisieLocale;
import com.mobile.diafarms.models.SaisieType;
import com.mobile.diafarms.network.ApiClient;
import com.mobile.diafarms.network.dto.AlimentationCreateRequest;
import com.mobile.diafarms.network.dto.ApiEnvelope;
import com.mobile.diafarms.network.dto.ClientCreateRequest;
import com.mobile.diafarms.network.dto.ClientSelectResponse;
import com.mobile.diafarms.network.dto.CollecteOeufsCreateRequest;
import com.mobile.diafarms.network.dto.CommandeCreateRequest;
import com.mobile.diafarms.network.dto.ConsommationAlimentCreateRequest;
import com.mobile.diafarms.network.dto.EffectifReformeResponse;
import com.mobile.diafarms.network.dto.PlafondSaisieResponse;
import com.mobile.diafarms.network.dto.BatimentSelectResponse;
import com.mobile.diafarms.network.dto.SiteSelectResponse;
import com.mobile.diafarms.network.dto.MagasinSelectResponse;
import com.mobile.diafarms.network.dto.MortaliteCreateRequest;
import com.mobile.diafarms.network.dto.OccupationBatimentResponse;
import com.mobile.diafarms.network.dto.ProjetDetailResponse;
import com.mobile.diafarms.network.dto.ProjetSelectResponse;
import com.mobile.diafarms.network.dto.ReformeCreateRequest;
import com.mobile.diafarms.network.dto.SalairePayerRequest;
import com.mobile.diafarms.network.dto.SalaireSelectResponse;
import com.mobile.diafarms.network.dto.SoinsCreateRequest;
import com.mobile.diafarms.network.dto.StockAlimentResponse;
import com.mobile.diafarms.network.dto.StockMagasinResponse;
import com.mobile.diafarms.network.dto.TransactionCreateRequest;
import com.mobile.diafarms.network.dto.VenteOeufsCreateRequest;
import com.mobile.diafarms.network.dto.VenteReformeCreateRequest;
import com.mobile.diafarms.util.OccupationUtils;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Écran de saisie unique, paramétré par SaisieType : affiche seulement les champs
 * pertinents pour le type demandé. Enregistre toujours en local (jamais d'appel réseau
 * direct ici) — la synchronisation se fait depuis l'accueil, via SyncManager, ce qui
 * permet de lister/modifier/supprimer une saisie tant qu'elle n'est pas encore envoyée.
 */
public class SaisieFormActivity extends AppCompatActivity {

    public static final String EXTRA_TYPE = "TYPE";
    public static final String EXTRA_PROJET_ID = "PROJET_ID";
    public static final String EXTRA_PROJET_LABEL = "PROJET_LABEL";
    public static final String EXTRA_LOCAL_ID = "LOCAL_ID"; // présent seulement en édition
    // Livraison / encaissement lancés depuis l'écran Commandes : commande concernée.
    public static final String EXTRA_COMMANDE_ID = "COMMANDE_ID";

    // "Vente" n'a de sens que pour une Entrée, "Achat"/"Salaire"/... que pour une
    // Sortie — deux listes séparées plutôt qu'une liste unique proposant des
    // catégories hors-sujet selon le type (même règle que CreateTransactionDialog
    // côté web). Le type est fixé pour tout l'écran (TRANSACTION_ENTREE ou
    // TRANSACTION_SORTIE choisi depuis l'accueil), donc pas besoin de basculer la
    // liste dynamiquement ici, contrairement au web où un seul formulaire couvre
    // les deux types.
    // "Vente" retirée — voir CreateTransactionDialog côté web : une vente réelle passe
    // par Vente œufs/réforme/fientes/Autre vente, jamais une "Entrée d'argent" manuelle.
    private static final String[] CATEGORIES_TRANSACTION_ENTREE = {"Location", "Don", "Autre"};
    // "Salaire" retiré : le paiement d'un salaire passe obligatoirement par "Payer un
    // salaire" (SALAIRE_PAYER), qui vérifie la grille et empêche un double paiement du
    // même mois — une "Sortie d'argent" catégorie "Salaire" contournerait ce contrôle.
    // "Santé / Vétérinaire" est de retour : depuis le retrait du champ Coût de l'écran
    // Santé / Vétérinaire (SaisieType.SOINS, sous-type Médicament/Autre — la Production
    // ne suit plus que le fait), c'est ici, en Comptabilité, que ce coût se saisit —
    // sauf pour un Vaccin, qui garde son propre calcul automatique (dose × prix).
    // Même liste que le web (CreateTransactionDialog/EditTransactionDialog,
    // CATEGORIES_SORTIE) : le mobile n'avait que 6 entrées dont "Transport" à la place
    // de "Logistique", sans Alvéole/Copeau/Matériels, donc impossible de saisir ces
    // dépenses depuis le terrain. Les anciennes saisies "Transport" déjà envoyées
    // restent valides côté back (catégorie libre), seule la liste proposée change.
    // "Achat d'aliment" : l'achat d'aliment n'a plus de formulaire à part. Choisir cette
    // catégorie bascule le formulaire en SaisieType.ALIMENTATION_ACHAT (champs aliment,
    // envoi vers alimentations/create qui crée le stock ET la sortie d'argent). Le serveur
    // refuse une sortie manuelle "Aliment" : cette catégorie ne part jamais en transaction.
    private static final String CATEGORIE_ACHAT_ALIMENT = "Achat d'aliment";
    // Santé / Vétérinaire : toujours liée au projet choisi à l'accueil (le serveur refuse
    // une telle dépense sans projet) ; poulailler facultatif, proposé seulement si le
    // projet en occupe plusieurs ; jamais « commune » ni site.
    private static final String CATEGORIE_SANTE = "Santé / Vétérinaire";
    private static final String[] CATEGORIES_TRANSACTION_SORTIE = {CATEGORIE_ACHAT_ALIMENT, "Santé / Vétérinaire", "Logistique", "Électricité / Eau", "Entretien / Maintenance", "Alvéole", "Copeau", "Matériels", "Autre"};
    // Type d'aliment acheté : libellés affichés et valeurs envoyées (enum TypeAliment côté back), même ordre.
    private static final String[] TYPES_ALIMENT = {"Démarrage", "Croissance", "Ponte", "Autre"};
    private static final String[] TYPES_ALIMENT_WIRE = {"DEMARRAGE", "CROISSANCE", "PONTE", "AUTRE"};
    private static final double POIDS_SAC_DEFAUT_KG = 50;
    // Fusion Soins/Vaccination (UI) : un seul point d'entrée (SaisieType.SOINS, voir
    // HomeActivity/btnSoins), le type choisi ici décide quel sous-groupe de champs est
    // affiché (voir updateGroupSoinsSousType()) — Vaccination n'est plus un
    // SaisieType/écran distinct.
    private static final String[] TYPES_SOIN = {"Vaccination", "Médicament", "Autre"};
    // Valeurs enum backend (Soins.type, entité unifiée) correspondant 1-pour-1 à
    // TYPES_SOIN ci-dessus, dans le même ordre — le spinner reste en français, seule
    // la valeur envoyée au serveur change.
    private static final String[] TYPES_SOIN_WIRE = {"VACCINATION", "MEDICAMENT", "AUTRE"};

    private static final String[] NIVEAUX_ENTRETIEN = {"Poulailler", "Site (toute la ferme)"};
    private static final String[] NIVEAUX_ENTRETIEN_WIRE = {"BATIMENT", "SITE"};
    private static final String[] TYPES_ENTRETIEN = {"Entretien / Nettoyage", "Remplacement de copeau", "Autre"};
    private static final String[] TYPES_ENTRETIEN_WIRE = {"NETTOYAGE", "COPEAU", "AUTRE"};

    private SaisieType type;
    private String projetUniqueId;
    private String projetLabel;
    private String editingLocalId; // null = nouvelle saisie

    private LocalDatabase localDatabase;
    private final Gson gson = new Gson();
    private final SimpleDateFormat isoDate = new SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE);
    private final SimpleDateFormat isoTime = new SimpleDateFormat("HH:mm", Locale.FRANCE);
    private final Calendar dateCal = Calendar.getInstance();
    private Calendar heureCal; // null tant que l'heure n'est pas choisie (optionnelle)

    private List<OccupationBatimentResponse> batiments = new ArrayList<>();
    // Bâtiment à resélectionner dès que "batiments" sera chargé — voir
    // selectBatimentByUniqueId/applyPendingBatimentSelection.
    private String pendingBatimentSelection;

    // TOUS les poulaillers de la ferme (pas seulement occupés par le projet
    // sélectionné, contrairement à "batiments" ci-dessus) — pour Entretien
    // uniquement, voir loadBatimentsEntretien/getBatimentsSelect.
    private List<com.mobile.diafarms.network.dto.BatimentSelectResponse> batimentsEntretien = new ArrayList<>();
    private String pendingBatimentEntretienSelection;

    // Magasins de vente (VENTE) : liste déjà filtrée par le serveur aux magasins liés
    // à ce vendeur — partagée par les deux spinners (Vente œufs / Vente réforme),
    // même liste dans les deux cas. Voir loadMagasins/selectMagasinByUniqueId.
    private List<MagasinSelectResponse> magasins = new ArrayList<>();
    private String pendingMagasinSelection;

    // Magasins de STOCKAGE (Collecte œufs, obligatoire) — déjà filtrés côté serveur au
    // type STOCKAGE (voir loadMagasinsStockage/selectBatimentStockageByUniqueId).
    private List<MagasinSelectResponse> batimentsStockage = new ArrayList<>();
    private String pendingBatimentStockageSelection;

    // Clients déjà synchronisés côté serveur (VENTE) — partagés par les 3 sélecteurs
    // (Vente œufs/réforme : optionnel : Commande : obligatoire), même schéma que
    // "magasins" ci-dessus. Voir loadClients/CachePrefetcher.CACHE_CLIENTS_SELECT.
    private List<ClientSelectResponse> clients = new ArrayList<>();
    private String pendingClientSelection;

    // Vues communes
    private TextView tvTitreForm, tvProjetForm, tvStockInfo;
    // Dernière valeur de stock connue (synchronisée ou en cache hors ligne) pour le
    // projet actif — sert à bloquer une saisie de consommation intenable AVANT de la
    // créer/mettre en file d'attente, plutôt que de laisser l'utilisateur remplir tout
    // le formulaire pour découvrir le rejet seulement à l'envoi (voir onValider, cas
    // ALIMENTATION_CONSOMMATION). null tant qu'aucune valeur n'est connue (jamais
    // bloquant dans ce cas : pas de fausse alerte faute de donnée).
    private Double stockAlimentRestantConnu;
    private View groupBatimentTop, groupDateHeureTop;
    private AutoCompleteTextView spinnerBatiment;
    private TextInputEditText etDate, etHeure;
    private MaterialButton btnValiderForm;

    // Collecte
    private View groupCollecte;
    private AutoCompleteTextView spinnerBatimentStockage;
    private TextView tvStockBatimentStockage;
    // Compté en deux temps comme sur le terrain (voir AlveoleUtils) : alvéoles pleines
    // + œufs qui ne remplissent pas un plateau entier, total = alvéoles×30 + œufs.
    // oeufsCasses reste toujours en œufs individuels.
    private TextInputEditText etAlveolesCollectees, etOeufsCollectes, etOeufsCasses, etOeufsNonUtilisables;
    private TextView tvResumeCollecte;

    // Soins & Vaccination (fusionnées, un seul écran/type — voir TYPES_SOIN ci-dessus)
    private View groupSoins;
    private AutoCompleteTextView spinnerTypeSoin;
    // Champs génériques (Médicament/Autre), visibles seulement si le type choisi
    // n'est pas Vaccination — voir updateGroupSoinsSousType().
    private View groupSoinsGenerique;
    // Pas de coût ici : la Production ne suit que le fait, le coût réel se saisit
    // séparément en Comptabilité (catégorie "Santé / Vétérinaire").
    private TextInputEditText etProduit, etQuantiteSoin, etObservationsSoin;

    // Champs Vaccination (doses seulement, aucun montant — la Production ne suit
    // que le fait, le coût réel se saisit en Comptabilité), visibles seulement si le
    // type choisi est Vaccination — voir updateGroupSoinsSousType().
    private View groupVaccination;
    private TextInputEditText etNomVaccin, etQuantiteVaccin;
    private CheckBox cbModeOral, cbModeInjection, cbModePulverisation, cbModeTopique;

    // Entretien (poulailler ou site — jamais lié au projet, voir SaisieType.ENTRETIEN)
    private View groupEntretien;
    private AutoCompleteTextView spinnerNiveauEntretien;
    private View groupEntretienBatiment;
    private AutoCompleteTextView spinnerBatimentEntretien, spinnerTypeEntretien;
    private TextInputEditText etDescriptionEntretien, etObservationsEntretien;

    // Mortalité
    private View groupMortalite;
    private TextInputEditText etNombreMorts, etCauseMortalite;

    // Réforme (Production) — comptage pur, plafonné par l'effectif vivant DU PROJET,
    // jamais de prix ici (voir effectifReformeDisponible). Mirroir de Mortalité.
    private View groupReforme;
    private TextView tvEffectifReformeInfo;
    private TextInputEditText etNombreSujetsReforme, etCauseReforme;
    private Integer effectifReformeDisponible;
    // Point de vente où la réforme place ses sujets (transfert automatique côté serveur,
    // voir PointDeVenteReformes) : masqué s'il n'y a qu'un point de vente, présélectionné
    // sur le point de vente par défaut sinon.
    private com.google.android.material.textfield.TextInputLayout tilPointDeVenteReforme;
    private AutoCompleteTextView spinnerPointDeVenteReforme;
    private List<MagasinSelectResponse> pointsDeVenteReforme = new ArrayList<>();
    private String pendingPointDeVenteReforme;

    // Alimentation - achat
    // Affiché dans le formulaire Sortie d'argent, catégorie "Achat d'aliment" (le montant
    // est celui de la sortie, etMontant).
    private View groupAlimentationAchat, groupAlimentationAchatFin, groupTransactionDetails;
    private AutoCompleteTextView spinnerTypeAliment;
    private TextInputEditText etSac, etPoidsSac, etQuantiteKgAchat, etFournisseurAchat, etObservationsAchat;
    // Nom libre d'un achat saisi avec une version antérieure (conservé tel quel en modification).
    private String nomAlimentExistant;
    // Quantité totale : calculée (sacs x poids d'un sac) tant que l'utilisateur ne l'a pas modifiée.
    private boolean quantiteAchatManuelle = false;
    private boolean majQuantiteAchatAuto = false;

    // Alimentation - consommation
    private View groupConsommation;
    private TextInputEditText etQuantiteKgConso;

    // Vente œufs (VENTE) — vendue DEPUIS un magasin précis (obligatoire, voir
    // spinnerMagasinOeufs), plafonnée par le stock vendable DE CE MAGASIN (vrai stock
    // séparé par magasin), calculé côté serveur (voir stockOeufsDisponible, jamais
    // recalculé sur l'appareil).
    private View groupVenteOeufs;
    private AutoCompleteTextView spinnerMagasinOeufs;
    private AutoCompleteTextView spinnerClientVenteOeufs;
    private TextView tvStockOeufsInfo;
    // Unité de saisie de etQuantiteOeufsVente/etPrixUnitaireOeufs (voir AlveoleUtils) —
    // Œuf ou Alvéole (plateau de 30 œufs) ; req.quantiteOeufs envoyé au serveur reste
    // toujours en œufs, quelle que soit l'unité choisie ici (voir onValider).
    private RadioGroup radioGroupUniteVenteOeufs;
    private TextInputLayout tilQuantiteOeufsVente, tilPrixUnitaireOeufs;
    private TextInputEditText etQuantiteOeufsVente, etPrixUnitaireOeufs, etMontantVenteOeufs, etMontantRapporteVenteOeufs;
    // Libellé/visibilité pilotés par la présence d'un client (spinnerClientVenteOeufs) —
    // voir refreshModePaiementVenteOeufs.
    private TextInputLayout tilMontantRapporteVenteOeufs, tilModePaiementVenteOeufs;
    private AutoCompleteTextView spinnerModePaiementVenteOeufs;
    private Integer stockOeufsDisponible;
    // Bon (défaut) ou cassé — deux pools de stock magasin totalement séparés côté
    // serveur (voir TypeStockMagasin.OEUFS_CASSES) : bascule quel disponible est
    // vérifié/affiché, voir refreshStockOeufsAffiche().
    private RadioGroup radioGroupTypeOeufVente;
    private StockMagasinResponse dernierStockMagasinOeufs;
    // Le montant rapporté suit le montant théorique par défaut (vente payée
    // intégralement) tant que l'utilisateur ne l'a pas modifié lui-même — même
    // principe que CreateVenteOeufsDialog côté web.
    private boolean montantRapporteOeufsModifieManuel = false;
    private boolean montantRapporteReformeModifieManuel = false;
    // Distingue une saisie utilisateur d'un setText() programmatique sur le champ
    // "montant rapporté" (voir recalculerMontantVenteOeufs/recalculerMontantVenteReforme) —
    // sans ça, le TextWatcher marquerait le champ "modifié à la main" dès la première
    // synchronisation automatique.
    private boolean syncingMontantRapporte = false;

    // Vente réforme (VENTE) — vendue DEPUIS un magasin précis (obligatoire, voir
    // spinnerMagasinReforme), plafonnée par le total réformé transféré dans CE
    // MAGASIN moins déjà vendu (voir stockReformeDisponible) — distinct de l'effectif
    // vivant d'UN projet (effectifReformeDisponible, saisie Réforme Production).
    private View groupVenteReforme;
    private AutoCompleteTextView spinnerMagasinReforme;
    private AutoCompleteTextView spinnerClientVenteReforme;
    private TextView tvStockReformeInfo;
    private TextInputEditText etNombreSujetsVente, etPrixUnitaireReforme, etMontantVenteReforme, etMontantRapporteVenteReforme;
    private Integer stockReformeDisponible;
    // Par tête (défaut) ou au kilo — même principe que radioGroupTypeOeufVente, sauf
    // que ça ne change pas quel stock est vérifié (toujours en sujets dans les deux
    // cas), seulement le sens de etPrixUnitaireReforme et la présence du poids total —
    // voir refreshTypeVenteReformeUi/recalculerMontantVenteReforme.
    private RadioGroup radioGroupTypeVenteReforme;
    private TextInputLayout tilPoidsTotalReforme, tilPrixUnitaireReforme;
    private View spacerPoidsTotalReforme;
    private TextInputEditText etPoidsTotalReforme;
    // Même principe que Vente d'œufs — voir refreshModePaiementVenteReforme.
    private TextInputLayout tilMontantRapporteVenteReforme, tilModePaiementVenteReforme;
    private AutoCompleteTextView spinnerModePaiementVenteReforme;

    // Nouveau client (VENTE) — voir Client.java côté back, aucune notion de date ici.
    private View groupClient;
    private TextInputEditText etClientNom, etClientTelephone, etClientAdresse, etClientEmail;

    // Nouvelle commande (VENTE) — client TOUJOURS obligatoire et déjà synchronisé
    // (spinnerClientCommande, pas d'option "aucun"), contrairement aux ventes. Voir
    // Commande.java côté back.
    private View groupCommande;
    private AutoCompleteTextView spinnerClientCommande;
    private TextView tvAucunClientCommande;
    private AutoCompleteTextView spinnerMagasinCommande;
    private RadioGroup radioGroupTypeCommande;
    // Unité de saisie (Œuf/Alvéole), visible seulement pour le type Œufs — même
    // patron que radioGroupUniteVenteOeufs (groupVenteOeufs) : jamais l'alvéole
    // envoyée au serveur, voir isCommandeEnAlveoles()/onValider().
    private View groupUniteCommande;
    private RadioGroup radioGroupUniteCommande;
    private TextInputLayout tilQuantiteCommande, tilPrixUnitaireCommande;
    private TextInputEditText etQuantiteCommande, etPrixUnitaireCommande, etMontantEstimeCommande, etAcompteCommande;
    private TextInputEditText etDateLivraisonCommande;
    // Visible seulement si l'acompte saisi ci-dessus est > 0 — voir
    // refreshModePaiementCommande.
    private TextInputLayout tilModePaiementCommande;
    private AutoCompleteTextView spinnerModePaiementCommande;
    private final Calendar dateLivraisonCal = Calendar.getInstance();
    // Commande de réformes : par tête (défaut) ou au kilo (sujets vivants pesés à la
    // livraison). Au kilo : prix du kg estimé obligatoire, poids estimé facultatif ;
    // quantite reste un nombre de sujets. Voir refreshTarificationCommandeUi.
    private View groupTarificationCommande, groupKiloCommande, spacerPrixUnitaireCommande;
    private RadioGroup radioGroupTarificationCommande;
    private TextInputEditText etPrixKgCommande, etPoidsEstimeCommande;
    private TextInputLayout tilMontantEstimeCommande;

    // Estimation du poids d'une vente/commande de réformes au kilo depuis la dernière
    // pesée terminée (sessions locales d'abord, sinon cache serveur), voir
    // loadDernierePesee/refreshEstimationPesee. Simple aide : le poids pesé compte seul.
    private DernierePeseeEstimation dernierePesee;
    private View groupEstimationPeseeReforme, groupEstimationPeseeCommande;
    private TextView tvEstimationPeseeReforme, tvEstimationPeseeCommande, tvPoidsMoyenReforme;
    private MaterialButton btnUtiliserEstimationReforme, btnUtiliserEstimationCommande;

    // Payer un salaire (COMPTABLE) — grille déjà synchronisée côté serveur (voir
    // loadSalaires/CachePrefetcher.CACHE_SALAIRES_SELECT), aucune gestion de la grille
    // elle-même ici (ça reste une action web, voir SaisieType.SALAIRE_PAYER).
    private View groupSalaire;
    private AutoCompleteTextView spinnerEmployeSalaire, spinnerMoisSalaire, spinnerAnneeSalaire;
    private TextView tvAucunSalaireEmploye, tvTauxInfoSalaire;
    private TextInputLayout tilQuantiteSalaire;
    private TextInputEditText etQuantiteSalaire, etMontantSalaire, etDescriptionSalaire;
    private List<SalaireSelectResponse> salaires = new ArrayList<>();
    private String pendingEmployeSalaireSelection;
    private static final String[] MOIS_LABELS = {"Janvier", "Février", "Mars", "Avril", "Mai", "Juin", "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"};

    // Livraison d'une commande (LIVRAISON_COMMANDE) : commande lue dans le cache des
    // commandes ouvertes (voir CommandesHorsLigne), jamais choisie dans ce formulaire.
    private CommandeResponse commandeLivree;
    private View groupLivraison, groupUniteLivraison, groupKiloLivraison;
    private TextView tvLivraisonCommande, tvStockLivraison, tvMontantLivraison;
    private RadioGroup radioGroupUniteLivraison;
    private TextInputLayout tilQuantiteLivraison, tilModePaiementLivraison;
    private TextInputEditText etQuantiteLivraison, etPoidsLivraison, etPrixKgLivraison, etMontantRecuLivraison;
    private AutoCompleteTextView spinnerModePaiementLivraison;
    // Stock du magasin de la commande (œufs bons ou sujets réformés), dernière donnée connue.
    private Integer stockLivraisonDisponible;

    // Encaissement client (PAIEMENT_CLIENT) : client obligatoire, commande facultative
    // (seulement ses commandes ouvertes en cache), mode de paiement obligatoire.
    private View groupPaiementClient;
    private AutoCompleteTextView spinnerClientPaiement, spinnerCommandePaiement, spinnerModePaiementClient;
    private TextView tvAucunClientPaiement, tvSoldeClient;
    private TextInputEditText etMontantPaiement, etObservationsPaiement;
    private final List<CommandeResponse> commandesClientPaiement = new ArrayList<>();
    private String pendingCommandePaiement;
    private static final String LABEL_SANS_COMMANDE = "Aucune (paiement libre)";
    // Modification d'un encaissement dont la commande n'est plus dans le cache (livrée,
    // close...) : on garde sa commande d'origine plutôt que d'en faire un paiement libre.
    private static final String LABEL_COMMANDE_HORS_CACHE = "Commande d'origine (commande non disponible hors ligne)";
    private String commandeHorsCachePaiement;

    // Vente diverse (VENTE_FIENTES / VENTE_AUTRE) : POST ventes-diverses/create.
    private View groupVenteDiverse, groupSacsVenteDiverse;
    private TextInputLayout tilDescriptionVenteDiverse;
    private TextInputEditText etQuantiteVenteDiverse, etPrixUnitaireVenteDiverse, etMontantVenteDiverse, etDescriptionVenteDiverse;
    private boolean majVenteDiverseAuto = false;

    // Payer un salaire : montant estimé (taux de la dernière synchro) affiché dans le champ ;
    // un montant différent saisi par l'utilisateur part en montantForce (prime, retenue).
    private Double montantSalaireEstime;
    private String employeSalaireAffiche;

    // Livraison en alvéoles : œufs en plus (hors alvéole).
    private View tilOeufsSuppLivraison;
    private TextInputEditText etOeufsSuppLivraison;

    // Transaction
    private View groupTransaction;
    private AutoCompleteTextView spinnerCategorie;
    // Catégorie « Autre » : précision obligatoire, envoyée en categoriePrecision.
    private static final String CATEGORIE_AUTRE = "Autre";
    private View tilCategoriePrecision;
    private TextInputEditText etCategoriePrecision;
    private TextInputEditText etMontant, etDescriptionTransaction;
    private View groupSanteQuantite;
    // Santé / Vétérinaire : achat de médicament (dépense + stock, saisie MEDICAMENT_ACHAT)
    // ou service (transaction simple).
    private View groupNatureSante, groupMedicamentAchat;
    private AutoCompleteTextView spinnerNatureSante, spinnerFormeMedicament, spinnerUniteMedicament;
    private TextInputEditText etNomMedicament, etFournisseurMedicament;
    private boolean editionMedicament = false;
    private SaisieType typeEnregistrement = null;
    private static final String NATURE_MEDICAMENT = "Achat de médicament ou de vaccin";
    private static final String NATURE_SERVICE = "Service de santé (consultation, visite...)";
    private static final String[] NATURES_SANTE = {NATURE_MEDICAMENT, NATURE_SERVICE};
    private static final String[] FORMES_MEDICAMENT = {"Liquide", "Poudre", "Comprimés", "Autre"};
    private static final String[] FORMES_MEDICAMENT_WIRE = {"LIQUIDE", "POUDRE", "COMPRIME", "AUTRE"};
    private static final String[][] UNITES_MEDICAMENT = {
            {"flacon", "litre", "ml", "dose"}, {"sachet", "kg", "g", "dose"}, {"boîte", "comprimé"}, {"unité", "dose", "boîte"}};
    // Soin pris dans le stock de médicaments du projet.
    private View groupSoinStock, groupSoinStockChamps;
    private CheckBox checkSoinDepuisStock;
    private AutoCompleteTextView spinnerSoinStock;
    private TextInputEditText etQuantiteSoinStock;
    private List<com.mobile.diafarms.network.dto.StockMedicamentResponse> stockMedicaments = new ArrayList<>();
    // Dernier stock connu du serveur (cache ou réseau), avant ajout des saisies en attente.
    private List<com.mobile.diafarms.network.dto.StockMedicamentResponse> stockMedicamentsServeur = new ArrayList<>();
    private String soinStockEnAttente;
    private TextInputEditText etQuantiteSante, etPrixUnitaireSante;
    private boolean majSanteAuto = false;
    // « Cette dépense concerne » (sortie / entrée d'argent hors Santé, ventes diverses) :
    // le projet de l'accueil (poulailler de ce projet facultatif), un site, ou toute la ferme.
    // Voir majConcerne / afficherRattachements.
    private View groupConcerne, tilBatimentTransaction, tilSiteTransaction;
    private TextView tvTitreConcerne, tvConcerneSansProjet;
    private RadioGroup rgConcerne;
    private RadioButton rbConcerneProjet, rbConcerneSite, rbConcerneFerme, rbConcerneAncien;
    // Saisie en attente d'une version antérieure qui ne se ramène à aucun choix unique
    // (plusieurs projets concernés, poulailler seul) : renvoyée telle quelle si gardée.
    private RattachementSaisie rattachementAncien;
    private AutoCompleteTextView spinnerSiteTransaction, spinnerBatimentTransaction;
    private List<SiteSelectResponse> sitesTransaction = new ArrayList<>();
    private List<BatimentSelectResponse> batimentsTransaction = new ArrayList<>();
    // Poulaillers proposés pour « Le Projet » : {uniqueId, nom}, ceux occupés par le projet.
    private final List<String[]> poulaillersProjet = new ArrayList<>();
    private String siteTransactionEnAttente, batimentTransactionEnAttente; // sélection à réappliquer (édition)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_saisie_form);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_saisie_form), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // Edge-to-edge désactive adjustResize : sans l'inset ime(), le clavier recouvre
            // les champs de saisie au lieu de laisser le ScrollView se réduire et défiler.
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, Math.max(systemBars.bottom, ime.bottom));
            return insets;
        });

        type = SaisieType.valueOf(getIntent().getStringExtra(EXTRA_TYPE));
        // Un achat de médicament en attente se modifie dans le formulaire Sortie d'argent.
        if (type == SaisieType.MEDICAMENT_ACHAT) {
            editionMedicament = true;
            type = SaisieType.TRANSACTION_SORTIE;
        }
        projetUniqueId = getIntent().getStringExtra(EXTRA_PROJET_ID);
        projetLabel = getIntent().getStringExtra(EXTRA_PROJET_LABEL);
        editingLocalId = getIntent().getStringExtra(EXTRA_LOCAL_ID);

        localDatabase = new LocalDatabase(this).figee();

        // Compte de démonstration : le serveur refuse toute écriture, rien à saisir ici.
        com.mobile.diafarms.models.User utilisateur = new com.mobile.diafarms.data.SessionManager(this).getCurrentUser();
        if (utilisateur != null && utilisateur.isConsultationSeule()) {
            toast("Compte de démonstration : consultation seulement");
            finish();
            return;
        }

        if (editingLocalId != null) {
            SaisieLocale aModifier = localDatabase.getSaisieById(editingLocalId);
            if (aModifier != null && !aModifier.peutEtreModifiee()) {
                toast(SaisieLocale.STATUT_DEJA_ENREGISTREE.equals(aModifier.getSyncStatus())
                        ? SaisieLocale.MESSAGE_DEJA_ENREGISTREE : SaisieLocale.MESSAGE_ATTENTE_CONFIRMATION);
                finish();
                return;
            }
            if (aModifier != null && aModifier.estIncoherente()) {
                toast(SaisieLocale.MESSAGE_INCOHERENTE);
                finish();
                return;
            }
            // Santé / Vétérinaire « commune » d'une version 1.31 ou antérieure (sans projet) :
            // un soin concerne un projet. Un seul projet concerné = ce projet ; sinon, à refaire.
            if (aModifier != null && type == SaisieType.TRANSACTION_SORTIE && !editionMedicament
                    && (projetUniqueId == null || projetUniqueId.isEmpty())) {
                TransactionCreateRequest ancienne = null;
                try {
                    ancienne = gson.fromJson(aModifier.getPayloadJson(), TransactionCreateRequest.class);
                } catch (Exception ignored) { }
                if (ancienne != null && CATEGORIE_SANTE.equals(ancienne.categorie)) {
                    if (ancienne.projetsConcernesUniqueIds != null && ancienne.projetsConcernesUniqueIds.size() == 1) {
                        projetUniqueId = ancienne.projetsConcernesUniqueIds.get(0);
                        projetLabel = libelleProjet(projetUniqueId);
                    } else {
                        Toast.makeText(this, "Cette saisie doit être supprimée puis saisie de nouveau depuis le projet", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                }
            }
            // Dépense / vente liée à un projet (y compris l'ancien « commune » avec un seul
            // projet concerné) : le formulaire s'ouvre sur ce projet (poulaillers, libellé).
            RattachementSaisie enregistre = aModifier != null ? RattachementSaisie.depuis(aModifier, gson) : null;
            if (enregistre != null && RattachementSaisie.PROJET.equals(enregistre.choix)
                    && enregistre.projetUniqueId != null && !enregistre.projetUniqueId.equals(projetUniqueId)) {
                String libelle = libelleProjet(enregistre.projetUniqueId);
                projetUniqueId = enregistre.projetUniqueId;
                projetLabel = libelle;
            }
        }

        if (type == SaisieType.LIVRAISON_COMMANDE && !chargerCommandeLivree()) {
            finish();
            return;
        }

        bindViews();
        applyTypeVisibility();
        setupDateHeurePickers();
        loadBatiments();
        if (type == SaisieType.SOINS) loadStockMedicaments();
        setupCoherenceChecks();
        if (estTypeTransaction() || estVenteDiverse()) {
            loadRattachementsTransaction();
            // Nouvelle saisie : le projet de l'accueil présélectionné (s'il y en a un).
            rgConcerne.setOnCheckedChangeListener((g, id) -> majConcerne());
            if (editingLocalId == null && projetUniqueId != null && !projetUniqueId.isEmpty()) rbConcerneProjet.setChecked(true);
            majConcerne();
        }

        if (type == SaisieType.ALIMENTATION_CONSOMMATION && projetUniqueId != null) {
            loadStock();
        }
        if (type == SaisieType.REFORME && projetUniqueId != null) {
            loadEffectifReforme();
        }
        if (type == SaisieType.REFORME) {
            loadPointsDeVenteReforme();
        }
        // Vente œufs/réforme (VENTE) : magasin de vente obligatoire — le stock qui
        // plafonne la vente est désormais celui DE CE MAGASIN précis (vrai stock
        // séparé par magasin), plus jamais un stock unique pour toute la ferme comme
        // avant. Voir loadMagasins/loadStockPourMagasinSelectionne. Une commande vise
        // aussi un magasin de vente (destination), même liste.
        if (type == SaisieType.VENTE_OEUFS || type == SaisieType.VENTE_REFORME || type == SaisieType.COMMANDE_CREATE) {
            loadMagasins();
        }
        // Client optionnel sur une vente, obligatoire sur une commande — voir
        // loadClients (uniquement les clients déjà synchronisés côté serveur).
        if (type == SaisieType.VENTE_OEUFS || type == SaisieType.VENTE_REFORME || type == SaisieType.COMMANDE_CREATE
                || type == SaisieType.PAIEMENT_CLIENT) {
            if (type == SaisieType.PAIEMENT_CLIENT && editingLocalId == null) {
                // Lancé depuis une commande : client et commande présélectionnés.
                CommandeResponse c = CommandesHorsLigne.trouver(localDatabase, getIntent().getStringExtra(EXTRA_COMMANDE_ID));
                if (c != null) {
                    pendingClientSelection = c.clientUniqueId;
                    pendingCommandePaiement = c.uniqueId;
                }
            }
            loadClients();
        }
        // Réformes au kilo : estimation du poids depuis la dernière pesée terminée.
        if (type == SaisieType.VENTE_REFORME || type == SaisieType.COMMANDE_CREATE) {
            loadDernierePesee();
        }
        // Bâtiment de stockage obligatoire (Production) : où ces œufs seront
        // physiquement déposés, plafonne les transferts vers un magasin de vente
        // plus tard (MagasinTransfertServiceImpl côté back).
        if (type == SaisieType.COLLECTE_OEUFS) {
            loadMagasinsStockage();
        }
        if (type == SaisieType.SALAIRE_PAYER) {
            loadSalaires();
        }
        if (type == SaisieType.LIVRAISON_COMMANDE) {
            setupLivraison();
        }

        if (editingLocalId != null) {
            prefillFromExisting();
        }

        if (type == SaisieType.COLLECTE_OEUFS) {
            setupResumeCollecteAuto();
        }

        btnValiderForm.setOnClickListener(v -> onValider());
    }

    private void bindViews() {
        tvTitreForm = findViewById(R.id.tvTitreForm);
        tvProjetForm = findViewById(R.id.tvProjetForm);
        groupBatimentTop = findViewById(R.id.groupBatimentTop);
        groupDateHeureTop = findViewById(R.id.groupDateHeureTop);
        spinnerBatiment = findViewById(R.id.spinnerBatiment);
        etDate = findViewById(R.id.etDate);
        etHeure = findViewById(R.id.etHeure);
        btnValiderForm = findViewById(R.id.btnValiderForm);
        ImageButton btnBack = findViewById(R.id.btnBackForm);
        btnBack.setOnClickListener(v -> finish());

        groupCollecte = findViewById(R.id.groupCollecte);
        spinnerBatimentStockage = findViewById(R.id.spinnerBatimentStockage);
        tvStockBatimentStockage = findViewById(R.id.tvStockBatimentStockage);
        spinnerBatimentStockage.setOnItemClickListener((parent, view, position, id) -> loadStockBatimentStockageSelectionne());
        etAlveolesCollectees = findViewById(R.id.etAlveolesCollectees);
        etOeufsCollectes = findViewById(R.id.etOeufsCollectes);
        etOeufsCasses = findViewById(R.id.etOeufsCasses);
        etOeufsNonUtilisables = findViewById(R.id.etOeufsNonUtilisables);
        tvResumeCollecte = findViewById(R.id.tvResumeCollecte);

        groupSoins = findViewById(R.id.groupSoins);
        spinnerTypeSoin = findViewById(R.id.spinnerTypeSoin);
        spinnerTypeSoin.setOnItemClickListener((parent, view, position, id) -> updateGroupSoinsSousType());
        groupSoinsGenerique = findViewById(R.id.groupSoinsGenerique);
        groupSoinStock = findViewById(R.id.groupSoinStock);
        groupSoinStockChamps = findViewById(R.id.groupSoinStockChamps);
        checkSoinDepuisStock = findViewById(R.id.checkSoinDepuisStock);
        spinnerSoinStock = findViewById(R.id.spinnerSoinStock);
        etQuantiteSoinStock = findViewById(R.id.etQuantiteSoinStock);
        checkSoinDepuisStock.setOnCheckedChangeListener((b, c) -> updateGroupSoinsSousType());
        etProduit = findViewById(R.id.etProduit);
        etQuantiteSoin = findViewById(R.id.etQuantiteSoin);
        etObservationsSoin = findViewById(R.id.etObservationsSoin);

        groupVaccination = findViewById(R.id.groupVaccination);
        etNomVaccin = findViewById(R.id.etNomVaccin);
        etQuantiteVaccin = findViewById(R.id.etQuantiteVaccin);
        cbModeOral = findViewById(R.id.cbModeOral);
        cbModeInjection = findViewById(R.id.cbModeInjection);
        cbModePulverisation = findViewById(R.id.cbModePulverisation);
        cbModeTopique = findViewById(R.id.cbModeTopique);

        groupEntretien = findViewById(R.id.groupEntretien);
        spinnerNiveauEntretien = findViewById(R.id.spinnerNiveauEntretien);
        groupEntretienBatiment = findViewById(R.id.groupEntretienBatiment);
        spinnerBatimentEntretien = findViewById(R.id.spinnerBatimentEntretien);
        spinnerTypeEntretien = findViewById(R.id.spinnerTypeEntretien);
        etDescriptionEntretien = findViewById(R.id.etDescriptionEntretien);
        etObservationsEntretien = findViewById(R.id.etObservationsEntretien);
        spinnerNiveauEntretien.setOnItemClickListener((parent, view, position, id) -> updateGroupEntretienNiveau());

        groupMortalite = findViewById(R.id.groupMortalite);
        etNombreMorts = findViewById(R.id.etNombreMorts);
        etCauseMortalite = findViewById(R.id.etCauseMortalite);

        groupReforme = findViewById(R.id.groupReforme);
        tvEffectifReformeInfo = findViewById(R.id.tvEffectifReformeInfo);
        etNombreSujetsReforme = findViewById(R.id.etNombreSujetsReforme);
        etCauseReforme = findViewById(R.id.etCauseReforme);
        tilPointDeVenteReforme = findViewById(R.id.tilPointDeVenteReforme);
        spinnerPointDeVenteReforme = findViewById(R.id.spinnerPointDeVenteReforme);

        groupAlimentationAchat = findViewById(R.id.groupAlimentationAchat);
        groupAlimentationAchatFin = findViewById(R.id.groupAlimentationAchatFin);
        groupTransactionDetails = findViewById(R.id.groupTransactionDetails);
        spinnerTypeAliment = findViewById(R.id.spinnerTypeAliment);
        spinnerTypeAliment.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, TYPES_ALIMENT));
        etSac = findViewById(R.id.etSac);
        etPoidsSac = findViewById(R.id.etPoidsSac);
        etPoidsSac.setText(formatSaisie(POIDS_SAC_DEFAUT_KG));
        etQuantiteKgAchat = findViewById(R.id.etQuantiteKgAchat);
        etFournisseurAchat = findViewById(R.id.etFournisseurAchat);
        etObservationsAchat = findViewById(R.id.etObservationsAchat);
        android.text.TextWatcher recalculQuantite = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void afterTextChanged(android.text.Editable e) { majQuantiteAchatCalculee(); }
        };
        etSac.addTextChangedListener(recalculQuantite);
        etPoidsSac.addTextChangedListener(recalculQuantite);
        etQuantiteKgAchat.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void afterTextChanged(android.text.Editable e) {
                if (majQuantiteAchatAuto) return;
                // Vidée à la main : le calcul automatique reprend.
                quantiteAchatManuelle = e.length() > 0;
            }
        });

        groupConsommation = findViewById(R.id.groupConsommation);
        tvStockInfo = findViewById(R.id.tvStockInfo);
        etQuantiteKgConso = findViewById(R.id.etQuantiteKgConso);

        groupVenteOeufs = findViewById(R.id.groupVenteOeufs);
        spinnerMagasinOeufs = findViewById(R.id.spinnerMagasinOeufs);
        spinnerMagasinOeufs.setOnItemClickListener((parent, view, position, id) -> loadStockPourMagasinSelectionne());
        spinnerClientVenteOeufs = findViewById(R.id.spinnerClientVenteOeufs);
        spinnerClientVenteOeufs.setOnItemClickListener((parent, view, position, id) -> refreshModePaiementVenteOeufs());
        tilMontantRapporteVenteOeufs = findViewById(R.id.tilMontantRapporteVenteOeufs);
        tilModePaiementVenteOeufs = findViewById(R.id.tilModePaiementVenteOeufs);
        spinnerModePaiementVenteOeufs = findViewById(R.id.spinnerModePaiementVenteOeufs);
        tvStockOeufsInfo = findViewById(R.id.tvStockOeufsInfo);
        radioGroupTypeOeufVente = findViewById(R.id.radioGroupTypeOeufVente);
        radioGroupTypeOeufVente.setOnCheckedChangeListener((group, checkedId) -> refreshStockOeufsAffiche());
        radioGroupUniteVenteOeufs = findViewById(R.id.radioGroupUniteVenteOeufs);
        tilQuantiteOeufsVente = findViewById(R.id.tilQuantiteOeufsVente);
        tilPrixUnitaireOeufs = findViewById(R.id.tilPrixUnitaireOeufs);
        etQuantiteOeufsVente = findViewById(R.id.etQuantiteOeufsVente);
        etPrixUnitaireOeufs = findViewById(R.id.etPrixUnitaireOeufs);
        etMontantVenteOeufs = findViewById(R.id.etMontantVenteOeufs);
        etMontantRapporteVenteOeufs = findViewById(R.id.etMontantRapporteVenteOeufs);
        radioGroupUniteVenteOeufs.setOnCheckedChangeListener((group, checkedId) -> {
            boolean enAlveoles = isVenteOeufsEnAlveoles();
            tilQuantiteOeufsVente.setHint(enAlveoles ? "Nombre d'alvéoles vendues" : "Nombre d'œufs vendus");
            tilPrixUnitaireOeufs.setHint(enAlveoles ? "Prix par alvéole (FCFA)" : "Prix unitaire (FCFA)");
            recalculerMontantVenteOeufs();
            refreshCoherence();
        });
        // Montant théorique jamais saisi à la main : toujours quantité × prix unitaire
        // (voir recalculerMontantVenteOeufs) — un rabais se reflète dans le montant
        // rapporté, pas ici (même principe que CreateVenteOeufsDialog côté web).
        etMontantVenteOeufs.setEnabled(false);
        TextWatcher venteOeufsWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { recalculerMontantVenteOeufs(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        etQuantiteOeufsVente.addTextChangedListener(venteOeufsWatcher);
        etPrixUnitaireOeufs.addTextChangedListener(venteOeufsWatcher);
        etMontantRapporteVenteOeufs.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!syncingMontantRapporte) montantRapporteOeufsModifieManuel = true;
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        groupVenteReforme = findViewById(R.id.groupVenteReforme);
        spinnerMagasinReforme = findViewById(R.id.spinnerMagasinReforme);
        spinnerMagasinReforme.setOnItemClickListener((parent, view, position, id) -> loadStockPourMagasinSelectionne());
        spinnerClientVenteReforme = findViewById(R.id.spinnerClientVenteReforme);
        spinnerClientVenteReforme.setOnItemClickListener((parent, view, position, id) -> refreshModePaiementVenteReforme());
        tilMontantRapporteVenteReforme = findViewById(R.id.tilMontantRapporteVenteReforme);
        tilModePaiementVenteReforme = findViewById(R.id.tilModePaiementVenteReforme);
        spinnerModePaiementVenteReforme = findViewById(R.id.spinnerModePaiementVenteReforme);
        tvStockReformeInfo = findViewById(R.id.tvStockReformeInfo);
        etNombreSujetsVente = findViewById(R.id.etNombreSujetsVente);
        radioGroupTypeVenteReforme = findViewById(R.id.radioGroupTypeVenteReforme);
        tilPoidsTotalReforme = findViewById(R.id.tilPoidsTotalReforme);
        spacerPoidsTotalReforme = findViewById(R.id.spacerPoidsTotalReforme);
        etPoidsTotalReforme = findViewById(R.id.etPoidsTotalReforme);
        tilPrixUnitaireReforme = findViewById(R.id.tilPrixUnitaireReforme);
        etPrixUnitaireReforme = findViewById(R.id.etPrixUnitaireReforme);
        etMontantVenteReforme = findViewById(R.id.etMontantVenteReforme);
        etMontantRapporteVenteReforme = findViewById(R.id.etMontantRapporteVenteReforme);
        // Même principe que Vente d'œufs juste au-dessus : montant théorique toujours
        // calculé, jamais saisi à la main.
        etMontantVenteReforme.setEnabled(false);
        radioGroupTypeVenteReforme.setOnCheckedChangeListener((group, checkedId) -> {
            refreshTypeVenteReformeUi();
            recalculerMontantVenteReforme();
        });
        TextWatcher venteReformeWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { recalculerMontantVenteReforme(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        groupEstimationPeseeReforme = findViewById(R.id.groupEstimationPeseeReforme);
        tvEstimationPeseeReforme = findViewById(R.id.tvEstimationPeseeReforme);
        btnUtiliserEstimationReforme = findViewById(R.id.btnUtiliserEstimationReforme);
        tvPoidsMoyenReforme = findViewById(R.id.tvPoidsMoyenReforme);
        btnUtiliserEstimationReforme.setOnClickListener(v ->
                utiliserEstimation(etNombreSujetsVente, etPoidsTotalReforme));
        etNombreSujetsVente.addTextChangedListener(venteReformeWatcher);
        etPoidsTotalReforme.addTextChangedListener(venteReformeWatcher);
        etPrixUnitaireReforme.addTextChangedListener(venteReformeWatcher);
        etMontantRapporteVenteReforme.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!syncingMontantRapporte) montantRapporteReformeModifieManuel = true;
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        groupClient = findViewById(R.id.groupClient);
        etClientNom = findViewById(R.id.etClientNom);
        etClientTelephone = findViewById(R.id.etClientTelephone);
        etClientAdresse = findViewById(R.id.etClientAdresse);
        etClientEmail = findViewById(R.id.etClientEmail);

        groupCommande = findViewById(R.id.groupCommande);
        spinnerClientCommande = findViewById(R.id.spinnerClientCommande);
        tvAucunClientCommande = findViewById(R.id.tvAucunClientCommande);
        spinnerMagasinCommande = findViewById(R.id.spinnerMagasinCommande);
        radioGroupTypeCommande = findViewById(R.id.radioGroupTypeCommande);
        groupUniteCommande = findViewById(R.id.groupUniteCommande);
        radioGroupUniteCommande = findViewById(R.id.radioGroupUniteCommande);
        tilQuantiteCommande = findViewById(R.id.tilQuantiteCommande);
        tilPrixUnitaireCommande = findViewById(R.id.tilPrixUnitaireCommande);
        etQuantiteCommande = findViewById(R.id.etQuantiteCommande);
        etPrixUnitaireCommande = findViewById(R.id.etPrixUnitaireCommande);
        etMontantEstimeCommande = findViewById(R.id.etMontantEstimeCommande);
        groupTarificationCommande = findViewById(R.id.groupTarificationCommande);
        radioGroupTarificationCommande = findViewById(R.id.radioGroupTarificationCommande);
        groupKiloCommande = findViewById(R.id.groupKiloCommande);
        spacerPrixUnitaireCommande = findViewById(R.id.spacerPrixUnitaireCommande);
        etPrixKgCommande = findViewById(R.id.etPrixKgCommande);
        etPoidsEstimeCommande = findViewById(R.id.etPoidsEstimeCommande);
        tilMontantEstimeCommande = findViewById(R.id.tilMontantEstimeCommande);
        groupEstimationPeseeCommande = findViewById(R.id.groupEstimationPeseeCommande);
        tvEstimationPeseeCommande = findViewById(R.id.tvEstimationPeseeCommande);
        btnUtiliserEstimationCommande = findViewById(R.id.btnUtiliserEstimationCommande);
        btnUtiliserEstimationCommande.setOnClickListener(v ->
                utiliserEstimation(etQuantiteCommande, etPoidsEstimeCommande));
        radioGroupTypeCommande.setOnCheckedChangeListener((group, checkedId) -> {
            boolean estReforme = checkedId == R.id.radioCommandeReforme;
            groupUniteCommande.setVisibility(estReforme ? View.GONE : View.VISIBLE);
            applyLabelsQuantiteCommande();
            refreshTarificationCommandeUi();
            recalculerMontantEstimeCommande();
        });
        radioGroupTarificationCommande.setOnCheckedChangeListener((group, checkedId) -> {
            refreshTarificationCommandeUi();
            recalculerMontantEstimeCommande();
        });
        radioGroupUniteCommande.setOnCheckedChangeListener((group, checkedId) -> {
            applyLabelsQuantiteCommande();
            recalculerMontantEstimeCommande();
        });
        applyLabelsQuantiteCommande();
        etAcompteCommande = findViewById(R.id.etAcompteCommande);
        tilModePaiementCommande = findViewById(R.id.tilModePaiementCommande);
        spinnerModePaiementCommande = findViewById(R.id.spinnerModePaiementCommande);
        etAcompteCommande.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refreshModePaiementCommande(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        etDateLivraisonCommande = findViewById(R.id.etDateLivraisonCommande);
        etDateLivraisonCommande.setHint("Aucune");
        etDateLivraisonCommande.setOnClickListener(v ->
                // Pas de maxAujourdhui ici (contrairement à etDate) : une livraison
                // prévue est par nature une date future, jamais bornée à aujourd'hui.
                showMaterialDatePicker(dateLivraisonCal, false, chosen -> {
                    dateLivraisonCal.setTimeInMillis(chosen.getTimeInMillis());
                    etDateLivraisonCommande.setText(isoDate.format(dateLivraisonCal.getTime()));
                }));
        TextWatcher commandeWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { recalculerMontantEstimeCommande(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        etQuantiteCommande.addTextChangedListener(commandeWatcher);
        etPrixUnitaireCommande.addTextChangedListener(commandeWatcher);
        etPrixKgCommande.addTextChangedListener(commandeWatcher);
        etPoidsEstimeCommande.addTextChangedListener(commandeWatcher);

        groupSalaire = findViewById(R.id.groupSalaire);
        spinnerEmployeSalaire = findViewById(R.id.spinnerEmployeSalaire);
        tvAucunSalaireEmploye = findViewById(R.id.tvAucunSalaireEmploye);
        spinnerMoisSalaire = findViewById(R.id.spinnerMoisSalaire);
        spinnerAnneeSalaire = findViewById(R.id.spinnerAnneeSalaire);
        tilQuantiteSalaire = findViewById(R.id.tilQuantiteSalaire);
        etQuantiteSalaire = findViewById(R.id.etQuantiteSalaire);
        tvTauxInfoSalaire = findViewById(R.id.tvTauxInfoSalaire);
        etMontantSalaire = findViewById(R.id.etMontantSalaire);
        etDescriptionSalaire = findViewById(R.id.etDescriptionSalaire);
        setupSalaireSpinners();
        spinnerEmployeSalaire.setOnItemClickListener((parent, view, position, id) -> applySalaireEmployeSelectionne());
        TextWatcher quantiteSalaireWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { recalculerMontantSalaire(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        etQuantiteSalaire.addTextChangedListener(quantiteSalaireWatcher);

        groupVenteDiverse = findViewById(R.id.groupVenteDiverse);
        groupSacsVenteDiverse = findViewById(R.id.groupSacsVenteDiverse);
        tilDescriptionVenteDiverse = findViewById(R.id.tilDescriptionVenteDiverse);
        etQuantiteVenteDiverse = findViewById(R.id.etQuantiteVenteDiverse);
        etPrixUnitaireVenteDiverse = findViewById(R.id.etPrixUnitaireVenteDiverse);
        etMontantVenteDiverse = findViewById(R.id.etMontantVenteDiverse);
        etDescriptionVenteDiverse = findViewById(R.id.etDescriptionVenteDiverse);
        // Fientes : montant = sacs x prix d'un sac, arrondi au franc (comme le serveur et le
        // web) ; reste modifiable (prix négocié), seul le montant est obligatoire.
        android.text.TextWatcher versMontantVenteDiverse = apresSaisie(() -> {
            if (majVenteDiverseAuto) return;
            Double q = parseDoubleOrNull(etQuantiteVenteDiverse.getText());
            Double pu = parseDoubleOrNull(etPrixUnitaireVenteDiverse.getText());
            if (q != null && q > 0 && pu != null && pu > 0) {
                majVenteDiverseAuto = true;
                etMontantVenteDiverse.setText(String.valueOf(Math.round(q * pu)));
                majVenteDiverseAuto = false;
            }
        });
        etQuantiteVenteDiverse.addTextChangedListener(versMontantVenteDiverse);
        etPrixUnitaireVenteDiverse.addTextChangedListener(versMontantVenteDiverse);

        tilOeufsSuppLivraison = findViewById(R.id.tilOeufsSuppLivraison);
        etOeufsSuppLivraison = findViewById(R.id.etOeufsSuppLivraison);

        groupLivraison = findViewById(R.id.groupLivraison);
        groupUniteLivraison = findViewById(R.id.groupUniteLivraison);
        groupKiloLivraison = findViewById(R.id.groupKiloLivraison);
        tvLivraisonCommande = findViewById(R.id.tvLivraisonCommande);
        tvStockLivraison = findViewById(R.id.tvStockLivraison);
        tvMontantLivraison = findViewById(R.id.tvMontantLivraison);
        radioGroupUniteLivraison = findViewById(R.id.radioGroupUniteLivraison);
        tilQuantiteLivraison = findViewById(R.id.tilQuantiteLivraison);
        tilModePaiementLivraison = findViewById(R.id.tilModePaiementLivraison);
        etQuantiteLivraison = findViewById(R.id.etQuantiteLivraison);
        etPoidsLivraison = findViewById(R.id.etPoidsLivraison);
        etPrixKgLivraison = findViewById(R.id.etPrixKgLivraison);
        etMontantRecuLivraison = findViewById(R.id.etMontantRecuLivraison);
        spinnerModePaiementLivraison = findViewById(R.id.spinnerModePaiementLivraison);

        groupPaiementClient = findViewById(R.id.groupPaiementClient);
        spinnerClientPaiement = findViewById(R.id.spinnerClientPaiement);
        spinnerCommandePaiement = findViewById(R.id.spinnerCommandePaiement);
        spinnerModePaiementClient = findViewById(R.id.spinnerModePaiementClient);
        tvAucunClientPaiement = findViewById(R.id.tvAucunClientPaiement);
        tvSoldeClient = findViewById(R.id.tvSoldeClient);
        etMontantPaiement = findViewById(R.id.etMontantPaiement);
        etObservationsPaiement = findViewById(R.id.etObservationsPaiement);
        // Mode obligatoire et jamais présélectionné : l'utilisateur doit dire comment il a
        // été payé (espèces, Orange Money...).
        spinnerModePaiementClient.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, MODE_PAIEMENT_LABELS));
        spinnerClientPaiement.setOnItemClickListener((parent, view, position, id) -> surClientPaiementChoisi(true));

        groupTransaction = findViewById(R.id.groupTransaction);
        spinnerCategorie = findViewById(R.id.spinnerCategorie);
        etMontant = findViewById(R.id.etMontant);
        etDescriptionTransaction = findViewById(R.id.etDescriptionTransaction);
        groupSanteQuantite = findViewById(R.id.groupSanteQuantite);
        groupNatureSante = findViewById(R.id.groupNatureSante);
        groupMedicamentAchat = findViewById(R.id.groupMedicamentAchat);
        spinnerNatureSante = findViewById(R.id.spinnerNatureSante);
        spinnerFormeMedicament = findViewById(R.id.spinnerFormeMedicament);
        spinnerUniteMedicament = findViewById(R.id.spinnerUniteMedicament);
        etNomMedicament = findViewById(R.id.etNomMedicament);
        etFournisseurMedicament = findViewById(R.id.etFournisseurMedicament);
        // En modification, une saisie garde son type : un achat de médicament (MEDICAMENT_ACHAT)
        // reste un achat, une sortie d'argent (TRANSACTION_SORTIE) ne peut pas en devenir un.
        String[] natures = editingLocalId == null ? NATURES_SANTE
                : editionMedicament ? new String[]{NATURE_MEDICAMENT} : new String[]{NATURE_SERVICE};
        spinnerNatureSante.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, natures));
        spinnerNatureSante.setOnItemClickListener((parent, view, position, id) -> appliquerSante());
        spinnerFormeMedicament.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, FORMES_MEDICAMENT));
        spinnerFormeMedicament.setOnItemClickListener((parent, view, position, id) -> majUnitesMedicament(true));
        if (editingLocalId != null) spinnerNatureSante.setEnabled(false);
        if (editionMedicament) spinnerCategorie.setEnabled(false);
        etQuantiteSante = findViewById(R.id.etQuantiteSante);
        etPrixUnitaireSante = findViewById(R.id.etPrixUnitaireSante);
        // Montant total <-> prix unitaire, à partir de la quantité (Santé / Vétérinaire).
        etMontant.addTextChangedListener(apresSaisie(() -> {
            if (majSanteAuto || !estSante()) return;
            Double q = parseDoubleOrNull(etQuantiteSante.getText());
            Double m = parseDoubleOrNull(etMontant.getText());
            if (q != null && q > 0 && m != null && m > 0) {
                majSanteAuto = true;
                etPrixUnitaireSante.setText(formatSaisie(Math.round(m / q * 100.0) / 100.0));
                majSanteAuto = false;
            }
        }));
        android.text.TextWatcher versMontant = apresSaisie(() -> {
            if (majSanteAuto || !estSante()) return;
            Double q = parseDoubleOrNull(etQuantiteSante.getText());
            Double pu = parseDoubleOrNull(etPrixUnitaireSante.getText());
            if (q != null && q > 0 && pu != null && pu > 0) {
                majSanteAuto = true;
                etMontant.setText(formatSaisie((double) Math.round(q * pu)));
                majSanteAuto = false;
            }
        });
        etQuantiteSante.addTextChangedListener(versMontant);
        etPrixUnitaireSante.addTextChangedListener(versMontant);
        groupConcerne = findViewById(R.id.groupConcerne);
        tvTitreConcerne = findViewById(R.id.tvTitreConcerne);
        tvConcerneSansProjet = findViewById(R.id.tvConcerneSansProjet);
        rgConcerne = findViewById(R.id.rgConcerne);
        rbConcerneProjet = findViewById(R.id.rbConcerneProjet);
        rbConcerneSite = findViewById(R.id.rbConcerneSite);
        rbConcerneFerme = findViewById(R.id.rbConcerneFerme);
        rbConcerneAncien = findViewById(R.id.rbConcerneAncien);
        tilBatimentTransaction = findViewById(R.id.tilBatimentTransaction);
        tilSiteTransaction = findViewById(R.id.tilSiteTransaction);
        spinnerSiteTransaction = findViewById(R.id.spinnerSiteTransaction);
        spinnerBatimentTransaction = findViewById(R.id.spinnerBatimentTransaction);

        tilCategoriePrecision = findViewById(R.id.tilCategoriePrecision);
        etCategoriePrecision = findViewById(R.id.etCategoriePrecision);
        String[] categories = categoriesPourType();
        ArrayAdapter<String> categorieAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, categories);
        spinnerCategorie.setAdapter(categorieAdapter);
        // Aucune catégorie présélectionnée (comme le web) : elle est obligatoire et choisie
        // volontairement, sinon une dépense partait en « Logistique » sans que personne
        // ne l'ait décidé. En modification, prefillFromExisting remet la catégorie
        // enregistrée ; un achat de médicament garde « Santé / Vétérinaire ».
        if (editingLocalId != null && categories.length == 1) spinnerCategorie.setText(categories[0], false);
        spinnerCategorie.setOnItemClickListener((parent, view, position, id) -> {
            basculerAchatAliment();
            appliquerSante();
            majCategoriePrecision();
        });

        ArrayAdapter<String> typeSoinAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, TYPES_SOIN);
        spinnerTypeSoin.setAdapter(typeSoinAdapter);
        spinnerTypeSoin.setText(TYPES_SOIN[0], false);
        updateGroupSoinsSousType();

        ArrayAdapter<String> niveauEntretienAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, NIVEAUX_ENTRETIEN);
        spinnerNiveauEntretien.setAdapter(niveauEntretienAdapter);
        spinnerNiveauEntretien.setText(NIVEAUX_ENTRETIEN[0], false);
        ArrayAdapter<String> typeEntretienAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, TYPES_ENTRETIEN);
        spinnerTypeEntretien.setAdapter(typeEntretienAdapter);
        spinnerTypeEntretien.setText(TYPES_ENTRETIEN[0], false);
        updateGroupEntretienNiveau();

        // Mode de paiement — liste statique (miroir de l'enum ModePaiement côté back),
        // Espèces par défaut. Visibilité/pertinence pilotées séparément (voir
        // refreshModePaiementVenteOeufs/Reforme/Commande) : un adaptateur distinct par
        // sélecteur, même si la liste est identique, pour éviter de partager l'état de
        // filtre d'un ArrayAdapter entre plusieurs AutoCompleteTextView actifs en même
        // temps (VenteOeufs/VenteReforme + Commande ne sont jamais affichés ensemble,
        // mais autant rester prudent).
        spinnerModePaiementVenteOeufs.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, MODE_PAIEMENT_LABELS));
        spinnerModePaiementVenteReforme.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, MODE_PAIEMENT_LABELS));
        spinnerModePaiementCommande.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, MODE_PAIEMENT_LABELS));
        spinnerModePaiementVenteOeufs.setText(MODE_PAIEMENT_LABELS[0], false);
        spinnerModePaiementVenteReforme.setText(MODE_PAIEMENT_LABELS[0], false);
        spinnerModePaiementCommande.setText(MODE_PAIEMENT_LABELS[0], false);
    }

    private boolean isTypeSoinVaccination() {
        return TYPES_SOIN[0].equalsIgnoreCase(spinnerTypeSoin.getText().toString());
    }

    /** Bascule quel sous-groupe de champs de la saisie Soins est affiché selon le
     * type choisi dans spinnerTypeSoin — appelé au changement de sélection, à
     * l'ouverture du formulaire (applyTypeVisibility) et en édition
     * (prefillFromExisting), une fois le spinner positionné sur la bonne valeur. */
    private void updateGroupSoinsSousType() {
        boolean vaccination = isTypeSoinVaccination();
        boolean stockDispo = groupSoinStock != null && !stockMedicaments.isEmpty();
        if (groupSoinStock != null) groupSoinStock.setVisibility(stockDispo ? View.VISIBLE : View.GONE);
        boolean depuisStock = stockDispo && checkSoinDepuisStock.isChecked();
        if (groupSoinStockChamps != null) groupSoinStockChamps.setVisibility(depuisStock ? View.VISIBLE : View.GONE);
        groupSoinsGenerique.setVisibility(!depuisStock && !vaccination ? View.VISIBLE : View.GONE);
        groupVaccination.setVisibility(!depuisStock && vaccination ? View.VISIBLE : View.GONE);
    }

    private boolean soinDepuisStock() {
        return type == SaisieType.SOINS && groupSoinStock != null && groupSoinStock.getVisibility() == View.VISIBLE
                && checkSoinDepuisStock.isChecked();
    }

    /** Stock de médicaments du projet : cache d'abord, puis réseau si possible. */
    private void loadStockMedicaments() {
        if (projetUniqueId == null || projetUniqueId.isEmpty()) return;
        String cle = CachePrefetcher.CACHE_STOCK_MEDICAMENTS_PREFIX + projetUniqueId;
        appliquerStockMedicaments(lireListeCache(cle, com.mobile.diafarms.network.dto.StockMedicamentResponse.class), true);
        ApiClient.dataApi(this).getStockMedicaments(projetUniqueId).enqueue(new Callback<ApiEnvelope<List<com.mobile.diafarms.network.dto.StockMedicamentResponse>>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<List<com.mobile.diafarms.network.dto.StockMedicamentResponse>>> call,
                                   Response<ApiEnvelope<List<com.mobile.diafarms.network.dto.StockMedicamentResponse>>> response) {
                List<com.mobile.diafarms.network.dto.StockMedicamentResponse> liste = response.isSuccessful() && response.body() != null
                        ? response.body().getData() : null;
                if (liste != null) {
                    localDatabase.putCache(cle, gson.toJson(liste));
                    appliquerStockMedicaments(liste, false);
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<List<com.mobile.diafarms.network.dto.StockMedicamentResponse>>> call, Throwable t) { }
        });
    }

    private void appliquerStockMedicaments(List<com.mobile.diafarms.network.dto.StockMedicamentResponse> liste, boolean premierAffichage) {
        boolean avaitStock = !stockMedicaments.isEmpty();
        stockMedicamentsServeur = liste != null ? liste : new ArrayList<>();
        stockMedicaments = avecSaisiesEnAttente(stockMedicamentsServeur);
        List<String> labels = new ArrayList<>();
        for (com.mobile.diafarms.network.dto.StockMedicamentResponse m : stockMedicaments) labels.add(libelleStock(m));
        String avant = spinnerSoinStock.getText().toString();
        spinnerSoinStock.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, labels));
        if (labels.contains(avant)) spinnerSoinStock.setText(avant, false);
        // Nouvelle saisie : cochée par défaut dès qu'un médicament du projet a du stock.
        if (editingLocalId == null && !avaitStock && !stockMedicaments.isEmpty()) {
            boolean restant = false;
            for (com.mobile.diafarms.network.dto.StockMedicamentResponse m : stockMedicaments) if (m.restant > 0) restant = true;
            checkSoinDepuisStock.setChecked(restant);
        }
        if (soinStockEnAttente != null) {
            for (com.mobile.diafarms.network.dto.StockMedicamentResponse m : stockMedicaments) {
                if (soinStockEnAttente.equals(cleMedicament(m.nom, m.unite))) {
                    spinnerSoinStock.setText(libelleStock(m), false);
                    soinStockEnAttente = null;
                    break;
                }
            }
        }
        if (type == SaisieType.SOINS) updateGroupSoinsSousType();
    }

    /** Même clé que MedicamentService.cle côté serveur : nom|unité, sans casse ni espaces autour. */
    private static String cleMedicament(String nom, String unite) {
        return (nom == null ? "" : nom.trim().toLowerCase(Locale.FRENCH)) + "|" + (unite == null ? "" : unite.trim().toLowerCase(Locale.FRENCH));
    }

    /** Stock hors ligne : stock serveur + achats de médicaments en attente du projet
     * - soins pris dans le stock en attente (la saisie en cours de modification exclue).
     * Un médicament acheté hors ligne apparaît donc dans la liste. */
    private List<com.mobile.diafarms.network.dto.StockMedicamentResponse> avecSaisiesEnAttente(
            List<com.mobile.diafarms.network.dto.StockMedicamentResponse> serveur) {
        java.util.LinkedHashMap<String, com.mobile.diafarms.network.dto.StockMedicamentResponse> parCle = new java.util.LinkedHashMap<>();
        for (com.mobile.diafarms.network.dto.StockMedicamentResponse m : serveur) {
            com.mobile.diafarms.network.dto.StockMedicamentResponse c = new com.mobile.diafarms.network.dto.StockMedicamentResponse();
            c.nom = m.nom;
            c.forme = m.forme;
            c.unite = m.unite;
            c.achete = m.achete;
            c.utilise = m.utilise;
            c.restant = m.restant;
            parCle.put(cleMedicament(m.nom, m.unite), c);
        }
        for (SaisieLocale s : saisiesEnAttente(SaisieType.MEDICAMENT_ACHAT)) {
            if (!projetUniqueId.equals(s.getProjetUniqueId()) || s.estIncoherente()) continue;
            com.mobile.diafarms.network.dto.AchatMedicamentCreateRequest a;
            try {
                a = gson.fromJson(s.getPayloadJson(), com.mobile.diafarms.network.dto.AchatMedicamentCreateRequest.class);
            } catch (Exception e) {
                continue;
            }
            if (a == null || a.nom == null || a.unite == null || a.quantite == null || a.quantite <= 0) continue;
            String k = cleMedicament(a.nom, a.unite);
            com.mobile.diafarms.network.dto.StockMedicamentResponse l = parCle.get(k);
            if (l == null) {
                l = new com.mobile.diafarms.network.dto.StockMedicamentResponse();
                l.nom = a.nom.trim();
                l.forme = a.forme;
                l.unite = a.unite.trim();
                parCle.put(k, l);
            }
            l.achete += a.quantite;
            l.restant += a.quantite;
        }
        for (SaisieLocale s : saisiesEnAttente(SaisieType.SOINS)) {
            SoinsCreateRequest r;
            try {
                r = gson.fromJson(s.getPayloadJson(), SoinsCreateRequest.class);
            } catch (Exception e) {
                continue;
            }
            if (r == null || !Boolean.TRUE.equals(r.depuisStock) || r.quantite == null) continue;
            String projetSoin = r.projetUniqueId != null ? r.projetUniqueId : s.getProjetUniqueId();
            if (!projetUniqueId.equals(projetSoin)) continue;
            com.mobile.diafarms.network.dto.StockMedicamentResponse l = parCle.get(cleMedicament(r.produit, r.unite));
            if (l == null) continue;
            l.utilise += r.quantite;
            l.restant -= r.quantite;
        }
        List<com.mobile.diafarms.network.dto.StockMedicamentResponse> res = new ArrayList<>(parCle.values());
        for (com.mobile.diafarms.network.dto.StockMedicamentResponse m : res) m.restant = Math.max(0, m.restant);
        return res;
    }

    private static String libelleStock(com.mobile.diafarms.network.dto.StockMedicamentResponse m) {
        return m.nom + " : reste " + formatSaisie(m.restant) + " " + m.unite;
    }

    private com.mobile.diafarms.network.dto.StockMedicamentResponse stockChoisi() {
        String choisi = spinnerSoinStock.getText().toString();
        for (com.mobile.diafarms.network.dto.StockMedicamentResponse m : stockMedicaments) if (libelleStock(m).equals(choisi)) return m;
        return null;
    }

    /** Achat de médicament : unités proposées selon la forme. */
    private void majUnitesMedicament(boolean choisirPremiere) {
        int i = java.util.Arrays.asList(FORMES_MEDICAMENT).indexOf(spinnerFormeMedicament.getText().toString());
        String[] unites = i >= 0 ? UNITES_MEDICAMENT[i] : new String[0];
        spinnerUniteMedicament.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, unites));
        if (choisirPremiere && unites.length > 0) spinnerUniteMedicament.setText(unites[0], false);
    }

    private boolean estMedicamentSante() {
        return estSante() && NATURE_MEDICAMENT.equals(spinnerNatureSante.getText().toString());
    }

    private boolean isNiveauEntretienBatiment() {
        return NIVEAUX_ENTRETIEN[0].equalsIgnoreCase(spinnerNiveauEntretien.getText().toString());
    }

    /** Bascule le champ poulailler/type selon le niveau choisi — un niveau "Site"
     * n'a ni poulailler ni type précis (toujours "Autres travaux", voir onValider). */
    private void updateGroupEntretienNiveau() {
        boolean batiment = isNiveauEntretienBatiment();
        groupEntretienBatiment.setVisibility(batiment ? View.VISIBLE : View.GONE);
        if (batiment && batimentsEntretien.isEmpty()) loadBatimentsEntretien();
    }

    private String entretienNiveauToWire(String label) {
        for (int i = 0; i < NIVEAUX_ENTRETIEN.length; i++) {
            if (NIVEAUX_ENTRETIEN[i].equalsIgnoreCase(label)) return NIVEAUX_ENTRETIEN_WIRE[i];
        }
        return NIVEAUX_ENTRETIEN_WIRE[0];
    }

    private String entretienNiveauFromWire(String wire) {
        for (int i = 0; i < NIVEAUX_ENTRETIEN_WIRE.length; i++) {
            if (NIVEAUX_ENTRETIEN_WIRE[i].equalsIgnoreCase(wire)) return NIVEAUX_ENTRETIEN[i];
        }
        return NIVEAUX_ENTRETIEN[0];
    }

    private String entretienTypeToWire(String label) {
        for (int i = 0; i < TYPES_ENTRETIEN.length; i++) {
            if (TYPES_ENTRETIEN[i].equalsIgnoreCase(label)) return TYPES_ENTRETIEN_WIRE[i];
        }
        return TYPES_ENTRETIEN_WIRE[2]; // AUTRE
    }

    private String entretienTypeFromWire(String wire) {
        for (int i = 0; i < TYPES_ENTRETIEN_WIRE.length; i++) {
            if (TYPES_ENTRETIEN_WIRE[i].equalsIgnoreCase(wire)) return TYPES_ENTRETIEN[i];
        }
        return TYPES_ENTRETIEN[2];
    }

    /** TOUS les poulaillers de la ferme (pas seulement ceux occupés par le projet
     * sélectionné) — même principe cache-puis-réseau que loadBatiments(), voir
     * CachePrefetcher.prefetchBatiments (déjà préchargé au démarrage/connexion). */
    private void loadBatimentsEntretien() {
        String cached = localDatabase.getCache(CachePrefetcher.CACHE_BATIMENTS_SELECT);
        if (cached != null) {
            com.mobile.diafarms.network.dto.BatimentSelectResponse[] arr =
                    gson.fromJson(cached, com.mobile.diafarms.network.dto.BatimentSelectResponse[].class);
            batimentsEntretien = new ArrayList<>(java.util.Arrays.asList(arr));
            populateBatimentEntretienSpinner();
        }

        ApiClient.dataApi(this).getBatimentsSelect().enqueue(new Callback<ApiEnvelope<List<com.mobile.diafarms.network.dto.BatimentSelectResponse>>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<List<com.mobile.diafarms.network.dto.BatimentSelectResponse>>> call,
                                    Response<ApiEnvelope<List<com.mobile.diafarms.network.dto.BatimentSelectResponse>>> response) {
                List<com.mobile.diafarms.network.dto.BatimentSelectResponse> data =
                        response.isSuccessful() && response.body() != null ? response.body().getData() : null;
                if (data != null) {
                    localDatabase.putCache(CachePrefetcher.CACHE_BATIMENTS_SELECT, gson.toJson(data));
                    batimentsEntretien = data;
                    populateBatimentEntretienSpinner();
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<List<com.mobile.diafarms.network.dto.BatimentSelectResponse>>> call, Throwable t) {
                // bâtiments déjà affichés depuis le cache le cas échéant, rien à faire de plus
            }
        });
    }

    private void populateBatimentEntretienSpinner() {
        List<String> labels = new ArrayList<>();
        for (com.mobile.diafarms.network.dto.BatimentSelectResponse b : batimentsEntretien) labels.add(b.getNom());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, labels);
        spinnerBatimentEntretien.setAdapter(adapter);
        applyPendingBatimentEntretienSelection();
    }

    private void selectBatimentEntretienByUniqueId(String uniqueId) {
        if (uniqueId == null) return;
        pendingBatimentEntretienSelection = uniqueId;
        applyPendingBatimentEntretienSelection();
    }

    private void applyPendingBatimentEntretienSelection() {
        if (pendingBatimentEntretienSelection == null) return;
        for (com.mobile.diafarms.network.dto.BatimentSelectResponse b : batimentsEntretien) {
            if (pendingBatimentEntretienSelection.equals(b.getUniqueId())) {
                spinnerBatimentEntretien.setText(b.getNom(), false);
                return;
            }
        }
    }

    private String[] categoriesPourType() {
        // En modification, une saisie garde son type (updateSaisie ne le change pas) : un
        // achat d'aliment reste un achat, une sortie ne peut pas devenir un achat.
        if (type == SaisieType.ALIMENTATION_ACHAT) {
            return editingLocalId != null ? new String[]{CATEGORIE_ACHAT_ALIMENT} : CATEGORIES_TRANSACTION_SORTIE;
        }
        if (type == SaisieType.TRANSACTION_SORTIE) {
            if (editingLocalId == null) return CATEGORIES_TRANSACTION_SORTIE;
            List<String> sansAchat = new ArrayList<>(java.util.Arrays.asList(CATEGORIES_TRANSACTION_SORTIE));
            sansAchat.remove(CATEGORIE_ACHAT_ALIMENT);
            return sansAchat.toArray(new String[0]);
        }
        return CATEGORIES_TRANSACTION_ENTREE;
    }

    /** Catégorie « Autre » (entrée ou sortie) : champ « Préciser la catégorie * » affiché. */
    private void majCategoriePrecision() {
        boolean autre = (type == SaisieType.TRANSACTION_ENTREE || type == SaisieType.TRANSACTION_SORTIE)
                && CATEGORIE_AUTRE.equals(spinnerCategorie.getText().toString());
        tilCategoriePrecision.setVisibility(autre ? View.VISIBLE : View.GONE);
    }

    /** Sortie d'argent : la catégorie "Achat d'aliment" bascule le formulaire en achat
     * d'aliment (ALIMENTATION_ACHAT), toute autre catégorie le ramène en sortie simple. */
    private void basculerAchatAliment() {
        if (editingLocalId != null) return;
        if (type != SaisieType.TRANSACTION_SORTIE && type != SaisieType.ALIMENTATION_ACHAT) return;
        boolean achat = CATEGORIE_ACHAT_ALIMENT.equals(spinnerCategorie.getText().toString());
        SaisieType nouveau = achat ? SaisieType.ALIMENTATION_ACHAT : SaisieType.TRANSACTION_SORTIE;
        if (nouveau == type) return;
        type = nouveau;
        applyTypeVisibility();
        if (achat && (projetUniqueId == null || projetUniqueId.isEmpty())) {
            toast("Un achat d'aliment entre dans le stock d'un projet : choisissez d'abord un projet à l'accueil");
        }
    }

    /** TextWatcher qui n'agit qu'après chaque modification du texte. */
    private static android.text.TextWatcher apresSaisie(Runnable action) {
        return new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void afterTextChanged(android.text.Editable e) { action.run(); }
        };
    }

    private static void setHintParent(android.widget.EditText champ, String hint) {
        android.view.ViewParent p = champ.getParent() != null ? champ.getParent().getParent() : null;
        if (p instanceof com.google.android.material.textfield.TextInputLayout) {
            ((com.google.android.material.textfield.TextInputLayout) p).setHint(hint);
        }
    }

    private boolean estSante() {
        return type == SaisieType.TRANSACTION_SORTIE && spinnerCategorie != null
                && CATEGORIE_SANTE.equals(spinnerCategorie.getText().toString());
    }

    /** Santé / Vétérinaire : toujours le projet (choix « concerne » masqué), jamais un site
     * ni la ferme ; poulailler du projet (facultatif) seulement s'il en occupe plusieurs. */
    private void appliquerSante() {
        if (type != SaisieType.TRANSACTION_SORTIE) return;
        boolean sante = estSante();
        if (sante && (projetUniqueId == null || projetUniqueId.isEmpty())) {
            toast("Un soin concerne un projet : choisissez d'abord le projet à l'accueil");
        }
        majConcerne();
        boolean natureChoisie = sante && spinnerNatureSante.getText().length() > 0;
        boolean medicament = sante && NATURE_MEDICAMENT.equals(spinnerNatureSante.getText().toString());
        groupNatureSante.setVisibility(sante ? View.VISIBLE : View.GONE);
        groupMedicamentAchat.setVisibility(medicament ? View.VISIBLE : View.GONE);
        groupSanteQuantite.setVisibility(natureChoisie ? View.VISIBLE : View.GONE);
        setHintParent(etQuantiteSante, medicament ? "Quantité achetée *" : "Nombre de jours (facultatif)");
        setHintParent(etPrixUnitaireSante, medicament ? "Prix unitaire (facultatif)" : "Prix par jour (facultatif)");
        setHintParent(etDescriptionTransaction, !sante ? "Description" : medicament ? "Observations (facultatif)" : "Nature du service *");
        com.google.android.material.textfield.TextInputLayout tilMontant = findViewById(R.id.tilMontant);
        if (tilMontant != null) tilMontant.setHint(sante ? "Montant total (FCFA) *" : "Montant (FCFA)");
        groupBatimentTop.setVisibility(sante && batiments.size() > 1 ? View.VISIBLE : View.GONE);
        if (sante) populateBatimentSpinner();
    }

    /** Quantité totale = sacs x poids d'un sac, tant qu'elle n'a pas été modifiée à la main. */
    private void majQuantiteAchatCalculee() {
        if (quantiteAchatManuelle || majQuantiteAchatAuto) return;
        Double sacs = parseDoubleOrNull(etSac.getText());
        Double poids = parseDoubleOrNull(etPoidsSac.getText());
        majQuantiteAchatAuto = true;
        etQuantiteKgAchat.setText(sacs != null && poids != null && sacs > 0 && poids > 0 ? formatSaisie(sacs * poids) : "");
        majQuantiteAchatAuto = false;
    }

    private static String typeAlimentToWire(String libelle) {
        for (int i = 0; i < TYPES_ALIMENT.length; i++) if (TYPES_ALIMENT[i].equals(libelle)) return TYPES_ALIMENT_WIRE[i];
        return null;
    }

    private static String typeAlimentLibelle(String wire) {
        for (int i = 0; i < TYPES_ALIMENT_WIRE.length; i++) if (TYPES_ALIMENT_WIRE[i].equals(wire)) return TYPES_ALIMENT[i];
        return null;
    }

    private void applyTypeVisibility() {
        // Un achat d'aliment se saisit dans le formulaire Sortie d'argent (catégorie dédiée).
        tvTitreForm.setText(type == SaisieType.ALIMENTATION_ACHAT ? SaisieType.TRANSACTION_SORTIE.getLabel() : type.getLabel());
        tvProjetForm.setText(projetLabel != null ? projetLabel : "");

        groupCollecte.setVisibility(type == SaisieType.COLLECTE_OEUFS ? View.VISIBLE : View.GONE);
        groupSoins.setVisibility(type == SaisieType.SOINS ? View.VISIBLE : View.GONE);
        if (type == SaisieType.SOINS) updateGroupSoinsSousType();
        groupEntretien.setVisibility(type == SaisieType.ENTRETIEN ? View.VISIBLE : View.GONE);
        if (type == SaisieType.ENTRETIEN) updateGroupEntretienNiveau();
        groupMortalite.setVisibility(type == SaisieType.MORTALITE ? View.VISIBLE : View.GONE);
        groupReforme.setVisibility(type == SaisieType.REFORME ? View.VISIBLE : View.GONE);
        groupAlimentationAchat.setVisibility(type == SaisieType.ALIMENTATION_ACHAT ? View.VISIBLE : View.GONE);
        groupAlimentationAchatFin.setVisibility(type == SaisieType.ALIMENTATION_ACHAT ? View.VISIBLE : View.GONE);
        groupTransactionDetails.setVisibility(type == SaisieType.ALIMENTATION_ACHAT ? View.GONE : View.VISIBLE);
        groupConsommation.setVisibility(type == SaisieType.ALIMENTATION_CONSOMMATION ? View.VISIBLE : View.GONE);
        groupVenteOeufs.setVisibility(type == SaisieType.VENTE_OEUFS ? View.VISIBLE : View.GONE);
        groupVenteReforme.setVisibility(type == SaisieType.VENTE_REFORME ? View.VISIBLE : View.GONE);
        if (type == SaisieType.VENTE_REFORME) refreshTypeVenteReformeUi();
        groupClient.setVisibility(type == SaisieType.CLIENT_CREATE ? View.VISIBLE : View.GONE);
        groupCommande.setVisibility(type == SaisieType.COMMANDE_CREATE ? View.VISIBLE : View.GONE);
        groupSalaire.setVisibility(type == SaisieType.SALAIRE_PAYER ? View.VISIBLE : View.GONE);
        groupLivraison.setVisibility(type == SaisieType.LIVRAISON_COMMANDE ? View.VISIBLE : View.GONE);
        groupPaiementClient.setVisibility(type == SaisieType.PAIEMENT_CLIENT ? View.VISIBLE : View.GONE);
        groupTransaction.setVisibility(
                (type == SaisieType.TRANSACTION_ENTREE || type == SaisieType.TRANSACTION_SORTIE
                        || type == SaisieType.ALIMENTATION_ACHAT)
                        ? View.VISIBLE : View.GONE);
        majCategoriePrecision();
        // Vente de fientes : sacs, prix d'un sac, montant *, description facultative.
        // Autre vente : montant * et commentaire * (comme le web).
        groupVenteDiverse.setVisibility(estVenteDiverse() ? View.VISIBLE : View.GONE);
        groupSacsVenteDiverse.setVisibility(type == SaisieType.VENTE_FIENTES ? View.VISIBLE : View.GONE);
        tilDescriptionVenteDiverse.setHint(type == SaisieType.VENTE_AUTRE ? "Commentaire * (ce qui a été vendu)" : "Description (facultatif)");

        // Aucun de ces types n'a de notion de bâtiment (poulailler) — client, commande et
        // salaire sont farm-scopés (voir Client.java/Commande.java/Salaire.java côté
        // back) ; les ventes puisent dans le stock d'un MAGASIN (toute la ferme), jamais
        // d'un poulailler précis — un vendeur sur le terrain n'a pas à choisir un
        // poulailler pour vendre des œufs déjà dans un magasin de vente (voir
        // VenteOeufsCreateRequest/VenteReformeCreateRequest, aucun batimentUniqueId).
        // Client et Salaire n'ont en plus aucune notion de date/heure : le bloc
        // Date/Heure est masqué en plus pour ces deux types (une commande garde
        // etDate = dateCommande, un paiement de salaire a sa propre Période dédiée).
        // ENTRETIEN a son propre champ poulailler (spinnerBatimentEntretien, TOUS les
        // poulaillers de la ferme) — le champ partagé ci-dessus ne liste que ceux
        // occupés par le projet sélectionné, non pertinent ici.
        boolean sansBatiment = type == SaisieType.CLIENT_CREATE || type == SaisieType.COMMANDE_CREATE || type == SaisieType.SALAIRE_PAYER
                || type == SaisieType.VENTE_OEUFS || type == SaisieType.VENTE_REFORME || type == SaisieType.VENTE_FIENTES
                || type == SaisieType.VENTE_AUTRE
                // Achat d'aliment : il entre dans le stock du projet, c'est la consommation qui
                // se fait dans un poulailler (le serveur ignore le poulailler d'un achat).
                || type == SaisieType.ALIMENTATION_ACHAT
                || type == SaisieType.ENTRETIEN || type == SaisieType.LIVRAISON_COMMANDE || type == SaisieType.PAIEMENT_CLIENT
                // Le poulailler d'une dépense se choisit dans "Rattachement (facultatif)" ; le champ du haut ne servait à rien pour une transaction.
                || type == SaisieType.TRANSACTION_ENTREE || type == SaisieType.TRANSACTION_SORTIE;
        groupBatimentTop.setVisibility(sansBatiment ? View.GONE : View.VISIBLE);
        // SOINS affiche toujours le bâtiment, même quand le type choisi dans le
        // formulaire est Vaccination : optionnel dans ce cas (voir onValider, le
        // choix "Aucun bâtiment précis" reste valide), obligatoire pour Médicament/
        // Autre — reprend le comportement de l'ancien écran Vaccination dédié, qui
        // n'avait tout simplement pas ce champ. Seuls Client/Salaire restent sans
        // aucune notion de date/heure de saisie.
        // Salaire : la date est celle du paiement (datePaiement), sans heure.
        groupDateHeureTop.setVisibility(type == SaisieType.CLIENT_CREATE ? View.GONE : View.VISIBLE);
        findViewById(R.id.groupHeureTop).setVisibility(
                type == SaisieType.SALAIRE_PAYER || estVenteDiverse() ? View.INVISIBLE : View.VISIBLE);
        if (type == SaisieType.LIVRAISON_COMMANDE && commandeLivree != null) {
            tvProjetForm.setText(commandeLivree.clientNom);
        }
        if (type == SaisieType.TRANSACTION_SORTIE && groupConcerne != null) appliquerSante();
        majConcerne();
    }

    // ===================== ENCAISSEMENT CLIENT =====================

    /** Client choisi (ou rechargé) : ses commandes ouvertes pour le sélecteur facultatif, et
     * son dernier compte connu (cache d'abord, puis réseau si possible). */
    private void surClientPaiementChoisi(boolean parUtilisateur) {
        String clientUid = getSelectedClientUniqueId(spinnerClientPaiement, false);
        commandesClientPaiement.clear();
        List<String> labels = new ArrayList<>();
        labels.add(LABEL_SANS_COMMANDE);
        if (clientUid != null) {
            for (CommandeResponse c : CommandesHorsLigne.lire(localDatabase)) {
                if (clientUid.equals(c.clientUniqueId)) {
                    commandesClientPaiement.add(c);
                    labels.add(libelleCommande(c));
                }
            }
        }
        String avant = spinnerCommandePaiement.getText().toString();
        // Un autre client choisi : la commande d'origine (hors cache) n'a plus de sens.
        if (parUtilisateur) commandeHorsCachePaiement = null;
        String choix = LABEL_SANS_COMMANDE;
        if (!parUtilisateur && labels.contains(avant)) choix = avant;
        if (pendingCommandePaiement != null && clientUid != null) {
            boolean trouvee = false;
            for (CommandeResponse c : commandesClientPaiement) {
                if (pendingCommandePaiement.equals(c.uniqueId)) {
                    choix = libelleCommande(c);
                    trouvee = true;
                    break;
                }
            }
            if (!trouvee) {
                commandeHorsCachePaiement = pendingCommandePaiement;
            }
            pendingCommandePaiement = null;
        }
        if (commandeHorsCachePaiement != null) {
            labels.add(LABEL_COMMANDE_HORS_CACHE);
            if (!parUtilisateur && (LABEL_SANS_COMMANDE.equals(choix) || LABEL_COMMANDE_HORS_CACHE.equals(avant))) {
                choix = LABEL_COMMANDE_HORS_CACHE;
            }
        }
        spinnerCommandePaiement.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, labels));
        spinnerCommandePaiement.setText(choix, false);
        afficherSoldeClient(clientUid);
        if (clientUid != null) {
            CachePrefetcher.rafraichirCompteClient(this, localDatabase, clientUid, ok -> {
                if (ok && !isFinishing() && clientUid.equals(getSelectedClientUniqueId(spinnerClientPaiement, false))) {
                    afficherSoldeClient(clientUid);
                }
            });
        }
    }

    private String libelleCommande(CommandeResponse c) {
        StringBuilder l = new StringBuilder("Commande ");
        l.append(c.estOeufs() ? "d'œufs" : "de réforme");
        if (c.dateCommande != null && c.dateCommande.length() >= 10) {
            l.append(" du ").append(c.dateCommande.substring(8, 10)).append('/').append(c.dateCommande.substring(5, 7));
        }
        int reste = c.resteServeur();
        l.append(", reste ").append(c.estOeufs() ? AlveoleUtils.formatOeufsAvecAlveoles(reste) : reste + " sujet(s)");
        return l.toString();
    }

    private String getSelectedCommandePaiement() {
        String selected = spinnerCommandePaiement.getText().toString();
        if (LABEL_COMMANDE_HORS_CACHE.equals(selected)) return commandeHorsCachePaiement;
        for (CommandeResponse c : commandesClientPaiement) {
            if (libelleCommande(c).equals(selected)) return c.uniqueId;
        }
        return null;
    }

    /** Dernier compte connu du client + encaissements saisis ici et pas encore envoyés. */
    private void afficherSoldeClient(String clientUid) {
        if (clientUid == null) {
            tvSoldeClient.setVisibility(View.GONE);
            return;
        }
        String key = CachePrefetcher.CACHE_COMPTE_CLIENT_PREFIX + clientUid;
        CompteClientResponse.Compte compte = getCachedOrNull(key, CompteClientResponse.Compte.class);
        double enAttente = 0;
        for (SaisieLocale s : localDatabase.getSaisiesPourControles(SaisieType.PAIEMENT_CLIENT)) {
            if (!s.seraRenvoyee() || s.getLocalId().equals(editingLocalId)) continue;
            PaiementClientRequest p = gson.fromJson(s.getPayloadJson(), PaiementClientRequest.class);
            if (p != null && clientUid.equals(p.clientUniqueId) && p.montant != null) enAttente += p.montant;
        }
        StringBuilder t = new StringBuilder();
        if (compte != null) {
            t.append(String.format(Locale.FRANCE, "Reste à payer : %,.0f F", compte.resteAPayer));
            t.append(String.format(Locale.FRANCE, "\nAvance libre : %,.0f F", compte.avanceLibre));
            t.append(String.format(Locale.FRANCE, "\nAvance réservée aux commandes : %,.0f F", compte.avanceReservee));
            long maj = localDatabase.getCacheUpdatedAt(key);
            if (maj > 0) {
                t.append("\nDonnées du ").append(new SimpleDateFormat("dd/MM à HH:mm", Locale.FRANCE).format(new java.util.Date(maj)));
            }
        } else {
            t.append("Solde du client inconnu sur ce téléphone (jamais chargé en ligne).");
        }
        if (enAttente > 0) {
            t.append(String.format(Locale.FRANCE, "\nEncaissements pas encore envoyés : %,.0f F", enAttente));
        }
        tvSoldeClient.setText(t.toString());
        tvSoldeClient.setVisibility(View.VISIBLE);
    }

    // ===================== LIVRAISON D'UNE COMMANDE =====================

    /** Commande à livrer : depuis l'écran Commandes (EXTRA_COMMANDE_ID) ou, en
     * modification, depuis la saisie elle-même. Toujours lue dans le cache local (marche
     * hors ligne). false si elle n'y est plus (livrée ou close entre-temps). */
    private boolean chargerCommandeLivree() {
        String uid = getIntent().getStringExtra(EXTRA_COMMANDE_ID);
        if (uid == null && editingLocalId != null) {
            SaisieLocale existante = localDatabase.getSaisieById(editingLocalId);
            if (existante != null) {
                LivraisonCommandeRequest r = gson.fromJson(existante.getPayloadJson(), LivraisonCommandeRequest.class);
                if (r != null) uid = r.commandeUniqueId;
            }
        }
        commandeLivree = CommandesHorsLigne.trouver(localDatabase, uid);
        if (commandeLivree == null) {
            toast("Commande introuvable sur ce téléphone (livrée ou close ?). Actualisez la liste des commandes.");
            return false;
        }
        return true;
    }

    private void setupLivraison() {
        CommandeResponse c = commandeLivree;
        boolean oeufs = c.estOeufs();
        boolean kilo = c.estAuKilo();
        groupUniteLivraison.setVisibility(oeufs ? View.VISIBLE : View.GONE);
        groupKiloLivraison.setVisibility(kilo ? View.VISIBLE : View.GONE);
        if (kilo && c.prixKgEstime != null) etPrixKgLivraison.setText(formatSaisie(c.prixKgEstime));
        spinnerModePaiementLivraison.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, MODE_PAIEMENT_LABELS));
        spinnerModePaiementLivraison.setText(MODE_PAIEMENT_LABELS[0], false);
        appliquerLibellesLivraison();

        StringBuilder info = new StringBuilder();
        info.append("Commande de ").append(c.clientNom).append(" : ");
        info.append(oeufs ? "œufs" : (kilo ? "réforme au kilo" : "réforme par tête"));
        if (c.magasinNom != null) info.append(", magasin ").append(c.magasinNom);
        int reste = resteLivrable();
        // Œufs : à partir d'une alvéole restant à livrer, la saisie se fait en alvéoles
        // (unité du marché) ; l'unité œuf reste au choix.
        if (oeufs && editingLocalId == null && reste >= AlveoleUtils.OEUFS_PAR_ALVEOLE) {
            radioGroupUniteLivraison.check(R.id.radioUniteLivraisonAlveole);
        }
        // Libellés selon l'unité réellement cochée (Alvéole par défaut ci-dessus).
        appliquerLibellesLivraison();
        info.append("\nReste à livrer : ").append(formatQuantiteLivraison(Math.max(0, reste)));
        if (c.acompteReserve != null && c.acompteReserve > 0) {
            info.append(String.format(Locale.FRANCE, "\nAcompte réservé : %,.0f F (règle d'abord cette livraison)", c.acompteReserve));
        }
        tvLivraisonCommande.setText(info.toString());

        TextWatcher w = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                recalculerMontantLivraison();
                refreshCoherence();
            }
        };
        etQuantiteLivraison.addTextChangedListener(w);
        etOeufsSuppLivraison.addTextChangedListener(w);
        etPoidsLivraison.addTextChangedListener(w);
        etPrixKgLivraison.addTextChangedListener(w);
        etMontantRecuLivraison.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                Double m = parseDoubleOrNull(etMontantRecuLivraison.getText());
                tilModePaiementLivraison.setVisibility(m != null && m > 0 ? View.VISIBLE : View.GONE);
            }
        });
        radioGroupUniteLivraison.setOnCheckedChangeListener((g, id) -> {
            appliquerLibellesLivraison();
            recalculerMontantLivraison();
            refreshCoherence();
        });
        recalculerMontantLivraison();
        if (c.magasinUniqueId != null) loadStockForMagasin(c.magasinUniqueId);
        else displayStockMagasin(null, false);
    }

    private boolean isLivraisonEnAlveoles() {
        return commandeLivree != null && commandeLivree.estOeufs()
                && radioGroupUniteLivraison.getCheckedRadioButtonId() == R.id.radioUniteLivraisonAlveole;
    }

    private void appliquerLibellesLivraison() {
        if (commandeLivree.estOeufs()) {
            tilQuantiteLivraison.setHint(isLivraisonEnAlveoles() ? "Nombre d'alvéoles livrées *" : "Nombre d'œufs livrés *");
            // En alvéoles : œufs en plus d'une alvéole pleine, comme à la collecte et au web.
            tilOeufsSuppLivraison.setVisibility(isLivraisonEnAlveoles() ? View.VISIBLE : View.GONE);
        } else {
            tilQuantiteLivraison.setHint("Nombre de sujets livrés *");
        }
    }

    /** Quantité saisie, toujours en œufs (jamais en alvéoles) ou en sujets. */
    private int quantiteLivraison() {
        int saisie = parseIntSafe(etQuantiteLivraison.getText());
        return isLivraisonEnAlveoles()
                ? AlveoleUtils.alveolesToOeufs(saisie) + Math.max(0, parseIntSafe(etOeufsSuppLivraison.getText()))
                : saisie;
    }

    /** Reste à livrer vu d'ici : reste serveur moins les autres livraisons en attente. */
    private int resteLivrable() {
        return commandeLivree.resteServeur()
                - CommandesHorsLigne.quantiteEnAttente(localDatabase, commandeLivree.uniqueId, editingLocalId);
    }

    private String formatQuantiteLivraison(int n) {
        return commandeLivree.estOeufs() ? AlveoleUtils.formatOeufsAvecAlveoles(n) : n + (n > 1 ? " sujets" : " sujet");
    }

    /** Montant de cette livraison, calculé comme le serveur (CommandeServiceImpl.livrer). */
    private Double montantLivraison() {
        if (commandeLivree.estAuKilo()) {
            Double poids = parseDoubleOrNull(etPoidsLivraison.getText());
            Double prixKg = parseDoubleOrNull(etPrixKgLivraison.getText());
            if (prixKg == null) prixKg = commandeLivree.prixKgEstime;
            return poids != null && poids > 0 && prixKg != null && prixKg > 0 ? (double) Math.round(poids * prixKg) : null;
        }
        Double pu = commandeLivree.prixUnitaireLivraison();
        int q = quantiteLivraison();
        return pu != null && q > 0 ? (double) Math.round(pu * q) : null;
    }

    private void recalculerMontantLivraison() {
        Double m = montantLivraison();
        tvMontantLivraison.setText(m != null ? String.format(Locale.FRANCE, "Montant de cette livraison : %,.0f F", m) : "");
        tvMontantLivraison.setVisibility(m != null ? View.VISIBLE : View.GONE);
    }

    /** Libellé d'un projet de la ferme (cache des projets), celui de l'accueil par défaut. */
    private String libelleProjet(String uid) {
        if (uid == null) return null;
        if (uid.equals(projetUniqueId) && projetLabel != null) return projetLabel;
        for (ProjetSelectResponse p : lireListeCache(CachePrefetcher.CACHE_PROJETS_SELECT, ProjetSelectResponse.class)) {
            if (uid.equals(p.getUniqueId())) return p.getLabel();
        }
        return uid.equals(projetUniqueId) ? projetLabel : null;
    }

    private boolean estTypeTransaction() {
        return type == SaisieType.TRANSACTION_ENTREE || type == SaisieType.TRANSACTION_SORTIE;
    }

    private boolean estVenteDiverse() {
        return type == SaisieType.VENTE_FIENTES || type == SaisieType.VENTE_AUTRE;
    }

    private <T> List<T> lireListeCache(String cacheKey, Class<T> clazz) {
        String json = localDatabase.getCache(cacheKey);
        if (json == null) return new ArrayList<>();
        try {
            Type listType = TypeToken.getParameterized(List.class, clazz).getType();
            List<T> parsed = gson.fromJson(json, listType);
            return parsed != null ? parsed : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** Sites et poulaillers (TOUS, occupés ou non) proposés pour rattacher une dépense : cache
     * préchargé d'abord (marche hors ligne), puis rafraîchi par le réseau si possible. */
    private void loadRattachementsTransaction() {
        sitesTransaction = lireListeCache(CachePrefetcher.CACHE_SITES_SELECT, SiteSelectResponse.class);
        batimentsTransaction = lireListeCache(CachePrefetcher.CACHE_BATIMENTS_TOUS, BatimentSelectResponse.class);
        afficherRattachements();

        ApiClient.dataApi(this).getSites().enqueue(new Callback<ApiEnvelope<List<SiteSelectResponse>>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<List<SiteSelectResponse>>> call, Response<ApiEnvelope<List<SiteSelectResponse>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    localDatabase.putCache(CachePrefetcher.CACHE_SITES_SELECT, gson.toJson(response.body().getData()));
                    sitesTransaction = response.body().getData();
                    afficherRattachements();
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<List<SiteSelectResponse>>> call, Throwable t) { }
        });
        ApiClient.dataApi(this).getBatimentsTous().enqueue(new Callback<ApiEnvelope<List<BatimentSelectResponse>>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<List<BatimentSelectResponse>>> call, Response<ApiEnvelope<List<BatimentSelectResponse>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    localDatabase.putCache(CachePrefetcher.CACHE_BATIMENTS_TOUS, gson.toJson(response.body().getData()));
                    batimentsTransaction = response.body().getData();
                    afficherRattachements();
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<List<BatimentSelectResponse>>> call, Throwable t) { }
        });
    }

    private boolean projetChoisi() {
        return projetUniqueId != null && !projetUniqueId.isEmpty();
    }

    /** Choix « Cette dépense concerne » visible : sortie / entrée d'argent hors Santé (toujours
     * le projet) et hors achat d'aliment, ventes diverses (projet ou ferme, jamais un site). */
    private void majConcerne() {
        if (groupConcerne == null) return;
        boolean vente = estVenteDiverse();
        boolean visible = (estTypeTransaction() && !estSante()) || vente;
        groupConcerne.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) return;
        tvTitreConcerne.setText(vente ? "Cette vente concerne :"
                : type == SaisieType.TRANSACTION_ENTREE ? "Cette entrée d'argent concerne :" : "Cette dépense concerne :");
        boolean projet = projetChoisi();
        rbConcerneProjet.setText(projet ? "Le Projet (" + (projetLabel != null ? projetLabel : "projet de l'accueil") + ")" : "Le Projet");
        rbConcerneProjet.setEnabled(projet);
        tvConcerneSansProjet.setVisibility(projet ? View.GONE : View.VISIBLE);
        rbConcerneSite.setVisibility(vente ? View.GONE : View.VISIBLE);
        if ((vente && rbConcerneSite.isChecked()) || (!projet && rbConcerneProjet.isChecked())) {
            rgConcerne.clearCheck();
            return; // clearCheck rappelle majConcerne
        }
        tilBatimentTransaction.setVisibility(!vente && rbConcerneProjet.isChecked() && !poulaillersProjet.isEmpty()
                ? View.VISIBLE : View.GONE);
        tilSiteTransaction.setVisibility(!vente && rbConcerneSite.isChecked() ? View.VISIBLE : View.GONE);
    }

    /** Coche le choix enregistré sur une saisie en attente (ancien format ramené au plus proche). */
    private void appliquerRattachementEnregistre(RattachementSaisie r) {
        if (r == null || rgConcerne == null) return;
        switch (r.choix) {
            case RattachementSaisie.PROJET:
                // Le projet de la saisie est celui du formulaire (voir onCreate).
                if (projetChoisi()) rbConcerneProjet.setChecked(true);
                batimentTransactionEnAttente = r.batimentUniqueId;
                break;
            case RattachementSaisie.SITE:
                rbConcerneSite.setChecked(true);
                siteTransactionEnAttente = r.siteUniqueId;
                break;
            case RattachementSaisie.ANCIEN: {
                rattachementAncien = r;
                String detail;
                if (r.projetsConcernes.size() > 1) {
                    detail = r.projetsConcernes.size() + " projets";
                } else {
                    String nom = null;
                    for (BatimentSelectResponse x : lireListeCache(CachePrefetcher.CACHE_BATIMENTS_TOUS, BatimentSelectResponse.class)) {
                        if (x.getUniqueId() != null && x.getUniqueId().equals(r.batimentUniqueId)) nom = x.getNom();
                    }
                    detail = "poulailler" + (nom != null ? " " + nom : "");
                }
                rbConcerneAncien.setText("Comme enregistrée (" + detail + ")");
                rbConcerneAncien.setVisibility(View.VISIBLE);
                rbConcerneAncien.setChecked(true);
                break;
            }
            default:
                rbConcerneFerme.setChecked(true);
        }
        afficherRattachements();
    }

    /** (Re)remplit la liste des sites et celle des poulaillers du projet en conservant le choix
     * courant ou celui à réappliquer (édition). Un poulailler enregistré que le projet n'occupe
     * plus est gardé dans la liste (le serveur l'accepte s'il l'a occupé). */
    private void afficherRattachements() {
        if (spinnerSiteTransaction == null) return;
        String siteId = siteTransactionEnAttente != null ? siteTransactionEnAttente : getSelectedSiteTransaction();
        String batimentId = batimentTransactionEnAttente != null ? batimentTransactionEnAttente : getSelectedBatimentTransaction();

        List<String> siteLabels = new ArrayList<>();
        String siteChoisi = "";
        for (SiteSelectResponse x : sitesTransaction) {
            siteLabels.add(x.getNom());
            if (x.getUniqueId() != null && x.getUniqueId().equals(siteId)) siteChoisi = x.getNom();
        }
        if (siteChoisi.isEmpty() && sitesTransaction.size() == 1) siteChoisi = sitesTransaction.get(0).getNom();
        spinnerSiteTransaction.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, siteLabels));
        spinnerSiteTransaction.setText(siteChoisi, false);
        if (!sitesTransaction.isEmpty()) siteTransactionEnAttente = null;

        poulaillersProjet.clear();
        boolean trouve = false;
        for (OccupationBatimentResponse b : batiments) {
            poulaillersProjet.add(new String[]{b.getBatimentUniqueId(), b.getNomBatiment()});
            if (b.getBatimentUniqueId() != null && b.getBatimentUniqueId().equals(batimentId)) trouve = true;
        }
        if (batimentId != null && !trouve) {
            String nom = "Poulailler enregistré";
            for (BatimentSelectResponse x : batimentsTransaction) {
                if (batimentId.equals(x.getUniqueId())) nom = x.getNom();
            }
            poulaillersProjet.add(new String[]{batimentId, nom});
        }
        List<String> batLabels = new ArrayList<>();
        batLabels.add(LIBELLE_TOUT_LE_PROJET);
        String batChoisi = LIBELLE_TOUT_LE_PROJET;
        for (String[] b : poulaillersProjet) {
            batLabels.add(b[1]);
            if (b[0] != null && b[0].equals(batimentId)) batChoisi = b[1];
        }
        spinnerBatimentTransaction.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, batLabels));
        spinnerBatimentTransaction.setText(batChoisi, false);
        batimentTransactionEnAttente = null;
        majConcerne();
    }

    private String getSelectedSiteTransaction() {
        String choisi = spinnerSiteTransaction.getText().toString();
        for (SiteSelectResponse x : sitesTransaction) if (x.getNom().equals(choisi)) return x.getUniqueId();
        return null;
    }

    /** Poulailler choisi pour « Le Projet », null = tout le projet. */
    private String getSelectedBatimentTransaction() {
        String choisi = spinnerBatimentTransaction.getText().toString();
        for (String[] b : poulaillersProjet) if (b[1].equals(choisi)) return b[0];
        return null;
    }

    private void setupDateHeurePickers() {
        etDate.setText(isoDate.format(dateCal.getTime()));
        etDate.setOnClickListener(v -> showMaterialDatePicker(dateCal, true, chosen -> {
            dateCal.setTimeInMillis(chosen.getTimeInMillis());
            etDate.setText(isoDate.format(dateCal.getTime()));
        }));

        etHeure.setHint("--:--");
        etHeure.setOnClickListener(v -> {
            Calendar base = heureCal != null ? heureCal : Calendar.getInstance();
            showMaterialTimePicker(base.get(Calendar.HOUR_OF_DAY), base.get(Calendar.MINUTE), (hour, minute) -> {
                heureCal = Calendar.getInstance();
                heureCal.set(Calendar.HOUR_OF_DAY, hour);
                heureCal.set(Calendar.MINUTE, minute);
                etHeure.setText(isoTime.format(heureCal.getTime()));
            });
        });
    }

    /**
     * Sélecteur de date M3 (calendrier en bas de l'écran) à la place du
     * DatePickerDialog système, qui détonnait visuellement avec le reste de l'appli.
     * MaterialDatePicker travaille en UTC (epoch millis) en interne — on convertit
     * explicitement vers/depuis UTC pour la présélection et le résultat, sinon le jour
     * affiché peut se décaler de ±1 selon le fuseau horaire de l'appareil (piège
     * classique de cette API si on lui passe directement un Calendar en fuseau local).
     */
    private void showMaterialDatePicker(Calendar initial, boolean maxAujourdhui, java.util.function.Consumer<Calendar> onDateChoisie) {
        Calendar utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH));

        com.google.android.material.datepicker.MaterialDatePicker.Builder<Long> builder =
                com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
                        .setSelection(utc.getTimeInMillis());
        if (maxAujourdhui) {
            builder.setCalendarConstraints(new com.google.android.material.datepicker.CalendarConstraints.Builder()
                    .setValidator(com.google.android.material.datepicker.DateValidatorPointBackward.now())
                    .build());
        }
        com.google.android.material.datepicker.MaterialDatePicker<Long> picker = builder.build();
        picker.addOnPositiveButtonClickListener(selectionUtcMillis -> {
            Calendar resultUtc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
            resultUtc.setTimeInMillis(selectionUtcMillis);
            Calendar resultLocal = (Calendar) initial.clone();
            resultLocal.set(resultUtc.get(Calendar.YEAR), resultUtc.get(Calendar.MONTH), resultUtc.get(Calendar.DAY_OF_MONTH));
            onDateChoisie.accept(resultLocal);
        });
        picker.show(getSupportFragmentManager(), "date_picker");
    }

    /** Sélecteur d'heure M3 (cadran), à la place du TimePickerDialog système. */
    private void showMaterialTimePicker(int heureInitiale, int minuteInitiale, java.util.function.BiConsumer<Integer, Integer> onHeureChoisie) {
        com.google.android.material.timepicker.MaterialTimePicker picker = new com.google.android.material.timepicker.MaterialTimePicker.Builder()
                .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H)
                .setHour(heureInitiale)
                .setMinute(minuteInitiale)
                .build();
        picker.addOnPositiveButtonClickListener(v -> onHeureChoisie.accept(picker.getHour(), picker.getMinute()));
        picker.show(getSupportFragmentManager(), "time_picker");
    }

    private void setupResumeCollecteAuto() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { calculerResumeCollecte(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        etAlveolesCollectees.addTextChangedListener(watcher);
        etOeufsCollectes.addTextChangedListener(watcher);
        etOeufsCasses.addTextChangedListener(watcher);
        etOeufsNonUtilisables.addTextChangedListener(watcher);
    }

    private boolean isVenteOeufsEnAlveoles() {
        return radioGroupUniteVenteOeufs != null && radioGroupUniteVenteOeufs.getCheckedRadioButtonId() == R.id.radioUniteVenteAlveole;
    }

    /** Montant = quantité saisie × prix unitaire saisi, tous deux dans la MÊME unité
     * (œuf ou alvéole) : pas besoin de conversion pour ce calcul, contrairement à
     * req.quantiteOeufs/req.prixUnitaire dans onValider() qui doivent, eux, toujours
     * être exprimés en œufs. Champ non modifiable (voir bindViews) : toujours ce
     * calcul, jamais une valeur libre. Tant que le montant rapporté n'a pas été
     * modifié à la main, il suit ce montant théorique (vente payée intégralement
     * par défaut). */
    private void recalculerMontantVenteOeufs() {
        int saisie = parseIntSafe(etQuantiteOeufsVente.getText());
        Double prix = parseDoubleOrNull(etPrixUnitaireOeufs.getText());
        String montant = (saisie > 0 && prix != null && prix > 0) ? String.valueOf(Math.round(saisie * prix)) : "";
        etMontantVenteOeufs.setText(montant);
        if (!montantRapporteOeufsModifieManuel) {
            syncingMontantRapporte = true;
            etMontantRapporteVenteOeufs.setText(montant);
            syncingMontantRapporte = false;
        }
    }

    private boolean isTypeVenteReformeKilo() {
        return radioGroupTypeVenteReforme != null
                && radioGroupTypeVenteReforme.getCheckedRadioButtonId() == R.id.radioTypeVenteReformeKilo;
    }

    /** Affiche/masque le champ poids et adapte le hint du prix selon le mode choisi —
     * appelé au changement de radio ET quand le groupe Vente réforme redevient
     * visible (voir showGroupFor). */
    private void refreshTypeVenteReformeUi() {
        boolean kilo = isTypeVenteReformeKilo();
        int visibility = kilo ? View.VISIBLE : View.GONE;
        tilPoidsTotalReforme.setVisibility(visibility);
        spacerPoidsTotalReforme.setVisibility(visibility);
        tilPrixUnitaireReforme.setHint(kilo ? "Prix du kg (FCFA)" : "Prix unitaire (FCFA)");
        refreshPoidsMoyenReforme();
        refreshEstimationPesee();
    }

    /** Au kilo : "Poids moyen : X kg/sujet" = poids total pesé / nombre de sujets. */
    private void refreshPoidsMoyenReforme() {
        if (tvPoidsMoyenReforme == null) return;
        Double poids = parseDoubleOrNull(etPoidsTotalReforme.getText());
        int sujets = parseIntSafe(etNombreSujetsVente.getText());
        if (isTypeVenteReformeKilo() && poids != null && poids > 0 && sujets > 0) {
            tvPoidsMoyenReforme.setText("Poids moyen : " + formatNombre(poids / sujets, 2) + " kg/sujet");
            tvPoidsMoyenReforme.setVisibility(View.VISIBLE);
        } else {
            tvPoidsMoyenReforme.setVisibility(View.GONE);
        }
    }

    /** Même principe que recalculerMontantVenteOeufs, pour Vente réforme. Par tête :
     * sujets × prix. Au kilo : poids total × prix (etPrixUnitaireReforme réinterprété
     * en prix/kg) — le nombre de sujets sert uniquement au plafond de stock, jamais à
     * ce calcul dans ce mode. */
    private void recalculerMontantVenteReforme() {
        Double prix = parseDoubleOrNull(etPrixUnitaireReforme.getText());
        Double poidsTotal = parseDoubleOrNull(etPoidsTotalReforme.getText());
        double quantite = isTypeVenteReformeKilo()
                ? (poidsTotal != null ? poidsTotal : 0.0)
                : parseIntSafe(etNombreSujetsVente.getText());
        String montant = (quantite > 0 && prix != null && prix > 0) ? String.valueOf(Math.round(quantite * prix)) : "";
        etMontantVenteReforme.setText(montant);
        if (!montantRapporteReformeModifieManuel) {
            syncingMontantRapporte = true;
            etMontantRapporteVenteReforme.setText(montant);
            syncingMontantRapporte = false;
        }
        refreshPoidsMoyenReforme();
        refreshEstimationPesee();
    }

    private boolean isCommandeReforme() {
        return radioGroupTypeCommande.getCheckedRadioButtonId() == R.id.radioCommandeReforme;
    }

    private boolean isCommandeEnAlveoles() {
        return !isCommandeReforme() && radioGroupUniteCommande.getCheckedRadioButtonId() == R.id.radioUniteCommandeAlveole;
    }

    /** Libellés des champs quantité/prix selon le type (Œufs/Réforme) et, pour Œufs,
     * l'unité choisie (Œuf/Alvéole) — même patron que le listener de
     * radioGroupUniteVenteOeufs (bindViews). */
    private void applyLabelsQuantiteCommande() {
        if (isCommandeReforme()) {
            tilQuantiteCommande.setHint("Nombre de sujets");
            tilPrixUnitaireCommande.setHint("Prix unitaire estimé (FCFA, optionnel)");
        } else {
            boolean enAlveoles = isCommandeEnAlveoles();
            tilQuantiteCommande.setHint(enAlveoles ? "Nombre d'alvéoles commandées" : "Nombre d'œufs commandés");
            tilPrixUnitaireCommande.setHint(enAlveoles ? "Prix par alvéole (FCFA, optionnel)" : "Prix unitaire (FCFA, optionnel)");
        }
    }

    /** Montant = quantité saisie × prix unitaire saisi, tous deux dans la MÊME unité
     * (œuf/alvéole, ou sujet pour Réforme) — pas besoin de conversion pour ce calcul,
     * contrairement à req.quantite/req.prixUnitaireEstime dans onValider() qui
     * doivent, eux, toujours être exprimés en œufs pour le type OEUFS. Reste
     * modifiable manuellement ensuite (ex: négociation), même principe que
     * recalculerMontantVenteOeufs(). */
    private void recalculerMontantEstimeCommande() {
        if (isCommandeKilo()) {
            // Au kilo : montant = poids estimé x prix du kg (le serveur refait ce calcul
            // et ignore la valeur envoyée) ; sans poids estimé, montant saisi à la main.
            Double prixKg = parseDoubleOrNull(etPrixKgCommande.getText());
            Double poids = parseDoubleOrNull(etPoidsEstimeCommande.getText());
            boolean calcule = prixKg != null && prixKg > 0 && poids != null && poids > 0;
            if (calcule) etMontantEstimeCommande.setText(String.valueOf(Math.round(poids * prixKg)));
            etMontantEstimeCommande.setEnabled(!calcule);
            tilMontantEstimeCommande.setHint(calcule ? "Montant estimé (FCFA) = poids x prix du kg" : "Montant estimé (FCFA)");
        } else {
            etMontantEstimeCommande.setEnabled(true);
            tilMontantEstimeCommande.setHint("Montant estimé (FCFA)");
            int saisie = parseIntSafe(etQuantiteCommande.getText());
            Double prix = parseDoubleOrNull(etPrixUnitaireCommande.getText());
            if (saisie > 0 && prix != null && prix > 0) {
                etMontantEstimeCommande.setText(String.valueOf(Math.round(saisie * prix)));
            }
        }
        refreshEstimationPesee();
    }

    private boolean isCommandeKilo() {
        return isCommandeReforme() && radioGroupTarificationCommande != null
                && radioGroupTarificationCommande.getCheckedRadioButtonId() == R.id.radioTarificationCommandeKilo;
    }

    /** Tarification visible pour une commande de réformes seulement ; au kilo, le prix
     * par sujet est remplacé par prix du kg estimé + poids estimé. */
    private void refreshTarificationCommandeUi() {
        boolean reforme = isCommandeReforme();
        boolean kilo = isCommandeKilo();
        groupTarificationCommande.setVisibility(reforme ? View.VISIBLE : View.GONE);
        groupKiloCommande.setVisibility(kilo ? View.VISIBLE : View.GONE);
        tilPrixUnitaireCommande.setVisibility(kilo ? View.GONE : View.VISIBLE);
        spacerPrixUnitaireCommande.setVisibility(kilo ? View.GONE : View.VISIBLE);
        refreshEstimationPesee();
    }

    // ===================== ESTIMATION PAR LA DERNIÈRE PESÉE (réformes au kilo) =====================

    /** Hors ligne d'abord : sessions TERMINEE du téléphone + dernière valeur serveur en
     * cache ; puis, si en ligne et un projet est choisi, rafraîchit ce cache. */
    private void loadDernierePesee() {
        dernierePesee = DernierePeseeEstimation.trouver(localDatabase, projetUniqueId);
        refreshEstimationPesee();
        if (projetUniqueId == null || projetUniqueId.isEmpty()) return;
        ApiClient.dataApi(this).getDernierPoidsMoyen(projetUniqueId).enqueue(new Callback<ApiEnvelope<DernierPoidsMoyenResponse>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<DernierPoidsMoyenResponse>> call, Response<ApiEnvelope<DernierPoidsMoyenResponse>> response) {
                if (!response.isSuccessful() || response.body() == null || isFinishing()) return;
                DernierPoidsMoyenResponse data = response.body().getData();
                String key = CachePrefetcher.CACHE_DERNIER_POIDS_MOYEN_PREFIX + projetUniqueId;
                if (data == null || data.poidsMoyenKg == null) localDatabase.deleteCache(key);
                else localDatabase.putCache(key, gson.toJson(data));
                dernierePesee = DernierePeseeEstimation.trouver(localDatabase, projetUniqueId);
                refreshEstimationPesee();
            }

            @Override
            public void onFailure(Call<ApiEnvelope<DernierPoidsMoyenResponse>> call, Throwable t) { }
        });
    }

    /** "Dernière pesée : 1,86 kg/sujet (25/09) → estimation 37,2 kg ≈ 74 400 F", pour
     * Vente réforme au kilo et Commande de réformes au kilo seulement. */
    private void refreshEstimationPesee() {
        if (groupEstimationPeseeReforme == null || groupEstimationPeseeCommande == null) return;
        boolean venteKilo = type == SaisieType.VENTE_REFORME && isTypeVenteReformeKilo();
        boolean commandeKilo = type == SaisieType.COMMANDE_CREATE && isCommandeKilo();
        groupEstimationPeseeReforme.setVisibility(venteKilo && dernierePesee != null ? View.VISIBLE : View.GONE);
        groupEstimationPeseeCommande.setVisibility(commandeKilo && dernierePesee != null ? View.VISIBLE : View.GONE);
        if (dernierePesee == null || (!venteKilo && !commandeKilo)) return;

        int sujets = parseIntSafe((venteKilo ? etNombreSujetsVente : etQuantiteCommande).getText());
        Double prixKg = parseDoubleOrNull((venteKilo ? etPrixUnitaireReforme : etPrixKgCommande).getText());
        StringBuilder texte = new StringBuilder("Dernière pesée : ")
                .append(formatNombre(dernierePesee.poidsMoyenKg, 2)).append(" kg/sujet");
        String date = dernierePesee.dateCourte();
        if (!date.isEmpty()) texte.append(" (").append(date).append(")");
        Double estimation = estimationPoids(sujets);
        if (estimation != null) {
            texte.append(" → estimation ").append(formatNombre(estimation, 1)).append(" kg");
            if (prixKg != null && prixKg > 0) {
                texte.append(" ≈ ").append(String.format(Locale.FRANCE, "%,.0f", estimation * prixKg)).append(" F");
            }
        } else {
            texte.append(". Indiquez le nombre de sujets pour estimer le poids.");
        }
        (venteKilo ? tvEstimationPeseeReforme : tvEstimationPeseeCommande).setText(texte.toString());
        (venteKilo ? btnUtiliserEstimationReforme : btnUtiliserEstimationCommande).setEnabled(estimation != null);
    }

    /** Poids estimé = sujets x dernier poids moyen, arrondi à 0,1 kg ; null si impossible. */
    private Double estimationPoids(int sujets) {
        if (dernierePesee == null || sujets <= 0) return null;
        return Math.round(dernierePesee.poidsMoyenKg * sujets * 10.0) / 10.0;
    }

    /** Pré-remplit le poids avec l'estimation (reste modifiable : le poids réellement
     * pesé est celui qui compte). */
    private void utiliserEstimation(TextInputEditText etSujets, TextInputEditText etPoids) {
        Double estimation = estimationPoids(parseIntSafe(etSujets.getText()));
        if (estimation == null) {
            toast("Indiquez d'abord le nombre de sujets");
            return;
        }
        etPoids.setText(formatSaisie(estimation));
        etPoids.requestFocus();
        if (etPoids.getText() != null) etPoids.setSelection(etPoids.getText().length());
    }

    /** Nombre à la française, au plus maxDecimales décimales, zéros inutiles retirés
     * (1,86 ; 37,2 ; 36). */
    private static String formatNombre(double valeur, int maxDecimales) {
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(Locale.FRANCE);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(maxDecimales);
        return nf.format(valeur);
    }

    /** Valeur pour un champ de saisie numérique (point décimal, sans séparateur). */
    private static String formatSaisie(double valeur) {
        if (valeur == Math.rint(valeur)) return String.valueOf((long) valeur);
        return new java.math.BigDecimal(valeur).setScale(3, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** Total = alvéoles×30 + œufs saisis hors alvéole, voir groupCollecte dans le
     * layout (deux champs simultanés, plus de bascule d'unité). */
    private int oeufsCollectesReel() {
        int alveoles = parseIntSafe(etAlveolesCollectees.getText());
        int oeufsSupp = parseIntSafe(etOeufsCollectes.getText());
        return AlveoleUtils.alveolesToOeufs(alveoles) + oeufsSupp;
    }

    private void calculerResumeCollecte() {
        int total = oeufsCollectesReel();
        int casses = parseIntSafe(etOeufsCasses.getText());
        int nonUtilisables = parseIntSafe(etOeufsNonUtilisables.getText());
        int bonEtat = Math.max(0, total - casses - nonUtilisables);

        tvResumeCollecte.setText(String.format(Locale.FRANCE, "Soit %d œufs en bon état (%d cassés, %d non utilisables)", bonEtat, casses, nonUtilisables));
        boolean tauxCasseEleve = total > 0 && casses > total * 0.05;
        tvResumeCollecte.setTextColor(getColor(tauxCasseEleve ? android.R.color.holo_red_dark : R.color.green_primary));
    }

    /**
     * Ne propose que les bâtiments occupés par le projet en cours (via son
     * occupationBatiment), pas tous les bâtiments de la ferme — inutile et source
     * d'erreur de saisir une collecte/soin/mortalité dans un bâtiment qui n'a rien
     * à voir avec ce projet.
     */
    private void loadBatiments() {
        if (projetUniqueId == null || projetUniqueId.isEmpty()) {
            populateBatimentSpinner();
            return;
        }

        String cacheKey = CachePrefetcher.CACHE_PROJET_DETAIL_PREFIX + projetUniqueId;
        // Affiche tout de suite les bâtiments connus en local (même cache que
        // l'accueil, voir CachePrefetcher/HomeActivity) au lieu d'attendre le réseau.
        ProjetDetailResponse cached = getCachedOrNull(cacheKey, ProjetDetailResponse.class);
        boolean hadCache = cached != null;
        if (hadCache) {
            applyBatiments(cached);
        } else {
            populateBatimentSpinner();
        }

        ApiClient.dataApi(this).getProjetDetail(projetUniqueId).enqueue(new Callback<ApiEnvelope<ProjetDetailResponse>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<ProjetDetailResponse>> call, Response<ApiEnvelope<ProjetDetailResponse>> response) {
                ProjetDetailResponse detail = response.isSuccessful() && response.body() != null
                        ? response.body().getData() : null;
                if (detail != null) {
                    localDatabase.putCache(cacheKey, gson.toJson(detail));
                    applyBatiments(detail);
                }
                // sinon : bâtiments déjà affichés depuis le cache le cas échéant, rien à faire
            }

            @Override
            public void onFailure(Call<ApiEnvelope<ProjetDetailResponse>> call, Throwable t) {
                // bâtiments déjà affichés depuis le cache le cas échéant, rien à faire de plus
            }
        });
    }

    private void applyBatiments(ProjetDetailResponse detail) {
        if (detail.getOccupationBatiment() != null) {
            batiments = new ArrayList<>();
            for (OccupationBatimentResponse occ : detail.getOccupationBatiment()) {
                if (OccupationUtils.estActive(occ.getDateSortie())) {
                    batiments.add(occ);
                }
            }
        }
        populateBatimentSpinner();
        if (estSante()) groupBatimentTop.setVisibility(batiments.size() > 1 ? View.VISIBLE : View.GONE);
        if (estTypeTransaction()) afficherRattachements();
    }

    // Collecte, sortie d'aliment, soins (vaccination comprise), mortalité et réforme se
    // passent dans UN poulailler : pas de choix "Aucun" pour elles (le serveur refuse
    // aussi, voir PoulaillerObligatoire). L'achat d'aliment reste lié au seul projet.
    private boolean poulaillerObligatoire() {
        return type == SaisieType.COLLECTE_OEUFS || type == SaisieType.ALIMENTATION_CONSOMMATION
                || type == SaisieType.SOINS || type == SaisieType.MORTALITE || type == SaisieType.REFORME;
    }

    private static final String LIBELLE_TOUT_LE_PROJET = "Tout le projet";

    private void populateBatimentSpinner() {
        List<String> labels = new ArrayList<>();
        boolean obligatoire = poulaillerObligatoire();
        com.google.android.material.textfield.TextInputLayout til = findViewById(R.id.tilBatimentTop);
        if (til != null) til.setHint(obligatoire ? "Poulailler *" : "Poulailler (optionnel)");
        if (!obligatoire) labels.add(estSante() ? LIBELLE_TOUT_LE_PROJET : "Aucun bâtiment précis");
        for (OccupationBatimentResponse b : batiments) {
            labels.add(b.getNomBatiment());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, labels);
        spinnerBatiment.setAdapter(adapter);
        // Obligatoire : présélectionné seulement s'il n'y a qu'un poulailler, sinon on
        // laisse vide pour forcer un vrai choix.
        spinnerBatiment.setText(!obligatoire ? labels.get(0) : (batiments.size() == 1 ? labels.get(0) : ""), false);
        // La liste des bâtiments vient de (ré)arriver (réseau ou cache) : si une
        // sélection avait été demandée avant que loadBatiments() ait fini de charger
        // (voir applyPendingBatimentSelection), on la réapplique maintenant.
        applyPendingBatimentSelection();
    }

    private String getSelectedBatimentUniqueId() {
        String selected = spinnerBatiment.getText().toString();
        for (OccupationBatimentResponse b : batiments) {
            if (b.getNomBatiment().equals(selected)) return b.getBatimentUniqueId();
        }
        return null;
    }

    // Même principe que getSelectedBatimentUniqueId() mais sur batimentsEntretien
    // (TOUS les poulaillers de la ferme) — voir Entretien.
    private String getSelectedBatimentEntretienUniqueId() {
        String selected = spinnerBatimentEntretien.getText().toString();
        for (com.mobile.diafarms.network.dto.BatimentSelectResponse b : batimentsEntretien) {
            if (b.getNom().equals(selected)) return b.getUniqueId();
        }
        return null;
    }

    /**
     * En édition d'une saisie existante, prefillFromExisting() (appelé de manière
     * synchrone dans onCreate) tente de resélectionner le bâtiment AVANT que
     * loadBatiments() (appel réseau asynchrone, lancé juste avant) ait eu le temps de
     * répondre — la liste "batiments" est encore vide à ce moment-là, donc la sélection
     * échouait silencieusement et affichait "Aucun bâtiment précis" alors qu'un
     * bâtiment était bien enregistré. On mémorise la demande et on la ré-applique
     * depuis populateBatimentSpinner() dès que la vraie liste est disponible.
     */
    private void selectBatimentByUniqueId(String uniqueId) {
        if (uniqueId == null) return;
        pendingBatimentSelection = uniqueId;
        applyPendingBatimentSelection();
    }

    private void applyPendingBatimentSelection() {
        if (pendingBatimentSelection == null) return;
        for (int i = 0; i < batiments.size(); i++) {
            if (pendingBatimentSelection.equals(batiments.get(i).getBatimentUniqueId())) {
                spinnerBatiment.setText(batiments.get(i).getNomBatiment(), false);
                return;
            }
        }
    }

    /** Retourne la dernière valeur connue en cache local pour cette clé, ou null si
     * absente — utilisé par les écrans de stock/effectif pour afficher tout de suite
     * une valeur déjà connue plutôt que d'attendre le réseau (jusqu'à 15s hors ligne,
     * voir ApiClient.connectTimeout/readTimeout) : la requête réseau est ensuite
     * lancée en tâche de fond et ne fait que rafraîchir l'affichage si elle aboutit. */
    private <T> T getCachedOrNull(String cacheKey, Class<T> clazz) {
        String cached = localDatabase.getCache(cacheKey);
        return cached != null ? gson.fromJson(cached, clazz) : null;
    }

    private void loadStock() {
        String cacheKey = CachePrefetcher.CACHE_STOCK_ALIMENT_PREFIX + projetUniqueId;
        StockAlimentResponse cached = getCachedOrNull(cacheKey, StockAlimentResponse.class);
        boolean hadCache = cached != null;
        if (hadCache) displayStock(cached, true);

        ApiClient.dataApi(this).getStockAliment(projetUniqueId).enqueue(new Callback<ApiEnvelope<StockAlimentResponse>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<StockAlimentResponse>> call, Response<ApiEnvelope<StockAlimentResponse>> response) {
                StockAlimentResponse stock = response.isSuccessful() && response.body() != null ? response.body().getData() : null;
                if (stock != null) {
                    localDatabase.putCache(cacheKey, gson.toJson(stock));
                    displayStock(stock, false);
                } else if (!hadCache) {
                    displayStock(null, false);
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<StockAlimentResponse>> call, Throwable t) {
                // Hors ligne : la dernière valeur connue (préchargée après le login/
                // scan QR ou vue depuis l'accueil, voir CachePrefetcher) est déjà
                // affichée ci-dessus si elle existe ; sinon on le signale maintenant.
                if (!hadCache) displayStock(null, true);
            }
        });
    }

    private void displayStock(StockAlimentResponse stock, boolean fromCache) {
        if (stock != null && stock.getStockRestant() != null) {
            String suffix = fromCache ? " (dernière donnée connue, hors ligne)" : "";
            tvStockInfo.setText(String.format(Locale.FRANCE, "Stock restant estimé : %.1f kg%s", stock.getStockRestant(), suffix));
            stockAlimentRestantConnu = stock.getStockRestant();
        } else {
            tvStockInfo.setText(fromCache ? "Stock non disponible (hors ligne)" : "Stock non disponible");
            stockAlimentRestantConnu = null;
        }
        refreshCoherence();
    }

    /**
     * Magasins de vente liés à ce vendeur (déjà filtrés côté serveur) — mêmes deux
     * spinners (Vente œufs / Vente réforme) alimentés depuis la même liste, même
     * schéma cache → réseau que loadBatiments(). Pas d'option "Aucun magasin" : le
     * magasin est obligatoire pour toute vente (contrairement au bâtiment).
     */
    private void loadMagasins() {
        String cacheKey = CachePrefetcher.CACHE_MAGASINS_SELECT;
        String cachedJson = localDatabase.getCache(cacheKey);
        if (cachedJson != null) {
            Type listType = new TypeToken<List<MagasinSelectResponse>>() {}.getType();
            List<MagasinSelectResponse> parsed = gson.fromJson(cachedJson, listType);
            if (parsed != null) {
                magasins = parsed;
                populateMagasinSpinners();
            }
        }

        ApiClient.dataApi(this).getMagasinsSelect("VENTE").enqueue(new Callback<ApiEnvelope<List<MagasinSelectResponse>>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<List<MagasinSelectResponse>>> call, Response<ApiEnvelope<List<MagasinSelectResponse>>> response) {
                List<MagasinSelectResponse> data = response.isSuccessful() && response.body() != null ? response.body().getData() : null;
                if (data != null) {
                    magasins = data;
                    localDatabase.putCache(cacheKey, gson.toJson(data));
                    populateMagasinSpinners();
                }
                // sinon : magasins déjà affichés depuis le cache le cas échéant, rien à faire
            }

            @Override
            public void onFailure(Call<ApiEnvelope<List<MagasinSelectResponse>>> call, Throwable t) {
                // magasins déjà affichés depuis le cache le cas échéant, rien à faire de plus
            }
        });
    }

    /** Présélection seulement s'il n'y a qu'un magasin (comme le web) : avec plusieurs, le
     * choix est obligatoire, sinon une vente pouvait puiser dans le mauvais magasin sans
     * que personne ne l'ait choisi. Un choix déjà fait reste en place quand la liste est
     * rechargée (cache puis réseau). */
    private void populateMagasinSpinners() {
        List<String> labels = new ArrayList<>();
        for (MagasinSelectResponse m : magasins) {
            labels.add(m.getNom());
        }
        AutoCompleteTextView[] spinners = {spinnerMagasinOeufs, spinnerMagasinReforme, spinnerMagasinCommande};
        boolean changement = false;
        for (AutoCompleteTextView sp : spinners) {
            String avant = sp.getText().toString();
            sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, labels));
            String apres = labels.contains(avant) ? avant : (labels.size() == 1 ? labels.get(0) : "");
            sp.setText(apres, false);
            if (!apres.equals(avant)) changement = true;
        }
        if (changement) loadStockPourMagasinSelectionne();
        applyPendingMagasinSelection();
    }

    private String getSelectedMagasinUniqueId(AutoCompleteTextView spinner) {
        String selected = spinner.getText().toString();
        for (MagasinSelectResponse m : magasins) {
            if (m.getNom().equals(selected)) return m.getUniqueId();
        }
        return null;
    }

    /** Même raisonnement que selectBatimentByUniqueId/applyPendingBatimentSelection —
     * voir leur commentaire pour la course entre prefillFromExisting() (synchrone) et
     * loadMagasins() (réseau asynchrone). */
    private void selectMagasinByUniqueId(String uniqueId) {
        if (uniqueId == null) return;
        pendingMagasinSelection = uniqueId;
        applyPendingMagasinSelection();
    }

    private void applyPendingMagasinSelection() {
        if (pendingMagasinSelection == null) return;
        for (int i = 0; i < magasins.size(); i++) {
            if (pendingMagasinSelection.equals(magasins.get(i).getUniqueId())) {
                String label = magasins.get(i).getNom();
                spinnerMagasinOeufs.setText(label, false);
                spinnerMagasinReforme.setText(label, false);
                spinnerMagasinCommande.setText(label, false);
                // Appelé explicitement (pas seulement via OnItemClickListener) car
                // AutoCompleteTextView.setText(..., false) ne déclenche jamais le
                // listener (à la différence de Spinner.setSelection() qui le fait sauf
                // quand la position ne change pas).
                loadStockPourMagasinSelectionne();
                return;
            }
        }
    }

    /**
     * Clients déjà synchronisés côté serveur (farm-scopée, pas de filtre par vendeur) —
     * partagés par les 3 sélecteurs (Vente œufs/réforme : optionnel avec un premier
     * élément "Vente directe" ; Commande : obligatoire, sans cet élément). Même schéma
     * cache → réseau que loadMagasins(). Un client créé hors ligne (voir CLIENT_CREATE)
     * n'apparaît ici qu'une fois synchronisé — voir SaisieType.CLIENT_CREATE.
     */
    private void loadClients() {
        String cacheKey = CachePrefetcher.CACHE_CLIENTS_SELECT;
        String cachedJson = localDatabase.getCache(cacheKey);
        if (cachedJson != null) {
            Type listType = new TypeToken<List<ClientSelectResponse>>() {}.getType();
            List<ClientSelectResponse> parsed = gson.fromJson(cachedJson, listType);
            if (parsed != null) {
                clients = parsed;
                populateClientSpinners();
            }
        }

        ApiClient.dataApi(this).getClientsSelect().enqueue(new Callback<ApiEnvelope<List<ClientSelectResponse>>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<List<ClientSelectResponse>>> call, Response<ApiEnvelope<List<ClientSelectResponse>>> response) {
                List<ClientSelectResponse> data = response.isSuccessful() && response.body() != null ? response.body().getData() : null;
                if (data != null) {
                    clients = data;
                    localDatabase.putCache(cacheKey, gson.toJson(data));
                    populateClientSpinners();
                }
                // sinon : clients déjà affichés depuis le cache le cas échéant, rien à faire
            }

            @Override
            public void onFailure(Call<ApiEnvelope<List<ClientSelectResponse>>> call, Throwable t) {
                // clients déjà affichés depuis le cache le cas échéant, rien à faire de plus
            }
        });
    }

    private static final String LABEL_VENTE_DIRECTE = "Vente directe (sans client)";

    private void populateClientSpinners() {
        // Vente œufs/réforme : "Vente directe" en position 0, comme le web (client
        // optionnel par défaut absent).
        List<String> labelsVente = new ArrayList<>();
        labelsVente.add(LABEL_VENTE_DIRECTE);
        for (ClientSelectResponse c : clients) labelsVente.add(c.getNom());
        ArrayAdapter<String> adapterVente = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, labelsVente);
        spinnerClientVenteOeufs.setAdapter(adapterVente);
        spinnerClientVenteReforme.setAdapter(adapterVente);
        spinnerClientVenteOeufs.setText(labelsVente.get(0), false);
        spinnerClientVenteReforme.setText(labelsVente.get(0), false);

        // Commande : client obligatoire, pas d'option "aucun" — si la liste est vide,
        // le message tvAucunClientCommande prend le relais (voir onValider pour le
        // blocage explicite).
        List<String> labelsCommande = new ArrayList<>();
        for (ClientSelectResponse c : clients) labelsCommande.add(c.getNom());
        ArrayAdapter<String> adapterCommande = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, labelsCommande);
        spinnerClientCommande.setAdapter(adapterCommande);
        if (!labelsCommande.isEmpty()) spinnerClientCommande.setText(labelsCommande.get(0), false);
        tvAucunClientCommande.setVisibility(clients.isEmpty() ? View.VISIBLE : View.GONE);
        spinnerClientCommande.setEnabled(!clients.isEmpty());

        // Encaissement : client obligatoire, aucun présélectionné (on ne devine pas qui paie).
        String clientPaiementAvant = spinnerClientPaiement.getText().toString();
        spinnerClientPaiement.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, labelsCommande));
        if (!clientPaiementAvant.isEmpty() && labelsCommande.contains(clientPaiementAvant)) {
            spinnerClientPaiement.setText(clientPaiementAvant, false);
        }
        tvAucunClientPaiement.setVisibility(clients.isEmpty() ? View.VISIBLE : View.GONE);
        if (type == SaisieType.PAIEMENT_CLIENT) surClientPaiementChoisi(false);

        applyPendingClientSelection();
    }

    /** Position dans "clients" (pas dans le spinner : les sélecteurs Vente ont un
     * décalage de 1 à cause de "Vente directe" en position 0). null = aucun client
     * sélectionné (vente directe, ou rien sélectionné côté Commande). */
    private String getSelectedClientUniqueId(AutoCompleteTextView spinner, boolean hasVenteDirecteOption) {
        String selected = spinner.getText().toString();
        if (hasVenteDirecteOption && LABEL_VENTE_DIRECTE.equals(selected)) return null;
        for (ClientSelectResponse c : clients) {
            if (c.getNom().equals(selected)) return c.getUniqueId();
        }
        return null;
    }

    /** Même raisonnement que selectMagasinByUniqueId/applyPendingMagasinSelection. */
    private void selectClientByUniqueId(String uniqueId) {
        if (uniqueId == null) return;
        pendingClientSelection = uniqueId;
        applyPendingClientSelection();
    }

    private void applyPendingClientSelection() {
        if (pendingClientSelection == null) return;
        for (int i = 0; i < clients.size(); i++) {
            if (pendingClientSelection.equals(clients.get(i).getUniqueId())) {
                String nom = clients.get(i).getNom();
                spinnerClientVenteOeufs.setText(nom, false);
                spinnerClientVenteReforme.setText(nom, false);
                spinnerClientCommande.setText(nom, false);
                spinnerClientPaiement.setText(nom, false);
                if (type == SaisieType.PAIEMENT_CLIENT) surClientPaiementChoisi(false);
                // Appelé explicitement (pas seulement via OnItemClickListener) car
                // AutoCompleteTextView.setText(..., false) ne déclenche jamais le
                // listener — même raisonnement que applyPendingMagasinSelection.
                refreshModePaiementVenteOeufs();
                refreshModePaiementVenteReforme();
                return;
            }
        }
    }

    // ===================== MODE DE PAIEMENT (acompte commande, vente à un client) =====================

    // Miroir de l'enum ModePaiement côté back — Espèces en position 0 = défaut.
    private static final String[] MODE_PAIEMENT_VALEURS =
            {"ESPECES", "ORANGE_MONEY", "MOOV_MONEY", "WAVE", "VIREMENT", "CHEQUE", "AUTRE"};
    private static final String[] MODE_PAIEMENT_LABELS =
            {"Espèces", "Orange Money", "Moov Money", "Wave", "Virement", "Chèque", "Autre"};

    private String getSelectedModePaiement(AutoCompleteTextView spinner) {
        String selected = spinner.getText().toString();
        for (int i = 0; i < MODE_PAIEMENT_LABELS.length; i++) {
            if (MODE_PAIEMENT_LABELS[i].equals(selected)) return MODE_PAIEMENT_VALEURS[i];
        }
        return null;
    }

    /** Espèces par défaut si value est null/inconnue (saisie hors ligne antérieure à ce
     * champ, ou toujours pas de valeur en édition) — cohérent avec le défaut serveur. */
    private void selectModePaiementByValue(AutoCompleteTextView spinner, String value) {
        for (int i = 0; i < MODE_PAIEMENT_VALEURS.length; i++) {
            if (MODE_PAIEMENT_VALEURS[i].equals(value)) {
                spinner.setText(MODE_PAIEMENT_LABELS[i], false);
                return;
            }
        }
        spinner.setText(MODE_PAIEMENT_LABELS[0], false);
    }

    /** Un mode de paiement n'a de sens que si le montant rapporté devient un paiement
     * client (voir VenteOeufsCreateRequest.modePaiement) — sinon (vente directe) c'est
     * un simple contrôle de caisse vendeur, le champ reste masqué et modePaiement part
     * null (voir onValider). */
    private void refreshModePaiementVenteOeufs() {
        boolean hasClient = getSelectedClientUniqueId(spinnerClientVenteOeufs, true) != null;
        tilMontantRapporteVenteOeufs.setHint(hasClient ? "Montant reçu maintenant (FCFA)" : "Montant rapporté (FCFA)");
        tilModePaiementVenteOeufs.setVisibility(hasClient ? View.VISIBLE : View.GONE);
    }

    /** Même raisonnement que refreshModePaiementVenteOeufs. */
    private void refreshModePaiementVenteReforme() {
        boolean hasClient = getSelectedClientUniqueId(spinnerClientVenteReforme, true) != null;
        tilMontantRapporteVenteReforme.setHint(hasClient ? "Montant reçu maintenant (FCFA)" : "Montant rapporté (FCFA)");
        tilModePaiementVenteReforme.setVisibility(hasClient ? View.VISIBLE : View.GONE);
    }

    /** Un mode de paiement n'a de sens que s'il y a réellement un acompte à qualifier —
     * champ masqué et modePaiement part null sinon (voir onValider). */
    private void refreshModePaiementCommande() {
        Double acompte = parseDoubleOrNull(etAcompteCommande.getText());
        tilModePaiementCommande.setVisibility(acompte != null && acompte > 0 ? View.VISIBLE : View.GONE);
    }

    // ===================== PAYER UN SALAIRE (COMPTABLE) =====================

    /** Mois (1-12, libellés français) + années courante ±1 — même plage que le web
     * (PayerSalaireDialog), largement suffisante pour rattraper un mois passé ou
     * anticiper un paiement en avance sans champ libre source d'erreur de saisie. */
    private void setupSalaireSpinners() {
        ArrayAdapter<String> moisAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, MOIS_LABELS);
        spinnerMoisSalaire.setAdapter(moisAdapter);

        int anneeCourante = Calendar.getInstance().get(Calendar.YEAR);
        List<String> annees = new ArrayList<>();
        for (int a = anneeCourante - 1; a <= anneeCourante + 1; a++) annees.add(String.valueOf(a));
        ArrayAdapter<String> anneeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, annees);
        spinnerAnneeSalaire.setAdapter(anneeAdapter);

        spinnerMoisSalaire.setText(MOIS_LABELS[Calendar.getInstance().get(Calendar.MONTH)], false);
        spinnerAnneeSalaire.setText(annees.get(1), false); // année courante, position 1 (courante-1, courante, courante+1)
    }

    /** "AAAA-MM" à partir des spinners mois/année — voir SalairePayerRequest.periode
     * côté back, résolu dynamiquement au taux réellement en vigueur pour cette période
     * (SalaireServiceImpl.resolveTauxPourPeriode), pas seulement le taux courant. */
    private String getSelectedPeriodeSalaire() {
        String moisText = spinnerMoisSalaire.getText().toString();
        int mois = java.util.Arrays.asList(MOIS_LABELS).indexOf(moisText) + 1;
        if (mois <= 0) return null;
        String annee = spinnerAnneeSalaire.getText().toString();
        if (annee.isEmpty()) return null;
        return String.format(Locale.FRANCE, "%s-%02d", annee, mois);
    }

    /** Grille salariale de la ferme (farm-scopée) — un Salaire par employé (mode +
     * taux de base), utilisée pour peupler le sélecteur employé et pré-remplir le
     * montant proposé. Même schéma cache → réseau que loadClients(). */
    private void loadSalaires() {
        String cacheKey = CachePrefetcher.CACHE_SALAIRES_SELECT;
        String cachedJson = localDatabase.getCache(cacheKey);
        if (cachedJson != null) {
            Type listType = new TypeToken<List<SalaireSelectResponse>>() {}.getType();
            List<SalaireSelectResponse> parsed = gson.fromJson(cachedJson, listType);
            if (parsed != null) {
                salaires = parsed;
                populateSalaireSpinner();
            }
        }

        ApiClient.dataApi(this).getSalairesSelect().enqueue(new Callback<ApiEnvelope<List<SalaireSelectResponse>>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<List<SalaireSelectResponse>>> call, Response<ApiEnvelope<List<SalaireSelectResponse>>> response) {
                List<SalaireSelectResponse> data = response.isSuccessful() && response.body() != null ? response.body().getData() : null;
                if (data != null) {
                    salaires = data;
                    localDatabase.putCache(cacheKey, gson.toJson(data));
                    populateSalaireSpinner();
                }
                // sinon : grille déjà affichée depuis le cache le cas échéant, rien à faire
            }

            @Override
            public void onFailure(Call<ApiEnvelope<List<SalaireSelectResponse>>> call, Throwable t) {
                // grille déjà affichée depuis le cache le cas échéant, rien à faire de plus
            }
        });
    }

    private void populateSalaireSpinner() {
        List<String> labels = new ArrayList<>();
        for (SalaireSelectResponse s : salaires) labels.add(s.getEmployeNom());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, labels);
        // Rechargement (cache puis réseau) : l'employé déjà choisi reste choisi.
        String avant = spinnerEmployeSalaire.getText().toString();
        spinnerEmployeSalaire.setAdapter(adapter);
        if (labels.contains(avant)) spinnerEmployeSalaire.setText(avant, false);
        else if (!labels.isEmpty()) spinnerEmployeSalaire.setText(labels.get(0), false);
        tvAucunSalaireEmploye.setVisibility(salaires.isEmpty() ? View.VISIBLE : View.GONE);
        spinnerEmployeSalaire.setEnabled(!salaires.isEmpty());
        applyPendingEmployeSalaireSelection();
        applySalaireEmployeSelectionne();
    }

    private SalaireSelectResponse getSelectedSalaireEmploye() {
        String selected = spinnerEmployeSalaire.getText().toString();
        for (SalaireSelectResponse s : salaires) {
            if (s.getEmployeNom().equals(selected)) return s;
        }
        return null;
    }

    /** JOURNALIER/HORAIRE : quantité obligatoire, montant = taux × quantité (recalculé
     * à la saisie, voir recalculerMontantSalaire). MENSUEL : montant = taux, pas de
     * quantité — mêmes règles que PayerSalaireDialog côté web. */
    private void applySalaireEmployeSelectionne() {
        SalaireSelectResponse s = getSelectedSalaireEmploye();
        if (s == null) {
            tilQuantiteSalaire.setVisibility(View.GONE);
            tvTauxInfoSalaire.setText("");
            employeSalaireAffiche = null;
            return;
        }
        boolean mensuel = "MENSUEL".equals(s.getModePaiement());
        tilQuantiteSalaire.setVisibility(mensuel ? View.GONE : View.VISIBLE);
        tilQuantiteSalaire.setHint("HORAIRE".equals(s.getModePaiement()) ? "Heures travaillées" : "Jours travaillés");
        Double taux = s.getTauxBase();
        String suffixe = "HORAIRE".equals(s.getModePaiement()) ? "/ heure" : ("JOURNALIER".equals(s.getModePaiement()) ? "/ jour" : "/ mois");
        tvTauxInfoSalaire.setText(taux != null ? String.format(Locale.FRANCE, "Taux (dernière synchronisation) : %,.0f FCFA %s", taux, suffixe) : "");
        // Même employé (liste rechargée) : rien à remettre à zéro, la saisie en cours reste.
        if (s.getEmployeUniqueId() != null && s.getEmployeUniqueId().equals(employeSalaireAffiche)) return;
        employeSalaireAffiche = s.getEmployeUniqueId();
        etQuantiteSalaire.setText("");
        montantSalaireEstime = estimationSalaire(s, null);
        etMontantSalaire.setText(montantSalaireEstime != null ? String.valueOf(Math.round(montantSalaireEstime)) : "");
    }

    /** Montant estimé, arrondi au franc : taux de la dernière synchro (x quantité hors
     * mensuel). Le serveur refait le calcul au taux de la période payée. */
    private static Double estimationSalaire(SalaireSelectResponse s, Double quantite) {
        if (s == null || s.getTauxBase() == null) return null;
        if ("MENSUEL".equals(s.getModePaiement())) return (double) Math.round(s.getTauxBase());
        if (quantite == null || quantite <= 0) return null;
        return (double) Math.round(s.getTauxBase() * quantite);
    }

    /** Le montant suit l'estimation tant que l'utilisateur ne l'a pas changé lui-même. */
    private void recalculerMontantSalaire() {
        SalaireSelectResponse s = getSelectedSalaireEmploye();
        if (s == null || "MENSUEL".equals(s.getModePaiement())) return;
        Double avant = montantSalaireEstime;
        Double saisi = parseDoubleOrNull(etMontantSalaire.getText());
        boolean suitEstimation = saisi == null || (avant != null && Math.round(saisi) == Math.round(avant));
        montantSalaireEstime = estimationSalaire(s, parseDoubleOrNull(etQuantiteSalaire.getText()));
        if (suitEstimation) {
            etMontantSalaire.setText(montantSalaireEstime != null ? String.valueOf(Math.round(montantSalaireEstime)) : "");
        }
    }

    private void selectEmployeSalaireByUniqueId(String uniqueId) {
        if (uniqueId == null) return;
        pendingEmployeSalaireSelection = uniqueId;
        applyPendingEmployeSalaireSelection();
    }

    private void applyPendingEmployeSalaireSelection() {
        if (pendingEmployeSalaireSelection == null) return;
        for (int i = 0; i < salaires.size(); i++) {
            if (pendingEmployeSalaireSelection.equals(salaires.get(i).getEmployeUniqueId())) {
                spinnerEmployeSalaire.setText(salaires.get(i).getEmployeNom(), false);
                applySalaireEmployeSelectionne();
                return;
            }
        }
    }

    /** Magasins de STOCKAGE de la ferme (Collecte œufs, obligatoire) — déjà filtrés
     * côté serveur au type STOCKAGE (voir DataApi.getMagasinsSelect). Pas d'option
     * "Aucun" : le magasin de stockage est obligatoire. Même schéma cache → réseau
     * que loadMagasins(). */
    private void loadMagasinsStockage() {
        String cacheKey = CachePrefetcher.CACHE_MAGASINS_STOCKAGE_SELECT;
        String cachedJson = localDatabase.getCache(cacheKey);
        if (cachedJson != null) {
            Type listType = new TypeToken<List<MagasinSelectResponse>>() {}.getType();
            List<MagasinSelectResponse> parsed = gson.fromJson(cachedJson, listType);
            if (parsed != null) {
                batimentsStockage = parsed;
                populateBatimentStockageSpinner();
            }
        }

        ApiClient.dataApi(this).getMagasinsSelect("STOCKAGE").enqueue(new Callback<ApiEnvelope<List<MagasinSelectResponse>>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<List<MagasinSelectResponse>>> call, Response<ApiEnvelope<List<MagasinSelectResponse>>> response) {
                List<MagasinSelectResponse> data = response.isSuccessful() && response.body() != null ? response.body().getData() : null;
                if (data != null) {
                    localDatabase.putCache(cacheKey, gson.toJson(data));
                    batimentsStockage = data;
                    populateBatimentStockageSpinner();
                }
                // sinon : magasins déjà affichés depuis le cache le cas échéant, rien à faire
            }

            @Override
            public void onFailure(Call<ApiEnvelope<List<MagasinSelectResponse>>> call, Throwable t) {
                // magasins déjà affichés depuis le cache le cas échéant, rien à faire de plus
            }
        });
    }

    private void populateBatimentStockageSpinner() {
        List<String> labels = new ArrayList<>();
        for (MagasinSelectResponse b : batimentsStockage) {
            labels.add(b.getNom());
        }
        // Présélection seulement s'il n'y a qu'un magasin de stockage (voir populateMagasinSpinners).
        String avant = spinnerBatimentStockage.getText().toString();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, labels);
        spinnerBatimentStockage.setAdapter(adapter);
        String apres = labels.contains(avant) ? avant : (labels.size() == 1 ? labels.get(0) : "");
        spinnerBatimentStockage.setText(apres, false);
        if (!apres.equals(avant)) loadStockBatimentStockageSelectionne();
        applyPendingBatimentStockageSelection();
    }

    private String getSelectedBatimentStockageUniqueId() {
        String selected = spinnerBatimentStockage.getText().toString();
        for (MagasinSelectResponse b : batimentsStockage) {
            if (b.getNom().equals(selected)) return b.getUniqueId();
        }
        return null;
    }

    /** Même raisonnement que selectBatimentByUniqueId/applyPendingBatimentSelection —
     * voir leur commentaire pour la course entre prefillFromExisting() (synchrone) et
     * loadMagasinsStockage() (réseau asynchrone). */
    private void selectBatimentStockageByUniqueId(String uniqueId) {
        if (uniqueId == null) return;
        pendingBatimentStockageSelection = uniqueId;
        applyPendingBatimentStockageSelection();
    }

    private void applyPendingBatimentStockageSelection() {
        if (pendingBatimentStockageSelection == null) return;
        for (int i = 0; i < batimentsStockage.size(); i++) {
            if (pendingBatimentStockageSelection.equals(batimentsStockage.get(i).getUniqueId())) {
                spinnerBatimentStockage.setText(batimentsStockage.get(i).getNom(), false);
                // Appelé explicitement (pas seulement via OnItemClickListener) car
                // AutoCompleteTextView.setText(..., false) ne déclenche jamais le
                // listener (à la différence de Spinner.setSelection() qui le fait sauf
                // quand la position ne change pas).
                loadStockBatimentStockageSelectionne();
                return;
            }
        }
    }

    /** Stock d'œufs pas encore transféré vers un magasin, dans le bâtiment de stockage
     * sélectionné — pure info contextuelle (voir tvStockBatimentStockage dans le
     * layout), ne plafonne rien : une collecte AJOUTE au stock, elle ne le consomme
     * pas. Même schéma cache → réseau que loadStockForMagasin. */
    private void loadStockBatimentStockageSelectionne() {
        String batimentUniqueId = getSelectedBatimentStockageUniqueId();
        if (batimentUniqueId == null) {
            tvStockBatimentStockage.setText("");
            return;
        }

        String cacheKey = CachePrefetcher.CACHE_STOCK_MAGASIN_STOCKAGE_PREFIX + batimentUniqueId;
        Integer cached = getCachedOrNull(cacheKey, Integer.class);
        boolean hadCache = cached != null;
        if (hadCache) displayStockBatimentStockage(cached, true);

        ApiClient.dataApi(this).getDisponibleMagasinStockage(batimentUniqueId).enqueue(new Callback<ApiEnvelope<Integer>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<Integer>> call, Response<ApiEnvelope<Integer>> response) {
                Integer stock = response.isSuccessful() && response.body() != null ? response.body().getData() : null;
                if (stock != null) {
                    localDatabase.putCache(cacheKey, gson.toJson(stock));
                    displayStockBatimentStockage(stock, false);
                } else if (!hadCache) {
                    tvStockBatimentStockage.setText("");
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<Integer>> call, Throwable t) {
                if (!hadCache) tvStockBatimentStockage.setText("");
            }
        });
    }

    private void displayStockBatimentStockage(int stock, boolean fromCache) {
        String suffix = fromCache ? " (dernière donnée connue, hors ligne)" : "";
        tvStockBatimentStockage.setText(String.format(Locale.FRANCE, "Actuellement dans ce magasin : %s%s",
                AlveoleUtils.formatOeufsAvecAlveoles(stock), suffix));
    }

    /** Le stock qui plafonne la vente est celui DU MAGASIN sélectionné, pas de toute
     * la ferme (vrai stock séparé par magasin) — déclenché à chaque changement de
     * sélection des spinners magasin (voir bindViews) et par applyPendingMagasinSelection. */
    private void loadStockPourMagasinSelectionne() {
        // Une commande vise aussi un magasin (spinnerMagasinCommande) mais son stock n'a
        // pas d'affichage dédié dans groupCommande (juste informatif sur une vente) —
        // rien à faire ici pour ce type.
        if (type != SaisieType.VENTE_OEUFS && type != SaisieType.VENTE_REFORME) return;
        AutoCompleteTextView spinner = (type == SaisieType.VENTE_OEUFS) ? spinnerMagasinOeufs : spinnerMagasinReforme;
        String magasinUniqueId = getSelectedMagasinUniqueId(spinner);
        if (magasinUniqueId == null) {
            displayStockMagasin(null, false);
            return;
        }
        loadStockForMagasin(magasinUniqueId);
    }

    private void loadStockForMagasin(String magasinUniqueId) {
        String cacheKey = CachePrefetcher.CACHE_STOCK_MAGASIN_PREFIX + magasinUniqueId;
        StockMagasinResponse cached = getCachedOrNull(cacheKey, StockMagasinResponse.class);
        boolean hadCache = cached != null;
        if (hadCache) displayStockMagasin(cached, true);

        ApiClient.dataApi(this).getStockMagasin(magasinUniqueId).enqueue(new Callback<ApiEnvelope<StockMagasinResponse>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<StockMagasinResponse>> call, Response<ApiEnvelope<StockMagasinResponse>> response) {
                StockMagasinResponse stock = response.isSuccessful() && response.body() != null ? response.body().getData() : null;
                if (stock != null) {
                    localDatabase.putCache(cacheKey, gson.toJson(stock));
                    displayStockMagasin(stock, false);
                } else if (!hadCache) {
                    displayStockMagasin(null, false);
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<StockMagasinResponse>> call, Throwable t) {
                if (!hadCache) displayStockMagasin(null, true);
            }
        });
    }

    private boolean isVenteOeufsCasse() {
        return radioGroupTypeOeufVente != null && radioGroupTypeOeufVente.getCheckedRadioButtonId() == R.id.radioTypeOeufCasse;
    }

    /** Rejoue l'affichage du stock (et stockOeufsDisponible, utilisé par onValider pour
     * le garde-fou client) selon le type bon/cassé actuellement sélectionné — appelé à
     * la fois quand un nouveau stock arrive du serveur ET quand l'utilisateur bascule
     * le RadioGroup (le stock des deux types est déjà connu, pas besoin de re-fetch). */
    private void refreshStockOeufsAffiche() {
        displayStockMagasin(dernierStockMagasinOeufs, false);
    }

    private void displayStockMagasin(StockMagasinResponse stock, boolean fromCache) {
        String suffix = fromCache ? " (dernière donnée connue, hors ligne)" : "";
        if (type == SaisieType.VENTE_OEUFS) {
            dernierStockMagasinOeufs = stock;
            boolean casse = isVenteOeufsCasse();
            stockOeufsDisponible = stock != null ? (casse ? stock.getOeufsCassesDisponible() : stock.getOeufsDisponible()) : null;
            if (stockOeufsDisponible != null) {
                tvStockOeufsInfo.setText(String.format(Locale.FRANCE, "Disponible %sdans ce magasin : %s%s",
                        casse ? "(cassés) " : "", AlveoleUtils.formatOeufsAvecAlveoles(stockOeufsDisponible), suffix));
            } else {
                tvStockOeufsInfo.setText(fromCache ? "Stock non disponible (hors ligne)" : "Stock non disponible");
            }
        } else if (type == SaisieType.LIVRAISON_COMMANDE) {
            boolean oeufs = commandeLivree.estOeufs();
            stockLivraisonDisponible = stock != null ? (oeufs ? stock.getOeufsDisponible() : stock.getReformeDisponible()) : null;
            if (stockLivraisonDisponible != null) {
                int dispo = stockLivraisonDisponible - (oeufs
                        ? ventesOeufsEnAttente(commandeLivree.magasinUniqueId, false)
                        : ventesReformeEnAttente(commandeLivree.magasinUniqueId) - reformesEnAttenteVers(commandeLivree.magasinUniqueId));
                tvStockLivraison.setText("Disponible dans le magasin : " + formatQuantiteLivraison(Math.max(0, dispo))
                        + (fromCache ? " (dernière donnée connue, hors ligne)" : ""));
            } else {
                tvStockLivraison.setText(fromCache ? "Stock du magasin non disponible (hors ligne)" : "Stock du magasin non disponible");
            }
        } else if (type == SaisieType.VENTE_REFORME) {
            stockReformeDisponible = stock != null ? stock.getReformeDisponible() : null;
            if (stockReformeDisponible != null) {
                tvStockReformeInfo.setText(String.format(Locale.FRANCE, "Disponible dans ce magasin : %d sujet(s)%s", stockReformeDisponible, suffix));
            } else {
                tvStockReformeInfo.setText(fromCache ? "Stock non disponible (hors ligne)" : "Stock non disponible");
            }
        }
        refreshCoherence();
    }

    /**
     * Effectif vivant DU PROJET sélectionné (nbSujets - mortalité - déjà réformés) —
     * plafond de la saisie Réforme (Production), même schéma cache → réseau que
     * loadStockForMagasin() plus bas.
     */
    private void loadEffectifReforme() {
        String cacheKey = CachePrefetcher.CACHE_EFFECTIF_REFORME_PREFIX + projetUniqueId;
        EffectifReformeResponse cached = getCachedOrNull(cacheKey, EffectifReformeResponse.class);
        boolean hadCache = cached != null;
        if (hadCache) displayEffectifReforme(cached, true);

        ApiClient.dataApi(this).getEffectifReforme(projetUniqueId).enqueue(new Callback<ApiEnvelope<EffectifReformeResponse>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<EffectifReformeResponse>> call, Response<ApiEnvelope<EffectifReformeResponse>> response) {
                EffectifReformeResponse effectif = response.isSuccessful() && response.body() != null ? response.body().getData() : null;
                if (effectif != null) {
                    localDatabase.putCache(cacheKey, gson.toJson(effectif));
                    displayEffectifReforme(effectif, false);
                } else if (!hadCache) {
                    displayEffectifReforme(null, false);
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<EffectifReformeResponse>> call, Throwable t) {
                if (!hadCache) displayEffectifReforme(null, true);
            }
        });
    }

    private void displayEffectifReforme(EffectifReformeResponse effectif, boolean fromCache) {
        effectifReformeDisponible = effectif != null ? effectif.getEffectifVivant() : null;
        if (effectifReformeDisponible != null) {
            String suffix = fromCache ? " (dernière donnée connue, hors ligne)" : "";
            tvEffectifReformeInfo.setText(String.format(Locale.FRANCE, "Sujets vivants disponibles : %d%s", effectifReformeDisponible, suffix));
        } else {
            tvEffectifReformeInfo.setText(fromCache ? "Effectif non disponible (hors ligne)" : "Effectif non disponible");
        }
        refreshCoherence();
    }

    // ===================== Alertes de cohérence en direct =====================
    // Le téléphone raisonne sur SES données : la dernière situation connue du serveur
    // (préchargée avec le reste, voir CachePrefetcher, donc valable hors ligne) MOINS
    // ce que ce téléphone a saisi et pas encore envoyé, que le serveur ne peut pas
    // connaître (deux collectes du même jour saisies hors ligne, mortalité en attente...).
    // Le serveur refait le contrôle à l'enregistrement/à la synchro. Une donnée inconnue
    // (jamais préchargée) ne bloque rien.
    private TextView tvAlerteCoherence;
    private PlafondSaisieResponse plafondBase;
    private String plafondCleDemandee;

    private void setupCoherenceChecks() {
        tvAlerteCoherence = findViewById(R.id.tvAlerteCoherence);
        TextWatcher valeurs = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { refreshCoherence(); }
        };
        TextInputEditText[] champs = {etAlveolesCollectees, etOeufsCollectes, etOeufsCasses, etOeufsNonUtilisables,
                etNombreMorts, etNombreSujetsReforme, etQuantiteKgConso, etQuantiteOeufsVente, etNombreSujetsVente};
        for (TextInputEditText et : champs) {
            if (et != null) et.addTextChangedListener(valeurs);
        }
        // Le plafond dépend du poulailler et de la date choisis.
        TextWatcher contexte = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { loadPlafondSaisie(); }
        };
        etDate.addTextChangedListener(contexte);
        spinnerBatiment.addTextChangedListener(contexte);
        loadPlafondSaisie();
    }

    /** Situation serveur (effectif vivant du poulailler ou du projet, œufs déjà collectés)
     * pour Collecte et Mortalité : le cache local est lu d'abord et suffit hors ligne ;
     * un appel réseau, s'il aboutit, ne fait que le rafraîchir. */
    private void loadPlafondSaisie() {
        // Réforme aussi : le serveur plafonne par l'effectif vivant du poulailler en plus de
        // celui du projet (ReformeImpl.validerEffectifPoulailler).
        if ((type != SaisieType.COLLECTE_OEUFS && type != SaisieType.MORTALITE && type != SaisieType.REFORME)
                || projetUniqueId == null || projetUniqueId.isEmpty()) return;
        String batiment = getSelectedBatimentUniqueId();
        String date = textOf(etDate);
        if (date.isEmpty()) return;
        String cle = projetUniqueId + "|" + batiment + "|" + date;
        if (cle.equals(plafondCleDemandee)) return;
        plafondCleDemandee = cle;
        String cacheKey = CachePrefetcher.CACHE_PLAFOND_PREFIX + projetUniqueId + "_" + batiment;
        plafondBase = getCachedOrNull(cacheKey, PlafondSaisieResponse.class);
        refreshCoherence();

        ApiClient.dataApi(this).getPlafondSaisie(projetUniqueId, batiment, date)
                .enqueue(new Callback<ApiEnvelope<PlafondSaisieResponse>>() {
                    @Override
                    public void onResponse(Call<ApiEnvelope<PlafondSaisieResponse>> call, Response<ApiEnvelope<PlafondSaisieResponse>> response) {
                        if (!cle.equals(plafondCleDemandee)) return; // réponse d'un choix périmé
                        PlafondSaisieResponse p = response.isSuccessful() && response.body() != null ? response.body().getData() : null;
                        if (p != null) {
                            p.setCacheDate(date);
                            localDatabase.putCache(cacheKey, gson.toJson(p));
                            plafondBase = p;
                            refreshCoherence();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiEnvelope<PlafondSaisieResponse>> call, Throwable t) {
                        // Hors ligne : le plafond en cache reste utilisé.
                    }
                });
    }

    /** Saisies de ce téléphone qui partiront au prochain envoi et consommeront donc le
     * plafond côté serveur (voir SaisieLocale.seraRenvoyee) : pas encore envoyées, ou en
     * échec temporaire (réseau, serveur, session expirée). Un refus définitif (400) ne
     * compte pas : il doit d'abord être corrigé. Comprend celles des autres comptes de la
     * même ferme présents sur ce téléphone (voir LocalDatabase.getSaisiesPourControles). */
    private List<SaisieLocale> saisiesEnAttente(SaisieType t) {
        List<SaisieLocale> res = new ArrayList<>();
        for (SaisieLocale s : localDatabase.getSaisiesPourControles(t)) {
            // En modification, la saisie éditée ne doit pas se compter contre elle-même.
            if (s.seraRenvoyee()
                    && (editingLocalId == null || !editingLocalId.equals(s.getLocalId()))) res.add(s);
        }
        return res;
    }

    private static boolean memeBatiment(String voulu, String saisi) {
        return voulu == null || voulu.equals(saisi);
    }

    private int mortsEnAttente(String batiment) {
        int total = 0;
        for (SaisieLocale s : saisiesEnAttente(SaisieType.MORTALITE)) {
            MortaliteCreateRequest r = gson.fromJson(s.getPayloadJson(), MortaliteCreateRequest.class);
            if (r != null && projetUniqueId.equals(r.projetUniqueId) && memeBatiment(batiment, r.batimentUniqueId) && r.nombreMorts != null) total += r.nombreMorts;
        }
        return total;
    }

    private int reformesEnAttente(String batiment) {
        int total = 0;
        for (SaisieLocale s : saisiesEnAttente(SaisieType.REFORME)) {
            ReformeCreateRequest r = gson.fromJson(s.getPayloadJson(), ReformeCreateRequest.class);
            if (r != null && projetUniqueId.equals(r.projetUniqueId) && memeBatiment(batiment, r.batimentUniqueId) && r.nombreSujets != null) total += r.nombreSujets;
        }
        return total;
    }

    private int collectesEnAttente(String batiment, String date) {
        int total = 0;
        for (SaisieLocale s : saisiesEnAttente(SaisieType.COLLECTE_OEUFS)) {
            CollecteOeufsCreateRequest r = gson.fromJson(s.getPayloadJson(), CollecteOeufsCreateRequest.class);
            if (r != null && projetUniqueId.equals(r.projetUniqueId) && date.equals(r.date)
                    && memeBatiment(batiment, r.batimentUniqueId) && r.oeufsCollectes != null) total += r.oeufsCollectes;
        }
        return total;
    }

    private double consommationsEnAttente() {
        double total = 0;
        for (SaisieLocale s : saisiesEnAttente(SaisieType.ALIMENTATION_CONSOMMATION)) {
            ConsommationAlimentCreateRequest r = gson.fromJson(s.getPayloadJson(), ConsommationAlimentCreateRequest.class);
            if (r != null && projetUniqueId.equals(r.projetUniqueId) && r.quantiteKg != null) total += r.quantiteKg;
        }
        return total;
    }

    /** Achats d'aliment de ce projet pas encore envoyés : ils comptent déjà comme stock. */
    private double achatsAlimentEnAttente() {
        double total = 0;
        for (SaisieLocale s : saisiesEnAttente(SaisieType.ALIMENTATION_ACHAT)) {
            if (!projetUniqueId.equals(s.getProjetUniqueId())) continue;
            AlimentationCreateRequest r = gson.fromJson(s.getPayloadJson(), AlimentationCreateRequest.class);
            if (r != null && r.quantiteKg != null) total += r.quantiteKg;
        }
        return total;
    }

    private int ventesOeufsEnAttente(String magasin, boolean casse) {
        int total = 0;
        for (SaisieLocale s : saisiesEnAttente(SaisieType.VENTE_OEUFS)) {
            VenteOeufsCreateRequest r = gson.fromJson(s.getPayloadJson(), VenteOeufsCreateRequest.class);
            boolean rCasse = r != null && "CASSE".equalsIgnoreCase(r.typeOeuf);
            if (r != null && magasin != null && magasin.equals(r.magasinUniqueId) && rCasse == casse && r.quantiteOeufs != null) total += r.quantiteOeufs;
        }
        // Les livraisons de commande en attente puisent aussi dans ce magasin (œufs bons).
        if (!casse) total += CommandesHorsLigne.quantiteEnAttenteMagasin(localDatabase, magasin, true, editingLocalId);
        return total;
    }

    private int ventesReformeEnAttente(String magasin) {
        int total = 0;
        for (SaisieLocale s : saisiesEnAttente(SaisieType.VENTE_REFORME)) {
            VenteReformeCreateRequest r = gson.fromJson(s.getPayloadJson(), VenteReformeCreateRequest.class);
            if (r != null && magasin != null && magasin.equals(r.magasinUniqueId) && r.nombreSujets != null) total += r.nombreSujets;
        }
        total += CommandesHorsLigne.quantiteEnAttenteMagasin(localDatabase, magasin, false, editingLocalId);
        return total;
    }

    /** Sujets des réformes pas encore envoyées qui iront dans ce point de vente (choisi
     * dans la réforme, ou point de vente par défaut pour une réforme sans choix). */
    private int reformesEnAttenteVers(String magasin) {
        if (magasin == null) return 0;
        int total = 0;
        for (SaisieLocale s : saisiesEnAttente(SaisieType.REFORME)) {
            ReformeCreateRequest r = gson.fromJson(s.getPayloadJson(), ReformeCreateRequest.class);
            if (r == null || r.nombreSujets == null) continue;
            if (magasin.equals(PointDeVenteReformes.destination(r.magasinVenteUniqueId, localDatabase))) total += r.nombreSujets;
        }
        return total;
    }

    /** Stock de réformés du point de vente vu du téléphone : dernier stock connu du serveur
     * + réformes en attente vers ce point de vente - ventes et livraisons en attente depuis
     * lui. null si le stock du point de vente n'a jamais été chargé (rien à contrôler). */
    private Integer stockReformeAuPointDeVente(String magasin) {
        if (stockReformeDisponible == null || magasin == null) return null;
        return stockReformeDisponible + reformesEnAttenteVers(magasin) - ventesReformeEnAttente(magasin);
    }

    private static String messageStockReformeInsuffisant(int stock) {
        return "Stock de réformés insuffisant au point de vente : " + Math.max(0, stock) + " sujet(s)";
    }

    /** Points de vente (cache puis réseau) pour le sélecteur de la saisie Réforme ; les
     * magasins de stockage servent seulement à déduire le point de vente par défaut. */
    private void loadPointsDeVenteReforme() {
        pointsDeVenteReforme = PointDeVenteReformes.pointsDeVente(localDatabase);
        populatePointsDeVenteReforme();
        ApiClient.dataApi(this).getMagasinsSelect("STOCKAGE").enqueue(new Callback<ApiEnvelope<List<MagasinSelectResponse>>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<List<MagasinSelectResponse>>> call, Response<ApiEnvelope<List<MagasinSelectResponse>>> response) {
                List<MagasinSelectResponse> data = response.isSuccessful() && response.body() != null ? response.body().getData() : null;
                if (data != null) {
                    localDatabase.putCache(CachePrefetcher.CACHE_MAGASINS_STOCKAGE_SELECT, gson.toJson(data));
                    populatePointsDeVenteReforme();
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<List<MagasinSelectResponse>>> call, Throwable t) { }
        });
        ApiClient.dataApi(this).getMagasinsSelect("VENTE").enqueue(new Callback<ApiEnvelope<List<MagasinSelectResponse>>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<List<MagasinSelectResponse>>> call, Response<ApiEnvelope<List<MagasinSelectResponse>>> response) {
                List<MagasinSelectResponse> data = response.isSuccessful() && response.body() != null ? response.body().getData() : null;
                if (data != null) {
                    localDatabase.putCache(CachePrefetcher.CACHE_MAGASINS_SELECT, gson.toJson(data));
                    pointsDeVenteReforme = data;
                    populatePointsDeVenteReforme();
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<List<MagasinSelectResponse>>> call, Throwable t) { }
        });
    }

    /** Sélecteur masqué avec zéro ou un point de vente ; sinon visible, choix déjà fait
     * gardé, sinon celui de la saisie modifiée, sinon le point de vente par défaut. */
    private void populatePointsDeVenteReforme() {
        if (spinnerPointDeVenteReforme == null) return;
        List<String> labels = new ArrayList<>();
        for (MagasinSelectResponse m : pointsDeVenteReforme) labels.add(m.getNom());
        tilPointDeVenteReforme.setVisibility(labels.size() > 1 ? View.VISIBLE : View.GONE);
        String avant = spinnerPointDeVenteReforme.getText().toString();
        spinnerPointDeVenteReforme.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, labels));
        String voulu = pendingPointDeVenteReforme != null ? pendingPointDeVenteReforme
                : PointDeVenteReformes.parDefaut(pointsDeVenteReforme, PointDeVenteReformes.stockages(localDatabase));
        String apres = labels.contains(avant) && pendingPointDeVenteReforme == null ? avant : "";
        if (apres.isEmpty() && voulu != null) {
            for (MagasinSelectResponse m : pointsDeVenteReforme) {
                if (voulu.equals(m.getUniqueId())) { apres = m.getNom(); pendingPointDeVenteReforme = null; break; }
            }
        }
        spinnerPointDeVenteReforme.setText(apres, false);
    }

    /** Point de vente envoyé avec la réforme (null : aucun point de vente connu, le
     * serveur applique sa règle par défaut). */
    private String pointDeVenteReformeChoisi() {
        if (pointsDeVenteReforme.size() == 1) return pointsDeVenteReforme.get(0).getUniqueId();
        String selected = spinnerPointDeVenteReforme.getText().toString();
        for (MagasinSelectResponse m : pointsDeVenteReforme) {
            if (m.getNom().equals(selected)) return m.getUniqueId();
        }
        // Liste pas encore chargée mais saisie modifiée qui avait un point de vente.
        return pointsDeVenteReforme.isEmpty() ? pendingPointDeVenteReforme : null;
    }

    /** Marque un champ en erreur (contour rouge) sans texte : le message complet est dans
     * tvAlerteCoherence, un demi-champ étant trop étroit pour le porter. */
    private void marquerChamp(TextInputEditText et, boolean enErreur) {
        if (et == null) return;
        android.view.ViewParent p = et.getParent();
        while (p != null && !(p instanceof TextInputLayout)) p = p.getParent();
        if (p instanceof TextInputLayout) ((TextInputLayout) p).setError(enErreur ? " " : null);
    }

    private void refreshCoherence() {
        if (tvAlerteCoherence == null) return;
        String message = null;
        TextInputEditText[] fautifs = {};
        switch (type) {
            case COLLECTE_OEUFS: {
                int total = oeufsCollectesReel();
                int casses = parseIntSafe(etOeufsCasses.getText());
                int nonUtilisables = parseIntSafe(etOeufsNonUtilisables.getText());
                if (plafondBase != null && plafondBase.getEffectifVivant() != null && total > 0) {
                    boolean parBatiment = "BATIMENT".equals(plafondBase.getPerimetre());
                    String batiment = parBatiment ? getSelectedBatimentUniqueId() : null;
                    String date = textOf(etDate);
                    int effectif = plafondBase.getEffectifVivant() - mortsEnAttente(batiment) - reformesEnAttente(batiment);
                    // Déjà collecté ce jour-là côté serveur : valable seulement pour le jour où la
                    // donnée a été calculée. + ce que ce téléphone a saisi et pas encore envoyé.
                    int dejaServeur = date.equals(plafondBase.getCacheDate()) && plafondBase.getOeufsDejaCollectes() != null
                            ? plafondBase.getOeufsDejaCollectes() : 0;
                    int deja = dejaServeur + collectesEnAttente(batiment, date);
                    int restants = Math.max(0, effectif - deja);
                    if (total > restants) {
                        String lieu = parBatiment ? "ce poulailler" : "ce projet";
                        message = String.format(Locale.FRANCE,
                                "Impossible : %d œufs saisis (%s), alors que %s n'a que %d poule(s) vivante(s), donc au plus %d œufs par jour%s. Il en reste %d à collecter au maximum. Vérifiez le nombre d'alvéoles.",
                                total, AlveoleUtils.formatOeufsAvecAlveoles(total), lieu, Math.max(0, effectif), Math.max(0, effectif),
                                deja > 0 ? String.format(Locale.FRANCE, " (%d déjà collectés ce jour)", deja) : "", restants);
                        fautifs = new TextInputEditText[]{etAlveolesCollectees, etOeufsCollectes};
                    }
                }
                if (message == null && casses + nonUtilisables > total) {
                    message = String.format(Locale.FRANCE,
                            "Impossible : %d cassé(s) + %d non utilisable(s) dépassent les %d œuf(s) collecté(s). Les cassés et non utilisables sont des œufs parmi ceux collectés.",
                            casses, nonUtilisables, total);
                    fautifs = new TextInputEditText[]{etOeufsCasses, etOeufsNonUtilisables};
                }
                break;
            }
            case MORTALITE: {
                int morts = parseIntSafe(etNombreMorts.getText());
                if (plafondBase != null && plafondBase.getEffectifVivant() != null && morts > 0) {
                    boolean parBatiment = "BATIMENT".equals(plafondBase.getPerimetre());
                    String batiment = parBatiment ? getSelectedBatimentUniqueId() : null;
                    int effectif = plafondBase.getEffectifVivant() - mortsEnAttente(batiment) - reformesEnAttente(batiment);
                    if (morts > effectif) {
                        String lieu = parBatiment ? "ce poulailler" : "le projet";
                        message = String.format(Locale.FRANCE,
                                "Impossible : %d morts saisis, alors qu'il n'y a que %d sujet(s) vivant(s) dans %s (%d de trop).",
                                morts, Math.max(0, effectif), lieu, morts - Math.max(0, effectif));
                        fautifs = new TextInputEditText[]{etNombreMorts};
                    }
                }
                break;
            }
            case REFORME: {
                int nombre = parseIntSafe(etNombreSujetsReforme.getText());
                if (effectifReformeDisponible != null && nombre > 0) {
                    int effectif = effectifReformeDisponible - mortsEnAttente(null) - reformesEnAttente(null);
                    if (nombre > effectif) {
                        message = String.format(Locale.FRANCE,
                                "Impossible : %d sujets réformés saisis, alors qu'il n'y a que %d sujet(s) vivant(s) dans le projet (%d de trop).",
                                nombre, Math.max(0, effectif), nombre - Math.max(0, effectif));
                        fautifs = new TextInputEditText[]{etNombreSujetsReforme};
                    }
                }
                // Effectif vivant du poulailler choisi (même contrôle que le serveur), morts et
                // réformes en attente de ce poulailler déduits.
                if (message == null && nombre > 0 && plafondBase != null && plafondBase.getEffectifVivant() != null
                        && "BATIMENT".equals(plafondBase.getPerimetre())) {
                    String batiment = getSelectedBatimentUniqueId();
                    int effectif = plafondBase.getEffectifVivant() - mortsEnAttente(batiment) - reformesEnAttente(batiment);
                    if (nombre > effectif) {
                        message = String.format(Locale.FRANCE,
                                "Impossible : %d sujets réformés saisis, alors qu'il n'y a que %d sujet(s) vivant(s) dans ce poulailler (%d de trop).",
                                nombre, Math.max(0, effectif), nombre - Math.max(0, effectif));
                        fautifs = new TextInputEditText[]{etNombreSujetsReforme};
                    }
                }
                break;
            }
            case ALIMENTATION_CONSOMMATION: {
                Double kg = parseDoubleOrNull(etQuantiteKgConso.getText());
                if (kg != null && stockAlimentRestantConnu != null) {
                    double stock = stockAlimentRestantConnu + achatsAlimentEnAttente() - consommationsEnAttente();
                    if (kg > stock) {
                        message = String.format(Locale.FRANCE,
                                "Impossible : %.1f kg saisis, alors qu'il ne reste que %.1f kg de stock pour ce projet. Si un achat n'est pas encore enregistré, il doit l'être par la comptabilité.",
                                kg, Math.max(0, stock));
                        fautifs = new TextInputEditText[]{etQuantiteKgConso};
                    }
                }
                break;
            }
            case VENTE_OEUFS: {
                int saisi = parseIntSafe(etQuantiteOeufsVente.getText());
                int enOeufs = isVenteOeufsEnAlveoles() ? AlveoleUtils.alveolesToOeufs(saisi) : saisi;
                if (stockOeufsDisponible != null && enOeufs > 0) {
                    boolean casse = isVenteOeufsCasse();
                    int stock = stockOeufsDisponible - ventesOeufsEnAttente(getSelectedMagasinUniqueId(spinnerMagasinOeufs), casse);
                    if (enOeufs > stock) {
                        message = String.format(Locale.FRANCE,
                                "Impossible : %d œufs saisis (%s), alors que le stock %sde ce magasin n'est que de %d œufs (%s). Vérifiez l'unité (œuf ou alvéole).",
                                enOeufs, AlveoleUtils.formatOeufsAvecAlveoles(enOeufs), casse ? "d'œufs cassés " : "",
                                Math.max(0, stock), AlveoleUtils.formatOeufsAvecAlveoles(Math.max(0, stock)));
                        fautifs = new TextInputEditText[]{etQuantiteOeufsVente};
                    }
                }
                break;
            }
            case VENTE_REFORME: {
                int nombre = parseIntSafe(etNombreSujetsVente.getText());
                Integer stock = stockReformeAuPointDeVente(getSelectedMagasinUniqueId(spinnerMagasinReforme));
                if (stock != null && nombre > 0 && nombre > stock) {
                    message = messageStockReformeInsuffisant(stock);
                    fautifs = new TextInputEditText[]{etNombreSujetsVente};
                }
                break;
            }
            case LIVRAISON_COMMANDE: {
                int q = quantiteLivraison();
                if (q <= 0 || commandeLivree == null) break;
                int reste = resteLivrable();
                if (q > reste) {
                    message = String.format(Locale.FRANCE,
                            "Impossible : %s saisis, alors qu'il ne reste que %s à livrer sur cette commande%s.",
                            formatQuantiteLivraison(q), formatQuantiteLivraison(Math.max(0, reste)),
                            reste < commandeLivree.resteServeur() ? " (livraisons en attente d'envoi comprises)" : "");
                    fautifs = new TextInputEditText[]{etQuantiteLivraison};
                } else if (stockLivraisonDisponible != null) {
                    boolean oeufs = commandeLivree.estOeufs();
                    int stock = stockLivraisonDisponible - (oeufs
                            ? ventesOeufsEnAttente(commandeLivree.magasinUniqueId, false)
                            : ventesReformeEnAttente(commandeLivree.magasinUniqueId));
                    if (q > stock) {
                        message = String.format(Locale.FRANCE,
                                "Impossible : %s saisis, alors que le stock du magasin de la commande n'est que de %s (ventes et livraisons en attente déduites).",
                                formatQuantiteLivraison(q), formatQuantiteLivraison(Math.max(0, stock)));
                        fautifs = new TextInputEditText[]{etQuantiteLivraison};
                    }
                }
                break;
            }
            default:
                break;
        }

        TextInputEditText[] tous = {etAlveolesCollectees, etOeufsCollectes, etOeufsCasses, etOeufsNonUtilisables,
                etNombreMorts, etNombreSujetsReforme, etQuantiteKgConso, etQuantiteOeufsVente, etNombreSujetsVente,
                etQuantiteLivraison};
        for (TextInputEditText et : tous) marquerChamp(et, false);
        for (TextInputEditText et : fautifs) marquerChamp(et, true);

        tvAlerteCoherence.setText(message);
        tvAlerteCoherence.setVisibility(message != null ? View.VISIBLE : View.GONE);
        btnValiderForm.setEnabled(message == null);
    }

    private int parseIntSafe(CharSequence s) {
        try {
            return s == null || s.length() == 0 ? 0 : Integer.parseInt(s.toString().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Double parseDoubleOrNull(CharSequence s) {
        try {
            return s == null || s.length() == 0 ? null : Double.parseDouble(s.toString().trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private java.util.List<String> selectedModesAdministration() {
        java.util.List<String> modes = new java.util.ArrayList<>();
        if (cbModeOral.isChecked()) modes.add("Oral");
        if (cbModeInjection.isChecked()) modes.add("Injection");
        if (cbModePulverisation.isChecked()) modes.add("Pulvérisation");
        if (cbModeTopique.isChecked()) modes.add("Topique");
        return modes;
    }

    private void setModesAdministration(java.util.List<String> modes) {
        cbModeOral.setChecked(modes != null && modes.contains("Oral"));
        cbModeInjection.setChecked(modes != null && modes.contains("Injection"));
        cbModePulverisation.setChecked(modes != null && modes.contains("Pulvérisation"));
        cbModeTopique.setChecked(modes != null && modes.contains("Topique"));
    }

    private String textOf(TextInputEditText edit) {
        return edit.getText() != null ? edit.getText().toString().trim() : "";
    }

    private String nullIfBlank(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    /** Montant arrondi au franc entier, comme le serveur (Math.round, x,5 vers le haut). */
    private static Double arrondiFranc(Double montant) {
        return montant != null ? (double) Math.round(montant) : null;
    }

    /** Message si ce nom (sans tenir compte des majuscules) ou ce téléphone est déjà pris
     * par un client connu du téléphone ou par un nouveau client pas encore envoyé ; null
     * sinon. Mêmes règles que le serveur (ClientServiceImpl.create). */
    private String clientDoublon(String nom, String telephone) {
        String n = nom.trim();
        String t = telephone.trim();
        for (ClientSelectResponse c : lireListeCache(CachePrefetcher.CACHE_CLIENTS_SELECT, ClientSelectResponse.class)) {
            if (c.getNom() != null && c.getNom().trim().equalsIgnoreCase(n)) return "Un client portant ce nom existe déjà";
            if (c.getTelephone() != null && c.getTelephone().trim().equals(t)) return "Un client avec ce numéro de téléphone existe déjà (" + c.getNom() + ")";
        }
        for (SaisieLocale s : saisiesEnAttente(SaisieType.CLIENT_CREATE)) {
            ClientCreateRequest r = gson.fromJson(s.getPayloadJson(), ClientCreateRequest.class);
            if (r == null) continue;
            if (r.nom != null && r.nom.trim().equalsIgnoreCase(n)) return "Un client portant ce nom attend déjà d'être envoyé";
            if (r.telephone != null && r.telephone.trim().equals(t)) return "Un client avec ce numéro attend déjà d'être envoyé (" + r.nom + ")";
        }
        return null;
    }

    // ===================== VALIDATION + ENREGISTREMENT LOCAL =====================

    private void onValider() {
        typeEnregistrement = null;
        String date = textOf(etDate);
        String heure = nullIfBlank(textOf(etHeure));
        String batimentUniqueId = getSelectedBatimentUniqueId();

        // Même règle que le serveur (DateSaisie) : une saisie ne peut pas être datée dans le
        // futur. Refusée ici plutôt qu'à l'envoi, parfois des jours plus tard. La date de
        // livraison PRÉVUE d'une commande est un autre champ, elle peut être future.
        if (type != SaisieType.CLIENT_CREATE && date.compareTo(isoDate.format(new java.util.Date())) > 0) {
            toast("La date ne peut pas être dans le futur");
            return;
        }

        Object requestObject;
        String summary;

        switch (type) {
            case COLLECTE_OEUFS: {
                if (batimentUniqueId == null) {
                    toast("Veuillez sélectionner le poulailler");
                    return;
                }
                String batimentStockageUniqueId = getSelectedBatimentStockageUniqueId();
                if (batimentStockageUniqueId == null) {
                    toast("Veuillez sélectionner le magasin de stockage");
                    return;
                }
                int alveoles = parseIntSafe(etAlveolesCollectees.getText());
                int oeufsSupp = parseIntSafe(etOeufsCollectes.getText());
                int collectes = oeufsCollectesReel();
                int casses = parseIntSafe(etOeufsCasses.getText());
                int nonUtilisables = parseIntSafe(etOeufsNonUtilisables.getText());
                if (collectes <= 0) {
                    toast("Veuillez saisir le nombre d'alvéoles et/ou d'œufs collectés");
                    return;
                }
                CollecteOeufsCreateRequest req = new CollecteOeufsCreateRequest();
                req.projetUniqueId = projetUniqueId;
                req.batimentUniqueId = batimentUniqueId;
                req.magasinStockageUniqueId = batimentStockageUniqueId;
                req.date = date;
                req.heure = heure;
                req.oeufsCollectes = collectes; // toujours en œufs, alvéoles + œufs supplémentaires additionnés
                req.oeufsCasses = casses;
                req.oeufsNonUtilisables = nonUtilisables;
                requestObject = req;
                summary = alveoles > 0
                        ? String.format(Locale.FRANCE, "%d alvéole(s) + %d œufs, %d au total (%d cassés, %d non utilisables)", alveoles, oeufsSupp, collectes, casses, nonUtilisables)
                        : String.format(Locale.FRANCE, "%d œufs collectés (%d cassés, %d non utilisables)", collectes, casses, nonUtilisables);
                break;
            }
            case SOINS: {
                SoinsCreateRequest req = new SoinsCreateRequest();
                req.projetUniqueId = projetUniqueId;
                req.batimentUniqueId = batimentUniqueId;
                req.date = date;
                req.heure = heure;
                req.type = soinsTypeToWire(spinnerTypeSoin.getText().toString());
                // Commun aux deux sous-types (Médicament/Autre ET Vaccination).
                req.observations = nullIfBlank(textOf(etObservationsSoin));

                if (soinDepuisStock()) {
                    com.mobile.diafarms.network.dto.StockMedicamentResponse m = stockChoisi();
                    if (m == null) {
                        toast("Choisissez le médicament dans le stock du projet");
                        return;
                    }
                    Double qUtilisee = parseDoubleOrNull(etQuantiteSoinStock.getText());
                    if (qUtilisee == null || qUtilisee <= 0) {
                        toast("Indiquez la quantité utilisée");
                        return;
                    }
                    if (qUtilisee > m.restant + 1e-9) {
                        toast("Stock insuffisant : il reste " + formatSaisie(m.restant) + " " + m.unite + " de " + m.nom);
                        return;
                    }
                    if (batimentUniqueId == null) {
                        toast("Veuillez sélectionner le poulailler");
                        return;
                    }
                    req.produit = m.nom;
                    req.unite = m.unite;
                    req.quantite = qUtilisee;
                    req.depuisStock = true;
                    requestObject = req;
                    summary = spinnerTypeSoin.getText().toString() + " : " + m.nom + " (" + formatSaisie(qUtilisee) + " " + m.unite + ", stock)";
                    break;
                }

                if (isTypeSoinVaccination()) {
                    String nomVaccin = textOf(etNomVaccin);
                    if (nomVaccin.isEmpty()) {
                        toast("Veuillez préciser le nom du vaccin");
                        return;
                    }
                    int quantite = parseIntSafe(etQuantiteVaccin.getText());
                    if (quantite <= 0) {
                        toast("Veuillez saisir le nombre de doses");
                        return;
                    }
                    // Poulailler obligatoire, vaccination comprise.
                    if (batimentUniqueId == null) {
                        toast("Veuillez sélectionner le poulailler");
                        return;
                    }
                    req.produit = nomVaccin;
                    req.quantite = (double) quantite;
                    // Aucun montant ici : la Production ne suit que le fait, le coût réel
                    // se saisit séparément en Comptabilité (catégorie "Santé / Vétérinaire").
                    req.modeAdministration = selectedModesAdministration();
                    requestObject = req;
                    summary = "Vaccination : " + nomVaccin + " (" + quantite + " doses)";
                } else {
                    if (batimentUniqueId == null) {
                        toast("Veuillez sélectionner le poulailler");
                        return;
                    }
                    String produit = textOf(etProduit);
                    if (produit.isEmpty()) {
                        toast("Veuillez préciser le produit utilisé");
                        return;
                    }
                    req.produit = produit;
                    req.quantite = parseDoubleOrNull(etQuantiteSoin.getText());
                    // Pas de coût ici : la Production suit le fait, pas l'argent, le coût
                    // réel se saisit séparément en Comptabilité ("Nouvelle transaction",
                    // catégorie "Santé / Vétérinaire") pour éviter une double saisie.
                    requestObject = req;
                    summary = spinnerTypeSoin.getText().toString() + " : " + produit;
                }
                break;
            }
            case ENTRETIEN: {
                boolean niveauBatiment = isNiveauEntretienBatiment();
                String descriptionEntretien = textOf(etDescriptionEntretien);
                if (descriptionEntretien.isEmpty()) {
                    toast("Veuillez préciser la description");
                    return;
                }
                String batimentEntretienUniqueId = niveauBatiment ? getSelectedBatimentEntretienUniqueId() : null;
                if (niveauBatiment && batimentEntretienUniqueId == null) {
                    toast("Veuillez sélectionner le poulailler");
                    return;
                }
                com.mobile.diafarms.network.dto.EntretienCreateRequest req = new com.mobile.diafarms.network.dto.EntretienCreateRequest();
                req.batimentUniqueId = batimentEntretienUniqueId;
                req.date = date;
                req.heure = heure;
                req.niveau = entretienNiveauToWire(spinnerNiveauEntretien.getText().toString());
                req.type = niveauBatiment ? entretienTypeToWire(spinnerTypeEntretien.getText().toString()) : "AUTRE";
                req.description = descriptionEntretien;
                req.observations = nullIfBlank(textOf(etObservationsEntretien));
                requestObject = req;
                summary = (niveauBatiment ? spinnerTypeEntretien.getText().toString() : "Site") + " : " + descriptionEntretien;
                break;
            }
            case MORTALITE: {
                if (batimentUniqueId == null) {
                    toast("Veuillez sélectionner le poulailler");
                    return;
                }
                int nombreMorts = parseIntSafe(etNombreMorts.getText());
                if (nombreMorts <= 0) {
                    toast("Veuillez saisir le nombre de sujets morts");
                    return;
                }
                MortaliteCreateRequest req = new MortaliteCreateRequest();
                req.projetUniqueId = projetUniqueId;
                req.batimentUniqueId = batimentUniqueId;
                req.date = date;
                req.heure = heure;
                req.nombreMorts = nombreMorts;
                req.cause = nullIfBlank(textOf(etCauseMortalite));
                requestObject = req;
                summary = nombreMorts + " sujet(s) mort(s)" + (req.cause != null ? " : " + req.cause : "");
                break;
            }
            case REFORME: {
                if (batimentUniqueId == null) {
                    toast("Veuillez sélectionner le poulailler");
                    return;
                }
                int nombreSujets = parseIntSafe(etNombreSujetsReforme.getText());
                if (nombreSujets <= 0) {
                    toast("Veuillez saisir le nombre de sujets réformés");
                    return;
                }
                if (effectifReformeDisponible != null && nombreSujets > effectifReformeDisponible) {
                    toast("Quantité supérieure à l'effectif vivant (" + effectifReformeDisponible + " sujet(s))");
                    return;
                }
                String pointDeVente = pointDeVenteReformeChoisi();
                if (pointsDeVenteReforme.size() > 1 && pointDeVente == null) {
                    toast("Choisissez le point de vente des réformés");
                    return;
                }
                ReformeCreateRequest req = new ReformeCreateRequest();
                req.projetUniqueId = projetUniqueId;
                req.batimentUniqueId = batimentUniqueId;
                req.date = date;
                req.heure = heure;
                req.nombreSujets = nombreSujets;
                req.cause = nullIfBlank(textOf(etCauseReforme));
                req.magasinVenteUniqueId = pointDeVente;
                requestObject = req;
                summary = nombreSujets + " sujet(s) réformé(s)" + (req.cause != null ? " : " + req.cause : "");
                break;
            }
            case ALIMENTATION_ACHAT: {
                // Catégorie "Achat d'aliment" de la sortie d'argent. Un achat est lié au projet
                // (stock du projet) ; le poulailler reste facultatif, c'est la consommation qui
                // se fait dans un poulailler.
                if (projetUniqueId == null || projetUniqueId.isEmpty()) {
                    toast("Un achat d'aliment entre dans le stock d'un projet : choisissez d'abord un projet à l'accueil");
                    return;
                }
                String typeAliment = typeAlimentToWire(spinnerTypeAliment.getText().toString());
                // Obligatoire pour un nouvel achat ; une saisie d'une version antérieure n'en a pas.
                if (typeAliment == null && nomAlimentExistant == null) {
                    toast("Veuillez choisir le type d'aliment");
                    return;
                }
                Double sacs = parseDoubleOrNull(etSac.getText());
                if (sacs != null && sacs < 0) {
                    toast("Le nombre de sacs ne peut pas être négatif");
                    return;
                }
                // Sacs facultatifs si la quantité totale (kg) est saisie, comme le serveur.
                if (sacs == null && parseDoubleOrNull(etQuantiteKgAchat.getText()) == null) {
                    toast("Veuillez saisir le nombre de sacs ou la quantité totale (kg)");
                    return;
                }
                if (sacs == null) sacs = 0d;
                Double poidsSac = parseDoubleOrNull(etPoidsSac.getText());
                Double quantiteKg = parseDoubleOrNull(etQuantiteKgAchat.getText());
                if (quantiteKg == null && poidsSac != null && poidsSac > 0) quantiteKg = sacs * poidsSac;
                if (quantiteKg == null || quantiteKg <= 0) {
                    toast("Veuillez préciser la quantité totale (kg)");
                    return;
                }
                Double coutAchat = arrondiFranc(parseDoubleOrNull(etMontant.getText()));
                if (coutAchat == null || coutAchat <= 0) {
                    toast("Veuillez saisir le montant de l'achat");
                    return;
                }
                AlimentationCreateRequest req = new AlimentationCreateRequest();
                req.typeAliment = typeAliment;
                // Vide pour un nouvel achat : le serveur le déduit du type ("Aliment ponte"...).
                req.nomAliment = nomAlimentExistant;
                req.sac = sacs;
                req.poidsSacKg = poidsSac != null && poidsSac > 0 ? poidsSac : null;
                req.quantiteKg = quantiteKg;
                // Montant de la sortie d'argent : le serveur crée la sortie liée au projet
                // (voir AlimentationImpl.syncTransaction).
                req.coutTotal = coutAchat;
                req.dateDistribution = date;
                req.heure = heure;
                req.observations = nullIfBlank(textOf(etObservationsAchat));
                req.fournisseur = nullIfBlank(textOf(etFournisseurAchat));
                // Poulailler non demandé pour un achat (ignoré par le serveur).
                req.batimentUniqueId = null;
                requestObject = req;
                String libelleType = typeAlimentLibelle(typeAliment);
                String quoi = libelleType != null ? "aliment " + libelleType.toLowerCase(Locale.FRANCE)
                        : (nomAlimentExistant != null ? nomAlimentExistant : "aliment");
                summary = String.format(Locale.FRANCE, "Achat %s : %s sac(s), %s kg (-%,.0f FCFA)",
                        quoi, formatNombre(sacs, 2), formatNombre(quantiteKg, 1), coutAchat);
                break;
            }
            case ALIMENTATION_CONSOMMATION: {
                if (batimentUniqueId == null) {
                    toast("Veuillez sélectionner le poulailler");
                    return;
                }
                Double quantiteKg = parseDoubleOrNull(etQuantiteKgConso.getText());
                if (quantiteKg == null || quantiteKg <= 0) {
                    toast("Veuillez saisir la quantité consommée (kg)");
                    return;
                }
                // Bloque ici, avec le stock déjà connu (synchronisé ou en cache hors
                // ligne) — plutôt que de créer une saisie qui échouera de toute façon au
                // moment de l'envoi (le serveur refait cette même vérification côté
                // données à jour, voir ConsommationAlimentImpl.create). Ne bloque
                // jamais si le stock est simplement inconnu (pas encore synchronisé) :
                // ce n'est pas au client de deviner "0" faute de donnée.
                if (stockAlimentRestantConnu != null && quantiteKg > stockAlimentRestantConnu) {
                    toast(String.format(Locale.FRANCE,
                            "Stock insuffisant : %.1f kg disponible(s) pour ce projet (dernière synchronisation)",
                            stockAlimentRestantConnu));
                    return;
                }
                ConsommationAlimentCreateRequest req = new ConsommationAlimentCreateRequest();
                req.projetUniqueId = projetUniqueId;
                req.batimentUniqueId = batimentUniqueId;
                req.date = date;
                req.heure = heure;
                req.quantiteKg = quantiteKg;
                requestObject = req;
                summary = String.format(Locale.FRANCE, "%.1f kg consommés", quantiteKg);
                break;
            }
            case VENTE_OEUFS: {
                String magasinUniqueId = getSelectedMagasinUniqueId(spinnerMagasinOeufs);
                if (magasinUniqueId == null) {
                    toast("Veuillez sélectionner le magasin de vente");
                    return;
                }
                boolean enAlveoles = isVenteOeufsEnAlveoles();
                int saisie = parseIntSafe(etQuantiteOeufsVente.getText());
                int quantite = enAlveoles ? AlveoleUtils.alveolesToOeufs(saisie) : saisie;
                Double prixSaisi = parseDoubleOrNull(etPrixUnitaireOeufs.getText());
                Double montant = arrondiFranc(parseDoubleOrNull(etMontantVenteOeufs.getText()));
                Double montantRapporte = arrondiFranc(parseDoubleOrNull(etMontantRapporteVenteOeufs.getText()));
                boolean avecClientOeufs = getSelectedClientUniqueId(spinnerClientVenteOeufs, true) != null;
                if (quantite <= 0) {
                    toast(enAlveoles ? "Veuillez saisir le nombre d'alvéoles vendues" : "Veuillez saisir le nombre d'œufs vendus");
                    return;
                }
                if (prixSaisi == null || prixSaisi <= 0) {
                    toast(enAlveoles ? "Veuillez saisir le prix par alvéole" : "Veuillez saisir le prix unitaire");
                    return;
                }
                if (montant == null || montant <= 0) {
                    toast("Veuillez saisir le montant de la vente");
                    return;
                }
                // Avec un client, le montant reçu maintenant est facultatif (vide = rien reçu,
                // la vente reste à régler), comme le web et le serveur.
                if (montantRapporte != null && montantRapporte < 0) {
                    toast("Le montant ne peut pas être négatif");
                    return;
                }
                if (montantRapporte == null && !avecClientOeufs) {
                    toast("Veuillez indiquer le montant réellement rapporté (même égal au montant théorique)");
                    return;
                }
                // Garde-fou client en plus de la validation serveur (voir loadStockForMagasin) :
                // évite un aller-retour réseau pour découvrir le refus après coup.
                boolean casse = isVenteOeufsCasse();
                if (stockOeufsDisponible != null && quantite > stockOeufsDisponible) {
                    toast("Quantité supérieure au stock " + (casse ? "cassé " : "") + "disponible dans ce magasin (" + stockOeufsDisponible + " œuf(s))");
                    return;
                }
                VenteOeufsCreateRequest req = new VenteOeufsCreateRequest();
                req.date = date;
                req.heure = heure;
                req.magasinUniqueId = magasinUniqueId;
                req.clientUniqueId = getSelectedClientUniqueId(spinnerClientVenteOeufs, true);
                req.quantiteOeufs = quantite; // toujours en œufs, quelle que soit l'unité saisie
                // prixUnitaire (VenteOeufs.prixUnitaire côté back) est "informatif, par
                // œuf" — reconverti depuis le prix par alvéole si c'est l'unité choisie.
                req.prixUnitaire = enAlveoles ? prixSaisi / AlveoleUtils.OEUFS_PAR_ALVEOLE : prixSaisi;
                req.montant = montant;
                req.montantRapporte = montantRapporte;
                // Seulement pertinent si le montant rapporté devient un paiement client —
                // voir refreshModePaiementVenteOeufs/VenteOeufsCreateRequest.modePaiement.
                req.modePaiement = req.clientUniqueId != null ? getSelectedModePaiement(spinnerModePaiementVenteOeufs) : null;
                req.typeOeuf = casse ? "CASSE" : "BON";
                requestObject = req;
                summary = enAlveoles
                        ? String.format(Locale.FRANCE, "Vente de %d alvéole(s), %d œufs (%,.0f FCFA)", saisie, quantite, montant)
                        : String.format(Locale.FRANCE, "Vente de %d œufs (%,.0f FCFA)", quantite, montant);
                break;
            }
            case VENTE_REFORME: {
                String magasinUniqueIdReforme = getSelectedMagasinUniqueId(spinnerMagasinReforme);
                if (magasinUniqueIdReforme == null) {
                    toast("Veuillez sélectionner le magasin de vente");
                    return;
                }
                int nombreSujets = parseIntSafe(etNombreSujetsVente.getText());
                Double prixReforme = parseDoubleOrNull(etPrixUnitaireReforme.getText());
                Double montant = arrondiFranc(parseDoubleOrNull(etMontantVenteReforme.getText()));
                Double montantRapporteReforme = arrondiFranc(parseDoubleOrNull(etMontantRapporteVenteReforme.getText()));
                boolean avecClientReforme = getSelectedClientUniqueId(spinnerClientVenteReforme, true) != null;
                if (nombreSujets <= 0) {
                    toast("Veuillez saisir le nombre de sujets vendus");
                    return;
                }
                if (prixReforme == null || prixReforme <= 0) {
                    toast(isTypeVenteReformeKilo() ? "Veuillez saisir le prix du kg" : "Veuillez saisir le prix unitaire");
                    return;
                }
                if (montant == null || montant <= 0) {
                    toast("Veuillez saisir le montant de la vente");
                    return;
                }
                if (montantRapporteReforme != null && montantRapporteReforme < 0) {
                    toast("Le montant ne peut pas être négatif");
                    return;
                }
                if (montantRapporteReforme == null && !avecClientReforme) {
                    toast("Veuillez indiquer le montant réellement rapporté (même égal au montant théorique)");
                    return;
                }
                Integer stockPdv = stockReformeAuPointDeVente(magasinUniqueIdReforme);
                if (stockPdv != null && nombreSujets > stockPdv) {
                    toast(messageStockReformeInsuffisant(stockPdv));
                    return;
                }
                boolean kiloReforme = isTypeVenteReformeKilo();
                Double poidsTotalReforme = parseDoubleOrNull(etPoidsTotalReforme.getText());
                if (kiloReforme && (poidsTotalReforme == null || poidsTotalReforme <= 0)) {
                    toast("Veuillez saisir le poids total pesé (kg) pour une vente au kilo");
                    return;
                }
                VenteReformeCreateRequest req = new VenteReformeCreateRequest();
                req.date = date;
                req.heure = heure;
                req.magasinUniqueId = magasinUniqueIdReforme;
                req.clientUniqueId = getSelectedClientUniqueId(spinnerClientVenteReforme, true);
                req.nombreSujets = nombreSujets;
                req.prixUnitaire = prixReforme;
                req.montant = montant;
                req.montantRapporte = montantRapporteReforme;
                // Même raisonnement que VENTE_OEUFS ci-dessus.
                req.modePaiement = req.clientUniqueId != null ? getSelectedModePaiement(spinnerModePaiementVenteReforme) : null;
                req.typeVente = kiloReforme ? "KILO" : "TETE";
                req.poidsTotalKg = kiloReforme ? poidsTotalReforme : null;
                requestObject = req;
                summary = kiloReforme
                        ? String.format(Locale.FRANCE, "Vente réforme : %d %s, %s kg, %,.0f F/kg",
                                nombreSujets, nombreSujets > 1 ? "sujets" : "sujet", formatNombre(poidsTotalReforme, 3), prixReforme)
                        : String.format(Locale.FRANCE, "Vente réforme de %d sujet(s) (%,.0f FCFA)", nombreSujets, montant);
                break;
            }
            case VENTE_FIENTES:
            case VENTE_AUTRE: {
                boolean fientes = type == SaisieType.VENTE_FIENTES;
                Double sacsVendus = fientes ? parseDoubleOrNull(etQuantiteVenteDiverse.getText()) : null;
                Double prixSac = fientes ? parseDoubleOrNull(etPrixUnitaireVenteDiverse.getText()) : null;
                if ((sacsVendus != null && sacsVendus < 0) || (prixSac != null && prixSac < 0)) {
                    toast("Le nombre de sacs et le prix ne peuvent pas être négatifs");
                    return;
                }
                Double montantVente = arrondiFranc(parseDoubleOrNull(etMontantVenteDiverse.getText()));
                if (montantVente == null && sacsVendus != null && sacsVendus > 0 && prixSac != null && prixSac > 0) {
                    montantVente = (double) Math.round(sacsVendus * prixSac);
                }
                if (montantVente == null || montantVente <= 0) {
                    toast("Veuillez saisir le montant (positif) de la vente");
                    return;
                }
                String descriptionVente = nullIfBlank(textOf(etDescriptionVenteDiverse));
                if (!fientes && descriptionVente == null) {
                    toast("Veuillez préciser ce qui a été vendu (commentaire)");
                    return;
                }
                com.mobile.diafarms.network.dto.VenteDiverseCreateRequest req = new com.mobile.diafarms.network.dto.VenteDiverseCreateRequest();
                req.produit = fientes ? com.mobile.diafarms.network.dto.VenteDiverseCreateRequest.PRODUIT_FIENTES
                        : com.mobile.diafarms.network.dto.VenteDiverseCreateRequest.PRODUIT_AUTRE;
                req.date = date;
                req.quantite = sacsVendus != null && sacsVendus > 0 ? sacsVendus : null;
                req.prixUnitaire = prixSac != null && prixSac > 0 ? prixSac : null;
                req.montant = montantVente;
                req.description = descriptionVente;
                // Cette vente concerne : le projet de l'accueil ou toute la ferme.
                if (rbConcerneProjet.isChecked()) {
                    if (!projetChoisi()) {
                        toast("Aucun projet choisi à l'accueil : choisissez « Toute la ferme » ou un projet depuis l'accueil");
                        return;
                    }
                    req.rattachement = RattachementSaisie.PROJET;
                    req.projetUniqueId = projetUniqueId;
                } else if (rbConcerneFerme.isChecked()) {
                    req.rattachement = RattachementSaisie.FERME;
                } else {
                    toast("Précisez ce que concerne cette vente : le projet ou toute la ferme");
                    return;
                }
                requestObject = req;
                summary = fientes
                        ? (req.quantite != null
                            ? String.format(Locale.FRANCE, "Vente de fientes : %s sac(s) (+%,.0f FCFA)", formatNombre(req.quantite, 2), montantVente)
                            : String.format(Locale.FRANCE, "Vente de fientes (+%,.0f FCFA)", montantVente))
                          + (descriptionVente != null ? " : " + descriptionVente : "")
                        : String.format(Locale.FRANCE, "Autre vente : %s (+%,.0f FCFA)", descriptionVente, montantVente);
                break;
            }
            case TRANSACTION_ENTREE:
            case TRANSACTION_SORTIE: {
                String categorieChoisie = spinnerCategorie.getText().toString().trim();
                if (categorieChoisie.isEmpty()) {
                    toast("Veuillez choisir la catégorie");
                    return;
                }
                String precisionCategorie = CATEGORIE_AUTRE.equals(categorieChoisie) ? nullIfBlank(textOf(etCategoriePrecision)) : null;
                if (CATEGORIE_AUTRE.equals(categorieChoisie) && precisionCategorie == null) {
                    toast("Veuillez préciser la catégorie");
                    return;
                }
                if (type == SaisieType.TRANSACTION_SORTIE && estSante() && spinnerNatureSante.getText().length() == 0) {
                    toast("Précisez ce que vous avez payé : achat de médicament ou service");
                    return;
                }
                if (type == SaisieType.TRANSACTION_SORTIE && estMedicamentSante()) {
                    if (projetUniqueId == null || projetUniqueId.isEmpty()) {
                        toast("Un médicament entre dans le stock d'un projet : choisissez d'abord le projet à l'accueil");
                        return;
                    }
                    String nomMed = textOf(etNomMedicament);
                    int iForme = java.util.Arrays.asList(FORMES_MEDICAMENT).indexOf(spinnerFormeMedicament.getText().toString());
                    String unite = spinnerUniteMedicament.getText().toString().trim();
                    Double qMed = parseDoubleOrNull(etQuantiteSante.getText());
                    Double montantMed = arrondiFranc(parseDoubleOrNull(etMontant.getText()));
                    if (nomMed.isEmpty() || iForme < 0 || unite.isEmpty()) {
                        toast("Indiquez le médicament, sa forme et son unité");
                        return;
                    }
                    if (qMed == null || qMed <= 0) {
                        toast("Indiquez la quantité achetée");
                        return;
                    }
                    if (montantMed == null || montantMed <= 0) {
                        toast("Indiquez le montant payé");
                        return;
                    }
                    com.mobile.diafarms.network.dto.AchatMedicamentCreateRequest achatMed = new com.mobile.diafarms.network.dto.AchatMedicamentCreateRequest();
                    achatMed.nom = nomMed;
                    achatMed.forme = FORMES_MEDICAMENT_WIRE[iForme];
                    achatMed.unite = unite;
                    achatMed.quantite = qMed;
                    Double puMed = parseDoubleOrNull(etPrixUnitaireSante.getText());
                    achatMed.prixUnitaire = puMed != null && puMed > 0 ? puMed : null;
                    achatMed.coutTotal = montantMed;
                    achatMed.dateAchat = date;
                    achatMed.fournisseur = nullIfBlank(textOf(etFournisseurMedicament));
                    achatMed.observations = nullIfBlank(textOf(etDescriptionTransaction));
                    achatMed.batimentUniqueId = batiments.size() > 1 ? getSelectedBatimentUniqueId() : null;
                    requestObject = achatMed;
                    typeEnregistrement = SaisieType.MEDICAMENT_ACHAT;
                    summary = String.format(Locale.FRANCE, "Achat médicament : %s %s de %s (%,.0f FCFA)",
                            formatSaisie(qMed), unite, nomMed, montantMed);
                    break;
                }
                // Arrondi au franc comme le serveur : 0,4 F devient 0, donc refusé ici.
                Double montant = arrondiFranc(parseDoubleOrNull(etMontant.getText()));
                String description = textOf(etDescriptionTransaction);
                if (montant == null || montant <= 0) {
                    toast("Veuillez saisir un montant positif");
                    return;
                }
                if (description.isEmpty()) {
                    toast("Veuillez saisir une description");
                    return;
                }
                boolean sante = estSante();
                if (sante && (projetUniqueId == null || projetUniqueId.isEmpty())) {
                    toast("Un soin concerne un projet : choisissez d'abord le projet à l'accueil");
                    return;
                }
                // Service de santé : nombre de jours facultatif.
                Double quantiteSante = sante ? parseDoubleOrNull(etQuantiteSante.getText()) : null;
                if (quantiteSante != null && quantiteSante <= 0) quantiteSante = null;
                TransactionCreateRequest req = new TransactionCreateRequest();
                req.type = (type == SaisieType.TRANSACTION_SORTIE) ? "SORTIE" : "ENTREE";
                req.date = date;
                req.description = description;
                req.montant = montant;
                req.categorie = categorieChoisie;
                req.categoriePrecision = precisionCategorie;
                String nomPoulailler = null;
                if (sante) {
                    // Santé / Vétérinaire : toujours le projet de l'accueil (choix masqué).
                    req.rattachement = RattachementSaisie.PROJET;
                    req.projetUniqueId = projetUniqueId;
                    req.quantite = quantiteSante;
                    Double pu = parseDoubleOrNull(etPrixUnitaireSante.getText());
                    req.prixUnitaire = pu != null && pu > 0 ? pu : null;
                    req.batimentUniqueId = batiments.size() > 1 ? getSelectedBatimentUniqueId() : null;
                    if (req.batimentUniqueId != null) nomPoulailler = spinnerBatiment.getText().toString();
                } else if (rbConcerneAncien.isChecked() && rattachementAncien != null) {
                    // Saisie d'une version antérieure gardée telle quelle : le serveur la normalise.
                    req.commun = true;
                    req.projetsConcernesUniqueIds = rattachementAncien.projetsConcernes.isEmpty() ? null : rattachementAncien.projetsConcernes;
                    req.siteUniqueId = rattachementAncien.siteUniqueId;
                    req.batimentUniqueId = rattachementAncien.batimentUniqueId;
                } else if (rbConcerneProjet.isChecked()) {
                    if (!projetChoisi()) {
                        toast("Aucun projet choisi à l'accueil : choisissez un site, toute la ferme, ou un projet depuis l'accueil");
                        return;
                    }
                    req.rattachement = RattachementSaisie.PROJET;
                    req.projetUniqueId = projetUniqueId;
                    req.batimentUniqueId = getSelectedBatimentTransaction();
                    if (req.batimentUniqueId != null) nomPoulailler = spinnerBatimentTransaction.getText().toString();
                } else if (rbConcerneSite.isChecked()) {
                    String site = getSelectedSiteTransaction();
                    // Site enregistré pas encore dans la liste (cache vide) : gardé tel quel.
                    if (site == null) site = siteTransactionEnAttente;
                    if (site == null) {
                        toast(sitesTransaction.isEmpty()
                                ? "Aucun site connu sur ce téléphone : synchronisez, ou choisissez une autre option"
                                : "Veuillez choisir le site");
                        return;
                    }
                    req.rattachement = RattachementSaisie.SITE;
                    req.siteUniqueId = site;
                } else if (rbConcerneFerme.isChecked()) {
                    req.rattachement = RattachementSaisie.FERME;
                } else {
                    toast(type == SaisieType.TRANSACTION_ENTREE
                            ? "Précisez ce que concerne cette entrée d'argent : le projet, un site ou toute la ferme"
                            : "Précisez ce que concerne cette dépense : le projet, un site ou toute la ferme");
                    return;
                }
                requestObject = req;
                summary = String.format(Locale.FRANCE, "%s %,.0f FCFA : %s",
                        type == SaisieType.TRANSACTION_SORTIE ? "-" : "+", montant, description)
                        + (precisionCategorie != null ? " [" + precisionCategorie + "]" : "")
                        + (nomPoulailler != null ? " (" + nomPoulailler + ")" : "");
                break;
            }
            case CLIENT_CREATE: {
                String nom = textOf(etClientNom);
                if (nom.isEmpty()) {
                    toast("Veuillez saisir le nom du client");
                    return;
                }
                String telephoneClient = textOf(etClientTelephone);
                if (telephoneClient.isEmpty()) {
                    toast("Veuillez saisir le numéro de téléphone du client");
                    return;
                }
                // Le serveur refuse un nom ou un téléphone déjà utilisé : vérifié ici sur les
                // clients connus du téléphone et les nouveaux clients pas encore envoyés.
                String doublon = clientDoublon(nom, telephoneClient);
                if (doublon != null) {
                    toast(doublon);
                    return;
                }
                ClientCreateRequest req = new ClientCreateRequest();
                req.nom = nom;
                req.telephone = telephoneClient;
                req.adresse = nullIfBlank(textOf(etClientAdresse));
                req.email = nullIfBlank(textOf(etClientEmail));
                requestObject = req;
                summary = "Nouveau client : " + nom;
                break;
            }
            case COMMANDE_CREATE: {
                if (clients.isEmpty()) {
                    toast("Aucun client synchronisé. Créez-en un d'abord (en ligne) ou synchronisez");
                    return;
                }
                String clientUniqueId = getSelectedClientUniqueId(spinnerClientCommande, false);
                if (clientUniqueId == null) {
                    toast("Veuillez sélectionner un client");
                    return;
                }
                String magasinUniqueIdCommande = getSelectedMagasinUniqueId(spinnerMagasinCommande);
                if (magasinUniqueIdCommande == null) {
                    toast("Veuillez sélectionner le magasin de vente");
                    return;
                }
                boolean estReforme = isCommandeReforme();
                boolean enAlveoles = isCommandeEnAlveoles();
                int saisie = parseIntSafe(etQuantiteCommande.getText());
                // Toujours reconverti en œufs pour l'API/la base — voir Commande.quantite
                // côté back, qui ne connaît jamais l'alvéole (même principe que VenteOeufs).
                int quantite = (!estReforme && enAlveoles) ? AlveoleUtils.alveolesToOeufs(saisie) : saisie;
                Double montantEstime = arrondiFranc(parseDoubleOrNull(etMontantEstimeCommande.getText()));
                boolean kiloCommande = isCommandeKilo();
                Double prixKgCommande = kiloCommande ? parseDoubleOrNull(etPrixKgCommande.getText()) : null;
                Double poidsEstimeCommande = kiloCommande ? parseDoubleOrNull(etPoidsEstimeCommande.getText()) : null;
                if (quantite <= 0) {
                    toast(estReforme ? "Veuillez saisir le nombre de sujets commandés"
                            : (enAlveoles ? "Veuillez saisir le nombre d'alvéoles commandées" : "Veuillez saisir la quantité commandée"));
                    return;
                }
                if (kiloCommande) {
                    if (prixKgCommande == null || prixKgCommande <= 0) {
                        toast("Veuillez saisir le prix du kg estimé");
                        return;
                    }
                    if (poidsEstimeCommande != null && poidsEstimeCommande <= 0) {
                        toast("Le poids estimé doit être positif (ou laissez-le vide)");
                        return;
                    }
                    // Même calcul que le serveur quand le poids estimé est connu.
                    if (poidsEstimeCommande != null) montantEstime = (double) Math.round(poidsEstimeCommande * prixKgCommande);
                }
                if (montantEstime == null || montantEstime <= 0) {
                    toast("Veuillez saisir le montant estimé");
                    return;
                }
                String clientNomCommande = null;
                for (ClientSelectResponse c : clients) {
                    if (c.getUniqueId().equals(clientUniqueId)) { clientNomCommande = c.getNom(); break; }
                }
                Double prixSaisiCommande = parseDoubleOrNull(etPrixUnitaireCommande.getText());
                // prixUnitaireEstime (Commande.prixUnitaireEstime côté back) est
                // "informatif, par œuf" — reconverti depuis le prix par alvéole si c'est
                // l'unité choisie, même principe que VenteOeufs.
                Double prixReelCommande = prixSaisiCommande != null
                        ? (!estReforme && enAlveoles ? prixSaisiCommande / AlveoleUtils.OEUFS_PAR_ALVEOLE : prixSaisiCommande)
                        : null;
                CommandeCreateRequest req = new CommandeCreateRequest();
                req.clientUniqueId = clientUniqueId;
                req.magasinUniqueId = magasinUniqueIdCommande;
                req.type = estReforme ? "REFORME" : "OEUFS";
                req.quantite = quantite;
                req.prixUnitaireEstime = kiloCommande ? null : prixReelCommande;
                req.montantEstime = montantEstime;
                if (estReforme) {
                    req.tarification = kiloCommande ? "KILO" : "TETE";
                    req.prixKgEstime = prixKgCommande;
                    req.poidsEstimeKg = poidsEstimeCommande;
                }
                req.montantAcompte = arrondiFranc(parseDoubleOrNull(etAcompteCommande.getText()));
                if (req.montantAcompte != null && req.montantAcompte < 0) {
                    toast("L'acompte ne peut pas être négatif");
                    return;
                }
                // Seulement pertinent s'il y a réellement un acompte — voir
                // refreshModePaiementCommande/CommandeCreateRequest.modePaiement.
                req.modePaiement = (req.montantAcompte != null && req.montantAcompte > 0)
                        ? getSelectedModePaiement(spinnerModePaiementCommande) : null;
                req.dateCommande = date;
                req.dateLivraisonPrevue = nullIfBlank(textOf(etDateLivraisonCommande));
                requestObject = req;
                summary = kiloCommande
                        ? String.format(Locale.FRANCE, "Commande de %d sujet(s) au kilo (%,.0f F/kg) pour %s (%,.0f FCFA estimés)",
                                quantite, prixKgCommande, clientNomCommande, montantEstime)
                        : String.format(Locale.FRANCE, "Commande de %d %s pour %s (%,.0f FCFA)",
                                quantite, estReforme ? "sujet(s)" : "œuf(s)", clientNomCommande, montantEstime);
                break;
            }
            case PAIEMENT_CLIENT: {
                if (clients.isEmpty()) {
                    toast("Aucun client synchronisé : synchronisez d'abord");
                    return;
                }
                String clientUid = getSelectedClientUniqueId(spinnerClientPaiement, false);
                if (clientUid == null) {
                    toast("Veuillez choisir le client");
                    return;
                }
                Double montantPaye = arrondiFranc(parseDoubleOrNull(etMontantPaiement.getText()));
                if (montantPaye == null || montantPaye <= 0) {
                    toast("Veuillez saisir le montant reçu");
                    return;
                }
                String mode = getSelectedModePaiement(spinnerModePaiementClient);
                if (mode == null) {
                    toast("Veuillez choisir le mode de paiement");
                    return;
                }
                PaiementClientRequest req = new PaiementClientRequest();
                req.clientUniqueId = clientUid;
                req.montant = montantPaye;
                req.mode = mode;
                req.date = date;
                req.commandeUniqueId = getSelectedCommandePaiement();
                req.observations = nullIfBlank(textOf(etObservationsPaiement));
                requestObject = req;
                summary = String.format(Locale.FRANCE, "Encaissement de %,.0f F de %s (%s)%s", montantPaye,
                        spinnerClientPaiement.getText().toString(), spinnerModePaiementClient.getText().toString(),
                        req.commandeUniqueId != null ? ", réservé à sa commande" : "");
                break;
            }
            case LIVRAISON_COMMANDE: {
                CommandeResponse c = commandeLivree;
                int q = quantiteLivraison();
                if (q <= 0) {
                    toast(c.estOeufs() ? (isLivraisonEnAlveoles() ? "Veuillez saisir le nombre d'alvéoles livrées" : "Veuillez saisir le nombre d'œufs livrés")
                            : "Veuillez saisir le nombre de sujets livrés");
                    return;
                }
                if (q > resteLivrable()) {
                    toast("Quantité supérieure au reste à livrer (" + formatQuantiteLivraison(Math.max(0, resteLivrable())) + ")");
                    return;
                }
                LivraisonCommandeRequest req = new LivraisonCommandeRequest();
                req.commandeUniqueId = c.uniqueId;
                req.quantite = q;
                // Date et heure réelles de la livraison (saisie hors ligne envoyée plus tard).
                req.date = date;
                req.heure = heure;
                req.type = c.type;
                req.magasinUniqueId = c.magasinUniqueId;
                req.clientUniqueId = c.clientUniqueId;
                req.clientNom = c.clientNom;
                if (c.estAuKilo()) {
                    Double poids = parseDoubleOrNull(etPoidsLivraison.getText());
                    if (poids == null || poids <= 0) {
                        toast("Veuillez saisir le poids total pesé (kg)");
                        return;
                    }
                    Double prixKg = parseDoubleOrNull(etPrixKgLivraison.getText());
                    if (prixKg != null && prixKg <= 0) {
                        toast("Le prix du kg doit être positif");
                        return;
                    }
                    if (prixKg == null && (c.prixKgEstime == null || c.prixKgEstime <= 0)) {
                        toast("Veuillez saisir le prix du kg");
                        return;
                    }
                    req.poidsTotalKg = Math.round(poids * 1000.0) / 1000.0;
                    req.prixKg = prixKg;
                }
                Double recu = arrondiFranc(parseDoubleOrNull(etMontantRecuLivraison.getText()));
                if (recu != null && recu < 0) {
                    toast("Le montant reçu ne peut pas être négatif");
                    return;
                }
                if (recu != null && recu > 0) {
                    req.montantRecu = recu;
                    req.mode = getSelectedModePaiement(spinnerModePaiementLivraison);
                    if (req.mode == null) {
                        toast("Veuillez choisir le mode de paiement");
                        return;
                    }
                }
                requestObject = req;
                StringBuilder resume = new StringBuilder("Livraison à ").append(c.clientNom).append(" : ")
                        .append(formatQuantiteLivraison(q));
                if (req.poidsTotalKg != null) resume.append(", ").append(formatNombre(req.poidsTotalKg, 3)).append(" kg");
                if (req.montantRecu != null) resume.append(String.format(Locale.FRANCE, " (%,.0f F reçus)", req.montantRecu));
                summary = resume.toString();
                break;
            }
            case SALAIRE_PAYER: {
                if (salaires.isEmpty()) {
                    toast("Aucune grille salariale synchronisée. Définissez un salaire depuis le web, ou synchronisez");
                    return;
                }
                SalaireSelectResponse employe = getSelectedSalaireEmploye();
                if (employe == null) {
                    toast("Veuillez sélectionner un employé");
                    return;
                }
                String periode = getSelectedPeriodeSalaire();
                // Bloque une tentative évidente de double paiement AVANT l'envoi — le
                // serveur reste seul juge définitif (SalaireServiceImpl.payer rejette
                // tout doublon quoi qu'il arrive), mais sur le terrain hors ligne, deux
                // paiements pour le même employé/période peuvent être mis en file avant
                // toute synchronisation, sans qu'aucun message d'erreur ne remonte avant
                // des jours. Deux vérifications complémentaires :
                // 1) le dernier paiement CONNU du serveur (best-effort, cache local) ;
                // 2) les saisies PAS ENCORE synchronisées sur CE téléphone (couvre le cas
                //    où le doublon vient d'être saisi hors ligne, avant toute synchro).
                if (periode.equals(employe.getDernierPaiementPeriode())) {
                    toast("Le salaire de " + periode + " a déjà été payé pour " + employe.getEmployeNom() + " (déjà synchronisé).");
                    return;
                }
                for (SaisieLocale existante : localDatabase.getSaisiesByType(SaisieType.SALAIRE_PAYER)) {
                    if (!existante.isEditable()) continue; // déjà synchronisée, sans rapport ici
                    if (existante.getLocalId().equals(editingLocalId)) continue; // soi-même, en édition
                    SalairePayerRequest autre = gson.fromJson(existante.getPayloadJson(), SalairePayerRequest.class);
                    if (employe.getEmployeUniqueId().equals(autre.employeUniqueId) && periode.equals(autre.periode)) {
                        toast("Le salaire de " + periode + " pour " + employe.getEmployeNom() + " est déjà en attente de synchronisation sur ce téléphone.");
                        return;
                    }
                }
                boolean mensuel = "MENSUEL".equals(employe.getModePaiement());
                Double quantiteSalaire = mensuel ? null : parseDoubleOrNull(etQuantiteSalaire.getText());
                if (!mensuel && (quantiteSalaire == null || quantiteSalaire <= 0)) {
                    toast("HORAIRE".equals(employe.getModePaiement()) ? "Indiquez le nombre d'heures travaillées" : "Indiquez le nombre de jours travaillés");
                    return;
                }
                // Le serveur calcule toujours le montant au taux de la période payée. Seul un
                // montant CHANGÉ par l'utilisateur (prime, retenue) part, en montantForce.
                Double estime = estimationSalaire(employe, quantiteSalaire);
                Double montantSaisi = arrondiFranc(parseDoubleOrNull(etMontantSalaire.getText()));
                if (montantSaisi != null && montantSaisi <= 0) {
                    toast("Le montant doit être positif (ou laissez le montant estimé)");
                    return;
                }
                boolean montantChange = montantSaisi != null && (estime == null || Math.round(montantSaisi) != Math.round(estime));
                SalairePayerRequest req = new SalairePayerRequest();
                req.employeUniqueId = employe.getEmployeUniqueId();
                req.periode = periode;
                req.quantite = quantiteSalaire;
                req.montantForce = montantChange ? montantSaisi : null;
                req.datePaiement = date;
                req.description = nullIfBlank(textOf(etDescriptionSalaire));
                requestObject = req;
                Double affiche = montantChange ? montantSaisi : estime;
                summary = affiche == null
                        ? String.format(Locale.FRANCE, "Salaire de %s, %s (montant calculé par le serveur)", employe.getEmployeNom(), periode)
                        : String.format(Locale.FRANCE, "Salaire de %s, %s (%,.0f FCFA%s)", employe.getEmployeNom(), periode, affiche,
                                montantChange ? ", montant modifié" : " estimé");
                break;
            }
            default:
                return;
        }

        String payloadJson = gson.toJson(requestObject);

        // Une dépense/rentrée COMMUNE n'appartient à aucun projet : ne pas enregistrer le projet
        // sélectionné à l'accueil dans les colonnes de la saisie (sinon "Mes saisies" l'afficherait
        // comme liée à ce projet, et l'édition la rattacherait par erreur).
        String projetColonne = projetUniqueId;
        String projetLabelColonne = projetLabel;
        if (requestObject instanceof LivraisonCommandeRequest || requestObject instanceof PaiementClientRequest) {
            projetColonne = null;
            projetLabelColonne = null;
        }
        // Dépense / vente : rangée sous le projet seulement si elle concerne le projet (un site,
        // la ferme ou plusieurs projets n'appartiennent à aucun projet).
        RattachementSaisie rattachement = requestObject instanceof TransactionCreateRequest
                ? RattachementSaisie.depuis((TransactionCreateRequest) requestObject)
                : requestObject instanceof com.mobile.diafarms.network.dto.VenteDiverseCreateRequest
                ? RattachementSaisie.depuis((com.mobile.diafarms.network.dto.VenteDiverseCreateRequest) requestObject)
                : null;
        if (rattachement != null) {
            boolean projet = RattachementSaisie.PROJET.equals(rattachement.choix) && rattachement.projetUniqueId != null;
            projetColonne = projet ? rattachement.projetUniqueId : null;
            projetLabelColonne = projet ? libelleProjet(rattachement.projetUniqueId) : null;
        }

        if (editingLocalId != null) {
            // Une modification ne change jamais la nature de la saisie (type enregistré).
            SaisieLocale enregistree = localDatabase.getSaisieById(editingLocalId);
            SaisieType typeConstruit = typeEnregistrement != null ? typeEnregistrement : type;
            if (enregistree != null && enregistree.getType() != typeConstruit) {
                toast("Cette modification changerait la nature de la saisie : supprimez-la et saisissez-la de nouveau");
                return;
            }
            // Revérifié ici : un envoi a pu être tenté pendant que le formulaire était ouvert.
            SaisieLocale actuelle = localDatabase.getSaisieById(editingLocalId);
            boolean refusee = actuelle == null || !actuelle.peutEtreModifiee()
                    || !localDatabase.updateSaisie(editingLocalId, projetColonne, projetLabelColonne, payloadJson, summary);
            if (refusee) {
                toast(actuelle != null && SaisieLocale.STATUT_DEJA_ENREGISTREE.equals(actuelle.getSyncStatus())
                        ? SaisieLocale.MESSAGE_DEJA_ENREGISTREE
                        : actuelle != null && SaisieLocale.STATUT_SYNCED.equals(actuelle.getSyncStatus())
                        ? "Cette saisie vient d'être envoyée : elle ne peut plus être modifiée ici"
                        : SaisieLocale.MESSAGE_ATTENTE_CONFIRMATION);
                return;
            }
            toast("Saisie modifiée");
        } else {
            localDatabase.insertSaisie(typeEnregistrement != null ? typeEnregistrement : type, projetColonne, projetLabelColonne, payloadJson, summary);
            toast("Enregistré, à synchroniser depuis l'accueil");
        }

        setResult(RESULT_OK);
        finish();
    }

    // ===================== PRÉ-REMPLISSAGE (édition d'une saisie existante) =====================

    private void prefillFromExisting() {
        SaisieLocale existing = localDatabase.getSaisieById(editingLocalId);
        if (existing == null) return;

        String json = existing.getPayloadJson();

        if (editionMedicament) {
            com.mobile.diafarms.network.dto.AchatMedicamentCreateRequest r = gson.fromJson(json, com.mobile.diafarms.network.dto.AchatMedicamentCreateRequest.class);
            spinnerCategorie.setText(CATEGORIE_SANTE, false);
            spinnerNatureSante.setText(NATURE_MEDICAMENT, false);
            setDateHeure(r.dateAchat, null);
            etNomMedicament.setText(r.nom);
            int iForme = java.util.Arrays.asList(FORMES_MEDICAMENT_WIRE).indexOf(r.forme);
            if (iForme >= 0) {
                spinnerFormeMedicament.setText(FORMES_MEDICAMENT[iForme], false);
                majUnitesMedicament(false);
            }
            spinnerUniteMedicament.setText(r.unite, false);
            majSanteAuto = true;
            if (r.quantite != null) etQuantiteSante.setText(formatSaisie(r.quantite));
            if (r.prixUnitaire != null) etPrixUnitaireSante.setText(formatSaisie(r.prixUnitaire));
            if (r.coutTotal != null) etMontant.setText(formatSaisie(r.coutTotal));
            majSanteAuto = false;
            etFournisseurMedicament.setText(r.fournisseur);
            etDescriptionTransaction.setText(r.observations);
            if (r.batimentUniqueId != null) selectBatimentByUniqueId(r.batimentUniqueId);
            appliquerSante();
            return;
        }

        switch (type) {
            case COLLECTE_OEUFS: {
                CollecteOeufsCreateRequest req = gson.fromJson(json, CollecteOeufsCreateRequest.class);
                setDateHeure(req.date, req.heure);
                if (req.oeufsCollectes != null) {
                    // Rescindé en alvéoles + reste pour l'affichage, symétrique de
                    // oeufsCollectesReel() — la valeur stockée est toujours un total en
                    // œufs, jamais décomposée (voir onValider()).
                    etAlveolesCollectees.setText(String.valueOf(req.oeufsCollectes / AlveoleUtils.OEUFS_PAR_ALVEOLE));
                    etOeufsCollectes.setText(String.valueOf(req.oeufsCollectes % AlveoleUtils.OEUFS_PAR_ALVEOLE));
                }
                if (req.oeufsCasses != null) etOeufsCasses.setText(String.valueOf(req.oeufsCasses));
                if (req.oeufsNonUtilisables != null) etOeufsNonUtilisables.setText(String.valueOf(req.oeufsNonUtilisables));
                selectBatimentByUniqueId(req.batimentUniqueId);
                selectBatimentStockageByUniqueId(req.magasinStockageUniqueId);
                break;
            }
            case SOINS: {
                SoinsCreateRequest req = gson.fromJson(json, SoinsCreateRequest.class);
                setDateHeure(req.date, req.heure);
                // Bascule d'abord le type (montre/masque le bon sous-groupe de champs,
                // voir updateGroupSoinsSousType()) avant de remplir les valeurs.
                selectSpinnerValue(spinnerTypeSoin, TYPES_SOIN, soinsTypeFromWire(req.type));
                if (Boolean.TRUE.equals(req.depuisStock)) {
                    checkSoinDepuisStock.setChecked(true);
                    soinStockEnAttente = cleMedicament(req.produit, req.unite);
                    if (req.quantite != null) etQuantiteSoinStock.setText(formatSaisie(req.quantite));
                    appliquerStockMedicaments(stockMedicamentsServeur, false);
                }
                updateGroupSoinsSousType();
                etObservationsSoin.setText(req.observations);
                if ("VACCINATION".equalsIgnoreCase(req.type)) {
                    etNomVaccin.setText(req.produit);
                    if (req.quantite != null) etQuantiteVaccin.setText(String.valueOf(req.quantite.intValue()));
                    setModesAdministration(req.modeAdministration);
                } else {
                    etProduit.setText(req.produit);
                    if (req.quantite != null) etQuantiteSoin.setText(String.valueOf(req.quantite));
                }
                selectBatimentByUniqueId(req.batimentUniqueId);
                break;
            }
            case ENTRETIEN: {
                com.mobile.diafarms.network.dto.EntretienCreateRequest req =
                        gson.fromJson(json, com.mobile.diafarms.network.dto.EntretienCreateRequest.class);
                setDateHeure(req.date, req.heure);
                selectSpinnerValue(spinnerNiveauEntretien, NIVEAUX_ENTRETIEN, entretienNiveauFromWire(req.niveau));
                updateGroupEntretienNiveau();
                selectSpinnerValue(spinnerTypeEntretien, TYPES_ENTRETIEN, entretienTypeFromWire(req.type));
                selectBatimentEntretienByUniqueId(req.batimentUniqueId);
                etDescriptionEntretien.setText(req.description);
                etObservationsEntretien.setText(req.observations);
                break;
            }
            case MORTALITE: {
                MortaliteCreateRequest req = gson.fromJson(json, MortaliteCreateRequest.class);
                setDateHeure(req.date, req.heure);
                if (req.nombreMorts != null) etNombreMorts.setText(String.valueOf(req.nombreMorts));
                etCauseMortalite.setText(req.cause);
                selectBatimentByUniqueId(req.batimentUniqueId);
                break;
            }
            case REFORME: {
                ReformeCreateRequest req = gson.fromJson(json, ReformeCreateRequest.class);
                setDateHeure(req.date, req.heure);
                if (req.nombreSujets != null) etNombreSujetsReforme.setText(String.valueOf(req.nombreSujets));
                etCauseReforme.setText(req.cause);
                selectBatimentByUniqueId(req.batimentUniqueId);
                if (req.magasinVenteUniqueId != null) {
                    pendingPointDeVenteReforme = req.magasinVenteUniqueId;
                    populatePointsDeVenteReforme();
                }
                break;
            }
            case ALIMENTATION_ACHAT: {
                AlimentationCreateRequest req = gson.fromJson(json, AlimentationCreateRequest.class);
                setDateHeure(req.dateDistribution, req.heure);
                nomAlimentExistant = nullIfBlank(req.nomAliment);
                String libelleType = typeAlimentLibelle(req.typeAliment);
                if (libelleType != null) spinnerTypeAliment.setText(libelleType, false);
                double poids = req.poidsSacKg != null && req.poidsSacKg > 0 ? req.poidsSacKg : POIDS_SAC_DEFAUT_KG;
                // Pas de recalcul pendant le pré-remplissage : la quantité enregistrée fait foi.
                majQuantiteAchatAuto = true;
                etPoidsSac.setText(formatSaisie(poids));
                if (req.sac != null) etSac.setText(formatSaisie(req.sac));
                if (req.quantiteKg != null) etQuantiteKgAchat.setText(formatSaisie(req.quantiteKg));
                majQuantiteAchatAuto = false;
                quantiteAchatManuelle = req.quantiteKg != null
                        && (req.sac == null || Math.abs(req.sac * poids - req.quantiteKg) > 1e-6);
                if (req.coutTotal != null) etMontant.setText(formatSaisie(req.coutTotal));
                etFournisseurAchat.setText(req.fournisseur);
                etObservationsAchat.setText(req.observations);
                selectBatimentByUniqueId(req.batimentUniqueId);
                break;
            }
            case ALIMENTATION_CONSOMMATION: {
                ConsommationAlimentCreateRequest req = gson.fromJson(json, ConsommationAlimentCreateRequest.class);
                setDateHeure(req.date, req.heure);
                if (req.quantiteKg != null) etQuantiteKgConso.setText(String.valueOf(req.quantiteKg));
                selectBatimentByUniqueId(req.batimentUniqueId);
                break;
            }
            case VENTE_OEUFS: {
                VenteOeufsCreateRequest req = gson.fromJson(json, VenteOeufsCreateRequest.class);
                setDateHeure(req.date, req.heure);
                if (req.quantiteOeufs != null) etQuantiteOeufsVente.setText(String.valueOf(req.quantiteOeufs));
                if (req.prixUnitaire != null) etPrixUnitaireOeufs.setText(String.valueOf(req.prixUnitaire));
                if (req.montant != null) etMontantVenteOeufs.setText(formatSaisie(req.montant));
                if (req.montantRapporte != null) etMontantRapporteVenteOeufs.setText(formatSaisie(req.montantRapporte));
                else if (req.clientUniqueId != null) {
                    // Vente à un client sans argent reçu : le champ reste vide.
                    etMontantRapporteVenteOeufs.setText("");
                    montantRapporteOeufsModifieManuel = true;
                }
                if ("CASSE".equals(req.typeOeuf)) radioGroupTypeOeufVente.check(R.id.radioTypeOeufCasse);
                selectModePaiementByValue(spinnerModePaiementVenteOeufs, req.modePaiement);
                selectMagasinByUniqueId(req.magasinUniqueId);
                selectClientByUniqueId(req.clientUniqueId);
                break;
            }
            case VENTE_REFORME: {
                VenteReformeCreateRequest req = gson.fromJson(json, VenteReformeCreateRequest.class);
                setDateHeure(req.date, req.heure);
                if (req.nombreSujets != null) etNombreSujetsVente.setText(String.valueOf(req.nombreSujets));
                if (req.prixUnitaire != null) etPrixUnitaireReforme.setText(String.valueOf(req.prixUnitaire));
                if (req.montant != null) etMontantVenteReforme.setText(formatSaisie(req.montant));
                if (req.montantRapporte != null) etMontantRapporteVenteReforme.setText(formatSaisie(req.montantRapporte));
                else if (req.clientUniqueId != null) {
                    etMontantRapporteVenteReforme.setText("");
                    montantRapporteReformeModifieManuel = true;
                }
                if ("KILO".equals(req.typeVente)) radioGroupTypeVenteReforme.check(R.id.radioTypeVenteReformeKilo);
                if (req.poidsTotalKg != null) etPoidsTotalReforme.setText(String.valueOf(req.poidsTotalKg));
                refreshTypeVenteReformeUi();
                selectModePaiementByValue(spinnerModePaiementVenteReforme, req.modePaiement);
                selectMagasinByUniqueId(req.magasinUniqueId);
                selectClientByUniqueId(req.clientUniqueId);
                break;
            }
            case CLIENT_CREATE: {
                ClientCreateRequest req = gson.fromJson(json, ClientCreateRequest.class);
                etClientNom.setText(req.nom);
                etClientTelephone.setText(req.telephone);
                etClientAdresse.setText(req.adresse);
                etClientEmail.setText(req.email);
                break;
            }
            case COMMANDE_CREATE: {
                CommandeCreateRequest req = gson.fromJson(json, CommandeCreateRequest.class);
                setDateHeure(req.dateCommande, null);
                // Bascule d'abord le type (déclenche le listener qui affiche/masque
                // groupUniteCommande, voir bindViews) avant de remplir les valeurs.
                // Réédition toujours en œufs (unité Œuf par défaut), jamais en alvéole —
                // même principe que VENTE_OEUFS : la valeur stockée est toujours un total
                // en œufs, pas la saisie d'origine.
                boolean estReforme = "REFORME".equals(req.type);
                radioGroupTypeCommande.check(estReforme ? R.id.radioCommandeReforme : R.id.radioCommandeOeufs);
                if (estReforme && "KILO".equals(req.tarification)) {
                    radioGroupTarificationCommande.check(R.id.radioTarificationCommandeKilo);
                }
                refreshTarificationCommandeUi();
                if (req.quantite != null) etQuantiteCommande.setText(String.valueOf(req.quantite));
                if (req.prixUnitaireEstime != null) etPrixUnitaireCommande.setText(String.valueOf(req.prixUnitaireEstime));
                if (req.prixKgEstime != null) etPrixKgCommande.setText(formatSaisie(req.prixKgEstime));
                if (req.poidsEstimeKg != null) etPoidsEstimeCommande.setText(formatSaisie(req.poidsEstimeKg));
                if (req.montantEstime != null) etMontantEstimeCommande.setText(formatSaisie(req.montantEstime));
                if (req.montantAcompte != null) etAcompteCommande.setText(formatSaisie(req.montantAcompte));
                selectModePaiementByValue(spinnerModePaiementCommande, req.modePaiement);
                if (req.dateLivraisonPrevue != null) etDateLivraisonCommande.setText(req.dateLivraisonPrevue);
                selectMagasinByUniqueId(req.magasinUniqueId);
                selectClientByUniqueId(req.clientUniqueId);
                break;
            }
            case PAIEMENT_CLIENT: {
                PaiementClientRequest req = gson.fromJson(json, PaiementClientRequest.class);
                setDateHeure(req.date, null);
                if (req.montant != null) etMontantPaiement.setText(formatSaisie(req.montant));
                etObservationsPaiement.setText(req.observations);
                for (int i = 0; i < MODE_PAIEMENT_VALEURS.length; i++) {
                    if (MODE_PAIEMENT_VALEURS[i].equals(req.mode)) spinnerModePaiementClient.setText(MODE_PAIEMENT_LABELS[i], false);
                }
                pendingCommandePaiement = req.commandeUniqueId;
                selectClientByUniqueId(req.clientUniqueId);
                break;
            }
            case LIVRAISON_COMMANDE: {
                LivraisonCommandeRequest req = gson.fromJson(json, LivraisonCommandeRequest.class);
                setDateHeure(req.date, req.heure);
                // Toujours rouverte en œufs (unité Œuf), comme une vente d'œufs.
                // Quantité enregistrée en œufs : on la remontre en œufs.
                radioGroupUniteLivraison.check(R.id.radioUniteLivraisonOeuf);
                if (req.quantite != null) etQuantiteLivraison.setText(String.valueOf(req.quantite));
                if (req.poidsTotalKg != null) etPoidsLivraison.setText(formatSaisie(req.poidsTotalKg));
                if (req.prixKg != null) etPrixKgLivraison.setText(formatSaisie(req.prixKg));
                if (req.montantRecu != null) etMontantRecuLivraison.setText(formatSaisie(req.montantRecu));
                selectModePaiementByValue(spinnerModePaiementLivraison, req.mode);
                break;
            }
            case SALAIRE_PAYER: {
                SalairePayerRequest req = gson.fromJson(json, SalairePayerRequest.class);
                selectEmployeSalaireByUniqueId(req.employeUniqueId);
                if (req.periode != null && req.periode.length() == 7) {
                    int annee = Integer.parseInt(req.periode.substring(0, 4));
                    int mois = Integer.parseInt(req.periode.substring(5));
                    spinnerMoisSalaire.setText(MOIS_LABELS[mois - 1], false);
                    // Peuplé avec [anneeCourante-1, anneeCourante, anneeCourante+1] (voir
                    // setupSalaireSpinners) — n'affiche que si l'année demandée fait bien
                    // partie de cette plage.
                    int anneeCourante = Calendar.getInstance().get(Calendar.YEAR);
                    if (Math.abs(annee - anneeCourante) <= 1) {
                        spinnerAnneeSalaire.setText(String.valueOf(annee), false);
                    }
                }
                setDateHeure(req.datePaiement, null);
                if (req.quantite != null) etQuantiteSalaire.setText(formatSaisie(req.quantite));
                // Montant changé à la main (prime, retenue) : remis tel quel ; sinon le champ
                // garde l'estimation (le montant d'une version antérieure est ignoré par le
                // serveur, qui calcule au taux de la période).
                if (req.montantForce != null) etMontantSalaire.setText(formatSaisie(req.montantForce));
                etDescriptionSalaire.setText(req.description);
                break;
            }
            case VENTE_FIENTES:
            case VENTE_AUTRE: {
                com.mobile.diafarms.network.dto.VenteDiverseCreateRequest req =
                        gson.fromJson(json, com.mobile.diafarms.network.dto.VenteDiverseCreateRequest.class);
                // Ancien format (Entrée d'argent « Vente fientes ») : date, montant et
                // description sont aux mêmes noms ; repart en vente diverse une fois modifiée.
                setDateHeure(req.date, null);
                majVenteDiverseAuto = true;
                if (req.quantite != null) etQuantiteVenteDiverse.setText(formatSaisie(req.quantite));
                if (req.prixUnitaire != null) etPrixUnitaireVenteDiverse.setText(formatSaisie(req.prixUnitaire));
                if (req.montant != null) etMontantVenteDiverse.setText(formatSaisie(req.montant));
                majVenteDiverseAuto = false;
                etDescriptionVenteDiverse.setText(req.description);
                appliquerRattachementEnregistre(RattachementSaisie.depuis(req));
                break;
            }
            case TRANSACTION_ENTREE:
            case TRANSACTION_SORTIE: {
                TransactionCreateRequest req = gson.fromJson(json, TransactionCreateRequest.class);
                setDateHeure(req.date, null);
                if (req.montant != null) etMontant.setText(formatSaisie(req.montant));
                etDescriptionTransaction.setText(req.description);
                selectSpinnerValue(spinnerCategorie, categoriesPourType(), req.categorie);
                // Catégorie d'une version antérieure absente de la liste (ex. « Transport ») :
                // gardée telle quelle plutôt que remplacée en silence.
                if (req.categorie != null && spinnerCategorie.getText().length() == 0) spinnerCategorie.setText(req.categorie, false);
                etCategoriePrecision.setText(req.categoriePrecision);
                majCategoriePrecision();
                majSanteAuto = true;
                if (req.quantite != null) etQuantiteSante.setText(formatSaisie(req.quantite));
                if (req.prixUnitaire != null) etPrixUnitaireSante.setText(formatSaisie(req.prixUnitaire));
                majSanteAuto = false;
                // Ce que concerne la saisie ; une saisie d'une version antérieure (commune,
                // projets concernés) est ramenée au choix le plus proche, sans rien perdre.
                appliquerRattachementEnregistre(RattachementSaisie.depuis(req));
                if (type == SaisieType.TRANSACTION_SORTIE && CATEGORIE_SANTE.equals(req.categorie)) {
                    // Une sortie d'argent Santé est un service (un achat de médicament est une autre saisie).
                    spinnerNatureSante.setText(NATURE_SERVICE, false);
                    appliquerSante();
                    if (req.batimentUniqueId != null) selectBatimentByUniqueId(req.batimentUniqueId);
                }
                break;
            }
        }
    }

    private void setDateHeure(String isoDateStr, String isoTimeStr) {
        if (isoDateStr != null && !isoDateStr.isEmpty()) {
            etDate.setText(isoDateStr);
            try {
                dateCal.setTime(isoDate.parse(isoDateStr));
            } catch (Exception ignored) { }
        }
        if (isoTimeStr != null && !isoTimeStr.isEmpty()) {
            etHeure.setText(isoTimeStr);
        }
    }

    private void selectSpinnerValue(AutoCompleteTextView spinner, String[] options, String value) {
        if (value == null) return;
        for (String option : options) {
            if (option.equalsIgnoreCase(value)) {
                spinner.setText(option, false);
                return;
            }
        }
    }

    /** Libellé français du spinner Soins (TYPES_SOIN) -> valeur enum backend
     * (TYPES_SOIN_WIRE), voir SoinsCreateRequest.type. */
    private String soinsTypeToWire(String label) {
        for (int i = 0; i < TYPES_SOIN.length; i++) {
            if (TYPES_SOIN[i].equalsIgnoreCase(label)) return TYPES_SOIN_WIRE[i];
        }
        return TYPES_SOIN_WIRE[0];
    }

    /** Sens inverse de soinsTypeToWire, pour le pré-remplissage en édition. */
    private String soinsTypeFromWire(String wire) {
        for (int i = 0; i < TYPES_SOIN_WIRE.length; i++) {
            if (TYPES_SOIN_WIRE[i].equalsIgnoreCase(wire)) return TYPES_SOIN[i];
        }
        return TYPES_SOIN[0];
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
