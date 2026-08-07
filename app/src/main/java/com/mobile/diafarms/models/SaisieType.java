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
    // Comptage pur des sujets retirés du cheptel vivant (comme Mortalité) — jamais de
    // prix ici, plafonné par l'effectif vivant du projet. Voir diafarms_back
    // ReformeImpl. La vente réelle (avec montant) est un acte Finance séparé.
    REFORME("Réforme", Categorie.PRODUCTION),
    // Ventes plafonnées côté serveur par le stock d'œufs vendables / de sujets
    // réformés de TOUTE LA FERME (pas d'un projet précis) — voir diafarms_back
    // VenteOeufsImpl/VenteReformeImpl. Catégorie Finance : c'est un acte commercial
    // (prix), pas un mouvement de stock Production, même s'il puise dans les
    // quantités remontées par Collecte œufs / Réforme.
    VENTE_OEUFS("Vente d'œufs", Categorie.FINANCE),
    VENTE_REFORME("Vente réforme", Categorie.FINANCE),
    // Contrairement à VENTE_OEUFS/VENTE_REFORME, pas de stock/plafond serveur dédié :
    // une simple transaction "entrée" commune, catégorie fixe "Vente fientes" — voir
    // diafarms_back TransactionServiceImpl.create (même endpoint que TRANSACTION_ENTREE).
    VENTE_FIENTES("Vente de fientes", Categorie.FINANCE),
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
