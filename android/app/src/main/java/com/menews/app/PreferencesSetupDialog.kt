package com.menews.app

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
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
    private var categoryCheckBoxes = mutableListOf<CheckBox>()
    private var sourceCheckBoxes = mutableListOf<CheckBox>()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnSetupComplete) {
            listener = context
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return buildStepDialog()
    }

    private fun buildStepDialog(): AlertDialog {
        try {
            val inflater = LayoutInflater.from(requireContext())
            val scrollView = ScrollView(requireContext())
            val container = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 16, 32, 16)
            }
            scrollView.addView(container)

            if (step == 0) {
                val categories = getAvailableCategories()
                categoryCheckBoxes.clear()
                categories.forEach { cat ->
                    val cb = CheckBox(requireContext()).apply {
                        text = cat
                        isChecked = true
                        setOnClickListener { if (isChecked) selectedCategories.add(cat) else selectedCategories.remove(cat) }
                    }
                    categoryCheckBoxes.add(cb)
                    container.addView(cb)
                }
            } else {
                val sources = getAvailableSources()
                sourceCheckBoxes.clear()
                sources.forEach { src ->
                    val cb = CheckBox(requireContext()).apply {
                        text = src
                        isChecked = true
                        setOnClickListener { if (isChecked) selectedSources.add(src) else selectedSources.remove(src) }
                    }
                    sourceCheckBoxes.add(cb)
                    container.addView(cb)
                }
            }

            return AlertDialog.Builder(requireContext())
                .setTitle(if (step == 0) "اختر الأقسام التي تريد متابعتها" else "اختر المصادر التي تريد متابعتها")
                .setView(scrollView)
                .setCancelable(false)
                .setPositiveButton(if (step == 0) "التالي" else "حفظ") { _, _ ->
                    if (step == 0) {
                        step = 1
                        show(parentFragmentManager, "prefs_setup")
                    } else {
                        listener?.onComplete(selectedCategories, selectedSources)
                    }
                }
                .setNegativeButton("الكل") { _, _ ->
                    if (step == 0) {
                        selectedCategories = getAvailableCategories().toMutableSet()
                        categoryCheckBoxes.forEach { it.isChecked = true }
                    } else {
                        selectedSources = getAvailableSources().toMutableSet()
                        sourceCheckBoxes.forEach { it.isChecked = true }
                    }
                    if (step == 0) {
                        step = 1
                        show(parentFragmentManager, "prefs_setup")
                    } else {
                        listener?.onComplete(selectedCategories, selectedSources)
                    }
                }
                .create()
        } catch (e: Exception) {
            // Fallback simple dialog
            return AlertDialog.Builder(requireContext())
                .setTitle("خطأ")
                .setMessage("تعذر عرض الإعدادات")
                .setPositiveButton("موافق") { _, _ -> listener?.onComplete(emptySet(), emptySet()) }
                .create()
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
            "Anadolu Agency", "Asharq Al-Awsat", "Al Quds Al Arabi", "Al Masry Al Youm",
            "Youm7", "Sada Elbalad", "El Watan News", "Masrawy"
        )
    }
}