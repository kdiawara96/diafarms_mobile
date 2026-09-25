package com.mobile.diafarms.network.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Miroir de SessionPeseeDTO côté backend : réponse de POST pesees/sessions/sync, de
 * GET pesees/sessions/{uniqueId} (détail) et éléments de GET pesees/sessions/list
 * (pesees et evenements vides dans la liste). C'est l'état qui fait foi côté serveur,
 * fusionné dans l'état local par SessionPeseeSyncRequest.fusionner.
 */
public class SessionPeseeServeur {
    public String uniqueId;
    public String projetUniqueId;
    public String projetCode;
    public String statut;          // EN_COURS | TERMINEE
    public Integer nombreParDefaut;
    public String dateDebut;       // ISO local, fractions de seconde possibles
    public String dateFin;
    public String derniereDatePesee;
    public Integer nombreTotalSujets;
    public Double poidsTotalKg;
    public Double poidsMoyenKg;
    public Integer nombrePesees;   // non annulées
    public String creeParNom;
    public Long version;           // +1 à chaque changement côté serveur
    public String origine;         // MOBILE | WEB
    /** null si la réponse ne porte pas le détail des pesées (repli dans SyncManager). */
    public List<Pesee> pesees;
    /** Journal des actions faites sur le web, chronologique. */
    public List<Evenement> evenements = new ArrayList<>();
    /** Synchro seulement : pesées envoyées NON enregistrées (session déjà terminée). */
    public List<String> peseesRefusees = new ArrayList<>();

    public static class Pesee {
        public String uniqueId;
        public Integer nombreSujets;
        public Double poidsKg;
        public String dateHeure;
        public Boolean annulee;
        public String creeParNom;
        public String origine;     // MOBILE | WEB
        public Boolean modifiee;   // corrigée depuis le web
    }

    public static class Evenement {
        public String uniqueId;
        public String type;        // CREATION_WEB, AJOUT_WEB, MODIFICATION_WEB, ANNULATION_WEB, TERMINAISON_WEB
        public String peseeUniqueId;
        public String description;
        public String parNom;
        public String date;
    }

    public boolean isTerminee() { return SessionPeseeSyncRequest.STATUT_TERMINEE.equals(statut); }

    /** Page de GET pesees/sessions/list (PaginatedResponse côté backend). */
    public static class Page {
        public List<SessionPeseeServeur> data = new ArrayList<>();
        public int currentPage;
        public int totalPages;
        public long totalItems;
    }
}
