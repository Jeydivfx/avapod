package com.avapod.app.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url
import retrofit2.http.Streaming

interface PodcastApiService {
    @GET
    @Streaming
    suspend fun getRssFeed(@Url url: String): Response<ResponseBody>
}
