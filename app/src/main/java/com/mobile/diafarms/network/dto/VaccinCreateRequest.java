package com.mobile.diafarms.network.dto;

import java.util.List;

/** Miroir de com.diafarms.ml.request.create.VaccinCreate côté backend. Le
 * projetUniqueId n'est pas dans le corps : il fait partie de l'URL
 * (POST /vaccinations/create/{uniqueIdProjet}), comme AlimentationCreateRequest. */
public class VaccinCreateRequest {
    public String nomVaccin;
    public Integer quantite;
    public Double prixUnitaire;
    public List<String> modeAdministration;
}
