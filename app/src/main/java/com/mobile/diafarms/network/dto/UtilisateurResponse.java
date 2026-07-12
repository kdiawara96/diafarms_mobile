package com.mobile.diafarms.network.dto;

import java.util.List;

/** Miroir minimal de UtilisateursDTO côté backend (endpoint GET /auth/me). */
public class UtilisateurResponse {
    private String uniqueId;
    private String fullName;
    private String region;
    private String city;
    private String farmName;
    private String photo;
    private String username;
    private String email;
    private String telephone;
    private boolean statut;
    private String lastLogin;
    private List<RoleResponse> roles;

    public String getUniqueId() { return uniqueId; }
    public String getFullName() { return fullName; }
    public String getRegion() { return region; }
    public String getCity() { return city; }
    public String getFarmName() { return farmName; }
    public String getPhoto() { return photo; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getTelephone() { return telephone; }
    public boolean isStatut() { return statut; }
    public String getLastLogin() { return lastLogin; }
    public List<RoleResponse> getRoles() { return roles; }
}
