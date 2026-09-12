package com.mobile.diafarms.activity;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
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
import com.mobile.diafarms.network.dto.FarmAppSettingsResponse;
import com.mobile.diafarms.network.dto.NotificationResponse;
import com.mobile.diafarms.network.dto.OccupationBatimentResponse;
import com.mobile.diafarms.network.dto.ProjetDetailResponse;
import com.mobile.diafarms.network.dto.ProjetSelectResponse;
import com.mobile.diafarms.network.dto.TransactionCreateRequest;
import com.mobile.diafarms.ui.saisie.SaisieFormActivity;
import com.mobile.diafarms.util.NetworkUtils;
import com.mobile.diafarms.util.OccupationUtils;

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
    // Évite de rafraîchir les projets deux fois au lancement : onCreate (loadProjets)
    // s'en charge déjà, onResume ne doit le refaire qu'aux reprises suivantes.
    private boolean premierResumeFait = false;

    // Connectivité réelle (voir setupConnectivityMonitor) : l'indicateur En ligne/Hors
    // ligne de la barre du bas était figé en dur dans le XML jusqu'ici.
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean wasOnline = true;

    // Vues Header
    private TextView tvAgentName;
    private TextView badgeRoles;
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
    private CardView btnVaccination;
    private CardView btnMortalite;
    private CardView btnReforme;

    // Vues Comptable et Vente — deux rôles distincts, deux sections indépendantes
    // (voir setupVisibilityByRole/loadFarmAppSettings) : avant, un seul titre "Saisie
    // comptable" mélangeait les cartes des deux rôles, source de confusion pour un
    // compte qui n'a que l'un des deux (ou les deux à la fois).
    private TextView tvSectionComptable;
    private GridLayout gridComptable;
    private TextView tvSectionVente;
    private GridLayout gridVente;
    private CardView btnEntreeArgent;
    private CardView btnSortieArgent;
    private CardView btnVenteOeufs;
    private CardView btnVenteReforme;
    private CardView btnVenteFientes;
    private CardView btnNouveauClient;
    private CardView btnNouvelleCommande;
    private CardView btnPayerSalaire;
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
        setupConnectivityMonitor();
        loadProjets();
        loadLastEntry();
        updateFinanceStats();

        // Vérification périodique des alertes en arrière-plan (notifications locales,
        // voir AlertCheckWorker) — pas de vrai push, un contrôle toutes les 15-30 min.
        // KEEP : sans effet si déjà enregistré, pas de doublon à chaque ouverture.
        com.mobile.diafarms.data.AlertCheckWorker.enregistrer(this);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                && androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 42);
        }
    }

    private void bindViews() {
        tvAgentName = findViewById(R.id.tvAgentName);
        badgeRoles = findViewById(R.id.badgeRoles);
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
        btnVaccination = findViewById(R.id.btnVaccination);
        btnMortalite = findViewById(R.id.btnMortalite);
        btnReforme = findViewById(R.id.btnReforme);

        tvSectionComptable = findViewById(R.id.tvSectionComptable);
        gridComptable = findViewById(R.id.gridComptable);
        tvSectionVente = findViewById(R.id.tvSectionVente);
        gridVente = findViewById(R.id.gridVente);
        btnEntreeArgent = findViewById(R.id.btnEntreeArgent);
        btnSortieArgent = findViewById(R.id.btnSortieArgent);
        btnVenteOeufs = findViewById(R.id.btnVenteOeufs);
        btnVenteReforme = findViewById(R.id.btnVenteReforme);
        btnVenteFientes = findViewById(R.id.btnVenteFientes);
        btnNouveauClient = findViewById(R.id.btnNouveauClient);
        btnNouvelleCommande = findViewById(R.id.btnNouvelleCommande);
        btnPayerSalaire = findViewById(R.id.btnPayerSalaire);
        cardStatsFinance = findViewById(R.id.cardStatsFinance);
        tvMesEntrees = findViewById(R.id.tvMesEntrees);
        tvMesSorties = findViewById(R.id.tvMesSorties);

        indicatorConnection = findViewById(R.id.indicatorConnection);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvPendingCount = findViewById(R.id.tvPendingCount);
        btnSyncNow = findViewById(R.id.btnSyncNow);
    }

    /** Un seul badge compact, jamais 3 en ligne : au-delà de 2 rôles cumulés, la liste
     * complète tenait mal dans l'espace dispo entre l'avatar et les boutons de droite
     * (voir activity_home.xml, llRoles). "PRODUCTION · VENTE" jusqu'à 2 rôles,
     * "PRODUCTION +2" au-delà — le détail complet reste consultable via showProfileDialog
     * (tap sur l'avatar/le nom). */
    private void setupHeader() {
        tvAgentName.setText(currentUser.getNom());

        List<String> actifs = new ArrayList<>();
        if (currentUser.isProduction()) actifs.add("PRODUCTION");
        if (currentUser.isComptable()) actifs.add("COMPTABLE");
        if (currentUser.isVente()) actifs.add("VENTE");
        if (currentUser.isAdmin()) actifs.add("ADMIN");

        if (actifs.isEmpty()) {
            badgeRoles.setVisibility(View.GONE);
        } else {
            badgeRoles.setVisibility(View.VISIBLE);
            badgeRoles.setText(actifs.size() <= 2
                    ? String.join(" · ", actifs)
                    : actifs.get(0) + " +" + (actifs.size() - 1));
        }
    }

    /** Infos de l'agent connecté + déconnexion classique (verrouillage, pas de
     * suppression — voir SessionManager.lockSession). RESPONSABLE n'apparaît jamais
     * ici : aucune présence mobile pour ce rôle (voir User.isComptable/isVente,
     * qui remplacent l'ancien isFinance unique). */
    private void showProfileDialog() {
        StringBuilder roles = new StringBuilder();
        if (currentUser.isProduction()) roles.append("Production");
        if (currentUser.isComptable()) {
            if (roles.length() > 0) roles.append(" · ");
            roles.append("Comptable");
        }
        if (currentUser.isVente()) {
            if (roles.length() > 0) roles.append(" · ");
            roles.append("Vente");
        }
        if (currentUser.isAdmin()) {
            if (roles.length() > 0) roles.append(" · ");
            roles.append("Administration");
        }

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle(currentUser.getNom())
                .setMessage(roles.length() > 0 ? roles.toString() : "Aucun rôle")
                .setPositiveButton("Déconnecter", (d, which) -> logout())
                .setNegativeButton("Fermer", null)
                .create();
        dialog.show();
        // Bouton par défaut du thème (vert primaire) pour les deux actions — "Déconnecter"
        // se confondait visuellement avec "Fermer" alors que c'est la seule des deux qui
        // change réellement l'état de la session.
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(getColor(R.color.red_error));
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
        boolean isComptable = currentUser.isComptable();
        boolean isVente = currentUser.isVente();
        boolean isFinance = isComptable || isVente; // section "Finance" = l'un ou l'autre

        // Fermé par défaut (fail-closed) tant que la réponse de /farm-settings n'est pas
        // arrivée — voir loadFarmAppSettings ci-dessous. Avant, ce bloc restait VISIBLE
        // pour quiconque avait le rôle PRODUCTION, sans jamais consulter
        // productionMobileEnabled : un compte multi-rôles (ex: PRODUCTION + COMPTABLE)
        // pouvait se connecter grâce à un autre rôle actif et voyait quand même les
        // cartes Production même si l'admin ne les avait pas activées pour ce rôle-là.
        tvSectionProduction.setVisibility(View.GONE);
        gridProduction.setVisibility(View.GONE);

        // Comptable et Vente sont deux rôles distincts, chacun avec son propre titre et
        // sa propre grille — un compte qui n'a que l'un des deux ne doit voir ni le
        // titre ni les cartes de l'autre (un compte qui cumule les deux voit les deux
        // sections l'une sous l'autre, jamais mélangées dans la même grille).
        tvSectionComptable.setVisibility(isComptable ? View.VISIBLE : View.GONE);
        gridComptable.setVisibility(isComptable ? View.VISIBLE : View.GONE);
        tvSectionVente.setVisibility(isVente ? View.VISIBLE : View.GONE);
        gridVente.setVisibility(isVente ? View.VISIBLE : View.GONE);
        // Entrées/sorties du jour — concept propre au COMPTABLE, pas au VENTE (voir
        // updateFinanceStats).
        cardStatsFinance.setVisibility(isComptable ? View.VISIBLE : View.GONE);

        // Fermé par défaut (fail-closed, cohérent avec AppAccessRules côté back) tant
        // que la réponse de /farm-settings n'est pas arrivée — voir loadFarmAppSettings,
        // appelé juste après. Chaque rôle a son propre jeu de boutons, gardé
        // indépendamment de l'autre : un COMPTABLE ne voit jamais les boutons vente (et
        // inversement) quel que soit le réglage admin.
        if (isComptable) {
            btnEntreeArgent.setVisibility(View.GONE);
            btnSortieArgent.setVisibility(View.GONE);
            btnPayerSalaire.setVisibility(View.GONE);
        }
        if (isVente) {
            btnVenteOeufs.setVisibility(View.GONE);
            btnVenteReforme.setVisibility(View.GONE);
            btnVenteFientes.setVisibility(View.GONE);
            btnNouveauClient.setVisibility(View.GONE);
            btnNouvelleCommande.setVisibility(View.GONE);
        }
        if (isProduction || isFinance) {
            loadFarmAppSettings();
        }
    }

    /** PRODUCTION, COMPTABLE et VENTE ont chacun un jeu d'actions mobile fixe,
     * activé/désactivé en bloc par l'admin (voir Paramètres côté web, AppAccessRules
     * côté back) — indépendamment du fait que le compte ait pu se connecter grâce à un
     * AUTRE rôle actif (voir AppAccessRules.canAccessMobile : un cumul de rôles donne
     * accès à l'app, pas automatiquement à CHAQUE section). Cache local d'abord (voir
     * CachePrefetcher.CACHE_FARM_SETTINGS) : l'app doit rester intégralement utilisable
     * hors ligne, y compris le tout premier écran de menu — masquer indéfiniment tant
     * que le réseau ne répond pas (comme avant ce correctif) cassait le mode hors ligne
     * pour TOUT le monde, pas seulement pour les rôles gérés ici. Le réseau, s'il
     * répond, rafraîchit l'affichage et le cache ; à défaut, le dernier réglage connu
     * reste appliqué plutôt que de tout masquer par défaut. */
    private void loadFarmAppSettings() {
        String cached = localDatabase.getCache(CachePrefetcher.CACHE_FARM_SETTINGS);
        if (cached != null) {
            applyFarmAppSettings(gson.fromJson(cached, FarmAppSettingsResponse.class));
        }

        ApiClient.dataApi(this).getFarmAppSettings().enqueue(new Callback<ApiEnvelope<FarmAppSettingsResponse>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<FarmAppSettingsResponse>> call, Response<ApiEnvelope<FarmAppSettingsResponse>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().getData() == null) return;
                FarmAppSettingsResponse s = response.body().getData();
                localDatabase.putCache(CachePrefetcher.CACHE_FARM_SETTINGS, gson.toJson(s));
                applyFarmAppSettings(s);
            }

            @Override
            public void onFailure(Call<ApiEnvelope<FarmAppSettingsResponse>> call, Throwable t) {
                Log.e(TAG, "Impossible de charger les accès Finance (dernier réglage connu déjà appliqué depuis le cache le cas échéant)", t);
            }
        });
    }

    private void applyFarmAppSettings(FarmAppSettingsResponse s) {
        if (currentUser.isProduction()) {
            tvSectionProduction.setVisibility(s.isProductionMobileEnabled() ? View.VISIBLE : View.GONE);
            gridProduction.setVisibility(s.isProductionMobileEnabled() ? View.VISIBLE : View.GONE);
        }
        if (currentUser.isComptable()) {
            btnEntreeArgent.setVisibility(s.isComptableMobileEnabled() ? View.VISIBLE : View.GONE);
            btnSortieArgent.setVisibility(s.isComptableMobileEnabled() ? View.VISIBLE : View.GONE);
            // Le Comptable ne peut que PAYER un salaire déjà défini sur mobile — gérer
            // la grille (ajouter un salarié, fixer son taux) reste une action web (voir
            // SaisieType.SALAIRE_PAYER, DefinirSalaireDialog côté web).
            btnPayerSalaire.setVisibility(s.isComptableMobileEnabled() ? View.VISIBLE : View.GONE);
        }
        if (currentUser.isVente()) {
            btnVenteOeufs.setVisibility(s.isVenteMobileEnabled() ? View.VISIBLE : View.GONE);
            btnVenteReforme.setVisibility(s.isVenteMobileEnabled() ? View.VISIBLE : View.GONE);
            btnVenteFientes.setVisibility(s.isVenteMobileEnabled() ? View.VISIBLE : View.GONE);
            btnNouveauClient.setVisibility(s.isVenteMobileEnabled() ? View.VISIBLE : View.GONE);
            btnNouvelleCommande.setVisibility(s.isVenteMobileEnabled() ? View.VISIBLE : View.GONE);
        }
    }

    /** Charge les projets réels de la ferme (GET /projets/select) pour peupler le sélecteur. */
    /** Local d'abord : affiche tout de suite la dernière liste connue en cache (voir
     * LocalDatabase.putCache/getCache) au lieu d'attendre la réponse réseau — sur le
     * terrain hors ligne, ApiClient met jusqu'à 15s (connectTimeout/readTimeout) à
     * échouer, ce qui figeait l'écran tout ce temps. Le réseau tourne ensuite en tâche
     * de fond et rafraîchit l'affichage/le cache s'il aboutit ; le cache est aussi
     * rafraîchi après chaque synchronisation (forceSync). */
    private void loadProjets() {
        boolean hadCache = renderProjetsFromCache();

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
                } else if (!hadCache) {
                    showAucunProjetDisponible();
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<List<ProjetSelectResponse>>> call, Throwable t) {
                Log.e(TAG, "Impossible de charger les projets depuis le réseau" + (hadCache ? " (données locales déjà affichées)" : ""), t);
                if (!hadCache) {
                    showAucunProjetDisponible();
                }
            }
        });
    }

    /** Affiche tout de suite la dernière liste de projets connue en local, sans
     * attendre le réseau — voir loadProjets(). Retourne false si rien n'est en cache. */
    private boolean renderProjetsFromCache() {
        String cached = localDatabase.getCache(CACHE_PROJETS_SELECT);
        if (cached == null) return false;
        Type type = new TypeToken<List<ProjetSelectResponse>>() {}.getType();
        projetsList = gson.fromJson(cached, type);
        setupProjetSelector();
        return true;
    }

    private void showAucunProjetDisponible() {
        projetsList = new ArrayList<>();
        Toast.makeText(this, "Impossible de charger les projets (hors ligne, aucune donnée enregistrée)", Toast.LENGTH_SHORT).show();
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
        tvPoulesCount.setText("");
        tvTauxPonte.setText("");
        tvJoursRestants.setText("");
        tvBatimentsOccupes.setText("Poulaillers : aucun poulailler assigné");
        alertesList = new ArrayList<>();
        cardAlertes.setVisibility(View.GONE);
    }

    /** Alertes recalculées côté serveur (stock, mortalité, transactions en attente...) —
     * même logique/endpoint que le tableau de bord web, jamais de seuils recalculés côté
     * mobile. Réseau d'abord, repli sur le cache local hors ligne (voir loadProjets). */
    private void loadAlertes() {
        if (currentProjet == null) return;
        String cacheKey = CACHE_NOTIFICATIONS_PREFIX + currentProjet.getUniqueId();

        // Affiche tout de suite les dernières alertes connues en local (ou aucune),
        // sans attendre le réseau — voir loadProjets().
        String cached = localDatabase.getCache(cacheKey);
        if (cached != null) {
            Type type = new TypeToken<List<NotificationResponse>>() {}.getType();
            alertesList = gson.fromJson(cached, type);
        } else {
            alertesList = new ArrayList<>();
        }
        updateAlertesCard();

        ApiClient.dataApi(this).getNotificationsForProjet(currentProjet.getUniqueId())
                .enqueue(new Callback<ApiEnvelope<List<NotificationResponse>>>() {
                    @Override
                    public void onResponse(Call<ApiEnvelope<List<NotificationResponse>>> call, Response<ApiEnvelope<List<NotificationResponse>>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                            alertesList = response.body().getData();
                            localDatabase.putCache(cacheKey, gson.toJson(alertesList));
                            updateAlertesCard();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiEnvelope<List<NotificationResponse>>> call, Throwable t) {
                        // données locales déjà affichées ci-dessus, rien à faire de plus
                    }
                });
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
     * ne doit pas être proposée pour lui, contrairement à PONTE et MIXTE. "Réforme",
     * en revanche, reste proposée pour TOUS les types de projet, PONTE compris — une
     * pondeuse est elle aussi réformée en fin de cycle de ponte, ce n'est pas réservé
     * aux projets chair/mixte (correction du 2026-08-16 : l'ancienne logique masquait
     * à tort "Réforme" pour un projet PONTE pur). Ces cartes sont Production,
     * rattachées au projet sélectionné. "Vente d'œufs"/"Vente réforme" (Finance)
     * restent TOUJOURS visibles indépendamment du projet sélectionné ici : elles
     * puisent dans un stock à l'échelle de la ferme entière. Rappelée à chaque
     * changement de projet.
     *
     * GridLayout ne referme PAS automatiquement l'espace d'une carte passée en GONE
     * (limitation connue : le placement automatique réserve quand même sa cellule) —
     * simplement masquer btnCollecteOeufs laissait donc un trou visible sur les
     * projets chair. On retire/reconstruit la grille à la place, en ne (ré)ajoutant
     * que les cartes réellement visibles, pour qu'elles se resserrent naturellement.
     *
     * PONTE/MIXTE : 6 cartes (Collecte, Alimentation, Soins, Vaccination, Mortalité,
     * Réforme) — pair depuis l'ajout de Vaccination, toutes appariées 2 à 2, plus
     * besoin d'isoler Collecte en pleine largeur comme avant (l'ancien compte de 5
     * était impair).
     * REFORME seul : Collecte masquée, 5 cartes (Alimentation, Soins, Vaccination,
     * Mortalité, Réforme) — impair, la dernière (Réforme) reste seule sur sa ligne,
     * sans conséquence visuelle grave (cas plus rare, projets chair uniquement). */
    private void updateSaisieButtonsVisibility() {
        boolean masquerCollecteOeufs = currentProjet != null && currentProjet.isReformeSeule();

        gridProduction.removeAllViews();

        if (masquerCollecteOeufs) {
            addProductionCard(btnAlimentation, false);
            addProductionCard(btnSoins, false);
            addProductionCard(btnVaccination, false);
            addProductionCard(btnMortalite, false);
            addProductionCard(btnReforme, false);
        } else {
            addProductionCard(btnCollecteOeufs, false);
            addProductionCard(btnAlimentation, false);
            addProductionCard(btnSoins, false);
            addProductionCard(btnVaccination, false);
            addProductionCard(btnMortalite, false);
            addProductionCard(btnReforme, false);
        }

        btnCollecteOeufs.setVisibility(masquerCollecteOeufs ? View.GONE : View.VISIBLE);
        btnReforme.setVisibility(View.VISIBLE);
    }

    private void addProductionCard(CardView card, boolean pleineLargeur) {
        GridLayout.LayoutParams lp = (GridLayout.LayoutParams) card.getLayoutParams();
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, pleineLargeur ? 2 : 1, 1f);
        lp.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        card.setVisibility(View.VISIBLE);
        gridProduction.addView(card, lp);
    }

    private void updateProjetDisplay() {
        updateSaisieButtonsVisibility();
        if (currentProjet == null) {
            clearProjetDisplay();
            return;
        }

        String cacheKey = CACHE_PROJET_DETAIL_PREFIX + currentProjet.getUniqueId();
        // Affiche tout de suite le dernier détail connu en local plutôt que de vider
        // l'écran (tirets) pendant jusqu'à 15s en attendant le réseau — voir loadProjets().
        boolean hadCache = renderProjetDetailFromCache(cacheKey);
        if (!hadCache) clearProjetDisplay();
        loadAlertes();

        ApiClient.dataApi(this).getProjetDetail(currentProjet.getUniqueId())
                .enqueue(new Callback<ApiEnvelope<ProjetDetailResponse>>() {
                    @Override
                    public void onResponse(Call<ApiEnvelope<ProjetDetailResponse>> call, Response<ApiEnvelope<ProjetDetailResponse>> response) {
                        ProjetDetailResponse detail = response.isSuccessful() && response.body() != null
                                ? response.body().getData() : null;
                        if (detail == null) return; // données locales déjà affichées ci-dessus le cas échéant
                        localDatabase.putCache(cacheKey, gson.toJson(detail));
                        renderProjetDetail(detail);
                    }

                    @Override
                    public void onFailure(Call<ApiEnvelope<ProjetDetailResponse>> call, Throwable t) {
                        Log.e(TAG, "Impossible de charger le détail du projet depuis le réseau" + (hadCache ? " (données locales déjà affichées)" : ""), t);
                    }
                });
    }

    /** Affiche tout de suite le dernier détail de projet connu en local, sans
     * attendre le réseau — voir updateProjetDisplay(). Retourne false si rien n'est en cache. */
    private boolean renderProjetDetailFromCache(String cacheKey) {
        String cached = localDatabase.getCache(cacheKey);
        if (cached == null) return false;
        renderProjetDetail(gson.fromJson(cached, ProjetDetailResponse.class));
        return true;
    }

    private void renderProjetDetail(ProjetDetailResponse detail) {
        tvPoulesCount.setText(detail.getNbSujets() != null ? String.valueOf(detail.getNbSujets()) : "");
        tvTauxPonte.setText(detail.getTauxPonte() != null
                ? String.format(Locale.FRANCE, "%.0f%%", detail.getTauxPonte()) : "");
        tvJoursRestants.setText(joursRestants(detail.getFinPrevue()));

        List<OccupationBatimentResponse> occupations = detail.getOccupationBatiment();
        if (occupations == null || occupations.isEmpty()) {
            tvBatimentsOccupes.setText("Poulaillers : aucun poulailler assigné");
        } else {
            String noms = occupations.stream()
                    .filter(o -> OccupationUtils.estActive(o.getDateSortie())) // occupations encore actives
                    .map(o -> o.getNomBatiment() + (o.getNbSujetsDansBatiment() != null
                            ? " (" + o.getNbSujetsDansBatiment() + ")" : ""))
                    .collect(Collectors.joining(", "));
            tvBatimentsOccupes.setText("Poulaillers : " + (noms.isEmpty() ? "aucun actif" : noms));
        }
    }

    private String joursRestants(String finPrevueIso) {
        // SimpleDateFormat/Calendar plutôt que java.time : minSdk 24 sans core library
        // desugaring, java.time planterait (NoClassDefFoundError) sous Android 7/7.1.
        if (finPrevueIso == null || finPrevueIso.isEmpty()) return "";
        try {
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE);
            java.util.Date finPrevue = isoFormat.parse(finPrevueIso);
            if (finPrevue == null) return "";

            java.util.Calendar today = java.util.Calendar.getInstance();
            today.set(java.util.Calendar.HOUR_OF_DAY, 0);
            today.set(java.util.Calendar.MINUTE, 0);
            today.set(java.util.Calendar.SECOND, 0);
            today.set(java.util.Calendar.MILLISECOND, 0);

            long diffMs = finPrevue.getTime() - today.getTimeInMillis();
            long jours = diffMs / (24L * 60 * 60 * 1000);
            return jours >= 0 ? jours + "j" : "Terminé";
        } catch (Exception e) {
            return "";
        }
    }

    private void setupClickListeners() {
        // Production
        btnCollecteOeufs.setOnClickListener(v -> openSaisie(SaisieType.COLLECTE_OEUFS));
        btnAlimentation.setOnClickListener(v -> showChoixAlimentation());
        btnSoins.setOnClickListener(v -> openSaisie(SaisieType.SOINS));
        btnVaccination.setOnClickListener(v -> openSaisie(SaisieType.VACCINATION));
        btnMortalite.setOnClickListener(v -> openSaisie(SaisieType.MORTALITE));
        btnReforme.setOnClickListener(v -> openSaisie(SaisieType.REFORME));

        // Finance
        btnEntreeArgent.setOnClickListener(v -> openSaisie(SaisieType.TRANSACTION_ENTREE));
        btnSortieArgent.setOnClickListener(v -> openSaisie(SaisieType.TRANSACTION_SORTIE));
        btnVenteOeufs.setOnClickListener(v -> openSaisie(SaisieType.VENTE_OEUFS));
        btnVenteReforme.setOnClickListener(v -> openSaisie(SaisieType.VENTE_REFORME));
        btnVenteFientes.setOnClickListener(v -> openSaisie(SaisieType.VENTE_FIENTES));
        btnNouveauClient.setOnClickListener(v -> openSaisie(SaisieType.CLIENT_CREATE));
        btnNouvelleCommande.setOnClickListener(v -> openSaisie(SaisieType.COMMANDE_CREATE));
        btnPayerSalaire.setOnClickListener(v -> openSaisie(SaisieType.SALAIRE_PAYER));

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
        // Client/Commande ne sont pas non plus rattachés à un projet (voir Client.java/
        // Commande.java côté back : farm-scopés, pas projet-scopés).
        boolean needsProjet = type != SaisieType.TRANSACTION_ENTREE && type != SaisieType.TRANSACTION_SORTIE
                && type != SaisieType.VENTE_OEUFS && type != SaisieType.VENTE_REFORME && type != SaisieType.VENTE_FIENTES
                && type != SaisieType.CLIENT_CREATE && type != SaisieType.COMMANDE_CREATE && type != SaisieType.SALAIRE_PAYER;
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

    /** Abonne un NetworkCallback pour refléter la connectivité réelle sur l'indicateur
     * de la barre du bas (figé en dur dans le XML jusqu'ici) et pour récupérer tout de
     * suite les changements serveur au retour de connexion sur le terrain, sans
     * attendre la prochaine reprise d'activité ni un tap manuel sur "Sync". */
    private void setupConnectivityMonitor() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;

        wasOnline = NetworkUtils.isOnline(this);
        updateConnectionIndicator(wasOnline);

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                runOnUiThread(() -> {
                    updateConnectionIndicator(true);
                    if (!wasOnline) {
                        refreshProjetsEtCache(() -> { });
                    }
                    wasOnline = true;
                });
            }

            @Override
            public void onLost(Network network) {
                // Peut se déclencher pour un seul réseau (ex. Wi-Fi perdu) alors que la
                // 4G a déjà pris le relais — on revérifie l'état global avant d'afficher
                // "Hors ligne" pour éviter un faux négatif pendant la bascule.
                runOnUiThread(() -> {
                    if (!NetworkUtils.isOnline(HomeActivity.this)) {
                        updateConnectionIndicator(false);
                        wasOnline = false;
                    }
                });
            }
        };
        connectivityManager.registerDefaultNetworkCallback(networkCallback);
    }

    private void updateConnectionIndicator(boolean online) {
        indicatorConnection.setBackgroundResource(online ? R.drawable.circle_green : R.drawable.circle_red);
        tvConnectionStatus.setText(online ? "En ligne • Sync auto" : "Hors ligne • Données locales");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
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
            tvLastEntryTime.setText("");
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

    /** Additionne les transactions locales (LOCAL + SYNCED) du jour, saisies par cet agent
     * — spécifique aux entrées/sorties du COMPTABLE, pas aux ventes (SaisieType.VENTE_*,
     * suivies séparément) : sans ça un VENTE pur verrait une carte à 0/0 sans rapport
     * avec son activité réelle. */
    private void updateFinanceStats() {
        if (!currentUser.isComptable()) return;

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

        // "Synchroniser" doit rafraîchir TOUTES les métadonnées locales, pas seulement
        // les projets — y compris les accès mobile (Production/Comptable/Vente), sinon
        // un changement de réglage admin ne remontait sur le terrain qu'au prochain
        // login. Voir loadFarmAppSettings (cache-first, met aussi à jour le cache local).
        if (currentUser.isProduction() || currentUser.isComptable() || currentUser.isVente()) {
            loadFarmAppSettings();
        }

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

        // Reprise après mise en arrière-plan (retour de connexion sur le terrain,
        // changement d'appli...) : on ne veut pas attendre un tap manuel sur "Sync"
        // pour retirer un projet passé inactif ou récupérer les changements serveur.
        // Le tout premier onResume suit loadProjets() (onCreate) de si près qu'il
        // ferait doublon — on ne rafraîchit qu'à partir de la 2e reprise.
        if (premierResumeFait) {
            refreshProjetsEtCache(() -> { });
        } else {
            premierResumeFait = true;
        }
    }
}
