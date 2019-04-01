package ru.seva.finder;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.ViewTreeObserver;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.MapBoxTileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class MapsActivity extends AppCompatActivity {

    MapView map = null;
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);

        setContentView(R.layout.activity_open_map);
        SharedPreferences sPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        map = (MapView) findViewById(R.id.map2);
        Configuration.getInstance().setTileFileSystemCacheMaxBytes(1024*1024*Long.parseLong(sPref.getString("cache_size", "5")));
        Configuration.getInstance().setTileFileSystemCacheTrimBytes(512*1024*Long.parseLong(sPref.getString("cache_size", "5")));  // trim storage to ~50% from max after oversize limit


        if (sPref.getBoolean("satellite", false)) {
            final MapBoxTileSource tileSource = new MapBoxTileSource();
            tileSource.retrieveAccessToken(this);
            tileSource.retrieveMapBoxMapId(this);
            map.setTileSource(tileSource);
        } else {
            map.setTileSource(TileSourceFactory.MAPNIK);
        }


        map.setBuiltInZoomControls(true);
        map.setMultiTouchControls(true);
        IMapController mapController = map.getController();

        CopyrightOverlay copyOverlay = new CopyrightOverlay(this);
        copyOverlay.setAlignRight(true);
        map.getOverlays().add(copyOverlay);

        ScaleBarOverlay scaleBar= new ScaleBarOverlay(map);
        scaleBar.setUnitsOfMeasure(ScaleBarOverlay.UnitsOfMeasure.metric);
        scaleBar.setScaleBarOffset(getResources().getDisplayMetrics().widthPixels/2, 50);
        scaleBar.setCentred(true);
        map.getOverlays().add(scaleBar);

        GpsMyLocationProvider gpsProvider = new GpsMyLocationProvider(this.getBaseContext());
        MyLocationNewOverlay myLoc = new MyLocationNewOverlay(gpsProvider, map);
        myLoc.setDrawAccuracyEnabled(true);
        map.getOverlays().add(myLoc);

        CompassOverlay compasOver = new CompassOverlay(this.getBaseContext(), map);
        compasOver.enableCompass();
        map.getOverlays().add(compasOver);


        Intent intent = this.getIntent();
        mapController.setZoom(intent.getDoubleExtra("zoom", 15.0d));
        String act = intent.getAction();
        Double lat, lon;
        if (act.equals("track")) {
            int track_id = intent.getIntExtra("track_id", 0);
            dBase baseConnect = new dBase(this);
            SQLiteDatabase db = baseConnect.getWritableDatabase();
            Cursor query =  db.query("tracking_table", new String[] {"_id", "lat", "lon", "speed", "date"}, "track_id = ?", new String[] {String.valueOf(track_id)}, null, null, "_id ASC");


            Cursor last_point =  db.rawQuery("SELECT lat, lon FROM tracking_table WHERE _id = (SELECT MAX(_id) FROM tracking_table WHERE track_id = ?)", new String[] {String.valueOf(track_id)});
            last_point.moveToFirst();
            lat = last_point.getDouble(0);
            lon = last_point.getDouble(1);
            last_point.close();

            //Привет костыль! а всё из-за того, что BoundBox не работает так, как должен, привет разрабам. Зумимся на последнюю точку
            mapController.setCenter(new GeoPoint(lat, lon));
            mapController.setZoom(15d);
            List<GeoPoint> track = new ArrayList<>();

            while (query.moveToNext()) {
                GeoPoint gp = new GeoPoint(query.getDouble(1), query.getDouble(2));
                track.add(gp);

                Marker marker = new Marker(map);
                marker.setPosition(gp);
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                map.getOverlays().add(marker);
                marker.setTitle(getString(R.string.date_time, query.getString(4), query.getFloat(3)));
            }
            query.close();


            Polyline line = new Polyline(map);
            line.setColor(Color.BLUE);
            line.setInfoWindow(null);  // уберём всплывающий поп-ап с трека
            line.setGeodesic(true);  //отобразим реальные прямые ("большие дуги")
            line.setPoints(track);
            map.getOverlays().add(line);
        } else {
            String accuracy = intent.getStringExtra("accuracy");
            GeoPoint startPoint = new GeoPoint(intent.getDoubleExtra("lat", 0d), intent.getDoubleExtra("lon", 0d));
            mapController.setCenter(startPoint);
            Marker startMarker = new Marker(map);
            startMarker.setPosition(startPoint);
            startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            map.getOverlays().add(startMarker);
            startMarker.setTitle(accuracy);
        }

    }

    public void onResume() {
        super.onResume();
        map.onResume(); //needed for compass, my location overlays, v6.0.0 and up
    }

    public void onPause() {
        super.onPause();
        map.onPause();  //needed for compass, my location overlays, v6.0.0 and up
    }
}
