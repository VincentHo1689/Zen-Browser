package com.zen.browser;

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
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectScrollDetection(view);
                injectZenController(view);
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

        btnBack.setOnClickListener(v -> { if (webView.canGoBack()) webView.goBack(); });
        btnForward.setOnClickListener(v -> { if (webView.canGoForward()) webView.goForward(); });
        btnBack.setOnLongClickListener(v -> { DialogHelper.showBookmarkPopup(this, bookmarksDb, webView); return true; });
        btnForward.setOnLongClickListener(v -> { DialogHelper.showDownloadListPopup(this); return true; });

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
            executor.execute(() -> {
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

    public void injectZenController(WebView view) {
        String js = "window.ZenController = {" +
                    "  init: function() {" +
                    "    var observer = new MutationObserver(function(mutations) {" +
                    "      var videos = document.querySelectorAll('video');" +
                    "      videos.forEach(function(v) {" +
                    "        if (!v.dataset.zenProcessed) {" + 
                    "          v.dataset.zenProcessed = 'true';" +
                    "          window.ZenController.hideVideo(v);" +
                    "        }" +
                    "      });" +
                    "    });" +
                    "    observer.observe(document.body, { childList: true, subtree: true });" +
                    "  }," +
                    "  hideVideo: function(v) {" +
                    "    v.style.opacity = '0';" +
                    "    v.style.pointerEvents = 'none';" +
                    "    v.play();" +
                    "    if (!v.parentNode) return;" +
                    "    if (window.getComputedStyle(v.parentNode).position === 'static') {" +
                    "      v.parentNode.style.position = 'relative';" +
                    "    }" +
                    "    var msg = document.createElement('div');" +
                    "    msg.innerText = 'Audio only';" +
                    "    msg.style.position = 'absolute';" +
                    "    msg.style.transform = 'translate(-50%, -50%)';" +
                    "    msg.style.zIndex = '9999';" +
                    "    msg.style.color = 'white';" +
                    "    msg.style.background = 'rgba(0,0,0,0.7)';" +
                    "    msg.style.padding = '10px 20px';" +
                    "    msg.style.borderRadius = '5px';" +
                    "    msg.style.pointerEvents = 'none';" +
                    "    v.parentNode.appendChild(msg);" +
                    "    " +
                    "    var ro = new ResizeObserver(function() {" +
                    "      if (v.offsetWidth === 0 || v.offsetHeight === 0) {" +
                    "        msg.style.display = 'none';" +
                    "      } else {" +
                    "        msg.style.display = 'block';" +
                    "        msg.style.left = (v.offsetLeft + v.offsetWidth / 2) + 'px';" +
                    "        msg.style.top = (v.offsetTop + v.offsetHeight / 2) + 'px';" +
                    "      }" +
                    "    });" +
                    "    ro.observe(v);" +
                    "    " +
                    "    var mo = new MutationObserver(function() {" +
                    "      if (!document.body.contains(v)) { msg.remove(); ro.disconnect(); mo.disconnect(); }" +
                    "    });" +
                    "    mo.observe(document.body, { childList: true, subtree: true });" +
                    "  }" +
                    "};" +
                    "window.ZenController.init();";
        
        view.evaluateJavascript(js, null);
    }

    private void injectScrollDetection(WebView view) {
        String scrollJs =
                "(function() {" +
                "  function getScrollTop() {" +
                "    var se = document.scrollingElement;" +
                "    if (se && se.scrollTop > 0) return se.scrollTop;" +
                "    if (document.body && document.body.scrollTop > 0) return document.body.scrollTop;" +
                "    if (document.documentElement && document.documentElement.scrollTop > 0) return document.documentElement.scrollTop;" +
                "    var all = document.querySelectorAll('*');" +
                "    for (var i = 0; i < all.length; i++) {" +
                "      var el = all[i];" +
                "      var style = window.getComputedStyle(el);" +
                "      if ((style.overflowY === 'auto' || style.overflowY === 'scroll') && el.scrollTop > 0) {" +
                "        return el.scrollTop;" +
                "      }" +
                "    }" +
                "    return 0;" +
                "  }" +
                "  function updateRefreshState() {" +
                "    var canRefresh = (getScrollTop() === 0);" +
                "    if (window.ZenBridge) window.ZenBridge.setCanRefresh(canRefresh);" +
                "  }" +
                "  window.addEventListener('scroll', updateRefreshState, true);" +
                "  window.addEventListener('touchmove', updateRefreshState, true);" +
                "  updateRefreshState();" +
                "})();";
        view.evaluateJavascript(scrollJs, null);
    }

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
        if (webView != null) {
            webView.evaluateJavascript("window.isAppBackgrounded = true;", null);
        }
    }
    @Override protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.evaluateJavascript("window.isAppBackgrounded = false;", null);
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
        timerManager.destroy();
        bookmarksDb.close();
    }
}
