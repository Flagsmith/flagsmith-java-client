package com.flagsmith.flagengine.unit.feature;

import com.flagsmith.flagengine.features.FeatureSegmentModel;
import com.flagsmith.flagengine.features.FeatureStateModel;

import org.testng.Assert;
import org.testng.annotations.Test;

@Test(groups = "unit")
public class FeatureStateModelTest {

  @Test()
  public void testFeatureState_IsHigherPriority_TwoNullFeatureSegments() {
    // Given
    FeatureStateModel featureState1 = new FeatureStateModel();
    FeatureStateModel featureState2 = new FeatureStateModel();
    
    // Then
    Assert.assertFalse(featureState1.isHigherPriority(featureState2));
    Assert.assertFalse(featureState2.isHigherPriority(featureState1));
  }

  @Test()
  public void testFeatureState_IsHigherPriority_OneNullFeatureSegment() {
    // Given
    FeatureStateModel featureState1 = new FeatureStateModel();
    FeatureStateModel featureState2 = new FeatureStateModel();

    FeatureSegmentModel featureSegment = new FeatureSegmentModel(1);
    featureState1.setFeatureSegment(featureSegment);
    
    // Then
    Assert.assertTrue(featureState1.isHigherPriority(featureState2));
    Assert.assertFalse(featureState2.isHigherPriority(featureState1));
  }

  @Test()
  public void testFeatureState_IsHigherPriority() {
    // Given
    FeatureStateModel featureState1 = new FeatureStateModel();
    FeatureStateModel featureState2 = new FeatureStateModel();

    FeatureSegmentModel featureSegment1 = new FeatureSegmentModel(1);
    featureState1.setFeatureSegment(featureSegment1);

    FeatureSegmentModel featureSegment2 = new FeatureSegmentModel(2);
    featureState2.setFeatureSegment(featureSegment2);
    
    // Then
    Assert.assertTrue(featureState1.isHigherPriority(featureState2));
    Assert.assertFalse(featureState2.isHigherPriority(featureState1));
  }
}
