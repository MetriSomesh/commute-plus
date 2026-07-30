# Commute+ — Setup Guide

## Prerequisites

- **JDK 17+** (required for OpenTripPlanner 2 and Kotlin 1.9)
- **Android Studio Hedgehog (2023.1.1)** or newer (for the Android app)
- **Docker** (optional — for self-hosting Photon geocoder)
- **Git**

## 1. Backend setup

### 1.1 Download the real data files

Place these in `backend/data/`:

1. **BMTC GTFS feed** (`.zip`):
   - Official: check DULT Bangalore open data portal
   - Community mirror: https://github.com/anikets95/bmtc-gtfs (download the `.zip`)

2. **Namma Metro (BMRCL) GTFS feed** (`.zip`):
   - Official: BMRCL open data release
   - Community GTFS mirrors (search "BMRCL GTFS")

3. **Bangalore OSM extract** (`.osm.pbf`):
   - Download from https://download.geofabrik.de/asia/india/karnataka-latest.osm.pbf

### 1.2 Build and run

```bash
cd backend
./gradlew run
```

First run builds the OTP graph from the GTFS + OSM data (takes a few minutes).
Subsequent runs load from cache (seconds).

The API server starts at `http://localhost:8080`.

### 1.3 Verify

```bash
curl http://localhost:8080/api/v1/health
# → {"status":"ok","city":"Bengaluru"}
```

### 1.4 (Optional) Self-host Photon geocoder

For better place search without rate limits:

```bash
docker run -p 2322:2322 komoot/photon:latest
```

Then set `PHOTON_URL=http://localhost:2322` before running the backend.

## 2. Android setup

### 2.1 Open in Android Studio

Open the `android/` directory as a project in Android Studio.

### 2.2 Configure backend URL

The default URL (`http://10.0.2.2:8080`) works for the Android emulator connecting to your
local backend. For a physical device on the same network, change `API_BASE_URL` in
`app/build.gradle.kts` to your machine's LAN IP.

### 2.3 Build and run

Run on emulator or device from Android Studio (min API 26 = Android 8).

## 3. Environment variables (backend)

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | 8080 | HTTP server port |
| `OTP_DATA_DIR` | `data` | Directory containing GTFS + OSM files |
| `PHOTON_URL` | `https://photon.komoot.io` | Photon geocoder URL |

## 4. No paid accounts required

See `docs/PLAN.md` §12. The entire stack is $0:
- MapLibre (no key)
- Protomaps tiles (static file)
- Photon (self-hosted or public)
- OpenTripPlanner (self-hosted)
- GraphHopper (self-hosted)
- OSM + GTFS (free downloads)
