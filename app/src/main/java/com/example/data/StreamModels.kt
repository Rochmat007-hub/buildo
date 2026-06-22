package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LiveStreamEvent(
    val status: String,
    val message: String? = null,
    val stage_label: String? = null,
    val percent: Double? = null,
    val subtitles: List<SubtitleSegment>? = null,
    val start_index: Int? = null,
    val translations: List<String>? = null
)
