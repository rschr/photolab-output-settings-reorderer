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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MainController {

    @FXML private Label fileLabel;
    @FXML private Label statusLabel;
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

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @FXML
    public void initialize() {
        listView.setItems(items);
        listView.setEditable(true);
        listView.setCellFactory(lv -> new EditableOutputSettingCell(this::markDirty));

        listView.getSelectionModel().selectedIndexProperty().addListener(
                (obs, oldIdx, newIdx) -> updateButtonStates(newIdx.intValue()));

        items.addListener((ListChangeListener<OutputSetting>) change -> {
            if (!change.next()) return;
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
        // Try to find PhotoLab user configs automatically
        List<Path> found = UserConfigService.findPhotoLabUserConfigs();

        if (found.isEmpty()) {
            openViaFileChooser();
            return;
        }

        if (found.size() == 1) {
            loadFile(found.getFirst());
            return;
        }

        // Multiple installs found — let user pick, with an "Other…" option
        Map<String, Path> displayMap = new LinkedHashMap<>();
        for (Path p : found) {
            // Build a readable label: show the two parent segments, e.g. "StrongName_xxx / 9.5.0.610"
            Path parent = p.getParent();
            Path grandParent = parent != null ? parent.getParent() : null;
            String label = (grandParent != null ? grandParent.getFileName() + " / " : "")
                    + (parent != null ? parent.getFileName() + " / " : "")
                    + p.getFileName();
            displayMap.put(label, p);
        }

        List<String> choices = List.copyOf(displayMap.keySet());
        ChoiceDialog<String> dialog = new ChoiceDialog<>(choices.getFirst(), choices);
        dialog.setTitle("Open PhotoLab Config");
        dialog.setHeaderText("Multiple PhotoLab installations found.\nSelect the user.config to open:");
        dialog.setContentText("Installation:");

        // Add an "Other…" button to fall back to file chooser
        ButtonType otherButton = new ButtonType("Other…", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().add(otherButton);

        // Track whether "Other…" was clicked before the dialog closes
        boolean[] wantsFileChooser = {false};
        dialog.getDialogPane().lookupButton(otherButton)
                .setOnMousePressed(e -> wantsFileChooser[0] = true);

        Optional<String> result = dialog.showAndWait();
        if (wantsFileChooser[0]) {
            openViaFileChooser();
        } else {
            result.filter(displayMap::containsKey)
                  .map(displayMap::get)
                  .ifPresent(this::loadFile);
        }
    }

    private void openViaFileChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open user.config");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PhotoLab config (user.config)", "*"));
        if (service.getConfigFile() != null) {
            chooser.setInitialDirectory(service.getConfigFile().getParent().toFile());
        }
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
            setStatus("Loaded " + loaded.size() + " output settings.");
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
            setStatus("Saved successfully at " + LocalTime.now().format(TIME_FMT)
                    + "  (backup created)");
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
        confirm.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
            reload();
            setStatus("Reverted to saved file.");
        });
    }

    @FXML
    private void onRestoreBackup() {
        if (service.getConfigFile() == null) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Backup to Restore");
        chooser.setInitialDirectory(service.getConfigFile().getParent().toFile());
        // No extension filter — show all files; the directory naturally contains only user.config* files
        File selected = chooser.showOpenDialog(listView.getScene().getWindow());
        if (selected == null) return;

        String name = selected.getName();
        if (!name.startsWith("user.config")) {
            Alert warn = new Alert(Alert.AlertType.WARNING);
            warn.setTitle("Unexpected File");
            warn.setHeaderText("\"" + name + "\" does not look like a user.config backup.");
            warn.setContentText("Only files whose name starts with 'user.config' should be restored. Proceed anyway?");
            warn.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
            if (warn.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Restore");
        confirm.setHeaderText("Restore from \"" + name + "\"?");
        confirm.setContentText(
                "The current user.config will first be backed up, then overwritten with the selected file.");
        confirm.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
            try {
                service.backup();
                service.restore(selected.toPath());
                reload();
                setStatus("Restored from \"" + name + "\" at " + LocalTime.now().format(TIME_FMT) + ".");
            } catch (Exception e) {
                showError("Restore failed", e.getMessage());
            }
        });
    }

    private void markDirty() {
        dirty.set(true);
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
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
