package com.example.pdi.plugin.idgenerator;

import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.trans.step.BaseStepData;
import org.pentaho.di.trans.step.StepDataInterface;

/**
 * Runtime data holder for IdGeneratorStep.
 *
 * PDI creates one instance per step copy. Storing the output RowMeta here
 * avoids recalculating it on every row after the first.
 */
public class IdGeneratorStepData extends BaseStepData implements StepDataInterface {

  /** Cached output row metadata, built once on the first row and reused. */
  public RowMetaInterface outputRowMeta;

  /** Resolved once in init() when prefixSourceType == SOURCE_PARAMETER -
   *  the variable's value doesn't change mid-run, so there is no need to
   *  re-resolve it on every row. */
  public String resolvedParameterPrefix;

  /** Row field index resolved once on the first row when
   *  prefixSourceType == SOURCE_FIELD. -1 until resolved. */
  public int prefixFieldIndex = -1;

  public IdGeneratorStepData() {
    super();
  }
}
