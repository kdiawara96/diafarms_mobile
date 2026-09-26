package com.mobile.diafarms.models;

/**
 * Une saisie enregistrée localement (SQLite), en attente de synchronisation vers le
 * backend, ou déjà synchronisée. Le détail des champs (quantités, montant, etc.) est
 * porté par payloadJson — un JSON qui correspond exactement au *Create DTO backend
 * du type concerné (ex: SoinsCreate, MortaliteCreate...), sérialisé/désérialisé via Gson.
 */
public class SaisieLocale {

    public static final String STATUT_LOCAL = "LOCAL";
    public static final String STATUT_SYNCED = "SYNCED";
    public static final String STATUT_ERROR = "ERROR";

    private String localId;
    private SaisieType type;
    private String projetUniqueId;
    private String projetLabel;
    private String payloadJson;
    private String displaySummary;
    private String syncStatus;
    private String serverUniqueId;
    private String errorMessage;
    private long createdAt;
    // Compte (User.getId) et ferme qui ont créé la saisie sur ce téléphone : seul ce compte
    // peut l'envoyer (voir SyncManager). ownerUserId null = saisie antérieure à la 1.31
    // dont le compte n'a pas pu être déterminé ("compte inconnu").
    private String ownerUserId;
    private String ownerFarmId;
    // Clé stable de la saisie, créée à l'enregistrement et gardée à chaque renvoi : servira
    // de clé d'idempotence côté serveur (pas encore envoyée).
    private String cleEnvoi;
    // Code HTTP du dernier échec d'envoi (0 = réseau, null = inconnu / avant la 1.31).
    private Integer httpCode;

    public String getLocalId() { return localId; }
    public void setLocalId(String localId) { this.localId = localId; }

    public SaisieType getType() { return type; }
    public void setType(SaisieType type) { this.type = type; }

    public String getProjetUniqueId() { return projetUniqueId; }
    public void setProjetUniqueId(String projetUniqueId) { this.projetUniqueId = projetUniqueId; }

    public String getProjetLabel() { return projetLabel; }
    public void setProjetLabel(String projetLabel) { this.projetLabel = projetLabel; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public String getDisplaySummary() { return displaySummary; }
    public void setDisplaySummary(String displaySummary) { this.displaySummary = displaySummary; }

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }

    public String getServerUniqueId() { return serverUniqueId; }
    public void setServerUniqueId(String serverUniqueId) { this.serverUniqueId = serverUniqueId; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }

    public String getOwnerFarmId() { return ownerFarmId; }
    public void setOwnerFarmId(String ownerFarmId) { this.ownerFarmId = ownerFarmId; }

    public String getCleEnvoi() { return cleEnvoi; }
    public void setCleEnvoi(String cleEnvoi) { this.cleEnvoi = cleEnvoi; }

    public Integer getHttpCode() { return httpCode; }
    public void setHttpCode(Integer httpCode) { this.httpCode = httpCode; }

    /** true si la saisie partira (ou repartira) au prochain envoi et consommera donc un
     * plafond côté serveur : pas encore envoyée, ou échec temporaire (réseau, serveur
     * indisponible, session expirée). Un refus métier (400) ou un accès refusé (403) ne
     * compte pas : la saisie doit d'abord être corrigée. */
    public boolean seraRenvoyee() {
        if (STATUT_LOCAL.equals(syncStatus)) return true;
        if (!STATUT_ERROR.equals(syncStatus) || httpCode == null) return false;
        int c = httpCode;
        return c == 0 || c == 401 || c == 408 || c == 429 || c >= 500;
    }

    public boolean isEditable() {
        return STATUT_LOCAL.equals(syncStatus) || STATUT_ERROR.equals(syncStatus);
    }
}
