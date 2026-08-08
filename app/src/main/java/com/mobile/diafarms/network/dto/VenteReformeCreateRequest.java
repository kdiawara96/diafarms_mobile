package com.mobile.diafarms.network.dto;

/**
 * Miroir de com.diafarms.ml.request.create.VenteReformeCreate côté backend — vendue
 * DEPUIS un magasin précis (magasinUniqueId obligatoire), voir VenteOeufsCreateRequest.
 */
public class VenteReformeCreateRequest {
    public String date;      // "yyyy-MM-dd"
    public String heure;     // "HH:mm", optionnel
    public String magasinUniqueId;
    public Integer nombreSujets;
    public Double prixUnitaire; // optionnel, informatif
    public Double montant;
}
