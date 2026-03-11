package dev.rsems.photolaboutputsettingsreorderer;

import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

public class EditableOutputSettingCell extends ListCell<OutputSetting> {

    private final Runnable onDirty;
    private TextField textField;

    public EditableOutputSettingCell(Runnable onDirty) {
        this.onDirty = onDirty;
    }

    @Override
    public void startEdit() {
        if (!isEditable() || !getListView().isEditable()) return;
        super.startEdit();
        if (textField == null) createTextField();
        textField.setText(getItem().getOutputName());
        setGraphic(textField);
        setText(null);
        textField.requestFocus();
        textField.selectAll();
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        setText(getItem() == null ? "" : getItem().getOutputName());
        setGraphic(null);
    }

    @Override
    protected void updateItem(OutputSetting item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
        } else if (isEditing()) {
            if (textField != null) textField.setText(item.getOutputName());
            setText(null);
            setGraphic(textField);
        } else {
            setText(item.getOutputName());
            setGraphic(null);
        }
    }

    private void createTextField() {
        textField = new TextField();
        textField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) commitEdit(getItem());
            else if (event.getCode() == KeyCode.ESCAPE) cancelEdit();
        });
        textField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (wasFocused && !isFocused && isEditing()) commitEdit(getItem());
        });
    }

    @Override
    public void commitEdit(OutputSetting item) {
        if (textField != null) {
            String newName = textField.getText();
            if (item != null && !newName.equals(item.getOutputName())) {
                item.setOutputName(newName);
                onDirty.run();
            }
        }
        super.commitEdit(item);
        setText(item == null ? "" : item.getOutputName());
        setGraphic(null);
    }
}
