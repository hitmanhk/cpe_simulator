package com.cpe.simulator.cwmp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DigestAuth {

    public static String calculateDigestResponse(String username, String password,
                                                  String realm, String nonce,
                                                  String uri, String method,
                                                  String qop, String nc, String cnonce) {
        String ha1 = md5Hex(username + ":" + realm + ":" + password);
        String ha2 = md5Hex(method + ":" + uri);

        String response;
        if ("auth".equals(qop)) {
            response = md5Hex(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2);
        } else {
            response = md5Hex(ha1 + ":" + nonce + ":" + ha2);
        }
        return response;
    }

    public static String buildAuthorizationHeader(String wwwAuthenticate, String username,
                                                   String password, String uri, String method) {
        Map<String, String> params = parseWwwAuthenticate(wwwAuthenticate);

        String realm = params.getOrDefault("realm", "");
        String nonce = params.getOrDefault("nonce", "");
        String qop = params.getOrDefault("qop", null);
        String opaque = params.getOrDefault("opaque", null);

        String nc = "00000001";
        String cnonce = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        String response = calculateDigestResponse(username, password, realm, nonce, uri, method, qop, nc, cnonce);

        StringBuilder sb = new StringBuilder();
        sb.append("Digest username=\"").append(username).append("\"");
        sb.append(", realm=\"").append(realm).append("\"");
        sb.append(", nonce=\"").append(nonce).append("\"");
        sb.append(", uri=\"").append(uri).append("\"");
        sb.append(", response=\"").append(response).append("\"");
        if (qop != null) {
            sb.append(", qop=").append(qop);
            sb.append(", nc=").append(nc);
            sb.append(", cnonce=\"").append(cnonce).append("\"");
        }
        if (opaque != null) {
            sb.append(", opaque=\"").append(opaque).append("\"");
        }
        return sb.toString();
    }

    static Map<String, String> parseWwwAuthenticate(String header) {
        Map<String, String> params = new HashMap<>();
        if (header == null || header.isEmpty()) return params;

        String content = header;
        if (content.regionMatches(true, 0, "Digest ", 0, 7)) {
            content = content.substring(7).trim();
        }

        int i = 0;
        while (i < content.length()) {
            int eqIdx = content.indexOf('=', i);
            if (eqIdx < 0) break;

            String key = content.substring(i, eqIdx).trim();

            int valStart = eqIdx + 1;
            String value;
            if (valStart < content.length() && content.charAt(valStart) == '"') {
                int valEnd = content.indexOf('"', valStart + 1);
                if (valEnd < 0) valEnd = content.length();
                value = content.substring(valStart + 1, valEnd);
                i = valEnd + 1;
                while (i < content.length() && (content.charAt(i) == ',' || content.charAt(i) == ' ')) {
                    i++;
                }
            } else {
                int commaIdx = content.indexOf(',', valStart);
                if (commaIdx < 0) {
                    value = content.substring(valStart).trim();
                    i = content.length();
                } else {
                    value = content.substring(valStart, commaIdx).trim();
                    i = commaIdx + 1;
                }
            }
            params.put(key, value);
        }
        return params;
    }

    static String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
