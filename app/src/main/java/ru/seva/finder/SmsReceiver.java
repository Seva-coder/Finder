package ru.seva.finder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.SmsMessage;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SmsReceiver extends BroadcastReceiver {
    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
    StringBuilder text = new StringBuilder("");

    @Override
    public void onReceive(Context context, Intent intent) {

        /* блок выключения звука
        AudioManager aMan = (AudioManager) context.getSystemService(context.AUDIO_SERVICE);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                aMan.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0);
                Toast.makeText(context, "213", Toast.LENGTH_SHORT).show();
            } else {
                aMan.setStreamMute(AudioManager.STREAM_NOTIFICATION, true);
                Toast.makeText(context, "000", Toast.LENGTH_SHORT).show();
            }
        } catch (NullPointerException e) {
            //хз когда это сработает
        }
        */

        String phone = "";  //на случай отсутствия в sms-ке
        Bundle intentExtras = intent.getExtras();
        String action = intent.getAction();
        if ((intentExtras != null) && (action.equals(SMS_RECEIVED))) {
            /* древний метод для совместимости с 17м API */
            Object[] sms = (Object[]) intentExtras.get("pdus");

            for (int i = 0; i < sms.length; ++i) {
                /* Parse Each Message */
                SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) sms[i]);
                phone = smsMessage.getOriginatingAddress();
                String message = smsMessage.getMessageBody();
                text.append(message);
            }

            Intent stop_bar = new Intent("disable_bar");
            String message = text.toString();
            if (checkWifiSms(message)) {
                Intent new_message_intent = new Intent(context, NewGoogleGeo.class);  //intent-сервис с запросом к google-api
                new_message_intent.putExtra("phone", phone);
                new_message_intent.putExtra("message", message);
                context.startService(new_message_intent);
                LocalBroadcastManager.getInstance(context).sendBroadcast(stop_bar);
            } else if (checkGpsSms(message)) {
                Intent gps_intent = new Intent("gps-result");
                gps_intent.putExtra("message", message);
                gps_intent.putExtra("phone", phone);
                LocalBroadcastManager.getInstance(context).sendBroadcast(gps_intent);
                LocalBroadcastManager.getInstance(context).sendBroadcast(stop_bar);
            } else if (message.equals("gps not enabled")) {
                Toast.makeText(context, R.string.gps_not_enabled, Toast.LENGTH_LONG).show();
                LocalBroadcastManager.getInstance(context).sendBroadcast(stop_bar);
            } else if (message.equals("unable get location")) {
                Toast.makeText(context, R.string.no_coord_bad_signal, Toast.LENGTH_LONG).show();
                LocalBroadcastManager.getInstance(context).sendBroadcast(stop_bar);
            }
            text = new StringBuilder("");  //иначе новый текст складывается с предудущим
        }
    }

    public static boolean checkWifiSms(String textMessage) {
        Pattern pat1 = Pattern.compile("^(gsm|wcdma|lte|cdma)\nMCC(\\d+)\nMNC(\\d+)\nLAC(\\d+)\nCID(\\d+)");
        Matcher m1 = pat1.matcher(textMessage);
        Pattern pat2 = Pattern.compile("([0-9a-f]{12})\n(-?\\d+)");  //mac или сети могут отсутствовать
        Matcher m2 = pat2.matcher(textMessage);
        return ((m1.find() || m2.find()));
    }

    public static boolean checkGpsSms(String message) {
        Pattern gps_pat = Pattern.compile("^lat:-?\\d+\\.\\d+ lon:-?\\d+\\.\\d+");
        Matcher m = gps_pat.matcher(message);
        return m.find();
    }


}
