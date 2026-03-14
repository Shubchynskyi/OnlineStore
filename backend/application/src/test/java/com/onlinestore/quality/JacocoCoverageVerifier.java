package com.onlinestore.quality;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public final class JacocoCoverageVerifier {

    private JacocoCoverageVerifier() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected jacoco.xml path and minimum line coverage ratio.");
        }

        Path reportPath = Path.of(args[0]);
        double minimumRatio = Double.parseDouble(args[1]);
        if (!Files.isRegularFile(reportPath)) {
            throw new IllegalStateException("JaCoCo aggregate report not found: " + reportPath);
        }

        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        var document = factory.newDocumentBuilder().parse(reportPath.toFile());
        var reportElement = document.getDocumentElement();
        Element lineCounter = null;
        for (Node child = reportElement.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element
                && "counter".equals(element.getTagName())
                && "LINE".equals(element.getAttribute("type"))) {
                lineCounter = element;
                break;
            }
        }

        if (lineCounter == null) {
            throw new IllegalStateException("JaCoCo aggregate report does not contain a top-level LINE counter.");
        }

        double missed = Double.parseDouble(lineCounter.getAttribute("missed"));
        double covered = Double.parseDouble(lineCounter.getAttribute("covered"));
        double total = covered + missed;
        if (total <= 0.0d) {
            throw new IllegalStateException("JaCoCo aggregate report does not contain eligible line coverage data.");
        }
        double ratio = covered / total;
        if (ratio < minimumRatio) {
            throw new IllegalStateException(
                "Aggregate backend line coverage %.2f%% is below required %.2f%%."
                    .formatted(ratio * 100.0, minimumRatio * 100.0)
            );
        }

        System.out.println(
            "Aggregate backend line coverage %.2f%% meets required %.2f%%."
                .formatted(ratio * 100.0, minimumRatio * 100.0)
        );
    }
}
