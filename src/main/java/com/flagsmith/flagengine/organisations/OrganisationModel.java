package com.flagsmith.flagengine.organisations;

import lombok.Data;

@Data
public class OrganisationModel {
    private Integer id;
    private String name;
    private Boolean featureAnalytics;
    private Boolean stopServingFlags;
    private Boolean persistTraitData;

    public String uniqueSlug() {
        return id.toString() + "-" + name;
    }
}
