package com.mobile.diafarms.network.dto;

/** Miroir de DeviseFermeDTO côté backend (GET /farm-settings/devise) : pays et devise
 * de la ferme. La devise n'est qu'une unité d'affichage et d'arrondi (aucune
 * conversion) ; voir util.Monnaie. */
public class DeviseFermeResponse {
    public String pays;
    public String paysNom;
    public String devise;
    public String symbole;
    public String nomDevise;
    public Integer decimales;
}
