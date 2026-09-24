package com.mobile.diafarms.network.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Miroir de SessionPeseeSyncRequest côté backend (POST pesees/sessions/sync) : l'état
 * COMPLET d'une session de pesée (session + toutes ses pesées, annulées comprises).
 * Stocké tel quel dans saisies_locales.payload_json (une seule ligne locale par
 * session, voir SaisieType.PESEE_SESSION) et renvoyé en entier à chaque
 * synchronisation : le serveur est idempotent (uniqueId générés sur le téléphone),
 * renvoyer le même instantané ne crée jamais de doublon.
 */
public class SessionPeseeSyncRequest {
    public static final String STATUT_EN_COURS = "EN_COURS";
    public static final String STATUT_TERMINEE = "TERMINEE";

    public String uniqueId;
    public String projetUniqueId;
    public Integer nombreParDefaut;
    public String dateDebut;   // "yyyy-MM-dd'T'HH:mm:ss" (heure locale)
    public String statut;      // EN_COURS | TERMINEE
    public String dateFin;     // null tant que EN_COURS
    public List<Pesee> pesees = new ArrayList<>();

    public static class Pesee {
        public String uniqueId;
        public Integer nombreSujets;
        public Double poidsKg;
        public String dateHeure; // "yyyy-MM-dd'T'HH:mm:ss"
        public Boolean annulee;

        public boolean isAnnulee() { return Boolean.TRUE.equals(annulee); }
    }

    public boolean isTerminee() { return STATUT_TERMINEE.equals(statut); }

    /** Pesées prises en compte dans les totaux (non annulées). */
    public List<Pesee> peseesActives() {
        List<Pesee> actives = new ArrayList<>();
        if (pesees != null) {
            for (Pesee p : pesees) if (!p.isAnnulee()) actives.add(p);
        }
        return actives;
    }

    public int totalSujets() {
        int total = 0;
        for (Pesee p : peseesActives()) total += p.nombreSujets != null ? p.nombreSujets : 0;
        return total;
    }

    public double poidsTotalKg() {
        double total = 0;
        for (Pesee p : peseesActives()) total += p.poidsKg != null ? p.poidsKg : 0;
        return total;
    }

    /** Poids total / total sujets (pesées non annulées), 0 si aucun sujet. */
    public double poidsMoyenKg() {
        int sujets = totalSujets();
        return sujets > 0 ? poidsTotalKg() / sujets : 0;
    }
}
