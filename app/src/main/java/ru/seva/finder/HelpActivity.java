package ru.seva.finder;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;
import ru.seva.finder.BuildConfig;

public class HelpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        TextView tv = findViewById(R.id.help_text);
        tv.setText(Html.fromHtml(getString(R.string.help, BuildConfig.VERSION_NAME)));
        tv.setMovementMethod(new ScrollingMovementMethod());
    }
}
