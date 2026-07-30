# Commute+

Multi-modal commute planner for India. Enter a start and destination and get every realistic way to
get there — bus, metro, auto, bike-taxi, cab — with prices, boarding/alighting points, and multi-leg
routes when no direct option exists.

**MVP launch city: Bangalore.**

## Repository layout

```
commute-plus/
├─ docs/            Project plan and design docs
├─ backend/         Journey-planning service (Kotlin + OpenTripPlanner 2)
│  └─ src/main/kotlin/com/commuteplus/
│     ├─ domain/    Core models + TransitDataProvider interface
│     ├─ routing/   OTP integration + direct-modes (auto/cab) service
│     ├─ fare/      Fare estimation
│     ├─ city/      Per-city data adapters (bangalore/...)
│     └─ api/       HTTP endpoints
└─ android/         Kotlin + Jetpack Compose client (MVVM + Clean Arch)
```

## Getting started

See [`docs/PLAN.md`](docs/PLAN.md) for the full architecture and roadmap.

Execution order follows the roadmap — **Phase 0 (data spike)** is the critical path:
get Bangalore GTFS + OSM into OpenTripPlanner and route A→B end to end before building UI on top.

## Status

Scaffolding in progress. See `docs/PLAN.md` §9 for phase tracking.
