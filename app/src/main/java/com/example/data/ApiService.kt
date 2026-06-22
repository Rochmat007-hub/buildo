package com.example.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface ApiService {
    @GET("api/settings")
    suspend fun getSettings(): ServerSettings

    @POST("api/settings")
    suspend fun saveSettings(@Body body: UpdateSettingsRequest): ServerSettings

    @POST("api/explain")
    suspend fun explainSentence(@Body body: ExplainRequest): ExplainResponse

    @GET("api/saved")
    suspend fun getSavedTranscripts(): List<ServerTranscriptMeta>

    @GET("api/saved/{id}")
    suspend fun getTranscriptDetail(@Path("id") id: String): ServerTranscriptResponse

    @DELETE("api/saved/{id}")
    suspend fun deleteTranscript(@Path("id") id: String): ResponseBody

    @Multipart
    @POST("api/upload")
    suspend fun uploadVideo(
        @Part file: MultipartBody.Part
    ): UploadResponse
}

class ApiServiceManager(private val context: Context) {
    private val sharedPrefs = context.getSharedPreferences("vibewhisper_prefs", Context.MODE_PRIVATE)

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private var currentUrl: String = getSavedServerUrl()
    private var cachedService: ApiService? = null

    fun getSavedServerUrl(): String {
        return sharedPrefs.getString("server_url", "http://10.0.2.2:5000") ?: "http://10.0.2.2:5000"
    }

    fun saveServerUrl(url: String) {
        val sanitized = if (url.endsWith("/")) url else "$url/"
        sharedPrefs.edit().putString("server_url", sanitized).apply()
        currentUrl = sanitized
        cachedService = null // Invalidate cache so it rebuilds Retrofit with the new URL
    }

    @Synchronized
    fun getService(): ApiService {
        val activeUrl = getSavedServerUrl()
        if (cachedService != null && activeUrl == currentUrl) {
            return cachedService!!
        }

        currentUrl = activeUrl
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(currentUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        val service = retrofit.create(ApiService::class.java)
        cachedService = service
        return service
    }

    fun getOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .build()
    }
}
