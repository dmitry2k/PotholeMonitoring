package com.example.dmitry.gmap;

import android.graphics.Color;
import android.os.Environment;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolylineOptions;

import android.media.ToneGenerator;
import android.media.AudioManager;
import android.view.WindowManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final float SPEED_PASS = 1.5f;
    private static final float HIGH_PASS = 4.5f;
    private static final float BUMP_FILTER = 6.460231f;

    private GoogleMap mMap;

    private LocationManager locationManager;
    private LatLng prevPosition;
    private boolean isFirstLocation = true;

    private SensorManager sensorManager;
    private Sensor sensorLinAccel;
    private boolean hasAnomaly = false;
    private boolean hasBump = false;

    private File sdFile;
    private BufferedWriter bw;
    private final String DIR_SD = "MyFiles";
    private final String FILENAME_SD = "data_fileSD";
    private String info;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //получить уведомление, когда карта готова к использованию
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        //получаем GPS
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        //получаем акселерометр
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorLinAccel = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        // проверяем доступность SD
        if (!Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED)) {
            return;
        }
        // получаем путь к SD
        File sdPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        // добавляем свой каталог к пути
        sdPath = new File(sdPath.getAbsolutePath() + "/" + DIR_SD);
        // создаем каталог
        sdPath.mkdirs();
        // формируем объект File, который содержит путь к файлу
        sdFile = new File(sdPath, FILENAME_SD);
        try {
            // открываем поток для записи
            bw = new BufferedWriter(new FileWriter(sdFile, true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        //начинаем собирать данные с акселерометра и GPS
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        sensorManager.registerListener(listener, sensorLinAccel, SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //прекращаем собирать данные с акселерометра и GPS
        locationManager.removeUpdates(locationListener); //
        sensorManager.unregisterListener(listener); //
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        //получаем карту
        mMap = googleMap;
        mMap.setMyLocationEnabled(true);
    }

    private LocationListener locationListener = new LocationListener() {

        @Override
        public void onLocationChanged(Location location) {
            LatLng curPosition = new LatLng(location.getLatitude() , location.getLongitude());
            if (isFirstLocation) {
                mMap.moveCamera(CameraUpdateFactory.zoomTo(18));
                isFirstLocation = false;
                hasAnomaly = false;
                hasBump = false;
            }
            else {
                if (location.getSpeed() > SPEED_PASS) {
                    int quality;
                    if (hasBump) {
                        quality = 2;
                        //сигнал, что на участке дороги была яма
                        ToneGenerator toneG = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
                        toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200);
                        mMap.addPolyline(new PolylineOptions().add(prevPosition, curPosition).color(Color.RED));
                    }
                    else if (hasAnomaly) {
                        quality = 1;
                        mMap.addPolyline(new PolylineOptions().add(prevPosition, curPosition).color(Color.YELLOW));
                    }
                    else {
                        quality = 0;
                        mMap.addPolyline(new PolylineOptions().add(prevPosition, curPosition).color(Color.GREEN));
                    }
                    info = String.format("%1$f %2$f %3$f %4$f %5$d\n", prevPosition.latitude, prevPosition.longitude,
                            curPosition.latitude, curPosition.longitude, quality);
                    writeFileSD();
                }
            }
            mMap.animateCamera(CameraUpdateFactory.newLatLng(curPosition));
            prevPosition = curPosition;
            hasAnomaly = false;
            hasBump = false;
        }

        @Override
        public void onProviderDisabled(String provider) { }

        @Override
        public void onProviderEnabled(String provider) { }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}
    };

    void writeFileSD() {
        try {
            // пишем данные
            bw.write(info);
            bw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    SensorEventListener listener = new SensorEventListener() {

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}

        @Override
        public void onSensorChanged(SensorEvent event) {
            float z = Math.abs(event.values[2]); //показания акселерометра по оси Z
            if (z > HIGH_PASS) {
                hasAnomaly = true;
                if (z > BUMP_FILTER)
                    hasBump = true;
            }
        }
    };

}
