package dev.notesmaker.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import dev.notesmaker.core.domain.engine.NoteGenerator
import dev.notesmaker.core.domain.engine.TranscriptionEngine

/**
 * Declares the engine sets (ADR-12) so the graph is valid while they are empty. Each engine module
 * adds itself with `@Binds @IntoSet` in its own Hilt module; nothing here lists engines by name.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {
    @Multibinds
    abstract fun transcriptionEngines(): Set<TranscriptionEngine>

    @Multibinds
    abstract fun noteGenerators(): Set<NoteGenerator>
}
