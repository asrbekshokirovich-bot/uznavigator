package com.uznavigator.app

import android.location.Location
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.MapboxNavigationProvider
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.state.NavigationCameraState
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineOptions
import com.uznavigator.app.databinding.ActivityNavigationBinding
import kotlin.math.roundToInt
import com.mapbox.api.directions.v5.models.RouteOptions

class NavigationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DEST_LAT = "dest_lat"
        const val EXTRA_DEST_LNG = "dest_lng"
        const val EXTRA_DEST_NAME = "dest_name"
    }

    private lateinit var binding: ActivityNavigationBinding
    private lateinit var mapboxNavigation: MapboxNavigation
    private lateinit var navigationCamera: NavigationCamera
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource
    private lateinit var routeLineApi: MapboxRouteLineApi
    private lateinit var routeLineView: MapboxRouteLineView
    private val navigationLocationProvider = NavigationLocationProvider()
    private var isCameraTracking = true
    private var routeRequested = false
    private var pendingDestination: Point? = null

    // ── Observers ──────────────────────────────────────────────────────────

    private val locationObserver = object : LocationObserver {
        override fun onNewRawLocation(rawLocation: Location) {}
        override fun onNewLocationMatcherResult(result: LocationMatcherResult) {
            navigationLocationProvider.changePosition(result.enhancedLocation, result.keyPoints)
            viewportDataSource.onLocationChanged(result.enhancedLocation)
            viewportDataSource.evaluate()

            // Request the route from the first accurate SDK location instead of
            // lastLocation, which can be stale or null and produce a route from
            // the wrong origin (e.g. the Tashkent-center fallback).
            val dest = pendingDestination
            if (!routeRequested && dest != null) {
                routeRequested = true
                val origin = Point.fromLngLat(
                    result.enhancedLocation.longitude,
                    result.enhancedLocation.latitude
                )
                requestRoute(origin, dest)
            }

            // Navigation SDK 2.x: SpeedLimit.speedKmph is already in km/h
            val limitKph: Int? = result.speedLimit?.speedKmph
            val speedKph = (result.enhancedLocation.speed * 3.6).roundToInt()

            runOnUiThread {
                binding.speedLimitView.updateSpeedLimit(limitKph)
                binding.speedLimitView.updateCurrentSpeed(speedKph)
            }

            NavigationState.update {
                copy(speedLimitKph = limitKph, currentSpeedKph = speedKph)
            }
        }
    }

    private val routeProgressObserver = RouteProgressObserver { progress ->
        viewportDataSource.onRouteProgressChanged(progress)
        viewportDataSource.evaluate()

        val distM: Double = (progress.currentLegProgress?.currentStepProgress?.distanceRemaining ?: 0f).toDouble()
        val instruction = progress.bannerInstructions?.primary()?.text()
        val durationSec = progress.durationRemaining
        val totalM = progress.distanceRemaining

        runOnUiThread {
            binding.distanceText.text = formatDistance(distM)
            binding.streetNameText.text = instruction ?: ""
            binding.etaText.text = formatEta(durationSec)
            binding.totalDistanceText.text = formatDistance(totalM.toDouble())
        }

        NavigationState.update {
            copy(
                nextInstruction = instruction,
                distanceToNextMeters = distM,
                etaMinutes = (durationSec / 60).roundToInt(),
                totalDistanceKm = totalM / 1000.0
            )
        }
    }

    private val routesObserver = RoutesObserver { result ->
        routeLineApi.setNavigationRoutes(result.navigationRoutes) { value ->
            binding.mapView.getMapboxMap().getStyle { style ->
                routeLineView.renderRouteDrawData(style, value)
            }
        }
        NavigationState.update { copy(isNavigating = result.navigationRoutes.isNotEmpty()) }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val destLat = intent.getDoubleExtra(EXTRA_DEST_LAT, 0.0)
        val destLng = intent.getDoubleExtra(EXTRA_DEST_LNG, 0.0)
        val destName = intent.getStringExtra(EXTRA_DEST_NAME) ?: ""

        binding.streetNameText.text = "To: $destName"

        pendingDestination = Point.fromLngLat(destLng, destLat)

        initNavigation()
        setupCamera()
        setupRouteLines()
        setupButtons()
    }

    override fun onStart() {
        super.onStart()
        mapboxNavigation.registerLocationObserver(locationObserver)
        mapboxNavigation.registerRoutesObserver(routesObserver)
        mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
        mapboxNavigation.startTripSession()
    }

    override fun onStop() {
        super.onStop()
        mapboxNavigation.unregisterLocationObserver(locationObserver)
        mapboxNavigation.unregisterRoutesObserver(routesObserver)
        mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
    }

    override fun onDestroy() {
        super.onDestroy()
        routeLineApi.cancel()
        NavigationState.reset()
        MapboxNavigationProvider.destroy()
    }

    // ── Setup ──────────────────────────────────────────────────────────────

    private fun initNavigation() {
        mapboxNavigation = MapboxNavigationProvider.create(
            NavigationOptions.Builder(this)
                .accessToken(BuildConfig.MAPBOX_PUBLIC_TOKEN)
                .build()
        )
        binding.mapView.getMapboxMap().loadStyleUri(Style.TRAFFIC_DAY)
        binding.mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            enabled = true
        }
    }

    private fun setupCamera() {
        viewportDataSource = MapboxNavigationViewportDataSource(binding.mapView.getMapboxMap())

        // ── Google Maps / Yandex Navigator-style camera ──────────────────────
        // Key insights from researching both apps:
        //   1. CLOSE zoom (~17.5) — street-level detail, ~150m visible ahead
        //   2. 3D pitch ~55° — forward-leaning driving perspective
        //   3. Puck in lower-third of screen — road ahead is visible above
        //   4. Bearing follows TRAVEL DIRECTION (auto-rotates map as you turn)
        //   5. NO huge "look-ahead" — focus is on user position + immediate next 100-200m
        val density = resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toDouble()

        // Padding: larger TOP than BOTTOM pushes the puck DOWN into the lower-third.
        // Bottom padding only avoids the speed-limit widget + stop button area.
        viewportDataSource.followingPadding = com.mapbox.maps.EdgeInsets(
            /* top    */ dp(380f),   // BIG → forces puck to lower 1/3 of screen
            /* left   */ dp(40f),
            /* bottom */ dp(140f),   // just above the bottom UI elements
            /* right  */ dp(40f)
        )
        viewportDataSource.overviewPadding = com.mapbox.maps.EdgeInsets(
            dp(160f), dp(40f), dp(160f), dp(40f)
        )

        // FORCE close-up street-level zoom (overrides Mapbox's "smart" auto-zoom
        // which was showing 2km ahead instead of street detail)
        viewportDataSource.followingZoomPropertyOverride(17.5)

        // FORCE 3D forward tilt for that Google Maps driving feel
        viewportDataSource.followingPitchPropertyOverride(55.0)

        // (Bearing is NOT overridden — Mapbox auto-rotates to user heading,
        //  which matches Google Maps "heading-up" behavior)

        viewportDataSource.evaluate()

        navigationCamera = NavigationCamera(
            binding.mapView.getMapboxMap(),
            binding.mapView.camera,
            viewportDataSource
        )
        navigationCamera.requestNavigationCameraToFollowing()

        navigationCamera.registerNavigationCameraStateChangeObserver { state ->
            isCameraTracking = state == NavigationCameraState.FOLLOWING
            binding.recenterFab.visibility =
                if (!isCameraTracking) View.VISIBLE else View.GONE
        }
    }

    private fun setupRouteLines() {
        val options = MapboxRouteLineOptions.Builder(this)
            .withRouteLineBelowLayerId("road-label")
            .build()
        routeLineApi = MapboxRouteLineApi(options)
        routeLineView = MapboxRouteLineView(options)
    }

    private fun setupButtons() {
        binding.stopNavBtn.setOnClickListener {
            mapboxNavigation.setNavigationRoutes(emptyList())
            finish()
        }
        binding.recenterFab.setOnClickListener {
            navigationCamera.requestNavigationCameraToFollowing()
        }
    }

    // ── Navigation logic ───────────────────────────────────────────────────

    private fun requestRoute(origin: Point, destination: Point) {
        val routeOptions = RouteOptions.builder()
            .applyDefaultNavigationOptions()
            .applyLanguageAndVoiceUnitOptions(this)
            .coordinatesList(listOf(origin, destination))
            .alternatives(true)
            .build()

        mapboxNavigation.requestRoutes(routeOptions, object : NavigationRouterCallback {
            override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: RouterOrigin) {
                if (routes.isNotEmpty()) {
                    mapboxNavigation.setNavigationRoutes(routes)
                }
            }
            override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                runOnUiThread {
                    binding.streetNameText.text = getString(R.string.route_failed)
                }
            }
            override fun onCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) {}
        })
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun formatDistance(meters: Double): String = when {
        meters >= 1000 -> "${"%.1f".format(meters / 1000)} km"
        else -> "${meters.roundToInt()} m"
    }

    private fun formatEta(seconds: Double): String {
        val mins = (seconds / 60).roundToInt()
        return if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "$mins min"
    }
}
