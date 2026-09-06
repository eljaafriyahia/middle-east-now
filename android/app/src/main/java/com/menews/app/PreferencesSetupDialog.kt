package com.menews.app

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatDialogFragment

class PreferencesSetupDialog : AppCompatDialogFragment() {

    interface OnSetupComplete {
        fun onComplete(categories: Set<String>, sources: Set<String>)
    }

    private var listener: OnSetupComplete? = null
    private var selectedCategories = mutableSetOf<String>()
    private var selectedSources = mutableSetOf<String>()
    private var step = 0 // 0 = categories, 1 = sources

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnSetupComplete) {
            listener = context
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return buildStepDialog()
    }

    private fun buildStepDialog(): AlertDialog.Builder {
        val inflater = LayoutInflater.from(requireContext())
        val scrollView = ScrollView(requireContext())
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }
        scrollView.addView(container)

        val (title, items, checkedItems, onItemClick) = when (step) {
            0 -> {
                val categories = getAvailableCategories()
                val checks = categories.map { cat ->
                    CheckBox(requireContext()).apply {
                        text = cat
                        isChecked = true // Default all selected
                        setOnClickListener { selectedCategories.add(cat) }
                    }
                }
                checks.forEach { container.addView(it) }
                "اختر الأقسام التي تريد متابعتها" to categories to
                    checks.map { it.isChecked }.toMutableList() to
                    { idx: Int -> checks[idx].isChecked = !checks[idx].isChecked; selectedCategories.add(categories[idx]) }
            }
            1 -> {
                val sources = getAvailableSources()
                val checks = sources.map { src ->
                    CheckBox(requireContext()).apply {
                        text = src
                        isChecked = true
                        setOnClickListener { selectedSources.add(src) }
                    }
                }
                checks.forEach { container.addView(it) }
                "اختر المصادر التي تريد متابعتها" to sources to
                    checks.map { it.isChecked }.toMutableList() to
                    { idx: Int -> checks[idx].isChecked = !checks[idx].isChecked; selectedSources.add(sources[idx]) }
            }
            else -> throw IllegalStateException()
        }

        return AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(scrollView)
            .setCancelable(false)
            .setPositiveButton(if (step == 0) "التالي" else "حفظ") { _, _ ->
                if (step == 0) {
                    step = 1
                    // Re-show dialog for sources
                    (dialog as? AlertDialog)?.dismiss()
                    show(requireActivity().supportFragmentManager, "prefs_setup")
                } else {
                    listener?.onComplete(selectedCategories, selectedSources)
                }
            }
            .setNegativeButton("الكل") { _, _ ->
                // Select all
                if (step == 0) {
                    selectedCategories = getAvailableCategories().toMutableSet()
                } else {
                    selectedSources = getAvailableSources().toMutableSet()
                }
                if (step == 0) {
                    step = 1
                    (dialog as? AlertDialog)?.dismiss()
                    show(requireActivity().supportFragmentManager, "prefs_setup")
                } else {
                    listener?.onComplete(selectedCategories, selectedSources)
                }
            }
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
}