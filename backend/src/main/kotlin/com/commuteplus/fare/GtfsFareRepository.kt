package com.commuteplus.fare

import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Real public-transport fares, read directly from the GTFS feeds' fare_attributes.txt +
 * fare_rules.txt (zone-pair fares). These are the operators' published fares — not estimates.
 *
 * Feed fare model (both BMTC and BMRCL/metro use this):
 *   fare_attributes.txt : fare_id -> price (INR)
 *   fare_rules.txt      : fare_id -> origin_id, destination_id  (zone or station codes)
 *
 * A leg's fare is looked up by (boarding stop zone, alighting stop zone). BMTC stop zones come from
 * stops.txt `zone_id`; metro station codes double as their zone. OTP gives us each stop's zoneId.
 *
 * Coverage note: the metro feed is complete; the community BMTC feed's fare_rules do not enumerate
 * every zone pair, so some bus legs legitimately have no published fare here — those return null
 * (the UI then shows "check in app" rather than a fabricated price).
 */
class GtfsFareRepository(dataDir: File) {

    private val log = LoggerFactory.getLogger(GtfsFareRepository::class.java)

    // feedId -> ((originZone, destZone) -> price in INR)
    private val fareByFeed = mutableMapOf<String, Map<Pair<String, String>, Double>>()

    init {
        loadFeed(File(dataDir, "bmtc.zip"), "bmtc")
        loadFeed(File(dataDir, "bmrcl.zip"), "bmrcl")
    }

    /**
     * Look up a fare for a leg. Tries the exact zone pair, then the reversed pair (fares are
     * effectively symmetric). Returns null when the feed has no published fare for the pair.
     */
    fun fare(feedId: String?, originZone: String?, destZone: String?): Double? {
        if (feedId.isNullOrBlank() || originZone.isNullOrBlank() || destZone.isNullOrBlank()) return null
        val table = fareByFeed[feedId] ?: return null
        return table[originZone to destZone] ?: table[destZone to originZone]
    }

    private fun loadFeed(zip: File, feedId: String) {
        if (!zip.exists()) {
            log.warn("Fare feed not found: ${zip.absolutePath} (fares for '$feedId' will be unavailable)")
            return
        }
        try {
            ZipFile(zip).use { zf ->
                val prices = mutableMapOf<String, Double>() // fare_id -> price
                zf.getEntry("fare_attributes.txt")?.let { entry ->
                    zf.getInputStream(entry).use { input ->
                        parseCsv(input) { row ->
                            val id = row["fare_id"]
                            val price = row["price"]?.toDoubleOrNull()
                            if (!id.isNullOrBlank() && price != null) prices[id] = price
                        }
                    }
                }

                val table = mutableMapOf<Pair<String, String>, Double>()
                zf.getEntry("fare_rules.txt")?.let { entry ->
                    zf.getInputStream(entry).use { input ->
                        parseCsv(input) { row ->
                            val id = row["fare_id"] ?: return@parseCsv
                            val origin = row["origin_id"]
                            val dest = row["destination_id"]
                            val price = prices[id] ?: return@parseCsv
                            if (!origin.isNullOrBlank() && !dest.isNullOrBlank()) {
                                val key = origin to dest
                                val existing = table[key]
                                // Keep the lowest fare when multiple rules map the same pair.
                                if (existing == null || price < existing) table[key] = price
                            }
                        }
                    }
                }
                fareByFeed[feedId] = table
                log.info("Loaded ${table.size} fare zone-pairs for '$feedId' from ${zip.name}")
            }
        } catch (e: Exception) {
            log.error("Failed to load fares for '$feedId': ${e.message}")
        }
    }

    /**
     * Minimal GTFS CSV reader: indexes values by header name (GTFS column order varies between
     * files/feeds). These fare files contain only simple code/number values with no quoted commas.
     */
    private fun parseCsv(input: InputStream, onRow: (Map<String, String>) -> Unit) {
        input.bufferedReader().use { br ->
            val header = br.readLine()?.split(",")?.map { it.trim().trim('"') } ?: return
            br.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val values = line.split(",")
                val row = header.mapIndexed { i, h -> h to (values.getOrNull(i)?.trim()?.trim('"') ?: "") }.toMap()
                onRow(row)
            }
        }
    }
}
