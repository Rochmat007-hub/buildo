package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WordChip(
    val text: String,
    val reading: String? = null,
    val romaji: String? = null,
    val start: Double? = null,
    val end: Double? = null
)

@JsonClass(generateAdapter = true)
data class SubtitleSegment(
    val start: Double,
    val end: Double,
    val text: String,
    var translation: String? = null,
    val chips: List<WordChip>? = null
)

@Entity(tableName = "saved_transcripts")
@JsonClass(generateAdapter = true)
data class SavedTranscript(
    @PrimaryKey val id: String,
    val title: String,
    val sourceType: String, // "youtube", "local"
    val sourceValue: String?,
    val subtitles: List<SubtitleSegment>,
    val createdAt: String,
    val isLocalOnly: Boolean = false
)

@JsonClass(generateAdapter = true)
data class ServerTranscriptMeta(
    val id: String,
    val title: String,
    val source_type: String,
    val line_count: Int,
    val created_at: String
)

@JsonClass(generateAdapter = true)
data class ServerTranscriptResponse(
    val id: String,
    val title: String,
    val source_type: String,
    val source_value: String?,
    val subtitles: List<SubtitleSegment>,
    val created_at: String
)

@JsonClass(generateAdapter = true)
data class ExplainRequest(
    val text: String
)

@JsonClass(generateAdapter = true)
data class ExplainResponse(
    val translation: String? = null,
    val vocabulary: List<VocabItem>? = null,
    val grammar: List<GrammarItem>? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class VocabItem(
    val word: String? = null,
    val reading: String? = null,
    val meaning: String? = null
)

@JsonClass(generateAdapter = true)
data class GrammarItem(
    val pattern: String? = null,
    val explanation: String? = null
)

@JsonClass(generateAdapter = true)
data class ServerSettings(
    val provider: String? = null,
    val model: String? = null,
    val has_key: Boolean = false,
    val api_key_masked: String? = null,
    val provider_defaults: Map<String, String>? = null
)

@JsonClass(generateAdapter = true)
data class UpdateSettingsRequest(
    val provider: String?,
    val model: String?,
    val api_key: String?
)

@JsonClass(generateAdapter = true)
data class UploadResponse(
    val file_id: String,
    val status: String
)
