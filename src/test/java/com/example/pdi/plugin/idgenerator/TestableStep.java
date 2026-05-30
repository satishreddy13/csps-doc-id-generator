package com.example.pdi.plugin.idgenerator;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.row.RowMeta;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.StepMeta;

/**
 * Test harness: a minimal subclass of IdGeneratorStep that:
 *  - supplies controlled input rows
 *  - captures output rows
 *  - requires no real PDI infrastructure
 *
 * Usage:
 * <pre>
 *   TestableStep step = new TestableStep("unique-run-id");
 *   IdGeneratorStepMeta meta = new IdGeneratorStepMeta();
 *   meta.setDefault();
 *   meta.setPrefix("TEST1");
 *
 *   List&lt;String&gt; ids = step.generateIds(meta, 5);
 *   // ids contains 5 generated 20-char IDs
 * </pre>
 */
class TestableStep extends IdGeneratorStep {

    private final Queue<Object[]>  inputQueue  = new LinkedList<>();
    private final List<Object[]>   outputRows  = new ArrayList<>();
    private final RowMeta          inputMeta   = new RowMeta();

    TestableStep(String runId) {
        super(stepMeta(), new IdGeneratorStepData(), 0, new TransMeta(), transWithId(runId));
    }

    // ---- control / inspection ----

    /** Add a raw input row (use {@code new Object[0]} for an empty pass-through row). */
    void addInputRow(Object... fields) {
        inputQueue.add(fields);
    }

    List<Object[]> getOutputRows() {
        return outputRows;
    }

    /**
     * Returns the generated ID from a single-row output.
     * The ID is always the last element of the output row.
     */
    String getGeneratedId(int rowIndex) {
        Object[] row = outputRows.get(rowIndex);
        return (String) row[row.length - 1];
    }

    /**
     * Convenience: queue {@code count} empty input rows, run the full lifecycle
     * (init → N × processRow → dispose), and return the list of generated IDs.
     */
    List<String> generateIds(IdGeneratorStepMeta meta, int count) throws KettleException {
        for (int i = 0; i < count; i++) {
            inputQueue.add(new Object[0]);
        }
        IdGeneratorStepData data = new IdGeneratorStepData();
        init(meta, data);
        while (processRow(meta, data)) { /* process */ }
        dispose(meta, data);
        List<String> ids = new ArrayList<>(outputRows.size());
        for (Object[] row : outputRows) {
            ids.add((String) row[row.length - 1]);
        }
        return ids;
    }

    // ---- BaseStep overrides ----

    @Override
    public Object[] getRow() {
        return inputQueue.poll();
    }

    @Override
    public RowMetaInterface getInputRowMeta() {
        return inputMeta;
    }

    @Override
    public void putRow(RowMetaInterface rowMeta, Object[] row) {
        outputRows.add(row);
        // do not call super – avoids mock infrastructure requirements
    }

    // ---- static factories ----

    static Trans transWithId(String id) {
        Trans t = new Trans();
        t.setContainerObjectId(id);
        return t;
    }

    private static StepMeta stepMeta() {
        StepMeta sm = new StepMeta();
        sm.setName("TestStep");
        return sm;
    }

    /** Build a default, ready-to-use meta object. */
    static IdGeneratorStepMeta defaultMeta() {
        IdGeneratorStepMeta meta = new IdGeneratorStepMeta();
        meta.setDefault();
        return meta;
    }

    /** Build a meta with the given prefix. */
    static IdGeneratorStepMeta metaWithPrefix(String prefix) {
        IdGeneratorStepMeta meta = defaultMeta();
        meta.setPrefix(prefix);
        return meta;
    }
}
