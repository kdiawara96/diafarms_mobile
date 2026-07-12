package com.mobile.diafarms.network.dto;

/**
 * Réponse générique réutilisée pour toutes les créations (SoinsDTO, MortaliteDTO,
 * CollecteOeufsDTO, AlimentationDTO, ConsommationAlimentDTO, TransactionDTO...) :
 * on n'a besoin que du uniqueId généré côté serveur pour marquer la saisie locale
 * comme synchronisée. Gson ignore silencieusement les autres champs du DTO complet.
 */
public class CreatedEntityResponse {
    private String uniqueId;

    public String getUniqueId() { return uniqueId; }
}
