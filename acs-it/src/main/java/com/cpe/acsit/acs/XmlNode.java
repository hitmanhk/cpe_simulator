package com.cpe.acsit.acs;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal XML element tree used by the test-side CWMP parser. Namespace
 * prefixes are ignored; matching is by local name (sufficient for the
 * controlled SOAP the simulator emits).
 */
public final class XmlNode {

    private final String name;
    private final String text;
    private final List<XmlNode> children;

    private XmlNode(String name, String text, List<XmlNode> children) {
        this.name = name;
        this.text = text;
        this.children = children;
    }

    public String name() {
        return name;
    }

    /** Concatenated character content of this element ("" for pure containers). */
    public String text() {
        return text;
    }

    public List<XmlNode> children() {
        return children;
    }

    public XmlNode first(String childName) {
        for (XmlNode child : children) {
            if (child.name.equals(childName)) {
                return child;
            }
        }
        return null;
    }

    public List<XmlNode> all(String childName) {
        List<XmlNode> result = new ArrayList<>();
        for (XmlNode child : children) {
            if (child.name.equals(childName)) {
                result.add(child);
            }
        }
        return result;
    }

    /** Text of the first child with the given name, or null when absent. */
    public String textOf(String childName) {
        XmlNode child = first(childName);
        return child != null ? child.text() : null;
    }

    public boolean has(String childName) {
        return first(childName) != null;
    }

    /** Parses a complete XML document and returns the root element. */
    public static XmlNode parse(String xml) {
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                    return readElement(reader, stripPrefix(reader.getLocalName()));
                }
            }
            throw new IllegalArgumentException("No root element found");
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse XML: " + e.getMessage(), e);
        }
    }

    private static XmlNode readElement(XMLStreamReader reader, String openedElement) throws Exception {
        String name = openedElement;
        StringBuilder text = new StringBuilder();
        List<XmlNode> children = new ArrayList<>();
        while (reader.hasNext()) {
            int event = reader.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    String local = stripPrefix(reader.getLocalName());
                    children.add(readElement(reader, local));
                }
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA ->
                    text.append(reader.getText());
                case XMLStreamConstants.END_ELEMENT -> {
                    return new XmlNode(name, text.toString().trim(), children);
                }
                case XMLStreamConstants.END_DOCUMENT -> {
                    return new XmlNode(name, text.toString().trim(), children);
                }
                default -> {
                    // skip processing instructions, comments, whitespace-only events
                }
            }
        }
        return new XmlNode(name, text.toString().trim(), children);
    }

    private static String stripPrefix(String localName) {
        int idx = localName.indexOf(':');
        return idx >= 0 ? localName.substring(idx + 1) : localName;
    }
}
