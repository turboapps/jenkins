package org.jenkinsci.plugins.spoontrigger.snapshot;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.InputStream;
import java.nio.file.Path;

import static com.google.common.base.Preconditions.checkState;

public class XapplEditor {

    private DocumentBuilderFactory documentBuilderFactory;
    private XPathFactory xpathFactory;
    private Document document;

    public void load(Path path) throws Exception {
        document = createDocumentBuilder().parse(path.toFile());
        document.normalize();
    }

    public void load(InputStream inputStream) throws Exception {
        document = createDocumentBuilder().parse(inputStream);
        document.normalize();
    }

    public void removeFile(String path) throws Exception {
        checkState(document != null, "Document not loaded");

        NodeList fileSystemElements = findFileSystemElements(path);
        for (int nodePos = 0; nodePos < fileSystemElements.getLength(); ++nodePos) {
            Node node = fileSystemElements.item(nodePos);
            Node parent = node.getParentNode();
            if (parent != null) {
                parent.removeChild(node);
            }
        }
    }

    public boolean fileExists(String path) throws Exception {
        checkState(document != null, "Document not loaded");

        NodeList fileSystemElements = findFileSystemElements(path);
        return fileSystemElements.getLength() > 0;
    }

    public void save(Path path) throws TransformerException {
        checkState(document != null, "Document not loaded");
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        DOMSource domSource = new DOMSource(document);
        StreamResult streamResult = new StreamResult(path.toFile());
        transformer.transform(domSource, streamResult);
    }

    private DocumentBuilder createDocumentBuilder() throws Exception {
        if (documentBuilderFactory == null) {
            documentBuilderFactory = DocumentBuilderFactory.newInstance();
        }
        return documentBuilderFactory.newDocumentBuilder();
    }

    private XPath createXPath() {
        if (xpathFactory == null) {
            xpathFactory = XPathFactory.newInstance();
        }
        return xpathFactory.newXPath();
    }

    private NodeList findFileSystemElements(String path) throws XPathExpressionException {
        XPath xpath = createXPath();
        try {
            StringBuilder pathBuilder = new StringBuilder("/Configuration/Layers/Layer/Filesystem");
            String[] segments = path.split("\\\\|/");
            int lastPos = segments.length - 1;
            for (int segmentPos = 0; segmentPos < lastPos; ++segmentPos) {
                pathBuilder.append("/Directory[@name='");
                pathBuilder.append(segments[segmentPos]);
                pathBuilder.append("']");
            }

            pathBuilder.append("/*[@name='");
            pathBuilder.append(segments[lastPos]);
            pathBuilder.append("']");

            return (NodeList) xpath.evaluate(pathBuilder.toString(), document, XPathConstants.NODESET);
        } finally {
            xpath.reset();
        }
    }
}
