package ru.seva.finder;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.AudioManager;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class TrackReceiveService extends IntentService {

    private SQLiteDatabase db;

    public TrackReceiveService() {
        super("TrackReceiveService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        //подрубаемся к базе
        dBase baseConnect = new dBase(this);
        db = baseConnect.getWritableDatabase();

        // выбор iD  для трека (дефолт/старый/инкремент) в зависимотсти от времени последних данных
        Cursor query = db.rawQuery("SELECT track_id, date FROM tracking_table WHERE _id = (SELECT MAX(_id) FROM tracking_table)", null);

        int track_id = 0;  // дефолтное, если в базе ещё нет треков
        if (query.moveToFirst()) {
            track_id = query.getInt(query.getColumnIndex("track_id"));
            String old_date = query.getString(query.getColumnIndex("date"));

            Date date, curr_date;
            curr_date = new Date();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            try {
                date = dateFormat.parse(old_date);
            } catch (ParseException e) {
                //да быть такого не может)
                //хз что делать
                date = curr_date;
            }

            float days_from_last = (curr_date.getTime() - date.getTime())/(1000f * 3600f * 24f);  //число дней от старых данных
            if (days_from_last >= 1.0f) {  //прошло более одного дня с последних данных
                track_id++;
            }
        }
        query.close();

        //создадим уведомление с возможностью открыть трек по клику на уведомление
        Intent intentRes = new Intent(getApplicationContext(), MapsActivity.class);
        intentRes.setAction("track");
        intentRes.putExtra("track_id", track_id);
        PendingIntent pendIntent = PendingIntent.getActivity(getApplicationContext(), 0, intentRes, PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = new Notification.Builder(getApplicationContext())
                .setContentIntent(pendIntent)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.new_track_data))
                .setAutoCancel(true);  //подумать над channel id;
        Notification notification = builder.build();
        NotificationManager nManage = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nManage.notify(1, notification);  // 1st notif - using for tracking

        String phone_number = intent.getStringExtra("phone_number");
        String message = intent.getStringExtra("message");

        Pattern tracking_pat = Pattern.compile("(\\d+\\.\\d+);(\\d+\\.\\d+);(\\d+\\.\\d+);(\\d\\d:\\d\\d)");
        Matcher m = tracking_pat.matcher(message);

        while (m.find()) {  //парсинг SMSки с даными
            writeToTrackTable(phone_number, Double.valueOf(m.group(1)), Double.valueOf(m.group(2)),
                    Float.valueOf(m.group(3)), m.group(4), track_id);
        }

        db.close();

        Intent update_map = new Intent("update_map");
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(update_map);

        //возвращение звука в норм состояние
        SharedPreferences sPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (sPref.getBoolean("disable_tracking_sound", false) && intent.getBooleanExtra("sound_was_normal", true)) {
            try {
                Thread.sleep(200);  //волшебный таймаут для того, чтобы не было звука
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            AudioManager aMan = (AudioManager) getApplication().getSystemService(Context.AUDIO_SERVICE);
            aMan.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        }
    }

    private void writeToTrackTable(String phone, Double lat, Double lon, Float speed, String time, int track_id) {

        ContentValues cv = new ContentValues();
        cv.put("phone", phone);
        cv.put("lat", lat);
        cv.put("lon", lon);
        cv.put("speed", speed);
        cv.put("track_id", track_id);

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String date = df.format(Calendar.getInstance().getTime());

        cv.put("date", String.format("%sT%s:00Z", date, time));  //стыковка времени из двух частей - дата системы+время точки из SMS
        db.insert("tracking_table", null, cv);
    }
}

