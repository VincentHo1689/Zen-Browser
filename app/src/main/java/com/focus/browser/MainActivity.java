package com.focus.browser;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private EditText urlBar;
    private final Set<String> blockedDomains = new HashSet<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        urlBar = findViewById(R.id.url_bar);
        TextView btnBack = findViewById(R.id.btn_back);
        TextView btnForward = findViewById(R.id.btn_forward);

        setupWebView();
        loadBlocklistsInBackground();

        webView.loadUrl("https://www.google.com");
        urlBar.setText("https://www.google.com");

        btnBack.setOnClickListener(v -> {
            if (webView.canGoBack()) webView.goBack();
        });

        btnForward.setOnClickListener(v -> {
            if (webView.canGoForward()) webView.goForward();
        });

        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                String query = urlBar.getText().toString().trim();
                if (!query.isEmpty()) {
                    String url;
                    if (query.startsWith("http://") || query.startsWith("https://")) {
                        url = query;
                    } else {
                        url = "https://www.google.com/search?q=" + Uri.encode(query);
                    }
                    webView.loadUrl(url);
                }
                return true;
            }
            return false;
        });
    }

    // The rest of the methods remain the same as before (setupWebView, isAdBlocked, etc.)
    // Copy them from the previous MainActivity.java

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLoadsImagesAutomatically(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                urlBar.setText(url);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (isAdBlocked(url) || isVideoUrl(url)) {
                    return new WebResourceResponse("text/plain", "utf-8",
                            new ByteArrayInputStream(new byte[0]));
                }
                return super.shouldInterceptRequest(view, request);
            }
        });
    }

    private boolean isAdBlocked(String url) {
        try {
            String host = Uri.parse(url).getHost();
            if (host == null) return false;
            synchronized (blockedDomains) {
                return blockedDomains.contains(host);
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isVideoUrl(String url) {
        String lower = url.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".3gp")
                || lower.contains(".m3u8") || lower.contains("/videoplayback?")
                || lower.contains("youtube.com/get_video");
    }

    private void loadBlocklistsInBackground() {
        executor.execute(() -> {
            String[] listUrls = {
                    "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/pro.txt",
                    "https://big.oisd.nl/domainswild2",
                    "https://big.oisd.nl/nsfw"
            };
            for (String listUrl : listUrls) {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(listUrl).openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String domain = extractDomain(line);
                            if (domain != null && !domain.isEmpty()) {
                                synchronized (blockedDomains) {
                                    blockedDomains.add(domain);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private String extractDomain(String line) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("!") || line.startsWith("[") || line.startsWith("#"))
            return null;
        if (line.startsWith("||")) {
            int end = line.indexOf('^');
            if (end == -1) end = line.length();
            return line.substring(2, end);
        }
        if (line.matches("^[a-zA-Z0-9.-]+$")) {
            return line;
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}

// package com.focus.browser;

// import android.annotation.SuppressLint;
// import android.net.Uri;
// import android.os.Bundle;
// import android.view.inputmethod.EditorInfo;
// import android.webkit.WebResourceRequest;
// import android.webkit.WebResourceResponse;
// import android.webkit.WebSettings;
// import android.webkit.WebView;
// import android.webkit.WebViewClient;
// import android.widget.EditText;
// import android.widget.ImageButton;
// import androidx.appcompat.app.AppCompatActivity;

// import java.io.BufferedReader;
// import java.io.ByteArrayInputStream;
// import java.io.InputStreamReader;
// import java.net.HttpURLConnection;
// import java.net.URL;
// import java.util.HashSet;
// import java.util.Set;
// import java.util.concurrent.ExecutorService;
// import java.util.concurrent.Executors;

// public class MainActivity extends AppCompatActivity {
//     private WebView webView;
//     private EditText urlBar;
//     private final Set<String> blockedDomains = new HashSet<>();
//     private final ExecutorService executor = Executors.newSingleThreadExecutor();

//     @Override
//     protected void onCreate(Bundle savedInstanceState) {
//         super.onCreate(savedInstanceState);
//         setContentView(R.layout.activity_main);

//         webView = findViewById(R.id.webview);
//         urlBar = findViewById(R.id.url_bar);
//         ImageButton btnBack = findViewById(R.id.btn_back);
//         ImageButton btnForward = findViewById(R.id.btn_forward);
//         ImageButton btnHome = findViewById(R.id.btn_home);

//         setupWebView();
//         loadBlocklistsInBackground();

//         webView.loadUrl("https://www.google.com");
//         urlBar.setText("https://www.google.com");

//         btnBack.setOnClickListener(v -> {
//             if (webView.canGoBack()) webView.goBack();
//         });

//         btnForward.setOnClickListener(v -> {
//             if (webView.canGoForward()) webView.goForward();
//         });

//         btnHome.setOnClickListener(v -> {
//             webView.loadUrl("https://www.google.com");
//         });

//         urlBar.setOnEditorActionListener((v, actionId, event) -> {
//             if (actionId == EditorInfo.IME_ACTION_GO) {
//                 String query = urlBar.getText().toString().trim();
//                 if (!query.isEmpty()) {
//                     String url;
//                     if (query.startsWith("http://") || query.startsWith("https://")) {
//                         url = query;
//                     } else {
//                         url = "https://www.google.com/search?q=" + Uri.encode(query);
//                     }
//                     webView.loadUrl(url);
//                 }
//                 return true;
//             }
//             return false;
//         });
//     }

//     @SuppressLint("SetJavaScriptEnabled")
//     private void setupWebView() {
//         WebSettings settings = webView.getSettings();
//         settings.setJavaScriptEnabled(true);
//         settings.setDomStorageEnabled(true);
//         settings.setLoadWithOverviewMode(true);
//         settings.setUseWideViewPort(true);
//         settings.setBuiltInZoomControls(true);
//         settings.setDisplayZoomControls(false);
//         settings.setLoadsImagesAutomatically(true);

//         webView.setWebViewClient(new WebViewClient() {
//             @Override
//             public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
//                 urlBar.setText(url);
//             }

//             @Override
//             public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
//                 String url = request.getUrl().toString();
//                 if (isAdBlocked(url) || isVideoUrl(url)) {
//                     return new WebResourceResponse("text/plain", "utf-8",
//                             new ByteArrayInputStream(new byte[0]));
//                 }
//                 return super.shouldInterceptRequest(view, request);
//             }
//         });
//     }

//     private boolean isAdBlocked(String url) {
//         try {
//             String host = Uri.parse(url).getHost();
//             if (host == null) return false;
//             synchronized (blockedDomains) {
//                 return blockedDomains.contains(host);
//             }
//         } catch (Exception e) {
//             return false;
//         }
//     }

//     private boolean isVideoUrl(String url) {
//         String lower = url.toLowerCase();
//         return lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".3gp")
//                 || lower.contains(".m3u8") || lower.contains("/videoplayback?")
//                 || lower.contains("youtube.com/get_video");
//     }

//     private void loadBlocklistsInBackground() {
//         executor.execute(() -> {
//             String[] listUrls = {
//                     "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/pro.txt",
//                     "https://big.oisd.nl/domainswild2",
//                     "https://big.oisd.nl/nsfw"
//             };
//             for (String listUrl : listUrls) {
//                 try {
//                     HttpURLConnection conn = (HttpURLConnection) new URL(listUrl).openConnection();
//                     conn.setConnectTimeout(10000);
//                     conn.setReadTimeout(10000);
//                     try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
//                         String line;
//                         while ((line = reader.readLine()) != null) {
//                             String domain = extractDomain(line);
//                             if (domain != null && !domain.isEmpty()) {
//                                 synchronized (blockedDomains) {
//                                     blockedDomains.add(domain);
//                                 }
//                             }
//                         }
//                     }
//                 } catch (Exception e) {
//                     e.printStackTrace();
//                 }
//             }
//         });
//     }

//     private String extractDomain(String line) {
//         line = line.trim();
//         if (line.isEmpty() || line.startsWith("!") || line.startsWith("[") || line.startsWith("#"))
//             return null;
//         if (line.startsWith("||")) {
//             int end = line.indexOf('^');
//             if (end == -1) end = line.length();
//             return line.substring(2, end);
//         }
//         if (line.matches("^[a-zA-Z0-9.-]+$")) {
//             return line;
//         }
//         return null;
//     }

//     @Override
//     protected void onDestroy() {
//         super.onDestroy();
//         executor.shutdown();
//     }
// }
