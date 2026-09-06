package com.menews.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.ActivityCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.loadWithOverviewMode = true
        web.settings.useWideViewPort = true
        web.addJavascriptInterface(CloudBridge(), "MENC")
        web.webViewClient = WebViewClient()
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
        scheduleBackgroundCheck()
        requestNotificationPermission()
    }

    private fun scheduleBackgroundCheck() {
        val work = PeriodicWorkRequestBuilder<NewsCheckWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "news_check", ExistingPeriodicWorkPolicy.KEEP, work
        )
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 2001
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finish()
    }
}

class CloudBridge {

    @JavascriptInterface
    fun cloud(): String {
        return try {
            val url = URL(BASE + "/rest/v1/news?select=guid,title,summary,link,image,category,source_id,source_name,source_cat,published,detail&order=published.desc&limit=300")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 12000
            conn.readTimeout = 15000
            conn.setRequestProperty("apikey", KEY)
            conn.setRequestProperty("Authorization", "Bearer $KEY")
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private companion object {
        const val BASE = "https://spemfwzbgdqpuctjgkkv.supabase.co"
        const val KEY = "sb_publishable_apj7AFUlW-ZCY9_hhQQPuw_CvyTbOgN"
    }
}