package dev.rsems.photolaboutputsettingsreorderer;

import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;

import java.util.function.Consumer;

public class EditableOutputSettingCell extends ListCell<OutputSetting> {

    private static final int MAX_NAME_LENGTH = 50;
    private static final String ILLEGAL_CHARS = "\\/:*?\"<>|";

    private final Runnable onDirty;
    private final Runnable onIllegalChar;
    private final Button infoBtn;
    private TextField textField;

    public EditableOutputSettingCell(Runnable onDirty, Runnable onIllegalChar,
                                     Consumer<OutputSetting> onInfo) {
        this.onDirty = onDirty;
        this.onIllegalChar = onIllegalChar;
        infoBtn = new Button("i");
        infoBtn.setStyle("-fx-background-radius: 1em; -fx-font-style: italic; "
                + "-fx-min-width: 22; -fx-min-height: 22; "
                + "-fx-max-width: 22; -fx-max-height: 22;");
        infoBtn.setOnAction(e -> { if (getItem() != null) onInfo.accept(getItem()); });
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
        OutputSetting item = getItem();
        setText(item == null ? "" : item.getOutputName());
        setGraphic(item != null ? infoBtn : null);
        applyStyle(item);
    }

    @Override
    protected void updateItem(OutputSetting item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            setStyle("");
        } else if (isEditing()) {
            if (textField != null) textField.setText(item.getOutputName());
            setText(null);
            setGraphic(textField);
        } else {
            setText(item.getOutputName());
            setGraphic(infoBtn);
            applyStyle(item);
        }
    }

    private void applyStyle(OutputSetting item) {
        if (item != null && item.isModified()) {
            setStyle("-fx-font-weight: bold;");
        } else {
            setStyle("");
        }
    }

    private void createTextField() {
        textField = new TextField();
        textField.setTextFormatter(new TextFormatter<>(change -> {
            for (char c : change.getText().toCharArray()) {
                if (c < 32 || ILLEGAL_CHARS.indexOf(c) >= 0) {
                    onIllegalChar.run();
                    return null;
                }
            }
            if (change.getControlNewText().length() > MAX_NAME_LENGTH) return null;
            return change;
        }));
        textField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                commitEdit(getItem());
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                cancelEdit();
                event.consume();
            }
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
        setGraphic(item != null ? infoBtn : null);
        applyStyle(item);
    }
}
