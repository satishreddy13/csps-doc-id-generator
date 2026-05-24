package com.example.pdi.plugin.idgenerator;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.row.RowDataUtil;
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

  private static final int PREFIX_LEN = 5;
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
    }

    String id = generateId(meta);

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

  private String generateId(IdGeneratorStepMeta meta) {
    // Single instant so date and nano-token are always consistent.
    Instant wallClock = Instant.now();
    ZonedDateTime zdt  = wallClock.atZone(ZoneId.systemDefault());

    // Part 1 – prefix (5 chars)
    String rawPrefix = meta.getPrefix() == null ? "" : meta.getPrefix();
    String part1 = padOrTruncate(rawPrefix, PREFIX_LEN, ' ');

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

  private static String padOrTruncate(String s, int width, char padChar) {
    if (s.length() == width) return s;
    if (s.length() > width)  return s.substring(0, width);
    return leftPad(s, width, padChar);
  }
}
