#!/usr/bin/env bash
# Compile the engine + tests with a bare JDK. The only dependency is Gson,
# vendored as a jar in ./libs (so this still works offline, no Gradle needed).
set -euo pipefail
cd "$(dirname "$0")"
CP="libs/gson-2.11.0.jar"
rm -rf bin && mkdir -p bin
find src/main/java -name '*.java' > /tmp/veritas_sources.txt
javac -cp "$CP" -d bin @/tmp/veritas_sources.txt
javac -cp "bin:$CP" -d bin src/test/java/com/eightfold/veritas/Tests.java
echo "Build OK -> ./bin"
