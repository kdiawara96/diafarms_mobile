package com.mobile.diafarms.network.dto;

/** POST medicaments/create/{projet} : achat de médicament ou de vaccin (dépense + stock du projet). */
public class AchatMedicamentCreateRequest {
    public String nom;
    public String forme;        // LIQUIDE | POUDRE | COMPRIME | AUTRE
    public String unite;        // flacon, litre, ml, sachet, kg, g, boîte...
    public Double quantite;
    public Double prixUnitaire; // facultatif
    public Double coutTotal;    // montant payé
    public String dateAchat;    // yyyy-MM-dd
    public String fournisseur;
    public String observations;
    public String batimentUniqueId;
}
