package com.mobile.diafarms.util;

import java.util.Locale;

/**
 * Conversion œufs <-> alvéoles (plateau de 30 œufs, standard du marché) — pure
 * facilité de saisie/affichage côté client : la base ne connaît que le nombre
 * d'œufs (voir VenteOeufs/CollecteOeufs côté backend), jamais l'alvéole.
 */
public class AlveoleUtils {

    public static final int OEUFS_PAR_ALVEOLE = 30;

    public static int alveolesToOeufs(int alveoles) {
        return alveoles * OEUFS_PAR_ALVEOLE;
    }

    /** Équivalent en alvéoles pour l'affichage uniquement (arrondi à l'entier
     * inférieur : un reste d'œufs ne fait pas une alvéole complète). */
    public static int oeufsToAlveolesApprox(int oeufs) {
        return oeufs / OEUFS_PAR_ALVEOLE;
    }

    /** "1 500 œuf(s) (50 alvéole(s))" — reste non-multiple de 30 signalé à part
     * ("+ 5 œufs") plutôt que silencieusement arrondi. */
    public static String formatOeufsAvecAlveoles(int oeufs) {
        int alveoles = oeufsToAlveolesApprox(oeufs);
        int reste = oeufs - alveolesToOeufs(alveoles);
        String suffixeReste = reste > 0 ? String.format(Locale.FRANCE, " + %d", reste) : "";
        return String.format(Locale.FRANCE, "%d œuf(s) (%d alvéole(s)%s)", oeufs, alveoles, suffixeReste);
    }

    private AlveoleUtils() {
    }
}
