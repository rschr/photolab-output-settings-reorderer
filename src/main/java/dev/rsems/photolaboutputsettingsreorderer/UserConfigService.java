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

    private static final String XPATH_VALUE =
            "/configuration/userSettings/DxO.PhotoLab.Properties.Settings" +
            "/setting[@name='OutputSettings']/value";

    private static final DateTimeFormatter BACKUP_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private Path configFile;
    private Document outerDoc;
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
        valueElement = (Element) xp.evaluate(XPATH_VALUE, outerDoc, XPathConstants.NODE);
        if (valueElement == null) {
            throw new IllegalStateException("OutputSettings value element not found in " + configFile);
        }

        String embeddedXml = valueElement.getTextContent();
        Document innerDoc;
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
