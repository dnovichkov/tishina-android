# Permissions rationale — Tisha (Тишина)

Copy-paste source for the **Permissions declaration** form in Google Play Console, RuStore moderator notes, and Samsung Galaxy Store reviewer comments.

Tisha requests **one** runtime permission. There are no background services and no foreground services. The microphone stream is opened on demand, fed through DSP in RAM, and released the moment the user pauses or leaves the Measure screen.

---

## `android.permission.RECORD_AUDIO`

### Short rationale (≤ 200 chars — Google Play API field limit)

Core functionality: measuring ambient sound levels in real time. No audio is recorded to a file or transmitted anywhere. PCM samples are processed in memory and discarded.

### Long rationale (for moderator notes)

Tisha is a sound level meter — its sole purpose is to compute the current sound pressure level (SPL) in dB(A) from the device microphone. To do this it opens an `AudioRecord` stream when the user starts a measurement, runs each PCM frame through DC-blocking, A-weighting, FAST/SLOW time-weighting, and RMS calculation entirely in RAM, then displays the resulting dB(A) value. The PCM samples themselves are never written to a file, never copied off the device, and never persisted across the lifetime of a single frame.

When the user pauses or leaves the Measure screen, `AudioRecord.stop()` and `AudioRecord.release()` are called immediately. No background service holds the microphone open. The release manifest does not declare `FOREGROUND_SERVICE` or `FOREGROUND_SERVICE_MICROPHONE`.

What is persisted to local Room storage after a measurement is saved:

- Start timestamp, duration in milliseconds.
- Aggregated statistics: min, average (Leq), max in dB(A).
- A user-authored title and note (both optional).
- The 5 Hz sample buffer for the duration of the saved measurement (numeric values, not audio).

No audio file is ever stored. The 5 Hz sample stream is post-DSP dB(A) numbers, not PCM.

### Why the user benefits

The microphone is the only on-device sensor that can measure sound. Denying the permission disables the only feature the user installed the app for. The app explains this in the runtime permission rationale dialog before the system prompt is shown.

### What happens if permission is denied

Tisha shows a friendly explanation on the Measure screen with a button to grant the permission from Settings. No other feature relies on the microphone. The History, Detail, Settings, and About screens remain fully usable for reviewing past measurements.

---

## Permissions that Tisha intentionally does NOT request

| Permission | Why we do not request it |
|---|---|
| `INTERNET` | App is fully offline. No analytics, no sync, no remote config. |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Location is not relevant to measuring sound level. |
| `WRITE_EXTERNAL_STORAGE` / `READ_MEDIA_AUDIO` | CSV export uses the Storage Access Framework — the user picks a destination via the system file picker, no broad storage access is needed. |
| `FOREGROUND_SERVICE_MICROPHONE` | Tisha does not measure in the background. The Measure screen must be visible for a measurement to run. |
| `POST_NOTIFICATIONS` | Tisha posts no notifications. |
| `BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN` | Bluetooth microphones are out of scope for the v1.0 MVP. |
| Advertising ID | No advertising. |
