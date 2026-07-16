package com.mobile.diafarms.models;

/**
 * Tous les types de saisie que l'application sait faire, chacun mappé sur son
 * entité backend (voir diafarms_back : SoinsControllers, MortaliteControllers,
 * CollecteOeufsControllers, AlimentationControllers, ConsommationAlimentControllers,
 * TransactionControllers).
 */
public enum SaisieType {
    COLLECTE_OEUFS("Collecte d'œufs", Categorie.PRODUCTION),
    // "Sortie" d'aliment : consommation, tirée du stock du projet (carte Alimentation).
    ALIMENTATION_CONSOMMATION("Alimentation (sortie)", Categorie.PRODUCTION),
    // "Entrée" d'aliment : achat/réception pour le projet (onglet Alimentation de la fiche projet côté web).
    ALIMENTATION_ACHAT("Achat d'aliment", Categorie.PRODUCTION),
    SOINS("Soins", Categorie.PRODUCTION),
    MORTALITE("Mortalité", Categorie.PRODUCTION),
    // Ventes plafonnées côté serveur (stock d'œufs vendables / effectif vivant) —
    // voir diafarms_back VenteOeufsImpl/VenteReformeImpl. Groupées avec Production
    // (mouvement physique de stock au quotidien), pas Finance, même si chacune génère
    // aussi une Transaction "entrée" automatiquement côté back.
    VENTE_OEUFS("Vente d'œufs", Categorie.PRODUCTION),
    VENTE_REFORME("Vente réforme", Categorie.PRODUCTION),
    TRANSACTION_ENTREE("Entrée d'argent", Categorie.FINANCE),
    TRANSACTION_SORTIE("Sortie d'argent", Categorie.FINANCE);

    public enum Categorie { PRODUCTION, FINANCE }

    private final String label;
    private final Categorie categorie;

    SaisieType(String label, Categorie categorie) {
        this.label = label;
        this.categorie = categorie;
    }

    public String getLabel() { return label; }
    public Categorie getCategorie() { return categorie; }
}
