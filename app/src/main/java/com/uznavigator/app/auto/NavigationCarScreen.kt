package com.uznavigator.app.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.uznavigator.app.NavigationState
import kotlinx.coroutines.*
import kotlin.math.roundToInt

class NavigationCarScreen(carContext: CarContext) : Screen(carContext) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val navigationManager = carContext.getCarService(NavigationManager::class.java)

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                navigationManager.setNavigationManagerCallback(object : NavigationManagerCallback {
                    override fun onStopNavigation() {
                        // User pressed stop from car — pop back to search
                        NavigationState.reset()
                        screenManager.popToRoot()
                    }
                    override fun onAutoDriveEnabled() {}
                })
                navigationManager.navigationStarted()
                observeState()
            }
            override fun onDestroy(owner: LifecycleOwner) {
                scope.cancel()
                if (NavigationState.state.value.isNavigating) {
                    navigationManager.navigationEnded()
                }
            }
        })
    }

    private fun observeState() {
        scope.launch {
            NavigationState.state.collect { invalidate() }
        }
    }

    override fun onGetTemplate(): Template {
        val state = NavigationState.state.value

        if (!state.isNavigating) {
            return MessageTemplate.Builder("Waiting for navigation…")
                .setHeaderAction(Action.APP_ICON)
                .setActionStrip(
                    ActionStrip.Builder()
                        .addAction(Action.Builder()
                            .setTitle("Search")
                            .setOnClickListener { screenManager.pushForResult(SearchCarScreen(carContext)) {} }
                            .build())
                        .build()
                )
                .build()
        }

        val distance = formatDistance(state.distanceToNextMeters)
        val instruction = state.nextInstruction ?: "Continue"
        val speedLimit = state.speedLimitKph
        val eta = formatEta(state.etaMinutes)

        val step = Step.Builder(instruction)
            .setManeuver(
                Maneuver.Builder(Maneuver.TYPE_STRAIGHT).build()
            )
            .build()

        val distanceObj = Distance.create(
            state.distanceToNextMeters.coerceAtLeast(0.0),
            Distance.UNIT_METERS
        )

        val routingInfo = RoutingInfo.Builder()
            .setCurrentStep(step, distanceObj)
            .build()

        val travelEstimate = TravelEstimate.Builder(
            Distance.create(state.totalDistanceKm * 1000, Distance.UNIT_METERS),
            DateTimeWithZone.create(
                System.currentTimeMillis() + state.etaMinutes * 60_000L,
                java.util.TimeZone.getDefault()
            )
        ).build()

        return NavigationTemplate.Builder()
            .setNavigationInfo(routingInfo)
            .setDestinationTravelEstimate(travelEstimate)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.Builder()
                        .setTitle("Stop")
                        .setOnClickListener {
                            NavigationState.reset()
                            screenManager.popToRoot()
                        }
                        .build())
                    .build()
            )
            .setMapActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.PAN)
                    .build()
            )
            .build()
    }

    private fun formatDistance(meters: Double): String = when {
        meters >= 1000 -> "${"%.1f".format(meters / 1000)} km"
        else -> "${meters.roundToInt()} m"
    }

    private fun formatEta(minutes: Int): String =
        if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "$minutes min"
}
