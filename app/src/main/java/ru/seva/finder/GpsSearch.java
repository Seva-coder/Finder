package ru.seva.finder;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
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
    private static final String OSM_URL = "https://osm.org/go/";
    private static final int OSM_ZOOM = 15;
    private static final char intToBase64[] = {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
            'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '_', '~'
    };

    private Handler h;  //stopper, in main thread - it is enough fast to not freeze app
    private final StringBuilder sms_answer = new StringBuilder("");
    private final ArrayList<String> phones = new ArrayList<>();

    private Location lastLocation;  //used in case of working GPS but bad accuracy
    private boolean lastTrue = false;
    private boolean no_accurate_coords = true;  //to stop adding gps data to answer after accuracy is good
    //there was a problem on andr. 4 emulator, were after "removeUpdates" method new "accurate" location cause
    // doubling sms text (2 location was received with pause 10 ms, "removeUpdates" may be too slow)

    private Notification notification;  //declared here to recreate it after stopping service (stopping foreground service kills notif.)
    private int id;

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
            if (no_accurate_coords && location.hasAccuracy() &&
                    (location.getAccuracy() < Float.valueOf(sPref.getString(GPS_ACCURACY, GPS_ACCURACY_DEFAULT)))) {
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
                sms_answer.append("\n");

                sms_answer.append("ts:");
                sms_answer.append(location.getTime());
                sms_answer.append("\n");

                sms_answer.append(gen_short_osm_url(location.getLatitude(), location.getLongitude(), OSM_ZOOM));
                sms_answer.append("\n");
                no_accurate_coords = false;
                start_send();
            } else {
                lastTrue = true;  //coords are ready but not enough precise, send them
                lastLocation = location;
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
            notification = builder.build();  //will be used also at service stop
            id = sPref.getInt("notification_id", 2);
            startForeground(id, notification);
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
                sms_answer.append("gps not enabled\n");
                start_send();
            }

        } else {
            if (phones.size() == 0) {  //if number not trusted, and list was empty - stop
                h.removeCallbacks(stopper);
                locMan.removeUpdates(locListen);
                cursor_check.close();
                db.close();
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
                sms_answer.append(String.format(Locale.US, "%.8f",lastLocation.getLatitude()));
                sms_answer.append(" lon:");
                sms_answer.append(String.format(Locale.US, "%.8f", lastLocation.getLongitude()));
                sms_answer.append(" vel:");
                sms_answer.append(String.format(Locale.US, "%.2f", lastLocation.getSpeed() * 3.6f));  //default by function = 0 if not available
                sms_answer.append(" km/h");
                sms_answer.append(" acc:");
                sms_answer.append(String.format(Locale.US, "%.0f", lastLocation.getAccuracy()));
                sms_answer.append("\n");

                sms_answer.append("ts:");
                sms_answer.append(lastLocation.getTime());
                sms_answer.append("\n");

                sms_answer.append(gen_short_osm_url(lastLocation.getLatitude(), lastLocation.getLongitude(), OSM_ZOOM));
                sms_answer.append("\n");
            } else {
                sms_answer.append("unable get location\n");
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
            sms_answer.append("bat:");
            sms_answer.append(batLevel);
            sms_answer.append("%");

            SmsManager sManager = SmsManager.getDefault();
            ArrayList<String> parts = sManager.divideMessage(sms_answer.toString());
            for (String number : phones) {
                sManager.sendMultipartTextMessage(number, null, parts, null,null);
            }
        }
        stopForeground(true);
        NotificationManager nManage = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nManage.notify(id, notification);  //now notification stay after service stop
        stopSelf();
    }

    private String gen_short_osm_url(double latitude, double longitude, int zoom) {
        long lat = (long) (((latitude + 90d)/180d)*(1L << 32));
        long lon = (long) (((longitude + 180d)/360d)*(1L << 32));
        long code = interleaveBits(lon, lat);
        String str = "";
        // add eight to the zoom level, which approximates an accuracy of one pixel in a tile.
        for (int i = 0; i < Math.ceil((zoom + 8) / 3d); i++) {
            str += intToBase64[(int) ((code >> (58 - 6 * i)) & 0x3f)];
        }
        // append characters onto the end of the string to represent
        // partial zoom levels (characters themselves have a granularity of 3 zoom levels).
        for (int j = 0; j < (zoom + 8) % 3; j++) {
            str += '-';
        }
        return OSM_URL + str + "?m";
    }

    //interleaves the bits of two 32-bit numbers. the result is known as a Morton code.
    private static long interleaveBits(long x, long y) {
        long c = 0;
        for (byte b = 31; b >= 0; b--) {
            c = (c << 1) | ((x >> b) & 1);
            c = (c << 1) | ((y >> b) & 1);
        }
        return c;
    }




    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
