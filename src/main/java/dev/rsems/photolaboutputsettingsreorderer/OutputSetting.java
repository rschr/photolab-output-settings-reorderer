package dev.rsems.photolaboutputsettingsreorderer;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

public class OutputSetting {

    private static final String A_NS = "http://schemas.datacontract.org/2004/07/DxO.OpticsPro.OutputSettings";

    private final Element element;
    private final StringProperty outputName = new SimpleStringProperty();
    private String originalName;

    public OutputSetting(Element element) {
        this.element = element;
        NodeList nodes = element.getElementsByTagNameNS(A_NS, "OutputName");
        if (nodes.getLength() > 0) {
            outputName.set(nodes.item(0).getTextContent());
        }
        originalName = outputName.get();
        outputName.addListener((obs, oldVal, newVal) -> {
            NodeList nameNodes = element.getElementsByTagNameNS(A_NS, "OutputName");
            if (nameNodes.getLength() > 0) {
                nameNodes.item(0).setTextContent(newVal);
            }
        });
    }

    public Element getElement() {
        return element;
    }

    public String getOutputName() {
        return outputName.get();
    }

    public void setOutputName(String name) {
        outputName.set(name);
    }

    public StringProperty outputNameProperty() {
        return outputName;
    }

    public boolean isModified() {
        String current = outputName.get();
        return current != null ? !current.equals(originalName) : originalName != null;
    }

    public void resetModified() {
        originalName = outputName.get();
    }

    public List<String[]> getAllProperties() {
        List<String[]> props = new ArrayList<>();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                props.add(new String[]{child.getLocalName(), child.getTextContent().trim()});
            }
        }
        return props;
    }

    @Override
    public String toString() {
        return getOutputName();
    }
}
