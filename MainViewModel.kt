package com.example.enhancer

import android.app.Application
import android.net.Uri
import android.os.StatFs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class JobUi(
    val id: UUID,
    val name: String,
    val kind: MediaKind,
    val state: WorkInfo.State,
    val progress: Int,
    val outUri: String?,
    val outBytes: Long,
    val error: String?,
    val ts: Long
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    var selected by mutableStateOf<MediaInfo?>(null)
        private set
    var settings by mutableStateOf(EnhanceSettings())
    var loading by mutableStateOf(false)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set

    private val wm = WorkManager.getInstance(app)

    val jobs: StateFlow<List<JobUi>> = wm.getWorkInfosByTagFlow(TAG)
        .map { list -> list.map(::toJob).sortedByDescending { it.ts } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun load(uri: Uri, kind: MediaKind) {
        viewModelScope.launch {
            loading = true
            loadError = null
            try {
                selected = withContext(Dispatchers.IO) { MediaLoader.load(getApplication(), uri, kind) }
            } catch (t: Throwable) {
                loadError = "تعذّر فتح الملف. جرّب ملفًا آخر."
            } finally {
                loading = false
            }
        }
    }

    fun clearSelection() { selected = null }

    fun freeBytes(): Long = StatFs(getApplication<Application>().cacheDir.path).availableBytes

    fun start() {
        val info = selected ?: return
        val data = settings.putInto(
            Data.Builder()
                .putString("uri", info.uri.toString())
                .putString("kind", info.kind.name)
                .putString("name", info.name)
                .putInt("w", info.width)
                .putInt("h", info.height)
                .putLong("dur", info.durationMs)
                .putFloat("fps", info.fps)
        ).build()

        val req = OneTimeWorkRequestBuilder<ExportWorker>()
            .setInputData(data)
            .addTag(TAG)
            .addTag("name:${info.name}")
            .addTag("kind:${info.kind.name}")
            .addTag("ts:${System.currentTimeMillis()}")
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        wm.enqueue(req)
        selected = null
    }

    fun cancel(id: UUID) { wm.cancelWorkById(id) }

    fun clearFinished() { wm.pruneWork() }

    private fun toJob(info: WorkInfo): JobUi {
        val tags = info.tags
        return JobUi(
            id = info.id,
            name = tags.firstOrNull { it.startsWith("name:") }?.removePrefix("name:") ?: "ملف",
            kind = if (tags.contains("kind:VIDEO")) MediaKind.VIDEO else MediaKind.IMAGE,
            state = info.state,
            progress = info.progress.getInt("p", 0),
            outUri = info.outputData.getString("outUri"),
            outBytes = info.outputData.getLong("outBytes", 0L),
            error = info.outputData.getString("error"),
            ts = tags.firstOrNull { it.startsWith("ts:") }?.removePrefix("ts:")?.toLongOrNull() ?: 0L
        )
    }

    companion object {
        const val TAG = "enhance"
    }
}
