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
    public Integer quantiteOeufs;
    public Double prixUnitaire; // optionnel, informatif
    public Double montant;
}
