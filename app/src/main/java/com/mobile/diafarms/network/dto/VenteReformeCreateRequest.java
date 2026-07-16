package com.mobile.diafarms.network.dto;

/**
 * Miroir de com.diafarms.ml.request.create.VenteReformeCreate côté backend — acte
 * Finance à l'échelle de la ferme entière, pas de projetUniqueId/batimentUniqueId.
 */
public class VenteReformeCreateRequest {
    public String date;      // "yyyy-MM-dd"
    public String heure;     // "HH:mm", optionnel
    public Integer nombreSujets;
    public Double prixUnitaire; // optionnel, informatif
    public Double montant;
}
