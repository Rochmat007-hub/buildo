package com.example.data

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class Converters {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val typeSegmentList = Types.newParameterizedType(List::class.java, SubtitleSegment::class.java)
    private val adapterSegmentList = moshi.adapter<List<SubtitleSegment>>(typeSegmentList)

    @TypeConverter
    fun fromSubtitleSegmentList(value: List<SubtitleSegment>?): String? {
        return value?.let { adapterSegmentList.toJson(it) }
    }

    @TypeConverter
    fun toSubtitleSegmentList(value: String?): List<SubtitleSegment>? {
        return value?.let { adapterSegmentList.fromJson(it) }
    }
}
