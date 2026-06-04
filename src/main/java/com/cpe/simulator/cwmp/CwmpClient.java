package com.cpe.simulator.cwmp;

import com.cpe.simulator.common.AppConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class CwmpClient {

    public record CwmpResponse(int statusCode, String body, Map<String, String> headers) {}

    private final HttpClient httpClient;
    private final String acsUrl;
    private final String acsUser;
    private final String acsPassword;
    private final Map<String, String> sessionCookies;
    private final SoapParser soapParser;

    public CwmpClient(AppConfig config) {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.acsUrl = config.getAcsUrl();
        this.acsUser = config.getAcsUser();
        this.acsPassword = config.getAcsPassword();
        this.sessionCookies = new ConcurrentHashMap<>();
        this.soapParser = new SoapParser();
    }

    public CwmpResponse sendInform(String sessionId, String soapXml) throws Exception {
        return sendRequest(sessionId, soapXml);
    }

    public CwmpResponse sendRequest(String sessionId, String soapXml) throws Exception {
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(acsUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "text/xml; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(soapXml));

        String cookie = sessionCookies.get(sessionId);
        if (cookie != null && !cookie.isEmpty()) {
            reqBuilder.header("Cookie", cookie);
        }

        HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 401) {
            String wwwAuth = response.headers().firstValue("WWW-Authenticate").orElse(null);
            String scheme = detectAuthScheme(wwwAuth);

            if (scheme != null) {
                String authHeader;
                if ("Digest".equals(scheme)) {
                    authHeader = DigestAuth.buildAuthorizationHeader(wwwAuth, acsUser, acsPassword, acsUrl, "POST");
                } else {
                    authHeader = buildBasicAuthHeader(acsUser, acsPassword);
                }

                HttpRequest.Builder retryBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(acsUrl))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "text/xml; charset=utf-8")
                        .header("Authorization", authHeader)
                        .POST(HttpRequest.BodyPublishers.ofString(soapXml));

                if (cookie != null && !cookie.isEmpty()) {
                    retryBuilder.header("Cookie", cookie);
                }

                response = httpClient.send(retryBuilder.build(), HttpResponse.BodyHandlers.ofString());
            }
        }

        extractCookies(sessionId, response);

        Map<String, String> respHeaders = new HashMap<>();
        response.headers().map().forEach((key, values) -> {
            if (!values.isEmpty()) {
                respHeaders.put(key, values.get(0));
            }
        });

        return new CwmpResponse(response.statusCode(), response.body(), respHeaders);
    }

    static String buildBasicAuthHeader(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    static String detectAuthScheme(String wwwAuthenticate) {
        if (wwwAuthenticate == null || wwwAuthenticate.isEmpty()) {
            return null;
        }
        String trimmed = wwwAuthenticate.trim();
        if (trimmed.regionMatches(true, 0, "Digest", 0, 6)) {
            return "Digest";
        }
        if (trimmed.regionMatches(true, 0, "Basic", 0, 5)) {
            return "Basic";
        }
        return null;
    }

    public void closeSession(String sessionId) {
        if (sessionId != null) {
            sessionCookies.remove(sessionId);
        }
    }

    public void shutdown() {
        httpClient.close();
    }

    private void extractCookies(String sessionId, HttpResponse<String> response) {
        List<String> setCookies = response.headers().allValues("set-cookie");
        if (setCookies.isEmpty()) {
            setCookies = response.headers().allValues("Set-Cookie");
        }
        if (!setCookies.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            String existing = sessionCookies.get(sessionId);
            if (existing != null && !existing.isEmpty()) {
                sb.append(existing);
            }
            for (String setCookie : setCookies) {
                int semiIdx = setCookie.indexOf(';');
                String nameValue = semiIdx > 0 ? setCookie.substring(0, semiIdx) : setCookie;
                if (!sb.isEmpty()) {
                    sb.append("; ");
                }
                sb.append(nameValue.trim());
            }
            sessionCookies.put(sessionId, sb.toString());
        }
    }
}
