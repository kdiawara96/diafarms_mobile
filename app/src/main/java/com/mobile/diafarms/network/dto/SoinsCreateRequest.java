package com.mobile.diafarms.network.dto;

/** Miroir de com.diafarms.ml.request.create.SoinsCreate côté backend. */
public class SoinsCreateRequest {
    public String projetUniqueId;
    public String batimentUniqueId;
    public String date;      // "yyyy-MM-dd"
    public String heure;     // "HH:mm", optionnel
    public String type;      // "Vaccin" | "Médicament" | "Autre"
    public String produit;
    public Double quantite;
    public Double coutTotal;
    public String observations;
}
