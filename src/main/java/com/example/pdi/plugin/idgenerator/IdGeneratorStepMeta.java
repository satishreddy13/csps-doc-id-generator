package com.example.pdi.plugin.idgenerator;

import java.util.List;

import org.pentaho.di.core.CheckResult;
import org.pentaho.di.core.CheckResultInterface;
import org.pentaho.di.core.annotations.Step;
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.exception.KettleXMLException;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.core.row.value.ValueMetaString;
import org.pentaho.di.core.variables.VariableSpace;
import org.pentaho.di.core.xml.XMLHandler;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.repository.ObjectId;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStepMeta;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;
import org.pentaho.metastore.api.IMetaStore;
import org.w3c.dom.Node;

/**
 * Metadata for the ID Generator step.
 *
 * Generated ID format (20 characters total):
 *   [1-5]   prefix        – 5 chars, user-configured
 *   [6-11]  date          – YYMMDD (6 chars)
 *   [12-15] time in base36 – HHMMSS as integer converted to base36, zero-padded to 4 chars
 *   [16-20] sequence      – per-transformation counter 00000-99999, zero-padded to 5 digits
 *
 * The @Step annotation registers this class with PDI's plugin registry at
 * startup (annotation-based discovery). plugin.xml in the plugin folder acts
 * as the fallback descriptor when loaded from a directory.
 */
@Step(
  id = "IdGeneratorStep",
  name = "CSPS DOC_ID Generator",
  description = "Generates a structured 20-character unique ID per row",
  categoryDescription = "i18n:org.pentaho.di.trans.step:BaseStep.Category.Transform",
  image = "ui/images/GEN.svg"
)
public class IdGeneratorStepMeta extends BaseStepMeta implements StepMetaInterface {

  private static final Class<?> PKG = IdGeneratorStepMeta.class;

  // -----------------------------------------------------------------------
  // Prefix source type constants
  // -----------------------------------------------------------------------

  /** Prefix is the literal text typed into the dialog - today's only behavior. */
  public static final String SOURCE_MANUAL = "MANUAL";

  /** Prefix comes from a Kettle variable/named parameter, resolved once per run. */
  public static final String SOURCE_PARAMETER = "PARAMETER";

  /** Prefix comes from a field in the incoming row stream, resolved per row. */
  public static final String SOURCE_FIELD = "FIELD";

  // -----------------------------------------------------------------------
  // Configurable fields (persisted in transformation XML / repository)
  // -----------------------------------------------------------------------

  /** Name of the output row field that receives the generated ID. */
  private String fieldName;

  /** Exactly 5-character prefix placed at positions 1-5 of the generated ID. */
  private String prefix;

  /** Which of the three prefix sources above is active. Defaults to MANUAL
   *  (via setDefault() for new steps, and via loadXML()/readRep() for steps
   *  saved before this option existed) so existing transformations are
   *  completely unaffected by this feature. */
  private String prefixSourceType;

  /** Bare variable/parameter name (no ${} syntax) used when
   *  prefixSourceType == SOURCE_PARAMETER. Resolved via environmentSubstitute(). */
  private String prefixParameter;

  /** Name of an upstream row field used when prefixSourceType == SOURCE_FIELD. */
  private String prefixField;

  // -----------------------------------------------------------------------
  // Accessors
  // -----------------------------------------------------------------------

  public String getFieldName() { return fieldName; }
  public void setFieldName(String fieldName) { this.fieldName = fieldName; }

  public String getPrefix() { return prefix; }
  public void setPrefix(String prefix) { this.prefix = prefix; }

  public String getPrefixSourceType() { return prefixSourceType; }
  public void setPrefixSourceType(String prefixSourceType) { this.prefixSourceType = prefixSourceType; }

  public String getPrefixParameter() { return prefixParameter; }
  public void setPrefixParameter(String prefixParameter) { this.prefixParameter = prefixParameter; }

  public String getPrefixField() { return prefixField; }
  public void setPrefixField(String prefixField) { this.prefixField = prefixField; }

  // -----------------------------------------------------------------------
  // StepMetaInterface – lifecycle
  // -----------------------------------------------------------------------

  @Override
  public void setDefault() {
    fieldName        = "DOC_ID";
    prefix           = "XXXXX";
    prefixSourceType = SOURCE_MANUAL;
    prefixParameter  = "";
    prefixField      = "";
  }

  @Override
  public Object clone() {
    IdGeneratorStepMeta clone = (IdGeneratorStepMeta) super.clone();
    clone.fieldName        = this.fieldName;
    clone.prefix           = this.prefix;
    clone.prefixSourceType = this.prefixSourceType;
    clone.prefixParameter  = this.prefixParameter;
    clone.prefixField      = this.prefixField;
    return clone;
  }

  // -----------------------------------------------------------------------
  // StepMetaInterface – field contribution
  // -----------------------------------------------------------------------

  /**
   * Appends the output field to the outgoing row metadata so downstream
   * steps can see it in field-name dropdowns.
   */
  @Override
  public void getFields(
      RowMetaInterface rowMeta,
      String origin,
      RowMetaInterface[] info,
      StepMeta nextStep,
      VariableSpace space,
      Repository repository,
      IMetaStore metaStore) throws KettleStepException {

    if (fieldName != null && !fieldName.isEmpty()) {
      ValueMetaInterface v = new ValueMetaString(fieldName);
      v.setLength(20);
      v.setOrigin(origin);
      rowMeta.addValueMeta(v);
    }
  }

  // -----------------------------------------------------------------------
  // StepMetaInterface – XML serialisation
  // -----------------------------------------------------------------------

  @Override
  public String getXML() throws KettleException {
    StringBuilder xml = new StringBuilder();
    xml.append(XMLHandler.addTagValue("fieldname",        fieldName));
    xml.append(XMLHandler.addTagValue("prefix",           prefix));
    xml.append(XMLHandler.addTagValue("prefixSourceType", prefixSourceType));
    xml.append(XMLHandler.addTagValue("prefixParameter",  prefixParameter));
    xml.append(XMLHandler.addTagValue("prefixField",      prefixField));
    return xml.toString();
  }

  @Override
  public void loadXML(Node stepnode, List<DatabaseMeta> databases, IMetaStore metaStore)
      throws KettleXMLException {
    try {
      fieldName = XMLHandler.getTagValue(stepnode, "fieldname");
      prefix    = XMLHandler.getTagValue(stepnode, "prefix");

      // prefixSourceType (and the two fields below) won't exist in a .ktr
      // saved before this feature existed - XMLHandler.getTagValue returns
      // null for a missing tag, so falling back to SOURCE_MANUAL here
      // reproduces the exact old behavior for every existing transformation.
      String loadedSourceType = XMLHandler.getTagValue(stepnode, "prefixSourceType");
      prefixSourceType = (loadedSourceType != null) ? loadedSourceType : SOURCE_MANUAL;

      String loadedParameter = XMLHandler.getTagValue(stepnode, "prefixParameter");
      prefixParameter = (loadedParameter != null) ? loadedParameter : "";

      String loadedField = XMLHandler.getTagValue(stepnode, "prefixField");
      prefixField = (loadedField != null) ? loadedField : "";
    } catch (Exception e) {
      throw new KettleXMLException("Unable to load IdGeneratorStep metadata from XML", e);
    }
  }

  // -----------------------------------------------------------------------
  // StepMetaInterface – repository serialisation
  // -----------------------------------------------------------------------

  @Override
  public void readRep(Repository rep, IMetaStore metaStore,
      ObjectId id_step, List<DatabaseMeta> databases) throws KettleException {
    try {
      fieldName = rep.getStepAttributeString(id_step, "fieldname");
      prefix    = rep.getStepAttributeString(id_step, "prefix");

      // Same backward-compat reasoning as loadXML(): a step saved to a
      // repository before this feature existed has no value for this
      // attribute at all - getStepAttributeString returns null/empty in
      // that case, so fall back to SOURCE_MANUAL to preserve old behavior.
      String loadedSourceType = rep.getStepAttributeString(id_step, "prefixSourceType");
      prefixSourceType = (loadedSourceType != null && !loadedSourceType.isEmpty())
          ? loadedSourceType : SOURCE_MANUAL;

      String loadedParameter = rep.getStepAttributeString(id_step, "prefixParameter");
      prefixParameter = (loadedParameter != null) ? loadedParameter : "";

      String loadedField = rep.getStepAttributeString(id_step, "prefixField");
      prefixField = (loadedField != null) ? loadedField : "";
    } catch (Exception e) {
      throw new KettleException("Unable to load IdGeneratorStep metadata from repository", e);
    }
  }

  @Override
  public void saveRep(Repository rep, IMetaStore metaStore,
      ObjectId id_transformation, ObjectId id_step) throws KettleException {
    try {
      rep.saveStepAttribute(id_transformation, id_step, "fieldname",        fieldName);
      rep.saveStepAttribute(id_transformation, id_step, "prefix",           prefix);
      rep.saveStepAttribute(id_transformation, id_step, "prefixSourceType", prefixSourceType);
      rep.saveStepAttribute(id_transformation, id_step, "prefixParameter",  prefixParameter);
      rep.saveStepAttribute(id_transformation, id_step, "prefixField",      prefixField);
    } catch (Exception e) {
      throw new KettleException("Unable to save IdGeneratorStep metadata to repository", e);
    }
  }

  // -----------------------------------------------------------------------
  // StepMetaInterface – validation
  // -----------------------------------------------------------------------

  @Override
  public void check(
      List<CheckResultInterface> remarks,
      TransMeta transMeta,
      StepMeta stepMeta,
      RowMetaInterface prev,
      String[] input,
      String[] output,
      RowMetaInterface info,
      VariableSpace space,
      Repository repository,
      IMetaStore metaStore) {

    if (fieldName == null || fieldName.trim().isEmpty()) {
      remarks.add(new CheckResult(
        CheckResultInterface.TYPE_RESULT_ERROR,
        BaseMessages.getString(PKG, "IdGeneratorStepMeta.CheckResult.MissingFieldName"),
        stepMeta));
      return;
    }

    // A step saved before this feature existed loads with prefixSourceType
    // == SOURCE_MANUAL (see loadXML()/readRep()), so this branch - and
    // therefore the exact same validation as before this feature existed -
    // is exactly what runs for every pre-existing transformation.
    String sourceType = (prefixSourceType != null) ? prefixSourceType : SOURCE_MANUAL;

    if (SOURCE_MANUAL.equals(sourceType)) {
      if (prefix == null || prefix.length() != 5) {
        remarks.add(new CheckResult(
          CheckResultInterface.TYPE_RESULT_ERROR,
          BaseMessages.getString(PKG, "IdGeneratorStepMeta.CheckResult.InvalidPrefix"),
          stepMeta));
        return;
      }
    } else if (SOURCE_PARAMETER.equals(sourceType)) {
      if (prefixParameter == null || prefixParameter.trim().isEmpty()) {
        remarks.add(new CheckResult(
          CheckResultInterface.TYPE_RESULT_ERROR,
          BaseMessages.getString(PKG, "IdGeneratorStepMeta.CheckResult.MissingPrefixParameter"),
          stepMeta));
        return;
      }
      // The parameter's resolved value isn't known until runtime, so its
      // length can't be checked here - that happens in processRow().
    } else if (SOURCE_FIELD.equals(sourceType)) {
      if (prefixField == null || prefixField.trim().isEmpty()) {
        remarks.add(new CheckResult(
          CheckResultInterface.TYPE_RESULT_ERROR,
          BaseMessages.getString(PKG, "IdGeneratorStepMeta.CheckResult.MissingPrefixField"),
          stepMeta));
        return;
      }
      if (prev != null && prev.size() > 0 && prev.indexOfValue(prefixField) < 0) {
        remarks.add(new CheckResult(
          CheckResultInterface.TYPE_RESULT_ERROR,
          BaseMessages.getString(PKG, "IdGeneratorStepMeta.CheckResult.PrefixFieldNotFound", prefixField),
          stepMeta));
        return;
      }
    }

    remarks.add(new CheckResult(
      CheckResultInterface.TYPE_RESULT_OK,
      BaseMessages.getString(PKG, "IdGeneratorStepMeta.CheckResult.OK"),
      stepMeta));
  }

  // -----------------------------------------------------------------------
  // StepMetaInterface – factory methods
  // -----------------------------------------------------------------------

  @Override
  public StepInterface getStep(StepMeta stepMeta, StepDataInterface stepDataInterface,
      int copyNr, TransMeta transMeta, Trans trans) {
    return new IdGeneratorStep(stepMeta, stepDataInterface, copyNr, transMeta, trans);
  }

  @Override
  public StepDataInterface getStepData() {
    return new IdGeneratorStepData();
  }
}
