package com.mobile.diafarms.network.dto;

/** Miroir minimal de SalaireDTO côté backend (GET /salaires/select), grille
 * salariale de la ferme (un par employé), utilisée pour le sélecteur "Payer un
 * salaire" côté mobile (rôle Comptable). tauxBase/modePaiement ne servent qu'à
 * PRÉ-REMPLIR le montant proposé hors ligne : le serveur reste seul juge du taux
 * réellement appliqué à la période payée (voir SalaireServiceImpl.resolveTauxPourPeriode),
 * qui peut différer si la grille a changé depuis la dernière synchronisation. */
public class SalaireSelectResponse {
    private String employeUniqueId;
    private String employeNom;
    private String modePaiement; // "MENSUEL", "JOURNALIER" ou "HORAIRE"
    private Double tauxBase;
    // Dernière période effectivement payée pour cet employé (peut être null, jamais
    // payé), utilisé pour bloquer côté mobile une tentative évidente de double
    // paiement AVANT même l'envoi au serveur (voir SaisieFormActivity, case
    // SALAIRE_PAYER). Best-effort seulement : reflète l'état à la dernière
    // synchronisation, le serveur reste seul juge définitif (voir
    // SalaireServiceImpl.payer, rejette tout doublon quoi qu'il arrive).
    private String dernierPaiementPeriode;
    // Toutes les périodes déjà payées (AAAA-MM) à la dernière synchronisation : un mois
    // plus ancien déjà payé est refusé dès son choix, pas seulement le dernier.
    private java.util.List<String> periodesPayees;

    public String getEmployeUniqueId() { return employeUniqueId; }
    public String getEmployeNom() { return employeNom; }
    public String getModePaiement() { return modePaiement; }
    public Double getTauxBase() { return tauxBase; }
    public String getDernierPaiementPeriode() { return dernierPaiementPeriode; }

    /** Période déjà payée d'après la dernière synchronisation (liste complète, sinon le
     * dernier paiement pour un cache d'avant ce champ). */
    public boolean estPayee(String periode) {
        if (periode == null) return false;
        if (periode.equals(dernierPaiementPeriode)) return true;
        return periodesPayees != null && periodesPayees.contains(periode);
    }
}
