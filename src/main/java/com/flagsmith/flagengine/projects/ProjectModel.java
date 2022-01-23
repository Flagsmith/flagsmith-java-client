package com.flagsmith.flagengine.projects;

import com.flagsmith.flagengine.organisations.OrganisationModel;
import com.flagsmith.flagengine.segments.SegmentModel;
import lombok.Data;

import java.util.List;

@Data
public class ProjectModel {
    private Integer id;
    private String name;
    private Boolean hideDisabledFlags;
    private OrganisationModel organisation;
    private List<SegmentModel> segments;
}
