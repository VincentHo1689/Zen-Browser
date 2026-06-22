package com.browser.focus;

import android.webkit.WebView;

public class WebViewInjector {

    public static void injectZenController(WebView view) {
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

    public static void injectScrollDetection(WebView view) {
        String js = "(function() {" +
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
        view.evaluateJavascript(js, null);
    }

    public static void injectVisibilityOverride(WebView view) {
        String js = "(function() {" +
                "   Object.defineProperty(document, 'visibilityState', {" +
                "       get: function() { return 'visible'; }" +
                "   });" +
                "   Object.defineProperty(document, 'hidden', {" +
                "       get: function() { return false; }" +
                "   });" +
                "   var event = new Event('visibilitychange');" +
                "   document.dispatchEvent(event);" +
                "})();";
        view.evaluateJavascript(js, null);
    }
}