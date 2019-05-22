package ru.seva.finder;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.SimpleCursorAdapter;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private Cursor cursor;
    private Cursor track_cursor;
    private SQLiteDatabase db;
    private SimpleCursorAdapter adapter;
    private SimpleCursorAdapter track_adapter;
    private final String points_sql = "SELECT history._id, history.phone, phones.name, history.date FROM history LEFT JOIN phones ON history.phone = phones.phone ORDER BY history._id DESC;";
    private final String tracks_sql = "SELECT tracking_table._id, tracking_table.phone, tracking_table.date AS date, phones.name AS name FROM tracking_table LEFT JOIN phones ON tracking_table.phone=phones.phone GROUP BY track_id ORDER BY tracking_table._id DESC;";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        ListView list_points = findViewById(R.id.lvHistory);
        ListView list_tracks = findViewById(R.id.lvTracks);

        //connect to DB
        dBase baseConnect = new dBase(this);
        db = baseConnect.getWritableDatabase();

        //processing list with single points
        String[] fromColons = {"name", "date"};
        int[] toViews = {android.R.id.text1, android.R.id.text2};

        cursor = db.rawQuery(points_sql, null);

        adapter = new SimpleCursorAdapter(this,
                android.R.layout.simple_list_item_2,
                cursor,
                fromColons,
                toViews,
                0);

        //processing list with tracks
        track_cursor = db.rawQuery(tracks_sql, null);

        track_adapter = new SimpleCursorAdapter(this,
                android.R.layout.simple_list_item_2,
                track_cursor,
                fromColons,
                toViews,
                0);  //"fromColons" and "toViews" same as before


        //ViewBinder for replacing null to "unknown"
        class Changer implements SimpleCursorAdapter.ViewBinder {
            public boolean setViewValue(View view, Cursor cursor, int column) {
                if (column == cursor.getColumnIndex("name") && cursor.getString(column) == null) {
                    TextView text = (TextView) view;
                    text.setText(getString(R.string.unknown_number, cursor.getString(1)));  //col index=1 - tracking_table.phone or history.phone
                    return true;
                }
                return false;
            }
        }

        Changer changer = new Changer();
        adapter.setViewBinder(changer);
        track_adapter.setViewBinder(changer);

        list_tracks.setAdapter(track_adapter);
        list_points.setAdapter(adapter);

        TabHost tabHost = findViewById(R.id.tabHostHist);
        tabHost.setup();
        TabHost.TabSpec tabspec = tabHost.newTabSpec("tag1");  //tag needs only for constructor
        tabspec.setContent(R.id.tab_points);
        tabspec.setIndicator(getString(R.string.poits_tab));
        tabHost.addTab(tabspec);

        tabspec = tabHost.newTabSpec("tag2");
        tabspec.setContent(R.id.tab_tracks);
        tabspec.setIndicator(getString(R.string.tracks_tab));
        tabHost.addTab(tabspec);


        list_points.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                showMenu(view, id);
            }
        });

        list_tracks.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                openTrackMenu(view, id);
            }
        });
    }


    protected void onResume() {
        super.onResume();
        updateList(points_sql, adapter);
    }

    protected void onDestroy() {
        super.onDestroy();
        cursor.close();
        track_cursor.close();
        db.close();
    }

    //popup tracks menu
    private int track_id_global;
    private void openTrackMenu(final View v, final long num_id) {
        PopupMenu menu = new PopupMenu(this, v);
        menu.inflate(R.menu.history_track_menu);
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                Cursor query = db.query("tracking_table", null,
                        "_id = ?", new String[] {Long.toString(num_id)},
                        null, null, null);  //get track data
                query.moveToFirst();
                final int track_id = query.getInt(2); //2 - number of column with track number

                switch (item.getItemId()) {
                    case R.id.track_open_id:
                        Intent open_map = new Intent(v.getContext(), MapsActivity.class);
                        open_map.setAction("track");
                        open_map.putExtra("track_id", track_id);
                        startActivity(open_map);
                        query.close();
                        return true;

                    case R.id.track_exp_id:
                        if ((Build.VERSION.SDK_INT >= 23 &&
                                (ContextCompat.checkSelfPermission(getApplicationContext(),
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                                        PackageManager.PERMISSION_GRANTED)) || Build.VERSION.SDK_INT < 23) {
                            writetrack(track_id);
                        } else {
                            track_id_global = track_id;  //global var to save track number after requestPermissions
                            ActivityCompat.requestPermissions(HistoryActivity.this, new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE} , 1);
                        }
                        query.close();
                        return true;

                    case R.id.track_del_id:
                        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                switch(which) {
                                    case DialogInterface.BUTTON_POSITIVE:
                                        db.delete("tracking_table", "track_id = ?", new String[] {Integer.toString(track_id)});
                                        updateList(tracks_sql, track_adapter);
                                        break;
                                    case DialogInterface.BUTTON_NEGATIVE:
                                        //del cancelled, nothing to do
                                        break;
                                }
                            }
                        };
                        AlertDialog.Builder builder2 = new AlertDialog.Builder(v.getContext());
                        builder2.setMessage(R.string.delete_track_warning).setPositiveButton(R.string.yes, dialogClickListener)
                                .setNegativeButton(R.string.no, dialogClickListener).show();

                        query.close();
                        return true;

                    default:
                        query.close();
                        return false;
                }
            }
        });
        menu.show();
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull  String[] permissions, @NonNull int[] grantResults) {
        if ((grantResults.length != 0) &&(grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
            writetrack(track_id_global);
        } else {
            Toast.makeText(HistoryActivity.this, R.string.no_permits_received, Toast.LENGTH_SHORT).show();
        }
    }

    //save track in .GPX file
    private void writetrack(int track_id) {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            File path = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            Cursor name_cursor = db.rawQuery("SELECT date FROM tracking_table WHERE _id = (SELECT MAX(_id) FROM tracking_table WHERE track_id = ?)", new String[] {String.valueOf(track_id)});
            name_cursor.moveToFirst();
            String name = name_cursor.getString(0);
            name_cursor.close();
            File file = new File(path, name + ".gpx");
            try {
                FileOutputStream fos = new FileOutputStream(file);
                String gpx_start = "<?xml version=\"1.0\"?>" +
                        "<gpx version=\"1.0\"" +
                        " creator=\"Finder - https://github.com/Seva-coder/Finder\"" +
                        " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
                        " xmlns=\"http://www.topografix.com/GPX/1/0\"" +
                        " xsi:schemaLocation=\"http://www.topografix.com/GPX/1/0" +
                        " http://www.topografix.com/GPX/1/0/gpx.xsd\"><trk><trkseg>\n";
                fos.write(gpx_start.getBytes());

                Cursor track_cursor = db.rawQuery("SELECT lat, lon, speed, date FROM tracking_table WHERE track_id = ?", new String[] {String.valueOf(track_id)});
                while (track_cursor.moveToNext()) {
                    Double lat = track_cursor.getDouble(0);
                    Double lon = track_cursor.getDouble(1);
                    Double speed = track_cursor.getDouble(2) / 3.6;
                    String date = track_cursor.getString(3);
                    fos.write(String.format(Locale.US, "<trkpt lat=\"%.8f\" lon=\"%.8f\"><time>%s</time><speed>%.1f</speed></trkpt>\n", lat, lon, date, speed).getBytes());
                }
                fos.write("</trkseg></trk></gpx>".getBytes());
                track_cursor.close();
                fos.close();
                Toast.makeText(HistoryActivity.this, R.string.track_saved_message, Toast.LENGTH_LONG).show();
            } catch (IOException e) {
                Toast.makeText(HistoryActivity.this, "failed to save", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(HistoryActivity.this, "Storage not mounted, not saved", Toast.LENGTH_LONG).show();
        }
    }


    //popup menu of single points
    private void showMenu(final View v, final long num_id) {
        PopupMenu menu = new PopupMenu(this, v);
        menu.inflate(R.menu.history_menu);
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                Cursor query = db.query("history", null,
                        "_id = ?", new String[] {Long.toString(num_id)},
                        null, null, null);  //get point data
                query.moveToFirst();
                Double lat = query.getDouble(query.getColumnIndex("lat"));
                Double lon = query.getDouble(query.getColumnIndex("lon"));

                switch (item.getItemId()) {
                    case R.id.internal_map__id:
                        //open built in map
                        Intent start_map = new Intent(v.getContext(), MapsActivity.class);
                        start_map.putExtra("lat", lat);
                        start_map.putExtra("lon", lon);
                        start_map.putExtra("zoom", 15d);
                        Integer acc = query.getInt(query.getColumnIndex("acc"));
                        if (!query.isNull(query.getColumnIndex("acc"))) {
                            start_map.putExtra("accuracy", String.valueOf(acc) + getString(R.string.meters));
                        }
                        start_map.setAction("point");
                        startActivity(start_map);
                        query.close();
                        return true;

                    case R.id.external_map_id:
                        //open in external app
                        Uri intentUri = Uri.parse("geo:" + lat + "," + lon);
                        Intent mapIntent = new Intent(Intent.ACTION_VIEW, intentUri);
                        if (mapIntent.resolveActivity(getPackageManager()) != null) {
                            startActivity(mapIntent);
                        } else {
                            Toast.makeText(HistoryActivity.this, R.string.no_map_app, Toast.LENGTH_LONG).show();
                        }
                        query.close();
                        return true;

                    case R.id.info_id:
                        TextView text = new TextView(v.getContext());
                        text.setTextIsSelectable(true);
                        text.setTextSize(24);
                        text.setPadding(20,5,5,5);

                        String phone = query.getString(query.getColumnIndex("phone"));
                        Cursor query2 = db.query(dBase.PHONES_TABLE_OUT, new String[] {dBase.NAME_COL},
                                "phone = ?", new String[] {phone},
                                null, null, null);  //second query because phone name in another table
                        String name;
                        if (query2.moveToFirst()) {
                            name = query2.getString(query2.getColumnIndex("name"));
                        } else {
                            name = getString(R.string.unknown_number, phone);
                        }
                        text.append(name);
                        text.append("\n");
                        query2.close();

                        String date = query.getString(query.getColumnIndex("date"));
                        text.append(date);
                        text.append("\n");

                        String bat = query.getString(query.getColumnIndex("bat"));
                        if (!query.isNull(query.getColumnIndex("bat"))) {
                            text.append(getString(R.string.battery, bat));
                        }

                        text.append(getString(R.string.latitude, lat));
                        text.append(getString(R.string.longitude, lon));

                        Integer accuracy = query.getInt(query.getColumnIndex("acc"));
                        if (!query.isNull(query.getColumnIndex("acc"))) {
                            text.append(getString(R.string.accuracy, accuracy));
                        }

                        Double altitude = query.getDouble(query.getColumnIndex("height"));
                        if (!query.isNull(query.getColumnIndex("height"))) {
                            text.append(getString(R.string.altitude, altitude));
                        }

                        Float speed = query.getFloat(query.getColumnIndex("speed"));
                        if (!query.isNull(query.getColumnIndex("speed"))) {
                            text.append(getString(R.string.speed, speed));
                        }

                        Float direction = query.getFloat(query.getColumnIndex("direction"));
                        if (!query.isNull(query.getColumnIndex("direction"))) {
                            text.append(getString(R.string.course, direction));
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
                        builder.setView(text).setTitle(R.string.point_data).setPositiveButton("OK", null);

                        AlertDialog dialog = builder.create();
                        dialog.show();
                        query.close();
                        return true;

                    case R.id.del_marker_id:  //delete point with warning
                        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                switch(which) {
                                    case DialogInterface.BUTTON_POSITIVE:
                                        db.delete("history", "_id = ?", new String[] {Long.toString(num_id)});
                                        updateList(points_sql, adapter);
                                        break;
                                    case DialogInterface.BUTTON_NEGATIVE:
                                        break;
                                }
                            }
                        };
                        AlertDialog.Builder builder2 = new AlertDialog.Builder(v.getContext());
                        builder2.setMessage(R.string.delete_warning).setPositiveButton(R.string.yes, dialogClickListener)
                                .setNegativeButton(R.string.no, dialogClickListener).show();
                        query.close();
                        return true;
                    default:
                        query.close();
                        return false;
                }
            }
        });
        menu.show();
    }

    private void updateList(String sql, SimpleCursorAdapter adapt) {
        Cursor temp_cursor = db.rawQuery(sql, null);
        adapt.swapCursor(temp_cursor);
        adapt.notifyDataSetChanged();
    }
}