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
