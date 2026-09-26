package com.mobile.diafarms.network.dto;

import java.util.List;

/** Partie "compte" de GET /clients/{uid}/compte (voir CompteClientDTO côté backend) :
 * ce que le client doit, et son avance (libre ou réservée à ses commandes en cours). */
public class CompteClientResponse {
    public Compte compte;

    public static class Compte {
        public String clientUniqueId;
        public double resteAPayer;
        public double avance;
        public double avanceLibre;
        public double avanceReservee;
        public double solde; // positif = le client doit
        public List<AvanceReservee> avancesReservees;
    }

    public static class AvanceReservee {
        public String commandeUniqueId;
        public String dateCommande;
        public double montant;
    }
}
