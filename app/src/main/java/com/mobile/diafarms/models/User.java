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
    // Renseignés après coup par /auth/me et /farms/me (voir CachePrefetcher.prefetchProfil) :
    // le JWT du QR ne porte ni la ferme ni le drapeau "consultation seule". null tant que
    // le téléphone n'a pas encore été en ligne avec ce compte.
    private String farmUniqueId;
    private Boolean consultationSeule;

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

    public String getFarmUniqueId() { return farmUniqueId; }
    public void setFarmUniqueId(String farmUniqueId) { this.farmUniqueId = farmUniqueId; }

    public Boolean getConsultationSeule() { return consultationSeule; }
    public void setConsultationSeule(Boolean consultationSeule) { this.consultationSeule = consultationSeule; }

    /** Compte de démonstration (voir ConsultationSeuleFilter côté back) : toute écriture
     * est refusée par le serveur, les boutons de saisie sont donc masqués. */
    public boolean isConsultationSeule() {
        return Boolean.TRUE.equals(consultationSeule);
    }

    // Méthodes utilitaires
    public boolean hasRole(String role) {
        if (roles == null || role == null) return false;
        for (String r : roles) {
            if (r != null && r.equalsIgnoreCase(role)) return true;
        }
        return false;
    }

    // Le backend nommait les rôles "PRODUCTEUR"/"FINANCIER"/"ADMIN" (voir MlApplication.java
    // côté diafarms_back), renommés depuis en PRODUCTION/COMPTABLE/VENTE/RESPONSABLE/ADMIN
    // (RESPONSABLE n'a aucune présence mobile) — on matche les deux formes pour rester
    // tolérant sur un appareil dont le token/cache daterait d'avant la migration.
    public boolean isProduction() {
        return hasRole("PRODUCTEUR") || hasRole("PRODUCTION");
    }

    // L'ancien FINANCIER cumulait ce que COMPTABLE et VENTE font désormais séparément
    // (voir isComptable/isVente ci-dessous, chacun avec son propre jeu d'actions
    // mobile fixe) — gardé pour tout code qui n'a besoin que de savoir si l'un des
    // deux rôles finance s'applique, sans distinguer lequel.
    public boolean isFinance() {
        return isComptable() || isVente();
    }

    public boolean isComptable() {
        return hasRole("FINANCIER") || hasRole("COMPTABLE");
    }

    public boolean isVente() {
        return hasRole("VENTE");
    }

    public boolean isAdmin() {
        return hasRole("ADMIN") || hasRole("SUPER_ADMIN");
    }

    public boolean isResponsable() {
        return hasRole("RESPONSABLE");
    }

    /** Voir et livrer les commandes : mêmes rôles que CommandeServiceImpl.ensureCanManage. */
    public boolean peutGererCommandes() {
        return isVente() || isResponsable() || isAdmin();
    }

    /** Encaisser un paiement client : mêmes rôles que PaiementClientService.ensureCanEncaisser. */
    public boolean peutEncaisser() {
        return peutGererCommandes() || isComptable();
    }

    public boolean isDoubleRole() {
        return isProduction() && isFinance();
    }
}