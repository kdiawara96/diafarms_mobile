package com.mobile.diafarms.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
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
import com.google.gson.reflect.TypeToken;
import com.mobile.diafarms.R;
import com.mobile.diafarms.data.CachePrefetcher;
import com.mobile.diafarms.data.LocalDatabase;
import com.mobile.diafarms.data.SessionManager;
import com.mobile.diafarms.data.SyncManager;
import com.mobile.diafarms.models.SaisieLocale;
import com.mobile.diafarms.models.SaisieType;
import com.mobile.diafarms.models.User;
import com.mobile.diafarms.network.ApiClient;
import com.mobile.diafarms.network.dto.ApiEnvelope;
import com.mobile.diafarms.network.dto.NotificationResponse;
import com.mobile.diafarms.network.dto.OccupationBatimentResponse;
import com.mobile.diafarms.network.dto.ProjetDetailResponse;
import com.mobile.diafarms.network.dto.ProjetSelectResponse;
import com.mobile.diafarms.network.dto.TransactionCreateRequest;
import com.mobile.diafarms.ui.saisie.SaisieFormActivity;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";

    // Cache local (voir LocalDatabase.putCache/getCache et CachePrefetcher) : permet
    // d'afficher les dernières données connues quand le réseau est indisponible plutôt
    // qu'un écran vierge — rafraîchi à chaque appel réseau réussi et après chaque
    // synchronisation. Clés partagées avec CachePrefetcher pour que le préchargement
    // déclenché après le login/scan QR écrive au même endroit que cet écran.
    private static final String CACHE_PROJETS_SELECT = CachePrefetcher.CACHE_PROJETS_SELECT;
    private static final String CACHE_PROJET_DETAIL_PREFIX = CachePrefetcher.CACHE_PROJET_DETAIL_PREFIX;
    private static final String CACHE_NOTIFICATIONS_PREFIX = CachePrefetcher.CACHE_NOTIFICATIONS_PREFIX;

    // Session et données
    private SessionManager sessionManager;
    private LocalDatabase localDatabase;
    private final Gson gson = new Gson();
    private User currentUser;
    private ProjetSelectResponse currentProjet;
    private List<ProjetSelectResponse> projetsList = new ArrayList<>();

    // Vues Header
    private TextView tvAgentName;
    private TextView badgeProduction;
    private TextView badgeFinance;
    private View indicatorSync;

    // Vues Projet
    private Spinner spinnerProjets;
    private TextView tvPoulesCount;
    private TextView tvTauxPonte;
    private TextView tvJoursRestants;
    private TextView tvBatimentsOccupes;

    // Vues Dernière saisie
    private CardView cardLastEntry;
    private CardView cardAlertes;
    private TextView tvAlertesDetail;
    private List<NotificationResponse> alertesList = new ArrayList<>();
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
    private CardView btnReforme;

    // Vues Finance
    private TextView tvSectionFinance;
    private GridLayout gridFinance;
    private CardView btnEntreeArgent;
    private CardView btnSortieArgent;
    private CardView btnVenteOeufs;
    private CardView btnVenteReforme;
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
        indicatorSync = findViewById(R.id.indicatorSync);
        // Le clic est posé sur l'ImageButton interne, pas sur le FrameLayout englobant :
        // un ImageButton est cliquable par défaut et absorbe le tap avant qu'il
        // n'atteigne un OnClickListener posé sur son parent (aucun listener dessus ==
        // rien ne se passe visuellement à part le ripple).
        findViewById(R.id.imgBtnDiagnostics).setOnClickListener(v -> startActivity(new Intent(this, DiagnosticsActivity.class)));

        // Avatar + nom/rôles de l'agent : affiche ses infos et propose la déconnexion
        // classique (verrouillage réversible, voir SessionManager.lockSession) — la
        // suppression complète du compte reste dans Paramètres (DiagnosticsActivity),
        // volontairement séparée pour ne pas confondre les deux actions.
        View.OnClickListener showProfile = v -> showProfileDialog();
        findViewById(R.id.flAvatar).setOnClickListener(showProfile);
        findViewById(R.id.llAgentInfo).setOnClickListener(showProfile);

        spinnerProjets = findViewById(R.id.spinnerProjets);
        tvPoulesCount = findViewById(R.id.tvPoulesCount);
        tvTauxPonte = findViewById(R.id.tvTauxPonte);
        tvJoursRestants = findViewById(R.id.tvJoursRestants);
        tvBatimentsOccupes = findViewById(R.id.tvBatimentsOccupes);

        cardLastEntry = findViewById(R.id.cardLastEntry);
        cardAlertes = findViewById(R.id.cardAlertes);
        tvAlertesDetail = findViewById(R.id.tvAlertesDetail);
        cardAlertes.setOnClickListener(v -> showAlertesDialog());
        tvLastEntryTitle = findViewById(R.id.tvLastEntryTitle);
        tvLastEntryDetail = findViewById(R.id.tvLastEntryDetail);
        tvLastEntryTime = findViewById(R.id.tvLastEntryTime);

        tvSectionProduction = findViewById(R.id.tvSectionProduction);
        gridProduction = findViewById(R.id.gridProduction);
        btnCollecteOeufs = findViewById(R.id.btnCollecteOeufs);
        btnAlimentation = findViewById(R.id.btnAlimentation);
        btnSoins = findViewById(R.id.btnSoins);
        btnMortalite = findViewById(R.id.btnMortalite);
        btnReforme = findViewById(R.id.btnReforme);

        tvSectionFinance = findViewById(R.id.tvSectionFinance);
        gridFinance = findViewById(R.id.gridFinance);
        btnEntreeArgent = findViewById(R.id.btnEntreeArgent);
        btnSortieArgent = findViewById(R.id.btnSortieArgent);
        btnVenteOeufs = findViewById(R.id.btnVenteOeufs);
        btnVenteReforme = findViewById(R.id.btnVenteReforme);
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

    /** Infos de l'agent connecté + déconnexion classique (verrouillage, pas de
     * suppression — voir SessionManager.lockSession). */
    private void showProfileDialog() {
        StringBuilder roles = new StringBuilder();
        if (currentUser.isProduction()) roles.append("Production");
        if (currentUser.isFinance()) {
            if (roles.length() > 0) roles.append(" · ");
            roles.append("Finance");
        }
        if (currentUser.isAdmin()) {
            if (roles.length() > 0) roles.append(" · ");
            roles.append("Administration");
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle(currentUser.getNom())
                .setMessage(roles.length() > 0 ? roles.toString() : "Aucun rôle")
                .setPositiveButton("Déconnecter", (dialog, which) -> logout())
                .setNegativeButton("Fermer", null)
                .show();
    }

    private void logout() {
        sessionManager.lockSession();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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
    /** Réseau d'abord ; en cas d'échec (hors ligne), retombe sur le cache local plutôt
     * que d'afficher un écran vierge — voir LocalDatabase.putCache/getCache. Le cache
     * est rafraîchi à chaque succès réseau et après chaque synchronisation (forceSync). */
    private void loadProjets() {
        ApiClient.dataApi(this).getProjetsSelect().enqueue(new Callback<ApiEnvelope<List<ProjetSelectResponse>>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<List<ProjetSelectResponse>>> call, Response<ApiEnvelope<List<ProjetSelectResponse>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    // On n'amène jamais un projet terminé sur le terrain (voir
                    // CachePrefetcher.filterActifs) : ni dans le sélecteur, ni en cache.
                    projetsList = CachePrefetcher.filterActifs(response.body().getData());
                    localDatabase.putCache(CACHE_PROJETS_SELECT, gson.toJson(projetsList));
                    // Précharge le détail/alertes/stock de TOUS les projets, pas
                    // uniquement celui affiché ici — sinon un projet jamais
                    // sélectionné en ligne apparaît vide dès qu'on le choisit hors
                    // ligne sur le terrain (voir CachePrefetcher).
                    CachePrefetcher.prefetchProjectsDetails(HomeActivity.this, localDatabase, projetsList);
                    setupProjetSelector();
                } else {
                    loadProjetsFromCache();
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<List<ProjetSelectResponse>>> call, Throwable t) {
                Log.e(TAG, "Impossible de charger les projets, repli sur le cache local", t);
                loadProjetsFromCache();
            }
        });
    }

    private void loadProjetsFromCache() {
        String cached = localDatabase.getCache(CACHE_PROJETS_SELECT);
        if (cached != null) {
            Type type = new TypeToken<List<ProjetSelectResponse>>() {}.getType();
            projetsList = gson.fromJson(cached, type);
            long updatedAt = localDatabase.getCacheUpdatedAt(CACHE_PROJETS_SELECT);
            Toast.makeText(this, "Hors ligne — projets du " + relativeTime(updatedAt), Toast.LENGTH_SHORT).show();
        } else {
            projetsList = new ArrayList<>();
            Toast.makeText(this, "Impossible de charger les projets (hors ligne, aucune donnée enregistrée)", Toast.LENGTH_SHORT).show();
        }
        setupProjetSelector();
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
            clearProjetDisplay();
        }
    }

    private void clearProjetDisplay() {
        tvPoulesCount.setText("—");
        tvTauxPonte.setText("—");
        tvJoursRestants.setText("—");
        tvBatimentsOccupes.setText("Bâtiments : —");
        alertesList = new ArrayList<>();
        cardAlertes.setVisibility(View.GONE);
    }

    /** Alertes recalculées côté serveur (stock, mortalité, transactions en attente...) —
     * même logique/endpoint que le tableau de bord web, jamais de seuils recalculés côté
     * mobile. Réseau d'abord, repli sur le cache local hors ligne (voir loadProjets). */
    private void loadAlertes() {
        if (currentProjet == null) return;
        String cacheKey = CACHE_NOTIFICATIONS_PREFIX + currentProjet.getUniqueId();

        ApiClient.dataApi(this).getNotificationsForProjet(currentProjet.getUniqueId())
                .enqueue(new Callback<ApiEnvelope<List<NotificationResponse>>>() {
                    @Override
                    public void onResponse(Call<ApiEnvelope<List<NotificationResponse>>> call, Response<ApiEnvelope<List<NotificationResponse>>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                            alertesList = response.body().getData();
                            localDatabase.putCache(cacheKey, gson.toJson(alertesList));
                            updateAlertesCard();
                        } else {
                            loadAlertesFromCache(cacheKey);
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiEnvelope<List<NotificationResponse>>> call, Throwable t) {
                        loadAlertesFromCache(cacheKey);
                    }
                });
    }

    private void loadAlertesFromCache(String cacheKey) {
        String cached = localDatabase.getCache(cacheKey);
        if (cached != null) {
            Type type = new TypeToken<List<NotificationResponse>>() {}.getType();
            alertesList = gson.fromJson(cached, type);
        } else {
            alertesList = new ArrayList<>();
        }
        updateAlertesCard();
    }

    private void updateAlertesCard() {
        List<NotificationResponse> nonLues = new ArrayList<>();
        for (NotificationResponse n : alertesList) {
            if (!n.isRead()) nonLues.add(n);
        }
        if (nonLues.isEmpty()) {
            cardAlertes.setVisibility(View.GONE);
            return;
        }
        cardAlertes.setVisibility(View.VISIBLE);
        NotificationResponse first = nonLues.get(0);
        String suffix = nonLues.size() > 1 ? " (+" + (nonLues.size() - 1) + " autre" + (nonLues.size() > 2 ? "s" : "") + ")" : "";
        tvAlertesDetail.setText(first.getMessage() + suffix);
    }

    private void showAlertesDialog() {
        if (alertesList.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (NotificationResponse n : alertesList) {
            if (n.isRead()) continue;
            String niveau = "CRITIQUE".equalsIgnoreCase(n.getLevel()) ? "🔴" : "🟠";
            sb.append(niveau).append(" ").append(n.getMessage()).append("\n\n");
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Alertes du projet")
                .setMessage(sb.toString().trim())
                .setPositiveButton("Marquer tout comme lu", (dialog, which) -> markAllAlertesRead())
                .setNegativeButton("Fermer", null)
                .show();
    }

    private void markAllAlertesRead() {
        for (NotificationResponse n : alertesList) {
            if (n.isRead()) continue;
            ApiClient.dataApi(this).markNotificationRead(n.getKey()).enqueue(new Callback<ApiEnvelope<String>>() {
                @Override
                public void onResponse(Call<ApiEnvelope<String>> call, Response<ApiEnvelope<String>> response) { }
                @Override
                public void onFailure(Call<ApiEnvelope<String>> call, Throwable t) { }
            });
        }
        cardAlertes.setVisibility(View.GONE);
        alertesList = new ArrayList<>();
    }

    /** /projets/select ne renvoie que code/titre : le détail (effectif, taux de ponte,
     * fin prévue, bâtiments occupés) vient de /projets/findbyUniqueId/{uniqueId}.
     * Réseau d'abord, repli sur le cache local hors ligne (voir loadProjets). */
    /** Un projet REFORME (chair) ne produit pas d'œufs : la carte "Collecte d'œufs"
     * ne doit pas être proposée pour lui, contrairement à PONTE et MIXTE.
     * Symétriquement, un projet PONTE pur n'a pas de sujets à réformer : "Réforme"
     * ne lui est proposée que s'il est REFORME ou MIXTE. Ces deux cartes sont
     * Production, rattachées au projet sélectionné. "Vente d'œufs"/"Vente réforme"
     * (Finance) restent en revanche TOUJOURS visibles : elles puisent dans un stock
     * à l'échelle de la ferme entière, indépendant du projet actuellement
     * sélectionné dans le spinner. Rappelée à chaque changement de projet. */
    private void updateSaisieButtonsVisibility() {
        boolean masquerCollecteOeufs = currentProjet != null && currentProjet.isReformeSeule();
        btnCollecteOeufs.setVisibility(masquerCollecteOeufs ? View.GONE : View.VISIBLE);

        boolean masquerReforme = currentProjet != null && currentProjet.isPonteSeule();
        btnReforme.setVisibility(masquerReforme ? View.GONE : View.VISIBLE);
    }

    private void updateProjetDisplay() {
        updateSaisieButtonsVisibility();
        if (currentProjet == null) {
            clearProjetDisplay();
            return;
        }
        clearProjetDisplay();
        loadAlertes();

        String cacheKey = CACHE_PROJET_DETAIL_PREFIX + currentProjet.getUniqueId();

        ApiClient.dataApi(this).getProjetDetail(currentProjet.getUniqueId())
                .enqueue(new Callback<ApiEnvelope<ProjetDetailResponse>>() {
                    @Override
                    public void onResponse(Call<ApiEnvelope<ProjetDetailResponse>> call, Response<ApiEnvelope<ProjetDetailResponse>> response) {
                        ProjetDetailResponse detail = response.isSuccessful() && response.body() != null
                                ? response.body().getData() : null;
                        if (detail == null) {
                            loadProjetDetailFromCache(cacheKey);
                            return;
                        }
                        localDatabase.putCache(cacheKey, gson.toJson(detail));
                        renderProjetDetail(detail);
                    }

                    @Override
                    public void onFailure(Call<ApiEnvelope<ProjetDetailResponse>> call, Throwable t) {
                        Log.e(TAG, "Impossible de charger le détail du projet, repli sur le cache local", t);
                        loadProjetDetailFromCache(cacheKey);
                    }
                });
    }

    private void loadProjetDetailFromCache(String cacheKey) {
        String cached = localDatabase.getCache(cacheKey);
        if (cached == null) return; // les tirets posés par clearProjetDisplay() restent affichés
        renderProjetDetail(gson.fromJson(cached, ProjetDetailResponse.class));
    }

    private void renderProjetDetail(ProjetDetailResponse detail) {
        tvPoulesCount.setText(detail.getNbSujets() != null ? String.valueOf(detail.getNbSujets()) : "—");
        tvTauxPonte.setText(detail.getTauxPonte() != null
                ? String.format(Locale.FRANCE, "%.0f%%", detail.getTauxPonte()) : "—");
        tvJoursRestants.setText(joursRestants(detail.getFinPrevue()));

        List<OccupationBatimentResponse> occupations = detail.getOccupationBatiment();
        if (occupations == null || occupations.isEmpty()) {
            tvBatimentsOccupes.setText("Bâtiments : aucun bâtiment assigné");
        } else {
            String noms = occupations.stream()
                    .filter(o -> o.getDateSortie() == null) // occupations encore actives
                    .map(o -> o.getNomBatiment() + (o.getNbSujetsDansBatiment() != null
                            ? " (" + o.getNbSujetsDansBatiment() + ")" : ""))
                    .collect(Collectors.joining(", "));
            tvBatimentsOccupes.setText("Bâtiments : " + (noms.isEmpty() ? "aucun actif" : noms));
        }
    }

    private String joursRestants(String finPrevueIso) {
        // SimpleDateFormat/Calendar plutôt que java.time : minSdk 24 sans core library
        // desugaring, java.time planterait (NoClassDefFoundError) sous Android 7/7.1.
        if (finPrevueIso == null || finPrevueIso.isEmpty()) return "—";
        try {
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE);
            java.util.Date finPrevue = isoFormat.parse(finPrevueIso);
            if (finPrevue == null) return "—";

            java.util.Calendar today = java.util.Calendar.getInstance();
            today.set(java.util.Calendar.HOUR_OF_DAY, 0);
            today.set(java.util.Calendar.MINUTE, 0);
            today.set(java.util.Calendar.SECOND, 0);
            today.set(java.util.Calendar.MILLISECOND, 0);

            long diffMs = finPrevue.getTime() - today.getTimeInMillis();
            long jours = diffMs / (24L * 60 * 60 * 1000);
            return jours >= 0 ? jours + "j" : "Terminé";
        } catch (Exception e) {
            return "—";
        }
    }

    private void setupClickListeners() {
        // Production
        btnCollecteOeufs.setOnClickListener(v -> openSaisie(SaisieType.COLLECTE_OEUFS));
        btnAlimentation.setOnClickListener(v -> showChoixAlimentation());
        btnSoins.setOnClickListener(v -> openSaisie(SaisieType.SOINS));
        btnMortalite.setOnClickListener(v -> openSaisie(SaisieType.MORTALITE));
        btnReforme.setOnClickListener(v -> openSaisie(SaisieType.REFORME));

        // Finance
        btnEntreeArgent.setOnClickListener(v -> openSaisie(SaisieType.TRANSACTION_ENTREE));
        btnSortieArgent.setOnClickListener(v -> openSaisie(SaisieType.TRANSACTION_SORTIE));
        btnVenteOeufs.setOnClickListener(v -> openSaisie(SaisieType.VENTE_OEUFS));
        btnVenteReforme.setOnClickListener(v -> openSaisie(SaisieType.VENTE_REFORME));

        // Sync — écouteur sur l'ImageButton interne, même raison que btnDiagnostics ci-dessus.
        findViewById(R.id.imgBtnSync).setOnClickListener(v -> forceSync());
        btnSyncNow.setOnClickListener(v -> forceSync());

        // Mes saisies
        cardLastEntry.setOnClickListener(v -> startActivity(new Intent(this, MesSaisiesActivity.class)));
        tvPendingCount.setOnClickListener(v -> startActivity(new Intent(this, MesSaisiesActivity.class)));
    }

    private void openSaisie(SaisieType type) {
        // Vente œufs/réforme (Finance) puisent dans un stock à l'échelle de la ferme
        // entière, pas du projet sélectionné — même exception que les transactions.
        boolean needsProjet = type != SaisieType.TRANSACTION_ENTREE && type != SaisieType.TRANSACTION_SORTIE
                && type != SaisieType.VENTE_OEUFS && type != SaisieType.VENTE_REFORME;
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

    /** La carte "Alimentation" couvre les deux flux (achat = entrant, consommation =
     * sortant) : on demande lequel plutôt que d'avoir une carte séparée sur l'accueil. */
    private void showChoixAlimentation() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Alimentation")
                .setItems(new CharSequence[]{"Achat (entrant)", "Consommation (sortant)"}, (dialog, which) -> {
                    openSaisie(which == 0 ? SaisieType.ALIMENTATION_ACHAT : SaisieType.ALIMENTATION_CONSOMMATION);
                })
                .show();
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

        btnSyncNow.setEnabled(false);

        if (pending == 0) {
            // Rien à pousser, mais "Synchroniser" doit quand même vérifier ce qui a pu
            // changer côté serveur (nouveau projet assigné, projet passé en terminé,
            // stock/alertes à jour...) plutôt que de ne rien faire.
            Toast.makeText(this, "Actualisation des données...", Toast.LENGTH_SHORT).show();
            refreshProjetsEtCache(() -> btnSyncNow.setEnabled(true));
            return;
        }

        Toast.makeText(this, "Synchronisation de " + pending + " saisie(s)...", Toast.LENGTH_SHORT).show();

        new SyncManager(this).syncAll(new SyncManager.SyncCallback() {
            @Override
            public void onComplete(int success, int failed) {
                runOnUiThread(() -> {
                    String message = failed == 0
                            ? success + " saisie(s) synchronisée(s) avec succès"
                            : success + " synchronisée(s), " + failed + " en échec (réessayez plus tard)";
                    Toast.makeText(HomeActivity.this, message, Toast.LENGTH_LONG).show();
                    setupSyncStatus();
                    loadLastEntry();
                    updateFinanceStats();
                    // Les saisies qu'on vient de pousser ont pu changer le stock, la
                    // mortalité cumulée, etc. côté serveur, et la liste de projets a pu
                    // changer (nouveau projet assigné, projet passé en terminé) : on
                    // récupère les métadonnées à jour et on remplace le cache local,
                    // en conservant la sélection en cours dans le spinner si possible.
                    refreshProjetsEtCache(() -> btnSyncNow.setEnabled(true));
                });
            }
        });
    }

    /** Récupère la liste de projets actifs à jour, remplace le cache local (projets +
     * détail/alertes/stock de chacun), et réapplique la sélection en cours si le projet
     * existe toujours dans la liste actualisée — sinon retombe sur le premier projet. */
    private void refreshProjetsEtCache(Runnable onDone) {
        String currentProjetId = currentProjet != null ? currentProjet.getUniqueId() : null;

        ApiClient.dataApi(this).getProjetsSelect().enqueue(new Callback<ApiEnvelope<List<ProjetSelectResponse>>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<List<ProjetSelectResponse>>> call, Response<ApiEnvelope<List<ProjetSelectResponse>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    projetsList = CachePrefetcher.filterActifs(response.body().getData());
                    localDatabase.putCache(CACHE_PROJETS_SELECT, gson.toJson(projetsList));
                    CachePrefetcher.prefetchProjectsDetails(HomeActivity.this, localDatabase, projetsList);
                    setupProjetSelectorPreservingSelection(currentProjetId);
                }
                onDone.run();
            }

            @Override
            public void onFailure(Call<ApiEnvelope<List<ProjetSelectResponse>>> call, Throwable t) {
                onDone.run(); // pas de réseau : on garde ce qui est déjà affiché/en cache
            }
        });
    }

    private void setupProjetSelectorPreservingSelection(String previousProjetId) {
        setupProjetSelector();
        if (previousProjetId == null) return;
        for (int i = 0; i < projetsList.size(); i++) {
            if (previousProjetId.equals(projetsList.get(i).getUniqueId())) {
                spinnerProjets.setSelection(i);
                break;
            }
        }
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
