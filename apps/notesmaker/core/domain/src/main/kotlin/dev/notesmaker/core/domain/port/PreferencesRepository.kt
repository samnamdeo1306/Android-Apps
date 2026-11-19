package dev.notesmaker.core.domain.port

import dev.notesmaker.core.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

/** **Port.** A read failure emits defaults, so a corrupt file never blocks the app (LLD Section 3.3). */
public interface PreferencesRepository {
    public val preferences: Flow<UserPreferences>

    public suspend fun update(transform: (UserPreferences) -> UserPreferences)
}
