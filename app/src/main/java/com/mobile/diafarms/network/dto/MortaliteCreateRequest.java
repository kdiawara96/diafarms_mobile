package com.mobile.diafarms.network.dto;

/** Miroir de com.diafarms.ml.request.create.MortaliteCreate côté backend. */
public class MortaliteCreateRequest {
    public String projetUniqueId;
    public String batimentUniqueId;
    public String date;      // "yyyy-MM-dd"
    public String heure;     // "HH:mm", optionnel
    public Integer nombreMorts;
    public String cause;
}
