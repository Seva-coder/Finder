package ru.seva.finder;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

public class HelpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        TextView tv = (TextView) findViewById(R.id.help_text);
        tv.setText(Html.fromHtml(getString(R.string.help)));
        tv.setMovementMethod(new ScrollingMovementMethod());
    }
}
