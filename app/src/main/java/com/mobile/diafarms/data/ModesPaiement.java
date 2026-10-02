package com.mobile.diafarms.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mobile.diafarms.network.dto.ModePaiementFermeResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Modes de paiement proposés dans les formulaires : ceux cochés par la ferme (cache local,
 * voir CachePrefetcher.CACHE_MODES_PAIEMENT), dans leur ordre. Sans cache : les 7 valeurs
 * historiques. On envoie toujours le `code` : le serveur garde une valeur historique telle
 * quelle et range tout autre code actif en AUTRE + libellé. Une saisie en attente faite
 * avec une valeur historique que la ferme a décochée depuis reste lisible et repart
 * inchangée (le serveur accepte toujours les valeurs historiques).
 */
public final class ModesPaiement {

    public static final String[] CODES_HISTORIQUES =
            {"ESPECES", "ORANGE_MONEY", "MOOV_MONEY", "WAVE", "VIREMENT", "CHEQUE", "AUTRE"};
    public static final String[] LIBELLES_HISTORIQUES =
            {"Espèces", "Orange Money", "Moov Money", "Wave", "Virement", "Chèque", "Autre"};

    private static final Gson gson = new Gson();

    private final List<String> codes = new ArrayList<>();
    private final List<String> libelles = new ArrayList<>();
    // Modes d'une saisie en attente qui ne sont plus dans la liste (décochés depuis) :
    // affichés tels quels et renvoyés avec leur code d'origine.
    private final java.util.Map<String, String> horsListe = new java.util.HashMap<>();

    private ModesPaiement() { }

    public static ModesPaiement de(LocalDatabase localDatabase) {
        ModesPaiement m = new ModesPaiement();
        try {
            String json = localDatabase != null ? localDatabase.getCache(CachePrefetcher.CACHE_MODES_PAIEMENT) : null;
            if (json != null) {
                List<ModePaiementFermeResponse> liste = gson.fromJson(json,
                        new TypeToken<List<ModePaiementFermeResponse>>() { }.getType());
                if (liste != null) {
                    for (ModePaiementFermeResponse r : liste) {
                        if (r == null || r.code == null || r.code.trim().isEmpty()) continue;
                        if (Boolean.FALSE.equals(r.actif)) continue;
                        String libelle = r.libelle != null && !r.libelle.trim().isEmpty() ? r.libelle.trim() : libelleHistorique(r.code);
                        if (libelle == null) libelle = r.code;
                        if (m.libelles.contains(libelle)) continue;
                        m.codes.add(r.code.trim());
                        m.libelles.add(libelle);
                    }
                }
            }
        } catch (Exception ignored) {
            m.codes.clear();
            m.libelles.clear();
        }
        if (m.codes.isEmpty()) {
            Collections.addAll(m.codes, CODES_HISTORIQUES);
            Collections.addAll(m.libelles, LIBELLES_HISTORIQUES);
        }
        return m;
    }

    /** Libellés à proposer, dans l'ordre de la ferme. */
    public String[] libelles() {
        return libelles.toArray(new String[0]);
    }

    /** Premier mode de la liste (présélection par défaut). */
    public String premierLibelle() {
        return libelles.get(0);
    }

    /** Code à envoyer pour un libellé affiché ; null si inconnu. */
    public String codePour(String libelle) {
        if (libelle == null) return null;
        int i = libelles.indexOf(libelle);
        if (i >= 0) return codes.get(i);
        if (horsListe.containsKey(libelle)) return horsListe.get(libelle);
        for (int k = 0; k < LIBELLES_HISTORIQUES.length; k++) {
            if (LIBELLES_HISTORIQUES[k].equals(libelle)) return CODES_HISTORIQUES[k];
        }
        return null;
    }

    /** Libellé à afficher pour un code (actif, historique décoché, ou inconnu = le code). */
    public String libellePour(String code) {
        if (code == null) return null;
        int i = codes.indexOf(code);
        if (i >= 0) return libelles.get(i);
        String h = libelleHistorique(code);
        return h != null ? h : code;
    }

    /** Libellé à afficher pour le code d'une saisie existante ; s'il n'est plus proposé,
     * il est retenu pour que codePour(libellé) redonne ce même code. */
    public String retenir(String code) {
        String libelle = libellePour(code);
        if (code != null && !contient(code) && !libelles.contains(libelle)) horsListe.put(libelle, code);
        return libelle;
    }

    /** Vrai si ce code est proposé tel quel dans la liste de la ferme. */
    public boolean contient(String code) {
        return code != null && codes.contains(code);
    }

    private static String libelleHistorique(String code) {
        for (int k = 0; k < CODES_HISTORIQUES.length; k++) {
            if (CODES_HISTORIQUES[k].equals(code)) return LIBELLES_HISTORIQUES[k];
        }
        return null;
    }
}
