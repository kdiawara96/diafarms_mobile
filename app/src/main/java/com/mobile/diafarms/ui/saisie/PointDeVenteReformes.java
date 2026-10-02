package com.mobile.diafarms.ui.saisie;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mobile.diafarms.data.CachePrefetcher;
import com.mobile.diafarms.data.LocalDatabase;
import com.mobile.diafarms.network.dto.MagasinSelectResponse;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Point de vente des réformés, même règle que le serveur (ReformePointDeVente) : une
 * réforme place automatiquement ses sujets dans un point de vente. Sans choix explicite :
 * le seul point de vente de la ferme, sinon celui que tous les magasins de stockage
 * désignent comme point de vente par défaut (s'ils en désignent un seul), sinon aucun
 * (le serveur refuse alors avec « Choisissez le point de vente des réformés »).
 * Calculé hors ligne depuis les listes de magasins en cache.
 */
public final class PointDeVenteReformes {

    private static final Gson gson = new Gson();

    private PointDeVenteReformes() {}

    public static List<MagasinSelectResponse> lireCache(LocalDatabase db, String cle) {
        String json = db.getCache(cle);
        if (json == null) return null;
        try {
            Type t = new TypeToken<List<MagasinSelectResponse>>() {}.getType();
            List<MagasinSelectResponse> l = gson.fromJson(json, t);
            return l;
        } catch (Exception e) {
            return null;
        }
    }

    public static List<MagasinSelectResponse> pointsDeVente(LocalDatabase db) {
        List<MagasinSelectResponse> l = lireCache(db, CachePrefetcher.CACHE_MAGASINS_SELECT);
        return l != null ? l : new ArrayList<>();
    }

    public static List<MagasinSelectResponse> stockages(LocalDatabase db) {
        List<MagasinSelectResponse> l = lireCache(db, CachePrefetcher.CACHE_MAGASINS_STOCKAGE_SELECT);
        return l != null ? l : new ArrayList<>();
    }

    /** uniqueId du point de vente par défaut, ou null s'il n'y en a pas. */
    public static String parDefaut(List<MagasinSelectResponse> ventes, List<MagasinSelectResponse> stockages) {
        if (ventes == null || ventes.isEmpty()) return null;
        if (ventes.size() == 1) return ventes.get(0).getUniqueId();
        Set<String> ids = new HashSet<>();
        for (MagasinSelectResponse v : ventes) ids.add(v.getUniqueId());
        Set<String> designes = new HashSet<>();
        if (stockages != null) {
            for (MagasinSelectResponse s : stockages) {
                String d = s.getMagasinVenteParDefautUniqueId();
                if (d != null && ids.contains(d)) designes.add(d);
            }
        }
        return designes.size() == 1 ? designes.iterator().next() : null;
    }

    /** Point de vente où ira une réforme : celui choisi, sinon celui par défaut. */
    public static String destination(String choisi, LocalDatabase db) {
        if (choisi != null && !choisi.isEmpty()) return choisi;
        return parDefaut(pointsDeVente(db), stockages(db));
    }
}
