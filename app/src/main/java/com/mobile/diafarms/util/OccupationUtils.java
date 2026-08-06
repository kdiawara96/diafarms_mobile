package com.mobile.diafarms.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Une occupation bâtiment-projet reste active tant que sa dateSortie n'est pas
 * encore passée — elle peut être une date de fin PRÉVUE dans le futur, pas
 * seulement "en cours indéfiniment" (dateSortie null). Même règle que
 * getActiveBatiments côté web (src/utils/batiments.ts) : avant ce correctif, le
 * mobile traitait toute dateSortie non-null comme "déjà terminée", faisant
 * disparaître un bâtiment pourtant toujours lié tant que sa date de sortie
 * prévue n'était pas encore arrivée.
 */
public class OccupationUtils {

    private static final SimpleDateFormat ISO_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE);

    public static boolean estActive(String dateSortieIso) {
        if (dateSortieIso == null || dateSortieIso.isEmpty()) return true;
        String todayIso = ISO_FORMAT.format(new Date());
        return dateSortieIso.compareTo(todayIso) >= 0;
    }

    private OccupationUtils() {
    }
}
