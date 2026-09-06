package com.menews.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import java.net.HttpURLConnection
import java.net.URL

class NewsCheckWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        return try {
            val url = URL(
                BASE + "/rest/v1/news" +
                    "?select=guid,title,category,source_id,source_name&order=published.desc&limit=5"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("apikey", KEY)
            conn.setRequestProperty("Authorization", "Bearer $KEY")
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                return Result.retry()
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val type = com.google.gson.reflect.TypeToken.getParameterized(Array::class.java, Article::class.java).type
            val articles = Gson().fromJson<Array<Article>>(body, type)
            val prefs = applicationContext.getSharedPreferences("news_preferences", Context.MODE_PRIVATE)
            val lastSeen = prefs.getString("last_guid", null)

            for (article in articles) {
                if (article.guid != lastSeen && PreferencesHelper.shouldNotifyForArticle(
                        applicationContext,
                        article.category,
                        article.source_id,
                        article.source_name
                    )) {
                    prefs.edit().putString("last_guid", article.guid).apply()
                    if (lastSeen != null) notifyNewNews(article.title)
                    break
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun notifyNewNews(title: String) {
        val channelId = "news_updates"
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "أخبار جديدة", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("الشرق الأوسط الآن")
            .setContentText(title.takeIf { it.length <= 60 } ?: "${title.substring(0, 57)}...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        nm.notify(1001, notification)
    }

    private data class Article(
        val guid: String,
        val title: String,
        val category: String?,
        val source_id: String?,
        val source_name: String?
    )

    private companion object {
        const val BASE = "https://spemfwzbgdqpuctjgkkv.supabase.co"
        const val KEY = "sb_publishable_apj7AFUlW-ZCY9_hhQQPuw_CvyTbOgN"
    }
}
