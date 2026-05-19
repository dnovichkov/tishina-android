# Fakes

Hand-written `Fake*` implementations of repositories, use cases, and data sources
live here. Tests pull them via `testImplementation(projects.core.testing)`.

Phase 1 (Foundation) has **no fakes** — no domain or data layer exists yet.
The directory ships empty so the package layout is stable when Phase 2 starts
adding `FakeAudioRepository`, `FakeMeasurementRepository`, etc.
