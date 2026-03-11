module dev.rsems.photolaboutputsettingsreorderer {
	requires javafx.controls;
	requires javafx.fxml;
	requires java.xml;

	opens dev.rsems.photolaboutputsettingsreorderer to javafx.fxml;
	exports dev.rsems.photolaboutputsettingsreorderer;
}