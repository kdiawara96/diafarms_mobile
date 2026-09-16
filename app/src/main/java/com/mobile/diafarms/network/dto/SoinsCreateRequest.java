package com.mobile.diafarms.network.dto;

import java.util.List;

/** Miroir de com.diafarms.ml.request.create.SoinsCreate côté backend — entité Soins
 * unifiée (ex-Soins générique + ex-Vaccination, fusionnées côté back). Sert à la fois
 * à Soins (Médicament/Autre) et à Vaccination : mêmes endpoints (/soins/create,
 * /soins/update/{uniqueId}), seul type change et donc quels champs sont renseignés. */
public class SoinsCreateRequest {
    public String projetUniqueId;
    public String batimentUniqueId;
    public String date;      // "yyyy-MM-dd", obligatoire
    public String heure;     // "HH:mm", optionnel
    public String type;      // "VACCINATION" | "MEDICAMENT" | "AUTRE"
    public String produit;   // pour VACCINATION : nom du vaccin
    public Double quantite;  // pour VACCINATION : nombre de doses/flacons
    // Uniquement pertinent pour VACCINATION — null pour MEDICAMENT/AUTRE.
    public Double prixUnitaire;
    // Pour VACCINATION, recalculé côté serveur (quantite × prixUnitaire) : la valeur
    // envoyée ici est ignorée dans ce cas. Pour MEDICAMENT/AUTRE, saisie manuelle.
    public Double coutTotal;
    // Uniquement pertinent pour VACCINATION — le back joint la liste avec " | ".
    public List<String> modeAdministration;
    public String observations;
}
