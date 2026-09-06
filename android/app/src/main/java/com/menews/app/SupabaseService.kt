package com.menews.app

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object SupabaseService {

    private const val BASE = "https://spemfwzbgdqpuctjgkkv.supabase.co"
    private const val KEY = "sb_publishable_apj7AFUlW-ZCY9_hhQQPuw_CvyTbOgN"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun fetchNews(limit: Int = 50): List<NewsItem> {
        val url = "$BASE/rest/v1/news" +
            "?select=*&order=published.desc&limit=$limit"

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", KEY)
            .addHeader("Authorization", "Bearer $KEY")
            .addHeader("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}")
        }

        val body = response.body?.string() ?: throw Exception("Empty response")
        val type = TypeToken.getParameterized(List::class.java, NewsItem::class.java).type
        return Gson().fromJson(body, type)
    }

    fun fetchNewsForCategories(categories: Set<String>, sources: Set<String>, limit: Int = 50): List<NewsItem> {
        val allNews = fetchNews(limit)

        if (categories.isEmpty() && sources.isEmpty()) {
            return allNews
        }

        return allNews.filter { item ->
            val catMatch = categories.isEmpty() || (item.category != null && categories.contains(item.category))
            val srcMatch = sources.isEmpty() ||
                (item.source_id != null && sources.contains(item.source_id)) ||
                (item.source_name != null && sources.contains(item.source_name))
            catMatch || srcMatch
        }
    }
}
