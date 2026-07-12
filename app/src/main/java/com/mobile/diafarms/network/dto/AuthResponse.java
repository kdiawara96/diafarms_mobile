package com.mobile.diafarms.network.dto;

import java.util.List;

/**
 * Miroir de UsersAuth_DTO côté backend. En cas d'échec (mauvais identifiant/mot de
 * passe), AuthImpl renvoie plutôt un Map {errorMessage: "..."} : seul errorMessage
 * sera alors renseigné, tous les autres champs resteront null.
 */
public class AuthResponse {
    private Long id;
    private String fullName;
    private String email;
    private String photo;
    private String username;
    private String telephone;
    private String farmName;
    private String region;
    private String city;
    private String uniqueId;
    private List<RoleResponse> roles;
    private String refreshToken;
    private String accessToken;
    private Boolean mustChangePassword;
    private String errorMessage;

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhoto() { return photo; }
    public String getUsername() { return username; }
    public String getTelephone() { return telephone; }
    public String getFarmName() { return farmName; }
    public String getRegion() { return region; }
    public String getCity() { return city; }
    public String getUniqueId() { return uniqueId; }
    public List<RoleResponse> getRoles() { return roles; }
    public String getRefreshToken() { return refreshToken; }
    public String getAccessToken() { return accessToken; }
    public Boolean getMustChangePassword() { return mustChangePassword; }
    public String getErrorMessage() { return errorMessage; }
}
