package com.flagsmith.interfaces;

import com.flagsmith.flagengine.EvaluationContext;

public interface IOfflineHandler {
  EvaluationContext getEvaluationContext();
}
