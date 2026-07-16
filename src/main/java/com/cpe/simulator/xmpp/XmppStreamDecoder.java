package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.XmlUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XmppStreamDecoder extends ByteToMessageDecoder {

    private static final Pattern STREAM_OPEN_PATTERN = Pattern.compile(
        "<stream:stream\\s[^>]*>", Pattern.DOTALL);
    private static final Pattern STREAM_CLOSE_PATTERN = Pattern.compile(
        "</stream:stream>");

    private final StringBuilder accumulator = new StringBuilder();
    private boolean streamOpened = false;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (!in.isReadable()) {
            return;
        }

        byte[] bytes = new byte[in.readableBytes()];
        in.readBytes(bytes);
        accumulator.append(new String(bytes, StandardCharsets.UTF_8));

        processAccumulator(out);
    }

    private void processAccumulator(List<Object> out) {
        boolean progress = true;
        while (progress) {
            progress = false;
            String data = accumulator.toString();

            if (data.isEmpty()) {
                return;
            }

            Matcher streamOpenMatcher = STREAM_OPEN_PATTERN.matcher(data);
            if (!streamOpened && streamOpenMatcher.find() && streamOpenMatcher.start() == 0) {
                String openTag = streamOpenMatcher.group();
                String from = extractAttribute(openTag, "from");
                String to = extractAttribute(openTag, "to");
                String id = extractAttribute(openTag, "id");
                out.add(new XmppStanza.StreamOpen(from, to, id));
                accumulator.delete(0, streamOpenMatcher.end());
                streamOpened = true;
                progress = true;
                continue;
            }

            Matcher streamCloseMatcher = STREAM_CLOSE_PATTERN.matcher(data);
            if (streamCloseMatcher.find() && streamCloseMatcher.start() == 0) {
                out.add(new XmppStanza.StreamClose());
                accumulator.delete(0, streamCloseMatcher.end());
                streamOpened = false;
                progress = true;
                continue;
            }

            if (data.startsWith("<?xml")) {
                int endOfDecl = data.indexOf("?>");
                if (endOfDecl >= 0) {
                    accumulator.delete(0, endOfDecl + 2);
                    progress = true;
                    continue;
                }
                return;
            }

            if (!data.startsWith("<")) {
                int nextTag = data.indexOf('<');
                if (nextTag > 0) {
                    accumulator.delete(0, nextTag);
                    progress = true;
                    continue;
                }
                return;
            }

            int endOfTopLevel = findEndOfTopLevelElement(data);
            if (endOfTopLevel < 0) {
                return;
            }

            String element = data.substring(0, endOfTopLevel);
            accumulator.delete(0, endOfTopLevel);
            progress = true;

            XmppStanza stanza = parseElement(element);
            if (stanza != null) {
                out.add(stanza);
            }
        }
    }

    private int findEndOfTopLevelElement(String data) {
        if (data.isEmpty() || data.charAt(0) != '<') {
            return -1;
        }

        int depth = 0;
        int i = 0;
        boolean inTag = false;
        boolean inQuote = false;
        char quoteChar = 0;

        while (i < data.length()) {
            char c = data.charAt(i);

            if (inQuote) {
                if (c == quoteChar) {
                    inQuote = false;
                }
                i++;
                continue;
            }

            if (c == '"' || c == '\'') {
                inQuote = true;
                quoteChar = c;
                i++;
                continue;
            }

            if (c == '<') {
                inTag = true;
                if (i + 1 < data.length() && data.charAt(i + 1) == '/') {
                    depth--;
                } else if (i + 1 < data.length() && data.charAt(i + 1) == '?') {
                    int end = data.indexOf("?>", i);
                    if (end < 0) return -1;
                    i = end + 2;
                    continue;
                } else if (i + 1 < data.length() && data.charAt(i + 1) == '!') {
                    int end = data.indexOf(">", i);
                    if (end < 0) return -1;
                    i = end + 1;
                    if (depth == 0) return i;
                    continue;
                } else {
                    depth++;
                }
                i++;
                continue;
            }

            if (c == '/' && inTag && i + 1 < data.length() && data.charAt(i + 1) == '>') {
                depth--;
                inTag = false;
                i += 2;
                if (depth == 0) return i;
                continue;
            }

            if (c == '>') {
                inTag = false;
                i++;
                if (depth == 0) return i;
                continue;
            }

            i++;
        }

        return -1;
    }

    private XmppStanza parseElement(String element) {
        String trimmed = element.trim();

        if (trimmed.startsWith("<stream:features")) {
            return parseStreamFeatures(trimmed);
        }

        if (trimmed.startsWith("<success") && trimmed.contains("xmlns=\"" + XmlUtil.NS_XMPP_SASL + "\"")) {
            String payload = extractTextContent(trimmed);
            return new XmppStanza.AuthSuccess(payload);
        }

        if (trimmed.startsWith("<challenge") && trimmed.contains("xmlns=\"" + XmlUtil.NS_XMPP_SASL + "\"")) {
            String payload = extractTextContent(trimmed);
            return new XmppStanza.AuthChallenge(payload);
        }

        if (trimmed.startsWith("<failure") && trimmed.contains("xmlns=\"" + XmlUtil.NS_XMPP_SASL + "\"")) {
            String payload = extractTextContent(trimmed);
            return new XmppStanza.AuthFailure(payload);
        }

        if (trimmed.startsWith("<proceed") && trimmed.contains("xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\"")) {
            return new XmppStanza.StartTlsProceed();
        }

        if (trimmed.startsWith("<failure") && trimmed.contains("xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\"")) {
            return new XmppStanza.StartTlsFailure();
        }

        if (trimmed.startsWith("<iq")) {
            return parseIq(trimmed);
        }

        if (trimmed.startsWith("<presence")) {
            return parsePresence(trimmed);
        }

        if (trimmed.startsWith("<message")) {
            return parseMessage(trimmed);
        }

        if (trimmed.startsWith("<auth")) {
            return parseAuth(trimmed);
        }

        return new XmppStanza.RawXml(trimmed);
    }

    private XmppStanza.StreamFeatures parseStreamFeatures(String xml) {
        List<String> mechanisms = new ArrayList<>();
        boolean hasBind = xml.contains("<bind") && xml.contains(XmlUtil.NS_XMPP_BIND);
        boolean hasSession = xml.contains("<session") && xml.contains(XmlUtil.NS_XMPP_SESSION);
        boolean hasStartTls = xml.contains("<starttls") && xml.contains("urn:ietf:params:xml:ns:xmpp-tls");

        Pattern mechPattern = Pattern.compile("<mechanism>([^<]+)</mechanism>");
        Matcher mechMatcher = mechPattern.matcher(xml);
        while (mechMatcher.find()) {
            mechanisms.add(mechMatcher.group(1).trim());
        }

        return new XmppStanza.StreamFeatures(xml, mechanisms, hasBind, hasSession, hasStartTls);
    }

    private XmppStanza.IqStanza parseIq(String xml) {
        String id = extractAttribute(xml, "id");
        String type = extractAttribute(xml, "type");
        String from = extractAttribute(xml, "from");
        String to = extractAttribute(xml, "to");

        String childElement = null;
        String childNamespace = null;

        Pattern childPattern = Pattern.compile("<(\\w+)(?:\\s[^>]*)?xmlns=\"([^\"]+)\"");
        Matcher childMatcher = childPattern.matcher(xml);
        int searchFrom = xml.indexOf('>') + 1;
        if (searchFrom > 0 && searchFrom < xml.length()) {
            childMatcher.region(searchFrom, xml.length());
            if (childMatcher.find()) {
                childElement = childMatcher.group(1);
                childNamespace = childMatcher.group(2);
            }
        }

        return new XmppStanza.IqStanza(id, type, from, to, childElement, childNamespace, xml);
    }

    private XmppStanza.PresenceStanza parsePresence(String xml) {
        String id = extractAttribute(xml, "id");
        String type = extractAttribute(xml, "type");
        String from = extractAttribute(xml, "from");
        String to = extractAttribute(xml, "to");
        return new XmppStanza.PresenceStanza(id, type, from, to);
    }

    private XmppStanza.MessageStanza parseMessage(String xml) {
        String id = extractAttribute(xml, "id");
        String type = extractAttribute(xml, "type");
        String from = extractAttribute(xml, "from");
        String to = extractAttribute(xml, "to");

        String body = null;
        Pattern bodyPattern = Pattern.compile("<body>([^<]*)</body>");
        Matcher bodyMatcher = bodyPattern.matcher(xml);
        if (bodyMatcher.find()) {
            body = XmlUtil.unescapeXml(bodyMatcher.group(1));
        }

        return new XmppStanza.MessageStanza(id, type, from, to, body);
    }

    private XmppStanza.AuthStanza parseAuth(String xml) {
        String mechanism = extractAttribute(xml, "mechanism");
        String payload = extractTextContent(xml);
        return new XmppStanza.AuthStanza(mechanism, payload);
    }

    static String extractAttribute(String xml, String attrName) {
        Pattern pattern = Pattern.compile("\\s" + attrName + "=\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(xml);
        if (matcher.find()) {
            return XmlUtil.unescapeXml(matcher.group(1));
        }
        return null;
    }

    static String extractTextContent(String xml) {
        int startIdx = xml.indexOf('>');
        if (startIdx < 0) return null;
        int endIdx = xml.lastIndexOf('<');
        if (endIdx <= startIdx) return null;
        String content = xml.substring(startIdx + 1, endIdx).trim();
        return content.isEmpty() ? null : content;
    }

    public void reset() {
        accumulator.setLength(0);
        streamOpened = false;
    }
}
