package com.mobile.diafarms.network.dto;

/** Miroir de FarmAppSettingsDTO côté backend (GET /farm-settings) — voir
 * HomeActivity.applyFarmAppSettings pour l'affichage/masquage des boutons Finance
 * d'un FINANCIER en fonction de ces drapeaux. */
public class FarmAppSettingsResponse {
    private boolean producteurMobileEnabled;
    private boolean producteurWebEnabled;
    private boolean financierWebEnabled;
    private boolean financierMobileVenteOeufs;
    private boolean financierMobileVenteReforme;
    private boolean financierMobileVenteFientes;
    private boolean financierMobileEntree;
    private boolean financierMobileSortie;

    public boolean isProducteurMobileEnabled() { return producteurMobileEnabled; }
    public boolean isProducteurWebEnabled() { return producteurWebEnabled; }
    public boolean isFinancierWebEnabled() { return financierWebEnabled; }
    public boolean isFinancierMobileVenteOeufs() { return financierMobileVenteOeufs; }
    public boolean isFinancierMobileVenteReforme() { return financierMobileVenteReforme; }
    public boolean isFinancierMobileVenteFientes() { return financierMobileVenteFientes; }
    public boolean isFinancierMobileEntree() { return financierMobileEntree; }
    public boolean isFinancierMobileSortie() { return financierMobileSortie; }
}
