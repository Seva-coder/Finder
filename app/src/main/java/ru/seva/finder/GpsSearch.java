package ru.seva.finder;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.telephony.SmsManager;

import java.util.ArrayList;
import java.util.Locale;


public class GpsSearch extends Service {
    private SharedPreferences sPref;
    private LocationManager locMan;
    private LocationHelper locationHelper;

    private static final String GPS_ACCURACY = "gps_accuracy";
    private static final String GPS_ACCURACY_DEFAULT = "12";
    private static final String GPS_TIME = "gps_time";
    private static final String GPS_TIME_DEFAULT = "20";  //in minutes

    private Handler h;  //stopper, in main thread - it is enough fast to not freeze app
    private final StringBuilder sms_answer = new StringBuilder("");
    private final ArrayList<String> phones = new ArrayList<>();

    private String lastLat;
    private String lastLon;  //used in case of working GPS but bad accuracy
    private String lastSpeed;
    private String lastAccuracy;
    private boolean lastTrue = false;
    private boolean sound_was_enabled;

    public GpsSearch() {
    }


    @Override
    public void onCreate() {
        h = new Handler();
        sPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        h.postDelayed(stopper, Integer.valueOf(sPref.getString(GPS_TIME, GPS_TIME_DEFAULT)) * 60000);  //stopping GPS if it is impossible to determine the coordinates for a long time
        locMan = (LocationManager) getApplicationContext().getSystemService(LOCATION_SERVICE);
        locationHelper = new LocationHelper(getApplicationContext());
    }

    private final LocationListener locListen = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (location.hasAccuracy() && (location.getAccuracy() < Float.valueOf(sPref.getString(GPS_ACCURACY, GPS_ACCURACY_DEFAULT)))) {
                locMan.removeUpdates(locListen);  //will be disabled after first accurate coords
                sms_answer.append("lat:");
                sms_answer.append(String.format(Locale.US, "%.8f",location.getLatitude()));
                sms_answer.append(" ");
                sms_answer.append("lon:");
                sms_answer.append(String.format(Locale.US, "%.8f", location.getLongitude()));
                sms_answer.append(" ");
                if (location.hasAltitude()) {
                    sms_answer.append("alt:");
                    sms_answer.append(String.format(Locale.US, "%.0f", location.getAltitude()));
                    sms_answer.append(" m ");
                }
                if (location.hasSpeed()) {
                    sms_answer.append("vel:");
                    sms_answer.append(String.format(Locale.US, "%.2f", location.getSpeed() * 3.6f));
                    sms_answer.append(" km/h ");
                }
                if (location.hasBearing()) {
                    sms_answer.append("az:");
                    sms_answer.append(String.format(Locale.US, "%.0f", location.getBearing()));
                    sms_answer.append(" ");
                }
                sms_answer.append("acc:");
                sms_answer.append(String.format(Locale.US, "%.0f", location.getAccuracy()));
                start_send();
            } else {
                lastTrue = true;  //coords are ready but not enough precise, send them
                lastLat = String.format(Locale.US, "%.8f",location.getLatitude());
                lastLon = String.format(Locale.US, "%.8f", location.getLongitude());
                lastSpeed = String.format(Locale.US, "%.2f", location.getSpeed() * 3.6f);  //default by function = 0 if not available
                lastAccuracy = String.format(Locale.US, "%.0f", location.getAccuracy());  // -//-
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    };


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String phone_number = intent.getStringExtra("phone_number");
        sound_was_enabled = intent.getBooleanExtra("sound_was_normal", true);
        dBase baseConnect = new dBase(this);
        SQLiteDatabase db = baseConnect.getReadableDatabase();

        //is number in trusted?
        Cursor cursor_check = db.query(dBase.PHONES_TABLE_IN,
                new String[] {dBase.PHONES_COL},
                dBase.PHONES_COL + "=?",
                new String[] {phone_number},
                null, null, null);

        if (cursor_check.moveToFirst()) {
            //adding number to list for answer if it in DB, and start GPS (if it not running now)
            if (!phones.contains(phone_number)) {
                phones.add(phone_number);
            }

            Cursor name_curs = db.query(dBase.PHONES_TABLE_IN, new String[] {dBase.NAME_COL},
                    "phone = ?", new String[] {phone_number},
                    null, null, null);
            String name;
            name = (name_curs.moveToFirst()) ? (name_curs.getString(name_curs.getColumnIndex(dBase.NAME_COL))) : (phone_number);
            name_curs.close();

            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), MainActivity.COMMON_NOTIF_CHANNEL)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(getString(R.string.gps_processed))
                    .setContentText(getString(R.string.from, name))
                    .setAutoCancel(true);
            Notification notification = builder.build();
            NotificationManager nManage = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            int id = sPref.getInt("notification_id", 2);
            nManage.notify(id, notification);
            sPref.edit().putInt("notification_id", id+1).apply();

            // Activate location if it's not enabled
            // (only if permission to write secure settings is granted via ADB)
            locationHelper.activateLocation(true);

            //checking permissions on new API and start GPS
            if (Build.VERSION.SDK_INT >= 23 && (getApplicationContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) &&
                    locMan.isProviderEnabled(LocationManager.GPS_PROVIDER) && startId == 1) {
                locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locListen);
            }

            //older API
            if (Build.VERSION.SDK_INT < 23 && locMan.isProviderEnabled(LocationManager.GPS_PROVIDER) && startId == 1) {
                locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locListen);
            }

            //on new API no permission or GPS disabled
            if ((Build.VERSION.SDK_INT >=23 && getApplicationContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) ||
                    !locMan.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                try {
                    Thread.sleep(200);  //timeout for muting
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                sms_answer.append("gps not enabled");
                start_send();
            }

        } else {
            if (phones.size() == 0) {  //if number not added, and list was empty - stop
                h.removeCallbacks(stopper);
                locMan.removeUpdates(locListen);
                cursor_check.close();
                db.close();
                if (sPref.getBoolean("disable_sound", false) && sound_was_enabled) {
                    AudioManager aMan = (AudioManager) getApplication().getSystemService(Context.AUDIO_SERVICE);
                    NotificationManager nManage = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    if ((Build.VERSION.SDK_INT >= 23 && nManage.isNotificationPolicyAccessGranted()) || (Build.VERSION.SDK_INT < 23)) {
                        aMan.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    }
                }
                stopSelf();
            }
        }

        cursor_check.close();
        db.close();

        return START_REDELIVER_INTENT; //answer at least the last number
    }


    private final Runnable stopper = new Runnable() {
        @Override
        public void run() {
            //stopping by timer
            locMan.removeUpdates(locListen);
            if (lastTrue) {  //send at least what we have (if we have)
                sms_answer.append("lat:");
                sms_answer.append(lastLat);
                sms_answer.append(" lon:");
                sms_answer.append(lastLon);
                sms_answer.append(" vel:");
                sms_answer.append(lastSpeed);
                sms_answer.append(" km/h");
                sms_answer.append(" acc:");
                sms_answer.append(lastAccuracy);
            } else {
                sms_answer.append("unable get location");
            }
            start_send();
        }
    };


    @Override
    public void onDestroy() {
        h.removeCallbacks(stopper);

        // Deactivate location if it was not enabled
        // (only if permission to write secure settings is granted via ADB)
        locationHelper.deactivateLocation();
    }


    private void start_send() {   //sending to all who asked
        if ((Build.VERSION.SDK_INT >= 23 &&
                (getApplicationContext().checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED)) ||
                Build.VERSION.SDK_INT < 23) {
            //adding battery data
            IntentFilter bat_filt= new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent battery = getApplicationContext().registerReceiver(null, bat_filt);
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            float batteryPct = level / (float) scale;
            String batLevel = String.valueOf(Math.round(batteryPct*100));
            sms_answer.append(" bat:");
            sms_answer.append(batLevel);
            sms_answer.append("%\n");

            SmsManager sManager = SmsManager.getDefault();
            ArrayList<String> parts = sManager.divideMessage(sms_answer.toString());
            for (String number : phones) {
                sManager.sendMultipartTextMessage(number, null, parts, null,null);
            }
        }

        if (sPref.getBoolean("disable_sound", false) && sound_was_enabled) {
            AudioManager aMan = (AudioManager) getApplication().getSystemService(Context.AUDIO_SERVICE);
            NotificationManager nManage = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if ((Build.VERSION.SDK_INT >= 23 && nManage.isNotificationPolicyAccessGranted()) || (Build.VERSION.SDK_INT < 23)) {
                aMan.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            }
        }
        stopSelf();
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
