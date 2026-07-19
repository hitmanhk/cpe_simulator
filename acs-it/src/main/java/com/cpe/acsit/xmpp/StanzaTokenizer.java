package com.cpe.acsit.xmpp;

import java.util.ArrayList;
import java.util.List;

/**
 * Incremental XMPP stanza tokenizer for the test server. Splits a byte stream
 * into complete top-level elements by depth counting (with quote awareness),
 * and surfaces the never-closing {@code <stream:stream>} opener separately.
 * Sufficient for the controlled client output produced by the simulator.
 */
public class StanzaTokenizer {

    private final StringBuilder buffer = new StringBuilder();

    /** Feed bytes; returns completed stanzas (stream open marker included). */
    public synchronized List<String> feed(byte[] bytes) {
        buffer.append(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        List<String> stanzas = new ArrayList<>();
        String stanza;
        while ((stanza = nextStanza()) != null) {
            stanzas.add(stanza);
        }
        return stanzas;
    }

    private String nextStanza() {
        String text = buffer.toString();
        int start = 0;
        // skip whitespace and XML declarations
        while (true) {
            int nonWs = skipWhitespace(text, start);
            if (nonWs < 0) {
                buffer.setLength(0);
                return null;
            }
            if (text.startsWith("<?", nonWs)) {
                int end = text.indexOf("?>", nonWs);
                if (end < 0) {
                    buffer.delete(0, nonWs);
                    return null;
                }
                start = end + 2;
                continue;
            }
            start = nonWs;
            break;
        }
        if (text.charAt(start) != '<') {
            // unexpected data; drop it to stay resynchronized
            buffer.delete(0, start + 1);
            return null;
        }

        // stream opener: <stream:stream ...> (not self-closing, never closed at depth 0)
        if (matches(text, start, "<stream:stream")) {
            int close = findTagEnd(text, start);
            if (close < 0) {
                buffer.delete(0, start);
                return null; // wait for more bytes
            }
            String open = text.substring(start, close + 1);
            buffer.delete(0, close + 1);
            return open;
        }
        // stream closer
        if (matches(text, start, "</stream:stream")) {
            int close = text.indexOf('>', start);
            if (close < 0) {
                buffer.delete(0, start);
                return null;
            }
            String closeTag = text.substring(start, close + 1);
            buffer.delete(0, close + 1);
            return closeTag;
        }

        // regular stanza: depth counting from first '<'
        int depth = 0;
        int i = start;
        boolean inQuote = false;
        char quoteChar = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
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
                if (text.startsWith("<!--", i)) {
                    int endComment = text.indexOf("-->", i);
                    if (endComment < 0) {
                        break;
                    }
                    i = endComment + 3;
                    continue;
                }
                if (i + 1 < text.length() && text.charAt(i + 1) == '/') {
                    int end = text.indexOf('>', i);
                    if (end < 0) {
                        break;
                    }
                    depth--;
                    i = end + 1;
                    if (depth == 0) {
                        String stanza = text.substring(start, i);
                        buffer.delete(0, i);
                        return stanza;
                    }
                    continue;
                }
                // opening tag: find its end respecting quotes
                int end = findTagEnd(text, i);
                if (end < 0) {
                    break;
                }
                boolean selfClosing = text.charAt(end - 1) == '/';
                if (!selfClosing) {
                    depth++;
                } else if (depth == 0) {
                    // self-contained stanza like <presence/>
                    String stanza = text.substring(start, end + 1);
                    buffer.delete(0, end + 1);
                    return stanza;
                }
                i = end + 1;
                continue;
            }
            i++;
        }
        // incomplete; keep everything from start for the next feed
        buffer.delete(0, start);
        return null;
    }

    private static int skipWhitespace(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean matches(String text, int offset, String prefix) {
        return text.regionMatches(offset, prefix, 0, prefix.length());
    }

    /** Finds the index of the closing '>' of a tag starting at '<', quote aware. */
    private static int findTagEnd(String text, int ltIndex) {
        boolean inQuote = false;
        char quoteChar = 0;
        for (int i = ltIndex + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuote) {
                if (c == quoteChar) {
                    inQuote = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inQuote = true;
                quoteChar = c;
            } else if (c == '>') {
                return i;
            }
        }
        return -1;
    }
}
