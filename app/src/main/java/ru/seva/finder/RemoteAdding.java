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

    @Override
    protected void onHandleIntent(Intent intent) {
        String phone_number = intent.getStringExtra("phone_number");

        //подрубаемся к базе
        baseConnect = new dBase(this);
        db = baseConnect.getWritableDatabase();

        //проверка на вхождение
        Cursor cursor_check = db.query(dBase.PHONES_TABLE_IN,
                new String[] {dBase.PHONES_COL},
                dBase.PHONES_COL + "=?",
                new String[] {phone_number},
                null, null, null);

        if (!cursor_check.moveToFirst()) {
            //номера в базе ещё нет
            ContentValues cv = new ContentValues();
            cv.put(dBase.PHONES_COL, phone_number);
            db.insert(dBase.PHONES_TABLE_IN, null, cv);
        }

        cursor_check.close();
        db.close();
    }
}