package com.example.pdi.plugin.idgenerator;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pentaho.di.core.CheckResultInterface;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.row.RowMeta;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.value.ValueMetaString;
import org.w3c.dom.Node;

/**
 * Tests for the prefix-source feature (Manual / Parameter / Field): the
 * "Prefix Source" dropdown added alongside the pre-existing manual prefix
 * entry. Does not re-cover ID format/uniqueness/performance, which are
 * already covered by IdFormatTest / IdUniquenessTest /
 * IdGeneratorPerformanceTest for the Manual case those exercise.
 */
@DisplayName("Prefix Source (Manual / Parameter / Field)")
class PrefixSourceTest {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyMMdd");

    // -----------------------------------------------------------------------
    // IdGeneratorStepMeta: defaults, clone, XML round trip, backward compat
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("setDefault sets prefixSourceType to MANUAL")
    void setDefault_setsSourceTypeManual() {
        IdGeneratorStepMeta meta = new IdGeneratorStepMeta();
        meta.setDefault();
        assertEquals(IdGeneratorStepMeta.SOURCE_MANUAL, meta.getPrefixSourceType());
        assertEquals("", meta.getPrefixParameter());
        assertEquals("", meta.getPrefixField());
    }

    @Test
    @DisplayName("clone copies prefixSourceType, prefixParameter and prefixField")
    void clone_copiesNewFields() {
        IdGeneratorStepMeta meta = new IdGeneratorStepMeta();
        meta.setPrefixSourceType(IdGeneratorStepMeta.SOURCE_FIELD);
        meta.setPrefixParameter("SOME_PARAM");
        meta.setPrefixField("upstream_field");

        IdGeneratorStepMeta clone = (IdGeneratorStepMeta) meta.clone();

        assertNotSame(meta, clone);
        assertEquals(meta.getPrefixSourceType(), clone.getPrefixSourceType());
        assertEquals(meta.getPrefixParameter(), clone.getPrefixParameter());
        assertEquals(meta.getPrefixField(), clone.getPrefixField());
    }

    @Test
    @DisplayName("getXML/loadXML round-trips prefixSourceType, prefixParameter and prefixField for all three source types")
    void xmlRoundTrip_allSourceTypes() throws Exception {
        for (String sourceType : new String[] {
                IdGeneratorStepMeta.SOURCE_MANUAL,
                IdGeneratorStepMeta.SOURCE_PARAMETER,
                IdGeneratorStepMeta.SOURCE_FIELD }) {

            IdGeneratorStepMeta original = new IdGeneratorStepMeta();
            original.setFieldName("DOC_ID");
            original.setPrefix("ABCDE");
            original.setPrefixSourceType(sourceType);
            original.setPrefixParameter("MY_PARAM");
            original.setPrefixField("my_field");

            IdGeneratorStepMeta reloaded = reloadThroughXML(original);

            assertEquals(sourceType, reloaded.getPrefixSourceType(), sourceType);
            assertEquals("MY_PARAM", reloaded.getPrefixParameter(), sourceType);
            assertEquals("my_field", reloaded.getPrefixField(), sourceType);
        }
    }

    @Test
    @DisplayName("loadXML defaults prefixSourceType to MANUAL when tags are absent (pre-existing transformations)")
    void loadXML_backwardCompat_oldSchemaDefaultsToManual() throws Exception {
        String oldSchemaXml =
            "<step>"
            + "<fieldname>DOC_ID</fieldname>"
            + "<prefix>TEST1</prefix>"
            + "</step>";

        IdGeneratorStepMeta meta = new IdGeneratorStepMeta();
        meta.loadXML(parseStepNode(oldSchemaXml), new ArrayList<>(), null);

        assertEquals("DOC_ID", meta.getFieldName());
        assertEquals("TEST1", meta.getPrefix());
        assertEquals(IdGeneratorStepMeta.SOURCE_MANUAL, meta.getPrefixSourceType());
        assertEquals("", meta.getPrefixParameter());
        assertEquals("", meta.getPrefixField());
    }

    private IdGeneratorStepMeta reloadThroughXML(IdGeneratorStepMeta original) throws Exception {
        String xml = "<step>" + original.getXML() + "</step>";
        IdGeneratorStepMeta reloaded = new IdGeneratorStepMeta();
        reloaded.loadXML(parseStepNode(xml), new ArrayList<>(), null);
        return reloaded;
    }

    private Node parseStepNode(String xml) throws Exception {
        return DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
            .getDocumentElement();
    }

    // -----------------------------------------------------------------------
    // IdGeneratorStepMeta.check()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("check: Parameter mode errors when parameter name is empty")
    void check_parameterMode_errorsWhenNameEmpty() {
        IdGeneratorStepMeta meta = new IdGeneratorStepMeta();
        meta.setFieldName("out");
        meta.setPrefixSourceType(IdGeneratorStepMeta.SOURCE_PARAMETER);
        meta.setPrefixParameter("");

        List<CheckResultInterface> remarks = runCheck(meta, null);

        assertEquals(1, remarks.size());
        assertEquals(CheckResultInterface.TYPE_RESULT_ERROR, remarks.get(0).getType());
    }

    @Test
    @DisplayName("check: Parameter mode is OK when a name is set, even if not 5 chars (value unknown until runtime)")
    void check_parameterMode_okWhenNameSet() {
        IdGeneratorStepMeta meta = new IdGeneratorStepMeta();
        meta.setFieldName("out");
        meta.setPrefixSourceType(IdGeneratorStepMeta.SOURCE_PARAMETER);
        meta.setPrefixParameter("SOME_PARAM_NAME");

        List<CheckResultInterface> remarks = runCheck(meta, null);

        assertEquals(1, remarks.size());
        assertEquals(CheckResultInterface.TYPE_RESULT_OK, remarks.get(0).getType());
    }

    @Test
    @DisplayName("check: Field mode errors when field name is empty")
    void check_fieldMode_errorsWhenNameEmpty() {
        IdGeneratorStepMeta meta = new IdGeneratorStepMeta();
        meta.setFieldName("out");
        meta.setPrefixSourceType(IdGeneratorStepMeta.SOURCE_FIELD);
        meta.setPrefixField("");

        List<CheckResultInterface> remarks = runCheck(meta, null);

        assertEquals(1, remarks.size());
        assertEquals(CheckResultInterface.TYPE_RESULT_ERROR, remarks.get(0).getType());
    }

    @Test
    @DisplayName("check: Field mode errors when the named field is not in the incoming stream")
    void check_fieldMode_errorsWhenFieldNotFound() throws Exception {
        IdGeneratorStepMeta meta = new IdGeneratorStepMeta();
        meta.setFieldName("out");
        meta.setPrefixSourceType(IdGeneratorStepMeta.SOURCE_FIELD);
        meta.setPrefixField("missing_field");

        RowMetaInterface prev = new RowMeta();
        prev.addValueMeta(new ValueMetaString("some_other_field"));

        List<CheckResultInterface> remarks = runCheck(meta, prev);

        assertEquals(1, remarks.size());
        assertEquals(CheckResultInterface.TYPE_RESULT_ERROR, remarks.get(0).getType());
    }

    @Test
    @DisplayName("check: Field mode is OK when the named field is found in the incoming stream")
    void check_fieldMode_okWhenFieldFound() throws Exception {
        IdGeneratorStepMeta meta = new IdGeneratorStepMeta();
        meta.setFieldName("out");
        meta.setPrefixSourceType(IdGeneratorStepMeta.SOURCE_FIELD);
        meta.setPrefixField("present_field");

        RowMetaInterface prev = new RowMeta();
        prev.addValueMeta(new ValueMetaString("present_field"));

        List<CheckResultInterface> remarks = runCheck(meta, prev);

        assertEquals(1, remarks.size());
        assertEquals(CheckResultInterface.TYPE_RESULT_OK, remarks.get(0).getType());
    }

    @Test
    @DisplayName("check: Field mode is OK when prev is unavailable (e.g. step not yet connected) - deferred to runtime")
    void check_fieldMode_okWhenPrevIsNull() {
        IdGeneratorStepMeta meta = new IdGeneratorStepMeta();
        meta.setFieldName("out");
        meta.setPrefixSourceType(IdGeneratorStepMeta.SOURCE_FIELD);
        meta.setPrefixField("some_field");

        List<CheckResultInterface> remarks = runCheck(meta, null);

        assertEquals(1, remarks.size());
        assertEquals(CheckResultInterface.TYPE_RESULT_OK, remarks.get(0).getType());
    }

    private List<CheckResultInterface> runCheck(IdGeneratorStepMeta meta, RowMetaInterface prev) {
        List<CheckResultInterface> remarks = new ArrayList<>();
        meta.check(remarks, null, null, prev, null, null, null, null, null, null);
        return remarks;
    }

    // -----------------------------------------------------------------------
    // IdGeneratorStep.resolvePrefix() - direct unit tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("resolvePrefix: Manual mode returns meta.getPrefix()")
    void resolvePrefix_manual() throws Exception {
        TestableStep step = new TestableStep("run-1");
        IdGeneratorStepMeta meta = new IdGeneratorStepMeta();
        meta.setPrefixSourceType(IdGeneratorStepMeta.SOURCE_MANUAL);
        meta.setPrefix("ABCDE");

        String result = step.resolvePrefix(meta, new IdGeneratorStepData(), new RowMeta(), new Object[0]);

        assertEquals("ABCDE", result);
    }

    @Test
    @DisplayName("resolvePrefix: Parameter mode returns data.resolvedParameterPrefix as cached during init()")
    void resolvePrefix_parameter() throws Exception {
        TestableStep step = new TestableStep("run-2");
        IdGeneratorStepMeta meta = new IdGeneratorStepMeta();
        meta.setPrefixSourceType(IdGeneratorStepMeta.SOURCE_PARAMETER);

        IdGeneratorStepData data = new IdGeneratorStepData();
        data.resolvedParameterPrefix = "XYZAB";

        String result = step.resolvePrefix(meta, data, new RowMeta(), new Object[0]);

        assertEquals("XYZAB", result);
    }

    @Test
    @DisplayName("resolvePrefix: Field mode reads the row value at the resolved field index")
    void resolvePrefix_field() throws Exception {
        TestableStep step = new TestableStep("run-3");
        IdGeneratorStepMeta meta = new IdGeneratorStepMeta();
        meta.setPrefixSourceType(IdGeneratorStepMeta.SOURCE_FIELD);

        RowMetaInterface rowMeta = new RowMeta();
        rowMeta.addValueMeta(new ValueMetaString("other_field"));
        rowMeta.addValueMeta(new ValueMetaString("myprefix"));

        IdGeneratorStepData data = new IdGeneratorStepData();
        data.prefixFieldIndex = 1;

        String result = step.resolvePrefix(meta, data, rowMeta, new Object[] { "ignored", "HELLO" });

        assertEquals("HELLO", result);
    }

    // -----------------------------------------------------------------------
    // End-to-end via TestableStep: Parameter mode
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Parameter mode: same resolved value is used for every row in the run")
    void parameterMode_sameValueAcrossMultipleRows() throws Exception {
        TestableStep step = new TestableStep("run-4");
        step.setVariable("MY_PREFIX_PARAM", "ABCDE");
        IdGeneratorStepMeta meta = TestableStep.defaultMeta();
        meta.setPrefixSourceType(IdGeneratorStepMeta.SOURCE_PARAMETER);
        meta.setPrefixParameter("MY_PREFIX_PARAM");

        List<String> ids = step.generateIds(meta, 3);

        assertEquals(3, ids.size());
        for (String id : ids) {
            assertTrue(id.startsWith("ABCDE"), id);
        }
    }

    @Test
    @DisplayName("Parameter mode: an unset variable throws with the literal \"${NAME}\" placeholder visible in the message")
    void parameterMode_unsetVariable_throwsWithPlaceholderVisible() {
        TestableStep step = new TestableStep("run-5");
        // Deliberately not calling setVariable(): environmentSubstitute()
        // then returns "${NEVER_SET_ANYWHERE}" unresolved, exactly like
        // real Kettle does for a variable that was never set.
        IdGeneratorStepMeta meta = TestableStep.defaultMeta();
        meta.setPrefixSourceType(IdGeneratorStepMeta.SOURCE_PARAMETER);
        meta.setPrefixParameter("NEVER_SET_ANYWHERE");

        KettleException ex = assertThrows(KettleException.class, () -> step.generateIds(meta, 1));
        assertTrue(ex.getMessage().contains("${NEVER_SET_ANYWHERE}"), ex.getMessage());
    }

    @Test
    @DisplayName("Parameter mode: a wrong-length resolved value throws naming the source and the value")
    void parameterMode_wrongLength_throwsWithSourceAndValue() {
        TestableStep step = new TestableStep("run-6");
        step.setVariable("MY_PREFIX_PARAM", "TOOLONG");
        IdGeneratorStepMeta meta = TestableStep.defaultMeta();
        meta.setPrefixSourceType(IdGeneratorStepMeta.SOURCE_PARAMETER);
        meta.setPrefixParameter("MY_PREFIX_PARAM");

        KettleException ex = assertThrows(KettleException.class, () -> step.generateIds(meta, 1));
        assertTrue(ex.getMessage().contains("PARAMETER"), ex.getMessage());
        assertTrue(ex.getMessage().contains("TOOLONG"), ex.getMessage());
    }

    // -----------------------------------------------------------------------
    // End-to-end via TestableStep: Field mode
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Field mode: reads a different prefix per row from the named upstream field")
    void fieldMode_differentValuePerRow() throws Exception {
        TestableStep step = new TestableStep("run-7");
        RowMetaInterface rowMeta = new RowMeta();
        rowMeta.addValueMeta(new ValueMetaString("myprefix"));
        step.setInputRowLayout(rowMeta);
        step.addInputRow("ROW01");
        step.addInputRow("ROW02");
        step.addInputRow("ROW03");

        IdGeneratorStepMeta meta = TestableStep.defaultMeta();
        meta.setPrefixSourceType(IdGeneratorStepMeta.SOURCE_FIELD);
        meta.setPrefixField("myprefix");

        IdGeneratorStepData data = new IdGeneratorStepData();
        step.init(meta, data);
        while (step.processRow(meta, data)) { /* drain */ }

        List<Object[]> outputRows = step.getOutputRows();
        assertEquals(3, outputRows.size());
        assertTrue(step.getGeneratedId(0).startsWith("ROW01"));
        assertTrue(step.getGeneratedId(1).startsWith("ROW02"));
        assertTrue(step.getGeneratedId(2).startsWith("ROW03"));
    }

    @Test
    @DisplayName("Field mode: a field name not present in the incoming stream throws naming the field")
    void fieldMode_fieldNotFound_throwsNamingTheField() throws Exception {
        TestableStep step = new TestableStep("run-8");
        RowMetaInterface rowMeta = new RowMeta();
        rowMeta.addValueMeta(new ValueMetaString("some_other_field"));
        step.setInputRowLayout(rowMeta);
        step.addInputRow("value");

        IdGeneratorStepMeta meta = TestableStep.defaultMeta();
        meta.setPrefixSourceType(IdGeneratorStepMeta.SOURCE_FIELD);
        meta.setPrefixField("does_not_exist");

        IdGeneratorStepData data = new IdGeneratorStepData();
        step.init(meta, data);

        KettleException ex = assertThrows(KettleException.class, () -> step.processRow(meta, data));
        assertTrue(ex.getMessage().contains("does_not_exist"), ex.getMessage());
    }

    // -----------------------------------------------------------------------
    // ID format sanity check for a non-Manual source (format itself is
    // otherwise fully covered by IdFormatTest for the Manual case)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Field-sourced ID still has correct 20-char length and date segment")
    void fieldMode_idHasCorrectLengthAndDate() throws Exception {
        TestableStep step = new TestableStep("run-9");
        RowMetaInterface rowMeta = new RowMeta();
        rowMeta.addValueMeta(new ValueMetaString("myprefix"));
        step.setInputRowLayout(rowMeta);
        step.addInputRow("ROW01");

        IdGeneratorStepMeta meta = TestableStep.defaultMeta();
        meta.setPrefixSourceType(IdGeneratorStepMeta.SOURCE_FIELD);
        meta.setPrefixField("myprefix");

        IdGeneratorStepData data = new IdGeneratorStepData();
        step.init(meta, data);
        step.processRow(meta, data);

        String id = step.getGeneratedId(0);
        assertEquals(20, id.length());
        assertEquals(LocalDate.now().format(DATE_FMT), id.substring(5, 11));
    }
}
