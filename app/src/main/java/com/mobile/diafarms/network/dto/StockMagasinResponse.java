package com.mobile.diafarms.network.dto;

/** Miroir de StockMagasinDTO côté backend (GET /magasins/{uniqueId}/stock) — stock
 * DANS ce magasin précis, distinct du stock farm-wide global (StockOeufsResponse/
 * StockReformeResponse), seul à plafonner réellement une vente depuis ce magasin. */
public class StockMagasinResponse {
    private int oeufsDisponible;
    private int reformeDisponible;
    private int oeufsCassesDisponible;

    public int getOeufsDisponible() { return oeufsDisponible; }
    public int getReformeDisponible() { return reformeDisponible; }
    public int getOeufsCassesDisponible() { return oeufsCassesDisponible; }
}
