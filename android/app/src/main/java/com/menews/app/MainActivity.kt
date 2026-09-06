package com.menews.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerNews: RecyclerView
    private lateinit var loadingView: LinearLayout
    private lateinit var errorView: LinearLayout
    private lateinit var errorText: TextView
    private lateinit var btnRetry: Button
    private lateinit var spinnerCategory: Spinner
    private lateinit var spinnerSource: Spinner
    private lateinit var adapter: NewsAdapter

    private var allNews: List<NewsItem> = emptyList()
    private var categories: List<String> = emptyList()
    private var sources: List<String> = emptyList()
    private var selectedCategory: String = "الكل"
    private var selectedSource: String = "الكل"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        recyclerNews = findViewById(R.id.recyclerNews)
        loadingView = findViewById(R.id.loadingView)
        errorView = findViewById(R.id.errorView)
        errorText = findViewById(R.id.errorText)
        btnRetry = findViewById(R.id.btnRetry)
        spinnerCategory = findViewById(R.id.spinnerCategory)
        spinnerSource = findViewById(R.id.spinnerSource)

        recyclerNews.layoutManager = LinearLayoutManager(this)
        adapter = NewsAdapter(emptyList()) { }
        recyclerNews.adapter = adapter

        swipeRefresh.setColorSchemeColors(0xFFF59E0B.toInt())
        swipeRefresh.setProgressBackgroundColorSchemeColor(0xFF1E293B.toInt())
        swipeRefresh.setOnRefreshListener { loadNews() }

        btnRetry.setOnClickListener { loadNews() }

        spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedCategory = categories.getOrNull(position) ?: "الكل"
                filterAndDisplay()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerSource.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedSource = sources.getOrNull(position) ?: "الكل"
                filterAndDisplay()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        requestNotificationPermission()
        scheduleNewsWorker()
        loadNews()
    }

    private fun loadNews() {
        showLoading(true)
        Thread {
            try {
                val news = SupabaseService.fetchNews(80)
                runOnUiThread {
                    allNews = news
                    buildFilters()
                    filterAndDisplay()
                    showLoading(false)
                    swipeRefresh.isRefreshing = false
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showError("تعذر الاتصال بالخادم.\n${e.message}")
                    showLoading(false)
                    swipeRefresh.isRefreshing = false
                }
            }
        }.start()
    }

    private fun buildFilters() {
        val cats = linkedSetOf<String>()
        val srcs = linkedSetOf<String>()
        for (item in allNews) {
            item.category?.let { if (it.isNotBlank()) cats.add(it) }
            item.source_name?.let { if (it.isNotBlank()) srcs.add(it) }
        }

        categories = listOf("الكل") + cats.toList()
        sources = listOf("الكل") + srcs.toList()

        spinnerCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        spinnerSource.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sources)
    }

    private fun filterAndDisplay() {
        val filtered = allNews.filter { item ->
            val catOk = selectedCategory == "الكل" || item.category == selectedCategory
            val srcOk = selectedSource == "الكل" || item.source_name == selectedSource
            catOk && srcOk
        }
        adapter.updateData(filtered)
    }

    private fun showLoading(loading: Boolean) {
        loadingView.visibility = if (loading && allNews.isEmpty()) View.VISIBLE else View.GONE
        swipeRefresh.visibility = if (!loading || allNews.isNotEmpty()) View.VISIBLE else View.GONE
        errorView.visibility = View.GONE
    }

    private fun showError(msg: String) {
        errorText.text = msg
        errorView.visibility = View.VISIBLE
        swipeRefresh.visibility = View.GONE
        loadingView.visibility = View.GONE
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }

    private fun scheduleNewsWorker() {
        val workRequest = PeriodicWorkRequestBuilder<NewsCheckWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "news_check",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
