package ru.seva.finder;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.SmsManager;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class Tracking extends Service {

    private NotificationCompat.Builder builder;
    private NotificationManager nManager;
    private LocationManager locMan;
    private StringBuilder sms_text = new StringBuilder("");
    private final Handler stop = new Handler();

    private Intent update_intent;  //intent for updating data in TrackStatus

    static boolean tracking_running = false;
    private boolean lastTrue = false;
    private String lastLat;
    private String lastLon;
    private String lastSpeed;
    private String phone;
    private SharedPreferences sPref;
    private Timer timer;
    static int sms_counter = 0;  //"static" for availability from TrackStatus
    private int coords_counter = 0;
    static int sms_number = 10;
    private int sms_buffer_max = 4;
    private int delay = 300;
    private int accuracy = 15;

    public Tracking() {
    }


    private void append_send_coordinates(String lat, String lon, String speed, String date) {
        //creating message
        sms_text.append(lat);
        sms_text.append(";");
        sms_text.append(lon);
        sms_text.append(";");
        sms_text.append(speed);
        sms_text.append(";");
        sms_text.append(date);
        sms_text.append("\n");
        coords_counter++;

        //buffer is full, time to send or stop service
        if (coords_counter == sms_buffer_max) {
            coords_counter = 0;
            if (sms_counter < sms_number) {
                if ((Build.VERSION.SDK_INT >= 23 &&
                        (getApplicationContext().checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED)) ||
                        Build.VERSION.SDK_INT < 23) {
                    SmsManager sManager = SmsManager.getDefault();
                    ArrayList<String> parts = sManager.divideMessage(sms_text.toString());
                    sManager.sendMultipartTextMessage(phone, null, parts, null,null);
                    sms_text = new StringBuilder("");
                    sms_counter++;
                    builder.setContentText(getString(R.string.sms_sent, sms_counter));

                    //update fields in TrackingStatus via intent
                    update_intent.setAction("update_fields");
                    LocalBroadcastManager.getInstance(this).sendBroadcast(update_intent);
                }
            }

            //second check to stop service straight after sending all messages
            if (sms_counter == sms_number) {
                //all SMS are sent, notification will be created in onDestroy
                stopSelf();
            }
        }
    }


    private final LocationListener locListen = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (location.hasAccuracy() && (location.getAccuracy() < accuracy)) {
                stop.removeCallbacks(stopper);
                locMan.removeUpdates(locListen);  //disable GPS listener after first accurate data
                String lat = String.format(Locale.US, "%.8f",location.getLatitude());
                String lon = String.format(Locale.US, "%.8f", location.getLongitude());
                String speed = String.format(Locale.US, "%.1f", location.getSpeed() * 3.6f);
                DateFormat df = new SimpleDateFormat("HH:mm");
                String date = df.format(Calendar.getInstance().getTime());
                append_send_coordinates(lat, lon, speed, date);
            } else {
                lastTrue = true;  //save not accurate data to sending, in case of big GPS work time
                lastLat = String.format(Locale.US, "%.8f",location.getLatitude());
                lastLon = String.format(Locale.US, "%.8f", location.getLongitude());
                lastSpeed = String.format(Locale.US, "%.1f", location.getSpeed() * 3.6f);  //default by function = 0 if not available
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


    class TrackingTask extends TimerTask {
        @Override
        public void run() {
            if (Build.VERSION.SDK_INT >= 23 && (getApplicationContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) &&
                    locMan.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locListen, Looper.getMainLooper());
            }

            //on old API permission get by default, check only provider
            if (Build.VERSION.SDK_INT < 23 && locMan.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locListen, Looper.getMainLooper());
            }
            int gps_timeout_setting = Integer.valueOf(sPref.getString("gps_time", "20")) * 60 * 1000;  //in ms
            int delay_90 = delay * 900; // 900=1000ms*0.9
            int delay_min = (delay_90 < gps_timeout_setting) ? delay_90 : gps_timeout_setting;
            stop.postDelayed(stopper, delay_min);  //stop GPS when 90% of the period has passed OR worktime exceeds time for single request. Will be used only in case when GPS is not available
        }
    }

    //stopping GPS by timer
    private final Runnable stopper = new Runnable() {
        @Override
        public void run() {
            locMan.removeUpdates(locListen);  //when position not getted in 90% time of period
            if (lastTrue) {  //send at least what we have
                DateFormat df = new SimpleDateFormat("HH:mm");
                String date = df.format(Calendar.getInstance().getTime());
                append_send_coordinates(lastLat, lastLon, lastSpeed, date);
                lastTrue =false;
            }
        }
    };

    //stopping all tracking service when time exceed calculated work time in 1.5 times
    private final Runnable full_stopper = new Runnable() {
        @Override
        public void run() {
            locMan.removeUpdates(locListen);
            stopSelf();
        }
    };


    @Override
    public void onCreate() {
        sPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        locMan = (LocationManager) getApplicationContext().getSystemService(LOCATION_SERVICE);
        nManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        update_intent = new Intent(this, TrackStatus.class);  //initialization of intent for updating data in TrackStatus
    }



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        tracking_running = true;
        phone = intent.getStringExtra("phone");
        sms_number = Integer.parseInt(intent.getStringExtra("tracking_sms_max_number"));
        delay = Integer.parseInt(intent.getStringExtra("tracking_delay"));
        sms_buffer_max = Integer.parseInt(intent.getStringExtra("tracking_coord_number"));
        accuracy = Integer.parseInt(intent.getStringExtra("tracking_accuracy"));
        String name = intent.getStringExtra("name");

        timer = new Timer();
        TrackingTask trackTask = new TrackingTask();  //timer for regular getting coords
        timer.scheduleAtFixedRate(trackTask, 0L, 1000L*delay);  //5 minutes default
        stop.postDelayed(full_stopper, sms_number * sms_buffer_max * delay * 1500L);  //stop this service when work time more than 1.5 times then calculated (to save battery) (1500L = 1000ms * 1.5)

        //only one tracking can work, so this code will be called only once
        Intent notifIntent = new Intent(this, TrackStatus.class);
        PendingIntent pendIntent = PendingIntent.getActivity(this, 0, notifIntent, 0);
        builder = new NotificationCompat.Builder(this, MainActivity.TRACKING_NOTIF_CHANNEL);
        builder.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.tracking_on_notify, name))  //"name" gets from intent - this is reason using onStartCommand instead onCreate
                .setContentText(getString(R.string.sms_sent, 0))
                .setContentIntent(pendIntent);
        startForeground(1, builder.build());  //id 1 for tracking, 2,3,4.. for others

        return START_REDELIVER_INTENT;
    }


    @Override
    public void onDestroy() {
        tracking_running = false;
        timer.cancel();
        stop.removeCallbacks(stopper);
        stop.removeCallbacks(full_stopper);
        locMan.removeUpdates(locListen);

        NotificationCompat.Builder builder2 = new NotificationCompat.Builder(this, MainActivity.TRACKING_NOTIF_CHANNEL);

        builder2.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.traking_complet))
                .setContentText(getString(R.string.sms_sent, sms_counter));
        int id = sPref.getInt("notification_id", 2);
        nManager.notify(id+1, builder2.build());
        sPref.edit().putInt("notification_id", id+1).commit();

        //rollback to default values (static are saved when restart)
        sms_counter = 0;
        sms_number = 10;
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
