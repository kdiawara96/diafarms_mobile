package com.mobile.diafarms.network.dto;

/** Miroir de GET /pesees/dernier-poids-moyen côté backend : dernière session de pesée
 * TERMINEE d'un projet (la plus récente par date de fin). data = null si aucune. */
public class DernierPoidsMoyenResponse {
    public String projetUniqueId;
    public Double poidsMoyenKg;
    public String dateFin; // "yyyy-MM-dd'T'HH:mm:ss"
    public String sessionUniqueId;
    public Integer nombreTotalSujets;
}
