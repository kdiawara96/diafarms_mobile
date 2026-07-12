package com.mobile.diafarms.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.Gson;
import com.mobile.diafarms.R;
import com.mobile.diafarms.data.LocalDatabase;
import com.mobile.diafarms.data.SessionManager;
import com.mobile.diafarms.data.SyncManager;
import com.mobile.diafarms.models.SaisieLocale;
import com.mobile.diafarms.models.SaisieType;
import com.mobile.diafarms.models.User;
import com.mobile.diafarms.network.ApiClient;
import com.mobile.diafarms.network.dto.ApiEnvelope;
import com.mobile.diafarms.network.dto.ProjetSelectResponse;
import com.mobile.diafarms.network.dto.TransactionCreateRequest;
import com.mobile.diafarms.ui.saisie.SaisieFormActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";

    // Session et données
    private SessionManager sessionManager;
    private LocalDatabase localDatabase;
    private User currentUser;
    private ProjetSelectResponse currentProjet;
    private List<ProjetSelectResponse> projetsList = new ArrayList<>();

    // Vues Header
    private TextView tvAgentName;
    private TextView badgeProduction;
    private TextView badgeFinance;
    private FrameLayout btnSync;
    private View indicatorSync;

    // Vues Projet
    private Spinner spinnerProjets;
    private TextView tvPoulesCount;
    private TextView tvTauxPonte;
    private TextView tvJoursRestants;

    // Vues Dernière saisie
    private CardView cardLastEntry;
    private TextView tvLastEntryTitle;
    private TextView tvLastEntryDetail;
    private TextView tvLastEntryTime;

    // Vues Production
    private TextView tvSectionProduction;
    private GridLayout gridProduction;
    private CardView btnCollecteOeufs;
    private CardView btnAlimentation;
    private CardView btnSoins;
    private CardView btnMortalite;
    private CardView btnAchatAliment;

    // Vues Finance
    private TextView tvSectionFinance;
    private GridLayout gridFinance;
    private CardView btnEntreeArgent;
    private CardView btnSortieArgent;
    private CardView cardStatsFinance;
    private TextView tvMesEntrees;
    private TextView tvMesSorties;

    // Vues Bottom
    private View indicatorConnection;
    private TextView tvConnectionStatus;
    private TextView tvPendingCount;
    private Button btnSyncNow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.appBar), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        sessionManager = new SessionManager(this);
        localDatabase = new LocalDatabase(this);

        if (!sessionManager.isLoggedIn()) {
            redirectToLogin();
            return;
        }

        currentUser = sessionManager.getCurrentUser();
        if (currentUser == null) {
            redirectToLogin();
            return;
        }

        bindViews();
        setupHeader();
        setupVisibilityByRole();
        setupClickListeners();
        setupSyncStatus();
        loadProjets();
        loadLastEntry();
        updateFinanceStats();
    }

    private void bindViews() {
        tvAgentName = findViewById(R.id.tvAgentName);
        badgeProduction = findViewById(R.id.badgeProduction);
        badgeFinance = findViewById(R.id.badgeFinance);
        btnSync = findViewById(R.id.btnSync);
        indicatorSync = findViewById(R.id.indicatorSync);

        spinnerProjets = findViewById(R.id.spinnerProjets);
        tvPoulesCount = findViewById(R.id.tvPoulesCount);
        tvTauxPonte = findViewById(R.id.tvTauxPonte);
        tvJoursRestants = findViewById(R.id.tvJoursRestants);

        cardLastEntry = findViewById(R.id.cardLastEntry);
        tvLastEntryTitle = findViewById(R.id.tvLastEntryTitle);
        tvLastEntryDetail = findViewById(R.id.tvLastEntryDetail);
        tvLastEntryTime = findViewById(R.id.tvLastEntryTime);

        tvSectionProduction = findViewById(R.id.tvSectionProduction);
        gridProduction = findViewById(R.id.gridProduction);
        btnCollecteOeufs = findViewById(R.id.btnCollecteOeufs);
        btnAlimentation = findViewById(R.id.btnAlimentation);
        btnSoins = findViewById(R.id.btnSoins);
        btnMortalite = findViewById(R.id.btnMortalite);
        btnAchatAliment = findViewById(R.id.btnAchatAliment);

        tvSectionFinance = findViewById(R.id.tvSectionFinance);
        gridFinance = findViewById(R.id.gridFinance);
        btnEntreeArgent = findViewById(R.id.btnEntreeArgent);
        btnSortieArgent = findViewById(R.id.btnSortieArgent);
        cardStatsFinance = findViewById(R.id.cardStatsFinance);
        tvMesEntrees = findViewById(R.id.tvMesEntrees);
        tvMesSorties = findViewById(R.id.tvMesSorties);

        indicatorConnection = findViewById(R.id.indicatorConnection);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvPendingCount = findViewById(R.id.tvPendingCount);
        btnSyncNow = findViewById(R.id.btnSyncNow);
    }

    private void setupHeader() {
        tvAgentName.setText(currentUser.getNom());
        badgeProduction.setVisibility(currentUser.isProduction() ? View.VISIBLE : View.GONE);
        badgeFinance.setVisibility(currentUser.isFinance() ? View.VISIBLE : View.GONE);
    }

    private void setupVisibilityByRole() {
        boolean isProduction = currentUser.isProduction();
        boolean isFinance = currentUser.isFinance();

        tvSectionProduction.setVisibility(isProduction ? View.VISIBLE : View.GONE);
        gridProduction.setVisibility(isProduction ? View.VISIBLE : View.GONE);

        tvSectionFinance.setVisibility(isFinance ? View.VISIBLE : View.GONE);
        gridFinance.setVisibility(isFinance ? View.VISIBLE : View.GONE);
        cardStatsFinance.setVisibility(isFinance ? View.VISIBLE : View.GONE);
    }

    /** Charge les projets réels de la ferme (GET /projets/select) pour peupler le sélecteur. */
    private void loadProjets() {
        ApiClient.dataApi(this).getProjetsSelect().enqueue(new Callback<ApiEnvelope<List<ProjetSelectResponse>>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<List<ProjetSelectResponse>>> call, Response<ApiEnvelope<List<ProjetSelectResponse>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    projetsList = response.body().getData();
                } else {
                    projetsList = new ArrayList<>();
                }
                setupProjetSelector();
            }

            @Override
            public void onFailure(Call<ApiEnvelope<List<ProjetSelectResponse>>> call, Throwable t) {
                Log.e(TAG, "Impossible de charger les projets", t);
                Toast.makeText(HomeActivity.this, "Impossible de charger les projets (hors ligne ?)", Toast.LENGTH_SHORT).show();
                projetsList = new ArrayList<>();
                setupProjetSelector();
            }
        });
    }

    private void setupProjetSelector() {
        List<String> projetLabels = new ArrayList<>();
        for (ProjetSelectResponse p : projetsList) {
            projetLabels.add(p.getLabel());
        }
        if (projetLabels.isEmpty()) {
            projetLabels.add("Aucun projet disponible");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, projetLabels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProjets.setAdapter(adapter);

        spinnerProjets.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < projetsList.size()) {
                    currentProjet = projetsList.get(position);
                    sessionManager.setCurrentProjetId(currentProjet.getUniqueId());
                    updateProjetDisplay();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        if (!projetsList.isEmpty()) {
            currentProjet = projetsList.get(0);
            updateProjetDisplay();
        } else {
            currentProjet = null;
            tvPoulesCount.setText("—");
            tvTauxPonte.setText("—");
            tvJoursRestants.setText("—");
        }
    }

    private void updateProjetDisplay() {
        // Le sélecteur /projets/select renvoie uniquement code/titre (pas les stats
        // d'élevage) : ces indicateurs nécessiteraient l'endpoint /projets/list complet.
        tvPoulesCount.setText("—");
        tvTauxPonte.setText("—");
        tvJoursRestants.setText("—");
    }

    private void setupClickListeners() {
        // Production
        btnCollecteOeufs.setOnClickListener(v -> openSaisie(SaisieType.COLLECTE_OEUFS));
        btnAlimentation.setOnClickListener(v -> openSaisie(SaisieType.ALIMENTATION_CONSOMMATION));
        btnSoins.setOnClickListener(v -> openSaisie(SaisieType.SOINS));
        btnMortalite.setOnClickListener(v -> openSaisie(SaisieType.MORTALITE));
        btnAchatAliment.setOnClickListener(v -> openSaisie(SaisieType.ALIMENTATION_ACHAT));

        // Finance
        btnEntreeArgent.setOnClickListener(v -> openSaisie(SaisieType.TRANSACTION_ENTREE));
        btnSortieArgent.setOnClickListener(v -> openSaisie(SaisieType.TRANSACTION_SORTIE));

        // Sync
        btnSync.setOnClickListener(v -> forceSync());
        btnSyncNow.setOnClickListener(v -> forceSync());

        // Mes saisies
        cardLastEntry.setOnClickListener(v -> startActivity(new Intent(this, MesSaisiesActivity.class)));
        tvPendingCount.setOnClickListener(v -> startActivity(new Intent(this, MesSaisiesActivity.class)));
    }

    private void openSaisie(SaisieType type) {
        boolean needsProjet = type != SaisieType.TRANSACTION_ENTREE && type != SaisieType.TRANSACTION_SORTIE;
        if (needsProjet && currentProjet == null) {
            Toast.makeText(this, "Veuillez sélectionner un projet", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, SaisieFormActivity.class);
        intent.putExtra(SaisieFormActivity.EXTRA_TYPE, type.name());
        if (currentProjet != null) {
            intent.putExtra(SaisieFormActivity.EXTRA_PROJET_ID, currentProjet.getUniqueId());
            intent.putExtra(SaisieFormActivity.EXTRA_PROJET_LABEL, currentProjet.getLabel());
        }
        startActivity(intent);
    }

    private void setupSyncStatus() {
        int pending = localDatabase.countPending();

        if (pending > 0) {
            tvPendingCount.setVisibility(View.VISIBLE);
            tvPendingCount.setText(pending + " saisie(s)");
            btnSyncNow.setVisibility(View.VISIBLE);
            indicatorSync.setBackgroundResource(R.drawable.circle_orange);
        } else {
            tvPendingCount.setVisibility(View.GONE);
            btnSyncNow.setVisibility(View.GONE);
            indicatorSync.setBackgroundResource(R.drawable.circle_green);
        }
    }

    private void loadLastEntry() {
        List<SaisieLocale> all = localDatabase.getAllSaisies();
        if (all.isEmpty()) {
            tvLastEntryTitle.setText("Dernière saisie");
            tvLastEntryDetail.setText("Aucune saisie pour l'instant");
            tvLastEntryTime.setText("--");
            return;
        }

        SaisieLocale last = all.get(0); // triées par created_at DESC
        tvLastEntryTitle.setText(last.getType().getLabel());
        tvLastEntryDetail.setText(last.getDisplaySummary());
        tvLastEntryTime.setText(relativeTime(last.getCreatedAt()));
    }

    private String relativeTime(long timestampMs) {
        long diffMinutes = (System.currentTimeMillis() - timestampMs) / 60000;
        if (diffMinutes < 1) return "À l'instant";
        if (diffMinutes < 60) return "Il y a " + diffMinutes + "min";
        long diffHours = diffMinutes / 60;
        if (diffHours < 24) return "Il y a " + diffHours + "h";
        return "Il y a " + (diffHours / 24) + "j";
    }

    /** Additionne les transactions locales (LOCAL + SYNCED) du jour, saisies par cet agent. */
    private void updateFinanceStats() {
        if (!currentUser.isFinance()) return;

        Gson gson = new Gson();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(new java.util.Date());

        double entrees = 0;
        double sorties = 0;

        for (SaisieLocale s : localDatabase.getSaisiesByType(SaisieType.TRANSACTION_ENTREE)) {
            TransactionCreateRequest req = gson.fromJson(s.getPayloadJson(), TransactionCreateRequest.class);
            if (req.montant != null && today.equals(req.date)) entrees += req.montant;
        }
        for (SaisieLocale s : localDatabase.getSaisiesByType(SaisieType.TRANSACTION_SORTIE)) {
            TransactionCreateRequest req = gson.fromJson(s.getPayloadJson(), TransactionCreateRequest.class);
            if (req.montant != null && today.equals(req.date)) sorties += req.montant;
        }

        tvMesEntrees.setText(formatMontant(entrees));
        tvMesSorties.setText(formatMontant(sorties));
    }

    private String formatMontant(double montant) {
        return String.format(Locale.FRANCE, "%,.0f FCFA", montant);
    }

    private void forceSync() {
        int pending = localDatabase.countPending();
        if (pending == 0) {
            Toast.makeText(this, "Rien à synchroniser", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSyncNow.setEnabled(false);
        Toast.makeText(this, "Synchronisation de " + pending + " saisie(s)...", Toast.LENGTH_SHORT).show();

        new SyncManager(this).syncAll(new SyncManager.SyncCallback() {
            @Override
            public void onComplete(int success, int failed) {
                runOnUiThread(() -> {
                    btnSyncNow.setEnabled(true);
                    String message = failed == 0
                            ? success + " saisie(s) synchronisée(s) avec succès"
                            : success + " synchronisée(s), " + failed + " en échec (réessayez plus tard)";
                    Toast.makeText(HomeActivity.this, message, Toast.LENGTH_LONG).show();
                    setupSyncStatus();
                    loadLastEntry();
                    updateFinanceStats();
                });
            }
        });
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (indicatorConnection == null || tvConnectionStatus == null) {
            return; // pas encore initialisé (premier onCreate en cours)
        }
        setupSyncStatus();
        updateFinanceStats();
        loadLastEntry();
    }
}
