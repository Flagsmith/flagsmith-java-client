package com.flagsmith.flagengine.projects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flagsmith.flagengine.organisations.OrganisationModel;
import com.flagsmith.flagengine.segments.SegmentModel;
import com.flagsmith.flagengine.utils.models.BaseModel;
import lombok.Data;

import java.util.List;

@Data
public class ProjectModel extends BaseModel {
  private Integer id;
  private String name;
  @JsonProperty("hide_disabled_flags")
  private Boolean hideDisabledFlags;
  private OrganisationModel organisation;
  private List<SegmentModel> segments;
}
