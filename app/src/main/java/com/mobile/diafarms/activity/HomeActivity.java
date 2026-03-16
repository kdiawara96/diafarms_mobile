package com.mobile.diafarms.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.mobile.diafarms.R;
import com.mobile.diafarms.data.LocalDatabase;
import com.mobile.diafarms.data.SessionManager;
import com.mobile.diafarms.models.Projet;
import com.mobile.diafarms.models.User;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    // Session et données
    private SessionManager sessionManager;
    private LocalDatabase localDatabase;
    private User currentUser;
    private Projet currentProjet;
    private List<Projet> projetsList;

    // Vues Header
    private TextView tvAgentName;
    private LinearLayout llRoles;
    private TextView badgeProduction;
    private TextView badgeFinance;
    private ImageButton btnSync;
    private View indicatorSync;
    private FrameLayout flAvatar;

    // Vues Projet
    private Spinner spinnerProjets;
    private androidx.constraintlayout.widget.ConstraintLayout clProjetResume;
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

        // Initialisation
        sessionManager = new SessionManager(this);
        localDatabase = new LocalDatabase(this);

        // Vérifier session
        if (!sessionManager.isLoggedIn() || !sessionManager.isQRValid()) {
            redirectToLogin();
            return;
        }

        currentUser = sessionManager.getCurrentUser();
        if (currentUser == null) {
            redirectToLogin();
            return;
        }

        // Binding vues
        bindViews();

        // Configuration selon rôle
        setupHeader();
        setupVisibilityByRole();
        setupProjetSelector();
        setupClickListeners();
        setupSyncStatus();

        // Chargement données
        loadProjets();
        loadLastEntry();
        updateFinanceStats();
    }

    private void bindViews() {

        // Vérifier que setContentView a été appelé
        if (findViewById(android.R.id.content) == null) {
            android.util.Log.e("HomeActivity", "ERREUR: setContentView non appelé !");
            return;
        }

        // Header
        tvAgentName = findViewById(R.id.tvAgentName);
        llRoles = findViewById(R.id.llRoles);
        badgeProduction = findViewById(R.id.badgeProduction);
        badgeFinance = findViewById(R.id.badgeFinance);
        btnSync = findViewById(R.id.btnSync);
        indicatorSync = findViewById(R.id.indicatorSync);
        flAvatar = findViewById(R.id.flAvatar);

        // Projet
        spinnerProjets = findViewById(R.id.spinnerProjets);
        clProjetResume = findViewById(R.id.clProjetResume);
        tvPoulesCount = findViewById(R.id.tvPoulesCount);
        tvTauxPonte = findViewById(R.id.tvTauxPonte);
        tvJoursRestants = findViewById(R.id.tvJoursRestants);

        // Dernière saisie
        cardLastEntry = findViewById(R.id.cardLastEntry);
        tvLastEntryTitle = findViewById(R.id.tvLastEntryTitle);
        tvLastEntryDetail = findViewById(R.id.tvLastEntryDetail);
        tvLastEntryTime = findViewById(R.id.tvLastEntryTime);

        // Production
        tvSectionProduction = findViewById(R.id.tvSectionProduction);
        gridProduction = findViewById(R.id.gridProduction);
        btnCollecteOeufs = findViewById(R.id.btnCollecteOeufs);
        btnAlimentation = findViewById(R.id.btnAlimentation);
        btnSoins = findViewById(R.id.btnSoins);
        btnMortalite = findViewById(R.id.btnMortalite);

        // Finance
        tvSectionFinance = findViewById(R.id.tvSectionFinance);
        gridFinance = findViewById(R.id.gridFinance);
        btnEntreeArgent = findViewById(R.id.btnEntreeArgent);
        btnSortieArgent = findViewById(R.id.btnSortieArgent);
        cardStatsFinance = findViewById(R.id.cardStatsFinance);
        tvMesEntrees = findViewById(R.id.tvMesEntrees);
        tvMesSorties = findViewById(R.id.tvMesSorties);

        // Bottom
        indicatorConnection = findViewById(R.id.indicatorConnection);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvPendingCount = findViewById(R.id.tvPendingCount);
        btnSyncNow = findViewById(R.id.btnSyncNow);


        // LOG de vérification
        android.util.Log.d("HomeActivity", "bindViews() OK");
        android.util.Log.d("HomeActivity", "  indicatorConnection = " + (indicatorConnection != null ? "OK" : "NULL"));
        android.util.Log.d("HomeActivity", "  tvConnectionStatus = " + (tvConnectionStatus != null ? "OK" : "NULL"));
    }

    private void setupHeader() {
        // Nom agent
        tvAgentName.setText(currentUser.getNom());

        // Badges rôles
        badgeProduction.setVisibility(currentUser.isProduction() ? View.VISIBLE : View.GONE);
        badgeFinance.setVisibility(currentUser.isFinance() ? View.VISIBLE : View.GONE);
    }

    private void setupVisibilityByRole() {
        boolean isProduction = currentUser.isProduction();
        boolean isFinance = currentUser.isFinance();

        // Section Production
        tvSectionProduction.setVisibility(isProduction ? View.VISIBLE : View.GONE);
        gridProduction.setVisibility(isProduction ? View.VISIBLE : View.GONE);

        // Section Finance
        tvSectionFinance.setVisibility(isFinance ? View.VISIBLE : View.GONE);
        gridFinance.setVisibility(isFinance ? View.VISIBLE : View.GONE);
        cardStatsFinance.setVisibility(isFinance ? View.VISIBLE : View.GONE);

        // Si double rôle, ajuster espacement
        if (isProduction && isFinance) {
            // Les deux sections visibles
        }
    }

    private void setupProjetSelector() {
        // Mock données projets
        projetsList = new ArrayList<>();

        Projet p1 = new Projet();
        p1.setId("proj_001");
        p1.setNumero("P-001");
        p1.setTitre("Poussins Oct 2025");
        p1.setNbPoulesActuelles(980);
        p1.setTauxPonteActuel(85);

        Projet p2 = new Projet();
        p2.setId("proj_002");
        p2.setNumero("P-002");
        p2.setTitre("Poussins Fév 2026");
        p2.setNbPoulesActuelles(1480);
        p2.setTauxPonteActuel(22);

        projetsList.add(p1);
        projetsList.add(p2);

        // Adapter spinner
        List<String> projetLabels = new ArrayList<>();
        for (Projet p : projetsList) {
            projetLabels.add(p.getNumero() + " - " + p.getTitre());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, projetLabels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProjets.setAdapter(adapter);

        // Sélection
        spinnerProjets.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentProjet = projetsList.get(position);
                sessionManager.setCurrentProjetId(currentProjet.getId());
                updateProjetDisplay();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Sélectionner premier par défaut
        if (!projetsList.isEmpty()) {
            currentProjet = projetsList.get(0);
            updateProjetDisplay();
        }
    }

    private void updateProjetDisplay() {
        if (currentProjet == null) return;

        tvPoulesCount.setText(String.valueOf(currentProjet.getNbPoulesActuelles()));
        tvTauxPonte.setText((int)currentProjet.getTauxPonteActuel() + "%");
        tvJoursRestants.setText(currentProjet.getJoursRestants() + "j");
    }

    private void setupClickListeners() {
        // Production
//        btnCollecteOeufs.setOnClickListener(v -> openSaisie(SaisieOeufsActivity.class));
//        btnAlimentation.setOnClickListener(v -> openSaisie(SaisieAlimentActivity.class));
//        btnSoins.setOnClickListener(v -> openSaisie(SaisieSoinsActivity.class));
//        btnMortalite.setOnClickListener(v -> openSaisie(SaisieMortaliteActivity.class));
//
//        // Finance
//        btnEntreeArgent.setOnClickListener(v -> openSaisie(SaisieEntreeActivity.class));
//        btnSortieArgent.setOnClickListener(v -> openSaisie(SaisieSortieActivity.class));

        // Sync
        btnSync.setOnClickListener(v -> forceSync());
        btnSyncNow.setOnClickListener(v -> forceSync());
    }

    private void openSaisie(Class<?> activityClass) {
        if (currentProjet == null) {
            Toast.makeText(this, "Veuillez sélectionner un projet", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, activityClass);
        intent.putExtra("projet_id", currentProjet.getId());
        intent.putExtra("projet_titre", currentProjet.getTitre());
        intent.putExtra("user_id", currentUser.getId());
        startActivity(intent);
    }

    private void setupSyncStatus() {
        // Vérifier connexion (simplifié)
        boolean isOnline = true; // TODO: vérifier réellement

        if (isOnline) {
            indicatorConnection.setBackgroundResource(R.drawable.circle_green);
            tvConnectionStatus.setText("En ligne • Sync auto");
        } else {
            indicatorConnection.setBackgroundResource(R.drawable.circle_red);
            tvConnectionStatus.setText("Hors ligne");
        }

        // Compter données en attente
        int pending = localDatabase.getEnregistrementsNonSync().size()
                + localDatabase.getTransactionsNonSync().size();

        if (pending > 0) {
            tvPendingCount.setVisibility(View.VISIBLE);
            tvPendingCount.setText(pending + " saisies");
            btnSyncNow.setVisibility(View.VISIBLE);
            indicatorSync.setBackgroundResource(R.drawable.circle_orange);
        } else {
            tvPendingCount.setVisibility(View.GONE);
            btnSyncNow.setVisibility(View.GONE);
            indicatorSync.setBackgroundResource(R.drawable.circle_green);
        }
    }

    private void loadProjets() {
        // Déjà chargé dans setupProjetSelector
    }

    private void loadLastEntry() {
        // Mock dernière saisie
        tvLastEntryTitle.setText("Dernière collecte");
        tvLastEntryDetail.setText("850 œufs • 27 alvéoles • 06:30");
        tvLastEntryTime.setText("Il y a 2h");
    }

    private void updateFinanceStats() {
        if (!currentUser.isFinance()) return;

        double entrees = localDatabase.getTotalEntreesAujourdhui(currentUser.getId());
        double sorties = localDatabase.getTotalSortiesAujourdhui(currentUser.getId());

        tvMesEntrees.setText(formatMontant(entrees));
        tvMesSorties.setText(formatMontant(sorties));
    }

    private String formatMontant(double montant) {
        return String.format("%,.0f FCFA", montant);
    }

    private void forceSync() {
        Toast.makeText(this, "Synchronisation...", Toast.LENGTH_SHORT).show();
        // TODO: implémenter sync réseau
        setupSyncStatus();
    }

    private void redirectToLogin() {
//        Intent intent = new Intent(this, LoginQRActivity.class);
//        startActivity(intent);
//        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // VÉRIFICATION CRITIQUE : ne rien faire si pas encore initialisé
        if (indicatorConnection == null || tvConnectionStatus == null) {
            android.util.Log.w("HomeActivity", "onResume: vues non initialisées, on ignore");
            return;
        }
        // Rafraîchir à chaque retour
        setupSyncStatus();
        updateFinanceStats();
        loadLastEntry();
    }
}