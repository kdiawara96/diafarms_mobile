package com.mobile.diafarms.network.dto;

/** Miroir minimal de BatimentsDTO côté backend (GET /batiments/select) — type ajouté
 * pour filtrer côté client les bâtiments de STOCKAGE (voir SaisieFormActivity,
 * sélecteur obligatoire de Collecte œufs), les autres écrans n'en ont pas besoin et
 * ignorent silencieusement ce champ (Gson). */
public class BatimentSelectResponse {
    private String uniqueId;
    private String nom;
    private String type; // "POULAILLER", "STOCKAGE" ou "AUTRE"

    public String getUniqueId() { return uniqueId; }
    public String getNom() { return nom; }
    public String getType() { return type; }
}
