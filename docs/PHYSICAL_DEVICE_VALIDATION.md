# Production validation checklist

Record device model, Android version, carrier, SIM configuration, timezone, app version and collection timestamp for each run. Use dedicated consented test devices and redact personal package inventories from shared reports.

1. Compare matching time windows against Android Settings after counters settle. Preserve system/unattributed differences instead of forcing sums to match.
2. Compare carrier readings separately. Carrier rating and zero-rated/unlimited traffic do not necessarily equal OS byte counts.
3. Exercise reboot, app force-stop, Doze, battery saver and restrictive OEM scheduling. Record collection delays and notification delivery; never promise exact timing.
4. Exercise permission grant, revocation, notification denial and disabled notification channels. Cached data must not silently become current.
5. Test dual SIM switching, eSIM, VPN, hotspot/tethering and work profiles. Record attribution limitations explicitly.
6. Test midnight rollover, timezone changes, an expired cycle, a cycle longer than available history, and two recharges on the same date.
7. Measure 24-hour battery overhead against a baseline using the same workload. Inspect background jobs and database growth.
8. Test TalkBack, large font scaling, dark mode, landscape and a small screen. Every control must remain reachable.
9. Verify share-sheet grant scope and CSV escaping with spreadsheet software. Verify delete-all, no internet permission in the merged release manifest, and backup exclusions.
10. Produce a signed release using a privately managed signing key only after these gates pass. Do not commit keys or passwords.
