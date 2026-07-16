package com.mobile.diafarms.network.dto;

/** Miroir de com.diafarms.ml.request.create.VenteOeufsCreate côté backend. */
public class VenteOeufsCreateRequest {
    public String projetUniqueId;
    public String batimentUniqueId;
    public String date;      // "yyyy-MM-dd"
    public String heure;     // "HH:mm", optionnel
    public Integer quantiteOeufs;
    public Double prixUnitaire; // optionnel, informatif
    public Double montant;
}
