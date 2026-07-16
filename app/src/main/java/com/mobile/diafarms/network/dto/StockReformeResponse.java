package com.mobile.diafarms.network.dto;

/**
 * Miroir de StockReformeDTO côté backend (GET /ventes-reforme/stock) — stock de
 * sujets réformés vendables à l'échelle de TOUTE LA FERME, distinct de
 * EffectifReformeResponse (effectif vivant d'UN projet, côté Production).
 */
public class StockReformeResponse {
    private Integer totalReforme;
    private Integer totalVendu;
    private Integer stockRestant;
    private String statut; // "ACTIF" | "EPUISE"

    public Integer getTotalReforme() { return totalReforme; }
    public Integer getTotalVendu() { return totalVendu; }
    public Integer getStockRestant() { return stockRestant; }
    public String getStatut() { return statut; }
}
