package ru.seva.finder;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
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

    SimpleCursorAdapter adapter;
    Cursor cursor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_read_sms);

        ListView list = (ListView) findViewById(R.id.lvSms);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            cursor = getContentResolver().query(Uri.parse("content://sms/inbox"), null, null, null, null);
        } else {
            Toast.makeText(NewReadSms.this, R.string.no_sms_rights, Toast.LENGTH_LONG).show();
        }
        String[] mWordListColumns = {
                //Telephony.Sms._ID,    // Contract class constant for the _ID column name
                Telephony.Sms.ADDRESS,  //ВНИМАНИЕ на API!!!
                Telephony.Sms.DATE
        };

        int[] toViews = {android.R.id.text1, android.R.id.text2};

        adapter = new SimpleCursorAdapter(this,
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
                    if (SmsReceiver.checkWifiSms(message)) {
                        //это сообщение с wifi-сетями
                        Intent new_message_intent = new Intent(NewReadSms.this, NewGoogleGeo.class);  //intent-сервис с запросом к google-api
                        new_message_intent.putExtra("phone", phone);
                        new_message_intent.putExtra("message", message);
                        startService(new_message_intent);
                        Toast.makeText(NewReadSms.this, R.string.wifi_sms_processing, Toast.LENGTH_SHORT).show();
                        finish();
                    } else if (SmsReceiver.checkGpsSms(message)) {
                        //sms с gps данными
                        Intent gps_intent = new Intent(NewReadSms.this, GpsCoordsReceived.class);
                        gps_intent.putExtra("phone", phone);
                        gps_intent.putExtra("message", message);
                        startService(gps_intent);
                        Toast.makeText(NewReadSms.this, R.string.gps_sms_processing, Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        //это левая SMS-ка
                        Toast.makeText(NewReadSms.this, R.string.not_valid_sms, Toast.LENGTH_LONG).show();
                    }
                } catch (NullPointerException e) {
                    Toast.makeText(NewReadSms.this, R.string.no_sms_accsess, Toast.LENGTH_LONG).show();
                }
            }
        });
    }


    protected void onDestroy() {
        super.onDestroy();
        if (cursor != null) {
            cursor.close();
        }
    }
}
