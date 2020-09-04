package ru.seva.finder;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class GpsCoordsReceived extends IntentService {
    public GpsCoordsReceived() {
        super("GpsCoordsReceived");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Double lat=0d, lon=0d, altitude = null;
        Float speed = null, direction = null;
        Integer acc = null;

        String message = intent.getStringExtra("message");
        String phone = intent.getStringExtra("phone");
        Pattern lat_lon = Pattern.compile("^lat:(-?\\d+\\.\\d+) lon:(-?\\d+\\.\\d+)");
        Matcher m_lat_lon = lat_lon.matcher(message);
        if (m_lat_lon.find()) {
            lat = Double.valueOf(m_lat_lon.group(1));  //will be initialized, regexp checked in the receiver
            lon = Double.valueOf(m_lat_lon.group(2));
        }

        Pattern alt = Pattern.compile("alt:(\\d+\\.?\\d*)");
        Matcher m_alt = alt.matcher(message);
        if (m_alt.find()) {
            altitude = Double.valueOf(m_alt.group(1));
        }

        Pattern spd = Pattern.compile("vel:(\\d+\\.?\\d*)");
        Matcher m_spd = spd.matcher(message);
        if (m_spd.find()) {
            speed = Float.valueOf(m_spd.group(1));
        }

        Pattern dir = Pattern.compile("az:(\\d+\\.?\\d*)");
        Matcher m_dir = dir.matcher(message);
        if (m_dir.find()) {
            direction = Float.valueOf(m_dir.group(1));
        }

        Pattern ac = Pattern.compile("acc:(\\d+)");
        Matcher m_acc = ac.matcher(message);
        if (m_acc.find()) {
            acc = Integer.valueOf(m_acc.group(1));
        }

        Pattern bat = Pattern.compile("bat:(\\d+)%");
        Matcher bat_matcher = bat.matcher(message);
        String bat_value = null;
        if (bat_matcher.find()) {
            bat_value = bat_matcher.group(1);
        }

        Pattern time = Pattern.compile("ts:(\\d+)");
        Matcher time_matcher = time.matcher(message);
        long time_unix_millis;
        if (time_matcher.find()) {
            time_unix_millis = Long.valueOf(time_matcher.group(1));
        } else {
            time_unix_millis = System.currentTimeMillis();
        }

        dBase baseConnect = new dBase(getApplicationContext());
        SQLiteDatabase db = baseConnect.getWritableDatabase();

        DateFormat df = new SimpleDateFormat("MMM d, HH:mm:ss, yyyy");
        String date = df.format(new Date(time_unix_millis));
        MainActivity.write_to_hist(db, phone, lat, lon, acc, date, bat_value, altitude, speed, direction);
        String name;
        //get phone name for notification, if it exists
        Cursor name_curs = db.query(dBase.PHONES_TABLE_OUT, new String[] {dBase.NAME_COL},
                "phone = ?", new String[] {phone},
                null, null, null);
        name = (name_curs.moveToFirst()) ? (name_curs.getString(name_curs.getColumnIndex(dBase.NAME_COL))) : (phone);
        name_curs.close();
        db.close();
        SharedPreferences sPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (MainActivity.activityRunning && sPref.getBoolean("auto_map", false)) {  //run map only in case of opened app and setting this
            Intent start_map = new Intent(getApplicationContext(), MapsActivity.class);
            start_map.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            start_map.putExtra("lat", lat);
            start_map.putExtra("lon", lon);
            start_map.putExtra("zoom", 15d);
            if (acc != null) {
                start_map.putExtra("accuracy", String.valueOf(acc) + getString(R.string.meters));
            }
            start_map.setAction("point");
            startActivity(start_map);
        } else {
            Intent intentRes = new Intent(getApplicationContext(), HistoryActivity.class);
            PendingIntent pendIntent = PendingIntent.getActivity(getApplicationContext(), 0, intentRes, PendingIntent.FLAG_UPDATE_CURRENT);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), MainActivity.COMMON_NOTIF_CHANNEL);
            builder.setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(getString(R.string.message_with_coord))
                    .setContentText(getString(R.string.coords_received, name))
                    .setAutoCancel(true)
                    .setContentIntent(pendIntent);
            Notification notification = builder.build();
            NotificationManager nManage = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            int id = sPref.getInt("notification_id", 2);
            nManage.notify(id, notification);
            sPref.edit().putInt("notification_id", id+1).commit();  //this is new thread (intent service)
        }
    }
}
