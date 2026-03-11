package dev.rsems.photolaboutputsettingsreorderer;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class MainController {

    @FXML private Label fileLabel;
    @FXML private ListView<OutputSetting> listView;
    @FXML private Button moveUpButton;
    @FXML private Button moveDownButton;
    @FXML private Button removeButton;
    @FXML private Button saveButton;
    @FXML private Button revertButton;
    @FXML private Button restoreButton;

    private final UserConfigService service = new UserConfigService();
    private final ObservableList<OutputSetting> items = FXCollections.observableArrayList();
    private final BooleanProperty dirty = new SimpleBooleanProperty(false);

    @FXML
    public void initialize() {
        listView.setItems(items);
        listView.setEditable(true);
        listView.setCellFactory(lv -> new EditableOutputSettingCell(this::markDirty));

        listView.getSelectionModel().selectedIndexProperty().addListener(
                (obs, oldIdx, newIdx) -> updateButtonStates(newIdx.intValue()));

        items.addListener((ListChangeListener<OutputSetting>) change -> {
            if (!change.next()) return;
            // Only mark dirty for reorder/remove, not the initial addAll
            if (dirty.get() || change.wasRemoved() || change.wasAdded()) {
                dirty.set(true);
            }
        });

        saveButton.disableProperty().bind(dirty.not());
        revertButton.disableProperty().bind(dirty.not());

        updateButtonStates(-1);
    }

    @FXML
    private void onOpen() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open user.config");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Config files", "user.config", "*.config"));
        File file = chooser.showOpenDialog(listView.getScene().getWindow());
        if (file != null) {
            loadFile(file.toPath());
        }
    }

    public void loadFile(Path path) {
        service.setConfigFile(path);
        fileLabel.setText(path.toString());
        reload();
    }

    private void reload() {
        try {
            List<OutputSetting> loaded = service.load();
            dirty.set(false);
            items.clear();
            items.addAll(loaded);
            dirty.set(false);
            updateButtonStates(listView.getSelectionModel().getSelectedIndex());
        } catch (Exception e) {
            showError("Failed to load file", e.getMessage());
        }
    }

    @FXML
    private void onMoveUp() {
        int idx = listView.getSelectionModel().getSelectedIndex();
        if (idx > 0) {
            OutputSetting item = items.remove(idx);
            items.add(idx - 1, item);
            listView.getSelectionModel().select(idx - 1);
        }
    }

    @FXML
    private void onMoveDown() {
        int idx = listView.getSelectionModel().getSelectedIndex();
        if (idx >= 0 && idx < items.size() - 1) {
            OutputSetting item = items.remove(idx);
            items.add(idx + 1, item);
            listView.getSelectionModel().select(idx + 1);
        }
    }

    @FXML
    private void onRemove() {
        int idx = listView.getSelectionModel().getSelectedIndex();
        if (idx < 0) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remove Option");
        confirm.setHeaderText("Remove \"" + items.get(idx).getOutputName() + "\"?");
        confirm.setContentText("This will remove the export option from the list.");
        confirm.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
            items.remove(idx);
            int newSel = Math.min(idx, items.size() - 1);
            if (newSel >= 0) listView.getSelectionModel().select(newSel);
        });
    }

    @FXML
    private void onSave() {
        if (service.getConfigFile() == null) return;
        try {
            service.save(List.copyOf(items));
            dirty.set(false);
        } catch (Exception e) {
            showError("Failed to save", e.getMessage());
        }
    }

    @FXML
    private void onRevert() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Revert Changes");
        confirm.setHeaderText("Discard all changes?");
        confirm.setContentText("All unsaved changes will be lost and the list will be reloaded from the file.");
        confirm.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> reload());
    }

    @FXML
    private void onRestoreBackup() {
        if (service.getConfigFile() == null) return;
        try {
            List<Path> backups = service.listBackups();
            if (backups.isEmpty()) {
                new Alert(Alert.AlertType.INFORMATION, "No backups found.").showAndWait();
                return;
            }
            ChoiceDialog<Path> dialog = new ChoiceDialog<>(backups.getFirst(), backups);
            dialog.setTitle("Restore Backup");
            dialog.setHeaderText("Select a backup to restore:");
            dialog.setContentText("Backup:");
            Optional<Path> choice = dialog.showAndWait();
            choice.ifPresent(backup -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Confirm Restore");
                confirm.setHeaderText("Restore from " + backup.getFileName() + "?");
                confirm.setContentText(
                        "The current user.config will first be backed up, then overwritten with the selected backup.");
                confirm.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
                    try {
                        service.backup();
                        service.restore(backup);
                        reload();
                    } catch (Exception e) {
                        showError("Restore failed", e.getMessage());
                    }
                });
            });
        } catch (Exception e) {
            showError("Failed to list backups", e.getMessage());
        }
    }

    private void markDirty() {
        dirty.set(true);
    }

    private void updateButtonStates(int selectedIndex) {
        boolean hasSelection = selectedIndex >= 0;
        moveUpButton.setDisable(!hasSelection || selectedIndex == 0);
        moveDownButton.setDisable(!hasSelection || selectedIndex >= items.size() - 1);
        removeButton.setDisable(!hasSelection);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
