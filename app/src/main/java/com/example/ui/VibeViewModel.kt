package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID

class VibeViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val apiManager = ApiServiceManager(application)
    private val repository = VibeRepository(database.transcriptDao(), apiManager)

    // General preferences
    private val sharedPrefs = application.getSharedPreferences("vibewhisper_prefs", Context.MODE_PRIVATE)

    // UI inputs
    val urlInput = MutableStateFlow("")
    val selectedSource = MutableStateFlow("youtube") // "youtube" or "local"
    val localFileUri = MutableStateFlow<Uri?>(null)
    val localFileName = MutableStateFlow<String?>(null)
    val selectedLang = MutableStateFlow("ja") // Default "ja" since original was Japanese
    val selectedModel = MutableStateFlow("small")
    val saveToServer = MutableStateFlow(true)

    // Trim selections
    val trimMode = MutableStateFlow("full") // "full" or "partial"
    val trimStart = MutableStateFlow(0.0)
    val trimEnd = MutableStateFlow(60.0)
    val videoDuration = MutableStateFlow(0.0)

    // Server State
    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _providerSettings = MutableStateFlow<ServerSettings?>(null)
    val providerSettings: StateFlow<ServerSettings?> = _providerSettings.asStateFlow()

    private val _serverTranscripts = MutableStateFlow<List<ServerTranscriptMeta>>(emptyList())
    val serverTranscripts: StateFlow<List<ServerTranscriptMeta>> = _serverTranscripts.asStateFlow()

    // Database state/cache
    val localSavedList = repository.allLocalTranscripts

    // Active playing tracks & timing state
    private val _currentTranscript = MutableStateFlow<SavedTranscript?>(null)
    val currentTranscript: StateFlow<SavedTranscript?> = _currentTranscript.asStateFlow()

    val currentPlaybackTime = MutableStateFlow(0.0)
    val isPlaying = MutableStateFlow(false)
    val playbackSpeed = MutableStateFlow(1.0f)
    val repeatSegmentActive = MutableStateFlow(false)

    // Explain states
    private val _explainResponse = MutableStateFlow<ExplainResponse?>(null)
    val explainResponse: StateFlow<ExplainResponse?> = _explainResponse.asStateFlow()

    private val _explainLoading = MutableStateFlow(false)
    val explainLoading: StateFlow<Boolean> = _explainLoading.asStateFlow()

    private val _explainError = MutableStateFlow<String?>(null)
    val explainError: StateFlow<String?> = _explainError.asStateFlow()

    // Transcription states
    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()

    private val _transcriptionLogs = MutableStateFlow<List<String>>(emptyList())
    val transcriptionLogs: StateFlow<List<String>> = _transcriptionLogs.asStateFlow()

    private val _progressStage = MutableStateFlow<String?>("")
    val progressStage: StateFlow<String?> = _progressStage.asStateFlow()

    private val _progressPercent = MutableStateFlow<Double?>(null)
    val progressPercent: StateFlow<Double?> = _progressPercent.asStateFlow()

    private val _llmProviderInput = MutableStateFlow("gemini")
    val llmProviderInput = _llmProviderInput

    private val _llmModelInput = MutableStateFlow("")
    val llmModelInput = _llmModelInput

    private val _llmApiKeyInput = MutableStateFlow("")
    val llmApiKeyInput = _llmApiKeyInput

    private val _settingsMessage = MutableStateFlow<String?>(null)
    val settingsMessage: StateFlow<String?> = _settingsMessage.asStateFlow()

    init {
        _serverUrl.value = apiManager.getSavedServerUrl()
        loadLocalSettings()
        refreshServerData()
    }

    private fun loadLocalSettings() {
        val prov = sharedPrefs.getString("llm_provider", "gemini") ?: "gemini"
        val model = sharedPrefs.getString("llm_model", "gemini-2.5-flash") ?: "gemini-2.5-flash"
        _llmProviderInput.value = prov
        _llmModelInput.value = model
    }

    fun updateServerUrl(url: String) {
        apiManager.saveServerUrl(url)
        _serverUrl.value = apiManager.getSavedServerUrl()
        refreshServerData()
    }

    fun refreshServerData() {
        viewModelScope.launch {
            try {
                _providerSettings.value = repository.getServerSettings()
                _providerSettings.value?.let {
                    _llmProviderInput.value = it.provider ?: "gemini"
                    _llmModelInput.value = it.model ?: ""
                }
            } catch (e: Exception) {
                _providerSettings.value = null
            }

            try {
                _serverTranscripts.value = repository.getSavedTranscriptsFromServer()
            } catch (e: Exception) {
                _serverTranscripts.value = emptyList()
            }
        }
    }

    // Save server LLM configuration
    fun saveServerSettings() {
        viewModelScope.launch {
            _settingsMessage.value = "Menyimpan setelan..."
            try {
                val apiKey = _llmApiKeyInput.value.trim().ifEmpty { null }
                val updated = repository.saveServerSettings(
                    _llmProviderInput.value,
                    _llmModelInput.value.trim(),
                    apiKey
                )
                _providerSettings.value = updated
                _settingsMessage.value = "✅ Tersimpan."
                _llmApiKeyInput.value = ""
            } catch (e: Exception) {
                _settingsMessage.value = "❌ Gagal menyimpan: ${e.localizedMessage}"
            }
        }
    }

    // Delete a transcript
    fun deleteTranscript(id: String) {
        viewModelScope.launch {
            repository.deleteTranscriptLocallyAndRemotely(id)
            if (_currentTranscript.value?.id == id) {
                _currentTranscript.value = null
            }
            refreshServerData()
        }
    }

    // Load active transcript details
    fun selectTranscript(id: String) {
        viewModelScope.launch {
            // First check local Room Database
            val local = repository.getTranscriptById(id)
            if (local != null) {
                _currentTranscript.value = local
                urlInput.value = local.sourceValue ?: ""
                selectedSource.value = local.sourceType
                return@launch
            }

            // Fallback to server if online
            try {
                val remote = repository.getTranscriptDetailFromServer(id)
                val mapped = SavedTranscript(
                    id = remote.id,
                    title = remote.title,
                    sourceType = remote.source_type,
                    sourceValue = remote.source_value,
                    subtitles = remote.subtitles,
                    createdAt = remote.created_at
                )
                repository.saveLocalTranscript(mapped)
                _currentTranscript.value = mapped
                urlInput.value = mapped.sourceValue ?: ""
                selectedSource.value = mapped.sourceType
            } catch (e: Exception) {
                _settingsMessage.value = "Gagal memuat transkrip: ${e.localizedMessage}"
            }
        }
    }

    // Manual load of a Custom pasted JSON string
    fun loadRawSubtitleJson(json: String, title: String) {
        viewModelScope.launch {
            try {
                val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, SubtitleSegment::class.java)
                val adapter = moshi.adapter<List<SubtitleSegment>>(listType)
                val segments = adapter.fromJson(json)
                if (!segments.isNullOrEmpty()) {
                    val transcript = SavedTranscript(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        sourceType = "local",
                        sourceValue = "",
                        subtitles = segments,
                        createdAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                    )
                    repository.saveLocalTranscript(transcript)
                    _currentTranscript.value = transcript
                    _settingsMessage.value = "Berhasil memuat JSON secara lokal!"
                } else {
                    _settingsMessage.value = "JSON kosong atau tidak valid."
                }
            } catch (e: Exception) {
                _settingsMessage.value = "Gagal parsing JSON: ${e.localizedMessage}"
            }
        }
    }

    // Sentence Explain API
    fun explainSentence(text: String) {
        viewModelScope.launch {
            _explainLoading.value = true
            _explainResponse.value = null
            _explainError.value = null
            try {
                val res = repository.explainSentence(text)
                _explainResponse.value = res
            } catch (e: Exception) {
                _explainError.value = "Gagal memuat penjelasan kata: ${e.localizedMessage}"
            } finally {
                _explainLoading.value = false
            }
        }
    }

    private fun readUriBytes(uri: Uri, context: Context): ByteArray? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val byteBuffer = ByteArrayOutputStream()
            val bufferSize = 4096
            val buffer = ByteArray(bufferSize)
            var len: Int
            if (inputStream != null) {
                while (inputStream.read(buffer).also { len = it } != -1) {
                    byteBuffer.write(buffer, 0, len)
                }
            }
            byteBuffer.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    // Main Gas Transcribe action (Youtube or Local file)
    fun startTranscription(context: Context) {
        viewModelScope.launch {
            val src = selectedSource.value
            val url = urlInput.value.trim()
            val fileUri = localFileUri.value

            if (src == "youtube" && url.isEmpty()) {
                _settingsMessage.value = "Masukkan Tautan Video YouTube terlebih dahulu!"
                return@launch
            }
            if (src == "local" && fileUri == null) {
                _settingsMessage.value = "Pilih video lokal dari HP terlebih dahulu!"
                return@launch
            }

            _isTranscribing.value = true
            _transcriptionLogs.value = emptyList()
            _progressStage.value = "Menghubungkan..."
            _progressPercent.value = 0.0

            addLog("Mulai proses transkripsi...", "info")

            var fileId: String? = null

            // If local video upload first
            if (src == "local" && fileUri != null) {
                addLog("Mengunggah video lokal ke server...", "info")
                _progressStage.value = "Mengunggah file..."
                _progressPercent.value = 25.0

                val bytes = withContext(Dispatchers.IO) { readUriBytes(fileUri, context) }
                if (bytes == null) {
                    errorTranscription("Gagal membaca file dari penyimpanan.")
                    return@launch
                }

                val originalName = localFileName.value ?: "imported_video.mp4"
                try {
                    val uploadRes = repository.uploadVideo(bytes, originalName)
                    fileId = uploadRes.file_id
                    addLog("Berhasil mengunggah video. ID File: $fileId", "success")
                } catch (e: Exception) {
                    errorTranscription("Gagal mengunggah file ke server: ${e.localizedMessage}")
                    return@launch
                }
            }

            val curTitle = if (src == "youtube") url else (localFileName.value ?: "Impor Video")
            val trimIsEnabled = trimMode.value == "partial"
            val start = if (trimIsEnabled) trimStart.value else null
            val end = if (trimIsEnabled) trimEnd.value else null

            // Trigger live transcription stream Flow
            val triggerUrl = if (src == "youtube") url else null

            repository.transcribeLive(
                url = triggerUrl,
                fileId = fileId,
                lang = selectedLang.value,
                model = selectedModel.value,
                start = start,
                end = end,
                saveTitle = if (saveToServer.value) curTitle else null
            ).collect { event ->
                when (event.status) {
                    "log" -> addLog(event.message ?: "")
                    "info" -> addLog(event.message ?: "", "info")
                    "progress" -> {
                        _progressStage.value = event.stage_label
                        _progressPercent.value = event.percent
                        if (event.message != null) {
                            addLog(event.message, "info")
                        }
                    }
                    "success" -> {
                        addLog("Selesai! Menyimpan transkrip baru.", "success")
                        _progressStage.value = "Selesai"
                        _progressPercent.value = 100.0

                        event.subtitles?.let { list ->
                            val saved = SavedTranscript(
                                id = UUID.randomUUID().toString(),
                                title = curTitle,
                                sourceType = src,
                                sourceValue = if (src == "youtube") url else fileUri.toString(),
                                subtitles = list,
                                createdAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                            )
                            repository.saveLocalTranscript(saved)
                            _currentTranscript.value = saved
                        }
                        _isTranscribing.value = false
                        refreshServerData()
                    }
                    "translation_chunk" -> {
                        // Dynamically attach translation segments to current active transcript list if applicable
                        val cur = _currentTranscript.value
                        val startIndex = event.start_index
                        val transList = event.translations
                        if (cur != null && startIndex != null && transList != null) {
                            val updatedSegments = cur.subtitles.toMutableList()
                            for (i in transList.indices) {
                                val targetIdx = startIndex + i
                                if (targetIdx < updatedSegments.size) {
                                    updatedSegments[targetIdx] = updatedSegments[targetIdx].copy(
                                        translation = transList[i]
                                    )
                                }
                            }
                            val updatedTranscript = cur.copy(subtitles = updatedSegments)
                            _currentTranscript.value = updatedTranscript
                            repository.saveLocalTranscript(updatedTranscript)
                        }
                    }
                    "translation_done" -> {
                        addLog("Seluruh proses terjemahan selesai!", "success")
                        _isTranscribing.value = false
                        refreshServerData()
                    }
                    "error" -> {
                        errorTranscription(event.message ?: "Terjadi kesalahan tidak dikenal.")
                    }
                }
            }
        }
    }

    private fun addLog(text: String, type: String = "log") {
        val prefix = when (type) {
            "success" -> "✅ "
            "error" -> "❌ "
            "info" -> "🔌 "
            else -> "• "
        }
        val line = "$prefix$text"
        val currList = _transcriptionLogs.value.toMutableList()
        currList.add(line)
        _transcriptionLogs.value = currList
    }

    private fun errorTranscription(msg: String) {
        addLog(msg, "error")
        _progressStage.value = "Gagal"
        _isTranscribing.value = false
    }

    fun cleanSettingsMessage() {
        _settingsMessage.value = null
    }

    fun clearActiveExplanation() {
        _explainResponse.value = null
        _explainError.value = null
        _explainLoading.value = false
    }
}
