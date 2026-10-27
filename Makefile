BENCHMARKS_JAR ?= target/benchmarks.jar
CODSPEED_JVM := third-party/codspeed-jvm
SDK_BENCHMARKS := third-party/sdk-benchmarks

$(CODSPEED_JVM)/gradlew:
	git submodule update --init -- $(CODSPEED_JVM)
	git -C $(CODSPEED_JVM) submodule update --init -- jmh-fork/jmh-core/native-instrument-hooks/instrument-hooks

$(SDK_BENCHMARKS)/environments:
	git submodule update --init -- $(SDK_BENCHMARKS)

$(BENCHMARKS_JAR): pom.xml $(shell find src/main src/jmh -type f) $(wildcard $(SDK_BENCHMARKS)/environments/*.json) | $(CODSPEED_JVM)/gradlew $(SDK_BENCHMARKS)/environments
	cd third-party/codspeed-jvm && ./gradlew -p jmh-fork publishToMavenLocal
	mvn --batch-mode --no-transfer-progress -P benchmarks -DskipTests package

.PHONY: benchmark
benchmark: $(BENCHMARKS_JAR)
	java -jar $(BENCHMARKS_JAR)
