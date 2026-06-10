package com.zen.browser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class BlocklistManager {
    private final Map<String, Set<String>> blocklists = new HashMap<>();

    public void addManualDomains(String name, Set<String> domains) {
        synchronized (blocklists) {
            blocklists.put(name, domains);
        }
    }

    public List<String> getBlockingLists(String host) {
        List<String> lists = new ArrayList<>();
        if (host == null) return lists;
        String checkHost = host;
        synchronized (blocklists) {
            while (checkHost != null && !checkHost.isEmpty()) {
                for (Map.Entry<String, Set<String>> entry : blocklists.entrySet()) {
                    if (entry.getValue().contains(checkHost)) {
                        if (!lists.contains(entry.getKey())) {
                            lists.add(entry.getKey());
                        }
                    }
                }
                int dot = checkHost.indexOf('.');
                if (dot == -1) break;
                checkHost = checkHost.substring(dot + 1);
            }
        }
        return lists;
    }

    public void loadBlocklistsInBackground(ExecutorService executor) {
        String[][] lists = {
            {"nsfw", "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/nsfw.txt"},
            {"social", "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/social.txt"},
            {"gambling", "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/gambling.mini.txt"},
            {"urlshortener", "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/urlshortener.txt"},
            {"hoster", "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/hoster.txt"},
            {"nosafesearch", "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/nosafesearch.txt"},
            {"tif", "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/tif.medium.txt"},
            {"pro", "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/pro.txt"}
        };
        for (String[] entry : lists) {
            String name = entry[0];
            String url = entry[1];
            executor.execute(() -> {
                Set<String> set = new HashSet<>();
                try {
                    HttpURLConnection conn = (HttpURLConnection) java.net.URI.create(url).toURL().openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String domain = extractDomain(line);
                        if (domain != null) set.add(domain);
                    }
                    reader.close();
                    synchronized (blocklists) {
                        blocklists.put(name, set);
                    }
                } catch (Exception e) { e.printStackTrace(); }
            });
        }
    }

    private String extractDomain(String line) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("!") || line.startsWith("[") || line.startsWith("#")) return null;
        if (line.startsWith("||")) {
            int end = line.indexOf('^');
            if (end == -1) end = line.length();
            return line.substring(2, end);
        }
        if (line.matches("^[a-zA-Z0-9.-]+$")) return line;
        return null;
    }
}
