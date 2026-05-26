package com.uznavigator.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object NavigationState {

    data class State(
        val isNavigating: Boolean = false,
        val speedLimitKph: Int? = null,
        val currentSpeedKph: Int = 0,
        val nextInstruction: String? = null,
        val distanceToNextMeters: Double = 0.0,
        val etaMinutes: Int = 0,
        val totalDistanceKm: Double = 0.0
    ) {
        val isOverLimit: Boolean
            get() = speedLimitKph != null && currentSpeedKph > speedLimitKph

        val isNearLimit: Boolean
            get() = speedLimitKph != null && currentSpeedKph >= speedLimitKph - 10 && !isOverLimit
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun update(block: State.() -> State) {
        _state.value = _state.value.block()
    }

    fun reset() {
        _state.value = State()
    }
}
