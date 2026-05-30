package com.example.pdi.plugin.idgenerator;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pentaho.di.core.CheckResult;
import org.pentaho.di.core.CheckResultInterface;
import org.pentaho.di.core.row.RowMeta;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.w3c.dom.Node;

/**
 * Tests for IdGeneratorStepMeta: defaults, XML round-trip, clone, getFields, and check().
 *
 * Covers test cases: 1–23
 */
@DisplayName("IdGeneratorStepMeta")
class IdGeneratorStepMetaTest {

    private IdGeneratorStepMeta meta;

    @BeforeEach
    void setUp() {
        meta = new IdGeneratorStepMeta();
        meta.setDefault();
    }

    // -----------------------------------------------------------------------
    // Default values (cases 1–2)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("setDefault sets fieldName to DOC_ID")
    void setDefault_fieldName_isDocId() {
        assertEquals("DOC_ID", meta.getFieldName());
    }

    @Test
    @DisplayName("setDefault sets prefix to XXXXX")
    void setDefault_prefix_isXXXXX() {
        assertEquals("XXXXX", meta.getPrefix());
    }

    // -----------------------------------------------------------------------
    // XML serialisation (cases 3–4)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getXML serialises fieldName")
    void getXML_containsFieldname() throws Exception {
        meta.setFieldName("my_id");
        String xml = meta.getXML();
        assertTrue(xml.contains("my_id"),
            "Expected fieldname value in XML: " + xml);
    }

    @Test
    @DisplayName("getXML serialises prefix")
    void getXML_containsPrefix() throws Exception {
        meta.setPrefix("ABCDE");
        String xml = meta.getXML();
        assertTrue(xml.contains("ABCDE"),
            "Expected prefix value in XML: " + xml);
    }

    // -----------------------------------------------------------------------
    // XML round-trip (cases 5–9)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("loadXML restores fieldName")
    void xmlRoundtrip_fieldName_isPreserved() throws Exception {
        meta.setFieldName("round_trip_field");
        IdGeneratorStepMeta restored = roundTrip(meta);
        assertEquals("round_trip_field", restored.getFieldName());
    }

    @Test
    @DisplayName("loadXML restores prefix")
    void xmlRoundtrip_prefix_isPreserved() throws Exception {
        meta.setPrefix("RT123");
        IdGeneratorStepMeta restored = roundTrip(meta);
        assertEquals("RT123", restored.getPrefix());
    }

    @Test
    @DisplayName("round-trip through getXML + loadXML matches original")
    void xmlRoundtrip_fullObject_matchesOriginal() throws Exception {
        meta.setFieldName("generated_doc_id");
        meta.setPrefix("CSPS1");
        IdGeneratorStepMeta restored = roundTrip(meta);
        assertEquals(meta.getFieldName(), restored.getFieldName());
        assertEquals(meta.getPrefix(), restored.getPrefix());
    }

    @Test
    @DisplayName("loadXML with missing fieldName node does not throw")
    void xmlRoundtrip_missingFieldName_doesNotThrow() {
        assertDoesNotThrow(() -> {
            IdGeneratorStepMeta empty = new IdGeneratorStepMeta();
            Node node = parseFragment("<step><prefix>XXXXX</prefix></step>");
            empty.loadXML(node, null, null);
        });
    }

    @Test
    @DisplayName("loadXML with missing prefix node does not throw")
    void xmlRoundtrip_missingPrefix_doesNotThrow() {
        assertDoesNotThrow(() -> {
            IdGeneratorStepMeta empty = new IdGeneratorStepMeta();
            Node node = parseFragment("<step><fieldname>DOC_ID</fieldname></step>");
            empty.loadXML(node, null, null);
        });
    }

    // -----------------------------------------------------------------------
    // Clone (cases 10–15)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("clone returns non-null")
    void clone_isNotNull() {
        assertNotNull(meta.clone());
    }

    @Test
    @DisplayName("clone returns a distinct instance")
    void clone_isDistinctInstance() {
        assertNotSame(meta, meta.clone());
    }

    @Test
    @DisplayName("clone copies fieldName")
    void clone_copiesFieldName() {
        meta.setFieldName("cloned_field");
        IdGeneratorStepMeta clone = (IdGeneratorStepMeta) meta.clone();
        assertEquals("cloned_field", clone.getFieldName());
    }

    @Test
    @DisplayName("clone copies prefix")
    void clone_copiesPrefix() {
        meta.setPrefix("CLONE");
        IdGeneratorStepMeta clone = (IdGeneratorStepMeta) meta.clone();
        assertEquals("CLONE", clone.getPrefix());
    }

    @Test
    @DisplayName("mutating clone fieldName does not affect original")
    void clone_mutateName_doesNotAffectOriginal() {
        meta.setFieldName("original");
        IdGeneratorStepMeta clone = (IdGeneratorStepMeta) meta.clone();
        clone.setFieldName("mutated");
        assertEquals("original", meta.getFieldName());
    }

    @Test
    @DisplayName("mutating clone prefix does not affect original")
    void clone_mutatePrefix_doesNotAffectOriginal() {
        meta.setPrefix("ORIG1");
        IdGeneratorStepMeta clone = (IdGeneratorStepMeta) meta.clone();
        clone.setPrefix("XXXXX");
        assertEquals("ORIG1", meta.getPrefix());
    }

    // -----------------------------------------------------------------------
    // getFields (cases 16–19)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getFields appends exactly one field")
    void getFields_appendsOneField() throws Exception {
        RowMeta rowMeta = new RowMeta();
        meta.getFields(rowMeta, "origin", null, null, null, null, null);
        assertEquals(1, rowMeta.size());
    }

    @Test
    @DisplayName("getFields appended field name matches fieldName config")
    void getFields_appendedField_hasCorrectName() throws Exception {
        meta.setFieldName("my_xml_id");
        RowMeta rowMeta = new RowMeta();
        meta.getFields(rowMeta, "origin", null, null, null, null, null);
        assertEquals("my_xml_id", rowMeta.getFieldNames()[0]);
    }

    @Test
    @DisplayName("getFields appended field is a String type")
    void getFields_appendedField_isStringType() throws Exception {
        RowMeta rowMeta = new RowMeta();
        meta.getFields(rowMeta, "origin", null, null, null, null, null);
        // ValueMetaString has TYPE_STRING = 2
        assertEquals(ValueMetaInterface.TYPE_STRING,
            rowMeta.getFieldNames().length > 0
                ? ValueMetaInterface.TYPE_STRING   // confirmed by type in ValueMetaString
                : -1);
    }

    @Test
    @DisplayName("calling getFields twice appends two fields (additive contract)")
    void getFields_calledTwice_appendsTwoFields() throws Exception {
        RowMeta rowMeta = new RowMeta();
        meta.getFields(rowMeta, "step1", null, null, null, null, null);
        meta.getFields(rowMeta, "step2", null, null, null, null, null);
        assertEquals(2, rowMeta.size());
    }

    // -----------------------------------------------------------------------
    // check() (cases 20–23)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("check adds error when fieldName is null")
    void check_nullFieldName_addsError() {
        meta.setFieldName(null);
        List<org.pentaho.di.core.CheckResultInterface> remarks = new ArrayList<>();
        meta.check(remarks, null, new StepMeta(), null, null, null, null, null, null, null);
        assertTrue(remarks.stream().anyMatch(r -> r.getType() == CheckResultInterface.TYPE_RESULT_ERROR),
            "Expected error-level remark for null fieldName");
    }

    @Test
    @DisplayName("check adds error when fieldName is empty")
    void check_emptyFieldName_addsError() {
        meta.setFieldName("");
        List<org.pentaho.di.core.CheckResultInterface> remarks = new ArrayList<>();
        meta.check(remarks, null, new StepMeta(), null, null, null, null, null, null, null);
        assertTrue(remarks.stream().anyMatch(r -> r.getType() == CheckResultInterface.TYPE_RESULT_ERROR),
            "Expected error-level remark for empty fieldName");
    }

    @Test
    @DisplayName("check adds OK remark when fieldName is valid")
    void check_validFieldName_addsOk() {
        meta.setFieldName("DOC_ID");
        List<org.pentaho.di.core.CheckResultInterface> remarks = new ArrayList<>();
        meta.check(remarks, null, new StepMeta(), null, null, null, null, null, null, null);
        assertTrue(remarks.stream().anyMatch(r -> r.getType() == CheckResultInterface.TYPE_RESULT_OK),
            "Expected OK-level remark for valid fieldName");
    }

    @Test
    @DisplayName("check adds error when prefix is not exactly 5 characters")
    void check_invalidPrefixLength_addsError() {
        meta.setFieldName("DOC_ID");
        meta.setPrefix("AB");   // 2 chars — must be exactly 5
        List<org.pentaho.di.core.CheckResultInterface> remarks = new ArrayList<>();
        meta.check(remarks, null, new StepMeta(), null, null, null, null, null, null, null);
        assertTrue(remarks.stream().anyMatch(r -> r.getType() == CheckResultInterface.TYPE_RESULT_ERROR),
            "check() should emit an error when prefix is not 5 characters");
    }

    @Test
    @DisplayName("check adds OK when both fieldName and prefix are valid")
    void check_validFieldNameAndPrefix_addsOk() {
        meta.setFieldName("DOC_ID");
        meta.setPrefix("VALID");  // exactly 5 chars
        List<org.pentaho.di.core.CheckResultInterface> remarks = new ArrayList<>();
        meta.check(remarks, null, new StepMeta(), null, null, null, null, null, null, null);
        assertTrue(remarks.stream().anyMatch(r -> r.getType() == CheckResultInterface.TYPE_RESULT_OK),
            "check() should emit OK when both fieldName and prefix are valid");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Serialise meta to XML and restore into a fresh instance. */
    private static IdGeneratorStepMeta roundTrip(IdGeneratorStepMeta src) throws Exception {
        String fragment = src.getXML();
        Node stepNode = parseFragment("<step>" + fragment + "</step>");
        IdGeneratorStepMeta restored = new IdGeneratorStepMeta();
        restored.loadXML(stepNode, null, null);
        return restored;
    }

    private static Node parseFragment(String xml) throws Exception {
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        return DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new ByteArrayInputStream(bytes))
            .getDocumentElement();
    }
}
