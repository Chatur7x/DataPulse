<div align="center">

<img src="docs/branding/DataPulse-logo.svg" width="96" alt="DataPulse pulse logo" />

# DataPulse

### Know your data. Find your rhythm.

A polished Android companion for daily data usage, recharge plans, and real internet measurements.

![Android](https://img.shields.io/badge/Android-6.0%2B-62E1CE?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack_Compose-103B52?logo=kotlin)
![Version](https://img.shields.io/badge/Preview-0.6.0-087F82)
[![CI](https://github.com/Chatur7x/DataPulse/actions/workflows/ci.yml/badge.svg)](https://github.com/Chatur7x/DataPulse/actions/workflows/ci.yml)

[Download APK](https://github.com/Chatur7x/DataPulse/releases/tag/v0.6.0) · [Validation](docs/VALIDATION_0.6.md) · [Design](docs/PREMIUM_DESIGN.md)

**No signup. No account gate. Your usage history stays on your phone.**

</div>

## A quieter view of your connection

Ocean tones, mint accents, subtle surface textures, bundled Manrope typography, and smooth native transitions. Light and dark themes feel at home on Android.

<table><tr><td align="center"><b>Overview</b><br><img src="docs/images/overview.jpg" width="240" alt="Data allowance overview" /></td><td align="center"><b>Daily history</b><br><img src="docs/images/history.jpg" width="240" alt="Daily usage history and date navigation" /></td><td align="center"><b>Dark mode</b><br><img src="docs/images/dark.jpg" width="240" alt="Dark theme overview" /></td></tr></table>

The history screenshot is from v0.4.0. Overview and dark screenshots show the v0.3 visual foundation retained in v0.6; sample data is for demonstration.

## What you can do

- **Track every recorded day:** browse dates, inspect mobile and Wi-Fi totals, see leading apps, collection time, and partial-day status.
- **Understand patterns:** seven-day summaries, fourteen-day charts, peak usage, and comparisons when enough comparable days exist. Missing records are not shown as measured zero.
- **Keep each SIM separate:** detect active SIMs with optional permission, inspect Android-published plan information when authorized, and keep independent confirmed recharge histories. Paste a receipt for local suggestions, then review and save.
- **Browse current offers:** open official Jio, Airtel, Vi and BSNL catalogues in browser tabs, without a cached or guessed price list.
- **Manage your allowance:** configure daily-reset or cycle-pool plans manually, track recharges, and view remaining allowance.
- **Measure internet speed:** our Kotlin measurement engine exchanges real payloads with a public Cloudflare endpoint and records download, upload, idle and loaded HTTP latency, jitter, throughput traces, and measurement history. Original samples and exact counters can be exported as JSON.
- **Stay in control:** pause daily collection, export CSV, delete local records, configure optional alerts, and use a home-screen widget.

## Install

Download `DataPulse-0.6.0-preview.apk` from [Releases](https://github.com/Chatur7x/DataPulse/releases/tag/v0.6.0), transfer it to your Android phone, and open it. Allow installation from your chosen installer when Android asks.

This optimized preview uses R8 and resource shrinking. It is development-signed for evaluation, not a production-signed Play Store release. Updates require the same signing key; a locally rebuilt APK may use a different development key.

Open DataPulse and grant **Usage Access** to collect Android network statistics. Notification permission is optional. You can use it without creating an account.

## SIMs & recharges

Open **My plan → SIMs & recharges**, then choose **My SIMs** or **Recharge catalogue**.

<table><tr><td align="center"><b>Detected SIM information</b><br><img src="docs/images/v06-sims.jpg" width="260" alt="Actual emulator SIM detection with restricted carrier-plan access explained" /></td><td align="center"><b>Official carrier directory</b><br><img src="docs/images/v06-catalogue.jpg" width="260" alt="Official carrier catalogue links" /></td></tr></table>

Android supplies active SIM carrier/slot information with optional Phone permission. Carrier billing-plan access is restricted; granting Phone permission does not unlock it for every app. Accessible Android-published records are shown as provided. When records are unavailable, DataPulse says so and offers a reviewed receipt/manual confirmation flow. It does not infer a paid plan from your carrier name or signal.

The catalogue is a directory to live official carrier pages in browser tabs, not a native searchable price-feed integration. Carriers may require a circle, number or account to show eligible offers. Universal automatic paid-plan discovery and all-world carrier coverage are not present. See [sources, behavior and limitations](docs/SIM_PLANS.md).

Screenshots use an emulator's detected T-Mobile SIM. Any **Artemis SIM fixture** shown elsewhere is explicitly entered test data, not an automatically discovered or purchased recharge. No real customer's billing records were used or published.

## Measurement and privacy

Usage collection uses Android system statistics and a local Room database. Initial collection attempts up to 31 recent days where Android makes them available; later refreshes reconcile recent days. Hourly background work is best effort and can be delayed by Android. Stored history remains available until deleted. Turning tracking off pauses collection; deleting records leaves tracking paused until enabled again or a new plan is saved.

Mobile statistics can aggregate accessible mobile networks; they are not guaranteed per-SIM. Wi-Fi statistics describe this phone, not every device on your router. Android counters and carrier billing may differ. Usage displays use MiB/GiB; plan entry uses decimal GB.

Speed tests require explicit consent and use HTTPS requests to `speed.cloudflare.com/__down` and `speed.cloudflare.com/__up`. Cloudflare receives your IP and test traffic; the app does not send your app-usage list. Quick and extended modes cap application payload at 64 MiB and 256 MiB respectively; protocol overhead is additional. Upload payloads contain generated bytes, not personal files.

The engine uses four parallel HTTP/1.1 streams, adaptive request sizes, monotonic timing, cache-busting, uncompressed responses, and completed-byte accounting. Idle and loaded HTTP latency are measured separately. [Read the measurement methodology and limitations](docs/SPEED_METHODOLOGY.md). Results measure this route and current conditions, including HTTP overhead; they are not a guaranteed maximum line rate. Latency is HTTP round-trip latency, not ICMP ping. Tests cancel when the screen is left or the app goes into the background. DataPulse does not run Ookla’s proprietary engine or claim a certified 99% accuracy score. Packet loss is not inferred from HTTP failures.

## Build locally

Requirements: JDK 21, Android SDK platform 36 and build tools, and internet access for dependency resolution. Minimum device API: 23. Target API: 36.

```bash
git clone https://github.com/Chatur7x/DataPulse.git
cd DataPulse
```

Open in Android Studio, or set `sdk.dir` in your untracked `local.properties`, then run:

```bash
./gradlew :app:assemblePreview :app:testDebugUnitTest :app:lintDebug
```

On Windows, use `gradlew.bat`. The optimized APK is generated at `app/build/outputs/apk/preview/app-preview.apk`. For a debuggable build, run `:app:assembleDebug`.

```bash
adb install -r app/build/outputs/apk/preview/app-preview.apk
```

No cloud credentials or authentication backend are required.

## Inside the app

| Layer | Implementation |
| --- | --- |
| Interface | Kotlin, Jetpack Compose, Material 3, bundled Manrope |
| State | ViewModels, coroutines, Flow, Hilt |
| Storage | Room v5 with committed schemas and explicit migrations |
| Collection | Android usage/network statistics, WorkManager |
| Speed measurement | Custom Kotlin engine over OkHttp HTTPS transport |
| Validation | JVM tests, Android instrumentation, Google Artemis controller |

```text
app/src/main/       Android app, UI, collection, storage, speed engine
app/src/test/       Accounting, analysis, export and speed-math tests
app/src/androidTest/  Database, migration and live network tests
app/schemas/        Versioned Room schemas
scripts/            Manifest checks and Artemis launcher
tests/artemis/      Device acceptance automation
docs/               Design, validation, screenshots and branding
licenses/           Bundled font license
```

## Tested, with clear limits

The v0.6 preview passed **60 JVM tests**, **5 database/migration instrumented tests**, and **12 Google Artemis controller UI checks**, plus the debug/optimized builds, lint and manifest checks. SIM tests cover permission denial, actual emulator SIM detection, restricted-plan handling, receipt review, saving, deletion cancellation, process persistence, catalogue availability and the existing speed-test entry.

The database tests preserve daily history and original speed evidence through upgrades, and verify independent records for two simulated subscriptions. Physical dual-SIM/eSIM behavior and successful carrier-privileged billing retrieval remain unverified. The Pixel 8a was disconnected during this update; v0.6 has not yet been installed there.

The speed engine itself is unchanged from v0.5, which passed a separate live Cloudflare engine test and ten end-to-end speed UI checks. Its [original UI-exported JSON](docs/test-results/v05-ui-export-original.json), [separate engine record](docs/test-results/v05-engine-original-measurement.json), and [validation report](docs/VALIDATION_0.5.md) remain available. No new live speed-engine pass is claimed for v0.6.

<table><tr><td align="center"><b>Real speed result from v0.5</b><br><img src="docs/images/speed-result.jpg" width="260" alt="Real speed result from the unchanged measurement engine" /></td><td align="center"><b>Original measurement evidence</b><br><img src="docs/images/speed-evidence.jpg" width="260" alt="Exact original byte counts and monotonic timing evidence" /></td></tr></table>

Recalculate an exported method-v2 record:

```bash
python scripts/verify_speed_result.py docs/test-results/v05-ui-export-original.json
```

Numerical consistency does not prove a 1% measurement error bound or server-signed authenticity. DataPulse remains an independent HTTP measurement engine with no Ookla certification. Controlled reference-link and paired Ookla comparisons remain release work.

[Google Artemis](https://github.com/google/artemis) is an external testing tool. These checks use its actual device controller. Autonomous model-driven Artemis validation remains unavailable with the local provider configuration; no autonomous-agent pass is claimed.

See [current validation](docs/VALIDATION_0.6.md), [SIM sources and limits](docs/SIM_PLANS.md), [speed methodology](docs/SPEED_METHODOLOGY.md), [Artemis setup](tests/artemis/README.md), and the [physical-device checklist](docs/PHYSICAL_DEVICE_VALIDATION.md).

## Typography and branding

The pulse mark is provided as native Android vector assets and SVG/PNG exports in [docs/branding](docs/branding). Manrope is bundled locally under the [SIL Open Font License](licenses/Manrope-OFL.txt). No remote font request is required.

No general source-code license has been selected for this repository; the font license applies to the font assets only.
