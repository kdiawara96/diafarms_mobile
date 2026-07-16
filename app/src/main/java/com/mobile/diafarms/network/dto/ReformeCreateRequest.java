package com.mobile.diafarms.network.dto;

/**
 * Miroir de com.diafarms.ml.request.create.ReformeCreate côté backend — comptage
 * pur des sujets retirés du cheptel vivant (comme MortaliteCreateRequest), aucun
 * prix ici.
 */
public class ReformeCreateRequest {
    public String projetUniqueId;
    public String batimentUniqueId;
    public String date;      // "yyyy-MM-dd"
    public String heure;     // "HH:mm", optionnel
    public Integer nombreSujets;
    public String cause;     // optionnel
}
