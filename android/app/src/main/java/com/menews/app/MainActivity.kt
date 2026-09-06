package com.menews.app

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private var web: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")
        
        // CRITICAL FIX: Wrap ALL WebView initialization in try/catch
        // If Android System WebView is missing/disabled/old, it throws exception that crashes app
        try {
            val webView = WebView(this)
            web = webView
            
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.loadWithOverviewMode = true
            webView.settings.useWideViewPort = true
            webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            webView.settings.allowFileAccess = true
            webView.settings.allowContentAccess = true
            
            webView.webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError) {
                    Log.e(TAG, "WebView error: ${error.description}")
                }
                
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "Page finished: $url")
                }
            }
            
            setContentView(webView)
            Log.d(TAG, "WebView created and set as content view")
            
            loadAssetWithFallback(webView)
            
        } catch (e: Throwable) {
            // CRITICAL: If WebView fails (missing/disabled WebView package), show error UI instead of crashing
            Log.e(TAG, "WebView initialization FAILED - showing fallback UI", e)
            showWebViewUnavailableError()
        }
    }

    private fun loadAssetWithFallback(webView: WebView) {
        try {
            webView.loadUrl("file:///android_asset/index.html")
            Log.d(TAG, "loadUrl called successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Primary loadUrl failed, showing error", e)
            // REAL FALLBACK: Show error in WebView instead of retrying same URL
            showErrorInWebView("تعذر تحميل ملفات التطبيق. تأكد من تثبيت APK بشكل صحيح.")
        }
    }

    private fun showWebViewUnavailableError() {
        // Show a proper error UI instead of crashing
        val errorView = TextView(this).apply {
            text = "تعذر تشغيل عارض الأخبار على هذا الجهاز.\n\n" +
                   "السبب المحتمل: Android System WebView معطل أو غير مثبت أو قديم.\n\n" +
                   "الحل: اذهب إلى الإعدادات → التطبيقات → Android System WebView → فعّله وحدثه من متجر Play.\n\n" +
                   "ثم أعد تشغيل التطبيق."
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            textSize = 18f
        }
        setContentView(errorView)
    }

    private fun showErrorInWebView(message: String) {
        // Try to show error in WebView as last resort
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "showErrorInWebView failed", e)
            showWebViewUnavailableError()
        }
    }
}