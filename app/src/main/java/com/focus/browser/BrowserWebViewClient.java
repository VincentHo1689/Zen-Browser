package com.zen.browser;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.text.TextUtils;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.util.List;

public class BrowserWebViewClient extends WebViewClient {
    private final MainActivity activity;
    private final BlocklistManager blocklistManager;

    public BrowserWebViewClient(MainActivity activity, BlocklistManager blocklistManager) {
        this.activity = activity;
        this.blocklistManager = blocklistManager;
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        activity.onPageStarted(url);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        activity.onPageFinished(url);
    }

    // Modern API 24+ handler
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        return handleUrlLoading(view, request.getUrl().toString());
    }

    // Legacy fallback handler
    @SuppressWarnings("deprecation")
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return handleUrlLoading(view, url);
    }

    /**
     * Unified URL routing logic to handle protocols, safetynet, and oauth safety.
     */
    private boolean handleUrlLoading(WebView view, String url) {
        if (url == null) return false;

        // 1. Handle common device protocols
        if (url.startsWith("tel:") || url.startsWith("mailto:") || 
            url.startsWith("sms:") || url.startsWith("geo:")) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                view.getContext().startActivity(intent);
                return true; 
            } catch (Exception e) {
                return true; 
            }
        }

        // 2. Handle standard web requests
        if (url.startsWith("http://") || url.startsWith("https://")) {
            
            // FIX: Never run Google OAuth URLs through URL modifiers or SafeSearch wrappers.
            // This prevents your code from corrupting query parameters (like changing %20 to +).
            if (url.contains("accounts.google.com")) {
                return false; // Let the WebView load the pristine raw URL string
            }

            String safeUrl = UrlHelper.enforceSafeSearch(url);
            if (!safeUrl.equals(url)) {
                view.loadUrl(safeUrl);
                return true; 
            }
            return false; 
        }

        // 3. FIX: Handle custom app intent redirects (e.g., intent://, market://, grok://)
        // Previously, returning true at the end of this method dropped these entirely.
        try {
            Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            if (intent != null) {
                // Check if target app is installed, otherwise handle fallback URL if available
                if (view.getContext().getPackageManager().resolveActivity(intent, 0) == null) {
                    String fallbackUrl = intent.getStringExtra("browser_fallback_url");
                    if (fallbackUrl != null) {
                        view.loadUrl(fallbackUrl);
                        return true;
                    }
                } else {
                    view.getContext().startActivity(intent);
                    return true;
                }
            }
        } catch (Exception e) {
            // Fallback for raw non-standard protocol strings
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                view.getContext().startActivity(intent);
                return true;
            } catch (Exception ex) {
                // Fail silently if device can't open the scheme
            }
        }
        
        return true;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString().toLowerCase();
        if (UrlHelper.isVideoUrlBlock(url)) {
            view.post(() -> activity.injectZenController(view));
        }

        List<String> blockingLists = blocklistManager.getBlockingLists(request.getUrl().getHost());
        if (!blockingLists.isEmpty()) {
            return generateBlockPage(blockingLists);
        }
        return super.shouldInterceptRequest(view, request);
    }

    private WebResourceResponse generateBlockPage(List<String> blockingLists) {
        boolean isDark = (activity.getResources().getConfiguration().uiMode &
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        String bgColor = isDark ? "#211f27" : "#f8f7f4";
        String textColor = isDark ? "#dddddd" : "#211f27";
        String mutedColor = isDark ? "#888888" : "#999999";
        String names = TextUtils.join(", ", blockingLists);
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
}
