package ru.seva.finder;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;

public class Tracking extends Service {

    static boolean tracking_running = false;
    SharedPreferences sPref;

    public Tracking() {
    }


    @Override
    public void onCreate() {
        sPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        //int id = sPref.getInt("notification_id", 1);
        Intent notifIntent = new Intent(this, HistoryActivity.class);  // НАПИСАТЬ активити для состояния трекинга/остановки
        PendingIntent pendIntent = PendingIntent.getActivity(this, 0, notifIntent, 0);
        Notification notification = new Notification.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.tracking_on_notify))
                .setContentIntent(pendIntent)
                .build();   //  .setContentText("text")
        startForeground(1, notification);  //id 1 for tracking, 2,3,4.. for others
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        tracking_running = true;




        return START_REDELIVER_INTENT;
    }


    @Override
    public void onDestroy() {
        tracking_running = false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
