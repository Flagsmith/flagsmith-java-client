# Runs this SDK, built from source, under the Flagsmith SDK behavioural spec.
# docker build -f sdk-spec/Dockerfile --build-context harness=<sdk-spec>/harness/java .
FROM docker.io/library/maven:3.9-eclipse-temurin-11 AS build

WORKDIR /sdk
COPY pom.xml lombok.config ./
COPY src/main ./src/main
RUN mvn --batch-mode --no-transfer-progress \
      -Dmaven.test.skip=true -Dcheckstyle.skip=true -Djacoco.skip=true -Dmaven.source.skip=true \
      package dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/lib \
 && cp target/flagsmith-java-client-*.jar target/lib/

COPY --from=harness Harness.java /harness/Harness.java
COPY sdk-spec/Steps.java ./sdk-spec/Steps.java
RUN javac -cp "target/lib/*" -d target/classes-spec /harness/Harness.java sdk-spec/Steps.java

FROM docker.io/library/eclipse-temurin:11-jre

WORKDIR /sdk
COPY --from=build /sdk/target/lib ./lib
COPY --from=build /sdk/target/classes-spec ./classes
COPY sdk-spec/Steps.java ./sdk-spec/Steps.java

ENTRYPOINT ["java", "-XX:TieredStopAtLevel=1", "-cp", "classes:lib/*", "Harness", "sdk-spec/Steps.java"]
