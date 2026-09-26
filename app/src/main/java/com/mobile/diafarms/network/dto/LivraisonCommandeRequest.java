package com.mobile.diafarms.network.dto;

/** Saisie locale d'une livraison de commande (SaisieType.LIVRAISON_COMMANDE), envoyée à
 * POST /commandes/{uid}/livrer en paramètres (voir CommandeController.livrer). Les champs
 * "infos" (type, magasin, client...) ne partent pas : ils servent aux contrôles hors ligne
 * (stock du magasin, reste à livrer) et à l'affichage. */
public class LivraisonCommandeRequest {
    public String commandeUniqueId;
    public Integer quantite; // toujours en œufs (jamais en alvéoles), ou en sujets
    public Double montantRecu; // argent reçu à cette livraison, null si rien
    public String mode; // mode de paiement de montantRecu
    public Double poidsTotalKg; // commande au kilo seulement
    public Double prixKg; // commande au kilo seulement

    // Infos (non envoyées)
    public String type; // "OEUFS" ou "REFORME"
    public String magasinUniqueId;
    public String clientUniqueId;
    public String clientNom;
}
