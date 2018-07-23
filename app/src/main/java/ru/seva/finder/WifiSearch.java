package ru.seva.finder;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.AudioManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.NeighboringCellInfo;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;

import java.util.ArrayList;
import java.util.List;


public class WifiSearch extends Service {
    public WifiSearch() {
    }

    List<ScanResult> wifi_list;
    SharedPreferences sPref;
    ArrayList<String> macs = new ArrayList<>();

    boolean search_active = false;
    boolean sound_was_enabled;

    dBase baseConnect;
    SQLiteDatabase db;
    WifiManager wifi;
    scan_ready wifiReceiver;

    public static final String PHONES_COL = "phone";
    public static final String SEARCH_CYCLES = "cycles";
    public static final String SEARCH_CYCLES_DEFAULT = "3";
    public static final String WIFI_TIMEOUT = "timeout";
    public static final String WIFI_TIMEOUT_DEFAULT = "7";
    public static final String MACS_NUMBER = "mac_numb";
    public static final String MACS_NUMBER_DEFAULT = "10";

    ArrayList<String> phones = new ArrayList<>();


    @Override
    public void onCreate() {
        wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        sPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String phone_number = intent.getStringExtra("phone_number");
        sound_was_enabled = intent.getBooleanExtra("sound_was_normal", true);

        baseConnect = new dBase(this);
        db = baseConnect.getReadableDatabase();

        //проверка номера на вхождение
        Cursor cursor_check = db.query(dBase.PHONES_TABLE_IN,
                new String[] {PHONES_COL},
                PHONES_COL + "=?",
                new String[] {phone_number},
                null, null, null);

        if (cursor_check.moveToFirst()) {
            //добавляем номер в список рассылки, если он в базе. И заводим wifi-поиск (если ещё не запущен)
            if (!phones.contains(phone_number)) {
                phones.add(phone_number);
            }
            if(!search_active) {  //запуск потока с поиском и создание ресивера
                search_active = true;
                wifiReceiver = new scan_ready();
                registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
                Searcher s = new Searcher();
                s.start();  //отдельный поток, тк юзает паузы
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext())
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(getString(R.string.wifi_processed))
                    .setContentText(getString(R.string.from) + phone_number)
                    .setAutoCancel(true);  //подумать над channel id
            Notification notification = builder.build();
            NotificationManager nManage = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            int id = sPref.getInt("notification_id", 0);
            nManage.notify(id, notification);
            sPref.edit().putInt("notification_id", id+1).apply();
        } else {
            if(!search_active) {  //в случае если до этого никто не присылал SMS с поиском, офайем этот сервис
                cursor_check.close();
                db.close();
                if (sPref.getBoolean("disable_sound", false) && sound_was_enabled) {
                    AudioManager aMan = (AudioManager) getApplication().getSystemService(Context.AUDIO_SERVICE);
                    aMan.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                }
                stopSelf();
            }
            //иначе продолжаем работу сервиса
        }
        cursor_check.close();
        db.close();

        return START_REDELIVER_INTENT;
    }


    class scan_ready extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            wifi_list = wifi.getScanResults();
            for (ScanResult wifi_net : wifi_list) {
                if (!macs.contains(wifi_net.BSSID.replace(":", ""))) {
                    macs.add(wifi_net.BSSID.replace(":", ""));
                    macs.add(String.valueOf(wifi_net.level));
                }
            }
        }
    }


    public class Searcher extends Thread {
        StringBuilder sms_answer = new StringBuilder("");
        boolean wifiWasEnabled = false;
        int count = 0;

        public void run() {

            if (wifi.isWifiEnabled()) {
                wifiWasEnabled = true;
            } else {
                wifiWasEnabled = false;
                wifi.setWifiEnabled(true);
            }

            //циклическое сканирование
            for(int i = 0; i < Integer.valueOf(sPref.getString(SEARCH_CYCLES, SEARCH_CYCLES_DEFAULT)); i++) {
                wifi.startScan();
                try {
                    Thread.sleep(1000 * Integer.valueOf(sPref.getString(WIFI_TIMEOUT, WIFI_TIMEOUT_DEFAULT)));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }

            //вернём wifi как было
            if (!wifiWasEnabled) {
                wifi.setWifiEnabled(false);
            }

            TelephonyManager tm = (TelephonyManager) getApplicationContext().getSystemService(TELEPHONY_SERVICE);

            try {
                List<CellInfo> net_info = tm.getAllCellInfo();  //права проверены в main activity
                for (CellInfo info : net_info) {
                    if (info.isRegistered()) {
                        if (info instanceof CellInfoWcdma) {  //непонятный момент - метод 17го API возвращает объект 18го API (CellInfoWcdma)???
                            if (Build.VERSION.SDK_INT >= 18) {
                                String mcc = String.valueOf(((CellInfoWcdma) info).getCellIdentity().getMcc());
                                String mnc = String.valueOf(((CellInfoWcdma) info).getCellIdentity().getMnc());
                                String lac = String.valueOf(((CellInfoWcdma) info).getCellIdentity().getLac());
                                String cid = String.valueOf(((CellInfoWcdma) info).getCellIdentity().getCid());
                                sms_answer.append("wcdma\nMCC");
                                sms_answer.append(mcc);
                                sms_answer.append("\nMNC");
                                sms_answer.append(mnc);
                                sms_answer.append("\nLAC");
                                sms_answer.append(lac);
                                sms_answer.append("\nCID");
                                sms_answer.append(cid);
                                sms_answer.append("\n");
                            } else {
                                //олдовыми методами
                                String mcc_mnc = tm.getNetworkOperator();
                                if (!mcc_mnc.isEmpty()) {
                                    String mcc = mcc_mnc.substring(0, 3);
                                    String mnc = mcc_mnc.substring(3);
                                    //непонятно что если две SIM'ки с разными операторами!!!
                                    List<NeighboringCellInfo> net_list = tm.getNeighboringCellInfo();
                                    for (NeighboringCellInfo net : net_list) {
                                        String lac = String.valueOf(net.getLac());
                                        String cid = String.valueOf(net.getCid());
                                        sms_answer.append("wcdma\nMCC");
                                        sms_answer.append(mcc);
                                        sms_answer.append("\nMNC");
                                        sms_answer.append(mnc);
                                        sms_answer.append("\nLAC");
                                        sms_answer.append(lac);
                                        sms_answer.append("\nCID");
                                        sms_answer.append(cid);
                                        sms_answer.append("\n");
                                    }
                                }
                            }
                        }
                        if (info instanceof CellInfoLte) {
                            String mcc = String.valueOf(((CellInfoLte) info).getCellIdentity().getMcc());
                            String mnc = String.valueOf(((CellInfoLte) info).getCellIdentity().getMnc());
                            String tac = String.valueOf(((CellInfoLte) info).getCellIdentity().getTac());  //для google api LTE вроде LAC=0
                            String cid = String.valueOf(((CellInfoLte) info).getCellIdentity().getCi());
                            sms_answer.append("lte\nMCC");
                            sms_answer.append(mcc);
                            sms_answer.append("\nMNC");
                            sms_answer.append(mnc);
                            sms_answer.append("\nLAC");
                            sms_answer.append("0");
                            sms_answer.append("\nCID");
                            sms_answer.append(cid);
                            sms_answer.append("\n");
                        }
                        if (info instanceof CellInfoGsm) {
                            String mcc = String.valueOf(((CellInfoGsm) info).getCellIdentity().getMcc());
                            String mnc = String.valueOf(((CellInfoGsm) info).getCellIdentity().getMnc());
                            String lac = String.valueOf(((CellInfoGsm) info).getCellIdentity().getLac());
                            String cid = String.valueOf(((CellInfoGsm) info).getCellIdentity().getCid());
                            sms_answer.append("gsm\nMCC");
                            sms_answer.append(mcc);
                            sms_answer.append("\nMNC");
                            sms_answer.append(mnc);
                            sms_answer.append("\nLAC");
                            sms_answer.append(lac);
                            sms_answer.append("\nCID");
                            sms_answer.append(cid);
                            sms_answer.append("\n");
                        }
                        if (info instanceof CellInfoCdma) {
                            String baseStation = String.valueOf(((CellInfoCdma) info).getCellIdentity().getBasestationId());
                            String lat = String.valueOf(((CellInfoCdma) info).getCellIdentity().getLatitude());
                            String lon = String.valueOf(((CellInfoCdma) info).getCellIdentity().getLongitude());
                            String netId = String.valueOf(((CellInfoCdma) info).getCellIdentity().getNetworkId());
                            String sysId = String.valueOf(((CellInfoCdma) info).getCellIdentity().getSystemId());
                            String mcc = "";
                            if (tm.getSimState() == TelephonyManager.SIM_STATE_READY) {
                                String mcc_mnc = tm.getSimOperator();
                                mcc = mcc_mnc.substring(0, 3);
                            }
                            sms_answer.append("cdma\nMCC");
                            sms_answer.append(mcc);  //необходимо тестирование в сети CDMA! неясно, на сколько это надёжно
                            sms_answer.append("\nMNC");
                            sms_answer.append(sysId);
                            sms_answer.append("\nLAC");
                            sms_answer.append(netId);
                            sms_answer.append("\nCID:");
                            sms_answer.append(baseStation);
                            sms_answer.append("\nlat");
                            sms_answer.append(lat);
                            sms_answer.append("\nlon");
                            sms_answer.append(lon);
                            sms_answer.append("\n");
                        }
                    }
                }

                IntentFilter bat_filt= new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent battery = getApplicationContext().registerReceiver(null, bat_filt);
                int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                float batteryPct = level / (float)scale;
                String batLevel = String.valueOf(Math.round(batteryPct*100));
                sms_answer.append("bat:");
                sms_answer.append(batLevel);
                sms_answer.append("%\n");
            }
            catch (NullPointerException|SecurityException e) {
                //всё из-за getAllCellInfo и getIntExtra
            }


            for (String item : macs) {
                // x2 так так 1 мак = 2 строчки (+уровень сигнала)
                if (count < 2 * Integer.valueOf(sPref.getString(MACS_NUMBER, MACS_NUMBER_DEFAULT))) {
                    sms_answer.append(item);
                    sms_answer.append("\n");
                    count += 1;
                } else break;
            }

            start_send(sms_answer);
            unregisterReceiver(wifiReceiver);
            stopSelf();
        }
    }

    void start_send(StringBuilder answer) {   //рассылка всем запросившим
        if ((Build.VERSION.SDK_INT >= 23 &&
                (getApplicationContext().checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED)) ||
                Build.VERSION.SDK_INT < 23) {
            SmsManager sManager = SmsManager.getDefault();
            ArrayList<String> parts = sManager.divideMessage(answer.toString());
            if (parts.size() != 0) {
                for (String number : phones) {
                    sManager.sendMultipartTextMessage(number, null, parts, null,null);
                }
            } else {
                for (String number : phones) {
                    sManager.sendTextMessage(number, null, "net info unavailable", null,null);
                }
            }
        }

        //вернуть звук, если был включен
        if (sPref.getBoolean("disable_sound", false) && sound_was_enabled) {
            AudioManager aMan = (AudioManager) getApplication().getSystemService(Context.AUDIO_SERVICE);
            aMan.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        }
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
