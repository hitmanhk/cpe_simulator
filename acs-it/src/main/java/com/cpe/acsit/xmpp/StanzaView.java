package com.cpe.acsit.xmpp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight read-only view over one XMPP stanza string (server-side).
 * Regex-based extraction is sufficient for the controlled client output of
 * the simulator; full XML parsing is intentionally avoided here.
 */
public class StanzaView {

    private static final Pattern ELEMENT_START = Pattern.compile("^<([a-zA-Z][\\w:.-]*)");
    private static final Pattern STREAM_OPEN = Pattern.compile("^<stream:stream[\\s>]");

    private final String xml;

    public StanzaView(String xml) {
        this.xml = xml;
    }

    public String xml() {
        return xml;
    }

    /** Root element local name: iq, presence, auth, response, starttls, stream:stream, ... */
    public String elementName() {
        if (xml.startsWith("</stream:stream")) {
            return "stream:close";
        }
        Matcher m = ELEMENT_START.matcher(xml.trim());
        return m.find() ? m.group(1) : "";
    }

    public boolean isStreamOpen() {
        return STREAM_OPEN.matcher(xml.trim()).find();
    }

    public String attr(String name) {
        Matcher m = Pattern.compile(name + "=\"([^\"]*)\"").matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    /** Character content of the root element (e.g. auth/response payload). */
    public String rootText() {
        int gt = xml.indexOf('>');
        int lt = xml.lastIndexOf('<');
        if (gt < 0 || lt <= gt) {
            return "";
        }
        return xml.substring(gt + 1, lt).trim();
    }

    /** Local name of the first child element inside iq (bind, ping, connectionRequest, ...). */
    public String firstChildName() {
        Matcher m = Pattern.compile(">\\s*<([a-zA-Z][\\w:.-]*)[\\s/>]").matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    /** xmlns attribute of the first child element, or null. */
    public String firstChildNamespace() {
        Matcher m = Pattern.compile(">\\s*<[a-zA-Z][\\w:.-]*\\s+xmlns=\"([^\"]*)\"").matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    /** Text of a named descendant element, or null. */
    public String childText(String elementName) {
        Matcher m = Pattern.compile("<" + elementName + ">([^<]*)</" + elementName + ">").matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    public boolean hasChild(String elementName) {
        return Pattern.compile("<" + elementName + "[\\s/>]").matcher(xml).find();
    }
}
