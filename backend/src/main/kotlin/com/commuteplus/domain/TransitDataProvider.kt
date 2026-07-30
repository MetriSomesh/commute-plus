package com.commuteplus.domain

/**
 * The pluggable, per-city seam of Commute+.
 *
 * Every city (Bangalore first) implements this interface using whatever data sources exist for it —
 * GTFS static feeds, curated metro tables, government auto-fare rules, etc. The routing engine and
 * the API layer depend ONLY on this interface, never on a concrete city. Adding a new city is a new
 * implementation plus config; it is never a fork of the routing code.
 *
 * See docs/PLAN.md §3 (Data strategy) and §5 (Routing engine).
 */
interface TransitDataProvider {

    /** Stable identifier, e.g. "bangalore". */
    val cityId: String

    /** Human-readable name, e.g. "Bengaluru". */
    val cityName: String

    /** True if this provider covers the given point (used to route a request to the right city). */
    fun covers(point: LatLng): Boolean

    /** Which modes this city currently has data for. Drives graceful degradation in the UI. */
    fun supportedModes(): Set<TravelMode>

    /**
     * Autocomplete / geocode a free-text query into candidate places within the city.
     * Powers the "from"/"to" search fields.
     */
    fun searchPlaces(query: String, locale: String = "en", limit: Int = 8): List<Place>

    /**
     * Plan public-transit + walk journeys (bus, metro) for the request.
     * Multi-leg results are expected here — this is where OTP2 does the heavy lifting.
     * Returns an empty list if no transit route exists (caller falls back to direct modes).
     */
    fun planTransit(request: JourneyRequest): List<Journey>

    /**
     * Estimate on-demand "direct" options (auto, bike-taxi, cab) from origin to destination.
     * Computed from road distance/time + this city's fare rules. Always produces estimates
     * (Fare.estimated == true) so results exist even where transit data does not.
     */
    fun estimateDirectModes(request: JourneyRequest): List<Journey>
}
