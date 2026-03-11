package dev.rsems.photolaboutputsettingsreorderer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class PhotoLabApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                PhotoLabApp.class.getResource("main-view.fxml"));
        Scene scene = new Scene(loader.load());
        stage.setTitle("PhotoLab Output Settings Reorderer");
        stage.setScene(scene);
        stage.show();
    }
}
