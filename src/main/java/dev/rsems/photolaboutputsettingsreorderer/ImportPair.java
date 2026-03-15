package dev.rsems.photolaboutputsettingsreorderer;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pairs an import-side setting (left, nullable) with a current-side setting (right, nullable).
 * <ul>
 *   <li>Both non-null, same content → case 1.1: normal, no action</li>
 *   <li>Both non-null, different content → case 1.2: bold left, diff tooltip, overwrite button</li>
 *   <li>Right only (importSetting == null) → case 2: empty left, no action</li>
 *   <li>Left only (currentSetting == null) → case 3: empty right, add button</li>
 * </ul>
 */
public class ImportPair {

    private final OutputSetting importSetting;  // left  — null for right-only
    private final OutputSetting currentSetting; // right — null for left-only

    public ImportPair(OutputSetting importSetting, OutputSetting currentSetting) {
        this.importSetting = importSetting;
        this.currentSetting = currentSetting;
    }

    public OutputSetting getImportSetting()  { return importSetting;  }
    public OutputSetting getCurrentSetting() { return currentSetting; }

    /** True when both sides exist and at least one child element differs. */
    public boolean isDifferent() {
        return !getDifferences().isEmpty();
    }

    /** True when a → button should be shown (add or overwrite). */
    public boolean hasImportAction() {
        return importSetting != null && (currentSetting == null || isDifferent());
    }

    /**
     * Returns differences as {localName, importValue, currentValue} triples.
     * Empty when one or both sides are null, or when content is identical.
     */
    public List<String[]> getDifferences() {
        if (importSetting == null || currentSetting == null) return List.of();

        Map<String, String> leftProps  = extractProps(importSetting.getElement());
        Map<String, String> rightProps = extractProps(currentSetting.getElement());

        Set<String> allKeys = new LinkedHashSet<>(leftProps.keySet());
        allKeys.addAll(rightProps.keySet());

        List<String[]> diffs = new ArrayList<>();
        for (String key : allKeys) {
            if (key.equals("Enabled")) continue;
            String lv = leftProps.getOrDefault(key, "");
            String rv = rightProps.getOrDefault(key, "");
            if (!lv.equals(rv)) {
                diffs.add(new String[]{key, lv, rv});
            }
        }
        return diffs;
    }

    private static Map<String, String> extractProps(Element el) {
        Map<String, String> props = new LinkedHashMap<>();
        NodeList children = el.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                props.put(child.getLocalName(), child.getTextContent().trim());
            }
        }
        return props;
    }
}
