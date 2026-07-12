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

    public boolean isEditable() {
        return STATUT_LOCAL.equals(syncStatus) || STATUT_ERROR.equals(syncStatus);
    }
}
