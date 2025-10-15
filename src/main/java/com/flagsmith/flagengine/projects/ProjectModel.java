package com.flagsmith.flagengine.projects;

import com.flagsmith.flagengine.segments.SegmentModel;
import com.flagsmith.utils.models.BaseModel;
import java.util.List;
import lombok.Data;

@Data
public class ProjectModel extends BaseModel {
  private List<SegmentModel> segments;
}
