package ru.seva.finder;

import android.app.IntentService;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
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
        String phone = intent.getStringExtra("phone");  //на будущее передадим тел (будет заноситься в базу)
        String textMessage = intent.getStringExtra("message");

        Pattern pat1 = Pattern.compile("(gsm|wcdma|lte|cdma)\nMCC(\\d+)\nMNC(\\d+)\nLAC(\\d+)\nCID(\\d+)");  //проверка на наличие данных сети
        Pattern pat2 = Pattern.compile("([0-9a-f]{12})\n(-?\\d+)");  //проверка на входящие маки

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
                json_req.put("homeMobileCountryCode", Integer.valueOf(mcc));  //первая часть json требует сети (возьмём первую)
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
                //хз когда такое вообще может произойти
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
            //хз когда такое вообще может произойти
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

        Intent geo_intent = new Intent("wifi-result");
        geo_intent.putExtra("result", sb.toString());
        geo_intent.putExtra("phone", phone);  //кочуем дальше)
        Pattern bat = Pattern.compile("bat:(\\d+)%");
        Matcher bat_matcher = bat.matcher(textMessage);
        if (bat_matcher.find()) {
            geo_intent.putExtra("bat", bat_matcher.group(1));
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(geo_intent);
    }
}
