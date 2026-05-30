# CSPS DOC_ID Generator — Pentaho PDI Step Plugin

A custom Pentaho Data Integration (PDI) step plugin that generates structured, unique 20-character IDs for each row in a transformation.

---

## ID Format

```
PPPPP  YYMMDD  NNNNNNNNN
│───│  │────│  │───────│
  5      6         9
prefix  date   nano-token
```

| Position | Length | Content |
|----------|--------|---------|
| 1–5 | 5 | User-configured prefix |
| 6–11 | 6 | Date in `YYMMDD` format |
| 12–20 | 9 | Nanosecond-within-day in base36, zero-padded |

**Example:** prefix `TEST1`, date `2026-05-24`, nano-token `U8KZXDGU0`
```
TEST1260524U8KZXDGU0
```

The 9-char nano-token encodes the nanosecond offset from midnight (0 – 86,399,999,999,999) in base36, zero-padded. Each nanosecond maps to a unique token — no separate sequence counter is needed.

### Uniqueness guarantee

Each call atomically claims a unique nanosecond slot via a per-transformation virtual clock:
- Real nanosecond is newer than last claimed → use it directly (normal case).
- Two calls land on the exact same nanosecond → virtual clock advances by 1 (tie-break).

There is **no sequence limit** and no wrap-around — the virtual clock advances monotonically for the lifetime of the transformation run.

---

## Performance

Tested on PDI 11.0 (Apple Silicon, macOS):

| Rows | Duplicates | Elapsed | Throughput |
|------|-----------|---------|------------|
| 50,000 | 0 | 1,859 ms | ~26,900 rows/sec |
| 100,000 | 0 | 1,844 ms | ~54,200 rows/sec |

---

## Step Configuration

When you place the step on the canvas and double-click it, the dialog shows two fields:

| Field | Description |
|-------|-------------|
| **Output Field Name** | Name of the row field that will hold the generated ID (e.g. `generated_id`) |
| **Prefix (5 chars)** | Exactly 5-character prefix placed at positions 1–5 of every ID |

---

## Testing

### Test suite layout

```
src/test/java/com/example/pdi/plugin/idgenerator/
├── TestableStep.java              ← shared harness (no PDI runtime required)
├── IdGeneratorStepMetaTest.java   ← 24 tests — defaults, XML round-trip, clone, getFields, check()
├── IdFormatTest.java              ← 19 tests — length, prefix, date, nano-token structure
├── VirtualClockTest.java          ← 11 tests — SEQUENCE_MAP lifecycle, monotonicity, tie-break
├── IdUniquenessTest.java          ←  8 tests — sequential, concurrent, multi-transformation
├── IdGeneratorEdgeCaseTest.java   ←  7 tests — null prefix, null containerObjectId, stop/restart
└── IdGeneratorPerformanceTest.java←  2 tests — 50k and 100k rows throughput (@Tag "performance")
```

**Total: 71 tests (69 regular + 2 performance).**

### What is tested

| Category | Cases | What is verified |
|---|---|---|
| **Meta defaults** | 2 | `setDefault()` produces `fieldName=DOC_ID`, `prefix=XXXXX` |
| **XML round-trip** | 5 | `getXML()` + `loadXML()` round-trips any field/prefix combination without data loss |
| **Clone** | 6 | `clone()` returns a deep, independent copy — mutating the clone does not affect the original |
| **getFields** | 4 | Exactly one `ValueMetaString` field is appended, with the configured name |
| **check()** | 4 | Error for null/empty `fieldName`, error for wrong prefix length, OK for valid config |
| **ID length** | 2 | Every generated ID is exactly 20 printable ASCII characters |
| **Prefix segment** | 7 | Exact 5-char → unchanged; shorter → space-padded left; longer → truncated; null → safe |
| **Date segment** | 5 | `YYMMDD` format, correct zero-padding, matches wall-clock date at generation time |
| **Nano-token segment** | 5 | Always 9 chars, base36 uppercase, value within `[0, 86_400_000_000_000)` |
| **SEQUENCE_MAP lifecycle** | 5 | `init()` inserts; `dispose()` removes; re-init resets to −1; other keys unaffected |
| **Run key** | 2 | Non-null containerObjectId used directly; null falls back to identity hash |
| **Virtual clock** | 4 | Monotonically non-decreasing; sentinel −1 → first call uses real nano; rapid calls produce distinct tokens |
| **Sequential uniqueness** | 2 | 100 and 10 000 sequential IDs are all distinct |
| **Concurrent uniqueness** | 1 | 4 threads × 2 500 IDs (shared run key) — no duplicates across 10 000 IDs |
| **Multi-transformation** | 3 | Different run keys produce non-overlapping IDs; prefix segment differs correctly |
| **Row processing** | 3 | 0 input → 0 output; N input → N output; ID is always the last field |
| **Edge cases** | 7 | Null/empty prefix; null containerObjectId; stop-restart cycle; dispose/processRow race |
| **Performance** | 2 | 50 000 and 100 000 rows — zero duplicates, ≥ 20 000 rows/sec |

### Running the tests

**Standard suite (excludes performance tests):**
```bash
mvn test
```

**Full suite including performance tests:**
```bash
mvn test -Dperformance.test.exclude=nothing
```

**Single test class:**
```bash
mvn test -Dtest=IdFormatTest
```

**Single test method:**
```bash
mvn test -Dtest=IdUniquenessTest#concurrent_4threads_10000IdsTotal_allUnique
```

### How the test harness works

The `TestableStep` class extends `IdGeneratorStep` and overrides three methods:

| Method | Override |
|---|---|
| `getRow()` | Polls from an in-memory queue |
| `getInputRowMeta()` | Returns an empty `RowMeta` |
| `putRow()` | Appends to an in-memory list |

This means tests run entirely in-process without a PDI installation, a database, or any network access. The compile-only PDI stubs from **[pdi-stubs](https://github.com/satishreddy13/pdi-stubs)** provide the API surface at compile time.

---

## Building

### Prerequisites
- Java 11+ (OpenJDK Adoptium 17 or 21 recommended)
- Maven 3.6+

### 1. Build the PDI compile-only stubs

PDI's Maven artifacts are not on a public repository. A set of minimal compile-only stubs is provided in a companion repository:

**https://github.com/satishreddy13/pdi-stubs**

```bash
git clone https://github.com/satishreddy13/pdi-stubs.git
cd pdi-stubs
mvn install -DskipTests
```

### 2. Build the plugin

```bash
cd id-generator-plugin
mvn clean package -DskipTests
```

This produces:
```
target/
  id-generator-plugin-1.0.0.jar          ← compiled plugin
  id-generator-plugin-1.0.0-plugin.zip   ← deployable zip
```

---

## Deployment

Unzip the plugin into PDI's `plugins/` directory and restart Spoon:

```bash
unzip target/id-generator-plugin-1.0.0-plugin.zip \
  -d <PDI_HOME>/plugins/
```

The **"CSPS DOC_ID Generator"** step will appear in the **Transform** category.

---

## Project Structure

```
id-generator-plugin/
├── pom.xml
├── test-id-generator.ktr           ← Pan test transformation (10k/50k/100k rows)
└── src/main/
    ├── java/com/example/pdi/plugin/idgenerator/
    │   ├── IdGeneratorStep.java        ← Row processing & ID generation
    │   ├── IdGeneratorStepMeta.java    ← Plugin metadata & XML persistence
    │   ├── IdGeneratorStepData.java    ← Runtime data holder
    │   └── IdGeneratorStepDialog.java  ← Spoon configuration dialog
    └── resources/
        ├── plugin.xml                  ← Plugin registration descriptor
        ├── assembly.xml                ← Maven assembly (builds deployable zip)
        └── messages/
            └── messages_en_US.properties

pdi-stubs/                          ← Compile-only PDI API stubs
├── kettle-core-stub/
├── kettle-engine-stub/
├── kettle-ui-swt-stub/
└── metastore-stub/
```

---

## Compatibility

| Component | Version |
|-----------|---------|
| Pentaho PDI | 11.0.0.0-237 (tested) |
| Java | 11+ (compiled at Java 11 bytecode) |
| OS | macOS (tested), Linux, Windows |
