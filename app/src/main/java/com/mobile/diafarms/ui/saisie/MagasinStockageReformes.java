package com.mobile.diafarms.ui.saisie;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mobile.diafarms.data.CachePrefetcher;
import com.mobile.diafarms.data.LocalDatabase;
import com.mobile.diafarms.network.dto.MagasinSelectResponse;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Réformés : même chemin que les œufs (règle du serveur, ReformeStockage). Une réforme
 * va dans un magasin de STOCKAGE, puis passe automatiquement au point de vente par défaut
 * de ce magasin s'il en a un ; sinon elle y reste jusqu'à un transfert manuel.
 * Sans magasin choisi (ancienne saisie) : le seul magasin de stockage de la ferme.
 * Calculé hors ligne depuis la liste des magasins de stockage en cache.
 */
public final class MagasinStockageReformes {

    private static final Gson gson = new Gson();

    private MagasinStockageReformes() {}

    public static List<MagasinSelectResponse> stockages(LocalDatabase db) {
        String json = db.getCache(CachePrefetcher.CACHE_MAGASINS_STOCKAGE_SELECT);
        if (json == null) return new ArrayList<>();
        try {
            Type t = new TypeToken<List<MagasinSelectResponse>>() {}.getType();
            List<MagasinSelectResponse> l = gson.fromJson(json, t);
            return l != null ? l : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** uniqueId du magasin de stockage présélectionné : le seul de la ferme, sinon null. */
    public static String parDefaut(List<MagasinSelectResponse> stockages) {
        return stockages != null && stockages.size() == 1 ? stockages.get(0).getUniqueId() : null;
    }

    /** Point de vente où passera automatiquement une réforme envoyée avec ce magasin de
     * stockage (null = défaut), ou null si les réformés restent au magasin de stockage. */
    public static String pointDeVenteAuto(String stockageChoisi, LocalDatabase db) {
        List<MagasinSelectResponse> stockages = stockages(db);
        String id = stockageChoisi != null && !stockageChoisi.isEmpty() ? stockageChoisi : parDefaut(stockages);
        if (id == null) return null;
        for (MagasinSelectResponse s : stockages) {
            if (id.equals(s.getUniqueId())) return s.getMagasinVenteParDefautUniqueId();
        }
        return null;
    }
}
