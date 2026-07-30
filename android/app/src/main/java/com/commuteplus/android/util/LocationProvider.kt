package com.commuteplus.android.util

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class UserLocation(val lat: Double, val lng: Double)

/**
 * Provides the user's current GPS location.
 * Uses FusedLocationProviderClient for a reliable, power-efficient result.
 *
 * The calling screen must handle runtime permission (ACCESS_FINE_LOCATION) before calling.
 */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * Get the user's current location.
     * Returns null if location is unavailable (permission not granted, GPS off, etc.).
     *
     * IMPORTANT: Caller must have already checked/requested location permissions.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): UserLocation? {
        return suspendCancellableCoroutine { continuation ->
            val cancellationToken = CancellationTokenSource()

            fusedClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token,
            ).addOnSuccessListener { location ->
                if (location != null) {
                    continuation.resume(UserLocation(location.latitude, location.longitude))
                } else {
                    continuation.resume(null)
                }
            }.addOnFailureListener {
                continuation.resume(null)
            }

            continuation.invokeOnCancellation {
                cancellationToken.cancel()
            }
        }
    }
}
