package ru.seva.finder;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.util.Log;

/**
 * This class helps to activate and deactivate location
 * if permission to write secure settings is granted via ADB.
 */
class LocationHelper {

    /**
     * Tag for Logging.
     */
    private static final String TAG = "LocationHelper";

    /**
     * The context to use.
     */
    private Context context;

    /**
     * The content resolver for accessing secure settings.
     */
    private ContentResolver contentResolver;

    /**
     * The application preferences.
     */
    private SharedPreferences preferences;

    /**
     * The previous location mode.
     */
    private Integer oldLocationMode = null;

    /**
     * Constructor for LocationHelper.
     *
     * @param context The context which is used to read and write location mode.
     */
    LocationHelper(Context context) {
        this.context = context;
        this.contentResolver = context.getContentResolver();
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    /**
     * Activate location if it's not enabled
     * and save previous state.
     */
    void activateLocation(boolean highAccuracy) {
        if (Build.VERSION.SDK_INT < 19) {
            return;
        }

        if (!preferences.getBoolean("location_enable", false)) {
            return;
        }

        int currentMode;
        Integer newMode = null;

        try {
            currentMode = Settings.Secure.getInt(contentResolver, Settings.Secure.LOCATION_MODE);
        } catch (Exception e) {
            Log.e(TAG, "Can't get location mode", e);
            return;
        }

        if (highAccuracy) {
            if (currentMode != Settings.Secure.LOCATION_MODE_HIGH_ACCURACY) {
                newMode = Settings.Secure.LOCATION_MODE_HIGH_ACCURACY;
            }
        } else {
            if (currentMode == Settings.Secure.LOCATION_MODE_OFF) {
                newMode = Settings.Secure.LOCATION_MODE_BATTERY_SAVING;
            }
        }

        if (newMode == null) {
            return;
        }

        try {
            Settings.Secure.putInt(contentResolver, Settings.Secure.LOCATION_MODE, newMode);
        } catch (Exception e) {
            Log.e(TAG, "Can't set location mode", e);
            return;
        }

        oldLocationMode = currentMode;
    }

    /**
     * Return location mode to previous state.
     */
    void deactivateLocation() {
        if (Build.VERSION.SDK_INT < 19) {
            return;
        }

        if (oldLocationMode == null) {
            return;
        }

        try {
            Settings.Secure.putInt(contentResolver, Settings.Secure.LOCATION_MODE, oldLocationMode);
        } catch (Exception e) {
            Log.e(TAG, "Can't change location mode", e);
        }

        oldLocationMode = null;
    }

    /**
     * Determine if location can be activated automatically.
     *
     * @return If location can be activated.
     */
    boolean canActivateLocation() {
        if (Build.VERSION.SDK_INT < 19) {
            return false;
        }

        String permission = android.Manifest.permission.WRITE_SECURE_SETTINGS;
        int granted = PackageManager.PERMISSION_GRANTED;

        return ContextCompat.checkSelfPermission(context, permission) == granted;
    }
}
