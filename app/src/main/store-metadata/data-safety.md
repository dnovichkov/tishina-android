# Data Safety declaration — Tisha (Тишина)

Drop-in answers for the Google Play Console **Data Safety** form. Each section below mirrors a question Google asks at submission time. Values are intentionally simple: Tisha does not collect, transmit, or share any data.

Source of truth: this app processes audio strictly in RAM and persists only user-authored measurement metadata to local Room storage. There is no network code, no analytics SDK, no crash reporter, no advertising library, no third-party SDK of any kind in the production manifest.

---

## Question: Does your app collect or share any of the required user data types?

**Answer:** **No.**

The app does not collect or share any of the data categories listed in the Play Console schema (Personal info, Financial info, Health and fitness, Messages, Photos and videos, Audio files, Files and docs, Calendar, Contacts, App activity, Web browsing, App info and performance, Device or other IDs, Location).

Justification per category:

| Category | Collected? | Reasoning |
|---|---|---|
| Personal info (name, email, address, phone, race, sexual orientation, etc.) | **No** | App requests no account, no profile, no contact info. |
| Financial info | **No** | No payments, no in-app purchases, no subscriptions. |
| Health and fitness | **No** | Sound level data stays local; not interpreted as a hearing-health diagnostic. |
| Messages | **No** | No messaging functionality. |
| Photos and videos | **No** | Share Intent emits a transient PNG snapshot of the chart only when the user explicitly taps "Share"; the file is written to the app's own cache and shared via FileProvider. Nothing is collected or sent to our servers (we have no servers). |
| Audio files | **No** | The microphone produces a PCM stream that is processed frame-by-frame in RAM and discarded. No `.wav`/`.mp3`/`.aac` is ever created. |
| Files and docs | **No** | The user can export their own history to CSV via the system Storage Access Framework; the file is written into a location the user picks. We do not collect or read it back. |
| Calendar | **No** | — |
| Contacts | **No** | — |
| App activity (page views, in-app search, installed apps, etc.) | **No** | No analytics. |
| Web browsing | **No** | — |
| App info and performance (crash logs, diagnostics, app performance) | **No** | Play Vitals is managed by Google and is not an SDK we integrate. We do not collect crash reports ourselves. |
| Device or other IDs (AAID, Android ID, IMEI, hardware fingerprint) | **No** | — |
| Location | **No** | App has no `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` permission. |

---

## Question: Is all of the user data collected by your app encrypted in transit?

**Answer:** **Not applicable — no user data is transmitted off the device.**

There is no network code in the production manifest. The release `AndroidManifest.xml` does not declare `android.permission.INTERNET`. (The debug variant inherits it transitively for build-tooling; release does not need it and is not granted it at runtime.)

---

## Question: Do you provide a way for users to request that their data is deleted?

**Answer:** **Yes — uninstalling the app removes all data, because everything is stored only on the device.**

In addition, the in-app UI exposes:

- Per-measurement delete (single tap).
- Bulk delete (selection mode → "Delete (N)") with a 5-second Undo snackbar.
- A "Reset history" affordance is planned for v1.1 if user feedback warrants it.

There is no server-side data to delete because there is no server.

---

## Question: Has your app been independently validated against a global security standard?

**Answer:** No (we have not pursued external security certification for this MVP). The app does not handle personal, financial, or sensitive data, so the surface for which a certification would be meaningful is minimal.

---

## Permissions used (declared in production `AndroidManifest.xml`)

| Permission | Why it is needed |
|---|---|
| `android.permission.RECORD_AUDIO` | Core functionality: sample PCM from the microphone to compute the SPL value. Sound data is processed in RAM and discarded; no audio file is ever created or transmitted. See `permissions-rationale.md`. |

No other dangerous permissions are requested. No background services. No foreground service.

---

## Target audience and content

- **IARC Age Rating:** 3+ (Everyone) — expected outcome of the IARC questionnaire (no violence, no sexual content, no gambling, no profanity, no user-generated content).
- **Designed for Families program:** not opted in. App is not marketed primarily to children.
- **COPPA:** not applicable (no data collection from anyone, including children).
- **GDPR:** not applicable to data collection (no collection happens). The Privacy Policy still meets GDPR transparency requirements as a courtesy.
