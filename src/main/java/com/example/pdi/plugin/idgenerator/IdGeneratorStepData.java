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

  public IdGeneratorStepData() {
    super();
  }
}
