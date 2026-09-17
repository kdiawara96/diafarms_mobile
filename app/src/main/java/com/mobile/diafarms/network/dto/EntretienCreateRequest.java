package com.mobile.diafarms.network.dto;

/** Miroir de com.diafarms.ml.request.create.EntretienCreate côté backend. Jamais lié
 * à un projet (contrairement à SoinsCreateRequest) : un poulailler peut être
 * entretenu même vide, voir Entretien.java côté back. */
public class EntretienCreateRequest {
    public String batimentUniqueId; // requis si niveau = BATIMENT, ignoré sinon
    public String date;             // "yyyy-MM-dd", obligatoire
    public String heure;            // "HH:mm", optionnel
    public String niveau;           // "BATIMENT" | "SITE"
    public String type;             // "NETTOYAGE" | "COPEAU" | "AUTRE" — ignoré si niveau = SITE
    public String description;
    public String observations;
}
