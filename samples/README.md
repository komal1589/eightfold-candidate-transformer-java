# Veritas – Multi-Source Candidate Data Transformer

## Overview

Veritas is a Java-based data transformation pipeline that combines candidate information from multiple structured and unstructured sources into a single canonical profile.

The pipeline performs:

- Source detection
- Data extraction
- Data normalization
- Claim-based merging
- Confidence scoring
- Projection using runtime configuration
- Schema validation

---

## Technologies

- Java 21
- Gson
- JSON
- Shell Scripts

---

## Project Structure

```
eightfold-candidate-transformer-java/
│
├── src/
│   ├── main/
│   │   └── java/
│   │       └── com/eightfold/veritas/
│   │           ├── adapters/        # Source-specific adapters
│   │           ├── model/           # Data models (Claim, CanonicalProfile, etc.)
│   │           ├── Canonical.java
│   │           ├── Cli.java
│   │           ├── Json.java
│   │           ├── Merge.java
│   │           ├── Normalize.java
│   │           ├── Pipeline.java
│   │           ├── Projection.java
│   │           └── Validate.java
│   │
│   └── test/
│       └── java/
│           └── com/eightfold/veritas/
│               └── Tests.java
│
├── samples/
│   ├── inputs/          # Sample manifests and input files
│   └── configs/         # Runtime projection configurations
│
├── libs/
│   └── gson-2.11.0.jar
│
├── design/
│   ├── architecture.jpg
│   └── Veritas_Design_Doc.pdf
│
├── bin/                 # Compiled class files
├── build/               # Gradle build output
│
├── build.sh             # Compile project
├── run.sh               # Run pipeline
├── test.sh              # Execute tests
├── build.gradle         # Gradle build configuration
├── gradlew
├── gradlew.bat
├── README.md
└── output.json          # Sample generated output
```

---

## Build

```bash
./build.sh
```

---

## Run

```bash
./run.sh \
  --manifest samples/inputs/manifest.json \
  --config samples/configs/compact.json \
  --out output.json \
  --explain
```

---

## Run Tests

```bash
./test.sh
```

Expected output:

```
15/15 tests passed
```

---

## Sample Output

The generated output is written to:

```
output.json
```

---

## Features

- Adapter-based architecture
- Multi-source data extraction
- Data normalization
- Trust-based conflict resolution
- Confidence scoring
- Configurable output projection
- Schema validation
- Fault-tolerant pipeline

---

## Author

**Komal Kumari**
