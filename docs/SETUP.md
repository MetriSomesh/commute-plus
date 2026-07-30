# Commute+ — Setup Guide

## Prerequisites

- **JDK 17+** (required for the Kotlin backend and OpenTripPlanner 2).
  Note: the backend will NOT build on JDK 8/11 — check with `java -version`.
- **Gradle 8.x** — the repo does not commit the Gradle wrapper binary. Either:
  - install Gradle and run `gradle wrapper` once in `backend/` and `android/` to generate `./gradlew`, or
  - open each module in an IDE (IntelliJ/Android Studio) which provisions Gradle automatically.
- **Android Studio Hedgehog (2023.1.1)** or newer (for the Android app).
- **Docker** (optional — for self-hosting Photon geocoder and OTP).
- **Git**.

## Architecture note (important)

The backend does NOT embed OpenTripPlanner. OTP2 runs as a **separate server process** built
from the Bangalore GTFS + OSM graph, and the Commute+ backend queries it over HTTP (GraphQL).
So there are two backend processes:

1. **OTP2 server** — transit routing. Default port **8080**.
2. **Commute+ API** (this repo's `backend/`) — default port **9090**. Calls OTP + GraphHopper + Photon.

## 1. Data files

Place these in `backend/data/` (git-ignored, downloaded separately):

1. **BMTC GTFS feed** (`.zip`) — official DULT portal, or the community mirror
   https://github.com/anikets95/bmtc-gtfs
2. **Namma Metro (BMRCL) GTFS feed** (`.zip`) — BMRCL open data release.
3. **Bangalore OSM extract** (`.osm.pbf`) — https://download.geofabrik.de/asia/india/karnataka-latest.osm.pbf

## 2. Run the OTP2 server

Download the OTP2 shaded jar (e.g. `otp-2.5.0-shaded.jar`) from the OpenTripPlanner releases, then:

```bash
# Build the graph from GTFS + OSM in backend/data, then serve it
java -Xmx4g -jar otp-2.5.0-shaded.jar --build --serve backend/data
```

OTP's GraphQL endpoint will be at:
`http://localhost:8080/otp/routers/default/index/graphql`

## 3. Run the Commute+ API

```bash
cd backend
./gradlew run          # (or: gradle run, if you haven't generated the wrapper)
```

GraphHopper builds a road cache from the `.osm.pbf` on first run (a few minutes), then the API
starts at `http://localhost:9090`.

### Verify

```bash
curl http://localhost:9090/api/v1/health
# → {"status":"ok","city":"Bengaluru"}
```

### (Optional) Self-host Photon geocoder

```bash
docker run -p 2322:2322 komoot/photon:latest
```
Then set `PHOTON_URL=http://localhost:2322`.

## 4. Android setup

1. Open the `android/` directory in Android Studio.
2. The default backend URL is `http://10.0.2.2:9090` (the host machine as seen from the emulator).
   For a physical device, change `API_BASE_URL` in `app/build.gradle.kts` to your machine's LAN IP.
   Cleartext HTTP is permitted only for `10.0.2.2`/`localhost` via `network_security_config.xml`.
3. Run on emulator or device (min API 26 = Android 8).

## 5. Environment variables (Commute+ API)

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | 9090 | Commute+ API HTTP port |
| `OTP_GRAPHQL_URL` | `http://localhost:8080/otp/routers/default/index/graphql` | OTP2 GraphQL endpoint |
| `OTP_DATA_DIR` | `data` | Directory containing GTFS + OSM files (used by GraphHopper) |
| `PHOTON_URL` | `https://photon.komoot.io` | Photon geocoder URL |

## 6. No paid accounts required

See `docs/PLAN.md` §12. The entire stack is $0:
- MapLibre (no key) + Protomaps/demo tiles
- Photon (self-hosted or public)
- OpenTripPlanner + GraphHopper (self-hosted)
- OSM + GTFS (free downloads)
