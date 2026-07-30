package com.commuteplus.geocoding

import com.commuteplus.domain.LatLng
import com.commuteplus.domain.Place
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Address search / autocomplete powered by Photon (OSM-based geocoder).
 *
 * Photon can be self-hosted (recommended for production — same OSM data the app already uses)
 * or the public instance at photon.komoot.io can be used for development (rate-limited).
 *
 * This is REAL geocoding against OSM data — not a mock.
 *
 * Self-hosting Photon:
 *   docker run -p 2322:2322 komoot/photon:latest
 *   (with a pre-built India extract, or import from the same .osm.pbf)
 */
class PhotonGeocoder(
    private val baseUrl: String = "https://photon.komoot.io", // default: public instance for dev
    private val httpClient: HttpClient,
) {

    private val log = LoggerFactory.getLogger(PhotonGeocoder::class.java)

    // Bangalore center — used to bias results toward the city
    private val biasCenterLat = 12.9716
    private val biasCenterLng = 77.5946

    /**
     * Search for places matching the query, biased toward Bangalore.
     * Returns real places from OpenStreetMap data.
     */
    suspend fun search(query: String, limit: Int = 8, locale: String = "en"): List<Place> {
        try {
            val response: HttpResponse = httpClient.get("$baseUrl/api") {
                parameter("q", query)
                parameter("lat", biasCenterLat)
                parameter("lon", biasCenterLng)
                parameter("limit", limit)
                parameter("lang", locale)
                // Bias to Bangalore bbox
                parameter("bbox", "77.40,12.75,77.80,13.15")
            }

            val body = response.body<String>()
            return parsePhotonResponse(body)
        } catch (e: Exception) {
            log.error("Photon geocoding error for query='$query': ${e.message}")
            return emptyList()
        }
    }

    /**
     * Reverse geocode: given coordinates, return the nearest named place.
     */
    suspend fun reverse(lat: Double, lng: Double): Place? {
        try {
            val response: HttpResponse = httpClient.get("$baseUrl/reverse") {
                parameter("lat", lat)
                parameter("lon", lng)
                parameter("limit", 1)
            }

            val body = response.body<String>()
            val results = parsePhotonResponse(body)
            return results.firstOrNull()
        } catch (e: Exception) {
            log.error("Photon reverse geocoding error: ${e.message}")
            return null
        }
    }

    private fun parsePhotonResponse(body: String): List<Place> {
        val json = Json { ignoreUnknownKeys = true }
        val root = json.parseToJsonElement(body).jsonObject
        val features = root["features"]?.jsonArray ?: return emptyList()

        return features.mapNotNull { feature ->
            val featureObj = feature.jsonObject
            val geometry = featureObj["geometry"]?.jsonObject ?: return@mapNotNull null
            val coords = geometry["coordinates"]?.jsonArray ?: return@mapNotNull null
            val properties = featureObj["properties"]?.jsonObject ?: return@mapNotNull null

            val lng = coords[0].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
            val lat = coords[1].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
            val name = properties["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val osmId = properties["osm_id"]?.jsonPrimitive?.contentOrNull ?: "unknown"

            // Build localized names from Photon's response if available
            val localizedNames = mutableMapOf<String, String>()
            properties["name:kn"]?.jsonPrimitive?.contentOrNull?.let { localizedNames["kn"] = it }
            properties["name:hi"]?.jsonPrimitive?.contentOrNull?.let { localizedNames["hi"] = it }

            Place(
                id = "osm:$osmId",
                name = name,
                localizedNames = localizedNames,
                location = LatLng(lat, lng),
            )
        }
    }
}
