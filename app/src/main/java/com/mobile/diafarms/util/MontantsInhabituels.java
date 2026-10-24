package com.mobile.diafarms.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mobile.diafarms.data.SessionManager;
import com.mobile.diafarms.network.ApiClient;
import com.mobile.diafarms.network.dto.AchatMedicamentCreateRequest;
import com.mobile.diafarms.network.dto.AlimentationCreateRequest;
import com.mobile.diafarms.network.dto.TransactionCreateRequest;
import com.mobile.diafarms.network.dto.VenteDiverseCreateRequest;
import com.mobile.diafarms.network.dto.VenteOeufsCreateRequest;
import com.mobile.diafarms.network.dto.VenteReformeCreateRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repère une faute de frappe sur un montant (un zéro de trop...) et demande
 * confirmation avant d'enregistrer, même hors ligne. Mêmes règles que le web
 * (useMontantInhabituel) :
 *  - prix unitaire plus de 3 fois plus cher ou moins cher que d'habitude ;
 *  - sinon montant de plus de 10 fois le plus gros de la catégorie sur 90 jours ;
 *  - sans historique : 10 millions ou plus ;
 *  - jamais en dessous de 100 000.
 * Repères de la ferme (GET reperes-montants, voir ReperesMontantsService) gardés sur le
 * téléphone, par ferme, et relus quand il y a du réseau.
 */
public final class MontantsInhabituels {

    static final double RATIO_PRIX = 3;
    static final double RATIO_MAX = 10;
    static final double SEUIL_SANS_HISTORIQUE = 10_000_000;
    static final double MONTANT_MIN = 100_000;
    private static final int OEUFS_PAR_ALVEOLE = 30;
    private static final String PREFS = "diafarms_reperes_montants";
    private static final Gson GSON = new Gson();

    private MontantsInhabituels() { }

    /** Repères de la ferme : prix habituels et plus gros montant par catégorie. */
    public static final class Reperes {
        public Map<String, Double> prix = new HashMap<>();
        public Map<String, Double> maxParCategorie = new HashMap<>();
    }

    public interface Format {
        String montant(double m);
    }

    /** Un montant à contrôler ; prixType null = seulement la règle de la catégorie. */
    static final class Controle {
        final double montant;
        @Nullable final String categorie;
        @Nullable final String prixType;
        final double prixUnitaire;
        final String unite;
        final double facteur;

        Controle(double montant, @Nullable String categorie, @Nullable String prixType, double prixUnitaire, String unite, double facteur) {
            this.montant = montant;
            this.categorie = categorie;
            this.prixType = prixType;
            this.prixUnitaire = prixUnitaire;
            this.unite = unite;
            this.facteur = facteur;
        }

        static Controle categorie(double montant, @Nullable String categorie) {
            return new Controle(montant, categorie, null, 0, "", 1);
        }
    }

    @Nullable
    static String message(Controle c, @Nullable Reperes r, Format f) {
        if (!(c.montant >= MONTANT_MIN)) return null;
        if (c.prixType != null && c.prixUnitaire > 0) {
            Double habituel = r != null ? r.prix.get(c.prixType) : null;
            if (habituel != null && habituel > 0) {
                if (c.prixUnitaire > habituel * RATIO_PRIX || c.prixUnitaire < habituel / RATIO_PRIX) {
                    return f.montant(c.prixUnitaire * c.facteur) + " " + c.unite + " ? D'habitude : environ "
                            + f.montant(habituel * c.facteur) + ".";
                }
                return null;
            }
        }
        Double max = r != null && c.categorie != null ? r.maxParCategorie.get(c.categorie) : null;
        if (max != null && max > 0) {
            return c.montant > max * RATIO_MAX
                    ? f.montant(c.montant) + " ? C'est plus de 10 fois votre plus gros montant récent (" + f.montant(max) + ")."
                    : null;
        }
        return c.montant >= SEUIL_SANS_HISTORIQUE ? f.montant(c.montant) + " ? Le montant est très élevé." : null;
    }

    private static double nz(@Nullable Double d) { return d == null ? 0 : d; }

    /** Contrôles d'une saisie construite par le formulaire (null = rien à contrôler). */
    static List<Controle> controles(Object req) {
        List<Controle> out = new ArrayList<>();
        if (req instanceof AlimentationCreateRequest) {
            AlimentationCreateRequest a = (AlimentationCreateRequest) req;
            double kg = nz(a.quantiteKg);
            out.add(new Controle(nz(a.coutTotal), "Aliment", kg > 0 ? "ALIMENT_KG" : null,
                    kg > 0 ? nz(a.coutTotal) / kg : 0, "le kilo d'aliment", 1));
        } else if (req instanceof VenteOeufsCreateRequest) {
            VenteOeufsCreateRequest v = (VenteOeufsCreateRequest) req;
            String type = "CASSE".equals(v.typeOeuf) ? "OEUF_CASSE" : "OEUF_BON";
            int q = v.quantiteOeufs == null ? 0 : v.quantiteOeufs;
            out.add(new Controle(nz(v.montant), "Vente œufs", type, nz(v.prixUnitaire), "l'alvéole", OEUFS_PAR_ALVEOLE));
            if (v.montantRapporte != null && q > 0) {
                out.add(new Controle(v.montantRapporte, "Vente œufs", type, v.montantRapporte / q,
                        "l'alvéole (montant rapporté)", OEUFS_PAR_ALVEOLE));
            }
        } else if (req instanceof VenteReformeCreateRequest) {
            VenteReformeCreateRequest v = (VenteReformeCreateRequest) req;
            boolean kilo = "KILO".equals(v.typeVente);
            String type = kilo ? "REFORME_KILO" : "REFORME_TETE";
            String unite = kilo ? "le kilo" : "le sujet";
            double q = kilo ? nz(v.poidsTotalKg) : (v.nombreSujets == null ? 0 : v.nombreSujets);
            out.add(new Controle(nz(v.montant), "Vente réforme", type, nz(v.prixUnitaire), unite, 1));
            if (v.montantRapporte != null && q > 0) {
                out.add(new Controle(v.montantRapporte, "Vente réforme", type, v.montantRapporte / q, unite + " (montant rapporté)", 1));
            }
        } else if (req instanceof VenteDiverseCreateRequest) {
            VenteDiverseCreateRequest v = (VenteDiverseCreateRequest) req;
            out.add(Controle.categorie(nz(v.montant), "FIENTES".equals(v.produit) ? "Vente fientes" : "Autre vente"));
        } else if (req instanceof AchatMedicamentCreateRequest) {
            out.add(Controle.categorie(nz(((AchatMedicamentCreateRequest) req).coutTotal), "Santé / Vétérinaire"));
        } else if (req instanceof TransactionCreateRequest) {
            TransactionCreateRequest t = (TransactionCreateRequest) req;
            String cat = "Autre".equals(t.categorie) && t.categoriePrecision != null ? t.categoriePrecision : t.categorie;
            out.add(Controle.categorie(nz(t.montant), cat));
        }
        return out;
    }

    /** Premier message d'alerte pour cette saisie, ou null. */
    @Nullable
    public static String messagePour(Context context, Object req, Format f) {
        Reperes r = lire(context);
        for (Controle c : controles(req)) {
            String m = message(c, r, f);
            if (m != null) return m;
        }
        return null;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Nullable
    private static Reperes lire(Context context) {
        String farm = SessionManager.activeFarmId(context);
        if (farm == null) return null;
        String json = prefs(context).getString(farm, null);
        if (json == null) return null;
        try {
            return GSON.fromJson(json, Reperes.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** Relit les repères de la ferme active (silencieux en cas d'échec). */
    public static void rafraichir(@NonNull Context context) {
        Context app = context.getApplicationContext();
        String farm = SessionManager.activeFarmId(app);
        if (farm == null || !NetworkUtils.isOnline(app)) return;
        ApiClient.dataApi(app).getReperesMontants().enqueue(new Callback<com.mobile.diafarms.network.dto.ApiEnvelope<JsonObject>>() {
            @Override
            public void onResponse(@NonNull Call<com.mobile.diafarms.network.dto.ApiEnvelope<JsonObject>> call,
                                   @NonNull Response<com.mobile.diafarms.network.dto.ApiEnvelope<JsonObject>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().getData() == null) return;
                prefs(app).edit().putString(farm, GSON.toJson(response.body().getData())).apply();
            }

            @Override
            public void onFailure(@NonNull Call<com.mobile.diafarms.network.dto.ApiEnvelope<JsonObject>> call, @NonNull Throwable t) { }
        });
    }
}
