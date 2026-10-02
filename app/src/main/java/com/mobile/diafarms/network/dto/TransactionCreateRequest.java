package com.mobile.diafarms.network.dto;

import java.util.List;

/** Miroir de com.diafarms.ml.request.create.TransactionCreate côté backend. */
public class TransactionCreateRequest {
    public static final String RATTACHEMENT_PROJET = "PROJET";
    public static final String RATTACHEMENT_SITE = "SITE";
    public static final String RATTACHEMENT_FERME = "FERME";

    public String type;    // "ENTREE" | "SORTIE"
    // « Cette dépense concerne » : PROJET (projetUniqueId, batimentUniqueId facultatif),
    // SITE (siteUniqueId) ou FERME (rien). Absent sur une saisie d'une version antérieure
    // (commun / projetsConcernes), que le serveur normalise.
    public String rattachement;
    // Anciens champs (APK 1.35 et avant) : plus envoyés quand rattachement est présent.
    public Boolean commun;
    public String projetUniqueId;
    public List<String> projetsConcernesUniqueIds;
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
    // Site (rattachement SITE) ou poulailler du projet (rattachement PROJET, facultatif).
    public String siteUniqueId;
    public String batimentUniqueId;
}
