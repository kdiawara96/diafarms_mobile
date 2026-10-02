package com.mobile.diafarms.network.dto;

import java.util.List;

/** Miroir minimal de MagasinDTO côté backend (GET /magasins/list) — un vendeur ne
 * voit que les magasins de vente auxquels il est lié, déjà filtré côté serveur.
 * type = "VENTE" ou "STOCKAGE" (voir Magasin.TypeMagasin côté back) : un magasin de
 * stockage est alimenté par la collecte (voir SaisieFormActivity), un magasin de
 * vente par un transfert explicite. */
public class MagasinSelectResponse {
    private String uniqueId;
    private String nom;
    private String type;
    private Integer seuilAlerteAlveoles;
    private List<Object> vendeurs;
    // Magasin de STOCKAGE : point de vente qui reçoit automatiquement ses collectes ;
    // sert aussi à déduire le point de vente par défaut des réformés (PointDeVenteReformes).
    private String magasinVenteParDefautUniqueId;

    public String getUniqueId() { return uniqueId; }
    public String getNom() { return nom; }
    public String getType() { return type; }
    public Integer getSeuilAlerteAlveoles() { return seuilAlerteAlveoles; }
    public String getMagasinVenteParDefautUniqueId() { return magasinVenteParDefautUniqueId; }
}
