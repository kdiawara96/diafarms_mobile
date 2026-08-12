package com.mobile.diafarms.network.dto;

/** Miroir minimal de ClientDTO côté backend (GET /clients/select) — clients de la
 * ferme, utilisés pour le sélecteur optionnel d'une vente et obligatoire d'une
 * commande. Seuls les clients déjà synchronisés (créés côté serveur) apparaissent
 * ici : un client saisi hors ligne n'a pas encore de uniqueId serveur tant qu'il n'a
 * pas été poussé par SyncManager. */
public class ClientSelectResponse {
    private String uniqueId;
    private String nom;
    private String telephone;

    public String getUniqueId() { return uniqueId; }
    public String getNom() { return nom; }
    public String getTelephone() { return telephone; }
}
