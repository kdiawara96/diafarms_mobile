package com.mobile.diafarms.network.dto;

/** Miroir de FarmAppSettingsDTO côté backend (GET /farm-settings) — voir
 * HomeActivity.loadFarmAppSettings pour l'affichage/masquage des boutons Finance
 * d'un COMPTABLE ou d'un VENTE en fonction de ces drapeaux. Un seul bouton par
 * rôle/action groupée (pas de granularité fine comme l'ancien FINANCIER à 5
 * drapeaux) : chaque rôle a désormais un jeu d'actions mobile fixe. */
public class FarmAppSettingsResponse {
    private boolean productionMobileEnabled;
    private boolean productionWebEnabled;
    private boolean comptableMobileEnabled;
    private boolean comptableWebEnabled;
    private boolean venteMobileEnabled;
    private boolean venteWebEnabled;
    private boolean responsableWebEnabled;

    public boolean isProductionMobileEnabled() { return productionMobileEnabled; }
    public boolean isProductionWebEnabled() { return productionWebEnabled; }
    public boolean isComptableMobileEnabled() { return comptableMobileEnabled; }
    public boolean isComptableWebEnabled() { return comptableWebEnabled; }
    public boolean isVenteMobileEnabled() { return venteMobileEnabled; }
    public boolean isVenteWebEnabled() { return venteWebEnabled; }
    public boolean isResponsableWebEnabled() { return responsableWebEnabled; }
}
