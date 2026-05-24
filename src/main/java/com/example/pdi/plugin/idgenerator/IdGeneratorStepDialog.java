package com.example.pdi.plugin.idgenerator;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.pentaho.di.core.Const;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStepMeta;
import org.pentaho.di.trans.step.StepDialogInterface;
import org.pentaho.di.ui.trans.step.BaseStepDialog;

/**
 * Spoon dialog for configuring the ID Generator step.
 *
 * Fields exposed to the user:
 *   - Step name      (standard in all PDI step dialogs)
 *   - Output Field Name – the row field that will hold the generated ID
 *   - Prefix (5 chars) – the fixed prefix placed at positions 1-5 of the ID
 */
public class IdGeneratorStepDialog extends BaseStepDialog implements StepDialogInterface {

  private static final Class<?> PKG = IdGeneratorStepMeta.class;

  private final IdGeneratorStepMeta input;

  private Text wFieldName;
  private Text wPrefix;

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

    // ---- Prefix ----
    Label wlPrefix = new Label(shell, SWT.RIGHT);
    wlPrefix.setText("DOC_ID Prefix");
    wlPrefix.setToolTipText("Exactly 5 characters prepended to every generated ID");
    props.setLook(wlPrefix);
    FormData fdlPrefix = new FormData();
    fdlPrefix.left  = new FormAttachment(0, 0);
    fdlPrefix.right = new FormAttachment(middle, -margin);
    fdlPrefix.top   = new FormAttachment(wFieldName, margin);
    wlPrefix.setLayoutData(fdlPrefix);

    wPrefix = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    props.setLook(wPrefix);
    wPrefix.addModifyListener(lsMod);
    wPrefix.setTextLimit(5);   // widget-level hard limit: user cannot type more than 5 chars
    FormData fdPrefix = new FormData();
    fdPrefix.left  = new FormAttachment(middle, 0);
    fdPrefix.right = new FormAttachment(100, 0);
    fdPrefix.top   = new FormAttachment(wFieldName, margin);
    wPrefix.setLayoutData(fdPrefix);

    // ---- OK / Cancel ----
    wOK     = new Button(shell, SWT.PUSH);
    wCancel = new Button(shell, SWT.PUSH);
    wOK.setText("OK");
    wCancel.setText("Cancel");

    BaseStepDialog.positionBottomButtons((org.eclipse.swt.widgets.Composite) shell, new Button[]{ wOK, wCancel }, margin, wPrefix);

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

  private void getData() {
    if (input.getFieldName() != null) {
      wFieldName.setText(input.getFieldName());
    }
    if (input.getPrefix() != null) {
      wPrefix.setText(input.getPrefix());
    }
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
    if (wPrefix.getText().length() != 5) {
      showError("Prefix must be exactly 5 characters.");
      return;
    }

    stepname = wStepname.getText();
    input.setFieldName(wFieldName.getText());
    input.setPrefix(wPrefix.getText());
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
