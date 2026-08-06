package com.mobile.diafarms.ui.saisie;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
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
import com.mobile.diafarms.network.dto.CollecteOeufsCreateRequest;
import com.mobile.diafarms.network.dto.ConsommationAlimentCreateRequest;
import com.mobile.diafarms.network.dto.EffectifReformeResponse;
import com.mobile.diafarms.network.dto.MortaliteCreateRequest;
import com.mobile.diafarms.network.dto.OccupationBatimentResponse;
import com.mobile.diafarms.network.dto.ProjetDetailResponse;
import com.mobile.diafarms.network.dto.ProjetSelectResponse;
import com.mobile.diafarms.network.dto.ReformeCreateRequest;
import com.mobile.diafarms.network.dto.SoinsCreateRequest;
import com.mobile.diafarms.network.dto.StockAlimentResponse;
import com.mobile.diafarms.network.dto.StockOeufsResponse;
import com.mobile.diafarms.network.dto.StockReformeResponse;
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

    // Vues communes
    private TextView tvTitreForm, tvProjetForm, tvStockInfo;
    private Spinner spinnerBatiment;
    private TextInputEditText etDate, etHeure;
    private MaterialButton btnValiderForm;

    // Collecte
    private View groupCollecte;
    // Unité de saisie du champ etOeufsCollectes (voir AlveoleUtils) — Œuf ou Alvéole
    // (plateau de 30 œufs) ; oeufsCasses reste toujours en œufs individuels.
    private RadioGroup radioGroupUniteCollecte;
    private TextInputLayout tilOeufsCollectes;
    private TextInputEditText etOeufsCollectes, etOeufsCasses;
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

    // Vente œufs (Finance) — acte commercial à l'échelle de TOUTE LA FERME (pas d'un
    // projet précis), plafonnée par le stock vendable (collectés - cassés - vendus de
    // la ferme), calculé côté serveur (voir stockOeufsDisponible, jamais recalculé
    // sur l'appareil).
    private View groupVenteOeufs;
    private TextView tvStockOeufsInfo;
    // Unité de saisie de etQuantiteOeufsVente/etPrixUnitaireOeufs (voir AlveoleUtils) —
    // Œuf ou Alvéole (plateau de 30 œufs) ; req.quantiteOeufs envoyé au serveur reste
    // toujours en œufs, quelle que soit l'unité choisie ici (voir onValider).
    private RadioGroup radioGroupUniteVenteOeufs;
    private TextInputLayout tilQuantiteOeufsVente, tilPrixUnitaireOeufs;
    private TextInputEditText etQuantiteOeufsVente, etPrixUnitaireOeufs, etMontantVenteOeufs;
    private Integer stockOeufsDisponible;

    // Vente réforme (Finance) — acte commercial à l'échelle de TOUTE LA FERME,
    // plafonnée par le total réformé (Reforme, Production) de la ferme moins déjà
    // vendu (voir stockReformeDisponible) — distinct de l'effectif vivant d'UN projet.
    private View groupVenteReforme;
    private TextView tvStockReformeInfo;
    private TextInputEditText etNombreSujetsVente, etPrixUnitaireReforme, etMontantVenteReforme;
    private Integer stockReformeDisponible;

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
        // Vente œufs/réforme (Finance) : plafonnées à l'échelle de la ferme, jamais
        // d'un projet précis — chargées systématiquement, pas conditionnées à
        // projetUniqueId (contrairement aux stocks/effectifs Production ci-dessus).
        if (type == SaisieType.VENTE_OEUFS) {
            loadStockOeufs();
        }
        if (type == SaisieType.VENTE_REFORME) {
            loadStockReforme();
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
        spinnerBatiment = findViewById(R.id.spinnerBatiment);
        etDate = findViewById(R.id.etDate);
        etHeure = findViewById(R.id.etHeure);
        btnValiderForm = findViewById(R.id.btnValiderForm);
        ImageButton btnBack = findViewById(R.id.btnBackForm);
        btnBack.setOnClickListener(v -> finish());

        groupCollecte = findViewById(R.id.groupCollecte);
        radioGroupUniteCollecte = findViewById(R.id.radioGroupUniteCollecte);
        tilOeufsCollectes = findViewById(R.id.tilOeufsCollectes);
        etOeufsCollectes = findViewById(R.id.etOeufsCollectes);
        etOeufsCasses = findViewById(R.id.etOeufsCasses);
        tvResumeCollecte = findViewById(R.id.tvResumeCollecte);
        radioGroupUniteCollecte.setOnCheckedChangeListener((group, checkedId) -> {
            tilOeufsCollectes.setHint(isCollecteEnAlveoles() ? "Nombre d'alvéoles collectées" : "Œufs collectés");
            calculerResumeCollecte();
        });

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
        tvStockReformeInfo = findViewById(R.id.tvStockReformeInfo);
        etNombreSujetsVente = findViewById(R.id.etNombreSujetsVente);
        etPrixUnitaireReforme = findViewById(R.id.etPrixUnitaireReforme);
        etMontantVenteReforme = findViewById(R.id.etMontantVenteReforme);

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

    private String[] categoriesPourType() {
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
        groupTransaction.setVisibility(
                (type == SaisieType.TRANSACTION_ENTREE || type == SaisieType.TRANSACTION_SORTIE) ? View.VISIBLE : View.GONE);
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
        etOeufsCollectes.addTextChangedListener(watcher);
        etOeufsCasses.addTextChangedListener(watcher);
    }

    private boolean isCollecteEnAlveoles() {
        return radioGroupUniteCollecte != null && radioGroupUniteCollecte.getCheckedRadioButtonId() == R.id.radioUniteCollecteAlveole;
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

    private void calculerResumeCollecte() {
        int saisie = parseIntSafe(etOeufsCollectes.getText());
        int total = isCollecteEnAlveoles() ? AlveoleUtils.alveolesToOeufs(saisie) : saisie;
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
     * Stock d'œufs vendables de TOUTE LA FERME (collectés - cassés - vendus), même
     * schéma retrofit → cache → repli hors ligne que loadStock() ci-dessus — la
     * valeur calculée côté serveur est gardée dans stockOeufsDisponible pour le
     * garde-fou de onValider() (le serveur reste juge en dernier ressort, mais on
     * refuse déjà côté client plutôt que de laisser l'utilisateur découvrir le refus
     * après coup). Pas de clé de cache par projet : un seul stock, pour toute la ferme.
     */
    private void loadStockOeufs() {
        String cacheKey = CachePrefetcher.CACHE_STOCK_OEUFS_FARM;
        StockOeufsResponse cached = getCachedOrNull(cacheKey, StockOeufsResponse.class);
        boolean hadCache = cached != null;
        if (hadCache) displayStockOeufs(cached, true);

        ApiClient.dataApi(this).getStockOeufs().enqueue(new Callback<ApiEnvelope<StockOeufsResponse>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<StockOeufsResponse>> call, Response<ApiEnvelope<StockOeufsResponse>> response) {
                StockOeufsResponse stock = response.isSuccessful() && response.body() != null ? response.body().getData() : null;
                if (stock != null) {
                    localDatabase.putCache(cacheKey, gson.toJson(stock));
                    displayStockOeufs(stock, false);
                } else if (!hadCache) {
                    displayStockOeufs(null, false);
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<StockOeufsResponse>> call, Throwable t) {
                if (!hadCache) displayStockOeufs(null, true);
            }
        });
    }

    private void displayStockOeufs(StockOeufsResponse stock, boolean fromCache) {
        stockOeufsDisponible = stock != null ? stock.getStockRestant() : null;
        if (stockOeufsDisponible != null) {
            String suffix = fromCache ? " (dernière donnée connue, hors ligne)" : "";
            tvStockOeufsInfo.setText(String.format(Locale.FRANCE, "Disponible à la vente (ferme) : %s%s",
                    AlveoleUtils.formatOeufsAvecAlveoles(stockOeufsDisponible), suffix));
        } else {
            tvStockOeufsInfo.setText(fromCache ? "Stock non disponible (hors ligne)" : "Stock non disponible");
        }
    }

    /**
     * Effectif vivant DU PROJET sélectionné (nbSujets - mortalité - déjà réformés) —
     * plafond de la saisie Réforme (Production), même schéma que loadStockOeufs().
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

    /**
     * Stock de sujets réformés vendables de TOUTE LA FERME (Reforme - déjà vendu),
     * plafond de la vente réforme (Finance) — distinct de l'effectif vivant d'UN
     * projet ci-dessus. Même schéma que loadStockOeufs().
     */
    private void loadStockReforme() {
        String cacheKey = CachePrefetcher.CACHE_STOCK_REFORME_FARM;
        StockReformeResponse cachedStock = getCachedOrNull(cacheKey, StockReformeResponse.class);
        boolean hadCache = cachedStock != null;
        if (hadCache) displayStockReforme(cachedStock, true);

        ApiClient.dataApi(this).getStockReforme().enqueue(new Callback<ApiEnvelope<StockReformeResponse>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<StockReformeResponse>> call, Response<ApiEnvelope<StockReformeResponse>> response) {
                StockReformeResponse stock = response.isSuccessful() && response.body() != null ? response.body().getData() : null;
                if (stock != null) {
                    localDatabase.putCache(cacheKey, gson.toJson(stock));
                    displayStockReforme(stock, false);
                } else if (!hadCache) {
                    displayStockReforme(null, false);
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<StockReformeResponse>> call, Throwable t) {
                if (!hadCache) displayStockReforme(null, true);
            }
        });
    }

    private void displayStockReforme(StockReformeResponse stock, boolean fromCache) {
        stockReformeDisponible = stock != null ? stock.getStockRestant() : null;
        if (stockReformeDisponible != null) {
            String suffix = fromCache ? " (dernière donnée connue, hors ligne)" : "";
            tvStockReformeInfo.setText(String.format(Locale.FRANCE, "Disponible à la vente (ferme) : %d sujet(s)%s", stockReformeDisponible, suffix));
        } else {
            tvStockReformeInfo.setText(fromCache ? "Stock non disponible (hors ligne)" : "Stock non disponible");
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
                boolean enAlveoles = isCollecteEnAlveoles();
                int saisie = parseIntSafe(etOeufsCollectes.getText());
                int collectes = enAlveoles ? AlveoleUtils.alveolesToOeufs(saisie) : saisie;
                int casses = parseIntSafe(etOeufsCasses.getText());
                if (collectes <= 0) {
                    toast(enAlveoles ? "Veuillez saisir le nombre d'alvéoles collectées" : "Veuillez saisir le nombre d'œufs collectés");
                    return;
                }
                CollecteOeufsCreateRequest req = new CollecteOeufsCreateRequest();
                req.projetUniqueId = projetUniqueId;
                req.batimentUniqueId = batimentUniqueId;
                req.date = date;
                req.heure = heure;
                req.oeufsCollectes = collectes; // toujours en œufs, quelle que soit l'unité saisie
                req.oeufsCasses = casses;
                requestObject = req;
                summary = enAlveoles
                        ? String.format(Locale.FRANCE, "%d alvéole(s) — %d œufs collectés (%d cassés)", saisie, collectes, casses)
                        : String.format(Locale.FRANCE, "%d œufs collectés (%d cassés)", collectes, casses);
                break;
            }
            case SOINS: {
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
                // Garde-fou client en plus de la validation serveur (voir loadStockOeufs) :
                // évite un aller-retour réseau pour découvrir le refus après coup.
                if (stockOeufsDisponible != null && quantite > stockOeufsDisponible) {
                    toast("Quantité supérieure au stock disponible (" + stockOeufsDisponible + " œuf(s))");
                    return;
                }
                VenteOeufsCreateRequest req = new VenteOeufsCreateRequest();
                req.date = date;
                req.heure = heure;
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
                    toast("Quantité supérieure au stock disponible (" + stockReformeDisponible + " sujet(s))");
                    return;
                }
                VenteReformeCreateRequest req = new VenteReformeCreateRequest();
                req.date = date;
                req.heure = heure;
                req.nombreSujets = nombreSujets;
                req.prixUnitaire = parseDoubleOrNull(etPrixUnitaireReforme.getText());
                req.montant = montant;
                requestObject = req;
                summary = String.format(Locale.FRANCE, "Vente réforme de %d sujet(s) (%,.0f FCFA)", nombreSujets, montant);
                break;
            }
            case TRANSACTION_ENTREE:
            case TRANSACTION_SORTIE: {
                Double montant = parseDoubleOrNull(etMontant.getText());
                String description = textOf(etDescriptionTransaction);
                if (montant == null || montant <= 0 || description.isEmpty()) {
                    toast("Veuillez saisir le montant et une description");
                    return;
                }
                boolean commun = checkCommun.isChecked();
                if (!commun && (projetUniqueId == null || projetUniqueId.isEmpty())) {
                    toast("Aucun projet actif : cochez \"commune\" ou sélectionnez un projet depuis l'accueil");
                    return;
                }
                List<String> projetsConcernes = commun ? getSelectedProjetsConcernesUniqueIds() : null;
                if (commun && projetsConcernes.isEmpty()) {
                    toast("Sélectionnez au moins un projet concerné par cette dépense/rentrée commune");
                    return;
                }
                TransactionCreateRequest req = new TransactionCreateRequest();
                req.type = (type == SaisieType.TRANSACTION_ENTREE) ? "ENTREE" : "SORTIE";
                req.commun = commun;
                req.projetUniqueId = commun ? null : projetUniqueId;
                req.projetsConcernesUniqueIds = projetsConcernes;
                req.date = date;
                req.description = description;
                req.montant = montant;
                req.categorie = (String) spinnerCategorie.getSelectedItem();
                requestObject = req;
                summary = String.format(Locale.FRANCE, "%s %,.0f FCFA — %s",
                        type == SaisieType.TRANSACTION_ENTREE ? "+" : "-", montant, description);
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
                if (req.oeufsCollectes != null) etOeufsCollectes.setText(String.valueOf(req.oeufsCollectes));
                if (req.oeufsCasses != null) etOeufsCasses.setText(String.valueOf(req.oeufsCasses));
                selectBatimentByUniqueId(req.batimentUniqueId);
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
                break;
            }
            case VENTE_REFORME: {
                VenteReformeCreateRequest req = gson.fromJson(json, VenteReformeCreateRequest.class);
                setDateHeure(req.date, req.heure);
                if (req.nombreSujets != null) etNombreSujetsVente.setText(String.valueOf(req.nombreSujets));
                if (req.prixUnitaire != null) etPrixUnitaireReforme.setText(String.valueOf(req.prixUnitaire));
                if (req.montant != null) etMontantVenteReforme.setText(String.valueOf(req.montant));
                break;
            }
            case TRANSACTION_ENTREE:
            case TRANSACTION_SORTIE: {
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
