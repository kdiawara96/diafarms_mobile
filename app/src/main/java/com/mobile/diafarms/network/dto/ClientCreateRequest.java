package com.mobile.diafarms.network.dto;

/** Miroir de com.diafarms.ml.request.create.ClientCreate côté backend. */
public class ClientCreateRequest {
    public String nom;
    public String telephone; // optionnel
    public String adresse;   // optionnel
    public String email;     // optionnel
}
