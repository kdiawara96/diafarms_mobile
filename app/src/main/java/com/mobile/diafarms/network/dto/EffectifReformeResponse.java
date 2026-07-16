package com.mobile.diafarms.network.dto;

/** Miroir de EffectifReformeDTO côté backend (GET /ventes-reforme/effectif/{projetUniqueId}). */
public class EffectifReformeResponse {
    private Integer nbSujetsInitial;
    private Integer mortaliteCumulee;
    private Integer sujetsReformesCumulee;
    private Integer effectifVivant;

    public Integer getNbSujetsInitial() { return nbSujetsInitial; }
    public Integer getMortaliteCumulee() { return mortaliteCumulee; }
    public Integer getSujetsReformesCumulee() { return sujetsReformesCumulee; }
    public Integer getEffectifVivant() { return effectifVivant; }
}
