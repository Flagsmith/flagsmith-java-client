package com.flagsmith.flagengine;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.assertThat;

public class EngineTest {
  private static final Path testCasesPath = Paths
      .get("src/test/java/com/flagsmith/flagengine/enginetestdata/test_cases");
  private static JsonMapper mapper = JsonMapper.builder()
      .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature())
      .build();
  RecursiveComparisonConfiguration recursiveComparisonConfig = RecursiveComparisonConfiguration.builder()
      .build();

  private static Arguments engineTestDataFromFile(Path path) {
    try (BufferedReader reader = Files.newBufferedReader(path)) {
      JsonNode root = mapper.readTree(reader);
      return Arguments.argumentSet(
          path.getFileName().toString(),
          mapper.treeToValue(root.get("context"), EvaluationContext.class),
          mapper.treeToValue(root.get("result"), EvaluationResult.class));
    } catch (IOException e) {
      throw new RuntimeException("Failed to read test data from file: " + path, e);
    }
  }

  private static Stream<Arguments> engineTestData() throws IOException {
    return Files.walk(testCasesPath)
        .filter(Files::isRegularFile)
        .filter(p -> {
          String n = p.getFileName().toString();
          return n.endsWith(".json") || n.endsWith(".jsonc");
        })
        .map(EngineTest::engineTestDataFromFile);
  }

  @ParameterizedTest()
  @MethodSource("engineTestData")
  public void testEngine(EvaluationContext evaluationContext, EvaluationResult expectedResult) {
    EvaluationResult evaluationResult = Engine.getEvaluationResult(evaluationContext);

    assertThat(evaluationResult)
        .usingRecursiveComparison(recursiveComparisonConfig)
        .ignoringAllOverriddenEquals()
        .isEqualTo(expectedResult);
  }
}
