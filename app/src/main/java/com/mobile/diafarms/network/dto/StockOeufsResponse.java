package com.mobile.diafarms.network.dto;

/** Miroir de StockOeufsDTO côté backend (GET /ventes-oeufs/stock/{projetUniqueId}). */
public class StockOeufsResponse {
    private Integer totalCollecte;
    private Integer totalCasse;
    private Integer totalVendu;
    private Integer stockRestant;
    private String statut; // "ACTIF" | "EPUISE"

    public Integer getTotalCollecte() { return totalCollecte; }
    public Integer getTotalCasse() { return totalCasse; }
    public Integer getTotalVendu() { return totalVendu; }
    public Integer getStockRestant() { return stockRestant; }
    public String getStatut() { return statut; }
}
