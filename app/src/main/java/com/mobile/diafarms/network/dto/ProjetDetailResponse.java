package com.mobile.diafarms.network.dto;

import java.util.List;

/** Miroir minimal de ProjetsDTO côté backend (GET /projets/findbyUniqueId/{uniqueId}). */
public class ProjetDetailResponse {
    private String uniqueId;
    private String code;
    private String titre;
    private String finPrevue; // "yyyy-MM-dd"
    private Integer nbSujets; // effectif INITIAL
    private Integer effectifVivant; // nbSujets - mortalité - réforme (null si serveur ancien)
    private Double tauxPonte;
    private Double mortaliteCumulee;
    private List<OccupationBatimentResponse> occupationBatiment;

    public String getUniqueId() { return uniqueId; }
    public String getCode() { return code; }
    public String getTitre() { return titre; }
    public String getFinPrevue() { return finPrevue; }
    public Integer getNbSujets() { return nbSujets; }
    public Integer getEffectifVivant() { return effectifVivant; }
    public Double getTauxPonte() { return tauxPonte; }
    public Double getMortaliteCumulee() { return mortaliteCumulee; }
    public List<OccupationBatimentResponse> getOccupationBatiment() { return occupationBatiment; }
}
