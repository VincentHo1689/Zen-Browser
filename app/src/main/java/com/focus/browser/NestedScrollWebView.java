package com.zen.browser;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.webkit.WebView;
import androidx.core.view.NestedScrollingChild2;
import androidx.core.view.NestedScrollingChildHelper;
import androidx.core.view.ViewCompat;

public class NestedScrollWebView extends WebView implements NestedScrollingChild2 {

    private NestedScrollingChildHelper childHelper;
    private final int[] scrollOffset = new int[2];
    private final int[] scrollConsumed = new int[2];
    private int nestedYOffset;

    public NestedScrollWebView(Context context) {
        super(context);
        init();
    }

    public NestedScrollWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public NestedScrollWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        childHelper = new NestedScrollingChildHelper(this);
        setNestedScrollingEnabled(true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        MotionEvent trackedEvent = MotionEvent.obtain(event);
        final int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            nestedYOffset = 0;
        }

        trackedEvent.offsetLocation(0, nestedYOffset);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH);
                break;
            case MotionEvent.ACTION_MOVE:
                int deltaY = 0;
                if (dispatchNestedPreScroll(0, deltaY, scrollConsumed, scrollOffset, ViewCompat.TYPE_TOUCH)) {
                    trackedEvent.offsetLocation(0, -scrollOffset[1]);
                    nestedYOffset += scrollOffset[1];
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                stopNestedScroll(ViewCompat.TYPE_TOUCH);
                break;
        }

        boolean returnValue = super.onTouchEvent(trackedEvent);
        trackedEvent.recycle();
        return returnValue;
    }

    @Override public boolean startNestedScroll(int axes, int type) { return childHelper.startNestedScroll(axes, type); }
    @Override public void stopNestedScroll(int type) { childHelper.stopNestedScroll(type); }
    @Override public boolean hasNestedScrollingParent(int type) { return childHelper.hasNestedScrollingParent(type); }
    @Override public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int[] offsetInWindow, int type) { return childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type); }
    @Override public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow, int type) { return childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type); }
}