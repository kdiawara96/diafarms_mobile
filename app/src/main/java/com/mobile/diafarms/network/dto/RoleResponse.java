package com.mobile.diafarms.network.dto;

/** Miroir minimal de RoleDTO côté backend (id, role) — les autres champs JSON sont ignorés par Gson. */
public class RoleResponse {
    private Long id;
    private String role;

    public Long getId() { return id; }
    public String getRole() { return role; }
}
