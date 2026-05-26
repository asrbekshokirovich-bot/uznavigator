package com.uznavigator.app.data

import com.uznavigator.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

data class GeocodingResult(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

@Serializable
private data class GeocodingResponse(val features: List<Feature> = emptyList())

@Serializable
private data class Feature(
    val place_name: String = "",
    val geometry: Geometry = Geometry(),
    val text: String = ""
)

@Serializable
private data class Geometry(val coordinates: List<Double> = listOf(0.0, 0.0))

object GeocodingService {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String): List<GeocodingResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://api.mapbox.com/geocoding/v5/mapbox.places/$encodedQuery.json" +
                "?country=uz" +
                "&language=en,uz,ru" +
                "&limit=8" +
                "&access_token=${BuildConfig.MAPBOX_PUBLIC_TOKEN}"

        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return@withContext emptyList()

        json.decodeFromString<GeocodingResponse>(body).features.map { feature ->
            val coords = feature.geometry.coordinates
            val parts = feature.place_name.split(", ", limit = 2)
            GeocodingResult(
                name = parts.firstOrNull() ?: feature.text,
                address = if (parts.size > 1) parts[1] else feature.place_name,
                longitude = coords.getOrElse(0) { 69.2401 },
                latitude = coords.getOrElse(1) { 41.2995 }
            )
        }
    }
}
