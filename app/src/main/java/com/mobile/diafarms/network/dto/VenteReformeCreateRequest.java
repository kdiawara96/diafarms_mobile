package com.mobile.diafarms.network.dto;

/**
 * Miroir de com.diafarms.ml.request.create.VenteReformeCreate côté backend — vendue
 * DEPUIS un magasin précis (magasinUniqueId obligatoire), voir VenteOeufsCreateRequest.
 */
public class VenteReformeCreateRequest {
    public String date;      // "yyyy-MM-dd"
    public String heure;     // "HH:mm", optionnel
    public String magasinUniqueId;
    public String clientUniqueId; // optionnel — voir VenteReforme.client
    public Integer nombreSujets;
    public Double prixUnitaire; // prix par tête (TETE) ou prix par kg (KILO)
    public Double montant; // montant théorique
    public Double montantRapporte; // optionnel — ce qui revient RÉELLEMENT en caisse ; si différent du montant théorique, l'écart est suivi comme dette (client si renseigné, sinon vendeur)
    public String typeVente; // "TETE" (défaut) ou "KILO" — voir TypeVenteReforme côté back
    public Double poidsTotalKg; // obligatoire si typeVente="KILO", null sinon
    // Mode de paiement du montant rapporté — même principe que VenteOeufsCreateRequest,
    // seulement pertinent quand clientUniqueId est renseigné.
    public String modePaiement;
}
