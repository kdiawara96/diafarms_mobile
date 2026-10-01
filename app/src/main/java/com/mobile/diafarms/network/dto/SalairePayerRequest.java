package com.mobile.diafarms.network.dto;

/** Miroir de com.diafarms.ml.request.others.SalairePayerRequest côté backend. */
public class SalairePayerRequest {
    public String employeUniqueId;
    public String periode; // "AAAA-MM"
    public Double quantite; // obligatoire si JOURNALIER/HORAIRE, ignoré en MENSUEL
    // Plus envoyé (ignoré par le serveur, qui calcule taux de la période x quantité) ;
    // gardé pour relire les saisies en attente d'une version antérieure.
    public Double montant;
    // Seulement quand l'utilisateur a changé le montant proposé (prime, retenue) : > 0,
    // FCFA entiers. Absent = le serveur calcule au taux de la période.
    public Double montantForce;
    // Date du paiement (yyyy-MM-dd), celle du formulaire : le paiement et la dépense
    // « Salaires » prennent cette date, même envoyés plus tard.
    public String datePaiement;
    public String description; // optionnel
}
