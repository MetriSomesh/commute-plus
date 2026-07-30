# Commute+ — Project Plan

Multi-modal commute planner for India. Enter a start and destination, get every realistic
way to get there — public bus, metro, auto, bike-taxi, cab — with prices, boarding/alighting
points, and multi-leg routes when no direct option exists.

**Launch city (MVP): Bangalore.** Every other city is added later as a data adapter, never a fork.

---

## 1. Core problem

The feature (multi-modal journey planning) is conceptually solved. The hard part in India is **data**:

1. Transit data is fragmented — each city has its own authority; only some publish machine-readable feeds.
2. Informal transport (auto, shared auto, bike taxi) has no schedule and no published live fare.
3. Aggregator pricing (Ola/Uber/Rapido) is not freely available via public APIs.

Architecture is therefore designed around **pluggable, per-city data adapters**, not a single national dataset.

## 2. Scope

**MVP (Bangalore)**
- Enter start + destination (search, map pin, or current location).
- Ranked journey options: BMTC bus, Namma Metro, auto/bike-taxi/cab estimates.
- Per option: total time, price (or range), boarding point, alighting point, walking legs.
- Multi-leg public transport (e.g., Bus 356 → Silk Board → Bus 500 → Bellandur).
- Languages: English + Kannada + Hindi, with transliterated stop names.

**Vision (post-MVP)**
- More cities via new adapters.
- Live vehicle tracking (GTFS-Realtime where available).
- Offline mode (cached city data).
- Crowd-sourced corrections, accessibility info, fare/ticket integration.

## 3. Data strategy

Every city sits behind a common `TransitDataProvider` interface. The routing engine only understands
GTFS; each adapter normalizes its sources into a GTFS-like internal model. Auto/cab are computed as
point-to-point options layered on top of the same origin/destination.

| Data type | Source | Notes |
|---|---|---|
| Bus routes/stops/schedules | GTFS static (Bangalore DULT/BMTC open data, ONDC mobility) | Industry-standard format. |
| Metro | Per-city metro GTFS or curated route/fare tables | Small, stable — cheap to hand-curate. |
| Live bus positions | GTFS-Realtime where available | Optional; degrade to schedule-based. |
| Auto / bike-taxi fares | Govt fare rules (base + per-km) → compute from distance/time | Regulated fare cards per city. |
| Cab estimates | Public APIs restricted → estimate via distance × rate bands, labeled "approx" | Transparent estimates, not live quotes. |

City with no data: still shows cab/auto estimates (needs only routing distance) + "transit data not yet available."

## 4. High-level architecture

```
Android App (Kotlin, Jetpack Compose, MVVM + Clean Arch)
        │ HTTPS (REST/gRPC)
        ▼
Backend — Journey Planning Service
   ├─ Routing Engine (OpenTripPlanner 2, multi-modal)
   ├─ Fare Estimation Service (transit + auto/cab)
   ├─ Per-city Data Adapters (TransitDataProvider)
   └─ Geocoding / Places proxy
Data layer
   ├─ GTFS store (Postgres + PostGIS)
   ├─ Redis cache
   └─ GTFS ingestion pipeline (batch)
```

Server-side routing (heavy graph work + third-party keys never ship in the APK). New cities/data
roll out without an app update.

## 5. Routing engine

- **OpenTripPlanner 2 (OTP2)** for public-transit legs: multi-modal, multi-leg transit+walk from GTFS + OSM.
  Handles "2 buses from 2 stops with a walk between" out of the box.
- Thin **direct-modes service**: given origin/destination, computes auto/bike/cab options from road distance
  (OSM/GraphHopper) + fare rules.
- Merge transit + direct options; rank by weighted score (time, price, walking, transfers). User can
  re-sort by cheapest / fastest / fewest changes.

Fallback if OTP can't be shaped: custom RAPTOR/CSA transit engine + Dijkstra/A* walk graph.

## 6. Android tech stack

- Kotlin, Jetpack Compose + Material 3
- MVVM + Clean Architecture (data / domain / presentation), Repository pattern
- Hilt (DI), Coroutines + Flow
- Retrofit + OkHttp, kotlinx.serialization
- Room + DataStore (offline cache)
- **MapLibre Native** for maps (open source, no account) — NOT Google Maps. See §12.
- Per-locale resources; server sends localized + transliterated stop names
- JUnit, Turbine, Compose UI tests, MockK

## 7. Backend tech stack

- Kotlin (Ktor or Spring Boot) — keeps the stack unified with the OTP JVM engine
- OpenTripPlanner 2 (routing)
- PostgreSQL + PostGIS, Redis
- Scheduled GTFS import/validation jobs
- Containerized; CDN for static assets

## 8. Key user flows

1. A→B search with disambiguation (a name can match several points → picker).
2. Result list — cards with mode icon, total time, price/range (estimates labeled), transfer count.
3. Journey detail — step-by-step legs (walk → board → alight → walk → board → alight) on a map.
4. Graceful degradation — no transit data → still show cab/auto estimates + clear message.
5. Offline — cached last city works without connection.

## 9. Roadmap

| Phase | Goal |
|---|---|
| 0 | Data spike: Bangalore GTFS + OSM into OTP2, route A→B end to end. **Critical path — do first.** |
| 1 | Backend API: bus+metro+walk planning; fare/auto/cab estimate service. |
| 2 | Android MVP: search, results, detail, maps, EN/KN/HI. |
| 3 | Polish: offline cache, ranking filters, error/empty states, analytics. |
| 4 | Bangalore beta; gather corrections, fix data gaps. |
| 5 | Second city via new adapter — validates pluggable design. |

## 10. Risks & mitigations

- **Data availability/accuracy** → best-data city first; crowd-sourced corrections; label freshness.
- **Auto/cab fare accuracy** → always show as estimate ranges; never imply a live quote.
- **Aggregator APIs closed** → deep-link to Ola/Uber/Rapido for booking; don't quote/book in-app for MVP.
- **Routing cost at scale** → cache popular O-D pairs. Maps are $0 already (MapLibre + Protomaps, §12).
- **Per-city sprawl** → enforce `TransitDataProvider`; every city is an adapter, never a fork.

## 11. Design decisions

- v1 is a **planner, not a booking platform** — deep-link to aggregator apps for the actual booking.
- **Android-only MVP**, but all business logic stays on the backend so a future iOS app is a thin client.

## 12. Confirmed tech stack & $0 services (LOCKED — no paid accounts required)

Hard constraints for the MVP: **no mock data anywhere**, and **no paid/billing accounts** — every
service below is free and open, self-hosted or download-based. No Google Maps, no billing.

### Data sources (real, download only — no account)
| What | Source | Notes |
|---|---|---|
| BMTC bus GTFS | Official DULT/BMTC open-data release; community mirror: https://github.com/anikets95/bmtc-gtfs | Real routes/stops/schedules. Prefer official DULT feed (more complete). |
| Namma Metro GTFS | Official BMRCL open-data release (unofficial mirrors exist) | Real metro routes/stations/fares. |
| Road/pedestrian graph | Bangalore/Karnataka **OSM extract** from Geofabrik (https://download.geofabrik.de) | Powers walking legs + real road distances for auto fares. |

### Routing (self-hosted, open source — no account)
- **OpenTripPlanner 2 (OTP2)** — multi-modal, multi-leg transit + walk routing from GTFS + OSM.
  Runs on our own server. No API key, no per-call cost.

### Maps on Android (free — no account, $0)
- **MapLibre Native** — open-source map SDK, replaces Google Maps SDK. No key.
- **Tiles: Protomaps `.pmtiles`** — a single map file generated from the OSM extract (or a prebuilt
  India/Bangalore file), served as a static file. No account, no tile server process, $0.
  - Fallbacks if needed: self-hosted tiles (TileServer-GL / Martin) from the same OSM extract; or a
    free-tier hosted provider (MapTiler / Stadia) which would require a free account (avoid if possible).

### Address search / autocomplete (free — no account)
- **Photon** (OSM-based, built for autocomplete) — self-hosted next to the backend on the same OSM data.
  - Alternative: **Nominatim** (self-hosted). OTP also geocodes transit stops internally.
- Tradeoff: OSM place search in India is slightly less rich than Google for obscure POIs; fine for
  localities/stops. Geocoder sits behind an interface, so it can be swapped without touching the rest.

### Fares (real — no account)
- **Auto**: official Karnataka RTA fare card (₹36 first 2 km, ₹18/km after, 1.5x night 22:00–05:00),
  effective 2025-08-01, computed against real OSM road distance. Implemented in
  `backend/.../fare/BangaloreAutoFare.kt`. Update constants there when the RTA revises fares.
- **Bus/Metro**: fares from the real GTFS feed where the feed populates them.
- **Bike-taxi (Rapido) / Cab (Ola/Uber)**: NO authoritative free price source and no open API.
  MVP shows them as options with a **deep-link into their apps** ("check price / book") — no fabricated
  price is ever displayed (satisfies no-mock). Real in-app pricing would need a paid/partner API later.

### Backend (self-hosted, open source — no account)
- **Kotlin + Ktor** (chosen for a lighter footprint over Spring), on the JVM alongside OTP2.
- **PostgreSQL + PostGIS**, **Redis** (all self-hostable, free).

### Account/cost summary
- **Total paid accounts required: 0.**
- **Total API keys shipped in the app: 0** (MapLibre + Protomaps need none).
- Everything real, nothing mocked, nothing billed.
