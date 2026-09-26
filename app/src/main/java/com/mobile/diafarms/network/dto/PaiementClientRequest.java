package com.mobile.diafarms.network.dto;

/** Miroir de PaiementClientCreate côté backend : saisie locale PAIEMENT_CLIENT. Envoyé à
 * POST /paiements-client/create, ou à POST /commandes/{uid}/paiement quand une commande est
 * choisie (origine acompte/règlement décidée par le serveur). venteCible n'est jamais
 * renseignée : un paiement sur commande ne peut régler que ses livraisons. */
public class PaiementClientRequest {
    public String clientUniqueId;
    public Double montant;
    public String mode; // ESPECES, ORANGE_MONEY, MOOV_MONEY, WAVE, VIREMENT, CHEQUE, AUTRE
    public String date; // yyyy-MM-dd
    public String commandeUniqueId; // facultatif
    public String observations;
}
