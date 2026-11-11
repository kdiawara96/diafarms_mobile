package com.mobile.diafarms.network.dto;

/** Miroir partiel de com.diafarms.ml.request.create.PersonnelCreate côté backend (corps de
 * PUT /personnel/update/{uid}). utilisateurCompteUniqueId n'est jamais envoyé : null =
 * compte lié inchangé. */
public class PersonnelUpdateBody {
    public String nom;
    public String poste;
    public String telephone;
}
