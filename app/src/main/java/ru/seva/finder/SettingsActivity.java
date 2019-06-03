package ru.seva.finder;


import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.widget.Toast;


public class SettingsActivity extends AppCompatPreferenceActivity {

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
            Preference remote = findPreference("remote");

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
        }
    }
}
