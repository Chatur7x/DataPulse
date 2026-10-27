# DataPulse 0.6.0 validation · 27 October 2026

| Check | Observed result |
| --- | --- |
| Debug APK, optimized R8 preview, test APK | Passed |
| JVM tests | 60 passed, zero failures/errors |
| Android database/migration tests | 5 passed in 11.914 seconds |
| Artemis controller SIM UI suite | 12 passed |
| Merged manifest/privacy surface | Passed; optional READ_PHONE_STATE, no SMS/call-log/contacts/phone-number permissions |
| Physical Pixel 8a installation / dual SIM | Not performed: phone disconnected |
| Carrier-authorized paid-plan retrieval | Not demonstrated; normal Android restriction shown |
| Native searchable price-feed catalogue | Not implemented; live official browser directories provided |

[UI assertions](test-results/v06-ui-results.json) cover separate section entry, optional-access explanation, all four directory links without permission, runtime permission denial, actual emulator SIM discovery, explicit restricted-plan state, validation, receipt review, per-SIM saving, deletion cancellation, process persistence and speed-test entry. These use the actual Google Artemis controller at upstream commit `351ca8422f7b5b54e80a9c1ce03a222e02415b6b`, not an autonomous model-driven run.

[Instrumented results](test-results/v06-instrumented.log) include repository tests, v2/v3 migration chains and a direct v4-to-v5 fixture. The direct fixture preserves original speed JSON, inserts two subscription records, preserves null unknown amounts/allowances, and removes one without affecting the other. No actual customer record is seeded or deleted by these tests.

The JVM suite includes 21 SIM/receipt tests for exact paise/byte conversion, ambiguity, punctuation, grouped prices, missing allowance periods, zero-data voice packs, unknown slots, inclusive dates and subscription/operator separation. Earlier development runs exposed punctuation and zero-data handling; the final suite passed after correction.

The UI record **Artemis SIM fixture** uses explicitly pasted test text (Rs.299, 1.5 decimal GB/day, 28 days) and a user-confirmed date. It is not a real T-Mobile recharge or proof of automatic billing retrieval. The actual emulator carrier/slot came from Android; the API's carrier-plan restriction was displayed without fabricated data.

Catalogue validation proves that the four official-source links are available without Phone permission. It does not verify every offer, circle, carrier website transaction, login or payment, and no purchase is attempted. The catalogue opens carrier-owned sites through browser Custom Tabs. Source URLs and limitations are in SIM_PLANS.md.

The speed measurement implementation is unchanged from v0.5. Its separate live engine and ten UI checks are recorded in VALIDATION_0.5.md; only speed-screen entry is checked again here. No universal 99% accuracy claim is made.

Remaining work includes physical dual-SIM/eSIM replacement and ID reassignment, authorized carrier integrations for universal paid-plan discovery, native maintained price feeds if required, broader receipt formats, more than 366-day manual validity, accessibility, production signing and controlled speed accuracy benchmarks.
