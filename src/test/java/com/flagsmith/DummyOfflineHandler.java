package com.flagsmith;

import com.flagsmith.flagengine.EvaluationContext;
import com.flagsmith.interfaces.IOfflineHandler;

public class DummyOfflineHandler implements IOfflineHandler {
  public EvaluationContext getEvaluationContext() {
    return FlagsmithTestHelper.evaluationContext();
  }
}
