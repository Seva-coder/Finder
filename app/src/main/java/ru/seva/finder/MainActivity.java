package ru.seva.finder;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SimpleCursorAdapter;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.regex.Pattern;


public class MainActivity extends AppCompatActivity {
    Button remember_btn;
    ListView list, list_receive;
    TabHost tabHost;

    public static boolean activityRunning = false;  //у нас будет только один инстанс, поэтому вроде норм
    SharedPreferences sPref;

    Cursor cursor, cursor_answ;
    dBase baseConnect;
    SQLiteDatabase db;
    SimpleCursorAdapter adapter, adapter_answ;
    String[] allDangPerm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        remember_btn = (Button) findViewById(R.id.button);
        list = (ListView) findViewById(R.id.lvSendTo);
        list_receive = (ListView) findViewById(R.id.lvReceiveFrom);
        activityRunning = true;
        allDangPerm = new String[] {
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        sPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        //подрубаемся к базе
        baseConnect = new dBase(this);
        db = baseConnect.getWritableDatabase();

        String[] fromColons = {dBase.NAME_COL, dBase.PHONES_COL};
        int[] toViews = {android.R.id.text1, android.R.id.text2};
        cursor = db.query(dBase.PHONES_TABLE_OUT, null, null, null, null, null, null);

        //создаём адаптер списка запросов
        adapter = new SimpleCursorAdapter(this,
                android.R.layout.simple_list_item_2,
                cursor,
                fromColons,
                toViews,
                0);

        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                showSendMenu(view, id);
            }
        });


        cursor_answ = db.query(dBase.PHONES_TABLE_IN, null, null, null, null, null, null);
        adapter_answ = new SimpleCursorAdapter(this,
                android.R.layout.simple_list_item_2,
                cursor_answ,
                fromColons,
                toViews,  //они совпадают в обоих вкладках
                0);  //колонка телефонов называется так же
        list_receive.setAdapter(adapter_answ);
        list_receive.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                click_on_trusted(view, id);
            }
        });

        //неплохо было бы всё в XML переписать
        tabHost = findViewById(R.id.tabHost);
        tabHost.setup();
        TabHost.TabSpec tabspec = tabHost.newTabSpec("tag1");  //тэг нам ненужен, но он нужен конструктору
        tabspec.setContent(R.id.tab_out);
        tabspec.setIndicator(getString(R.string.request_numbs));
        tabHost.addTab(tabspec);

        tabspec = tabHost.newTabSpec("tag2");
        tabspec.setContent(R.id.tab_in);
        tabspec.setIndicator(getString(R.string.answer_numbs));
        tabHost.addTab(tabspec);

        //ресивер остановки прогресс-бара
        LocalBroadcastManager.getInstance(this).registerReceiver(PGbar, new IntentFilter("disable_bar"));

        //проверка прав на новых android
        if (Build.VERSION.SDK_INT >= 23 && hasPermitions()) {
            remember_btn.setEnabled(true);
        } else if (Build.VERSION.SDK_INT >= 23 && !hasPermitions()) {
            ArrayList<String> lacking = new ArrayList<String>();
            for (String prem : allDangPerm) {
                if (ContextCompat.checkSelfPermission(this, prem) == PackageManager.PERMISSION_DENIED) {
                    lacking.add(prem);
                }
            }
            ActivityCompat.requestPermissions(MainActivity.this, lacking.toArray(new String[0]), 1);
        } else if (Build.VERSION.SDK_INT < 23) {
            //права и так уже все выданы по дефолту
            remember_btn.setEnabled(true);
        }

        //предложение прочесть справку при первом старте
        if (sPref.getBoolean("first_start", true)) {
            sPref.edit().putBoolean("first_start", false).apply();
            DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int witch) {
                    switch(witch) {
                        case DialogInterface.BUTTON_POSITIVE:
                            Intent intent = new Intent(getApplicationContext(), HelpActivity.class);
                            startActivity(intent);
                            break;
                        case DialogInterface.BUTTON_NEGATIVE:
                            //ничего не отправляем
                            break;
                    }
                }
            };
            AlertDialog.Builder builder2 = new AlertDialog.Builder(this);
            builder2.setMessage(R.string.open_help_start).setPositiveButton(R.string.yes, dialogClickListener)
                    .setNegativeButton(R.string.no, dialogClickListener).show();
        }
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        boolean rules = true;

        for(int rul: grantResults) {
            rules = rules && (rul == PackageManager.PERMISSION_GRANTED);
        }

        if (requestCode == 1 && rules) {
            Toast.makeText(MainActivity.this, R.string.permissions_obtained, Toast.LENGTH_SHORT).show();
            remember_btn.setEnabled(true);  //разлочить интерфейс
        } else {
            Toast.makeText(MainActivity.this, R.string.no_permits_received, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasPermitions() {
        boolean answer = true;
        for (String perm : allDangPerm) {
            if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_DENIED) {
                answer = false;
                break;
            }
        }
        return answer;
    }

    //описание работы меню списка доверенных номеров
    private void click_on_trusted(final View v, final long id) {
        PopupMenu menu = new PopupMenu(this, v);
        menu.inflate(R.menu.context_menu_trusted);
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                Cursor query = db.query(dBase.PHONES_TABLE_IN, new String[] {dBase.PHONES_COL},
                        "_id = ?", new String[] {Long.toString(id)},
                        null, null, null);
                query.moveToFirst();
                final String phone = query.getString(query.getColumnIndex(dBase.PHONES_COL));
                query.close();
                switch(item.getItemId()) {
                    case R.id.track:
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        LayoutInflater inflater = getLayoutInflater();
                        final View v2 = inflater.inflate(R.layout.tracking_menu, null);
                        final EditText max_number_edit = (EditText) v2.findViewById(R.id.max_number);
                        final EditText delay_edit = (EditText) v2.findViewById(R.id.delay);
                        final EditText coord_numb_edit = (EditText) v2.findViewById(R.id.number_of_coordinates);
                        final EditText accuracy_edit = (EditText) v2.findViewById(R.id.accuracy);

                        if (sPref.contains("tracking_sms_max_number")) {
                            max_number_edit.setText(sPref.getString("tracking_sms_max_number", "10"));
                        }

                        if (sPref.contains("tracking_delay")) {
                            delay_edit.setText(sPref.getString("tracking_delay", "300"));
                        }

                        if (sPref.contains("tracking_accuracy")) {
                            accuracy_edit.setText(sPref.getString("tracking_accuracy", "15"));
                        }

                        if (sPref.contains("tracking_coord_number")) {
                            coord_numb_edit.setText(sPref.getString("tracking_coord_number", "4"));
                        }

                        builder.setView(v2)
                                .setPositiveButton(R.string.positive_add_button, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        String tracking_sms_max_number, tracking_delay, tracking_coord_number, tracking_accuracy;
                                        tracking_sms_max_number = max_number_edit.getText().toString();
                                        tracking_delay = delay_edit.getText().toString();
                                        tracking_coord_number = coord_numb_edit.getText().toString();
                                        tracking_accuracy = accuracy_edit.getText().toString();
                                        //сохраним настройки для повторного заполнения формы
                                        sPref.edit().putString("tracking_sms_max_number", tracking_sms_max_number).apply();
                                        sPref.edit().putString("tracking_delay", tracking_delay).apply();
                                        sPref.edit().putString("tracking_coord_number", tracking_coord_number).apply();
                                        sPref.edit().putString("tracking_accuracy", tracking_accuracy).apply();

                                        //передача через интент тк быстрее
                                        Intent intent = new Intent(getApplicationContext(), Tracking.class);
                                        intent.putExtra("tracking_sms_max_number", tracking_sms_max_number);
                                        intent.putExtra("tracking_delay", tracking_delay);
                                        intent.putExtra("tracking_coord_number", tracking_coord_number);
                                        intent.putExtra("tracking_accuracy", tracking_accuracy);
                                        intent.putExtra("phone", phone);
                                        startService(intent);

                                        Toast.makeText(v.getContext(), R.string.tracking_started, Toast.LENGTH_LONG).show();
                                    }
                                })
                                .setNegativeButton(R.string.negative_add_button, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.cancel();
                                    }
                                });
                        //разрешаем трекинг только на 1 номер
                        final AlertDialog dialog = builder.create();

                        if (Tracking.tracking_running) {
                            Toast.makeText(v.getContext(), R.string.tracking_already_running, Toast.LENGTH_LONG).show();
                        } else {
                            dialog.show();
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                        }

                        final Pattern pat = Pattern.compile("\\d+");

                        //обработчик активатор-деактиватор кнопки старта трекинга
                        TextWatcher edit_fields = new TextWatcher() {
                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                            }

                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) {
                                String max_number = max_number_edit.getText().toString();
                                String delay = delay_edit.getText().toString();
                                String coord_numb = coord_numb_edit.getText().toString();
                                String accuracy = accuracy_edit.getText().toString();
                                //активируем когда все поля - чилсла > 0
                                if (pat.matcher(max_number).find() && pat.matcher(delay).find()
                                        && pat.matcher(coord_numb).find() && pat.matcher(accuracy).find()
                                        && Integer.parseInt(max_number) >= 1 && Integer.parseInt(delay) >= 1
                                        && Integer.parseInt(coord_numb) >= 1 && Integer.parseInt(accuracy) >= 1) {
                                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                } else {
                                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                                }
                            }

                            @Override
                            public void afterTextChanged(Editable s) {

                            }
                        };
                        max_number_edit.addTextChangedListener(edit_fields);
                        delay_edit.addTextChangedListener(edit_fields);
                        coord_numb_edit.addTextChangedListener(edit_fields);
                        accuracy_edit.addTextChangedListener(edit_fields);
                        return true;

                    case R.id.gps_send:
                        Intent gps_intent = new Intent(getApplicationContext(), GpsSearch.class);
                        gps_intent.putExtra("phone_number", phone);
                        LocationManager locMan = (LocationManager) getApplicationContext().getSystemService(LOCATION_SERVICE);
                        if ((Build.VERSION.SDK_INT >= 23 && hasPermitions() && locMan.isProviderEnabled(LocationManager.GPS_PROVIDER)) ||
                                locMan.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            getApplicationContext().startService(gps_intent);
                            Toast.makeText(v.getContext(), getString(R.string.coordinates_will_be_sent, phone), Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(v.getContext(), R.string.no_gps_or_rights, Toast.LENGTH_LONG).show();
                        }
                        return true;
                    case R.id.wifi_send:
                        if ((Build.VERSION.SDK_INT >= 23 && hasPermitions()) || Build.VERSION.SDK_INT < 23) {
                            Intent wifi_intent = new Intent(getApplicationContext(), WifiSearch.class);
                            wifi_intent.putExtra("phone_number", phone);
                            getApplicationContext().startService(wifi_intent);
                            Toast.makeText(v.getContext(), getString(R.string.nets_will_be_sent, phone), Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(v.getContext(), R.string.no_rights_for_wifi_sending, Toast.LENGTH_LONG).show();
                        }
                        return true;
                    case R.id.delete:
                        db.delete(dBase.PHONES_TABLE_IN, "_id = ?", new String[] {Long.toString(id)});
                        updateAnswList();
                        return true;
                    default:
                        return false;
                }
            }
        });
        menu.show();
    }

    //отключение прогрессбара
    private BroadcastReceiver PGbar = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            TextView label = (TextView) findViewById(R.id.textProgress);
            ProgressBar bar = (ProgressBar) findViewById(R.id.progress);
            label.setVisibility(View.GONE);
            bar.setVisibility(View.GONE);
        }
    };

    static void write_to_hist(SQLiteDatabase base, String phone, Double lat, Double lon, @Nullable Integer accuracy,
                               String date, @Nullable String bat, @Nullable Double altitude,
                               @Nullable Float speed, @Nullable Float direction) {
        ContentValues cv = new ContentValues();
        cv.put("phone", phone);
        cv.put("lat", lat);
        cv.put("lon", lon);
        cv.put("date", date);

        if (altitude == null) {
            cv.putNull("height");
        } else {
            cv.put("height", altitude);
        }

        if (accuracy == null) {
            cv.putNull("acc");
        } else {
            cv.put("acc", accuracy);
        }

        if (bat == null) {
            cv.putNull("bat");
        } else {
            cv.put("bat", bat);
        }

        if (speed == null) {
            cv.putNull("speed");
        } else {
            cv.put("speed", speed);
        }

        if (direction == null) {
            cv.putNull("direction");
        } else {
            cv.put("direction", direction);
        }

        base.insert("history", null, cv);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        activityRunning = false;
        cursor.close();
        db.close();
    }


    private void showSendMenu(final View v, final long num_id) {
        PopupMenu menu = new PopupMenu(this, v);
        menu.inflate(R.menu.context_menu);
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {

            private void sendSmsRequest(String key, String def_text, String phone) {
                SmsManager sManager = SmsManager.getDefault();
                TextView label = (TextView) findViewById(R.id.textProgress);
                ProgressBar bar = (ProgressBar) findViewById(R.id.progress);
                if ((ContextCompat.checkSelfPermission(v.getContext(), Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) &&
                        (ContextCompat.checkSelfPermission(v.getContext(), Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED)) {
                    sManager.sendTextMessage(phone, null, sPref.getString(key, def_text), null, null);
                    Toast.makeText(v.getContext(), R.string.request_has_been_send, Toast.LENGTH_LONG).show();
                    label.setVisibility(View.VISIBLE);
                    bar.setVisibility(View.VISIBLE);
                } else {
                    Toast.makeText(v.getContext(), R.string.no_rights_for_sms, Toast.LENGTH_LONG).show();
                    label.setVisibility(View.GONE);
                    bar.setVisibility(View.GONE);
                }
            }

            @Override
            public boolean onMenuItemClick(MenuItem item) {
                Cursor query = db.query(dBase.PHONES_TABLE_OUT, new String[] {dBase.PHONES_COL},
                        "_id = ?", new String[] {Long.toString(num_id)},
                        null, null, null);
                query.moveToFirst();
                final String phone = query.getString(query.getColumnIndex(dBase.PHONES_COL));
                final TextView label = (TextView) findViewById(R.id.textProgress);
                final ProgressBar bar = (ProgressBar) findViewById(R.id.progress);
                switch (item.getItemId()) {
                    case R.id.wifi_id:
                        //предупреждение об интернете
                        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int witch) {
                                switch(witch) {
                                    case DialogInterface.BUTTON_POSITIVE:
                                        //ваще поифиг, юзверь оповещён что работать ничего не будет, инет не чекаем
                                        sendSmsRequest("wifi", "wifi_search", phone);
                                        break;
                                    case DialogInterface.BUTTON_NEGATIVE:
                                        //ничего не отправляем
                                        break;
                                }
                            }
                        };

                        //проверим наличие сети
                        ConnectivityManager cManager = (ConnectivityManager) v.getContext().getSystemService(v.getContext().CONNECTIVITY_SERVICE);
                        NetworkInfo network_inf = cManager.getActiveNetworkInfo();

                        if (network_inf != null && network_inf.isConnected()) {
                            //сеть "активна", отправляем запрос не дёргая юзера
                            sendSmsRequest("wifi", "wifi_search", phone);
                        } else {
                            //сети нет, но может всё равно отправить?
                            AlertDialog.Builder builder2 = new AlertDialog.Builder(v.getContext());
                            builder2.setMessage(R.string.no_internet_warning).setPositiveButton(R.string.yes, dialogClickListener)
                                    .setNegativeButton(R.string.no, dialogClickListener).show();
                        }
                        query.close();
                        return true;

                    case R.id.gps_id:
                        sendSmsRequest("gps", "gps_search", phone);
                        query.close();
                        return true;

                    case R.id.del_id:
                        db.delete(dBase.PHONES_TABLE_OUT, "_id = ?", new String[] {Long.toString(num_id)});
                        //обновление списка
                        updateList();
                        query.close();
                        return true;
                    default:
                        return false;
                }
            }
        });
        menu.show();
    }

    public void set_btn_clicked(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    public void hist_btn_clicked(View view) {
        Intent intent = new Intent(this, HistoryActivity.class);
        startActivity(intent);
    }

    public void help_btn_clicked(View view) {
        Intent intent = new Intent(this, HelpActivity.class);
        startActivity(intent);
    }

    public void rem_btn_clicked(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        final View v = inflater.inflate(R.layout.add_menu, null);
        final EditText field_name = (EditText) v.findViewById(R.id.name);
        final EditText field_phone = (EditText) v.findViewById(R.id.phone);

        builder.setView(v)
                .setPositiveButton(R.string.positive_add_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String number_name = field_name.getText().toString();
                        String phone_number = field_phone.getText().toString();
                        ContentValues cv = new ContentValues();
                        cv.put(dBase.PHONES_COL, phone_number);
                        cv.put(dBase.NAME_COL, number_name);

                        String table;  //выбор таблицы для записи
                        if (tabHost.getCurrentTab() == 0) {
                            table = dBase.PHONES_TABLE_OUT;  //"phones";
                        } else {
                            table = dBase.PHONES_TABLE_IN;  //"phones_to_answer";
                            if (!sPref.getBoolean("answer", false)) {
                                sPref.edit().putBoolean("answer", true).apply();  // включение режима ответа
                                Toast.makeText(MainActivity.this, R.string.check_settings, Toast.LENGTH_SHORT).show();
                            }
                            if (Build.VERSION.SDK_INT >= 23) {
                                Toast.makeText(MainActivity.this, R.string.wifi_gps_warning, Toast.LENGTH_LONG).show();
                            }
                        }

                        //проверка номера на повторное вхождение
                        Cursor cursor_check = db.query(table,
                                new String[] {dBase.PHONES_COL},
                                dBase.PHONES_COL + "=?",
                                new String[] {phone_number},
                                null, null, null);

                        if (cursor_check.moveToFirst()) {
                            Toast.makeText(MainActivity.this, R.string.phone_already_recorded, Toast.LENGTH_SHORT).show();
                        } else {
                            db.insert(table, null, cv);
                            Toast.makeText(MainActivity.this, R.string.number_saved, Toast.LENGTH_SHORT).show();
                        }
                        cursor_check.close();

                        if (tabHost.getCurrentTab() == 0) {
                            updateList();
                        } else {
                            updateAnswList();
                        }
                    }
                })
                .setNegativeButton(R.string.negative_add_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

        final AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        //активация кнопки при непустом поле
        field_phone.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() != 0) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                } else {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void afterTextChanged (Editable s) {

            }
        });
    }

    public void sms_btn_clicked(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            //in API 19 added SMS provider
            Intent intent = new Intent(this, NewReadSms.class);
            startActivity(intent);
        } else {
            Intent intent = new Intent(this, OldReadSms.class);
            startActivity(intent);
        }
    }

    public void updateList() {
        cursor = db.query(dBase.PHONES_TABLE_OUT, null, null, null, null, null, null);
        adapter.swapCursor(cursor);
        adapter.notifyDataSetChanged();
    }

    public void updateAnswList() {
        cursor = db.query(dBase.PHONES_TABLE_IN, null, null, null, null, null, null);
        adapter_answ.swapCursor(cursor);
        adapter_answ.notifyDataSetChanged();
    }
}
