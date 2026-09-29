package com.mobile.diafarms.network.dto;

/** GET medicaments/stock/{projet} : une ligne du stock de médicaments du projet. */
public class StockMedicamentResponse {
    public String nom;
    public String forme;
    public String unite;
    public double achete;
    public double utilise;
    public double restant;
}
