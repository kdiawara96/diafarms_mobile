package com.mobile.diafarms.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mobile.diafarms.models.SaisieLocale;
import com.mobile.diafarms.models.SaisieType;
import com.mobile.diafarms.network.dto.CommandeResponse;
import com.mobile.diafarms.network.dto.LivraisonCommandeRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Commandes ouvertes telles que le téléphone les connaît : dernière liste du serveur en
 * cache (CachePrefetcher.CACHE_COMMANDES_OUVERTES) + livraisons saisies ici et pas encore
 * envoyées. Même principe que les autres contrôles hors ligne (voir SaisieFormActivity).
 */
public final class CommandesHorsLigne {

    private static final Gson gson = new Gson();

    private CommandesHorsLigne() {
    }

    public static List<CommandeResponse> lire(LocalDatabase db) {
        String json = db.getCache(CachePrefetcher.CACHE_COMMANDES_OUVERTES);
        if (json == null) return new ArrayList<>();
        try {
            List<CommandeResponse> l = gson.fromJson(json, new TypeToken<List<CommandeResponse>>() {}.getType());
            return l != null ? l : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static CommandeResponse trouver(LocalDatabase db, String uniqueId) {
        if (uniqueId == null) return null;
        for (CommandeResponse c : lire(db)) {
            if (uniqueId.equals(c.uniqueId)) return c;
        }
        return null;
    }

    /** Livraisons de cette commande saisies sur ce téléphone et qui partiront (voir
     * SaisieLocale.seraRenvoyee), sauf exclureLocalId (la saisie en cours de modification). */
    public static int quantiteEnAttente(LocalDatabase db, String commandeUniqueId, String exclureLocalId) {
        int total = 0;
        for (LivraisonCommandeRequest r : livraisonsEnAttente(db, exclureLocalId)) {
            if (commandeUniqueId != null && commandeUniqueId.equals(r.commandeUniqueId) && r.quantite != null) {
                total += r.quantite;
            }
        }
        return total;
    }

    /** Livraisons en attente qui puisent dans ce magasin (œufs bons ou sujets réformés). */
    public static int quantiteEnAttenteMagasin(LocalDatabase db, String magasinUniqueId, boolean oeufs, String exclureLocalId) {
        int total = 0;
        for (LivraisonCommandeRequest r : livraisonsEnAttente(db, exclureLocalId)) {
            boolean rOeufs = !"REFORME".equals(r.type);
            if (magasinUniqueId != null && magasinUniqueId.equals(r.magasinUniqueId) && rOeufs == oeufs && r.quantite != null) {
                total += r.quantite;
            }
        }
        return total;
    }

    public static List<LivraisonCommandeRequest> livraisonsEnAttente(LocalDatabase db, String exclureLocalId) {
        List<LivraisonCommandeRequest> res = new ArrayList<>();
        for (SaisieLocale s : db.getSaisiesPourControles(SaisieType.LIVRAISON_COMMANDE)) {
            if (!s.seraRenvoyee()) continue;
            if (exclureLocalId != null && exclureLocalId.equals(s.getLocalId())) continue;
            try {
                LivraisonCommandeRequest r = gson.fromJson(s.getPayloadJson(), LivraisonCommandeRequest.class);
                if (r != null) res.add(r);
            } catch (Exception ignored) {
                // saisie illisible : ignorée pour le contrôle
            }
        }
        return res;
    }
}
