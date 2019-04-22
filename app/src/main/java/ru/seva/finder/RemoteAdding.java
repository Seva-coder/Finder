package ru.seva.finder;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.AudioManager;
import android.preference.PreferenceManager;


public class RemoteAdding extends IntentService {

    public RemoteAdding() {
        super("RemoteAdding");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String phone_number = intent.getStringExtra("phone_number");

        dBase baseConnect = new dBase(this);
        SQLiteDatabase db = baseConnect.getWritableDatabase();

        //this check is to avoid repetition of phone numbers in DB
        Cursor cursor_check = db.query(dBase.PHONES_TABLE_IN,
                new String[] {dBase.PHONES_COL},
                dBase.PHONES_COL + "=?",
                new String[] {phone_number},
                null, null, null);

        if (!cursor_check.moveToFirst()) {
            //add number in case of its absence
            ContentValues cv = new ContentValues();
            cv.put(dBase.PHONES_COL, phone_number);
            db.insert(dBase.PHONES_TABLE_IN, null, cv);
        }

        cursor_check.close();
        db.close();

        SharedPreferences sPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Notification.Builder builder = new Notification.Builder(getApplicationContext())
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.remote_adding))
                .setContentText(getString(R.string.was_added, phone_number))
                .setAutoCancel(true);
        Notification notification = builder.build();
        NotificationManager nManage = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        int id = sPref.getInt("notification_id", 2);
        nManage.notify(id, notification);
        sPref.edit().putInt("notification_id", id+1).apply();

        //enable response mode after remote adding
        sPref.edit().putBoolean("answer", true).apply();

        if (sPref.getBoolean("disable_sound", false) && intent.getBooleanExtra("sound_was_normal", true)) {
            try {
                Thread.sleep(200);  //magic timeout for mute
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            AudioManager aMan = (AudioManager) getApplication().getSystemService(Context.AUDIO_SERVICE);
            aMan.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        }
    }
}