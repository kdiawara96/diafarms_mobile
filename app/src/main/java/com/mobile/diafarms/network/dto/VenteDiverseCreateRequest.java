package com.mobile.diafarms.network.dto;

/** Miroir de la création d'une vente diverse côté backend (POST /ventes-diverses/create) :
 * vente de fientes (produit FIENTES : sacs et prix facultatifs, description facultative)
 * ou autre vente (produit AUTRE : montant et description obligatoires). Commune à la
 * ferme : ni projet, ni site, ni poulailler. Montant arrondi au franc. */
public class VenteDiverseCreateRequest {
    public static final String PRODUIT_FIENTES = "FIENTES";
    public static final String PRODUIT_AUTRE = "AUTRE";

    public String produit;      // "FIENTES" | "AUTRE"
    public String date;         // "yyyy-MM-dd"
    public Double quantite;     // sacs (fientes seulement, facultatif)
    public Double prixUnitaire; // par sac (fientes seulement, facultatif)
    public Double montant;      // > 0, FCFA entiers
    public String description;  // facultative pour les fientes, obligatoire pour une autre vente

    /** Saisie de fientes d'une version antérieure (payload d'Entrée d'argent, sans produit). */
    public boolean estAncienFormat() {
        return produit == null;
    }
}
