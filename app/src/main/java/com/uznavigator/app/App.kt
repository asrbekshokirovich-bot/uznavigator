package com.uznavigator.app

import android.app.Application

/**
 * Mapbox access token is configured via AndroidManifest <meta-data
 * MAPBOX_ACCESS_TOKEN .../>, injected from local.properties.
 * Navigation SDK reads the token explicitly in NavigationActivity.
 */
class App : Application()
