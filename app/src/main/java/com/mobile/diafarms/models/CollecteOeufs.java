package com.mobile.diafarms.models;

public class CollecteOeufs {
    private String id;
    private String projetId;
    private String date;
    private int quantite;
    private String categorie; // "Petit", "Moyen", "Grand", "Extra"
    private String batiment;  // Bâtiment concerné
    private int oeufsCasses;
    private String saisiPar;
    private String syncStatus;

    // Constructeurs
    public CollecteOeufs() {}

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProjetId() { return projetId; }
    public void setProjetId(String projetId) { this.projetId = projetId; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public int getQuantite() { return quantite; }
    public void setQuantite(int quantite) { this.quantite = quantite; }

    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }

    public String getBatiment() { return batiment; }
    public void setBatiment(String batiment) { this.batiment = batiment; }

    public int getOeufsCasses() { return oeufsCasses; }
    public void setOeufsCasses(int oeufsCasses) { this.oeufsCasses = oeufsCasses; }

    public String getSaisiPar() { return saisiPar; }
    public void setSaisiPar(String saisiPar) { this.saisiPar = saisiPar; }

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }
}