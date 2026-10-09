package com.mobile.diafarms.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

// Une vente (œufs, réformes, fientes ou autre) se fait dans Ventes : le stock et le compte
// du client suivent. Une entrée d'argent qui décrit une vente est refusée, comme au
// serveur (voir VenteHorsVentes côté serveur, mêmes mots).
public final class VenteHorsVentes {

    public static final String MESSAGE =
            "La vente d'œufs, de réformes, de fientes ou de tout autre produit se fait dans Ventes, "
            + "pas dans les entrées d'argent. Ainsi le stock et le compte du client restent justes, "
            + "et l'argent de la vente est compté tout seul.";

    private static final Pattern MOTS_VENTE = Pattern.compile(
            "\\b(ventes?|vendus?|vendues?|vendre|vend|vendons|vendez"
            + "|oeufs?|alveoles?|plateaux?|reformes?|reformees?"
            + "|poules?|poulets?|pondeuses?|coqs?|fientes?|fumier)\\b");

    private VenteHorsVentes() {
    }

    public static boolean contientMotVente(String... textes) {
        for (String texte : textes) {
            if (texte == null || texte.trim().isEmpty()) continue;
            String t = Normalizer.normalize(texte.replace("œ", "oe").replace("Œ", "OE"), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "")
                    .toLowerCase(Locale.ROOT);
            if (MOTS_VENTE.matcher(t).find()) return true;
        }
        return false;
    }
}
