package com.menews.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.loadWithOverviewMode = true
        web.settings.useWideViewPort = true
        web.addJavascriptInterface(CloudBridge(this), "MENC")
        web.webViewClient = WebViewClient()
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
        scheduleBackgroundCheck()
        requestNotificationPermission()
        checkFirstLaunch()
    }

    private fun checkFirstLaunch() {
        if (!PreferencesHelper.isSetupCompleted(this)) {
            PreferencesSetupDialog().show(supportFragmentManager, "prefs_setup")
        }
    }

    override fun onComplete(categories: Set<String>, sources: Set<String>) {
        PreferencesHelper.setSelectedCategories(this, categories)
        PreferencesHelper.setSelectedSources(this, sources)
        PreferencesHelper.setSetupCompleted(this, true)
        // Reschedule work with new preferences
        scheduleBackgroundCheck()
    }

    private fun scheduleBackgroundCheck() {
        val work = PeriodicWorkRequestBuilder<NewsCheckWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "news_check", ExistingPeriodicWorkPolicy.REPLACE, work
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

    internal fun openPreferencesDialog() {
        val dialog = PreferencesSetupDialog()
        dialog.show(supportFragmentManager, "prefs_edit")
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
            ""
        }
    }

    @JavascriptInterface
    fun getPreferences(): String {
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
    }

    @JavascriptInterface
    fun savePreferences(categoriesJson: String, sourcesJson: String): Boolean {
        return try {
            val type = com.google.gson.reflect.TypeToken.getParameterized(Set::class.java, String::class.java)
            val cats = Gson().fromJson(categoriesJson, type)
            val srcs = Gson().fromJson(sourcesJson, type)
            PreferencesHelper.setSelectedCategories(activity, cats)
            PreferencesHelper.setSelectedSources(activity, srcs)
            // Reschedule background check with new preferences
            val work = PeriodicWorkRequestBuilder<NewsCheckWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(activity).enqueueUniquePeriodicWork(
                "news_check", ExistingPeriodicWorkPolicy.REPLACE, work
            )
            true
        } catch (e: Exception) {
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