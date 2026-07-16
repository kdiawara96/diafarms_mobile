package com.mobile.diafarms.network.dto;

/** Miroir de com.diafarms.ml.request.create.VenteReformeCreate côté backend. */
public class VenteReformeCreateRequest {
    public String projetUniqueId;
    public String batimentUniqueId;
    public String date;      // "yyyy-MM-dd"
    public String heure;     // "HH:mm", optionnel
    public Integer nombreSujets;
    public Double prixUnitaire; // optionnel, informatif
    public Double montant;
}
