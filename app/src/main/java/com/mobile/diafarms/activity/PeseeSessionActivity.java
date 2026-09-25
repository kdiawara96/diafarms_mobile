package com.mobile.diafarms.activity;

import android.graphics.Paint;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.mobile.diafarms.R;
import com.mobile.diafarms.data.CachePrefetcher;
import com.mobile.diafarms.data.LocalDatabase;
import com.mobile.diafarms.data.PeseeServeurSync;
import com.mobile.diafarms.models.SaisieLocale;
import com.mobile.diafarms.models.SaisieType;
import com.mobile.diafarms.network.ApiClient;
import com.mobile.diafarms.network.dto.ApiEnvelope;
import com.mobile.diafarms.network.dto.SessionPeseeServeur;
import com.mobile.diafarms.network.dto.SessionPeseeSyncRequest;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Sessions de pesée d'un projet (celui sélectionné à l'accueil, jamais redemandé).
 *
 * Une session = UNE ligne saisies_locales de type PESEE_SESSION dont payload_json est
 * l'état complet de la session (SessionPeseeSyncRequest : session + toutes les pesées,
 * annulées comprises). Chaque modification (création, ajout, annulation, clôture) est
 * écrite immédiatement en base locale et remet la ligne en LOCAL : rien n'est perdu si
 * l'application se ferme, le téléphone redémarre ou le réseau manque. L'envoi se fait
 * ensuite comme pour toutes les autres saisies (SyncManager, depuis l'accueil) ; le
 * serveur est idempotent, l'instantané complet peut être renvoyé sans doublon.
 */
public class PeseeSessionActivity extends AppCompatActivity {

    public static final String EXTRA_PROJET_ID = "PROJET_ID";
    public static final String EXTRA_PROJET_LABEL = "PROJET_LABEL";
    /** Présent à l'ouverture depuis "Mes saisies" : ouvre directement cette session. */
    public static final String EXTRA_LOCAL_ID = "LOCAL_ID";
    private static final String STATE_SESSION_LOCAL_ID = "sessionLocalId";

    // Bornes de saisie (fautes de frappe : "63" au lieu de "6,3" reste possible, mais
    // plus "630000").
    private static final int NOMBRE_MAX = 10000;
    private static final double POIDS_MAX_KG = 10000d;

    private static final SimpleDateFormat ISO_LOCAL = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
    private static final SimpleDateFormat ISO_LOCAL_MINUTES = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
    private static final SimpleDateFormat AFFICHAGE_DATE_HEURE = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE);
    private static final SimpleDateFormat AFFICHAGE_HEURE = new SimpleDateFormat("HH:mm", Locale.FRANCE);

    private final Gson gson = new Gson();
    private LocalDatabase localDatabase;

    private String projetUniqueId;
    private String projetLabel;
    /** true si l'écran a été ouvert directement sur une session (depuis "Mes saisies"). */
    private boolean ouvertSurSession;

    // Session affichée (null = panneau d'accueil)
    private String sessionLocalId;
    private SessionPeseeSyncRequest session;

    private LinearLayout layoutEntree;
    private LinearLayout layoutSession;
    private LinearLayout containerSessionsEnCours;
    private TextView tvAucuneSession;
    private TextView tvTitre;
    private TextView tvSessionInfo;
    private TextView tvSessionTerminee;
    private TextView tvTotalSujets;
    private TextView tvPoidsTotal;
    private TextView tvPoidsMoyen;
    private LinearLayout layoutSaisiePesee;
    private TextInputLayout tilNombre;
    private TextInputLayout tilPoids;
    private TextInputEditText etNombre;
    private TextInputEditText etPoids;
    private TextView tvAucunePesee;
    private LinearLayout containerPesees;
    private MaterialButton btnTerminer;
    private MaterialButton btnRouvrir;
    private LinearLayout layoutModifsWeb;
    private LinearLayout containerModifsWeb;
    /** Sessions EN_COURS connues du serveur seulement (web, autre téléphone), affichées
     * sous les sessions locales dans "Reprendre". */
    private List<SessionPeseeServeur> sessionsServeur = new ArrayList<>();
    /** Anti double tap sur l'ouverture d'une session du serveur. */
    private boolean importEnCours;
    /** true tant que l'activité est visible (callbacks réseau arrivés après la fermeture). */
    private boolean actif;
    /** server_unique_id de la ligne non null : le serveur a déjà reçu la session. */
    private boolean sessionDejaRecue;
    /** Ligne locale SYNCED (repli pour les sessions antérieures à la 1.27). */
    private boolean ligneSynchronisee;
    // Anti double tap sur "Nouvelle session" : un seul dialogue, une seule création.
    private boolean dialogueNouvelleSessionOuvert;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_pesee_session);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_pesee), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        localDatabase = new LocalDatabase(this);

        layoutEntree = findViewById(R.id.layoutEntree);
        layoutSession = findViewById(R.id.layoutSession);
        containerSessionsEnCours = findViewById(R.id.containerSessionsEnCours);
        tvAucuneSession = findViewById(R.id.tvAucuneSession);
        tvTitre = findViewById(R.id.tvPeseeTitre);
        tvSessionInfo = findViewById(R.id.tvSessionInfo);
        tvSessionTerminee = findViewById(R.id.tvSessionTerminee);
        tvTotalSujets = findViewById(R.id.tvTotalSujets);
        tvPoidsTotal = findViewById(R.id.tvPoidsTotal);
        tvPoidsMoyen = findViewById(R.id.tvPoidsMoyen);
        layoutSaisiePesee = findViewById(R.id.layoutSaisiePesee);
        tilNombre = findViewById(R.id.tilNombre);
        tilPoids = findViewById(R.id.tilPoids);
        etNombre = findViewById(R.id.etNombre);
        etPoids = findViewById(R.id.etPoids);
        tvAucunePesee = findViewById(R.id.tvAucunePesee);
        containerPesees = findViewById(R.id.containerPesees);
        btnTerminer = findViewById(R.id.btnTerminerSession);
        btnRouvrir = findViewById(R.id.btnRouvrirSession);
        layoutModifsWeb = findViewById(R.id.layoutModifsWeb);
        containerModifsWeb = findViewById(R.id.containerModifsWeb);

        findViewById(R.id.btnBackPesee).setOnClickListener(v -> retour());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                retour();
            }
        });

        findViewById(R.id.btnNouvelleSession).setOnClickListener(v -> demanderNouvelleSession());
        findViewById(R.id.btnAjouterPesee).setOnClickListener(v -> ajouterPesee());
        btnTerminer.setOnClickListener(v -> demanderTerminer());
        btnRouvrir.setOnClickListener(v -> demanderRouvrir());
        etPoids.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                ajouterPesee();
                return true;
            }
            return false;
        });

        projetUniqueId = getIntent().getStringExtra(EXTRA_PROJET_ID);
        projetLabel = getIntent().getStringExtra(EXTRA_PROJET_LABEL);
        String localId = getIntent().getStringExtra(EXTRA_LOCAL_ID);

        // Recréation (rotation, retour après que le système a tué l'activité...) : on
        // rouvre la session qui était affichée plutôt que de retomber sur l'accueil.
        String sessionRestauree = savedInstanceState != null
                ? savedInstanceState.getString(STATE_SESSION_LOCAL_ID) : null;
        if (localId != null) ouvertSurSession = true;
        if (sessionRestauree != null) {
            SaisieLocale saisie = localDatabase.getSaisieById(sessionRestauree);
            if (saisie != null && saisie.getType() == SaisieType.PESEE_SESSION) {
                projetUniqueId = saisie.getProjetUniqueId();
                projetLabel = saisie.getProjetLabel();
                afficherProjet();
                ouvrirSession(saisie);
                return;
            }
        }

        if (localId != null) {
            SaisieLocale saisie = localDatabase.getSaisieById(localId);
            if (saisie == null || saisie.getType() != SaisieType.PESEE_SESSION) {
                Toast.makeText(this, "Session de pesée introuvable", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            projetUniqueId = saisie.getProjetUniqueId();
            projetLabel = saisie.getProjetLabel();
            afficherProjet();
            ouvrirSession(saisie);
        } else {
            if (projetUniqueId == null) {
                Toast.makeText(this, "Veuillez sélectionner un projet", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            afficherProjet();
            afficherAccueil();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        actif = true;
        if (session == null && projetUniqueId != null && layoutEntree.getVisibility() == View.VISIBLE) {
            dessinerSessionsEnCours(); // une synchro a pu importer/changer des sessions
        }
        // Une synchro (lancée depuis l'accueil) a pu marquer des pesées comme envoyées.
        if (session != null && recharger()) rafraichirSession();
    }

    @Override
    protected void onPause() {
        actif = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        actif = false;
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (sessionLocalId != null) outState.putString(STATE_SESSION_LOCAL_ID, sessionLocalId);
    }

    private void afficherProjet() {
        ((TextView) findViewById(R.id.tvPeseeProjet)).setText(projetLabel != null ? "Projet : " + projetLabel : "");
    }

    private void retour() {
        if (session != null && !ouvertSurSession) {
            afficherAccueil();
        } else {
            finish();
        }
    }

    // ===================== ACCUEIL =====================

    private void afficherAccueil() {
        session = null;
        sessionLocalId = null;
        tvTitre.setText("Pesée");
        layoutSession.setVisibility(View.GONE);
        layoutEntree.setVisibility(View.VISIBLE);

        // Hors ligne d'abord : sessions du téléphone + sessions du serveur préchargées
        // (CachePrefetcher, avec leur détail : importables sans réseau). Puis, si le réseau
        // répond, la liste fraîche du serveur remplace la liste préchargée.
        sessionsServeur = sessionsServeurEnCache();
        dessinerSessionsEnCours();
        chargerSessionsServeur();
    }

    private void dessinerSessionsEnCours() {
        containerSessionsEnCours.removeAllViews();
        List<SaisieLocale> enCours = new ArrayList<>();
        java.util.Set<String> uidsLocaux = new java.util.HashSet<>();
        for (SaisieLocale s : localDatabase.getSaisiesByType(SaisieType.PESEE_SESSION)) {
            SessionPeseeSyncRequest req = lire(s);
            if (req != null && req.uniqueId != null) uidsLocaux.add(req.uniqueId);
            if (projetUniqueId == null || !projetUniqueId.equals(s.getProjetUniqueId())) continue;
            if (req != null && !req.isTerminee()) enCours.add(s);
        }
        List<SessionPeseeServeur> serveurSeul = new ArrayList<>();
        for (SessionPeseeServeur s : sessionsServeur) {
            if (s != null && s.uniqueId != null && !s.isTerminee() && !uidsLocaux.contains(s.uniqueId)
                    && (projetUniqueId == null || projetUniqueId.equals(s.projetUniqueId))) {
                serveurSeul.add(s);
            }
        }
        tvAucuneSession.setVisibility(enCours.isEmpty() && serveurSeul.isEmpty() ? View.VISIBLE : View.GONE);

        for (SaisieLocale s : enCours) {
            SessionPeseeSyncRequest req = lire(s);
            MaterialButton btn = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setAllCaps(false);
            btn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            btn.setTextSize(15);
            btn.setCornerRadius(dp(12));
            btn.setText("Reprendre - début " + formatDateAffichage(req.dateDebut) + "\n"
                    + req.totalSujets() + " sujet(s), " + formatKg(req.poidsTotalKg()) + " kg, "
                    + req.peseesActives().size() + " pesée(s)");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);
            btn.setMinHeight(dp(64));
            btn.setOnClickListener(v -> ouvrirSession(localDatabase.getSaisieById(s.getLocalId())));
            containerSessionsEnCours.addView(btn, lp);
        }

        for (SessionPeseeServeur s : serveurSeul) {
            MaterialButton btn = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setAllCaps(false);
            btn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            btn.setTextSize(15);
            btn.setCornerRadius(dp(12));
            String origine = "WEB".equals(s.origine) ? "ouverte sur le web" : "ouverte sur un autre appareil";
            String par = s.creeParNom != null ? " par " + s.creeParNom : "";
            int sujets = s.nombreTotalSujets != null ? s.nombreTotalSujets : 0;
            btn.setText("Reprendre - début " + formatDateAffichage(s.dateDebut) + "\n"
                    + "Session " + origine + par + "\n"
                    + sujets + " sujet(s), " + formatKg(s.poidsTotalKg != null ? s.poidsTotalKg : 0) + " kg, "
                    + (s.nombrePesees != null ? s.nombrePesees : 0) + " pesée(s)");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);
            btn.setMinHeight(dp(64));
            btn.setOnClickListener(v -> ouvrirSessionServeur(s));
            containerSessionsEnCours.addView(btn, lp);
        }
    }

    /** Sessions du serveur préchargées dont le détail est aussi en cache (importables hors ligne). */
    private List<SessionPeseeServeur> sessionsServeurEnCache() {
        List<SessionPeseeServeur> res = new ArrayList<>();
        if (projetUniqueId == null) return res;
        try {
            String json = localDatabase.getCache(CachePrefetcher.CACHE_PESEE_SESSIONS_PREFIX + projetUniqueId);
            if (json == null) return res;
            SessionPeseeServeur[] tab = gson.fromJson(json, SessionPeseeServeur[].class);
            if (tab == null) return res;
            for (SessionPeseeServeur s : tab) {
                if (s != null && s.uniqueId != null
                        && localDatabase.getCache(CachePrefetcher.CACHE_PESEE_DETAIL_PREFIX + s.uniqueId) != null) {
                    res.add(s);
                }
            }
        } catch (Exception ignored) {
            // cache illisible : sessions du téléphone seulement
        }
        return res;
    }

    /** Liste fraîche des sessions EN_COURS du projet sur le serveur (sans bloquer l'écran ;
     * échec ignoré : on garde les sessions du téléphone et le cache). */
    private void chargerSessionsServeur() {
        if (projetUniqueId == null) return;
        ApiClient.dataApi(this).listSessionsPesee(projetUniqueId, SessionPeseeSyncRequest.STATUT_EN_COURS, 0, 50)
                .enqueue(new Callback<ApiEnvelope<SessionPeseeServeur.Page>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<SessionPeseeServeur.Page>> call, Response<ApiEnvelope<SessionPeseeServeur.Page>> response) {
                if (!actif || session != null || isFinishing()) return;
                SessionPeseeServeur.Page page = response.isSuccessful() && response.body() != null
                        ? response.body().getData() : null;
                if (page == null || page.data == null) return;
                sessionsServeur = new ArrayList<>(page.data);
                dessinerSessionsEnCours();
            }

            @Override
            public void onFailure(Call<ApiEnvelope<SessionPeseeServeur.Page>> call, Throwable t) { }
        });
    }

    /**
     * Ouvre une session connue du serveur seulement : elle est importée comme une nouvelle
     * ligne locale (SYNCED, pesées envoyées), puis fonctionne hors ligne comme les autres.
     * Détail préchargé d'abord (immédiat, hors ligne) ; l'ouverture relit ensuite l'état
     * frais du serveur. Sans cache : lecture réseau.
     */
    private void ouvrirSessionServeur(SessionPeseeServeur s) {
        if (importEnCours || session != null) return;
        SaisieLocale existante = PeseeServeurSync.trouverLigne(localDatabase, s.uniqueId);
        if (existante != null) {
            ouvrirSession(existante);
            return;
        }
        SessionPeseeServeur enCache = null;
        try {
            String json = localDatabase.getCache(CachePrefetcher.CACHE_PESEE_DETAIL_PREFIX + s.uniqueId);
            if (json != null) enCache = gson.fromJson(json, SessionPeseeServeur.class);
        } catch (Exception ignored) {
            enCache = null;
        }
        if (enCache != null && enCache.uniqueId != null && enCache.pesees != null) {
            importerEtOuvrir(enCache);
            return;
        }
        importEnCours = true;
        Toast.makeText(this, "Récupération de la session...", Toast.LENGTH_SHORT).show();
        ApiClient.dataApi(this).getSessionPesee(s.uniqueId).enqueue(new Callback<ApiEnvelope<SessionPeseeServeur>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<SessionPeseeServeur>> call, Response<ApiEnvelope<SessionPeseeServeur>> response) {
                importEnCours = false;
                if (!actif || isFinishing()) return;
                SessionPeseeServeur d = response.isSuccessful() && response.body() != null ? response.body().getData() : null;
                if (d == null || d.uniqueId == null || d.pesees == null) {
                    Toast.makeText(PeseeSessionActivity.this, "Session introuvable sur le serveur", Toast.LENGTH_LONG).show();
                    return;
                }
                if (session == null) importerEtOuvrir(d);
            }

            @Override
            public void onFailure(Call<ApiEnvelope<SessionPeseeServeur>> call, Throwable t) {
                importEnCours = false;
                if (!actif || isFinishing()) return;
                Toast.makeText(PeseeSessionActivity.this,
                        "Connexion nécessaire pour ouvrir cette session la première fois", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void importerEtOuvrir(SessionPeseeServeur d) {
        String localId = PeseeServeurSync.importer(localDatabase, d, projetLabel);
        ouvrirSession(localDatabase.getSaisieById(localId));
    }

    /**
     * Session déjà connue du serveur et sans rien en attente (SYNCED) : relit son état
     * serveur en arrière-plan pour montrer les modifications faites sur le web sans attendre
     * une nouvelle pesée. Jamais bloquant, échec ignoré (hors ligne : état local).
     */
    private void rafraichirDepuisServeur(SaisieLocale saisie) {
        if (saisie.getServerUniqueId() == null || !SaisieLocale.STATUT_SYNCED.equals(saisie.getSyncStatus())) return;
        final String localId = saisie.getLocalId();
        ApiClient.dataApi(this).getSessionPesee(saisie.getServerUniqueId())
                .enqueue(new Callback<ApiEnvelope<SessionPeseeServeur>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<SessionPeseeServeur>> call, Response<ApiEnvelope<SessionPeseeServeur>> response) {
                SessionPeseeServeur d = response.isSuccessful() && response.body() != null ? response.body().getData() : null;
                if (d == null || d.uniqueId == null || d.pesees == null) return;
                SaisieLocale ligne = localDatabase.getSaisieById(localId);
                // Une synchro ERROR n'est pas touchée (message de refus conservé).
                if (ligne == null || SaisieLocale.STATUT_ERROR.equals(ligne.getSyncStatus())) return;
                boolean visible = actif && !isFinishing() && localId.equals(sessionLocalId);
                SessionPeseeSyncRequest.Fusion f = PeseeServeurSync.appliquer(getApplicationContext(),
                        localDatabase, localId, null, d, !visible);
                if (f == null || !visible) return;
                if (recharger()) rafraichirSession();
                if (!f.nouveauxEvenements.isEmpty()) {
                    Toast.makeText(PeseeSessionActivity.this, "Session modifiée sur le web", Toast.LENGTH_LONG).show();
                }
                if (f.nouvellesRefusees > 0) {
                    Toast.makeText(PeseeSessionActivity.this, "Session terminée sur le web : "
                            + f.nouvellesRefusees + " pesée(s) non enregistrée(s)", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<SessionPeseeServeur>> call, Throwable t) { }
        });
    }

    private void demanderNouvelleSession() {
        if (dialogueNouvelleSessionOuvert || session != null) return;
        dialogueNouvelleSessionOuvert = true;
        TextInputLayout til = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        til.setHint("Nombre habituel de sujets par pesée");
        TextInputEditText et = new TextInputEditText(til.getContext());
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(5)});
        et.setText("1");
        et.setSelectAllOnFocus(true);
        et.setTextSize(22);
        til.addView(et);
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.setPadding(dp(20), dp(8), dp(20), 0);
        wrapper.addView(til);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Nouvelle session de pesée")
                .setView(wrapper)
                .setPositiveButton("Commencer", null)
                .setNegativeButton("Annuler", null)
                .create();
        final boolean[] creee = {false};
        dialog.setOnDismissListener(d -> dialogueNouvelleSessionOuvert = false);
        dialog.setOnShowListener(d -> {
            et.requestFocus();
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (creee[0]) return; // double tap sur "Commencer"
                Integer nombre = parseEntier(et.getText() != null ? et.getText().toString() : "");
                if (nombre == null || nombre < 1 || nombre > NOMBRE_MAX) {
                    til.setError("Entier entre 1 et " + NOMBRE_MAX);
                    return;
                }
                creee[0] = true;
                v.setEnabled(false);
                dialog.dismiss();
                creerSession(nombre);
            });
        });
        dialog.show();
    }

    private void creerSession(int nombreParDefaut) {
        SessionPeseeSyncRequest req = new SessionPeseeSyncRequest();
        req.uniqueId = UUID.randomUUID().toString();
        req.projetUniqueId = projetUniqueId;
        req.nombreParDefaut = nombreParDefaut;
        req.dateDebut = maintenantIso();
        req.statut = SessionPeseeSyncRequest.STATUT_EN_COURS;

        String localId = localDatabase.insertSaisie(SaisieType.PESEE_SESSION, projetUniqueId, projetLabel,
                gson.toJson(req), resume(req));
        ouvrirSession(localDatabase.getSaisieById(localId));
    }

    // ===================== SESSION =====================

    private void ouvrirSession(SaisieLocale saisie) {
        SessionPeseeSyncRequest req = saisie != null ? lire(saisie) : null;
        if (req == null) {
            Toast.makeText(this, "Session de pesée illisible", Toast.LENGTH_SHORT).show();
            if (ouvertSurSession) finish(); else afficherAccueil();
            return;
        }
        if (req.pesees == null) req.pesees = new ArrayList<>();
        sessionLocalId = saisie.getLocalId();
        session = req;
        sessionDejaRecue = saisie.getServerUniqueId() != null;
        ligneSynchronisee = SaisieLocale.STATUT_SYNCED.equals(saisie.getSyncStatus());

        layoutEntree.setVisibility(View.GONE);
        layoutSession.setVisibility(View.VISIBLE);
        tvTitre.setText("Session de pesée");
        etNombre.setText(String.valueOf(nombreParDefaut()));
        etPoids.setText("");
        tilNombre.setError(null);
        tilPoids.setError(null);
        rafraichirSession();
        if (!session.isTerminee()) etPoids.requestFocus();
        rafraichirDepuisServeur(saisie);
    }

    /**
     * Relit la session en base avant chaque modification : une synchro a pu la changer
     * depuis l'affichage (pesées marquées envoyées, voir SyncManager). Retourne false (et
     * quitte la session) si elle n'existe plus.
     */
    private boolean recharger() {
        SaisieLocale saisie = sessionLocalId != null ? localDatabase.getSaisieById(sessionLocalId) : null;
        SessionPeseeSyncRequest req = saisie != null ? lire(saisie) : null;
        if (req == null) {
            Toast.makeText(this, "Session de pesée introuvable", Toast.LENGTH_SHORT).show();
            if (ouvertSurSession) finish(); else afficherAccueil();
            return false;
        }
        if (req.pesees == null) req.pesees = new ArrayList<>();
        session = req;
        sessionDejaRecue = saisie.getServerUniqueId() != null;
        ligneSynchronisee = SaisieLocale.STATUT_SYNCED.equals(saisie.getSyncStatus());
        return true;
    }

    private SessionPeseeSyncRequest.Pesee trouverPesee(String uniqueId) {
        for (SessionPeseeSyncRequest.Pesee p : session.pesees) {
            if (p != null && uniqueId != null && uniqueId.equals(p.uniqueId)) return p;
        }
        return null;
    }

    private boolean estEnvoyee(SessionPeseeSyncRequest.Pesee p) {
        return SessionPeseeSyncRequest.estEnvoyee(p, sessionDejaRecue);
    }

    private int nombreParDefaut() {
        return session.nombreParDefaut != null && session.nombreParDefaut >= 1 ? session.nombreParDefaut : 1;
    }

    private void rafraichirSession() {
        boolean terminee = session.isTerminee();

        String info = "Début : " + formatDateAffichage(session.dateDebut)
                + " · " + nombreParDefaut() + " sujet(s) par pesée par défaut";
        if (terminee) info += "\nFin : " + formatDateAffichage(session.dateFin);
        tvSessionInfo.setText(info);
        tvSessionTerminee.setVisibility(terminee ? View.VISIBLE : View.GONE);
        // Clôture envoyée = session figée côté serveur. Sinon elle peut encore être rouverte.
        boolean clotureEnvoyee = session.isClotureEnvoyee(ligneSynchronisee);
        int refusees = 0;
        for (SessionPeseeSyncRequest.Pesee p : session.pesees) if (p != null && p.isRefusee()) refusees++;
        String texteTerminee = clotureEnvoyee
                ? "Session terminée et enregistrée sur le serveur : modification impossible."
                : "Session terminée, pas encore envoyée : vous pouvez encore la rouvrir.";
        if (refusees > 0) {
            texteTerminee += "\nSession terminée sur le web : " + refusees + " pesée(s) non enregistrée(s).";
        }
        tvSessionTerminee.setText(texteTerminee);
        dessinerModifsWeb();
        btnRouvrir.setVisibility(terminee && !clotureEnvoyee ? View.VISIBLE : View.GONE);

        tvTotalSujets.setText(String.valueOf(session.totalSujets()));
        tvPoidsTotal.setText(formatKg(session.poidsTotalKg()));
        tvPoidsMoyen.setText(session.totalSujets() > 0 ? formatMoyenne(session.poidsMoyenKg()) : "-");

        // Session terminée : lecture seule (saisie, annulation et clôture désactivées).
        layoutSaisiePesee.setVisibility(terminee ? View.GONE : View.VISIBLE);
        etNombre.setEnabled(!terminee);
        etPoids.setEnabled(!terminee);
        btnTerminer.setVisibility(terminee ? View.GONE : View.VISIBLE);

        containerPesees.removeAllViews();
        tvAucunePesee.setVisibility(session.pesees.isEmpty() ? View.VISIBLE : View.GONE);
        // Plus récente d'abord ; numérotée dans l'ordre de saisie.
        for (int i = session.pesees.size() - 1; i >= 0; i--) {
            containerPesees.addView(ligneForPesee(session.pesees.get(i), i + 1, terminee));
        }
    }

    /** Bandeau "Modifications faites sur le web" : journal du serveur, le plus récent d'abord. */
    private void dessinerModifsWeb() {
        containerModifsWeb.removeAllViews();
        List<SessionPeseeSyncRequest.EvenementWeb> journal = session.evenementsWeb;
        if (journal == null || journal.isEmpty()) {
            layoutModifsWeb.setVisibility(View.GONE);
            return;
        }
        layoutModifsWeb.setVisibility(View.VISIBLE);
        int max = 10;
        int affiches = 0;
        for (int i = journal.size() - 1; i >= 0 && affiches < max; i--, affiches++) {
            SessionPeseeSyncRequest.EvenementWeb e = journal.get(i);
            TextView tv = new TextView(this);
            String meta = formatDateAffichage(e.date);
            if (e.parNom != null && (e.description == null || !e.description.contains(e.parNom))) {
                meta += " · " + e.parNom;
            }
            tv.setText((e.description != null ? e.description : "Modification") + "\n" + meta);
            tv.setTextSize(14);
            tv.setTextColor(ContextCompat.getColor(this, R.color.gray_text_dark));
            tv.setPadding(0, dp(6), 0, 0);
            containerModifsWeb.addView(tv);
        }
        if (journal.size() > max) {
            TextView tv = new TextView(this);
            tv.setText("et " + (journal.size() - max) + " modification(s) plus ancienne(s)");
            tv.setTextSize(13);
            tv.setTextColor(ContextCompat.getColor(this, R.color.gray_text_medium));
            tv.setPadding(0, dp(6), 0, 0);
            containerModifsWeb.addView(tv);
        }
    }

    private View ligneForPesee(SessionPeseeSyncRequest.Pesee p, int numero, boolean terminee) {
        CardView card = new CardView(this);
        card.setRadius(dp(12));
        card.setCardElevation(dp(1));
        boolean barree = p.isAnnulee() || p.isRefusee();
        card.setCardBackgroundColor(ContextCompat.getColor(this, barree ? R.color.gray_light : R.color.white));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dp(8);
        card.setLayoutParams(cardLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(8), dp(10));

        boolean envoyee = estEnvoyee(p);
        TextView tv = new TextView(this);
        String texte = "#" + numero + "  " + p.nombreSujets + " sujet(s) · " + formatKg(p.poidsKg != null ? p.poidsKg : 0) + " kg"
                + "\n" + formatHeure(p.dateHeure);
        if (p.isAnnulee()) texte += " · annulée";
        if (p.isRefusee()) {
            texte += " · refusée (session terminée sur le web)";
        } else {
            texte += envoyee ? " · envoyée ✓" : " · non envoyée";
        }
        if (p.isWeb()) texte += "\najoutée sur le web";
        if (p.isModifiee()) texte += p.isWeb() ? ", modifiée sur le web" : "\nmodifiée sur le web";
        tv.setText(texte);
        tv.setTextSize(16);
        tv.setTextColor(ContextCompat.getColor(this, barree ? R.color.gray : R.color.gray_text_dark));
        if (barree) tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (!terminee && !envoyee) {
            // Pas encore envoyée : modifiable / supprimable librement sur le téléphone.
            MaterialButton btn = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setText("Modifier");
            btn.setAllCaps(false);
            btn.setCornerRadius(dp(10));
            btn.setContentDescription("Modifier la pesée");
            btn.setOnClickListener(v -> demanderModification(p.uniqueId, numero));
            row.addView(btn);
            card.setOnClickListener(v -> demanderModification(p.uniqueId, numero));
        } else if (!terminee && !p.isAnnulee() && !p.isRefusee()) {
            // Déjà envoyée : le serveur n'accepte plus que l'annulation.
            MaterialButton btn = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setText("Annuler");
            btn.setAllCaps(false);
            btn.setTextColor(ContextCompat.getColor(this, R.color.red_error));
            btn.setStrokeColor(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.red_error)));
            btn.setCornerRadius(dp(10));
            btn.setContentDescription("Annuler cette pesée");
            btn.setOnClickListener(v -> demanderAnnulation(p, numero));
            row.addView(btn);
            card.setOnLongClickListener(v -> {
                demanderAnnulation(p, numero);
                return true;
            });
        }
        card.addView(row);
        return card;
    }

    private void ajouterPesee() {
        if (session == null || !recharger()) return;
        if (session.isTerminee()) {
            Toast.makeText(this, "Session terminée : plus aucune pesée ne peut être ajoutée", Toast.LENGTH_SHORT).show();
            return;
        }
        tilNombre.setError(null);
        tilPoids.setError(null);

        Integer nombre = validerNombre(tilNombre, etNombre);
        Double poidsArrondi = validerPoids(tilPoids, etPoids);
        if (nombre == null || poidsArrondi == null) return;

        SessionPeseeSyncRequest.Pesee p = new SessionPeseeSyncRequest.Pesee();
        p.uniqueId = UUID.randomUUID().toString();
        p.nombreSujets = nombre;
        p.poidsKg = poidsArrondi;
        p.dateHeure = maintenantIso();
        p.annulee = false;
        p.envoyee = false; // explicite : null = pesée d'avant la 1.27 (voir estEnvoyee)
        session.pesees.add(p);
        enregistrer();

        etNombre.setText(String.valueOf(nombreParDefaut()));
        etPoids.setText("");
        etPoids.requestFocus();
        rafraichirSession();
        Toast.makeText(this, "Pesée ajoutée", Toast.LENGTH_SHORT).show();
    }

    /** Nombre de sujets saisi, ou null (erreur affichée sur {@code til}) s'il est invalide. */
    private Integer validerNombre(TextInputLayout til, TextInputEditText et) {
        Integer nombre = parseEntier(et.getText() != null ? et.getText().toString() : "");
        if (nombre == null || nombre < 1 || nombre > NOMBRE_MAX) {
            til.setError("Entre 1 et " + NOMBRE_MAX);
            return null;
        }
        til.setError(null);
        return nombre;
    }

    /** Poids arrondi à 3 décimales, ou null (erreur affichée sur {@code til}). Arrondi
     * d'abord (comme le serveur), puis contrôle : "0,0004" donnerait sinon une pesée à
     * 0 kg, refusée au moment de l'envoi. */
    private Double validerPoids(TextInputLayout til, TextInputEditText et) {
        Double poids = parseDecimal(et.getText() != null ? et.getText().toString() : "");
        Double poidsArrondi = poids != null ? Math.round(poids * 1000d) / 1000d : null;
        if (poidsArrondi == null || poidsArrondi <= 0) {
            til.setError("Poids supérieur à 0");
            return null;
        }
        if (poidsArrondi > POIDS_MAX_KG) {
            til.setError("Au plus " + (int) POIDS_MAX_KG + " kg par pesée");
            return null;
        }
        til.setError(null);
        return poidsArrondi;
    }

    /**
     * Pesée pas encore envoyée : modification (nombre, poids) ou suppression réelle. Une
     * fois envoyée, le serveur ignorerait ces changements : on refuse alors (la synchro a
     * pu passer pendant que le dialogue était ouvert, d'où le rechargement).
     */
    private void demanderModification(String uniqueId, int numero) {
        if (session == null || !recharger()) return;
        SessionPeseeSyncRequest.Pesee p = trouverPesee(uniqueId);
        if (p == null || session.isTerminee() || estEnvoyee(p)) {
            rafraichirSession();
            return;
        }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);

        TextInputLayout tilN = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        tilN.setHint("Nombre de sujets");
        TextInputEditText etN = new TextInputEditText(tilN.getContext());
        etN.setInputType(InputType.TYPE_CLASS_NUMBER);
        etN.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(5)});
        etN.setText(p.nombreSujets != null ? String.valueOf(p.nombreSujets) : "");
        etN.setTextSize(20);
        tilN.addView(etN);
        form.addView(tilN);

        TextInputLayout tilP = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        tilP.setHint("Poids (kg)");
        TextInputEditText etP = new TextInputEditText(tilP.getContext());
        // Virgule acceptée comme le point (comme android:digits du champ principal).
        etP.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));
        etP.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etP.setText(p.poidsKg != null ? formatKg(p.poidsKg) : "");
        etP.setTextSize(20);
        tilP.addView(etP);
        LinearLayout.LayoutParams lpP = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpP.topMargin = dp(8);
        form.addView(tilP, lpP);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Modifier la pesée #" + numero)
                .setView(form)
                .setPositiveButton("Enregistrer", null)
                .setNeutralButton("Supprimer", null)
                .setNegativeButton("Fermer", null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                Integer nombre = validerNombre(tilN, etN);
                Double poids = validerPoids(tilP, etP);
                if (nombre == null || poids == null) return;
                dialog.dismiss();
                SessionPeseeSyncRequest.Pesee cible = peseeModifiable(uniqueId);
                if (cible == null) return;
                cible.nombreSujets = nombre;
                cible.poidsKg = poids;
                enregistrer();
                rafraichirSession();
                Toast.makeText(this, "Pesée modifiée", Toast.LENGTH_SHORT).show();
            });
            android.widget.Button btnSupprimer = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL);
            btnSupprimer.setTextColor(ContextCompat.getColor(this, R.color.red_error));
            btnSupprimer.setOnClickListener(v -> {
                dialog.dismiss();
                demanderSuppression(uniqueId, numero);
            });
        });
        dialog.show();
    }

    private void demanderSuppression(String uniqueId, int numero) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Supprimer cette pesée ?")
                .setMessage("La pesée #" + numero + " n'a pas encore été envoyée : elle sera retirée de la session.")
                .setPositiveButton("Supprimer", (d, w) -> {
                    SessionPeseeSyncRequest.Pesee cible = peseeModifiable(uniqueId);
                    if (cible == null) return;
                    session.pesees.remove(cible);
                    // Pierre tombale : si le serveur l'a reçue malgré tout (réponse perdue),
                    // elle reviendra annulée (voir SessionPeseeSyncRequest.fusionner).
                    if (session.peseesSupprimees == null) session.peseesSupprimees = new ArrayList<>();
                    if (!session.peseesSupprimees.contains(cible.uniqueId)) session.peseesSupprimees.add(cible.uniqueId);
                    enregistrer();
                    rafraichirSession();
                    Toast.makeText(this, "Pesée supprimée", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Garder", null)
                .show();
    }

    /** Recharge la session et retourne la pesée si elle est encore modifiable (session en
     * cours, pesée non envoyée) ; sinon explique pourquoi et retourne null. */
    private SessionPeseeSyncRequest.Pesee peseeModifiable(String uniqueId) {
        if (session == null || !recharger()) return null;
        SessionPeseeSyncRequest.Pesee p = trouverPesee(uniqueId);
        if (p == null || session.isTerminee() || estEnvoyee(p)) {
            Toast.makeText(this, "Cette pesée vient d'être envoyée : elle ne peut plus être modifiée, seulement annulée.",
                    Toast.LENGTH_LONG).show();
            rafraichirSession();
            return null;
        }
        return p;
    }

    private void demanderAnnulation(SessionPeseeSyncRequest.Pesee p, int numero) {
        if (session == null || session.isTerminee() || p.isAnnulee()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Annuler cette pesée ?")
                .setMessage("Pesée #" + numero + " : " + p.nombreSujets + " sujet(s), "
                        + formatKg(p.poidsKg != null ? p.poidsKg : 0) + " kg.\n\n"
                        + "Elle restera visible dans l'historique mais ne comptera plus dans les totaux. "
                        + "Cette annulation est définitive.")
                .setPositiveButton("Annuler la pesée", (d, w) -> {
                    if (!recharger()) return;
                    SessionPeseeSyncRequest.Pesee cible = trouverPesee(p.uniqueId);
                    if (cible != null && !session.isTerminee()) {
                        cible.annulee = true;
                        enregistrer();
                    }
                    rafraichirSession();
                })
                .setNegativeButton("Garder", null)
                .show();
    }

    private void demanderTerminer() {
        if (session == null || session.isTerminee()) return;
        List<SessionPeseeSyncRequest.Pesee> actives = session.peseesActives();
        if (actives.isEmpty()) {
            Toast.makeText(this, "Ajoutez au moins une pesée avant de terminer la session", Toast.LENGTH_LONG).show();
            return;
        }
        String derniere = "-";
        for (SessionPeseeSyncRequest.Pesee p : actives) derniere = p.dateHeure; // ordre de saisie
        String recap = "Total sujets : " + session.totalSujets()
                + "\nPoids total : " + formatKg(session.poidsTotalKg()) + " kg"
                + "\nPoids moyen : " + formatMoyenne(session.poidsMoyenKg()) + " kg"
                + "\nNombre de pesées : " + actives.size()
                + "\nDate de début : " + formatDateAffichage(session.dateDebut)
                + "\nDernière pesée : " + formatDateAffichage(derniere)
                + "\n\nTant qu'elle n'est pas envoyée, vous pourrez la rouvrir. "
                + "Une fois envoyée, elle ne pourra plus être modifiée.";
        new MaterialAlertDialogBuilder(this)
                .setTitle("Terminer la session ?")
                .setMessage(recap)
                .setPositiveButton("Terminer", (d, w) -> {
                    if (!recharger()) return;
                    if (session.isTerminee() || session.peseesActives().isEmpty()) {
                        rafraichirSession();
                        return;
                    }
                    session.statut = SessionPeseeSyncRequest.STATUT_TERMINEE;
                    session.dateFin = maintenantIso();
                    session.termineeEnvoyee = false; // passe à true après un envoi réussi
                    enregistrer();
                    rafraichirSession();
                    Toast.makeText(this, "Session terminée, à synchroniser depuis l'accueil", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Continuer la pesée", null)
                .show();
    }

    /** Session terminée mais clôture pas encore envoyée : retour à EN_COURS (dateFin
     * effacée), de nouveau modifiable. Impossible une fois la clôture reçue par le serveur. */
    private void demanderRouvrir() {
        if (session == null || !session.isTerminee()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Rouvrir la session ?")
                .setMessage("La session repassera en cours : vous pourrez ajouter, modifier ou annuler des pesées, "
                        + "puis la terminer à nouveau.")
                .setPositiveButton("Rouvrir", (d, w) -> {
                    if (!recharger()) return;
                    if (!session.isTerminee() || session.isClotureEnvoyee(ligneSynchronisee)) {
                        Toast.makeText(this, "Session envoyée : modification impossible", Toast.LENGTH_LONG).show();
                        rafraichirSession();
                        return;
                    }
                    session.statut = SessionPeseeSyncRequest.STATUT_EN_COURS;
                    session.dateFin = null;
                    session.termineeEnvoyee = false;
                    enregistrer();
                    etNombre.setText(String.valueOf(nombreParDefaut()));
                    etPoids.setText("");
                    rafraichirSession();
                    Toast.makeText(this, "Session rouverte", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    /** Écrit l'état complet de la session en base locale (repasse la ligne en LOCAL). Comme
     * les autres écrans de saisie, l'envoi se fait ensuite depuis l'accueil (SyncManager). */
    private void enregistrer() {
        localDatabase.updateSaisie(sessionLocalId, projetUniqueId, projetLabel, gson.toJson(session), resume(session));
        ligneSynchronisee = false; // updateSaisie repasse la ligne en LOCAL
    }

    // ===================== OUTILS =====================

    /** Ex. "Pesée : 6 sujets, 13,0 kg, moy. 2,167 kg (en cours)" (résumé de Mes saisies). */
    public static String resume(SessionPeseeSyncRequest s) {
        int sujets = s.totalSujets();
        String txt = "Pesée : " + sujets + (sujets > 1 ? " sujets, " : " sujet, ")
                + formatKg(s.poidsTotalKg()) + " kg";
        if (sujets > 0) txt += ", moy. " + formatMoyenne(s.poidsMoyenKg()) + " kg";
        return txt + (s.isTerminee() ? " (terminée)" : " (en cours)");
    }

    private SessionPeseeSyncRequest lire(SaisieLocale s) {
        try {
            return gson.fromJson(s.getPayloadJson(), SessionPeseeSyncRequest.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static synchronized String maintenantIso() {
        return ISO_LOCAL.format(new Date());
    }

    private static synchronized String formatDateAffichage(String iso) {
        if (iso == null) return "-";
        Date d = parseIso(iso);
        return d != null ? AFFICHAGE_DATE_HEURE.format(d) : iso;
    }

    private static synchronized String formatHeure(String iso) {
        if (iso == null) return "";
        Date d = parseIso(iso);
        return d != null ? AFFICHAGE_HEURE.format(d) : iso;
    }

    /** Dates du téléphone ("…T08:05:00") et du serveur (fractions de seconde possibles,
     * secondes parfois absentes : "…T08:05"). */
    private static Date parseIso(String iso) {
        try {
            return ISO_LOCAL.parse(iso); // ignore ce qui suit les secondes
        } catch (ParseException e) {
            try {
                return ISO_LOCAL_MINUTES.parse(iso);
            } catch (ParseException e2) {
                return null;
            }
        }
    }

    private static String formatKg(double kg) {
        return new DecimalFormat("0.0##", DecimalFormatSymbols.getInstance(Locale.FRANCE)).format(kg);
    }

    /** 3 décimales, comme le serveur et le web (arrondi HALF_UP côté back). */
    private static String formatMoyenne(double kg) {
        return new java.math.BigDecimal(Double.toString(kg)).setScale(3, java.math.RoundingMode.HALF_UP)
                .toPlainString().replace('.', ',');
    }

    private static Integer parseEntier(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Accepte la virgule comme le point ("6,3" ou "6.3"). */
    private static Double parseDecimal(String s) {
        try {
            double v = Double.parseDouble(s.trim().replace(',', '.'));
            return Double.isNaN(v) || Double.isInfinite(v) ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
