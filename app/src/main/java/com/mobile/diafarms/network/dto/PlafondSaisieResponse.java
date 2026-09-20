package com.mobile.diafarms.network.dto;

/** Miroir de PlafondSaisieDTO côté backend (GET /plafond-saisie) : effectif vivant du
 * poulailler (ou du projet) et œufs encore collectables ce jour-là, pour les alertes
 * de cohérence en direct du formulaire. */
public class PlafondSaisieResponse {
    private Integer effectifVivant;
    private String perimetre; // "BATIMENT" ou "PROJET"
    private Integer oeufsDejaCollectes;
    private Integer oeufsRestants;
    // Jour pour lequel oeufsDejaCollectes a été calculé : renseigné par le téléphone (pas
    // par le serveur) au moment de mettre en cache, voir CachePrefetcher/SaisieFormActivity.
    private String cacheDate;

    public Integer getEffectifVivant() { return effectifVivant; }
    public String getPerimetre() { return perimetre; }
    public Integer getOeufsDejaCollectes() { return oeufsDejaCollectes; }
    public Integer getOeufsRestants() { return oeufsRestants; }
    public String getCacheDate() { return cacheDate; }
    public void setCacheDate(String cacheDate) { this.cacheDate = cacheDate; }
}
