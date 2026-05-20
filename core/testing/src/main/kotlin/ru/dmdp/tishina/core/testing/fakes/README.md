# Fakes

Hand-written `Fake*` implementations of repositories and data sources used by
JVM unit tests. Pulled in via `testImplementation(projects.core.testing)`.

| Fake | Models | Added |
|---|---|---|
| `FakeAudioRepository` | `AudioRepository` (PCM `Flow` + session lifecycle) | Phase 2 |
| `FakePcmAudioSource` | `PcmAudioSource` (low-level PCM stream) | Phase 2 |
| `FakeMeasurementRepository` | `MeasurementRepository` (in-memory `Map<Long, MeasurementDetails>`, monotonic id generator, `seed(...)`/`size()` helpers, reactive `MutableStateFlow`-driven `observeSummaries`, optional `saveError` knob) | Phase 3 |

All fakes are deterministic and intended for `runTest` coroutine harnesses.
`FakeMeasurementRepository` is single-threaded (no internal dispatcher); pair
it with a `MainDispatcherRule` (`:core:testing`) when testing ViewModels.
