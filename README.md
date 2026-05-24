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

**Example:** prefix `ABCDE`, date `2026-05-24`, time `14:32:07.123456789`
```
ABCDE260524TJEVUJKPS
```

### Uniqueness guarantee

Each call atomically claims a nanosecond slot using a virtual clock per transformation run:
- If the real nanosecond is newer than the last claimed → use it directly.
- If two calls land on the exact same nanosecond → advance the virtual clock by 1.

This means there is **no sequence limit** — uniqueness is guaranteed regardless of throughput.

---

## Performance

Tested on PDI 11.0 (Apple Silicon, macOS):

| Rows | Duplicates | Elapsed | Throughput |
|------|-----------|---------|------------|
| 10,000 | 0 | ~340 ms | ~29,000 rows/sec |
| 50,000 | 0 | ~1,859 ms | ~26,900 rows/sec |
| 100,000 | 0 | ~1,844 ms | ~54,200 rows/sec |

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

PDI's Maven artifacts are not on a public repository. A set of minimal compile-only stubs is provided in the sibling `pdi-stubs` project:

```bash
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
