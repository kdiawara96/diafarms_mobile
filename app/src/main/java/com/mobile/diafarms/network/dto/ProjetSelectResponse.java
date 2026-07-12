package com.mobile.diafarms.network.dto;

/** Miroir de ProjetsSelect côté backend (GET /projets/select). */
public class ProjetSelectResponse {
    private String uniqueId;
    private String code;
    private String titre;

    public String getUniqueId() { return uniqueId; }
    public String getCode() { return code; }
    public String getTitre() { return titre; }

    public String getLabel() {
        return (code != null ? code : "") + (titre != null ? " — " + titre : "");
    }
}
