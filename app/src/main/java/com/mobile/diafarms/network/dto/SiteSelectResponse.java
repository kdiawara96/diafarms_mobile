package com.mobile.diafarms.network.dto;

/** Miroir minimal de SiteDTO côté backend (GET /sites/list) : un emplacement de la ferme,
 * utilisé pour rattacher (facultativement) une dépense à un site. */
public class SiteSelectResponse {
    private String uniqueId;
    private String nom;

    public String getUniqueId() { return uniqueId; }
    public String getNom() { return nom; }
}
