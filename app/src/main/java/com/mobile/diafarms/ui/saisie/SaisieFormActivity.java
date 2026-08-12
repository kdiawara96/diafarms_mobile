package com.mobile.diafarms.ui.saisie;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
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
    private static final String[] CATEGORIES_TRANSACTION_SORTIE = {"Achat", "Salaire", "Santé / Vétérinaire", "Transport", "Électricité / Eau", "Entretien / Maintenance", "Autre"};
    private static final String[] TYPES_SOIN = {"Vaccin", "Médicament", "Autre"};

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
    private View groupBatimentTop, groupDateHeureTop;
    private Spinner spinnerBatiment;
    private TextInputEditText etDate, etHeure;
    private MaterialButton btnValiderForm;

    // Collecte
    private View groupCollecte;
    private Spinner spinnerBatimentStockage;
    private TextView tvStockBatimentStockage;
    // Compté en deux temps comme sur le terrain (voir AlveoleUtils) : alvéoles pleines
    // + œufs qui ne remplissent pas un plateau entier, total = alvéoles×30 + œufs.
    // oeufsCasses reste toujours en œufs individuels.
    private TextInputEditText etAlveolesCollectees, etOeufsCollectes, etOeufsCasses;
    private TextView tvResumeCollecte;

    // Soins
    private View groupSoins;
    private Spinner spinnerTypeSoin;
    private TextInputEditText etProduit, etQuantiteSoin, etObservationsSoin;

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
    private TextInputEditText etNomAliment, etSac, etQuantiteKgAchat, etObservationsAchat;

    // Alimentation - consommation
    private View groupConsommation;
    private TextInputEditText etQuantiteKgConso;

    // Vente œufs (VENTE) — vendue DEPUIS un magasin précis (obligatoire, voir
    // spinnerMagasinOeufs), plafonnée par le stock vendable DE CE MAGASIN (vrai stock
    // séparé par magasin), calculé côté serveur (voir stockOeufsDisponible, jamais
    // recalculé sur l'appareil).
    private View groupVenteOeufs;
    private Spinner spinnerMagasinOeufs;
    private Spinner spinnerClientVenteOeufs;
    private TextView tvStockOeufsInfo;
    // Unité de saisie de etQuantiteOeufsVente/etPrixUnitaireOeufs (voir AlveoleUtils) —
    // Œuf ou Alvéole (plateau de 30 œufs) ; req.quantiteOeufs envoyé au serveur reste
    // toujours en œufs, quelle que soit l'unité choisie ici (voir onValider).
    private RadioGroup radioGroupUniteVenteOeufs;
    private TextInputLayout tilQuantiteOeufsVente, tilPrixUnitaireOeufs;
    private TextInputEditText etQuantiteOeufsVente, etPrixUnitaireOeufs, etMontantVenteOeufs;
    private Integer stockOeufsDisponible;

    // Vente réforme (VENTE) — vendue DEPUIS un magasin précis (obligatoire, voir
    // spinnerMagasinReforme), plafonnée par le total réformé transféré dans CE
    // MAGASIN moins déjà vendu (voir stockReformeDisponible) — distinct de l'effectif
    // vivant d'UN projet (effectifReformeDisponible, saisie Réforme Production).
    private View groupVenteReforme;
    private Spinner spinnerMagasinReforme;
    private Spinner spinnerClientVenteReforme;
    private TextView tvStockReformeInfo;
    private TextInputEditText etNombreSujetsVente, etPrixUnitaireReforme, etMontantVenteReforme;
    private Integer stockReformeDisponible;

    // Nouveau client (VENTE) — voir Client.java côté back, aucune notion de date ici.
    private View groupClient;
    private TextInputEditText etClientNom, etClientTelephone, etClientAdresse, etClientEmail;

    // Nouvelle commande (VENTE) — client TOUJOURS obligatoire et déjà synchronisé
    // (spinnerClientCommande, pas d'option "aucun"), contrairement aux ventes. Voir
    // Commande.java côté back.
    private View groupCommande;
    private Spinner spinnerClientCommande;
    private TextView tvAucunClientCommande;
    private Spinner spinnerMagasinCommande;
    private RadioGroup radioGroupTypeCommande;
    private TextInputEditText etQuantiteCommande, etPrixUnitaireCommande, etMontantEstimeCommande, etAcompteCommande;
    private TextInputEditText etDateLivraisonCommande;
    private final Calendar dateLivraisonCal = Calendar.getInstance();

    // Transaction
    private View groupTransaction;
    private Spinner spinnerCategorie;
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
        spinnerBatimentStockage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                loadStockBatimentStockageSelectionne();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        etAlveolesCollectees = findViewById(R.id.etAlveolesCollectees);
        etOeufsCollectes = findViewById(R.id.etOeufsCollectes);
        etOeufsCasses = findViewById(R.id.etOeufsCasses);
        tvResumeCollecte = findViewById(R.id.tvResumeCollecte);

        groupSoins = findViewById(R.id.groupSoins);
        spinnerTypeSoin = findViewById(R.id.spinnerTypeSoin);
        etProduit = findViewById(R.id.etProduit);
        etQuantiteSoin = findViewById(R.id.etQuantiteSoin);
        etObservationsSoin = findViewById(R.id.etObservationsSoin);

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
        etObservationsAchat = findViewById(R.id.etObservationsAchat);

        groupConsommation = findViewById(R.id.groupConsommation);
        tvStockInfo = findViewById(R.id.tvStockInfo);
        etQuantiteKgConso = findViewById(R.id.etQuantiteKgConso);

        groupVenteOeufs = findViewById(R.id.groupVenteOeufs);
        spinnerMagasinOeufs = findViewById(R.id.spinnerMagasinOeufs);
        spinnerMagasinOeufs.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                loadStockPourMagasinSelectionne();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        spinnerClientVenteOeufs = findViewById(R.id.spinnerClientVenteOeufs);
        tvStockOeufsInfo = findViewById(R.id.tvStockOeufsInfo);
        radioGroupUniteVenteOeufs = findViewById(R.id.radioGroupUniteVenteOeufs);
        tilQuantiteOeufsVente = findViewById(R.id.tilQuantiteOeufsVente);
        tilPrixUnitaireOeufs = findViewById(R.id.tilPrixUnitaireOeufs);
        etQuantiteOeufsVente = findViewById(R.id.etQuantiteOeufsVente);
        etPrixUnitaireOeufs = findViewById(R.id.etPrixUnitaireOeufs);
        etMontantVenteOeufs = findViewById(R.id.etMontantVenteOeufs);
        radioGroupUniteVenteOeufs.setOnCheckedChangeListener((group, checkedId) -> {
            boolean enAlveoles = isVenteOeufsEnAlveoles();
            tilQuantiteOeufsVente.setHint(enAlveoles ? "Nombre d'alvéoles vendues" : "Nombre d'œufs vendus");
            tilPrixUnitaireOeufs.setHint(enAlveoles ? "Prix par alvéole (FCFA, optionnel)" : "Prix unitaire (FCFA, optionnel)");
            recalculerMontantVenteOeufs();
        });
        TextWatcher venteOeufsWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { recalculerMontantVenteOeufs(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        etQuantiteOeufsVente.addTextChangedListener(venteOeufsWatcher);
        etPrixUnitaireOeufs.addTextChangedListener(venteOeufsWatcher);

        groupVenteReforme = findViewById(R.id.groupVenteReforme);
        spinnerMagasinReforme = findViewById(R.id.spinnerMagasinReforme);
        spinnerMagasinReforme.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                loadStockPourMagasinSelectionne();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        spinnerClientVenteReforme = findViewById(R.id.spinnerClientVenteReforme);
        tvStockReformeInfo = findViewById(R.id.tvStockReformeInfo);
        etNombreSujetsVente = findViewById(R.id.etNombreSujetsVente);
        etPrixUnitaireReforme = findViewById(R.id.etPrixUnitaireReforme);
        etMontantVenteReforme = findViewById(R.id.etMontantVenteReforme);

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
        etQuantiteCommande = findViewById(R.id.etQuantiteCommande);
        etPrixUnitaireCommande = findViewById(R.id.etPrixUnitaireCommande);
        etMontantEstimeCommande = findViewById(R.id.etMontantEstimeCommande);
        etAcompteCommande = findViewById(R.id.etAcompteCommande);
        etDateLivraisonCommande = findViewById(R.id.etDateLivraisonCommande);
        etDateLivraisonCommande.setHint("Aucune");
        etDateLivraisonCommande.setOnClickListener(v -> {
            Calendar base = dateLivraisonCal;
            DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
                dateLivraisonCal.set(year, month, day);
                etDateLivraisonCommande.setText(isoDate.format(dateLivraisonCal.getTime()));
            }, base.get(Calendar.YEAR), base.get(Calendar.MONTH), base.get(Calendar.DAY_OF_MONTH));
            // Pas de setMaxDate ici (contrairement à etDate) : une livraison prévue est
            // par nature une date future, jamais bornée à aujourd'hui.
            dialog.show();
        });
        TextWatcher commandeWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { recalculerMontantEstimeCommande(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        etQuantiteCommande.addTextChangedListener(commandeWatcher);
        etPrixUnitaireCommande.addTextChangedListener(commandeWatcher);

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

        ArrayAdapter<String> categorieAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, categoriesPourType());
        categorieAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategorie.setAdapter(categorieAdapter);

        ArrayAdapter<String> typeSoinAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, TYPES_SOIN);
        typeSoinAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTypeSoin.setAdapter(typeSoinAdapter);
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
        groupMortalite.setVisibility(type == SaisieType.MORTALITE ? View.VISIBLE : View.GONE);
        groupReforme.setVisibility(type == SaisieType.REFORME ? View.VISIBLE : View.GONE);
        groupAlimentationAchat.setVisibility(type == SaisieType.ALIMENTATION_ACHAT ? View.VISIBLE : View.GONE);
        groupConsommation.setVisibility(type == SaisieType.ALIMENTATION_CONSOMMATION ? View.VISIBLE : View.GONE);
        groupVenteOeufs.setVisibility(type == SaisieType.VENTE_OEUFS ? View.VISIBLE : View.GONE);
        groupVenteReforme.setVisibility(type == SaisieType.VENTE_REFORME ? View.VISIBLE : View.GONE);
        groupClient.setVisibility(type == SaisieType.CLIENT_CREATE ? View.VISIBLE : View.GONE);
        groupCommande.setVisibility(type == SaisieType.COMMANDE_CREATE ? View.VISIBLE : View.GONE);
        groupTransaction.setVisibility(
                (type == SaisieType.TRANSACTION_ENTREE || type == SaisieType.TRANSACTION_SORTIE || type == SaisieType.VENTE_FIENTES)
                        ? View.VISIBLE : View.GONE);

        // Ni un client ni une commande n'ont de notion de bâtiment (poulailler) — voir
        // Client.java/Commande.java côté back, tous deux farm-scopés. Un client
        // (ClientCreate) n'a en plus aucune notion de date : le bloc Date/Heure est
        // masqué en plus pour ce type (une commande garde etDate = dateCommande).
        boolean isClientOuCommande = type == SaisieType.CLIENT_CREATE || type == SaisieType.COMMANDE_CREATE;
        groupBatimentTop.setVisibility(isClientOuCommande ? View.GONE : View.VISIBLE);
        groupDateHeureTop.setVisibility(type == SaisieType.CLIENT_CREATE ? View.GONE : View.VISIBLE);
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
        etDate.setOnClickListener(v -> {
            DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
                dateCal.set(year, month, day);
                etDate.setText(isoDate.format(dateCal.getTime()));
            }, dateCal.get(Calendar.YEAR), dateCal.get(Calendar.MONTH), dateCal.get(Calendar.DAY_OF_MONTH));
            dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
            dialog.show();
        });

        etHeure.setHint("--:--");
        etHeure.setOnClickListener(v -> {
            Calendar base = heureCal != null ? heureCal : Calendar.getInstance();
            TimePickerDialog dialog = new TimePickerDialog(this, (view, hour, minute) -> {
                heureCal = Calendar.getInstance();
                heureCal.set(Calendar.HOUR_OF_DAY, hour);
                heureCal.set(Calendar.MINUTE, minute);
                etHeure.setText(isoTime.format(heureCal.getTime()));
            }, base.get(Calendar.HOUR_OF_DAY), base.get(Calendar.MINUTE), true);
            dialog.show();
        });
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
    }

    private boolean isVenteOeufsEnAlveoles() {
        return radioGroupUniteVenteOeufs != null && radioGroupUniteVenteOeufs.getCheckedRadioButtonId() == R.id.radioUniteVenteAlveole;
    }

    /** Montant = quantité saisie × prix unitaire saisi, tous deux dans la MÊME unité
     * (œuf ou alvéole) : pas besoin de conversion pour ce calcul, contrairement à
     * req.quantiteOeufs/req.prixUnitaire dans onValider() qui doivent, eux, toujours
     * être exprimés en œufs. Reste modifiable manuellement ensuite (ex: remise). */
    private void recalculerMontantVenteOeufs() {
        int saisie = parseIntSafe(etQuantiteOeufsVente.getText());
        Double prix = parseDoubleOrNull(etPrixUnitaireOeufs.getText());
        if (saisie > 0 && prix != null && prix > 0) {
            etMontantVenteOeufs.setText(String.format(Locale.FRANCE, "%.0f", saisie * prix));
        }
    }

    /** Montant estimé = quantité × prix unitaire, reste modifiable manuellement ensuite
     * (ex: négociation) — même principe que recalculerMontantVenteOeufs(). */
    private void recalculerMontantEstimeCommande() {
        int quantite = parseIntSafe(etQuantiteCommande.getText());
        Double prix = parseDoubleOrNull(etPrixUnitaireCommande.getText());
        if (quantite > 0 && prix != null && prix > 0) {
            etMontantEstimeCommande.setText(String.format(Locale.FRANCE, "%.0f", quantite * prix));
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
        int vendables = Math.max(0, total - casses);

        tvResumeCollecte.setText(String.format(Locale.FRANCE, "Soit %d œufs vendables (%d cassés)", vendables, casses));
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
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBatiment.setAdapter(adapter);
        // La liste des bâtiments vient de (ré)arriver (réseau ou cache) : si une
        // sélection avait été demandée avant que loadBatiments() ait fini de charger
        // (voir applyPendingBatimentSelection), on la réapplique maintenant.
        applyPendingBatimentSelection();
    }

    private String getSelectedBatimentUniqueId() {
        int position = spinnerBatiment.getSelectedItemPosition();
        if (position <= 0 || position - 1 >= batiments.size()) return null;
        return batiments.get(position - 1).getBatimentUniqueId();
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
                spinnerBatiment.setSelection(i + 1);
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
        } else {
            tvStockInfo.setText(fromCache ? "Stock non disponible (hors ligne)" : "Stock non disponible");
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
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMagasinOeufs.setAdapter(adapter);
        spinnerMagasinReforme.setAdapter(adapter);
        spinnerMagasinCommande.setAdapter(adapter);
        applyPendingMagasinSelection();
    }

    private String getSelectedMagasinUniqueId(Spinner spinner) {
        int position = spinner.getSelectedItemPosition();
        if (position < 0 || position >= magasins.size()) return null;
        return magasins.get(position).getUniqueId();
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
                spinnerMagasinOeufs.setSelection(i);
                spinnerMagasinReforme.setSelection(i);
                spinnerMagasinCommande.setSelection(i);
                // Appelé explicitement (pas seulement via OnItemSelectedListener) car
                // Spinner.setSelection() ne déclenche pas le listener quand la position
                // ne change pas (ex. le magasin voulu est déjà en position 0 par défaut).
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
        ArrayAdapter<String> adapterVente = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labelsVente);
        adapterVente.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerClientVenteOeufs.setAdapter(adapterVente);
        spinnerClientVenteReforme.setAdapter(adapterVente);

        // Commande : client obligatoire, pas d'option "aucun" — si la liste est vide,
        // le message tvAucunClientCommande prend le relais (voir onValider pour le
        // blocage explicite).
        List<String> labelsCommande = new ArrayList<>();
        for (ClientSelectResponse c : clients) labelsCommande.add(c.getNom());
        ArrayAdapter<String> adapterCommande = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labelsCommande);
        adapterCommande.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerClientCommande.setAdapter(adapterCommande);
        tvAucunClientCommande.setVisibility(clients.isEmpty() ? View.VISIBLE : View.GONE);
        spinnerClientCommande.setEnabled(!clients.isEmpty());

        applyPendingClientSelection();
    }

    /** Position dans "clients" (pas dans le spinner : les sélecteurs Vente ont un
     * décalage de 1 à cause de "Vente directe" en position 0). null = aucun client
     * sélectionné (vente directe, ou rien sélectionné côté Commande). */
    private String getSelectedClientUniqueId(Spinner spinner, boolean hasVenteDirecteOption) {
        int position = spinner.getSelectedItemPosition();
        int index = hasVenteDirecteOption ? position - 1 : position;
        if (index < 0 || index >= clients.size()) return null;
        return clients.get(index).getUniqueId();
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
                spinnerClientVenteOeufs.setSelection(i + 1);
                spinnerClientVenteReforme.setSelection(i + 1);
                spinnerClientCommande.setSelection(i);
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
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBatimentStockage.setAdapter(adapter);
        applyPendingBatimentStockageSelection();
    }

    private String getSelectedBatimentStockageUniqueId() {
        int position = spinnerBatimentStockage.getSelectedItemPosition();
        if (position < 0 || position >= batimentsStockage.size()) return null;
        return batimentsStockage.get(position).getUniqueId();
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
                spinnerBatimentStockage.setSelection(i);
                // Appelé explicitement (pas seulement via OnItemSelectedListener) car
                // Spinner.setSelection() ne déclenche pas le listener quand la position
                // ne change pas (ex. le bâtiment voulu est déjà en position 0 par défaut).
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
        Spinner spinner = (type == SaisieType.VENTE_OEUFS) ? spinnerMagasinOeufs : spinnerMagasinReforme;
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

    private void displayStockMagasin(StockMagasinResponse stock, boolean fromCache) {
        String suffix = fromCache ? " (dernière donnée connue, hors ligne)" : "";
        if (type == SaisieType.VENTE_OEUFS) {
            stockOeufsDisponible = stock != null ? stock.getOeufsDisponible() : null;
            if (stockOeufsDisponible != null) {
                tvStockOeufsInfo.setText(String.format(Locale.FRANCE, "Disponible dans ce magasin : %s%s",
                        AlveoleUtils.formatOeufsAvecAlveoles(stockOeufsDisponible), suffix));
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
                requestObject = req;
                summary = alveoles > 0
                        ? String.format(Locale.FRANCE, "%d alvéole(s) + %d œufs — %d au total (%d cassés)", alveoles, oeufsSupp, collectes, casses)
                        : String.format(Locale.FRANCE, "%d œufs collectés (%d cassés)", collectes, casses);
                break;
            }
            case SOINS: {
                if (batimentUniqueId == null) {
                    toast("Veuillez sélectionner le bâtiment");
                    return;
                }
                String produit = textOf(etProduit);
                if (produit.isEmpty()) {
                    toast("Veuillez préciser le produit utilisé");
                    return;
                }
                SoinsCreateRequest req = new SoinsCreateRequest();
                req.projetUniqueId = projetUniqueId;
                req.batimentUniqueId = batimentUniqueId;
                req.date = date;
                req.heure = heure;
                req.type = (String) spinnerTypeSoin.getSelectedItem();
                req.produit = produit;
                req.quantite = parseDoubleOrNull(etQuantiteSoin.getText());
                // Coût volontairement absent de la saisie Production : le prix d'un soin
                // se déclare comme une sortie d'argent (Finance), pas ici.
                req.observations = nullIfBlank(textOf(etObservationsSoin));
                requestObject = req;
                summary = req.type + " — " + produit;
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
                summary = nombreMorts + " sujet(s) mort(s)" + (req.cause != null ? " — " + req.cause : "");
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
                summary = nombreSujets + " sujet(s) réformé(s)" + (req.cause != null ? " — " + req.cause : "");
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
                // Coût volontairement absent de la saisie Production : le prix d'un achat
                // d'aliment se déclare comme une sortie d'argent (Finance), pas ici.
                req.dateDistribution = date;
                req.heure = heure;
                req.observations = nullIfBlank(textOf(etObservationsAchat));
                req.batimentUniqueId = batimentUniqueId;
                requestObject = req;
                summary = String.format(Locale.FRANCE, "Achat %s — %.1f kg", nom, quantiteKg);
                break;
            }
            case ALIMENTATION_CONSOMMATION: {
                Double quantiteKg = parseDoubleOrNull(etQuantiteKgConso.getText());
                if (quantiteKg == null || quantiteKg <= 0) {
                    toast("Veuillez saisir la quantité consommée (kg)");
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
                if (quantite <= 0) {
                    toast(enAlveoles ? "Veuillez saisir le nombre d'alvéoles vendues" : "Veuillez saisir le nombre d'œufs vendus");
                    return;
                }
                if (montant == null || montant <= 0) {
                    toast("Veuillez saisir le montant de la vente");
                    return;
                }
                // Garde-fou client en plus de la validation serveur (voir loadStockForMagasin) :
                // évite un aller-retour réseau pour découvrir le refus après coup.
                if (stockOeufsDisponible != null && quantite > stockOeufsDisponible) {
                    toast("Quantité supérieure au stock disponible dans ce magasin (" + stockOeufsDisponible + " œuf(s))");
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
                req.prixUnitaire = prixSaisi != null ? (enAlveoles ? prixSaisi / AlveoleUtils.OEUFS_PAR_ALVEOLE : prixSaisi) : null;
                req.montant = montant;
                requestObject = req;
                summary = enAlveoles
                        ? String.format(Locale.FRANCE, "Vente de %d alvéole(s) — %d œufs (%,.0f FCFA)", saisie, quantite, montant)
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
                Double montant = parseDoubleOrNull(etMontantVenteReforme.getText());
                if (nombreSujets <= 0) {
                    toast("Veuillez saisir le nombre de sujets vendus");
                    return;
                }
                if (montant == null || montant <= 0) {
                    toast("Veuillez saisir le montant de la vente");
                    return;
                }
                if (stockReformeDisponible != null && nombreSujets > stockReformeDisponible) {
                    toast("Quantité supérieure au stock disponible dans ce magasin (" + stockReformeDisponible + " sujet(s))");
                    return;
                }
                VenteReformeCreateRequest req = new VenteReformeCreateRequest();
                req.date = date;
                req.heure = heure;
                req.magasinUniqueId = magasinUniqueIdReforme;
                req.clientUniqueId = getSelectedClientUniqueId(spinnerClientVenteReforme, true);
                req.nombreSujets = nombreSujets;
                req.prixUnitaire = parseDoubleOrNull(etPrixUnitaireReforme.getText());
                req.montant = montant;
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
                req.categorie = (String) spinnerCategorie.getSelectedItem();
                requestObject = req;
                summary = String.format(Locale.FRANCE, "%s %,.0f FCFA — %s",
                        type == SaisieType.TRANSACTION_SORTIE ? "-" : "+", montant, description);
                break;
            }
            case CLIENT_CREATE: {
                String nom = textOf(etClientNom);
                if (nom.isEmpty()) {
                    toast("Veuillez saisir le nom du client");
                    return;
                }
                ClientCreateRequest req = new ClientCreateRequest();
                req.nom = nom;
                req.telephone = nullIfBlank(textOf(etClientTelephone));
                req.adresse = nullIfBlank(textOf(etClientAdresse));
                req.email = nullIfBlank(textOf(etClientEmail));
                requestObject = req;
                summary = "Nouveau client — " + nom;
                break;
            }
            case COMMANDE_CREATE: {
                if (clients.isEmpty()) {
                    toast("Aucun client synchronisé — créez-en un d'abord (en ligne) ou synchronisez");
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
                boolean estReforme = radioGroupTypeCommande.getCheckedRadioButtonId() == R.id.radioCommandeReforme;
                int quantite = parseIntSafe(etQuantiteCommande.getText());
                Double montantEstime = parseDoubleOrNull(etMontantEstimeCommande.getText());
                if (quantite <= 0) {
                    toast("Veuillez saisir la quantité commandée");
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
                CommandeCreateRequest req = new CommandeCreateRequest();
                req.clientUniqueId = clientUniqueId;
                req.magasinUniqueId = magasinUniqueIdCommande;
                req.type = estReforme ? "REFORME" : "OEUFS";
                req.quantite = quantite;
                req.prixUnitaireEstime = parseDoubleOrNull(etPrixUnitaireCommande.getText());
                req.montantEstime = montantEstime;
                req.montantAcompte = parseDoubleOrNull(etAcompteCommande.getText());
                req.dateCommande = date;
                req.dateLivraisonPrevue = nullIfBlank(textOf(etDateLivraisonCommande));
                requestObject = req;
                summary = String.format(Locale.FRANCE, "Commande de %d %s pour %s (%,.0f FCFA)",
                        quantite, estReforme ? "sujet(s)" : "œuf(s)", clientNomCommande, montantEstime);
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
            toast("Enregistré — à synchroniser depuis l'accueil");
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
                selectBatimentByUniqueId(req.batimentUniqueId);
                selectBatimentStockageByUniqueId(req.magasinStockageUniqueId);
                break;
            }
            case SOINS: {
                SoinsCreateRequest req = gson.fromJson(json, SoinsCreateRequest.class);
                setDateHeure(req.date, req.heure);
                etProduit.setText(req.produit);
                if (req.quantite != null) etQuantiteSoin.setText(String.valueOf(req.quantite));
                etObservationsSoin.setText(req.observations);
                selectSpinnerValue(spinnerTypeSoin, TYPES_SOIN, req.type);
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
                if (req.quantite != null) etQuantiteCommande.setText(String.valueOf(req.quantite));
                if (req.prixUnitaireEstime != null) etPrixUnitaireCommande.setText(String.valueOf(req.prixUnitaireEstime));
                if (req.montantEstime != null) etMontantEstimeCommande.setText(String.valueOf(req.montantEstime));
                if (req.montantAcompte != null) etAcompteCommande.setText(String.valueOf(req.montantAcompte));
                if (req.dateLivraisonPrevue != null) etDateLivraisonCommande.setText(req.dateLivraisonPrevue);
                radioGroupTypeCommande.check("REFORME".equals(req.type) ? R.id.radioCommandeReforme : R.id.radioCommandeOeufs);
                selectMagasinByUniqueId(req.magasinUniqueId);
                selectClientByUniqueId(req.clientUniqueId);
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

    private void selectSpinnerValue(Spinner spinner, String[] options, String value) {
        if (value == null) return;
        for (int i = 0; i < options.length; i++) {
            if (options[i].equalsIgnoreCase(value)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
