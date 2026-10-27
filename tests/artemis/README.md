# Google Artemis acceptance testing

Two modes are kept distinct:

1. `scripts/Run-Artemis.ps1` invokes Artemis Flash's autonomous model-driven workflow. It requires a working configured model endpoint and valid credentials. The existing local configuration failed live validation; this mode has not passed.
2. `controller_acceptance.py` imports and uses the device controller from the actual google/artemis repository for every observation, tap, swipe, text entry and app launch. It needs the Artemis Python environment and a dedicated emulator, but no model call. It produces assertion results, repository commit identity, screenshots and captured UI element trees.

```powershell
D:\Tools\artemis\.venv\Scripts\python.exe tests\artemis\controller_acceptance.py --artemis-root D:\Tools\artemis --serial emulator-5554 --output evidence\artemis
```

Install the DataPulse debug APK first. Start with no saved plans. The suite creates `Artemis Test Carrier`, checks daily and pooled allowances, toggles quiet hours, verifies deletion cancellation, and finally deletes its test records. It changes Usage Access on the test emulator. It never shares files or transmits app records.

After an interrupted run, `--reset-test-plan` permits cleanup only after the named test carrier is observed. Never run this suite on a personal phone. Exact observed text and verified bottom-navigation geometry avoid Artemis's substring matching ambiguity between `Settings` and `Open usage settings`.

Upstream: https://github.com/google/artemis

## Speed measurement regression (v0.5)

Install the optimized preview on a dedicated emulator. This suite makes real public-endpoint transfers within the on-screen quick-mode budget per completed test; cancelled warm-ups may add traffic. It creates a JSON document in the emulator Downloads folder. Never run it on a personal phone.

```powershell
D:\Tools\artemis\.venv\Scripts\python.exe tests\artemis\speed_acceptance.py --artemis-root D:\Tools\artemis --serial emulator-5554 --output evidence\speed
```

The suite verifies completed measurements, cancellation/restart, loaded HTTP latency, JSON export, background cancellation and process persistence. This is controller-driven testing, not an autonomous LLM run.
