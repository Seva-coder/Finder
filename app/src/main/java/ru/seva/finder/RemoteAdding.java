package ru.seva.finder;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;


public class RemoteAdding extends IntentService {

    public RemoteAdding() {
        super("RemoteAdding");
    }

    dBase baseConnect;
    SQLiteDatabase db;
    public static final String PHONES_TABLE = "phones";
    public static final String PHONES_COL = "phone";

    @Override
    protected void onHandleIntent(Intent intent) {
        String phone_number = intent.getStringExtra("phone_number");

        //подрубаемся к базе
        baseConnect = new dBase(this);
        db = baseConnect.getWritableDatabase();

        //проверка на вхождение
        Cursor cursor_check = db.query(PHONES_TABLE,
                new String[] {PHONES_COL},
                PHONES_COL + "=?",
                new String[] {phone_number},
                null, null, null);

        if (!cursor_check.moveToFirst()) {
            //номера в базе ещё нет
            ContentValues cv = new ContentValues();
            cv.put(PHONES_COL, phone_number);
            db.insert(PHONES_TABLE, null, cv);
        }

        cursor_check.close();
        db.close();
    }
}
