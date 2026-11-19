package dev.notesmaker.core.domain.engine

import dev.notesmaker.core.domain.model.EngineId
import javax.inject.Inject

/**
 * Every registered engine, collected by DI multibinding (ADR-12): each engine module adds itself
 * with `@IntoSet`, and nothing lists engines by name.
 */
public class EngineRegistry @Inject constructor(
    public val transcriptionEngines: Set<@JvmSuppressWildcards TranscriptionEngine>,
    public val noteGenerators: Set<@JvmSuppressWildcards NoteGenerator>,
) {
    /**
     * Engine IDs are persisted and shown to users, so they must be unique. Called at start-up in
     * debug builds to fail fast with the duplicate ID (API design Section 5.7).
     */
    public fun verifyUniqueIds() {
        val duplicates = duplicateIds()
        check(duplicates.isEmpty()) { "Duplicate engine IDs registered: ${duplicates.joinToString { it.value }}" }
    }

    public fun duplicateIds(): Set<EngineId> =
        (transcriptionEngines + noteGenerators)
            .groupBy { it.info.id }
            .filterValues { it.size > 1 }
            .keys
}
