package com.mobile.diafarms.network.dto;

import java.util.List;

/** Miroir de com.diafarms.ml.request.create.TransactionCreate côté backend. */
public class TransactionCreateRequest {
    public String type;    // "ENTREE" | "SORTIE"
    public Boolean commun; // true = dépense/rentrée commune, false = liée à un seul projet
    public String projetUniqueId; // requis si commun = false
    public List<String> projetsConcernesUniqueIds; // optionnel, pertinent seulement si commun = true
    public String date;    // "yyyy-MM-dd"
    public String description;
    public Double montant;
    public String categorie;
    // Catégorie « Autre » : précision libre (ex. « Gardiennage »), enregistrée par le
    // serveur comme catégorie (même résultat que « Préciser la catégorie » du web).
    public String categoriePrecision;
    // Santé / Vétérinaire : quantité (obligatoire) et prix unitaire (facultatif).
    public Double quantite;
    public Double prixUnitaire;
    // Rattachements FACULTATIFS (absents = ferme entière), voir Transaction.site/batiment côté back.
    public String siteUniqueId;
    public String batimentUniqueId;
}
