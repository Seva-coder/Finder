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
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;

import java.util.ArrayList;
import java.util.List;


public class WifiSearch extends Service {
    public WifiSearch() {
    }

    private SharedPreferences sPref;
    private LocationHelper locationHelper;
    private final ArrayList<String> macs = new ArrayList<>();

    private boolean search_active = false;

    private WifiManager wifi;
    private scan_ready wifiReceiver;

    private static final String SEARCH_CYCLES = "cycles";
    private static final String SEARCH_CYCLES_DEFAULT = "3";
    private static final String WIFI_TIMEOUT = "timeout";
    private static final String WIFI_TIMEOUT_DEFAULT = "7";
    private static final String MACS_NUMBER = "mac_numb";
    private static final String MACS_NUMBER_DEFAULT = "10";

    private final ArrayList<String> phones = new ArrayList<>();

    private Notification notification;
    private int id;


    @Override
    public void onCreate() {
        wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        sPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        locationHelper = new LocationHelper(getApplicationContext());
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String phone_number = intent.getStringExtra("phone_number");

        dBase baseConnect = new dBase(this);
        SQLiteDatabase db = baseConnect.getReadableDatabase();

        //check numbers to the list of trusted entry
        Cursor cursor_check = db.query(dBase.PHONES_TABLE_IN,
                new String[] {dBase.PHONES_COL},
                dBase.PHONES_COL + "=?",
                new String[] {phone_number},
                null, null, null);

        if (cursor_check.moveToFirst()) {
            //add phone number to mailing list if it is trusted. And start wifi-search (if not running)
            if (!phones.contains(phone_number)) {
                phones.add(phone_number);
            }
            if(!search_active) {  //start thread with searching
                search_active = true;

                // Activate location if it's not enabled
                // (only if permission to write secure settings is granted via ADB)
                locationHelper.activateLocation(false);

                wifiReceiver = new scan_ready();
                registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
                Searcher s = new Searcher();
                s.start();
            }

            Cursor name_curs = db.query(dBase.PHONES_TABLE_IN, new String[] {dBase.NAME_COL},
                    "phone = ?", new String[] {phone_number},
                    null, null, null);
            String name;
            name = (name_curs.moveToFirst()) ? (name_curs.getString(name_curs.getColumnIndex(dBase.NAME_COL))) : (phone_number);
            name_curs.close();

            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), MainActivity.COMMON_NOTIF_CHANNEL)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(getString(R.string.wifi_processed))
                    .setContentText(getString(R.string.from, name))
                    .setAutoCancel(true);

            notification = builder.build();  //will be used also at service stop
            id = sPref.getInt("notification_id", 2);
            startForeground(id, notification);
            sPref.edit().putInt("notification_id", id+1).apply();
        } else {
            if(!search_active) {  //stop service in case of wrong number
                cursor_check.close();
                db.close();
                stopSelf();
            }
        }
        cursor_check.close();
        db.close();

        return START_REDELIVER_INTENT;
    }


    class scan_ready extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            List<ScanResult> wifi_list = wifi.getScanResults();
            for (ScanResult wifi_net : wifi_list) {
                if (!macs.contains(wifi_net.BSSID.replace(":", ""))) {
                    macs.add(wifi_net.BSSID.replace(":", ""));
                    macs.add(String.valueOf(wifi_net.level));
                }
            }
        }
    }


    public class Searcher extends Thread {
        final StringBuilder sms_answer = new StringBuilder("");
        boolean wifiWasEnabled = false;
        int count = 0;

        public void run() {

            if (wifi.isWifiEnabled()) {
                wifiWasEnabled = true;
            } else {
                wifiWasEnabled = false;
                wifi.setWifiEnabled(true);
            }

            //cyclic scanning
            for(int i = 0; i < Integer.valueOf(sPref.getString(SEARCH_CYCLES, SEARCH_CYCLES_DEFAULT)); i++) {
                wifi.startScan();
                try {
                    Thread.sleep(1000 * Integer.valueOf(sPref.getString(WIFI_TIMEOUT, WIFI_TIMEOUT_DEFAULT)));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }

            //return wifi state as it was
            if (!wifiWasEnabled) {
                wifi.setWifiEnabled(false);
            }

            TelephonyManager tm = (TelephonyManager) getApplicationContext().getSystemService(TELEPHONY_SERVICE);

            try {
                List<CellInfo> net_info = tm.getAllCellInfo();
                if (net_info == null) {
                    //case of old android
                    CellLocation cell = tm.getCellLocation();
                    String mcc_mnc = tm.getNetworkOperator();
                    if (cell instanceof GsmCellLocation && !mcc_mnc.isEmpty()) {
                        String mcc = mcc_mnc.substring(0, 3);
                        String mnc = mcc_mnc.substring(3);
                        String lac = String.valueOf(((GsmCellLocation) cell).getLac());
                        String cid = String.valueOf(((GsmCellLocation) cell).getCid());
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
                    if (cell instanceof CdmaCellLocation && !mcc_mnc.isEmpty()) {
                        String mcc = mcc_mnc.substring(0, 3);
                        String sysId = String.valueOf(((CdmaCellLocation) cell).getSystemId());
                        String netId = String.valueOf(((CdmaCellLocation) cell).getNetworkId());
                        String baseStId = String.valueOf(((CdmaCellLocation) cell).getBaseStationId());
                        sms_answer.append("cdma\nMCC");
                        sms_answer.append(mcc);
                        sms_answer.append("\nMNC");
                        sms_answer.append(sysId);  //in compliance with Google geo api
                        sms_answer.append("\nLAC");
                        sms_answer.append(netId);  //in compliance with Google geo api
                        sms_answer.append("\nCID");
                        sms_answer.append(baseStId);  //in compliance with Google geo api
                        sms_answer.append("\n");
                    }
                } else {
                    //for newer devices, net_info not null
                    for (CellInfo info : net_info) {
                        if (info.isRegistered()) {
                            if (info instanceof CellInfoWcdma) {  //strange thing - 17s API method return 18s API object? (CellInfoWcdma)???
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
                                    //old method
                                    String mcc_mnc = tm.getNetworkOperator();
                                    if (!mcc_mnc.isEmpty()) {
                                        String mcc = mcc_mnc.substring(0, 3);
                                        String mnc = mcc_mnc.substring(3);
                                        //case with 2 sim with different providers not checked
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
                                //String tac = String.valueOf(((CellInfoLte) info).getCellIdentity().getTac());  //google api for LTE use LAC=0
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
                                sms_answer.append(mcc);  //such net type has not been tested! reliability not guaranteed (no such nets in Russia)
                                sms_answer.append("\nMNC");
                                sms_answer.append(sysId);  //in compliance with Google geo api
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

                IntentFilter bat_filt= new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent battery = getApplicationContext().registerReceiver(null, bat_filt);
                int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                float batteryPct = level / (float)scale;
                String batLevel = String.valueOf(Math.round(batteryPct*100));
                sms_answer.append("bat:");
                sms_answer.append(batLevel);
                sms_answer.append("%\n");
                sms_answer.append("ts:");
                sms_answer.append(System.currentTimeMillis());
                sms_answer.append("\n");

            }
            catch (NullPointerException|SecurityException e) {

            }


            for (String item : macs) {
                //x2 because 1 mac address takes 2 strings (with signal strength)
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

    private void start_send(StringBuilder answer) {   //mailing to all who requested (only from trusted)
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
        stopForeground(true);
        NotificationManager nManage = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nManage.notify(id, notification);  //now notification stay after service stop
    }

    @Override
    public void onDestroy() {
        // Deactivate location if it was not enabled
        // (only if permission to write secure settings is granted via ADB)
        locationHelper.deactivateLocation();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
