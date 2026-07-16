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
import com.google.gson.Gson;
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
import com.mobile.diafarms.network.dto.ReformeCreateRequest;
import com.mobile.diafarms.network.dto.SoinsCreateRequest;
import com.mobile.diafarms.network.dto.StockAlimentResponse;
import com.mobile.diafarms.network.dto.StockOeufsResponse;
import com.mobile.diafarms.network.dto.StockReformeResponse;
import com.mobile.diafarms.network.dto.TransactionCreateRequest;
import com.mobile.diafarms.network.dto.VenteOeufsCreateRequest;
import com.mobile.diafarms.network.dto.VenteReformeCreateRequest;

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

    private static final String[] CATEGORIES_TRANSACTION = {"Vente", "Achat", "Salaire", "Autre"};
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

    // Vues communes
    private TextView tvTitreForm, tvProjetForm, tvStockInfo;
    private Spinner spinnerBatiment;
    private TextInputEditText etDate, etHeure;
    private MaterialButton btnValiderForm;

    // Collecte
    private View groupCollecte;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_saisie_form);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_saisie_form), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
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
        tvStockOeufsInfo = findViewById(R.id.tvStockOeufsInfo);
        etQuantiteOeufsVente = findViewById(R.id.etQuantiteOeufsVente);
        etPrixUnitaireOeufs = findViewById(R.id.etPrixUnitaireOeufs);
        etMontantVenteOeufs = findViewById(R.id.etMontantVenteOeufs);

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

        ArrayAdapter<String> categorieAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, CATEGORIES_TRANSACTION);
        categorieAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategorie.setAdapter(categorieAdapter);

        ArrayAdapter<String> typeSoinAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, TYPES_SOIN);
        typeSoinAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTypeSoin.setAdapter(typeSoinAdapter);
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

    private void calculerResumeCollecte() {
        int total = parseIntSafe(etOeufsCollectes.getText());
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

        ApiClient.dataApi(this).getProjetDetail(projetUniqueId).enqueue(new Callback<ApiEnvelope<ProjetDetailResponse>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<ProjetDetailResponse>> call, Response<ApiEnvelope<ProjetDetailResponse>> response) {
                ProjetDetailResponse detail = response.isSuccessful() && response.body() != null
                        ? response.body().getData() : null;
                if (detail != null) {
                    localDatabase.putCache(cacheKey, gson.toJson(detail));
                    applyBatiments(detail);
                } else {
                    loadBatimentsFromCache(cacheKey);
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<ProjetDetailResponse>> call, Throwable t) {
                // Hors ligne : on retombe sur le même cache que l'accueil (voir
                // CachePrefetcher/HomeActivity) plutôt que d'abandonner directement —
                // le bâtiment reste optionnel côté backend si le cache est vide aussi.
                loadBatimentsFromCache(cacheKey);
            }
        });
    }

    private void loadBatimentsFromCache(String cacheKey) {
        String cached = localDatabase.getCache(cacheKey);
        if (cached != null) {
            applyBatiments(gson.fromJson(cached, ProjetDetailResponse.class));
        } else {
            populateBatimentSpinner();
        }
    }

    private void applyBatiments(ProjetDetailResponse detail) {
        if (detail.getOccupationBatiment() != null) {
            batiments = new ArrayList<>();
            for (OccupationBatimentResponse occ : detail.getOccupationBatiment()) {
                if (occ.getDateSortie() == null) { // occupation encore active
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
    }

    private String getSelectedBatimentUniqueId() {
        int position = spinnerBatiment.getSelectedItemPosition();
        if (position <= 0 || position - 1 >= batiments.size()) return null;
        return batiments.get(position - 1).getBatimentUniqueId();
    }

    private void selectBatimentByUniqueId(String uniqueId) {
        if (uniqueId == null) return;
        for (int i = 0; i < batiments.size(); i++) {
            if (uniqueId.equals(batiments.get(i).getBatimentUniqueId())) {
                spinnerBatiment.setSelection(i + 1);
                return;
            }
        }
    }

    private void loadStock() {
        String cacheKey = CachePrefetcher.CACHE_STOCK_ALIMENT_PREFIX + projetUniqueId;

        ApiClient.dataApi(this).getStockAliment(projetUniqueId).enqueue(new Callback<ApiEnvelope<StockAlimentResponse>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<StockAlimentResponse>> call, Response<ApiEnvelope<StockAlimentResponse>> response) {
                StockAlimentResponse stock = response.isSuccessful() && response.body() != null ? response.body().getData() : null;
                if (stock != null) {
                    localDatabase.putCache(cacheKey, gson.toJson(stock));
                }
                displayStock(stock, false);
            }

            @Override
            public void onFailure(Call<ApiEnvelope<StockAlimentResponse>> call, Throwable t) {
                // Hors ligne : on affiche la dernière valeur connue (préchargée après
                // le login/scan QR ou vue depuis l'accueil, voir CachePrefetcher)
                // plutôt que de déclarer directement l'info indisponible.
                String cached = localDatabase.getCache(cacheKey);
                StockAlimentResponse stock = cached != null ? gson.fromJson(cached, StockAlimentResponse.class) : null;
                displayStock(stock, true);
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

        ApiClient.dataApi(this).getStockOeufs().enqueue(new Callback<ApiEnvelope<StockOeufsResponse>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<StockOeufsResponse>> call, Response<ApiEnvelope<StockOeufsResponse>> response) {
                StockOeufsResponse stock = response.isSuccessful() && response.body() != null ? response.body().getData() : null;
                if (stock != null) {
                    localDatabase.putCache(cacheKey, gson.toJson(stock));
                }
                displayStockOeufs(stock, false);
            }

            @Override
            public void onFailure(Call<ApiEnvelope<StockOeufsResponse>> call, Throwable t) {
                String cached = localDatabase.getCache(cacheKey);
                StockOeufsResponse stock = cached != null ? gson.fromJson(cached, StockOeufsResponse.class) : null;
                displayStockOeufs(stock, true);
            }
        });
    }

    private void displayStockOeufs(StockOeufsResponse stock, boolean fromCache) {
        stockOeufsDisponible = stock != null ? stock.getStockRestant() : null;
        if (stockOeufsDisponible != null) {
            String suffix = fromCache ? " (dernière donnée connue, hors ligne)" : "";
            tvStockOeufsInfo.setText(String.format(Locale.FRANCE, "Disponible à la vente (ferme) : %d œuf(s)%s", stockOeufsDisponible, suffix));
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

        ApiClient.dataApi(this).getEffectifReforme(projetUniqueId).enqueue(new Callback<ApiEnvelope<EffectifReformeResponse>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<EffectifReformeResponse>> call, Response<ApiEnvelope<EffectifReformeResponse>> response) {
                EffectifReformeResponse effectif = response.isSuccessful() && response.body() != null ? response.body().getData() : null;
                if (effectif != null) {
                    localDatabase.putCache(cacheKey, gson.toJson(effectif));
                }
                displayEffectifReforme(effectif, false);
            }

            @Override
            public void onFailure(Call<ApiEnvelope<EffectifReformeResponse>> call, Throwable t) {
                String cached = localDatabase.getCache(cacheKey);
                EffectifReformeResponse effectif = cached != null ? gson.fromJson(cached, EffectifReformeResponse.class) : null;
                displayEffectifReforme(effectif, true);
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

        ApiClient.dataApi(this).getStockReforme().enqueue(new Callback<ApiEnvelope<StockReformeResponse>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<StockReformeResponse>> call, Response<ApiEnvelope<StockReformeResponse>> response) {
                StockReformeResponse stock = response.isSuccessful() && response.body() != null ? response.body().getData() : null;
                if (stock != null) {
                    localDatabase.putCache(cacheKey, gson.toJson(stock));
                }
                displayStockReforme(stock, false);
            }

            @Override
            public void onFailure(Call<ApiEnvelope<StockReformeResponse>> call, Throwable t) {
                String cached = localDatabase.getCache(cacheKey);
                StockReformeResponse stock = cached != null ? gson.fromJson(cached, StockReformeResponse.class) : null;
                displayStockReforme(stock, true);
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
                int collectes = parseIntSafe(etOeufsCollectes.getText());
                int casses = parseIntSafe(etOeufsCasses.getText());
                if (collectes <= 0) {
                    toast("Veuillez saisir le nombre d'œufs collectés");
                    return;
                }
                CollecteOeufsCreateRequest req = new CollecteOeufsCreateRequest();
                req.projetUniqueId = projetUniqueId;
                req.batimentUniqueId = batimentUniqueId;
                req.date = date;
                req.heure = heure;
                req.oeufsCollectes = collectes;
                req.oeufsCasses = casses;
                requestObject = req;
                summary = String.format(Locale.FRANCE, "%d œufs collectés (%d cassés)", collectes, casses);
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
                int quantite = parseIntSafe(etQuantiteOeufsVente.getText());
                Double montant = parseDoubleOrNull(etMontantVenteOeufs.getText());
                if (quantite <= 0) {
                    toast("Veuillez saisir le nombre d'œufs vendus");
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
                req.quantiteOeufs = quantite;
                req.prixUnitaire = parseDoubleOrNull(etPrixUnitaireOeufs.getText());
                req.montant = montant;
                requestObject = req;
                summary = String.format(Locale.FRANCE, "Vente de %d œufs (%,.0f FCFA)", quantite, montant);
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
                TransactionCreateRequest req = new TransactionCreateRequest();
                req.type = (type == SaisieType.TRANSACTION_ENTREE) ? "ENTREE" : "SORTIE";
                req.commun = commun;
                req.projetUniqueId = commun ? null : projetUniqueId;
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
                selectSpinnerValue(spinnerCategorie, CATEGORIES_TRANSACTION, req.categorie);
                checkCommun.setChecked(Boolean.TRUE.equals(req.commun));
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
