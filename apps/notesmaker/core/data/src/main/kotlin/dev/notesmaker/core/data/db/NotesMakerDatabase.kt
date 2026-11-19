package dev.notesmaker.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Version history (each version's schema is committed under `core/data/schemas/`):
 *  - 1: initial schema (ADR-23).
 *
 * Additive changes use `@AutoMigration`; anything else is a hand-written `Migration` with a
 * `MigrationTestHelper` test from every released version (NFR-22). Destructive fallback is never used.
 */
@Database(
    version = 1,
    exportSchema = true,
    entities = [
        SessionEntity::class,
        TranscriptEntity::class,
        TranscriptSegmentEntity::class,
        NotesEntity::class,
        NoteSectionEntity::class,
        HighlightEntity::class,
    ],
)
@TypeConverters(StringListConverter::class)
internal abstract class NotesMakerDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    abstract fun transcriptDao(): TranscriptDao

    abstract fun notesDao(): NotesDao

    companion object {
        const val FILE_NAME = "notesmaker.db"

        fun build(context: Context): NotesMakerDatabase =
            Room.databaseBuilder(context, NotesMakerDatabase::class.java, FILE_NAME).build()
    }
}
