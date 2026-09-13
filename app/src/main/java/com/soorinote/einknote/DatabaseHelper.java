package com.soorinote.einknote;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "eink_notes.db";
    private static final int DB_VERSION = 1;
    public static final String TABLE_NOTES = "notes";

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_NOTES + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "title TEXT, " +
                "content TEXT, " +
                "updated_at TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NOTES);
        onCreate(db);
    }

    public long insertOrUpdate(long id, String title, String content) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("content", content);
        values.put("updated_at", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()));

        if (id == -1) {
            return db.insert(TABLE_NOTES, null, values);
        } else {
            db.update(TABLE_NOTES, values, "id = ?", new String[]{String.valueOf(id)});
            return id;
        }
    }

    public void deleteNote(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NOTES, "id = ?", new String[]{String.valueOf(id)});
    }

    public List<Note> getAllNotes() {
        List<Note> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT id, title, content, updated_at FROM " + TABLE_NOTES + " ORDER BY id DESC", null);
        if (c.moveToFirst()) {
            do {
                list.add(new Note(c.getLong(0), c.getString(1), c.getString(2), c.getString(3)));
            } while (c.moveToNext());
        }
        c.close();
        return list;
    }
}