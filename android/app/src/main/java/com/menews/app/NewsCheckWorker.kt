package com.menews.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URL

class NewsCheckWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        return try {
            val url = URL(
                BASE + "/rest/v1/news" +
                    "?select=guid&order=published.desc&limit=1"
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

            val latestGuid = Regex("\"guid\":\"(.*?)\"").find(body)?.groupValues?.get(1)
            val prefs = applicationContext.getSharedPreferences("news_check", Context.MODE_PRIVATE)
            val lastSeen = prefs.getString("last_guid", null)

            if (latestGuid != null && latestGuid != lastSeen) {
                prefs.edit().putString("last_guid", latestGuid).apply()
                if (lastSeen != null) notifyNewNews()
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun notifyNewNews() {
        val channelId = "news_updates"
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "أخبار جديدة", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("الشرق الأوسط الآن")
            .setContentText("في أخبار جديدة — افتح التطبيق للاطلاع")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        nm.notify(1001, notification)
    }

    private companion object {
        const val BASE = "https://spemfwzbgdqpuctjgkkv.supabase.co"
        const val KEY = "sb_publishable_apj7AFUlW-ZCY9_hhQQPuw_CvyTbOgN"
    }
}