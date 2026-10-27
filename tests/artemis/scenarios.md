# DataPulse Artemis acceptance scenarios

Target package: com.datapulse.app. Use a dedicated test emulator. Never share exports with a recipient during automated tests.

1. Launch DataPulse with Usage Access denied. Verify Connect your usage and a plan setup action appear. No measured zero is claimed.
2. Open Set up my plan. Save with empty fields; verify a validation message and remain on the form.
3. Enter Test carrier, 1.5 GB daily, 299 rupees, 28 days and today's date. Save. Verify the carrier appears and daily-reset language is used.
4. Visit Trends, Apps, My plan and Settings. Verify empty/access-denied states are clear and no crashes occur.
5. In Settings toggle 75 percent and quiet hours, leave and return; verify persistence.
6. Open a new plan and choose Cycle pool. Enter 10 GB, 199 rupees and 28 days. Verify the allowance is a cycle pool, not 280 GB.
7. Open deletion, cancel, verify the plan remains. Delete only on a disposable emulator when explicitly testing deletion; verify the first-plan state returns.
8. Grant Usage Access through system settings; return and refresh. Verify a collection timestamp appears after success. Emulator totals do not establish carrier accuracy.

Run with the installed Google Artemis CLI and the device serial. Artemis is development-only and is not packaged into DataPulse.
