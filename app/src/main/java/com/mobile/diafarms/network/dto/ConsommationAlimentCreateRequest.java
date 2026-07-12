package com.mobile.diafarms.network.dto;

/**
 * Miroir de com.diafarms.ml.request.create.ConsommationAlimentCreate côté backend —
 * "aliment sortant" (consommation, tirée du stock du projet, carte Alimentation).
 */
public class ConsommationAlimentCreateRequest {
    public String projetUniqueId;
    public String batimentUniqueId;
    public String date;      // "yyyy-MM-dd"
    public String heure;     // "HH:mm", optionnel
    public Double quantiteKg;
}
