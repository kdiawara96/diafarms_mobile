package com.mobile.diafarms.network.dto;

/** Correction d'un client (SaisieType.CLIENT_UPDATE), PUT /clients/update/{clientUniqueId}.
 * clientUniqueId sert à l'URL ; le corps envoyé est un ClientCreateRequest (voir
 * {@link #corps()}). Adresse/email null = inchangés (le serveur ignore les champs null). */
public class ClientUpdateRequest {
    public String clientUniqueId;
    public String nom;
    public String telephone;
    public String adresse; // null = inchangée
    public String email;   // null = inchangé

    /** Corps attendu par le serveur (ClientCreate), sans l'identifiant. */
    public ClientCreateRequest corps() {
        ClientCreateRequest c = new ClientCreateRequest();
        c.nom = nom;
        c.telephone = telephone;
        c.adresse = adresse;
        c.email = email;
        return c;
    }
}
