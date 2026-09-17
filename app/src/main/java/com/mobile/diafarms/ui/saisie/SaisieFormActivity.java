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

    // "Vente" n'a de sens que pour une Entrée, "Achat"/"Salaire"/... que pour une
    // Sortie — deux listes séparées plutôt qu'une liste unique proposant des
    // catégories hors-sujet selon le type (même règle que CreateTransactionDialog
    // côté web). Le type est fixé pour tout l'écran (TRANSACTION_ENTREE ou
    // TRANSACTION_SORTIE choisi depuis l'accueil), donc pas besoin de basculer la
    // liste dynamiquement ici, contrairement au web où un seul formulaire couvre
    // les deux types.
    private static final String[] CATEGORIES_TRANSACTION_ENTREE = {"Vente", "Autre"};
    // "Salaire" retiré : le paiement d'un salaire passe obligatoirement par "Payer un
    // salaire" (SALAIRE_PAYER), qui vérifie la grille et empêche un double paiement du
    // même mois — une "Sortie d'argent" catégorie "Salaire" contournerait ce contrôle.
    // "Santé / Vétérinaire" est de retour : depuis le retrait du champ Coût de l'écran
    // Santé / Vétérinaire (SaisieType.SOINS, sous-type Médicament/Autre — la Production
    // ne suit plus que le fait), c'est ici, en Comptabilité, que ce coût se saisit —
    // sauf pour un Vaccin, qui garde son propre calcul automatique (dose × prix).
    private static final String[] CATEGORIES_TRANSACTION_SORTIE = {"Achat", "Santé / Vétérinaire", "Transport", "Électricité / Eau", "Entretien / Maintenance", "Autre"};
    // Fusion Soins/Vaccination (UI) : un seul point d'entrée (SaisieType.SOINS, voir
    // HomeActivity/btnSoins), le type choisi ici décide quel sous-groupe de champs est
    // affiché (voir updateGroupSoinsSousType()) — Vaccination n'est plus un
    // SaisieType/écran distinct.
    private static final String[] TYPES_SOIN = {"Vaccination", "Médicament", "Autre"};
    // Valeurs enum backend (Soins.type, entité unifiée) correspondant 1-pour-1 à
    // TYPES_SOIN ci-dessus, dans le même ordre — le spinner reste en français, seule
    // la valeur envoyée au serveur change.
    private static final String[] TYPES_SOIN_WIRE = {"VACCINATION", "MEDICAMENT", "AUTRE"};

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

    // Mortalité
    private View groupMortalite;
    private TextInputEditText etNombreMorts, etCauseMortalite;

    // Réforme (Production) — comptage pur, plafonné par l'effectif vivant DU PROJET,
    // jamais de prix ici (voir effectifReformeDisponible). Mirroir de Mortalité.
    private View groupReforme;
    private TextView tvEffectifReformeInfo;
    private TextInputEditText etNombreSujetsReforme, etCauseReforme;
    private Integer effectifReformeDisponible;

    // Alimentation - achat
    private View groupAlimentationAchat;
    private TextInputEditText etNomAliment, etSac, etQuantiteKgAchat, etCoutAchatAliment, etObservationsAchat;

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
    private final Calendar dateLivraisonCal = Calendar.getInstance();

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

    // Transaction
    private View groupTransaction;
    private AutoCompleteTextView spinnerCategorie;
    private TextInputEditText etMontant, etDescriptionTransaction;
    private CheckBox checkCommun;
    private View groupProjetsConcernes;
    private LinearLayout containerProjetsConcernes;
    private TextView btnToutSelectionner;
    private final List<CheckBox> checkboxesProjetsConcernes = new ArrayList<>();

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
        projetUniqueId = getIntent().getStringExtra(EXTRA_PROJET_ID);
        projetLabel = getIntent().getStringExtra(EXTRA_PROJET_LABEL);
        editingLocalId = getIntent().getStringExtra(EXTRA_LOCAL_ID);

        localDatabase = new LocalDatabase(this);

        bindViews();
        applyTypeVisibility();
        setupDateHeurePickers();
        loadBatiments();

        if (type == SaisieType.ALIMENTATION_CONSOMMATION && projetUniqueId != null) {
            loadStock();
        }
        if (type == SaisieType.REFORME && projetUniqueId != null) {
            loadEffectifReforme();
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
        if (type == SaisieType.VENTE_OEUFS || type == SaisieType.VENTE_REFORME || type == SaisieType.COMMANDE_CREATE) {
            loadClients();
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

        groupMortalite = findViewById(R.id.groupMortalite);
        etNombreMorts = findViewById(R.id.etNombreMorts);
        etCauseMortalite = findViewById(R.id.etCauseMortalite);

        groupReforme = findViewById(R.id.groupReforme);
        tvEffectifReformeInfo = findViewById(R.id.tvEffectifReformeInfo);
        etNombreSujetsReforme = findViewById(R.id.etNombreSujetsReforme);
        etCauseReforme = findViewById(R.id.etCauseReforme);

        groupAlimentationAchat = findViewById(R.id.groupAlimentationAchat);
        etNomAliment = findViewById(R.id.etNomAliment);
        etSac = findViewById(R.id.etSac);
        etQuantiteKgAchat = findViewById(R.id.etQuantiteKgAchat);
        etCoutAchatAliment = findViewById(R.id.etCoutAchatAliment);
        etObservationsAchat = findViewById(R.id.etObservationsAchat);

        groupConsommation = findViewById(R.id.groupConsommation);
        tvStockInfo = findViewById(R.id.tvStockInfo);
        etQuantiteKgConso = findViewById(R.id.etQuantiteKgConso);

        groupVenteOeufs = findViewById(R.id.groupVenteOeufs);
        spinnerMagasinOeufs = findViewById(R.id.spinnerMagasinOeufs);
        spinnerMagasinOeufs.setOnItemClickListener((parent, view, position, id) -> loadStockPourMagasinSelectionne());
        spinnerClientVenteOeufs = findViewById(R.id.spinnerClientVenteOeufs);
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
        radioGroupTypeCommande.setOnCheckedChangeListener((group, checkedId) -> {
            boolean estReforme = checkedId == R.id.radioCommandeReforme;
            groupUniteCommande.setVisibility(estReforme ? View.GONE : View.VISIBLE);
            applyLabelsQuantiteCommande();
            recalculerMontantEstimeCommande();
        });
        radioGroupUniteCommande.setOnCheckedChangeListener((group, checkedId) -> {
            applyLabelsQuantiteCommande();
            recalculerMontantEstimeCommande();
        });
        applyLabelsQuantiteCommande();
        etAcompteCommande = findViewById(R.id.etAcompteCommande);
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

        groupTransaction = findViewById(R.id.groupTransaction);
        spinnerCategorie = findViewById(R.id.spinnerCategorie);
        etMontant = findViewById(R.id.etMontant);
        etDescriptionTransaction = findViewById(R.id.etDescriptionTransaction);
        checkCommun = findViewById(R.id.checkCommun);
        groupProjetsConcernes = findViewById(R.id.groupProjetsConcernes);
        containerProjetsConcernes = findViewById(R.id.containerProjetsConcernes);
        btnToutSelectionner = findViewById(R.id.btnToutSelectionner);

        checkCommun.setOnCheckedChangeListener((buttonView, isChecked) -> {
            groupProjetsConcernes.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (isChecked && checkboxesProjetsConcernes.isEmpty()) {
                populateProjetsConcernesCheckboxes(null);
            }
        });
        btnToutSelectionner.setOnClickListener(v -> toggleToutSelectionner());

        String[] categories = categoriesPourType();
        ArrayAdapter<String> categorieAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, categories);
        spinnerCategorie.setAdapter(categorieAdapter);
        if (categories.length > 0) spinnerCategorie.setText(categories[0], false);

        ArrayAdapter<String> typeSoinAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, TYPES_SOIN);
        spinnerTypeSoin.setAdapter(typeSoinAdapter);
        spinnerTypeSoin.setText(TYPES_SOIN[0], false);
        updateGroupSoinsSousType();
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
        groupSoinsGenerique.setVisibility(vaccination ? View.GONE : View.VISIBLE);
        groupVaccination.setVisibility(vaccination ? View.VISIBLE : View.GONE);
    }

    private static final String[] CATEGORIE_VENTE_FIENTES = {"Vente fientes"};

    private String[] categoriesPourType() {
        if (type == SaisieType.VENTE_FIENTES) return CATEGORIE_VENTE_FIENTES;
        return type == SaisieType.TRANSACTION_SORTIE ? CATEGORIES_TRANSACTION_SORTIE : CATEGORIES_TRANSACTION_ENTREE;
    }

    private void applyTypeVisibility() {
        tvTitreForm.setText(type.getLabel());
        tvProjetForm.setText(projetLabel != null ? projetLabel : "");

        groupCollecte.setVisibility(type == SaisieType.COLLECTE_OEUFS ? View.VISIBLE : View.GONE);
        groupSoins.setVisibility(type == SaisieType.SOINS ? View.VISIBLE : View.GONE);
        if (type == SaisieType.SOINS) updateGroupSoinsSousType();
        groupMortalite.setVisibility(type == SaisieType.MORTALITE ? View.VISIBLE : View.GONE);
        groupReforme.setVisibility(type == SaisieType.REFORME ? View.VISIBLE : View.GONE);
        groupAlimentationAchat.setVisibility(type == SaisieType.ALIMENTATION_ACHAT ? View.VISIBLE : View.GONE);
        groupConsommation.setVisibility(type == SaisieType.ALIMENTATION_CONSOMMATION ? View.VISIBLE : View.GONE);
        groupVenteOeufs.setVisibility(type == SaisieType.VENTE_OEUFS ? View.VISIBLE : View.GONE);
        groupVenteReforme.setVisibility(type == SaisieType.VENTE_REFORME ? View.VISIBLE : View.GONE);
        if (type == SaisieType.VENTE_REFORME) refreshTypeVenteReformeUi();
        groupClient.setVisibility(type == SaisieType.CLIENT_CREATE ? View.VISIBLE : View.GONE);
        groupCommande.setVisibility(type == SaisieType.COMMANDE_CREATE ? View.VISIBLE : View.GONE);
        groupSalaire.setVisibility(type == SaisieType.SALAIRE_PAYER ? View.VISIBLE : View.GONE);
        groupTransaction.setVisibility(
                (type == SaisieType.TRANSACTION_ENTREE || type == SaisieType.TRANSACTION_SORTIE || type == SaisieType.VENTE_FIENTES)
                        ? View.VISIBLE : View.GONE);

        // Aucun de ces types n'a de notion de bâtiment (poulailler) — client, commande et
        // salaire sont farm-scopés (voir Client.java/Commande.java/Salaire.java côté
        // back) ; les ventes puisent dans le stock d'un MAGASIN (toute la ferme), jamais
        // d'un poulailler précis — un vendeur sur le terrain n'a pas à choisir un
        // poulailler pour vendre des œufs déjà dans un magasin de vente (voir
        // VenteOeufsCreateRequest/VenteReformeCreateRequest, aucun batimentUniqueId).
        // Client et Salaire n'ont en plus aucune notion de date/heure : le bloc
        // Date/Heure est masqué en plus pour ces deux types (une commande garde
        // etDate = dateCommande, un paiement de salaire a sa propre Période dédiée).
        boolean sansBatiment = type == SaisieType.CLIENT_CREATE || type == SaisieType.COMMANDE_CREATE || type == SaisieType.SALAIRE_PAYER
                || type == SaisieType.VENTE_OEUFS || type == SaisieType.VENTE_REFORME || type == SaisieType.VENTE_FIENTES;
        groupBatimentTop.setVisibility(sansBatiment ? View.GONE : View.VISIBLE);
        // SOINS affiche toujours le bâtiment, même quand le type choisi dans le
        // formulaire est Vaccination : optionnel dans ce cas (voir onValider, le
        // choix "Aucun bâtiment précis" reste valide), obligatoire pour Médicament/
        // Autre — reprend le comportement de l'ancien écran Vaccination dédié, qui
        // n'avait tout simplement pas ce champ. Seuls Client/Salaire restent sans
        // aucune notion de date/heure de saisie.
        groupDateHeureTop.setVisibility(
                (type == SaisieType.CLIENT_CREATE || type == SaisieType.SALAIRE_PAYER)
                        ? View.GONE : View.VISIBLE);
    }

    /**
     * Remplit la case à cocher "Projets concernés" à partir des projets actifs en
     * cache local (voir CachePrefetcher.CACHE_PROJETS_SELECT — fonctionne donc aussi
     * hors ligne, comme le reste de l'app). Coché par défaut = toute la ferme, à
     * décocher un par un ou via "Tout désélectionner" — c'est l'utilisateur qui
     * précise ensuite quels projets sont réellement concernés par cette dépense/
     * rentrée commune.
     *
     * @param preselectionnesUniqueIds si non null (édition d'une saisie existante),
     *                                 seuls ces projets sont cochés au départ.
     */
    private void populateProjetsConcernesCheckboxes(List<String> preselectionnesUniqueIds) {
        containerProjetsConcernes.removeAllViews();
        checkboxesProjetsConcernes.clear();

        String json = localDatabase.getCache(CachePrefetcher.CACHE_PROJETS_SELECT);
        List<ProjetSelectResponse> projets = new ArrayList<>();
        if (json != null) {
            Type listType = new TypeToken<List<ProjetSelectResponse>>() {}.getType();
            List<ProjetSelectResponse> parsed = gson.fromJson(json, listType);
            if (parsed != null) projets = parsed;
        }

        for (ProjetSelectResponse projet : projets) {
            if (!projet.isActive()) continue;
            CheckBox cb = new CheckBox(this);
            cb.setText(projet.getLabel());
            cb.setTag(projet.getUniqueId());
            cb.setChecked(preselectionnesUniqueIds == null || preselectionnesUniqueIds.contains(projet.getUniqueId()));
            containerProjetsConcernes.addView(cb);
            checkboxesProjetsConcernes.add(cb);
        }
        updateToutSelectionnerLabel();
    }

    private void toggleToutSelectionner() {
        boolean toutEstCoche = checkboxesProjetsConcernes.stream().allMatch(CheckBox::isChecked);
        boolean nouvelEtat = !toutEstCoche;
        for (CheckBox cb : checkboxesProjetsConcernes) {
            cb.setChecked(nouvelEtat);
        }
        updateToutSelectionnerLabel();
    }

    private void updateToutSelectionnerLabel() {
        boolean toutEstCoche = !checkboxesProjetsConcernes.isEmpty()
                && checkboxesProjetsConcernes.stream().allMatch(CheckBox::isChecked);
        btnToutSelectionner.setText(toutEstCoche ? "Tout désélectionner" : "Tout sélectionner");
    }

    private List<String> getSelectedProjetsConcernesUniqueIds() {
        List<String> result = new ArrayList<>();
        for (CheckBox cb : checkboxesProjetsConcernes) {
            if (cb.isChecked()) result.add((String) cb.getTag());
        }
        return result;
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
        String montant = (saisie > 0 && prix != null && prix > 0) ? String.format(Locale.FRANCE, "%.0f", saisie * prix) : "";
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
        tilPrixUnitaireReforme.setHint(kilo ? "Prix au kilo (FCFA)" : "Prix unitaire (FCFA)");
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
        String montant = (quantite > 0 && prix != null && prix > 0) ? String.format(Locale.FRANCE, "%.0f", quantite * prix) : "";
        etMontantVenteReforme.setText(montant);
        if (!montantRapporteReformeModifieManuel) {
            syncingMontantRapporte = true;
            etMontantRapporteVenteReforme.setText(montant);
            syncingMontantRapporte = false;
        }
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
        int saisie = parseIntSafe(etQuantiteCommande.getText());
        Double prix = parseDoubleOrNull(etPrixUnitaireCommande.getText());
        if (saisie > 0 && prix != null && prix > 0) {
            etMontantEstimeCommande.setText(String.format(Locale.FRANCE, "%.0f", saisie * prix));
        }
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
    }

    private void populateBatimentSpinner() {
        List<String> labels = new ArrayList<>();
        labels.add("Aucun bâtiment précis");
        for (OccupationBatimentResponse b : batiments) {
            labels.add(b.getNomBatiment());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, labels);
        spinnerBatiment.setAdapter(adapter);
        spinnerBatiment.setText(labels.get(0), false);
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

    private void populateMagasinSpinners() {
        List<String> labels = new ArrayList<>();
        for (MagasinSelectResponse m : magasins) {
            labels.add(m.getNom());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, labels);
        spinnerMagasinOeufs.setAdapter(adapter);
        spinnerMagasinReforme.setAdapter(adapter);
        spinnerMagasinCommande.setAdapter(adapter);
        if (!labels.isEmpty()) {
            spinnerMagasinOeufs.setText(labels.get(0), false);
            spinnerMagasinReforme.setText(labels.get(0), false);
            spinnerMagasinCommande.setText(labels.get(0), false);
        }
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
                return;
            }
        }
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
        spinnerEmployeSalaire.setAdapter(adapter);
        if (!labels.isEmpty()) spinnerEmployeSalaire.setText(labels.get(0), false);
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
            return;
        }
        boolean mensuel = "MENSUEL".equals(s.getModePaiement());
        tilQuantiteSalaire.setVisibility(mensuel ? View.GONE : View.VISIBLE);
        tilQuantiteSalaire.setHint("HORAIRE".equals(s.getModePaiement()) ? "Heures travaillées" : "Jours travaillés");
        Double taux = s.getTauxBase();
        String suffixe = "HORAIRE".equals(s.getModePaiement()) ? "/ heure" : ("JOURNALIER".equals(s.getModePaiement()) ? "/ jour" : "/ mois");
        tvTauxInfoSalaire.setText(taux != null ? String.format(Locale.FRANCE, "Taux (dernière sync) : %,.0f FCFA %s", taux, suffixe) : "");
        etQuantiteSalaire.setText("");
        if (mensuel && taux != null) {
            etMontantSalaire.setText(String.format(Locale.FRANCE, "%.0f", taux));
        } else {
            etMontantSalaire.setText("");
        }
    }

    private void recalculerMontantSalaire() {
        SalaireSelectResponse s = getSelectedSalaireEmploye();
        if (s == null || s.getTauxBase() == null || "MENSUEL".equals(s.getModePaiement())) return;
        Double quantite = parseDoubleOrNull(etQuantiteSalaire.getText());
        if (quantite == null || quantite <= 0) return;
        etMontantSalaire.setText(String.format(Locale.FRANCE, "%.0f", s.getTauxBase() * quantite));
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
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, labels);
        spinnerBatimentStockage.setAdapter(adapter);
        if (!labels.isEmpty()) spinnerBatimentStockage.setText(labels.get(0), false);
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
        } else if (type == SaisieType.VENTE_REFORME) {
            stockReformeDisponible = stock != null ? stock.getReformeDisponible() : null;
            if (stockReformeDisponible != null) {
                tvStockReformeInfo.setText(String.format(Locale.FRANCE, "Disponible dans ce magasin : %d sujet(s)%s", stockReformeDisponible, suffix));
            } else {
                tvStockReformeInfo.setText(fromCache ? "Stock non disponible (hors ligne)" : "Stock non disponible");
            }
        }
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

    // ===================== VALIDATION + ENREGISTREMENT LOCAL =====================

    private void onValider() {
        String date = textOf(etDate);
        String heure = nullIfBlank(textOf(etHeure));
        String batimentUniqueId = getSelectedBatimentUniqueId();

        Object requestObject;
        String summary;

        switch (type) {
            case COLLECTE_OEUFS: {
                if (batimentUniqueId == null) {
                    toast("Veuillez sélectionner le bâtiment");
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
                    // batimentUniqueId reste optionnel pour Vaccination (pas de toast si
                    // "Aucun bâtiment précis" est sélectionné) — reprend le comportement
                    // de l'ancien écran Vaccination dédié, qui n'avait pas ce champ.
                    req.produit = nomVaccin;
                    req.quantite = (double) quantite;
                    // Aucun montant ici : la Production ne suit que le fait, le coût réel
                    // se saisit séparément en Comptabilité (catégorie "Santé / Vétérinaire").
                    req.modeAdministration = selectedModesAdministration();
                    requestObject = req;
                    summary = "Vaccination : " + nomVaccin + " (" + quantite + " doses)";
                } else {
                    if (batimentUniqueId == null) {
                        toast("Veuillez sélectionner le bâtiment");
                        return;
                    }
                    String produit = textOf(etProduit);
                    if (produit.isEmpty()) {
                        toast("Veuillez préciser le produit utilisé");
                        return;
                    }
                    req.produit = produit;
                    req.quantite = parseDoubleOrNull(etQuantiteSoin.getText());
                    // Pas de coût ici : la Production suit le fait, pas l'argent — le coût
                    // réel se saisit séparément en Comptabilité ("Nouvelle transaction",
                    // catégorie "Santé / Vétérinaire") pour éviter une double saisie.
                    req.observations = nullIfBlank(textOf(etObservationsSoin));
                    requestObject = req;
                    summary = spinnerTypeSoin.getText().toString() + " : " + produit;
                }
                break;
            }
            case MORTALITE: {
                if (batimentUniqueId == null) {
                    toast("Veuillez sélectionner le bâtiment");
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
                int nombreSujets = parseIntSafe(etNombreSujetsReforme.getText());
                if (nombreSujets <= 0) {
                    toast("Veuillez saisir le nombre de sujets réformés");
                    return;
                }
                if (effectifReformeDisponible != null && nombreSujets > effectifReformeDisponible) {
                    toast("Quantité supérieure à l'effectif vivant (" + effectifReformeDisponible + " sujet(s))");
                    return;
                }
                ReformeCreateRequest req = new ReformeCreateRequest();
                req.projetUniqueId = projetUniqueId;
                req.batimentUniqueId = batimentUniqueId;
                req.date = date;
                req.heure = heure;
                req.nombreSujets = nombreSujets;
                req.cause = nullIfBlank(textOf(etCauseReforme));
                requestObject = req;
                summary = nombreSujets + " sujet(s) réformé(s)" + (req.cause != null ? " : " + req.cause : "");
                break;
            }
            case ALIMENTATION_ACHAT: {
                if (batimentUniqueId == null) {
                    toast("Veuillez sélectionner le bâtiment");
                    return;
                }
                String nom = textOf(etNomAliment);
                Double quantiteKg = parseDoubleOrNull(etQuantiteKgAchat.getText());
                if (nom.isEmpty() || quantiteKg == null || quantiteKg <= 0) {
                    toast("Veuillez préciser l'aliment et la quantité (kg)");
                    return;
                }
                AlimentationCreateRequest req = new AlimentationCreateRequest();
                req.nomAliment = nom;
                req.sac = parseDoubleOrNull(etSac.getText());
                req.quantiteKg = quantiteKg;
                // Optionnel — si renseigné, génère automatiquement une sortie comptable
                // liée au projet côté back (voir AlimentationImpl.syncTransaction) : plus
                // besoin de ressaisir ce coût séparément dans "Sortie d'argent".
                req.coutTotal = parseDoubleOrNull(etCoutAchatAliment.getText());
                req.dateDistribution = date;
                req.heure = heure;
                req.observations = nullIfBlank(textOf(etObservationsAchat));
                req.batimentUniqueId = batimentUniqueId;
                requestObject = req;
                summary = String.format(Locale.FRANCE, "Achat %s : %.1f kg", nom, quantiteKg);
                break;
            }
            case ALIMENTATION_CONSOMMATION: {
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
                Double montant = parseDoubleOrNull(etMontantVenteOeufs.getText());
                Double montantRapporte = parseDoubleOrNull(etMontantRapporteVenteOeufs.getText());
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
                if (montantRapporte == null || montantRapporte < 0) {
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
                Double montant = parseDoubleOrNull(etMontantVenteReforme.getText());
                Double montantRapporteReforme = parseDoubleOrNull(etMontantRapporteVenteReforme.getText());
                if (nombreSujets <= 0) {
                    toast("Veuillez saisir le nombre de sujets vendus");
                    return;
                }
                if (prixReforme == null || prixReforme <= 0) {
                    toast("Veuillez saisir le prix unitaire");
                    return;
                }
                if (montant == null || montant <= 0) {
                    toast("Veuillez saisir le montant de la vente");
                    return;
                }
                if (montantRapporteReforme == null || montantRapporteReforme < 0) {
                    toast("Veuillez indiquer le montant réellement rapporté (même égal au montant théorique)");
                    return;
                }
                if (stockReformeDisponible != null && nombreSujets > stockReformeDisponible) {
                    toast("Quantité supérieure au stock disponible dans ce magasin (" + stockReformeDisponible + " sujet(s))");
                    return;
                }
                boolean kiloReforme = isTypeVenteReformeKilo();
                Double poidsTotalReforme = parseDoubleOrNull(etPoidsTotalReforme.getText());
                if (kiloReforme && (poidsTotalReforme == null || poidsTotalReforme <= 0)) {
                    toast("Veuillez saisir le poids total (kg) pour une vente au kilo");
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
                req.typeVente = kiloReforme ? "KILO" : "TETE";
                req.poidsTotalKg = kiloReforme ? poidsTotalReforme : null;
                requestObject = req;
                summary = String.format(Locale.FRANCE, "Vente réforme de %d sujet(s) (%,.0f FCFA)", nombreSujets, montant);
                break;
            }
            case TRANSACTION_ENTREE:
            case TRANSACTION_SORTIE:
            case VENTE_FIENTES: {
                Double montant = parseDoubleOrNull(etMontant.getText());
                String description = textOf(etDescriptionTransaction);
                if (montant == null || montant <= 0 || description.isEmpty()) {
                    toast("Veuillez saisir le montant et une description");
                    return;
                }
                // Vente de fientes : toujours commune (acte Finance à l'échelle de la
                // ferme, comme sur web — voir CreateVenteFienteDialog), la case à cocher
                // ne s'affiche même pas pour ce type.
                boolean commun = (type == SaisieType.VENTE_FIENTES) || checkCommun.isChecked();
                if (!commun && (projetUniqueId == null || projetUniqueId.isEmpty())) {
                    toast("Aucun projet actif : cochez \"commune\" ou sélectionnez un projet depuis l'accueil");
                    return;
                }
                List<String> projetsConcernes = commun ? getSelectedProjetsConcernesUniqueIds() : null;
                if (commun && type != SaisieType.VENTE_FIENTES && projetsConcernes.isEmpty()) {
                    toast("Sélectionnez au moins un projet concerné par cette dépense/rentrée commune");
                    return;
                }
                TransactionCreateRequest req = new TransactionCreateRequest();
                req.type = (type == SaisieType.TRANSACTION_SORTIE) ? "SORTIE" : "ENTREE";
                req.commun = commun;
                req.projetUniqueId = commun ? null : projetUniqueId;
                req.projetsConcernesUniqueIds = projetsConcernes;
                req.date = date;
                req.description = description;
                req.montant = montant;
                req.categorie = spinnerCategorie.getText().toString();
                requestObject = req;
                summary = String.format(Locale.FRANCE, "%s %,.0f FCFA : %s",
                        type == SaisieType.TRANSACTION_SORTIE ? "-" : "+", montant, description);
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
                Double montantEstime = parseDoubleOrNull(etMontantEstimeCommande.getText());
                if (quantite <= 0) {
                    toast(estReforme ? "Veuillez saisir le nombre de sujets commandés"
                            : (enAlveoles ? "Veuillez saisir le nombre d'alvéoles commandées" : "Veuillez saisir la quantité commandée"));
                    return;
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
                req.prixUnitaireEstime = prixReelCommande;
                req.montantEstime = montantEstime;
                req.montantAcompte = parseDoubleOrNull(etAcompteCommande.getText());
                req.dateCommande = date;
                req.dateLivraisonPrevue = nullIfBlank(textOf(etDateLivraisonCommande));
                requestObject = req;
                summary = String.format(Locale.FRANCE, "Commande de %d %s pour %s (%,.0f FCFA)",
                        quantite, estReforme ? "sujet(s)" : "œuf(s)", clientNomCommande, montantEstime);
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
                Double montantSalaire = parseDoubleOrNull(etMontantSalaire.getText());
                if (montantSalaire == null || montantSalaire <= 0) {
                    toast("Montant invalide");
                    return;
                }
                SalairePayerRequest req = new SalairePayerRequest();
                req.employeUniqueId = employe.getEmployeUniqueId();
                req.periode = periode;
                req.quantite = quantiteSalaire;
                req.montant = montantSalaire;
                req.description = nullIfBlank(textOf(etDescriptionSalaire));
                requestObject = req;
                summary = String.format(Locale.FRANCE, "Salaire de %s, %s (%,.0f FCFA)", employe.getEmployeNom(), periode, montantSalaire);
                break;
            }
            default:
                return;
        }

        String payloadJson = gson.toJson(requestObject);

        if (editingLocalId != null) {
            localDatabase.updateSaisie(editingLocalId, projetUniqueId, projetLabel, payloadJson, summary);
            toast("Saisie modifiée");
        } else {
            localDatabase.insertSaisie(type, projetUniqueId, projetLabel, payloadJson, summary);
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
                updateGroupSoinsSousType();
                if ("VACCINATION".equalsIgnoreCase(req.type)) {
                    etNomVaccin.setText(req.produit);
                    if (req.quantite != null) etQuantiteVaccin.setText(String.valueOf(req.quantite.intValue()));
                    setModesAdministration(req.modeAdministration);
                } else {
                    etProduit.setText(req.produit);
                    if (req.quantite != null) etQuantiteSoin.setText(String.valueOf(req.quantite));
                    etObservationsSoin.setText(req.observations);
                }
                selectBatimentByUniqueId(req.batimentUniqueId);
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
                break;
            }
            case ALIMENTATION_ACHAT: {
                AlimentationCreateRequest req = gson.fromJson(json, AlimentationCreateRequest.class);
                setDateHeure(req.dateDistribution, req.heure);
                etNomAliment.setText(req.nomAliment);
                if (req.sac != null) etSac.setText(String.valueOf(req.sac));
                if (req.quantiteKg != null) etQuantiteKgAchat.setText(String.valueOf(req.quantiteKg));
                if (req.coutTotal != null) etCoutAchatAliment.setText(String.valueOf(req.coutTotal));
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
                if (req.montant != null) etMontantVenteOeufs.setText(String.valueOf(req.montant));
                if (req.montantRapporte != null) etMontantRapporteVenteOeufs.setText(String.valueOf(req.montantRapporte));
                if ("CASSE".equals(req.typeOeuf)) radioGroupTypeOeufVente.check(R.id.radioTypeOeufCasse);
                selectMagasinByUniqueId(req.magasinUniqueId);
                selectClientByUniqueId(req.clientUniqueId);
                break;
            }
            case VENTE_REFORME: {
                VenteReformeCreateRequest req = gson.fromJson(json, VenteReformeCreateRequest.class);
                setDateHeure(req.date, req.heure);
                if (req.nombreSujets != null) etNombreSujetsVente.setText(String.valueOf(req.nombreSujets));
                if (req.prixUnitaire != null) etPrixUnitaireReforme.setText(String.valueOf(req.prixUnitaire));
                if (req.montant != null) etMontantVenteReforme.setText(String.valueOf(req.montant));
                if (req.montantRapporte != null) etMontantRapporteVenteReforme.setText(String.valueOf(req.montantRapporte));
                if ("KILO".equals(req.typeVente)) radioGroupTypeVenteReforme.check(R.id.radioTypeVenteReformeKilo);
                if (req.poidsTotalKg != null) etPoidsTotalReforme.setText(String.valueOf(req.poidsTotalKg));
                refreshTypeVenteReformeUi();
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
                if (req.quantite != null) etQuantiteCommande.setText(String.valueOf(req.quantite));
                if (req.prixUnitaireEstime != null) etPrixUnitaireCommande.setText(String.valueOf(req.prixUnitaireEstime));
                if (req.montantEstime != null) etMontantEstimeCommande.setText(String.valueOf(req.montantEstime));
                if (req.montantAcompte != null) etAcompteCommande.setText(String.valueOf(req.montantAcompte));
                if (req.dateLivraisonPrevue != null) etDateLivraisonCommande.setText(req.dateLivraisonPrevue);
                selectMagasinByUniqueId(req.magasinUniqueId);
                selectClientByUniqueId(req.clientUniqueId);
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
                if (req.quantite != null) etQuantiteSalaire.setText(String.valueOf(req.quantite));
                if (req.montant != null) etMontantSalaire.setText(String.valueOf(req.montant));
                etDescriptionSalaire.setText(req.description);
                break;
            }
            case TRANSACTION_ENTREE:
            case TRANSACTION_SORTIE:
            case VENTE_FIENTES: {
                TransactionCreateRequest req = gson.fromJson(json, TransactionCreateRequest.class);
                setDateHeure(req.date, null);
                if (req.montant != null) etMontant.setText(String.valueOf(req.montant));
                etDescriptionTransaction.setText(req.description);
                selectSpinnerValue(spinnerCategorie, categoriesPourType(), req.categorie);
                checkCommun.setChecked(Boolean.TRUE.equals(req.commun));
                if (Boolean.TRUE.equals(req.commun)) {
                    // Écrase la présélection "tout coché" posée par défaut par le
                    // listener de checkCommun (voir bindViews) avec les projets
                    // réellement enregistrés sur cette saisie existante.
                    populateProjetsConcernesCheckboxes(req.projetsConcernesUniqueIds);
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
