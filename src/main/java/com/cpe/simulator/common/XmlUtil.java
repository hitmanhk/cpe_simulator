package com.cpe.simulator.common;

public final class XmlUtil {

    public static final String NS_SOAP_ENV = "http://schemas.xmlsoap.org/soap/envelope/";
    public static final String NS_SOAP_ENC = "http://schemas.xmlsoap.org/soap/encoding/";
    public static final String NS_CWMP = "urn:dslforum-org:cwmp-1-0";
    public static final String NS_XSD = "http://www.w3.org/2001/XMLSchema";
    public static final String NS_XSI = "http://www.w3.org/2001/XMLSchema-instance";

    public static final String NS_XMPP_STREAM = "http://etherx.jabber.org/streams";
    public static final String NS_XMPP_CLIENT = "jabber:client";
    public static final String NS_XMPP_SASL = "urn:ietf:params:xml:ns:xmpp-sasl";
    public static final String NS_XMPP_BIND = "urn:ietf:params:xml:ns:xmpp-bind";
    public static final String NS_XMPP_SESSION = "urn:ietf:params:xml:ns:xmpp-session";
    public static final String NS_XMPP_PING = "urn:xmpp:ping";
    public static final String NS_CWMP_XMPP_CONN_REQ = "urn:broadband-forum-org:cwmp:xmppConnReq";

    private XmlUtil() {}

    public static String escapeXml(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String unescapeXml(String input) {
        if (input == null) return "";
        return input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'");
    }
}
