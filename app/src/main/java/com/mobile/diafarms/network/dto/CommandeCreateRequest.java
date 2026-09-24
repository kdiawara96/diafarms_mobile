package com.mobile.diafarms.network.dto;

/** Miroir de com.diafarms.ml.request.create.CommandeCreate côté backend — client
 * TOUJOURS obligatoire (contrairement à une vente), et forcément déjà synchronisé
 * (voir ClientSelectResponse) : impossible de référencer hors ligne un client créé
 * dans la même session avant qu'il ait un uniqueId serveur. */
public class CommandeCreateRequest {
    public String clientUniqueId;
    public String magasinUniqueId;
    public String type; // "OEUFS" ou "REFORME"
    public Integer quantite;
    public Double prixUnitaireEstime; // optionnel, informatif
    public Double montantEstime;
    public Double montantAcompte; // optionnel
    // Mode de paiement de l'acompte — ESPECES/ORANGE_MONEY/MOOV_MONEY/WAVE/VIREMENT/
    // CHEQUE/AUTRE, null si pas d'acompte (serveur défaut ESPECES si acompte > 0 et
    // mode null, voir ModePaiement côté back).
    public String modePaiement;
    public String dateCommande; // "yyyy-MM-dd", optionnel (défaut aujourd'hui côté back)
    public String dateLivraisonPrevue; // "yyyy-MM-dd", optionnel
}
