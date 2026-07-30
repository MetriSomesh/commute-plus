package com.commuteplus.android.ui.screens.detail

import androidx.lifecycle.ViewModel
import com.commuteplus.android.data.api.JourneyDto
import com.commuteplus.android.data.session.JourneySessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Detail screen ViewModel. Reads the selected journey from the shared session store
 * (populated by the results screen) so no re-planning network call is needed.
 */
@HiltViewModel
class JourneyDetailViewModel @Inject constructor(
    private val sessionStore: JourneySessionStore,
) : ViewModel() {

    fun journeyAt(index: Int): JourneyDto? = sessionStore.journeyAt(index)

    val deepLinks: Map<String, String> get() = sessionStore.currentDeepLinks
    val originLat: Double get() = sessionStore.originLat
    val originLng: Double get() = sessionStore.originLng
    val destLat: Double get() = sessionStore.destLat
    val destLng: Double get() = sessionStore.destLng
}
