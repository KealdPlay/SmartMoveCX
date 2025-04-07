package com.example.smartmovecx;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMapClickListener, GoogleMap.OnMapLongClickListener {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final long LOCATION_UPDATE_INTERVAL = 2000; // 2 seconds

    private EditText txtLat, txtLng;
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private Handler locationUpdateHandler;
    private Runnable locationUpdateRunnable;
    private ImageButton menuButton;
    private ImageView logoImageView;

    // Path A and Path B variables
    private List<LatLng> pathAPoints;
    private List<LatLng> pathBPoints;
    private Polyline pathAPolyline;
    private Polyline pathBPolyline;
    private List<Marker> pathAMarkers;
    private List<Marker> pathBMarkers;
    private String currentRoute = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize text fields
        txtLat = findViewById(R.id.txtLat);
        txtLng = findViewById(R.id.txtLng);

        // Initialize menu button and logo
        menuButton = findViewById(R.id.menu_button);
      //  logoImageView = findViewById(R.id.logo_image);
        setupMenuButton();

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize paths and markers
        pathAPoints = new ArrayList<>();
        pathBPoints = new ArrayList<>();
        pathAMarkers = new ArrayList<>();
        pathBMarkers = new ArrayList<>();

        // Load Route 50 points from txt file
        loadRoute50Points();

        // Define dummy Path B points (can be replaced with actual route)
        loadRoute25Points();

        // Initialize location update handler
        locationUpdateHandler = new Handler(Looper.getMainLooper());

        // Get map fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

    }

    private void setupMenuButton() {
        menuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRouteMenu(v);
            }
        });
    }

    private void showRouteMenu(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.route_menu, popup.getMenu());

        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.route_50) {
                    currentRoute = getString(R.string.route_50);
                    updateRouteVisibility(currentRoute);
                    return true;
                } else if (item.getItemId() == R.id.route_b) {
                    currentRoute = getString(R.string.route_b);
                    updateRouteVisibility(currentRoute);
                    return true;
                }
                return false;
            }
        });
        popup.show();
    }

    private void updateRouteVisibility(String selectedRoute) {
        if (mMap == null) return;

        // Hide all routes and markers first
        if (pathAPolyline != null) pathAPolyline.setVisible(false);
        if (pathBPolyline != null) pathBPolyline.setVisible(false);

        // Hide all markers
        for (Marker marker : pathAMarkers) {
            marker.setVisible(false);
        }

        for (Marker marker : pathBMarkers) {
            marker.setVisible(false);
        }

        // Show the selected route
        if (selectedRoute.equals(getString(R.string.route_50))) {
            if (pathAPolyline != null) pathAPolyline.setVisible(true);
            for (Marker marker : pathAMarkers) {
                marker.setVisible(true);
            }

            // Center the map on Route 50's first point if available
            if (!pathAPoints.isEmpty()) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pathAPoints.get(0), 13));
            }
        } else if (selectedRoute.equals(getString(R.string.route_b))) {
            if (pathBPolyline != null) pathBPolyline.setVisible(true);
            for (Marker marker : pathBMarkers) {
                marker.setVisible(true);
            }

            // Center the map on Route B's first point if available
            if (!pathBPoints.isEmpty()) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pathBPoints.get(0), 13));
            }
        }
    }

    private void loadRoute50Points() {
        try {
            InputStream inputStream = getAssets().open("r50_paradas.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;

            while ((line = reader.readLine()) != null) {
                String[] coordinates = line.split(",");
                if (coordinates.length >= 2) {
                    try {
                        double lat = Double.parseDouble(coordinates[0].trim());
                        double lng = Double.parseDouble(coordinates[1].trim());
                        pathAPoints.add(new LatLng(lat, lng));
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error cargando la información de la ruta 50", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadRoute25Points() {
        try {
            InputStream inputStream = getAssets().open("r25_paradas.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;

            while ((line = reader.readLine()) != null) {
                String[] coordinates = line.split(",");
                if (coordinates.length >= 2) {
                    try {
                        double lat = Double.parseDouble(coordinates[0].trim());
                        double lng = Double.parseDouble(coordinates[1].trim());
                        pathAPoints.add(new LatLng(lat, lng));
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error cargando la información de la ruta 25", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMapClickListener(this);
        mMap.setOnMapLongClickListener(this);

        // Check and request location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            setupMap();
        }

        // Draw Path A (Route 50) and Path B
        drawPath(pathAPoints, true);
        drawPath(pathBPoints, false);

        // Add markers for stops
        addRouteMarkers();

        // Initialize with the default route
        updateRouteVisibility(currentRoute);

        // Set map bounds to cover both routes
        LatLng southwest = new LatLng(21.634775200235094, -102.86666648574204);
        LatLng northeast = new LatLng(22.461368865192668, -101.82028662287628);
        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(new com.google.android.gms.maps.model.LatLngBounds(southwest, northeast), 0));

    }

    private void addRouteMarkers() {
        // Add markers for Path A (Route 50)
        for (LatLng point : pathAPoints) {
            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(point)
                    .title("Parada de Ruta 50")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
            if (marker != null) {
                pathAMarkers.add(marker);
            }
        }

        // Add markers for Path B
        for (LatLng point : pathBPoints) {
            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(point)
                    .title("Parada de Ruta B")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
            if (marker != null) {
                pathBMarkers.add(marker);
            }
        }
    }

    private void drawPath(List<LatLng> points, boolean isPathA) {
        if (points.isEmpty() || mMap == null) return;

        PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(points)
                .width(12)
                .color(isPathA ? getResources().getColor(android.R.color.holo_blue_dark) :
                        getResources().getColor(android.R.color.holo_green_dark))
                .geodesic(true);

        if (isPathA) {
            if (pathAPolyline != null) {
                pathAPolyline.remove();
            }
            pathAPolyline = mMap.addPolyline(polylineOptions);
        } else {
            if (pathBPolyline != null) {
                pathBPolyline.remove();
            }
            pathBPolyline = mMap.addPolyline(polylineOptions);
        }
    }

    @SuppressLint("MissingPermission")
    private void setupMap() {
        mMap.setMyLocationEnabled(true);

        // Start periodic location updates
        startLocationUpdates();

        // Get initial location
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15));
                            updateLocationDisplay(location);
                        } else {
                            // Default location if unable to get current location - adjusted to be near Route 50
                            LatLng mexicoDefault = new LatLng(21.88234, -102.28259);
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mexicoDefault, 13f));
                        }
                    }
                });
    }

    private void startLocationUpdates() {
        locationUpdateRunnable = new Runnable() {
            @SuppressLint("MissingPermission")
            @Override
            public void run() {
                fusedLocationClient.getLastLocation()
                        .addOnSuccessListener(MainActivity.this, new OnSuccessListener<Location>() {
                            @Override
                            public void onSuccess(Location location) {
                                if (location != null) {
                                    updateLocationDisplay(location);
                                }
                                // Schedule the next update
                                locationUpdateHandler.postDelayed(locationUpdateRunnable, LOCATION_UPDATE_INTERVAL);
                            }
                        });
            }
        };

        // Start the initial update
        locationUpdateHandler.post(locationUpdateRunnable);
    }

    private void updateLocationDisplay(Location location) {
        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());

        // Update UI with current coordinates
        txtLat.setText(String.valueOf(location.getLatitude()));
        txtLng.setText(String.valueOf(location.getLongitude()));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupMap();
            } else {
                Toast.makeText(this, "Permisos de localización denegados.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onMapClick(@NonNull LatLng latLng) {
        txtLat.setText("" + latLng.latitude);
        txtLng.setText("" + latLng.longitude);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onMapLongClick(@NonNull LatLng latLng) {
        txtLat.setText("" + latLng.latitude);
        txtLng.setText("" + latLng.longitude);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop location updates when app is in background
        if (locationUpdateHandler != null && locationUpdateRunnable != null) {
            locationUpdateHandler.removeCallbacks(locationUpdateRunnable);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Restart location updates when app is in foreground
        if (locationUpdateHandler != null && locationUpdateRunnable != null &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationUpdateHandler.post(locationUpdateRunnable);
        }
    }
}