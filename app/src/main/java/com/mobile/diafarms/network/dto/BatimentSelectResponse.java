package com.mobile.diafarms.network.dto;

/** Miroir minimal de BatimentsDTO côté backend (GET /batiments/select) — un bâtiment
 * ne sert plus qu'à l'élevage (poulailler), le stockage/la vente vivent désormais
 * dans Magasin (voir MagasinSelectResponse, type VENTE/STOCKAGE). */
public class BatimentSelectResponse {
    private String uniqueId;
    private String nom;

    public String getUniqueId() { return uniqueId; }
    public String getNom() { return nom; }
}
