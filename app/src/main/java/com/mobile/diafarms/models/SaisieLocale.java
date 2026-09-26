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
    // Réponse 422 « clé déjà utilisée » : le serveur a DÉJÀ enregistré une première version
    // de cette saisie (même clé, contenu différent). Traitée comme une saisie envoyée avec
    // un avertissement, et non comme une erreur : elle ne repart plus (sinon doublon ou 422
    // sans fin), n'est plus modifiable ici, et se corrige sur le web.
    public static final String STATUT_DEJA_ENREGISTREE = "DEJA_ENREGISTREE";
    public static final String MESSAGE_DEJA_ENREGISTREE =
            "Cette saisie a déjà été enregistrée sur le serveur ; pour la corriger, faites-le depuis l'application web";
    public static final String MESSAGE_ATTENTE_CONFIRMATION =
            "Envoi en attente de confirmation : modification possible après la prochaine synchronisation";

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
    // Envois tentés sans réponse claire, et date (epoch ms) du premier : voir isBloqueeLongtemps.
    private int nbEssais;
    private long premierEchec;

    /** Seuils au-delà desquels une saisie "à renvoyer" est considérée comme bloquée. */
    public static final int ESSAIS_MAX = 5;
    public static final long DUREE_MAX_MS = 3L * 24 * 60 * 60 * 1000;

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
        // Bloquée depuis longtemps : sort des contrôles de stock et de plafond hors ligne
        // (elle continue d'être renvoyée à chaque synchronisation).
        if (isBloqueeLongtemps()) return false;
        if (STATUT_LOCAL.equals(syncStatus)) return true;
        if (!STATUT_ERROR.equals(syncStatus) || httpCode == null) return false;
        int c = httpCode;
        return c == 0 || c == 401 || c == 408 || c == 429 || c >= 500;
    }

    /** Déjà tentée sans réponse claire (réseau, délai, 5xx, 409) : le serveur l'a peut-être
     * enregistrée. Ni modification ni suppression tant que le prochain envoi (même clé) n'a
     * pas tranché. Les sessions de pesée, idempotentes par leurs propres identifiants et
     * réécrites au fil de l'eau, ne sont pas concernées. */
    public boolean isEnAttenteConfirmation() {
        if (!STATUT_LOCAL.equals(syncStatus) || httpCode == null || type == SaisieType.PESEE_SESSION) return false;
        int c = httpCode;
        return c == 0 || c == 408 || c == 409 || c == 429 || c >= 500;
    }

    public int getNbEssais() { return nbEssais; }
    public void setNbEssais(int nbEssais) { this.nbEssais = nbEssais; }

    public long getPremierEchec() { return premierEchec; }
    public void setPremierEchec(long premierEchec) { this.premierEchec = premierEchec; }

    /** "À renvoyer" depuis 5 essais ou 3 jours : peut être supprimée (avec un avertissement
     * fort, le serveur l'a peut-être déjà) et ne compte plus dans les contrôles hors ligne. */
    public boolean isBloqueeLongtemps() {
        if (!isEnAttenteConfirmation()) return false;
        return nbEssais >= ESSAIS_MAX
                || (premierEchec > 0 && System.currentTimeMillis() - premierEchec >= DUREE_MAX_MS);
    }

    /** Modifiable / supprimable par l'utilisateur : jamais envoyée, ou refusée
     * définitivement (ERROR, à corriger). */
    public boolean peutEtreModifiee() {
        return (STATUT_LOCAL.equals(syncStatus) && !isEnAttenteConfirmation()) || STATUT_ERROR.equals(syncStatus);
    }

    /** Pas encore acceptée par le serveur (LOCAL ou ERROR), qu'elle soit modifiable ou non. */
    public boolean isEditable() {
        return STATUT_LOCAL.equals(syncStatus) || STATUT_ERROR.equals(syncStatus);
    }
}
