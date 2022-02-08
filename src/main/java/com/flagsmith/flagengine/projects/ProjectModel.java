package com.flagsmith.flagengine.projects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flagsmith.flagengine.organisations.OrganisationModel;
import com.flagsmith.flagengine.segments.SegmentModel;
import com.flagsmith.utils.models.BaseModel;
import java.util.List;
import lombok.Data;

@Data
public class ProjectModel extends BaseModel {
  private Integer id;
  private String name;
  @JsonProperty("hide_disabled_flags")
  private Boolean hideDisabledFlags;
  private OrganisationModel organisation;
  private List<SegmentModel> segments;
}
