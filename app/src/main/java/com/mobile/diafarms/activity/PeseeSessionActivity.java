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
import com.mobile.diafarms.data.LocalDatabase;
import com.mobile.diafarms.models.SaisieLocale;
import com.mobile.diafarms.models.SaisieType;
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

        containerSessionsEnCours.removeAllViews();
        List<SaisieLocale> enCours = new ArrayList<>();
        for (SaisieLocale s : localDatabase.getSaisiesByType(SaisieType.PESEE_SESSION)) {
            if (projetUniqueId == null || !projetUniqueId.equals(s.getProjetUniqueId())) continue;
            SessionPeseeSyncRequest req = lire(s);
            if (req != null && !req.isTerminee()) enCours.add(s);
        }
        tvAucuneSession.setVisibility(enCours.isEmpty() ? View.VISIBLE : View.GONE);

        for (SaisieLocale s : enCours) {
            SessionPeseeSyncRequest req = lire(s);
            MaterialButton btn = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setAllCaps(false);
            btn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            btn.setTextSize(15);
            btn.setCornerRadius(dp(12));
            btn.setText("Reprendre — début " + formatDateAffichage(req.dateDebut) + "\n"
                    + req.totalSujets() + " sujet(s), " + formatKg(req.poidsTotalKg()) + " kg, "
                    + req.peseesActives().size() + " pesée(s)");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);
            btn.setMinHeight(dp(64));
            btn.setOnClickListener(v -> ouvrirSession(localDatabase.getSaisieById(s.getLocalId())));
            containerSessionsEnCours.addView(btn, lp);
        }
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

        layoutEntree.setVisibility(View.GONE);
        layoutSession.setVisibility(View.VISIBLE);
        tvTitre.setText("Session de pesée");
        etNombre.setText(String.valueOf(nombreParDefaut()));
        etPoids.setText("");
        tilNombre.setError(null);
        tilPoids.setError(null);
        rafraichirSession();
        if (!session.isTerminee()) etPoids.requestFocus();
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

        tvTotalSujets.setText(String.valueOf(session.totalSujets()));
        tvPoidsTotal.setText(formatKg(session.poidsTotalKg()));
        tvPoidsMoyen.setText(session.totalSujets() > 0 ? formatMoyenne(session.poidsMoyenKg()) : "—");

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

    private View ligneForPesee(SessionPeseeSyncRequest.Pesee p, int numero, boolean terminee) {
        CardView card = new CardView(this);
        card.setRadius(dp(12));
        card.setCardElevation(dp(1));
        card.setCardBackgroundColor(ContextCompat.getColor(this, p.isAnnulee() ? R.color.gray_light : R.color.white));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dp(8);
        card.setLayoutParams(cardLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(8), dp(10));

        TextView tv = new TextView(this);
        String texte = "#" + numero + "  " + p.nombreSujets + " sujet(s) · " + formatKg(p.poidsKg != null ? p.poidsKg : 0) + " kg"
                + "\n" + formatHeure(p.dateHeure);
        if (p.isAnnulee()) texte += " · annulée";
        tv.setText(texte);
        tv.setTextSize(16);
        tv.setTextColor(ContextCompat.getColor(this, p.isAnnulee() ? R.color.gray : R.color.gray_text_dark));
        if (p.isAnnulee()) tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (!terminee && !p.isAnnulee()) {
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
        if (session == null) return;
        if (session.isTerminee()) {
            Toast.makeText(this, "Session terminée : plus aucune pesée ne peut être ajoutée", Toast.LENGTH_SHORT).show();
            return;
        }
        tilNombre.setError(null);
        tilPoids.setError(null);

        Integer nombre = parseEntier(etNombre.getText() != null ? etNombre.getText().toString() : "");
        Double poids = parseDecimal(etPoids.getText() != null ? etPoids.getText().toString() : "");
        boolean ok = true;
        if (nombre == null || nombre < 1 || nombre > NOMBRE_MAX) {
            tilNombre.setError("Entre 1 et " + NOMBRE_MAX);
            ok = false;
        }
        // Arrondi d'abord (3 décimales, comme le serveur), puis contrôle : "0,0004"
        // donnerait sinon une pesée à 0 kg, refusée au moment de l'envoi.
        Double poidsArrondi = poids != null ? Math.round(poids * 1000d) / 1000d : null;
        if (poidsArrondi == null || poidsArrondi <= 0) {
            tilPoids.setError("Poids supérieur à 0");
            ok = false;
        } else if (poidsArrondi > POIDS_MAX_KG) {
            tilPoids.setError("Au plus " + (int) POIDS_MAX_KG + " kg par pesée");
            ok = false;
        }
        if (!ok) return;

        SessionPeseeSyncRequest.Pesee p = new SessionPeseeSyncRequest.Pesee();
        p.uniqueId = UUID.randomUUID().toString();
        p.nombreSujets = nombre;
        p.poidsKg = poidsArrondi;
        p.dateHeure = maintenantIso();
        p.annulee = false;
        session.pesees.add(p);
        enregistrer();

        etNombre.setText(String.valueOf(nombreParDefaut()));
        etPoids.setText("");
        etPoids.requestFocus();
        rafraichirSession();
        Toast.makeText(this, "Pesée ajoutée", Toast.LENGTH_SHORT).show();
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
                    p.annulee = true;
                    enregistrer();
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
        String derniere = "—";
        for (SessionPeseeSyncRequest.Pesee p : actives) derniere = p.dateHeure; // ordre de saisie
        String recap = "Total sujets : " + session.totalSujets()
                + "\nPoids total : " + formatKg(session.poidsTotalKg()) + " kg"
                + "\nPoids moyen : " + formatMoyenne(session.poidsMoyenKg()) + " kg"
                + "\nNombre de pesées : " + actives.size()
                + "\nDate de début : " + formatDateAffichage(session.dateDebut)
                + "\nDernière pesée : " + formatDateAffichage(derniere)
                + "\n\nUne fois terminée, la session ne pourra plus être modifiée.";
        new MaterialAlertDialogBuilder(this)
                .setTitle("Terminer la session ?")
                .setMessage(recap)
                .setPositiveButton("Terminer", (d, w) -> {
                    session.statut = SessionPeseeSyncRequest.STATUT_TERMINEE;
                    session.dateFin = maintenantIso();
                    enregistrer();
                    rafraichirSession();
                    Toast.makeText(this, "Session terminée, à synchroniser depuis l'accueil", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Continuer la pesée", null)
                .show();
    }

    /** Écrit l'état complet de la session en base locale (repasse la ligne en LOCAL). Comme
     * les autres écrans de saisie, l'envoi se fait ensuite depuis l'accueil (SyncManager). */
    private void enregistrer() {
        localDatabase.updateSaisie(sessionLocalId, projetUniqueId, projetLabel, gson.toJson(session), resume(session));
    }

    // ===================== OUTILS =====================

    /** Ex. "Pesée — 6 sujets, 13,0 kg, moy. 2,167 kg (en cours)". */
    public static String resume(SessionPeseeSyncRequest s) {
        int sujets = s.totalSujets();
        String txt = "Pesée — " + sujets + (sujets > 1 ? " sujets, " : " sujet, ")
                + String.format(Locale.FRANCE, "%.1f", s.poidsTotalKg()) + " kg";
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
        if (iso == null) return "—";
        try {
            return AFFICHAGE_DATE_HEURE.format(ISO_LOCAL.parse(iso));
        } catch (ParseException e) {
            return iso;
        }
    }

    private static synchronized String formatHeure(String iso) {
        if (iso == null) return "";
        try {
            return AFFICHAGE_HEURE.format(ISO_LOCAL.parse(iso));
        } catch (ParseException e) {
            return iso;
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
