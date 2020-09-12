package ru.seva.finder;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
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
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
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
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
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
    private TabHost tabHost;

    public static boolean activityRunning = false;  //we have only one instance, it's ok
    public final static String COMMON_NOTIF_CHANNEL = "common_channel";
    public final static String TRACKING_NOTIF_CHANNEL = "tracking_channel";
    private final static int REQUEST_CODE_CONTACTS = 1;
    private final static int REQUEST_CODE_AFTER_DISTURB_MENU = 2;
    private static final int REQUEST_CODE_LACKING_PERMISSIONS_RESPONSE = 3;
    private static final int REQUEST_CODE_LACKING_PERMISSIONS_TRACKING =4;
    private static final int REQUEST_CODE_LACKING_PERMISSIONS_REQUEST = 5;
    private static final int REQUEST_CODE_LACKING_PERMISSIONS_SEND_MY_COORD = 6;
    private static final int REQUEST_CODE_LACKING_PERMISSIONS_SEND_MY_NETS = 7;
    private SharedPreferences sPref;

    private Cursor cursor;
    private Cursor cursor_answ;
    private SQLiteDatabase db;
    private SimpleCursorAdapter adapter;
    private SimpleCursorAdapter adapter_answ;
    private String[] permsForRequest, permsForRequestOreo, permsForResponse, permsForResponseOreo;

    private EditText field_name, field_phone;
    private NotificationManager nManage;

    private Intent mSavedGpsIntent, mSavedWifiIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ListView list = findViewById(R.id.lvSendTo);
        ListView list_receive = findViewById(R.id.lvReceiveFrom);
        activityRunning = true;

        nManage = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        permsForRequest= new String[]{
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,
        };

        //because bug in android Oreo (to send SMS READ_PHONE_STATE permission is needed) https://issuetracker.google.com/issues/66979952
        permsForRequestOreo = new String[]{
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_PHONE_STATE
        };

        permsForResponse = new String[]{
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CHANGE_WIFI_STATE
        };

        permsForResponseOreo = new String[]{
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CHANGE_WIFI_STATE
        };


        sPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        //checking permissions needed for answer, if it enabled
        if (sPref.getBoolean("answer", false)) {
            requestPermsForResponse();
        }


        dBase baseConnect = new dBase(this);
        db = baseConnect.getWritableDatabase();

        String[] fromColons = {dBase.NAME_COL, dBase.PHONES_COL};
        int[] toViews = {android.R.id.text1, android.R.id.text2};
        cursor = db.query(dBase.PHONES_TABLE_OUT, null, null, null, null, null, null);

        //adapter for requests list
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
                toViews,  //"toViews" are the same in both tabs
                0);
        list_receive.setAdapter(adapter_answ);
        list_receive.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                click_on_trusted(view, id);
            }
        });

        //maybe it's possible to do in XML?
        tabHost = findViewById(R.id.tabHost);
        tabHost.setup();
        TabHost.TabSpec tabspec = tabHost.newTabSpec("tag1");  //tag needed only for constructor
        tabspec.setContent(R.id.tab_out);
        tabspec.setIndicator(getString(R.string.request_numbs));
        tabHost.addTab(tabspec);

        tabspec = tabHost.newTabSpec("tag2");
        tabspec.setContent(R.id.tab_in);
        tabspec.setIndicator(getString(R.string.answer_numbs));
        tabHost.addTab(tabspec);

        //local receiver for progress bar stopping
        LocalBroadcastManager.getInstance(this).registerReceiver(PGbar, new IntentFilter("disable_bar"));


        //offer to read the help at the first app start and create channels for notifications
        if (sPref.getBoolean("first_start", true)) {
            sPref.edit().putBoolean("first_start", false).apply();

            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                NotificationChannel single_channel = new NotificationChannel(COMMON_NOTIF_CHANNEL, getString(R.string.single_coord_chan), NotificationManager.IMPORTANCE_HIGH);
                single_channel.setDescription(getString(R.string.single_coord_chan_description));
                nm.createNotificationChannel(single_channel);

                NotificationChannel tracking_channel = new NotificationChannel(TRACKING_NOTIF_CHANNEL, getString(R.string.tracking_chan), NotificationManager.IMPORTANCE_LOW);
                tracking_channel.setDescription(getString(R.string.tracking_chan_description));
                nm.createNotificationChannel(tracking_channel);
            }


            DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int witch) {
                    switch(witch) {
                        case DialogInterface.BUTTON_POSITIVE:
                            Intent intent = new Intent(getApplicationContext(), HelpActivity.class);
                            startActivity(intent);
                            break;
                        case DialogInterface.BUTTON_NEGATIVE:
                            //don't open
                            break;
                    }
                }
            };
            AlertDialog.Builder builder2 = new AlertDialog.Builder(this);
            builder2.setMessage(R.string.open_help_start).setPositiveButton(R.string.yes, dialogClickListener)
                    .setNegativeButton(R.string.no, dialogClickListener).show();
        }


    }


    /**
     * calls before adding number to list for requests, when not enough permissions
     */
    private void requestPermsForRequest() {
        ArrayList<String> lacking = new ArrayList<>();

        if (Build.VERSION.SDK_INT == 26 && !hasPermissions(permsForRequestOreo)) {
            for (String perm : permsForRequestOreo) { //READ_PHONE_STATE added to workaround known bug (only in android 8.0)
                if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_DENIED) {
                    lacking.add(perm);
                }
            }
        } else if (Build.VERSION.SDK_INT >= 23 && !hasPermissions(permsForRequest)) {
            for (String perm : permsForRequest) {
                if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_DENIED) {
                    lacking.add(perm);
                }
            }
        }

        if (lacking.size() != 0) {
            ActivityCompat.requestPermissions(MainActivity.this, lacking.toArray(new String[0]), REQUEST_CODE_LACKING_PERMISSIONS_REQUEST);
        }
    }


    /**
     * calls at start when "respond to answers" enabled, and before adding new number to response ("trusted list")
     */
    private void requestPermsForResponse() {
        //"dangerous" permission check
        ArrayList<String> lacking = new ArrayList<>();

        if (Build.VERSION.SDK_INT == 26 && !hasPermissions(permsForResponseOreo)) {
            for (String perm : permsForResponseOreo) { //READ_PHONE_STATE added to workaround known bug (only in android 8.0)
                if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_DENIED) {
                    lacking.add(perm);
                }
            }
        } else if (Build.VERSION.SDK_INT >= 23 && !hasPermissions(permsForResponse)) {
            for (String perm : permsForResponse) {
                if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_DENIED) {
                    lacking.add(perm);
                }
            }
        }

        if (lacking.size() != 0) {
            ActivityCompat.requestPermissions(MainActivity.this, lacking.toArray(new String[0]), REQUEST_CODE_LACKING_PERMISSIONS_RESPONSE);
        } else {
            //now check muting permission
            if (Build.VERSION.SDK_INT >= 23 && !nManage.isNotificationPolicyAccessGranted()) {  //request for muting permission
                Intent dont_disturb_intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                Toast.makeText(this, R.string.enable_sound, Toast.LENGTH_LONG).show();
                startActivityForResult(dont_disturb_intent, REQUEST_CODE_AFTER_DISTURB_MENU);
            }
        }



        //add Finder to exclusion list on MIUI
        if (sPref.getBoolean("miui_menu_was_not_called", true)) {
            try {
                Intent miui_intent = new Intent();
                miui_intent.setComponent(new ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"));
                miui_intent.putExtra("package_name", getPackageName());
                miui_intent.putExtra("package_label", getText(R.string.app_name));
                startActivity(miui_intent);
                Toast.makeText(this, R.string.enable_background_miui, Toast.LENGTH_LONG).show();
            } catch (ActivityNotFoundException anfe) {
            } finally {
                sPref.edit().putBoolean("miui_menu_was_not_called", false).apply();
            }
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        boolean rules = true;

        for(int rul: grantResults) {
            rules = rules && (rul == PackageManager.PERMISSION_GRANTED);
        }

        if (rules) {
            switch (requestCode) {
                case REQUEST_CODE_LACKING_PERMISSIONS_RESPONSE:
                    Toast.makeText(MainActivity.this, R.string.permissions_obtained, Toast.LENGTH_SHORT).show();
                    //request for muting permission
                    NotificationManager nManage = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    if (Build.VERSION.SDK_INT >= 23 && !nManage.isNotificationPolicyAccessGranted()) {
                        Intent dont_disturb_intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                        Toast.makeText(this, R.string.enable_sound, Toast.LENGTH_LONG).show();
                        startActivityForResult(dont_disturb_intent, REQUEST_CODE_AFTER_DISTURB_MENU);
                    }
                    break;

                case REQUEST_CODE_LACKING_PERMISSIONS_SEND_MY_COORD:
                    sendMyGpsCoord();
                    break;

                case REQUEST_CODE_LACKING_PERMISSIONS_SEND_MY_NETS:
                    sendMyNets();
                    break;

                case REQUEST_CODE_LACKING_PERMISSIONS_TRACKING:
                case REQUEST_CODE_LACKING_PERMISSIONS_REQUEST:
                    default:
                    Toast.makeText(MainActivity.this, R.string.permissions_obtained, Toast.LENGTH_SHORT).show();
                    break;
            }
        } else {
            Toast.makeText(MainActivity.this, R.string.no_permits_received, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasPermissions(String[] perms) {
        boolean answer = true;
        for (String perm : perms) {
            if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_DENIED) {
                answer = false;
                break;
            }
        }
        return answer;
    }

    //popup menu for list of trusted numbers
    private void click_on_trusted(final View v, final long id) {
        PopupMenu menu = new PopupMenu(this, v);
        menu.inflate(R.menu.context_menu_trusted);
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                Cursor query = db.query(dBase.PHONES_TABLE_IN, new String[] {dBase.PHONES_COL, dBase.NAME_COL},
                        "_id = ?", new String[] {Long.toString(id)},
                        null, null, null);
                query.moveToFirst();
                final String phone = query.getString(query.getColumnIndex(dBase.PHONES_COL));
                final String name = query.getString(query.getColumnIndex(dBase.NAME_COL));
                query.close();
                switch(item.getItemId()) {
                    case R.id.track:
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        LayoutInflater inflater = getLayoutInflater();
                        final View v2 = inflater.inflate(R.layout.tracking_menu, null);
                        final EditText max_number_edit = v2.findViewById(R.id.max_number);
                        final EditText delay_edit = v2.findViewById(R.id.delay);
                        final EditText coord_numb_edit = v2.findViewById(R.id.number_of_coordinates);
                        final EditText accuracy_edit = v2.findViewById(R.id.accuracy);

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
                                .setPositiveButton(R.string.start_tracking, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        String tracking_sms_max_number, tracking_delay, tracking_coord_number, tracking_accuracy;
                                        tracking_sms_max_number = max_number_edit.getText().toString();
                                        tracking_delay = delay_edit.getText().toString();
                                        tracking_coord_number = coord_numb_edit.getText().toString();
                                        tracking_accuracy = accuracy_edit.getText().toString();
                                        //save settings for auto complete this form in future (no checks need - keyboard is digital)
                                        sPref.edit().putString("tracking_sms_max_number", tracking_sms_max_number).apply();
                                        sPref.edit().putString("tracking_delay", tracking_delay).apply();
                                        sPref.edit().putString("tracking_coord_number", tracking_coord_number).apply();
                                        sPref.edit().putString("tracking_accuracy", tracking_accuracy).apply();

                                        Intent intent = new Intent(getApplicationContext(), Tracking.class);
                                        intent.putExtra("tracking_sms_max_number", tracking_sms_max_number);
                                        intent.putExtra("tracking_delay", tracking_delay);
                                        intent.putExtra("tracking_coord_number", tracking_coord_number);
                                        intent.putExtra("tracking_accuracy", tracking_accuracy);
                                        intent.putExtra("phone", phone);
                                        intent.putExtra("name", name);
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

                        final AlertDialog dialog = builder.create();
                        //tracking is only possible for 1 phone number
                        if (Tracking.tracking_running) {
                            Toast.makeText(v.getContext(), R.string.tracking_already_running, Toast.LENGTH_LONG).show();
                        } else {
                            String[] trackingPermOreo = new String[] {Manifest.permission.SEND_SMS,
                                                                    Manifest.permission.READ_PHONE_STATE,
                                                                    Manifest.permission.ACCESS_FINE_LOCATION};  //READ_PHONE_STATE added to workaround known bug (only in android 8.0)

                            String[] trackingNorm = new String[] {Manifest.permission.SEND_SMS,
                                                                Manifest.permission.ACCESS_FINE_LOCATION};

                            String[] permTracking;
                            if (Build.VERSION.SDK_INT == 26) {
                                permTracking = trackingPermOreo;
                            } else {
                                permTracking = trackingNorm;
                            }
                            //show menu with inactive start button in case of missing settings. sms_number is just an example, all settings are created simultaneously. Button will be activated later in listener
                            if (hasPermissions(permTracking)) {
                                dialog.show();
                                if (sPref.contains("tracking_sms_max_number")) {
                                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                } else {
                                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                                }
                            } else {
                                requirePermissions(permTracking, REQUEST_CODE_LACKING_PERMISSIONS_TRACKING);
                            }
                        }

                        final Pattern pat = Pattern.compile("\\d+");

                        //activator/deactivator of start tracking button
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
                                //enable when all fields are numbers > 0 (else disable)
                                if (pat.matcher(max_number).find() && pat.matcher(delay).find()
                                        && pat.matcher(coord_numb).find() && pat.matcher(accuracy).find()
                                        && Integer.parseInt(max_number) >= 1 && Integer.parseInt(delay) >= 20
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
                        mSavedGpsIntent = new Intent(getApplicationContext(), GpsSearch.class);
                        mSavedGpsIntent.putExtra("phone_number", phone);
                        LocationManager locMan = (LocationManager) getApplicationContext().getSystemService(LOCATION_SERVICE);
                        String[] sendGpsPerm;
                        if (Build.VERSION.SDK_INT == 26) {
                            sendGpsPerm = new String[] {Manifest.permission.SEND_SMS,
                                                        Manifest.permission.READ_PHONE_STATE,
                                                        Manifest.permission.ACCESS_FINE_LOCATION};
                        } else {
                            sendGpsPerm = new String[] {Manifest.permission.SEND_SMS,
                                                        Manifest.permission.ACCESS_FINE_LOCATION};
                        }

                        if (!sPref.getBoolean("location_enable", false) && !locMan.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            Toast.makeText(v.getContext(), R.string.no_gps_or_rights, Toast.LENGTH_LONG).show();
                            return true;
                        }

                        if (
                                (Build.VERSION.SDK_INT >= 23 && hasPermissions(sendGpsPerm)) || (Build.VERSION.SDK_INT < 23)
                        ) {
                            sendMyGpsCoord();
                        } else {
                            requirePermissions(sendGpsPerm, REQUEST_CODE_LACKING_PERMISSIONS_SEND_MY_COORD);
                        }
                        return true;
                    case R.id.wifi_send:
                        mSavedWifiIntent = new Intent(getApplicationContext(), WifiSearch.class);
                        mSavedWifiIntent.putExtra("phone_number", phone);
                        String[] wifiPerm;
                        if (Build.VERSION.SDK_INT == 26) {
                            wifiPerm = new String[] {
                                    Manifest.permission.SEND_SMS,
                                    Manifest.permission.READ_PHONE_STATE,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                            };
                        } else {
                            wifiPerm = new String[] {
                                    Manifest.permission.SEND_SMS,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                            };
                        }
                        if ((Build.VERSION.SDK_INT >= 23 && hasPermissions(wifiPerm)) || Build.VERSION.SDK_INT < 23) {
                            sendMyNets();
                        } else {
                            requirePermissions(wifiPerm, REQUEST_CODE_LACKING_PERMISSIONS_SEND_MY_NETS);
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


    /**
     * starts standard service for sending current wifi- and cell nets
     * It get intent from class variable, because can be called from onRequestPermissionsResult too
     */
    private void sendMyNets() {
        getApplicationContext().startService(mSavedWifiIntent);
        Toast.makeText(this, getString(R.string.nets_will_be_sent, mSavedWifiIntent.getStringExtra("phone_number")), Toast.LENGTH_LONG).show();
    }

    /**
     * starts standard service for sending current coordinates
     * It get intent from class variable, because can be called from onRequestPermissionsResult too
     */
    private void sendMyGpsCoord() {
        getApplicationContext().startService(mSavedGpsIntent);
        Toast.makeText(this, getString(R.string.coordinates_will_be_sent, mSavedGpsIntent.getStringExtra("phone_number")), Toast.LENGTH_LONG).show();
    }

    /**
     * request permissions, if they are not granted
     * @param requiredPerm
     * @param requestCode
     */
    private void requirePermissions(@NonNull String[] requiredPerm, int requestCode) {
        //request missing permissions
        ArrayList<String> lacking = new ArrayList<>();
        if (requiredPerm.length == 0) return;
        for (String permission : requiredPerm) {
            if (ContextCompat.checkSelfPermission(getApplicationContext(), permission) == PackageManager.PERMISSION_DENIED) {
                lacking.add(permission);
            }
        }
        if (lacking.size() == 0) return;
        ActivityCompat.requestPermissions(MainActivity.this, lacking.toArray(new String[0]), requestCode);
    }

    //progress bar disabling
    private final BroadcastReceiver PGbar = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            TextView label = findViewById(R.id.textProgress);
            ProgressBar bar = findViewById(R.id.progress);
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
        cursor_answ.close();
        db.close();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(PGbar);
    }

    //popup for sending request SMS
    private void showSendMenu(final View v, final long num_id) {
        PopupMenu menu = new PopupMenu(this, v);
        menu.inflate(R.menu.context_menu);
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {

            private void sendSmsRequest(String key, String def_text, String phone) {
                SmsManager sManager = SmsManager.getDefault();
                TextView label = findViewById(R.id.textProgress);
                ProgressBar bar = findViewById(R.id.progress);
                if ((ContextCompat.checkSelfPermission(v.getContext(), Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) &&
                        (ContextCompat.checkSelfPermission(v.getContext(), Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED)) {
                    sManager.sendTextMessage(phone, null, sPref.getString(key, def_text), null, null);
                    Toast.makeText(v.getContext(), R.string.request_has_been_send, Toast.LENGTH_LONG).show();
                    if (!key.equals("ringing")) {
                        label.setVisibility(View.VISIBLE);
                        bar.setVisibility(View.VISIBLE);
                    }
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
                query.close();
                switch (item.getItemId()) {
                    case R.id.wifi_id:
                        //dialog telling "no internet"
                        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int witch) {
                                switch(witch) {
                                    case DialogInterface.BUTTON_POSITIVE:
                                        //user knows about disconnect, send sms-request
                                        sendSmsRequest("wifi", "wifi_search", phone);
                                        break;
                                    case DialogInterface.BUTTON_NEGATIVE:
                                        //nothing to do
                                        break;
                                }
                            }
                        };

                        //check network
                        ConnectivityManager cManager = (ConnectivityManager) v.getContext().getSystemService(v.getContext().CONNECTIVITY_SERVICE);
                        NetworkInfo network_inf = cManager.getActiveNetworkInfo();

                        if (network_inf != null && network_inf.isConnected()) {
                            //network active - send SMS without warning
                            sendSmsRequest("wifi", "wifi_search", phone);
                        } else {
                            //no internet - but after dialog user can send SMS too
                            AlertDialog.Builder builder2 = new AlertDialog.Builder(v.getContext());
                            builder2.setMessage(R.string.no_internet_warning).setPositiveButton(R.string.yes, dialogClickListener)
                                    .setNegativeButton(R.string.no, dialogClickListener).show();
                        }
                        return true;

                    case R.id.gps_id:
                        sendSmsRequest("gps", "gps_search", phone);
                        return true;

                    case R.id.ringing_id:
                        sendSmsRequest("ringing", getString(R.string.ring_default_command), phone);
                        return true;

                    case R.id.del_id:
                        db.delete(dBase.PHONES_TABLE_OUT, "_id = ?", new String[] {Long.toString(num_id)});
                        updateList();
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

    /**
     * check corresponding permissions before show menu to add number (to response/request)
     */
    public void add_number_btn(View view) {
        String[] permsResponseCurrent, permsRequestCurrent;
        if (Build.VERSION.SDK_INT == 26) {
            permsResponseCurrent = permsForResponseOreo;
            permsRequestCurrent = permsForRequestOreo;
        } else {
            permsResponseCurrent = permsForResponse;
            permsRequestCurrent = permsForRequest;
        }

        if (Build.VERSION.SDK_INT >= 23) {
            if (tabHost.getCurrentTab() == 0) {
                //request tab (left)
                if (hasPermissions(permsRequestCurrent)) {
                    rem_btn_clicked(view);
                } else {
                    requestPermsForRequest();
                }
            } else {
                //response tab (right)
                if (hasPermissions(permsResponseCurrent) && nManage.isNotificationPolicyAccessGranted()) {
                    rem_btn_clicked(view);
                } else {
                    requestPermsForResponse();
                }
            }
        } else {
            rem_btn_clicked(view);
        }
    }

    private void rem_btn_clicked(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        final View v = inflater.inflate(R.layout.add_menu, null);
        field_name = v.findViewById(R.id.name);
        field_phone = v.findViewById(R.id.phone);

        builder.setView(v)
                .setPositiveButton(R.string.positive_add_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String number_name = field_name.getText().toString();
                        String phone_number = field_phone.getText().toString().replaceAll("\\s+", "");
                        ContentValues cv = new ContentValues();
                        cv.put(dBase.PHONES_COL, phone_number);
                        cv.put(dBase.NAME_COL, number_name);

                        String table;  //select table for adding phone number
                        if (tabHost.getCurrentTab() == 0) {
                            table = dBase.PHONES_TABLE_OUT;  //"phones";
                        } else {
                            table = dBase.PHONES_TABLE_IN;  //"phones_to_answer";
                            if (!sPref.getBoolean("answer", false)) {
                                sPref.edit().putBoolean("answer", true).apply();  //enabling "Respond to requests" mode
                                Toast.makeText(MainActivity.this, R.string.check_settings, Toast.LENGTH_SHORT).show();
                            }
                            if (Build.VERSION.SDK_INT >= 23) {
                                Toast.makeText(MainActivity.this, R.string.wifi_gps_warning, Toast.LENGTH_LONG).show();
                            }
                        }

                        //is this number in DB?
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
        //activate "add" button when phone field is not empty
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

    //open contacts app on the phone to select contact
    public void btn_get_contact(View v) {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
        startActivityForResult(intent, REQUEST_CODE_CONTACTS);
    }

    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (requestCode == REQUEST_CODE_CONTACTS) {
            if (resultCode == RESULT_OK) {
                Uri uri = data.getData();
                String[] columns_to_get = new String[] {ContactsContract.CommonDataKinds.Phone.NUMBER,
                                                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME};
                Cursor curs = getContentResolver().query(uri, columns_to_get, null, null, null);

                if (curs != null && curs.moveToFirst()) {
                    String name = curs.getString(1);
                    String number = curs.getString(0);
                    field_name.setText(name);
                    field_phone.setText(number.replaceAll("\\s+|-+|[()]+", ""));
                }
            }
        } else if (requestCode == REQUEST_CODE_AFTER_DISTURB_MENU) {
            NotificationManager nManage = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 23 && !nManage.isNotificationPolicyAccessGranted()) {
                Toast.makeText(this, R.string.no_mute_permission_warning, Toast.LENGTH_LONG).show();
            }
        }
    }

    public void sms_btn_clicked(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            //in API 19 added SMS provider
            Intent intent = new Intent(this, NewReadSms.class);
            startActivity(intent);
        } else {
            //on older API user enters text manually
            Intent intent = new Intent(this, OldReadSms.class);
            startActivity(intent);
        }
    }

    private void updateList() {
        Cursor cursor = db.query(dBase.PHONES_TABLE_OUT, null, null, null, null, null, null);
        adapter.swapCursor(cursor);
        adapter.notifyDataSetChanged();
    }

    private void updateAnswList() {
        Cursor cursor = db.query(dBase.PHONES_TABLE_IN, null, null, null, null, null, null);
        adapter_answ.swapCursor(cursor);
        adapter_answ.notifyDataSetChanged();
    }
}
