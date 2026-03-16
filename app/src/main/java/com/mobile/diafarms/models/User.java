package com.mobile.diafarms.models;

import java.util.List;

public class User {
    private String id;
    private String nom;
    private String telephone;
    private String email;
    private List<String> roles; // "production", "finance", "admin"
    private List<String> projetsAssignes;
    private String qrCode;
    private long qrExpiry;
    private boolean actif;
    private String photoUrl;

    public User() {}

    public User(String id, String nom, String telephone, List<String> roles) {
        this.id = id;
        this.nom = nom;
        this.telephone = telephone;
        this.roles = roles;
    }

    // Getters et Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }

    public List<String> getProjetsAssignes() { return projetsAssignes; }
    public void setProjetsAssignes(List<String> projetsAssignes) { this.projetsAssignes = projetsAssignes; }

    public String getQrCode() { return qrCode; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }

    public long getQrExpiry() { return qrExpiry; }
    public void setQrExpiry(long qrExpiry) { this.qrExpiry = qrExpiry; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    // Méthodes utilitaires
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean isProduction() {
        return hasRole("production");
    }

    public boolean isFinance() {
        return hasRole("finance");
    }

    public boolean isDoubleRole() {
        return isProduction() && isFinance();
    }
}