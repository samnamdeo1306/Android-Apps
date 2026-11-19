package dev.notesmaker.core.domain.testing

import dev.notesmaker.core.domain.model.EngineId
import dev.notesmaker.core.domain.model.InstallState
import dev.notesmaker.core.domain.model.LanguageTag
import dev.notesmaker.core.domain.model.ModelId
import dev.notesmaker.core.domain.model.QualityTier
import dev.notesmaker.core.domain.model.SpeechModel
import dev.notesmaker.core.domain.port.ModelCatalog
import dev.notesmaker.core.domain.port.ModelInstaller
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.concurrent.ConcurrentHashMap

public class FakeModelCatalog(models: List<SpeechModel> = emptyList()) : ModelCatalog {
    public val models: MutableStateFlow<List<SpeechModel>> = MutableStateFlow(models)

    override fun observe(): Flow<List<SpeechModel>> = models

    override suspend fun get(id: ModelId): SpeechModel? = models.value.firstOrNull { it.id == id }

    override suspend fun defaultFor(engine: EngineId, language: LanguageTag, quality: QualityTier): SpeechModel? =
        models.value.firstOrNull { it.engine == engine && it.quality == quality && (it.languages == null || language in it.languages) }

    override suspend fun installedFile(id: ModelId): File? = get(id)?.takeIf { it.installed }?.let { File("/fake-models/${it.id}.bin") }
}

/** Installs instantly unless [instant] is false, in which case the test moves the state itself. */
public class FakeModelInstaller(private val instant: Boolean = true) : ModelInstaller {
    private val states = ConcurrentHashMap<ModelId, MutableStateFlow<InstallState>>()

    public fun stateOf(id: ModelId): MutableStateFlow<InstallState> = states.getOrPut(id) { MutableStateFlow(InstallState.NotInstalled) }

    override fun install(id: ModelId, allowMetered: Boolean) {
        stateOf(id).value = if (instant) InstallState.Installed else InstallState.Queued(waitingForUnmetered = !allowMetered)
    }

    override fun cancel(id: ModelId) {
        stateOf(id).value = InstallState.NotInstalled
    }

    override suspend fun delete(id: ModelId) {
        stateOf(id).value = InstallState.NotInstalled
    }

    override fun observe(id: ModelId): Flow<InstallState> = stateOf(id).map { it }
}

public fun speechModel(
    id: String = "fake-model",
    engine: EngineId = TestData.TRANSCRIPTION_ENGINE,
    quality: QualityTier = QualityTier.Fast,
    installed: Boolean = true,
): SpeechModel = SpeechModel(
    id = ModelId(id),
    engine = engine,
    languages = setOf(TestData.ENGLISH),
    quality = quality,
    sizeBytes = 1_000,
    sha256 = "0".repeat(64),
    url = "https://example.invalid/$id.bin",
    minRamMb = 0,
    installed = installed,
)
