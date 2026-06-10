package com.zen.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DialogHelper {

    private static float dstPx(Context context, int dp) {
        return dp * context.getResources().getDisplayMetrics().density;
    }

    public static void showBookmarkPopup(Activity activity, BookmarksDbHelper bookmarksDb, WebView webView) {
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(24, 24, 24, 40);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        boolean isDarkMode = (activity.getResources().getConfiguration().uiMode &
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int titleTextColor = isDarkMode ? Color.parseColor("#dddddd") : Color.parseColor("#211f27");

        // Title bar with add/delete
        android.widget.RelativeLayout titleBar = new android.widget.RelativeLayout(activity);
        titleBar.setPadding(16, 16, 16, 32);

        TextView title = new TextView(activity);
        title.setText("Bookmarks");
        title.setTextSize(20);
        title.setTextColor(titleTextColor);
        title.setGravity(Gravity.CENTER);
        android.widget.RelativeLayout.LayoutParams titleParams = new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT, android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
        title.setLayoutParams(titleParams);
        titleBar.addView(title);

        LinearLayout actionButtons = new LinearLayout(activity);
        actionButtons.setOrientation(LinearLayout.HORIZONTAL);
        actionButtons.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.RelativeLayout.LayoutParams actionParams = new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT, android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
        actionParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT);
        actionParams.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
        actionButtons.setLayoutParams(actionParams);

        ImageView addBtn = new ImageView(activity);
        addBtn.setImageResource(R.drawable.ic_minimalist_add);
        addBtn.setPadding(12, 2, 12, 2);
        if (isDarkMode) addBtn.setColorFilter(Color.parseColor("#dddddd"));
        ImageView deleteBtn = new ImageView(activity);
        deleteBtn.setImageResource(R.drawable.ic_minimalist_delete);
        deleteBtn.setPadding(12, 2, 12, 2);
        if (isDarkMode) deleteBtn.setColorFilter(Color.parseColor("#dddddd"));
        actionButtons.addView(addBtn);
        actionButtons.addView(deleteBtn);
        titleBar.addView(actionButtons);
        container.addView(titleBar);

        // Scrollable list
        ScrollView scroll = new ScrollView(activity);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);   
        scroll.setLayoutParams(scrollParams);
        LinearLayout listContainer = new LinearLayout(activity);
        listContainer.setOrientation(LinearLayout.VERTICAL);

        List<BookmarksDbHelper.Bookmark> bookmarks = bookmarksDb.getAllBookmarks();
        Set<Long> selectedIds = new HashSet<>();
        int iconSize = (int) dstPx(activity, 20);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(container)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.setOnShowListener(dialogInterface -> {
            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
            int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
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
        dialog.setCanceledOnTouchOutside(true);

        for (BookmarksDbHelper.Bookmark bm : bookmarks) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(8, 12, 8, 12);

            ImageView selectIcon = new ImageView(activity);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconParams.gravity = Gravity.CENTER_VERTICAL;  
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

            TextView tv = new TextView(activity);
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
        shape.setCornerRadius(dstPx(activity, 16));
        android.util.TypedValue typedValue = new android.util.TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.windowBackground, typedValue, true);
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
                    Toast.makeText(activity, "Bookmark added", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    showBookmarkPopup(activity, bookmarksDb, webView);
                } else Toast.makeText(activity, "Already bookmarked!", Toast.LENGTH_SHORT).show();
            }
        });
        deleteBtn.setOnClickListener(v -> {
            if (!selectedIds.isEmpty()) {
                for (Long id : selectedIds) bookmarksDb.deleteBookmark(id);
                Toast.makeText(activity, "Deleted selected", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                showBookmarkPopup(activity, bookmarksDb, webView);
            } else Toast.makeText(activity, "Tap icons to select", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    public static void showDownloadListPopup(Activity activity) {
        LinearLayout container = new LinearLayout(activity);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(24, 24, 24, 40);

        boolean isDark = (activity.getResources().getConfiguration().uiMode &
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int textColor = isDark ? Color.parseColor("#dddddd") : Color.parseColor("#211f27");

        TextView title = new TextView(activity);
        title.setText("Downloads");
        title.setTextSize(20);
        title.setTextColor(textColor);
        title.setGravity(Gravity.CENTER);
        title.setPadding(16, 16, 16, 32);
        container.addView(title);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scroll.setLayoutParams(scrollParams);
        scroll.setFillViewport(true);

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);

        DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm != null) {
            Cursor cursor = dm.query(new DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL));
            if (cursor.moveToFirst()) {
                int titleIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE);
                int uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                do {
                    String titleStr = cursor.getString(titleIdx);
                    String uri = cursor.getString(uriIdx);
                    TextView tv = new TextView(activity);
                    tv.setText(titleStr != null ? titleStr : uri);
                    tv.setSingleLine(true);
                    tv.setEllipsize(TextUtils.TruncateAt.END);
                    tv.setPadding(16, 12, 16, 12);
                    tv.setOnClickListener(v -> {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(Uri.parse(uri), "*/*");
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            activity.startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(activity, "Cannot open file", Toast.LENGTH_SHORT).show();
                        }
                    });
                final long downloadId = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_ID));
                final String localUriStr = uri;

                tv.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(activity)
                            .setTitle("Delete download?")
                            .setMessage("Remove this download and its file?")
                            .setPositiveButton("Delete", (dialog, which) -> {
                                if (dm != null) {
                                    dm.remove(downloadId);
                                }
                                try {
                                    activity.getContentResolver().delete(Uri.parse(localUriStr), null, null);
                                } catch (Exception ignored) {}
                                Toast.makeText(activity, "Deleted", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                                showDownloadListPopup(activity);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    return true;
                });
                    list.addView(tv);
                    
                } while (cursor.moveToNext());
            } else {
                TextView empty = new TextView(activity);
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
        shape.setCornerRadius(dstPx(activity, 16));
        android.util.TypedValue typedValue = new android.util.TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.windowBackground, typedValue, true);
        int bg = (typedValue.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT &&
                typedValue.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) ? typedValue.data : Color.parseColor("#f8f7f4");
        shape.setColor(bg);
        container.setBackground(shape);

        AlertDialog dialog = new AlertDialog.Builder(activity)
        .setView(container)
        .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.setCanceledOnTouchOutside(true);

        dialog.setOnShowListener(dialogInterface -> {
            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
            int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
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
}
