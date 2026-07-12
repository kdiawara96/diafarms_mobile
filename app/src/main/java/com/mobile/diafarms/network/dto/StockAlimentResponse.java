package com.mobile.diafarms.network.dto;

/** Miroir de StockAlimentDTO côté backend (GET /consommations-aliment/stock/{projetUniqueId}). */
public class StockAlimentResponse {
    private Double totalAchete;
    private Double totalConsomme;
    private Double stockRestant;
    private String statut; // "ACTIF" | "EPUISE"

    public Double getTotalAchete() { return totalAchete; }
    public Double getTotalConsomme() { return totalConsomme; }
    public Double getStockRestant() { return stockRestant; }
    public String getStatut() { return statut; }
}
