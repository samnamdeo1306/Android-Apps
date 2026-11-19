# Architecture decision records — NotesMaker

This is NotesMaker's living decision log, following the repository conventions in [AGENTS.md](../../../AGENTS.md). Each record has a stable ID, date, status, context, decision, alternatives with tradeoffs, consequences, and implementation status. When a decision changes, add a new record that supersedes the old one and link both, rather than editing away the original reasoning.

ADR-01 to ADR-09 were made while writing the [product requirements](PRODUCT_REQUIREMENTS.md) and set product direction and constraints. ADR-10 to ADR-18 were made while writing the [high-level design](HIGH_LEVEL_DESIGN.md) and set structure, dependency injection, processing, recording, persistence and navigation. ADR-19 set the benchmark device tiers. ADR-20 to ADR-30 were made while writing the [low-level design](LOW_LEVEL_DESIGN.md) and [internal API design](API_DESIGN.md) and set error handling, engine and pipeline contracts, the schema, the native build, the download client, recording logic, prompts, voice activity detection, screen boundaries and test doubles.

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
| [ADR-10](#adr-10--layered-architecture-with-ports-and-adapters-mvvm-in-the-ui) | Layered architecture with ports and adapters; MVVM with unidirectional data flow | Domain logic testable on the JVM; adapters replaceable | More files and mapping code |
| [ADR-11](#adr-11--gradle-modules-by-layer-feature-and-engine-with-convention-plugins) | Modules by layer, feature and engine, with convention plugins | Compiler-enforced boundaries; heavy engines isolated | About 14 modules for a small app |
| [ADR-12](#adr-12--hilt-for-dependency-injection-multibinding-as-the-engine-registry) | Hilt, with multibinding as the engine registry | Plug-in engines without editing a central list; compile-time checks | Annotation processing, learning curve |
| [ADR-13](#adr-13--database-backed-processing-queue-drained-by-one-workmanager-worker-with-chunk-level-checkpoints) | Database-backed queue, one WorkManager worker, chunk-level checkpoints | One source of truth for status; cheap resume | Resume after reboot may wait until the app opens |
| [ADR-14](#adr-14--crash-safe-recording-audiorecord-and-mediacodec-to-adts-remuxed-to-m4a-on-stop) | Record with AudioRecord + MediaCodec to ADTS, remux to M4A | Audio survives crashes; PCM available for level and future live captions | More code than MediaRecorder |
| [ADR-15](#adr-15--engine-registry-and-capability-based-selection-policy) | Capability-based engine selection; never block on the foreground | Notes always produced; new engines need no policy edits | Notes quality depends on when processing ran |
| [ADR-16](#adr-16--structured-notes-model-llm-generators-share-one-chunk-then-combine-implementation) | Structured notes; shared chunk-then-combine over a `LanguageModel` port | Timestamps and edits per item; one tested chunking path | Generic chunking for every model |
| [ADR-17](#adr-17--navigation-3-with-an-adaptive-list-detail-layout) | Navigation 3 with adaptive list-detail | Back stack as state; tablet layouts | Newer library, fewer examples |
| [ADR-18](#adr-18--sort-titles-in-memory-with-a-locale-aware-collator-no-paging-in-01) | Locale-aware title sort in memory; no Paging | Correct accented sorting; simple | Loads all list rows at once |
| [ADR-19](#adr-19--benchmark-device-tiers-emulator-baseline-galaxy-a-series-and-pixel-8-pro) | Emulator baseline; Galaxy A-series and Pixel 8 Pro for speed targets | Fast daily iteration; realistic targets on real phones | Emulator speed is not representative |
| [ADR-20](#adr-20--expected-failures-are-typed-values-exceptions-are-for-bugs) | Typed `Outcome` values for expected failures | Compiler-checked failure handling (NFR-23) | Some mapping boilerplate |
| [ADR-21](#adr-21--transcription-engines-receive-the-file-and-emit-committed-segments) | Engines take the file and emit committed segments | Fits whisper.cpp and ML Kit; domain owns checkpoints | Each engine handles resume |
| [ADR-22](#adr-22--session-status-changes-are-compare-and-set-transitions-from-one-table) | Status changes are compare-and-set transitions | Cancel/complete races resolved in the database | Callers handle "not applied" |
| [ADR-23](#adr-23--room-schema-normalized-notes-tables-string-enums-composite-child-keys) | Normalized notes tables, string enums, `(session_id, idx)` keys | Idempotent stages, safe migrations | JSON columns not searchable |
| [ADR-24](#adr-24--build-whispercpp-from-a-pinned-release-archive-with-cmake-fetchcontent) | whisper.cpp via CMake FetchContent, pinned by hash | Reproducible, no submodule in a shared repo | First build needs network |
| [ADR-25](#adr-25--download-models-with-httpurlconnection-behind-a-small-interface) | `HttpURLConnection` for model download | No runtime dependency for one call site | Verbose API |
| [ADR-26](#adr-26--recording-logic-is-a-pure-reducer-the-service-only-performs-effects) | Recording as a pure reducer plus effects | Hardest flow testable on the JVM | Logic split across two files |
| [ADR-27](#adr-27--llm-notes-use-versioned-prompt-templates-and-json-output-validated-by-one-validator) | Versioned prompts, JSON output, one validator | Robust structure; comparable prompt versions | Strictness causes some fallbacks |
| [ADR-28](#adr-28--energy-based-voice-activity-detection-first-whispercpp-vad-evaluated-in-the-spike) | Energy VAD first; whisper.cpp VAD in the spike | Works before the spike, no extra model | May miss quiet speech |
| [ADR-29](#adr-29--screens-call-use-cases-for-every-write-feature-modules-expose-only-entries-and-navigator-interfaces) | Writes via use cases; features expose entries and navigators | Rules stay in the domain; features independent | A few small classes |
| [ADR-30](#adr-30--shared-fakes-and-contract-suites-in-testfixtures-no-mocking-library) | Shared fakes and contract suites in `testFixtures` | Faithful doubles, one place | Fakes need upkeep |

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
5. Vosk and the platform recognizer are not planned. Vosk may become a "fast draft" option if whisper.cpp proves too slow on the mid-range test phone (ADR-19).

### Reasoning

whisper.cpp is the only option that meets all of: any recent arm64 device, file input of hours, timestamps and punctuation, background execution, and no data leaving the device. Its main cost, NDK/JNI integration, is also valuable learning for an advanced Android project. Keeping ML Kit behind the same interface demonstrates the Open/Closed principle with a real second implementation (NFR-60, NFR-66).

### Tradeoffs accepted

- Model download of roughly 60 to 500 MB (ADR-06).
- Processing may take about as long as the lecture on mid-range devices, and drains battery. Mitigated by background processing, notifications and the option to defer on low battery (FR-25).
- The app owns native-code builds for `arm64-v8a` (and `x86_64` for emulators), adding APK size of a few MB.

### Follow-up and verification

1. **Spike:** transcribe a 10-minute and a 60-minute lecture-style clip with tiny.en, base.en and small.en (quantized) on the test devices from ADR-19. Record real-time factor, peak memory, battery drop and word error rate. Update NFR-11 and NFR-13 with measured targets.
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

## ADR-10 — Layered architecture with ports and adapters, MVVM in the UI

- **Date:** 2026-11-18
- **Status:** Proposed
- **Implementation:** Not started. Described in [HLD](HIGH_LEVEL_DESIGN.md) Section 3.

### Context

The brief asks for a modular design following SOLID, with abstractions so new features need minimal change to the core. NFR-60 to NFR-64 make that checkable: engines, inputs, outputs and stages are replaceable, and the domain is plain Kotlin tested on the JVM.

### Decision

Three layers with dependencies pointing inward: **presentation** (Compose + ViewModels), **domain** (model, use cases, pipeline, ports) and **adapters** (Room, files, MediaCodec, whisper.cpp, ML Kit, WorkManager). The domain owns every interface it needs from the outside (Dependency Inversion). The UI uses **MVVM with unidirectional data flow**: one immutable `UiState` per screen as a `StateFlow`, user actions as ViewModel functions, stateless screen composables. This matches the pattern Daybreak settled on (Daybreak ADR-16), so the two samples are comparable.

### Alternatives considered

| Alternative | Assessment |
| --- | --- |
| Two layers (UI + data), no separate domain | Less code, and fine for a CRUD app. But the pipeline, engine selection and chunking are real business logic that must be JVM-testable and independent of WorkManager and Room. It would end up inside ViewModels or workers. |
| MVI with a reducer and a single event stream | Very explicit state transitions, good for learning. Adds boilerplate (intent sealed classes, reducer) for screens whose state is mostly "observe a Flow". Can be adopted per screen later; the Capture screen's recording state machine is the most likely candidate. |
| Full clean architecture with a use case per repository call and separate data-transfer models per layer | Maximum separation, but many pass-through classes that teach little. Use cases exist only where they hold logic or coordinate more than one port. |

### Tradeoffs accepted

More files and indirection than a two-layer app. Mapping between Room entities and domain models is written by hand.

---

## ADR-11 — Gradle modules by layer, feature and engine, with convention plugins

- **Date:** 2026-11-18
- **Status:** Proposed
- **Implementation:** Not started. Module table in [HLD](HIGH_LEVEL_DESIGN.md) Section 4.

### Context

Daybreak uses three modules and its architecture notes warn against splitting every feature at that scale. NotesMaker is different in two ways: it has heavy, optional technology (an NDK build, ML Kit) that should not leak into the rest of the app, and modular extensibility is an explicit goal and learning topic.

### Decision

Modules: `:app`; `:core:domain` (pure Kotlin), `:core:data`, `:core:media`, `:core:processing`, `:core:designsystem`, `:core:ui`; one module per screen group (`:feature:capture`, `:feature:sessions`, `:feature:session`, `:feature:settings`); one module per engine (`:engine:whisper`, `:engine:notes-basic`, `:engine:notes-gemini-nano`). Shared build configuration lives in convention plugins in an included `build-logic` build. Features never depend on each other or on adapter modules; engines depend only on the domain.

### Reasoning

- Engine modules make Open/Closed visible: adding an engine is adding a module, and the compiler proves it touches nothing else. The native whisper build is isolated, so editing a screen does not rebuild it.
- Feature modules enforce that screens use only the domain. Without the module boundary, a ViewModel importing a DAO is one auto-import away.
- Convention plugins and multi-module builds are themselves "advanced Android" topics the project wants to learn.

### Alternatives considered

| Alternative | Assessment |
| --- | --- |
| Daybreak's three modules (`app`, `core:domain`, `core:data`) | Simplest. But whisper.cpp and ML Kit would sit in `app` or `core:data`, and nothing would stop a screen from using an engine directly. |
| Fewer, coarser modules: one `:engines` module | Fewer builds to configure, but an ML Kit change would rebuild native code and every engine's dependencies would be on every engine's classpath. |
| API/implementation split for every feature | Useful in large teams for build speed; overkill for one maintainer and four screens. |

### Tradeoffs accepted

About 14 modules plus `build-logic` for a small app: slower first build, more Gradle files, a steeper start for a reader. Mitigated by convention plugins and the module map. Merging modules later does not touch the domain.

---

## ADR-12 — Hilt for dependency injection, multibinding as the engine registry

- **Date:** 2026-11-18
- **Status:** Proposed
- **Implementation:** Not started.

### Context

Daybreak deliberately uses a hand-written composition root. NotesMaker has more moving parts: ViewModels in four feature modules, two kinds of workers, a service, and plug-in sets of engines and exporters that must be collected without a central list naming each one.

### Decision

Use **Hilt** (with KSP). Engines, note generators and exporters register with `@IntoSet` multibindings, and the domain receives `Set<TranscriptionEngine>` and similar sets. Workers use `@HiltWorker`. The domain module depends only on the tiny `javax.inject` annotation artifact, not on Hilt or Dagger.

### Alternatives considered

| Alternative | Assessment |
| --- | --- |
| Manual composition root (Daybreak's approach) | No annotation processing, everything explicit, easy to read. But every new engine edits the root by hand, worker and ViewModel factories across feature modules are boilerplate, and it repeats what Daybreak already teaches. |
| Koin | Simple Kotlin DSL and no code generation. Errors appear at runtime instead of compile time, and it is less common in Google's Android guidance and samples. |
| Keep the domain annotation-free and provide every use case with `@Provides` | Purer, but dozens of hand-written provider functions. `javax.inject` is a pure JVM annotation and keeps NFR-64 intact. |

### Tradeoffs accepted

Annotation processing adds build time; Hilt's generated code and scoping rules are a learning curve; errors can be verbose. Compile-time verification of the graph is the main payoff.

---

## ADR-13 — Database-backed processing queue drained by one WorkManager worker, with chunk-level checkpoints

- **Date:** 2026-11-18
- **Status:** Proposed
- **Implementation:** Not started. [HLD](HIGH_LEVEL_DESIGN.md) Section 6.3.

### Context

FR-20 to FR-24 require background processing that shows progress, processes one session at a time in order, survives app kill and reboot, and resumes from the last incomplete stage. Transcribing one session can take an hour.

### Decision

1. The **Room database is the queue**: `Waiting` sessions ordered by a queue timestamp.
2. One **unique WorkManager job** (`session-processing`, `ExistingWorkPolicy.KEEP`) drains the queue in a loop. Enqueuing a session only ensures the job exists.
3. The worker runs as a foreground service: `mediaProcessing` on Android 15+, `dataSync` below.
4. Progress, stage and failure are written to the session row. **The UI observes only the database**, never `WorkInfo`.
5. Each stage has a checkpoint. The `Transcribe` stage checkpoints **per 30-second window** (segments plus last audio position), so a crash late in a long file loses at most one window rather than the whole stage.

### Alternatives considered

| Alternative | Assessment |
| --- | --- |
| One WorkManager request per session, chained with `APPEND` | WorkManager becomes the queue. Reordering, cancelling one session and showing "Waiting" all require reading `WorkInfo` and mapping it back to sessions; a failed request in an append chain also fails or cancels the ones after it unless carefully configured. Two sources of truth for status. |
| One WorkManager request per stage (prepare, transcribe, notes) | Neat mapping of stage to work, but stage outputs must be passed between requests through storage anyway, and one-at-a-time ordering across sessions gets harder. |
| A long-running foreground service without WorkManager | Full control, but the app must reimplement persistence of pending work, retries, backoff and reboot rescheduling. |
| Stage-level checkpoints only | Simpler, but a crash at minute 55 of a 60-minute transcription repeats 55 minutes of work. Too costly for hours-long sessions. |

### Tradeoffs accepted

- The worker loop must handle cancellation of the current session without stopping the queue, which is slightly more code than per-session requests.
- On Android 15+, `dataSync` and `mediaProcessing` foreground services have a 6-hour daily limit and cannot be started from `BOOT_COMPLETED`; Android 12+ also limits foreground service starts from the background. After a reboot, processing may only resume when the user opens the app. Checkpoints keep that cheap.
- Writing segments per window means more, smaller transactions during transcription. Negligible against the cost of transcription itself.

---

## ADR-14 — Crash-safe recording: AudioRecord and MediaCodec to ADTS, remuxed to M4A on stop

- **Date:** 2026-11-18
- **Status:** Proposed
- **Implementation:** Not started. [HLD](HIGH_LEVEL_DESIGN.md) Section 6.1.

### Context

ADR-08 stores the voice note as AAC in an M4A container. FR-05 and NFR-20 require that audio captured before a crash or process death is never lost, flushed at least every 5 seconds. An MP4/M4A file is only readable once its index (`moov` box) is written at close, so a file written by `MediaRecorder` and cut off by a crash is normally unplayable.

### Decision

The recording service reads PCM with `AudioRecord`, encodes with `MediaCodec` (AAC-LC, mono) and appends frames with **ADTS headers** to `audio.aac`, flushing at least every 5 seconds. Each ADTS frame is self-describing, so a truncated file is still valid up to its last full frame. On stop, or on start-up recovery after a crash, the ADTS stream is **remuxed** (no re-encoding) into `audio.m4a` with `MediaMuxer`; the ADTS file is deleted only after the M4A is verified. Live input level (FR-01) is computed from the PCM buffers.

### Alternatives considered

| Alternative | Assessment |
| --- | --- |
| `MediaRecorder` writing M4A | Least code; built-in pause and resume. A crash leaves an unplayable file, breaking FR-05. |
| `MediaRecorder` with segment rotation (new file every few minutes) | Loses at most one segment, but the last segment is still lost and segments must be concatenated afterwards. |
| `MediaRecorder` writing raw AAC ADTS output | Crash-safe container, but no access to PCM for the level meter or for future live captions, and less control over pause handling. |
| Record WAV/PCM | Trivially crash-safe, but about 115 MB per hour (rejected in ADR-08). |

### Reasoning

Owning the PCM stream solves crash safety, gives an accurate level meter, and is the natural tap point for live captions planned in 0.3 (ADR-03): PCM can be fed to the encoder and a recognizer at the same time.

### Tradeoffs accepted

More code than `MediaRecorder`: buffer handling, encoder lifecycle, timestamps across pauses, and a remux step. Codec behavior can vary by device, so this needs an instrumented round-trip test (HLD risk R-4).

---

## ADR-15 — Engine registry and capability-based selection policy

- **Date:** 2026-11-18
- **Status:** Proposed
- **Implementation:** Not started. [HLD](HIGH_LEVEL_DESIGN.md) Section 6.3, "Engine selection".

### Context

NFR-65 requires each engine to report whether it can run, and the app to choose by a defined policy instead of scattered device checks. ADR-04 requires notes to be produced even when the best generator cannot run because the app is in the background.

### Decision

Every engine reports a `Capability`: `Ready`, `NeedsModel`, `NeedsForeground`, `NeedsOptIn` or `Unsupported`. A domain `EngineSelector` takes the registered set (ADR-12), removes engines that are unsupported or not opted into, orders the rest by user preference and then by the engine's declared quality rank, and returns the first `Ready` engine together with a flag saying whether a better engine was only blocked by `NeedsForeground`. The pipeline never blocks waiting for the foreground: it uses the best engine available now and sets `upgradeAvailable` on the session, and the details screen offers "Improve notes".

### Alternatives considered

| Alternative | Assessment |
| --- | --- |
| Pause the notes stage until the app is in the foreground | Best notes eventually, but a session can stay unfinished for hours and FR-44 says notes are always produced. |
| Fixed engine order in code (`if (geminiAvailable) ... else ...`) | Simple with two engines, but every new engine edits that code, which NFR-60 forbids. |
| Let the user always choose the engine manually | Transparent, but pushes a technical decision onto every session. Kept as an optional preference instead. |

### Tradeoffs accepted

The same session can get different notes depending on whether the app was open when processing reached the notes stage. The notes are labeled with their generator, and regenerating is one tap.

---

## ADR-16 — Structured notes model; LLM generators share one chunk-then-combine implementation

- **Date:** 2026-11-18
- **Status:** Proposed
- **Implementation:** Not started. Resolves the follow-up in ADR-04. [HLD](HIGH_LEVEL_DESIGN.md) Section 6.5.

### Context

ADR-04 asked the HLD to define how notes are represented so every generator fills the same shape, and decided on chunk-then-combine for long transcripts.

### Decision

1. Notes are a **structured domain model** (`Notes` with title, summary, `NoteSection` list, `Highlight` list, generator ID and edit flags), stored in normalized tables, not as Markdown or free text. Rendering to Markdown is an exporter's job.
2. Two extension points: `NoteGenerator` (any complete strategy, used by the built-in generator) and `LanguageModel` (just "prompt plus schema in, text out", with a token budget and capability).
3. A domain class `LlmNoteGenerator` implements `NoteGenerator` on top of any `LanguageModel` and owns chunking, map and reduce prompts, parsing and validation (Template Method). Model adapters such as Gemini Nano are thin (Adapter).
4. Prompts are versioned resources in the domain.

### Alternatives considered

| Alternative | Assessment |
| --- | --- |
| Generators return Markdown text | Easy to display, but sections and highlights could not link to timestamps (FR-62), edits could not be tracked per item (FR-65), and every exporter would parse Markdown. |
| Each LLM adapter implements its own chunking | Freedom per model, but the hardest and most testable logic would be duplicated per adapter and tested only on devices. |

### Tradeoffs accepted

A generic chunking strategy may not use each model's strengths fully (for example a cloud model that could take the whole transcript at once). A model with a large enough token budget simply gets one chunk, so the map step degenerates to a single call.

---

## ADR-17 — Navigation 3 with an adaptive list-detail layout

- **Date:** 2026-11-18
- **Status:** Proposed
- **Implementation:** Not started. Builds on ADR-09. [HLD](HIGH_LEVEL_DESIGN.md) Section 7.2.

### Context

ADR-09 chose a single activity with Compose navigation. The app must work on phones and tablets in both orientations (NFR-04), and the Sessions plus Session details pair is a classic list-detail layout.

### Decision

Use **Jetpack Navigation 3** (stable since November 2025). The back stack is a list of typed route keys owned by `:app`; feature modules contribute entry builders. On wide windows, Sessions and Session details are shown side by side using a Navigation 3 list-detail scene with Material 3 adaptive components. Notification deep links are translated into a back stack in the activity.

### Alternatives considered

| Alternative | Assessment |
| --- | --- |
| Navigation Compose 2.x with type-safe routes | Mature and widely documented. The back stack is owned by the library, which makes adaptive list-detail layouts and custom back stacks more awkward. Existing knowledge from many tutorials is a plus. |
| Hand-rolled navigation with a state list | Maximum control and simple to read, but reinvents saved state, transitions and scene handling. |

### Tradeoffs accepted

Navigation 3 is newer, so fewer examples and answers exist. The exact library versions are fixed in the LLD.

---

## ADR-18 — Sort titles in memory with a locale-aware collator; no Paging in 0.1

- **Date:** 2026-11-18
- **Status:** Proposed
- **Implementation:** Not started. [HLD](HIGH_LEVEL_DESIGN.md) Section 7.3.

### Context

FR-51 requires title sorting that ignores case and handles accented characters in the user's locale. NFR-15 requires smooth scrolling with 1,000 sessions.

### Decision

Date sorting is done in SQL. Title sorting is done in the domain with a locale-aware collator over the list emitted by Room. The list uses `LazyColumn` with stable keys and is not paged.

### Alternatives considered

| Alternative | Assessment |
| --- | --- |
| SQLite `COLLATE NOCASE` or `LOCALIZED` | `NOCASE` only folds ASCII; Android's `LOCALIZED` collation depends on the database locale and is hard to test on the JVM. |
| Store a precomputed collation key column | Sortable in SQL and pageable, but the key goes stale when the device locale changes and must be recomputed. |
| Paging 3 | Needed for tens of thousands of rows; at 1,000 small rows it adds complexity without measurable benefit. |

### Tradeoffs accepted

All session list rows are loaded at once. Revisit when full-text search over transcripts (FR-55 stretch) or much larger libraries make paging worthwhile.

---

## ADR-19 — Benchmark device tiers: emulator baseline, Galaxy A-series and Pixel 8 Pro

- **Date:** 2026-11-19
- **Status:** Accepted (maintainer's choice, 2026-11-19)
- **Implementation:** Not started. Applies from the transcription spike onward. Resolves PRD OQ-3.

### Context

The PRD set performance targets for an unnamed "reference mid-range device" (OQ-3). The maintainer will develop on the emulator for now and test on mid-range and high-end phones, including a Samsung Galaxy A-series phone and a Pixel 8 Pro.

### Decision

Targets are set per tier (PRD Section 7.2, HLD Section 10a):

| Tier | Device | Role |
| --- | --- | --- |
| E | Android emulator, API 36, 4 GB RAM | Daily development baseline: functional checks, memory at the 4 GB floor, trends between builds. No pass/fail speed targets. |
| M | Samsung Galaxy A-series (A3x/A5x class) | Main pass/fail tier: cold start under 1.5 s, real-time factor at or below 1.0, basic notes within 30 s, smooth list. |
| H | Pixel 8 Pro | Upper bound: cold start under 1 s, real-time factor at or below 0.5, on-device LLM notes within 10 min if supported. |

Peak memory under 1.5 GB applies to every tier.

### Reasoning

- The emulator is what the maintainer has available every day, so functional work and tests must not depend on a phone.
- Emulator speed depends on the host computer, and the emulator usually runs an x86_64 build of whisper.cpp with different CPU instructions from phones. Treating its timings as pass/fail would give misleading results in both directions. Memory use and functional behavior, in contrast, carry over well.
- A Galaxy A-series phone is a realistic "typical teacher's phone" and is not expected to support Gemini Nano, so it exercises the guaranteed built-in notes path (ADR-04). The Pixel 8 Pro shows how good the experience can get and is the likely device for trying the on-device LLM path.

### Alternatives considered

| Alternative | Assessment |
| --- | --- |
| One reference mid-range phone only (the PRD's assumption) | Simpler, but gives no view of the high end and no check of the Gemini Nano path. |
| Emulator as the only reference | Always available, but speed results are not meaningful for real users. |
| Firebase Test Lab or another device farm | Wide coverage, but costs money and setup, and long transcription runs are slow to iterate on. A possible later addition. |

### Tradeoffs accepted

- Speed regressions may go unnoticed between real-device runs. Mitigated by tracking emulator trends on the same host.
- "Galaxy A-series" covers a range of chips. The exact model tested is recorded with each result, and targets are revisited if the phone used is much older or newer than the A3x/A5x class.
- Whether the Pixel 8 Pro supports the ML Kit GenAI Prompt API must be checked against the current device list; if it does not, the H tier only tests speed and the LLM path stays on fakes.

---

## ADR-20 — Expected failures are typed values; exceptions are for bugs

- **Date:** 2026-11-19
- **Status:** Proposed
- **Implementation:** Not started. [API design](API_DESIGN.md) Section 1.3.

### Context

NFR-23 requires every engine failure to reach the user as a message with a recovery action, never as a crash. The HLD (Section 9.4) said adapters translate technology exceptions into a domain failure type, but not what the type looks like or how it travels.

### Decision

1. Ports return a small sealed `Outcome<T, E>` (Success or Failure) when the caller must handle a known failure. Failure types are sealed hierarchies per area: `ProcessingFailure`, `CaptureFailure`, `LanguageModelFailure`, `InstallFailure`.
2. Adapters catch technology exceptions at the port boundary. No Android or library exception type appears in a domain signature.
3. `CancellationException` is always rethrown. A domain helper `runCatchingCancellable` replaces `runCatching`, which is disallowed by a lint rule.
4. Unexpected exceptions inside the pipeline become `ProcessingFailure.Unknown` and never crash the worker.
5. Each `ProcessingFailure` has a stable `code` stored in the database.

### Alternatives considered

| Alternative | Assessment |
| --- | --- |
| Kotlin's `Result<T>` | Built in, but the error type is always `Throwable`, so callers cannot get an exhaustive `when` over known failures, and `runCatching` swallows `CancellationException`. |
| Exceptions everywhere (custom exception hierarchy) | Idiomatic for Java-style code and less wrapping. But the compiler does not force callers to handle them, which is exactly what NFR-23 needs. |
| Arrow's `Either` | Rich and well tested, but a large functional library for a handful of uses, and an extra concept for a learning project focused on Android. |

### Tradeoffs accepted

Some `map` and `flatMap` boilerplate in use cases. Two error styles coexist (values and exceptions), so the rule "expected failure → value" must be applied consistently in review.

---

## ADR-21 — Transcription engines receive the file and emit committed segments

- **Date:** 2026-11-19
- **Status:** Proposed
- **Implementation:** Not started. [API design](API_DESIGN.md) Section 5.2.

### Context

ADR-13 checkpoints transcription per window so a crash loses little work. The checkpoint must be written by the domain (the `Transcribe` stage), but engines consume audio differently: whisper.cpp takes PCM windows, while ML Kit Speech Recognition (the planned second engine, ADR-02) takes a file or its own audio stream.

### Decision

`TranscriptionEngine.transcribe(audio: AudioFile, request)` returns a `Flow<TranscriptionEvent>`. The request carries `resumeFromMs` and the tail of already-committed text. The engine emits `Committed(segments, untilMs)` whenever a part of the audio is final, `Progress`, and at most one terminal `Failed`. The stage persists each `Committed` event together with the checkpoint in one transaction. A contract test suite checks ordering, non-overlap, monotonic `untilMs` and correct resume.

### Alternatives considered

| Alternative | Assessment |
| --- | --- |
| The stage decodes PCM windows and calls `engine.transcribe(window)` | Simplest for whisper.cpp and puts all looping in the domain. But it forces every engine to accept 30-second PCM windows, which fits ML Kit and cloud engines poorly, and fixes window boundaries in the domain even though cutting a word at a boundary is an engine-specific problem. |
| `suspend fun transcribe(file): Transcript` for the whole file | Simplest contract. No checkpointing inside a long file, which ADR-13 rejected. |
| Callbacks (`onSegment`, `onProgress`) | Same information as a `Flow`, but loses structured cancellation and backpressure. |

### Tradeoffs accepted

Each engine handles resuming from a position itself. The whisper engine needs a decoder that can start at any position, which `AudioDecoder` provides. Engines are slightly more complex in exchange for a contract that fits every planned engine.

---

## ADR-22 — Session status changes are compare-and-set transitions from one table

- **Date:** 2026-11-19
- **Status:** Proposed
- **Implementation:** Not started. [LLD](LOW_LEVEL_DESIGN.md) Section 5.1.

### Context

Several actors change a session's status at the same time: the worker (start, complete, fail), the user (cancel, retry, regenerate), start-up recovery (requeue) and the model installer (requeue after install). A cancel arriving while the worker finishes must not end as `Ready`. The HLD sketched a `StageOutcome.Deferred` that no stage uses.

### Decision

1. The allowed transitions are listed once in a domain `SessionStateMachine`.
2. The only way to change status is `SessionRepository.transition(id, expectedStatuses, change)`, implemented as one SQL `UPDATE ... WHERE id = ? AND status IN (...)` that reports whether it applied. Callers act on the result.
3. `StageOutcome` has only `Completed` and `Failed`. `Deferred` is dropped, since ADR-15 already decided the pipeline never waits for the foreground. Adding it back later is a Plug-in API change with its own ADR.
4. Regenerating notes reuses the same pipeline with a `PipelineTrigger`, rather than a second code path.

### Alternatives considered

| Alternative | Assessment |
| --- | --- |
| Plain `setStatus(id, status)` and a `Mutex` in the app | Works inside one process, but not across start-up recovery and the worker after process death, and it is easy to forget a lock. The database is already the single source of truth (ADR-13). |
| Optimistic locking with a version column | Also correct and more general, but every update needs the version read first. The status set is the only thing that conflicts, so checking it directly is simpler. |
| A state machine library | Adds a dependency for about a dozen transitions that fit in one table. |

### Tradeoffs accepted

Callers must handle "transition did not apply", which is a little more code at every call site. That is the point: the race becomes visible.

---

## ADR-23 — Room schema: normalized notes tables, string enums, composite child keys

- **Date:** 2026-11-19
- **Status:** Proposed
- **Implementation:** Not started. [LLD](LOW_LEVEL_DESIGN.md) Section 3.

### Context

ADR-16 chose structured notes stored in normalized tables. NFR-21 requires idempotent stages and NFR-22 safe migrations. The sessions list must load quickly with 1,000 rows (NFR-15).

### Decision

- Tables `sessions`, `transcripts` (one row per session, holding the checkpoint and a `revision`), `transcript_segments`, `notes`, `note_sections`, `highlights`. Children cascade on session delete.
- Session IDs are UUID strings. Child tables use `(session_id, idx)` primary keys.
- Enums and IDs are stored as stable string names, never ordinals.
- The summary and each section's points are JSON arrays in a text column, because they are never queried alone.
- The list query selects only list columns.
- `notes.transcript_revision` links notes to the transcript they were built from.

### Alternatives considered

| Alternative | Assessment |
| --- | --- |
| Store notes as one JSON document per session | Fewer tables and simpler writes. But per-item edit flags (FR-65) and seeking by time are clumsier, and schema changes inside the JSON are invisible to Room's migration checks. |
| A separate table for section points | Fully normalized, but more joins and code for data that is always loaded with its section. |
| Auto-increment integer session IDs | Smaller and faster indexes. But the recording service must create the ID before the row exists in some flows (file paths), and UUIDs never collide across a future export or import of sessions. |
| Store enums as ordinals | Smaller, but reordering an enum silently corrupts data. |

### Tradeoffs accepted

JSON columns need a `TypeConverter` and cannot be searched in SQL; full-text search over notes (FR-55 stretch) would need an FTS table fed from them. String enums cost a few bytes per row.

---

## ADR-24 — Build whisper.cpp from a pinned release archive with CMake FetchContent

- **Date:** 2026-11-19
- **Status:** Proposed
- **Implementation:** Not started. Resolves HLD open question OQ-6. [LLD](LOW_LEVEL_DESIGN.md) Section 1.3.

### Context

`:engine:whisper` compiles whisper.cpp from source for `arm64-v8a` and `x86_64` (ADR-19). The source must be pinned for reproducible builds, and the repository holds several independent samples.

### Decision

Use CMake `FetchContent` with the URL of a tagged whisper.cpp release archive and its SHA-256 (`URL_HASH`). The version and hash live in `libs.versions.toml` and are passed to CMake by Gradle. CI caches the download. An `arm64-v8a` variant built for ARMv8.2 with dot-product and FP16 instructions is loaded only on CPUs that support them.

### Alternatives considered

| Alternative | Assessment |
| --- | --- |
| Git submodule | Common and pinned by commit, but submodules are repository-wide: every clone of the samples repository must handle it, `git clone` without `--recursive` gives a confusing build failure, and Daybreak's CI would fetch it too. |
| Vendored copy of the source in the repository | Fully offline and reviewable. Adds tens of MB of third-party code to the repository, and upgrades produce huge diffs. |
| A prebuilt AAR from a third party | Fastest to start, but loses the NDK and JNI learning goal (ADR-02), and the build flags and version are outside our control. |

### Tradeoffs accepted

The first build needs network access to GitHub. A checksum mismatch (for example if a tag were moved) fails the build, which is intended. Two native library variants double the native build time.

---

## ADR-25 — Download models with `HttpURLConnection` behind a small interface

- **Date:** 2026-11-19
- **Status:** Proposed
- **Implementation:** Not started. Resolves HLD open question OQ-7. [API design](API_DESIGN.md) Section 10, [LLD](LOW_LEVEL_DESIGN.md) Section 5.7.

### Context

The only network use in 0.1 is downloading speech models: a handful of large files over HTTPS, with resume through range requests (FR-33, NFR-31).

### Decision

Use the platform's `HttpURLConnection` inside a `HttpFileFetcher` interface in `:core:processing`. Tests run against OkHttp's `MockWebServer` (a test-only dependency), covering 200, 206, 416, 404 and checksum mismatch.

### Alternatives considered

| Alternative | Assessment |
| --- | --- |
| OkHttp | The de facto Android HTTP client, with a nicer API, connection pooling and interceptors. None of that matters for a few sequential downloads, and it adds a runtime dependency (with Okio) for one call site. |
| Android `DownloadManager` | System-managed, survives app death, handles resume. But it writes to locations and shows notifications on its own terms, gives less control over checksum-before-install, and behaves differently across manufacturers. |
| Ktor client | Kotlin-first and multiplatform-friendly, but the largest dependency of the options for the smallest use. |

### Tradeoffs accepted

`HttpURLConnection` is verbose and has quirks (redirect handling, error streams), contained in one class. If cloud engines arrive in 0.3 and need a richer client, this decision is revisited and the interface makes the swap local.

---

## ADR-26 — Recording logic is a pure reducer; the service only performs effects

- **Date:** 2026-11-19
- **Status:** Proposed
- **Implementation:** Not started. [LLD](LOW_LEVEL_DESIGN.md) Section 4.

### Context

Recording has the most edge cases in the app: pause, resume, phone calls, another app taking the microphone, low storage, the 3-hour cap, stop, discard, and failures in remuxing. ADR-10 named the Capture flow as the likely place for an MVI-style reducer.

### Decision

`RecordingMachine` in `:core:domain` is a pure function `reduce(state, event) -> (state, effects)`. `RecordingService` turns Android signals into events and executes the returned effects (open the microphone, stop feeding the encoder, remux, update the notification). After an interruption ends, the machine goes to `Paused`, not back to `Recording`; the user resumes. Audio focus is not requested; phone calls and microphone silencing are detected through audio mode and recording callbacks.

### Alternatives considered

| Alternative | Assessment |
| --- | --- |
| State handled directly in the service with flags | Less code at first, but the logic can only be tested on a device, and flag combinations (paused and interrupted and low storage) are where bugs hide. |
| MVI for every screen | Consistent, but most screens only observe a `Flow`; ADR-10 rejected that boilerplate. The reducer is used where the state machine is real. |
| Resume automatically after a call | Fewer taps, but could record a private conversation; pausing is the safer default. |

### Tradeoffs accepted

Two places to read to understand recording (reducer and service). The effect list must stay in sync with what the service can do, which the compiler checks through a sealed type.

---

## ADR-27 — LLM notes use versioned prompt templates and JSON output validated by one validator

- **Date:** 2026-11-19
- **Status:** Proposed
- **Implementation:** Not started. Detail for ADR-16. [LLD](LOW_LEVEL_DESIGN.md) Section 7.

### Context

ADR-16 decided that LLM-backed generators share one chunk-then-combine implementation with prompts owned by the domain. HLD risk R-6 is that model output does not follow the structure.

### Decision

1. Prompts are Kotlin string templates in versioned objects (`NotesPromptsV1`); the version is stored with the notes.
2. Map and reduce steps request JSON with a schema. The schema is passed to the backend when it supports structured output and described in the prompt otherwise. Times are `mm:ss` strings, matching the `[mm:ss]` markers in the transcript.
3. A lenient parser (kotlinx.serialization, unknown fields ignored, code fences stripped) and one `NotesValidator` for every generator, including the basic one. A failed chunk is retried once, then the stage falls back to the next generator.

### Alternatives considered

| Alternative | Assessment |
| --- | --- |
| Prompts as Android string resources or asset files | Editable without recompiling and translatable, but the domain is pure Kotlin (NFR-64) and prompts are logic, not UI copy. Translation of prompts is not wanted; the model is told the output language instead. |
| Free text with headings, parsed with regular expressions | Works with any model, but fragile, and timestamps would be guessed. |
| Times as seconds (integers) | Easier to parse, but models copy the `[mm:ss]` markers they see more reliably than they convert units. |

### Tradeoffs accepted

Some valid notes are rejected by strict validation and fall back to basic notes. The prompt text is in code, so tuning a prompt needs a build.

---

## ADR-28 — Energy-based voice activity detection first; whisper.cpp VAD evaluated in the spike

- **Date:** 2026-11-19
- **Status:** Proposed. To be confirmed or superseded after the transcription spike.
- **Implementation:** Not started. [LLD](LOW_LEVEL_DESIGN.md) Section 6.3.

### Context

Whisper models can invent text on silence, and skipping silent windows saves time. The HLD named whisper.cpp's built-in VAD as the first candidate and an energy threshold as the fallback, with the choice left to the LLD after the spike.

### Decision

Define a `SpeechDetector` interface inside `:engine:whisper` (not a domain port). Ship an adaptive energy detector as the default so the engine works before the spike. Measure the whisper.cpp built-in VAD (Silero) against it on the lecture clips across tiers E, M and H, and switch the binding if it wins on word error rate and time.

### Alternatives considered

| Alternative | Assessment |
| --- | --- |
| whisper.cpp VAD from the start | Likely better accuracy, but needs a second model file to download and verify, and its cost on mid-range phones is unknown. |
| No VAD | Simplest; wastes time on silence and risks invented text in long lectures with pauses. |
| A domain-level `SpeechDetector` port | Would let other engines share it, but no other planned engine needs it (ML Kit does its own). Kept inside the engine per NFR-66. |

### Tradeoffs accepted

The energy detector can misclassify quiet speech in a noisy room as silence. Its thresholds are tuned on the spike clips.

---

## ADR-29 — Screens call use cases for every write; feature modules expose only entries and navigator interfaces

- **Date:** 2026-11-19
- **Status:** Proposed
- **Implementation:** Not started. [API design](API_DESIGN.md) Sections 8 and 9.

### Context

ADR-10 said use cases exist only where they hold logic or coordinate ports, to avoid pass-through classes. HLD rule 3 keeps feature modules away from adapters, and rule 2 keeps features independent.

### Decision

1. ViewModels perform every write through a use case, and read through `Observe*` use cases. They never call repository write methods directly. All 0.1 writes have some logic (validation, title source, status transitions, file cleanup), so this adds no empty pass-throughs.
2. A feature module's public surface is its Navigation 3 entry builder and a navigator interface (for example `SessionsNavigator`). Everything else is `internal`. `:app` implements the navigator interfaces with the back stack.

### Alternatives considered

| Alternative | Assessment |
| --- | --- |
| ViewModels use repositories directly for simple reads and writes | Fewer classes, but business rules (rename sets the title source, delete removes files) would drift into ViewModels. |
| Navigation through route types shared from a common module | Simple, but each feature then knows other features' routes. A navigator interface keeps the dependency pointing at `:app`. |

### Tradeoffs accepted

A few small use cases and one interface per feature. Navigation callbacks are slightly more ceremony than calling `backStack.add(...)` in a screen.

---

## ADR-30 — Shared fakes and contract suites in `testFixtures`; no mocking library

- **Date:** 2026-11-19
- **Status:** Proposed
- **Implementation:** Not started. [API design](API_DESIGN.md) Section 11, [LLD](LOW_LEVEL_DESIGN.md) Section 11.

### Context

NFR-71 requires a contract test suite per engine kind. Use cases, ViewModels and workers need test doubles for about fifteen ports.

### Decision

`:core:domain` publishes a `testFixtures` source set with abstract contract suites (`TranscriptionEngineContract`, `NoteGeneratorContract`, `LanguageModelContract`, `NotesExporterContract`, `PipelineStageContract`) and in-memory fakes for every port (`FakeSessionRepository`, `FakeTranscriptionEngine`, `FakeLanguageModel`, `FakeClock`). Other modules use them with `testImplementation(testFixtures(project(":core:domain")))`. No mocking library is added.

### Alternatives considered

| Alternative | Assessment |
| --- | --- |
| MockK in every module | Quick to write and flexible. But mocks restate implementation details, break on refactors, and do not check that the double behaves like the real thing. Daybreak also uses hand-written fakes. |
| A separate `:core:testing` module | Common in Google's samples and equally valid. `testFixtures` keeps fakes next to the ports they implement and avoids one more module (HLD risk R-5). |

### Tradeoffs accepted

Fakes must be maintained when a port changes. The fakes themselves run the repository contract checks where one exists (for example the compare-and-set rules of ADR-22), so they stay faithful.

---

## Sources

- ML Kit GenAI overview (APIs, status, device lists, foreground and quota limits): https://developers.google.com/ml-kit/genai
- ML Kit GenAI Speech Recognition: https://developers.google.com/ml-kit/genai/speech-recognition/android
- ML Kit GenAI Summarization: https://developers.google.com/ml-kit/genai/summarization/android
- Android Developers Blog, on-device inference (July 2026): https://android-developers.googleblog.com/2026/07/android-on-device-inference.html
- whisper.cpp: https://github.com/ggml-org/whisper.cpp
- Vosk: https://alphacephei.com/vosk/
- Android `RecognizerIntent` reference: https://developer.android.com/reference/android/speech/RecognizerIntent
- Jetpack Navigation 3 is stable (November 2025): https://android-developers.googleblog.com/2025/11/jetpack-navigation-3-is-stable.html
- Foreground service timeouts (Android 15+): https://developer.android.com/develop/background-work/services/fgs/timeout
- Foreground service type changes in Android 15: https://developer.android.com/about/versions/15/changes/foreground-service-types
