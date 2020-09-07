package ru.seva.finder;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class NewReadSms extends AppCompatActivity {

    private Cursor cursor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_read_sms);


        if ((Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) ||
                Build.VERSION.SDK_INT < 23) {
            cursor = getContentResolver().query(Uri.parse("content://sms/inbox"), null, null, null, null);
        } else {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.READ_SMS}, 1);
        }
        initActivity();
    }

    private void initActivity() {
        ListView list = findViewById(R.id.lvSms);

        String[] mWordListColumns = {
                Telephony.Sms.ADDRESS,  //this activity will be started only on 19 api and later
                Telephony.Sms.DATE
        };

        int[] toViews = {android.R.id.text1, android.R.id.text2};

        SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
                android.R.layout.simple_list_item_2,
                cursor,
                mWordListColumns,
                toViews,
                0);

        adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            public boolean setViewValue(View view, Cursor cursor, int column) {
                if (column == cursor.getColumnIndex("DATE")) {
                    TextView text = (TextView) view;
                    Date date = new Date(cursor.getLong(column));
                    DateFormat df = new SimpleDateFormat("MMM d, HH:mm");
                    String res = df.format(date);
                    text.setText(res);
                    return true;
                }
                return false;
            }
        });

        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Cursor query = getContentResolver().query(Uri.parse("content://sms/inbox"),
                        null,
                        "_id = ?",
                        new String[] {String.valueOf(id)},
                        null);
                try {
                    query.moveToFirst();
                    String phone = query.getString(query.getColumnIndex("ADDRESS"));
                    String message = query.getString(query.getColumnIndex("BODY"));
                    query.close();
                    boolean ok = checkSms(NewReadSms.this, phone, message);
                    if (ok) {  //close activity in success case
                        finish();
                    }
                } catch (NullPointerException e) {
                    Toast.makeText(NewReadSms.this, R.string.no_sms_accsess, Toast.LENGTH_LONG).show();
                }
            }
        });
    }


    static boolean checkSms(Context context, String phone, String message) {
        boolean res = false;
        if (SmsReceiver.checkWifiSms(message)) {
            //this message with wifi-nets
            Intent new_message_intent = new Intent(context, NewGoogleGeo.class);  //use google-api to get location
            new_message_intent.putExtra("phone", phone);
            new_message_intent.putExtra("message", message);
            context.startService(new_message_intent);
            Toast.makeText(context, R.string.wifi_sms_processing, Toast.LENGTH_SHORT).show();
            res = true;
        } else if (SmsReceiver.checkGpsSms(message)) {
            //this message with GPS-data
            Intent gps_intent = new Intent(context, GpsCoordsReceived.class);
            gps_intent.putExtra("phone", phone);
            gps_intent.putExtra("message", message);
            context.startService(gps_intent);
            Toast.makeText(context, R.string.gps_sms_processing, Toast.LENGTH_SHORT).show();
            res = true;
        } else if (SmsReceiver.checkTrackingSms(message)) {
            //this is tracking SMS
            Intent track_point = new Intent(context, TrackReceiveService.class);
            track_point.putExtra("message", message);
            track_point.putExtra("phone_number", phone);
            Toast.makeText(context, R.string.tracking_sms_open, Toast.LENGTH_SHORT).show();
            context.startService(track_point);
            res = true;
        } else {
            //SMS without coordinates
            Toast.makeText(context, R.string.not_valid_sms, Toast.LENGTH_LONG).show();
        }
        return res;
    }

    protected void onDestroy() {
        super.onDestroy();
        if (cursor != null) {
            cursor.close();
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
            Toast.makeText(this, R.string.no_sms_rights, Toast.LENGTH_LONG).show();
        } else {
            cursor = getContentResolver().query(Uri.parse("content://sms/inbox"), null, null, null, null);
            initActivity();
        }
    }

}
