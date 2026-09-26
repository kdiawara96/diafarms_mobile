package com.mobile.diafarms.data;

import com.google.gson.Gson;
import com.mobile.diafarms.models.SaisieLocale;
import com.mobile.diafarms.models.SaisieType;
import com.mobile.diafarms.network.dto.DernierPoidsMoyenResponse;
import com.mobile.diafarms.network.dto.ProjetSelectResponse;
import com.mobile.diafarms.network.dto.SessionPeseeSyncRequest;

import java.util.HashSet;
import java.util.Set;

/**
 * Dernier poids moyen par sujet connu pour un projet, pour estimer le poids d'une vente
 * ou d'une commande de réformes au kilo, SANS réseau : d'abord les sessions de pesée
 * TERMINEE du téléphone (saisies_locales PESEE_SESSION, envoyées ou non), puis la valeur
 * serveur mise en cache par CachePrefetcher (GET /pesees/dernier-poids-moyen). La plus
 * récente par date de fin l'emporte ; à date égale, la session locale.
 *
 * Simple estimation affichée et proposée : le poids réellement pesé à la vente reste
 * seul à compter.
 */
public final class DernierePeseeEstimation {

    public final String projetUniqueId;
    public final double poidsMoyenKg;
    /** "yyyy-MM-dd'T'HH:mm:ss" (ou au moins "yyyy-MM-dd"). */
    public final String dateFin;

    private DernierePeseeEstimation(String projetUniqueId, double poidsMoyenKg, String dateFin) {
        this.projetUniqueId = projetUniqueId;
        this.poidsMoyenKg = poidsMoyenKg;
        this.dateFin = dateFin;
    }

    private static final Gson GSON = new Gson();

    /** projetUniqueId null : aucun projet choisi à l'accueil, on prend la pesée la plus
     * récente parmi tous les projets connus (sessions locales + projets en cache). */
    public static DernierePeseeEstimation trouver(LocalDatabase db, String projetUniqueId) {
        DernierePeseeEstimation meilleure = null;

        for (SaisieLocale s : db.getSaisiesByType(SaisieType.PESEE_SESSION)) {
            SessionPeseeSyncRequest session;
            try {
                session = GSON.fromJson(s.getPayloadJson(), SessionPeseeSyncRequest.class);
            } catch (Exception e) {
                continue;
            }
            if (session == null || !session.isTerminee()) continue;
            String projet = session.projetUniqueId != null ? session.projetUniqueId : s.getProjetUniqueId();
            if (projetUniqueId != null && !projetUniqueId.equals(projet)) continue;
            double moyen = session.poidsMoyenKg();
            if (moyen <= 0) continue;
            String date = session.dateFin != null ? session.dateFin : session.dateDebut;
            if (date == null) continue;
            DernierePeseeEstimation e = new DernierePeseeEstimation(projet, moyen, date);
            if (meilleure == null || date.compareTo(meilleure.dateFin) > 0) meilleure = e;
        }

        Set<String> projets = new HashSet<>();
        if (projetUniqueId != null) {
            projets.add(projetUniqueId);
        } else {
            try {
                String json = db.getCache(CachePrefetcher.CACHE_PROJETS_SELECT);
                ProjetSelectResponse[] liste = json != null ? GSON.fromJson(json, ProjetSelectResponse[].class) : null;
                if (liste != null) {
                    for (ProjetSelectResponse p : liste) if (p != null && p.getUniqueId() != null) projets.add(p.getUniqueId());
                }
            } catch (Exception ignored) {
            }
        }
        for (String projet : projets) {
            DernierPoidsMoyenResponse cache;
            try {
                String json = db.getCache(CachePrefetcher.CACHE_DERNIER_POIDS_MOYEN_PREFIX + projet);
                cache = json != null ? GSON.fromJson(json, DernierPoidsMoyenResponse.class) : null;
            } catch (Exception e) {
                cache = null;
            }
            if (cache == null || cache.poidsMoyenKg == null || cache.poidsMoyenKg <= 0 || cache.dateFin == null) continue;
            // Strictement plus récente seulement : à date égale, la session locale gagne.
            if (meilleure == null || cache.dateFin.compareTo(meilleure.dateFin) > 0) {
                meilleure = new DernierePeseeEstimation(projet, cache.poidsMoyenKg, cache.dateFin);
            }
        }
        return meilleure;
    }

    /** "25/09" depuis "2026-09-25T..." ; chaîne vide si illisible. */
    public String dateCourte() {
        if (dateFin == null || dateFin.length() < 10) return "";
        return dateFin.substring(8, 10) + "/" + dateFin.substring(5, 7);
    }
}
