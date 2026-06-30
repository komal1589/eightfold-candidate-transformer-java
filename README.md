# Veritas (Java) — Multi-Source Candidate Data Transformer

> Eightfold Engineering Intern (Jul–Dec 2026) assignment — Stage 2 implementation.

Turns messy, conflicting candidate data from many sources into **one clean,
canonical, fully-traceable profile** — with a confidence score on every value
and a record of exactly where each value came from.

The core idea: **nothing is merged directly.** Every source emits typed
*Claims* into a shared **Evidence Ledger**, and the final profile is a pure,
deterministic *reduction* over that ledger. That single decision buys all three
constraints at once — deterministic & explainable, robust, and scalable.

```
detect → extract → normalize → merge → confidence → project → validate
         (per-source     (reduce the ledger:        (runtime config
          sandboxes)       pick winners + score)      reshapes output)
```

## Build & run (JDK 17+, one dependency)

The only third-party library is **[Gson](https://github.com/google/gson)** for
JSON parsing. Its jar is **vendored in `./libs`**, so the project still builds
and runs with a bare JDK — no Gradle/Maven and no network needed. The scripts
just put that jar on the classpath for you.

```bash
./build.sh                 # javac the engine + tests into ./bin (uses libs/gson)
./test.sh                  # run the 15-test harness
./run.sh --manifest samples/inputs/manifest.json                      # default profile
./run.sh --manifest samples/inputs/manifest.json \
         --config  samples/configs/compact.json --explain             # custom projection
./run.sh --manifest samples/inputs/manifest.json \
         --config  samples/configs/default.json --out out.json        # write to a file
```

`--explain` prints the per-source extraction log to stderr:

```
  recruiter_csv    OK   8 claims
  ats_json         OK  16 claims
  ats_json         SKIP (extract failed: invalid JSON)   ← corrupt source, run survives
  github_api       OK  10 claims
  ...
  canonical schema: VALID
```

> Prefer Gradle? A `build.gradle` is included (it picks up the vendored Gson jar,
> so it works offline): `gradle runTests` and
> `gradle run --args="--manifest samples/inputs/manifest.json --explain"`.

> **JSON handling:** `Json.java` is a thin wrapper over Gson. Reading uses Gson;
> writing uses a small built-in pretty-printer so whole numbers print as `12`
> (not `12.0`). Every other file just calls `Json.parse` / `Json.write`, so the
> library choice lives in exactly one place.

## Inputs — a manifest of sources

A run is described by a small manifest naming each source, its type, and
(optionally) a trust override and an observation timestamp:

```json
{
  "candidate_id": "cand_ada_lovelace_001",
  "sources": [
    {"type": "recruiter_csv",  "path": "recruiter_export.csv", "timestamp": "2026-06-20"},
    {"type": "ats_json",       "path": "ats_blob.json"},
    {"type": "github_api",     "path": "github_profile.json"},
    {"type": "linkedin",       "path": "linkedin_profile.json"},
    {"type": "resume",         "path": "resume.txt"},
    {"type": "recruiter_notes","path": "recruiter_notes.txt"}
  ]
}
```

Sources covered (≥1 structured **and** ≥1 unstructured, as required):

| Group | Source type | Adapter |
|-------|-------------|---------|
| Structured | Recruiter CSV export | `RecruiterCsv` |
| Structured | ATS JSON blob (foreign field names) | `AtsJson` |
| Unstructured | GitHub public API (offline-mocked JSON) | `GitHubApi` |
| Unstructured | LinkedIn profile fields | `LinkedIn` |
| Unstructured | Résumé prose (`.txt` from PDF/DOCX) | `Resume` |
| Unstructured | Recruiter notes (free text) | `RecruiterNotes` |

> **Why GitHub is a local JSON file:** determinism is a hard requirement, so the
> live API response is captured to `github_profile.json` and the adapter parses
> it exactly as it would the real payload. Swap in a real HTTP call (e.g.
> `java.net.http.HttpClient`) and nothing else changes — the adapter contract is
> the same.

## The required twist — configurable output

A runtime config reshapes the output **without touching the engine**. The
canonical record and the projection layer are cleanly separated: the engine
always builds the same canonical record; `Projection` is a small, total
interpreter for the config.

```json
{
  "fields": [
    { "path": "full_name",     "type": "string", "required": true },
    { "path": "primary_email", "from": "emails[0]", "type": "string", "required": true },
    { "path": "phone",         "from": "phones[0]", "type": "string", "normalize": "E164" },
    { "path": "skills",        "from": "skills[].name", "type": "string[]", "normalize": "canonical" }
  ],
  "include_confidence": true,
  "on_missing": "null"
}
```

The config can: **select** a subset of fields, **rename/remap** from a canonical
path (`"from"`), set **per-field normalization**, **toggle** provenance &
confidence, and choose the **missing-value policy** (`null` | `omit` | `error`).
Every projected value is **type-validated** against the requested schema.

Path mini-language for `"from"`: `full_name`, `emails[0]`, `location.country`,
`skills[].name` (pluck a field from each list element), `links.other` (a list).

## Output (default schema)

`candidate_id, full_name, emails[], phones[], location{city,region,country},
links{linkedin,github,portfolio,other[]}, headline, years_experience,
skills[{name,confidence,sources[]}], experience[{company,title,start,end,summary}],
education[{institution,degree,field,end_year}], provenance[{field,source,method}],
overall_confidence`.

Normalized formats: phones → **E.164**, dates → **YYYY-MM**, country →
**ISO-3166 alpha-2**, skills → **canonical names** (alias table), emails →
lower-cased & deduped.

See `samples/outputs/default_output.json` and `samples/outputs/compact_output.json`
for the output produced on the sample inputs.

## Tests

```bash
./test.sh        # 15 assertions, no JUnit required
```

They cover normalizers, conflict resolution, dedup, robustness to a corrupt
source, determinism, the projection path language, and all three `on_missing`
policies. `pipeline_runs_and_validates` doubles as a gold-profile check.

## Layout

```
libs/gson-2.11.0.jar       the one dependency (JSON parsing), vendored
src/main/java/com/eightfold/veritas/
  Json.java                thin Gson wrapper (read via Gson, small writer)
  model/Claim.java         one traceable assertion (the ledger atom)
  model/FieldResolution.java, model/CanonicalProfile.java
  adapters/                one robust adapter per source format + trust tiers
  Normalize.java           deterministic normalizers + a name→fn registry
  Merge.java               conflict resolution + confidence (the reduction)
  Canonical.java           canonical-record renderer (the clean boundary)
  Projection.java          the configurable projection layer + validation
  Validate.java            default-schema structural validation
  Pipeline.java            detect → … → validate orchestration
  Cli.java                 thin input/output surface
src/test/java/...          test harness (no JUnit needed)
samples/                   inputs, two configs, produced outputs
design/                    one-page technical design (Stage 1)
build.sh · run.sh · test.sh   build/run/test (bare JDK + vendored Gson)
build.gradle              optional Gradle build (uses the vendored jar, offline)
```

## Design decisions worth defending

- **Claims + Evidence Ledger.** Merging is a pure function of immutable Claims,
  so the output is reproducible, every value is explainable (provenance), and the
  policy lives in one place instead of being smeared across adapters.
- **Per-field trust, not just per-source.** GitHub is authoritative for skills
  (0.95) but worthless for phones (0.0); the recruiter CSV outranks LinkedIn on
  identity. The winner policy encodes that, with fully deterministic tie-breaks.
- **Canonical record vs. projection layer are separate types.** The engine never
  changes shape; configs are interpreted data. This is what makes "same engine,
  no code changes" literally true.
- **Unknown → null, never invented.** Every normalizer returns `null` on failure;
  a corrupt source contributes zero claims. Wrong-but-confident is the one
  outcome we refuse to produce.

## Assumptions & deliberately descoped

- One candidate per manifest (matching the sample shape); cross-candidate
  identity resolution is out of scope.
- Résumé parsing is regex + section-headers (deterministic), not ML — it
  intentionally under-extracts rather than hallucinate structure.
- Trust tiers are sensible static defaults with per-field overrides; learning
  trust from outcomes is future work.
- No live network calls — the GitHub source is a captured response for
  reproducibility.
