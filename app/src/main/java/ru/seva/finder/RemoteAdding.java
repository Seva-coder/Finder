package ru.seva.finder;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;


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

        SharedPreferences sPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext())
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.remote_adding))
                .setContentText(phone_number + getString(R.string.was_added))
                .setAutoCancel(true);  //подумать над channel id  и ИКОНКОЙ!
        Notification notification = builder.build();
        NotificationManager nManage = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        int id = sPref.getInt("notification_id", 0);
        nManage.notify(id, notification);
        sPref.edit().putInt("notification_id", id+1).apply();
    }
}