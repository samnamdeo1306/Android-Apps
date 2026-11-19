# Product requirements — NotesMaker

Status: **Draft v0.1 (2026-11-18)**, requirements only. No code exists yet. The high-level design (HLD), low-level design (LLD) and internal API design follow this document and may feed changes back into it.
Owner: project maintainer. Purpose: a learning project for deep Android and Jetpack Compose practice, built as a usable tool.

Every significant choice in this document is recorded with its reasoning, alternatives and tradeoffs in [DECISIONS.md](DECISIONS.md). Requirement rows reference those records as `ADR-nn`.

## 1. Problem and audience

Teachers and instructors spend time after each class writing notes that students could study from. The spoken lecture already contains most of that content. NotesMaker records a live session, or accepts an existing recording, and turns it into structured study notes: a title, a summary, sections and highlights, with the original audio kept alongside as a voice note.

**Primary user:** a teacher or instructor who records their own class or tutorial on an Android phone or tablet, usually in one language, often for 30 to 120 minutes.

**Secondary user (out of scope for the app itself):** the students who later read the notes. They receive notes only through an explicit export or share by the teacher.

## 2. Goals

1. Capture a session by recording live or importing an audio file, from a single main screen.
2. Turn the audio into a transcript **on the device by default**, so lecture audio never has to leave the phone (ADR-02).
3. Generate structured notes from the transcript: title, summary, sections and highlights (ADR-04).
4. List all processed sessions on a separate screen, sortable by title or date.
5. Keep the core structure modular, following SOLID principles, so new engines, inputs, outputs and features can be added with minimal change to existing code (Section 7).
6. Make every design decision traceable and explainable, because this is a learning project.

## 3. Non-goals for the first release

- Real-time captions during recording (planned later, see ADR-03).
- Speaker identification (diarization), for example separating teacher from student questions.
- Video input, slides, OCR or whiteboard capture.
- Accounts, cloud sync, multi-device access or collaboration.
- A student-facing app or a sharing backend.
- Translation of notes into another language.
- Guaranteed accuracy. Notes are a draft for the teacher to review, not an authoritative record.

## 4. Terminology

| Term | Meaning |
| --- | --- |
| Session | One lecture or class, represented by one audio recording and everything derived from it. |
| Source audio | The recorded or imported audio file, stored in app storage. Shown to the user as the session's **voice note**. |
| Transcript | Time-stamped text produced from the source audio by a transcription engine. |
| Notes | The generated title, summary, sections and highlights. |
| Section | A titled part of the notes covering a continuous time range of the session, with its own short body text. |
| Highlight | A short key point, definition, formula, date, or instruction (such as "assignment due Friday") worth calling out, linked to the moment it was said. |
| Engine | A replaceable implementation of a processing capability, such as transcription or note generation. |
| Processing pipeline | The ordered steps that turn source audio into notes: prepare audio, transcribe, generate notes, save. |

## 5. User flows

### 5.1 Capture (main screen)

The app opens on the **Capture** screen. It offers two primary actions: **Start recording** and **Import audio file**. A link or tab leads to the **Sessions** screen.

1. **Record:** the user taps Start recording. The app asks for microphone permission if needed, and on first use shows a one-time consent reminder about recording others. Recording runs in a foreground service with a persistent notification, so it continues with the screen off or the app in the background. The user can pause, resume, stop, or discard. On stop, the session is saved and processing starts.
2. **Import:** the user taps Import audio file and picks a file with the system file picker. The app copies the file into its own storage, validates that it can be decoded, and starts processing.

### 5.2 Processing

Processing runs as background work that survives leaving the app (ADR-03). The session shows a stage (preparing audio, transcribing, generating notes) and progress where measurable. Processing can be cancelled. A failed stage can be retried without repeating completed stages. When finished, the user receives a notification that opens the session.

### 5.3 Sessions list

The **Sessions** screen lists all sessions with title, date, duration and processing status. The user can sort by **date** (newest or oldest first) or **title** (A to Z or Z to A). The chosen sort is remembered. Tapping a session opens its details.

### 5.4 Session details

Shows the title, date and duration, an audio player for the voice note, the summary, the sections and the highlights. Tapping a section or highlight seeks the player to that moment. The full transcript is available on a separate tab. The user can rename the session, edit notes text, regenerate notes, share or export, and delete.

## 6. Functional requirements

Priorities use MoSCoW: **M** must have for the first release, **S** should have, **C** could have later. Acceptance criteria describe observable behavior that the test plan will verify.

### 6.1 Capture: recording

| ID | Pri | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| FR-01 | M | Start a recording from the Capture screen. | With microphone permission granted, tapping Start recording begins capture within 1 second and shows elapsed time and a live input level. If permission is denied, the app explains why it is needed and offers a link to settings. No recording begins without permission. |
| FR-02 | M | Recording continues in the background. | With the screen locked or another app in front, recording continues for at least 2 hours. A persistent notification shows elapsed time and offers Pause and Stop. |
| FR-03 | M | Pause and resume. | Paused time is not included in the audio. The saved file plays back as one continuous recording. |
| FR-04 | M | Stop and save, or discard. | Stop saves a new session and starts processing. Discard asks for confirmation and deletes the audio. |
| FR-05 | M | Survive interruptions. | During an incoming phone call or when another app takes the microphone, recording pauses automatically and the user is told. Audio captured before the interruption is never lost. If the app process is killed, audio written so far is recoverable as a session on next launch. |
| FR-06 | M | Protect against running out of storage. | Before starting, the app warns if free space is below the amount needed for 2 hours. During recording, it stops and saves cleanly if free space runs low. |
| FR-07 | M | Consent reminder. | The first time the user records, the app shows a short reminder that they may need consent from people being recorded. The user can dismiss it permanently. |

### 6.2 Capture: importing audio

| ID | Pri | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| FR-10 | M | Import an audio file from device storage or a cloud provider via the system picker. | The picker filters to audio. The selected file is copied into app storage, so later processing does not depend on the original file still existing. |
| FR-11 | M | Supported formats. | At least M4A/AAC, MP3, WAV, OGG/Opus and FLAC import successfully. An unsupported or corrupt file shows a clear error and creates no session. |
| FR-12 | M | Length limits. | Files up to 3 hours are accepted. Longer files are rejected with a message stating the limit (ADR-08). |
| FR-13 | S | Receive audio shared from other apps. | NotesMaker appears as a share target for audio files, and sharing a file follows the same flow as FR-10. |

### 6.3 Processing pipeline

| ID | Pri | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| FR-20 | M | Process sessions in the background. | After recording stops or an import completes, processing starts automatically and continues when the user leaves the app. A notification shows progress (ADR-03). |
| FR-21 | M | Show stage and progress. | The session shows its current stage and a percentage during transcription. |
| FR-22 | M | Resumable stages. | Each completed stage's output is saved. After a failure, app kill or reboot, processing resumes from the last incomplete stage rather than starting over. |
| FR-23 | M | Cancel and retry. | The user can cancel processing. A failed or cancelled session shows the reason and a Retry action. |
| FR-24 | M | One session at a time. | Sessions are processed one after another in the order they were queued. Queued sessions show "Waiting". |
| FR-25 | S | Respect device conditions. | Heavy processing can be deferred while battery is low, if the user chooses this in settings. |

### 6.4 Transcription

| ID | Pri | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| FR-30 | M | Transcribe on the device by default. | With the default settings, transcription completes in airplane mode once the speech model is installed. No audio or transcript is sent over the network (ADR-02). |
| FR-31 | M | Time-stamped transcript. | The transcript is stored as segments with start and end times, accurate enough to seek the player to within about 2 seconds of the spoken text. |
| FR-32 | M | Punctuated, readable text. | The transcript includes punctuation and capitalization for the supported languages, so it can be read directly. |
| FR-33 | M | Speech model management. | If the required speech model is not on the device, the app shows its size and asks before downloading it. Downloads use Wi-Fi by default, can resume after interruption, and are verified with a checksum before use (ADR-06). |
| FR-34 | M | Language. | English is supported in the first release. The session language is selectable per session (defaulting to the device language when supported), so other languages can be enabled without changing the pipeline. |
| FR-35 | S | Choose model quality. | Settings offer a faster, smaller model and a more accurate, larger model, with their download size and an estimated speed shown. |
| FR-36 | C | Optional cloud transcription. | A user may opt in to a cloud engine per session or as a default. The app clearly states that audio will leave the device. Off by default (ADR-05). |

### 6.5 Note generation

| ID | Pri | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| FR-40 | M | Generate a title. | Every processed session gets a title of at most 80 characters describing the topic. If generation fails, the title falls back to "Session on <date, time>". |
| FR-41 | M | Generate a summary. | A summary of 3 to 8 sentences (or bullets) covering the main topics, in the session language. |
| FR-42 | M | Generate sections. | The notes are divided into sections, each with a heading, a start time, and a body of key points. Sections cover the session in time order without overlap. A session shorter than about 5 minutes may have a single section. |
| FR-43 | M | Generate highlights. | Up to 10 highlights, each linked to a timestamp. Highlights include explicit instructions such as deadlines or assignments when the speaker states them. |
| FR-44 | M | Always produce usable notes. | On any device, even without on-device AI model support or network, processing yields a title, summary, sections and highlights using a basic built-in method. The notes show which method produced them (ADR-04). |
| FR-45 | M | Handle long sessions. | Sessions up to 3 hours produce complete notes, even when the transcript exceeds a single model's input limit (ADR-04). |
| FR-46 | S | Regenerate notes. | The user can regenerate notes from the saved transcript, optionally with a different available generator, without re-transcribing. |
| FR-47 | C | Optional cloud note generation. | Same rules as FR-36: opt in, clearly disclosed, off by default (ADR-05). |

### 6.6 Sessions list

| ID | Pri | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| FR-50 | M | Separate sessions screen. | Lists every session with title, date and time, duration and status (Recording, Waiting, Processing, Ready, Failed). |
| FR-51 | M | Sort by date or title. | Options: newest first (default), oldest first, title A to Z, title Z to A. Title sorting ignores case and handles accented characters in the user's locale. Ties sort by date, newest first. |
| FR-52 | M | Remember sort choice. | The chosen sort persists across app restarts. |
| FR-53 | M | Live updates. | The list updates without manual refresh when a session's status or title changes. |
| FR-54 | M | Empty state. | With no sessions, the screen explains how to create one and links to the Capture screen. |
| FR-55 | S | Search. | Searching by text matches titles and, as a stretch, notes and transcript content. |
| FR-56 | C | Filters and grouping. | Filter by status or date range; group by month. |

### 6.7 Session details, editing and export

| ID | Pri | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| FR-60 | M | View notes. | Shows title, date, duration, summary, sections and highlights. |
| FR-61 | M | Play the voice note. | An audio player with play, pause, seek and skip forward/back 10 seconds. Playback continues in the background with media controls in the notification. |
| FR-62 | M | Jump to moment. | Tapping a section or highlight seeks playback to its timestamp. |
| FR-63 | M | View transcript. | The full transcript is shown with timestamps. Tapping a line seeks the player. |
| FR-64 | M | Rename. | The user can edit the title. An edited title is never overwritten by regeneration unless the user confirms. |
| FR-65 | S | Edit notes. | The user can edit the summary, section text and highlights. Edits are saved and marked as user-edited. |
| FR-66 | M | Delete. | Deleting a session asks for confirmation and removes its audio, transcript and notes from storage. |
| FR-67 | S | Share and export. | Export notes as Markdown or plain text via the share sheet. Optionally include the audio file. |
| FR-68 | C | More export formats. | PDF export and a printable layout. |

### 6.8 Settings

| ID | Pri | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| FR-70 | M | Manage speech models. | View installed models with size, download or delete them. |
| FR-71 | S | Default language and model quality. | See FR-34 and FR-35. |
| FR-72 | S | Recording quality. | Choose standard (default) or high quality, showing the storage per hour of each. |
| FR-73 | C | Cloud providers. | Configure an opt-in cloud engine (FR-36, FR-47). |

## 7. Non-functional requirements

### 7.1 Platform and technology

| ID | Requirement |
| --- | --- |
| NFR-01 | Kotlin, Jetpack Compose and Material 3. Single-activity app with Compose navigation between the Capture, Sessions, Session details and Settings screens (ADR-09). |
| NFR-02 | Minimum Android 8.0 (API 26), compile and target API 36, matching the repository's other sample. Features that need newer APIs degrade gracefully (ADR-07). |
| NFR-03 | Supported hardware: arm64 phones and tablets with at least 4 GB RAM. 32-bit-only and x86 devices are not targeted, apart from x86_64 emulators for development. |
| NFR-04 | Phone and tablet layouts, portrait and landscape. Recording and processing must not be affected by rotation or other configuration changes. |

### 7.2 Performance

Targets are for a **reference mid-range device** (to be fixed during the HLD, for example a 2023 mid-range phone with 6 GB RAM). They will be measured in a benchmark spike before the design is finalized.

| ID | Requirement |
| --- | --- |
| NFR-10 | App cold start to an interactive Capture screen in under 1.5 seconds. |
| NFR-11 | Transcription with the default model is no slower than the audio length (real-time factor at or below 1.0). A 60-minute lecture is transcribed within 60 minutes. Stretch goal: 0.5. |
| NFR-12 | Note generation for a 60-minute transcript completes within 10 minutes with an on-device model, and within 30 seconds with the basic method. |
| NFR-13 | Peak memory during processing stays below 1.5 GB so the system does not kill the app on 4 GB devices. |
| NFR-14 | The UI stays responsive (no dropped frames visible to the user, no "app not responding") during recording and processing. All heavy work runs off the main thread. |
| NFR-15 | Sessions list scrolls smoothly with 1,000 sessions. |

### 7.3 Reliability and data integrity

| ID | Requirement |
| --- | --- |
| NFR-20 | No recorded audio is lost on crash, process death, low memory or reboot. Audio is flushed to disk at least every 5 seconds. |
| NFR-21 | Processing is idempotent per stage. Re-running a stage replaces its previous output and leaves no duplicates. |
| NFR-22 | Database schema changes use versioned migrations with tests. No destructive migrations once released. |
| NFR-23 | Engine failures (model missing, out of memory, unsupported device, quota exceeded) are mapped to user-facing messages with a recovery action, never to a crash. |

### 7.4 Privacy and security

| ID | Requirement |
| --- | --- |
| NFR-30 | Local-first. Audio, transcripts and notes stay in app-private storage. The default configuration sends no user content over the network (ADR-02, ADR-05). |
| NFR-31 | Network use is limited to downloading models, and to cloud engines only when the user has opted in. |
| NFR-32 | No accounts, ads, analytics or third-party tracking SDKs. |
| NFR-33 | App data is excluded from cloud backup by default, because recordings may contain students' voices. |
| NFR-34 | Downloaded models are verified by checksum before use. |
| NFR-35 | Any API key for an opt-in cloud engine is supplied by the user and stored encrypted. No keys or secrets are committed to the repository. |

### 7.5 Storage

| ID | Requirement |
| --- | --- |
| NFR-40 | Standard recording quality uses at most about 30 MB per hour (mono AAC, about 64 kbps) (ADR-08). |
| NFR-41 | Settings show total storage used by sessions and by models. |

### 7.6 Accessibility and localization

| ID | Requirement |
| --- | --- |
| NFR-50 | All controls have content descriptions. Works with TalkBack, large font sizes and display scaling. Touch targets are at least 48 dp. |
| NFR-51 | Color is never the only signal for status. Meets WCAG AA contrast in light and dark themes. |
| NFR-52 | All user-facing strings are in resources, ready for translation. English first. |

### 7.7 Modularity and extensibility

These requirements turn the project's design goals into checkable rules. The HLD will define the actual modules and interfaces.

| ID | Requirement |
| --- | --- |
| NFR-60 | **Replaceable engines.** Transcription and note generation are behind interfaces owned by the domain layer. Adding a new engine (for example a new on-device model or a cloud service) requires adding a new implementation and registering it, with no changes to the pipeline, the UI or other engines (Open/Closed principle). |
| NFR-61 | **Replaceable inputs.** Recording and file import produce the same session input. A future input (for example a Bluetooth microphone or a URL) is added the same way. |
| NFR-62 | **Replaceable outputs.** Export formats are implementations of one export interface. Adding PDF must not change Markdown export. |
| NFR-63 | **Extensible pipeline.** The processing pipeline is a sequence of stages with a shared contract. A new stage (for example translation or quiz generation) can be inserted without rewriting existing stages. |
| NFR-64 | **Dependency direction.** The domain layer is plain Kotlin with no Android framework dependencies, and is unit-tested on the JVM. Android, storage and ML libraries live in outer layers (Dependency Inversion). |
| NFR-65 | **Capability checks.** Each engine reports whether it can run on the current device (model installed, hardware support, network, opt-in). The app chooses among available engines by a defined policy, rather than by scattered device checks. |
| NFR-66 | **No speculative abstraction.** An interface is introduced when it has a real second implementation, a test seam, or a listed planned extension. This keeps the design explainable, in line with the repository conventions. |

### 7.8 Quality and testing

| ID | Requirement |
| --- | --- |
| NFR-70 | Domain logic (pipeline orchestration, sorting, note assembly, chunking) has JVM unit tests. |
| NFR-71 | Each engine adapter has a contract test suite that every implementation must pass, using short sample audio clips stored in the repository. |
| NFR-72 | Room DAOs and migrations have instrumented tests. Key Compose screens have UI tests. |
| NFR-73 | CI builds, runs unit tests and lint for this sample on each change, following the repository's per-sample workflow convention. |
| NFR-74 | Transcription quality is tracked by word error rate on a small fixed set of lecture-style clips, so engine or model changes can be compared. |

## 8. Assumptions

1. Teachers record their own sessions in a reasonably quiet room, with the phone near the speaker. Far-field or very noisy recordings will produce weaker transcripts.
2. One main speaker per session. Questions from the room may be transcribed but are not attributed.
3. One language per session. Mixed-language speech is transcribed on a best-effort basis.
4. Typical sessions are 30 to 120 minutes. The hard limit is 3 hours.
5. Users accept a one-time model download of roughly 60 to 500 MB, depending on the quality chosen, in exchange for offline, private transcription.
6. Users accept that notes are produced after the session ends, not during it, for the first release (ADR-03).
7. Users are responsible for obtaining consent to record where required. The app reminds but does not enforce.
8. The app is distributed as a sideloaded or debug build during the learning phase. Play Store distribution is not a first-release goal, but nothing in the design should prevent it.

## 9. Constraints

1. **Device AI availability is uneven.** Google's on-device Gemini Nano features (ML Kit GenAI) run only on a listed set of recent flagship devices, are in alpha or beta, and may only run while the app is in the foreground. They cannot be the only path (ADR-02, ADR-04).
2. **Background limits.** Android restricts background work and requires foreground services of a declared type for recording and long processing. Some engines are blocked when the app is not in the foreground.
3. **Model input limits.** On-device language models accept only a few thousand tokens at a time, while a 1-hour lecture produces roughly 8,000 to 10,000 words. Long transcripts must be processed in chunks.
4. **App size.** Speech models are too large to bundle comfortably in the APK and are downloaded on demand (ADR-06).
5. **Learning project.** Choices favor understandable, well-documented approaches that can be explained in a design review over maximum polish.

## 10. Release plan

| Release | Scope |
| --- | --- |
| **0.1 MVP** | All **M** requirements: record and import, background processing, on-device transcription in English, notes via the basic method and on-device model where available, sessions list with sorting, details with playback and seek, rename, delete. |
| **0.2** | **S** requirements: share target, model quality choice, note editing, regenerate with another generator, Markdown export, search by title. |
| **0.3+** | **C** requirements and new features: live captions while recording, opt-in cloud engines, more languages, PDF export, full-text search, translation, quiz or flashcard generation from notes. |

## 11. Success criteria

- A 60-minute English lecture recorded on the reference device produces a transcript and complete notes without user intervention beyond starting and stopping.
- The transcript's word error rate on the test clips is within the range documented for the chosen model.
- In a review of 5 real sessions, the maintainer judges titles and section headings as accurate and highlights as useful in at least 4 of 5.
- A new engine or export format can be added in a single change that touches no existing engine, stage or screen, demonstrated at least once before 0.3.
- Debug APK builds, unit tests and lint pass in CI.

## 12. Open questions

These are tracked so later documents can resolve them. None blocks the HLD.

| ID | Question | Current assumption |
| --- | --- | --- |
| OQ-1 | Which languages besides English should come first? | English only for 0.1, with the pipeline language-aware (FR-34). |
| OQ-2 | Is live captioning during recording needed for 0.1? | No. Record first, process after (ADR-03). |
| OQ-3 | Which reference device do we benchmark on? | A mid-range arm64 phone the maintainer owns. Still open after the HLD (risk table, Section 11); needed before the transcription spike. |
| OQ-4 | Which cloud provider, if any, for opt-in cloud engines? | Deferred to 0.3. The interface allows any. |
| OQ-5 | Should notes formatting include formulas (LaTeX) for STEM teachers? | Plain text and Markdown for now. |

## 13. Traceability

The test plan (to be written with the LLD) will map each **M** and **S** requirement to at least one automated or manual test. Requirement IDs are stable: a removed requirement is marked "Withdrawn" with a reason rather than renumbered.
