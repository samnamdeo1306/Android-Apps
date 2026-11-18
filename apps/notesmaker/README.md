# NotesMaker

Part of the [Android sample applications repository](../../README.md).

NotesMaker helps teachers and instructors turn a class into study notes. Record a live session or import an audio file, and the app transcribes it on the device and produces a title, summary, sections and highlights, with the original audio kept as a voice note. Processed sessions are listed on a separate screen, sortable by title or date.

## Status

Planning. Only documentation exists so far; there is no Gradle project yet.

| Document | Status |
| --- | --- |
| [Product requirements](docs/PRODUCT_REQUIREMENTS.md) | Draft v0.1 |
| [Decision log](docs/DECISIONS.md) | ADR-01 to ADR-09 proposed |
| High-level design | Not started |
| Low-level design and API design | Not started |

Planned stack: Kotlin, Jetpack Compose, Material 3, on-device transcription with whisper.cpp, Room, coroutines and Flow, WorkManager.
