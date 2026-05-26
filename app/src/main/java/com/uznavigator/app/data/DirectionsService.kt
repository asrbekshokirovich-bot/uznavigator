package com.uznavigator.app.data

import com.uznavigator.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches a quick driving route preview from Mapbox Directions API.
 * Used to populate the destination card with distance + duration + ETA
 * BEFORE the user commits to navigation.
 */
data class RoutePreview(
    val distanceMeters: Double,
    val durationSeconds: Double
)

@Serializable
private data class DirectionsResponse(
    val routes: List<RouteJson> = emptyList(),
    val code: String = ""
)

@Serializable
private data class RouteJson(
    val distance: Double = 0.0,
    val duration: Double = 0.0
)

object DirectionsService {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun preview(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double
    ): RoutePreview? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.mapbox.com/directions/v5/mapbox/driving/" +
                    "$originLng,$originLat;$destLng,$destLat" +
                    "?overview=false" +
                    "&geometries=geojson" +
                    "&access_token=${BuildConfig.MAPBOX_PUBLIC_TOKEN}"

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null

            val parsed = json.decodeFromString<DirectionsResponse>(body)
            val route = parsed.routes.firstOrNull() ?: return@withContext null

            RoutePreview(
                distanceMeters = route.distance,
                durationSeconds = route.duration
            )
        } catch (e: Exception) {
            null
        }
    }
}
