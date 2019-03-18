package ru.seva.finder;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class TrackReceiveService extends IntentService {

    dBase baseConnect;
    SQLiteDatabase db;

    public TrackReceiveService() {
        super("TrackReceiveService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        //подрубаемся к базе
        baseConnect = new dBase(this);
        db = baseConnect.getWritableDatabase();

        // выбор iD  для трека (дефолт/старый/инкремент) в зависимотсти от времени последних данных
        Cursor query = db.rawQuery("SELECT track_id, date FROM tracking_table WHERE _id = (SELECT MAX(_id) FROM tracking_table)", null);

        int track_id = 0;  // дефолтное, если в базе ещё нет треков
        if (query.moveToFirst()) {
            track_id = query.getInt(query.getColumnIndex("track_id"));
            String old_date = query.getString(query.getColumnIndex("date"));

            Date date, curr_date;
            curr_date = new Date();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
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

        String phone_number = intent.getStringExtra("phone_number");
        String message = intent.getStringExtra("message");

        Pattern tracking_pat = Pattern.compile("(\\d+\\.\\d+);(\\d+\\.\\d+);(\\d+\\.\\d+);(\\d\\d:\\d\\d)\n");
        Matcher m = tracking_pat.matcher(message);

        while (m.find()) {  //парсинг SMSки с даными
            writeToTrackTable(phone_number, Double.valueOf(m.group(1)), Double.valueOf(m.group(2)),
                    Float.valueOf(m.group(3)), m.group(4), track_id);
        }

        db.close();
    }

    public void writeToTrackTable(String phone, Double lat, Double lon, Float speed, String time, int track_id) {

        ContentValues cv = new ContentValues();
        cv.put("phone", phone);
        cv.put("lat", lat);
        cv.put("lon", lon);
        cv.put("speed", speed);
        cv.put("track_id", track_id);

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String date = df.format(Calendar.getInstance().getTime());

        cv.put("date", String.format("%s %s", date, time));  //стыковка времени из двух частей - дата системы+время точки из SMS
        db.insert("tracking_table", null, cv);
    }
}

