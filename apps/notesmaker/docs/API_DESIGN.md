# Internal API design — NotesMaker

Status: **Draft v0.1 (2026-11-19)**. Design only; no code exists yet.
Inputs: [product requirements](PRODUCT_REQUIREMENTS.md) v0.2, [high-level design](HIGH_LEVEL_DESIGN.md) v0.2 and [decision log](DECISIONS.md) up to ADR-19. Decisions made in this document and the [low-level design](LOW_LEVEL_DESIGN.md) are ADR-20 to ADR-30.

NotesMaker has no server, so "API" here means the **contracts between modules**: the Kotlin interfaces that the domain owns (ports), the plug-in interfaces that engines and exporters implement, the pipeline stage contract, the use cases the screens call, and the error and result types that cross those boundaries. The only network API is the model download, covered in Section 10.

The [low-level design](LOW_LEVEL_DESIGN.md) covers how these contracts are implemented: database schema, state machines, threading, native code and manifest details.

## 1. Conventions

These rules apply to every contract below. They exist so that a new engine, stage or screen can be written by reading this document, without reading other implementations.

### 1.1 Package and naming

| Item | Rule | Example |
| --- | --- | --- |
| Root package | `dev.notesmaker`, then the module path | `dev.notesmaker.core.domain`, `dev.notesmaker.engine.whisper` |
| Domain sub-packages | `model`, `port`, `engine`, `pipeline`, `usecase`, `notes`, `error` | `dev.notesmaker.core.domain.port.SessionRepository` |
| Ports | A noun for what the domain needs, never the technology | `AudioDecoder`, not `MediaCodecDecoder` |
| Adapters | Technology prefix plus the port name | `RoomSessionRepository`, `MediaCodecAudioDecoder` |
| Use cases | Verb phrase for the user goal, one public `operator fun invoke` | `RenameSession`, `ImportAudio` |
| Times on the audio timeline | `Long` milliseconds from the start of the audio, named `...Ms` | `startMs`, `atMs` |
| Wall-clock times | `Long` epoch milliseconds, named `...AtEpochMs` | `createdAtEpochMs` |

Why `Long` milliseconds and not `kotlin.time.Duration` or `Instant`: they map directly to Room columns, Media3 positions and whisper.cpp timestamps (which are in 10 ms units) without conversion layers, and they are trivially comparable in tests. The naming suffix carries the unit. This is a convention, not an ADR, because it is easy to change before code exists.

### 1.2 Asynchrony

| Shape | Used for | Rule |
| --- | --- | --- |
| `suspend fun` | One-shot work: a query, a write, a stage run, a model call | Main-safe: the implementation moves to the right dispatcher itself. Callers never wrap calls in `withContext`. |
| `Flow<T>` | Observable state: sessions, notes, recording state, playback position | Cold, distinct-until-changed, main-safe collection. Room-backed flows re-emit on table changes (FR-53). |
| `StateFlow<T>` | Process-wide state with a current value: recording state, foreground state | Only exposed by adapters that own the state. |

No port returns `LiveData`, callbacks or `Future`s. Adapters wrap callback APIs (`MediaCodec`, ML Kit `ListenableFuture`s, Media3 listeners) with `suspendCancellableCoroutine` or `callbackFlow` at the boundary.

### 1.3 Errors and results (ADR-20)

Expected failures are **values**, unexpected ones are **exceptions**:

```kotlin
package dev.notesmaker.core.domain.error

/** Result of an operation that can fail in a way the user should hear about. */
sealed interface Outcome<out T, out E> {
    data class Success<T>(val value: T) : Outcome<T, Nothing>
    data class Failure<E>(val error: E) : Outcome<Nothing, E>
}

inline fun <T, E, R> Outcome<T, E>.map(f: (T) -> R): Outcome<R, E>
inline fun <T, E, R> Outcome<T, E>.flatMap(f: (T) -> Outcome<R, E>): Outcome<R, E>
inline fun <T, E> Outcome<T, E>.onFailure(f: (E) -> Unit): Outcome<T, E>
fun <T, E> Outcome<T, E>.getOrNull(): T?
```

Rules:

1. A port method returns `Outcome<T, SomeFailure>` when the caller must handle a known failure (file unreadable, model missing, storage full). It returns a plain `T` when the only possible failures are programming errors or corruption.
2. Adapters catch technology exceptions (`IOException`, `MediaCodec.CodecException`, `OutOfMemoryError` while loading a model, ML Kit `GenAiException`) **at the port boundary** and convert them to a failure value. No Android or library exception type appears in a domain signature (NFR-23, HLD 9.4).
3. `CancellationException` is always rethrown, never converted. Helper `runCatchingCancellable { }` in the domain does this correctly; plain `runCatching` is banned by a lint rule because it swallows cancellation.
4. Exceptions that reach the pipeline (bugs) are converted to `ProcessingFailure.Unknown` by the pipeline, logged without transcript text, and never crash the worker.

The failure hierarchy:

```kotlin
/** Every failure that can stop processing a session. Persisted by its code. */
sealed interface ProcessingFailure {
    val code: String                       // stable, stored in sessions.failure_code
    val retryable: Boolean

    data class ModelMissing(val modelId: ModelId) : ProcessingFailure
    data class AudioUnreadable(val detail: String) : ProcessingFailure
    data object OutOfMemory : ProcessingFailure
    data class EngineUnavailable(val engineId: EngineId, val reason: String) : ProcessingFailure
    data object StorageFull : ProcessingFailure
    data object TimedOut : ProcessingFailure        // FGS time limit or engine timeout
    data object Cancelled : ProcessingFailure
    data class Unknown(val detail: String) : ProcessingFailure
}

/** Failures of creating a session from an input (record or import). */
sealed interface CaptureFailure {
    data object PermissionDenied : CaptureFailure
    data object MicrophoneBusy : CaptureFailure
    data class InsufficientStorage(val requiredBytes: Long, val freeBytes: Long) : CaptureFailure
    data class UnsupportedFormat(val mimeType: String?) : CaptureFailure
    data object Corrupt : CaptureFailure
    data class TooLong(val durationMs: Long, val limitMs: Long) : CaptureFailure
    data object SourceUnavailable : CaptureFailure   // URI revoked or provider offline
}
```

`:core:ui` owns the single `when` that maps each failure to a `UiText` message and a recovery action (Retry, Install model, Free up space, Open settings). Because the hierarchies are sealed, adding a failure is a compile error until the mapping handles it.

### 1.4 Identifiers

```kotlin
@JvmInline value class SessionId(val value: String)   // UUID string
@JvmInline value class EngineId(val value: String)    // "whisper.cpp", "notes.basic", "llm.gemini-nano"
@JvmInline value class ModelId(val value: String)     // "whisper-base.en-q5_1"
@JvmInline value class StageId(val value: String)     // "prepare-audio", "transcribe", ...
@JvmInline value class ExporterId(val value: String)  // "markdown", "plain-text"
```

Value classes give type safety at zero runtime cost. They are string-backed so that adding a stage, engine or model is a new value rather than an enum change in persisted data (HLD 5.1). Stable IDs are part of the contract: once released, an ID is never renamed, because it is stored in the database.

### 1.5 Stability levels

Each public type in `:core:domain` is marked in KDoc as one of:

| Level | Meaning | Examples |
| --- | --- | --- |
| **Plug-in API** | Implemented outside the domain by engines, exporters and stages. Changing it breaks every implementation, so changes need an ADR and a contract test update. | `TranscriptionEngine`, `NoteGenerator`, `LanguageModel`, `NotesExporter`, `PipelineStage` |
| **Port** | Implemented once by an adapter in `:core:*`, plus fakes in tests. Can change in a normal review. | `SessionRepository`, `AudioDecoder`, `ProcessingScheduler` |
| **Use case** | Called by ViewModels and workers. | `ImportAudio`, `ObserveSessions` |
| **Internal** | `internal` visibility; not callable from other modules. | `TranscriptChunker`, `NotesValidator` |

Kotlin's [explicit API mode](https://kotlinlang.org/docs/whatsnew14.html#explicit-api-mode-for-library-authors) is enabled in `:core:domain`, so every public declaration states its visibility on purpose.

## 2. Domain model

All model types are immutable `data class`es in `dev.notesmaker.core.domain.model`. Lists are `List`, never mutable. Validation that must always hold lives in `init` blocks, so an invalid object cannot exist.

```kotlin
data class Session(
    val id: SessionId,
    val title: String,
    val titleSource: TitleSource,          // Fallback, Generated, User
    val createdAtEpochMs: Long,
    val origin: SessionOrigin,             // Recorded, Imported(displayName)
    val language: LanguageTag,             // BCP 47, e.g. "en"
    val durationMs: Long?,                 // null until PrepareAudio or recording stop
    val audio: AudioRef?,                  // null while recording
    val status: SessionStatus,
    val processing: ProcessingState,       // stage, progress, failure, queue position
    val notesUpgradeAvailable: Boolean,
)

enum class SessionStatus { Recording, Waiting, Processing, Ready, Failed, Cancelled }

data class ProcessingState(
    val stage: StageId?,                   // current or last stage
    val progress: Float?,                  // 0..1 within the stage, null if not measurable
    val failure: ProcessingFailure?,
    val queuedAtEpochMs: Long?,            // queue order (FR-24)
)

@JvmInline value class LanguageTag(val value: String)
@JvmInline value class AudioRef(val relativePath: String)   // resolved only by AudioStore

data class Transcript(
    val sessionId: SessionId,
    val engineId: EngineId,
    val modelId: ModelId?,
    val language: LanguageTag,
    val segments: List<TranscriptSegment>,
)

data class TranscriptSegment(val index: Int, val startMs: Long, val endMs: Long, val text: String) {
    init { require(startMs in 0..endMs) }
}

data class Notes(
    val sessionId: SessionId,
    val title: String,                     // <= 80 chars (FR-40)
    val summary: List<String>,             // 3..8 sentences or bullets (FR-41)
    val sections: List<NoteSection>,       // time-ordered, non-overlapping (FR-42)
    val highlights: List<Highlight>,       // <= 10 (FR-43)
    val provenance: NotesProvenance,
    val edits: EditFlags,
)

data class NoteSection(val heading: String, val startMs: Long, val endMs: Long, val points: List<String>, val edited: Boolean = false)

data class Highlight(val kind: HighlightKind, val text: String, val atMs: Long, val edited: Boolean = false)
enum class HighlightKind { KeyPoint, Definition, Formula, Date, Instruction }

data class NotesProvenance(
    val generatorId: EngineId,             // shown as "Notes by ..." (FR-44)
    val modelId: ModelId?,
    val promptVersion: String?,            // LLM generators only (ADR-27)
    val transcriptRevision: Int,           // which transcript the notes were built from
    val generatedAtEpochMs: Long,
)

data class EditFlags(val summaryEdited: Boolean = false)
```

Notes invariants are enforced by one internal `NotesValidator` that every generator's output passes through (Section 5.4), not by each generator. The model classes only check what can never be wrong (non-negative times).

## 3. Persistence ports

Implemented in `:core:data` (Room, DataStore, files). Every write that replaces stage output is **one transaction** that deletes the old output and inserts the new, which makes stages idempotent (NFR-21).

```kotlin
interface SessionRepository {
    fun observeAll(): Flow<List<SessionListItem>>             // newest first; title sort is done in ObserveSessions
    fun observe(id: SessionId): Flow<Session?>
    suspend fun get(id: SessionId): Session?
    suspend fun create(draft: NewSession): Session
    suspend fun nextWaiting(): Session?                         // oldest queuedAt among Waiting
    suspend fun rename(id: SessionId, title: String, source: TitleSource)
    suspend fun setDuration(id: SessionId, durationMs: Long)
    suspend fun setAudio(id: SessionId, audio: AudioRef, durationMs: Long)
    suspend fun updateProgress(id: SessionId, stage: StageId, progress: Float?)
    suspend fun setNotesUpgradeAvailable(id: SessionId, available: Boolean)

    /**
     * Compare-and-set status change (ADR-22). Applies [change] only if the current status is in
     * [expected]; returns false otherwise. This is the only way to change status, so a cancel from
     * the UI and a completion from the worker can never both win.
     */
    suspend fun transition(id: SessionId, expected: Set<SessionStatus>, change: StatusChange): Boolean

    suspend fun findByStatus(vararg status: SessionStatus): List<Session>
    suspend fun delete(id: SessionId)                           // row and cascaded children only
}

data class SessionListItem(          // lightweight row for the list (ADR-18, NFR-15)
    val id: SessionId, val title: String, val createdAtEpochMs: Long,
    val durationMs: Long?, val status: SessionStatus, val stage: StageId?, val progress: Float?,
)

sealed interface StatusChange {
    data class Enqueue(val queuedAtEpochMs: Long) : StatusChange        // -> Waiting
    data object Start : StatusChange                                    // -> Processing
    data object Complete : StatusChange                                 // -> Ready
    data class Fail(val failure: ProcessingFailure) : StatusChange      // -> Failed
    data object Cancel : StatusChange                                   // -> Cancelled
    data object Requeue : StatusChange                                  // Processing -> Waiting, keeps queuedAt
}

interface TranscriptRepository {
    fun observe(id: SessionId): Flow<Transcript?>
    suspend fun get(id: SessionId): Transcript?
    /** Appends segments and moves the checkpoint in one transaction (ADR-13). */
    suspend fun appendCommitted(id: SessionId, header: TranscriptHeader, segments: List<TranscriptSegment>, committedUntilMs: Long)
    suspend fun checkpoint(id: SessionId): TranscriptCheckpoint?   // null = not started
    suspend fun markComplete(id: SessionId)                         // bumps transcript revision
    suspend fun clear(id: SessionId)                                // for re-transcription
}

data class TranscriptCheckpoint(val header: TranscriptHeader, val committedUntilMs: Long, val nextIndex: Int, val complete: Boolean, val revision: Int)
data class TranscriptHeader(val engineId: EngineId, val modelId: ModelId?, val language: LanguageTag)

interface NotesRepository {
    fun observe(id: SessionId): Flow<Notes?>
    suspend fun get(id: SessionId): Notes?
    suspend fun replace(notes: Notes)                       // transactional replace (NFR-21)
    suspend fun applyEdit(id: SessionId, edit: NotesEdit)   // marks the edited item (FR-65)
}

sealed interface NotesEdit {
    data class Summary(val summary: List<String>) : NotesEdit
    data class Section(val index: Int, val heading: String, val points: List<String>) : NotesEdit
    data class HighlightText(val index: Int, val text: String) : NotesEdit
    data class RemoveHighlight(val index: Int) : NotesEdit
}

interface AudioStore {
    suspend fun newRecordingTarget(id: SessionId): RecordingTarget        // paths for audio.aac and audio.m4a
    suspend fun importToTemp(source: ImportSource, onProgress: (Long) -> Unit): Outcome<TempAudio, CaptureFailure>
    suspend fun commitTemp(temp: TempAudio, id: SessionId): AudioRef
    suspend fun discardTemp(temp: TempAudio)
    suspend fun open(ref: AudioRef): AudioFile                             // resolved, readable handle
    suspend fun deleteSession(id: SessionId)
    suspend fun freeBytes(): Long
    suspend fun orphans(knownIds: Set<SessionId>): List<OrphanFile>
}

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun update(transform: (UserPreferences) -> UserPreferences)
}

data class UserPreferences(
    val sortOrder: SortOrder = SortOrder.NewestFirst,
    val defaultLanguage: LanguageTag? = null,          // null = device language when supported
    val preferredTranscriptionEngine: EngineId? = null,
    val preferredNoteGenerator: EngineId? = null,
    val transcriptionQuality: QualityTier = QualityTier.Fast,
    val recordingQuality: RecordingQuality = RecordingQuality.Standard,
    val consentReminderDismissed: Boolean = false,
    val deferOnLowBattery: Boolean = false,
    val optedInEngines: Set<EngineId> = emptySet(),    // cloud engines, 0.3+ (ADR-05)
)

enum class SortOrder { NewestFirst, OldestFirst, TitleAscending, TitleDescending }
```

`AudioFile`, `TempAudio`, `RecordingTarget` and `ImportSource` are small value types wrapping a `java.io.File` or an opaque content URI string. `java.io.File` is plain JVM, so the domain stays free of Android types (NFR-64). `ImportSource` holds the URI as a string; only `:core:data` turns it into an Android `Uri`.

## 4. Media ports

Implemented in `:core:media`.

```kotlin
/** Streams any supported file as 16 kHz mono float PCM, never decoding the whole file at once (ADR-08). */
interface AudioDecoder {
    fun decode(file: AudioFile, fromMs: Long = 0, windowMs: Long = 30_000): Flow<PcmWindow>
}

/** One window of PCM on the session timeline. The array is owned by the receiver. */
class PcmWindow(val startMs: Long, val samples: FloatArray, val sampleRate: Int = 16_000) {
    val endMs: Long get() = startMs + samples.size * 1000L / sampleRate
}

interface AudioProbe {
    suspend fun probe(file: AudioFile): Outcome<AudioInfo, CaptureFailure>
}
data class AudioInfo(val durationMs: Long, val mimeType: String, val sampleRate: Int, val channels: Int)

interface RecordingController {
    val state: StateFlow<RecordingState>
    suspend fun start(language: LanguageTag, quality: RecordingQuality): Outcome<SessionId, CaptureFailure>
    fun pause()
    fun resume()
    fun stop()                    // finalization is asynchronous; observe state for Stopped
    fun discard()
}

sealed interface RecordingState {
    data object Idle : RecordingState
    data class Active(
        val sessionId: SessionId,
        val elapsedMs: Long,
        val level: Float,                       // 0..1, for the meter (FR-01)
        val paused: PauseReason?,               // null = recording
    ) : RecordingState
    data class Finishing(val sessionId: SessionId) : RecordingState   // remuxing
    data class Stopped(val sessionId: SessionId, val result: StopResult) : RecordingState
}

enum class PauseReason { User, PhoneCall, MicrophoneTaken, AudioFocusLost }
enum class StopResult { Saved, Discarded, LowStorage, Failed }

interface VoiceNotePlayer {
    val state: StateFlow<PlayerState>          // loaded session, playing, positionMs, durationMs
    suspend fun load(id: SessionId, audio: AudioRef)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun skipBy(deltaMs: Long)                  // +/- 10 s (FR-61)
    fun release()
}
```

`RecordingController.start` returns once the service has created the session row and the microphone is open, so FR-01's "capture within 1 second" is measurable at this call. The full recording state machine is in [LLD Section 4](LOW_LEVEL_DESIGN.md#4-recording-state-machine).

## 5. Engine plug-in API

These are the interfaces that make the app extensible (NFR-60, NFR-65). They are **Plug-in API** stability: changing them needs an ADR.

### 5.1 Shared engine contract

```kotlin
interface Engine {
    val info: EngineInfo
    /** Cheap, side-effect free, may be called often (for example on every Settings recomposition). */
    suspend fun capability(request: CapabilityRequest): Capability
}

data class EngineInfo(
    val id: EngineId,
    val kind: EngineKind,                  // Transcription, Notes
    val displayName: UiLabel,              // resource-free label, resolved by :core:ui
    val location: EngineLocation,          // OnDevice, Cloud
    val qualityRank: Int,                  // higher = preferred by default (ADR-15)
    val languages: Set<LanguageTag>?,      // null = any
)

data class CapabilityRequest(val language: LanguageTag, val quality: QualityTier, val inForeground: Boolean)

sealed interface Capability {
    data object Ready : Capability
    data class NeedsModel(val modelId: ModelId, val sizeBytes: Long) : Capability
    data object NeedsForeground : Capability
    data object NeedsOptIn : Capability
    data class NeedsDownload(val description: String) : Capability   // system-managed model, e.g. Gemini Nano
    data class Unsupported(val reason: String) : Capability
}
```

`NeedsDownload` is new compared with the HLD list: ML Kit reports that Gemini Nano is "downloadable" on supported devices, and that state is different from a model the app installs itself (`NeedsModel`). It is treated like `NeedsModel` by the selector but triggers the system download instead of `InstallModel`.

The engine is told whether the app is in the foreground instead of asking an Android API itself, so engines stay testable and the foreground signal has one owner (`ForegroundState`).

### 5.2 Transcription (ADR-21)

```kotlin
interface TranscriptionEngine : Engine {
    /**
     * Transcribes [audio] from [request.resumeFromMs] to the end and emits events as it goes.
     *
     * Contract (checked by TranscriptionEngineContract, NFR-71):
     *  - Committed.untilMs is strictly increasing and never exceeds the audio duration.
     *  - Every segment in a Committed event lies within [previous untilMs, untilMs] and segments are
     *    in time order without overlap. Times are absolute on the session timeline.
     *  - Everything before untilMs is final: the caller persists it and will resume after it.
     *  - The flow completes normally at the end of the audio, or throws CancellationException.
     *  - Expected failures are emitted as a terminal Failed event, never thrown.
     */
    fun transcribe(audio: AudioFile, request: TranscriptionRequest): Flow<TranscriptionEvent>
}

data class TranscriptionRequest(
    val language: LanguageTag,
    val modelId: ModelId?,
    val resumeFromMs: Long,
    val continuationText: String?,         // tail of already-committed text, for context across resume
)

sealed interface TranscriptionEvent {
    data class Committed(val segments: List<RawSegment>, val untilMs: Long) : TranscriptionEvent
    data class Progress(val processedMs: Long, val totalMs: Long) : TranscriptionEvent
    data class Failed(val failure: ProcessingFailure) : TranscriptionEvent
}

data class RawSegment(val startMs: Long, val endMs: Long, val text: String)
```

Why the engine receives the **file** and emits **commits**, rather than receiving PCM windows from the stage: whisper.cpp works on PCM windows, but ML Kit Speech Recognition (the planned second engine) accepts a file or its own audio stream. Giving the engine the file and the resume position lets each engine read audio its own way, while the `Committed(untilMs)` event gives the stage everything it needs to checkpoint. The whisper engine uses `AudioDecoder` internally to get windows. See ADR-21 for the alternatives.

### 5.3 Note generation

```kotlin
interface NoteGenerator : Engine {
    /**
     * Builds complete notes from a finished transcript.
     * Output is validated by the pipeline (NotesValidator) before it is saved, so a generator may
     * return slightly out-of-range times; it must not return an empty title or zero sections for a
     * non-empty transcript.
     */
    suspend fun generate(input: NotesInput, progress: ProgressSink): Outcome<DraftNotes, ProcessingFailure>
}

data class NotesInput(val transcript: Transcript, val durationMs: Long, val language: LanguageTag, val sessionCreatedAtEpochMs: Long)

/** Notes before provenance, edit flags and validation are attached by the pipeline. */
data class DraftNotes(
    val title: String,
    val summary: List<String>,
    val sections: List<DraftSection>,
    val highlights: List<DraftHighlight>,
    val modelId: ModelId? = null,
    val promptVersion: String? = null,
)

fun interface ProgressSink { suspend fun report(fraction: Float) }
```

### 5.4 Language models and the shared LLM generator (ADR-16, ADR-27)

```kotlin
interface LanguageModel : Engine {
    /** Input budget in tokens for one request, prompt included. */
    val inputTokenBudget: Int
    /** Rough token count for budgeting. Adapters with a real tokenizer use it; others estimate. */
    fun estimateTokens(text: String): Int = (text.length + 3) / 4

    suspend fun complete(request: CompletionRequest): Outcome<String, LanguageModelFailure>
}

data class CompletionRequest(
    val system: String,
    val prompt: String,
    val responseSchema: JsonSchema?,       // used when the backend supports structured output
    val maxOutputTokens: Int,
    val temperature: Float = 0.2f,
)

sealed interface LanguageModelFailure {
    data object NotReady : LanguageModelFailure           // left foreground, model unloaded
    data object QuotaExceeded : LanguageModelFailure      // ML Kit per-app quota
    data object InputTooLong : LanguageModelFailure
    data class Backend(val detail: String) : LanguageModelFailure
}
```

`LlmNoteGenerator` is a **domain class**, not a port. It implements `NoteGenerator` on top of one `LanguageModel` and owns the algorithm:

```kotlin
class LlmNoteGenerator(
    private val model: LanguageModel,
    private val chunker: TranscriptChunker,
    private val prompts: NotesPromptSet,           // versioned templates (ADR-27)
    private val parser: StructuredNotesParser,     // kotlinx.serialization, lenient
) : NoteGenerator {
    override val info: EngineInfo = model.info.copy(kind = EngineKind.Notes)
    override suspend fun capability(request: CapabilityRequest) = model.capability(request)
    override suspend fun generate(input: NotesInput, progress: ProgressSink): Outcome<DraftNotes, ProcessingFailure>
}
```

Every `LanguageModel` registered in DI is wrapped into an `LlmNoteGenerator` by one Hilt provider in `:app`, so the notes generator set contains both direct generators (`BasicNoteGenerator`) and wrapped models. The engine ID stays the model's ID, so the user sees "Notes by Gemini Nano", not "Notes by LLM".

Prompts, schema and validation rules are in [LLD Section 7](LOW_LEVEL_DESIGN.md#7-note-generation-details).

### 5.5 Engine selection

```kotlin
class EngineSelector @Inject constructor(private val foreground: ForegroundState) {
    suspend fun <E : Engine> select(candidates: Set<E>, context: SelectionContext): Selection<E>
}

data class SelectionContext(val language: LanguageTag, val quality: QualityTier, val preferred: EngineId?, val optedIn: Set<EngineId>)

sealed interface Selection<out E : Engine> {
    data class Chosen<E : Engine>(val engine: E, val betterBlockedByForeground: Boolean) : Selection<E>
    data class NoneReady(val blockers: List<Pair<EngineInfo, Capability>>) : Selection<Nothing>
}
```

Algorithm, unchanged from ADR-15: drop `Unsupported` and not-opted-in cloud engines; sort by preferred first, then `qualityRank` descending, then ID for determinism; return the first `Ready`. `betterBlockedByForeground` is true if any engine ranked above the chosen one reported `NeedsForeground`. `NoneReady` carries the blockers so the stage can turn `NeedsModel` into `ProcessingFailure.ModelMissing` with an Install action.

### 5.6 Exporters (0.2)

```kotlin
interface NotesExporter {
    val id: ExporterId
    val label: UiLabel
    val mimeType: String
    val fileExtension: String
    suspend fun export(session: Session, notes: Notes, transcript: Transcript?, options: ExportOptions, out: java.io.OutputStream)
}
data class ExportOptions(val includeTranscript: Boolean = false, val includeTimestamps: Boolean = true)
```

Writing to an `OutputStream` instead of returning a `String` lets a PDF exporter (binary) and a text exporter share one interface (NFR-62). The share flow writes to a cache file and hands a `FileProvider` URI to the share sheet; including the audio file (FR-67) is done by the share flow, not by exporters.

### 5.7 Registration

Each engine module contributes exactly one Hilt module:

```kotlin
@Module @InstallIn(SingletonComponent::class)
abstract class WhisperEngineModule {
    @Binds @IntoSet abstract fun bind(engine: WhisperTranscriptionEngine): TranscriptionEngine
}
```

Engine IDs must be unique. `EngineRegistry` (domain, injected with all sets) checks this at start-up in debug builds and fails fast with the duplicate ID, the same idea as Daybreak's registry duplicate-ID test.

## 6. Pipeline API (ADR-22)

```kotlin
interface PipelineStage {
    val id: StageId
    /** True if this stage's output for the session exists and matches its inputs. Must be cheap. */
    suspend fun isComplete(ctx: StageContext): Boolean
    /** Does the work. Must be idempotent: running twice leaves one copy of the output. */
    suspend fun run(ctx: StageContext): StageOutcome
}

data class StageContext(
    val session: Session,
    val progress: ProgressSink,
    val trigger: PipelineTrigger,          // Initial, Retry, RegenerateNotes(generatorId)
)

sealed interface StageOutcome {
    data object Completed : StageOutcome
    data class Failed(val failure: ProcessingFailure) : StageOutcome
}

/** Ordered list of stages, provided by DI. The only shared line that changes when a stage is added. */
@JvmInline value class StagePlan(val stages: List<PipelineStage>)

class ProcessingPipeline @Inject constructor(
    private val plan: StagePlan,
    private val sessions: SessionRepository,
    private val clock: Clock,
) {
    suspend fun run(id: SessionId, trigger: PipelineTrigger): PipelineResult
}

sealed interface PipelineResult {
    data object Completed : PipelineResult
    data class Failed(val stage: StageId, val failure: ProcessingFailure) : PipelineResult
    data object Cancelled : PipelineResult
    data object Skipped : PipelineResult        // session was not in an expected state
}
```

The HLD sketch had a third outcome, `Deferred`. It is dropped: ADR-15 already decided that the pipeline never waits for the foreground, and no current stage needs to defer. If a future stage does, adding a `StageOutcome` subtype is a Plug-in API change with an ADR, which is the right amount of friction.

Stages for 0.1, in order: `PrepareAudioStage`, `TranscribeStage`, `GenerateNotesStage`, `FinalizeStage`. Their inputs, outputs and checkpoints are in [LLD Section 5](LOW_LEVEL_DESIGN.md#5-processing-details).

The `PipelineTrigger` exists so `RegenerateNotes` reuses the same pipeline rather than a second code path: with `RegenerateNotes(generatorId)`, `GenerateNotesStage.isComplete` returns false and `FinalizeStage` keeps a user-edited title (FR-64).

## 7. Scheduling and environment ports

```kotlin
interface ProcessingScheduler {
    /** Makes sure the single processing worker is running or scheduled (ADR-13). Idempotent. */
    fun ensureRunning()
    /** Cancels the in-flight work for this session if it is the one running. */
    fun cancelCurrent(id: SessionId)
}

interface ForegroundState { val isForeground: StateFlow<Boolean> }

interface ModelCatalog {
    fun observe(): Flow<List<SpeechModel>>          // bundled catalog joined with install state
    suspend fun get(id: ModelId): SpeechModel?
    suspend fun defaultFor(engine: EngineId, language: LanguageTag, quality: QualityTier): SpeechModel?
    suspend fun installedFile(id: ModelId): java.io.File?   // verified file or null
}

interface ModelInstaller {
    fun install(id: ModelId, allowMetered: Boolean = false)
    fun cancel(id: ModelId)
    suspend fun delete(id: ModelId)
    fun observe(id: ModelId): Flow<InstallState>
}

sealed interface InstallState {
    data object NotInstalled : InstallState
    data class Queued(val waitingForUnmetered: Boolean) : InstallState
    data class Downloading(val bytes: Long, val total: Long) : InstallState
    data object Verifying : InstallState
    data object Installed : InstallState
    data class Failed(val reason: InstallFailure) : InstallState   // Network, ChecksumMismatch, StorageFull
}

interface Clock { fun nowEpochMs(): Long }
interface IdGenerator { fun newSessionId(): SessionId }
```

`ProcessingScheduler.cancelCurrent` exists because cancelling a coroutine inside a running worker needs a handle the domain does not have. The adapter keeps a process-wide map from session ID to the running `Job` (LLD Section 5.4).

## 8. Use cases

Each use case is a class with `@Inject constructor` and one `operator fun invoke`. ViewModels depend on use cases and on read-only `observe` methods of repositories through `Observe*` use cases; they never call a write method of a repository directly (ADR-29).

| Use case | Signature | Notes |
| --- | --- | --- |
| `StartRecording` | `suspend (language: LanguageTag?) -> Outcome<SessionId, CaptureFailure>` | Checks free space for 2 hours at the chosen quality (FR-06), reads quality from preferences, then `RecordingController.start` |
| `PauseRecording`, `ResumeRecording`, `StopRecording`, `DiscardRecording` | `() -> Unit` | Thin, but they are the notification and UI entry points; `StopRecording` enqueues after the service reports `Saved` |
| `ImportAudio` | `suspend (source: ImportSource, language: LanguageTag?, onProgress: (Float) -> Unit) -> Outcome<SessionId, CaptureFailure>` | Copy to temp, probe, 3-hour limit (FR-12), create session, commit audio, enqueue. Runs in the application scope (HLD 6.2) |
| `CreateSessionFromAudio` | `suspend (temp: TempAudio, info: AudioInfo, origin: SessionOrigin, language: LanguageTag) -> SessionId` | Shared tail of every input (NFR-61). Import and recovery call it; a future input calls it too |
| `EnqueueProcessing` | `suspend (id: SessionId) -> Boolean` | `transition(expected = {Failed, Cancelled, Ready*}, Enqueue)` then `ensureRunning()` |
| `ProcessNextSession` | `suspend () -> PipelineResult?` | Called in a loop by the worker; null when the queue is empty |
| `CancelProcessing` | `suspend (id: SessionId) -> Boolean` | CAS to `Cancelled` from `Waiting` or `Processing`; then `cancelCurrent` |
| `RetryProcessing` | `suspend (id: SessionId) -> Boolean` | Same as enqueue; completed stages are skipped by checkpoints (FR-22, FR-23) |
| `ObserveSessions` | `(query: String = "") -> Flow<SessionListState>` | Combines repository flow with sort preference; title sort with `TitleCollator` (ADR-18); ties by date newest first (FR-51) |
| `SetSortOrder` | `suspend (order: SortOrder)` | FR-52 |
| `ObserveSessionDetails` | `(id: SessionId) -> Flow<SessionDetails?>` | `combine` of session, notes, transcript |
| `RenameSession` | `suspend (id, title) -> Outcome<Unit, ValidationFailure>` | Trims, rejects blank, caps 80 characters, sets `TitleSource.User` |
| `EditNotes` | `suspend (id, edit: NotesEdit)` | FR-65 |
| `RegenerateNotes` | `suspend (id, generatorId: EngineId?, overwriteUserTitle: Boolean) -> Boolean` | Enqueues with `PipelineTrigger.RegenerateNotes`. Requires a complete transcript |
| `DeleteSession` | `suspend (id: SessionId)` | Cancel if running, delete row, then files (HLD 9.3) |
| `ExportNotes` | `suspend (id, exporterId, options, out: OutputStream)` | 0.2 |
| `ListEngines` | `() -> Flow<List<EngineStatus>>` | Every registered engine with its current capability, for Settings |
| `InstallModel`, `DeleteModel` | `(id: ModelId)` | Delegate to `ModelInstaller`; deletion refused while a session is transcribing with it |
| `RecoverInterruptedWork` | `suspend () -> RecoveryReport` | HLD 6.6; details in LLD Section 5.6 |
| `ShouldShowConsentReminder`, `DismissConsentReminder` | `Flow<Boolean>`, `suspend ()` | FR-07 |

`*` `Ready` is allowed only for `RegenerateNotes`. The full status transition table is in [LLD Section 5.1](LOW_LEVEL_DESIGN.md#51-session-state-machine-adr-22).

## 9. Presentation contracts

Not a domain API, but every screen follows the same shape so a new screen is predictable (HLD 7.1).

```kotlin
// Feature module public surface: one entry builder per route, nothing else is public.
fun EntryProviderBuilder<NavKey>.sessionsEntries(navigator: SessionsNavigator)

interface SessionsNavigator { fun openSession(id: SessionId); fun openCapture() }

@HiltViewModel
class SessionsViewModel @Inject constructor(
    observeSessions: ObserveSessions,
    private val setSortOrder: SetSortOrder,
    private val retry: RetryProcessing,
) : ViewModel() {
    val uiState: StateFlow<SessionsUiState>
    fun onSortChanged(order: SortOrder)
    fun onRetry(id: SessionId)
}

data class SessionsUiState(
    val loading: Boolean = true,
    val items: List<SessionRowUi> = emptyList(),   // @Immutable, stable keys
    val sort: SortOrder = SortOrder.NewestFirst,
    val empty: Boolean = false,                    // FR-54
)
```

Rules:

1. Each feature module exposes only its entry builder, its navigator interface and nothing else (`internal` everywhere else). `:app` implements the navigator interfaces with the back stack, so features never know about each other (HLD 4.2 rule 2).
2. `UiState` classes are `@Immutable` data classes with `List` fields wrapped by `kotlinx.collections.immutable` `ImmutableList`, so Compose can skip recomposition reliably.
3. One-off effects are fields in `UiState` (for example `pendingShare: ShareRequest?`) acknowledged by a ViewModel call (HLD 7.1).
4. Route keys are `@Serializable` objects or data classes in `:app` implementing Navigation 3's `NavKey`: `Capture`, `Sessions`, `SessionDetails(id: String)`, `Settings`, `Models(highlight: String?)`.

## 10. External API: model download

The only network call in 0.1 (NFR-31). ADR-25 chooses the client.

| Item | Contract |
| --- | --- |
| Catalog | `assets/models/catalog.json`, bundled and versioned with the app. Never fetched from the network in 0.1, so the checksum source cannot be tampered with remotely (NFR-34). |
| Request | `GET <catalog url>` over HTTPS only. `Range: bytes=<partial size>-` when a `.partial` file exists. `User-Agent: NotesMaker/<version>` with no user or device details. |
| Responses | `200` (restart from 0), `206` (append; `Content-Range` start must equal the partial size, otherwise restart), `416` (partial is complete or invalid: verify, else delete and restart), `404`/`410` (catalog entry broken: `Failed`), `5xx` and network errors (`Result.retry()` with exponential backoff). Redirects followed only to HTTPS. |
| Verification | SHA-256 of the full file must equal the catalog value. Mismatch deletes the file and fails with `ChecksumMismatch` (no automatic retry, to avoid loops). |
| Install | Atomic rename from `models/<id>.partial` to `models/<id>.bin` after verification. |

Catalog format:

```json
{
  "version": 1,
  "models": [
    {
      "id": "whisper-base.en-q5_1",
      "engine": "whisper.cpp",
      "languages": ["en"],
      "quality": "Fast",
      "sizeBytes": 59707625,
      "sha256": "<pinned at implementation time>",
      "url": "https://huggingface.co/ggerganov/whisper.cpp/resolve/<commit>/ggml-base.en-q5_1.bin",
      "minRamMb": 3000
    }
  ]
}
```

Sizes and checksums are placeholders until the spike fixes the model list; the URL pins a repository commit, not `main`, so the file behind a checksum cannot change.

## 11. Contract tests (NFR-71)

Each Plug-in API ships an abstract JUnit suite in a `testFixtures` source set of `:core:domain`. An implementation's test class extends it and supplies the subject.

| Suite | Checks |
| --- | --- |
| `TranscriptionEngineContract` | Committed `untilMs` strictly increasing; segments inside the committed range, ordered, non-overlapping; resume from a midpoint yields no segment before it; cancellation stops within one window; silent input yields no or empty segments; missing model reported as capability, not exception |
| `NoteGeneratorContract` | Non-empty title and at least one section for a non-empty transcript; output passes `NotesValidator` after clamping; deterministic for the basic generator; respects cancellation |
| `LanguageModelContract` | `complete` respects `maxOutputTokens` roughly; returns `InputTooLong` instead of throwing for oversized input; capability reflects foreground for foreground-only adapters |
| `NotesExporterContract` | Non-empty output; MIME type and extension consistent; no transcript text when `includeTranscript = false` |
| `PipelineStageContract` | Running twice yields one output (idempotency, NFR-21); `isComplete` true after `run`; cancellation leaves a resumable state |

Repository ports have fakes in the same `testFixtures` set (`FakeSessionRepository` and so on), so feature and worker tests use one shared, reviewed fake instead of ad-hoc mocks (ADR-30).

## 12. Changing these APIs

1. A **Plug-in API** change needs an ADR, an update to its contract suite, and an update to every implementation in the same change.
2. A new failure subtype, `Capability` subtype or `StageOutcome` subtype is a Plug-in API change, because `when` expressions over them must be updated.
3. Adding a use case, port method or `UiState` field needs only a normal review.
4. A persisted ID (`EngineId`, `StageId`, `ModelId`, failure `code`) is never renamed. Retired IDs stay readable.
