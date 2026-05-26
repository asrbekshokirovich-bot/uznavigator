package com.uznavigator.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.locationcomponent.location
import com.uznavigator.app.data.DirectionsService
import com.uznavigator.app.data.GeocodingResult
import com.uznavigator.app.data.GeocodingService
import com.uznavigator.app.data.RoutePreview
import com.uznavigator.app.databinding.ActivityMainBinding
import com.uznavigator.app.ui.SearchAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val searchAdapter = SearchAdapter { onDestinationSelected(it) }
    private var selectedDestination: GeocodingResult? = null
    private var searchJob: Job? = null
    private var previewJob: Job? = null
    private var hasCenteredOnUser = false
    private var lastKnownLocation: Location? = null

    // Fallback camera position: Tashkent (used only if user location is unavailable)
    private val tashkentFallback = CameraOptions.Builder()
        .center(Point.fromLngLat(69.2401, 41.2995))
        .zoom(11.5)
        .build()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            onLocationGranted()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupMap()
        setupSearch()
        setupButtons()
        checkLocationPermission()
    }

    private fun setupMap() {
        binding.mapView.getMapboxMap().apply {
            loadStyleUri(Style.MAPBOX_STREETS)
            // Don't set camera here — wait for user location or fallback after permission flow
        }
    }

    private fun setupSearch() {
        binding.searchResultsList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = searchAdapter
        }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                binding.clearSearchBtn.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                triggerSearch(query)
            }
        })

        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                triggerSearch(binding.searchInput.text.toString(), immediate = true)
                hideKeyboard()
                true
            } else false
        }

        binding.clearSearchBtn.setOnClickListener {
            binding.searchInput.setText("")
            binding.searchResultsContainer.visibility = View.GONE
        }
    }

    private fun triggerSearch(query: String, immediate: Boolean = false) {
        searchJob?.cancel()
        if (query.length < 2) {
            binding.searchResultsContainer.visibility = View.GONE
            return
        }
        searchJob = lifecycleScope.launch {
            if (!immediate) delay(350)
            val results = runCatching { GeocodingService.search(query) }
                .getOrElse { emptyList() }
            searchAdapter.submit(results)
            binding.searchResultsContainer.visibility =
                if (results.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun onDestinationSelected(result: GeocodingResult) {
        selectedDestination = result
        binding.searchInput.setText(result.name)
        binding.searchResultsContainer.visibility = View.GONE
        binding.destinationCard.visibility = View.VISIBLE
        binding.destinationName.text = result.name
        binding.destinationAddress.text = result.address

        // Reset preview UI; show progress while fetching
        binding.routePreviewRow.visibility = View.GONE
        binding.previewProgress.visibility = View.VISIBLE
        binding.navigateBtn.isEnabled = false

        hideKeyboard()

        binding.mapView.getMapboxMap().flyTo(
            CameraOptions.Builder()
                .center(Point.fromLngLat(result.longitude, result.latitude))
                .zoom(14.5)
                .build()
        )

        // Fetch route preview (distance + duration + ETA) using user's last known location
        fetchRoutePreview(result)
    }

    private fun fetchRoutePreview(dest: GeocodingResult) {
        previewJob?.cancel()
        previewJob = lifecycleScope.launch {
            // Try to use last known location; if null, request a fresh one
            val origin = lastKnownLocation ?: requestFreshLocation()

            if (origin == null) {
                // Couldn't get location — still allow navigation (NavigationActivity will retry)
                binding.previewProgress.visibility = View.GONE
                binding.navigateBtn.isEnabled = true
                return@launch
            }

            val preview = DirectionsService.preview(
                originLat = origin.latitude,
                originLng = origin.longitude,
                destLat = dest.latitude,
                destLng = dest.longitude
            )

            binding.previewProgress.visibility = View.GONE
            binding.navigateBtn.isEnabled = true

            if (preview != null) {
                renderPreview(preview)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFreshLocation(): Location? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            kotlinx.coroutines.suspendCancellableCoroutine<Location?> { cont ->
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { loc -> if (cont.isActive) cont.resumeWith(Result.success(loc)) }
                    .addOnFailureListener { if (cont.isActive) cont.resumeWith(Result.success(null)) }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun renderPreview(preview: RoutePreview) {
        binding.routePreviewRow.visibility = View.VISIBLE
        binding.previewDistance.text = formatDistance(preview.distanceMeters)
        binding.previewDuration.text = formatDuration(preview.durationSeconds)
        binding.previewArrival.text = formatArrivalTime(preview.durationSeconds)
    }

    private fun formatDistance(meters: Double): String = when {
        meters >= 1000 -> "${"%.0f".format(meters / 1000)} km"
        else -> "${meters.roundToInt()} m"
    }

    private fun formatDuration(seconds: Double): String {
        val mins = (seconds / 60).roundToInt()
        return when {
            mins >= 60 -> "${mins / 60}h ${mins % 60}m"
            else -> "${mins} min"
        }
    }

    private fun formatArrivalTime(secondsFromNow: Double): String {
        val arrivalMs = System.currentTimeMillis() + (secondsFromNow * 1000).toLong()
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(arrivalMs))
    }

    private fun setupButtons() {
        binding.navigateBtn.setOnClickListener {
            val dest = selectedDestination ?: return@setOnClickListener
            val intent = Intent(this, NavigationActivity::class.java).apply {
                putExtra(NavigationActivity.EXTRA_DEST_LAT, dest.latitude)
                putExtra(NavigationActivity.EXTRA_DEST_LNG, dest.longitude)
                putExtra(NavigationActivity.EXTRA_DEST_NAME, dest.name)
            }
            startActivity(intent)
        }

        binding.closeDestinationBtn.setOnClickListener {
            binding.destinationCard.visibility = View.GONE
            binding.searchInput.setText("")
            selectedDestination = null
        }

        binding.myLocationFab.setOnClickListener {
            flyToUserLocation(zoomLevel = 16.0)
        }
    }

    // ── Location handling ──────────────────────────────────────────

    private fun checkLocationPermission() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            onLocationGranted()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            // While waiting for permission, show fallback view
            binding.mapView.getMapboxMap().setCamera(tashkentFallback)
        }
    }

    private fun onLocationGranted() {
        enableLocationPuck()
        flyToUserLocation(zoomLevel = 15.5, fallbackToTashkent = true)
    }

    private fun enableLocationPuck() {
        binding.mapView.location.apply {
            enabled = true
            pulsingEnabled = true
        }
    }

    @SuppressLint("MissingPermission")
    private fun flyToUserLocation(zoomLevel: Double = 15.5, fallbackToTashkent: Boolean = false) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (fallbackToTashkent) binding.mapView.getMapboxMap().setCamera(tashkentFallback)
            return
        }

        // Try fast cached location first
        fusedLocationClient.lastLocation
            .addOnSuccessListener { loc: Location? ->
                if (loc != null) {
                    animateToLocation(loc, zoomLevel)
                } else {
                    // No cached location — request a fresh one
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { fresh ->
                            if (fresh != null) animateToLocation(fresh, zoomLevel)
                            else if (fallbackToTashkent && !hasCenteredOnUser) {
                                binding.mapView.getMapboxMap().setCamera(tashkentFallback)
                            }
                        }
                }
            }
            .addOnFailureListener {
                if (fallbackToTashkent && !hasCenteredOnUser) {
                    binding.mapView.getMapboxMap().setCamera(tashkentFallback)
                }
            }
    }

    private fun animateToLocation(loc: Location, zoomLevel: Double) {
        hasCenteredOnUser = true
        lastKnownLocation = loc
        binding.mapView.getMapboxMap().flyTo(
            CameraOptions.Builder()
                .center(Point.fromLngLat(loc.longitude, loc.latitude))
                .zoom(zoomLevel)
                .build()
        )
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
    }
}
