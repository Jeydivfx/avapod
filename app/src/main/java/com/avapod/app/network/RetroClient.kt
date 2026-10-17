package com.avapod.app.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object RetrofitClient {
    val instance: PodcastApiService by lazy {
        Retrofit.Builder().baseUrl("https://example.com/")
            .client(OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build())
            .build().create(PodcastApiService::class.java)
    }
}
