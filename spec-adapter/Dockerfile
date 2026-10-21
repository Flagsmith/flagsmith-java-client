# Builds the SDK from source with the adapter for the Flagsmith SDK behavioural spec.
# Build from the repository root: docker build -f spec-adapter/Dockerfile .
FROM docker.io/library/maven:3.9-eclipse-temurin-11 AS build

WORKDIR /sdk
COPY pom.xml lombok.config ./
COPY src/main ./src/main
RUN mvn --batch-mode --no-transfer-progress \
      -Dmaven.test.skip=true -Dcheckstyle.skip=true -Djacoco.skip=true -Dmaven.source.skip=true \
      package dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/lib \
 && cp target/flagsmith-java-client-*.jar target/lib/

COPY spec-adapter/Adapter.java ./spec-adapter/
RUN javac -cp "target/lib/*" -d target/adapter spec-adapter/Adapter.java

FROM docker.io/library/eclipse-temurin:11-jre

WORKDIR /adapter
COPY --from=build /sdk/target/lib ./lib
COPY --from=build /sdk/target/adapter ./classes

ENV PORT=8080
EXPOSE 8080
CMD ["java", "-cp", "classes:lib/*", "Adapter"]
