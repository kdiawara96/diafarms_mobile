package com.mobile.diafarms.network.dto;

/** Miroir de ProjetsSelect côté backend (GET /projets/select). */
public class ProjetSelectResponse {
    private String uniqueId;
    private String code;
    private String titre;
    private String finPrevue; // "yyyy-MM-dd", date indicative seulement — voir active
    private boolean active; // !initialisation.archive — le vrai statut actif/archivé, pas une date
    private String objectif; // "PONTE" | "REFORME" | "MIXTE" — voir isReformeSeule()

    public String getUniqueId() { return uniqueId; }
    public String getCode() { return code; }
    public String getTitre() { return titre; }
    public String getFinPrevue() { return finPrevue; }
    public boolean isActive() { return active; }
    public String getObjectif() { return objectif; }

    /** Un projet REFORME (chair) ne produit pas d'œufs — contrairement à PONTE et
     * MIXTE, la saisie "Collecte d'œufs"/"Vente d'œufs" n'a pas de sens pour lui
     * (voir HomeActivity). */
    public boolean isReformeSeule() { return "REFORME".equalsIgnoreCase(objectif); }

    /** Symétrique de isReformeSeule() : un projet PONTE pur n'a pas de sujets à
     * réformer, la saisie "Vente réforme" n'a pas de sens pour lui. */
    public boolean isPonteSeule() { return "PONTE".equalsIgnoreCase(objectif); }

    public String getLabel() {
        return (code != null ? code : "") + (titre != null ? " — " + titre : "");
    }
}
