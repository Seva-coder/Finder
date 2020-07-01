package ru.seva.finder;


import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.widget.Toast;


public class SettingsActivity extends AppCompatPreferenceActivity {
    private final static int REQUEST_CODE_MUTE = 2;
    private final static int REQUEST_CODE_TRACKING = 3;
    private final static int REQUEST_CODE_RINGING = 4;
    private static String new_ring_command = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction().replace(android.R.id.content, new Frag()).commit();
    }


    public static class Frag extends PreferenceFragment {
        SharedPreferences sPref;
        Preference.OnPreferenceChangeListener gpsCommandCheck = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                String stringValue = value.toString();
                if (stringValue.equals("") || stringValue.equals(sPref.getString("wifi", "wifi_search"))) {
                    Toast.makeText(getActivity(), R.string.wrong_values, Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }
        };


        @Override  //checking mute permission on new androids (no output in callback, only isNotificationPolicyAccessGranted)
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            NotificationManager notificationManager = (NotificationManager) getActivity().getSystemService(NOTIFICATION_SERVICE);
            if (requestCode == REQUEST_CODE_MUTE && Build.VERSION.SDK_INT >= 23 && notificationManager.isNotificationPolicyAccessGranted()) {
                sPref.edit().putBoolean("disable_sound", true).apply();
                CheckBoxPreference sound = (CheckBoxPreference) findPreference("disable_sound");
                sound.setChecked(true);
            }

            if (requestCode == REQUEST_CODE_TRACKING && Build.VERSION.SDK_INT >= 23 && notificationManager.isNotificationPolicyAccessGranted()) {
                sPref.edit().putBoolean("disable_tracking_sound", true).apply();
                CheckBoxPreference sound = (CheckBoxPreference) findPreference("disable_tracking_sound");
                sound.setChecked(true);
            }

            if (requestCode == REQUEST_CODE_RINGING && Build.VERSION.SDK_INT >= 23 && notificationManager.isNotificationPolicyAccessGranted()) {
                sPref.edit().putString("ringing", new_ring_command).apply();  //to change text in settings
                EditTextPreference ringing = (EditTextPreference) findPreference("ringing");
                ringing.setText(new_ring_command);  //to change text in current activity
            }

            if (Build.VERSION.SDK_INT >= 23 &&
                    (       (requestCode == REQUEST_CODE_MUTE) ||
                            (requestCode == REQUEST_CODE_TRACKING) ||
                            (requestCode == REQUEST_CODE_RINGING)   ) &&
                    !notificationManager.isNotificationPolicyAccessGranted()) {
                Toast.makeText(getActivity(), R.string.sound_perm_fail, Toast.LENGTH_LONG).show();
            }
        }

        Preference.OnPreferenceChangeListener wifiCommandCheck = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                String stringValue = value.toString();
                if (stringValue.equals("") || stringValue.equals(sPref.getString("gps", "gps_search"))) {
                    Toast.makeText(getActivity(), R.string.wrong_values, Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }
        };

        Preference.OnPreferenceChangeListener emptyCheck = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                String stringValue = value.toString();
                if (stringValue.equals("") || stringValue.equals("0")) {
                    Toast.makeText(getActivity(), R.string.wrong_values, Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }
        };

        Preference.OnPreferenceChangeListener audioCheck = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                String stringValue = value.toString();
                NotificationManager nManage = (NotificationManager) getActivity().getSystemService(NOTIFICATION_SERVICE);
                if (Build.VERSION.SDK_INT >= 23 && stringValue.equals("true") && !nManage.isNotificationPolicyAccessGranted()) {  //request for muting permission
                    Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                    Toast.makeText(getActivity(), R.string.enable_sound, Toast.LENGTH_LONG).show();
                    if (preference.getKey().equals("disable_sound")) {
                        startActivityForResult(intent, REQUEST_CODE_MUTE);
                    } else {
                        startActivityForResult(intent, REQUEST_CODE_TRACKING);
                    }
                    return false;
                }
                return true;
            }
        };



        Preference.OnPreferenceChangeListener ringingCheck = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                String stringValue = value.toString();

                if (stringValue.equals("")) {
                    Toast.makeText(getActivity(), R.string.wrong_values, Toast.LENGTH_LONG).show();
                    return false;
                }

                NotificationManager nManage = (NotificationManager) getActivity().getSystemService(NOTIFICATION_SERVICE);
                if (Build.VERSION.SDK_INT >= 23 && !nManage.isNotificationPolicyAccessGranted()) {  //request for muting permission
                    Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                    Toast.makeText(getActivity(), R.string.enable_sound, Toast.LENGTH_LONG).show();
                    new_ring_command = stringValue;
                    startActivityForResult(intent, REQUEST_CODE_RINGING);
                    return false;
                }
                return true;
            }
        };

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_general);
            Preference gps = findPreference("gps");
            Preference wifi = findPreference("wifi");

            Preference cache = findPreference("cache_size");
            Preference scans = findPreference("cycles");
            Preference pause = findPreference("timeout");
            Preference mac_number = findPreference("mac_numb");
            Preference gps_time = findPreference("gps_time");
            Preference accuracy = findPreference("gps_accuracy");
            Preference location_enable = findPreference("location_enable");
            Preference remote = findPreference("remote");
            Preference sound = findPreference("disable_sound");
            Preference tracking_sound = findPreference("disable_tracking_sound");
            Preference ringing = findPreference("ringing");
            Preference ringing_duration = findPreference("ringing_duration");

            sPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
            gps.setOnPreferenceChangeListener(gpsCommandCheck);
            wifi.setOnPreferenceChangeListener(wifiCommandCheck);

            cache.setOnPreferenceChangeListener(emptyCheck);
            scans.setOnPreferenceChangeListener(emptyCheck);
            pause.setOnPreferenceChangeListener(emptyCheck);
            mac_number.setOnPreferenceChangeListener(emptyCheck);
            gps_time.setOnPreferenceChangeListener(emptyCheck);
            accuracy.setOnPreferenceChangeListener(emptyCheck);
            remote.setOnPreferenceChangeListener(emptyCheck);
            ringing_duration.setOnPreferenceChangeListener(emptyCheck);

            sound.setOnPreferenceChangeListener(audioCheck);
            tracking_sound.setOnPreferenceChangeListener(audioCheck);

            ringing.setOnPreferenceChangeListener(ringingCheck);

            if (new LocationHelper(getActivity()).canActivateLocation()) {
                location_enable.setOnPreferenceChangeListener(emptyCheck);
            } else {
                location_enable.setEnabled(false);
                location_enable.setSummary(R.string.location_enable_grant_descr);
            }
        }
    }
}
