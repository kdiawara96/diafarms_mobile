package com.mobile.diafarms.network.dto;

/** Miroir minimal de OccupationBatimentDTO côté backend. */
public class OccupationBatimentResponse {
    private String batimentUniqueId;
    private String nomBatiment;
    private String typeBatiment;
    private Integer nbSujetsDansBatiment;
    private String dateSortie; // null = occupation toujours active

    public String getBatimentUniqueId() { return batimentUniqueId; }
    public String getNomBatiment() { return nomBatiment; }
    public String getTypeBatiment() { return typeBatiment; }
    public Integer getNbSujetsDansBatiment() { return nbSujetsDansBatiment; }
    public String getDateSortie() { return dateSortie; }
}
