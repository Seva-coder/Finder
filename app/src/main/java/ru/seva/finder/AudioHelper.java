package ru.seva.finder;


import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Build;


/**
 * class for sound control (enabling/disabling/ringing)
 */
class AudioHelper {


    private Context context;
    private AudioManager aMan;
    private NotificationManager nManage;
    private Ringtone ringtone;
    private SharedPreferences prefs;

    private int oldVolume;


    AudioHelper(Context context, SharedPreferences prefs) {
        this.context = context;
        this.prefs = prefs;
        aMan = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        nManage = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private void mute() {
        if ((Build.VERSION.SDK_INT >= 23 && nManage.isNotificationPolicyAccessGranted()) || (Build.VERSION.SDK_INT < 23)) {
            aMan.setRingerMode(AudioManager.RINGER_MODE_SILENT);
        }
    }

    private void unmute(int oldRingerMode) {
        if ((Build.VERSION.SDK_INT >= 23 && nManage.isNotificationPolicyAccessGranted()) || (Build.VERSION.SDK_INT < 23)) {
            aMan.setRingerMode(oldRingerMode);
        }
    }

    private void setVolume(int level) {
        if ((Build.VERSION.SDK_INT >= 23 && nManage.isNotificationPolicyAccessGranted()) || (Build.VERSION.SDK_INT < 23)) {
            aMan.setStreamVolume(AudioManager.STREAM_RING, level, 0);
        }
    }

    /**
     * disable sound from requests on 1 sec
     */
    void pauseSound() {
        if (prefs.getBoolean("disable_sound", false)) {
            SoundPauseThread pauseThread = new SoundPauseThread();
            pauseThread.start();
        }
    }

    /**
     * disable sound from tracking SMS on 1 sec
     */
    void pauseTrackingSound() {
        if (prefs.getBoolean("disable_tracking_sound", false)) {
            SoundPauseThread pauseThread = new SoundPauseThread();
            pauseThread.start();
        }
    }

    /**
     * start ringing at 90% volume, even if sound was muted.
     * after stop - set old volume
     */
    void startRinging() {
        unmute(AudioManager.RINGER_MODE_NORMAL);
        ringtone = RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));

        int maxVol = aMan.getStreamMaxVolume(AudioManager.STREAM_RING);
        oldVolume = aMan.getStreamVolume(AudioManager.STREAM_RING);
        setVolume((int) (maxVol * 0.9f));

        ringtone.play();
    }


    void stopRinging() {
        ringtone.stop();
        setVolume(oldVolume);
    }


    /**
     * Thread for sound muting without blocking ui thread
     */
    private class SoundPauseThread extends Thread {
        private static final long pause = 1000L;

        public void run() {

            //state of sound before changing
            int oldRingerMode = aMan.getRingerMode();

            mute();
            try {
                Thread.sleep(pause);
            }
            catch (Exception e) {
                //nothing should terminate our thread
            }
            unmute(oldRingerMode);
        }
    }
}
