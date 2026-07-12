package com.mobile.diafarms.network.dto;

/** Miroir de com.diafarms.ml.DTO.NotificationDTO côté backend — alertes recalculées
 * côté serveur (stock, mortalité, transactions en attente...), jamais de contenu
 * mocké côté client, à l'identique de la logique déjà en place sur le web. */
public class NotificationResponse {
    private String key;
    private String type;         // STOCK | MORTALITE | TRANSACTION
    private String level;        // CRITIQUE | WARNING
    private String message;
    private String projetCode;
    private String projetUniqueId;
    private String actionPath;
    private boolean read;

    public String getKey() { return key; }
    public String getType() { return type; }
    public String getLevel() { return level; }
    public String getMessage() { return message; }
    public String getProjetCode() { return projetCode; }
    public String getProjetUniqueId() { return projetUniqueId; }
    public String getActionPath() { return actionPath; }
    public boolean isRead() { return read; }
}
