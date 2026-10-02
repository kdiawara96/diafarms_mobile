package com.mobile.diafarms.util;

import android.content.Context;

import com.google.gson.Gson;
import com.mobile.diafarms.data.CachePrefetcher;
import com.mobile.diafarms.data.LocalDatabase;
import com.mobile.diafarms.network.dto.DeviseFermeResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Devise de la ferme du compte connecté (cache local, voir CachePrefetcher.CACHE_DEVISE_FERME) :
 * seul endroit qui affiche un montant ou l'arrondit. La devise n'est qu'une unité
 * d'affichage et d'arrondi, aucun montant n'est jamais converti. Sans cache (premier
 * démarrage hors ligne) : XOF, « FCFA », 0 décimale, comme avant.
 */
public final class Monnaie {

    public final String code;
    public final String symbole;
    public final int decimales;

    private static final Monnaie DEFAUT = new Monnaie("XOF", "FCFA", 0);
    private static final Gson gson = new Gson();

    private Monnaie(String code, String symbole, int decimales) {
        this.code = code;
        this.symbole = symbole;
        this.decimales = decimales;
    }

    public static Monnaie defaut() {
        return DEFAUT;
    }

    /** Devise en cache pour le compte de cette base (le cache est rangé par compte). */
    public static Monnaie de(LocalDatabase localDatabase) {
        try {
            String json = localDatabase != null ? localDatabase.getCache(CachePrefetcher.CACHE_DEVISE_FERME) : null;
            if (json == null) return DEFAUT;
            DeviseFermeResponse d = gson.fromJson(json, DeviseFermeResponse.class);
            if (d == null || d.devise == null || d.devise.trim().isEmpty()) return DEFAUT;
            String symbole = d.symbole != null && !d.symbole.trim().isEmpty() ? d.symbole.trim() : d.devise.trim();
            int dec = d.decimales != null ? Math.max(0, Math.min(3, d.decimales)) : 0;
            return new Monnaie(d.devise.trim(), symbole, dec);
        } catch (Exception e) {
            return DEFAUT;
        }
    }

    public static Monnaie de(Context context) {
        return de(new LocalDatabase(context));
    }

    /** Nombre groupé à la française avec exactement `decimales` décimales, sans symbole. */
    public String nombre(double valeur) {
        return String.format(Locale.FRANCE, "%,." + decimales + "f", valeur);
    }

    /** « 12 500 FCFA », « 12 500,50 € ». null vaut 0. */
    public String montant(Double valeur) {
        return nombre(valeur != null ? valeur : 0.0) + " " + symbole;
    }

    /** Prix unitaire : jusqu'à 2 décimales sans zéros inutiles (« 62,5 FCFA », « 1,25 € »). */
    public String prixUnitaire(Double valeur) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.FRANCE);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(2);
        return nf.format(valeur != null ? valeur : 0.0) + " " + symbole;
    }

    /** Libellé de champ ou texte écrit pour le franc CFA : « Montant (FCFA) » devient « Montant (€) ». */
    public String libelle(String texte) {
        if (texte == null || "FCFA".equals(symbole)) return texte;
        return texte.replace("FCFA", symbole);
    }

    /** Arrondi d'un montant à `decimales` décimales, demi vers le haut (comme le serveur).
     * 0 décimale : Math.round, comportement XOF inchangé. */
    public double arrondi(double valeur) {
        if (decimales == 0) return (double) Math.round(valeur);
        return BigDecimal.valueOf(valeur).setScale(decimales, RoundingMode.HALF_UP).doubleValue();
    }

    public Double arrondi(Double valeur) {
        return valeur != null ? arrondi(valeur.doubleValue()) : null;
    }

    /** Montant arrondi écrit pour un champ de saisie (point décimal, sans séparateur). */
    public String saisie(double valeur) {
        if (decimales == 0) return String.valueOf(Math.round(valeur));
        BigDecimal b = BigDecimal.valueOf(arrondi(valeur)).setScale(decimales, RoundingMode.HALF_UP).stripTrailingZeros();
        return b.scale() < 0 ? b.setScale(0).toPlainString() : b.toPlainString();
    }

    public boolean accepteDecimales() {
        return decimales > 0;
    }
}
