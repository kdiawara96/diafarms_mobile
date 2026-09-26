package com.mobile.diafarms.network.dto;

/** Miroir de CommandeDTO côté backend (GET /commandes/list), champs utiles au téléphone :
 * liste des commandes ouvertes, détail et livraison hors ligne (voir CommandesActivity,
 * SaisieFormActivity LIVRAISON_COMMANDE). Les dates arrivent en "yyyy-MM-dd". */
public class CommandeResponse {
    public String uniqueId;
    public String clientUniqueId;
    public String clientNom;
    public String magasinUniqueId;
    public String magasinNom;
    public String type; // "OEUFS" ou "REFORME"
    public Integer quantite; // œufs, ou sujets pour la réforme
    public Integer quantiteLivree;
    public Integer quantiteRestante;
    public Double prixUnitaireEstime;
    public Double montantEstime;
    public String tarification; // "TETE" ou "KILO"
    public Double prixKgEstime;
    public Double poidsEstimeKg;
    public Double poidsLivreKg;
    public Double montantAcompte;
    public String dateCommande;
    public String dateLivraisonPrevue;
    public String statut; // EN_ATTENTE, CONFIRMEE, EN_LIVRAISON, CONVERTIE, CLOTUREE, ANNULEE
    public String statutLibelle;
    public Double montantLivre;
    public Double acompteRecu;
    public Double acompteImpute;
    public Double acompteReserve;
    public Double avanceReservee;
    public Double payeSurCommande;
    public Double resteAPayerLivre;
    public Integer resteALivrer;

    public boolean estOeufs() {
        return !"REFORME".equals(type);
    }

    public boolean estAuKilo() {
        return "REFORME".equals(type) && "KILO".equals(tarification);
    }

    /** Ce qu'il reste à livrer selon le serveur (dernière donnée connue). */
    public int resteServeur() {
        if (resteALivrer != null) return resteALivrer;
        if (quantiteRestante != null) return quantiteRestante;
        int q = quantite != null ? quantite : 0;
        return q - (quantiteLivree != null ? quantiteLivree : 0);
    }

    /** Prix d'une unité livrée par tête, calculé comme le serveur (CommandeServiceImpl.livrer). */
    public Double prixUnitaireLivraison() {
        if (prixUnitaireEstime != null) return prixUnitaireEstime;
        if (montantEstime != null && quantite != null && quantite > 0) return montantEstime / quantite;
        return null;
    }
}
