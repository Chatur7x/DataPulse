# SIMs and recharges · DataPulse 0.6

Open **My plan → SIMs & recharges**. **My SIMs** and **Recharge catalogue** are distinct sections.

## What is automatic

With optional `READ_PHONE_STATE` permission, Android supplies visible active subscriptions, carrier/display names, slot indices, eSIM status where supported, and the default mobile-data subscription where the API supports it. The screen refreshes on entry, resume and the Refresh SIMs action. Disabled eSIM profiles and removed SIMs are not promised to appear in the active list.

On Android 9+, DataPulse attempts `SubscriptionManager.getSubscriptionPlans`. This API is restricted to carrier-authorized or otherwise specifically authorized apps. A normal installation often receives a SecurityException; granting Phone permission does not grant carrier privileges. The screen explains the restriction rather than inventing a paid plan.

If carrier-published records are accessible, DataPulse shows their actual titles, reported cycle allowance/unlimited flag, cycle boundaries, and a usage snapshot only with its provided timestamp. This Android API does not provide the paid recharge price or reliably establish daily-reset rules. No price, expiry, quota, or active entitlement is guessed from the carrier name, SIM slot, signal or traffic counters.

## Confirmed recharge records

Each subscription has independent recharge records stored in Room. The association uses Android subscription ID plus the reported operator key, not a slot number alone. Two SIMs do not overwrite each other's records. Subscription IDs are not a permanent global SIM identity: Android reassignment or a different operator may leave older records in **Other saved SIM records**. Review them if you replace a SIM. The app does not read ICCID, IMSI, phone number APIs, SMS inbox, contacts or call logs.

You can paste a recharge confirmation into an on-device parser. Only explicit rupee amounts, GB/MB allowances, per-day qualifiers and explicit validity days become suggestions. Multiple different amounts or allowances are treated as ambiguous and left blank. The full receipt is not saved in the database. You must review all fields, give the plan a name and enter its actual start date before saving. A queued recharge's activation date may differ from the payment date.

Blank amount or allowance means unknown. An explicitly entered zero-data allowance is supported for voice-only packs. Monetary values retain paise exactly. Allowances are stored as exact bytes; display units follow the existing MiB/GiB reports (1.5 decimal GB is about 1.40 GiB). The confirmation form supports numeric validity from 1 to 366 days; longer or unspecified validity can still be viewed on the official carrier site. Dates are inclusive and labelled **Within entered dates**, **Upcoming** or **Expired**; these labels are not carrier verification of active service. Records can be removed through a confirmation dialog. Delete all local records clears SIM recharge rows too.

These records do not silently change the existing phone-wide Overview allowance. Aggregate Android mobile usage is not split or charged against individual SIM plans by this feature.

## Available recharge offers

The catalogue contains live official directories for Jio, Airtel, Vi and BSNL, opened in browser Custom Tabs. Supported detected carriers are promoted in the directory. Other operators remain unsupported in this directory; no universal global coverage is claimed.

- Jio: https://www.jio.com/selfcare/plans/
- Airtel: https://www.airtel.in/recharge/prepaid/
- Vi: https://www.myvi.in/prepaid
- BSNL: https://www.bsnl.co.in/ (choose service and circle on the official site)

This is a directory into live carrier-published catalogues, not a native cached price comparison API. The carrier may require your number, circle or login to display eligible offers. DataPulse neither auto-fills a phone number nor receives browser credentials or payment information. Viewing or buying an offer does not automatically import or activate a plan in DataPulse. No purchase is automated.

A uniform native searchable catalogue and guaranteed automatic paid-plan discovery would require an authorized, maintained carrier/provider integration. Those integrations are not present in this build. The live official pages and Android-provided data are the supported sources.

## Primary Android references

- [Active subscriptions](https://developer.android.com/reference/android/telephony/SubscriptionManager#getActiveSubscriptionInfoList())
- [Restricted subscription-plan access](https://developer.android.com/reference/android/telephony/SubscriptionManager#getSubscriptionPlans(int))
- [SubscriptionPlan fields and cycle iterator](https://developer.android.com/reference/android/telephony/SubscriptionPlan)
