package com.focus.browser;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
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
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private EditText urlBar;
    private TextView timerView;
    private ProgressBar progressBar;
    private BookmarksDbHelper bookmarksDb;
    private final Set<String> blockedDomains = new HashSet<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private long startTime = 0;
    private long pausedTime = 0;
    private final Handler timerHandler = new Handler();
    private Runnable timerRunnable;
    private boolean isEditingUrl = false;

    private static final Pattern URL_PATTERN = Pattern.compile(
            "^(https?://)?" +
            "([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}" +
            "(/[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=]*)?$"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bookmarksDb = new BookmarksDbHelper(this);

        webView = findViewById(R.id.webview);
        urlBar = findViewById(R.id.url_bar);
        timerView = findViewById(R.id.timer);
        progressBar = findViewById(R.id.progress_bar);
        TextView btnBack = findViewById(R.id.btn_back);
        TextView btnForward = findViewById(R.id.btn_forward);

        setupWebView();
        loadBlocklistsInBackground();

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setProgress(newProgress);
                    progressBar.setVisibility(View.VISIBLE);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (!isEditingUrl) {
                    urlBar.setText(url);
                    updateTimerVisibility(false);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                String safeUrl = enforceSafeSearch(url);
                if (!safeUrl.equals(url)) {
                    view.loadUrl(safeUrl);
                    return true;
                }
                return false;
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

        webView.loadUrl(enforceSafeSearch("https://www.google.com"));
        urlBar.setText("https://www.google.com");

        // Handle focus changes (Fade timer out, URL bar goes full width)
        urlBar.setOnFocusChangeListener((v, hasFocus) -> {
            isEditingUrl = hasFocus;
            updateTimerVisibility(hasFocus);
        });

        urlBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                updateTimerVisibility(urlBar.hasFocus());
            }
        });

        btnBack.setOnClickListener(v -> { if (webView.canGoBack()) webView.goBack(); });
        btnForward.setOnClickListener(v -> { if (webView.canGoForward()) webView.goForward(); });
        btnBack.setOnLongClickListener(v -> { showBookmarkPopup(); return true; });
        btnForward.setOnLongClickListener(v -> { showDownloadListPopup(); return true; });

        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                String input = urlBar.getText().toString().trim();
                if (!input.isEmpty()) {
                    webView.loadUrl(formatUrl(input));
                }
                urlBar.clearFocus();
                return true;
            }
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
                String timeStr = (hours > 0) ? String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60)
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

    // Controls the visibility animation and structural widths of elements without crashing
    // Fixes the blank space issue, makes timer a clean overlay, and auto-selects all URL text
    private void updateTimerVisibility(boolean urlBarHasFocus) {
        android.view.ViewGroup.LayoutParams urlParams = urlBar.getLayoutParams();
        if (urlParams == null) return;

        urlParams.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        urlBar.setLayoutParams(urlParams);

        if (urlBarHasFocus) {
            // FIX: Remove right padding entirely so text can safely expand across the full width
            urlBar.setPadding(urlBar.getPaddingLeft(), urlBar.getPaddingTop(), 20, urlBar.getPaddingBottom());

            if (timerView.getVisibility() != View.GONE) {
                timerView.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                    timerView.setVisibility(View.GONE);
                }).start();
            }
        } else {
            // FIX: Restore comfortable padding (e.g., 80dp converted to pixels) to clear the timer overlay when not editing
            int rightPaddingInPx = (int) dstPx(44); 
            urlBar.setPadding(urlBar.getPaddingLeft(), urlBar.getPaddingTop(), rightPaddingInPx, urlBar.getPaddingBottom());

            if (timerView.getVisibility() != View.VISIBLE) {
                timerView.setAlpha(0f);
                timerView.setVisibility(View.VISIBLE);
                timerView.animate().alpha(1f).setDuration(200).start();
            }
        }
    }

    private String formatUrl(String input) {
        input = input.trim();
        if (input.isEmpty()) return "https://www.google.com";
        if (URL_PATTERN.matcher(input).matches()) {
            if (!input.startsWith("http://") && !input.startsWith("https://"))
                input = "https://" + input;
            return enforceSafeSearch(input);
        } else {
            return "https://www.google.com/search?q=" + Uri.encode(input) + "&safe=active";
        }
    }

    private void showBookmarkPopup() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        
        // Added distinct bottom padding inside the popup container
        container.setPadding(24, 24, 24, 40); 

        // Detect current theme mode for color adaptations
        boolean isDarkMode = (getResources().getConfiguration().uiMode & 
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int titleTextColor = isDarkMode ? Color.WHITE : Color.BLACK;

        // Title bar using a RelativeLayout so text stays center-balanced while buttons float right
        android.widget.RelativeLayout titleBar = new android.widget.RelativeLayout(this);
        titleBar.setPadding(16, 16, 16, 32); 

        TextView title = new TextView(this);
        title.setText("Bookmarks");
        title.setTextSize(20);
        title.setTextColor(titleTextColor); // Theme adaptive color fix
        title.setGravity(Gravity.CENTER);
        
        android.widget.RelativeLayout.LayoutParams titleParams = new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT, android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
        title.setLayoutParams(titleParams);
        titleBar.addView(title);

        // Right-aligned button stack layout container
        LinearLayout actionButtonsContainer = new LinearLayout(this);
        actionButtonsContainer.setOrientation(LinearLayout.HORIZONTAL);
        actionButtonsContainer.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.RelativeLayout.LayoutParams actionParams = new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT, android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
        actionParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT);
        actionParams.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
        actionButtonsContainer.setLayoutParams(actionParams);

        ImageView addBtn = new ImageView(this);
        addBtn.setImageResource(android.R.drawable.ic_menu_add);
        addBtn.setPadding(12, 12, 12, 12);
        addBtn.setClickable(true);
        if (isDarkMode) addBtn.setColorFilter(Color.WHITE);
        
        ImageView deleteBtn = new ImageView(this);
        deleteBtn.setImageResource(android.R.drawable.ic_menu_delete);
        deleteBtn.setPadding(12, 12, 12, 12);
        deleteBtn.setClickable(true);
        if (isDarkMode) deleteBtn.setColorFilter(Color.WHITE);
        
        actionButtonsContainer.addView(addBtn);
        actionButtonsContainer.addView(deleteBtn);
        titleBar.addView(actionButtonsContainer);
        
        container.addView(titleBar);

        // Scrollable list
        ScrollView scroll = new ScrollView(this);
        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        
        List<BookmarksDbHelper.Bookmark> bookmarks = bookmarksDb.getAllBookmarks();
        
        // Track the selected bookmark IDs for deletion
        Set<Long> selectedIdsToDelete = new HashSet<>();
        
        // Base measurement targeting font scale harmony (~20dp bounds for the selection icon)
        int iconSize = (int) dstPx(20);

        // Reusable dialog instance reference
        final AlertDialog[] dialogHolder = new AlertDialog[1];

        for (BookmarksDbHelper.Bookmark bm : bookmarks) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(8, 12, 8, 12);

            // Minimalist custom selection indicator
            ImageView selectIcon = new ImageView(this);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            selectIcon.setLayoutParams(iconParams);
            selectIcon.setImageResource(android.R.drawable.checkbox_off_background); // Minimal unselected state
            selectIcon.setColorFilter(Color.GRAY); // Subdued appearance

            // Toggle selection logic on icon click
            selectIcon.setOnClickListener(v -> {
                if (selectedIdsToDelete.contains(bm.id)) {
                    selectedIdsToDelete.remove(bm.id);
                    selectIcon.setImageResource(android.R.drawable.checkbox_off_background);
                    selectIcon.setColorFilter(Color.GRAY);
                } else {
                    selectedIdsToDelete.add(bm.id);
                    selectIcon.setImageResource(android.R.drawable.checkbox_on_background);
                    selectIcon.setColorFilter(ContextCompat.getColor(this, R.color.timer_red)); // Highlight when chosen
                }
            });

            TextView tv = new TextView(this);
            tv.setText(bm.title); 
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setTextSize(16);
            tv.setPadding(24, 0, 16, 0); // Comfortable breathing room next to the icon
            tv.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            // Pressing text row changes URL AND terminates popup view immediately
            tv.setOnClickListener(v -> {
                webView.loadUrl(bm.url);
                urlBar.clearFocus();
                if (dialogHolder[0] != null) {
                    dialogHolder[0].dismiss();
                }
            });

            row.addView(selectIcon);
            row.addView(tv);
            listContainer.addView(row);
        }
        scroll.addView(listContainer);
        container.addView(scroll);

        // Rounded Corner Background Drawable for Popup
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(getPopupCornerRadiusFixer());
        
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.windowBackground, typedValue, true);
        int currentWindowBg = (typedValue.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT && 
                               typedValue.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) ? typedValue.data : Color.WHITE;
        
        shape.setColor(currentWindowBg);
        container.setBackground(shape);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(container)
                .create();
        
        // FIX: Erases the default system dialog background framing layer
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialog.setCanceledOnTouchOutside(true);
        dialogHolder[0] = dialog;

        addBtn.setOnClickListener(v -> {
            String titleStr = webView.getTitle();
            String url = webView.getUrl();
            if (url != null && !url.isEmpty()) {
                boolean exists = false;
                for (BookmarksDbHelper.Bookmark bm : bookmarks) {
                    if (url.equalsIgnoreCase(bm.url)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    bookmarksDb.addBookmark(titleStr != null ? titleStr : url, url);
                    Toast.makeText(this, "Bookmark added", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    showBookmarkPopup(); 
                } else {
                    Toast.makeText(this, "Already bookmarked!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        deleteBtn.setOnClickListener(v -> {
            if (!selectedIdsToDelete.isEmpty()) {
                for (Long id : selectedIdsToDelete) {
                    bookmarksDb.deleteBookmark(id);
                }
                Toast.makeText(this, "Deleted selected", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                showBookmarkPopup();
            } else {
                Toast.makeText(this, "Tap the minimal icons to select items for deletion", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void showDownloadListPopup() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        
        // Added distinct bottom padding inside the popup container
        container.setPadding(24, 24, 24, 40); 

        boolean isDarkMode = (getResources().getConfiguration().uiMode & 
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int titleTextColor = isDarkMode ? Color.WHITE : Color.BLACK;

        // Centered title layout across total layout width
        TextView title = new TextView(this);
        title.setText("Downloads");
        title.setTextSize(20);
        title.setTextColor(titleTextColor); // Theme adaptive color fix
        title.setGravity(Gravity.CENTER);
        title.setPadding(16, 16, 16, 32);
        title.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        container.addView(title);

        ScrollView scroll = new ScrollView(this);
        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);

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
                    listContainer.addView(tv);
                } while (cursor.moveToNext());
            } else {
                TextView empty = new TextView(this);
                empty.setText("No downloads yet");
                empty.setPadding(16, 16, 16, 16);
                listContainer.addView(empty);
            }
            cursor.close();
        } else {
            TextView empty = new TextView(this);
            empty.setText("Download manager unavailable");
            listContainer.addView(empty);
        }
        scroll.addView(listContainer);
        container.addView(scroll);

        // Rounded corners for download menu
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(getPopupCornerRadiusFixer());
        
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.windowBackground, typedValue, true);
        int currentWindowBg = (typedValue.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT && 
                               typedValue.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) ? typedValue.data : Color.WHITE;
        
        shape.setColor(currentWindowBg);
        container.setBackground(shape);


        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(container)
                .create();

        // FIX: Erases the default system dialog background framing layer
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private float _cachedDp = -1f;
    private float dstDensity() {
        if (_cachedDp < 0) {
            _cachedDp = getResources().getDisplayMetrics().density;
        }
        return _cachedDp;
    }
    private float dstPx(int dp) { return dp * dstDensity(); }
    
    // Fixed method name syntax error
    private float getPopupCornerRadiusFixer() { 
        return dstPx(16); 
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
                    if (!query.contains("safe=")) {
                        String newQuery = query.isEmpty() ? "safe=active" : query + "&safe=active";
                        return uri.buildUpon().encodedQuery(newQuery).build().toString();
                    } else if (!query.contains("safe=active") && !query.contains("safe=moderate")) {
                        String newQuery = query.replaceAll("safe=[^&]*", "safe=active");
                        return uri.buildUpon().encodedQuery(newQuery).build().toString();
                    }
                }
            }
        } catch (Exception ignored) {}
        return url;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

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
    }

    private boolean isAdBlocked(String url) {
        try {
            String host = Uri.parse(url).getHost();
            if (host == null) return false;
            synchronized (blockedDomains) {
                String testHost = host;
                while (testHost != null && !testHost.isEmpty()) {
                    if (blockedDomains.contains(testHost)) return true;
                    int dot = testHost.indexOf('.');
                    if (dot == -1) break;
                    testHost = testHost.substring(dot + 1);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return false;
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
                "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/nsfw.txt",
                "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/social.txt",
                "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/gambling.mini.txt",
                "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/urlshortener.txt",
                "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/hoster.txt",
                "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/nosafesearch.txt",
                "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/tif.medium.txt",
                "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/domains/pro.txt"
            };
            for (String listUrl : listUrls) {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(listUrl).openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String domain = extractDomain(line);
                            if (domain != null && !domain.isEmpty()) {
                                synchronized (blockedDomains) { blockedDomains.add(domain); }
                            }
                        }
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
        });
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

    @Override
    protected void onPause() {
        super.onPause();
        pausedTime += (SystemClock.elapsedRealtime() - startTime);
        startTime = 0;
        timerHandler.removeCallbacks(timerRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startTime = SystemClock.elapsedRealtime();
        timerHandler.post(timerRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        timerHandler.removeCallbacks(timerRunnable);
        bookmarksDb.close();
    }
}