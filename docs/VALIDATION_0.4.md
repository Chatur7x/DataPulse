# DataPulse 0.4.0 validation

Published 27 October 2026. These are local results, not a claim that GitHub Actions has already passed.

| Check | Result |
| --- | --- |
| Optimized preview build | Passed; R8 and resource shrinking enabled |
| Debug build and lint | Passed |
| JVM tests | 33 passed |
| Room repository and v2-to-v3 migration | 3 instrumented tests passed |
| Real HTTPS speed-engine test | 1 passed in 21.991 seconds |
| Pixel 8a install and launch | Passed earlier; reported cold activity launch 509 ms |
| v0.4 Artemis controller UI | 5 checks passed, then result-screen wait timed out |
| Autonomous Artemis model workflow | Not passed; configured provider unavailable/invalid credentials |

The live engine test reported approximately 26.23 Mbps download, 5.37 Mbps upload, 129.25 ms HTTP latency, and 35,913,728 payload bytes. These are observations from a single emulator/network session, not performance guarantees.

The v0.4 UI controller checked history entry, previous-day navigation, calendar entry, account-free speed-test entry, and cancellation. A subsequent test did not reach the expected result-screen assertion within 90 seconds. This is an unresolved end-to-end UI validation issue; the passing engine test does not establish that the full UI flow passed. Both devices were disconnected during publication, preventing immediate reproduction. Do not label this preview production-ready.

Historical v0.3 Artemis controller results (16 passed) are documented separately in VALIDATION.md. They do not establish full v0.4 regression coverage. Artemis upstream commit used locally: `351ca8422f7b5b54e80a9c1ce03a222e02415b6b`.

Remaining release work includes reliable completed-speed-result UI reproduction, long-running daily collection, OEM scheduling, dual-SIM/VPN/tethering attribution, accessibility and font scaling, controlled reference speed comparisons, and production signing. See PHYSICAL_DEVICE_VALIDATION.md.
