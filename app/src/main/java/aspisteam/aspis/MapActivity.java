package aspisteam.aspis;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;
import com.karumi.dexter.listener.single.PermissionListener;

import java.util.ArrayList;
import java.util.List;

import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.geom.LookAt;
import gov.nasa.worldwind.geom.Offset;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.globe.BasicElevationCoverage;
import gov.nasa.worldwind.layer.BackgroundLayer;
import gov.nasa.worldwind.layer.BlueMarbleLandsatLayer;
import gov.nasa.worldwind.layer.Layer;
import gov.nasa.worldwind.layer.RenderableLayer;
import gov.nasa.worldwind.ogc.WmsLayer;
import gov.nasa.worldwind.ogc.WmsLayerConfig;
import gov.nasa.worldwind.render.ImageSource;
import gov.nasa.worldwind.shape.Placemark;
import gov.nasa.worldwind.shape.PlacemarkAttributes;

public class MapActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "ASPISLOG";
    private WorldWindow wwd;
    RelativeLayout wwdContainer;

    LocationManager locationManager;
    double longitudeGPS, latitudeGPS;

    SensorManager mSensorManager;

    private DatabaseReference mDatabase;


    private final LocationListener locationListenerBEST = new LocationListener() {

        public void onLocationChanged(Location location) {
            longitudeGPS = location.getLongitude();
            latitudeGPS = location.getLatitude();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.i(TAG,longitudeGPS+" "+latitudeGPS);
                    drawMe(new Position(latitudeGPS,longitudeGPS,0));
                }
            });
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {
            Log.i(TAG,"On status changed : "+s);
        }

        @Override
        public void onProviderEnabled(String s) {
            Log.i(TAG,"onProviderEnabled "+s);

        }

        @Override
        public void onProviderDisabled(String s) {
            Log.i(TAG,"onProviderDisabled "+s);
        }
    };

    private float currentDegree = 0f;
    private boolean shouldRotateScreen = false;

    ImageView compass;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        mDatabase = FirebaseDatabase.getInstance().getReference("fire_spots");

        Dexter.withActivity(this)
                .withPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {
                        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                        startReadingLocation();
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {

                    }
                })
                .check();

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        compass = (ImageView) findViewById(R.id.mycompass);

        wwdContainer = (RelativeLayout) findViewById(R.id.mapview);

        // Create the WorldWindow (a GLSurfaceView) which displays the globe.
        this.wwd = new WorldWindow(this);

        WmsLayerConfig config = new WmsLayerConfig();
        config.serviceAddress = "https://worldwind27.arc.nasa.gov/wms/virtualearth";
        config.wmsVersion =  "1.1.1"; // NEO server works best with WMS 1.1.1
        config.layerNames = "ve"; // Sea surface temperature (MODIS)
        WmsLayer layer = new WmsLayer(new Sector().setFullSphere(), 1, config); // 1km resolution

        // Add the WMS layer to the WorldWindow.
        wwd.getLayers().addLayer(layer);

        //wwd.getLayers().addLayer(new BackgroundLayer());
        //wwd.getLayers().addLayer(new BlueMarbleLandsatLayer());
        this.wwd.getGlobe().getElevationModel().addCoverage(new BasicElevationCoverage());

        wwdContainer.addView(wwd);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(MapActivity.this, "Location Permissions Not Set", Toast.LENGTH_SHORT).show();
        } else {
            startReadingLocation();
            readFireSpots();
        }

    }

    private void readFireSpots(){
        mDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot noteDataSnapshot : dataSnapshot.getChildren()) {
                    FireSpot fireSpot = noteDataSnapshot.getValue(FireSpot.class);
                    Log.i("FIRE",fireSpot.toString());

                    drawFireSpot(fireSpot.getPosition());
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.i("ERROR",databaseError.getMessage());
            }
        });
    }

    private void drawFireSpot(Position position){
        String fire_spots = "fire_spots";

        RenderableLayer placemarksLayer = new RenderableLayer(fire_spots);

        wwd.getLayers().addLayer(placemarksLayer);

        Placemark myPosition = new Placemark(
                position,
                PlacemarkAttributes
                        .createWithImage(ImageSource.fromResource(R.drawable.flame)).setImageScale(1)
                        .setImageOffset(Offset.bottomCenter()), "Fire Spot");

        placemarksLayer.addRenderable(myPosition);
        wwd.requestRender();
        wwd.requestRedraw();
    }

    private void startReadingLocation() {
        if (isLocationEnabled()) {
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            criteria.setAltitudeRequired(true);
            criteria.setBearingRequired(true);
            criteria.setCostAllowed(true);
            criteria.setPowerRequirement(Criteria.POWER_HIGH);
            String provider = locationManager.getBestProvider(criteria, true);
            if (provider != null) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG,"LOCATION PERMISSION ERROR");
                }else{
                    Log.i(TAG,"STARTING LOCATION");
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3 * 1000, 5, locationListenerBEST);
                }
            }
        }else{
            Log.i(TAG,"LOCATION NOT ENABLED");
            Toast.makeText(MapActivity.this, "Location Is Not Enabled",Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isLocationEnabled() {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    private void drawMe(Position position){

        String placemarks = "Me";

        RenderableLayer placemarksLayer = new RenderableLayer(placemarks);

        for(int i=0; i<wwd.getLayers().count(); i++){
            Layer l = wwd.getLayers().getLayer(i);
            Log.i(TAG,l.getDisplayName());
            if(l.getDisplayName().equals(placemarks)){
                wwd.getLayers().removeLayer(l);
            }
        }

        wwd.getLayers().addLayer(placemarksLayer);

        Placemark myPosition = new Placemark(
                position,
                PlacemarkAttributes
                        .createWithImage(ImageSource.fromResource(R.drawable.poi_empty)).setImageScale(.1)
                        .setImageOffset(Offset.bottomCenter()), "Me");

        placemarksLayer.addRenderable(myPosition);

         LookAt lookAt = new LookAt().set(position.latitude, position.longitude, 1, WorldWind.ABSOLUTE, 1000 /*range*/, currentDegree /*heading*/, 0 /*tilt*/, 0 /*roll*/);
         wwd.getNavigator().setAsLookAt(wwd.getGlobe(), lookAt);
    }

    private void rotateMap(float degrees){
        LookAt lookAt = new LookAt(
                wwd.getNavigator().getLatitude(),
                wwd.getNavigator().getLongitude(),
                1,
                WorldWind.ABSOLUTE,
                500,
                currentDegree,
                0,
                0);
        wwd.getNavigator().setAsLookAt(wwd.getGlobe(), lookAt);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // for the system's orientation sensor registered listeners
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION), 48, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // to stop the listener and save battery
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        // get the angle around the z-axis rotated
        float degree = Math.round(sensorEvent.values[0]);
        if(Math.abs(currentDegree - degree) > 20){
            currentDegree = degree;
            Log.i(TAG,degree+"");
            if(shouldRotateScreen){
                rotateMap(degree);
            }else{
                //degrees.setText(degree+"");
                compass.setRotation(degree);
            }
        }


    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}
