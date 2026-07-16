package com.mobile.diafarms.network.dto;

/**
 * Miroir de com.diafarms.ml.request.create.VenteOeufsCreate côté backend — acte
 * Finance à l'échelle de la ferme entière, pas de projetUniqueId/batimentUniqueId.
 */
public class VenteOeufsCreateRequest {
    public String date;      // "yyyy-MM-dd"
    public String heure;     // "HH:mm", optionnel
    public Integer quantiteOeufs;
    public Double prixUnitaire; // optionnel, informatif
    public Double montant;
}
