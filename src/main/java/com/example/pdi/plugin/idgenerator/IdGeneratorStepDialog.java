package com.example.pdi.plugin.idgenerator;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.pentaho.di.core.Const;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStepMeta;
import org.pentaho.di.trans.step.StepDialogInterface;
import org.pentaho.di.ui.trans.step.BaseStepDialog;

/**
 * Spoon dialog for configuring the ID Generator step.
 *
 * Fields exposed to the user:
 *   - Step name         (standard in all PDI step dialogs)
 *   - Output Field Name – the row field that will hold the generated ID
 *   - Prefix Source     – Manual / Parameter or Variable / Field from stream
 *       - Manual:    exactly 5 literal characters, same as before this option existed
 *       - Parameter: a variable/parameter name, resolved once per run
 *       - Field:     an upstream row field name, resolved per row
 *
 * All dialog text is literal strings, not BaseMessages.getString() lookups -
 * matching this dialog's existing convention (see "Replace i18n lookups
 * with literal strings in dialog" in the project history).
 */
public class IdGeneratorStepDialog extends BaseStepDialog implements StepDialogInterface {

  private static final String[] PREFIX_SOURCE_TYPES = {
    IdGeneratorStepMeta.SOURCE_MANUAL,
    IdGeneratorStepMeta.SOURCE_PARAMETER,
    IdGeneratorStepMeta.SOURCE_FIELD
  };

  private final IdGeneratorStepMeta input;

  private Text wFieldName;

  private Combo wPrefixSource;
  private StackLayout prefixStack;
  private Composite prefixStackComposite;
  private Composite manualPanel;
  private Composite parameterPanel;
  private Composite fieldPanel;
  private Text wPrefix;
  private Text wPrefixParameter;
  private Combo wPrefixField;

  // -----------------------------------------------------------------------
  // Constructor
  // -----------------------------------------------------------------------

  public IdGeneratorStepDialog(Shell parent, Object baseStepMeta,
      TransMeta transMeta, String stepname) {
    super(parent, (BaseStepMeta) baseStepMeta, transMeta, stepname);
    input = (IdGeneratorStepMeta) baseStepMeta;
  }

  // -----------------------------------------------------------------------
  // StepDialogInterface
  // -----------------------------------------------------------------------

  @Override
  public String open() {
    Shell   parent  = getParent();
    Display display = parent.getDisplay();

    shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX);
    props.setLook(shell);
    setShellImage(shell, input);

    // Mark step changed whenever any widget is edited
    ModifyListener lsMod = (e) -> input.setChanged();
    changed = input.hasChanged();

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth  = Const.FORM_MARGIN;
    formLayout.marginHeight = Const.FORM_MARGIN;
    shell.setLayout(formLayout);
    shell.setText("CSPS DOC_ID Generator");

    int middle = props.getMiddlePct();
    int margin  = Const.MARGIN;

    // ---- Step name (mandatory first widget by PDI convention) ----
    wlStepname = new Label(shell, SWT.RIGHT);
    wlStepname.setText("Step Name");
    props.setLook(wlStepname);
    fdlStepname = new FormData();
    fdlStepname.left  = new FormAttachment(0, 0);
    fdlStepname.right = new FormAttachment(middle, -margin);
    fdlStepname.top   = new FormAttachment(0, margin);
    wlStepname.setLayoutData(fdlStepname);

    wStepname = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    wStepname.setText(stepname);
    props.setLook(wStepname);
    wStepname.addModifyListener(lsMod);
    fdStepname = new FormData();
    fdStepname.left  = new FormAttachment(middle, 0);
    fdStepname.top   = new FormAttachment(0, margin);
    fdStepname.right = new FormAttachment(100, 0);
    wStepname.setLayoutData(fdStepname);

    // ---- Output Field Name ----
    Label wlFieldName = new Label(shell, SWT.RIGHT);
    wlFieldName.setText("Field Name");
    wlFieldName.setToolTipText("The name of the row field that will receive the generated ID");
    props.setLook(wlFieldName);
    FormData fdlFieldName = new FormData();
    fdlFieldName.left  = new FormAttachment(0, 0);
    fdlFieldName.right = new FormAttachment(middle, -margin);
    fdlFieldName.top   = new FormAttachment(wStepname, margin);
    wlFieldName.setLayoutData(fdlFieldName);

    wFieldName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    props.setLook(wFieldName);
    wFieldName.addModifyListener(lsMod);
    FormData fdFieldName = new FormData();
    fdFieldName.left  = new FormAttachment(middle, 0);
    fdFieldName.right = new FormAttachment(100, 0);
    fdFieldName.top   = new FormAttachment(wStepname, margin);
    wFieldName.setLayoutData(fdFieldName);

    // ---- Prefix Source ----
    Label wlPrefixSource = new Label(shell, SWT.RIGHT);
    wlPrefixSource.setText("Prefix Source");
    wlPrefixSource.setToolTipText("Where the 5-character prefix comes from");
    props.setLook(wlPrefixSource);
    FormData fdlPrefixSource = new FormData();
    fdlPrefixSource.left  = new FormAttachment(0, 0);
    fdlPrefixSource.right = new FormAttachment(middle, -margin);
    fdlPrefixSource.top   = new FormAttachment(wFieldName, margin);
    wlPrefixSource.setLayoutData(fdlPrefixSource);

    wPrefixSource = new Combo(shell, SWT.READ_ONLY | SWT.DROP_DOWN);
    wPrefixSource.setItems(new String[] { "Manual", "Parameter or Variable", "Field from stream" });
    props.setLook(wPrefixSource);
    FormData fdPrefixSource = new FormData();
    fdPrefixSource.left  = new FormAttachment(middle, 0);
    fdPrefixSource.right = new FormAttachment(100, 0);
    fdPrefixSource.top   = new FormAttachment(wFieldName, margin);
    wPrefixSource.setLayoutData(fdPrefixSource);

    // ---- Prefix Source detail panels (one visible at a time) ----
    prefixStackComposite = new Composite(shell, SWT.NONE);
    props.setLook(prefixStackComposite);
    prefixStack = new StackLayout();
    prefixStackComposite.setLayout(prefixStack);
    FormData fdPrefixStack = new FormData();
    fdPrefixStack.left  = new FormAttachment(0, 0);
    fdPrefixStack.right = new FormAttachment(100, 0);
    fdPrefixStack.top   = new FormAttachment(wPrefixSource, margin);
    prefixStackComposite.setLayoutData(fdPrefixStack);

    // Manual panel: exactly the same widget/behavior as before this option existed.
    manualPanel = new Composite(prefixStackComposite, SWT.NONE);
    props.setLook(manualPanel);
    manualPanel.setLayout(new FormLayout());
    Label wlPrefix = new Label(manualPanel, SWT.RIGHT);
    wlPrefix.setText("DOC_ID Prefix");
    wlPrefix.setToolTipText("Exactly 5 characters prepended to every generated ID");
    props.setLook(wlPrefix);
    FormData fdlPrefix = new FormData();
    fdlPrefix.left  = new FormAttachment(0, 0);
    fdlPrefix.right = new FormAttachment(middle, -margin);
    fdlPrefix.top   = new FormAttachment(0, 0);
    wlPrefix.setLayoutData(fdlPrefix);
    wPrefix = new Text(manualPanel, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    props.setLook(wPrefix);
    wPrefix.addModifyListener(lsMod);
    wPrefix.setTextLimit(5);   // widget-level hard limit: user cannot type more than 5 chars
    FormData fdPrefix = new FormData();
    fdPrefix.left  = new FormAttachment(middle, 0);
    fdPrefix.right = new FormAttachment(100, 0);
    fdPrefix.top   = new FormAttachment(0, 0);
    wPrefix.setLayoutData(fdPrefix);

    // Parameter panel: bare variable/parameter name, resolved once per run.
    parameterPanel = new Composite(prefixStackComposite, SWT.NONE);
    props.setLook(parameterPanel);
    parameterPanel.setLayout(new FormLayout());
    Label wlPrefixParameter = new Label(parameterPanel, SWT.RIGHT);
    wlPrefixParameter.setText("Parameter/Variable Name");
    wlPrefixParameter.setToolTipText("Name only - no ${...} syntax needed");
    props.setLook(wlPrefixParameter);
    FormData fdlPrefixParameter = new FormData();
    fdlPrefixParameter.left  = new FormAttachment(0, 0);
    fdlPrefixParameter.right = new FormAttachment(middle, -margin);
    fdlPrefixParameter.top   = new FormAttachment(0, 0);
    wlPrefixParameter.setLayoutData(fdlPrefixParameter);
    wPrefixParameter = new Text(parameterPanel, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    props.setLook(wPrefixParameter);
    wPrefixParameter.addModifyListener(lsMod);
    FormData fdPrefixParameter = new FormData();
    fdPrefixParameter.left  = new FormAttachment(middle, 0);
    fdPrefixParameter.right = new FormAttachment(100, 0);
    fdPrefixParameter.top   = new FormAttachment(0, 0);
    wPrefixParameter.setLayoutData(fdPrefixParameter);
    Label wlParameterNote = new Label(parameterPanel, SWT.WRAP);
    wlParameterNote.setText("Resolved once when the step starts - the same prefix is used for every "
        + "row in this run. Its value must be exactly 5 characters, or the step will fail with a "
        + "clear error naming the parameter and the value it resolved to.");
    props.setLook(wlParameterNote);
    FormData fdParameterNote = new FormData();
    fdParameterNote.left  = new FormAttachment(0, 0);
    fdParameterNote.right = new FormAttachment(100, 0);
    fdParameterNote.top   = new FormAttachment(wPrefixParameter, margin);
    wlParameterNote.setLayoutData(fdParameterNote);

    // Field panel: an upstream row field, resolved per row. Editable combo -
    // populated from the stream when available, but the user can still type
    // a name manually if detection is stale, matching other PDI dialogs.
    fieldPanel = new Composite(prefixStackComposite, SWT.NONE);
    props.setLook(fieldPanel);
    fieldPanel.setLayout(new FormLayout());
    Label wlPrefixField = new Label(fieldPanel, SWT.RIGHT);
    wlPrefixField.setText("Field Name");
    wlPrefixField.setToolTipText("An upstream row field to read the prefix from - resolved per row, "
        + "so different rows can get different prefixes. Its value must be exactly 5 characters, "
        + "or the step will fail on that row.");
    props.setLook(wlPrefixField);
    FormData fdlPrefixField = new FormData();
    fdlPrefixField.left  = new FormAttachment(0, 0);
    fdlPrefixField.right = new FormAttachment(middle, -margin);
    fdlPrefixField.top   = new FormAttachment(0, 0);
    wlPrefixField.setLayoutData(fdlPrefixField);
    wPrefixField = new Combo(fieldPanel, SWT.SINGLE | SWT.LEFT | SWT.BORDER | SWT.DROP_DOWN);
    try {
      wPrefixField.setItems(transMeta.getPrevStepFields(stepname).getFieldNames());
    } catch (Exception e) {
      // Upstream fields not resolvable yet (e.g. hop not connected) -
      // leave the combo empty; the user can still type a name manually.
    }
    props.setLook(wPrefixField);
    wPrefixField.addModifyListener(lsMod);
    FormData fdPrefixField = new FormData();
    fdPrefixField.left  = new FormAttachment(middle, 0);
    fdPrefixField.right = new FormAttachment(100, 0);
    fdPrefixField.top   = new FormAttachment(0, 0);
    wPrefixField.setLayoutData(fdPrefixField);

    wPrefixSource.addSelectionListener(new SelectionAdapter() {
      @Override public void widgetSelected(SelectionEvent e) {
        input.setChanged();
        showPrefixSourcePanel();
      }
    });

    prefixStack.topControl = manualPanel;

    // ---- OK / Cancel ----
    wOK     = new Button(shell, SWT.PUSH);
    wCancel = new Button(shell, SWT.PUSH);
    wOK.setText("OK");
    wCancel.setText("Cancel");

    BaseStepDialog.positionBottomButtons((org.eclipse.swt.widgets.Composite) shell, new Button[]{ wOK, wCancel }, margin, prefixStackComposite);

    wOK.addSelectionListener(new SelectionAdapter() {
      @Override public void widgetSelected(SelectionEvent e) { ok(); }
    });
    wCancel.addSelectionListener(new SelectionAdapter() {
      @Override public void widgetSelected(SelectionEvent e) { cancel(); }
    });

    // Window close (X) behaves like Cancel
    shell.addShellListener(new ShellAdapter() {
      @Override public void shellClosed(ShellEvent e) { cancel(); }
    });

    getData();
    setSize();
    input.setChanged(changed);

    shell.open();
    while (!shell.isDisposed()) {
      if (!display.readAndDispatch()) {
        display.sleep();
      }
    }

    return stepname;
  }

  // -----------------------------------------------------------------------
  // Populate widgets from meta
  // -----------------------------------------------------------------------

  private void showPrefixSourcePanel() {
    switch (wPrefixSource.getSelectionIndex()) {
      case 1:
        prefixStack.topControl = parameterPanel;
        break;
      case 2:
        prefixStack.topControl = fieldPanel;
        break;
      default:
        prefixStack.topControl = manualPanel;
        break;
    }
    prefixStackComposite.layout();
  }

  private void getData() {
    if (input.getFieldName() != null) {
      wFieldName.setText(input.getFieldName());
    }
    if (input.getPrefix() != null) {
      wPrefix.setText(input.getPrefix());
    }
    if (input.getPrefixParameter() != null) {
      wPrefixParameter.setText(input.getPrefixParameter());
    }
    if (input.getPrefixField() != null) {
      wPrefixField.setText(input.getPrefixField());
    }

    String sourceType = input.getPrefixSourceType();
    int sourceIndex = 0; // default: Manual - matches steps saved before this option existed
    for (int i = 0; i < PREFIX_SOURCE_TYPES.length; i++) {
      if (PREFIX_SOURCE_TYPES[i].equals(sourceType)) {
        sourceIndex = i;
        break;
      }
    }
    wPrefixSource.select(sourceIndex);
    showPrefixSourcePanel();

    wStepname.selectAll();
    wStepname.setFocus();
  }

  // -----------------------------------------------------------------------
  // OK / Cancel
  // -----------------------------------------------------------------------

  private void ok() {
    if (wFieldName.getText().trim().isEmpty()) {
      showError("Output field name must not be empty.");
      return;
    }

    String sourceType = PREFIX_SOURCE_TYPES[wPrefixSource.getSelectionIndex()];

    // Only Manual's length is checkable here - Parameter/Field values
    // aren't known until the step actually runs (see processRow()).
    if (IdGeneratorStepMeta.SOURCE_MANUAL.equals(sourceType)) {
      if (wPrefix.getText().length() != 5) {
        showError("Prefix must be exactly 5 characters.");
        return;
      }
    } else if (IdGeneratorStepMeta.SOURCE_PARAMETER.equals(sourceType)) {
      if (wPrefixParameter.getText().trim().isEmpty()) {
        showError("Parameter/Variable name must not be empty.");
        return;
      }
    } else if (IdGeneratorStepMeta.SOURCE_FIELD.equals(sourceType)) {
      if (wPrefixField.getText().trim().isEmpty()) {
        showError("Field name must not be empty.");
        return;
      }
    }

    stepname = wStepname.getText();
    input.setFieldName(wFieldName.getText());
    input.setPrefixSourceType(sourceType);
    // All three are saved regardless of which is currently active, so
    // switching the dropdown back and forth before hitting OK never loses
    // what was typed into the other panels.
    input.setPrefix(wPrefix.getText());
    input.setPrefixParameter(wPrefixParameter.getText());
    input.setPrefixField(wPrefixField.getText());
    dispose();
  }

  private void cancel() {
    stepname = null;
    input.setChanged(changed);
    dispose();
  }

  private void showError(String message) {
    MessageBox mb = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
    mb.setMessage(message);
    mb.open();
  }
}
