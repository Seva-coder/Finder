package ru.seva.finder;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.SmsMessage;


import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SmsReceiver extends BroadcastReceiver {
    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
    private StringBuilder text = new StringBuilder("");


    @Override
    public void onReceive(Context context, Intent intent) {

        SharedPreferences sPref;
        sPref = PreferenceManager.getDefaultSharedPreferences(context);
        AudioHelper aHelp = new AudioHelper(context, sPref);
        String phone = "";  //default init
        Bundle intentExtras = intent.getExtras();

        String action = intent.getAction();
        if ((intentExtras != null) && (action.equals(SMS_RECEIVED))) {
            /* old method to work with 17s API */
            Object[] sms = (Object[]) intentExtras.get("pdus");

            for (int i = 0; i < sms.length; ++i) {
                /* Parse Each Message */
                SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) sms[i]);
                phone = smsMessage.getOriginatingAddress();
                String message = smsMessage.getMessageBody();
                text.append(message);
            }

            NotificationManager nManage = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            //checking for possible answer
            Intent stop_bar = new Intent("disable_bar");
            String message = text.toString();
            if (checkWifiSms(message)) {
                Intent new_message_intent = new Intent(context, NewGoogleGeo.class);  //using google geo api
                new_message_intent.putExtra("phone", phone);
                new_message_intent.putExtra("message", message);
                context.startService(new_message_intent);
                LocalBroadcastManager.getInstance(context).sendBroadcast(stop_bar);
            } else if (checkGpsSms(message)) {
                Intent gps_intent = new Intent(context, GpsCoordsReceived.class);
                gps_intent.putExtra("phone", phone);
                gps_intent.putExtra("message", message);
                LocalBroadcastManager.getInstance(context).sendBroadcast(stop_bar);
                context.startService(gps_intent);
            } else if (checkTrackingSms(message)) {
                aHelp.pauseTrackingSound();
                Intent track_point = new Intent(context, TrackReceiveService.class);
                track_point.putExtra("message", message);
                track_point.putExtra("phone_number", phone);
                context.startService(track_point);
            } else if ((message.length() > 15) && message.substring(0, 15).equals("gps not enabled")) {
                //only 0-15 symbols because after goes battery data
                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, MainActivity.COMMON_NOTIF_CHANNEL)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(phone)
                        .setContentText(context.getString(R.string.gps_not_enabled))
                        .setAutoCancel(true);
                Notification notification = builder.build();
                int id = sPref.getInt("notification_id", 2);
                nManage.notify(id, notification);
                sPref.edit().putInt("notification_id", id+1).apply();
                LocalBroadcastManager.getInstance(context).sendBroadcast(stop_bar);
            } else if (((message.length() > 19) && message.substring(0, 19).equals("unable get location"))
                    || message.equals("net info unavailable")) {
                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, MainActivity.COMMON_NOTIF_CHANNEL)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(phone)
                        .setContentText(context.getString(R.string.no_coord_bad_signal))
                        .setAutoCancel(true);
                Notification notification = builder.build();
                int id = sPref.getInt("notification_id", 2);
                nManage.notify(id, notification);
                sPref.edit().putInt("notification_id", id+1).apply();
                LocalBroadcastManager.getInstance(context).sendBroadcast(stop_bar);
            }


            //checking for possible request of coordinates
            //wifi request
            if (message.equals(sPref.getString("wifi", context.getString(R.string.wifi_default_command))) && sPref.getBoolean("answer", false)) {
                aHelp.pauseSound();
                Intent wifi_intent = new Intent(context, WifiSearch.class);
                wifi_intent.putExtra("phone_number", phone);
                context.startService(wifi_intent);
            }

            //GPS request
            if (message.equals(sPref.getString("gps", context.getString(R.string.gps_default_command))) && sPref.getBoolean("answer", false)) {
                aHelp.pauseSound();
                Intent gps_intent = new Intent(context, GpsSearch.class);
                gps_intent.putExtra("phone_number", phone);
                context.startService(gps_intent);
            }

            //remote phone number add (if enabled in options)
            if (message.equals(sPref.getString("remote", "NO_DEFAULT_VALUE")) && sPref.getBoolean("remote_active", false)) {
                aHelp.pauseSound();
                Intent remote_intent = new Intent(context, RemoteAdding.class);
                remote_intent.putExtra("phone_number", phone);
                context.startService(remote_intent);
            }

            //start ringing command
            if (message.equals(sPref.getString("ringing", context.getString(R.string.ring_default_command))) && sPref.getBoolean("answer", false)) {
                Intent ringing_intent = new Intent(context, RingingService.class);
                ringing_intent.putExtra("phone_number", phone);
                context.startService(ringing_intent);
            }

            text = new StringBuilder("");  //default value, else new text added to previous
        }
    }

    static boolean checkWifiSms(String textMessage) {
        Pattern pat1 = Pattern.compile("^(gsm|wcdma|lte|cdma)\nMCC(\\d+)\nMNC(\\d+)\nLAC(\\d+)\nCID(\\d+)");
        Matcher m1 = pat1.matcher(textMessage);
        Pattern pat2 = Pattern.compile("([0-9a-f]{12})\n(-?\\d+)");  //mac or nets can absent
        Matcher m2 = pat2.matcher(textMessage);
        return ((m1.find() || m2.find()));
    }

    static boolean checkGpsSms(String message) {
        Pattern gps_pat = Pattern.compile("^lat:-?\\d+\\.\\d+ lon:-?\\d+\\.\\d+");
        Matcher m = gps_pat.matcher(message);
        return m.find();
    }


    static boolean checkTrackingSms(String message) {
        Pattern tracking_pat = Pattern.compile("^(\\d+\\.\\d+;\\d+\\.\\d+;\\d+\\.\\d+;\\d\\d:\\d\\d\n?)+");
        Matcher m = tracking_pat.matcher(message);
        return m.find();
    }


}
