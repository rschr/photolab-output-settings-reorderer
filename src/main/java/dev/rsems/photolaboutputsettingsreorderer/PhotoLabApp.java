package dev.rsems.photolaboutputsettingsreorderer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class PhotoLabApp extends Application {

    public static final String VERSION = "0.1.0";

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                PhotoLabApp.class.getResource("main-view.fxml"));
        Scene scene = new Scene(loader.load());
        stage.setTitle("PhotoLab Output Settings Reorderer " + VERSION);
        stage.setScene(scene);
        stage.show();
    }
}
