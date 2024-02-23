package com.flagsmith.benchmark;

import com.flagsmith.flagengine.Engine;
import com.flagsmith.flagengine.EngineTest;
import com.flagsmith.flagengine.environments.EnvironmentModel;
import com.flagsmith.flagengine.environments.OptimizedAccessEnvironmentModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.IdentityModel;
import com.flagsmith.models.Flags;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.params.provider.Arguments;
import org.openjdk.jmh.annotations.*;

import java.util.*;
import java.util.stream.Collectors;

public class EvaluationBenchmark {

  @State(Scope.Benchmark)
  public static class ExecutionPlan {

    @Param({"engineTestData", "engineTestDataBig"})
    public String dataSet;
    public List<Arguments> argumentsList;

    public List<FeatureStateModel> featureStates;

    public HashMap<Arguments, OptimizedAccessEnvironmentModel> testRunEnvModel = new HashMap<>();

    @Setup(Level.Invocation)
    public void setUp() {
      if ("engineTestData".equals(dataSet)) {
        argumentsList = EngineTest.engineTestData().collect(Collectors.toList());
      } else {
        argumentsList = EngineTest.engineTestDataBig().collect(Collectors.toList());
      }

      featureStates = argumentsList.stream().flatMap((arguments -> {
        IdentityModel identity = (IdentityModel) arguments.get()[0];
        EnvironmentModel environmentModel = (EnvironmentModel) arguments.get()[1];

        OptimizedAccessEnvironmentModel optimizedAccessEnvironmentModel =
            OptimizedAccessEnvironmentModel.fromEnvironmentModel(environmentModel);

        testRunEnvModel.put(arguments, optimizedAccessEnvironmentModel);

        return Engine.getIdentityFeatureStates(environmentModel, identity).stream();
      })).collect(Collectors.toList());
    }
  }

  @Benchmark
  @Warmup(iterations = 10, time = 1)
  @Measurement(iterations = 5, time = 1)
  @Fork(1)
  @OperationsPerInvocation(10_000)
  @BenchmarkMode(Mode.Throughput)
  @Threads(1)
  @SneakyThrows
  public void singleFlagLegacy(ExecutionPlan plan) {
    Random random = new Random();
    for (int i = 0; i < 10000; i++) {

      val randomArgument = plan.argumentsList.get(random.nextInt(plan.argumentsList.size()));

      IdentityModel identity = (IdentityModel) randomArgument.get()[0];
      EnvironmentModel environmentModel = (EnvironmentModel) randomArgument.get()[1];

      Flags flags = Flags.fromFeatureStateModels(
          Engine.getIdentityFeatureStates(environmentModel, identity),
          null);

      FeatureStateModel randomFeature = plan.featureStates.get(random.nextInt(plan.featureStates.size()));

      flags.getFlag(randomFeature.getFeature().getName());
    }
  }

  @Benchmark
  @Warmup(iterations = 10, time = 1)
  @Measurement(iterations = 5, time = 1)
  @Fork(1)
  @OperationsPerInvocation(10_000)
  @BenchmarkMode(Mode.Throughput)
  @Threads(1)
  @SneakyThrows
  public void singleFlagOptimal(ExecutionPlan plan) {
    Random random = new Random();
    for (int i = 0; i < 10_000; i++) {
      val randomArgument = plan.argumentsList.get(random.nextInt(plan.argumentsList.size()));

      IdentityModel identity = (IdentityModel) randomArgument.get()[0];
      OptimizedAccessEnvironmentModel optimizedAccessEnvironmentModel = plan.testRunEnvModel.get(randomArgument);

      FeatureStateModel randomFeature = plan.featureStates.get(random.nextInt(plan.featureStates.size()));

      FeatureStateModel foundFeature = Engine.getIdentityFeatureStateForFlag(
          optimizedAccessEnvironmentModel, identity, randomFeature.getFeature().getName(), null
      );

      Flags flags = Flags.fromFeatureStateModels(
          Collections.singletonList(foundFeature),
          null);

      flags.getFlag(randomFeature.getFeature().getName());
    }
  }
}
