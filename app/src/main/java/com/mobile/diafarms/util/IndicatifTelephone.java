package com.mobile.diafarms.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Indicatifs des pays pour les numéros de téléphone (même liste que l'application web,
 * components/ChampTelephone.tsx). Le numéro reste un seul texte « +226 70 12 34 56 » :
 * le serveur et les autres écrans n'ont rien à changer.
 */
public final class IndicatifTelephone {

    public static final class Pays {
        public final String iso, nom, indicatif;
        Pays(String iso, String nom, String indicatif) { this.iso = iso; this.nom = nom; this.indicatif = indicatif; }

        /** Libellé de la liste : drapeau, indicatif et nom. */
        public String libelle() { return drapeau(iso) + " +" + indicatif + " " + nom; }
    }

    public static final List<Pays> PAYS = Arrays.asList(
            new Pays("ML", "Mali", "223"), new Pays("BF", "Burkina Faso", "226"),
            new Pays("CI", "Côte d'Ivoire", "225"), new Pays("SN", "Sénégal", "221"),
            new Pays("GN", "Guinée", "224"), new Pays("NE", "Niger", "227"),
            new Pays("TG", "Togo", "228"), new Pays("BJ", "Bénin", "229"),
            new Pays("TD", "Tchad", "235"), new Pays("CM", "Cameroun", "237"),
            new Pays("MR", "Mauritanie", "222"), new Pays("GH", "Ghana", "233"),
            new Pays("NG", "Nigeria", "234"), new Pays("GM", "Gambie", "220"),
            new Pays("GW", "Guinée-Bissau", "245"), new Pays("SL", "Sierra Leone", "232"),
            new Pays("LR", "Liberia", "231"), new Pays("CV", "Cap-Vert", "238"),
            new Pays("CF", "Centrafrique", "236"), new Pays("GA", "Gabon", "241"),
            new Pays("CG", "Congo", "242"), new Pays("CD", "RD Congo", "243"),
            new Pays("RW", "Rwanda", "250"), new Pays("KE", "Kenya", "254"),
            new Pays("MA", "Maroc", "212"), new Pays("DZ", "Algérie", "213"),
            new Pays("TN", "Tunisie", "216"), new Pays("EG", "Égypte", "20"),
            new Pays("FR", "France", "33"), new Pays("BE", "Belgique", "32"),
            new Pays("CH", "Suisse", "41"), new Pays("ES", "Espagne", "34"),
            new Pays("IT", "Italie", "39"), new Pays("DE", "Allemagne", "49"),
            new Pays("GB", "Royaume-Uni", "44"), new Pays("US", "États-Unis, Canada", "1"),
            new Pays("AE", "Émirats arabes unis", "971"), new Pays("SA", "Arabie saoudite", "966"),
            new Pays("CN", "Chine", "86"));

    public static final String INDICATIF_DEFAUT = "223";

    private IndicatifTelephone() {}

    static String drapeau(String iso) {
        StringBuilder b = new StringBuilder();
        for (char c : iso.toUpperCase().toCharArray()) b.appendCodePoint(0x1F1E6 + c - 'A');
        return b.toString();
    }

    public static List<String> libelles() {
        List<String> l = new ArrayList<>();
        for (Pays p : PAYS) l.add(p.libelle());
        return l;
    }

    public static int position(String indicatif) {
        for (int i = 0; i < PAYS.size(); i++) if (PAYS.get(i).indicatif.equals(indicatif)) return i;
        return 0;
    }

    /** { indicatif, numéro sans l'indicatif } ; sans « + », l'indicatif par défaut. */
    public static String[] decouper(String valeur, String indicatifDefaut) {
        String v = valeur == null ? "" : valeur.trim();
        if (v.startsWith("00")) v = "+" + v.substring(2);
        if (!v.startsWith("+")) return new String[] {indicatifDefaut, v};
        String chiffres = v.substring(1).replace(" ", "");
        List<Pays> parLongueur = new ArrayList<>(PAYS);
        parLongueur.sort(Comparator.comparingInt((Pays p) -> p.indicatif.length()).reversed());
        for (Pays p : parLongueur) {
            if (chiffres.startsWith(p.indicatif)) {
                String reste = v.substring(1).trim();
                int aRetirer = p.indicatif.length(), i = 0;
                while (aRetirer > 0 && i < reste.length()) {
                    if (Character.isDigit(reste.charAt(i))) aRetirer--;
                    i++;
                }
                return new String[] {p.indicatif, reste.substring(i).trim()};
            }
        }
        return new String[] {indicatifDefaut, v.substring(1).trim()};
    }

    /** « +226 70 12 34 56 », ou "" si le numéro est vide. */
    public static String assembler(String indicatif, String local) {
        String l = local == null ? "" : local.trim();
        return l.isEmpty() ? "" : "+" + indicatif + " " + l;
    }

    /** Chiffres seuls au format international (pour comparer deux numéros écrits différemment). */
    public static String chiffres(String numero) {
        if (numero == null) return "";
        String c = numero.replaceAll("[^0-9]", "");
        if (c.startsWith("00")) c = c.substring(2);
        if (c.length() == 8) c = INDICATIF_DEFAUT + c;
        return c;
    }
}
