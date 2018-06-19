package ru.seva.finder;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.IBinder;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
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

    dBase baseConnect;
    SQLiteDatabase db;
    WifiManager wifi;
    scan_ready wifiReceiver;

    public static final String PHONES_TABLE = "phones";
    public static final String PHONES_COL = "phone";
    public static final String SEARCH_CYCLES = "cycles";
    public static final int SEARCH_CYCLES_DEFAULT = 3;
    public static final String WIFI_TIMEOUT = "timeout";
    public static final int WIFI_TIMEOUT_DEFAULT = 7;
    public static final String MACS_NUMBER = "mac_numb";
    public static final int MACS_NUMBER_DEFAULT = 10;

    ArrayList<String> phones = new ArrayList<>();


    @Override
    public void onCreate() {

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String phone_number = intent.getStringExtra("phone_number");

        baseConnect = new dBase(this);
        db = baseConnect.getReadableDatabase();

        //проверка на вхождение
        Cursor cursor_check = db.query(PHONES_TABLE,
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
                s.start();
            }

        } else {
            if(!search_active) {  //в случае если до этого никто не присылал SMS с поиском, офайем этот сервис
                cursor_check.close();
                db.close();
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
                    macs.add("" + wifi_net.level);
                }
            }
        }
    }


    public class Searcher extends Thread {
        StringBuilder sms_answer = new StringBuilder("");
        boolean wifiWasEnabled = false;
        int count = 0;

        public void run() {
            wifi = (WifiManager) getApplicationContext().getSystemService(WifiManager.class);
            if (wifi.isWifiEnabled()) {
                wifiWasEnabled = true;
            } else {
                wifiWasEnabled = false;
                wifi.setWifiEnabled(true);
            }

            sPref = getSharedPreferences(getString(R.string.preferences_file), MODE_PRIVATE);
            //циклическое сканирование
            for(int i = 0; i < sPref.getInt(SEARCH_CYCLES, SEARCH_CYCLES_DEFAULT); i++) {
                wifi.startScan();
                try {
                    Thread.sleep(1000 * sPref.getInt(WIFI_TIMEOUT, WIFI_TIMEOUT_DEFAULT));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }

            //вернём wifi как было
            if (!wifiWasEnabled) {
                wifi.setWifiEnabled(false);
            }

            TelephonyManager tm = (TelephonyManager) getApplicationContext().getSystemService(TelephonyManager.class);

            try {
                List<CellInfo> net_info = tm.getAllCellInfo();  //права проверены в main activity
                for (CellInfo info : net_info) {
                    if (info.isRegistered()) {
                        if (info instanceof CellInfoWcdma) {
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
                        }
                        if (info instanceof CellInfoLte) {
                            String mcc = String.valueOf(((CellInfoLte) info).getCellIdentity().getMcc());
                            String mnc = String.valueOf(((CellInfoLte) info).getCellIdentity().getMnc());
                            String tac = String.valueOf(((CellInfoLte) info).getCellIdentity().getTac());  //для LTE вроде LAC=0
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
                            //ДОПИСАТЬ MCC!!!!
                            sms_answer.append("cdma\nMNC");
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
            }
            catch (NullPointerException|SecurityException e) {
                //всё из-за getAllCellInfo
            }

            //добавим заряд батареи
            BatteryManager bm = (BatteryManager) getSystemService(BatteryManager.class);
            int batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            sms_answer.append("bat:");
            sms_answer.append(String.valueOf(batLevel));
            sms_answer.append("%\n");


            for (String item : macs) {
                if (count < 2 * sPref.getInt(MACS_NUMBER, MACS_NUMBER_DEFAULT)) {
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
        SmsManager sManager = SmsManager.getDefault();
        ArrayList<String> parts = sManager.divideMessage(answer.toString());
        for (String number : phones) {
            sManager.sendMultipartTextMessage(number, null, parts, null,null);
        }
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
