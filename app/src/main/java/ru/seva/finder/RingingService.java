package ru.seva.finder;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;


public class RingingService extends Service {

    Intent stopIntent;
    PendingIntent pStopIntent;

    IntentFilter filterStop;
    StopRingingReceiver mStopRingingReceiver;
    AudioHelper aHelp;

    Handler stopHandler;

    // some trusted number already requested ringing
    // and service won't stop after non trusted request
    boolean already_trusted_number = false;

    static final String ACTION_STOP = "ru.seva.finder.STOP_RINGING";

    NotificationManager nManage;

    public RingingService() {
    }

    private SharedPreferences sPref;

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }


    class StopRingingReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopRinging.run();
        }
    }


    private Runnable stopRinging = new Runnable() {
        @Override
        public void run() {
            aHelp.stopRinging();
            unregisterReceiver(mStopRingingReceiver);
            stopHandler.removeCallbacks(stopRinging);
            stopForeground(true);
            stopSelf();
        }
    };

    @Override
    public void onCreate() {
        sPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        stopIntent = new Intent(ACTION_STOP);
        stopIntent.setPackage(getApplicationContext().getPackageName());
        pStopIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, stopIntent, PendingIntent.FLAG_ONE_SHOT);

        filterStop = new IntentFilter(ACTION_STOP);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String phone_number = intent.getStringExtra("phone_number");

        dBase baseConnect = new dBase(this);
        SQLiteDatabase db = baseConnect.getWritableDatabase();

        //is number is trusted?
        Cursor cursor_check = db.query(dBase.PHONES_TABLE_IN,
                new String[] {dBase.PHONES_COL},
                dBase.PHONES_COL + "=?",
                new String[] {phone_number},
                null, null, null);


        if (cursor_check.moveToFirst() && !already_trusted_number) {
            already_trusted_number = true;
            cursor_check.close();
            db.close();

            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), MainActivity.COMMON_NOTIF_CHANNEL)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(getString(R.string.notification_ringing_title))
                    .setContentText(getString(R.string.notification_ringing_text))
                    .setAutoCancel(true)
                    .setContentIntent(pStopIntent);
            Notification notification = builder.build();
            nManage = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            int id = sPref.getInt("notification_id", 2);
            startForeground(id, notification);
            sPref.edit().putInt("notification_id", id + 1).apply();

            mStopRingingReceiver = new StopRingingReceiver();
            registerReceiver(mStopRingingReceiver, filterStop);

            aHelp = new AudioHelper(getApplicationContext(), sPref);
            aHelp.startRinging();

            stopHandler = new Handler();
            //stop ringing when nobody stops it
            stopHandler.postDelayed(stopRinging, Long.valueOf(sPref.getString("ringing_duration", "60")) * 1000);
        } else if (!cursor_check.moveToFirst() && !already_trusted_number) {
            //number not trusted and no trusted processing now - stop service
            cursor_check.close();
            db.close();
            stopSelf();
        }
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {

    }
}
