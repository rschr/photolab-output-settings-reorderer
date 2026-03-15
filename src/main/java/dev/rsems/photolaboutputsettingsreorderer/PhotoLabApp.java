package dev.rsems.photolaboutputsettingsreorderer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

public class PhotoLabApp extends Application {

    public static final String VERSION = "0.3.0";

    @Override
    public void start(Stage stage) throws IOException {
        ResourceBundle bundle = ResourceBundle.getBundle(
                "dev.rsems.photolaboutputsettingsreorderer.messages",
                Locale.getDefault());
        FXMLLoader loader = new FXMLLoader(
                PhotoLabApp.class.getResource("main-view.fxml"), bundle);
        Scene scene = new Scene(loader.load());
        MainController controller = loader.getController();
        stage.setTitle("PhotoLab Output Settings Reorderer " + VERSION);
        stage.setScene(scene);
        stage.setOnCloseRequest(controller::handleCloseRequest);
        try {
            stage.getIcons().add(new Image(
                    Objects.requireNonNull(PhotoLabApp.class.getResourceAsStream("icon.ico"))));
        } catch (Exception ignored) {}
        stage.show();
    }
}
