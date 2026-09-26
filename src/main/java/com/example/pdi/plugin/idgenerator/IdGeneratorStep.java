package com.example.pdi.plugin.idgenerator;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.row.RowDataUtil;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStep;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;

/**
 * ID Generator step – row processing logic.
 *
 * Generated ID format (20 chars total):
 *
 *   Position  Length  Content
 *   --------  ------  -------
 *   1–5         5     Prefix (user-configured)
 *   6–11        6     Date in YYMMDD format
 *   12–20       9     Nanosecond-within-day token in base36, zero-padded.
 *                     Encodes the nanosecond offset from midnight (0 – 86,399,999,999,999).
 *                     Max value 8.64e13 fits in 9 base36 chars (36^9 ≈ 1.02e14).
 *                     Each call atomically claims a unique slot:
 *                       • If the real nanosecond is newer than the last claimed → use it.
 *                       • If not (concurrent call in the same nanosecond) → advance
 *                         the virtual clock by 1, guaranteeing uniqueness without a
 *                         separate sequence counter and without any hard limit.
 *
 * Example: prefix=TEST1, date=2026-05-23, time=23:05:31.929_123_456
 *          nanoInDay = 83131929123456 → base36 "XXXXXXXXX"
 *          → "TEST1260523XXXXXXXXX"
 *
 * Thread-safety:
 *   AtomicLong.accumulateAndGet() performs a lock-free CAS loop.
 *   SEQUENCE_MAP.putIfAbsent() ensures only one AtomicLong per run.
 *   dispose() removes the entry; ConcurrentHashMap.remove() is idempotent.
 */
public class IdGeneratorStep extends BaseStep implements StepInterface {

  // -----------------------------------------------------------------------
  // Constants
  // -----------------------------------------------------------------------

  /** Nanoseconds in one full day. The nano token wraps at this value. */
  private static final long MAX_NANO_IN_DAY = 86_400_000_000_000L; // 24 * 60 * 60 * 1e9

  static final int PREFIX_LEN = 5; // package-private: referenced directly by tests
  private static final int NANO_PAD   = 9; // base36 chars for nanosecond token

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyMMdd");

  // -----------------------------------------------------------------------
  // Static per-transformation-run virtual clock map
  // -----------------------------------------------------------------------

  /**
   * Key   = run key (never null)
   * Value = last allocated nanosecond-within-day value for this run.
   *         Monotonically non-decreasing; advances by 1 on collision.
   */
  static final ConcurrentHashMap<String, AtomicLong> SEQUENCE_MAP = new ConcurrentHashMap<>();

  // -----------------------------------------------------------------------
  // Constructor
  // -----------------------------------------------------------------------

  public IdGeneratorStep(StepMeta stepMeta, StepDataInterface stepDataInterface,
      int copyNr, TransMeta transMeta, Trans trans) {
    super(stepMeta, stepDataInterface, copyNr, transMeta, trans);
  }

  // -----------------------------------------------------------------------
  // Lifecycle – init
  // -----------------------------------------------------------------------

  @Override
  public boolean init(StepMetaInterface smi, StepDataInterface sdi) {
    if (!super.init(smi, sdi)) {
      return false;
    }
    SEQUENCE_MAP.putIfAbsent(getRunKey(), new AtomicLong(-1L));

    IdGeneratorStepMeta meta = (IdGeneratorStepMeta) smi;
    IdGeneratorStepData data = (IdGeneratorStepData) sdi;

    // The variable's value can't change mid-run, so resolve it once here
    // rather than on every row.
    if (IdGeneratorStepMeta.SOURCE_PARAMETER.equals(meta.getPrefixSourceType())) {
      // If the named variable/parameter is genuinely unset, Kettle's
      // environmentSubstitute() leaves the "${NAME}" placeholder text
      // unresolved rather than returning null/empty - which is actually
      // useful here: the length check in processRow() will then fail with
      // an error message showing the literal "${NAME}" text, an
      // unmistakable, self-diagnosing symptom of an unset variable.
      String name = stripVariableWrapper(meta.getPrefixParameter());
      data.resolvedParameterPrefix = environmentSubstitute("${" + name + "}");
    }

    return true;
  }

  // -----------------------------------------------------------------------
  // Row processing
  // -----------------------------------------------------------------------

  @Override
  public boolean processRow(StepMetaInterface smi, StepDataInterface sdi)
      throws KettleException {

    IdGeneratorStepMeta meta = (IdGeneratorStepMeta) smi;
    IdGeneratorStepData data = (IdGeneratorStepData) sdi;

    Object[] inputRow = getRow();

    if (inputRow == null) {
      setOutputDone();
      return false;
    }

    if (first) {
      first = false;
      data.outputRowMeta = getInputRowMeta().clone();
      meta.getFields(data.outputRowMeta, getStepname(), null, null, this, null, null);

      if (IdGeneratorStepMeta.SOURCE_FIELD.equals(meta.getPrefixSourceType())) {
        data.prefixFieldIndex = getInputRowMeta().indexOfValue(meta.getPrefixField());
        if (data.prefixFieldIndex < 0) {
          throw new KettleException(
              "Prefix field not found in input stream: " + meta.getPrefixField());
        }
      }
    }

    String rawPrefix = resolvePrefix(meta, data, getInputRowMeta(), inputRow);
    if (rawPrefix == null || rawPrefix.length() != PREFIX_LEN) {
      throw new KettleException(
          "Prefix must be exactly " + PREFIX_LEN + " characters (source: "
          + meta.getPrefixSourceType() + ", value: \"" + rawPrefix + "\", length: "
          + (rawPrefix == null ? 0 : rawPrefix.length()) + ")");
    }

    String id = generateId(rawPrefix);

    Object[] outputRow = RowDataUtil.addValueData(inputRow, data.outputRowMeta.size() - 1, id);
    putRow(data.outputRowMeta, outputRow);

    if (checkFeedback(getLinesRead()) && log.isBasic()) {
      logBasic("Lines read: " + getLinesRead());
    }

    return true;
  }

  // -----------------------------------------------------------------------
  // ID generation
  // -----------------------------------------------------------------------

  /**
   * Resolves the raw prefix text according to the step's configured source.
   * Returned value is NOT yet validated for length - the caller
   * (processRow()) does that, since it has the row-level error-message
   * context.
   *
   * Package-private (not private) so unit tests can call it directly
   * without needing to drive it through the full BaseStep/processRow()
   * lifecycle.
   */
  String resolvePrefix(IdGeneratorStepMeta meta, IdGeneratorStepData data,
      RowMetaInterface inputRowMeta, Object[] inputRow) throws KettleException {

    String sourceType = meta.getPrefixSourceType();

    if (IdGeneratorStepMeta.SOURCE_PARAMETER.equals(sourceType)) {
      return data.resolvedParameterPrefix;
    }

    if (IdGeneratorStepMeta.SOURCE_FIELD.equals(sourceType)) {
      return inputRowMeta.getString(inputRow, data.prefixFieldIndex);
    }

    // SOURCE_MANUAL, and the fallback for a null sourceType on a step
    // whose meta somehow wasn't run through loadXML()/readRep()/setDefault()
    // (shouldn't normally happen, but this keeps old behavior rather than
    // throwing in that edge case).
    return meta.getPrefix();
  }

  /**
   * Formats the 20-character ID from an already-validated (exactly
   * PREFIX_LEN characters) prefix.
   *
   * Package-private (not private) so unit tests can call it directly.
   */
  String generateId(String rawPrefix) {
    // Single instant so date and nano-token are always consistent.
    Instant wallClock = Instant.now();
    ZonedDateTime zdt  = wallClock.atZone(ZoneId.systemDefault());

    // Part 1 – prefix (5 chars, already validated by the caller)
    String part1 = rawPrefix;

    // Part 2 – date YYMMDD (6 chars)
    String part2 = zdt.format(DATE_FMT);

    // Part 3 – nanosecond-within-day token (9 base36 chars)
    long realNano = (long) zdt.getHour()   * 3_600_000_000_000L
                  + (long) zdt.getMinute() *    60_000_000_000L
                  + (long) zdt.getSecond() *     1_000_000_000L
                  +        zdt.getNano();

    // Atomically claim a unique slot.
    // If realNano > last → claim realNano (new nanosecond, fresh start).
    // If realNano <= last → advance virtual clock by 1 (same nanosecond, tie-break).
    // computeIfAbsent guards against the entry being absent if dispose() races with processRow().
    AtomicLong clock = SEQUENCE_MAP.computeIfAbsent(getRunKey(), k -> new AtomicLong(-1L));
    long allocated = clock.accumulateAndGet(realNano,
        (prev, real) -> real > prev ? real : prev + 1L);

    // Keep within one day (wraps cleanly at midnight)
    String nanoStr = Long.toString(allocated % MAX_NANO_IN_DAY, 36).toUpperCase();
    String part3   = leftPad(nanoStr, NANO_PAD, '0');

    return part1 + part2 + part3;
  }

  // -----------------------------------------------------------------------
  // Run key – never null
  // -----------------------------------------------------------------------

  private String getRunKey() {
    Trans t   = getTrans();
    String id = t.getContainerObjectId();
    return (id != null) ? id : "trans@" + System.identityHashCode(t);
  }

  // -----------------------------------------------------------------------
  // Lifecycle – dispose
  // -----------------------------------------------------------------------

  @Override
  public void dispose(StepMetaInterface smi, StepDataInterface sdi) {
    SEQUENCE_MAP.remove(getRunKey());
    super.dispose(smi, sdi);
  }

  // -----------------------------------------------------------------------
  // String helpers
  // -----------------------------------------------------------------------

  private static String leftPad(String s, int width, char padChar) {
    if (s.length() >= width) return s;
    StringBuilder sb = new StringBuilder(width);
    for (int i = s.length(); i < width; i++) sb.append(padChar);
    sb.append(s);
    return sb.toString();
  }

  /**
   * The Parameter/Variable name field is meant to hold a bare name (e.g.
   * DOC_ID_PREFIX), but users commonly type it Kettle-variable-style with
   * the ${...} wrapper already included, out of habit from other fields
   * in the UI. Since init() always wraps the name in "${" + name + "}"
   * itself, an already-wrapped name would double-wrap into the literal,
   * unresolvable "${${NAME}}". Stripping one layer of wrapping here makes
   * both forms work.
   */
  private static String stripVariableWrapper(String name) {
    if (name == null) return "";
    String trimmed = name.trim();
    if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
      return trimmed.substring(2, trimmed.length() - 1);
    }
    return trimmed;
  }
}
