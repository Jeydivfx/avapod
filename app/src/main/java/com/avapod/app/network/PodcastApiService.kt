package com.avapod.app.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface PodcastApiService {
    @GET
    suspend fun getRssFeed(@Url url: String): Response<ResponseBody>
}