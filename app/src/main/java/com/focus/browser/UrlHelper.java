package com.zen.browser;

import android.net.Uri;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class UrlHelper {
    public static final Pattern URL_PATTERN = Pattern.compile(
            "^(https?://)?([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(/[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]*)?$"
    );

    public static final Set<String> TRUSTED_DOMAINS = new HashSet<>(Arrays.asList(
        "wikipedia.org", "notion.so", "github.com", "stackoverflow.com", "khanacademy.org", "overleaf.com","scholar.google.com","researchgate.net"
    ));

    public static String formatUrl(String input) {
        input = input.trim();
        if (input.isEmpty()) return "https://www.google.com/webhp?pws=0&safe=active";
        if (URL_PATTERN.matcher(input).matches()) {
            if (!input.startsWith("http://") && !input.startsWith("https://"))
                input = "https://" + input;
            return enforceSafeSearch(input);
        }
        return "https://www.google.com/search?q=" + Uri.encode(input) + "&safe=active";
    }

    // Inside UrlHelper class, add these static maps and constants:
    private static final Map<String, String> SAFE_SEARCH_PARAMS = new HashMap<>();
    static {
        // Google TLDs (The main ones, plus global variations)
        SAFE_SEARCH_PARAMS.put("google.com", "safe=active");
        SAFE_SEARCH_PARAMS.put("google.com.hk", "safe=active");
        SAFE_SEARCH_PARAMS.put("google.co.uk", "safe=active");
        SAFE_SEARCH_PARAMS.put("google.ca", "safe=active");
        SAFE_SEARCH_PARAMS.put("google.de", "safe=active");
        SAFE_SEARCH_PARAMS.put("google.fr", "safe=active");
        SAFE_SEARCH_PARAMS.put("google.co.jp", "safe=active");
        SAFE_SEARCH_PARAMS.put("google.it", "safe=active");
        SAFE_SEARCH_PARAMS.put("google.es", "safe=active");
        SAFE_SEARCH_PARAMS.put("google.com.br", "safe=active");
        SAFE_SEARCH_PARAMS.put("google.com.mx", "safe=active");
        SAFE_SEARCH_PARAMS.put("google.co.in", "safe=active");
        SAFE_SEARCH_PARAMS.put("google.com.au", "safe=active");

        // Major Search Engines
        SAFE_SEARCH_PARAMS.put("bing.com", "adlt=strict");
        SAFE_SEARCH_PARAMS.put("duckduckgo.com", "kp=1");       // -2 is strict, -1 is moderate, 1 is off
        SAFE_SEARCH_PARAMS.put("search.yahoo.com", "vm=r");       // r is strict, p is moderate, i is off
        SAFE_SEARCH_PARAMS.put("yandex.com", "nomisspell=1&reask=1&sch=1"); // sch=1 forces family search
        SAFE_SEARCH_PARAMS.put("yandex.ru", "nomisspell=1&reask=1&sch=1");

        // Secondary & Regional Search Engines
        SAFE_SEARCH_PARAMS.put("baidu.com", "cl=3");              // Restricts adult content filtering
        SAFE_SEARCH_PARAMS.put("naver.com", "fistmode=2");        // Naver's strict filter equivalent
        SAFE_SEARCH_PARAMS.put("daum.net", "fistmode=2");         // Daum/Kakao filter parameter
        SAFE_SEARCH_PARAMS.put("qwant.com", "safesearch=2");      // 2 is strict, 1 is moderate, 0 is off
        SAFE_SEARCH_PARAMS.put("ask.com", "safeSearch=on");
        SAFE_SEARCH_PARAMS.put("ecosia.org", "safesearch=2");     // 2 is strict (Works on standard web UI, but DNS CNAME is preferred for network enforcement)
    }

    public static String enforceSafeSearch(String url) {
        if (url == null) return url;
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host != null) {
                // Remove leading "www." to match keys
                String hostKey = host.startsWith("www.") ? host.substring(4) : host;
                String safeParam = SAFE_SEARCH_PARAMS.get(hostKey);
                if (safeParam == null) {
                    // Check if host ends with any key (for subdomains like www.google.com)
                    for (Map.Entry<String, String> entry : SAFE_SEARCH_PARAMS.entrySet()) {
                        if (host.equals(entry.getKey()) || host.endsWith("." + entry.getKey())) {
                            safeParam = entry.getValue();
                            break;
                        }
                    }
                }
                if (safeParam != null) {
                    // Parse existing query params
                    Set<String> existingKeys = new HashSet<>();
                    Uri.Builder builder = uri.buildUpon().clearQuery();
                    String query = uri.getEncodedQuery();
                    if (query != null) {
                        // Re‑append all existing params except the safe search one we’re about to set
                        String[] params = query.split("&");
                        for (String param : params) {
                            String key = param.contains("=") ? param.substring(0, param.indexOf('=')) : param;
                            existingKeys.add(key);
                            builder.appendQueryParameter(
                                    Uri.decode(param.contains("=") ? param.substring(0, param.indexOf('=')) : param),
                                    param.contains("=") ? Uri.decode(param.substring(param.indexOf('=') + 1)) : ""
                            );
                        }
                    }
                    // Now add/override the safe search param
                    String[] safeParts = safeParam.split("&");
                    for (String part : safeParts) {
                        String[] kv = part.split("=", 2);
                        String key = kv[0];
                        String value = kv.length > 1 ? kv[1] : "";
                        // Remove any existing entry for this key (already collected above, but we need to clear)
                        // To be safe, we build fresh: remove the param if it exists, then append new
                        // A simpler approach: rebuild query from scratch
                    }
                    // Instead of complex rebuilding, we can just replace the query string using a map.
                    // Let’s use a more robust method:
                    return buildSafeSearchUrl(uri, hostKey, safeParam);
                }
            }
        } catch (Exception ignored) {}
        return url;
    }

    // Helper to construct URL with safe search param
    private static String buildSafeSearchUrl(Uri uri, String hostKey, String safeParam) {
        Uri.Builder builder = uri.buildUpon().clearQuery();
        Map<String, String> queryMap = new LinkedHashMap<>();
        String oldQuery = uri.getEncodedQuery();
        if (oldQuery != null) {
            for (String param : oldQuery.split("&")) {
                int idx = param.indexOf('=');
                String key = idx != -1 ? Uri.decode(param.substring(0, idx)) : Uri.decode(param);
                String value = idx != -1 ? Uri.decode(param.substring(idx + 1)) : "";
                queryMap.put(key, value);
            }
        }
        // Override/add safe search keys
        for (String part : safeParam.split("&")) {
            String[] kv = part.split("=", 2);
            queryMap.put(kv[0], kv.length > 1 ? kv[1] : "");
        }
        for (Map.Entry<String, String> entry : queryMap.entrySet()) {
            builder.appendQueryParameter(entry.getKey(), entry.getValue() != null ? entry.getValue() : "");
        }
        return builder.build().toString();
    }

    public static boolean isTrustedSite(String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            java.net.URL parsedUrl = new java.net.URL(url);
            String host = parsedUrl.getHost().toLowerCase();
            String[] trustedTLDs = { ".edu", ".edu.hk", ".ac.uk", ".gov", ".gov.hk" };
            for (String tld : trustedTLDs) {
                if (host.endsWith(tld)) return true;
            }
            for (String trusted : TRUSTED_DOMAINS) {
                if (host.contains(trusted)) return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public static boolean isVideoUrlBlock(String url) {
        String lower = url.toLowerCase();
        if (isTrustedSite(lower)) {
            return false; 
        }
        return lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".3gp")
                || lower.endsWith(".m3u8") || lower.endsWith(".mov") || lower.endsWith(".avi")
                || lower.endsWith(".ts") || lower.endsWith(".flv")
                || lower.contains("videoplayback")
                || lower.contains("googlevideo.com/videoplayback")
                || lower.contains("manifest.googlevideo.com");
    }
}
