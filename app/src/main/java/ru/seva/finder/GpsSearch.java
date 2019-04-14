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
import android.telephony.SmsManager;

import java.util.ArrayList;
import java.util.Locale;


public class GpsSearch extends Service {
    private SharedPreferences sPref;
    private LocationManager locMan;

    private static final String GPS_ACCURACY = "gps_accuracy";
    private static final String GPS_ACCURACY_DEFAULT = "12";
    private static final String GPS_TIME = "gps_time";
    private static final String GPS_TIME_DEFAULT = "20";  //здесь в минутах

    private Handler h;  //stopper, котрый будет работать в основном потоке, вроде как быстр, и не подвесит приложение
    private final StringBuilder sms_answer = new StringBuilder("");
    private final ArrayList<String> phones = new ArrayList<>();

    private String lastLat;
    private String lastLon;  //переменные на случай еслси gps завёлся, но не успел набрал точность до таймера
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
        h.postDelayed(stopper, Integer.valueOf(sPref.getString(GPS_TIME, GPS_TIME_DEFAULT)) * 60000);  //остановка поиска GPS в случае если он не может получить координаты в течение опр. времени
        locMan = (LocationManager) getApplicationContext().getSystemService(LOCATION_SERVICE);
    }

    private final LocationListener locListen = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (location.hasAccuracy() && (location.getAccuracy() < Float.valueOf(sPref.getString(GPS_ACCURACY, GPS_ACCURACY_DEFAULT)))) {
                locMan.removeUpdates(locListen);  //после первого же нормального определения - выкл
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
                lastTrue = true;  //местополежение всё таки было определено, но недостаточно точно. Если что - отрпавим хотя бы это
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

        //проверка на вхождение номера в доверенные
        Cursor cursor_check = db.query(dBase.PHONES_TABLE_IN,
                new String[] {dBase.PHONES_COL},
                dBase.PHONES_COL + "=?",
                new String[] {phone_number},
                null, null, null);

        if (cursor_check.moveToFirst()) {
            //добавляем номер в список рассылки, если он в базе. И заводим GPS (если ещё не запущен)
            if (!phones.contains(phone_number)) {
                phones.add(phone_number);
            }

            Cursor name_curs = db.query(dBase.PHONES_TABLE_IN, new String[] {dBase.NAME_COL},
                    "phone = ?", new String[] {phone_number},
                    null, null, null);
            String name;
            name = (name_curs.moveToFirst()) ? (name_curs.getString(name_curs.getColumnIndex(dBase.NAME_COL))) : (phone_number);
            name_curs.close();

            Notification.Builder builder = new Notification.Builder(getApplicationContext())
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(getString(R.string.gps_processed))
                    .setContentText(getString(R.string.from, name))
                    .setAutoCancel(true);  //подумать над channel id
            Notification notification = builder.build();
            NotificationManager nManage = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            int id = sPref.getInt("notification_id", 2);
            nManage.notify(id, notification);
            sPref.edit().putInt("notification_id", id+1).apply();

            //проверка прав на новых API
            if (Build.VERSION.SDK_INT >= 23 && (getApplicationContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) &&
                    locMan.isProviderEnabled(LocationManager.GPS_PROVIDER) && startId == 1) {
                locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locListen);  //стартуем!
            }

            //на старом можно включить и так (если работает gps)
            if (Build.VERSION.SDK_INT < 23 && locMan.isProviderEnabled(LocationManager.GPS_PROVIDER) && startId == 1) {
                locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locListen);  //стартуем!
            }

            //на новом API нет прав либо на любоой API gps выключен
            if ((Build.VERSION.SDK_INT >=23 && getApplicationContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) ||
                    !locMan.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                try {
                    Thread.sleep(200);  //волшебный таймаут для того, чтобы не было звука
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                sms_answer.append("gps not enabled");
                start_send();
            }

        } else {
            if (phones.size() == 0) {  //если НИКОГО в списке рассылки НЕ было, остановливаемся
                h.removeCallbacks(stopper);
                locMan.removeUpdates(locListen);
                cursor_check.close();
                db.close();
                if (sPref.getBoolean("disable_sound", false) && sound_was_enabled) {
                    AudioManager aMan = (AudioManager) getApplication().getSystemService(Context.AUDIO_SERVICE);
                    aMan.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                }
                stopSelf();
            }
        }

        cursor_check.close();
        db.close();

        return START_REDELIVER_INTENT; //ответим хотя бы последнему номеру
    }


    private final Runnable stopper = new Runnable() {
        @Override
        public void run() {
            //логика завершения по таймеру
            locMan.removeUpdates(locListen);
            if (lastTrue) {  //отправим хотя бы то что есть
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
    }


    private void start_send() {   //рассылка всем запросившим
        if ((Build.VERSION.SDK_INT >= 23 &&
                (getApplicationContext().checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED)) ||
                Build.VERSION.SDK_INT < 23) {
            //добавим данные по батарее
            IntentFilter bat_filt= new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent battery = getApplicationContext().registerReceiver(null, bat_filt);
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            float batteryPct = level / (float)scale;
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
            aMan.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        }
        stopSelf();
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
