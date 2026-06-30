# Veritas (Java) – Multi-Source Candidate Data Transformer

This project was developed as part of the **Eightfold AI Engineering Intern Assignment (Stage 2)**.

The objective of this project is to combine candidate information coming from multiple sources into a single **canonical profile**. The system resolves conflicting information, calculates confidence scores, keeps track of where every value came from, and produces a clean output in a configurable format.

---

# Features

- Reads candidate information from multiple structured and unstructured sources.
- Normalizes data before merging.
- Resolves conflicting values using predefined source trust.
- Calculates confidence scores for merged fields.
- Maintains provenance for every selected value.
- Supports configurable output through JSON configuration files.
- Handles missing or corrupted input files without stopping the pipeline.

---

# Technologies Used

- Java 17
- Gradle
- Gson

---

# Project Structure

```
eightfold-candidate-transformer-java/
│
├── src/
│   ├── main/java/com/eightfold/veritas/
│   │   ├── adapters/
│   │   ├── model/
│   │   ├── Canonical.java
│   │   ├── Cli.java
│   │   ├── Json.java
│   │   ├── Merge.java
│   │   ├── Normalize.java
│   │   ├── Pipeline.java
│   │   ├── Projection.java
│   │   └── Validate.java
│   │
│   └── test/
│
├── samples/
│   ├── inputs/
│   ├── outputs/
│   └── configs/
│
├── design/
├── libs/
├── README.md
├── build.gradle
├── build.sh
├── run.sh
└── test.sh
```

---

# How It Works

The pipeline follows these steps:

```
Detect
   ↓
Extract
   ↓
Normalize
   ↓
Merge
   ↓
Calculate Confidence
   ↓
Project Output
   ↓
Validate
```

### 1. Detect

Reads the manifest file and identifies all input sources.

### 2. Extract

Each source has its own adapter that extracts candidate information and converts it into Claim objects.

### 3. Normalize

Values like emails, phone numbers, countries, skills, and dates are converted into a standard format.

### 4. Merge

Conflicting values are resolved using source trust, timestamps, and deterministic tie-breaking rules.

### 5. Confidence

Each merged field receives a confidence score based on source trust and agreement between sources. An overall confidence score is then calculated using weighted averages.

### 6. Projection

The canonical profile is transformed into the required output format using a JSON configuration file.

### 7. Validation

The final output is checked against the required schema before being returned.

---

# Building the Project

```bash
./build.sh
```

or

```bash
./gradlew build
```

---

# Running the Project

Default configuration

```bash
./run.sh \
  --manifest samples/inputs/manifest.json \
  --config samples/configs/default.json \
  --out output.json \
  --explain
```

Custom configuration

```bash
./run.sh \
  --manifest samples/inputs/manifest.json \
  --config samples/configs/compact.json \
  --out compact_output.json \
  --explain
```

---

# Running Tests

```bash
./test.sh
```

---

# Input Sources

The project supports the following input formats:

- Recruiter CSV
- ATS JSON
- Resume
- LinkedIn
- GitHub
- Recruiter Notes

The input files are listed inside a manifest file, which tells the pipeline what sources to process.

---

# Output

The generated canonical profile includes:

- Candidate ID
- Name
- Emails
- Phone Numbers
- Location
- Skills
- Experience
- Education
- Links
- Confidence Scores
- Provenance

Example outputs are available in:

```
samples/outputs/
```

---

# Confidence Calculation

Confidence is calculated in two stages:

- Every merged field receives a confidence score based on source trust and agreement between multiple sources.
- An overall confidence score is then calculated using weighted averages, giving higher importance to identity fields such as Name and Email than supporting fields like Skills or Links.

---

# Configuration

The output format can be customized using JSON configuration files.

Using the configuration file you can:

- Select only required fields
- Rename fields
- Change output structure
- Apply normalization
- Include or exclude confidence
- Include or exclude provenance
- Choose how missing fields are handled

No changes to the Java code are required.

---

# Design Decisions

- Used the Adapter Pattern to support multiple input formats.
- Converted all extracted information into Claim objects before merging.
- Normalized values before conflict resolution.
- Used deterministic merge rules to ensure reproducible output.
- Kept the canonical model separate from the projection layer for flexibility.

---

# Assumptions

- One manifest represents one candidate.
- Resume parsing is rule-based.
- Source trust values are predefined.
- GitHub data is provided as a local JSON file for deterministic execution.

---

# Author

**Komal Kumari**

B.Tech Computer Science

Eightfold AI Engineering Intern Assignment (Stage 2)
