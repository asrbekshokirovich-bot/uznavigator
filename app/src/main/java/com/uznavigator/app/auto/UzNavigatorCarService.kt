package com.uznavigator.app.auto

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class UzNavigatorCarService : CarAppService() {

    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = NavigationSession()
}

class NavigationSession : Session() {
    override fun onCreateScreen(intent: Intent) = SearchCarScreen(carContext)
}
