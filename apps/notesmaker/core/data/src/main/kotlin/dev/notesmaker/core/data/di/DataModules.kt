package dev.notesmaker.core.data.di

import android.content.Context
import dev.notesmaker.core.data.db.NotesDao
import dev.notesmaker.core.data.db.NotesMakerDatabase
import dev.notesmaker.core.data.db.SessionDao
import dev.notesmaker.core.data.db.TranscriptDao
import dev.notesmaker.core.data.environment.SystemClock
import dev.notesmaker.core.data.environment.UuidIdGenerator
import dev.notesmaker.core.data.repository.RoomNotesRepository
import dev.notesmaker.core.data.repository.RoomSessionRepository
import dev.notesmaker.core.data.repository.RoomTranscriptRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.notesmaker.core.domain.port.Clock
import dev.notesmaker.core.domain.port.IdGenerator
import dev.notesmaker.core.domain.port.NotesRepository
import dev.notesmaker.core.domain.port.SessionRepository
import dev.notesmaker.core.domain.port.TranscriptRepository
import javax.inject.Singleton

/*
 * Bindings from domain ports to this module's adapters (ADR-12). Everything here is internal: other
 * modules see only the ports, and `:app` gets the bindings through Hilt's aggregation.
 */

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): NotesMakerDatabase = NotesMakerDatabase.build(context)

    @Provides
    fun sessionDao(db: NotesMakerDatabase): SessionDao = db.sessionDao()

    @Provides
    fun transcriptDao(db: NotesMakerDatabase): TranscriptDao = db.transcriptDao()

    @Provides
    fun notesDao(db: NotesMakerDatabase): NotesDao = db.notesDao()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun sessions(impl: RoomSessionRepository): SessionRepository

    @Binds
    @Singleton
    abstract fun transcripts(impl: RoomTranscriptRepository): TranscriptRepository

    @Binds
    @Singleton
    abstract fun notes(impl: RoomNotesRepository): NotesRepository
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class EnvironmentModule {
    @Binds
    abstract fun clock(impl: SystemClock): Clock

    @Binds
    abstract fun ids(impl: UuidIdGenerator): IdGenerator
}
