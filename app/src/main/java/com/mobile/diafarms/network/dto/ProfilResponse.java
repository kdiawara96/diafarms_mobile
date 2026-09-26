package com.mobile.diafarms.network.dto;

/** Miroir minimal de UtilisateursDTO côté backend (GET /auth/me) : seuls les champs que
 * le JWT du QR ne porte pas nous intéressent (voir CachePrefetcher.prefetchProfil). */
public class ProfilResponse {
    public String uniqueId;
    // Compte de démonstration : le serveur refuse toute écriture (ConsultationSeuleFilter).
    public Boolean consultationSeule;
}
