package com.mobile.diafarms.network.dto;

/** Miroir de com.diafarms.ml.request.create.CollecteOeufsCreate côté backend. */
public class CollecteOeufsCreateRequest {
    public String projetUniqueId;
    public String batimentUniqueId; // bâtiment d'élevage, optionnel
    public String batimentStockageUniqueId; // bâtiment de stockage, obligatoire
    public String date;      // "yyyy-MM-dd"
    public String heure;     // "HH:mm", optionnel
    public Integer oeufsCollectes;
    public Integer oeufsCasses;
}
