package com.mobile.diafarms.ui.saisie;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mobile.diafarms.data.CachePrefetcher;
import com.mobile.diafarms.data.LocalDatabase;
import com.mobile.diafarms.models.SaisieLocale;
import com.mobile.diafarms.models.SaisieType;
import com.mobile.diafarms.network.dto.BatimentSelectResponse;
import com.mobile.diafarms.network.dto.SiteSelectResponse;
import com.mobile.diafarms.network.dto.TransactionCreateRequest;
import com.mobile.diafarms.network.dto.VenteDiverseCreateRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * « Cette dépense concerne » d'une saisie (sortie / entrée d'argent, vente diverse) :
 * Projet, Site ou Ferme. Une saisie d'une version antérieure (commun, projets concernés,
 * site / poulailler facultatifs) est ramenée au choix le plus proche, comme le serveur la
 * normalise ; ce qui ne se ramène à aucun choix unique (plusieurs projets concernés, ou un
 * poulailler seul sur une dépense commune) reste ANCIEN et repart tel quel.
 */
public final class RattachementSaisie {
    public static final String PROJET = TransactionCreateRequest.RATTACHEMENT_PROJET;
    public static final String SITE = TransactionCreateRequest.RATTACHEMENT_SITE;
    public static final String FERME = TransactionCreateRequest.RATTACHEMENT_FERME;
    public static final String ANCIEN = "ANCIEN";

    public final String choix;
    public final String projetUniqueId;
    public final String siteUniqueId;
    public final String batimentUniqueId;
    public final List<String> projetsConcernes;

    private RattachementSaisie(String choix, String projet, String site, String batiment, List<String> projets) {
        this.choix = choix;
        this.projetUniqueId = projet;
        this.siteUniqueId = site;
        this.batimentUniqueId = batiment;
        this.projetsConcernes = projets != null ? projets : new ArrayList<>();
    }

    private static boolean vide(String s) {
        return s == null || s.trim().isEmpty();
    }

    public static RattachementSaisie depuis(TransactionCreateRequest r) {
        if (r == null) return new RattachementSaisie(FERME, null, null, null, null);
        if (PROJET.equals(r.rattachement)) return new RattachementSaisie(PROJET, r.projetUniqueId, null, r.batimentUniqueId, null);
        if (SITE.equals(r.rattachement)) return new RattachementSaisie(SITE, null, r.siteUniqueId, null, null);
        if (FERME.equals(r.rattachement)) return new RattachementSaisie(FERME, null, null, null, null);
        // Ancien format : commun absent = commune, comme le serveur.
        boolean commun = r.commun == null || r.commun;
        if (!commun) return new RattachementSaisie(PROJET, r.projetUniqueId, null, r.batimentUniqueId, null);
        List<String> projets = r.projetsConcernesUniqueIds;
        if (projets != null && projets.size() == 1) return new RattachementSaisie(PROJET, projets.get(0), null, r.batimentUniqueId, null);
        if (projets != null && projets.size() > 1) return new RattachementSaisie(ANCIEN, null, r.siteUniqueId, r.batimentUniqueId, projets);
        if (!vide(r.siteUniqueId)) return new RattachementSaisie(SITE, null, r.siteUniqueId, null, null);
        if (!vide(r.batimentUniqueId)) return new RattachementSaisie(ANCIEN, null, null, r.batimentUniqueId, null);
        return new RattachementSaisie(FERME, null, null, null, null);
    }

    public static RattachementSaisie depuis(VenteDiverseCreateRequest v) {
        if (v == null) return new RattachementSaisie(FERME, null, null, null, null);
        if (FERME.equals(v.rattachement)) return new RattachementSaisie(FERME, null, null, null, null);
        if (PROJET.equals(v.rattachement) || !vide(v.projetUniqueId)) {
            return new RattachementSaisie(PROJET, v.projetUniqueId, null, null, null);
        }
        return new RattachementSaisie(FERME, null, null, null, null);
    }

    /** Rattachement d'une saisie locale, ou null si son type n'en a pas. */
    public static RattachementSaisie depuis(SaisieLocale s, Gson gson) {
        try {
            if (s.getType() == SaisieType.TRANSACTION_ENTREE || s.getType() == SaisieType.TRANSACTION_SORTIE) {
                return depuis(gson.fromJson(s.getPayloadJson(), TransactionCreateRequest.class));
            }
            if (s.getType() == SaisieType.VENTE_FIENTES || s.getType() == SaisieType.VENTE_AUTRE) {
                return depuis(gson.fromJson(s.getPayloadJson(), VenteDiverseCreateRequest.class));
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** Libellé court pour « Mes saisies » : « Projet X », « Site Y » ou « Ferme ». */
    public static String libelle(SaisieLocale s, LocalDatabase db, Gson gson) {
        RattachementSaisie r = depuis(s, gson);
        if (r == null) return null;
        switch (r.choix) {
            case PROJET:
                return "Projet" + (s.getProjetLabel() != null ? " " + s.getProjetLabel() : "");
            case SITE: {
                String nom = null;
                for (SiteSelectResponse x : lire(db, gson, CachePrefetcher.CACHE_SITES_SELECT, SiteSelectResponse.class)) {
                    if (r.siteUniqueId != null && r.siteUniqueId.equals(x.getUniqueId())) nom = x.getNom();
                }
                return "Site" + (nom != null ? " " + nom : "");
            }
            case ANCIEN: {
                if (r.projetsConcernes.size() > 1) return r.projetsConcernes.size() + " projets";
                String nom = null;
                for (BatimentSelectResponse x : lire(db, gson, CachePrefetcher.CACHE_BATIMENTS_TOUS, BatimentSelectResponse.class)) {
                    if (r.batimentUniqueId != null && r.batimentUniqueId.equals(x.getUniqueId())) nom = x.getNom();
                }
                return "Poulailler" + (nom != null ? " " + nom : "");
            }
            default:
                return "Ferme";
        }
    }

    private static <T> List<T> lire(LocalDatabase db, Gson gson, String cle, Class<T> clazz) {
        try {
            String json = db.getCache(cle);
            if (json == null) return new ArrayList<>();
            List<T> l = gson.fromJson(json, TypeToken.getParameterized(List.class, clazz).getType());
            return l != null ? l : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
