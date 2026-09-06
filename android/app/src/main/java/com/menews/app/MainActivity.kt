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
    private var initializationDone = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize WebView as early as possible
        val webView = WebView(this)
        web = webView
        
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        
        webView.addJavascriptInterface(CloudBridge(this), "MENC")
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError) {
                Log.e(TAG, "WebView error: ${error.description}")
            }
            
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (!initializationDone) {
                    initializationDone = true
                    initializeBackgroundServices()
                }
            }
        }
        
        setContentView(webView)
        
        // Try to load asset with multiple fallbacks
        loadAssetWithFallback(webView)
    }

    private fun loadAssetWithFallback(webView: WebView) {
        try {
            webView.loadUrl("file:///android_asset/index.html")
        } catch (e: Exception) {
            Log.e(TAG, "Asset load failed, trying alternative", e)
            // Fallback: try with different scheme
            try {
                webView.loadUrl("file:///android_asset/index.html")
            } catch (e2: Exception) {
                Log.e(TAG, "All asset loads failed", e2)
                showErrorInWebView("تعذر تحميل التطبيق. تأكد من تثبيت APK بشكل صحيح.")
            }
        }
    }

    private fun showErrorInWebView(message: String) {
        runOnUiThread {
            web?.loadDataWithBaseURL(
                null,
                """
                <!DOCTYPE html>
                <html dir="rtl" lang="ar">
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <style>
                        body {font-family: sans-serif; padding: 20px; text-align: center; background: #0f172a; color: #e2e8f0;}
                        .error {background: #7f1d1d; border: 1px solid #ef4444; border-radius: 12px; padding: 20px; margin: 20px;}
                        button {background: #3b82f6; color: white; border: none; padding: 12px 24px; border-radius: 8px; font-size: 16px; margin-top: 16px;}
                    </style>
                </head>
                <body>
                    <div class="error">
                        <h2>⚠️ خطأ في التحميل</h2>
                        <p>$message</p>
                        <button onclick="location.reload()">إعادة المحاولة</button>
                    </div>
                </body>
                </html>
                """.trimIndent(),
                "text/html",
                "utf-8",
                null
            )
        }
    }

    private fun initializeBackgroundServices() {
        Handler(Looper.getMainLooper()).postDelayed({
            safeExecute("WorkManager") { scheduleBackgroundCheck() }
            safeExecute("NotificationPermission") { requestNotificationPermission() }
            // Delay dialog more to ensure UI is ready
            Handler(Looper.getMainLooper()).postDelayed({
                safeExecute("FirstLaunchDialog") { checkFirstLaunch() }
            }, 1000)
        }, 300)
    }

    private inline fun safeExecute(tag: String, crossinline action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            Log.e(TAG, "$tag error", e)
        }
    }

    private fun checkFirstLaunch() {
        safeExecute("checkFirstLaunch") {
            if (!PreferencesHelper.isSetupCompleted(this)) {
                // Extra safety: ensure fragment manager is ready
                if (!isFinishing && !isDestroyed) {
                    PreferencesSetupDialog().show(supportFragmentManager, "prefs_setup")
                }
            }
        }
    }

    override fun onComplete(categories: Set<String>, sources: Set<String>) {
        safeExecute("onComplete") {
            PreferencesHelper.setSelectedCategories(this, categories)
            PreferencesHelper.setSelectedSources(this, sources)
            PreferencesHelper.setSetupCompleted(this, true)
            scheduleBackgroundCheck()
        }
    }

    private fun scheduleBackgroundCheck() {
        safeExecute("scheduleBackgroundCheck") {
            val work = PeriodicWorkRequestBuilder<NewsCheckWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "news_check", ExistingPeriodicWorkPolicy.REPLACE, work
            )
        }
    }

    private fun requestNotificationPermission() {
        safeExecute("requestNotificationPermission") {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 2001
                )
            }
        }
    }

    internal fun openPreferencesDialog() {
        safeExecute("openPreferencesDialog") {
            if (!isFinishing && !isDestroyed) {
                val dialog = PreferencesSetupDialog()
                dialog.show(supportFragmentManager, "prefs_edit")
            }
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
        return try {
            val cats = PreferencesHelper.getSelectedCategories(activity)
            val srcs = PreferencesHelper.getSelectedSources(activity)
            val allCats = getAvailableCategories()
            val allSrcs = getAvailableSources()
            Gson().toJson(mapOf(
                "selectedCategories" to cats,
                "selectedSources" to srcs,
                "allCategories" to allCats,
                "allSources" to allSrcs
            ))
        } catch (e: Exception) {
            Log.e("CloudBridge", "getPreferences error", e)
            "{}"
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