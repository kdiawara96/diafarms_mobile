package com.mobile.diafarms.models;

public class Transaction {
    private String id;
    private String type; // "entree" ou "sortie"
    private String categorie;
    private double montant;
    private String date;
    private String projetId; // null si commun
    private String description;
    private String clientId; // pour ventes
    private String modePaiement; // "especes", "mobile_money"
    private String saisiPar;
    private String syncStatus;

    public Transaction() {}

    // Getters et Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }

    public double getMontant() { return montant; }
    public void setMontant(double montant) { this.montant = montant; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getProjetId() { return projetId; }
    public void setProjetId(String projetId) { this.projetId = projetId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getModePaiement() { return modePaiement; }
    public void setModePaiement(String modePaiement) { this.modePaiement = modePaiement; }

    public String getSaisiPar() { return saisiPar; }
    public void setSaisiPar(String saisiPar) { this.saisiPar = saisiPar; }

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }
}