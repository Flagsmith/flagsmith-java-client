package com.flagsmith.models.projects;

import com.flagsmith.models.segments.SegmentModel;
import com.flagsmith.utils.models.BaseModel;
import java.util.List;
import lombok.Data;

@Data
public class ProjectModel extends BaseModel {
  private List<SegmentModel> segments;
}