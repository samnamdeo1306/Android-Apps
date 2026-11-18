# Architecture decision records — NotesMaker

This is NotesMaker's living decision log, following the repository conventions in [AGENTS.md](../../../AGENTS.md). Each record has a stable ID, date, status, context, decision, alternatives with tradeoffs, consequences, and implementation status. When a decision changes, add a new record that supersedes the old one and link both, rather than editing away the original reasoning.

The records below were made while writing the [product requirements](PRODUCT_REQUIREMENTS.md). They set product direction and constraints. The HLD and LLD will add records for module structure, dependency injection, persistence and concurrency.

Statuses: **Proposed** (awaiting maintainer review), **Accepted**, **Superseded by ADR-nn**. Facts about third-party libraries were checked against their documentation on 2026-11-18. Performance figures marked "to verify" will be measured in the transcription spike (ADR-02 follow-up) before the HLD fixes them.

| ID | Decision | Reason | Main cost |
| --- | --- | --- | --- |
| [ADR-01](#adr-01--independent-kotlin--compose-sample) | Independent Kotlin + Compose sample under `apps/notesmaker/` | Matches repository conventions and the learning goal | Shares no code with Daybreak |
| [ADR-02](#adr-02--on-device-transcription-by-default-whispercpp-as-the-primary-engine) | On-device transcription by default; whisper.cpp primary engine | Works offline on most devices, on files and recordings, in the background | NDK/JNI complexity, model download, CPU and battery cost |
| [ADR-03](#adr-03--record-first-process-after) | Record first, process after | Simpler, more accurate, avoids foreground-only engine limits | No live captions in 0.1 |
| [ADR-04](#adr-04--layered-note-generation-with-a-guaranteed-baseline) | Layered note generation with a guaranteed baseline | Every device produces notes; better devices produce better notes | Quality varies by device; more code paths to test |
| [ADR-05](#adr-05--cloud-engines-are-opt-in-and-deferred) | Cloud engines are opt-in and deferred | Privacy of classroom audio | Lower quality on low-end devices until added |
| [ADR-06](#adr-06--download-speech-models-on-demand) | Download speech models on demand | Keeps APK small; lets users choose model size | First-use download; needs INTERNET permission |
| [ADR-07](#adr-07--minimum-api-26-target-api-36) | Minimum API 26, target API 36 | Broad reach; consistent with Daybreak | Some newer APIs need fallbacks |
| [ADR-08](#adr-08--keep-source-audio-as-compressed-mono-aac-3-hour-cap) | Keep source audio as compressed mono AAC; 3-hour cap | Small files, plays everywhere, supports the voice-note feature | Decode step before transcription |
| [ADR-09](#adr-09--single-activity-with-compose-navigation) | Single activity with Compose navigation | Modern Compose practice; shared state and transitions | Differs from "separate activity" wording in the brief |

---

## ADR-01 — Independent Kotlin + Compose sample

- **Date:** 2026-11-18
- **Status:** Proposed
- **Implementation:** Not started. Documentation only.

### Context

The repository holds independent Android samples under `apps/<sample>/`, each with its own Gradle project, docs and CI workflow. NotesMaker is a learning project focused on Jetpack Compose and advanced Android topics.

### Decision

Create NotesMaker as an independent Gradle project at `apps/notesmaker/`, written in Kotlin with Jetpack Compose and Material 3. It shares no modules with Daybreak.

### Alternatives considered

| Alternative | Assessment |
| --- | --- |
| Shared `core` library with Daybreak | The two apps have almost no common domain. The repository conventions say to extract shared code only when multiple apps actually need it. |
| Kotlin Multiplatform | Interesting, but widens the scope beyond the Android and Compose learning goal. Domain code kept free of Android dependencies (NFR-64) leaves this path open. |
| XML Views | Contradicts the stated goal of learning Compose. |

### Consequences

Independent toolchain upgrades and build. Some boilerplate (Gradle setup, theme) is repeated from Daybreak.

---

## ADR-02 — On-device transcription by default, whisper.cpp as the primary engine

- **Date:** 2026-11-18
- **Status:** Proposed
- **Implementation:** Not started. A benchmark spike is the first follow-up.

### Context

The maintainer prefers transcription on the device. The app must transcribe both **recorded** and **imported** audio, 30 to 180 minutes long, with timestamps (FR-31) and punctuation (FR-32). Lecture audio can contain students' voices, so keeping it local has real privacy value. Recording and processing may happen while the app is in the background.

**Is on-device transcription feasible?** Yes. Several mature options run fully offline on Android today. The question is which one fits long-form, file-based transcription across a range of devices.

### Options evaluated

| Option | How it works | Strengths | Weaknesses for NotesMaker |
| --- | --- | --- | --- |
| **whisper.cpp** (OpenAI Whisper models in C/C++ via NDK/JNI) | Bundle the native library; download a ggml model file (about 75 MB tiny, 142 MB base, 466 MB small at full precision; roughly a third of that when quantized). | Works on any arm64 device with no vendor dependency. Handles files of any length by chunking. Produces punctuation and segment timestamps. Multilingual models available; English-only `.en` models are more accurate for English. MIT license. Runs in a background service. | Must build and maintain NDK/JNI integration. CPU-heavy: battery and heat on long files. Speed varies widely by device (base expected near or faster than real time on recent mid-range phones; small may be slower: to verify). Can occasionally repeat or invent text on silence, which needs voice activity detection and chunking. |
| **ML Kit GenAI Speech Recognition** (Google, Gemini Nano or a basic on-device model) | Kotlin API; accepts microphone streaming or files (MP3, WAV, FLAC, M4A, OGG). | Simple Kotlin API, no model management by the app, good quality in Advanced mode, about 21 locales. | **Alpha**, no stability guarantee. Advanced mode only on a short list of recent flagship chipsets (Pixel 10/11, Galaxy S26 class); Basic mode needs API 31+. Gemini Nano inference is blocked when the app is not the top foreground app, including from a foreground service, and is subject to per-app battery quotas. Word timing is not available for file input. |
| **Android platform `SpeechRecognizer`** (on-device recognizer, API 31+; audio input from a file descriptor on API 33+) | System recognition service. | No extra download on many devices; zero app size. | Designed for short voice commands and dictation, not hour-long files. Behavior, language packs and limits vary by device maker. Limited timestamp support. Hard to test reliably. |
| **Vosk** (Kaldi-based, offline) | Java/Android library with small models (about 40 to 50 MB for English). | Small, fast, streaming-friendly, low memory, Apache 2.0. | Noticeably lower accuracy on lecture speech than Whisper; small models produce no punctuation or capitalization, which hurts note generation. |
| **Cloud speech-to-text** (for example Google Cloud, Deepgram, OpenAI) | Upload audio to a provider. | Highest accuracy and speed, diarization available. | Audio leaves the device; needs network, API keys and cost. Contradicts the stated preference. See ADR-05. |

### Decision

1. **Transcription runs on the device by default** (FR-30). Cloud is an opt-in extension only (ADR-05).
2. Transcription is defined by a domain interface (working name `TranscriptionEngine`) with a capability check (NFR-65). The pipeline does not know which engine runs.
3. **whisper.cpp is the primary engine for 0.1**, with an English-only quantized base model as the default and a small model as the "more accurate" option (FR-35). Final model choice depends on the spike results.
4. **ML Kit GenAI Speech Recognition is a planned second adapter** for supported devices once it leaves alpha, or earlier as a learning exercise. It must respect its foreground-only constraint (see ADR-03).
5. Vosk and the platform recognizer are not planned. Vosk may become a "fast draft" option if whisper.cpp proves too slow on the reference device.

### Reasoning

whisper.cpp is the only option that meets all of: any recent arm64 device, file input of hours, timestamps and punctuation, background execution, and no data leaving the device. Its main cost, NDK/JNI integration, is also valuable learning for an advanced Android project. Keeping ML Kit behind the same interface demonstrates the Open/Closed principle with a real second implementation (NFR-60, NFR-66).

### Tradeoffs accepted

- Model download of roughly 60 to 500 MB (ADR-06).
- Processing may take about as long as the lecture on mid-range devices, and drains battery. Mitigated by background processing, notifications and the option to defer on low battery (FR-25).
- The app owns native-code builds for `arm64-v8a` (and `x86_64` for emulators), adding APK size of a few MB.

### Follow-up and verification

1. **Spike:** transcribe a 10-minute and a 60-minute lecture-style clip with tiny.en, base.en and small.en (quantized) on the reference device. Record real-time factor, peak memory, battery drop and word error rate. Update NFR-11 and NFR-13 with measured targets.
2. Try voice activity detection to skip silence and reduce invented text.
3. Re-evaluate ML Kit GenAI Speech Recognition when it reaches beta.

---

## ADR-03 — Record first, process after

- **Date:** 2026-11-18
- **Status:** Proposed
- **Implementation:** Not started.

### Context

The brief says the app should prepare notes "by listening to their live sessions or accepting audio recordings." That can mean live, as-you-speak transcription, or recording the live session and processing it afterwards.

### Decision

For 0.1, **record the live session to a file, then process it** through the same pipeline as an imported file. Processing runs as background work (WorkManager with a foreground notification) that is resumable per stage (FR-20 to FR-24). Live captions during recording are planned for 0.3.

### Alternatives considered

| Alternative | Assessment |
| --- | --- |
| Live streaming transcription during recording | Gives immediate feedback, but runs the microphone, encoder and recognizer together for up to 2 hours, raising heat, battery and failure risk. Streaming models are usually less accurate than batch transcription with full context. Gemini Nano-based engines refuse to run from a foreground service, so they could not transcribe with the screen off. Two separate code paths (live and file) for the same result. |
| Live transcription plus batch re-transcription afterwards | Best of both, but doubles complexity. A natural 0.3 feature once the batch path is solid. |

### Reasoning

One pipeline for both inputs keeps the core simple and testable (NFR-61, NFR-63). Batch processing allows chunking, voice activity detection and full-context accuracy. Notes need the whole lecture anyway to produce sections and a summary, so live text adds little to the main outcome.

### Tradeoffs accepted

The teacher sees nothing but a recording timer during class. Notes arrive after processing, possibly an hour later on slower devices.

### Consequences

- Recording uses a foreground service of type `microphone`. Processing uses expedited or long-running WorkManager work with a foreground notification, using the `mediaProcessing` foreground service type where available (Android 15+) and `dataSync` on older versions. Exact choices go to the HLD.
- Any engine that requires the app to be in the foreground (ML Kit GenAI) can only run when the user opens the app. The pipeline must be able to pause such a stage until the app is in the foreground, or choose a different engine.

---

## ADR-04 — Layered note generation with a guaranteed baseline

- **Date:** 2026-11-18
- **Status:** Proposed
- **Implementation:** Not started.

### Context

Each session needs a title, summary, sections and highlights (FR-40 to FR-45). Good results need a language model. A 60-minute lecture produces roughly 8,000 to 10,000 words (about 11,000 to 14,000 tokens).

### Options evaluated

| Option | Strengths | Weaknesses |
| --- | --- | --- |
| **ML Kit GenAI Summarization API** (Gemini Nano) | Very simple API. On device. | Beta. Output is only 1 to 3 bullets. Input under 4,000 tokens. English, Japanese and Korean only. Limited device list. Cannot produce a title, sections or highlights. Too narrow on its own. |
| **ML Kit GenAI Prompt API** (Gemini Nano, free-form prompts, structured output in alpha) | On device, flexible: one model can produce titles, sections and highlights with suitable prompts. No model download managed by the app. | Beta. Runs only on Gemini Nano devices (recent flagships). Foreground-only, per-app quotas. Small context, so long transcripts need chunking. |
| **Self-hosted small LLM** (for example a Gemma model through Google's LiteRT-LM or MediaPipe LLM inference, or llama.cpp) | Runs on more devices than Gemini Nano; full control over model and prompts; background allowed. | Large download (roughly 0.5 to 3 GB), heavy memory use, slow on mid-range phones. Significant integration effort. |
| **Cloud LLM** (for example Gemini, Claude or OpenAI APIs) | Best quality, large context: a whole lecture in one request. Fast. | Transcript leaves the device; needs key, network and cost. See ADR-05. |
| **Basic built-in method** (no LLM): extractive summary by sentence scoring (for example TextRank), sections by time windows and topic shifts, title from top keywords, highlights by rules (definitions, "important", "remember", dates, "due") | Works on every device, instantly, offline, deterministic and unit-testable. | Clearly lower quality: no paraphrasing, rough section boundaries, keyword-style titles. |

### Decision

1. Note generation is a domain interface (working name `NoteGenerator`) with a capability check. Generators are tried in a **policy order** (Strategy and Chain of Responsibility patterns):
   1. A cloud generator, only if the user has opted in (0.3+).
   2. **On-device LLM via the ML Kit GenAI Prompt API** when the device supports it and the app is in the foreground.
   3. **Basic built-in generator**, always available. Guarantees FR-44.
2. Long transcripts use a **chunk-then-combine** approach for any generator with a limited context: summarize fixed-size chunks aligned to transcript segments, then combine chunk results into the final title, summary, sections and highlights. The chunking logic is shared and tested in the domain layer.
3. Notes record which generator produced them, so the UI can label them and the user can regenerate with another (FR-46).
4. A self-hosted LLM is a candidate later adapter if Gemini Nano coverage proves too narrow. It needs no change to the pipeline.

### Reasoning

No single on-device option works everywhere today. A guaranteed baseline means the core promise (notes for every session) does not depend on owning a flagship phone, while better hardware automatically produces better notes. The design shows the Strategy pattern solving a real problem rather than a contrived one.

### Tradeoffs accepted

- Note quality varies by device, and the baseline will feel mechanical. This is disclosed in the UI.
- Prompt API notes require the app in the foreground. If the user is away, the pipeline completes with baseline notes and offers "Improve notes" when the app is next open, rather than blocking.
- More code paths to test. Each generator must pass a shared contract test suite (NFR-71).

### Follow-up

Decide in the HLD how notes are represented (a structured model, not free text) so every generator fills the same shape. Prototype prompts and measure quality on the spike transcripts.

---

## ADR-05 — Cloud engines are opt-in and deferred

- **Date:** 2026-11-18
- **Status:** Proposed
- **Implementation:** Not planned before 0.3.

### Context

Cloud transcription and note generation give the best quality, especially on low-end devices, but send classroom audio or transcripts to a third party.

### Decision

No cloud engine ships in 0.1 or 0.2. The interfaces from ADR-02 and ADR-04 allow adding them later as adapters. When added, they are **off by default**, enabled per user with a clear statement of what is sent where, use a user-supplied key stored encrypted, and are never chosen silently as a fallback.

### Alternatives

Shipping a cloud fallback in 0.1 would raise quality on low-end devices, but requires a key management story, network error handling and a privacy notice before the core is proven. It also contradicts the on-device preference.

### Consequences

The app needs only the `INTERNET` permission for model downloads in 0.1. Low-end devices get baseline notes until a cloud or self-hosted option arrives.

---

## ADR-06 — Download speech models on demand

- **Date:** 2026-11-18
- **Status:** Proposed
- **Implementation:** Not started.

### Context

whisper.cpp models range from about 30 MB to over 400 MB. Bundling them makes the APK large and fixes one model size for everyone.

### Decision

Download models on first need, after showing the size and asking (FR-33). Use Wi-Fi by default, support resuming, and verify a SHA-256 checksum from a pinned manifest in the app before use. Models are stored in app-private storage and listed in Settings (FR-70).

### Alternatives considered

| Alternative | Assessment |
| --- | --- |
| Bundle the default model in the APK | Works offline from install, but adds 60 to 150 MB to every build and every CI artifact. Users cannot choose size. |
| Play Asset Delivery (install-time or on-demand asset packs) | The right answer for a Play Store release, with Google hosting the files. Requires App Bundles and Play distribution, which is not a 0.1 goal. Revisit for release (constraint 8 in the PRD). |
| Ask users to supply their own model file | Flexible but poor experience. Could be an advanced option. |

### Tradeoffs accepted

The app needs network access once. The model host (for example the public Hugging Face repository for whisper.cpp models) must stay available; the source URL is configurable.

---

## ADR-07 — Minimum API 26, target API 36

- **Date:** 2026-11-18
- **Status:** Proposed
- **Implementation:** Not started.

### Decision

`minSdk 26`, `compileSdk` and `targetSdk 36`, matching Daybreak.

### Reasoning and tradeoffs

API 26 covers almost all active devices and matches the repository's other sample. whisper.cpp has no higher requirement. Features that need newer APIs (the `mediaProcessing` foreground service type on Android 15, ML Kit GenAI Basic speech recognition on API 31, per-app language on API 33) are optional and guarded by version checks. A higher minimum such as API 29 would remove some legacy storage and foreground service handling, but the gain is small because the app uses only app-private storage and the system file picker.

---

## ADR-08 — Keep source audio as compressed mono AAC, 3-hour cap

- **Date:** 2026-11-18
- **Status:** Proposed
- **Implementation:** Not started.

### Context

The original audio is the session's voice note (FR-61) and must be kept. Speech models expect 16 kHz mono PCM.

### Decision

- Record to AAC in an M4A container, mono, 16 to 24 kHz sample rate, about 64 kbps (standard quality, about 30 MB per hour). A high-quality option uses a higher bitrate (FR-72).
- Imported files are copied as-is if they decode correctly; they are not re-encoded.
- Decode to 16 kHz mono PCM only while transcribing, in streamed chunks, never storing the full PCM (a 3-hour PCM file would be about 345 MB).
- Cap sessions at 3 hours for both recording and import (FR-12), keeping processing time, memory and storage predictable.

### Alternatives

| Alternative | Assessment |
| --- | --- |
| Record directly to WAV/PCM | No decode step, but about 115 MB per hour. Poor for long-term storage of voice notes. |
| Opus in OGG | Better quality per bit than AAC, but encoding support on older Android versions is weaker. AAC is supported on all target devices. |
| No length cap | Unbounded processing time and storage; harder to test. The cap can be raised later. |

---

## ADR-09 — Single activity with Compose navigation

- **Date:** 2026-11-18
- **Status:** Proposed, **needs maintainer confirmation**
- **Implementation:** Not started.

### Context

The brief asks for sessions to be listed "in a separate activity" and for the main activity to handle session processing. In modern Compose apps, "screen" usually replaces "activity".

### Decision

Use **one activity** hosting Compose navigation, with separate **screens** (destinations) for Capture (start destination), Sessions, Session details and Settings. This document reads "separate activity" as "separate screen".

### Alternatives

| Alternative | Assessment |
| --- | --- |
| One Android activity per screen | Matches the brief literally. But it splits Compose state and theme across activities, makes shared-element and navigation transitions harder, and is no longer the recommended approach. |
| Single activity, Compose navigation | Recommended practice; simple back stack, deep links (for example a notification opening a session), and easy testing. Each screen still has its own ViewModel and state, so the separation of concerns the brief wants is kept. |

### Consequences

Notifications deep-link to destinations. If the maintainer specifically wants to learn multi-activity patterns, this record will be superseded.

---

## Sources

- ML Kit GenAI overview (APIs, status, device lists, foreground and quota limits): https://developers.google.com/ml-kit/genai
- ML Kit GenAI Speech Recognition: https://developers.google.com/ml-kit/genai/speech-recognition/android
- ML Kit GenAI Summarization: https://developers.google.com/ml-kit/genai/summarization/android
- Android Developers Blog, on-device inference (July 2026): https://android-developers.googleblog.com/2026/07/android-on-device-inference.html
- whisper.cpp: https://github.com/ggml-org/whisper.cpp
- Vosk: https://alphacephei.com/vosk/
- Android `RecognizerIntent` reference: https://developer.android.com/reference/android/speech/RecognizerIntent
