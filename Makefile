BENCHMARKS_JAR ?= target/benchmarks.jar
CODSPEED_JVM := third-party/codspeed-jvm

$(CODSPEED_JVM)/gradlew:
	git submodule update --init -- $(CODSPEED_JVM)
	git -C $(CODSPEED_JVM) submodule update --init -- jmh-fork/jmh-core/native-instrument-hooks/instrument-hooks

$(BENCHMARKS_JAR): pom.xml $(shell find src/main src/jmh -type f) | $(CODSPEED_JVM)/gradlew
	cd third-party/codspeed-jvm && ./gradlew -p jmh-fork publishToMavenLocal
	mvn --batch-mode --no-transfer-progress -P benchmarks -DskipTests package

.PHONY: benchmark
benchmark: $(BENCHMARKS_JAR)
	java -jar $(BENCHMARKS_JAR)
