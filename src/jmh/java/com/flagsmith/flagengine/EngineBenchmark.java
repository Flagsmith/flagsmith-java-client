package com.flagsmith.flagengine;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmarks local flag evaluation against every engine test data case.
 */
@State(Scope.Benchmark)
public class EngineBenchmark {
  private static final Path testCasesPath = Paths
      .get("src/test/java/com/flagsmith/flagengine/enginetestdata/test_cases");
  private static final JsonMapper mapper = JsonMapper.builder()
      .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature())
      .build();

  private List<EvaluationContext> evaluationContexts;

  /**
   * Load the evaluation contexts from the engine test data.
   */
  @Setup
  public void loadEvaluationContexts() throws IOException {
    try (Stream<Path> paths = Files.walk(testCasesPath)) {
      evaluationContexts = paths
          .filter(Files::isRegularFile)
          .filter(p -> {
            String n = p.getFileName().toString();
            return n.endsWith(".json") || n.endsWith(".jsonc");
          })
          .sorted()
          .map(EngineBenchmark::evaluationContextFromFile)
          .collect(Collectors.toList());
    }
  }

  private static EvaluationContext evaluationContextFromFile(Path path) {
    try (BufferedReader reader = Files.newBufferedReader(path)) {
      return mapper.treeToValue(mapper.readTree(reader).get("context"), EvaluationContext.class);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read test data from file: " + path, e);
    }
  }

  /**
   * Evaluate flags for every engine test data case.
   */
  @Benchmark
  public void evaluateEngineTestCases(Blackhole blackhole) {
    for (EvaluationContext evaluationContext : evaluationContexts) {
      blackhole.consume(Engine.getEvaluationResult(evaluationContext));
    }
  }
}
