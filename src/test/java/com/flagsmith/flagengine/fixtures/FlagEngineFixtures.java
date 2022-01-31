package com.flagsmith.flagengine.fixtures;

import com.flagsmith.flagengine.environments.EnvironmentModel;
import com.flagsmith.flagengine.features.FeatureModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.features.MultivariateFeatureOptionModel;
import com.flagsmith.flagengine.features.MultivariateFeatureStateValueModel;
import com.flagsmith.flagengine.identities.IdentityModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.flagengine.organisations.OrganisationModel;
import com.flagsmith.flagengine.projects.ProjectModel;
import com.flagsmith.flagengine.segments.SegmentConditionModel;
import com.flagsmith.flagengine.segments.SegmentModel;
import com.flagsmith.flagengine.segments.SegmentRuleModel;
import com.flagsmith.flagengine.segments.constants.SegmentConditions;
import com.flagsmith.flagengine.segments.constants.SegmentRules;
import java.util.Arrays;

public class FlagEngineFixtures {

  public static SegmentConditionModel segmentCondition() {
    SegmentConditionModel condition = new SegmentConditionModel();
    condition.setValue("bar");
    condition.setProperty_("bar");
    condition.setOperator(SegmentConditions.EQUAL);

    return condition;
  }

  public static SegmentRuleModel segmentRule() {
    SegmentRuleModel rule = new SegmentRuleModel();
    rule.setType(SegmentRules.ALL_RULE.name());
    rule.setConditions(Arrays.asList(segmentCondition()));

    return rule;
  }

  public static SegmentModel segment() {
    SegmentModel segment = new SegmentModel();
    segment.setId(1);
    segment.setName("my_segment");
    segment.setRules(Arrays.asList(segmentRule()));

    return segment;
  }

  public static OrganisationModel organisation() {
    OrganisationModel organisation = new OrganisationModel();
    organisation.setId(1);
    organisation.setName("Test Project");
    organisation.setStopServingFlags(Boolean.FALSE);
    organisation.setPersistTraitData(Boolean.TRUE);
    organisation.setFeatureAnalytics(Boolean.TRUE);

    return organisation;
  }

  public static ProjectModel project() {
    ProjectModel project = new ProjectModel();
    project.setId(1);
    project.setName("Test Project");
    project.setOrganisation(organisation());
    project.setSegments(Arrays.asList(segment()));
    project.setHideDisabledFlags(Boolean.FALSE);

    return project;
  }

  public static FeatureModel feature1() {
    FeatureModel feature = new FeatureModel();
    feature.setId(1);
    feature.setName("feature_1");
    feature.setType("STANDARD");

    return feature;
  }

  public static FeatureModel feature2() {
    FeatureModel feature = new FeatureModel();
    feature.setId(2);
    feature.setName("feature_2");
    feature.setType("STANDARD");

    return feature;
  }

  public static FeatureStateModel featureState1() {
    FeatureStateModel feature = new FeatureStateModel();
    feature.setDjangoId(1);
    feature.setEnabled(Boolean.TRUE);
    feature.setFeature(feature1());

    return feature;
  }

  public static FeatureStateModel featureState2() {
    FeatureStateModel feature = new FeatureStateModel();
    feature.setDjangoId(2);
    feature.setEnabled(Boolean.FALSE);
    feature.setFeature(feature2());

    return feature;
  }

  public static EnvironmentModel environment() {
    EnvironmentModel environment = new EnvironmentModel();
    environment.setId(1);
    environment.setApiKey("api-key");
    environment.setProject(project());
    environment.setFeatureStates(Arrays.asList(featureState1(), featureState2()));

    return environment;
  }

  public static IdentityModel identity() {
    IdentityModel identityModel = new IdentityModel();
    identityModel.setEnvironmentApiKey(environment().getApiKey());
    identityModel.setIdentifier("identity_1");

    return identityModel;
  }

  public static TraitModel traitMatchingSegment() {
    TraitModel trait = new TraitModel();
    trait.setTraitKey(segmentCondition().getProperty_());
    trait.setTraitValue(segmentCondition().getValue());

    return trait;
  }

  public static IdentityModel identityInSegment() {
    IdentityModel identityModel = new IdentityModel();
    identityModel.setEnvironmentApiKey("identity_2");
    identityModel.setEnvironmentApiKey(environment().getApiKey());
    identityModel.setIdentityTraits(Arrays.asList(traitMatchingSegment()));

    return identityModel;
  }

  public static FeatureStateModel segmentOverrideFs() {
    FeatureStateModel featureState = new FeatureStateModel();
    featureState.setDjangoId(4);
    featureState.setFeature(feature1());
    featureState.setEnabled(Boolean.FALSE);

    featureState.setValue("segment_override");

    return featureState;
  }

  public static MultivariateFeatureOptionModel mvFeatureFeatureOption() {
    MultivariateFeatureOptionModel multi = new MultivariateFeatureOptionModel();
    multi.setId(1);
    multi.setValue("test_value");

    return multi;
  }

  public static MultivariateFeatureStateValueModel mvFeatureStateValue() {
    MultivariateFeatureStateValueModel multi = new MultivariateFeatureStateValueModel();
    multi.setId(1);
    multi.setMultivariateFeatureOption(mvFeatureFeatureOption());
    multi.setPercentageAllocation(100f);

    return multi;
  }

  public static EnvironmentModel environmentWithSegmentOverride() {
    EnvironmentModel environmentModel = environment();
    FeatureStateModel segmentOverrideFs = segmentOverrideFs();
    SegmentModel segment = segment();

    segment.getFeatureStates().add(segmentOverrideFs);
    environmentModel.getProject().getSegments().add(segment);

    return environmentModel;
  }
}
