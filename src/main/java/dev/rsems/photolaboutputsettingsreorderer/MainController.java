package dev.rsems.photolaboutputsettingsreorderer;

import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.WindowEvent;

import java.io.File;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

public class MainController {

    @FXML private ResourceBundle resources;
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
    private final ObservableList<OutputSetting> items = FXCollections.observableArrayList(
            setting -> new Observable[]{ setting.outputNameProperty() });
    private final BooleanProperty dirty = new SimpleBooleanProperty(false);

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @FXML
    public void initialize() {
        listView.setItems(items);
        listView.setEditable(true);
        listView.setCellFactory(lv -> new EditableOutputSettingCell(
                this::markDirty,
                () -> setStatus(resources.getString("status.illegalChar"))));

        listView.getSelectionModel().selectedIndexProperty().addListener(
                (obs, oldIdx, newIdx) -> updateButtonStates(newIdx.intValue()));

        items.addListener((ListChangeListener<OutputSetting>) change -> {
            while (change.next()) {
                if (change.wasRemoved() || change.wasAdded() || change.wasUpdated()) {
                    dirty.set(true);
                }
            }
        });

        saveButton.disableProperty().bind(dirty.not());
        revertButton.disableProperty().bind(dirty.not());

        updateButtonStates(-1);
    }

    @FXML
    private void onOpen() {
        List<Path> found = UserConfigService.findPhotoLabUserConfigs();

        if (found.isEmpty()) {
            openViaFileChooser();
            return;
        }

        if (found.size() == 1) {
            loadFile(found.getFirst(), true);
            return;
        }

        Map<String, Path> displayMap = new LinkedHashMap<>();
        for (Path p : found) {
            Path parent = p.getParent();
            Path grandParent = parent != null ? parent.getParent() : null;
            String label = (grandParent != null ? grandParent.getFileName() + " / " : "")
                    + (parent != null ? parent.getFileName() + " / " : "")
                    + p.getFileName();
            displayMap.put(label, p);
        }

        List<String> choices = displayMap.keySet().stream()
                .sorted(Comparator.reverseOrder())
                .toList();
        ChoiceDialog<String> dialog = new ChoiceDialog<>(choices.getFirst(), choices);
        dialog.setTitle(resources.getString("dialog.open.title"));
        dialog.setHeaderText(resources.getString("dialog.open.header"));
        dialog.setContentText(resources.getString("dialog.open.content"));

        ButtonType otherButton = new ButtonType(resources.getString("dialog.open.otherButton"), ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().add(otherButton);

        boolean[] wantsFileChooser = {false};
        dialog.getDialogPane().lookupButton(otherButton)
                .setOnMousePressed(e -> wantsFileChooser[0] = true);

        Optional<String> result = dialog.showAndWait();
        if (wantsFileChooser[0]) {
            openViaFileChooser();
        } else {
            result.filter(displayMap::containsKey)
                  .map(displayMap::get)
                  .ifPresent(p -> loadFile(p, true));
        }
    }

    private void openViaFileChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(resources.getString("dialog.open.chooserTitle"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(resources.getString("dialog.open.filterDesc"), "*"));
        if (service.getConfigFile() != null) {
            chooser.setInitialDirectory(service.getConfigFile().getParent().toFile());
        }
        File file = chooser.showOpenDialog(listView.getScene().getWindow());
        if (file != null) {
            loadFile(file.toPath(), false);
        }
    }

    public void loadFile(Path path, boolean canonical) {
        service.setConfigFile(path);
        if (canonical) {
            Path parent = path.getParent();
            String display = ".../" + (parent != null ? parent.getFileName() + "/" : "")
                    + path.getFileName();
            fileLabel.setText(display);
        } else {
            fileLabel.setText(path.toString());
        }
        reload();
    }

    public void handleCloseRequest(WindowEvent event) {
        if (!dirty.get()) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(listView.getScene().getWindow());
        confirm.setTitle(resources.getString("dialog.exit.title"));
        confirm.setHeaderText(resources.getString("dialog.exit.header"));
        confirm.setContentText(resources.getString("dialog.exit.content"));
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            event.consume();
        }
    }

    private void reload() {
        try {
            List<OutputSetting> loaded = service.load();
            dirty.set(false);
            items.clear();
            items.addAll(loaded);
            dirty.set(false);
            updateButtonStates(listView.getSelectionModel().getSelectedIndex());
            setStatus(MessageFormat.format(resources.getString("status.loaded"), loaded.size()));
        } catch (Exception e) {
            showError(resources.getString("error.load.title"), e.getMessage());
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
        confirm.setTitle(resources.getString("dialog.remove.title"));
        confirm.setHeaderText(MessageFormat.format(resources.getString("dialog.remove.header"),
                items.get(idx).getOutputName()));
        confirm.setContentText(resources.getString("dialog.remove.content"));
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
            items.forEach(OutputSetting::resetModified);
            listView.refresh();
            setStatus(MessageFormat.format(resources.getString("status.saved"),
                    LocalTime.now().format(TIME_FMT)));
        } catch (Exception e) {
            showError(resources.getString("error.save.title"), e.getMessage());
        }
    }

    @FXML
    private void onRevert() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(resources.getString("dialog.revert.title"));
        confirm.setHeaderText(resources.getString("dialog.revert.header"));
        confirm.setContentText(resources.getString("dialog.revert.content"));
        confirm.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
            reload();
            setStatus(resources.getString("status.reverted"));
        });
    }

    @FXML
    private void onRestoreBackup() {
        if (service.getConfigFile() == null) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle(resources.getString("dialog.restore.chooserTitle"));
        chooser.setInitialDirectory(service.getConfigFile().getParent().toFile());
        File selected = chooser.showOpenDialog(listView.getScene().getWindow());
        if (selected == null) return;

        String name = selected.getName();
        if (!name.startsWith("user.config")) {
            Alert warn = new Alert(Alert.AlertType.WARNING);
            warn.setTitle(resources.getString("dialog.restore.unexpectedTitle"));
            warn.setHeaderText(MessageFormat.format(
                    resources.getString("dialog.restore.unexpectedHeader"), name));
            warn.setContentText(resources.getString("dialog.restore.unexpectedContent"));
            warn.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
            if (warn.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(resources.getString("dialog.restore.confirmTitle"));
        confirm.setHeaderText(MessageFormat.format(
                resources.getString("dialog.restore.confirmHeader"), name));
        confirm.setContentText(resources.getString("dialog.restore.confirmContent"));
        confirm.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
            try {
                service.backup();
                service.restore(selected.toPath());
                reload();
                setStatus(MessageFormat.format(resources.getString("status.restored"),
                        name, LocalTime.now().format(TIME_FMT)));
            } catch (Exception e) {
                showError(resources.getString("error.restore.title"), e.getMessage());
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
