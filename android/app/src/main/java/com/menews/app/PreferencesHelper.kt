package com.menews.app

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object PreferencesHelper {

    private const val PREFS_NAME = "news_preferences"
    private const val KEY_SELECTED_CATEGORIES = "selected_categories"
    private const val KEY_SELECTED_SOURCES = "selected_sources"
    private const val KEY_SETUP_COMPLETED = "setup_completed"

    fun getSelectedCategories(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SELECTED_CATEGORIES, "[]")
        val type = com.google.gson.reflect.TypeToken.getParameterized(Set::class.java, String::class.java).type
        return Gson().fromJson(json, type) as Set<String>
    }

    fun setSelectedCategories(context: Context, categories: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SELECTED_CATEGORIES, Gson().toJson(categories)).apply()
    }

    fun getSelectedSources(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SELECTED_SOURCES, "[]")
        val type = com.google.gson.reflect.TypeToken.getParameterized(Set::class.java, String::class.java)
        return Gson().fromJson(json, type)
    }

    fun setSelectedSources(context: Context, sources: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SELECTED_SOURCES, Gson().toJson(sources)).apply()
    }

    fun isSetupCompleted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SETUP_COMPLETED, false)
    }

    fun setSetupCompleted(context: Context, completed: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SETUP_COMPLETED, completed).apply()
    }

    fun shouldNotifyForArticle(context: Context, category: String?, sourceId: String?, sourceName: String?): Boolean {
        val selectedCategories = getSelectedCategories(context)
        val selectedSources = getSelectedSources(context)

        // If no preferences set (empty sets), notify for everything
        if (selectedCategories.isEmpty() && selectedSources.isEmpty()) {
            return true
        }

        // Check category match
        val categoryMatch = category != null && selectedCategories.contains(category)
        // Check source match (by id or name)
        val sourceMatch = (sourceId != null && selectedSources.contains(sourceId)) ||
                          (sourceName != null && selectedSources.contains(sourceName))

        // Notify if matches category OR source (or both empty)
        return categoryMatch || sourceMatch
    }
}