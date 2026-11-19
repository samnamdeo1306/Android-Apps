package dev.notesmaker.core.domain.pipeline

import dev.notesmaker.core.domain.engine.ProgressSink
import dev.notesmaker.core.domain.error.ProcessingFailure
import dev.notesmaker.core.domain.model.EngineId
import dev.notesmaker.core.domain.model.Session
import dev.notesmaker.core.domain.model.StageId

/**
 * One step of processing a session (ADR-22). **Plug-in API**: changing it needs an ADR and an
 * update to `PipelineStageContract`.
 */
public interface PipelineStage {
    public val id: StageId

    /** True if this stage's output for the session exists and matches its inputs. Must be cheap. */
    public suspend fun isComplete(ctx: StageContext): Boolean

    /** Does the work. Must be idempotent: running twice leaves one copy of the output (NFR-21). */
    public suspend fun run(ctx: StageContext): StageOutcome
}

public data class StageContext(
    val session: Session,
    val progress: ProgressSink,
    val trigger: PipelineTrigger,
)

public sealed interface StageOutcome {
    public data object Completed : StageOutcome

    public data class Failed(val failure: ProcessingFailure) : StageOutcome
}

/** Ordered list of stages, provided by DI. The only shared line that changes when a stage is added. */
@JvmInline
public value class StagePlan(public val stages: List<PipelineStage>) {
    init {
        require(stages.map { it.id }.toSet().size == stages.size) { "Stage IDs must be unique: ${stages.map { it.id }}" }
    }
}

/** Why a session is being processed. Persisted in `sessions.trigger` through [PipelineTriggerCodec]. */
public sealed interface PipelineTrigger {
    public data object Initial : PipelineTrigger

    public data object Retry : PipelineTrigger

    /** Re-run note generation, optionally with a specific generator (FR-46). */
    public data class RegenerateNotes(val generatorId: EngineId?) : PipelineTrigger
}

public sealed interface PipelineResult {
    public data object Completed : PipelineResult

    public data class Failed(val stage: StageId, val failure: ProcessingFailure) : PipelineResult

    public data object Cancelled : PipelineResult

    /** The session was not in an expected state. */
    public data object Skipped : PipelineResult
}

/** Persisted form of [PipelineTrigger]: `initial`, `retry`, `regenerate` or `regenerate:<engineId>` (LLD Section 3.1). */
public object PipelineTriggerCodec {
    private const val REGENERATE = "regenerate"

    public fun encode(trigger: PipelineTrigger): String = when (trigger) {
        PipelineTrigger.Initial -> "initial"
        PipelineTrigger.Retry -> "retry"
        is PipelineTrigger.RegenerateNotes -> trigger.generatorId?.let { "$REGENERATE:${it.value}" } ?: REGENERATE
    }

    /** Unknown values decode to [PipelineTrigger.Retry], the safe way to process a session again. */
    public fun decode(value: String): PipelineTrigger = when {
        value == "initial" -> PipelineTrigger.Initial
        value == "retry" -> PipelineTrigger.Retry
        value == REGENERATE -> PipelineTrigger.RegenerateNotes(null)
        value.startsWith("$REGENERATE:") -> PipelineTrigger.RegenerateNotes(EngineId(value.substringAfter(':')))
        else -> PipelineTrigger.Retry
    }
}
