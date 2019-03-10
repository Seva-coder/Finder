package ru.seva.finder;

import android.Manifest;
import android.app.Notification;
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

    Notification.Builder builder;
    NotificationManager nManager;
    LocationManager locMan;
    StringBuilder sms_text = new StringBuilder("");
    Handler stop = new Handler();

    Intent update_intent;  //интент для передачи свежих данных в окно с текущими данными о трекинге

    static boolean tracking_running = false;
    boolean lastTrue = false;
    String lastLat, lastLon, lastSpeed, phone;
    SharedPreferences sPref;
    Timer timer;
    static int sms_counter = 0, coords_counter = 0;  //static для доступноти из TrackStatus
    static int sms_number = 10, sms_buffer_max = 4;  //default values
    int delay = 300, accuracy = 15;

    public Tracking() {
    }


    private void append_send_coordinates(String lat, String lon, String speed, String date) {
        //просто наполнение буфера
        sms_text.append(lat);
        sms_text.append(";");
        sms_text.append(lon);
        sms_text.append(";");
        sms_text.append(speed);
        sms_text.append(";");
        sms_text.append(date);
        sms_text.append("\n");
        coords_counter++;

        //буфер полон => пора слать/+возможно стопать сервис
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

                    //отправим данные интентом
                    update_intent.setAction("update_fields");
                    LocalBroadcastManager.getInstance(this).sendBroadcast(update_intent);
                }
            }

            //двойная проверка для того чтобы СРАЗУ же остановить сервис после отправки всех сообщений
            if (sms_counter == sms_number) {
                //весь запас SMS отправлен, останавливаем всё. Уведомление в onDestroy
                stopSelf();
            }
        }
    }


    LocationListener locListen = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (location.hasAccuracy() && (location.getAccuracy() < accuracy)) {
                stop.removeCallbacks(stopper);
                locMan.removeUpdates(locListen);  //после первого же нормального определения - выкл
                String lat = String.format(Locale.US, "%.8f",location.getLatitude());
                String lon = String.format(Locale.US, "%.8f", location.getLongitude());
                String speed = String.format(Locale.US, "%.1f", location.getSpeed() * 3.6f);
                DateFormat df = new SimpleDateFormat("HH:mm");
                String date = df.format(Calendar.getInstance().getTime());
                append_send_coordinates(lat, lon, speed, date);
            } else {
                lastTrue = true;  //местополежение всё таки было определено, но недостаточно точно. Если что - отрпавим хотя бы это
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
            //проверка прав на новых API
            if (Build.VERSION.SDK_INT >= 23 && (getApplicationContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) &&
                    locMan.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locListen, Looper.getMainLooper());  //стартуем!
            }

            //на старом можно включить и так (если работает gps)
            if (Build.VERSION.SDK_INT < 23 && locMan.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locListen, Looper.getMainLooper());  //стартуем!
            }
            stop.postDelayed(stopper, delay * 800);  //остановка черз 80% вермени периода (800=1000ms*0.8)

            startForeground(1, builder.build());
        }
    }

    //логика завершения определения КООРДИНАТ по таймеру
    Runnable stopper = new Runnable() {
        @Override
        public void run() {
            locMan.removeUpdates(locListen);  //стопаем в люблм случае, значит за 80% периода координаты так и не удалось получить
            if (lastTrue) {  //отправим хотя бы то что есть
                DateFormat df = new SimpleDateFormat("HH:mm");
                String date = df.format(Calendar.getInstance().getTime());
                append_send_coordinates(lastLat, lastLon, lastSpeed, date);
                lastTrue =false;
            }
        }
    };

    //логика завершения ВСЕГО СЕРВИСА по таймеру при превышении расчётного времени в 1.5 раза
    Runnable full_stopper = new Runnable() {
        @Override
        public void run() {
            locMan.removeUpdates(locListen);
            stopSelf();
        }
    };


    @Override
    public void onCreate() {
        sPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        Intent notifIntent = new Intent(this, TrackStatus.class);
        PendingIntent pendIntent = PendingIntent.getActivity(this, 0, notifIntent, 0);
        builder = new Notification.Builder(this);
        builder.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.tracking_on_notify))
                .setContentIntent(pendIntent);
        startForeground(1, builder.build());  //id 1 for tracking, 2,3,4.. for others

        locMan = (LocationManager) getApplicationContext().getSystemService(LOCATION_SERVICE);
        nManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        update_intent = new Intent(this, TrackStatus.class);  //создадим интент для передачи данных о состоянии
    }



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        tracking_running = true;
        phone = intent.getStringExtra("phone");
        sms_number = Integer.parseInt(intent.getStringExtra("tracking_sms_max_number"));
        delay = Integer.parseInt(intent.getStringExtra("tracking_delay"));
        sms_buffer_max = Integer.parseInt(intent.getStringExtra("tracking_coord_number"));
        accuracy = Integer.parseInt(intent.getStringExtra("tracking_accuracy"));

        timer = new Timer();
        TrackingTask trackTask = new TrackingTask();  //таймер для получения очередной точки трека
        timer.scheduleAtFixedRate(trackTask, 0L, 1000L*delay);  //5 minutes default
        stop.postDelayed(full_stopper, sms_number * sms_buffer_max * delay * 1500L);  //остановим весь сервис на случай отсутствия GPS дольше чем 1.5 заплпнированного времени  (1500L = 1000ms * 1.5)

        return START_REDELIVER_INTENT;
    }


    @Override
    public void onDestroy() {
        tracking_running = false;
        timer.cancel();
        stop.removeCallbacks(stopper);
        stop.removeCallbacks(full_stopper);
        locMan.removeUpdates(locListen);

        Notification.Builder builder2 = new Notification.Builder(this);

        builder2.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.traking_complet))
                .setContentText(getString(R.string.sms_sent, sms_counter));
        int id = sPref.getInt("notification_id", 2);
        nManager.notify(id+1, builder2.build());
        sPref.edit().putInt("notification_id", id+1).commit();

        //возврат к дефолтным значениям, чтобы при повторном запуске было ОК
        sms_counter = 0;
        coords_counter = 0;
        sms_number = 10;
        sms_buffer_max = 4;
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
