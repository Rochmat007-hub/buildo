package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptDao {
    @Query("SELECT * FROM saved_transcripts ORDER BY createdAt DESC")
    fun getAllTranscripts(): Flow<List<SavedTranscript>>

    @Query("SELECT * FROM saved_transcripts WHERE id = :id")
    suspend fun getTranscriptById(id: String): SavedTranscript?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscript(transcript: SavedTranscript)

    @Query("DELETE FROM saved_transcripts WHERE id = :id")
    suspend fun deleteTranscriptById(id: String)
}

@Database(entities = [SavedTranscript::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transcriptDao(): TranscriptDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vibewhisper_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
