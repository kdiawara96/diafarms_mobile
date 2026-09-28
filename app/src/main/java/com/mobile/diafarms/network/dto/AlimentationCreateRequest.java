package com.mobile.diafarms.network.dto;

/**
 * Miroir de com.diafarms.ml.request.create.AlimentationCreate côté backend — "aliment
 * entrant" (achat/réception pour le projet, onglet Alimentation de la fiche projet côté
 * web). Le projetUniqueId n'est pas dans le corps : il fait partie de l'URL
 * (POST /alimentations/create/{uniqueIdProjet}).
 */
public class AlimentationCreateRequest {
    public String nomAliment;
    public Double sac;
    public Double quantiteKg;
    public Double coutTotal;
    public String dateDistribution; // "yyyy-MM-dd"
    public String heure;            // "HH:mm", optionnel
    public String observations;
    public String batimentUniqueId;
    public String fournisseur; // optionnel
    // DEMARRAGE / CROISSANCE / PONTE / AUTRE (null = saisie d'une version antérieure :
    // le serveur déduit alors le nom "Aliment").
    public String typeAliment;
    // Poids d'un sac (kg), non stocké côté serveur : sert au calcul si quantiteKg manque.
    public Double poidsSacKg;
}
