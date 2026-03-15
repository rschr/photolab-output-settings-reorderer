package dev.rsems.photolaboutputsettingsreorderer;

import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.WindowEvent;

import java.io.File;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

public class MainController {

    // ── FXML fields ──────────────────────────────────────────────────────────

    @FXML private ResourceBundle resources;
    @FXML private Label   fileLabel;
    @FXML private Label   statusLabel;
    @FXML private ListView<OutputSetting> listView;
    @FXML private Button  moveUpButton;
    @FXML private Button  moveDownButton;
    @FXML private Button  removeButton;
    @FXML private Button  saveButton;
    @FXML private Button  revertButton;
    @FXML private Button  restoreButton;
    @FXML private Button  importButton;
    @FXML private Button  closeImportButton;
    @FXML private HBox    normalView;
    @FXML private StackPane centerStack;

    // ── State ─────────────────────────────────────────────────────────────────

    private final UserConfigService service = new UserConfigService();
    private final ObservableList<OutputSetting> items = FXCollections.observableArrayList(
            setting -> new Observable[]{ setting.outputNameProperty() });
    private final BooleanProperty dirty = new SimpleBooleanProperty(false);

    // Import mode
    private final ObservableList<ImportPair> importPairs = FXCollections.observableArrayList();
    private TableView<ImportPair> importTable;
    private TableColumn<ImportPair, ImportPair> importLeftCol;
    private TableColumn<ImportPair, ImportPair> importRightCol;
    private boolean inImportMode = false;
    private Path    importFilePath;
    private String  importPathDisplay;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ── Initialization ────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        listView.setItems(items);
        listView.setEditable(true);
        listView.setCellFactory(lv -> new EditableOutputSettingCell(
                this::markDirty,
                () -> setStatus(resources.getString("status.illegalChar")),
                this::showSettingInfoDialog));

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

        // Import button only visible when a file is loaded; hidden in import mode
        importButton.setVisible(false);
        importButton.setManaged(false);
        closeImportButton.setVisible(false);
        closeImportButton.setManaged(false);

        updateButtonStates(-1);
        setupImportTable();
        centerStack.getChildren().add(importTable);
    }

    // ── Normal mode: file loading ─────────────────────────────────────────────

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

        Map<String, Path> displayMap = buildDisplayMap(found);
        List<String> choices = displayMap.keySet().stream().sorted(Comparator.reverseOrder()).toList();
        showPathChoiceDialog(choices, displayMap,
                resources.getString("dialog.open.title"),
                resources.getString("dialog.open.header"),
                resources.getString("dialog.open.content"),
                this::openViaFileChooser,
                p -> loadFile(p, true));
    }

    private void openViaFileChooser() {
        File file = showConfigFileChooser(
                resources.getString("dialog.open.chooserTitle"),
                service.getConfigFile() != null ? service.getConfigFile().getParent() : null);
        if (file != null) loadFile(file.toPath(), false);
    }

    public void loadFile(Path path, boolean canonical) {
        service.setConfigFile(path);
        fileLabel.setText(canonical
                ? ".../" + (path.getParent() != null ? path.getParent().getFileName() + "/" : "")
                        + path.getFileName()
                : path.toString());
        exitImportMode();  // ensure clean state if re-opening
        importButton.setVisible(true);
        importButton.setManaged(true);
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
            setStatus(MessageFormat.format(resources.getString("status.loaded"), loaded.size()));
            if (inImportMode && importFilePath != null) {
                rebuildImportPairs();
            }
        } catch (Exception e) {
            showError(resources.getString("error.load.title"), e.getMessage());
        }
    }

    /** Called after reload() when already in import mode (e.g. after Revert). */
    private void rebuildImportPairs() {
        try {
            List<OutputSetting> importSettings = UserConfigService.loadSettingsFrom(importFilePath);
            importPairs.setAll(buildImportPairs(importSettings));
            // Refresh column headers in case fileLabel text changed
            importLeftCol.setText(MessageFormat.format(
                    resources.getString("importTable.col.import"), importPathDisplay));
            importRightCol.setText(MessageFormat.format(
                    resources.getString("importTable.col.current"), fileLabel.getText()));
        } catch (Exception e) {
            showError(resources.getString("error.load.title"), e.getMessage());
        }
    }

    // ── Normal mode: list editing ─────────────────────────────────────────────

    @FXML
    private void onMoveUp() {
        int idx = listView.getSelectionModel().getSelectedIndex();
        if (idx > 0) {
            items.add(idx - 1, items.remove(idx));
            listView.getSelectionModel().select(idx - 1);
        }
    }

    @FXML
    private void onMoveDown() {
        int idx = listView.getSelectionModel().getSelectedIndex();
        if (idx >= 0 && idx < items.size() - 1) {
            items.add(idx + 1, items.remove(idx));
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
            int sel = Math.min(idx, items.size() - 1);
            if (sel >= 0) listView.getSelectionModel().select(sel);
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
        File selected = showConfigFileChooser(
                resources.getString("dialog.restore.chooserTitle"),
                service.getConfigFile().getParent());
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

    // ── Import mode ───────────────────────────────────────────────────────────

    @FXML
    private void onImport() {
        List<Path> allFound = UserConfigService.findPhotoLabUserConfigs();
        // Candidates: other canonical configs in reverse order (most recent first)
        List<Path> candidates = allFound.stream()
                .filter(p -> !p.equals(service.getConfigFile()))
                .sorted(Comparator.reverseOrder())
                .toList();

        Path importPath;
        boolean canonical;

        if (candidates.isEmpty()) {
            File f = showConfigFileChooser(resources.getString("dialog.import.chooserTitle"), null);
            importPath = f != null ? f.toPath() : null;
            canonical  = false;
        } else if (candidates.size() == 1) {
            importPath = candidates.getFirst();
            canonical  = true;
        } else {
            Map<String, Path> displayMap = buildDisplayMap(candidates); // already descending
            List<String> choices = List.copyOf(displayMap.keySet());
            Path[] chosen = {null};
            boolean[] wantsChooser = {false};
            showPathChoiceDialog(choices, displayMap,
                    resources.getString("dialog.import.title"),
                    resources.getString("dialog.import.header"),
                    resources.getString("dialog.import.content"),
                    () -> wantsChooser[0] = true,
                    p -> chosen[0] = p);
            if (wantsChooser[0]) {
                File f = showConfigFileChooser(resources.getString("dialog.import.chooserTitle"),
                        service.getConfigFile().getParent());
                importPath = f != null ? f.toPath() : null;
                canonical  = false;
            } else {
                importPath = chosen[0];
                canonical  = importPath != null;
            }
        }

        if (importPath == null) return;

        try {
            List<OutputSetting> importSettings = UserConfigService.loadSettingsFrom(importPath);
            importFilePath = importPath;
            importPairs.setAll(buildImportPairs(importSettings));

            String colHeader = canonical
                    ? ".../" + (importPath.getParent() != null
                            ? importPath.getParent().getFileName() + "/" : "")
                            + importPath.getFileName()
                    : importPath.toString();
            importPathDisplay = colHeader;
            enterImportMode(colHeader);
        } catch (Exception e) {
            showError(resources.getString("error.load.title"), e.getMessage());
        }
    }

    @FXML
    private void onCloseImport() {
        exitImportMode();
        listView.refresh();
    }

    private void enterImportMode(String colHeader) {
        inImportMode = true;
        importLeftCol.setText(MessageFormat.format(
                resources.getString("importTable.col.import"), colHeader));
        importRightCol.setText(MessageFormat.format(
                resources.getString("importTable.col.current"), fileLabel.getText()));
        normalView.setVisible(false);
        normalView.setManaged(false);
        importTable.setVisible(true);
        importTable.setManaged(true);
        importButton.setVisible(false);
        importButton.setManaged(false);
        closeImportButton.setVisible(true);
        closeImportButton.setManaged(true);
    }

    private void exitImportMode() {
        inImportMode = false;
        importFilePath = null;
        importPathDisplay = null;
        importTable.setVisible(false);
        importTable.setManaged(false);
        normalView.setVisible(true);
        normalView.setManaged(true);
        closeImportButton.setVisible(false);
        closeImportButton.setManaged(false);
        importPairs.clear();
        // Bug 3: restore Import button whenever a file is loaded
        if (service.getConfigFile() != null) {
            importButton.setVisible(true);
            importButton.setManaged(true);
        }
    }

    private List<ImportPair> buildImportPairs(List<OutputSetting> importSettings) {
        Map<String, OutputSetting> importByName = new LinkedHashMap<>();
        for (OutputSetting s : importSettings) importByName.put(s.getOutputName(), s);

        Set<String> currentNames = items.stream()
                .map(OutputSetting::getOutputName)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<ImportPair> pairs = new ArrayList<>();
        for (OutputSetting current : items) {
            pairs.add(new ImportPair(importByName.get(current.getOutputName()), current));
        }
        for (OutputSetting imp : importSettings) {
            if (!currentNames.contains(imp.getOutputName())) {
                pairs.add(new ImportPair(imp, null));
            }
        }
        return pairs;
    }

    private void onImportAction(ImportPair pair) {
        if (pair == null || !pair.hasImportAction()) return;
        int index = importPairs.indexOf(pair);
        if (index < 0) return;
        try {
            if (pair.getCurrentSetting() == null) {
                // Case 3: add new setting to current
                OutputSetting newSetting = service.adoptSetting(pair.getImportSetting());
                items.add(newSetting);
                importPairs.set(index, new ImportPair(pair.getImportSetting(), newSetting));
            } else {
                // Case 1.2: overwrite current with import
                service.overwriteSetting(pair.getCurrentSetting(), pair.getImportSetting());
                importPairs.set(index, new ImportPair(pair.getImportSetting(), pair.getCurrentSetting()));
            }
            dirty.set(true);
        } catch (Exception e) {
            showError(resources.getString("error.load.title"), e.getMessage());
        }
    }

    // ── Import table setup ────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    private void setupImportTable() {
        importTable = new TableView<>(importPairs);
        importTable.setEditable(false);
        importTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        importTable.setFixedCellSize(28);   // uniform row height for all rows
        importTable.setVisible(false);
        importTable.setManaged(false);

        // Left column: import setting name (bold when different)
        importLeftCol = new TableColumn<>();
        importLeftCol.setSortable(false);
        importLeftCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        importLeftCol.setCellFactory(col -> new TableCell<>() {
            { setAlignment(javafx.geometry.Pos.CENTER_LEFT); }
            @Override
            protected void updateItem(ImportPair pair, boolean empty) {
                super.updateItem(pair, empty);
                if (empty || pair == null || pair.getImportSetting() == null) {
                    setText(null); setStyle("");
                } else {
                    setText(pair.getImportSetting().getOutputName());
                    setStyle(pair.isDifferent() ? "-fx-font-weight: bold;" : "");
                }
            }
        });

        // Middle column: → button (opens diff dialog for case 1.2, direct import for case 3)
        Button importAllBtn = new Button("\u21d2");
        importAllBtn.setOnAction(e -> onImportAll());
        TableColumn<ImportPair, ImportPair> actionCol = new TableColumn<>("");
        actionCol.setGraphic(importAllBtn);
        actionCol.setSortable(false);
        actionCol.setResizable(false);
        actionCol.setMinWidth(44);
        actionCol.setMaxWidth(44);
        actionCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("\u2192");
            {
                setAlignment(javafx.geometry.Pos.CENTER);
                btn.setMaxWidth(Double.MAX_VALUE);
                btn.setOnAction(e -> onArrowButton(getItem()));
            }
            @Override
            protected void updateItem(ImportPair pair, boolean empty) {
                super.updateItem(pair, empty);
                setGraphic(empty || pair == null || !pair.hasImportAction() ? null : btn);
            }
        });

        // Right column: current setting name
        importRightCol = new TableColumn<>();
        importRightCol.setSortable(false);
        importRightCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        importRightCol.setCellFactory(col -> new TableCell<>() {
            { setAlignment(javafx.geometry.Pos.CENTER_LEFT); }
            @Override
            protected void updateItem(ImportPair pair, boolean empty) {
                super.updateItem(pair, empty);
                setText(empty || pair == null || pair.getCurrentSetting() == null
                        ? null : pair.getCurrentSetting().getOutputName());
            }
        });

        importTable.getColumns().addAll(importLeftCol, actionCol, importRightCol);
    }

    /** → button handler: shows diff-confirm dialog for case 1.2, direct import for case 3. */
    private void onArrowButton(ImportPair pair) {
        if (pair == null || !pair.hasImportAction()) return;
        if (pair.getImportSetting() != null
                && pair.getCurrentSetting() != null
                && pair.isDifferent()) {
            showDiffConfirmDialog(pair);
        } else {
            onImportAction(pair);
        }
    }

    @SuppressWarnings("deprecation")
    private void showDiffConfirmDialog(ImportPair pair) {
        List<String[]> diffs = pair.getDifferences();

        TableView<String[]> table = new TableView<>();
        table.setEditable(false);
        // Equal-distribution policy so all three columns widen when dialog is resized
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setFixedCellSize(26);

        TableColumn<String[], String> propCol = new TableColumn<>(
                resources.getString("importDiff.header.property"));
        propCol.setSortable(false);
        propCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()[0]));

        TableColumn<String[], String> impCol = new TableColumn<>(
                resources.getString("importDiff.header.import"));
        impCol.setSortable(false);
        impCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()[1]));

        TableColumn<String[], String> curCol = new TableColumn<>(
                resources.getString("importDiff.header.current"));
        curCol.setSortable(false);
        curCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()[2]));

        table.getColumns().addAll(propCol, impCol, curCol);
        table.getItems().addAll(diffs);
        // header ≈ 32px + fixed-cell rows + 8px padding to avoid a spurious scrollbar
        table.setPrefHeight(Math.min(diffs.size() * 26 + 40, 400));
        table.setMaxWidth(Double.MAX_VALUE);
        table.setMaxHeight(Double.MAX_VALUE);

        String settingName = pair.getImportSetting().getOutputName();

        ButtonType importBtn = new ButtonType(
                resources.getString("importDiff.dialog.importBtn"),
                ButtonBar.ButtonData.OK_DONE);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(listView.getScene().getWindow());
        dialog.setResizable(true);
        dialog.setTitle(resources.getString("importDiff.dialog.title"));
        dialog.setHeaderText(MessageFormat.format(
                resources.getString("importDiff.dialog.header"), settingName));
        dialog.getDialogPane().setContent(table);
        dialog.getDialogPane().setMinWidth(620);
        dialog.getDialogPane().setPrefWidth(700);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, importBtn);
        dialog.showAndWait()
              .filter(r -> r == importBtn)
              .ifPresent(r -> onImportAction(pair));
    }

    private void onImportAll() {
        List<ImportPair> actionPairs = importPairs.stream()
                .filter(ImportPair::hasImportAction).toList();
        if (actionPairs.isEmpty()) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(importTable.getScene().getWindow());
        confirm.setTitle(resources.getString("dialog.importAll.title"));
        confirm.setHeaderText(MessageFormat.format(
                resources.getString("dialog.importAll.header"), actionPairs.size()));
        confirm.setContentText(resources.getString("dialog.importAll.content"));
        confirm.showAndWait().filter(r -> r == ButtonType.OK)
               .ifPresent(r -> actionPairs.forEach(this::onImportAction));
    }

    @SuppressWarnings("deprecation")
    private void showSettingInfoDialog(OutputSetting setting) {
        List<String[]> props = setting.getAllProperties();

        TableView<String[]> table = new TableView<>();
        table.setEditable(false);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setFixedCellSize(26);

        TableColumn<String[], String> propCol = new TableColumn<>(
                resources.getString("settingInfo.col.property"));
        propCol.setSortable(false);
        propCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()[0]));

        TableColumn<String[], String> valCol = new TableColumn<>(
                resources.getString("settingInfo.col.value"));
        valCol.setSortable(false);
        valCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()[1]));

        table.getColumns().addAll(propCol, valCol);
        table.getItems().addAll(props);
        table.setPrefHeight(Math.min(props.size() * 26 + 40, 400));
        table.setMaxWidth(Double.MAX_VALUE);
        table.setMaxHeight(Double.MAX_VALUE);

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(listView.getScene().getWindow());
        dialog.setResizable(true);
        dialog.setTitle(resources.getString("settingInfo.dialog.title"));
        dialog.setHeaderText(MessageFormat.format(
                resources.getString("settingInfo.dialog.header"), setting.getOutputName()));
        dialog.getDialogPane().setContent(table);
        dialog.getDialogPane().setMinWidth(480);
        dialog.getDialogPane().setPrefWidth(560);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Builds an ordered label→path map from a list of paths (preserves list order). */
    private Map<String, Path> buildDisplayMap(List<Path> paths) {
        Map<String, Path> map = new LinkedHashMap<>();
        for (Path p : paths) {
            Path parent = p.getParent();
            Path grandParent = parent != null ? parent.getParent() : null;
            String label = (grandParent != null ? grandParent.getFileName() + " / " : "")
                    + (parent != null ? parent.getFileName() + " / " : "")
                    + p.getFileName();
            map.put(label, p);
        }
        return map;
    }

    /** Shows a ChoiceDialog with an "Other…" button; invokes {@code onOther} or {@code onSelect}. */
    private void showPathChoiceDialog(List<String> choices, Map<String, Path> displayMap,
            String title, String header, String content,
            Runnable onOther, java.util.function.Consumer<Path> onSelect) {
        ChoiceDialog<String> dialog = new ChoiceDialog<>(choices.getFirst(), choices);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(content);

        ButtonType otherButton = new ButtonType(
                resources.getString("dialog.open.otherButton"), ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().add(otherButton);

        boolean[] wantsOther = {false};
        dialog.getDialogPane().lookupButton(otherButton)
                .setOnMousePressed(e -> wantsOther[0] = true);

        Optional<String> result = dialog.showAndWait();
        if (wantsOther[0]) {
            onOther.run();
        } else {
            result.filter(displayMap::containsKey).map(displayMap::get).ifPresent(onSelect);
        }
    }

    /** Opens a generic config file chooser; returns the selected file or null. */
    private File showConfigFileChooser(String title, Path initialDir) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(resources.getString("dialog.open.filterDesc"), "*"));
        if (initialDir != null && initialDir.toFile().isDirectory()) {
            chooser.setInitialDirectory(initialDir.toFile());
        }
        return chooser.showOpenDialog(listView.getScene().getWindow());
    }

    private void markDirty() { dirty.set(true); }

    private void setStatus(String message) { statusLabel.setText(message); }

    private void updateButtonStates(int selectedIndex) {
        boolean has = selectedIndex >= 0;
        moveUpButton.setDisable(!has || selectedIndex == 0);
        moveDownButton.setDisable(!has || selectedIndex >= items.size() - 1);
        removeButton.setDisable(!has);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
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
}
