package com.browser.zen;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class BookmarksDbHelper extends SQLiteOpenHelper {
    private static final int VERSION = 1;
    private static final String DB_NAME = "bookmarks.db";
    private static final String TABLE = "bookmarks";
    private static final String COL_ID = "_id";
    private static final String COL_TITLE = "title";
    private static final String COL_URL = "url";

    public BookmarksDbHelper(Context context) {
        super(context, DB_NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_TITLE + " TEXT, " +
                COL_URL + " TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    public void addBookmark(String title, String url) {
        ContentValues values = new ContentValues();
        values.put(COL_TITLE, title);
        values.put(COL_URL, url);
        getWritableDatabase().insert(TABLE, null, values);
    }

    public List<Bookmark> getAllBookmarks() {
        List<Bookmark> list = new ArrayList<>();
        Cursor c = getReadableDatabase().query(TABLE, null, null, null, null, null, null);
        while (c.moveToNext()) {
            long id = c.getLong(c.getColumnIndex(COL_ID));
            String title = c.getString(c.getColumnIndex(COL_TITLE));
            String url = c.getString(c.getColumnIndex(COL_URL));
            list.add(new Bookmark(id, title, url));
        }
        c.close();
        return list;
    }

    public void deleteBookmark(long id) {
        getWritableDatabase().delete(TABLE, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public void deleteAllBookmarks() {
        getWritableDatabase().delete(TABLE, null, null);
    }

    public static class Bookmark {
        public long id;
        public String title;
        public String url;
        public Bookmark(long id, String title, String url) {
            this.id = id;
            this.title = title;
            this.url = url;
        }
    }
}
