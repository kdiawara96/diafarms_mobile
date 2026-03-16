package com.mobile.diafarms.models;


public class Enregistrement {
    private String id;
    private String projetId;
    private String type; // "oeufs", "alimentation", "soins", "mortalite"
    private String date;
    private String heure;
    private String saisiPar;
    private String syncStatus; // "local", "synced"

    // Données spécifiques selon type (stockées en JSON ou champs séparés)
    private int nbOeufsTotal;
    private int nbOeufsCasses;
    private int nbAlveoles;
    private String typeAliment;
    private int quantiteAlimentKg;
    private int nbPoulesMortes;
    private String causeMort;
    private String photos; // URLs séparées par virgule

    public Enregistrement() {}

    // Getters et Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProjetId() { return projetId; }
    public void setProjetId(String projetId) { this.projetId = projetId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getHeure() { return heure; }
    public void setHeure(String heure) { this.heure = heure; }

    public String getSaisiPar() { return saisiPar; }
    public void setSaisiPar(String saisiPar) { this.saisiPar = saisiPar; }

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }

    public int getNbOeufsTotal() { return nbOeufsTotal; }
    public void setNbOeufsTotal(int nbOeufsTotal) { this.nbOeufsTotal = nbOeufsTotal; }

    public int getNbOeufsCasses() { return nbOeufsCasses; }
    public void setNbOeufsCasses(int nbOeufsCasses) { this.nbOeufsCasses = nbOeufsCasses; }

    public int getNbAlveoles() { return nbAlveoles; }
    public void setNbAlveoles(int nbAlveoles) { this.nbAlveoles = nbAlveoles; }

    public String getTypeAliment() { return typeAliment; }
    public void setTypeAliment(String typeAliment) { this.typeAliment = typeAliment; }

    public int getQuantiteAlimentKg() { return quantiteAlimentKg; }
    public void setQuantiteAlimentKg(int quantiteAlimentKg) { this.quantiteAlimentKg = quantiteAlimentKg; }

    public int getNbPoulesMortes() { return nbPoulesMortes; }
    public void setNbPoulesMortes(int nbPoulesMortes) { this.nbPoulesMortes = nbPoulesMortes; }

    public String getCauseMort() { return causeMort; }
    public void setCauseMort(String causeMort) { this.causeMort = causeMort; }

    public String getPhotos() { return photos; }
    public void setPhotos(String photos) { this.photos = photos; }
}