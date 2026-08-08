package com.mobile.diafarms.network.dto;

import java.util.List;

/** Miroir minimal de MagasinVenteDTO côté backend (GET /magasins/list) — un vendeur ne
 * voit que les magasins auxquels il est lié, déjà filtré côté serveur. */
public class MagasinSelectResponse {
    private String uniqueId;
    private String nom;
    private List<Object> vendeurs;

    public String getUniqueId() { return uniqueId; }
    public String getNom() { return nom; }
}
