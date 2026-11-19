package dev.notesmaker.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.notesmaker.core.domain.engine.EngineRegistry
import javax.inject.Inject

@HiltAndroidApp
class NotesMakerApplication : Application() {

    @Inject
    lateinit var engines: EngineRegistry

    override fun onCreate() {
        super.onCreate()
        // Engine IDs are persisted, so a duplicate is a bug to catch on the first debug run (API design Section 5.7).
        if (BuildConfig.DEBUG) engines.verifyUniqueIds()
    }
}
