package ru.seva.finder;


import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

public class MapsActivity extends AppCompatActivity {

    MapView map = null;
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);

        setContentView(R.layout.activity_open_map);

        map = (MapView) findViewById(R.id.map2);
        map.setTileSource(TileSourceFactory.MAPNIK);

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
        String accuracy = intent.getStringExtra("accuracy");
        mapController.setZoom(intent.getDoubleExtra("zoom", 15.0d));


        GeoPoint startPoint = new GeoPoint(intent.getDoubleExtra("lat", 0d), intent.getDoubleExtra("lon", 0d));
        mapController.setCenter(startPoint);
        Marker startMarker = new Marker(map);
        startMarker.setPosition(startPoint);
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        map.getOverlays().add(startMarker);
        startMarker.setTitle(accuracy);

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
