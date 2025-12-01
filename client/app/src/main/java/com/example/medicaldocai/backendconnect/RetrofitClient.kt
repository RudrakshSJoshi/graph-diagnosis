package com.example.medicaldocai.backendconnect

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.medicaldocai.BuildConfig // Import the generated config

object RetrofitClient {
    // Use the variable we defined in build.gradle
    private const val BASE_URL = BuildConfig.BASE_URL

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}