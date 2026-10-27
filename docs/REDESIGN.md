> Historical 0.2 design. Version 0.4 adds optional, consented HTTPS speed tests; see the current README and VALIDATION_0.4.md.

# DataPulse 0.2

An offline Android companion for understanding this phone's estimated data usage against a manually entered recharge plan.

## Visual system

Ocean ink #103B52, turquoise #087F82, mint #62E1CE, mist #EFF6F8 and white. A pulse dial anchors the overview; quiet lists support exploration. Native sans typography, readable allowance figures, rounded grouping surfaces and system dark mode. No decorative automatic animation. Charts have text equivalents.

## Measurement contract

Usage Access is optional. Missing access and uncollected history are not measured zero. Accessible mobile networks are aggregated, not guaranteed per-SIM. Wi-Fi represents this phone only. Statistics are estimates with variable delay; alerts offer no overage guarantee.

Day windows are [local midnight, next local midnight). Daily summaries are queried directly. Every collection replaces seven days of potentially revised statistics; first collection requests 31 days. App-day replacement is transactional. Raw receive/transmit bytes are preserved for app records. Reports use MiB/GiB; entered plans use decimal GB. One plan is active; supported allowances are daily reset and pooled cycle data. Saving a recharge archives the previous plan atomically. Same-day recharges cannot split daily historical measurements.

## Privacy and boundaries

No INTERNET permission, analytics, advertisements or remote account. Backup and transfer exclusions cover database, preferences, files and root storage. Sharing uses temporary content URIs. CSV strings are escaped and formula-prefixed values neutralized. Delete-all cancels work and removes database contents, settings and cached exports. Previously shared copies remain with recipients. No forensic admissibility or compromised-device protection claims.

## Deferred features

Whole-home router usage, speed tests, live carrier catalogue, regulatory violation verdicts, 5G causal scoring, night-unlimited and rollover plans are deferred. Coarse OS buckets do not justify a precise hourly heatmap. No invented sample usage is mixed into measured views.

## Release gates

Build, domain tests, Android lint, emulator navigation/validation tests, and real-device reconciliation. OEM background behavior, battery cost, dual SIM, VPN/tethering and carrier comparison require physical-device validation. A debug APK is for evaluation, not a production-signed release.
