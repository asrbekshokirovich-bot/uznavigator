# UzNavigator

Personal Android navigator built for **Uzbekistan** with **huge, road-sign-style speed limit display** and **Android Auto support** for Hyundai Creta.

The whole point of this project: speed limits in apps like Waze are tiny and easy to miss. UzNavigator shows the speed limit in a giant UZ road-sign style widget that's impossible to miss while driving.

## Features

- **Big speed limit widget** — 180×220dp UZ road-sign style (white circle, red border, large bold number)
- **NO LIMIT badge** — clear dark badge when no speed data available
- **Color-coded current speed** — turns red & pulses if you exceed the limit
- **Route preview** — distance, duration, and ETA shown before you tap Navigate
- **Address search** — Mapbox Geocoding API, filtered to Uzbekistan
- **Android Auto** — works on Hyundai Creta and other Android Auto-equipped cars
- **Smooth turn-by-turn navigation** powered by Mapbox Navigation SDK

## Tech Stack

| Layer | Library |
|-------|---------|
| Maps | Mapbox Maps SDK 10.18.3 |
| Routing & nav | Mapbox Navigation SDK 2.20.0 |
| Car projection | androidx.car.app 1.7.0 (Android Auto) |
| Geocoding | Mapbox Geocoding API (REST) |
| Language | Kotlin 1.9.24 |
| Min SDK | Android 7.0 (API 24) |
| Target SDK | Android 15 (API 35) |

## Setup

### 1. Clone the repo
```bash
git clone https://github.com/YOUR_USERNAME/UzNavigator.git
cd UzNavigator
```

### 2. Configure Mapbox tokens

Get free tokens at [Mapbox](https://account.mapbox.com/access-tokens/):
- **Public token** (starts with `pk.`)
- **Secret token** with `DOWNLOADS:READ` scope (starts with `sk.`)

Copy the example files and add your tokens:
```bash
cp gradle.properties.example gradle.properties
cp local.properties.example local.properties
```

Then edit:
- `gradle.properties` → paste your `sk.` token after `MAPBOX_DOWNLOADS_TOKEN=`
- `local.properties` → paste your `pk.` token after `MAPBOX_PUBLIC_TOKEN=` and fix the `sdk.dir` path

### 3. Build & install

Open in Android Studio → Sync → Run on device.

OR command-line:
```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Android Auto Setup (for Hyundai Creta)

UzNavigator is built as an Android Auto navigation app, but Google requires special approval for production. For personal/dev use:

1. Open Android Auto app on phone
2. Tap **"Version"** 10 times (enables developer mode)
3. Menu → Developer settings → enable **"Unknown sources"**
4. Plug phone into Creta → UzNavigator appears in Android Auto app drawer

## Project Structure

```
app/src/main/
├── java/com/uznavigator/app/
│   ├── App.kt                       # Application class
│   ├── MainActivity.kt              # Map + search + destination preview
│   ├── NavigationActivity.kt        # Active turn-by-turn navigation
│   ├── auto/                        # Android Auto integration
│   │   ├── UzNavigatorCarService.kt
│   │   ├── NavigationCarScreen.kt
│   │   └── SearchCarScreen.kt
│   ├── data/
│   │   ├── GeocodingService.kt      # Mapbox geocoding REST wrapper
│   │   └── DirectionsService.kt     # Route preview wrapper
│   └── ui/
│       ├── BigSpeedLimitView.kt     # ⭐ The star feature
│       └── SearchAdapter.kt
└── res/
    ├── layout/
    │   ├── activity_main.xml
    │   ├── activity_navigation.xml
    │   └── item_search_result.xml
    └── values/
        ├── colors.xml
        ├── strings.xml
        └── themes.xml
```

## Speed Limit Data Coverage

Speed limits come from **HERE + OpenStreetMap** (Mapbox uses both). Coverage in Uzbekistan:
- ✅ Major highways (M34, A380, M39): good
- ✅ Tashkent main roads: good
- ⚠️ Rural / small roads: incomplete (shows "NO LIMIT" badge)

To improve coverage, contribute corrections to [OpenStreetMap](https://www.openstreetmap.org/).

## License

Personal project. Mapbox SDK is subject to Mapbox's [Terms of Service](https://www.mapbox.com/legal/tos).
