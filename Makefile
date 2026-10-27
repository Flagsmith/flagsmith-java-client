BENCHMARKS_JAR ?= target/benchmarks.jar

$(BENCHMARKS_JAR): pom.xml $(shell find src/main src/jmh -type f)
	cd third-party/codspeed-jvm && ./gradlew -p jmh-fork publishToMavenLocal
	mvn --batch-mode --no-transfer-progress -P benchmarks -DskipTests package

.PHONY: benchmark
benchmark: $(BENCHMARKS_JAR)
	java -jar $(BENCHMARKS_JAR)
