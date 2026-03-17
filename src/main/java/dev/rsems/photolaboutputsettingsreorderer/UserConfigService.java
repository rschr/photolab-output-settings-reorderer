package dev.rsems.photolaboutputsettingsreorderer;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class UserConfigService {

    private static final String XPATH_SETTINGS_BASE =
            "/configuration/userSettings/DxO.PhotoLab.Properties.Settings/setting[@name='%s']/value";
    /** Setting name used in PhotoLab 9.6 and later. */
    private static final String SETTING_NAME_96  = "FileExportSettings";
    /** Setting name used in PhotoLab 9.5 and earlier. */
    private static final String SETTING_NAME_95  = "OutputSettings";

    private static final DateTimeFormatter BACKUP_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private Path configFile;
    private Document outerDoc;
    private Document innerDoc;
    private Element valueElement;

    public void setConfigFile(Path path) {
        this.configFile = path;
    }

    public Path getConfigFile() {
        return configFile;
    }

    public List<OutputSetting> load() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        outerDoc = db.parse(configFile.toFile());

        XPath xp = XPathFactory.newInstance().newXPath();
        valueElement = (Element) xp.evaluate(
                XPATH_SETTINGS_BASE.formatted(SETTING_NAME_96), outerDoc, XPathConstants.NODE);
        if (valueElement == null) {
            valueElement = (Element) xp.evaluate(
                    XPATH_SETTINGS_BASE.formatted(SETTING_NAME_95), outerDoc, XPathConstants.NODE);
        }
        if (valueElement == null) {
            throw new IllegalStateException(
                    "Neither FileExportSettings nor OutputSettings found in " + configFile);
        }

        String embeddedXml = valueElement.getTextContent();
        try (InputStream is = new ByteArrayInputStream(embeddedXml.getBytes(StandardCharsets.UTF_8))) {
            innerDoc = db.parse(is);
        }

        NodeList anyTypes = innerDoc.getDocumentElement().getChildNodes();
        List<OutputSetting> result = new ArrayList<>();
        for (int i = 0; i < anyTypes.getLength(); i++) {
            Node n = anyTypes.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                result.add(new OutputSetting((Element) n));
            }
        }
        return result;
    }

    public void save(List<OutputSetting> settings) throws Exception {
        backup();

        // Rebuild ArrayOfanyType from current settings order
        Element firstElement = settings.getFirst().getElement();
        Document innerDoc = firstElement.getOwnerDocument();
        Element root = innerDoc.getDocumentElement();

        // Remove all existing anyType children
        NodeList children = root.getChildNodes();
        List<Node> toRemove = new ArrayList<>();
        for (int i = 0; i < children.getLength(); i++) {
            toRemove.add(children.item(i));
        }
        for (Node n : toRemove) {
            root.removeChild(n);
        }

        // Re-append in new order
        for (OutputSetting s : settings) {
            root.appendChild(s.getElement());
        }

        // Serialize inner doc to string
        String serialized = serializeInner(innerDoc);

        // Update the value element in the outer doc
        valueElement.setTextContent(serialized);

        // Write outer doc back to file
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.ENCODING, "utf-8");
        t.setOutputProperty(OutputKeys.INDENT, "no");
        t.transform(new DOMSource(outerDoc), new StreamResult(configFile.toFile()));
    }

    public void backup() throws Exception {
        String timestamp = LocalDateTime.now().format(BACKUP_TIMESTAMP);
        Path backup = configFile.resolveSibling(configFile.getFileName() + "." + timestamp);
        Files.copy(configFile, backup, StandardCopyOption.REPLACE_EXISTING);
    }

    public List<Path> listBackups() throws Exception {
        Path dir = configFile.getParent();
        String prefix = configFile.getFileName() + ".";
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.getFileName().toString().startsWith(prefix))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .toList();
        }
    }

    public void restore(Path backup) throws Exception {
        Files.copy(backup, configFile, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Searches the platform-specific DxO installation directory for user.config files
     * inside subdirectories matching {@code DxO.PhotoLab.exe_StrongName_*}.
     * <p>
     * Windows: {@code %LOCALAPPDATA%\DxO\DxO.PhotoLab.exe_StrongName_*\}<br>
     * Linux (test): {@code ~/WindowsData/Fototechnik/DxO/DxO.PhotoLab.exe_StrongName_*\}
     */
    public static List<Path> findPhotoLabUserConfigs() {
        String os = System.getProperty("os.name", "").toLowerCase();
        Path baseDir = os.contains("win")
                ? Path.of(System.getenv().getOrDefault("LOCALAPPDATA",
                          System.getProperty("user.home") + "\\AppData\\Local"), "DxO")
                : Path.of(System.getProperty("user.home"), "WindowsData", "Fototechnik", "DxO");

        if (!Files.isDirectory(baseDir)) return List.of();

        try (Stream<Path> topLevel = Files.list(baseDir)) {
            return topLevel
                    .filter(p -> Files.isDirectory(p)
                            && p.getFileName().toString().startsWith("DxO.PhotoLab.exe_StrongName_"))
                    .flatMap(strongNameDir -> {
                        try {
                            return Files.walk(strongNameDir, 2)
                                    .filter(p -> Files.isRegularFile(p)
                                            && p.getFileName().toString().equals("user.config"));
                        } catch (IOException e) {
                            return Stream.empty();
                        }
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Loads settings from an arbitrary path without changing this service's state.
     */
    public static List<OutputSetting> loadSettingsFrom(Path path) throws Exception {
        UserConfigService tmp = new UserConfigService();
        tmp.setConfigFile(path);
        return tmp.load();
    }

    /**
     * Replaces all child elements of {@code target}'s element with imported copies
     * of {@code source}'s child elements, adopting them into the current inner document.
     */
    public void overwriteSetting(OutputSetting target, OutputSetting source) {
        Element targetEl = target.getElement();
        Element sourceEl = source.getElement();
        while (targetEl.hasChildNodes()) {
            targetEl.removeChild(targetEl.getFirstChild());
        }
        NodeList children = sourceEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            targetEl.appendChild(innerDoc.importNode(children.item(i), true));
        }
    }

    /**
     * Imports {@code source}'s element into the current inner document and wraps it
     * in a new {@link OutputSetting}. The caller is responsible for adding it to the
     * settings list; {@link #save} will append it to the DOM root automatically.
     */
    public OutputSetting adoptSetting(OutputSetting source) {
        Element adopted = (Element) innerDoc.importNode(source.getElement(), true);
        return new OutputSetting(adopted);
    }

    private String serializeInner(Document doc) throws Exception {
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        t.setOutputProperty(OutputKeys.ENCODING, "utf-8");
        t.setOutputProperty(OutputKeys.INDENT, "no");
        StringWriter sw = new StringWriter();
        t.transform(new DOMSource(doc), new StreamResult(sw));
        return sw.toString();
    }
}
