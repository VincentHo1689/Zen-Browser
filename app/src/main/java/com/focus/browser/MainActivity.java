package com.zen.browser;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.Spanned;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import androidx.activity.result.ActivityResultLauncher;

public class MainActivity extends AppCompatActivity {

    // --- UI elements ---
    private SwipeRefreshLayout swipeRefreshLayout;
    private WebView webView;
    private EditText urlBar;
    private TextView timerView;
    private ProgressBar progressBar;
    private ImageView qrButton;
    private View backWrapper, forwardWrapper;
    private RecyclerView suggestionList;

    // --- Data ---
    private BookmarksDbHelper bookmarksDb;
    private final Map<String, Set<String>> blocklists = new HashMap<>(); // listName -> set of domains
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // --- Timer ---
    private long startTime = 0;
    private long pausedTime = 0;
    private final Handler timerHandler = new Handler();
    private Runnable timerRunnable;
    private boolean isEditingUrl = false;

    // --- Suggestions ---
    private SuggestionAdapter suggestionAdapter;
    private final Handler suggestionHandler = new Handler();
    private Runnable suggestionFetcher;

    private static final Pattern URL_PATTERN = Pattern.compile(
            "^(https?://)?([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(/[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=]*)?$"
    );

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
                webView.loadUrl(formatUrl(result.getContents()));
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
        webView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            swipeRefreshLayout.setEnabled(scrollY == 0);
        });

        bookmarksDb = new BookmarksDbHelper(this);

        setupWebView();
        loadBlocklistsInBackground();
        Set<String> manualDomains = new HashSet<>(Arrays.asList(
            "ytimg.com"
        ));
        synchronized (blocklists) {
            blocklists.put("custom", manualDomains);
        }
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

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                lastLoadedUrl = url;
                if (!isEditingUrl) {
                    setUrlTextWithColoredProtocol(url);
                    updateTimerVisibility(false);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                swipeRefreshLayout.setRefreshing(false);
                if (!isEditingUrl) {
                    setUrlTextWithColoredProtocol(url);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("tel:") || url.startsWith("mailto:") || 
                    url.startsWith("sms:") || url.startsWith("geo:")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        view.getContext().startActivity(intent);
                        return true; // We handled it, stop WebView
                    } catch (Exception e) {
                        return true; // App failed to open, block it to avoid crash
                    }
                }
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    String safeUrl = enforceSafeSearch(url);
                    if (!safeUrl.equals(url)) {
                        view.loadUrl(safeUrl);
                        return true; 
                    }
                    return false; 
                }
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString().toLowerCase();
                if (isVideoUrlBlock(url)) {
                    view.post(() -> injectZenController(view));
                    //return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
                }

                // Check blocklists and show custom blocked page
                List<String> blockingLists = getBlockingLists(request.getUrl().getHost());
                if (!blockingLists.isEmpty()) {
                    return generateBlockPage(blockingLists);
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        webView.loadUrl("https://www.google.com/search?q=&pws=0&safe=active");
        setUrlTextWithColoredProtocol("https://www.google.com/search?q=&pws=0&safe=active");

        // --- URL bar focus handling (keyboard, QR, back/forward) ---
        urlBar.setOnFocusChangeListener((v, hasFocus) -> {
            isEditingUrl = hasFocus;
            updateTimerVisibility(hasFocus);

            if (hasFocus) {
                // Clear URL only when transitioning from unfocused to focused
                if (isLongPressFocus) {
                    // Long‑press focus – do not clear the URL
                    isLongPressFocus = false;  // reset flag
                } else {
                    // Normal tap focus – clear the field
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
                // Restore URL if user didn't type anything
                if (urlBar.getText().toString().trim().isEmpty()) {
                    setUrlTextWithColoredProtocol(lastLoadedUrl);
                }
                backWrapper.setVisibility(View.VISIBLE);
                forwardWrapper.setVisibility(View.VISIBLE);
                qrButton.setVisibility(View.GONE);
                suggestionList.setVisibility(View.GONE);
            }
        });

        // Track last touch position for long press
        final float[] lastTouchX = {0};
        urlBar.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                lastTouchX[0] = event.getX();
                return false; // let normal handling continue
            }
            return false;
        });

        urlBar.setOnLongClickListener(v -> {
            isLongPressFocus = true;  // prevent clearing in focus listener
            // Place cursor at the position of the long press
            int offset = urlBar.getOffsetForPosition(lastTouchX[0], 0);
            if (offset >= 0 && offset <= urlBar.getText().length()) {
                urlBar.setSelection(offset);
            } else {
                urlBar.setSelection(urlBar.getText().length()); // fallback to end
            }
            return true;
        });

        // Text watcher for suggestions (debounced)
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

        // Back/forward buttons
        btnBack.setOnClickListener(v -> { if (webView.canGoBack()) webView.goBack(); });
        btnForward.setOnClickListener(v -> { if (webView.canGoForward()) webView.goForward(); });
        btnBack.setOnLongClickListener(v -> { showBookmarkPopup(); return true; });
        btnForward.setOnLongClickListener(v -> { showDownloadListPopup(); return true; });

        // IME action Go
        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                String input = urlBar.getText().toString().trim();
                if (!input.isEmpty()) {
                    webView.loadUrl(formatUrl(input));
                } else {
                    webView.loadUrl("https://www.google.com/webhp?pws=0&safe=active");
                }
                urlBar.clearFocus();
                return true;
            }
            return false;
        });

        // --- WebView long press for image/link context menu ---
        webView.setOnLongClickListener(v -> {
            WebView.HitTestResult result = webView.getHitTestResult();
            int type = result.getType();
            if (type == WebView.HitTestResult.IMAGE_TYPE ||
                type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                String imageUrl = result.getExtra();
                showImageContextMenu(imageUrl);
                return true; // consumed, no text selection
            } else if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
                String linkUrl = result.getExtra();
                showLinkContextMenu(linkUrl);
                return true; // consumed
            }
            // For anything else (including plain text), let WebView handle it (text selection)
            return false;
        });
        // Timer
        startTime = SystemClock.elapsedRealtime();
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsed = (SystemClock.elapsedRealtime() - startTime) + pausedTime;
                long seconds = elapsed / 1000;
                long minutes = seconds / 60;
                long hours = minutes / 60;
                String timeStr = (hours > 0) ?
                        String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60)
                        : String.format("%d:%02d", minutes, seconds % 60);
                timerView.setText(timeStr);
                int color;
                if (minutes < 15) color = ContextCompat.getColor(MainActivity.this, R.color.timer_green);
                else if (minutes < 30) color = ContextCompat.getColor(MainActivity.this, R.color.timer_yellow);
                else color = ContextCompat.getColor(MainActivity.this, R.color.timer_red);
                timerView.setTextColor(color);
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    // ---- Suggestions ----
    private void setupSuggestionRecyclerView() {
        suggestionAdapter = new SuggestionAdapter(suggestionList, item -> {
            webView.loadUrl(formatUrl(item));
            urlBar.clearFocus();
        });
        suggestionList.setLayoutManager(new LinearLayoutManager(this));
        suggestionList.setAdapter(suggestionAdapter);
        // Dark/light background
        boolean isDark = (getResources().getConfiguration().uiMode &
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        suggestionList.setBackgroundColor(isDark ? Color.parseColor("#211f27") : Color.parseColor("#f8f7f4"));
    }

    private void fetchSuggestions(String query) {
        String key = query.trim().toLowerCase();

        // 1. Show cached results immediately
        List<String> cached = suggestionCache.get(key);
        if (cached != null) {
            suggestionAdapter.setSuggestions(cached);
        }

        // 2. Debounced network fetch
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
                        // 3. Cache the fresh response
                        synchronized (suggestionCache) {
                            suggestionCache.put(key, results);
                        }
                    }
                } catch (Exception e) {
                    return;  // keep whatever we already showed (cached)
                }
                // 4. Update UI only if the input still matches
                String currentQuery = urlBar.getText().toString().trim().toLowerCase();
                if (currentQuery.equals(key)) {
                    runOnUiThread(() -> suggestionAdapter.setSuggestions(results));
                }
            });
        };
        suggestionHandler.postDelayed(suggestionFetcher, 200);  // slightly faster debounce
    }

    // ---- Blocking ----
    private List<String> getBlockingLists(String host) {
        List<String> lists = new ArrayList<>();
        if (host == null) return lists;
        String checkHost = host;
        // Check each blocklist
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

    private WebResourceResponse generateBlockPage(List<String> blockingLists) {
        boolean isDark = (getResources().getConfiguration().uiMode &
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        String bgColor = isDark ? "#211f27" : "#f8f7f4";
        String textColor = isDark ? "#dddddd" : "#211f27";
        String mutedColor = isDark ? "#888888" : "#999999";
        String names = TextUtils.join(", ", blockingLists);
        // String html = "<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
        //         "<style>body { font-family: sans-serif; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; background: " + bgColor + "; color: " + textColor + "; } " +
        //         "div { text-align: center; padding: 20px; } h2 { font-size: 20px; } p { font-size: 14px; }</style></head>" +
        //         "<body><div><h2>Blocked</h2><p>This page is blocked by: <b>" + names + "</b></p></div></body></html>";
        String html = "<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
            "<style>" +
            "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; " +
            "display: flex; flex-direction: column; justify-content: center; align-items: center; " +
            "height: 100vh; margin: 0; background: " + bgColor + "; color: " + textColor + "; text-align: center; }" +
            ".content-wrapper { flex: 1; display: flex; flex-direction: column; justify-content: center; align-items: center; }" +
            ".footer { padding-bottom: 50px; width: 100%; }" +
            "h2 { font-weight: 300; font-size: 28px; margin-bottom: 12px; }" +
            "p { color: " + mutedColor + "; font-size: 16px; margin: 0; }" +
            ".reason { font-size: 12px; letter-spacing: 1px; text-transform: uppercase; color: " + mutedColor + "; " +
            "margin-top: 20px; opacity: 0.5; text-transform: uppercase; }" +
            "button { background: transparent; border: 1px solid " + textColor + "; color: " + textColor + "; " +
            "padding: 12px 30px; border-radius: 25px; cursor: pointer; font-size: 14px; " +
            "transition: all 0.3s ease; outline: none; }" +
            "button:active { background: " + textColor + "; color: " + bgColor + "; }" +
            "</style></head>" +
            "<body>" +
            "<div class='content-wrapper'>" +
            "  <h2>Take a deep breath.</h2>" +
            "  <p>Your focus matters.</p>" +
            "</div>" +
            "<div class='footer'>" +
            "  <button onclick='history.back()'>Continue my focus</button>" +
            "  <div class='reason'>Protected from: " + names + "</div>" +
            "</div>" +
            "</body></html>";
        return new WebResourceResponse("text/html", "utf-8", new ByteArrayInputStream(html.getBytes()));
    }

    private void showBookmarkPopup() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(24, 24, 24, 40);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        boolean isDarkMode = (getResources().getConfiguration().uiMode &
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int titleTextColor = isDarkMode ? Color.parseColor("#dddddd") : Color.parseColor("#211f27");

        // Title bar with add/delete
        android.widget.RelativeLayout titleBar = new android.widget.RelativeLayout(this);
        titleBar.setPadding(16, 16, 16, 32);

        TextView title = new TextView(this);
        title.setText("Bookmarks");
        title.setTextSize(20);
        title.setTextColor(titleTextColor);
        title.setGravity(Gravity.CENTER);
        android.widget.RelativeLayout.LayoutParams titleParams = new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT, android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
        title.setLayoutParams(titleParams);
        titleBar.addView(title);

        LinearLayout actionButtons = new LinearLayout(this);
        actionButtons.setOrientation(LinearLayout.HORIZONTAL);
        actionButtons.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.RelativeLayout.LayoutParams actionParams = new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT, android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
        actionParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT);
        actionParams.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
        actionButtons.setLayoutParams(actionParams);

        ImageView addBtn = new ImageView(this);
        addBtn.setImageResource(R.drawable.ic_minimalist_add);
        addBtn.setPadding(12, 2, 12, 2);
        if (isDarkMode) addBtn.setColorFilter(Color.parseColor("#dddddd"));
        ImageView deleteBtn = new ImageView(this);
        deleteBtn.setImageResource(R.drawable.ic_minimalist_delete);
        deleteBtn.setPadding(12, 2, 12, 2);
        if (isDarkMode) deleteBtn.setColorFilter(Color.parseColor("#dddddd"));
        actionButtons.addView(addBtn);
        actionButtons.addView(deleteBtn);
        titleBar.addView(actionButtons);
        container.addView(titleBar);

        // Scrollable list
        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);   // height=0, weight=1 → fill remaining
        scroll.setLayoutParams(scrollParams);
        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);

        List<BookmarksDbHelper.Bookmark> bookmarks = bookmarksDb.getAllBookmarks();
        Set<Long> selectedIds = new HashSet<>();
        int iconSize = (int) dstPx(20);

        AlertDialog[] dialogHolder = new AlertDialog[1];
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(container)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.setOnShowListener(dialogInterface -> {
            // Set the window size first
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            int dialogWidth = (int)(screenWidth * 0.8);
            int dialogHeight = (int)(screenHeight * 0.65);
            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(dialogWidth, dialogHeight);
            }
            // Force the container to match the dialog height
            ViewGroup.LayoutParams params = container.getLayoutParams();
            params.height = dialogHeight;
            container.setLayoutParams(params);
            container.requestLayout();
        });
        dialog.setCanceledOnTouchOutside(true);
        dialogHolder[0] = dialog;

        for (BookmarksDbHelper.Bookmark bm : bookmarks) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(8, 12, 8, 12);

            ImageView selectIcon = new ImageView(this);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconParams.gravity = Gravity.CENTER_VERTICAL;  // <-- add this
            selectIcon.setLayoutParams(iconParams);
            selectIcon.setImageResource(R.drawable.ic_checkbox_off);
            selectIcon.setOnClickListener(v -> {
                if (selectedIds.contains(bm.id)) {
                    selectedIds.remove(bm.id);
                    selectIcon.setImageResource(R.drawable.ic_checkbox_off);
                } else {
                    selectedIds.add(bm.id);
                    selectIcon.setImageResource(R.drawable.ic_checkbox_on);
                }
            });

            TextView tv = new TextView(this);
            tv.setText(bm.title);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setTextSize(16);
            tv.setPadding(24, 0, 16, 0);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(selectIcon);
            row.addView(tv);
            listContainer.addView(row);
        }
        scroll.addView(listContainer);
        container.addView(scroll);

        // Rounded background
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dstPx(16));
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.windowBackground, typedValue, true);
        int bg = (typedValue.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT &&
                typedValue.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) ? typedValue.data : Color.parseColor("#f8f7f4");
        shape.setColor(bg);
        container.setBackground(shape);

        addBtn.setOnClickListener(v -> {
            String titleStr = webView.getTitle();
            String url = webView.getUrl();
            if (url != null && !url.isEmpty()) {
                boolean exists = false;
                for (BookmarksDbHelper.Bookmark bm : bookmarks)
                    if (url.equalsIgnoreCase(bm.url)) { exists = true; break; }
                if (!exists) {
                    bookmarksDb.addBookmark(titleStr != null ? titleStr : url, url);
                    Toast.makeText(this, "Bookmark added", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    showBookmarkPopup();
                } else Toast.makeText(this, "Already bookmarked!", Toast.LENGTH_SHORT).show();
            }
        });
        deleteBtn.setOnClickListener(v -> {
            if (!selectedIds.isEmpty()) {
                for (Long id : selectedIds) bookmarksDb.deleteBookmark(id);
                Toast.makeText(this, "Deleted selected", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                showBookmarkPopup();
            } else Toast.makeText(this, "Tap icons to select", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void showDownloadListPopup() {
        LinearLayout container = new LinearLayout(this);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(24, 24, 24, 40);

        boolean isDark = (getResources().getConfiguration().uiMode &
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int textColor = isDark ? Color.parseColor("#dddddd") : Color.parseColor("#211f27");

        TextView title = new TextView(this);
        title.setText("Downloads");
        title.setTextSize(20);
        title.setTextColor(textColor);
        title.setGravity(Gravity.CENTER);
        title.setPadding(16, 16, 16, 32);
        container.addView(title);

        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scroll.setLayoutParams(scrollParams);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (dm != null) {
            Cursor cursor = dm.query(new DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL));
            if (cursor.moveToFirst()) {
                int titleIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE);
                int uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                do {
                    String titleStr = cursor.getString(titleIdx);
                    String uri = cursor.getString(uriIdx);
                    TextView tv = new TextView(this);
                    tv.setText(titleStr != null ? titleStr : uri);
                    tv.setSingleLine(true);
                    tv.setEllipsize(TextUtils.TruncateAt.END);
                    tv.setPadding(16, 12, 16, 12);
                    tv.setOnClickListener(v -> {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(Uri.parse(uri), "*/*");
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(this, "Cannot open file", Toast.LENGTH_SHORT).show();
                        }
                    });
                    // Capture download ID now, before cursor is closed
                final long downloadId = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_ID));
                final String localUriStr = uri;

                tv.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Delete download?")
                            .setMessage("Remove this download and its file?")
                            .setPositiveButton("Delete", (dialog, which) -> {
                                if (dm != null) {
                                    dm.remove(downloadId);
                                }
                                // Delete file using ContentResolver
                                try {
                                    getContentResolver().delete(Uri.parse(localUriStr), null, null);
                                } catch (Exception ignored) {}
                                Toast.makeText(MainActivity.this, "Deleted", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                                showDownloadListPopup();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    return true;
                });
                    list.addView(tv);
                    
                } while (cursor.moveToNext());
            } else {
                TextView empty = new TextView(this);
                empty.setText("No downloads yet");
                empty.setPadding(16, 16, 16, 16);
                empty.setGravity(Gravity.CENTER);
                list.addView(empty);
            }
            cursor.close();
        }
        scroll.addView(list);
        container.addView(scroll);

        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dstPx(16));
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.windowBackground, typedValue, true);
        int bg = (typedValue.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT &&
                typedValue.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) ? typedValue.data : Color.parseColor("#f8f7f4");
        shape.setColor(bg);
        container.setBackground(shape);

        AlertDialog dialog = new AlertDialog.Builder(this)
        .setView(container)
        .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.setCanceledOnTouchOutside(true);

        dialog.setOnShowListener(dialogInterface -> {
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            int dialogWidth = (int)(screenWidth * 0.8);
            int dialogHeight = (int)(screenHeight * 0.65);
            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(dialogWidth, dialogHeight);
            }
            ViewGroup.LayoutParams params = container.getLayoutParams();
            params.height = dialogHeight;
            container.setLayoutParams(params);
            container.requestLayout();
        });
        dialog.show();
    }

    // ---- Image / Link context menu ----
    private void showImageContextMenu(final String imageUrl) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Image");
        builder.setItems(new String[]{"Download image"}, (dialog, which) -> {
            // Show progress popup
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

            // Start download
            downloadFile(imageUrl);

            // Dismiss after 2 seconds
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

    // ---- Blocklist loading ----
    private void loadBlocklistsInBackground() {
        String[][] lists = {
            {"nsfw", "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/nsfw.txt"},
            {"social", "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/social.txt"},
            {"gambling", "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/gambling.mini.txt"},
            {"urlshortener", "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/urlshortener.txt"},
            {"hoster", "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/hoster.txt"},
            {"nosafesearch", "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/nosafesearch.txt"},
            {"tif", "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/tif.medium.txt"},
            {"pro", "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/domains/pro.txt"}
        };
        for (String[] entry : lists) {
            String name = entry[0];
            String url = entry[1];
            executor.execute(() -> {
                Set<String> set = new HashSet<>();
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
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

    // ---- Helpers ----
    private String formatUrl(String input) {
        input = input.trim();
        if (input.isEmpty()) return "https://www.google.com/webhp?pws=0&safe=active";
        if (URL_PATTERN.matcher(input).matches()) {
            if (!input.startsWith("http://") && !input.startsWith("https://"))
                input = "https://" + input;
            return enforceSafeSearch(input);
        }
        return "https://www.google.com/search?q=" + Uri.encode(input) + "&safe=active";
    }

    private void setUrlTextWithColoredProtocol(String fullUrl) {
        if (fullUrl == null) return;
        // Only color if it starts with http:// or https://
        String lower = fullUrl.toLowerCase();
        int color;
        if (lower.startsWith("https://")) {
            color = ContextCompat.getColor(this, R.color.timer_green);  // green
        } else if (lower.startsWith("http://")) {
            color = ContextCompat.getColor(this, R.color.timer_red);    // red
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

    private String enforceSafeSearch(String url) {
        if (url == null) return url;
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host != null && (host.equals("www.google.com") || host.equals("google.com"))) {
                String path = uri.getPath();
                if (path != null && path.startsWith("/search")) {
                    String query = uri.getQuery();
                    if (query == null) query = "";

                    // 1. Handle SafeSearch
                    if (!query.contains("safe=")) {
                        query = query.isEmpty() ? "safe=active" : query + "&safe=active";
                    } else if (!query.contains("safe=active") && !query.contains("safe=moderate")) {
                        query = query.replaceAll("safe=[^&]*", "safe=active");
                    }

                    // 2. Add Personalization Removal (pws=0)
                    if (!query.contains("pws=0")) {
                        query += "&pws=0";
                    }

                    return uri.buildUpon().encodedQuery(query).build().toString();
                }
            }
        } catch (Exception ignored) {}
        return url;
    }

    private static final Set<String> TRUSTED_DOMAINS = new HashSet<>(Arrays.asList(
        "wikipedia.org", "notion.so", "github.com", "stackoverflow.com", "khanacademy.org", "overleaf.com","scholar.google.com","researchgate.net"
    ));

    private boolean isTrustedSite(String url) {
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

    private boolean isVideoUrlBlock(String url) {
    String lower = url.toLowerCase();
    if (isTrustedSite(lower)) {
        return false; 
    }
    // Block only video file extensions and stream endpoints
    return lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".3gp")
            || lower.endsWith(".m3u8") || lower.endsWith(".mov") || lower.endsWith(".avi")
            || lower.endsWith(".ts") || lower.endsWith(".flv")
            || lower.contains("videoplayback")        // YouTube video stream
            || lower.contains("googlevideo.com/videoplayback")
            || lower.contains("manifest.googlevideo.com");  // YouTube DRM manifest
    }

    private float dstPx(int dp) { return dp * getResources().getDisplayMetrics().density; }
    private float getPopupCornerRadiusFixer() { return dstPx(16); }


    private void injectZenController(WebView view) {
        String js = "window.ZenController = {" +
                    "  init: function() {" +
                    "    var observer = new MutationObserver(function(mutations) {" +
                    "      var videos = document.querySelectorAll('video');" +
                    "      videos.forEach(function(v) {" +
                    "        if (!v.dataset.zenProcessed) {" + // Only process each video once
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
                    "    var rect = v.getBoundingClientRect();" +
                    "    var msg = document.createElement('div');" +
                    "    msg.innerText = 'Audio only';" +
                    "    msg.style.position = 'fixed';" +
                    "    msg.style.left = (rect.left + rect.width/2) + 'px';" +
                    "    msg.style.top = (rect.top + rect.height/2) + 'px';" +
                    "    msg.style.transform = 'translate(-50%, -50%)';" +
                    "    msg.style.zIndex = '9999';" +
                    "    msg.style.color = 'white';" +
                    "    msg.style.background = 'rgba(0,0,0,0.7)';" +
                    "    msg.style.padding = '10px 20px';" +
                    "    msg.style.borderRadius = '5px';" +
                    "    msg.style.pointerEvents = 'none';" +
                    "    document.body.appendChild(msg);" +
                    "    var gObserver = new MutationObserver(function() {" +
                    "      if (!document.body.contains(v)) { msg.remove(); gObserver.disconnect(); }" +
                    "    });" +
                    "    gObserver.observe(document.body, { childList: true, subtree: true });" +
                    "  }" +
                    "};" +
                    "window.ZenController.init();";
        
        view.evaluateJavascript(js, null);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        webView.getSettings().setTextZoom(100); // Not needed for selection, but good practice
        settings.setDisplayZoomControls(false);
        settings.setLoadsImagesAutomatically(true);
    }

    @Override
    public void onBackPressed() {
        if (urlBar.hasFocus()) {
            urlBar.clearFocus(); // automatically dismisses keyboard, restores URL
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override protected void onPause() {
        super.onPause();
        pausedTime += (SystemClock.elapsedRealtime() - startTime);
        startTime = 0;
        timerHandler.removeCallbacks(timerRunnable);
    }
    @Override protected void onResume() {
        super.onResume();
        startTime = SystemClock.elapsedRealtime();
        timerHandler.post(timerRunnable);
    }
    @Override protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        timerHandler.removeCallbacks(timerRunnable);
        bookmarksDb.close();
    }
}