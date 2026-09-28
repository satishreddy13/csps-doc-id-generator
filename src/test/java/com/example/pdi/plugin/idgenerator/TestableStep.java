package com.example.pdi.plugin.idgenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
    private RowMetaInterface       inputMeta   = new RowMeta();

    /** Configured via setVariable(); environmentSubstitute() consults this
     *  before falling back to the real stub's identity-function behavior -
     *  added for the prefix-source feature's PARAMETER mode, which needs
     *  ${NAME} to actually resolve to a configured test value. */
    private final Map<String, String> variables = new HashMap<>();

    TestableStep(String runId) {
        super(stepMeta(), new IdGeneratorStepData(), 0, new TransMeta(), transWithId(runId));
    }

    // ---- control / inspection ----

    /** Add a raw input row (use {@code new Object[0]} for an empty pass-through row). */
    void addInputRow(Object... fields) {
        inputQueue.add(fields);
    }

    /** Overrides the default empty RowMeta returned by getInputRowMeta() -
     *  needed for FIELD-source tests, where a row needs named fields for
     *  resolvePrefix() to look up by name. */
    void setInputRowLayout(RowMetaInterface rowMeta) {
        this.inputMeta = rowMeta;
    }

    /** Configures what environmentSubstitute("${name}") resolves to - the
     *  real stub's environmentSubstitute() is an identity function (it does
     *  not actually resolve ${...} the way real Kettle does), so PARAMETER-
     *  source tests that need a real resolved value configure it here. A
     *  variable that's never configured still returns its "${name}" input
     *  unchanged, same as the real stub's default and real Kettle's actual
     *  behavior for a variable that was never set. */
    void setVariable(String name, String value) {
        variables.put("${" + name + "}", value);
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

    @Override
    public String environmentSubstitute(String value) {
        return variables.containsKey(value) ? variables.get(value) : value;
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
