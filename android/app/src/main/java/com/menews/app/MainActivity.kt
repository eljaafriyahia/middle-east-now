package com.menews.app

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), PreferencesSetupDialog.OnSetupComplete {

    private val TAG = "MainActivity"
    private var web: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val webView = WebView(this)
        web = webView
        
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        
        webView.addJavascriptInterface(CloudBridge(this), "MENC")
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError) {
                Log.e(TAG, "WebView error: ${error.description}")
            }
        }
        
        setContentView(webView)
        
        // Load asset with error handling
        try {
            webView.loadUrl("file:///android_asset/index.html")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load asset", e)
        }
        
        // Delay heavy operations to avoid crash on startup
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                scheduleBackgroundCheck()
            } catch (e: Exception) {
                Log.e(TAG, "WorkManager error", e)
            }
            requestNotificationPermission()
            
            // Delay dialog to ensure activity is ready
            Handler(Looper.getMainLooper()).postDelayed({
                checkFirstLaunch()
            }, 500)
        }, 100)
    }

    private fun checkFirstLaunch() {
        try {
            if (!PreferencesHelper.isSetupCompleted(this)) {
                PreferencesSetupDialog().show(supportFragmentManager, "prefs_setup")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Dialog error", e)
        }
    }

    override fun onComplete(categories: Set<String>, sources: Set<String>) {
        try {
            PreferencesHelper.setSelectedCategories(this, categories)
            PreferencesHelper.setSelectedSources(this, sources)
            PreferencesHelper.setSetupCompleted(this, true)
            scheduleBackgroundCheck()
        } catch (e: Exception) {
            Log.e(TAG, "onComplete error", e)
        }
    }

    private fun scheduleBackgroundCheck() {
        try {
            val work = PeriodicWorkRequestBuilder<NewsCheckWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "news_check", ExistingPeriodicWorkPolicy.REPLACE, work
            )
        } catch (e: Exception) {
            Log.e(TAG, "scheduleBackgroundCheck error", e)
        }
    }

    private fun requestNotificationPermission() {
        try {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 2001
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Permission error", e)
        }
    }

    internal fun openPreferencesDialog() {
        try {
            val dialog = PreferencesSetupDialog()
            dialog.show(supportFragmentManager, "prefs_edit")
        } catch (e: Exception) {
            Log.e(TAG, "openPreferencesDialog error", e)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finish()
    }
}

class CloudBridge(private val activity: MainActivity) {

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
            Log.e("CloudBridge", "cloud error", e)
            ""
        }
    }

    @JavascriptInterface
    fun getPreferences(): String {
        try {
            val cats = PreferencesHelper.getSelectedCategories(activity)
            val srcs = PreferencesHelper.getSelectedSources(activity)
            val allCats = getAvailableCategories()
            val allSrcs = getAvailableSources()
            return Gson().toJson(mapOf(
                "selectedCategories" to cats,
                "selectedSources" to srcs,
                "allCategories" to allCats,
                "allSources" to allSrcs
            ))
        } catch (e: Exception) {
            Log.e("CloudBridge", "getPreferences error", e)
            return "{}"
        }
    }

    @JavascriptInterface
    fun savePreferences(categoriesJson: String, sourcesJson: String): Boolean {
        return try {
            val type = com.google.gson.reflect.TypeToken.getParameterized(Set::class.java, String::class.java).type
            val cats = Gson().fromJson(categoriesJson, type) as Set<String>
            val srcs = Gson().fromJson(sourcesJson, type) as Set<String>
            PreferencesHelper.setSelectedCategories(activity, cats)
            PreferencesHelper.setSelectedSources(activity, srcs)
            val work = PeriodicWorkRequestBuilder<NewsCheckWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(activity).enqueueUniquePeriodicWork(
                "news_check", ExistingPeriodicWorkPolicy.REPLACE, work
            )
            true
        } catch (e: Exception) {
            Log.e("CloudBridge", "savePreferences error", e)
            false
        }
    }

    @JavascriptInterface
    fun openPreferences() {
        activity.runOnUiThread { activity.openPreferencesDialog() }
    }

    private fun getAvailableCategories(): List<String> {
        return listOf(
            "سياسة", "اقتصاد", "رياضة", "تقنية", "صحة", "علوم",
            "ثقافة", "مجتمع", "عالم", "خليج", "شمال أفريقيا", "العراق", "سوريا", "اليمن", "لبنان", "فلسطين"
        )
    }

    private fun getAvailableSources(): List<String> {
        return listOf(
            "BBC Arabic", "Al Jazeera", "Al Arabiya", "Sky News Arabia",
            "RT Arabic", "CNN Arabic", "DW Arabic", "France 24 Arabic",
            "Anadolu Agency", "Middle East Monitor", "The New Arab",
            "Asharq Al-Awsat", "Al Quds Al Arabi", "Al Masry Al Youm",
            "Youm7", "Sada Elbalad", "El Watan News", "Masrawy"
        )
    }

    private companion object {
        const val BASE = "https://spemfwzbgdqpuctjgkkv.supabase.co"
        const val KEY = "sb_publishable_apj7AFUlW-ZCY9_hhQQPuw_CvyTbOgN"
    }
}