package com.example.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import java.io.File

class VibeRepository(
    private val transcriptDao: TranscriptDao,
    val apiServiceManager: ApiServiceManager
) {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // Database Actions
    val allLocalTranscripts: Flow<List<SavedTranscript>> = transcriptDao.getAllTranscripts()

    suspend fun getTranscriptById(id: String): SavedTranscript? {
        return transcriptDao.getTranscriptById(id)
    }

    suspend fun saveLocalTranscript(transcript: SavedTranscript) {
        transcriptDao.insertTranscript(transcript)
    }

    suspend fun deleteTranscriptLocallyAndRemotely(id: String) {
        // Delete locally first
        transcriptDao.deleteTranscriptById(id)
        // Attempt network delete
        try {
            apiServiceManager.getService().deleteTranscript(id)
        } catch (e: Exception) {
            // Safe ignore if offline or delete deleted
        }
    }

    // Network Actions
    suspend fun getServerSettings(): ServerSettings {
        return apiServiceManager.getService().getSettings()
    }

    suspend fun saveServerSettings(provider: String, model: String, apiKey: String?): ServerSettings {
        return apiServiceManager.getService().saveSettings(
            UpdateSettingsRequest(provider, model, apiKey)
        )
    }

    suspend fun explainSentence(text: String): ExplainResponse {
        return apiServiceManager.getService().explainSentence(ExplainRequest(text))
    }

    suspend fun getSavedTranscriptsFromServer(): List<ServerTranscriptMeta> {
        return apiServiceManager.getService().getSavedTranscripts()
    }

    suspend fun getTranscriptDetailFromServer(id: String): ServerTranscriptResponse {
        return apiServiceManager.getService().getTranscriptDetail(id)
    }

    suspend fun uploadVideo(fileBytes: ByteArray, fileName: String): UploadResponse {
        val mediaType = "video/*".toMediaTypeOrNull()
        val requestBody = fileBytes.toRequestBody(mediaType)
        val part = MultipartBody.Part.createFormData("file", fileName, requestBody)
        return apiServiceManager.getService().uploadVideo(part)
    }

    // Live EventSource (SSE) Parser
    fun transcribeLive(
        url: String?,
        fileId: String?,
        lang: String,
        model: String,
        start: Double?,
        end: Double?,
        saveTitle: String?
    ): Flow<LiveStreamEvent> = flow {
        val baseUrl = apiServiceManager.getSavedServerUrl()
        val cleanBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        
        val urlBuilder = "${cleanBase}api/transcribe/live".toHttpUrlOrNull()?.newBuilder()
        if (urlBuilder == null) {
            emit(LiveStreamEvent("error", message = "Format URL Server salah: $baseUrl"))
            return@flow
        }

        if (url != null) urlBuilder.addQueryParameter("url", url)
        if (fileId != null) urlBuilder.addQueryParameter("file_id", fileId)
        urlBuilder.addQueryParameter("lang", lang)
        urlBuilder.addQueryParameter("model", model)
        if (start != null) urlBuilder.addQueryParameter("start", start.toString())
        if (end != null) urlBuilder.addQueryParameter("end", end.toString())
        if (saveTitle != null) urlBuilder.addQueryParameter("save_title", saveTitle)

        val requestUrl = urlBuilder.build()
        val request = Request.Builder()
            .url(requestUrl)
            .header("Accept", "text/event-stream")
            .build()

        val client = apiServiceManager.getOkHttpClient()
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                emit(LiveStreamEvent("error", message = "Gagal menghubungi server live. Code: ${response.code}"))
                return@flow
            }

            val reader = response.body?.charStream()?.buffered()
            if (reader == null) {
                emit(LiveStreamEvent("error", message = "Konten kosong dari server."))
                return@flow
            }

            reader.use { bufReader ->
                var line: String? = bufReader.readLine()
                val eventAdapter = moshi.adapter(LiveStreamEvent::class.java)
                while (line != null) {
                    if (line.startsWith("data:")) {
                        val jsonData = line.substring(5).trim()
                        if (jsonData.isNotEmpty()) {
                            try {
                                val sseEvent = eventAdapter.fromJson(jsonData)
                                if (sseEvent != null) {
                                    emit(sseEvent)
                                    if (sseEvent.status == "translation_done" || sseEvent.status == "error") {
                                        break
                                    }
                                }
                            } catch (parseEx: Exception) {
                                // Keep reading
                            }
                        }
                    }
                    line = bufReader.readLine()
                }
            }
        } catch (e: Exception) {
            emit(LiveStreamEvent("error", message = "Koneksi terputus: ${e.localizedMessage}"))
        }
    }.flowOn(Dispatchers.IO)
}
