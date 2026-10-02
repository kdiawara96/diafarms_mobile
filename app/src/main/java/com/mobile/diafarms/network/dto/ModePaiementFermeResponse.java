package com.mobile.diafarms.network.dto;

/** Miroir de ModePaiementFermeDTO côté backend (GET /farm-settings/modes-paiement) :
 * un mode de paiement ACTIF de la ferme. `code` est la valeur à envoyer (le serveur
 * range un code non historique en AUTRE + libellé), `libelle` le texte à afficher.
 * Voir data.ModesPaiement. */
public class ModePaiementFermeResponse {
    public String code;
    public String libelle;
    public Boolean actif;
    public Boolean historique;
    public Boolean personnalise;
}
