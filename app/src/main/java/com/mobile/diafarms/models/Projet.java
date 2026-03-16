package com.mobile.diafarms.models;

public class Projet {
    private String id;
    private String numero;
    private String titre;
    private String dateDemarrage;
    private String dateFinPrevue;
    private int nbPoussinsInit;
    private int nbPoulesActuelles;
    private String objectif; // "ponte" ou "reforme"
    private String statut; // "actif", "cloture"
    private String responsableProduction;
    private String localisation;

    // Stats production
    private int totalOeufsProduits;
    private double tauxPonteActuel;
    private double tauxMortalite;

    // Stats finance
    private double budgetInitial;
    private double coutTotal;
    private double chiffreAffaires;
    private double marge;

    public Projet() {}

    // Getters et Setters complets
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public String getDateDemarrage() { return dateDemarrage; }
    public void setDateDemarrage(String dateDemarrage) { this.dateDemarrage = dateDemarrage; }

    public String getDateFinPrevue() { return dateFinPrevue; }
    public void setDateFinPrevue(String dateFinPrevue) { this.dateFinPrevue = dateFinPrevue; }

    public int getNbPoussinsInit() { return nbPoussinsInit; }
    public void setNbPoussinsInit(int nbPoussinsInit) { this.nbPoussinsInit = nbPoussinsInit; }

    public int getNbPoulesActuelles() { return nbPoulesActuelles; }
    public void setNbPoulesActuelles(int nbPoulesActuelles) { this.nbPoulesActuelles = nbPoulesActuelles; }

    public String getObjectif() { return objectif; }
    public void setObjectif(String objectif) { this.objectif = objectif; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getResponsableProduction() { return responsableProduction; }
    public void setResponsableProduction(String responsableProduction) { this.responsableProduction = responsableProduction; }

    public String getLocalisation() { return localisation; }
    public void setLocalisation(String localisation) { this.localisation = localisation; }

    public int getTotalOeufsProduits() { return totalOeufsProduits; }
    public void setTotalOeufsProduits(int totalOeufsProduits) { this.totalOeufsProduits = totalOeufsProduits; }

    public double getTauxPonteActuel() { return tauxPonteActuel; }
    public void setTauxPonteActuel(double tauxPonteActuel) { this.tauxPonteActuel = tauxPonteActuel; }

    public double getTauxMortalite() { return tauxMortalite; }
    public void setTauxMortalite(double tauxMortalite) { this.tauxMortalite = tauxMortalite; }

    public double getBudgetInitial() { return budgetInitial; }
    public void setBudgetInitial(double budgetInitial) { this.budgetInitial = budgetInitial; }

    public double getCoutTotal() { return coutTotal; }
    public void setCoutTotal(double coutTotal) { this.coutTotal = coutTotal; }

    public double getChiffreAffaires() { return chiffreAffaires; }
    public void setChiffreAffaires(double chiffreAffaires) { this.chiffreAffaires = chiffreAffaires; }

    public double getMarge() { return marge; }
    public void setMarge(double marge) { this.marge = marge; }

    public int getJoursRestants() {
        // Calcul simplifié
        return 32; // Mock
    }
}