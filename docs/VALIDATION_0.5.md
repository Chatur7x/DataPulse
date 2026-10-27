# DataPulse 0.5.0 validation · 27 October 2026

| Check | Observed result |
| --- | --- |
| Debug build, optimized R8 preview, Android test APK | Passed |
| JVM tests | 39 passed, zero failures/errors |
| Android database tests | 4 passed: repository tests plus v2-to-v4 and v3-to-v4 preservation |
| Live Cloudflare engine test | 1 passed; actual HTTPS payload |
| Artemis device-controller UI suite | 10 passed |
| Merged preview manifest | Passed: explicit internet and usage permissions, backup/cleartext disabled |
| Independent evidence recalculation | Passed for engine and real UI-exported JSON |
| Physical Pixel 8a v0.5 install | Not performed: device disconnected |
| Universal 99% accuracy / Ookla agreement | Not established |

The five instrumented tests passed together in 31.907 seconds. The engine test observed 10.823355 Mbps download, 9.627021 Mbps upload, 117.07765 ms median idle HTTP latency and 31,823,173 payload bytes. See [original record](test-results/v05-engine-original-measurement.json) and [instrumentation output](test-results/v05-instrumented.log). The UI test is a separate real session and its original values differ; see [actual exported record](test-results/v05-ui-export-original.json). No performance claim is inferred from comparing these uncontrolled sessions.

The [ten UI assertions](test-results/v05-ui-results.json) cover account-free entry, cancellation, immediate restart, completed visible results, loaded latency, original counters, Android document-picker export, navigation persistence, completed background cancellation, and process-restart persistence. Tests ran on a dedicated Android 15 emulator using the actual Google Artemis controller at upstream commit `351ca8422f7b5b54e80a9c1ce03a222e02415b6b`.

Initial automation attempts missed off-screen controls or expected a full activity stop after only half a second; the final script explicitly scrolls and waits three seconds for backgrounding. Those incomplete attempts are not counted as successful runs. The final full suite passed. Autonomous model-driven Artemis testing remains unavailable with the local provider configuration.

Independent Python checks recompute both rates from acknowledged bytes and monotonic duration, median idle latency, successive jitter, payload-cap accounting, and every sampled trace rate from its raw byte/time deltas. This establishes numerical consistency, not server attestation or a 99% accuracy certification.

Old speed results retain their original summaries with blank raw evidence; migration does not invent retrospective samples. The v3 fixture verifies this explicitly. Daily history is preserved through the v2 migration chain.

Remaining production work: controlled bandwidth/reference-server comparisons, alternating paired Ookla runs on physical devices, low/high-speed and congested links, VPN/mobile/dual-SIM coverage, long-running collection/accessibility checks, and production signing. The preview uses a development signing key. See SPEED_METHODOLOGY.md and PHYSICAL_DEVICE_VALIDATION.md.
