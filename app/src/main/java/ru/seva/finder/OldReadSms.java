package ru.seva.finder;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

public class OldReadSms extends AppCompatActivity {
    EditText text, phone_text;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_old_read_sms);
        text = (EditText) findViewById(R.id.textSms);
        phone_text = (EditText) findViewById(R.id.editTextPhone);
    }

    public void sms_process(View view) {
        String phone = phone_text.getText().toString();
        String message = text.getText().toString();
        if (SmsReceiver.checkWifiSms(message)) {
            //это сообщение с wifi-сетями
            Intent new_message_intent = new Intent(this, NewGoogleGeo.class);  //intent-сервис с запросом к google-api
            new_message_intent.putExtra("phone", phone);
            new_message_intent.putExtra("message", message);
            startService(new_message_intent);
            Toast.makeText(this, R.string.wifi_sms_processing, Toast.LENGTH_SHORT).show();
            finish();
        } else if (SmsReceiver.checkGpsSms(message)) {
            //sms с gps данными
            Intent gps_intent = new Intent(this, GpsCoordsReceived.class);
            gps_intent.putExtra("message", message);
            gps_intent.putExtra("phone", phone);
            startService(gps_intent);
            Toast.makeText(this, R.string.gps_sms_processing, Toast.LENGTH_SHORT).show();
            finish();
        } else {
            //это левая SMS-ка
            Toast.makeText(this, R.string.not_valid_sms, Toast.LENGTH_LONG).show();
        }
    }
}
