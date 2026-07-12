package com.mobile.diafarms.network.dto;

/** Miroir minimal de BatimentsDTO côté backend (GET /batiments/select). */
public class BatimentSelectResponse {
    private String uniqueId;
    private String nom;

    public String getUniqueId() { return uniqueId; }
    public String getNom() { return nom; }
}
