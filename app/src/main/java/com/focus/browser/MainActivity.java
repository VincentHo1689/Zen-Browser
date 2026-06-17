package com.browser.zen;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.Spanned;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import androidx.activity.result.ActivityResultLauncher;

public class MainActivity extends AppCompatActivity {

    // --- UI elements ---
    private SwipeRefreshLayout swipeRefreshLayout;
    private NestedScrollWebView webView;
    private EditText urlBar;
    private TextView timerView;
    private ProgressBar progressBar;
    private ImageView qrButton;
    private View backWrapper, forwardWrapper;
    private RecyclerView suggestionList;

    // --- Data ---
    private BookmarksDbHelper bookmarksDb;
    private BlocklistManager blocklistManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService suggestionExecutor = Executors.newSingleThreadExecutor();

    // --- Timer ---
    private TimerManager timerManager;
    private boolean isEditingUrl = false;

    // --- Suggestions ---
    private SuggestionAdapter suggestionAdapter;
    private final Handler suggestionHandler = new Handler();
    private Runnable suggestionFetcher;

    // --- URL ---
    private String lastLoadedUrl = "";
    private ActivityResultLauncher<ScanOptions> qrScanLauncher;
    private boolean isLongPressFocus = false;

    private final Map<String, List<String>> suggestionCache = new LinkedHashMap<String, List<String>>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > 50;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        qrScanLauncher = registerForActivityResult(new ScanContract(), result -> {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            if (result.getContents() != null) {
                webView.loadUrl(UrlHelper.formatUrl(result.getContents()));
                urlBar.clearFocus();
            }
        });

        // Find views
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        webView = findViewById(R.id.webview);
        urlBar = findViewById(R.id.url_bar);
        timerView = findViewById(R.id.timer);
        progressBar = findViewById(R.id.progress_bar);
        ImageView btnBack = findViewById(R.id.btn_back_image);
        ImageView btnForward = findViewById(R.id.btn_forward_image);
        qrButton = findViewById(R.id.btn_qr);
        boolean isDark = (getResources().getConfiguration().uiMode &
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        if (isDark) {
            qrButton.setColorFilter(Color.parseColor("#f8f7f4"));
            btnBack.setColorFilter(Color.parseColor("#f8f7f4"));
            btnForward.setColorFilter(Color.parseColor("#f8f7f4"));
        }
        backWrapper = findViewById(R.id.back_wrapper);
        forwardWrapper = findViewById(R.id.forward_wrapper);
        suggestionList = findViewById(R.id.suggestion_list);

        // this is added as the targetSdk updated from 34 to 35
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            View mainRoot = findViewById(R.id.main_root);
            View bottomBar = findViewById(R.id.bottom_bar);
            
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(mainRoot, (v, insets) -> {
                androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
                androidx.core.graphics.Insets ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime());
                
                // 1. Add safe margin to the top of the root layout so content clears the status bar cleanly
                mainRoot.setPadding(0, systemBars.top, 0, 0);
                
                // 2. Adjust the bottom bar dynamically when the software keyboard pops up
                int keyboardHeight = ime.bottom;
                if (keyboardHeight > 0) {
                    bottomBar.setPadding(bottomBar.getPaddingLeft(), bottomBar.getPaddingTop(), 
                                      bottomBar.getPaddingRight(), keyboardHeight);
                } else {
                    bottomBar.setPadding(bottomBar.getPaddingLeft(), bottomBar.getPaddingTop(), 
                                      bottomBar.getPaddingRight(), systemBars.bottom);
                }
                return insets;
            });
        }

        // Setup swipe refresh
        swipeRefreshLayout.setOnRefreshListener(() -> webView.reload());
        swipeRefreshLayout.setEnabled(true); 

        // swipeRefreshLayout.setOnChildScrollUpCallback((parent, child) -> {
        //     if (webView.canScrollVertically(-1)) {
        //         return true;
        //     }
        //     return webView.getScrollY() > 0;
        // });

        bookmarksDb = new BookmarksDbHelper(this);
        blocklistManager = new BlocklistManager();
        timerManager = new TimerManager(this, timerView);

        setupWebView();
        blocklistManager.loadBlocklistsInBackground(executor);
        Set<String> manualDomains = new HashSet<>(Arrays.asList("ytimg.com"));
        blocklistManager.addManualDomains("custom", manualDomains);
        setupSuggestionRecyclerView();

        // QR button action (placeholder – launch a QR scanner)
        qrButton.setOnClickListener(v -> {
            try {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                ScanOptions options = new ScanOptions();
                options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
                options.setPrompt("Scan a QR code");
                options.setCameraId(0);
                options.setBeepEnabled(false);
                options.setBarcodeImageEnabled(false);
                options.setOrientationLocked(true);
                qrScanLauncher.launch(options);
            } catch (Exception e) {
                Toast.makeText(this, "No QR scanner found", Toast.LENGTH_SHORT).show();
            }
        });

        // WebView client
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
            }
        });

        // webView.setWebViewClient(new BrowserWebViewClient(this, blocklistManager));
        webView.setWebViewClient(new BrowserWebViewClient(this, blocklistManager) {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                injectScrollDetection(view);
                injectVisibilityOverride(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectScrollDetection(view);
                injectZenController(view);
                injectVisibilityOverride(view);

                updateStatusBarToMatchWebsite(view);
            }
        });

        webView.loadUrl("https://www.google.com/search?q=&pws=0&safe=active");
        setUrlTextWithColoredProtocol("https://www.google.com/search?q=&pws=0&safe=active");

        // --- URL bar focus handling (keyboard, QR, back/forward) ---
        urlBar.setOnFocusChangeListener((v, hasFocus) -> {
            isEditingUrl = hasFocus;
            updateTimerVisibility(hasFocus);

            if (hasFocus) {
                if (isLongPressFocus) {
                    isLongPressFocus = false;  
                } else {
                    lastLoadedUrl = urlBar.getText().toString();
                    urlBar.setText("");
                }
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                imm.showSoftInput(urlBar, InputMethodManager.SHOW_IMPLICIT);
                backWrapper.setVisibility(View.GONE);
                forwardWrapper.setVisibility(View.GONE);
                qrButton.setVisibility(View.VISIBLE);
                if (urlBar.getText().length() > 0) {
                    suggestionList.setVisibility(View.VISIBLE);
                }
            } else {
                if (urlBar.getText().toString().trim().isEmpty()) {
                    setUrlTextWithColoredProtocol(lastLoadedUrl);
                }
                backWrapper.setVisibility(View.VISIBLE);
                forwardWrapper.setVisibility(View.VISIBLE);
                qrButton.setVisibility(View.GONE);
                suggestionList.setVisibility(View.GONE);
            }
        });

        final float[] lastTouchX = {0};
        urlBar.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                lastTouchX[0] = event.getX();
            }
            return false;
        });

        urlBar.setOnLongClickListener(v -> {
            isLongPressFocus = true;  
            int offset = urlBar.getOffsetForPosition(lastTouchX[0], 0);
            if (offset >= 0 && offset <= urlBar.getText().length()) {
                urlBar.setSelection(offset);
            } else {
                urlBar.setSelection(urlBar.getText().length()); 
            }
            return true;
        });

        urlBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (isEditingUrl && s.length() > 0) {
                    suggestionList.setVisibility(View.VISIBLE);
                    fetchSuggestions(s.toString());
                } else {
                    suggestionList.setVisibility(View.GONE);
                    suggestionAdapter.setSuggestions(new ArrayList<>());
                }
            }
        });

        // btnBack.setOnClickListener(v -> { if (webView.canGoBack()) webView.goBack(); });
        // btnForward.setOnClickListener(v -> { if (webView.canGoForward()) webView.goForward(); });
        // btnBack.setOnLongClickListener(v -> { DialogHelper.showBookmarkPopup(this, bookmarksDb, webView); return true; });
        // btnForward.setOnLongClickListener(v -> { DialogHelper.showDownloadListPopup(this); return true; });

        // Attach listeners to the outer wrappers instead of the inner image views
        backWrapper.setOnClickListener(v -> { if (webView.canGoBack()) webView.goBack(); });
        forwardWrapper.setOnClickListener(v -> { if (webView.canGoForward()) webView.goForward(); });

        backWrapper.setOnLongClickListener(v -> { 
            DialogHelper.showBookmarkPopup(this, bookmarksDb, webView); 
            return true; 
        });
        forwardWrapper.setOnLongClickListener(v -> { 
            DialogHelper.showDownloadListPopup(this); 
            return true; 
        });

        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                String input = urlBar.getText().toString().trim();
                if (!input.isEmpty()) {
                    webView.loadUrl(UrlHelper.formatUrl(input));
                } else {
                    webView.loadUrl("https://www.google.com/webhp?pws=0&safe=active");
                }
                urlBar.clearFocus();
                return true;
            }
            return false;
        });

        webView.setOnLongClickListener(v -> {
            WebView.HitTestResult result = webView.getHitTestResult();
            int type = result.getType();
            if (type == WebView.HitTestResult.IMAGE_TYPE ||
                type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                String imageUrl = result.getExtra();
                showImageContextMenu(imageUrl);
                return true; 
            } else if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
                String linkUrl = result.getExtra();
                showLinkContextMenu(linkUrl);
                return true; 
            }
            return false;
        });
        
        timerManager.start();

        Intent serviceIntent = new Intent(this, BackgroundService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    public void injectZenController(WebView view) {
        WebViewInjector.injectZenController(view);
    }
    public void injectScrollDetection(WebView view) {
        WebViewInjector.injectScrollDetection(view);
    }
    public void injectVisibilityOverride(WebView view) {
        WebViewInjector.injectVisibilityOverride(view);
    }

    public void onPageStarted(String url) {
        lastLoadedUrl = url;
        if (!isEditingUrl) {
            setUrlTextWithColoredProtocol(url);
            updateTimerVisibility(false);
        }
    }

    public void onPageFinished(String url) {
        swipeRefreshLayout.setRefreshing(false);
        if (!isEditingUrl) {
            setUrlTextWithColoredProtocol(url);
        }
    }

    // ---- Suggestions ----
    private void setupSuggestionRecyclerView() {
        suggestionAdapter = new SuggestionAdapter(suggestionList, item -> {
            webView.loadUrl(UrlHelper.formatUrl(item));
            urlBar.clearFocus();
        });
        suggestionList.setLayoutManager(new LinearLayoutManager(this));
        suggestionList.setAdapter(suggestionAdapter);
        boolean isDark = (getResources().getConfiguration().uiMode &
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        suggestionList.setBackgroundColor(isDark ? Color.parseColor("#211f27") : Color.parseColor("#f8f7f4"));
    }

    private void fetchSuggestions(String query) {
        String key = query.trim().toLowerCase();

        List<String> cached = suggestionCache.get(key);
        if (cached != null) {
            suggestionAdapter.setSuggestions(cached);
        }

        suggestionHandler.removeCallbacks(suggestionFetcher);
        suggestionFetcher = () -> {
            suggestionExecutor.execute(() -> {
                List<String> results = new ArrayList<>();
                try {
                    String encoded = URLEncoder.encode(query, "utf-8");
                    String url = "https://suggestqueries.google.com/complete/search?client=firefox&q=" + encoded;
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(3000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    JSONArray arr = new JSONArray(sb.toString());
                    if (arr.length() > 1) {
                        JSONArray suggArr = arr.getJSONArray(1);
                        for (int i = 0; i < suggArr.length(); i++) {
                            results.add(suggArr.getString(i));
                        }
                        synchronized (suggestionCache) {
                            suggestionCache.put(key, results);
                        }
                    }
                } catch (Exception e) {
                    return;  
                }
                String currentQuery = urlBar.getText().toString().trim().toLowerCase();
                if (currentQuery.equals(key)) {
                    runOnUiThread(() -> suggestionAdapter.setSuggestions(results));
                }
            });
        };
        suggestionHandler.postDelayed(suggestionFetcher, 200);  
    }

    // ---- Image / Link context menu ----
    private void showImageContextMenu(final String imageUrl) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Image");
        builder.setItems(new String[]{"Download image"}, (dialog, which) -> {
            LinearLayout progressContainer = new LinearLayout(this);
            progressContainer.setOrientation(LinearLayout.HORIZONTAL);
            progressContainer.setPadding(40, 20, 40, 20);
            ProgressBar pb = new ProgressBar(this);
            pb.setIndeterminate(true);
            progressContainer.addView(pb);
            TextView text = new TextView(this);
            text.setText("Starting download...");
            text.setPadding(20, 0, 0, 0);
            progressContainer.addView(text);
            AlertDialog progressDialog = new AlertDialog.Builder(this)
                    .setView(progressContainer)
                    .setCancelable(false)
                    .create();
            progressDialog.show();

            downloadFile(imageUrl);

            new Handler().postDelayed(progressDialog::dismiss, 2000);
        });
        builder.show();
    }

    private void showLinkContextMenu(final String linkUrl) {
        new AlertDialog.Builder(this)
                .setTitle("Link")
                .setItems(new String[]{"Open", "Copy URL"}, (dialog, which) -> {
                    if (which == 0) webView.loadUrl(linkUrl);
                    else {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("URL", linkUrl));
                        Toast.makeText(this, "URL copied", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void downloadFile(String url) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        dm.enqueue(request);
        Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
    }

    // ---- Helpers ----
    private void updateStatusBarToMatchWebsite(WebView view) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) return;

        // Extract the actual computed background color of the web page body
        view.evaluateJavascript(
            "window.getComputedStyle(document.body).backgroundColor",
            rgbString -> {
                if (rgbString == null || rgbString.equals("null") || rgbString.equals("\"rgba(0, 0, 0, 0)\"")) return;

                try {
                    // Parse "rgb(r, g, b)" or "rgba(r, g, b, a)" string
                    String clean = rgbString.replace("\"", "").replaceAll("[^0-9,]", "");
                    String[] parts = clean.split(",");
                    if (parts.length < 3) return;

                    int r = Integer.parseInt(parts[0].trim());
                    int g = Integer.parseInt(parts[1].trim());
                    int b = Integer.parseInt(parts[2].trim());
                    int webColor = android.graphics.Color.rgb(r, g, b);

                    // Apply background color to both the window status bar and your layout's top spacing layer
                    getWindow().setStatusBarColor(webColor);
                    View mainRoot = findViewById(R.id.main_root);
                    if (mainRoot != null) {
                        mainRoot.setBackgroundColor(webColor);
                    }

                    // Calculate color contrast (Luminance formula) to see if website color is light or dark
                    double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
                    boolean isWebPageDark = luminance < 0.5;

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        View decorView = getWindow().getDecorView();
                        int flags = decorView.getSystemUiVisibility();
                        if (isWebPageDark) {
                            // Dark background -> White icons
                            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                        } else {
                            // Light background -> Black/Grey icons
                            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                        }
                        decorView.setSystemUiVisibility(flags);
                    }
                } catch (Exception e) {
                    // Fallback safety if JS returns an unparsable string format
                }
            }
        );
    }

    private void clearAllDataExceptCookies() {
        if (webView != null) {
            webView.clearCache(true);            // clears cache files (images, js, etc.)
            webView.clearHistory();              // clears back/forward list
            webView.clearFormData();             // clears autofill / form entries
        }
        android.webkit.WebStorage.getInstance().deleteAllData();
        deleteDatabase("webview.db");
        deleteDatabase("webviewCache.db");
    }

    private void setUrlTextWithColoredProtocol(String fullUrl) {
        if (fullUrl == null) return;
        String lower = fullUrl.toLowerCase();
        int color;
        if (lower.startsWith("https://")) {
            color = ContextCompat.getColor(this, R.color.timer_green);  
        } else if (lower.startsWith("http://")) {
            color = ContextCompat.getColor(this, R.color.timer_red);    
        } else {
            urlBar.setText(fullUrl);
            return;
        }
        SpannableString spannable = new SpannableString(fullUrl);
        int prefixLen = (lower.startsWith("https://")) ? 8 : 7;
        spannable.setSpan(new ForegroundColorSpan(color), 0, prefixLen, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        urlBar.setText(spannable);
    }

    private void updateTimerVisibility(boolean urlBarHasFocus) {
        android.view.ViewGroup.LayoutParams urlParams = urlBar.getLayoutParams();
        if (urlParams == null) return;

        urlParams.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        urlBar.setLayoutParams(urlParams);

        if (urlBarHasFocus) {
            urlBar.setPadding(urlBar.getPaddingLeft(), urlBar.getPaddingTop(), (int) dstPx(48), urlBar.getPaddingBottom());
            timerView.animate().alpha(0f).setDuration(200).withEndAction(() -> timerView.setVisibility(View.GONE)).start();
        } else {
            int rightPadding = (int) dstPx(44);
            urlBar.setPadding(urlBar.getPaddingLeft(), urlBar.getPaddingTop(), rightPadding, urlBar.getPaddingBottom());
            timerView.setAlpha(0f);
            timerView.setVisibility(View.VISIBLE);
            timerView.animate().alpha(1f).setDuration(200).start();
        }
    }

    private float dstPx(int dp) { return dp * getResources().getDisplayMetrics().density; }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        webView.getSettings().setTextZoom(100);
        settings.setDisplayZoomControls(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setDatabaseEnabled(true);

        android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);
        
        String defaultUA = settings.getUserAgentString();
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        
        String customUA = "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
        settings.setUserAgentString(customUA);

        webView.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void setCanRefresh(final boolean canRefresh) {
                runOnUiThread(() -> swipeRefreshLayout.setEnabled(canRefresh));
            }
        }, "ZenBridge");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, android.webkit.JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm())
                        .setCancelable(false)
                        .create()
                        .show();
                return true;
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                WebView newWebView = new WebView(MainActivity.this);
                newWebView.getSettings().setJavaScriptEnabled(true);
                newWebView.getSettings().setUserAgentString(defaultUA.replace("; wv", ""));
                
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(newWebView);
                resultMsg.sendToTarget();
                
                newWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                        webView.loadUrl(request.getUrl().toString());
                        return true;
                    }
                });
                return true;
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (urlBar.hasFocus()) {
            urlBar.clearFocus(); 
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override protected void onPause() {
        super.onPause();
        timerManager.pause();
    }
    @Override protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
        timerManager.resume();
    }

    @Override protected void onStop() {
        super.onStop();
    }

    @Override protected void onDestroy() {
        clearAllDataExceptCookies();
        super.onDestroy();
        executor.shutdown();
        suggestionExecutor.shutdown();
        timerManager.destroy();
        bookmarksDb.close();

        stopService(new Intent(this, BackgroundService.class));

        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
    }
}
