package ru.seva.finder;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

public class OldReadSms extends AppCompatActivity {
    private EditText text;
    private EditText phone_text;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_old_read_sms);
        text = findViewById(R.id.textSms);
        phone_text = findViewById(R.id.editTextPhone);
    }

    public void sms_process(View view) {
        String phone = phone_text.getText().toString();
        String message = text.getText().toString();
        boolean ok = NewReadSms.checkSms(OldReadSms.this, phone, message);
        if (ok) {  //close activity in success case
            finish();
        }
    }
}
