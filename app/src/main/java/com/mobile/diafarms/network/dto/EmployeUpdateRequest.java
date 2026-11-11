package com.mobile.diafarms.network.dto;

/** Correction d'un employé (SaisieType.EMPLOYE_UPDATE), PUT /personnel/update/{employeUniqueId}.
 * employeUniqueId sert à l'URL ; le corps envoyé est un PersonnelUpdateBody (voir
 * {@link #corps()}). Poste/téléphone null = inchangés (le serveur ignore les champs null). */
public class EmployeUpdateRequest {
    public String employeUniqueId;
    public String nom;
    public String poste;     // null = inchangé
    public String telephone; // null = inchangé

    /** Corps attendu par le serveur (PersonnelCreate), sans l'identifiant. */
    public PersonnelUpdateBody corps() {
        PersonnelUpdateBody b = new PersonnelUpdateBody();
        b.nom = nom;
        b.poste = poste;
        b.telephone = telephone;
        return b;
    }
}
