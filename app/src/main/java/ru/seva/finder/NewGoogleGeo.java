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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;


public class NewGoogleGeo extends IntentService {

    public NewGoogleGeo() {
        super("NewGoogleGeo");
    }

    public void onCreate() {
        super.onCreate();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String phone = intent.getStringExtra("phone");  //phone needed to record to DB and notification
        String textMessage = intent.getStringExtra("message");

        Pattern pat1 = Pattern.compile("(gsm|wcdma|lte|cdma)\nMCC(\\d+)\nMNC(\\d+)\nLAC(\\d+)\nCID(\\d+)");  //check for nets in SMS
        Pattern pat2 = Pattern.compile("([0-9a-f]{12})\n(-?\\d+)");  //check for mac addr in SMS

        Matcher m1 = pat1.matcher(textMessage);
        Matcher m2 = pat2.matcher(textMessage);

        JSONObject json_req = new JSONObject();
        JSONArray cells_array = new JSONArray();
        JSONArray macs_array = new JSONArray();

        if (m1.find()) {
            String type = m1.group(1);
            String mcc = m1.group(2);
            String mnc = m1.group(3);
            try {
                json_req.put("homeMobileCountryCode", Integer.valueOf(mcc));  //first part of json request require nets (first net used)
                json_req.put("homeMobileNetworkCode", Integer.valueOf(mnc));
                json_req.put("radioType", type);
                json_req.put("considerIp", "false");

                m1.reset();
                while (m1.find()) {
                    JSONObject cell = new JSONObject();
                    cell.put("cellId", Integer.valueOf(m1.group(5)));
                    cell.put("locationAreaCode", Integer.valueOf(m1.group(4)));
                    cell.put("mobileCountryCode", Integer.valueOf(m1.group(2)));
                    cell.put("mobileNetworkCode", Integer.valueOf(m1.group(3)));
                    cells_array.put(cell);
                }
                json_req.put("cellTowers", cells_array);

            } catch (JSONException e) {
                //is it possible?)
            }
        }

        try {
            while (m2.find()) {
                String addr = m2.group(1);
                JSONObject mac = new JSONObject();
                mac.put("macAddress", String.format("%s:%s:%s:%s:%s:%s",
                        addr.substring(0,2),
                        addr.substring(2,4),
                        addr.substring(4,6),
                        addr.substring(6,8),
                        addr.substring(8,10),
                        addr.substring(10,12)));
                mac.put("signalStrength", Integer.valueOf(m2.group(2)));
                macs_array.put(mac);
            }
            json_req.put("wifiAccessPoints", macs_array);
        } catch (JSONException e) {
            //is it possible?)
        }

        StringBuilder sb = new StringBuilder();
        HttpsURLConnection urlConnection = null;


        try {
            byte[] postDataBytes = json_req.toString().getBytes("UTF-8");
            URL url = new URL("https://www.googleapis.com/geolocation/v1/geolocate?key=" + getString(R.string.google_geo_api));
            urlConnection = (HttpsURLConnection) url.openConnection();
            urlConnection.setRequestProperty("Content-Type", "application/json");
            urlConnection.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
            urlConnection.setDoOutput(true);
            urlConnection.getOutputStream().write(postDataBytes);
            Reader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), "UTF-8"));

            for(int c; (c = in.read()) >= 0;) {
                sb.append((char) c);
            }
        } catch (IOException e) {

        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }

        String api_answer = sb.toString();

        Pattern bat = Pattern.compile("bat:(\\d+)%");
        Matcher bat_matcher = bat.matcher(textMessage);
        String bat_value = null;
        if (bat_matcher.find()) {
            bat_value = bat_matcher.group(1);
        }

        Pattern time = Pattern.compile("ts:(\\d+)");
        Matcher time_matcher = time.matcher(textMessage);
        long time_unix_millis;
        if (time_matcher.find()) {
            time_unix_millis = Long.valueOf(time_matcher.group(1));
        } else {
            time_unix_millis = System.currentTimeMillis();
        }

        SharedPreferences sPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        int id = sPref.getInt("notification_id", 2);

        //recording to DB
        dBase baseConnect = new dBase(getApplicationContext());
        SQLiteDatabase db = baseConnect.getWritableDatabase();
        try {
            JSONObject response = new JSONObject(api_answer);
            if (response.has("location")) {
                Double lat = response.getJSONObject("location").getDouble("lat");
                Double lon = response.getJSONObject("location").getDouble("lng");
                Integer acc = null;
                if (response.has("accuracy")) {
                    acc = response.getInt("accuracy");
                }

                DateFormat df = new SimpleDateFormat("MMM d, HH:mm:ss, yyyy");
                String date = df.format(new Date(time_unix_millis));
                MainActivity.write_to_hist(db, phone, lat, lon, acc, date, bat_value, null, null, null);

                if (MainActivity.activityRunning && sPref.getBoolean("auto_map", false)) {  //map will be open only in case of running app and enabled option
                    Intent start_map = new Intent(getApplicationContext(), MapsActivity.class);
                    start_map.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    start_map.putExtra("lat", lat);
                    start_map.putExtra("lon", lon);
                    start_map.putExtra("zoom", 15d);
                    start_map.setAction("point");
                    if (acc != null) {
                        start_map.putExtra("accuracy", String.valueOf(acc) + getString(R.string.meters));
                    }
                    startActivity(start_map);
                } else {
                    //get phone name if exist
                    Cursor name_curs = db.query(dBase.PHONES_TABLE_OUT, new String[] {dBase.NAME_COL},
                            "phone = ?", new String[] {phone},
                            null, null, null);
                    String name;
                    name = (name_curs.moveToFirst()) ? (name_curs.getString(name_curs.getColumnIndex(dBase.NAME_COL))) : (phone);
                    name_curs.close();

                    Intent intentRes = new Intent(getApplicationContext(), HistoryActivity.class);
                    PendingIntent pendIntent = PendingIntent.getActivity(getApplicationContext(), 0, intentRes, PendingIntent.FLAG_UPDATE_CURRENT);
                    NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), MainActivity.COMMON_NOTIF_CHANNEL)
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setContentTitle(getString(R.string.message_with_coord))
                            .setContentText(getString(R.string.coords_received, name))
                            .setAutoCancel(true)
                            .setContentIntent(pendIntent);
                    Notification notification = builder.build();
                    NotificationManager nManage = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    nManage.notify(id, notification);
                }
            } else {
                NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), MainActivity.COMMON_NOTIF_CHANNEL)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(getString(R.string.error_getting_coordinats))
                        .setContentText(getString(R.string.api_error))
                        .setAutoCancel(true);
                Notification notification = builder.build();
                NotificationManager nManage = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                nManage.notify(id, notification);
            }
        } catch (JSONException e) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), MainActivity.COMMON_NOTIF_CHANNEL)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(getString(R.string.error_getting_coordinats))
                    .setContentText(getString(R.string.parsing_error))
                    .setAutoCancel(true);
            Notification notification = builder.build();
            NotificationManager nManage = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nManage.notify(id, notification);
        }
        db.close();
        sPref.edit().putInt("notification_id", id+1).commit();  //this is new thread
    }
}
