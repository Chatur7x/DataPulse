# Validation — 26 October 2026

## Current 0.3.0 optimized preview retest

The polished non-debug preview passed all 16 Artemis repository-controller assertions, including final deletion, and all 19 JVM unit tests. Five standard emulator cold-process launches measured 1281, 1112, 1003, 986 and 1030 milliseconds. No new Android ANR was recorded during those launches or the controller run. Earlier debug-build failures below remain historical evidence; the optimized preview result does not prove the debug build or all physical devices are free of startup problems. The host had approximately 5 GB of free RAM after releasing this task's compiler/build daemons. Runtime model-driven Flash/Pro remains unavailable because of the existing provider configuration.

The 0.3.0 APK is approximately 2.63 MB, bundles Manrope and its SIL license, and retains no INTERNET permission. The app has an engraved allowance surface, animated dial, navigation transitions and comparison expansion. A recorded emulator motion sample reported 171 frames and 32 janky frames (18.71%, Android's current criterion); recording/automation and software-emulator overhead apply. Physical-device frame timing remains a release gate. No universal frame-rate claim is made.

## Later Google Artemis regression result

The actual google/artemis device-controller suite subsequently passed 15 assertions but failed overall because of recurring cold-start ANRs. A cross-tab scroll-position defect was fixed and its regression check passed. Android recorded no-focused-window timeouts both during and after build activity; the startup root cause is unresolved. This later failure supersedes any interpretation of the earlier checks as a clean release acceptance pass. Autonomous Flash/Pro remains blocked by provider configuration. See the delivered Artemis-test-report.md and tests/artemis/controller_acceptance.py for details. Test app data was cleared on the dedicated emulator after evidence collection.

DataPulse 0.2.0, com.datapulse.app. Android SDK compile/target 36, minimum 23. Evaluation build signed with a development key.

## Results

- Debug APK build passed.
- 19 JVM unit tests passed: allowance arithmetic, decimal/binary unit conversion, projections, anomaly predicates and CSV safety.
- 2 Android instrumented database tests passed on the API 35 test emulator: repeated collection replaces app-day records without duplicating traffic; recharge replacement selects the new active plan.
- 10 deterministic UI checks passed: denied-access explanation, form opening, input validation, plan saving, Trends/Apps/My plan/Settings navigation, deletion confirmation, and deletion cancellation preserving the plan.
- Separate live checks passed for quiet-hours persistence and confirmed deletion returning to the no-plan state.
- Granting Usage Access on the dedicated emulator triggered successful measurement and rendered the allowance dashboard. This verifies the integration path, not carrier accuracy.
- Screenshot review completed for onboarding, form validation, tabs, and the measured overview. Status-bar contrast and navigation/chip colours were corrected. Screenshot uses a manually entered Test carrier plan; no demo data is seeded into the APK.
- Android lint passed with 0 errors and 28 advisory warnings, primarily newer dependency versions and attributes ignored on older Android releases.
- Merged manifest and APK permission inspection confirmed no INTERNET permission, Usage Access declaration and backup disabled.
- No app crash entries were observed in the emulator crash log during these workflows.

## Google Artemis

Used the existing installation at D:\Tools\artemis from https://github.com/google/artemis. Ran doctor, launched a recorded Flash acceptance session, and invoked the MCP mobile_diagnose tool with live credential and device probes. Device connection and screen observation worked. The autonomous session could not proceed: the configured model endpoint was unreachable, and live diagnosis also reported an invalid configured provider credential. This is not a passing Artemis acceptance run.

scripts/Run-Artemis.ps1 and tests/artemis/scenarios.md provide the rerun workflow. Repair credentials privately in the Artemis .env/configuration, ensure its configured endpoint is running, increase the existing four-turn Flash cap for a full acceptance run, and restart Artemis. Never put API keys in this repository or in chat. Direct UIAutomator verification was used as a fallback.

## Remaining release gates

Production signing and distribution have not been performed. Physical-device carrier reconciliation, OEM battery/background behavior, dual SIM, VPN/hotspot attribution, Android 23/36 coverage, TalkBack/large fonts and launcher widget rendering require further validation. Device-derived usage remains an estimate. A successful API call does not prove that all historical counters are retained by Android.

Whole-home broadband metering, online speed tests, a live plan catalogue, regulatory verdicts, guaranteed pre-throttle alerts, daily rollover and night-unlimited allowances are outside this redesigned release. See REDESIGN.md for the product contract.
