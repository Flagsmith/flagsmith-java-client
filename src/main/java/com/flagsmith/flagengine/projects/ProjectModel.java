package com.flagsmith.flagengine.projects;

import com.flagsmith.flagengine.organisations.OrganisationModel;
import com.flagsmith.flagengine.segments.SegmentModel;
import com.flagsmith.flagengine.utils.models.BaseModel;
import lombok.Data;

import java.util.List;

@Data
public class ProjectModel extends BaseModel {
    private Integer id;
    private String name;
    private Boolean hideDisabledFlags;
    private OrganisationModel organisation;
    private List<SegmentModel> segments;
}
