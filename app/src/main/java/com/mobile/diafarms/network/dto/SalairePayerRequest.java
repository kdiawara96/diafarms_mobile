package com.mobile.diafarms.network.dto;

/** Miroir de com.diafarms.ml.request.others.SalairePayerRequest côté backend. */
public class SalairePayerRequest {
    public String employeUniqueId;
    public String periode; // "AAAA-MM"
    public Double quantite; // obligatoire si JOURNALIER/HORAIRE, ignoré en MENSUEL
    public Double montant; // optionnel — force le montant
    public String description; // optionnel
}
