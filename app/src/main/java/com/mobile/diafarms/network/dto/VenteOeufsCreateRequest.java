package com.mobile.diafarms.network.dto;

/**
 * Miroir de com.diafarms.ml.request.create.VenteOeufsCreate côté backend — vendue
 * DEPUIS un magasin précis (magasinUniqueId obligatoire), pas un projetUniqueId/
 * batimentUniqueId : la répartition entre projets contributeurs DE ce magasin est
 * calculée automatiquement côté serveur.
 */
public class VenteOeufsCreateRequest {
    public String date;      // "yyyy-MM-dd"
    public String heure;     // "HH:mm", optionnel
    public String magasinUniqueId;
    public String clientUniqueId; // optionnel — voir VenteOeufs.client
    public Integer quantiteOeufs;
    public Double prixUnitaire; // optionnel, informatif
    public Double montant; // montant théorique
    public Double montantRapporte; // optionnel — ce qui revient RÉELLEMENT en caisse ; si différent du montant théorique, l'écart est suivi comme dette (client si renseigné, sinon vendeur)
    public String typeOeuf; // "BON" (défaut) ou "CASSE" — voir TypeVenteOeufs côté backend
    // Mode de paiement du montant rapporté — seulement pertinent quand clientUniqueId
    // est renseigné (montantRapporte devient alors un paiement client) ; null sinon
    // (contrôle de caisse vendeur). Serveur défaut ESPECES si null — voir ModePaiement.
    public String modePaiement;
}
